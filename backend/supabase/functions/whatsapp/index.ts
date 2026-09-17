/**
 * Harbor's WhatsApp bot — the only server-side code in this repo.
 *
 * A participant forwards a message from a class group chat. This reads the
 * days and times out of it, drops everything else, and puts the result in
 * that participant's `schedule_inbox`. Their phone drains the inbox the next
 * time Harbor comes to the front and places the blocks on their week.
 *
 * See ADR-014 for why a bot at all, and `0012_whatsapp_inbox.sql` for the
 * rules the schema enforces.
 *
 * ## What this is not allowed to do
 *
 * - Keep a message. Nothing here writes the text, the sender, the group or
 *   the subject anywhere. `readTimetable` returns times and there is no
 *   column for anything else.
 * - Guess who is talking. An unknown number gets an instruction and nothing
 *   else — no rows read, none written.
 * - Matter to the cue. The pipeline on the phone must work with this function
 *   down, unreachable or never deployed (ADR-003). It does: the inbox is a
 *   source of blocks the user could have drawn by hand.
 *
 * ## Deploying
 *
 *   supabase functions deploy whatsapp --no-verify-jwt
 *
 * `--no-verify-jwt` because Meta calls it, not a signed-in client. The
 * signature check below is what stands in for that, and it is not optional.
 */
import { createClient } from "jsr:@supabase/supabase-js@2";
import { readTimetable, describe } from "./timetable.ts";

const VERIFY_TOKEN = Deno.env.get("WHATSAPP_VERIFY_TOKEN") ?? "";
const APP_SECRET = Deno.env.get("WHATSAPP_APP_SECRET") ?? "";
const GRAPH_TOKEN = Deno.env.get("WHATSAPP_TOKEN") ?? "";
const PHONE_ID = Deno.env.get("WHATSAPP_PHONE_ID") ?? "";

const db = createClient(
  Deno.env.get("SUPABASE_URL") ?? "",
  // The service role, because there is no signed-in user on a webhook and
  // `schedule_inbox` has no insert policy by design. It never leaves this
  // process.
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
);

Deno.serve(async (request: Request) => {
  const url = new URL(request.url);

  // Meta's one-time subscription handshake.
  if (request.method === "GET") {
    const challenge = url.searchParams.get("hub.challenge") ?? "";
    const token = url.searchParams.get("hub.verify_token") ?? "";
    if (url.searchParams.get("hub.mode") === "subscribe" && token === VERIFY_TOKEN) {
      return new Response(challenge, { status: 200 });
    }
    return new Response("no", { status: 403 });
  }

  if (request.method !== "POST") return new Response("no", { status: 405 });

  // Read the body once, as text, because the signature is over the bytes as
  // sent. Parsing first and re-serialising would not reproduce them.
  const raw = await request.text();
  if (!(await signedByMeta(raw, request.headers.get("x-hub-signature-256")))) {
    return new Response("no", { status: 401 });
  }

  // Everything past here answers 200 whatever happens. A webhook that returns
  // an error gets retried, and then retried more slowly, and eventually the
  // subscription is dropped — over a message we could not parse, which is not
  // a failure that retrying fixes.
  try {
    for (const message of messagesIn(raw)) {
      await handle(message.from, message.text, new Date());
    }
  } catch (problem) {
    console.error("whatsapp:", problem instanceof Error ? problem.message : problem);
  }
  return new Response("ok", { status: 200 });
});

interface Incoming {
  /** E.164 without the plus, which is how Meta sends it. */
  from: string;
  text: string;
}

/** The text messages in a webhook body. Everything else is ignored. */
function messagesIn(raw: string): Incoming[] {
  const body = JSON.parse(raw);
  const out: Incoming[] = [];
  for (const entry of body?.entry ?? []) {
    for (const change of entry?.changes ?? []) {
      for (const message of change?.value?.messages ?? []) {
        if (message?.type !== "text") continue;
        const text = message?.text?.body;
        if (typeof text === "string" && message?.from) {
          out.push({ from: String(message.from), text });
        }
      }
    }
  }
  return out;
}

