package com.phonemonitor.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * 每小时检查娱乐类应用使用时长
 * 超过阈值时震动提醒 + 发送飞书通知
 */
public class EntertainmentAlertReceiver extends BroadcastReceiver {
    private static final String TAG = "EntertainmentAlert";
    private static final int ALARM_CODE = 88800;
    private static final String PREFS_NAME = "phone_monitor_prefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("entertainment_alert_enabled", true);

        // Reschedule next hour first
        scheduleNextCheck(context);

        if (!enabled) {
            Log.d(TAG, "娱乐提醒已关闭，跳过检查");
            return;
        }

        Log.i(TAG, "⏰ 开始检查娱乐应用使用时长");

        // Acquire WakeLock
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "PhoneMonitor:EntertainmentCheck");
        wl.acquire(30000);

        new Thread(() -> {
            try {
                checkAndAlert(context, prefs);
            } catch (Exception e) {
                Log.e(TAG, "检查失败: " + e.getMessage(), e);
            } finally {
                if (wl.isHeld()) wl.release();
            }
        }).start();
    }

    private void checkAndAlert(Context context, SharedPreferences prefs) {
        int thresholdMin = prefs.getInt("entertainment_alert_threshold_min", 30);

        // Current hour window
        TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar cal = Calendar.getInstance(tz);
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);

        // Avoid duplicate alerts for the same hour
        int lastAlertedHour = prefs.getInt("entertainment_last_alerted_hour", -1);
        String lastAlertedDate = prefs.getString("entertainment_last_alerted_date", "");
        String today = String.valueOf(cal.get(Calendar.DAY_OF_YEAR));
        if (lastAlertedHour == currentHour && today.equals(lastAlertedDate)) {
            Log.d(TAG, "本小时已提醒过，跳过");
            return;
        }

        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long hourStart = cal.getTimeInMillis();
        long now = System.currentTimeMillis();

        // Query usage events for this hour
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;

        Map<String, Long> entertainmentApps = queryHourlyEntertainment(usm, hourStart, now);
        long totalMs = 0;
        for (long ms : entertainmentApps.values()) {
            totalMs += ms;
        }

        long totalMin = totalMs / 60000;
        Log.i(TAG, "本小时娱乐时长: " + totalMin + "分钟 (阈值: " + thresholdMin + ")");

        if (totalMin >= thresholdMin) {
            // Vibrate
            vibrate(context);

            // Build and send Feishu alert
            StringBuilder sb = new StringBuilder();
            sb.append("⚠️ 娱乐时间提醒\n");
            sb.append("过去1小时娱乐类应用使用时长已达 ").append(totalMin).append(" 分钟\n");
            for (Map.Entry<String, Long> entry : entertainmentApps.entrySet()) {
                long min = entry.getValue() / 60000;
                if (min < 1) continue;
                AppDictionary.AppInfo info = AppDictionary.lookup(entry.getKey());
                String name = info != null ? info.name : entry.getKey();
                String emoji = info != null ? info.emoji : "📱";
                sb.append(emoji).append(" ").append(name).append(": ").append(min).append("分钟\n");
            }
            sb.append("建议休息一下 👀");

            FeishuWebhook.sendText(context, sb.toString());
            Log.i(TAG, "⚠️ 已发送娱乐提醒");

            // Mark this hour as alerted
            prefs.edit()
                    .putInt("entertainment_last_alerted_hour", currentHour)
                    .putString("entertainment_last_alerted_date", today)
                    .apply();
        }
    }

    /**
     * Query entertainment app usage for a time window using UsageEvents for accuracy
     */
    static Map<String, Long> queryHourlyEntertainment(UsageStatsManager usm, long start, long end) {
        Map<String, Long> result = new HashMap<>();
        Map<String, Long> lastForeground = new HashMap<>();

        try {
            UsageEvents events = usm.queryEvents(start, end);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                String pkg = event.getPackageName();
                String cat = AppDictionary.getCategory(pkg);
                if (!"视频".equals(cat) && !"游戏".equals(cat)) continue;

                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForeground.put(pkg, event.getTimeStamp());
                } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                    Long startTs = lastForeground.remove(pkg);
                    if (startTs != null) {
                        long duration = event.getTimeStamp() - startTs;
                        result.merge(pkg, duration, Long::sum);
                    }
                }
            }
            // Account for apps still in foreground
            for (Map.Entry<String, Long> entry : lastForeground.entrySet()) {
                long duration = end - entry.getValue();
                result.merge(entry.getKey(), duration, Long::sum);
            }
        } catch (Exception e) {
            Log.w(TAG, "查询娱乐使用事件失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * Query per-hour entertainment breakdown for the whole day (for daily report timeline)
     */
    static Map<Integer, Map<String, Long>> queryDailyHourlyBreakdown(UsageStatsManager usm, long dayStart) {
        Map<Integer, Map<String, Long>> hourly = new HashMap<>();
        long now = System.currentTimeMillis();

        try {
            UsageEvents events = usm.queryEvents(dayStart, now);
            UsageEvents.Event event = new UsageEvents.Event();
            Map<String, Long> lastForeground = new HashMap<>();

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                String pkg = event.getPackageName();
                String cat = AppDictionary.getCategory(pkg);
                // Include all apps for timeline, not just entertainment
                if ("系统".equals(cat) || "其他".equals(cat)) continue;

                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForeground.put(pkg, event.getTimeStamp());
                } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                    Long startTs = lastForeground.remove(pkg);
                    if (startTs != null) {
                        addToHourlyBucket(hourly, pkg, startTs, event.getTimeStamp(), dayStart);
                    }
                }
            }
            // Still-foreground apps
            for (Map.Entry<String, Long> entry : lastForeground.entrySet()) {
                addToHourlyBucket(hourly, entry.getKey(), entry.getValue(), now, dayStart);
            }
        } catch (Exception e) {
            Log.w(TAG, "查询每小时明细失败: " + e.getMessage());
        }
        return hourly;
    }

    private static void addToHourlyBucket(Map<Integer, Map<String, Long>> hourly,
                                           String pkg, long start, long end, long dayStart) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        // Split across hour boundaries
        long cursor = start;
        while (cursor < end) {
            cal.setTimeInMillis(cursor);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.HOUR_OF_DAY, 1);
            long hourEnd = Math.min(cal.getTimeInMillis(), end);
            long duration = hourEnd - cursor;

            hourly.computeIfAbsent(hour, k -> new HashMap<>())
                    .merge(pkg, duration, Long::sum);
            cursor = hourEnd;
        }
    }

    private void vibrate(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            // 3 short bursts
            long[] pattern = {0, 300, 200, 300, 200, 300};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            Log.w(TAG, "震动失败: " + e.getMessage());
        }
    }

    /**
     * Schedule the next hourly check (on the next hour boundary)
     */
    public static void scheduleNextCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, EntertainmentAlertReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(Calendar.MINUTE, 5); // Check at :05 past each hour for data accuracy
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.HOUR_OF_DAY, 1);
        }

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                pendingIntent);

        Log.i(TAG, "⏰ 下次娱乐检查: " + cal.getTime());
    }

    /**
     * Cancel the hourly check alarm
     */
    public static void cancelCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, EntertainmentAlertReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
        Log.i(TAG, "🛑 娱乐检查已取消");
    }
}
