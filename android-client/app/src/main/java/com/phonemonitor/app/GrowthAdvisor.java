package com.phonemonitor.app;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GrowthAdvisor {

    private final Context context;
    private final UsageStatsDb usageDb;
    private final GrowthGoalDb goalDb;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public GrowthAdvisor(Context context) {
        this.context = context;
        this.usageDb = UsageStatsDb.getInstance(context);
        this.goalDb = GrowthGoalDb.getInstance(context);
    }

    public AnalysisResult analyze() {
        String today = sdf.format(Calendar.getInstance().getTime());
        List<UsageStatsDb.AppUsageRecord> todayRecords = usageDb.getDailyUsage(today);
        UsageStatsDb.DailySummary todaySummary = usageDb.getDailySummary(today);

        // 7-day averages
        List<UsageStatsDb.DailySummary> recentSummaries = usageDb.getRecentSummariesForRange(today, 7);
        long avg7Day = 0;
        int validDays = 0;
        for (UsageStatsDb.DailySummary s : recentSummaries) {
            if (s.totalUsageMs > 0) {
                avg7Day += s.totalUsageMs;
                validDays++;
            }
        }
        if (validDays > 0) avg7Day /= validDays;

        long todayMs = todaySummary != null ? todaySummary.totalUsageMs : 0;
        if (todayMs == 0 && !todayRecords.isEmpty()) {
            for (UsageStatsDb.AppUsageRecord r : todayRecords) todayMs += r.usageMs;
        }

        // Category breakdown for today
        Map<String, Long> todayCategories = new HashMap<>();
        for (UsageStatsDb.AppUsageRecord r : todayRecords) {
            String cat = r.category != null && !r.category.isEmpty() ? r.category : "其他";
            todayCategories.put(cat, todayCategories.getOrDefault(cat, 0L) + r.usageMs);
        }

        // 7-day category averages
        Map<String, Long> avg7Categories = computeAvgCategories(today, 7);

        // Detect trends
        List<CategoryTrend> trends = detectTrends(todayCategories, avg7Categories);

        // Generate tips
        List<String> tips = generateTips(todayMs, avg7Day, todayCategories, trends, todayRecords);

        // Goal progress
        List<GoalProgress> goalProgresses = computeGoalProgress(todayMs, todayCategories, todayRecords);

        AnalysisResult result = new AnalysisResult();
        result.todayMs = todayMs;
        result.avg7DayMs = avg7Day;
        result.todayCategories = todayCategories;
        result.trends = trends;
        result.tips = tips;
        result.goalProgresses = goalProgresses;
        return result;
    }

    private Map<String, Long> computeAvgCategories(String endDate, int days) {
        Map<String, Long> totals = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        try { cal.setTime(sdf.parse(endDate)); } catch (Exception ignored) {}
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1));

        for (int i = 0; i < days; i++) {
            String date = sdf.format(cal.getTime());
            List<UsageStatsDb.AppUsageRecord> records = usageDb.getDailyUsage(date);
            for (UsageStatsDb.AppUsageRecord r : records) {
                String cat = r.category != null && !r.category.isEmpty() ? r.category : "其他";
                totals.put(cat, totals.getOrDefault(cat, 0L) + r.usageMs);
                counts.put(cat, counts.getOrDefault(cat, 0) + 1);
            }
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        Map<String, Long> avgs = new HashMap<>();
        for (String cat : totals.keySet()) {
            avgs.put(cat, totals.get(cat) / days);
        }
        return avgs;
    }

    private List<CategoryTrend> detectTrends(Map<String, Long> today, Map<String, Long> avg) {
        List<CategoryTrend> trends = new ArrayList<>();
        for (String cat : today.keySet()) {
            long todayVal = today.get(cat);
            long avgVal = avg.getOrDefault(cat, 0L);
            if (avgVal == 0) continue;
            double ratio = (double) todayVal / avgVal;
            String direction;
            if (ratio > 1.3) direction = "increasing";
            else if (ratio < 0.7) direction = "decreasing";
            else direction = "stable";
            trends.add(new CategoryTrend(cat, direction, ratio, todayVal, avgVal));
        }
        Collections.sort(trends, (a, b) -> Double.compare(Math.abs(b.ratio - 1.0), Math.abs(a.ratio - 1.0)));
        return trends;
    }

    private List<String> generateTips(long todayMs, long avg7Day,
                                       Map<String, Long> categories,
                                       List<CategoryTrend> trends,
                                       List<UsageStatsDb.AppUsageRecord> records) {
        List<String> tips = new ArrayList<>();

        // Overall comparison
        if (avg7Day > 0 && todayMs > 0) {
            double ratio = (double) todayMs / avg7Day;
            if (ratio > 1.3) {
                tips.add("📈 今日使用时长超出7天均值 " + pct(ratio - 1) + "，注意控制");
            } else if (ratio < 0.7) {
                tips.add("🎉 今日使用时长低于7天均值 " + pct(1 - ratio) + "，保持下去");
            } else {
                tips.add("📊 今日使用时长与7天均值持平，表现稳定");
            }
        } else if (todayMs == 0) {
            tips.add("📱 今日暂无使用数据，稍后再来查看");
            return tips;
        }

        // Category-specific tips
        for (CategoryTrend t : trends) {
            if (tips.size() >= 5) break;
            if ("increasing".equals(t.direction) && t.todayMs > 600000) {
                String catName = t.category;
                if ("社交".equals(catName)) {
                    tips.add("💬 社交类使用上升 " + pct(t.ratio - 1) + "，试试设定固定社交时间段");
                } else if ("视频".equals(catName) || "影音".equals(catName)) {
                    tips.add("🎬 视频类使用上升 " + pct(t.ratio - 1) + "，可以用阅读替代部分刷视频时间");
                } else if ("游戏".equals(catName)) {
                    tips.add("🎮 游戏时间上升 " + pct(t.ratio - 1) + "，建议设置游戏时间上限");
                } else {
                    tips.add("📱 " + catName + "类使用上升 " + pct(t.ratio - 1) + "，留意是否必要");
                }
            } else if ("decreasing".equals(t.direction) && t.avgMs > 600000) {
                tips.add("👍 " + t.category + "类使用下降 " + pct(1 - t.ratio) + "，做得不错");
            }
        }

        // Top app tip
        if (!records.isEmpty()) {
            UsageStatsDb.AppUsageRecord top = records.get(0);
            if (top.usageMs > 3600000) {
                AppDictionary.AppInfo info = AppDictionary.lookup(top.packageName);
                String name = info != null ? info.name : (top.appName != null ? top.appName : top.packageName);
                tips.add("⏰ " + name + " 使用超过1小时（" + formatDuration(top.usageMs) + "），考虑设置提醒");
            }
        }

        // Screen time tip
        if (todayMs > 6 * 3600000L) {
            tips.add("🔴 今日屏幕时间已超6小时，建议休息眼睛、活动身体");
        } else if (todayMs > 4 * 3600000L) {
            tips.add("🟡 今日屏幕时间已超4小时，适当休息");
        }

        if (tips.size() < 3) {
            tips.add("💡 坚持每天查看成长建议，养成自律好习惯");
        }

        return tips.subList(0, Math.min(tips.size(), 5));
    }

    private List<GoalProgress> computeGoalProgress(long todayMs,
                                                     Map<String, Long> categories,
                                                     List<UsageStatsDb.AppUsageRecord> records) {
        List<GoalProgress> progresses = new ArrayList<>();
        List<GrowthGoalDb.Goal> goals = goalDb.getActiveGoals();
        String today = sdf.format(Calendar.getInstance().getTime());

        for (GrowthGoalDb.Goal goal : goals) {
            int currentMinutes = 0;
            if ("total_screen_time".equals(goal.goalType)) {
                currentMinutes = (int) (todayMs / 60000);
            } else if (goal.goalType.startsWith("category_limit:")) {
                String cat = goal.goalType.substring("category_limit:".length());
                Long catMs = categories.get(cat);
                currentMinutes = catMs != null ? (int) (catMs / 60000) : 0;
            } else if (goal.goalType.startsWith("app_limit:")) {
                String pkg = goal.goalType.substring("app_limit:".length());
                for (UsageStatsDb.AppUsageRecord r : records) {
                    if (pkg.equals(r.packageName)) {
                        currentMinutes = (int) (r.usageMs / 60000);
                        break;
                    }
                }
            }

            goalDb.updateGoalCurrentValue(goal.id, currentMinutes);
            boolean met = currentMinutes <= goal.targetValue;
            goalDb.recordHistory(goal.id, today, currentMinutes, met);

            GoalProgress gp = new GoalProgress();
            gp.goal = goal;
            gp.currentMinutes = currentMinutes;
            gp.targetMinutes = goal.targetValue;
            gp.met = met;
            gp.streak = goalDb.getStreakDays(goal.id);
            progresses.add(gp);
        }
        return progresses;
    }

    private String pct(double ratio) {
        return String.format(Locale.getDefault(), "%.0f%%", ratio * 100);
    }

    private String formatDuration(long ms) {
        long minutes = ms / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    // Data classes

    public static class AnalysisResult {
        public long todayMs;
        public long avg7DayMs;
        public Map<String, Long> todayCategories;
        public List<CategoryTrend> trends;
        public List<String> tips;
        public List<GoalProgress> goalProgresses;
    }

    public static class CategoryTrend {
        public String category;
        public String direction;
        public double ratio;
        public long todayMs;
        public long avgMs;

        public CategoryTrend(String category, String direction, double ratio, long todayMs, long avgMs) {
            this.category = category;
            this.direction = direction;
            this.ratio = ratio;
            this.todayMs = todayMs;
            this.avgMs = avgMs;
        }
    }

    public static class GoalProgress {
        public GrowthGoalDb.Goal goal;
        public int currentMinutes;
        public int targetMinutes;
        public boolean met;
        public int streak;
    }
}
