package com.phonemonitor.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageDashboardActivity extends AppCompatActivity {

    private static final int COLOR_CYAN = Color.parseColor("#00E5FF");
    private static final int COLOR_PURPLE = Color.parseColor("#7C4DFF");
    private static final int COLOR_PINK = Color.parseColor("#FF4081");
    private static final int COLOR_AMBER = Color.parseColor("#FFD740");
    private static final int COLOR_GREEN = Color.parseColor("#69F0AE");
    private static final int COLOR_ORANGE = Color.parseColor("#FFAB40");
    private static final int COLOR_BLUE = Color.parseColor("#448AFF");
    private static final int COLOR_RED = Color.parseColor("#FF5252");
    private static final int COLOR_TEAL = Color.parseColor("#64FFDA");
    private static final int COLOR_SURFACE = Color.parseColor("#1A1A2E");
    private static final int COLOR_TEXT = Color.parseColor("#E0E0E0");
    private static final int COLOR_TEXT_DIM = Color.parseColor("#888888");


    private LineChart lineChart;
    private PieChart pieChart;
    private TextView tvSelectedDate, tvTotalTime, tvTotalApps;
    private LinearLayout layoutTopApps;
    private FloatingActionButton fabRefresh;

    private Calendar selectedDate;
    private final SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("MMM dd, yyyy (EEE)", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage_dashboard);

        selectedDate = Calendar.getInstance();

        initViews();
        setupLineChart();
        setupPieChart();
        loadData();
    }

    private void initViews() {
        lineChart = findViewById(R.id.line_chart);
        pieChart = findViewById(R.id.pie_chart);
        tvSelectedDate = findViewById(R.id.tv_selected_date);
        tvTotalTime = findViewById(R.id.tv_total_time);
        tvTotalApps = findViewById(R.id.tv_total_apps);
        layoutTopApps = findViewById(R.id.layout_top_apps);
        fabRefresh = findViewById(R.id.fab_refresh);

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnPrev = findViewById(R.id.btn_prev_day);
        ImageButton btnNext = findViewById(R.id.btn_next_day);

        btnBack.setOnClickListener(v -> finish());

        btnPrev.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1);
            updateDateDisplay();
            loadData();
        });

        btnNext.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            if (selectedDate.before(today)) {
                selectedDate.add(Calendar.DAY_OF_YEAR, 1);
                updateDateDisplay();
                loadData();
            }
        });

        fabRefresh.setOnClickListener(v -> {
            fabRefresh.animate().rotation(fabRefresh.getRotation() + 360f).setDuration(600).start();
            loadData();
        });

        updateDateDisplay();
    }

    private void updateDateDisplay() {
        Calendar today = Calendar.getInstance();
        if (isSameDay(selectedDate, today)) {
            tvSelectedDate.setText("Today");
        } else {
            tvSelectedDate.setText(displayDateFormat.format(selectedDate.getTime()));
        }
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void setupLineChart() {
        lineChart.setBackgroundColor(Color.TRANSPARENT);
        lineChart.getDescription().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setExtraBottomOffset(8f);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(COLOR_TEXT_DIM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(10f);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(COLOR_TEXT_DIM);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#1F1F3A"));
        leftAxis.setDrawAxisLine(false);
        leftAxis.setTextSize(10f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatHours(value);
            }
        });

        lineChart.getAxisRight().setEnabled(false);

        Legend legend = lineChart.getLegend();
        legend.setEnabled(false);
    }

    private void setupPieChart() {
        pieChart.setBackgroundColor(Color.TRANSPARENT);
        pieChart.getDescription().setEnabled(false);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(COLOR_SURFACE);
        pieChart.setHoleRadius(55f);
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setTransparentCircleColor(COLOR_SURFACE);
        pieChart.setTransparentCircleAlpha(80);
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.setEntryLabelColor(COLOR_TEXT);
        pieChart.setEntryLabelTextSize(11f);
        pieChart.setCenterTextColor(COLOR_TEXT);
        pieChart.setCenterTextSize(14f);

        Legend legend = pieChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextColor(COLOR_TEXT_DIM);
        legend.setTextSize(11f);
        legend.setWordWrapEnabled(true);
    }

    private void loadData() {
        new Thread(() -> {
            UsageStatsDb db = UsageStatsDb.getInstance(this);
            String dateStr = dbDateFormat.format(selectedDate.getTime());

            // Load 7-day summaries for line chart
            List<UsageStatsDb.DailySummary> summaries = db.getRecentSummaries(7);
            Collections.reverse(summaries);

            // Load daily usage for selected date (pie chart + top apps)
            List<UsageStatsDb.AppUsageRecord> records = db.getDailyUsage(dateStr);

            // Load daily summary for selected date
            UsageStatsDb.DailySummary todaySummary = db.getDailySummary(dateStr);

            runOnUiThread(() -> {
                updateSummaryCards(todaySummary, records);
                updateLineChart(summaries);
                updatePieChart(records);
                updateTopApps(records);
            });
        }).start();
    }

    private void updateSummaryCards(UsageStatsDb.DailySummary summary,
                                     List<UsageStatsDb.AppUsageRecord> records) {
        if (summary != null) {
            tvTotalTime.setText(formatDuration(summary.totalUsageMs));
            tvTotalApps.setText(String.valueOf(summary.totalApps));
        } else if (!records.isEmpty()) {
            long total = 0;
            for (UsageStatsDb.AppUsageRecord r : records) total += r.usageMs;
            tvTotalTime.setText(formatDuration(total));
            tvTotalApps.setText(String.valueOf(records.size()));
        } else {
            tvTotalTime.setText("--");
            tvTotalApps.setText("--");
        }
    }

    private void updateLineChart(List<UsageStatsDb.DailySummary> summaries) {
        if (summaries.isEmpty()) {
            lineChart.setNoDataText("No data yet");
            lineChart.setNoDataTextColor(COLOR_TEXT_DIM);
            lineChart.invalidate();
            return;
        }

        List<Entry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();

        for (int i = 0; i < summaries.size(); i++) {
            UsageStatsDb.DailySummary s = summaries.get(i);
            float hours = s.totalUsageMs / 3600000f;
            entries.add(new Entry(i, hours));
            // Show short date label (MM/dd)
            String label = s.date.length() >= 10 ? s.date.substring(5) : s.date;
            labels.add(label);
        }

        LineDataSet dataSet = new LineDataSet(entries, "Usage");
        dataSet.setColor(COLOR_CYAN);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleColor(COLOR_CYAN);
        dataSet.setCircleRadius(4f);
        dataSet.setCircleHoleColor(COLOR_SURFACE);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(COLOR_TEXT_DIM);
        dataSet.setValueTextSize(9f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return formatHours(value);
            }
        });
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.15f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(COLOR_CYAN);
        dataSet.setFillAlpha(30);

        lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                return idx >= 0 && idx < labels.size() ? labels.get(idx) : "";
            }
        });

        lineChart.setData(new LineData(dataSet));
        lineChart.animateX(800, Easing.EaseInOutCubic);
    }

    private void updatePieChart(List<UsageStatsDb.AppUsageRecord> records) {
        if (records.isEmpty()) {
            pieChart.setNoDataText("No data for this day");
            pieChart.setNoDataTextColor(COLOR_TEXT_DIM);
            pieChart.setCenterText("");
            pieChart.invalidate();
            return;
        }

        // Aggregate by category
        Map<String, Long> categoryMap = new HashMap<>();
        long totalMs = 0;
        for (UsageStatsDb.AppUsageRecord r : records) {
            String cat = r.category != null && !r.category.isEmpty() ? r.category : "Other";
            categoryMap.put(cat, categoryMap.getOrDefault(cat, 0L) + r.usageMs);
            totalMs += r.usageMs;
        }

        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Long> e : categoryMap.entrySet()) {
            float pct = totalMs > 0 ? (e.getValue() * 100f / totalMs) : 0;
            if (pct >= 2f) { // Only show categories >= 2%
                entries.add(new PieEntry(pct, e.getKey()));
            }
        }

        if (entries.isEmpty()) {
            entries.add(new PieEntry(100f, "Other"));
        }

        int[] colors = {COLOR_CYAN, COLOR_PURPLE, COLOR_PINK, COLOR_AMBER,
                COLOR_GREEN, COLOR_ORANGE, COLOR_BLUE, COLOR_RED, COLOR_TEAL};

        PieDataSet dataSet = new PieDataSet(entries, "");
        List<Integer> colorList = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            colorList.add(colors[i % colors.length]);
        }
        dataSet.setColors(colorList);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(6f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(11f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f%%", value);
            }
        });

        pieChart.setCenterText(formatDuration(totalMs));
        pieChart.setData(new PieData(dataSet));
        pieChart.animateY(800, Easing.EaseInOutCubic);
    }

    private void updateTopApps(List<UsageStatsDb.AppUsageRecord> records) {
        layoutTopApps.removeAllViews();

        int limit = Math.min(records.size(), 8);
        long maxMs = records.isEmpty() ? 1 : records.get(0).usageMs;

        for (int i = 0; i < limit; i++) {
            UsageStatsDb.AppUsageRecord r = records.get(i);
            if (r.usageMs < 60000) continue; // skip < 1 min

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 8, 0, 8);

            // Rank number
            TextView tvRank = new TextView(this);
            tvRank.setText(String.valueOf(i + 1));
            tvRank.setTextColor(i < 3 ? COLOR_CYAN : COLOR_TEXT_DIM);
            tvRank.setTextSize(14f);
            tvRank.setWidth(dpToPx(24));
            row.addView(tvRank);

            // App name + bar container
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // App name row
            LinearLayout nameRow = new LinearLayout(this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView tvName = new TextView(this);
            tvName.setText(r.appName != null ? r.appName : r.packageName);
            tvName.setTextColor(COLOR_TEXT);
            tvName.setTextSize(13f);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            nameRow.addView(tvName);

            TextView tvTime = new TextView(this);
            tvTime.setText(formatDuration(r.usageMs));
            tvTime.setTextColor(COLOR_TEXT_DIM);
            tvTime.setTextSize(12f);
            nameRow.addView(tvTime);

            col.addView(nameRow);

            // Progress bar
            View bar = new View(this);
            float ratio = maxMs > 0 ? (float) r.usageMs / maxMs : 0;
            int barWidth = (int) (ratio * dpToPx(240));
            bar.setLayoutParams(new LinearLayout.LayoutParams(Math.max(barWidth, dpToPx(4)), dpToPx(3)));
            int barColor = i < 3 ? COLOR_CYAN : COLOR_PURPLE;
            bar.setBackgroundColor(barColor);
            bar.setAlpha(0.6f + 0.4f * ratio);
            LinearLayout.LayoutParams barParams = (LinearLayout.LayoutParams) bar.getLayoutParams();
            barParams.topMargin = dpToPx(4);
            col.addView(bar);

            row.addView(col);
            layoutTopApps.addView(row);
        }

        if (records.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No app usage data");
            empty.setTextColor(COLOR_TEXT_DIM);
            empty.setTextSize(13f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dpToPx(20), 0, dpToPx(20));
            layoutTopApps.addView(empty);
        }
    }

    // Utility methods

    private String formatDuration(long ms) {
        long minutes = ms / 60000;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String formatHours(float hours) {
        if (hours >= 1f) return String.format(Locale.getDefault(), "%.1fh", hours);
        return String.format(Locale.getDefault(), "%.0fm", hours * 60);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
