# Master context

Written for a teammate opening a fresh Claude Code session against this repo.
Everything in it was checked against the running code and the actual CI
history on 15 Sep 2026, not recalled from memory — where something is a
belief rather than a checked fact, it says so.

This is a supplement to `CLAUDE.md` and `docs/01-decisions.md`, not a
replacement. Those two are still the authority; this is the map of how
today's twenty-one usability changes land on what they describe.

---

## What Harbor is

An Android app that catches the still moment right after a walk ends and
offers, once, to call a parent. Built for a one-week usability/data study,
not for a store listing.

The shape of the product is a small number of hard promises, not a feature
list:

- The cue (now called a **reminder** in every screen — see below) is an
  offer, never a demand. Dismissing one costs nothing: no streak, no score,
  nothing that goes down.
- The parent installs nothing, is never contacted by the app, and is never
  told anything about whether their child answered.
- Nothing about where the phone is or was is ever sensed or stored.
- The device is authoritative. There is no server the app depends on to
  function — see Architecture, below.

Read `docs/00-product.md` for the fuller pitch and `docs/01-decisions.md` for
why each promise is a promise and not just a design choice.

---

## Current architecture

**Two repositories, deliberately kept apart:**

- **This one** — `AndroidStudioProjects/harbor` — the Android app, Kotlin +
  Jetpack Compose, plus a `backend/` folder holding Supabase SQL migrations
  that have never been applied to any real database.
- **`Bored-Kxiden/harvest-pulse`** — a v0-generated Next.js prototype. It is
  still **the behaviour spec**: where the two disagree about what something
  *does* (not how it looks), `lib/harbor/model.ts` wins. It says nothing
  about how anything looks any more — the app has gone through two full
  visual languages since the prototype was ported.

**Inside the app, three layers:**

- `domain/` — pure Kotlin, no Android imports where avoidable. The model,
  the field/garden geometry, the flower library, the reminder policy, the
  telemetry vocabulary. This is what the unit tests exercise, and it is the
  only layer that runs outside an emulator.
