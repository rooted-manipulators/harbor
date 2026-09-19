/**
 * Pretend to be Meta, so the bot can be tested without a WhatsApp number.
 *
 * The inbound half of this function — signature check, pairing lookup, parse,
 * write — has nothing to do with WhatsApp except the shape of the body and the
 * HMAC on it. Both are reproducible locally, so none of it has to wait for a
 * number to be provisioned.
 *
 * This is a development tool. It is NOT deployed: the function ships the files
 * named explicitly in the deploy, which is `index.ts` and `timetable.ts` only.
 *
 * ## Your app secret does not leave your machine
 *
 * It is read from the environment or from a local file, used to sign the body,
 * and never printed. Do not paste it into a chat, a commit, or an issue.
 *
 * ## Use
 *
 *   # the secret, once per shell — or put it in backend/.env as WHATSAPP_APP_SECRET
 *   export WHATSAPP_APP_SECRET='...'
 *
 *   # pair the seeded test account
 *   node probe.mjs 'TEST99'
 *
 *   # then send it something a class group would actually send
 *   node probe.mjs 'Marketing 101 shifted to Thursday 2-4 pm'
 *
 * Override the sender with WHATSAPP_TEST_FROM (E.164, no plus). It defaults to
 * a number in the 555-01 fictional range, which cannot reach a real person.
 */
import { createHmac } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const FUNCTION_URL =
  process.env.HARBOR_FUNCTION_URL ??
  "https://vxtufvcpirdjtrmidter.supabase.co/functions/v1/whatsapp";

// 555-01xx is reserved for fiction. If anything ever tries to send to this, it
// reaches nobody, which is the point.
const FROM = process.env.WHATSAPP_TEST_FROM ?? "15550100001";

const text = process.argv.slice(2).join(" ");
if (!text) {
  console.error("usage: node probe.mjs '<the message to send>'");
  process.exit(2);
}

const secret = appSecret();
if (!secret) {
  console.error(
    "No app secret. Set WHATSAPP_APP_SECRET in the environment, or put it in\n" +
      "backend/.env. It is read locally and never printed.",
  );
  process.exit(2);
}

/** From the environment, else from backend/.env. Never logged. */
function appSecret() {
  if (process.env.WHATSAPP_APP_SECRET) return process.env.WHATSAPP_APP_SECRET.trim();
  try {
    const here = dirname(fileURLToPath(import.meta.url));
    const env = readFileSync(resolve(here, "../../../.env"), "utf8");
    const line = env.split(/\r?\n/).find((l) => l.startsWith("WHATSAPP_APP_SECRET="));
    return line?.slice("WHATSAPP_APP_SECRET=".length).trim().replace(/^["']|["']$/g, "") || null;
  } catch {
    return null;
  }
}

// The shape Meta actually posts. Only `from` and `text.body` are read by the
// function; the rest is here so this stays a realistic rehearsal rather than a
// body shaped to fit our own parser.
const body = JSON.stringify({
  object: "whatsapp_business_account",
  entry: [
    {
      id: "0",
      changes: [
        {
          field: "messages",
          value: {
            messaging_product: "whatsapp",
            metadata: { display_phone_number: FROM, phone_number_id: "0" },
            contacts: [{ profile: { name: "Probe" }, wa_id: FROM }],
            messages: [
              {
                from: FROM,
                id: `wamid.probe.${Date.now()}`,
                timestamp: String(Math.floor(Date.now() / 1000)),
                type: "text",
                text: { body: text },
              },
            ],
          },
        },
      ],
    },
  ],
});

// Over the bytes as sent, which is why the function reads the body as text
// before parsing it.
const signature = "sha256=" + createHmac("sha256", secret).update(body, "utf8").digest("hex");

const response = await fetch(FUNCTION_URL, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "x-hub-signature-256": signature,
  },
  body,
});

console.log(`from +${FROM}: ${JSON.stringify(text)}`);
console.log(`-> HTTP ${response.status} ${await response.text()}`);
if (response.status === 401) {
  console.log("\n401 means the signature did not match: the secret here is not the");
  console.log("one set as WHATSAPP_APP_SECRET on the deployed function.");
}
