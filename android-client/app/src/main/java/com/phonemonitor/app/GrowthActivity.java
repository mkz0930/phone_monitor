package com.phonemonitor.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GrowthActivity extends AppCompatActivity {

    private static final int COLOR_CYAN = Color.parseColor("#00FBFF");
    private static final int COLOR_PURPLE = Color.parseColor("#9D7AFF");
    private static final int COLOR_SURFACE = Color.parseColor("#1A1A2E");
    private static final int COLOR_TEXT = Color.parseColor("#E0E0E0");
    private static final int COLOR_TEXT_DIM = Color.parseColor("#888888");
    private static final int COLOR_GREEN = Color.parseColor("#00FF88");
    private static final int COLOR_RED = Color.parseColor("#FF3333");
    private static final int COLOR_AMBER = Color.parseColor("#FFD700");

    private ProgressBar progressGoal, progressBar;
    private TextView tvGoalStatus, tvGoalDetail, tvTodaySummary;
    private LinearLayout layoutTips, layoutTrends, layoutGoals;
    private Button btnAddGoal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_growth);

        initViews();
        loadData();
    }

    // PLACEHOLDER_INIT_VIEWS

    private void initViews() {
        progressGoal = findViewById(R.id.progress_goal);
        progressBar = findViewById(R.id.progress_bar);
        tvGoalStatus = findViewById(R.id.tv_goal_status);
        tvGoalDetail = findViewById(R.id.tv_goal_detail);
        tvTodaySummary = findViewById(R.id.tv_today_summary);
        layoutTips = findViewById(R.id.layout_tips);
        layoutTrends = findViewById(R.id.layout_trends);
        layoutGoals = findViewById(R.id.layout_goals);
        btnAddGoal = findViewById(R.id.btn_add_goal);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        btnAddGoal.setOnClickListener(v -> showAddGoalDialog());
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            GrowthAdvisor advisor = new GrowthAdvisor(this);
            GrowthAdvisor.AnalysisResult result = advisor.analyze();
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                displayResult(result);
            });
        }).start();
    }

    private void displayResult(GrowthAdvisor.AnalysisResult result) {
        // Summary
        String summary = String.format(Locale.getDefault(),
                "今日 %s | 7天均值 %s",
                formatDuration(result.todayMs), formatDuration(result.avg7DayMs));
        tvTodaySummary.setText(summary);

        // Goal progress (show first active goal)
        displayGoalProgress(result.goalProgresses);

        // Tips
        displayTips(result.tips);

        // Trends
        displayTrends(result.trends);

        // Goals list
        displayGoalsList();
    }

    // PLACEHOLDER_DISPLAY_METHODS

    private void displayGoalProgress(List<GrowthAdvisor.GoalProgress> progresses) {
        if (progresses.isEmpty()) {
            progressGoal.setProgress(0);
            tvGoalStatus.setText("暂无目标，点击下方添加");
            tvGoalDetail.setText("");
            return;
        }

        GrowthAdvisor.GoalProgress gp = progresses.get(0);
        int pct = gp.targetMinutes > 0 ? Math.min(100, gp.currentMinutes * 100 / gp.targetMinutes) : 0;
        progressGoal.setProgress(pct);

        String goalLabel = formatGoalType(gp.goal.goalType);
        if (gp.met) {
            tvGoalStatus.setTextColor(COLOR_GREEN);
            tvGoalStatus.setText("✅ " + goalLabel + " 达标");
        } else if (pct > 80) {
            tvGoalStatus.setTextColor(COLOR_AMBER);
            tvGoalStatus.setText("⚠️ " + goalLabel + " 接近上限");
        } else {
            tvGoalStatus.setTextColor(COLOR_PURPLE);
            tvGoalStatus.setText(goalLabel);
        }

        String detail = String.format(Locale.getDefault(),
                "当前 %dm / 目标 %dm (%d%%)",
                gp.currentMinutes, gp.targetMinutes, pct);
        if (gp.streak > 0) {
            detail += " | 连续达标 " + gp.streak + "天";
        }
        tvGoalDetail.setText(detail);
    }

    private void displayTips(List<String> tips) {
        layoutTips.removeAllViews();
        for (String tip : tips) {
            TextView tv = new TextView(this);
            tv.setText(tip);
            tv.setTextColor(COLOR_TEXT);
            tv.setTextSize(13f);
            tv.setPadding(0, dpToPx(4), 0, dpToPx(4));
            layoutTips.addView(tv);
        }
        if (tips.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("暂无建议，稍后再来");
            tv.setTextColor(COLOR_TEXT_DIM);
            tv.setTextSize(13f);
            layoutTips.addView(tv);
        }
    }

    // PLACEHOLDER_TRENDS_AND_GOALS

    private void displayTrends(List<GrowthAdvisor.CategoryTrend> trends) {
        layoutTrends.removeAllViews();
        if (trends.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("暂无趋势数据");
            tv.setTextColor(COLOR_TEXT_DIM);
            tv.setTextSize(13f);
            layoutTrends.addView(tv);
            return;
        }

        for (GrowthAdvisor.CategoryTrend t : trends) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dpToPx(4), 0, dpToPx(4));

            TextView tvCat = new TextView(this);
            tvCat.setText(t.category);
            tvCat.setTextColor(COLOR_TEXT);
            tvCat.setTextSize(13f);
            tvCat.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvCat);

            TextView tvDir = new TextView(this);
            String arrow;
            int color;
            if ("increasing".equals(t.direction)) {
                arrow = "↑ +" + String.format(Locale.getDefault(), "%.0f%%", (t.ratio - 1) * 100);
                color = COLOR_RED;
            } else if ("decreasing".equals(t.direction)) {
                arrow = "↓ -" + String.format(Locale.getDefault(), "%.0f%%", (1 - t.ratio) * 100);
                color = COLOR_GREEN;
            } else {
                arrow = "→ 稳定";
                color = COLOR_TEXT_DIM;
            }
            tvDir.setText(arrow);
            tvDir.setTextColor(color);
            tvDir.setTextSize(13f);
            row.addView(tvDir);

            TextView tvTime = new TextView(this);
            tvTime.setText("  " + formatDuration(t.todayMs));
            tvTime.setTextColor(COLOR_TEXT_DIM);
            tvTime.setTextSize(12f);
            row.addView(tvTime);

            layoutTrends.addView(row);
        }
    }

    private void displayGoalsList() {
        layoutGoals.removeAllViews();
        GrowthGoalDb db = GrowthGoalDb.getInstance(this);
        List<GrowthGoalDb.Goal> goals = db.getActiveGoals();

        for (GrowthGoalDb.Goal goal : goals) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dpToPx(6), 0, dpToPx(6));

            TextView tvLabel = new TextView(this);
            tvLabel.setText(formatGoalType(goal.goalType) + ": " + goal.targetValue + "分钟");
            tvLabel.setTextColor(COLOR_TEXT);
            tvLabel.setTextSize(13f);
            tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(tvLabel);

            TextView btnEdit = new TextView(this);
            btnEdit.setText("编辑");
            btnEdit.setTextColor(COLOR_CYAN);
            btnEdit.setTextSize(12f);
            btnEdit.setPadding(dpToPx(8), 0, dpToPx(8), 0);
            btnEdit.setOnClickListener(v -> showEditGoalDialog(goal));
            row.addView(btnEdit);

            TextView btnDel = new TextView(this);
            btnDel.setText("删除");
            btnDel.setTextColor(COLOR_RED);
            btnDel.setTextSize(12f);
            btnDel.setPadding(dpToPx(8), 0, 0, 0);
            btnDel.setOnClickListener(v -> {
                db.deleteGoal(goal.id);
                loadData();
            });
            row.addView(btnDel);

            layoutGoals.addView(row);
        }
    }

    // PLACEHOLDER_DIALOGS

    private void showAddGoalDialog() {
        String[] types = {"⏱️ 每日总屏幕时间", "📂 分类限制", "📱 单个应用限制"};

        new MaterialAlertDialogBuilder(this, R.style.Theme_PhoneMonitor_Dialog)
                .setTitle("选择目标类型")
                .setItems(types, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showTargetInputDialog("total_screen_time", "每日总屏幕时间");
                            break;
                        case 1:
                            showCategoryPickerDialog();
                            break;
                        case 2:
                            showAppPickerDialog();
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCategoryPickerDialog() {
        Map<String, String> cats = AppDictionary.getAllCategories();
        String[] labels = new String[cats.size()];
        String[] keys = new String[cats.size()];
        int i = 0;
        for (Map.Entry<String, String> e : cats.entrySet()) {
            labels[i] = e.getValue() + " " + e.getKey();
            keys[i] = "category_limit:" + e.getKey();
            i++;
        }

        new MaterialAlertDialogBuilder(this, R.style.Theme_PhoneMonitor_Dialog)
                .setTitle("选择分类")
                .setItems(labels, (dialog, which) ->
                        showTargetInputDialog(keys[which], labels[which] + "限制"))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAppPickerDialog() {
        new Thread(() -> {
            // Collect unique apps from last 7 days
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            LinkedHashMap<String, String> appMap = new LinkedHashMap<>();
            UsageStatsDb db = UsageStatsDb.getInstance(this);
            for (int d = 0; d < 7; d++) {
                String date = sdf.format(cal.getTime());
                List<UsageStatsDb.AppUsageRecord> records = db.getDailyUsage(date);
                for (UsageStatsDb.AppUsageRecord r : records) {
                    if (!appMap.containsKey(r.packageName)) {
                        AppDictionary.AppInfo info = AppDictionary.lookup(r.packageName);
                        String label = info != null
                                ? info.emoji + " " + info.name
                                : "📦 " + r.appName;
                        appMap.put(r.packageName, label);
                    }
                }
                cal.add(Calendar.DAY_OF_YEAR, -1);
            }
            List<String> pkgs = new ArrayList<>(appMap.keySet());
            List<String> labels = new ArrayList<>(appMap.values());
            runOnUiThread(() -> showAppListDialog(pkgs, labels));
        }).start();
    }

    private void showAppListDialog(List<String> allPkgs, List<String> allLabels) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), 0);

        EditText search = new EditText(this);
        search.setHint("🔍 搜索应用...");
        search.setTextColor(COLOR_TEXT);
        search.setHintTextColor(COLOR_TEXT_DIM);
        search.setSingleLine(true);
        search.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        container.addView(search);

        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, dpToPx(8), 0, 0);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(300)));
        scrollView.addView(listLayout);
        container.addView(scrollView);

        // Dialog holder for dismiss from click listeners
        final androidx.appcompat.app.AlertDialog[] dlgHolder = new androidx.appcompat.app.AlertDialog[1];

        // Build initial list
        Runnable[] refreshList = new Runnable[1];
        refreshList[0] = () -> {
            listLayout.removeAllViews();
            String query = search.getText().toString().toLowerCase(Locale.getDefault());
            for (int i = 0; i < allLabels.size(); i++) {
                if (!query.isEmpty() && !allLabels.get(i).toLowerCase(Locale.getDefault()).contains(query)
                        && !allPkgs.get(i).toLowerCase(Locale.getDefault()).contains(query)) {
                    continue;
                }
                final int idx = i;
                TextView tv = new TextView(this);
                tv.setText(allLabels.get(i));
                tv.setTextColor(COLOR_TEXT);
                tv.setTextSize(14f);
                tv.setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10));
                tv.setOnClickListener(v -> {
                    if (dlgHolder[0] != null) dlgHolder[0].dismiss();
                    showTargetInputDialog("app_limit:" + allPkgs.get(idx),
                            allLabels.get(idx) + " 限制");
                });
                listLayout.addView(tv);
            }
            if (listLayout.getChildCount() == 0) {
                TextView empty = new TextView(this);
                empty.setText("无匹配应用");
                empty.setTextColor(COLOR_TEXT_DIM);
                empty.setTextSize(13f);
                empty.setPadding(dpToPx(8), dpToPx(10), 0, 0);
                listLayout.addView(empty);
            }
        };
        refreshList[0].run();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refreshList[0].run(); }
        });

        dlgHolder[0] = new MaterialAlertDialogBuilder(this, R.style.Theme_PhoneMonitor_Dialog)
                .setTitle("选择应用")
                .setView(container)
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTargetInputDialog(String goalType, String label) {
        EditText input = new EditText(this);
        input.setHint("目标分钟数");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_TEXT_DIM);
        input.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        new MaterialAlertDialogBuilder(this, R.style.Theme_PhoneMonitor_Dialog)
                .setTitle(label + " - 设置上限")
                .setMessage("请输入每日目标上限（分钟）")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    try {
                        int target = Integer.parseInt(text);
                        if (target <= 0) {
                            Toast.makeText(this, "请输入正整数", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        GrowthGoalDb db = GrowthGoalDb.getInstance(this);
                        GrowthGoalDb.Goal existing = db.getGoalByType(goalType);
                        if (existing != null) {
                            db.updateGoal(existing.id, target);
                        } else {
                            db.insertGoal(goalType, target, "分钟");
                        }
                        loadData();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showEditGoalDialog(GrowthGoalDb.Goal goal) {
        EditText input = new EditText(this);
        input.setHint("目标分钟数");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(goal.targetValue));
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_TEXT_DIM);
        input.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        new MaterialAlertDialogBuilder(this, R.style.Theme_PhoneMonitor_Dialog)
                .setTitle("编辑: " + formatGoalType(goal.goalType))
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    try {
                        int target = Integer.parseInt(text);
                        if (target <= 0) return;
                        GrowthGoalDb.getInstance(this).updateGoal(goal.id, target);
                        loadData();
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String formatGoalType(String goalType) {
        if ("total_screen_time".equals(goalType)) return "⏱️ 每日屏幕时间";
        if (goalType.startsWith("category_limit:")) {
            String cat = goalType.substring("category_limit:".length());
            return AppDictionary.getCategoryEmoji(cat) + " " + cat + "类限制";
        }
        if (goalType.startsWith("app_limit:")) {
            String pkg = goalType.substring("app_limit:".length());
            AppDictionary.AppInfo info = AppDictionary.lookup(pkg);
            if (info != null) return info.emoji + " " + info.name + "限制";
            return "📦 " + pkg + "限制";
        }
        return goalType;
    }

    private String formatDuration(long ms) {
        long minutes = ms / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
