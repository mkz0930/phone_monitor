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
     * 发送文本消息到飞书 Webhook（带重试）
     * @return true if sent successfully
     */
    public static boolean sendText(Context context, String text) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String webhookUrl = prefs.getString("webhook_url", "");
        if (webhookUrl.isEmpty()) {
            Log.w(TAG, "Webhook 未配置");
            return false;
        }
        return sendText(webhookUrl, text);
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
