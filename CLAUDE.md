# Harbor

An Android app that catches the still moment after you stop walking and makes
it easy to call a parent. Built by a small team at BITSoM; the near-term goal
is a one-week study with real users on real phones.

Read `docs/00-product.md` for what it is, `docs/01-decisions.md` before
changing architecture, and `docs/slack-tide-handoff.html` for the original
pipeline spec.

There is a second repository — the v0-built web prototype at
`Bored-Kxiden/harvest-pulse` — which is the **authority on product
behaviour**. It does not merge with this one; it is ported from, against a
pinned commit. `docs/04-how-the-repos-fit.md` explains the arrangement, and
you should read it before porting a screen or touching the model.

## Repo layout

```
app/                  the Android app (Kotlin + Compose). Gradle root is the
                      repo root, so open THIS folder in Android Studio.
backend/supabase/     Postgres schema as migrations. No application server.
docs/                 product, decisions, study protocol, original handoff.
```

## Hard constraints — do not violate these without changing the ADR

- **Raw activity data never leaves the device.** Only ledger entries sync.
  (ADR-004.) This is the promise the permission screen makes.
- **No parent-side read path.** No table, role, or RLS policy that lets one
  user see another's data. (ADR-004.) The parent gets no app, no web surface,
  and no account — Conversation is one-sided and Harvest is self-entered.
  (ADR-007.)
- **The cue must fire offline.** Nothing in stages 1–4 may block on network.
  (ADR-003.)
- **Every cue is dismissible in one gesture, at no cost.** No streaks, no
  breakable chains, no forced binary choice. These are gate-risk mitigations
  from the design audit, not style preferences. (Handoff, section 7.)
- **Thresholds are user-set.** Ship a suggested default; never lock it.
- **No location, ever.** No route tracking, no `ACCESS_FINE_LOCATION`, no
  fitness integration. The explainer screen promises "not where you are", and
  that promise is why people grant the activity permission. (ADR-010.)
- **Do not build against Google Fit.** It is being retired. The walking signal
  is the Activity Recognition Transition API, which is a different API.
  (ADR-005.)
- **Do not fork Signal / Linphone / Jami to place the call.** Harbor is
  standalone. The parent installs nothing, and the call is a plain cellular
  one placed by the phone's own dialer. (ADR-002.)
- **Do not add `CALL_PHONE` or `READ_PHONE_STATE`.** Harbor hands off with
  `ACTION_DIAL` and asks the user whether the call happened, which needs no
  permission. Activity recognition is already the biggest funnel risk; a
  phone-and-call-log prompt beside it is the worst place to spend more trust.
  (ADR-002, amended.)

## Working here

**Gradle cannot be run from Claude Code's Bash tool on this setup.** The
daemon forks a process and connects over loopback, which the sandbox blocks
(`Unable to establish loopback connection`). Builds happen in Android Studio.
Do not try to work around this — just ask the human to build.

**You can still compile-check and run unit tests without Gradle.** Android
Studio ships a standalone Kotlin compiler, and the Gradle cache already holds
JUnit and coroutines. This catches real errors in the domain and data layers
in seconds instead of waiting for a human to open Studio:

```bash
export MSYS_NO_PATHCONV=1
KLIB="C:/Program Files/Android/Android Studio/plugins/Kotlin/kotlinc/lib"
AJAR="$LOCALAPPDATA/Android/Sdk/platforms/android-37.0/android.jar"

# compile (add -cp entries for any library the file imports)
java -cp "$KLIB/kotlin-compiler.jar" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler   -d /tmp/out -cp "$AJAR" -jvm-target 11 app/src/main/java/app/harbor/domain/*.kt

# run pure JUnit tests
java -cp "/tmp/out;$JUNIT_JAR;$HAMCREST_JAR;$KLIB/kotlin-stdlib.jar"   org.junit.runner.JUnitCore app.harbor.domain.CuePolicyTest
```

This is a check, not a build — it proves the code compiles and the pure logic
is correct. It says nothing about resource linking, manifest merging, or
anything that needs the real toolchain. A green run here still needs a Studio
build before it means the app works.

`adb` does work. It lives at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
Export `MSYS_NO_PATHCONV=1` first or Git Bash mangles `/data/...` paths. Do not
leave the emulator screen off after a test — launching an activity with the
screen off ANRs and looks exactly like a black-screen bug.

