import "dotenv/config";
import express from "express";
import cron from "node-cron";
import { DateTime } from "luxon";
import { saveReport, loadReport } from "./lib/storage.js";
import { formatNoDataMessage, formatReportMessage } from "./lib/format.js";
import { sendMessage } from "./scripts/send_message.js";

const app = express();

// ── Logging Middleware (New) ──
app.use((req, res, next) => {
  const start = Date.now();
  const timestamp = new Date().toISOString();
  const { method, url } = req;
  const ip = req.ip || req.socket.remoteAddress || "unknown";

  res.on("finish", () => {
    const duration = Date.now() - start;
    const status = res.statusCode;
    console.log(`[${timestamp}] ${ip} ${method} ${url} ${status} ${duration}ms`);
  });
  next();
});

// ── CORS (simple implementation, no extra dep) ──
app.use((_req, res, next) => {
  res.setHeader("Access-Control-Allow-Origin", process.env.CORS_ORIGIN || "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
  if (_req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

app.use(express.json({ limit: "1mb" }));

const PORT = Number(process.env.PORT) || 3000;
const AUTH_TOKEN = process.env.REPORT_TOKEN;

// ── Rate limiter (in-memory, no extra dep) ──
const rateLimitMap = new Map();
const RATE_WINDOW_MS = 15 * 60 * 1000; // 15 min
const RATE_MAX = Number(process.env.RATE_LIMIT) || 60;

function rateLimit(req, res, next) {
  const ip = req.ip || req.socket.remoteAddress || "unknown";
  const now = Date.now();
  let entry = rateLimitMap.get(ip);

  if (!entry || now - entry.windowStart > RATE_WINDOW_MS) {
    entry = { windowStart: now, count: 0 };
    rateLimitMap.set(ip, entry);
  }

  entry.count++;

  if (entry.count > RATE_MAX) {
    return res.status(429).json({
      error: "Too many requests",
      retryAfterMs: RATE_WINDOW_MS - (now - entry.windowStart),
    });
  }

  next();
}

// Clean up stale rate limit entries every 30 min
setInterval(() => {
  const now = Date.now();
  for (const [ip, entry] of rateLimitMap) {
    if (now - entry.windowStart > RATE_WINDOW_MS) rateLimitMap.delete(ip);
  }
}, 30 * 60 * 1000).unref();

// ── Auth middleware ──
function requireAuth(req, res, next) {
  if (!AUTH_TOKEN) return next();
  const auth = req.headers.authorization || "";
  const parts = auth.split(" ");
  const token = parts.length === 2 ? parts[1] : auth;
  if (token && token === AUTH_TOKEN) return next();
  return res.status(401).json({ error: "unauthorized" });
}

// ── Validation ──
function validateReport(body) {
  if (!body || typeof body !== "object") return "invalid body";
  if (typeof body.date !== "string") return "date required";
  if (typeof body.timezone !== "string") return "timezone required";
  if (!Array.isArray(body.apps)) return "apps must be array";
  for (const a of body.apps) {
    if (!a || typeof a !== "object") return "apps item invalid";
    if (typeof a.package !== "string") return "app.package required";
    if (typeof a.foreground_ms !== "number") return "app.foreground_ms required";
  }
  return null;
}

// ── Routes ──
app.get("/health", (_req, res) => {
  res.json({ 
    ok: true, 
    uptime: process.uptime(),
    version: "0.2.1",
    timestamp: new Date().toISOString()
  });
});

app.post("/report", rateLimit, requireAuth, (req, res, next) => {
  try {
    const err = validateReport(req.body);
    if (err) return res.status(400).json({ error: err });
    saveReport(req.body);
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});

// ── Clipboard endpoint ──
app.post("/clipboard", rateLimit, requireAuth, async (req, res, next) => {
  try {
    const { text, device } = req.body;
    if (!text) return res.status(400).json({ error: "text required" });

    // Target group: oc_64940ea1490ff3baafc4b50f4b045cfc
    const target = process.env.CLIPBOARD_TARGET_ID || "oc_64940ea1490ff3baafc4b50f4b045cfc";
    
    await sendMessage({ 
      text: `📋 *Clipboard from ${device || "Android"}*\n\n${text}`, 
      target 
    });
    
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});

app.get("/report/:date", requireAuth, (req, res, next) => {
  try {
    const report = loadReport(req.params.date);
    if (!report) return res.status(404).json({ error: "not found" });
    res.json(report);
  } catch (e) {
    next(e);
  }
});

// ── Global error handler ──
app.use((err, _req, res, _next) => {
  console.error("[ERROR]", err.stack || err.message || err);
  res.status(500).json({ error: "internal server error" });
});

// ── Daily report cron ──
async function sendDailyReport() {
  const today = DateTime.now().setZone("Asia/Shanghai").toISODate();
  const report = loadReport(today);
  const text = report
    ? formatReportMessage(report, { topN: 10 })
    : formatNoDataMessage(today);
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

// ── Start server ──
const server = app.listen(PORT, () => {
  console.log(`Server listening on :${PORT}`);
});

// ── Graceful shutdown ──
function shutdown(signal) {
  console.log(`\n${signal} received. Shutting down gracefully...`);
  server.close(() => {
    console.log("HTTP server closed.");
    process.exit(0);
  });
  // Force exit after 10s
  setTimeout(() => {
    console.error("Forced shutdown after timeout.");
    process.exit(1);
  }, 10000).unref();
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));