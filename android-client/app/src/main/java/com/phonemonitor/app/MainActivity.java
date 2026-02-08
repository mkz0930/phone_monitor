package com.phonemonitor.app;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "phone_monitor_prefs";

    private EditText etWebhookUrl;
    private Button btnSave, btnTest, btnGrant, btnSendNow;
    private TextView tvStatus, tvLog;
    private ScrollView scrollLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etWebhookUrl = findViewById(R.id.et_webhook_url);
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test);
        btnGrant = findViewById(R.id.btn_grant_permission);
        btnSendNow = findViewById(R.id.btn_send_now);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        scrollLog = findViewById(R.id.scroll_log);

        loadPrefs();
        updateStatus();

        btnGrant.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        });

        btnSave.setOnClickListener(v -> {
            savePrefs();
            scheduleWork();
            Toast.makeText(this, "✅ 已保存，定时任务已启动", Toast.LENGTH_SHORT).show();
            updateStatus();
        });

        btnTest.setOnClickListener(v -> {
            if (!hasUsagePermission()) {
                Toast.makeText(this, "⚠️ 请先授权使用情况访问", Toast.LENGTH_SHORT).show();
                return;
            }
            appendLog("📊 正在采集今日数据...");
            new Thread(() -> {
                String result = collectAndFormat();
                runOnUiThread(() -> appendLog(result));
            }).start();
        });

        btnSendNow.setOnClickListener(v -> {
            if (!hasUsagePermission()) {
                Toast.makeText(this, "⚠️ 请先授权使用情况访问", Toast.LENGTH_SHORT).show();
                return;
            }
            savePrefs();
            appendLog("📤 正在发送到飞书...");
            new Thread(() -> {
                try {
                    FeishuSender sender = new FeishuSender(this);
                    String result = sender.collectAndSend();
                    runOnUiThread(() -> appendLog("✅ " + result));
                } catch (Exception e) {
                    runOnUiThread(() -> appendLog("❌ " + e.getMessage()));
                }
            }).start();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private boolean hasUsagePermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void updateStatus() {
        boolean hasPerm = hasUsagePermission();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String url = prefs.getString("webhook_url", "");

        StringBuilder sb = new StringBuilder();
        sb.append(hasPerm ? "✅ 使用情况权限已授权" : "❌ 使用情况权限未授权");
        sb.append("\n");
        sb.append(url.isEmpty() ? "❌ 飞书 Webhook 未配置" : "✅ Webhook 已配置");

        tvStatus.setText(sb.toString());
        btnGrant.setVisibility(hasPerm ? View.GONE : View.VISIBLE);
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etWebhookUrl.setText(prefs.getString("webhook_url", ""));
    }

    private void savePrefs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("webhook_url", etWebhookUrl.getText().toString().trim())
                .apply();
    }

    private void scheduleWork() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                UsageUploadWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "phone_monitor_upload",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest);
    }

    private String collectAndFormat() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            PackageManager pm = getPackageManager();

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startTime = cal.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startTime, endTime);
            if (statsMap == null || statsMap.isEmpty()) {
                return "今日暂无使用数据";
            }

            List<UsageStats> statsList = new ArrayList<>(statsMap.values());
            Collections.sort(statsList, (a, b) ->
                    Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

            StringBuilder sb = new StringBuilder();
            sb.append("📱 今日使用情况：\n\n");
            long totalMs = 0;
            int count = 0;

            for (UsageStats stats : statsList) {
                long fg = stats.getTotalTimeInForeground();
                if (fg < 60000) continue;

                totalMs += fg;
                count++;
                String appName;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(stats.getPackageName(), 0);
                    appName = pm.getApplicationLabel(ai).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    appName = stats.getPackageName();
                }

                sb.append(String.format("• %s: %s\n", appName, formatMs(fg)));
                if (count >= 15) break;
            }

            sb.append(String.format("\n合计: %s (%d 个应用)", formatMs(totalMs), count));
            return sb.toString();

        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    static String formatMs(long ms) {
        long minutes = ms / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours > 0) return String.format("%d小时%d分钟", hours, minutes);
        return String.format("%d分钟", minutes);
    }

    private void appendLog(String text) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvLog.append("[" + time + "] " + text + "\n\n");
        new Handler(Looper.getMainLooper()).post(() ->
                scrollLog.fullScroll(View.FOCUS_DOWN));
    }
}
