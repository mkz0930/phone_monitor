package com.phonemonitor.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 无障碍服务：监听剪贴板变化
 * 无障碍服务拥有系统级权限，可以在后台读取剪贴板（绕过 Android 10+ 限制）
 */
public class ClipboardAccessibilityService extends AccessibilityService {
    private static final String TAG = "ClipA11y";
    private static final String PREFS_NAME = "phone_monitor_prefs";

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private String lastClipHash = "";
    private long lastClipTime = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        // 配置无障碍服务（最小权限）
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 500;
        setServiceInfo(info);

        // 注册剪贴板监听
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipListener = this::onClipChanged;
        clipboardManager.addPrimaryClipChangedListener(clipListener);

        Log.i(TAG, "✅ 无障碍剪贴板监听已启动");
    }

    private void onClipChanged() {
        try {
            // 防抖：500ms 内的重复事件忽略
            long now = System.currentTimeMillis();
            if (now - lastClipTime < 500) return;
            lastClipTime = now;

            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                Log.d(TAG, "剪贴板为空");
                return;
            }

            // 尝试获取文本
            CharSequence rawText = clip.getItemAt(0).getText();
            if (rawText == null) {
                // 尝试 coerceToText
                rawText = clip.getItemAt(0).coerceToText(this);
            }
            if (rawText == null || rawText.length() == 0) {
                Log.d(TAG, "剪贴板内容为空");
                return;
            }

            String content = rawText.toString().trim();
            if (content.isEmpty() || content.length() < 2) return;

            // MD5 去重
            String hash = md5(content);
            if (hash.equals(lastClipHash)) {
                Log.d(TAG, "重复内容，跳过");
                return;
            }
            lastClipHash = hash;

            // 截断超长内容
            if (content.length() > 5000) {
                content = content.substring(0, 5000) + "\n...(已截断)";
            }

            // 过滤敏感内容
            if (looksLikeSensitive(content)) {
                Log.d(TAG, "疑似敏感内容，跳过");
                return;
            }

            Log.i(TAG, "📋 新内容 (" + content.length() + " chars)");
            sendToFeishu(content);

        } catch (Exception e) {
            Log.e(TAG, "处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理无障碍事件，只用它来保持服务存活
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (clipboardManager != null && clipListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        }
        Log.i(TAG, "无障碍服务已停止");
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

                StringBuilder sb = new StringBuilder();
                sb.append("📋 剪贴板同步\n");
                sb.append("⏰ ").append(time).append(" · ").append(Build.MODEL).append("\n");
                sb.append("━━━━━━━━━━━━━━━━━━\n\n");

                // 内容类型标记
                if (content.matches("(?s)^https?://\\S+$")) {
                    sb.append("🔗 ");
                } else if (content.contains("\n") && content.length() > 200) {
                    sb.append("📄 ");
                }

                sb.append(content);

                JSONObject jsonContent = new JSONObject();
                jsonContent.put("text", sb.toString());

                JSONObject body = new JSONObject();
                body.put("msg_type", "text");
                body.put("content", jsonContent);

                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int code = conn.getResponseCode();
                conn.disconnect();

                Log.i(TAG, code == 200 ? "✅ 已发送" : "❌ 飞书返回: " + code);
            } catch (Exception e) {
                Log.e(TAG, "发送失败: " + e.getMessage(), e);
            }
        }).start();
    }

    private boolean looksLikeSensitive(String content) {
        if (content.matches("^\\d{6,20}$")) return true;
        String lower = content.toLowerCase();
        if (!content.contains("\n") && content.length() < 200) {
            if (lower.contains("password") || lower.contains("token") ||
                lower.contains("secret") || lower.contains("api_key") ||
                lower.contains("apikey") || lower.contains("密码")) {
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
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
