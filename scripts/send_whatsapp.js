import "dotenv/config";

export async function sendMessage({ text, groupId }) {
  const GROUP_ID = groupId || process.env.GROUP_ID;
  if (!GROUP_ID) {
    throw new Error("GROUP_ID is required");
  }

  const gateway = process.env.GATEWAY_URL;
  const token = process.env.TOKEN;

  if (!gateway) {
    console.log("[clawdbot-stub]", { groupId: GROUP_ID, text });
    return { ok: true, stub: true };
  }

  const res = await fetch(`${gateway.replace(/\/$/, "")}/send`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ group_id: GROUP_ID, text })
  });

  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Gateway error ${res.status}: ${body}`);
  }

  return { ok: true };
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const text = process.argv.slice(2).join(" ").trim();
  if (!text) {
    console.error("Usage: node scripts/send_whatsapp.js <message text>");
    process.exit(1);
  }
  sendMessage({ text })
    .then(() => {
      console.log("Sent.");
    })
    .catch((err) => {
      console.error(err.message || err);
      process.exit(1);
    });
}
