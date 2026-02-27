package com.phonemonitor.app;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

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
 * 采集使用数据 → 通过飞书 Webhook 发送到群聊
 */
public class FeishuSender {
    private static final String TAG = "FeishuSender";
    private final Context context;

    public FeishuSender(Context context) {
        this.context = context.getApplicationContext();
    }

    public String collectAndSend() throws Exception {
        String message = buildReport();
        boolean ok = FeishuWebhook.sendText(context, message);
        if (!ok) throw new Exception("发送失败（已重试）");
        FeishuWebhook.incrementSendCount(context, "report_send_count");
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

        // 统计解锁次数
        int unlockCount = countUnlocks(usm, startTime, endTime);

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
        LinkedHashMap<String, Long> categoryMs = new LinkedHashMap<>();

        for (UsageStats stats : sorted) {
            long fg = stats.getTotalTimeInForeground();
            if (fg < 60000) continue;

            totalMs += fg;
            count++;
            String pkg = stats.getPackageName();

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
                    String[] parts = pkg.split("\\.");
                    appName = parts[parts.length - 1];
                }
            }

            // 分类统计
            String cat = dictInfo != null ? dictInfo.category : AppDictionary.getCategory(pkg);
            categoryMs.merge(cat, fg, Long::sum);

            // Top 10
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
        List<Map.Entry<String, Long>> catList = new ArrayList<>(categoryMs.entrySet());
        catList.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Long> entry : catList) {
            String catEmoji = AppDictionary.getCategoryEmoji(entry.getKey());
            sb.append(String.format("  %s %s: %s\n",
                    catEmoji, entry.getKey(), MainActivity.formatMs(entry.getValue())));
        }

        // 每小时时间线
        sb.append("\n━━━━━━━━━━━━━━━━━━\n");
        sb.append("🕐 每小时明细：\n");
        buildHourlyTimeline(sb, usm, startTime);

        // 总计
        long totalHours = totalMs / 3600000;
        sb.append("\n⏱ 总计: ").append(MainActivity.formatMs(totalMs));
        sb.append(" (").append(count).append("个应用)");
        if (totalHours >= 5) {
            sb.append(" ⚠️ 使用较多");
        } else if (totalHours <= 1) {
            sb.append(" ✅ 控制良好");
        }

        // 解锁次数
        sb.append("\n🔓 解锁: ").append(unlockCount).append("次");
        if (unlockCount > 100) {
            sb.append(" ⚠️");
        } else if (unlockCount <= 30) {
            sb.append(" ✅");
        }

        sb.append("\n📱 ").append(DeviceNames.get());

        return sb.toString();
    }

    /**
     * 构建每小时使用明细时间线
     */
    private void buildHourlyTimeline(StringBuilder sb, UsageStatsManager usm, long dayStart) {
        Map<Integer, Map<String, Long>> hourly =
                EntertainmentAlertReceiver.queryDailyHourlyBreakdown(usm, dayStart);

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        int currentHour = cal.get(Calendar.HOUR_OF_DAY);

        for (int h = 0; h <= currentHour; h++) {
            Map<String, Long> apps = hourly.get(h);
            if (apps == null || apps.isEmpty()) continue;

            // Sort by usage desc
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(apps.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            // Only show hours with >1min total usage
            long hourTotal = 0;
            for (Map.Entry<String, Long> e : sorted) hourTotal += e.getValue();
            if (hourTotal < 60000) continue;

            sb.append(String.format("  %02d:00-%02d:00: ", h, h + 1));
            int shown = 0;
            for (Map.Entry<String, Long> entry : sorted) {
                long min = entry.getValue() / 60000;
                if (min < 1) continue;
                if (shown > 0) sb.append(", ");
                AppDictionary.AppInfo info = AppDictionary.lookup(entry.getKey());
                String name = info != null ? info.name : entry.getKey();
                sb.append(name).append(" ").append(min).append("min");
                shown++;
                if (shown >= 3) break; // Top 3 per hour
            }
            sb.append("\n");
        }
    }

    /**
     * 统计今日解锁次数（通过 KEYGUARD_HIDDEN 事件）
     */
    private int countUnlocks(UsageStatsManager usm, long startTime, long endTime) {
        try {
            UsageEvents events = usm.queryEvents(startTime, endTime);
            int count = 0;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.KEYGUARD_HIDDEN) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            Log.w(TAG, "无法统计解锁次数: " + e.getMessage());
            return -1;
        }
    }
}
