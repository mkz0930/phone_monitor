package com.phonemonitor.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * 开机自启：重新注册定时任务 + 恢复剪贴板监听
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("BootReceiver", "📱 开机完成，恢复服务");

            // 恢复每天 19:00 日报
            DailyAlarmReceiver.scheduleDailyAlarm(context);

            // 恢复剪贴板监听
            SharedPreferences prefs = context.getSharedPreferences(
                    "phone_monitor_prefs", Context.MODE_PRIVATE);
            if (prefs.getBoolean("clipboard_enabled", false)) {
                Intent svcIntent = new Intent(context, ClipboardService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent);
                } else {
                    context.startService(svcIntent);
                }
                Log.i("BootReceiver", "📋 剪贴板监听已恢复");
            }
        }
    }
}
