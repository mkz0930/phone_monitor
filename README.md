# Android App Usage Monitor

> Track **per-app usage time** on an Android phone, send a **daily report at 23:00 (Asia/Shanghai)** to Horse via Feishu.
>
> 目标：在安卓手机上统计 **各应用使用时长**，并在 **每天 23:00（上海时区）** 把报告通过飞书发给 Horse。

## Features

- 📊 **Per-app usage tracking** with top N ranking
- 📁 **Category grouping** (Social / Video / Gaming / Work / Reading / Shopping / Other)
- 📈 **Trend comparison** with yesterday (↑/↓ deltas)
- 🔒 **Auth + rate limiting** (60 req / 15 min per IP)
- 🌐 **CORS support**
- 💾 **Atomic file writes** (crash-safe storage)
- 🛑 **Graceful shutdown** (SIGTERM/SIGINT)
- 📡 **Feishu delivery** (via OpenClaw gateway)

## Quick Start

Requirements: **Node.js 18+**

```bash
cp .env.example .env
# Edit .env with your tokens
npm install
npm start
```

## API

### `GET /health`
Health check. Returns `{ ok: true, uptime: <seconds> }`.

### `POST /report`
Submit a daily usage report. Requires `Authorization: Bearer <REPORT_TOKEN>`.

```bash
curl -X POST http://localhost:3000/report \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $REPORT_TOKEN" \
  -d @daily.json
```

**Payload:**
```json
{
  "date": "2026-02-08",
  "timezone": "Asia/Shanghai",
  "apps": [
    { "package": "com.tencent.mm", "name": "WeChat", "foreground_ms": 3600000 },
    { "package": "com.ss.android.ugc.aweme", "name": "Douyin", "foreground_ms": 1800000 }
  ],
  "total_foreground_ms": 5400000
}
```

### `GET /report/:date`
Retrieve a stored report by date. Requires auth.

```bash
curl http://localhost:3000/report/2026-02-08 \
  -H "Authorization: Bearer $REPORT_TOKEN"
```

## Report Format

```
📱 Daily App Usage (2026-02-08)

Top Apps:
• WeChat: 1h ↑15m
• Douyin: 30m ↓10m
• YouTube: 15m 🆕

By Category:
• Social: 1h
• Video: 45m

Total: 1h 45m ↑5m
```

## Scripts

```bash
# Generate message for a date
node scripts/generate_message.js --date 2026-02-08

# Send a message manually
node scripts/send_message.js "Hello"
```

## Testing

```bash
# Start server first, then:
npm test
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | Server port | `3000` |
| `REPORT_TOKEN` | Auth token for API | – |
| `CORS_ORIGIN` | Allowed CORS origin | `*` |
| `RATE_LIMIT` | Max requests per 15 min | `60` |
| `TARGET_ID` | Feishu open_id or chat_id | – |
| `GATEWAY_URL` | OpenClaw gateway URL | – |
| `TOKEN` | Gateway auth token | – |

## Architecture

```
Android Phone → POST /report → Express Server → data/*.json
                                     ↓ (23:00 cron)
                              Format report → Feishu (via gateway)
```

Reports stored as JSON files in `data/`, auto-pruned after 7 days.

## Android Client

See the spec in the README for implementation details. Key points:
- Uses `UsageStatsManager` API
- Requires `PACKAGE_USAGE_STATS` permission
- Schedule via WorkManager at 23:00 daily
- POST JSON to this server's `/report` endpoint
