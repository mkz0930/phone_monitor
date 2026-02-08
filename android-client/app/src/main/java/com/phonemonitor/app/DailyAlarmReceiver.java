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
 * AlarmManager 触发的定时发送 receiver
 * 每天 19:00 自动采集并发送日报到飞书
 */
public class DailyAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "DailyAlarmReceiver";
    private static final int ALARM_REQUEST_CODE = 19000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "⏰ 定时任务触发");

        // 获取 WakeLock 防止 CPU 休眠（最多 60 秒）
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "PhoneMonitor:DailyReport");
        wl.acquire(60000);

        new Thread(() -> {
            try {
                FeishuSender sender = new FeishuSender(context);
                String result = sender.collectAndSend();
                Log.i(TAG, "✅ " + result);
            } catch (Exception e) {
                Log.e(TAG, "❌ " + e.getMessage(), e);
            } finally {
                if (wl.isHeld()) wl.release();
            }
        }).start();

        // 重新调度明天
        scheduleDailyAlarm(context);
    }

    public static void scheduleDailyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailyAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
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

    public static void cancelDailyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailyAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }
}
