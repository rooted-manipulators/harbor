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
  0013_whatsapp_inbox.sql pairing a number, and the queue the bot fills
supabase/functions/
  whatsapp/               the bot itself (ADR-014)
```

**Everything through 0013 is applied to the hosted project** as of 2026-09-18.
That ends the "nothing has been pushed yet, so consolidate rather than patch"
era this file used to describe: from here on, never edit a migration that has
been applied — add a new numbered one.

Two things about that database are not true of this folder, and both will bite
somebody who assumes `supabase db reset` reproduces production:

- **`calendar_events` has no file here.** It was applied directly to the hosted
  project (as `20260915210140`, with `20260916154354` after it) and never
  committed. Until someone exports it, this folder does not describe the
  database. It also overlaps `schedule_inbox` — see ADR-014.
- **0011 and 0013 are recorded under timestamp versions**, not `0011` and
  `0013`, because they were applied through the Supabase API rather than the
  CLI. A `db push` will therefore try to run both files again and fail on
  `type "block_kind" already exists`. Fix the bookkeeping once:

  ```sql
  update supabase_migrations.schema_migrations
  set version = '0011' where version = '20260917231418';
  update supabase_migrations.schema_migrations
  set version = '0013' where version = '20260918095721';
  ```

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

**Where this stopped, 2026-09-18.** Deployed and half-configured. Picking it
up cold means doing exactly one thing: fixing the app secret.

| | |
| --- | --- |
| Function `whatsapp` | deployed, ACTIVE, `verify_jwt` off |
| Schema (0013) | applied |
| Webhook handshake | **verified with Meta** — a GET came back 200 |
| `WHATSAPP_VERIFY_TOKEN` | set, and proven working by that 200 |
| `WHATSAPP_APP_SECRET` | set, but **does not match Meta's** — every POST 401s |
| `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_ID` | not set; no number provisioned yet |

Nothing replies while the last two are unset — `reply()` returns early — but
the bot still reads, parses and files. That is the designed state, not a
degradation.

Two notes for whoever resumes:

- **Secrets reach a running function immediately.** No redeploy is needed; the
  verify token was read live on the request that succeeded. So a 401 is a
  wrong *value*, never a stale one. The usual cause is the App ID (all digits)
  copied instead of the App Secret (32 hex characters).
- **The verify token is currently `harbor-hook-2026`**, which was an example
  string typed into a chat and should be replaced with something random in both
  Meta and Supabase. It only guards the subscription handshake — nobody can
  write data with it — but it should not stay guessable.

A **test account** is seeded for seeding's sake, not for the study:
`00000000-0000-4000-8000-000000000001`, `cohort = 'test'`, pairing code
`TEST99`, unpaired. `probe.mjs` beside the function impersonates Meta with a
correctly signed body so the whole inbound path can be tested with no number.
Remove the account with one statement — `on delete cascade` does the rest:

```sql
delete from auth.users where id = '00000000-0000-4000-8000-000000000001';
```

`supabase/functions/whatsapp` takes a message a participant forwarded from a
class group chat, reads the days and times out of it, and files them in that
participant's `schedule_inbox`. Their phone drains the queue on its next
resume and places the blocks on the week. Nothing else about the message —
the text, the subject, the room, the sender, the group — is kept anywhere.
ADR-014 has the reasoning, `0013` has the rules the schema enforces.

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
