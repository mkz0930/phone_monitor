package com.phonemonitor.app;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 应用使用统计采集器
 * 每天 23:59 自动采集当天的应用使用数据
 */
public class UsageStatsCollector {
    private static final String TAG = "UsageStatsCollector";
    private final Context context;
    private final UsageStatsDb db;
    private final PackageManager pm;

    public UsageStatsCollector(Context context) {
        this.context = context.getApplicationContext();
        this.db = UsageStatsDb.getInstance(context);
        this.pm = context.getPackageManager();
    }

    /**
     * 采集今天的使用统计
     */
    public void collectTodayStats() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        collectStatsForDate(today);
    }

    /**
     * 采集最近 N 天的使用统计
     */
    public void collectRecentHistory(int days) {
        Log.i(TAG, "📊 开始采集最近 " + days + " 天的历史数据");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        
        for (int i = 0; i < days; i++) {
            String date = sdf.format(cal.getTime());
            collectStatsForDate(date);
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
    }

    /**
     * 采集指定日期的使用统计
     */
    public void collectStatsForDate(String date) {
        Log.i(TAG, "📊 开始采集使用统计: " + date);

        // 计算时间范围（当天 00:00 到 23:59）
        Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            cal.setTime(sdf.parse(date));
        } catch (Exception e) {
            Log.e(TAG, "日期解析失败: " + date, e);
            return;
        }

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startTime = cal.getTimeInMillis();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        long endTime = cal.getTimeInMillis();

        // 获取使用统计
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            Log.e(TAG, "❌ UsageStatsManager 不可用");
            return;
        }

        // 针对“今天”使用更准确的聚合方法
        List<UsageStats> statsList;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (date.equals(today)) {
            Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startTime, System.currentTimeMillis());
            statsList = new ArrayList<>(statsMap.values());
        } else {
            statsList = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
            );
        }

        if (statsList == null || statsList.isEmpty()) {
            Log.w(TAG, "⚠️ 未获取到使用统计数据（可能缺少权限）");
            return;
        }

        // 统计数据
        Map<String, CategoryStats> categoryMap = new HashMap<>();
        long totalUsageMs = 0;
        int totalApps = 0;
        String topApp = "";
        long topAppUsage = 0;

        for (UsageStats stats : statsList) {
            String packageName = stats.getPackageName();
            long usageMs = stats.getTotalTimeInForeground();

            // 过滤时长为 0 的应用和不需要关注的系统应用
            if (usageMs == 0 || isExcludedSystemApp(packageName)) {
                continue;
            }

            // 获取应用名称和分类
            String appName = getAppName(packageName);
            String category = AppDictionary.getCategory(packageName);

            // 保存到数据库
            db.upsertDailyUsage(
                    date,
                    packageName,
                    appName,
                    category,
                    usageMs,
                    0 // launchCount 暂不统计
            );

            // 累计统计
            totalUsageMs += usageMs;
            totalApps++;

            // 记录 Top App
            if (usageMs > topAppUsage) {
                topAppUsage = usageMs;
                topApp = appName;
            }

            // 分类统计
            CategoryStats catStats = categoryMap.get(category);
            if (catStats == null) {
                catStats = new CategoryStats(category);
                categoryMap.put(category, catStats);
            }
            catStats.totalMs += usageMs;
            catStats.appCount++;
        }

        // 找出 Top Category
        String topCategory = "";
        long topCategoryUsage = 0;
        for (CategoryStats stats : categoryMap.values()) {
            if (stats.totalMs > topCategoryUsage) {
                topCategoryUsage = stats.totalMs;
                topCategory = stats.category;
            }
        }

        // 保存每日汇总
        db.insertDailySummary(date, totalUsageMs, totalApps, topApp, topCategory);

        Log.i(TAG, "✅ 采集完成: " + totalApps + " 个应用, 总时长 " + (totalUsageMs / 60000) + " 分钟");
    }

    /**
     * 获取应用名称
     */
    private String getAppName(String packageName) {
        // 先查字典
        AppDictionary.AppInfo dictInfo = AppDictionary.lookup(packageName);
        if (dictInfo != null) return dictInfo.name;

        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    /**
     * 判断是否为需要排除的系统应用
     */
    private boolean isExcludedSystemApp(String packageName) {
        // 如果在字典中，说明是我们关注的应用，不排除（即使它是系统应用，如 Chrome）
        if (AppDictionary.lookup(packageName) != null) {
            return false;
        }

        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            // 排除大部分系统应用，但保留安装在 /data 分区的应用（用户安装的）
            // 以及排除掉一些明显的系统组件
            if (packageName.equals("android") || 
                packageName.startsWith("com.android.systemui") ||
                packageName.startsWith("com.google.android.permissioncontroller")) {
                return true;
            }
            
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && 
                   (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 分类统计辅助类
     */
    private static class CategoryStats {
        String category;
        long totalMs;
        int appCount;

        CategoryStats(String category) {
            this.category = category;
        }
    }
}
