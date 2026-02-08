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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    static final String PREFS_NAME = "phone_monitor_prefs";

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

        // 预填 Webhook（如果为空）
        SharedPreferences initPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (initPrefs.getString("webhook_url", "").isEmpty()) {
            String defaultUrl = "https://open.feishu.cn/open-apis/bot/v2/hook/24f69dd6-c2aa-4dee-9b5e-f959696878b8";
            etWebhookUrl.setText(defaultUrl);
            initPrefs.edit().putString("webhook_url", defaultUrl).apply();
            // 首次自动注册定时任务
            DailyAlarmReceiver.scheduleDailyAlarm(this);
            appendLog("✅ 已自动配置，每天 19:00 发送日报");
        }

        updateStatus();

        btnGrant.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        });

        btnSave.setOnClickListener(v -> {
            savePrefs();
            DailyAlarmReceiver.scheduleDailyAlarm(this);
            Toast.makeText(this, "✅ 已保存，每天 19:00 自动发送", Toast.LENGTH_SHORT).show();
            appendLog("📅 定时任务已注册：每天 19:00 自动发送");
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
        sb.append("\n");

        // 显示下次发送时间
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(Calendar.HOUR_OF_DAY, 19);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        String nextTime = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(cal.getTime());
        sb.append("⏰ 下次自动发送: ").append(nextTime);

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
                String pkg = stats.getPackageName();
                String appName;
                AppDictionary.AppInfo dictInfo = AppDictionary.lookup(pkg);
                if (dictInfo != null) {
                    appName = dictInfo.emoji + " " + dictInfo.name;
                } else {
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                        appName = pm.getApplicationLabel(ai).toString();
                    } catch (PackageManager.NameNotFoundException e) {
                        String[] parts = pkg.split("\\.");
                        appName = parts[parts.length - 1];
                    }
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
