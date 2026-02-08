package com.phonemonitor.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
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
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private Button btnSave, btnTest, btnGrant, btnSendNow, btnClipboard, btnNotification;
    private TextView tvStatus, tvLog, tvWebhookHeader;
    private LinearLayout layoutWebhook;
    private ScrollView scrollLog;
    private boolean webhookExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etWebhookUrl = findViewById(R.id.et_webhook_url);
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test);
        btnGrant = findViewById(R.id.btn_grant_permission);
        btnSendNow = findViewById(R.id.btn_send_now);
        btnClipboard = findViewById(R.id.btn_clipboard);
        btnNotification = findViewById(R.id.btn_notification);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        tvWebhookHeader = findViewById(R.id.tv_webhook_header);
        layoutWebhook = findViewById(R.id.layout_webhook);
        scrollLog = findViewById(R.id.scroll_log);

        loadPrefs();

        // 预填 Webhook
        SharedPreferences initPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (initPrefs.getString("webhook_url", "").isEmpty()) {
            String defaultUrl = "https://open.feishu.cn/open-apis/bot/v2/hook/24f69dd6-c2aa-4dee-9b5e-f959696878b8";
            etWebhookUrl.setText(defaultUrl);
            initPrefs.edit().putString("webhook_url", defaultUrl).apply();
            DailyAlarmReceiver.scheduleDailyAlarm(this);
            appendLog("✅ 已自动配置");
        }

        // 初始化消息队列
        MessageQueue.getInstance(this);

        updateStatus();

        // Webhook 折叠/展开
        tvWebhookHeader.setOnClickListener(v -> {
            webhookExpanded = !webhookExpanded;
            layoutWebhook.setVisibility(webhookExpanded ? View.VISIBLE : View.GONE);
            tvWebhookHeader.setText(webhookExpanded ? "⚙️ Webhook 配置 ▾" : "⚙️ Webhook 配置 ▸");
        });

        btnGrant.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

        btnSave.setOnClickListener(v -> {
            savePrefs();
            DailyAlarmReceiver.scheduleDailyAlarm(this);
            Toast.makeText(this, "✅ 已保存", Toast.LENGTH_SHORT).show();
            appendLog("💾 配置已保存");
            updateStatus();
        });

        btnTest.setOnClickListener(v -> {
            if (!hasUsagePermission()) {
                Toast.makeText(this, "⚠️ 请先授权", Toast.LENGTH_SHORT).show();
                return;
            }
            appendLog("📊 采集中...");
            new Thread(() -> {
                String result = collectAndFormat();
                runOnUiThread(() -> appendLog(result));
            }).start();
        });

        btnSendNow.setOnClickListener(v -> {
            if (!hasUsagePermission()) {
                Toast.makeText(this, "⚠️ 请先授权", Toast.LENGTH_SHORT).show();
                return;
            }
            savePrefs();
            btnSendNow.setEnabled(false);
            appendLog("📤 发送中...");
            new Thread(() -> {
                try {
                    FeishuSender sender = new FeishuSender(this);
                    String result = sender.collectAndSend();
                    runOnUiThread(() -> {
                        appendLog("✅ " + result);
                        btnSendNow.setEnabled(true);
                        updateStatus();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        appendLog("❌ " + e.getMessage());
                        btnSendNow.setEnabled(true);
                    });
                }
            }).start();
        });

        btnClipboard.setOnClickListener(v -> {
            if (isAccessibilityEnabled()) {
                appendLog("ℹ️ 跳转到无障碍设置");
            } else {
                appendLog("ℹ️ 请找到「Phone Monitor」并开启");
            }
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        btnNotification.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (isNotificationListenerEnabled()) {
                // 已有权限，切换开关
                boolean current = prefs.getBoolean("notification_enabled", false);
                prefs.edit().putBoolean("notification_enabled", !current).apply();
                appendLog(!current ? "🔔 通知同步已开启" : "🔕 通知同步已关闭");
            } else {
                // 需要授权
                appendLog("ℹ️ 请找到「Phone Monitor」并允许通知使用权");
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
            updateStatus();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo().serviceInfo.packageName.equals(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        ComponentName cn = new ComponentName(this, NotificationMonitorService.class);
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    private boolean hasUsagePermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void updateStatus() {
        boolean hasPerm = hasUsagePermission();
        boolean clipEnabled = isAccessibilityEnabled();
        boolean notifPermission = isNotificationListenerEnabled();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String url = prefs.getString("webhook_url", "");
        boolean notifEnabled = prefs.getBoolean("notification_enabled", false) && notifPermission;

        StringBuilder sb = new StringBuilder();

        // 权限行
        sb.append(hasPerm ? "✅" : "❌").append(" 使用统计  ");
        sb.append(url.isEmpty() ? "❌" : "✅").append(" Webhook\n");

        // 剪贴板
        if (clipEnabled) {
            int clipCount = FeishuWebhook.getSendCount(this, "clipboard_send_count");
            sb.append("✅ 剪贴板");
            if (clipCount > 0) sb.append(" · ").append(clipCount).append("条");
            String lastClip = prefs.getString("clipboard_last_content", "");
            if (!lastClip.isEmpty()) {
                sb.append("\n   📝 ").append(lastClip);
            }
        } else {
            sb.append("❌ 剪贴板未开启");
        }
        sb.append("\n");

        // 通知
        if (notifEnabled) {
            int notifCount = FeishuWebhook.getSendCount(this, "notification_send_count");
            sb.append("✅ 通知同步");
            if (notifCount > 0) sb.append(" · ").append(notifCount).append("条");
        } else if (notifPermission) {
            sb.append("⏸ 通知同步已暂停");
        } else {
            sb.append("❌ 通知同步未授权");
        }
        sb.append("\n");

        // 下次日报
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(Calendar.HOUR_OF_DAY, 19);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        String nextTime = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(cal.getTime());
        sb.append("⏰ 日报: ").append(nextTime);
        int reportCount = FeishuWebhook.getSendCount(this, "report_send_count");
        if (reportCount > 0) sb.append(" · 累计").append(reportCount).append("次");

        tvStatus.setText(sb.toString());
        btnGrant.setVisibility(hasPerm ? View.GONE : View.VISIBLE);

        // 剪贴板按钮
        if (clipEnabled) {
            btnClipboard.setText("✅ 剪贴板监听中");
            btnClipboard.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        } else {
            btnClipboard.setText("📋 开启剪贴板监听");
            btnClipboard.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFF9800));
        }

        // 通知按钮
        if (notifEnabled) {
            btnNotification.setText("✅ 通知同步中（点击暂停）");
            btnNotification.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        } else if (notifPermission) {
            btnNotification.setText("⏸ 通知同步已暂停（点击开启）");
            btnNotification.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF9E9E9E));
        } else {
            btnNotification.setText("🔔 开启通知同步");
            btnNotification.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFF9800));
        }
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
