package com.phonemonitor.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 前台服务：监听剪贴板变化，有新内容时发送到飞书群
 */
public class ClipboardService extends Service {
    private static final String TAG = "ClipboardService";
    private static final String CHANNEL_ID = "clipboard_monitor";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS_NAME = "phone_monitor_prefs";

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private String lastClipHash = "";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("监听中..."));

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipListener = () -> {
            try {
                ClipData clip = clipboardManager.getPrimaryClip();
                if (clip == null || clip.getItemCount() == 0) return;

                CharSequence text = clip.getItemAt(0).getText();
                if (text == null || text.length() == 0) return;

                String content = text.toString().trim();
                if (content.isEmpty()) return;

                // 去重：跟上次一样就跳过
                String hash = md5(content);
                if (hash.equals(lastClipHash)) return;
                lastClipHash = hash;

                // 过滤太短或太长的内容
                if (content.length() < 2) return;
                if (content.length() > 5000) {
                    content = content.substring(0, 5000) + "\n...(已截断)";
                }

                // 过滤密码/敏感内容（简单规则）
                if (looksLikeSensitive(content)) {
                    Log.d(TAG, "跳过疑似敏感内容");
                    return;
                }

                Log.i(TAG, "📋 新剪贴板内容 (" + content.length() + " chars)");
                updateNotification("最近: " + content.substring(0, Math.min(30, content.length())) + "...");

                // 发送到飞书
                sendToFeishu(content);

            } catch (Exception e) {
                Log.e(TAG, "剪贴板处理失败: " + e.getMessage(), e);
            }
        };

        clipboardManager.addPrimaryClipChangedListener(clipListener);
        Log.i(TAG, "✅ 剪贴板监听已启动");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // 被杀后自动重启
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (clipboardManager != null && clipListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        }
        Log.i(TAG, "剪贴板监听已停止");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendToFeishu(String content) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String webhookUrl = prefs.getString("webhook_url", "");
                if (webhookUrl.isEmpty()) {
                    Log.w(TAG, "Webhook 未配置");
                    return;
                }

                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                String deviceModel = Build.MODEL;

                // 判断内容类型并格式化
                String formatted = formatClipContent(content, time, deviceModel);

                // 发送
                org.json.JSONObject jsonContent = new org.json.JSONObject();
                jsonContent.put("text", formatted);

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("msg_type", "text");
                body.put("content", jsonContent);

                java.net.URL url = new java.net.URL(webhookUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                byte[] payload = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int code = conn.getResponseCode();
                conn.disconnect();

                if (code == 200) {
                    Log.i(TAG, "✅ 已发送到飞书");
                } else {
                    Log.e(TAG, "飞书返回: " + code);
                }
            } catch (Exception e) {
                Log.e(TAG, "发送失败: " + e.getMessage(), e);
            }
        }).start();
    }

    private String formatClipContent(String content, String time, String device) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 剪贴板同步\n");
        sb.append("⏰ ").append(time).append(" · ").append(device).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━\n\n");

        // 检测内容类型
        if (content.matches("https?://\\S+")) {
            sb.append("🔗 链接:\n");
        } else if (content.contains("\n") && content.length() > 100) {
            sb.append("📄 长文本:\n");
        }

        sb.append(content);
        return sb.toString();
    }

    /**
     * 简单判断是否为敏感内容（密码、token 等）
     */
    private boolean looksLikeSensitive(String content) {
        String lower = content.toLowerCase();
        // 纯数字 6-20 位（可能是验证码/密码）
        if (content.matches("^\\d{6,20}$")) return true;
        // 包含 password/token/secret 关键词的单行内容
        if (!content.contains("\n") && content.length() < 200) {
            if (lower.contains("password") || lower.contains("token") ||
                lower.contains("secret") || lower.contains("api_key") ||
                lower.contains("apikey")) {
                return true;
            }
        }
        return false;
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "剪贴板监听", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("监听剪贴板变化并同步到飞书");
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("📋 剪贴板监听")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }
}
