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
import android.os.Build;
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

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity implements LogBus.LogListener {
    static final String PREFS_NAME = "phone_monitor_prefs";

    private EditText etWebhookUrl, etExtraWebhooks, etAppId, etAppSecret, etSyncChatId;
    private Button btnSave, btnTest, btnGrant, btnSendNow, btnKnowledge, btnDashboard, btnGrowth;
    private SwitchMaterial btnClipboard, btnClipService, btnNotification;
    private TextView tvStatus, tvLog, tvWebhookHeader;
    private LinearLayout layoutWebhook;
    private ScrollView scrollLog;
    private boolean webhookExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etWebhookUrl = findViewById(R.id.et_webhook_url);
        etExtraWebhooks = findViewById(R.id.et_extra_webhooks);
        etAppId = findViewById(R.id.et_app_id);
        etAppSecret = findViewById(R.id.et_app_secret);
        etSyncChatId = findViewById(R.id.et_sync_chat_id);
        btnSave = findViewById(R.id.btn_save);
        btnTest = findViewById(R.id.btn_test);
        btnGrant = findViewById(R.id.btn_grant_permission);
        btnSendNow = findViewById(R.id.btn_send_now);
        btnClipboard = findViewById(R.id.btn_clipboard);
        btnClipService = findViewById(R.id.btn_clip_service);
        btnNotification = findViewById(R.id.btn_notification);
        btnKnowledge = findViewById(R.id.btn_knowledge);
        btnDashboard = findViewById(R.id.btn_dashboard);
        btnGrowth = findViewById(R.id.btn_growth);
        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);
        tvWebhookHeader = findViewById(R.id.tv_webhook_header);
        layoutWebhook = findViewById(R.id.layout_webhook);
        scrollLog = findViewById(R.id.scroll_log);

        loadPrefs();

        // 初始化消息队列
        MessageQueue.getInstance(this);

        // 始终调度定时任务
        DailyAlarmReceiver.scheduleDailyReport(this);
        DailyAlarmReceiver.scheduleStatsCollection(this);

        // 如果是首次运行或数据库为空，尝试采集最近 7 天数据
        new Thread(() -> {
            UsageStatsDb db = UsageStatsDb.getInstance(this);
            if (db.getRecentSummaries(1).isEmpty() && hasUsagePermission()) {
                appendLog("🔍 首次运行，正在采集历史数据...");
                UsageStatsCollector collector = new UsageStatsCollector(this);
                collector.collectRecentHistory(7);
                runOnUiThread(() -> {
                    appendLog("✅ 历史数据采集完成");
                    updateStatus();
                });
            }
        }).start();

        // 预填 Webhook
        SharedPreferences initPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (initPrefs.getString("webhook_url", "").isEmpty()) {
            String defaultUrl = "https://open.feishu.cn/open-apis/bot/v2/hook/48b87aef-33db-435a-90e3-2f5ae80077ba";
            etWebhookUrl.setText(defaultUrl);
            initPrefs.edit().putString("webhook_url", defaultUrl).apply();
            appendLog("✅ 已自动配置默认 Webhook");
        }

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
            DailyAlarmReceiver.scheduleDailyReport(this);
            DailyAlarmReceiver.scheduleStatsCollection(this);
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
                appendLog("ℹ️ 请找到「手机监控」并开启");
            }
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        btnClipService.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean running = ClipboardForegroundService.isServiceRunning();
            if (running) {
                // 停止服务
                stopService(new Intent(this, ClipboardForegroundService.class));
                prefs.edit().putBoolean("clipboard_service_enabled", false).apply();
                appendLog("⏹ 后台剪贴板服务已停止");
            } else {
                // 启动服务
                Intent svcIntent = new Intent(this, ClipboardForegroundService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svcIntent);
                } else {
                    startService(svcIntent);
                }
                ClipboardForegroundService.isRunning = true;
                prefs.edit().putBoolean("clipboard_service_enabled", true).apply();
                appendLog("✅ 后台剪贴板服务已启动");
            }
            updateStatus();
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
                appendLog("ℹ️ 请找到「手机监控」并允许通知使用权");
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
            updateStatus();
        });

        btnKnowledge.setOnClickListener(v -> {
            startActivity(new Intent(this, KnowledgeActivity.class));
        });

        btnDashboard.setOnClickListener(v -> {
            startActivity(new Intent(this, UsageDashboardActivity.class));
        });

        btnGrowth.setOnClickListener(v -> {
            startActivity(new Intent(this, GrowthActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogBus.register(this);
        updateStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogBus.unregister(this);
    }

    @Override
    public void onLog(String tag, String message) {
        appendLog(tag + " " + message);
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
        sb.append(url.isEmpty() ? "❌" : "✅").append(" Webhook");
        String extra = prefs.getString("extra_webhooks", "");
        if (!extra.trim().isEmpty()) {
            int count = extra.trim().split("[,\\n]+").length;
            sb.append(" +").append(count);
        }
        sb.append("\n");

        // 剪贴板
        if (clipEnabled) {
            int clipCount = FeishuWebhook.getSendCount(this, "clipboard_send_count");
            sb.append("✅ 剪贴板(无障碍)");
            if (clipCount > 0) sb.append(" · ").append(clipCount).append("条");
            String lastClip = prefs.getString("clipboard_last_content", "");
            if (!lastClip.isEmpty()) {
                sb.append("\n   📝 ").append(lastClip);
            }
        } else {
            sb.append("❌ 剪贴板(无障碍)未开启");
        }
        sb.append("\n");

        // 前台剪贴板服务
        boolean clipSvcRunning = ClipboardForegroundService.isServiceRunning();
        if (clipSvcRunning) {
            sb.append("✅ 后台剪贴板服务运行中");
        } else {
            sb.append("⏹ 后台剪贴板服务未启动");
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
        sb.append("\n");

        // 知识库
        try {
            int knowledgeCount = KnowledgeDb.getInstance(this).getContentCount();
            sb.append("📚 知识库 · ").append(knowledgeCount).append("条");
        } catch (Exception e) {
            sb.append("📚 知识库");
        }

        tvStatus.setText(sb.toString());
        btnGrant.setVisibility(hasPerm ? View.GONE : View.VISIBLE);

        // 剪贴板按钮
        btnClipboard.setChecked(clipEnabled);
        btnClipboard.setText(clipEnabled ? "✅ 无障碍监听中" : "📋 开启无障碍监听");

        // 前台剪贴板服务按钮
        boolean clipSvcRunning2 = ClipboardForegroundService.isServiceRunning();
        btnClipService.setChecked(clipSvcRunning2);
        btnClipService.setText(clipSvcRunning2 ? "✅ 后台剪贴板运行中" : "🔄 启动后台剪贴板服务");

        // 通知按钮
        btnNotification.setChecked(notifEnabled);
        if (notifEnabled) {
            btnNotification.setText("✅ 通知同步中（点击暂停）");
        } else if (notifPermission) {
            btnNotification.setText("⏸ 通知同步已暂停（点击开启）");
        } else {
            btnNotification.setText("🔔 开启通知同步");
        }

        // 知识库按钮
        try {
            int kCount = KnowledgeDb.getInstance(this).getContentCount();
            btnKnowledge.setText("📚 知识库" + (kCount > 0 ? " · " + kCount + "条" : ""));
        } catch (Exception e) {
            btnKnowledge.setText("📚 知识库");
        }
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etWebhookUrl.setText(prefs.getString("webhook_url", ""));
        etExtraWebhooks.setText(prefs.getString("extra_webhooks", ""));
        etAppId.setText(prefs.getString("feishu_app_id", ""));
        etAppSecret.setText(prefs.getString("feishu_app_secret", ""));
        etSyncChatId.setText(prefs.getString("feishu_sync_chat_id", ""));
    }

    private void savePrefs() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("webhook_url", etWebhookUrl.getText().toString().trim())
                .putString("extra_webhooks", etExtraWebhooks.getText().toString().trim())
                .putString("feishu_app_id", etAppId.getText().toString().trim())
                .putString("feishu_app_secret", etAppSecret.getText().toString().trim())
                .putString("feishu_sync_chat_id", etSyncChatId.getText().toString().trim())
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
