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

**These have been applied.** The line that used to sit here said nothing had
been run against any database yet and that migrations could therefore still be
consolidated rather than patched. That stopped being true on 18 Sep 2026 and
the line went on being read — it is what sent somebody looking for a way to
push `0013` that was already live. Every migration through `0013` is on the
`calling` project (`vxtufvcpirdjtrmidter`, ap-south-1) with one exception noted
below. From here, **never edit one; add a new numbered file.**

### The remote numbering does not match this folder

`0001` to `0010` went up under their own names. The four after them were
applied through the dashboard or with `supabase migration new`, so the remote
`schema_migrations` records them with timestamps instead:

| this folder | how the database has it |
| --- | --- |
| `0011_people_and_sharing.sql` | `20260917231418 people_and_sharing` |
| `0013_whatsapp_inbox.sql` | `20260918095721 whatsapp_inbox` |
| — | `20260915210140 calendar_events` |
| — | `20260916154354 harden_calendar_timestamp_trigger` |

Two consequences, and the first will bite you.

**`supabase db push` from this folder will try to re-run `0011` and `0013`,**
because the versions it looks for are not the versions that are recorded. It
will fail on `relation already exists`. Reconcile before pushing:

```bash
supabase migration repair --status applied 0011
supabase migration repair --status applied 0013
```

**`calendar_events` and the trigger hardening exist only on the server.**
Nothing in this folder creates them, so a fresh project built from these files
alone comes out different from the one we are running. Whoever wrote them
should bring them back as numbered files.

### `0012` is the one that has not been applied

`0012_cooldown_may_be_nothing.sql` widens
`user_settings_cooldown_minutes_check` to allow zero. The database still has
the original `cooldown_minutes >= 1`, while the app ships
`Thresholds.SUGGESTED.cooldownMinutes = 0`. Nothing syncs settings yet, so this
is latent rather than broken — but the first settings row that goes up with the
suggested default will be rejected.

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
