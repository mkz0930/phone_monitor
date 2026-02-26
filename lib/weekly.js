import { DateTime } from "luxon";
import { listReportDates, loadReport } from "./storage.js";

function msToHuman(ms) {
  const totalMinutes = Math.round(ms / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours <= 0) return `${minutes}m`;
  if (minutes === 0) return `${hours}h`;
  return `${hours}h ${minutes}m`;
}

export function formatWeeklyReportMessage(endDateStr) {
  const endDt = DateTime.fromISO(endDateStr, { zone: "Asia/Shanghai" });
  if (!endDt.isValid) return "Invalid date";

  const startDt = endDt.minus({ days: 6 });
  const startDateStr = startDt.toISODate();

  const allDates = listReportDates();
  const weekDates = allDates.filter(d => d >= startDateStr && d <= endDateStr);

  if (weekDates.length === 0) {
    return `📊 **Weekly App Usage (${startDateStr} to ${endDateStr})**\nNo data received for this week.`;
  }

  const dailyTotals = [];
  const appAggregates = {};
  let grandTotalMs = 0;

  for (const date of weekDates) {
    const report = loadReport(date);
    if (!report) continue;

    const totalMs = report.total_foreground_ms || (report.apps || []).reduce((sum, a) => sum + (a.foreground_ms || 0), 0);
    dailyTotals.push({ date, ms: totalMs });
    grandTotalMs += totalMs;

    if (report.apps) {
      for (const app of report.apps) {
        if (!appAggregates[app.package]) {
          appAggregates[app.package] = { name: app.name || app.package, ms: 0 };
        }
        appAggregates[app.package].ms += app.foreground_ms || 0;
      }
    }
  }

  const avgMs = grandTotalMs / weekDates.length;
  const topApps = Object.values(appAggregates)
    .sort((a, b) => b.ms - a.ms)
    .slice(0, 5);

  const lines = [`📊 **Weekly App Usage Report**`, `${startDateStr} to ${endDateStr}`, ""];

  lines.push(`**Weekly Total:** ${msToHuman(grandTotalMs)}`);
  lines.push(`**Daily Average:** ${msToHuman(avgMs)} (over ${weekDates.length} days)`);
  lines.push("");

  lines.push("**Top Apps this Week:**");
  for (const app of topApps) {
    lines.push(`• ${app.name}: ${msToHuman(app.ms)}`);
  }

  lines.push("");
  lines.push("**Daily Breakdown:**");
  for (const day of dailyTotals) {
    lines.push(`• ${day.date}: ${msToHuman(day.ms)}`);
  }

  // Chinese section
  lines.push("", `> 📊 **每周应用使用报告**`);
  lines.push(`> ${startDateStr} 至 ${endDateStr}`);
  lines.push(`> `);
  lines.push(`> **本周合计：**${msToHuman(grandTotalMs).replace('h', '小时').replace('m', '分钟')}`);
  lines.push(`> **日均时长：**${msToHuman(avgMs).replace('h', '小时').replace('m', '分钟')}（统计 ${weekDates.length} 天）`);
  lines.push(`> `);
  lines.push(`> **本周 Top 5 应用：**`);
  for (const app of topApps) {
    lines.push(`> • ${app.name}：${msToHuman(app.ms).replace('h', '小时').replace('m', '分钟')}`);
  }

  return lines.join("\n");
}
