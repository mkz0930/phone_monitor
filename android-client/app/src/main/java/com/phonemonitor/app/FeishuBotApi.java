package com.phonemonitor.app;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 飞书 Bot API 发送工具（通过 tenant_access_token 发消息到群聊）
 */
public class FeishuBotApi {
    private static final String TAG = "FeishuBotApi";
    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String MESSAGE_URL = "https://open.feishu.cn/open-apis/im/v1/messages";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    // Token 缓存（2小时有效，提前5分钟刷新）
    private static String cachedToken = null;
    private static long tokenExpireTime = 0;

    private final String appId;
    private final String appSecret;

    public FeishuBotApi(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
    }

    /**
     * 发送文本消息到群聊
     * @param chatId 群聊 ID（oc_ 开头）
     * @param text 消息内容
     * @return true if sent successfully
     */
    public boolean sendText(String chatId, String text) {
        try {
            String token = getTenantAccessToken();
            if (token == null) {
                Log.e(TAG, "❌ 获取 token 失败");
                return false;
            }

            // 构建消息体
            JSONObject content = new JSONObject();
            content.put("text", text);

            JSONObject body = new JSONObject();
            body.put("receive_id", chatId);
            body.put("msg_type", "text");
            body.put("content", content.toString());

            String urlStr = MESSAGE_URL + "?receive_id_type=chat_id";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int code = conn.getResponseCode();
                String resp = readResponse(conn);

                if (code == 200) {
                    JSONObject respJson = new JSONObject(resp);
                    int bizCode = respJson.optInt("code", -1);
                    if (bizCode == 0) {
                        Log.i(TAG, "✅ Bot API 发送成功 → " + chatId);
                        return true;
                    }
                    Log.w(TAG, "飞书业务错误: code=" + bizCode + " msg=" + respJson.optString("msg"));
                } else {
                    Log.w(TAG, "HTTP " + code + ": " + resp);
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Bot API 发送失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 获取 tenant_access_token（带缓存）
     */
    private synchronized String getTenantAccessToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return cachedToken;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("app_id", appId);
            body.put("app_secret", appSecret);

            URL url = new URL(TOKEN_URL);
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
                String resp = readResponse(conn);

                if (code == 200) {
                    JSONObject respJson = new JSONObject(resp);
                    int bizCode = respJson.optInt("code", -1);
                    if (bizCode == 0) {
                        cachedToken = respJson.getString("tenant_access_token");
                        int expire = respJson.optInt("expire", 7200);
                        // 提前5分钟刷新
                        tokenExpireTime = System.currentTimeMillis() + (expire - 300) * 1000L;
                        Log.i(TAG, "✅ Token 获取成功，有效期 " + expire + "s");
                        return cachedToken;
                    }
                    Log.e(TAG, "Token 获取失败: " + respJson.optString("msg"));
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Token 请求异常: " + e.getMessage());
        }

        cachedToken = null;
        tokenExpireTime = 0;
        return null;
    }

    private static String readResponse(HttpURLConnection conn) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                            StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