async function handle(from: string, text: string, now: Date): Promise<void> {
  const phone = `+${from.replace(/\D/g, "")}`;

  // A bare six characters is somebody pairing, not a timetable. Checked
  // before the parse so a code can never be read as a time.
  const code = text.trim().toUpperCase();
  if (/^[A-Z0-9]{6}$/.test(code)) {
    await pair(code, phone);
    return;
  }

  const { data: pairing } = await db
    .from("whatsapp_pairings")
    .select("user_id")
    .eq("phone_e164", phone)
    .not("paired_at", "is", null)
    .maybeSingle();

  if (!pairing) {
    await reply(
      phone,
      "I do not know whose week this is yet. Open Harbor, go to your week, " +
        'and send me the six characters shown under "Forward your class chats".',
    );
    return;
  }

  const blocks = readTimetable(text, now);
  if (blocks.length > 0) {
    // Ignoring duplicates rather than failing on them: the same reminder goes
    // round a group more than once, and forwarding it twice should be a
    // no-op, not an error.
    await db
      .from("schedule_inbox")
      .upsert(
        blocks.map((block) => ({
          user_id: pairing.user_id,
          day: block.day,
          starts_at: block.start,
          ends_at: block.end,
          kind: block.kind,
        })),
        { onConflict: "user_id,day,starts_at,ends_at,kind", ignoreDuplicates: true },
      );
  }

  await reply(phone, describe(blocks));
}

/**
 * Bind a number to the account that minted [code].
 *
 * A number already bound to another account is refused rather than moved:
 * moving it silently would take somebody's week off them without either
 * person seeing it happen.
 */
async function pair(code: string, phone: string): Promise<void> {
  const { data: waiting } = await db
    .from("whatsapp_pairings")
    .select("user_id, phone_e164")
    .eq("code", code)
    .maybeSingle();

  if (!waiting) {
    await reply(phone, "That code is not one of mine. Check Harbor's week screen for the current one.");
    return;
  }
  if (waiting.phone_e164 && waiting.phone_e164 !== phone) {
    await reply(phone, "That code is already in use by another number.");
    return;
  }

  const { error } = await db
    .from("whatsapp_pairings")
    .update({ phone_e164: phone, paired_at: new Date().toISOString() })
    .eq("code", code);

  await reply(
    phone,
    error
      ? "I could not connect that number. It may already be connected to another Harbor account."
      : "Connected. Forward anything from your class groups and I will mark the times on your week.",
  );
}

/** Say something back. Best effort — a failed reply is not worth a retry. */
async function reply(phone: string, text: string): Promise<void> {
  if (!GRAPH_TOKEN || !PHONE_ID) return;
  try {
    await fetch(`https://graph.facebook.com/v21.0/${PHONE_ID}/messages`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${GRAPH_TOKEN}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        messaging_product: "whatsapp",
        to: phone,
        type: "text",
        text: { body: text },
      }),
    });
  } catch (problem) {
    console.error("reply:", problem instanceof Error ? problem.message : problem);
  }
}

/**
 * Whether Meta signed this body with our app secret.
 *
 * Without it the endpoint is a public write into any paired participant's
 * week: anybody who learned the URL could post a body claiming to be from
 * their number. Compared byte by byte in constant time, because a compare
 * that returns early on the first wrong character leaks the right one.
 */
async function signedByMeta(raw: string, header: string | null): Promise<boolean> {
  if (!APP_SECRET) return false;
  if (!header?.startsWith("sha256=")) return false;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(APP_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(raw));
  const expected = Array.from(new Uint8Array(signature))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");

  const given = header.slice("sha256=".length);
  if (given.length !== expected.length) return false;
  let difference = 0;
  for (let i = 0; i < expected.length; i++) {
    difference |= given.charCodeAt(i) ^ expected.charCodeAt(i);
  }
  return difference === 0;
}
