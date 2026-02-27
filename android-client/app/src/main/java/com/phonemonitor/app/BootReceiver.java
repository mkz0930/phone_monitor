package com.phonemonitor.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * 开机自启：
 * 1. 注册每天 19:00 日报定时任务
 * 2. 启动前台剪贴板服务（如果用户之前开启过）
 * 无障碍服务由系统自动恢复，无需手动启动
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("BootReceiver", "📱 开机完成，注册日报定时任务");
            DailyAlarmReceiver.scheduleDailyReport(context);
            DailyAlarmReceiver.scheduleStatsCollection(context);

            SharedPreferences prefs = context.getSharedPreferences("phone_monitor_prefs",
                    Context.MODE_PRIVATE);

            // 调度娱乐提醒（如果启用）
            if (prefs.getBoolean("entertainment_alert_enabled", true)) {
                Log.i("BootReceiver", "🎮 调度娱乐提醒");
                EntertainmentAlertReceiver.scheduleNextCheck(context);
            }

            // 如果用户之前开启了前台剪贴板服务，自动启动
            boolean clipServiceEnabled = prefs.getBoolean("clipboard_service_enabled", false);
            if (clipServiceEnabled) {
                Log.i("BootReceiver", "🔄 自动启动前台剪贴板服务");
                Intent svcIntent = new Intent(context, ClipboardForegroundService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent);
                } else {
                    context.startService(svcIntent);
                }
            }
        }
    }
}
