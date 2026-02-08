package com.phonemonitor.app;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
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

        String message = buildReport();
        sendToFeishu(webhookUrl, message);
        return "已发送到飞书群";
    }

    private String buildReport() {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = context.getPackageManager();

        TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar cal = Calendar.getInstance(tz);
        String dateStr = new SimpleDateFormat("yyyy-MM-dd (E)", Locale.CHINA).format(cal.getTime());

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startTime, endTime);

        StringBuilder sb = new StringBuilder();
        sb.append("📱 手机使用日报\n");
        sb.append("📅 ").append(dateStr).append("\n");
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

        // 分类统计 (保持插入顺序)
        LinkedHashMap<String, Long> categoryMs = new LinkedHashMap<>();

        for (UsageStats stats : sorted) {
            long fg = stats.getTotalTimeInForeground();
            if (fg < 60000) continue; // 跳过 < 1分钟

            totalMs += fg;
            count++;
            String pkg = stats.getPackageName();

            // 用字典查名字，查不到用系统 label，再查不到用包名最后一段
            String appName;
            String emoji = "";
            AppDictionary.AppInfo dictInfo = AppDictionary.lookup(pkg);
            if (dictInfo != null) {
                appName = dictInfo.name;
                emoji = dictInfo.emoji + " ";
            } else {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    appName = pm.getApplicationLabel(ai).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    // 取包名最后一段作为可读名
                    String[] parts = pkg.split("\\.");
                    appName = parts[parts.length - 1];
                }
            }

            // 分类统计
            String cat = dictInfo != null ? dictInfo.category : AppDictionary.getCategory(pkg);
            categoryMs.merge(cat, fg, Long::sum);

            // Top 10 列表
            if (count <= 10) {
                String rank = count <= 3 ?
                        new String[]{"🥇", "🥈", "🥉"}[count - 1] :
                        String.format("%2d.", count);
                sb.append(String.format("%s %s%s  %s\n",
                        rank, emoji, appName, MainActivity.formatMs(fg)));
            }
        }

        // 分类汇总
        sb.append("\n━━━━━━━━━━━━━━━━━━\n");
        sb.append("📊 分类统计：\n");
        // 按时长排序
        List<Map.Entry<String, Long>> catList = new ArrayList<>(categoryMs.entrySet());
        catList.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Long> entry : catList) {
            String catEmoji = AppDictionary.getCategoryEmoji(entry.getKey());
            sb.append(String.format("  %s %s: %s\n",
                    catEmoji, entry.getKey(), MainActivity.formatMs(entry.getValue())));
        }

        // 总计
        long totalHours = totalMs / 3600000;
        sb.append("\n⏱ 总计: ").append(MainActivity.formatMs(totalMs));
        sb.append(" (").append(count).append("个应用)");
        if (totalHours >= 5) {
            sb.append(" ⚠️ 使用较多");
        } else if (totalHours <= 1) {
            sb.append(" ✅ 控制良好");
        }
        sb.append("\n📱 ").append(android.os.Build.MODEL);

        return sb.toString();
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
