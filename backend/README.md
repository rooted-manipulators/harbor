# Harbor backend

Supabase (Postgres + auth + RLS). There is no application server: the Android
app talks to Supabase directly with the anon key, and row-level security does
the authorisation.

The one exception is the WhatsApp bot, which is an edge function because a
webhook has to be somewhere. It is off the cue path entirely — see below.

## Why so little backend

The trigger pipeline runs on-device. The database exists to (a) survive a
reinstall, (b) let us export the week-one study data. If you find yourself
adding an endpoint the app blocks on before showing a cue, stop — that is a
design error, not a missing feature. See `docs/01-decisions.md`, ADR-003.

## Layout

```
supabase/migrations/
  0001_init.sql           tables, enums, RLS, new-user trigger
  0002_study_export.sql   the two views the study actually needs
  ...
  0012_whatsapp_inbox.sql pairing a number, and the queue the bot fills
supabase/functions/
  whatsapp/               the bot itself (ADR-014)
```

Both are still a first draft: nothing has been applied to any database yet, so
they were consolidated rather than patched. Once you have run them against a
real project, that stops being true — from then on, add a new numbered file.

## Running it

Install the Supabase CLI, then from `backend/`:

```bash
supabase init
supabase start
supabase db reset
```

`supabase db reset` replays every migration against the local Postgres, so it
is also how you check a new migration before pushing it.

To point at the hosted project:

```bash
supabase link --project-ref <ref>
supabase db push
```

## Adding a migration

Never edit a migration that has been pushed. Add a new numbered file.

```bash
supabase migration new <short_name>
```

## The WhatsApp bot

`supabase/functions/whatsapp` takes a message a participant forwarded from a
class group chat, reads the days and times out of it, and files them in that
participant's `schedule_inbox`. Their phone drains the queue on its next
resume and places the blocks on the week. Nothing else about the message —
the text, the subject, the room, the sender, the group — is kept anywhere.
ADR-014 has the reasoning, `0012` has the rules the schema enforces.

```bash
supabase functions deploy whatsapp --no-verify-jwt
supabase secrets set --env-file .env
```

`--no-verify-jwt` because Meta calls it, not a signed-in client. What stands
in for the JWT is the `X-Hub-Signature-256` check in `index.ts`, and it is not
optional: without it anyone who learned the URL could post a body claiming to
come from a participant's number. The function refuses every POST while
`WHATSAPP_APP_SECRET` is unset rather than running unsigned.

Then point Meta's webhook at
`https://<project-ref>.supabase.co/functions/v1/whatsapp` with the same
`WHATSAPP_VERIFY_TOKEN`, and subscribe to `messages`.

The parser is pure and has no Deno in it, so it runs under plain node:

```bash
node --test supabase/functions/whatsapp/timetable.test.ts
```

**This is not on the cue path.** The function being down, unreachable or never
deployed changes nothing about whether a cue fires (ADR-003). It is a way to
fill in a week that the user could equally have drawn by hand.

## Keys

Copy `.env.example` to `.env` and fill it in. `.env` is gitignored — the
service role key must never reach the repo or the Android app. The app only
ever gets the anon key, which is safe to ship precisely because RLS is on.

The edge function is the one thing that holds the service role key, because
`schedule_inbox` deliberately has no insert policy — a client that could write
there could put a lecture in somebody else's week.
