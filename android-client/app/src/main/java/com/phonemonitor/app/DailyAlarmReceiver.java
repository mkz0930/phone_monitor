package com.phonemonitor.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * AlarmManager 触发的定时任务 receiver
 * - 19:00: 采集并发送日报到飞书
 * - 23:59: 收集今日 App 使用统计
 */
public class DailyAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "DailyAlarmReceiver";
    private static final int ALARM_REPORT_CODE = 19000;
    private static final int ALARM_STATS_CODE = 23590;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) action = "REPORT";

        Log.i(TAG, "⏰ 定时任务触发: " + action);

        // 获取 WakeLock 防止 CPU 休眠（最多 60 秒）
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "PhoneMonitor:DailyTask");
        wl.acquire(60000);

        new Thread(() -> {
            try {
                if ("STATS".equals(action)) {
                    // 23:59 - 收集使用统计
                    UsageStatsCollector collector = new UsageStatsCollector(context);
                    collector.collectTodayStats();
                    Log.i(TAG, "✅ 使用统计已收集");
                } else {
                    // 19:00 - 发送日报
                    FeishuSender sender = new FeishuSender(context);
                    String result = sender.collectAndSend();
                    Log.i(TAG, "✅ " + result);
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ " + e.getMessage(), e);
            } finally {
                if (wl.isHeld()) wl.release();
            }
        }).start();

        // 重新调度明天
        if ("STATS".equals(action)) {
            scheduleStatsCollection(context);
        } else {
            scheduleDailyReport(context);
        }
    }

    /**
     * 调度每日 19:00 日报
     */
    public static void scheduleDailyReport(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailyAlarmReceiver.class);
        intent.setAction("REPORT");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REPORT_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(Calendar.HOUR_OF_DAY, 19);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                pendingIntent);

        Log.i(TAG, "📅 下次日报: " + cal.getTime());
    }

    /**
     * 调度每日 23:59 使用统计收集
     */
    public static void scheduleStatsCollection(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailyAlarmReceiver.class);
        intent.setAction("STATS");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_STATS_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                pendingIntent);

        Log.i(TAG, "📊 下次统计收集: " + cal.getTime());
    }

    /**
     * 取消所有定时任务
     */
    public static void cancelAllAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // 取消日报
        Intent reportIntent = new Intent(context, DailyAlarmReceiver.class);
        reportIntent.setAction("REPORT");
        PendingIntent reportPending = PendingIntent.getBroadcast(
                context, ALARM_REPORT_CODE, reportIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(reportPending);

        // 取消统计收集
        Intent statsIntent = new Intent(context, DailyAlarmReceiver.class);
        statsIntent.setAction("STATS");
        PendingIntent statsPending = PendingIntent.getBroadcast(
                context, ALARM_STATS_CODE, statsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(statsPending);

        Log.i(TAG, "🛑 所有定时任务已取消");
    }
}
