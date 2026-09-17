# Before you give this to people

A readiness list, written from the code as it stands rather than from
intentions. Everything marked **unproven** has genuinely never run on real
hardware; everything marked **hole** is a thing the study would measure wrongly
if it ran tomorrow.

**Harbor is now an online *and* offline app** (ADR-013). That changes this list
in two directions at once. The offline core is no longer just how the app works
— it is a claim that now has to be tested deliberately, because it is possible
to break it by accident. And there is a whole second surface, the account and
the sharing, which has never spoken to a real server.

The short version, still: **do not run the full cohort until one real
walking-stop cue has fired on a real phone with the radio off, and until the
app can tell you whether sensing was alive.** Everything else on this list is
smaller than those two, including all of the online work.

---

## 1. The measurement hole that matters most

`Sensing.isActive` is `cuesEnabled && hasPermission`. That is all it is. It
does not know whether Google Play services ever registered the transitions,
whether the receiver is still alive, or whether the OS put the app to sleep
three days ago.

`SensingStore` keeps exactly one thing — `walking_since`. Nothing anywhere
records *the last time the system told us anything at all*.

So at the end of the week, a participant with zero cues is indistinguishable
between:

- the trigger works, and they simply never took a ten-minute walk;
- the trigger works, fired, and they dismissed it before it registered;
- Play services never delivered a single transition and the app was dead from
  day one, while telling them "Cues are on" the whole time.

The study's first question is *does the walking-stop trigger land at moments
people call good*. With this hole, a null result cannot be interpreted — and a
null result is the likeliest outcome on aggressive Android skins.

