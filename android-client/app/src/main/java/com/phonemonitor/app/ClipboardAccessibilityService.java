package com.phonemonitor.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 无障碍服务：监听剪贴板变化，智能识别内容类型，批量发送
 */
public class ClipboardAccessibilityService extends AccessibilityService {
    private static final String TAG = "ClipA11y";
    private static final String PREFS_NAME = "phone_monitor_prefs";
    private static final String COUNT_KEY = "clipboard_send_count";
    private static final String LAST_CLIP_KEY = "clipboard_last_content";

    // 批量发送：3秒内的多次复制合并为一条消息
    private static final long BATCH_WINDOW_MS = 3000;

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private String lastClipHash = "";
    private long lastClipTime = 0;

    // 批量缓冲
    private final List<String> batchBuffer = new ArrayList<>();
    private final Handler batchHandler = new Handler(Looper.getMainLooper());
    private Runnable batchRunnable;

    // 内容类型检测 patterns
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://\\S+$", Pattern.DOTALL);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d[\\d\\s\\-()]{7,18}\\d$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("(\\{[\\s\\S]*\\}|function\\s|import\\s|class\\s|def\\s|const\\s|var\\s|let\\s|=>|\\bif\\s*\\(|for\\s*\\()");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(省|市|区|县|路|街|号|楼|室|大厦|广场|小区|village|street|road|ave|blvd)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_DIGITS = Pattern.compile("^\\d{6,20}$");

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 500;
        setServiceInfo(info);

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipListener = this::onClipChanged;
        clipboardManager.addPrimaryClipChangedListener(clipListener);

        Log.i(TAG, "✅ 无障碍剪贴板监听已启动");
    }

    private void onClipChanged() {
        try {
            long now = System.currentTimeMillis();
            // 防抖 300ms
            if (now - lastClipTime < 300) return;
            lastClipTime = now;

            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;

            CharSequence rawText = clip.getItemAt(0).getText();
            if (rawText == null) {
                rawText = clip.getItemAt(0).coerceToText(this);
            }
            if (rawText == null || rawText.length() == 0) return;

            String content = rawText.toString().trim();
            if (content.isEmpty() || content.length() < 2) return;

            // MD5 去重
            String hash = md5(content);
            if (hash.equals(lastClipHash)) return;
            lastClipHash = hash;

            // 截断
            if (content.length() > 5000) {
                content = content.substring(0, 5000) + "\n...(已截断)";
            }

            // 过滤敏感内容
            if (isSensitive(content)) {
                Log.d(TAG, "敏感内容，跳过");
                return;
            }

            Log.i(TAG, "📋 新内容 (" + content.length() + " chars)");

            // 保存最后一条到 prefs（供 UI 显示）
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(LAST_CLIP_KEY, content.length() > 50 ?
                            content.substring(0, 50) + "..." : content)
                    .putLong("clipboard_last_time", now)
                    .apply();

            // 加入批量缓冲
            addToBatch(content);

        } catch (Exception e) {
            Log.e(TAG, "处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量发送：3秒窗口内的多次复制合并为一条消息
     */
    private synchronized void addToBatch(String content) {
        batchBuffer.add(content);

        // 取消之前的定时发送
        if (batchRunnable != null) {
            batchHandler.removeCallbacks(batchRunnable);
        }

        // 3秒后发送
        batchRunnable = () -> {
            List<String> toSend;
            synchronized (this) {
                toSend = new ArrayList<>(batchBuffer);
                batchBuffer.clear();
            }
            if (!toSend.isEmpty()) {
                sendBatch(toSend);
            }
        };
        batchHandler.postDelayed(batchRunnable, BATCH_WINDOW_MS);
    }

    private void sendBatch(List<String> items) {
        new Thread(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

            StringBuilder sb = new StringBuilder();
            sb.append("📋 剪贴板同步");
            if (items.size() > 1) {
                sb.append(" (").append(items.size()).append("条)");
            }
            sb.append("\n");
            sb.append("⏰ ").append(time).append(" · ").append(Build.MODEL).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━\n");

            for (int i = 0; i < items.size(); i++) {
                String content = items.get(i);
                if (items.size() > 1) {
                    sb.append("\n[").append(i + 1).append("] ");
                } else {
                    sb.append("\n");
                }

                // 智能类型标记
                String typeTag = detectContentType(content);
                if (!typeTag.isEmpty()) {
                    sb.append(typeTag).append(" ");
                }
                sb.append(content);

                if (i < items.size() - 1) {
                    sb.append("\n");
                }
            }

            boolean ok = FeishuWebhook.sendText(this, sb.toString());
            if (ok) {
                FeishuWebhook.incrementSendCount(this, COUNT_KEY);
                Log.i(TAG, "✅ 已发送 " + items.size() + " 条");
            }
        }).start();
    }

    /**
     * 智能内容类型检测
     */
    private String detectContentType(String content) {
        String trimmed = content.trim();

        if (URL_PATTERN.matcher(trimmed).matches()) return "🔗";
        if (PHONE_PATTERN.matcher(trimmed).matches()) return "📞";
        if (EMAIL_PATTERN.matcher(trimmed).matches()) return "📧";
        if (ADDRESS_PATTERN.matcher(trimmed).find() && trimmed.length() < 200) return "📍";
        if (CODE_PATTERN.matcher(trimmed).find()) return "💻";
        if (trimmed.contains("\n") && trimmed.length() > 200) return "📄";

        return "";
    }

    /**
     * 敏感内容过滤（增强版）
     */
    private boolean isSensitive(String content) {
        // 纯数字 6-20 位（验证码/密码）
        if (SENSITIVE_DIGITS.matcher(content).matches()) return true;

        String lower = content.toLowerCase();

        // 短文本中的敏感关键词
        if (!content.contains("\n") && content.length() < 200) {
            String[] keywords = {"password", "passwd", "token", "secret",
                    "api_key", "apikey", "private_key", "密码", "口令",
                    "验证码", "otp", "2fa", "mfa"};
            for (String kw : keywords) {
                if (lower.contains(kw)) return true;
            }
        }

        // SSH key / PEM
        if (lower.contains("-----begin") && lower.contains("-----end")) return true;

        // JWT token pattern
        if (content.matches("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")) return true;

        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 仅用于保持服务存活
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
        if (batchRunnable != null) {
            batchHandler.removeCallbacks(batchRunnable);
        }
        Log.i(TAG, "无障碍服务已停止");
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
