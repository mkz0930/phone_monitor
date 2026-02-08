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
        Log.i(TAG, "Starting usage upload...");
        try {
            UsageReporter reporter = new UsageReporter(getApplicationContext());
            String result = reporter.collectAndSend();
            Log.i(TAG, "Upload success: " + result);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Upload failed: " + e.getMessage(), e);
            return Result.retry();
        }
    }
}