**Fixed.** `TransitionReceiver` now stamps `lastTransitionAt` on every batch,
whatever the transition — the question is whether the pipe is alive, not what
came down it. The cues screen shows it ("last noticed you moving 20 minutes
ago"), and says so plainly when it has been silent longer than a night. The
export carries it as `last_transition_at`, so silence in the data can be told
apart from silence in the world.

`Liveness` holds the judgement about when silence is worth mentioning: twelve
hours, because a night is legitimately quiet — becoming still at bedtime is one
transition and there is nothing to report until morning.

What this does **not** do is make sensing work. It makes a failure visible,
which is the difference between a null result you can publish and one you
cannot.

---

## 2. Never run on real hardware

The whole sensing path has been exercised only by unit tests and by manual
cues. As of now, on a real phone:

| | Status |
| --- | --- |
| A real `walking_stop` cue firing | **unproven** — every cue ever recorded on a real device has been `manual` |
| The settle re-check firing the held cue | **unproven** — the alarm path is new and has never run outside tests |
| The cue appearing over the lock screen | **unproven** |
| The ringtone actually playing from a cold, pocketed phone | **unproven** |
| The heads-up fallback when full-screen intent is refused | **unproven** |
| Sensing surviving a reboot (`BootReceiver`) | **unproven** |
| Sensing surviving a day of Doze and app standby | **unproven** |
| The export writing a file through the system save dialog | **unproven** on a phone |

The first one is the study. The rest are the difference between "it fired" and
"it fired and they saw it".

The settle row is worth its own line because it used to be worse than unproven.
`CuePolicy` holds a cue until the person has been still for `SETTLE`, and Play
services delivers the STILL transition within seconds of detecting it — always
*inside* that window. Every sensed cue was held once and then dropped, with
nothing scheduled to ask again, so no real walk could ever have produced a
reminder. `SettleAlarm` and `SettleReceiver` park the signal and re-decide when
the stillness is genuinely as old as the window claims. That is a fix to a
deadlock, not a proof that the pipeline works; it removes the reason the first
row *could not* pass.

**Test protocol for one person, before anybody else gets it:** set it up, walk
for twelve unbroken minutes with the phone in a pocket and the screen off,
stop, and wait two minutes without touching the phone. Then reboot, repeat the
next day, and leave it untouched overnight to see whether it still works on day
two. That last part is where OEM battery management usually shows up.

---

## 3. The online half has never spoken to a server

Not "lightly tested" — **zero sockets**. `SupabaseClient` has never made a
successful request in any environment, and the current build cannot make one:
`harbor.supabaseAnonKey` in `gradle.properties` is empty, so `configured` is
false and every call returns null without touching the network. That is a
deliberate supported state (the app behaves exactly as it did before it had a
server), and it also means **today's build is the fully offline app** no matter
what the manifest permits.

What exists in code, and what does not:

| | Status |
| --- | --- |
| `0011_people_and_sharing.sql` applied to a project | **never run** — tables, enums, RLS and `request_link`/`answer_link` are all untested SQL |
| Signing in — email code, Google, Microsoft | **written and wired** (`SignInScreen`, `MainActivity` hands the `harbor://auth` redirect to `completeSignIn`), never executed against a server |
| Asking someone, answering, reading their week | **no UI** — `ask`, `links`, `weekOf` and `putWeek` exist on the client and have no caller |
| The ledger actually syncing | **not wired** — `unsyncedCues`, `unsyncedEntries` and `markSynced` are on the repository and nothing drives them |
| The offline core with the radio off | **unproven since the change** — this is the regression that would matter most |

Two of these are not the same kind of gap. No sharing UI and no ledger sync are
*features that are not finished*. The last row is a **guarantee that might
already be broken** and nobody has looked.

### The verification plan, in order

Each step is chosen to be cheap and to fail loudly before the next one depends
on it.

1. **`supabase db push` with `0011`.** Needs no OAuth, no SMTP, no app. It is
   the cheapest place for a schema or policy error to show up, and everything
   below assumes it worked.
2. **The offline core, radio off, signed out.** Airplane mode, a real walk,
   and then: does it sense, hold, settle, fire, record, and write the study
   file? This comes *before* any online testing on purpose. It is the
   regression gate on the whole direction change, and nothing else will reveal
   a network await that crept into the cue path. ADR-013 accepted the network
   as a new failure mode on the condition that it never reaches stages 1–9;
   this is the step that checks the condition was kept.
3. **`requestCode(email)` against a live project.** Proves the URL, the anon
   key, TLS through whatever network the phone is on, and the shape of the
   GoTrue call — and it proves all of that *without needing the mail to
   arrive*, because a `200` here means the server accepted the request. This is
   the first real socket, and it can be done today.
4. **A full sign-in, once codes are actually delivered.** Blocked on the
   sending identity (below), not on code. Then, with two accounts: ask,
   accept, read the week back, revoke, and confirm the next read fails.
5. **The boundary cases**, which are where an online-and-offline app actually
   breaks:
   - sign in, then go offline and restart the app — does anything block?
   - let the session expire while offline — does the app degrade or stall?
   - revoke a link from the other side and read again — the policy is
     evaluated per query and cached nowhere, so this should fail immediately.

Steps 1–3 can all happen before the sending identity exists. Only step 4 waits.

---

## 4. Signing in

**Email verification is the main path.** Not a fallback and not the third
button: it is the route that needs no provider configured, works for a
university address, for Yahoo, for anybody, and it is what the study should
assume participants will use. Google and Microsoft stay as convenience
(ADR-013 explains why `azure`, and why the browser rather than a WebView), but
nothing should be designed on the assumption that a participant has either.

**The sending identity is not ready yet.** Codes cannot be delivered until the
address Harbor sends from exists and is configured as SMTP in the Supabase
project. Until then step 4 above cannot run, and that is the single thing
gating the online half — the app code for it is written.

Two consequences worth planning for rather than discovering:

- **Delivery is the failure mode, not the code.** A spam filter, a university
  mail rule or greylisting locks somebody out of the account layer entirely,
  and there is no second channel to reach them on. Supabase's default sending
  identity is rate-limited and shared; a real sender on a domain the team
  controls is the difference between "a few codes arrive late" and "half the
  cohort never gets one".
- **Lockout must stay survivable.** Someone who never receives a code has to
  still be a usable participant: the offline core works signed out, and it has
  to keep working signed out. That is step 2 again, and it is why step 2 is
  run signed out.

**Phone sign-in with an SMS code is wanted, and is deferred on cost, not on
merit.** It reads better than email for an app about ringing people — the
number you would type to add your aunt is the number that finds her. It means
paying an SMS provider per send and per retry, and during development, before
anybody has agreed the feature earns its keep, that is a running bill for a
thing nobody has validated. So it is not being built now. When it is built it
is a *second* route alongside email, not a replacement: email verification
stays the path that always works.

---

## 5. Holes in the data itself

**`called` is recorded at the moment of dialling, not after the call.**
`CueActivity.placeCall` writes `Resolution.CALLED` before the dialer even
opens. Someone who taps *Call now*, sees the dialer, thinks better of it and
presses back is recorded as having called. There is no way to correct it —
the reflection asks how the call felt, not whether it happened.

The study's second question is the distribution across called / reacted /
proposed-later / dismissed. This inflates `called` by exactly the number of
people who changed their mind, which is not a small number.

**Fixed.** The first reflection step now offers *we did not get to talk*, which
rewrites the row to `Resolution.NOT_REACHED` and ends the flow — nothing to
plant, nothing to ask about. It is kept separate from `dismissed` on purpose:
dismissing is declining the cue, this is accepting it and coming away with
nothing, and the second question is exactly the distribution those two sit in.
`0007_not_reached.sql` adds the value to the Postgres enum to match.

`docs/03` promises to write this up as "reported a call" rather than a measured
one. That phrasing only stays honest because there is now a way to report
*no*.

**No crash reporting and no telemetry, by design.** If Harbor crashes on a
participant's phone on day two, nobody finds out until the debrief, and the
week is gone. That is the correct privacy posture and it has a cost: plan to
check in with people mid-week rather than assuming silence means it is working.
Having a server does not change this — the server is for the account and the
shared week, and putting error reporting on it is a separate decision that has
not been taken.

---

## 6. Device and OS risk

Recruit for this deliberately — `docs/03` already says so, and it matters more
than it sounds.

- **One UI, MIUI, ColorOS** put unused apps to sleep aggressively. Harbor runs
  no foreground service on purpose (ADR-008), which is the right call for
  battery and for not being creepy, and it is exactly what makes it vulnerable
  to being killed.
- **Battery optimisation exemption is never requested.** Consider asking for
  it during onboarding on the devices that need it, or at minimum walking
  participants through the OEM setting by hand at setup. An app that is asleep
  is not a trigger that failed.
- **Android 16 / SDK 36** is what the test phone runs. Older devices in the
  cohort are a different code path for notifications and full-screen intents.
- The cue uses a **full-screen intent**, which recent Android versions restrict.
  Check the fallback on every OEM in the cohort, not just one.
- **The settle re-check is an inexact alarm** (`setAndAllowWhileIdle`, chosen
  over `SCHEDULE_EXACT_ALARM` so the install funnel does not grow another
  permission). In Doze it fires late. Late is fine for a reminder and fatal for
  a test: if you are checking whether the cue works, give it minutes, not
  seconds, before calling it broken.

---

## 7. Getting the data back

The app now holds `INTERNET` and can reach a server, but **the study file is
still the only route the data actually takes**. Nothing syncs the ledger today
— the seam exists on the repository and has no caller — and the ledger is not
what sharing was built for anyway: ADR-013 shares the shape of a week and
nothing else. Who you called and how it felt does not cross the wire.

So the collection plan is unchanged, and should stay unchanged for this study
even if sync lands mid-build. One route, tested, is worth more than two routes
where nobody is sure which one ran.

- **Collect it in person, at the debrief.** An instruction to send it later
  will lose the tail of the cohort.
- **An uninstall, a factory reset, or "clear data" destroys the week.** There
  is no backup and no server copy. Tell people not to clear the app, and get
  the file before anything else happens to the phone.
- **Only the last 90 entries are retained.** Fine for a week at a cap of two a
  day; worth knowing before anyone runs a longer study.
- One file per phone, named by day and a short opaque participant id. Check
  you can actually open and parse one *before* the cohort, not after.

---

## 8. The human side

- **Consent in writing, before install** — already the plan in `docs/03`. It
  has to be rewritten for the app as it now is, and this is ADR-013's first
  precondition, not a formality. The old sheet describes an app that could not
  send anything anywhere, which was verifiable by reading the dependency list.
  The new one says: what is sensed and that the raw stream never leaves the
  phone; that there is now an account and what it holds; that the only thing
  ever shared with another person is the busy-or-free shape of a week, only
  after they asked and you said yes, and only until you revoke it; that the
  ledger, the garden and anything about whether a call happened are never
  shared; and that the file they hand over carries no names, numbers or words.
- **Re-consent anyone who agreed to the offline app.** Running a syncing build
  against people who agreed to the other one is not a thing to do quietly.
- **Account deletion does not exist in the app.** `on delete cascade` is in the
  schema and there is no way to ask for it from a phone. ADR-013 lists this as
  a precondition; a study that creates accounts for participants and cannot
  delete them on request is the wrong way round.
- **The copy has never been read by anyone but its author.** The permission
  explainer is the single biggest install-funnel risk in the product and I
  wrote it. Get a person who is not on this team to read it cold. The sign-in
  screen now needs the same treatment.
- **What the participant tells their parent** is not nothing. Someone who
  starts calling home daily will be asked why. Decide whether you want that in
  the debrief questions; it is probably the most interesting thing the study
  could learn and it is not currently asked.
- **A cue during something awful.** Activity recognition cannot tell a walk
  home from a walk out of a hospital. Nothing in the app can fix that, but the
  debrief should ask whether a cue ever landed badly, and the consent should
  make clear it can be turned off in one tap.

---

## 9. Rough order to fix

**Before anyone outside the team installs it**

1. ~~Record and surface `last_transition_at`, and carry it into the export.~~
   Done — shown on the cues screen, and in the export as `last_transition_at`.
2. ~~Add the *we did not get to talk* escape.~~ Done — `Resolution.NOT_REACHED`,
   with `0007_not_reached.sql` to match.
3. ~~Fix the settle deadlock.~~ Done — `SettleAlarm` re-asks once the stillness
   is old enough, so a held cue is no longer a dropped one.
4. **Get one real `walking_stop` on one real phone, in airplane mode.** Still
   the open one, still the thing the week depends on, and now it doubles as the
   offline-core regression test (§3, step 2).
5. **Push `0011`** (§3, step 1) and **prove one live call** with
   `requestCode` (§3, step 3). Neither needs the sending identity.

**Before the full cohort**

6. Sending identity configured, then a real sign-in and a two-account
   ask/accept/read (§3, step 4) and the boundary cases (§3, step 5).
7. Account deletion in the app, and the rewritten consent sheet.
8. Run a two-person, two-day pilot including an overnight and a reboot.
9. Battery-optimisation guidance or prompt, per OEM.
10. An outside read of the permission, privacy and sign-in copy.
11. Ship the real typefaces — the app currently uses whatever the phone has, so
    it looks different on every device in the cohort.

**Nice, not blocking**

12. Phone sign-in by SMS code, when somebody is willing to pay for it (§4).
13. Sharing UI — asking, the inbox, reading a week — and whatever decides
    whether the ledger syncs at all.
14. Onboarding currently restarts at the welcome screen if it is backgrounded
    half way. Saved data survives, so it is harmless, but it is untidy on a
    first run.
15. `GardenCanvas` is dead code since the field landed.

**Decide, do not drift into**

16. Whether the study cohort signs in at all. Everything the week measures
    works signed out, and an account is a thing that can fail in front of a
    participant on day one. Shipping the study build with the anon key left
    empty is a legitimate answer, and it is the current state by default rather
    than by decision — which is the part worth fixing either way.

---

## 10. The pilot is the thing

Two people, two days, one of them on a Samsung or Xiaomi, with a reboot and an
overnight in the middle, and at least one stretch with the radio off. Then read
their exports with the same script you will use for the real thing.

Nearly everything on this list is the kind of problem that a two-day pilot
surfaces immediately and that a one-week cohort surfaces only in the debrief,
when it is too late to do anything but write it up.
