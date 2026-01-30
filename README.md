# Android App Usage Monitor (Spec)

> Goal: Track **per-app usage time** on an Android phone, and send a **daily report at 23:00 (Asia/Shanghai)** to Horse.
>
> 目标：在安卓手机上统计 **各应用使用时长**，并在 **每天 23:00（上海时区）** 把报告发给 Horse。

## Quick start (Architecture A server)

Requirements: Node.js 18+ (for built-in `fetch`).

```bash
cp .env.example .env
npm install
npm run start
```

POST daily report (authenticated):

```bash
curl -X POST http://localhost:3000/report \
  -H "content-type: application/json" \
  -H "authorization: Bearer $REPORT_TOKEN" \
  -d @daily.json
```

Generate message for a date:

```bash
node scripts/generate_message.js --date 2026-01-28
```

The server schedules a daily send at **23:00 Asia/Shanghai**. If no data is received for today, it sends a “no data received” alert instead.
Reports are stored as files in `data/` and only the latest 7 days are kept.

Send a message manually (uses Clawdbot stub unless `GATEWAY_URL` is set):

```bash
node scripts/send_whatsapp.js "Hello group"
```

## 1) Scope / 范围
- **Platform / 平台**: Android (recommended: Android 10+)
- **Metrics / 统计项**:
  - Per-app foreground usage time (Screen Time) / 每个应用前台使用时长
  - Optional: total screen time, unlock count / 可选：总亮屏时长、解锁次数
- **Reporting / 报告**:
  - Daily summary for “today” (00:00–23:59) / 当天汇总（00:00–23:59）
  - Send at **23:00** every day / 每天 **23:00** 定时发送

## 2) Permissions & APIs / 权限与系统接口
### Required
- `android.permission.PACKAGE_USAGE_STATS`
  - Granted via Settings → “Usage access” (special access) / 需要在系统设置里授予“使用情况访问权限”

### Primary API
- `UsageStatsManager`
  - `queryUsageStats()` or `queryEvents()` to compute per-app usage

### Notes
- OEM ROMs (MIUI, etc.) may kill background jobs; need battery whitelist guidance.

## 3) Data model / 数据结构
Daily record:
```json
{
  "date": "2026-01-28",
  "timezone": "Asia/Shanghai",
  "apps": [
    {"package": "com.tencent.mm", "name": "WeChat", "foreground_ms": 5234000},
    {"package": "com.ss.android.ugc.aweme", "name": "Douyin", "foreground_ms": 3120000}
  ],
  "total_foreground_ms": 11023456
}
```
Local storage: Room DB or JSON file in app-private storage.

## 4) Scheduling / 定时任务
Use **WorkManager** (recommended) with a daily periodic work:
- Run once per day at **23:00** (local time)
- If the phone is in Doze mode, WorkManager may delay; mitigate via:
  - Battery optimization whitelist instructions
  - Optionally using `AlarmManager` (exact alarms require extra permission on Android 12+)

## 5) Report format / 报告格式
English-first + Chinese support, concise:

Example:
- **Daily App Usage (2026-01-28)**
- Top apps:
  - Douyin: 52m
  - WeChat: 41m
  - Browser: 18m
- Total: 2h 13m

（中文）
- **每日应用使用统计（2026-01-28）**
- Top 应用：抖音 52 分钟，微信 41 分钟…
- 合计：2 小时 13 分钟

## 6) How to send to Horse / 如何把报告发给 Horse（关键决策）
There are **two practical delivery architectures**:

### A) Phone → Server/API → Clawdbot → WhatsApp (recommended)
- Phone uploads the daily JSON/text to a small HTTP endpoint.
- Clawdbot receives it and sends to WhatsApp via gateway.

Pros: reliable, no WhatsApp automation on phone. / 优点：可靠，不需要手机端自动操作 WhatsApp。

### B) Phone directly sends (hard)
- Direct WhatsApp sending from app is limited by platform restrictions.
- Usually requires user interaction or accessibility automation (fragile).

Recommendation: choose **A**.

## 7) Implementation plan / 实施步骤
1. Android app skeleton (Kotlin)
2. Usage access permission flow + UI status indicator
3. Collect usage data for a chosen day window
4. Format report text
5. Schedule daily job at 23:00
6. Add “upload to endpoint” (if using Architecture A)
7. Testing + battery optimization instructions

## 8) Open questions / 待确认
1. **Which phone?** (brand/model/Android version)
2. Do you want **top N apps** (e.g., top 10) or full list?
3. Delivery architecture: **A** (recommended) or B?
4. Should we include **category grouping** (Social/Video/Work)?

---
File location: `/home/claw/tests/android_app_usage_monitor_spec.md`