**Installing the CI artifact over a Studio build fails**, and the error is
worth recognising because the symptom looks like a broken app:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: ... signatures do not match
```

Studio signs with your local debug keystore, CI with the runner's. Uninstall
first, then install — `adb install -r` will not do it.

**Fixed as of `app/debug.keystore`.** Gradle used to sign debug builds with
whatever throwaway key sat in the builder's home directory, so no two machines
— and no two CI runs — agreed. `signingConfigs.debug` now points at a keystore
checked into the repo, and every build shares one signature, so `adb install
-r` works and keeps the data.

That key is the stock Android debug identity with the published password
`android`; it is committed precisely because it is worthless, and Play rejects
anything signed with it. Release signing is a separate key and does not belong
in this repo.

The rest of this section applies to any build made **before** that change, and
to installing over one:

```sh
adb -s emulator-5554 shell run-as app.harbor \
    cat /data/data/app.harbor/shared_prefs/harbor.xml > prefs.xml
# ... uninstall, install, launch once so shared_prefs/ exists, force-stop ...
adb -s emulator-5554 push prefs.xml /data/local/tmp/h.xml
adb -s emulator-5554 shell run-as app.harbor \
    cp /data/local/tmp/h.xml /data/data/app.harbor/shared_prefs/harbor.xml
