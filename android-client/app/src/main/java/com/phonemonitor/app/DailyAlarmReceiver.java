package com.phonemonitor.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
        Log.i(TAG, "⏰ 定时任务触发，开始采集并发送日报...");

        // 在后台线程执行
        new Thread(() -> {
            try {
                FeishuSender sender = new FeishuSender(context);
                String result = sender.collectAndSend();
                Log.i(TAG, "✅ 日报发送成功: " + result);
            } catch (Exception e) {
                Log.e(TAG, "❌ 日报发送失败: " + e.getMessage(), e);
            }
        }).start();

        // 重新调度明天的闹钟（防止漂移）
        scheduleDailyAlarm(context);
    }

    /**
     * 设置每天 19:00 (Asia/Shanghai) 的精确闹钟
     */
    public static void scheduleDailyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailyAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 计算下一个 19:00
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(Calendar.HOUR_OF_DAY, 19);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // 如果今天 19:00 已过，设为明天
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        // 使用 setExactAndAllowWhileIdle 确保省电模式下也能触发
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(),
                pendingIntent);

        Log.i(TAG, "📅 下次日报时间: " + cal.getTime());
    }

    /**
     * 取消定时任务
     */
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
