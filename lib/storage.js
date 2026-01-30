import fs from "fs";
import path from "path";
import { DateTime } from "luxon";

const DATA_DIR = path.resolve("./data");

function ensureDataDir() {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
}

function filePathForDate(dateStr) {
  return path.join(DATA_DIR, `${dateStr}.json`);
}

export function saveReport(report) {
  ensureDataDir();
  const fp = filePathForDate(report.date);
  fs.writeFileSync(fp, JSON.stringify(report, null, 2));
  pruneOldReports();
}

export function loadReport(dateStr) {
  ensureDataDir();
  const fp = filePathForDate(dateStr);
  if (!fs.existsSync(fp)) return null;
  const raw = fs.readFileSync(fp, "utf8");
  return JSON.parse(raw);
}

export function pruneOldReports() {
  ensureDataDir();
  const files = fs.readdirSync(DATA_DIR).filter((f) => f.endsWith(".json"));
  const cutoff = DateTime.now().setZone("Asia/Shanghai").minus({ days: 6 }).startOf("day");

  for (const f of files) {
    const dateStr = path.basename(f, ".json");
    const dt = DateTime.fromISO(dateStr, { zone: "Asia/Shanghai" });
    if (!dt.isValid) continue;
    if (dt < cutoff) {
      fs.unlinkSync(path.join(DATA_DIR, f));
    }
  }
}
