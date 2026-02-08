import "dotenv/config";

/**
 * Send a message via OpenClaw gateway or Feishu API.
 * Replaces the old WhatsApp-only sender.
 *
 * Env vars:
 *   GATEWAY_URL  – OpenClaw gateway base URL (e.g. http://localhost:3001)
 *   TOKEN        – Bearer token for the gateway
 *   TARGET_ID    – Feishu open_id / chat_id / WhatsApp group id
 *   CHANNEL      – "feishu" | "whatsapp" (default: "feishu")
 */

export async function sendMessage({ text, target, channel }) {
  const TARGET = target || process.env.TARGET_ID;
  const CHANNEL = channel || process.env.CHANNEL || "feishu";

  if (!TARGET) {
    throw new Error("TARGET_ID is required (set env or pass target)");
  }

  const gateway = process.env.GATEWAY_URL;
  const token = process.env.TOKEN;

  if (!gateway) {
    console.log("[stub]", { target: TARGET, channel: CHANNEL, text });
    return { ok: true, stub: true };
  }

  const res = await fetch(`${gateway.replace(/\/$/, "")}/send`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({
      target: TARGET,
      channel: CHANNEL,
      text,
    }),
  });

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Gateway error ${res.status}: ${body}`);
  }

  return { ok: true };
}

// CLI usage: node scripts/send_message.js "Hello"
if (process.argv[1] === new URL(import.meta.url).pathname) {
  const text = process.argv.slice(2).join(" ").trim();
  if (!text) {
    console.error("Usage: node scripts/send_message.js <message text>");
    process.exit(1);
  }
  sendMessage({ text })
    .then(() => console.log("Sent."))
    .catch((err) => {
      console.error(err.message || err);
      process.exit(1);
    });
}
