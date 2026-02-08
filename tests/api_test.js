import { strict as assert } from "assert";

const BASE_URL = "http://localhost:3000";
const TOKEN = process.env.REPORT_TOKEN || "test-token";

const headers = (extra = {}) => ({
  "Content-Type": "application/json",
  Authorization: `Bearer ${TOKEN}`,
  ...extra,
});

let passed = 0;
let failed = 0;

async function test(name, fn) {
  try {
    await fn();
    console.log(`✅ ${name}`);
    passed++;
  } catch (e) {
    console.error(`❌ ${name}: ${e.message}`);
    failed++;
  }
}

async function runTests() {
  console.log("🚀 Phone Monitor API Tests\n");

  // ── Health ──
  await test("GET /health returns ok", async () => {
    const res = await fetch(`${BASE_URL}/health`);
    const data = await res.json();
    assert.equal(data.ok, true);
    assert.equal(typeof data.uptime, "number");
  });

  // ── Auth ──
  await test("POST /report without auth → 401", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({}),
      headers: { "Content-Type": "application/json" },
    });
    assert.equal(res.status, 401);
  });

  await test("POST /report with wrong token → 401", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({}),
      headers: headers({ Authorization: "Bearer wrong-token" }),
    });
    assert.equal(res.status, 401);
  });

  // ── Validation ──
  await test("POST /report empty body → 400", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: "{}",
      headers: headers(),
    });
    assert.equal(res.status, 400);
  });

  await test("POST /report missing date → 400", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({ timezone: "Asia/Shanghai", apps: [] }),
      headers: headers(),
    });
    assert.equal(res.status, 400);
    const data = await res.json();
    assert.equal(data.error, "date required");
  });

  await test("POST /report missing timezone → 400", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({ date: "2026-01-01", apps: [] }),
      headers: headers(),
    });
    assert.equal(res.status, 400);
    const data = await res.json();
    assert.equal(data.error, "timezone required");
  });

  await test("POST /report apps not array → 400", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({
        date: "2026-01-01",
        timezone: "Asia/Shanghai",
        apps: "not-array",
      }),
      headers: headers(),
    });
    assert.equal(res.status, 400);
  });

  await test("POST /report app missing package → 400", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({
        date: "2026-01-01",
        timezone: "Asia/Shanghai",
        apps: [{ foreground_ms: 1000 }],
      }),
      headers: headers(),
    });
    assert.equal(res.status, 400);
    const data = await res.json();
    assert.equal(data.error, "app.package required");
  });

  await test("POST /report app missing foreground_ms → 400", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({
        date: "2026-01-01",
        timezone: "Asia/Shanghai",
        apps: [{ package: "com.test" }],
      }),
      headers: headers(),
    });
    assert.equal(res.status, 400);
    const data = await res.json();
    assert.equal(data.error, "app.foreground_ms required");
  });

  // ── Valid report ──
  const validPayload = {
    date: "2026-02-08",
    timezone: "Asia/Shanghai",
    apps: [
      { package: "com.tencent.mm", name: "WeChat", foreground_ms: 3600000 },
      {
        package: "com.ss.android.ugc.aweme",
        name: "Douyin",
        foreground_ms: 1800000,
      },
      {
        package: "com.google.android.youtube",
        name: "YouTube",
        foreground_ms: 900000,
      },
    ],
  };

  await test("POST /report valid → 200", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify(validPayload),
      headers: headers(),
    });
    const data = await res.json();
    assert.equal(res.status, 200);
    assert.equal(data.ok, true);
  });

  await test("POST /report duplicate (overwrite) → 200", async () => {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({ ...validPayload, apps: validPayload.apps.slice(0, 1) }),
      headers: headers(),
    });
    assert.equal(res.status, 200);
  });

  // ── GET report ──
  await test("GET /report/:date existing → 200", async () => {
    const res = await fetch(`${BASE_URL}/report/2026-02-08`, {
      headers: headers(),
    });
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.date, "2026-02-08");
  });

  await test("GET /report/:date missing → 404", async () => {
    const res = await fetch(`${BASE_URL}/report/1999-01-01`, {
      headers: headers(),
    });
    assert.equal(res.status, 404);
  });

  // ── CORS ──
  await test("OPTIONS /report returns CORS headers", async () => {
    const res = await fetch(`${BASE_URL}/report`, { method: "OPTIONS" });
    assert.equal(res.status, 204);
    assert.ok(res.headers.get("access-control-allow-origin"));
  });

  // ── 404 ──
  await test("GET /nonexistent → 404", async () => {
    const res = await fetch(`${BASE_URL}/nonexistent`);
    assert.equal(res.status, 404);
  });

  // ── Summary ──
  console.log(`\n📊 Results: ${passed} passed, ${failed} failed`);
  process.exit(failed > 0 ? 1 : 0);
}

runTests();
