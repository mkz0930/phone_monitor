package com.phonemonitor.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class UsageUploadWorker extends Worker {
    private static final String TAG = "UsageUploadWorker";

    public UsageUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "开始上报使用数据...");
        try {
            FeishuSender sender = new FeishuSender(getApplicationContext());
            String result = sender.collectAndSend();
            Log.i(TAG, "上报成功: " + result);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "上报失败: " + e.getMessage(), e);
            return Result.retry();
        }
    }
}
