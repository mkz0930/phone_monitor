import { DateTime } from "luxon";
import { loadReport } from "./storage.js";

// ── App category mapping ──
const CATEGORY_MAP = {
  // Social
  "com.tencent.mm": "Social",
  "com.tencent.mobileqq": "Social",
  "com.whatsapp": "Social",
  "com.facebook.katana": "Social",
  "com.facebook.orca": "Social",
  "com.instagram.android": "Social",
  "com.twitter.android": "Social",
  "com.snapchat.android": "Social",
  "com.linkedin.android": "Social",
  "org.telegram.messenger": "Social",
  "com.discord": "Social",
  "com.reddit.frontpage": "Social",
  "com.zhihu.android": "Social",
  "com.sina.weibo": "Social",
  "com.tencent.wework": "Social",
  "com.larksuite.suite": "Social",
  "com.ss.android.lark": "Social",

  // Video / Entertainment
  "com.ss.android.ugc.aweme": "Video",
  "com.ss.android.ugc.aweme.lite": "Video",
  "com.zhiliaoapp.musically": "Video",
  "com.google.android.youtube": "Video",
  "tv.danmaku.bili": "Video",
  "com.tencent.qqlive": "Video",
  "com.youku.phone": "Video",
  "com.netflix.mediaclient": "Video",
  "com.disney.disneyplus": "Video",
  "com.kuaishou.nebula": "Video",
  "com.smile.gifmaker": "Video",

  // Gaming
  "com.tencent.tmgp.sgame": "Gaming",
  "com.miHoYo.Yuanshen": "Gaming",
  "com.supercell.clashofclans": "Gaming",
  "com.tencent.ig": "Gaming",

  // Productivity / Work
  "com.google.android.gm": "Work",
  "com.microsoft.office.outlook": "Work",
  "com.google.android.apps.docs": "Work",
  "com.microsoft.teams": "Work",
  "com.slack": "Work",
  "com.notion.id": "Work",
  "com.todoist": "Work",
  "com.google.android.calendar": "Work",

  // Reading / News
  "com.ss.android.article.news": "Reading",
  "com.readdle.documents": "Reading",
  "com.amazon.kindle": "Reading",
  "com.douban.frodo": "Reading",
  "com.tencent.weread": "Reading",

  // Shopping
  "com.taobao.taobao": "Shopping",
  "com.jingdong.app.mall": "Shopping",
  "com.xunmeng.pinduoduo": "Shopping",
  "com.meituan": "Shopping",
  "com.eg.android.AlipayGphone": "Shopping",
};

function getCategory(packageName) {
  return CATEGORY_MAP[packageName] || "Other";
}

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

function deltaSymbol(todayMs, yesterdayMs) {
  if (yesterdayMs === 0 && todayMs === 0) return "";
  if (yesterdayMs === 0) return " 🆕";
  const diff = todayMs - yesterdayMs;
  if (Math.abs(diff) < 60000) return " ＝"; // <1min diff
  const arrow = diff > 0 ? "↑" : "↓";
  return ` ${arrow}${msToHuman(Math.abs(diff))}`;
}

function buildCategoryStats(apps) {
  const cats = {};
  for (const app of apps) {
    const cat = getCategory(app.package);
    cats[cat] = (cats[cat] || 0) + (app.foreground_ms || 0);
  }
  return Object.entries(cats)
    .sort((a, b) => b[1] - a[1])
    .map(([name, ms]) => ({ name, ms }));
}

function getYesterdayReport(dateStr) {
  try {
    const dt = DateTime.fromISO(dateStr, { zone: "Asia/Shanghai" });
    if (!dt.isValid) return null;
    const yesterday = dt.minus({ days: 1 }).toISODate();
    return loadReport(yesterday);
  } catch {
    return null;
  }
}

function buildYesterdayMap(report) {
  if (!report || !Array.isArray(report.apps)) return {};
  const map = {};
  for (const app of report.apps) {
    map[app.package] = app.foreground_ms || 0;
  }
  return map;
}

export function formatReportMessage(report, opts = {}) {
  const topN = opts.topN ?? 10;
  const dateStr = report.date;
  const apps = Array.isArray(report.apps) ? report.apps : [];
  const sorted = [...apps].sort(
    (a, b) => (b.foreground_ms || 0) - (a.foreground_ms || 0)
  );
  const top = sorted.slice(0, topN);
  const totalMs =
    typeof report.total_foreground_ms === "number"
      ? report.total_foreground_ms
      : apps.reduce((sum, a) => sum + (a.foreground_ms || 0), 0);

  // Yesterday comparison
  const yesterdayReport = getYesterdayReport(dateStr);
  const yMap = buildYesterdayMap(yesterdayReport);
  const yesterdayTotal = yesterdayReport
    ? typeof yesterdayReport.total_foreground_ms === "number"
      ? yesterdayReport.total_foreground_ms
      : (yesterdayReport.apps || []).reduce(
          (s, a) => s + (a.foreground_ms || 0),
          0
        )
    : null;

  // Category stats
  const categories = buildCategoryStats(apps);

  // ── English section ──
  const lines = [`📱 **Daily App Usage (${dateStr})**`, ""];

  // Top apps
  lines.push("**Top Apps:**");
  for (const app of top) {
    const name = app.name || app.package || "(unknown)";
    const delta = yMap ? deltaSymbol(app.foreground_ms || 0, yMap[app.package] || 0) : "";
    lines.push(`• ${name}: ${msToHuman(app.foreground_ms || 0)}${delta}`);
  }

  // Category breakdown
  lines.push("", "**By Category:**");
  for (const cat of categories) {
    lines.push(`• ${cat.name}: ${msToHuman(cat.ms)}`);
  }

  // Total
  const totalDelta =
    yesterdayTotal !== null ? deltaSymbol(totalMs, yesterdayTotal) : "";
  lines.push("", `**Total: ${msToHuman(totalMs)}${totalDelta}**`);

  // ── Chinese section ──
  lines.push("", `> 📱 **每日应用统计（${dateStr}）**`);
  lines.push("> Top 应用：");
  for (const app of top) {
    const name = app.name || app.package || "(未知)";
    const delta = yMap ? deltaSymbol(app.foreground_ms || 0, yMap[app.package] || 0) : "";
    lines.push(`> • ${name}：${msToHumanZh(app.foreground_ms || 0)}${delta}`);
  }
  lines.push("> 分类：");
  for (const cat of categories) {
    lines.push(`> • ${cat.name}：${msToHumanZh(cat.ms)}`);
  }
  const totalDeltaZh =
    yesterdayTotal !== null ? deltaSymbol(totalMs, yesterdayTotal) : "";
  lines.push(`> **合计：${msToHumanZh(totalMs)}${totalDeltaZh}**`);

  return lines.join("\n");
}

export function formatNoDataMessage(dateStr) {
  const dt = DateTime.fromISO(dateStr, { zone: "Asia/Shanghai" });
  const safeDate = dt.isValid ? dt.toISODate() : dateStr;
  return [
    `⚠️ **Daily App Usage (${safeDate})**`,
    "No data received.",
    "",
    `> ⚠️ **每日应用统计（${safeDate}）**`,
    "> 未收到数据。",
  ].join("\n");
}
