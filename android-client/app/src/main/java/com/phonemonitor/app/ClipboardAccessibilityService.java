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
 * 无障碍服务：监听剪贴板变化
 * 
 * 双重检测机制：
 * 1. ClipboardManager.OnPrimaryClipChangedListener（主）
 * 2. 每次无障碍事件时轮询剪贴板（备用，兼容 OPPO/vivo 等厂商）
 */
public class ClipboardAccessibilityService extends AccessibilityService {
    private static final String TAG = "ClipA11y";
    private static final String PREFS_NAME = "phone_monitor_prefs";
    private static final String COUNT_KEY = "clipboard_send_count";
    private static final String LAST_CLIP_KEY = "clipboard_last_content";

    private static final long BATCH_WINDOW_MS = 3000;
    private static final long POLL_INTERVAL_MS = 1500; // 事件轮询最小间隔
    private static final long TIMER_POLL_MS = 2000;    // 定时轮询间隔

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private String lastClipHash = "";
    private long lastClipTime = 0;
    private long lastPollTime = 0;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private final List<String> batchBuffer = new ArrayList<>();
    private final Handler batchHandler = new Handler(Looper.getMainLooper());
    private Runnable batchRunnable;

    // 内容类型检测
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://\\S+$", Pattern.DOTALL);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d[\\d\\s\\-()]{7,18}\\d$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("(\\{[\\s\\S]*\\}|function\\s|import\\s|class\\s|def\\s|const\\s|var\\s|let\\s|=>|\\bif\\s*\\(|for\\s*\\()");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(省|市|区|县|路|街|号|楼|室|大厦|广场|小区|village|street|road|ave|blvd)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_DIGITS = Pattern.compile("^\\d{6,20}$");

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        // 配置无障碍：监听所有事件类型
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 200;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        // 方式1：直接监听（部分设备有效）
        clipListener = this::processClipboard;
        clipboardManager.addPrimaryClipChangedListener(clipListener);

        // 方式3：定时轮询（兜底，覆盖抖音等不触发无障碍事件的 App）
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                processClipboard();
                timerHandler.postDelayed(this, TIMER_POLL_MS);
            }
        };
        timerHandler.postDelayed(timerRunnable, TIMER_POLL_MS);

        Log.i(TAG, "✅ 剪贴板监听已启动（三重检测）");
        LogBus.post("✅", "剪贴板监听已启动");
    }

    /**
     * 方式2：每次无障碍事件时检查剪贴板（兼容 OPPO/vivo/ColorOS）
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // 节流：最少间隔 1.5 秒检查一次
        long now = System.currentTimeMillis();
        if (now - lastPollTime < POLL_INTERVAL_MS) return;
        lastPollTime = now;

        processClipboard();
    }

    private void processClipboard() {
        try {
            long now = System.currentTimeMillis();
            // 防抖 500ms
            if (now - lastClipTime < 500) return;

            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;

            CharSequence rawText = clip.getItemAt(0).getText();
            if (rawText == null) {
                rawText = clip.getItemAt(0).coerceToText(this);
            }
            if (rawText == null || rawText.length() == 0) return;

            String content = rawText.toString().trim();
            if (content.isEmpty() || content.length() < 2) return;

            // MD5 去重（核心：同一内容不重复处理）
            String hash = md5(content);
            if (hash.equals(lastClipHash)) return;
            lastClipHash = hash;
            lastClipTime = now;

            // 截断
            if (content.length() > 5000) {
                content = content.substring(0, 5000) + "\n...(已截断)";
            }

            // 过滤敏感内容
            if (isSensitive(content)) {
                Log.d(TAG, "🔒 敏感内容，跳过");
                LogBus.post("📋", "🔒 检测到敏感内容，已跳过");
                return;
            }

            Log.i(TAG, "📋 新内容 (" + content.length() + " chars)");

            // 保存到 prefs
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(LAST_CLIP_KEY, content.length() > 50 ?
                            content.substring(0, 50) + "..." : content)
                    .putLong("clipboard_last_time", now)
                    .apply();

            addToBatch(content);

        } catch (SecurityException se) {
            // Android 13+ 可能限制后台剪贴板访问
            Log.w(TAG, "剪贴板访问被拒: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "处理失败: " + e.getMessage(), e);
        }
    }

    private synchronized void addToBatch(String content) {
        batchBuffer.add(content);

        if (batchRunnable != null) {
            batchHandler.removeCallbacks(batchRunnable);
        }

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

                String typeTag = detectContentType(content);
                if (!typeTag.isEmpty()) {
                    sb.append(typeTag).append(" ");
                }
                sb.append(content);

                if (i < items.size() - 1) {
                    sb.append("\n");
                }
            }

            MessageQueue.getInstance(this).send(sb.toString());
            FeishuWebhook.incrementSendCount(this, COUNT_KEY);
            Log.i(TAG, "📤 已提交 " + items.size() + " 条");

            // 通知 UI
            for (String item : items) {
                String typeTag = detectContentType(item);
                String preview = item.length() > 80 ? item.substring(0, 80) + "..." : item;
                LogBus.post("📋", typeTag + " " + preview);
            }
        }).start();
    }

    String detectContentType(String content) {
        String trimmed = content.trim();
        if (URL_PATTERN.matcher(trimmed).matches()) return "🔗";
        if (PHONE_PATTERN.matcher(trimmed).matches()) return "📞";
        if (EMAIL_PATTERN.matcher(trimmed).matches()) return "📧";
        if (ADDRESS_PATTERN.matcher(trimmed).find() && trimmed.length() < 200) return "📍";
        if (CODE_PATTERN.matcher(trimmed).find()) return "💻";
        if (trimmed.contains("\n") && trimmed.length() > 200) return "📄";
        return "";
    }

    private boolean isSensitive(String content) {
        if (SENSITIVE_DIGITS.matcher(content).matches()) return true;

        String lower = content.toLowerCase();

        if (!content.contains("\n") && content.length() < 200) {
            String[] keywords = {"password", "passwd", "token", "secret",
                    "api_key", "apikey", "private_key", "密码", "口令",
                    "验证码", "otp", "2fa", "mfa"};
            for (String kw : keywords) {
                if (lower.contains(kw)) return true;
            }
        }

        if (lower.contains("-----begin") && lower.contains("-----end")) return true;
        if (content.matches("^eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")) return true;

        return false;
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
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
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
