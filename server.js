import "dotenv/config";
import express from "express";
import cron from "node-cron";
import { DateTime } from "luxon";
import { saveReport, loadReport } from "./lib/storage.js";
import { formatNoDataMessage, formatReportMessage } from "./lib/format.js";
import { sendMessage } from "./scripts/send_whatsapp.js";

const app = express();
app.use(express.json({ limit: "1mb" }));

const PORT = Number(process.env.PORT) || 3000;
const AUTH_TOKEN = process.env.REPORT_TOKEN;

function requireAuth(req, res, next) {
  if (!AUTH_TOKEN) return next();
  const auth = req.headers.authorization || "";
  const parts = auth.split(" ");
  const token = parts.length === 2 ? parts[1] : auth;
  if (token && token === AUTH_TOKEN) return next();
  return res.status(401).json({ error: "unauthorized" });
}

function validateReport(body) {
  if (!body || typeof body !== "object") return "invalid body";
  if (typeof body.date !== "string") return "date required";
  if (typeof body.timezone !== "string") return "timezone required";
  if (!Array.isArray(body.apps)) return "apps must be array";
  for (const app of body.apps) {
    if (!app || typeof app !== "object") return "apps item invalid";
    if (typeof app.package !== "string") return "app.package required";
    if (typeof app.foreground_ms !== "number") return "app.foreground_ms required";
  }
  return null;
}

app.get("/health", (_req, res) => {
  res.json({ ok: true });
});

app.post("/report", requireAuth, (req, res) => {
  const err = validateReport(req.body);
  if (err) return res.status(400).json({ error: err });
  saveReport(req.body);
  res.json({ ok: true });
});

async function sendDailyReport() {
  const today = DateTime.now().setZone("Asia/Shanghai").toISODate();
  const report = loadReport(today);
  const text = report ? formatReportMessage(report, { topN: 10 }) : formatNoDataMessage(today);
  await sendMessage({ text });
}

cron.schedule(
  "0 23 * * *",
  () => {
    sendDailyReport().catch((err) => {
      console.error("Failed to send daily report:", err.message || err);
    });
  },
  { timezone: "Asia/Shanghai" }
);

app.listen(PORT, () => {
  console.log(`Server listening on :${PORT}`);
});