- `data/` — `HarborStore` (the one `HarborRepository` implementation,
  backed by `SharedPreferences` and JSON, not a database), `LedgerJson`
  (the wire format), `StudyFile` (writes the week's export).
- `ui/` + `cue/` + `sensing/` — Compose screens, the full-screen reminder
  surface and its own activity, and the Android `ActivityRecognition`
  plumbing.

**No server dependency.** The app holds **no `INTERNET` permission** and has
no HTTP client in its dependencies at all (checked: no Retrofit, OkHttp, or
Supabase SDK anywhere in `app/build.gradle.kts`). Nothing the app records can
ever reach a server. This is ADR-004 and ADR-003 made physically true rather
than merely promised — see "Things not to change."

**The one-week study's data path**, concretely: `Telemetry` records category
beats (never content) → `HarborStore` appends them to a capped local ledger
→ `StudyFile` writes the whole bundle to a JSON file in the phone's
**Downloads** folder on every app pause → a human collects that file by hand.
That file is the entire data-collection mechanism. The Supabase migrations
exist for a possible future sync layer and are inert today.

**Build:** Gradle cannot run in this sandbox. **GitHub Actions is the only
way to know whether something compiles** — push, then
`gh run list --branch dark-reskin`, then pull the `harbor-debug-apk`
artifact off a green run and `adb install -r` it. Budget for a ~2 minute
round trip on every change; there is no faster loop.

---

## Important components

Named because they come up constantly, or because their exact shape is easy
to get subtly wrong:

- **`domain/Model.kt` → `FlowerKind`** — the shelf of things a call becomes.
  Renamed twice (see History). `FlowerKind.stored()` is the compatibility
  shim that lets old ledger rows written under either earlier name keep
  reading; **extend it, never replace it** when the shelf changes again.
- **`domain/Windows.kt`** — the only place "busy" and "free" time are
  reasoned about. `busyAt` currently suppresses; nothing currently requires
  a *free* block to exist. This is the file the free-time-only sensing
  change (still open) will touch.
- **`domain/Telemetry.kt` → `Moment`** — the closed enum that is the whole
  of what the study is allowed to know happened. It can never grow a case
  that carries free text.
- **`cue/CueNotifier.kt` / `CueActivity.kt`** — how a reminder actually
  reaches the screen. Two failure modes here are silent (see Known bugs) and
  the code now surfaces both on the settings screen rather than leaving them
  invisible.
- **`ui/theme/Harbor.kt`** — the shared component vocabulary: `Surface`,
  `PrimaryAction`, `QuietAction`, `TextLink`, `SectionHeader`, `Eyebrow`,
  `Avatar`. Every screen composes from these rather than styling its own
  boxes; a visual change almost always belongs here, not in a screen file.
- **`ui/FieldCanvas.kt` + `domain/Field.kt` + `domain/Terrain.kt`** — the
  garden. `Field.kt` generates the terrain and flower placement per patch;
  `FieldCanvas.kt` is the camera (pan/zoom/fly-to) and the Compose drawing
  loop. Ported originally by reading the prototype's own JavaScript, which
  is why the geometry code reads as unusually exact.
- **`data/StudyFile.kt`** — writes to both the app's private external files
  directory *and* the shared Downloads collection (MediaStore), because the
  private one is invisible to the Files app on Android 11+. This was a real
  bug, found and fixed on 14 Sep.

---

## File structure

```
app/src/main/java/app/harbor/
  MainActivity.kt          composition root: nav state, screen switch, the
                            crossfade between screens, the post-call flower
                            hand-off
  cue/                      the reminder surface: CueActivity (its own
                            Activity, full-screen, shows over the lock
                            screen), CueSurface (the Compose content),
                            CueNotifier (posts it), Dialer (places the
                            call), Ringer (plays the sound), CallFlow (the
                            reflection screens after a call)
  data/                     HarborRepository (interface) / HarborStore
                            (the implementation) / LedgerJson (wire format)
                            / StudyFile (the export)
  domain/                   pure logic — Model, Field, Terrain, Flowers,
                            Windows, CuePolicy, Reminders, Telemetry, Garden,
                            CallStats, StudyExport, DailyQuestion, Liveness
  sensing/                  ActivityTransitions, TransitionReceiver,
                            BootReceiver, BoutTracker, SensingStore
  ui/                       every screen, plus:
    theme/                  Color.kt, Theme.kt, Type.kt, Harbor.kt (the
                            component vocabulary)
app/src/test/java/app/harbor/domain/   unit tests — this is what CI's
                                        "Unit tests" step actually runs;
                                        no instrumented/device tests exist
backend/supabase/migrations/           0001 through 0010, none applied
docs/                                  00 through 08, this file, and 10
```

---

## Design system

One language now, not a pairing of two. In order of how often you'll touch
each layer:

- **Ground and glass** (`ui/theme/Color.kt`). Near-black ground
  (`Paper`, `#0D0E11`). Cards are **translucent**, not composited — white
  held at ~7% laid *over* a lit gradient, not flattened to a grey. This
  matters more than it sounds: flattening it once already shipped and read
  as plain dark mode instead of glass on a sunset, and it took several
  passes to notice why. If you're touching card fills, keep them as alpha.
- **The one accent.** Amber (`Ember`/`Gold`) appears on exactly three
  things: the current tab, the primary action, the selected chip. Nowhere
  else gets colour — anything else that wants attention gets brightness or
  weight instead.
- **The weather is the ground**, on Home specifically. `FieldSky` paints
  the whole screen; the field's terrain draws directly onto it and is
  erased into it with `BlendMode.DstIn` at the bottom edge rather than
  covered by a scrim. Storm runs dark blue to near-black. Every other
  screen (Schedule, Account) is deliberately flat — they do not get the
  weather wash.
- **Type**: one face, Manjari, bundled as three `.ttf` files in
  `res/font/`. Three weights only — Thin, Regular, Bold, no Medium/SemiBold
  — so every style that used to ask for SemiBold now asks for Bold. See
  `Type.kt`'s own doc comment for which specific styles that touched.
- **Shape**: cards at 24dp, everything you press is a full pill. Radii
  collapsed to 8–10dp in the *first* (light, now-dead) reskin; do not
  reintroduce that ladder.
- **Flowers**: drawn geometry, not bitmaps (`FlowerMark.kt`), petals use
  `BlendMode.Screen` (not `Multiply` — that was a real, long-lived bug; see
  Known bugs). Twenty kinds, named for a feeling after a call, colours and
  petal counts in `domain/Flowers.kt`.
- **Icons**: also drawn geometry, never a library or bitmaps — see
  `drawTabMark` in `ui/HarborShell.kt` for the pattern to copy.
- **Motion**: `LocalReducedMotion` is a `CompositionLocal` read by anything
  that animates (card entrance, screen crossfade, the press-scale on
  buttons). Anything new that animates has to check it — it's an
  accessibility setting, not a taste one.

---

## Current state

- **Branch:** `dark-reskin`, PR #3, based on `new-onboarding` (not `main`).
  62 commits ahead of that base as of this writing.
- **CI:** the last commit with a genuine green compile is `4fe4d26`
  (14 Sep). Every commit after that — `f523805` through the current tip
  `f1ac305` — has **not** had a successful CI run. The one run attempted
  against the tip failed in Android SDK setup itself (`sdkmanager` could not
  fetch a package), which is an infrastructure flake, not a code signal —
  it tells you nothing about whether the code compiles. **Re-run CI before
  trusting that anything after `4fe4d26` builds.**
- **Working tree has uncommitted changes right now**, touching
  `ContactScreen.kt`, `CuesSetupScreen.kt`, `HomeScreen.kt`,
  `OnboardingScreen.kt`, `PersonScreen.kt`. These are a real, complete fix
  (verified: every `TextLink` call site in the app checked and consistent)
  for a break that commit `c87ace6` introduced — see Known bugs, next
  section. **Do not discard this diff.** It should be committed before
  anything else, ideally as its own commit, so the branch has a chance of
  a real green run.
- Everything under "Completed usability changes" below is committed, on
  the branch, unpushed-verification aside.

---

## Known bugs

**Live right now:**

- Nothing known in the code. The `TextLink` break this section used to
  describe was fixed from the other end by `9964839`, which dropped the
  `centered` and `underline` parameters `c87ace6` had added; CI has been
  green since.

- **The phone freezes Harbor, so the transition never arrives.** Not a code
  bug and not fixable in code — but it is why nobody had ever seen a
  reminder fire, and it will be true of every participant's phone. Harbor
  has no service of its own (ADR-008) and waits as a cached process. On a
  Galaxy S24+ (One UI, Android 16), One UI froze it about two minutes after
  it was backgrounded (`FreecessController: FZ ... reason: LEV`), and every
  transition delivered while frozen was lost — three real walks on 17 Sep
  produced nothing at all. The only transitions that ever arrived came
  while the app happened to be on screen. Granting the battery exemption
  restored delivery immediately (`UFZ ... reason: broadcast`), which is why
  onboarding now asks for it. **Check this first** when sensing looks
  dead: `adb logcat | grep Freecess`, and
  `adb shell cmd deviceidle whitelist +app.harbor` to rule it out.

**Fixed already, worth knowing about because the fix is non-obvious and a
future change could reintroduce the shape of the bug:**

- **Every flower bloomed the moment a contact existed**, regardless of
  whether any call had ever happened. `Field.cells()` used to plant every
  cell inside a patch unconditionally; it now plants a cell only when a
  per-cell hash falls under a bloom share proportional to `calls`, capped
  at 75%. Four tests in `FieldTest.kt` guard this specifically because nothing
  about a field full of flowers *looks* wrong in a screenshot unless you
  already know the call count behind it.
- **Every flower in the app was a barely-visible dim smudge.** Petals were
  drawn with `BlendMode.Multiply`, which is correct against a light page
  (translucent petals darken where they overlap, like pigment) and is
  catastrophic against the near-black ground the dark reskin introduced
  (anything multiplied by near-black goes to near-black). Now `Screen`.
  Same shape of danger: nothing threw, nothing looked broken, it just
  looked like a small flower.
- **A reminder could be sensed, decided on, and posted, and the person
  would never see anything, with no error anywhere.** Two independent
  silent-failure permissions: `POST_NOTIFICATIONS` (a reminder posted
  without it is discarded by the OS) and the `USE_FULL_SCREEN_INTENT` app-op
  (without it, Android silently downgrades the full-screen surface to a
  background notification). Both are now checked and surfaced with a fix-it
  card on the reminders settings screen — but **this whole path is still
  unverified against a real walk on a real phone**, see below.
- **A sensed stop was held once for settling and then dropped forever.**
  `CuePolicy` holds a cue until the person has been still for `SETTLE`
  (90s), so Harbor does not fire at a traffic light — but Play services
  delivers the STILL transition within seconds of detecting it, so that
  check was always inside its own window and nothing was scheduled to ask
  again. Every sensed cue died there; no real walk could ever produce a
  reminder. Fixed 17 Sep: a hold for `TRANSITION_UNSETTLED` now arms
  `sensing/SettleAlarm` at `stillSince + SETTLE` and the receiver decides a
  second time. Only that one reason arms an alarm — every other Hold is a
  no. Note a force-stop cancels the alarm, so deploying mid-test loses it.
- **Onboarding's preview reminder started a two-hour cooldown.**
  `dayState` counted manual cues in `lastCueAt` while deliberately
  excluding them from the daily cap, so finishing the flow suppressed the
  first real walk after setup. `lastCueAt` now excludes them too.
- **Tapping "later" on a reminder switched sensed reminders off for good.**
  `markReminderDone` was implemented and had no callers anywhere, while
  `dayState` held every sensed cue for as long as any proposed-later row was
  unmarked — across all days. So the first deferral any participant made
  suppressed the trigger for the rest of their week, silently, and the app's
  own promise of "a reminder inside Harbor" had nothing behind it. Fixed
  17 Sep: the hold is now bounded and lives in `domain/Reminders`, a card on
  home closes the loop, reaching the person closes it by itself, and
  `reminder_done` is set only by something the person actually did. The whole
  decision, including the two constants and what was deliberately not done, is
  in `docs/10-the-later-loop.md`. Same shape of danger as the two below:
  nothing threw, nothing looked broken, the reminders simply stopped.

- **The study export file was unrecoverable on a real phone.** It wrote
  only to the app's private external-files directory, which Android 11+
  hides from both the Files app and MTP/USB file transfer. Fixed by also
  writing to the shared Downloads collection via `MediaStore`.

**Not yet proven, because nothing has driven the real path:**

- A reminder firing from an actual sensed walking-stop, on a real phone,
  with all three permissions granted. Verified only via the in-app "show me
  a reminder now" test button, which bypasses sensing entirely.
- The post-call sequence (land on Home → camera flies to the patch →
  flower opens) end to end, off a real phone call. The timings in the code
  are estimates, not calibrated against a real device.
- Every Supabase migration. None has been applied to any database, ever.
  Not urgent — see Architecture, the app cannot reach one anyway.

---

## Completed usability changes

Cross-referenced against the nineteen items sent after usability testing on
15 Sep. Commit hashes are on `dark-reskin`.

- **"Cue"/"notification" → "reminder", everywhere a person reads it**
  (`f523805`). Deliberately **did not** rename the internal identifiers
  (`Cue`, `CueActivity`, `Moment.CUE_SHOWN`, the Postgres enum values) —
  those are the study's data contract, already written into ledgers and
  schemas under the old name. Renaming the wire format would silently
  split the week's dataset. If you touch this area again, keep that split:
  strings change, identifiers don't.
- **Post-call: ask how it felt, not which flower** (`31b9891`), and the
  duplicate "good time to be asked" question removed so it isn't asked
  twice in one flow.
- **Flowers renamed a second time**, from the twenty mood-words to
  call-specific feelings — `GLAD_WE_TALKED`, `WISHED_IT_WAS_LONGER`,
  `DREADED_THIS_ONE`, and so on (`1bb73e8`). `FlowerKind.stored()` now maps
  two generations of old names forward. Migration `0010` added alongside.
- **Zebra-banding the week grid for real** (`9dd73bf`) — the alternating
  day-column band was upgraded from a near-invisible flat colour to
  translucent white at a visible alpha over the dark ground, after
  usability testing flagged the seven columns weren't actually countable.
- **A large onboarding pass** (`c87ace6`), bundled in one commit:
  reordered so sound selection follows name/number directly; the sound
  screen collapsed to two choices; built-in chimes removed; the finished-
  flower screen centres, drops the Continue button, and fades on its own;
  "I have already seen a reminder" now reads as a button (centred,
  underlined — this is the change that introduced the `TextLink` break,
  above); a terms pop-up now sits over the reminders-per-day screen; a
  first-run tutorial runs after the calendar step, once, covering the
  weather metaphor, both sliders, what a reminder is, and what the family
  sees.
- **One typeface, Manjari, bundled** (`f1ac305`) — replaces the
  Instrument-Serif/Manrope pairing that was never actually shipped as
  files and fell back to whatever each phone had installed.

## Remaining usability changes

Checked against the running code, not assumed from the request list:

- **A second way to add a person: pick from your contacts**, not just type
  them. **Not built.** `OnboardingScreen.kt`'s own doc comment says search-
  your-contacts is drawn in the UI but disabled, "pending the integrations,"
  and explicitly recommends the **system contact picker** (`ACTION_PICK`)
  as the permission-free route — the same trick already used for the photo
  picker. Building this with `READ_CONTACTS` and in-app search would cross
  ADR-009, which argues against exactly that; the picker doesn't cross it.
- **Search Spotify for a ringtone.** Also drawn, also disabled, for the
  same "not yet wired" reason — and this one is a harder call than it
  looks: Spotify search needs the network, and the app's **complete
  absence of `INTERNET` permission** is what makes "nothing leaves this
  phone" physically true rather than a policy promise. Flagging this one
  for Devansh rather than building it is the safer default.
- **Icons on the walking/doomscrolling onboarding options.** Not yet
  drawn. Follow the `drawTabMark` pattern in `ui/HarborShell.kt` — geometry,
  not a bitmap or an icon font.
- **Sense movement only during marked free time, not all day with busy
  suppressing.** Not built — `HarborStore` still calls `Windows.busyAt`
  (a blocklist), not something that requires a `FREE` block to exist (an
  allowlist). The model already has both halves (`BlockKind.FREE` exists,
  `Windows.planted()` exists) so this is a real but contained change. One
  decision it needs before it's built: what happens to someone who marks
  *no* free time at all — under a strict allowlist they'd never get a
  reminder. Needs Devansh's call on the empty case.

---

## Important implementation decisions

Beyond the eleven ADRs in `docs/01-decisions.md` (read that file — this is
only the index, and it does not repeat their reasoning):

1. No `INTERNET` permission, no server dependency for anything the app
   needs to function (ADR-003, ADR-004).
2. No `READ_CONTACTS` (ADR-009) — the contact and photo pickers are the
   permission-free substitute, deliberately.
3. No location, ever, not even opt-in (ADR-010).
4. The parent gets no software and no notification of any kind (ADR-007).
5. The reminder is call-shaped (full screen, her photo, her ringtone) but
   the copy never claims to be an actual incoming call (ADR-009).
6. Harbor places the call itself now, rather than handing off to the
   dialer — this is a **reversal** of the original ADR-002; the amendment
   is recorded in that same ADR, dated 13 Sep.
7. Light-only was the original visual decision; it was **reversed** on
   14 Sep in favour of dark-only. The reversal is recorded with its reasons
   in `docs/07-reskin-prompt.md`. There is still exactly one skin — the app
   does not follow the system light/dark setting either way.
8. The study's telemetry vocabulary (`Moment`) is a closed enum by design:
   it can record that something happened and roughly when, never what was
   typed, said, or chosen in free text.
9. `FlowerKind.stored()` is an append-only compatibility map. Every rename
   of the flower shelf adds to it; nothing is ever removed from it, because
   real devices in the study are holding ledger rows written under earlier
   names.
10. Everyone on this project runs their own Claude Code session against the
    same repo. There is no shared chat history between sessions — context
    that matters has to be written into a committed file (a doc, a code
    comment, this one) or it does not exist for the next person. That
    convention is why this document exists at all.

---

## Things NOT to change

- **Do not add `READ_CONTACTS`, `READ_PHONE_STATE`, location permissions
  of any kind, or an `INTERNET` permission** without it being an explicit,
  discussed decision with Devansh — each one reverses a named ADR.
- **Do not rename `Moment`'s enum values, `Cue`/`CueActivity`, or any
  Postgres enum label that a shipped migration already added**, without
  extending a compatibility map the way `FlowerKind.stored()` does. Real
  devices hold data written under the current names.
- **Do not flatten a card's fill to an opaque colour.** It has to stay
  translucent (alpha, not a solid hex) or the "glass on a sunset" look
  degrades to plain dark mode — this exact mistake already shipped once.
- **Do not use `BlendMode.Multiply` for anything drawn against the app's
  near-black ground.** Use `Screen`. This is the flower-petal bug, and the
  underlying trap (multiply against near-black goes to near-black) applies
  to any future illustration too.
- **Do not add an icon library, bitmap icons, or flower artwork as
  images.** Every mark in the app is drawn geometry on purpose, so it
  restyles for free with the palette. This is stated directly in
  `docs/05-changing-the-ui.md`.
- **Do not treat the v0 prototype (`harvest-pulse`) as a visual reference
  any more.** It is behaviour-only now; the app has shipped two full visual
  languages since it was last visually accurate.
- **Do not discard the uncommitted `TextLink` fix currently sitting in the
  working tree** (see Current state) — it's real, complete work fixing a
  genuine break, not a stray edit.
