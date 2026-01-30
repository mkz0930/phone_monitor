import { DateTime } from "luxon";

function msToHuman(ms) {
  const totalMinutes = Math.round(ms / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours <= 0) return `${minutes}m`;
  if (minutes === 0) return `${hours}h`;
  return `${hours}h ${minutes}m`;
}

function msToHumanZh(ms) {
  const totalMinutes = Math.round(ms / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours <= 0) return `${minutes} 分钟`;
  if (minutes === 0) return `${hours} 小时`;
  return `${hours} 小时 ${minutes} 分钟`;
}

export function formatReportMessage(report, opts = {}) {
  const topN = opts.topN ?? 10;
  const dateStr = report.date;
  const apps = Array.isArray(report.apps) ? report.apps : [];
  const sorted = [...apps].sort((a, b) => (b.foreground_ms || 0) - (a.foreground_ms || 0));
  const top = sorted.slice(0, topN);
  const totalMs =
    typeof report.total_foreground_ms === "number"
      ? report.total_foreground_ms
      : apps.reduce((sum, a) => sum + (a.foreground_ms || 0), 0);

  const titleEn = `Daily App Usage (${dateStr})`;
  const titleZh = `每日应用使用统计（${dateStr}）`;

  const linesEn = [titleEn, "Top apps:"];
  for (const app of top) {
    const name = app.name || app.package || "(unknown)";
    linesEn.push(`- ${name}: ${msToHuman(app.foreground_ms || 0)}`);
  }
  linesEn.push(`Total: ${msToHuman(totalMs)}`);

  const linesZh = [titleZh, "Top 应用："];
  for (const app of top) {
    const name = app.name || app.package || "(未知)";
    linesZh.push(`- ${name}：${msToHumanZh(app.foreground_ms || 0)}`);
  }
  linesZh.push(`合计：${msToHumanZh(totalMs)}`);

  return `${linesEn.join("\n")}\n\n${linesZh.join("\n")}`;
}

export function formatNoDataMessage(dateStr) {
  const dt = DateTime.fromISO(dateStr, { zone: "Asia/Shanghai" });
  const safeDate = dt.isValid ? dt.toISODate() : dateStr;
  const en = `Daily App Usage (${safeDate})\nNo data received.`;
  const zh = `每日应用使用统计（${safeDate}）\n未收到数据。`;
  return `${en}\n\n${zh}`;
}
