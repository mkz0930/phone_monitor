package com.phonemonitor.app;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.gson.Gson;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class UsageReporter {
    private static final String TAG = "UsageReporter";
    private static final String PREFS_NAME = "phone_monitor_prefs";
    private final Context context;

    public UsageReporter(Context context) {
        this.context = context.getApplicationContext();
    }

    public String collectAndSend() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String serverUrl = prefs.getString("server_url", "");
        String token = prefs.getString("token", "");

        if (serverUrl.isEmpty()) throw new Exception("Server URL not configured");
        if (token.isEmpty()) throw new Exception("Token not configured");

        // Collect usage
        Map<String, Object> report = collectUsage();

        // Send to server
        return sendReport(serverUrl, token, report);
    }

    public Map<String, Object> collectUsage() {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = context.getPackageManager();

        TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar cal = Calendar.getInstance(tz);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());

        Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startTime, endTime);
        List<Map<String, Object>> apps = new ArrayList<>();
        long totalMs = 0;

        if (statsMap != null) {
            List<UsageStats> sorted = new ArrayList<>(statsMap.values());
            Collections.sort(sorted, (a, b) ->
                    Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

            for (UsageStats stats : sorted) {
                long fg = stats.getTotalTimeInForeground();
                if (fg < 60000) continue; // skip < 1 min

                totalMs += fg;
                String pkg = stats.getPackageName();
                String appName;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    appName = pm.getApplicationLabel(ai).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    appName = pkg;
                }

                Map<String, Object> appEntry = new HashMap<>();
                appEntry.put("package", pkg);
                appEntry.put("name", appName);
                appEntry.put("foreground_ms", fg);
                apps.add(appEntry);
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("date", dateStr);
        report.put("timezone", "Asia/Shanghai");
        report.put("apps", apps);
        report.put("total_foreground_ms", totalMs);
        report.put("device", android.os.Build.MODEL);
        report.put("android_version", android.os.Build.VERSION.RELEASE);

        return report;
    }

    private String sendReport(String serverUrl, String token, Map<String, Object> report) throws Exception {
        String urlStr = serverUrl.replaceAll("/$", "") + "/report";
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            String json = new Gson().toJson(report);
            Log.d(TAG, "Sending: " + json.substring(0, Math.min(200, json.length())));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> apps = (List<Map<String, Object>>) report.get("apps");
                return String.format("Sent %d apps, total %s",
                        apps != null ? apps.size() : 0,
                        formatMs((Long) report.get("total_foreground_ms")));
            } else {
                throw new Exception("Server returned " + code);
            }
        } finally {
            conn.disconnect();
        }
    }

    private String formatMs(long ms) {
        long minutes = ms / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        return String.format("%dm", minutes);
    }
}