```

Restore while the app is stopped — SharedPreferences is cached in memory and a
running process will overwrite the file on its next write. And `run-as ... sh
-c '...'` does not survive the trip through `adb shell`: the quotes are eaten
locally and the remote shell sees the words as separate arguments. Run each
command on its own.

**Why this mattered enough to fix.** Handing participants a new build mid-week
would have wiped their garden — the one thing the study measures. If you ever
change the signing config, that is the consequence to weigh.

A Studio deploy that dies midway leaves the package installed and marked
`TEST_ONLY` with `notLaunched=true`. In that state `am start` returns
`result code=0` and **no process ever spawns**, with nothing in the crash
buffer. It looks exactly like an app that crashes on launch. Check
`dumpsys package app.harbor | grep flags=` before believing it is your code.

**Give Harbor its own AVD.** Sharing one with the sibling Glow project ran it
out of memory — the low-memory killer started killing system apps, the
emulator went `offline` mid-install, and every adb command hung.

**This repo must stay outside OneDrive.** Gradle's file churn plus OneDrive
sync produces file-lock build failures. That is why it lives in
`AndroidStudioProjects/` and not in `Documents/`.

The Gradle toolchain (AGP 9.4, Gradle 9.6, Kotlin 2.2.10 bundled into AGP 9 —
there is no separate `kotlin-android` plugin, compileSdk 37 via the
`compileSdk { version = release(37) }` block) was generated by the Studio
wizard. Let Studio update it; do not hand-edit version numbers.

## Backend

Supabase, no application server. The app uses the anon key and RLS does the
authorisation. `backend/README.md` has the CLI commands. Never edit a pushed
migration — add a new numbered one. The service role key never goes in the
app, in CI, or in chat.

## Branches and PRs

`main` is protected. Branch as `<yourname>/<short-thing>`, e.g.
`devansh/threshold-calibration`. One PR per thing, and fill in the template —
the "which guardrail does this touch" line is there because the guardrails are
the part most likely to be eroded by accident.

## Current state

v0.1 targets the **whole prototype** (harvest-pulse), not Slack Tide alone.
The prototype is the reference for product behaviour; `docs/02-ui-reconciliation.md`
records how each disagreement with the handoff was settled.

Done:

- Package `app.harbor`, spelling settled as **Harbor**.
- `domain/Model.kt` — the model, mirroring the Postgres schema by name.
- `domain/CuePolicy.kt` — pipeline stages 2-4 as one pure function. No IO, no
  Android, no clock of its own. 24 unit tests, all passing.
- `data/` — `HarborRepository` with a SharedPreferences + `org.json`
  implementation. Deliberately not kotlinx-serialization: that needs a
  compiler plugin version-locked to Kotlin, which has bitten this team before.
- `backend/` — migrations 0001-0002, aligned with the prototype at `e60eef7`.

- `sensing/` — stage 1. `BoutTracker` is a pure state machine over the
  transition stream (12 tests); `TransitionReceiver` runs it and calls
  `CuePolicy`. No foreground service — see ADR-008.
  **Not yet proven on a device.** No real transition has ever reached it —
  `harbor_sensing.xml` does not exist on any phone yet. An emulator will not
  produce walking transitions, so this needs someone to install the app and
  go for an actual walk.

- `ui/CuesSetupScreen` — the permission and privacy explainer, and the switch
  that enables cues. Currently the whole of `MainActivity`. **Verified on an
  emulator 2026-09-10**: renders, requests the permission, and persists
  `cues_enabled: true` — which is only written when Play services actually
  accepts the transition registration, so that path is proven too.

- `cue/` — stages 5-9. `CueActivity` is the call-shaped surface; `CueNotifier`
  posts it with a full-screen intent and degrades gracefully; `Ringer` loops
  the contact's sound. See ADR-009.

- `ui/PersonScreen` + `ui/DayClock` + `domain/DayArcs` — a person's page,
  rebuilt around a dial. The day is drawn as a **twenty-four hour** clock
  (one turn is one day, so an arc means one stretch of it and not two) with
  the week's own two marks bent round it: a thorned arc over busy hours, a
  blooming one over hours kept free, and nothing over hours nobody marked.
  Press a free arc, drop a flower on it, push it round to pick a time, and
  Done writes the same `PROPOSED_LATER` row that tapping "later" on a cue
  writes — so the hold, the card on home and the study's export all work on it
  already. Dragging it into a thorn or off the end buzzes and will not move.
  `DayArcs` is pure and has 21 tests; the drawing and the platform live apart
  from it.
  **The dial shows *your* week, not theirs.** Harbor holds one week. Reading
  somebody else's needs `domain/Sharing` (ADR-013), which exists with no
  caller — the seam for it is one argument wide and is documented at the top
  of `PersonScreen`. Do not relabel the dial as theirs without wiring that.

- `ui/ContactScreen` — who the cue is about: name, number, ringtone, photo.
  No permissions: the system ringtone picker, `ACTION_GET_CONTENT` for the
  image, and a copy into app storage. Do not "improve" this with
  `READ_CONTACTS`.

- `backend/supabase/functions/whatsapp/` + `data/WhatsAppInbox.kt` — the bot a
  participant forwards a class group message to, so a week can stay current
  without anybody redrawing it (ADR-014). It proposes into `schedule_inbox`
  and the phone places the blocks; it is off the cue path and off by default.
  **Not yet run against a real WhatsApp number** — the parser and the ingest
  chain are tested, the webhook has never been called by Meta.

Not built yet: threshold calibration, the Garden/Jar, the other prototype
screens, and Supabase sync.

**The explainer's copy is a promise the code has to keep.** Every claim on
that screen — movement never leaves the phone, nothing shared with family,
dismissing costs nothing — maps to something enforced elsewhere. If you change
what syncs, that screen changes in the same PR.

A fired cue is recorded but **nothing shows it to the user yet** — that is
build-order item 5. The cue is already counted against the daily cap when it
is recorded, so whatever surfaces it must not record a second one.

Two things to know before touching the pipeline:

- The daily cap counts **cues**, not ledger entries. A cue the user swiped
  away still spent one. That is why `cues` is its own table and its own
  `recordCue` call.
- **The device owns identity.** Rows the app creates carry a UUID the phone
  generated, used directly as the primary key. Never add a server-generated
  id: it makes foreign keys between synced rows unfillable, because the device
  cannot reference an id it has never read back.
- The prototype is pinned at a commit, not tracked live. See
  `docs/02-ui-reconciliation.md` before assuming its current `main` is the
  spec.
- **The cue evokes a call; it must never claim to be one.** No "Mom is
  calling", no answer/decline pair, no mimicry of the system call UI. A
  student who thinks their mother is unexpectedly ringing assumes an
  emergency. (ADR-009.)
- The feedback pulse **amends** the cue's ledger entry rather than writing a
  second one — the entry id is generated once and reused, which makes `append`
  an idempotent replace.
- Cues are **off by default** and stay off until the user turns them on behind
  a privacy explainer. Any code path that could flip that on without an
  explicit user action is a bug.
