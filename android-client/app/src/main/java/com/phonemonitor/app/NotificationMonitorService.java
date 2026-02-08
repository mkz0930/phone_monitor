package com.phonemonitor.app;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 通知监听服务：捕获重要通知并同步到飞书
 * 需要用户在「通知使用权」中授权
 */
public class NotificationMonitorService extends NotificationListenerService {
    private static final String TAG = "NotifMonitor";
    private static final String PREFS_NAME = "phone_monitor_prefs";
    private static final String COUNT_KEY = "notification_send_count";

    // 去重：最近 50 条通知的 hash
    private final Set<String> recentHashes = new HashSet<>();
    private static final int MAX_RECENT = 50;

    // 忽略的包名（系统/低价值通知）
    private static final Set<String> IGNORED_PACKAGES = new HashSet<>();
    static {
        IGNORED_PACKAGES.add("com.android.systemui");
        IGNORED_PACKAGES.add("com.android.providers.downloads");
        IGNORED_PACKAGES.add("android");
        IGNORED_PACKAGES.add("com.android.vending");  // Play Store 更新
        IGNORED_PACKAGES.add("com.google.android.gms"); // Google Play Services
        IGNORED_PACKAGES.add("com.google.android.gsf"); // Google Services Framework
        IGNORED_PACKAGES.add("com.android.settings");
        IGNORED_PACKAGES.add("com.phonemonitor.app");  // 自己
    }

    // 重要通知的包名（优先同步）
    private static final Set<String> PRIORITY_PACKAGES = new HashSet<>();
    static {
        PRIORITY_PACKAGES.add("com.tencent.mm");           // 微信
        PRIORITY_PACKAGES.add("com.tencent.mobileqq");     // QQ
        PRIORITY_PACKAGES.add("org.telegram.messenger");    // Telegram
        PRIORITY_PACKAGES.add("com.whatsapp");              // WhatsApp
        PRIORITY_PACKAGES.add("com.ss.android.lark");       // 飞书
        PRIORITY_PACKAGES.add("com.alibaba.android.rimet"); // 钉钉
        PRIORITY_PACKAGES.add("com.tencent.wework");        // 企业微信
        PRIORITY_PACKAGES.add("com.google.android.gm");     // Gmail
        PRIORITY_PACKAGES.add("com.android.phone");         // 来电
        PRIORITY_PACKAGES.add("com.android.mms");           // 短信
        PRIORITY_PACKAGES.add("com.google.android.apps.messaging"); // Google Messages
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();

            // 忽略系统通知
            if (IGNORED_PACKAGES.contains(pkg)) return;

            // 忽略 ongoing（进行中）通知（如音乐播放、导航）
            if (sbn.isOngoing()) return;

            // 忽略 group summary
            Notification notification = sbn.getNotification();
            if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

            // 提取内容
            Bundle extras = notification.extras;
            String title = extras.getCharSequence(Notification.EXTRA_TITLE, "").toString().trim();
            String text = extras.getCharSequence(Notification.EXTRA_TEXT, "").toString().trim();
            String bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT, "").toString().trim();

            // 用 bigText 如果有（更完整）
            String content = bigText.isEmpty() ? text : bigText;

            // 空通知跳过
            if (title.isEmpty() && content.isEmpty()) return;

            // 去重
            String hash = md5(pkg + "|" + title + "|" + content);
            synchronized (recentHashes) {
                if (recentHashes.contains(hash)) return;
                if (recentHashes.size() >= MAX_RECENT) recentHashes.clear();
                recentHashes.add(hash);
            }

            // 检查是否启用通知同步
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("notification_enabled", false);
            if (!enabled) return;

            // 非优先包：只同步标记为重要的
            boolean isPriority = PRIORITY_PACKAGES.contains(pkg);
            boolean onlyPriority = prefs.getBoolean("notification_priority_only", true);
            if (onlyPriority && !isPriority) return;

            // 格式化并发送
            String appName = getAppName(pkg);
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

            StringBuilder sb = new StringBuilder();
            sb.append("🔔 通知同步\n");
            sb.append("⏰ ").append(time).append(" · ").append(DeviceNames.get()).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━\n\n");
            sb.append("📱 ").append(appName);
            if (isPriority) sb.append(" ⭐");
            sb.append("\n");
            if (!title.isEmpty()) {
                sb.append("📌 ").append(title).append("\n");
            }
            if (!content.isEmpty()) {
                // 截断超长内容
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                sb.append(content);
            }

            Log.i(TAG, "🔔 " + appName + ": " + title);

            new Thread(() -> {
                MessageQueue.getInstance(this).send(sb.toString());
                FeishuWebhook.incrementSendCount(this, COUNT_KEY);
            }).start();

            // 通知 UI 日志
            String preview = title.isEmpty() ? content : title;
            if (preview.length() > 60) preview = preview.substring(0, 60) + "...";
            LogBus.post("🔔", appName + ": " + preview);

        } catch (Exception e) {
            Log.e(TAG, "处理通知失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 不需要处理
    }

    private String getAppName(String pkg) {
        // 先查字典
        AppDictionary.AppInfo info = AppDictionary.lookup(pkg);
        if (info != null) return info.emoji + " " + info.name;

        // 再查系统
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            String[] parts = pkg.split("\\.");
            return parts[parts.length - 1];
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
