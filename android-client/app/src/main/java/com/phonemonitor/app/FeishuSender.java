package com.phonemonitor.app;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 采集使用数据 → 直接通过飞书 Webhook 发送到群聊
 */
public class FeishuSender {
    private static final String TAG = "FeishuSender";
    private static final String PREFS_NAME = "phone_monitor_prefs";
    private final Context context;

    public FeishuSender(Context context) {
        this.context = context.getApplicationContext();
    }

    public String collectAndSend() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String webhookUrl = prefs.getString("webhook_url", "");
        if (webhookUrl.isEmpty()) throw new Exception("Webhook URL 未配置");

        // 采集数据
        String message = buildReport();

        // 发送到飞书
        sendToFeishu(webhookUrl, message);

        return "已发送到飞书群";
    }

    private String buildReport() {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = context.getPackageManager();

        TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar cal = Calendar.getInstance(tz);
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startTime, endTime);

        StringBuilder sb = new StringBuilder();
        sb.append("📱 手机使用日报 (").append(dateStr).append(")\n");
        sb.append("━━━━━━━━━━━━━━━━━━\n\n");

        if (statsMap == null || statsMap.isEmpty()) {
            sb.append("暂无数据\n");
            return sb.toString();
        }

        List<UsageStats> sorted = new ArrayList<>(statsMap.values());
        Collections.sort(sorted, (a, b) ->
                Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

        long totalMs = 0;
        int count = 0;

        // 分类统计
        long socialMs = 0, videoMs = 0, workMs = 0, otherMs = 0;

        for (UsageStats stats : sorted) {
            long fg = stats.getTotalTimeInForeground();
            if (fg < 60000) continue; // 跳过 < 1分钟

            totalMs += fg;
            count++;
            String pkg = stats.getPackageName();
            String appName;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                appName = pm.getApplicationLabel(ai).toString();
            } catch (PackageManager.NameNotFoundException e) {
                appName = pkg;
            }

            // 分类
            String cat = categorize(pkg);
            switch (cat) {
                case "社交": socialMs += fg; break;
                case "视频": videoMs += fg; break;
                case "工作": workMs += fg; break;
                default: otherMs += fg; break;
            }

            if (count <= 10) {
                sb.append(String.format("%-2d. %s  %s\n", count, appName, MainActivity.formatMs(fg)));
            }
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━\n");
        sb.append("📊 分类统计：\n");
        if (socialMs > 0) sb.append("  💬 社交: ").append(MainActivity.formatMs(socialMs)).append("\n");
        if (videoMs > 0) sb.append("  🎬 视频: ").append(MainActivity.formatMs(videoMs)).append("\n");
        if (workMs > 0) sb.append("  💼 工作: ").append(MainActivity.formatMs(workMs)).append("\n");
        if (otherMs > 0) sb.append("  📦 其他: ").append(MainActivity.formatMs(otherMs)).append("\n");

        sb.append("\n⏱ 总计: ").append(MainActivity.formatMs(totalMs));
        sb.append(" (").append(count).append("个应用)\n");
        sb.append("📱 设备: ").append(android.os.Build.MODEL);

        return sb.toString();
    }

    private String categorize(String pkg) {
        // 社交
        if (pkg.contains("tencent.mm") || pkg.contains("tencent.mobileqq") ||
            pkg.contains("whatsapp") || pkg.contains("telegram") ||
            pkg.contains("discord") || pkg.contains("instagram") ||
            pkg.contains("twitter") || pkg.contains("weibo") ||
            pkg.contains("zhihu") || pkg.contains("lark") ||
            pkg.contains("wework") || pkg.contains("facebook")) {
            return "社交";
        }
        // 视频
        if (pkg.contains("ugc.aweme") || pkg.contains("musically") ||
            pkg.contains("youtube") || pkg.contains("bili") ||
            pkg.contains("qqlive") || pkg.contains("youku") ||
            pkg.contains("netflix") || pkg.contains("kuaishou") ||
            pkg.contains("disneyplus")) {
            return "视频";
        }
        // 工作
        if (pkg.contains("google.android.gm") || pkg.contains("outlook") ||
            pkg.contains("teams") || pkg.contains("slack") ||
            pkg.contains("notion") || pkg.contains("docs") ||
            pkg.contains("calendar") || pkg.contains("todoist")) {
            return "工作";
        }
        return "其他";
    }

    private void sendToFeishu(String webhookUrl, String text) throws Exception {
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
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Feishu webhook response: " + code);
            if (code != 200) {
                throw new Exception("飞书返回错误: " + code);
            }
        } finally {
            conn.disconnect();
        }
    }
}
