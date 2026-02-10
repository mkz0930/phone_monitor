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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 前台服务：后台剪贴板监听
 *
 * Android 10+ 限制后台应用访问剪贴板，但前台服务不受此限制。
 * 此服务通过持久通知保持前台状态，确保剪贴板监听在后台也能正常工作。
 *
 * 与 ClipboardAccessibilityService 共享去重状态（static lastClipHash），
 * 避免同一内容被重复处理。
 */
public class ClipboardForegroundService extends Service {
    private static final String TAG = "ClipFgSvc";
    private static final String CHANNEL_ID = "clipboard_monitor";
    private static final String CHANNEL_NAME = "剪贴板监听";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS_NAME = "phone_monitor_prefs";
    private static final String COUNT_KEY = "clipboard_send_count";

    private static final long POLL_INTERVAL_MS = 2000;
    private static final long BATCH_WINDOW_MS = 3000;
    private static final long NOTIFICATION_UPDATE_MS = 30000;

    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private Handler pollHandler;
    private Runnable pollRunnable;
    private Handler notifHandler;
    private Runnable notifRunnable;
    private NotificationManager notificationManager;

    private int clipCount = 0;
    private String lastPreview = "";

    private final List<String> batchBuffer = new ArrayList<>();
    private final Handler batchHandler = new Handler(Looper.getMainLooper());
    private Runnable batchRunnable;

    // 内容类型检测（与 ClipboardAccessibilityService 一致）
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://\\S+$", Pattern.DOTALL);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d[\\d\\s\\-()]{7,18}\\d$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("(\\{[\\s\\S]*\\}|function\\s|import\\s|class\\s|def\\s|const\\s|var\\s|let\\s|=>|\\bif\\s*\\(|for\\s*\\()");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(省|市|区|县|路|街|号|楼|室|大厦|广场|小区|village|street|road|ave|blvd)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENSITIVE_DIGITS = Pattern.compile("^\\d{6,20}$");

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;

        // 启动前台通知
        startForeground(NOTIFICATION_ID, buildNotification("📋 剪贴板监听中", "等待新内容..."));

        // 方式1：直接监听
        clipListener = this::processClipboard;
        clipboardManager.addPrimaryClipChangedListener(clipListener);

        // 方式2：定时轮询（兜底）
        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                processClipboard();
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);

        // 定时更新通知
        notifHandler = new Handler(Looper.getMainLooper());
        notifRunnable = new Runnable() {
            @Override
            public void run() {
                updateNotification();
                notifHandler.postDelayed(this, NOTIFICATION_UPDATE_MS);
            }
        };
        notifHandler.postDelayed(notifRunnable, NOTIFICATION_UPDATE_MS);

        Log.i(TAG, "✅ 前台剪贴板服务已启动");
        LogBus.post("🔄", "前台剪贴板服务已启动");

        return START_STICKY;
    }

    private void processClipboard() {
        try {
            long now = System.currentTimeMillis();

            // 使用共享去重状态
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;

            CharSequence rawText = clip.getItemAt(0).getText();
            if (rawText == null) {
                rawText = clip.getItemAt(0).coerceToText(this);
            }
            if (rawText == null || rawText.length() == 0) return;

            String content = rawText.toString().trim();
            if (content.isEmpty() || content.length() < 2) return;

            // MD5 去重（与 ClipboardAccessibilityService 共享）
            String hash = md5(content);
            if (ClipboardAccessibilityService.checkAndUpdateHash(hash)) {
                return; // 已处理过
            }

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

            Log.i(TAG, "📋 [FgSvc] 新内容 (" + content.length() + " chars)");

            // 更新预览
            lastPreview = content.length() > 40 ? content.substring(0, 40) + "..." : content;
            clipCount++;

            // 保存到 prefs
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString("clipboard_last_content", content.length() > 50 ?
                            content.substring(0, 50) + "..." : content)
                    .putLong("clipboard_last_time", now)
                    .apply();

            // 保存到知识库
            saveToKnowledge(content);

            // 批量发送
            addToBatch(content);

            // 更新通知
            updateNotification();

        } catch (SecurityException se) {
            Log.w(TAG, "剪贴板访问被拒（将重试）: " + se.getMessage());
            // 1秒后重试
            pollHandler.postDelayed(this::processClipboard, 1000);
        } catch (Exception e) {
            Log.e(TAG, "处理失败: " + e.getMessage(), e);
        }
    }

    private void saveToKnowledge(String content) {
        try {
            String type = ContentClassifier.classifyContent(content);
            String title = ContentClassifier.generateTitle(content, type);
            String url = ContentClassifier.extractUrl(content);

            KnowledgeDb db = KnowledgeDb.getInstance(this);
            long id = db.insertContent(title, content, url, type, "clipboard", null);
            if (id > 0) {
                LogBus.post("📚", "已保存到知识库 #" + id);
            }
        } catch (Exception e) {
            Log.e(TAG, "知识库保存失败: " + e.getMessage());
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
            sb.append("⏰ ").append(time).append(" · ").append(DeviceNames.get()).append("\n");
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
            Log.i(TAG, "📤 [FgSvc] 已提交 " + items.size() + " 条");

            for (String item : items) {
                String typeTag = detectContentType(item);
                String preview = item.length() > 80 ? item.substring(0, 80) + "..." : item;
                LogBus.post("📋", typeTag + " " + preview);
            }
        }).start();
    }

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

    // --- 通知相关 ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("后台剪贴板监听服务");
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String text) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        String text;
        if (clipCount > 0) {
            text = "已捕获 " + clipCount + " 条 · " + lastPreview;
        } else {
            text = "等待新内容...";
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification("📋 剪贴板监听中", text));
    }

    // --- 静态工具方法 ---

    /**
     * 检查前台服务是否正在运行
     */
    static boolean isRunning = false;

    public static boolean isServiceRunning() {
        return isRunning;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;

        if (clipboardManager != null && clipListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipListener);
        }
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
        if (notifHandler != null && notifRunnable != null) {
            notifHandler.removeCallbacks(notifRunnable);
        }
        if (batchRunnable != null) {
            batchHandler.removeCallbacks(batchRunnable);
        }

        Log.i(TAG, "前台剪贴板服务已停止");
        LogBus.post("🔄", "前台剪贴板服务已停止");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 应用被划掉时重启服务
        super.onTaskRemoved(rootIntent);
        Log.i(TAG, "任务被移除，尝试重启...");
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
