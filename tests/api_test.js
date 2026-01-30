import fetch from "node-fetch";

const BASE_URL = "http://localhost:3000";
const TOKEN = process.env.REPORT_TOKEN || "test-token";

async function runTests() {
  console.log("🚀 Starting Phone Monitor API Tests...");

  // 1. Check Health
  try {
    const health = await fetch(`${BASE_URL}/health`);
    const data = await health.json();
    console.log("✅ Health Check:", data.ok ? "PASS" : "FAIL");
  } catch (e) {
    console.error("❌ Health Check Failed:", e.message);
  }

  // 2. Test Unauthorized Report
  try {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify({}),
      headers: { "Content-Type": "application/json" }
    });
    console.log("✅ Unauthorized Check:", res.status === 401 ? "PASS" : "FAIL");
  } catch (e) {
    console.error("❌ Unauthorized Check Failed:", e.message);
  }

  // 3. Test Valid Report
  const payload = {
    date: new Date().toISOString().split('T')[0],
    timezone: "Asia/Shanghai",
    apps: [
      { package: "com.tencent.mm", name: "WeChat", foreground_ms: 3600000 },
      { package: "com.ss.android.ugc.aweme", name: "Douyin", foreground_ms: 1800000 }
    ]
  };

  try {
    const res = await fetch(`${BASE_URL}/report`, {
      method: "POST",
      body: JSON.stringify(payload),
      headers: { 
        "Content-Type": "application/json",
        "Authorization": `Bearer ${TOKEN}`
      }
    });
    const data = await res.json();
    console.log("✅ Valid Report Check:", data.ok ? "PASS" : "FAIL");
  } catch (e) {
    console.error("❌ Valid Report Check Failed:", e.message);
  }
}

runTests();
