package com.phonemonitor.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启：重新注册每天 19:00 的定时发送
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("BootReceiver", "📱 开机完成，重新注册定时任务");
            DailyAlarmReceiver.scheduleDailyAlarm(context);
        }
    }
}
