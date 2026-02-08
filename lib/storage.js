import fs from "fs";
import path from "path";
import os from "os";
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

/**
 * Atomic write: write to a temp file, then rename.
 * Prevents corruption if the process crashes mid-write.
 */
function atomicWriteSync(filePath, data) {
  const tmpPath = filePath + `.tmp.${process.pid}`;
  fs.writeFileSync(tmpPath, data);
  fs.renameSync(tmpPath, filePath);
}

export function saveReport(report) {
  ensureDataDir();
  const fp = filePathForDate(report.date);
  atomicWriteSync(fp, JSON.stringify(report, null, 2));
  pruneOldReports();
}

export function loadReport(dateStr) {
  ensureDataDir();
  const fp = filePathForDate(dateStr);
  if (!fs.existsSync(fp)) return null;
  const raw = fs.readFileSync(fp, "utf8");
  return JSON.parse(raw);
}

export function listReportDates() {
  ensureDataDir();
  return fs
    .readdirSync(DATA_DIR)
    .filter((f) => f.endsWith(".json"))
    .map((f) => path.basename(f, ".json"))
    .sort();
}

export function pruneOldReports() {
  ensureDataDir();
  const files = fs.readdirSync(DATA_DIR).filter((f) => f.endsWith(".json"));
  const cutoff = DateTime.now()
    .setZone("Asia/Shanghai")
    .minus({ days: 6 })
    .startOf("day");

  for (const f of files) {
    const dateStr = path.basename(f, ".json");
    const dt = DateTime.fromISO(dateStr, { zone: "Asia/Shanghai" });
    if (!dt.isValid) continue;
    if (dt < cutoff) {
      fs.unlinkSync(path.join(DATA_DIR, f));
    }
  }
}
