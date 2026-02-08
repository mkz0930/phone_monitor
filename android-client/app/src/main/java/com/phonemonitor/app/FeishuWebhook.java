package com.phonemonitor.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 飞书 Webhook 发送工具类（统一 HTTP 逻辑）
 */
public class FeishuWebhook {
    private static final String TAG = "FeishuWebhook";
    private static final String PREFS_NAME = "phone_monitor_prefs";
    private static final int MAX_RETRIES = 2;
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    /**
     * 发送文本消息到飞书 Webhook（主 + 额外目标都发）
     * 额外目标支持：webhook URL 或 oc_ 群聊 ID（通过 Bot API）
     * @return true if at least one sent successfully
     */
    public static boolean sendText(Context context, String text) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String webhookUrl = prefs.getString("webhook_url", "");
        String extraWebhooks = prefs.getString("extra_webhooks", "");
        String appId = prefs.getString("feishu_app_id", "");
        String appSecret = prefs.getString("feishu_app_secret", "");

        boolean anyOk = false;

        // 主 webhook
        if (!webhookUrl.isEmpty()) {
            if (sendText(webhookUrl, text)) anyOk = true;
        }

        // 额外目标（逗号或换行分隔）
        if (!extraWebhooks.isEmpty()) {
            String[] targets = extraWebhooks.split("[,\\n]+");
            for (String target : targets) {
                target = target.trim();
                if (target.isEmpty()) continue;

                if (target.startsWith("oc_")) {
                    // Bot API 发送到群聊
                    if (!appId.isEmpty() && !appSecret.isEmpty()) {
                        FeishuBotApi api = new FeishuBotApi(appId, appSecret);
                        if (api.sendText(target, text)) anyOk = true;
                    } else {
                        Log.w(TAG, "Bot API 需要 App ID 和 App Secret，跳过: " + target);
                    }
                } else if (target.startsWith("http")) {
                    // Webhook 发送
                    if (sendText(target, text)) anyOk = true;
                }
            }
        }

        if (!anyOk) Log.w(TAG, "所有目标未配置或全部失败");
        return anyOk;
    }

    /**
     * 发送文本消息到指定 Webhook URL（带重试）
     */
    public static boolean sendText(String webhookUrl, String text) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    Log.i(TAG, "重试 #" + attempt);
                    Thread.sleep(1000L * attempt); // 退避
                }

                JSONObject content = new JSONObject();
                content.put("text", text);

                JSONObject body = new JSONObject();
                body.put("msg_type", "text");
                body.put("content", content);

                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);

                    byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(payload);
                    }

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        Log.i(TAG, "✅ 发送成功");
                        return true;
                    }
                    Log.w(TAG, "飞书返回: " + code);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.e(TAG, "发送失败: " + e.getMessage());
            }
        }
        Log.e(TAG, "❌ 所有重试均失败");
        return false;
    }

    /**
     * 更新发送计数器
     */
    public static void incrementSendCount(Context context, String key) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int count = prefs.getInt(key, 0);
        prefs.edit().putInt(key, count + 1).apply();
    }

    public static int getSendCount(Context context, String key) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(key, 0);
    }
}
