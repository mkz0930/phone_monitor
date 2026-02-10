package com.phonemonitor.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 异步获取网页标题
 */
public class TitleFetcher {
    private static final String TAG = "TitleFetcher";
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_READ_BYTES = 50000; // 只读前 50KB

    public interface Callback {
        void onSuccess(String title);
        void onError(String error);
    }

    /**
     * 异步获取 URL 的标题
     */
    public static void fetch(String url, Callback callback) {
        new Thread(() -> {
            try {
                String title = fetchSync(url);
                if (title != null && !title.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(title));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("未找到标题"));
                }
            } catch (Exception e) {
                Log.e(TAG, "获取标题失败: " + url, e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    /**
     * 同步获取标题（在后台线程调用）
     */
    private static String fetchSync(String urlStr) throws Exception {
        // 处理抖音短链接（需要跟随重定向）
        if (urlStr.contains("v.douyin.com")) {
            urlStr = followRedirect(urlStr);
        }

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
        conn.setInstanceFollowRedirects(true);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("HTTP " + responseCode);
        }

        // 读取 HTML（限制大小）
        StringBuilder html = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            char[] buffer = new char[1024];
            int read;
            int totalRead = 0;
            while ((read = reader.read(buffer)) != -1 && totalRead < MAX_READ_BYTES) {
                html.append(buffer, 0, read);
                totalRead += read;
                // 如果已经找到 </title>，提前退出
                if (html.toString().contains("</title>")) {
                    break;
                }
            }
        }

        // 解析标题
        Matcher matcher = TITLE_PATTERN.matcher(html.toString());
        if (matcher.find()) {
            String title = matcher.group(1).trim();
            // 清理常见后缀
            title = title.replaceAll("\\s*[-_|]\\s*(抖音|知乎|微博|bilibili|B站|YouTube).*$", "");
            // 限制长度
            if (title.length() > 100) {
                title = title.substring(0, 100) + "…";
            }
            return title;
        }

        return null;
    }

    /**
     * 跟随重定向获取最终 URL
     */
    private static String followRedirect(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");

        int responseCode = conn.getResponseCode();
        if (responseCode == 301 || responseCode == 302) {
            String location = conn.getHeaderField("Location");
            if (location != null && !location.isEmpty()) {
                return location;
            }
        }

        return urlStr;
    }

    /**
     * 检测是否为需要解析标题的 URL
     */
    public static boolean shouldFetchTitle(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
