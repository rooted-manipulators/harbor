# Master context

Written for a teammate opening a fresh Claude Code session against this repo.
Everything in it was checked against the running code, not recalled from
memory — where something is a belief rather than a checked fact, it says so.

Last corrected **19 Sep 2026**, twice: once when the second trigger landed
and again after an overnight audit before the first real testing session.
**Read "What the audit found" before the study runs.**

Two things changed since the 18 Sep pass, and the second is the larger:

- **There are two triggers now.** A reminder can arrive after a walk, or
  after a long stretch in one app, and which ones you get is a choice made
  during onboarding. See *The two triggers*, below. If you read only one
  section of this file before touching the cue pipeline, read that one — the
  two are opposite in shape and the difference is easy to reverse by
  accident.
- **The A/B arms were not comparable, and the arm did not apply on the run
  it was claimed on.** Both fixed. See *What the audit found*.

Still true from the 18 Sep pass:

- **Gradle runs on this machine again**, which means the app can be built,
  installed and looked at without waiting for CI. See *Building and handing
  out a build* — the fix is one environment variable and it is not obvious.
- **The cue fires from a real walk**, on a real phone. That was the single
  biggest unproven thing in the 17 Sep version of this file. As of 19 Sep it
  also fires from a real app stretch, measured the same way: on a phone,
  from logcat, with the prefs read back afterwards.
- **The sky is hand-mixed per weather** (`ui/WeatherWash.kt`), no longer
  derived from the ported landscape palette.
- **The sky is the A/B.** Garden arm: the slider paints it, five weathers,
  rain and all (`washFor`). Bees arm: the clock paints it, three hours
  (`washForHour`), and the slider paints the bee instead. One variable in one
  of two places, which is the whole experiment.

Still true from 17 Sep, and still the two ADRs to read before touching
anything:

- **The flowers are artwork now**, not drawn geometry (ADR-012).
- **Harbor has a server, an account and the `INTERNET` permission**
  (ADR-013), which reverses ADR-003, ADR-004 and ADR-007.

This is a supplement to `CLAUDE.md` and `docs/01-decisions.md`, not a
replacement. Those two are still the authority; this is the map of how the
recent work lands on what they describe.

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
- ~~The parent installs nothing~~ — **changed 17 Sep.** Harbor is no longer a
  child-and-parent app with one participant and one bystander: everybody who
  uses it has it, and you add your aunt the way you add anybody. See ADR-013.
  What survives the change is the part that mattered: nobody is ever told
  anything about whether you answered, and nothing about a call leaves your
  phone.
- Nothing about where the phone is or was is ever sensed or stored.
- The device is authoritative. There is a server now, and **nothing the app
  needs in order to work goes near it** — sensing, the reminder, the ledger,
  the garden and the study file are all local and offline. Sync is a mirror,
  never a source of truth. See Architecture, below.

Read `docs/00-product.md` for the fuller pitch and `docs/01-decisions.md` for
why each promise is a promise and not just a design choice.

---

## Current architecture

**Two repositories, deliberately kept apart:**

- **This one** — `AndroidStudioProjects/harbor` — the Android app, Kotlin +
  Jetpack Compose, plus a `backend/` folder holding Supabase SQL migrations.
  **None has been applied to any database yet**, including `0011`, which is
  the one that matters now.
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
  (the wire format), `StudyFile` (writes the week's export), and since
  17 Sep the sync: `SyncJson`, `SupabaseClient`, `SessionStore`.
- `ui/` + `cue/` + `sensing/` — Compose screens, the full-screen reminder
  surface and its own activity, and the Android `ActivityRecognition`
  plumbing.

**There is a server now, and it is optional.** Added 17 Sep by ADR-013, which
reverses ADR-003, ADR-004 and ADR-007 and records what each cost. Read it.

What to hold on to:

- **`INTERNET` is in the manifest.** Its absence used to be the strongest
  privacy guarantee in the app — not a policy but a fact you could check by
  reading the build file. That guarantee is spent. What replaces it is review.
- **Still no HTTP library.** `SupabaseClient` is plain REST over
  `HttpURLConnection`, which is in the platform. The only new dependency is a
  *test-scoped* `org.json`, because Android ships it as stubs that throw in
  unit tests. Every request is a GET, POST or DELETE — `HttpURLConnection`
  will not reliably send a PATCH, which is why answering a link goes through
  the `answer_link` function rather than a table update.
- **No backend configured is a supported state.** With `harbor.supabaseUrl` or
  `harbor.supabaseAnonKey` blank in `gradle.properties`,
  `SupabaseClient.configured` is false, every call returns null without
  touching the network, and Harbor behaves exactly as it did before. The URL
  is set; **the anon key is deliberately left empty.** A study build ships fine
  in this state.
- **Only the shape of a week crosses the wire.** `WeekBlock.label` is
  documented "Never leaves the device", `Sharing.SharedBlock` has no field for
  it, and `week_blocks` in `0011` has no column for it. Three layers agree on
  purpose — that is what a promise like that takes. `SharingTest` and
  `SyncJsonTest` each assert it independently; they are the most important
  tests in the repo.
- **Nothing in the cue path may await the network.** That was ADR-003's real
  argument — the reminder has to fire on a train with no signal — and it
  survives the reversal as a constraint rather than as an architecture.

**The one-week study's data path**, concretely: `Telemetry` records category
beats (never content) → `HarborStore` appends them to a capped local ledger
→ `StudyFile` writes the whole bundle to a JSON file in the phone's
**Downloads** folder on every app pause → a human collects that file by hand.
That file is the entire data-collection mechanism. The Supabase migrations
exist for a possible future sync layer and are inert today.

**Build:** Gradle cannot run in this sandbox. **GitHub Actions is the only
way to know whether something compiles** — push, then
`gh run list --branch devansh/cue-settle`, then pull the `harbor-debug-apk`
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
  reasoned about. `busyAt` suppresses and **is already wired**: `HarborStore.
  dayState` feeds it to `CuePolicy` as `busyNow`, which holds with
  `IN_CLASS` before any other suppression. A thorn on the grid already stops
  a sensed reminder. A *flower* still does nothing to the cue pipeline —
  that is the open half. Also holds `load()` and `weatherFor()`, added
  17 Sep, which weigh a day's busy minutes so the mood picker can open at a
  guess instead of blank.
- **`domain/Sharing.kt`** — the device's half of ADR-013: who may see whose
  week, and in what shape. Pure, no IO. `forWire` is the only door out and
  is the function that drops the label.
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
- **`sensing/ScrollWatch.kt`** — the second trigger's whole sensing layer,
  added 19 Sep. Reads `UsageStatsManager` for the package in front and since
  when, and nothing else. Two things to know before changing it: the stretch
  is ended by a screen-off or keyguard event rather than by an app pausing,
  because a pause is usually followed by a resume of the same app half a
  second later; and the permission is granted in Settings rather than by a
  dialog, so `hasPermission` is an AppOps check and `request` returns an
  intent that can legitimately be null on an OEM build that dropped the
  screen.
- **`data/StudyFile.kt`** — writes to both the app's private external files
  directory *and* the shared Downloads collection (MediaStore), because the
  private one is invisible to the Files app on Android 11+. This was a real
  bug, found and fixed on 14 Sep.
- **`data/SyncJson.kt`** — the wire format, hand-written against `0011` the
  way `LedgerJson` is hand-written against the study file. Pure, so the half
  of networking where bugs actually live is testable while `SupabaseClient`
  stays a thin shell. A mis-named column fails loudly; a wrong day number
  moves somebody's whole week by a day and looks fine, which is why
  `SyncJsonTest` checks Monday is 1 at both ends.
- **`ui/FlowerMark.kt`** — no longer draws anything. It picks one of forty
  files in `res/drawable-nodpi` (whole plant, and bloom on its own) and
  `Image`s it. `drawFlowerDot` is the exception: the two top-down views draw
  a cheap rosette, because at eleven pixels there is no shape left to
  recognise and a plot view can hold hundreds.

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
                            / StudyFile (the export) / SyncJson +
                            SupabaseClient + SessionStore (the server)
  domain/                   pure logic — Model, Field, Terrain, Flowers,
                            Windows, CuePolicy, Reminders, Telemetry, Garden,
                            CallStats, StudyExport, DailyQuestion, Liveness,
                            Sharing
  sensing/                  ActivityTransitions, TransitionReceiver,
                            BootReceiver, BoutTracker, SensingStore
  ui/                       every screen, plus:
    theme/                  Color.kt, Theme.kt, Type.kt, Harbor.kt (the
                            component vocabulary)
  res/drawable-nodpi/       the flower artwork: 40 webp files, two cuts of
                            each of the twenty. Generated, never hand-edited
                            — see tools/cut_flowers.py
app/src/test/java/app/harbor/            unit tests — this is what CI's
  domain/ and data/                       "Unit tests" step runs. 252 of them
                                          as of 17 Sep. No instrumented or
                                          device tests exist.
backend/supabase/migrations/           0001 through 0011, none applied
tools/                                 cut_flowers.py (makes the drawables)
                                        and flower-source/ (the 20 masters,
                                        ~5MB)
docs/                                  00 through 08, this file, and 10
.mcp.json                              the Supabase MCP server, project
                                        scope. Only loads for a session
                                        rooted in THIS directory.
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
- **The weather is the ground**, on Home and in the garden. `FieldSky`
  paints a wash; the field's terrain draws directly onto it and is erased
  into it with `BlendMode.DstIn` at the bottom edge rather than covered by a
  scrim. Every other screen (Schedule, Account) is deliberately flat — they
  do not get the wash. Three things about it are easy to get wrong:
  - **It is sized to the field, not to the window.** On Home it is bounded
    to `fieldHeight + FieldDrop`. Given the whole screen it drew a gradient
    about three times too large.
  - **The sky is hand-mixed and the ground is not.** Four sky stops per
    weather live in `ui/WeatherWash.kt`; the land stops still come from
    `ui/Meadow.kt`, the ported prototype palette. That split is deliberate:
    above the horizon nothing is drawn on the wash, so it can be any colour;
    below it, every stop is seen *between* the field's dots and has to be
    the colour those gaps should be. `Wash`'s own doc comment has the rest.
  - **The ladder may only ever get darker.** `noBrighterThan` in
    `FieldSky.kt` caps each ground stop against the one above it. Without it
    four of the five weathers had a land stop *lighter* than the sky stop
    above them — rain by a third of the whole range — and a falloff that
    brightens again draws a visible ring around the light. It is a rule
    rather than five retuned colours precisely because the two halves of the
    ladder go on being edited separately.
- **Type**: one face, Manjari, bundled as three `.ttf` files in
  `res/font/`. Three weights only — Thin, Regular, Bold, no Medium/SemiBold
  — so every style that used to ask for SemiBold now asks for Bold. See
  `Type.kt`'s own doc comment for which specific styles that touched.
- **Shape**: cards at 24dp, everything you press is a full pill. Radii
  collapsed to 8–10dp in the *first* (light, now-dead) reskin; do not
  reintroduce that ladder.
- **Flowers**: **artwork, not drawn geometry, since 17 Sep** (ADR-012). Forty
  webp files in `res/drawable-nodpi`, cut by `tools/cut_flowers.py` from the
  twenty masters in `tools/flower-source`. Do not hand-edit either; re-run the
  cutter. Three separate attempts were made to draw them — one petal function,
  then a silhouette per flower, then the illustration vectorised and its Bézier
  outlines ported into Compose paths — and each produced a recognisable flower
  that was not *the* flower. `domain/Flowers.kt` still holds three colours per
  kind, for the things that need a colour rather than a picture: the field's
  cells and the garden's dots.
- **Icons**: still drawn geometry, never a library or bitmaps — see
  `drawTabMark` in `ui/HarborShell.kt` for the pattern to copy. ADR-012
  reverses the rule **for the flowers only**; everything else in
  `docs/05-changing-the-ui.md`'s table still holds.
- **Level of detail** (`domain/Field.kt`, `ui/FieldCanvas.kt`). The field
  resolves as you lean in, on two ladders. A planted cell is a dot, then
  petals around a centre past `FLOWER_AT`, then the artwork past `ARTWORK_AT`
  with a short crossfade. Ground is a dot until `GRASS_AT`, then grows blades
  out of it. Grass is gated on `Field.grassStand` — tilt *and* nearness — and
  not on size alone: in plan view every cell projects at the same scale, so
  gating on size grew grass over the whole overview, thickest along the far
  edge, which is the one place the eye is not.
- **Motion**: `LocalReducedMotion` is a `CompositionLocal` read by anything
  that animates (card entrance, screen crossfade, the press-scale on
  buttons). Anything new that animates has to check it — it's an
  accessibility setting, not a taste one.

---

## Current state

**19 Sep 2026. Everything below is committed.**

- **Branch:** `dark-reskin`. Tip is the overnight audit pass; `versionCode`
  is 4 and `app/build/outputs/apk/release/app-release.apk` is the build to
  hand out.
- **Tests: 373, all passing**, under Gradle (`testDebugUnitTest`) and under
  the standalone kotlinc recipe in `CLAUDE.md`. Two notes if you use the
  standalone recipe: the test source set needs `-Xfriend-paths` pointed at
  the main output or `internal` is invisible, and anything touching
  `org.json` needs a real implementation **ahead of `android.jar` on the
  classpath** — Android ships stubs that throw, and if `android.jar` sorts
  first you get fifteen `RuntimeException: Stub!` failures that look like a
  regression and are not. `app/build.gradle.kts` has
  `testImplementation(libs.json)` so Gradle gets this right by itself.
- **The two orphans from the last pass are still orphans.** `HomeBud` and
  `Windows.weatherFor` are built, verified and uncalled.

**What landed since 17 Sep**, in the two arcs it happened in:

| What | Where |
| --- | --- |
| The cue actually firing: settle alarm, foreground service, battery exemption, onboarding asks | `sensing/SettleAlarm.kt`, `sensing/SensingService.kt`, `ui/OnboardingScreen.kt` |
| Field density and level of detail, 2.25x the cells on the same island | `domain/Terrain.kt`, `domain/Field.kt` |
| The stir: flowers move as you travel through them | `ui/FieldCanvas.kt` |
| The weather wash: bowed bands, grain, green haze, per-weather skies | `ui/FieldSky.kt`, `ui/WeatherWash.kt` |
| The scroll-linked pull-back and its crane shot | `ui/FieldCanvas.kt`, `Field.pullTilt`, `Field.wideZoom` |

**What landed on 19 Sep:**

| What | Where |
| --- | --- |
| Your activity: three pictures of what grew, as a swipeable deck | `ui/GardenActivity.kt`, `domain/Growth.kt` |
| The scrolling cue taking the screen over another app | `cue/CueNotifier.canOpenOverApps` |
| The arm applying on the run it was claimed on | `HarborRepository.armFlow` |
| Both arms answering on the slider thumb | `ui/WeatherBar.kt`, `Sky.drawEmblem` |
| The five grants and the per-trigger cap on the export | `domain/StudyExport.kt` (format 4) |
| The detent tick, replacing a long-press thud on swipes | `ui/theme/Buzz.kt` |

**What landed earlier on 19 Sep:**

| What | Where |
| --- | --- |
| The second trigger: opt-in, consent, policy gate | `domain/Model.kt`, `domain/CuePolicy.kt`, `ui/OnboardingScreen.kt` |
| The sensing behind it | `sensing/ScrollWatch.kt`, `sensing/SensingService.kt`, `sensing/SensingStore.kt` |
| Four a day, two per trigger | `Thresholds.sourceCap`, `0015` |
| `0012` and `0014` corrected: both named a table that does not exist | `backend/supabase/migrations/` |

---

## What the audit found

An overnight pass on 19 Sep, the night before the first session with real
participants. Everything here is fixed and committed; it is written down
because each one was invisible from inside the app and could come back the
same way.

**Three things would have broken the study rather than the app.**

1. **The arm did not apply on the run it was claimed on.** It was read once
   in a `LaunchedEffect` at process start — before the code screen. Typing a
   B code wrote `bees` to preferences and left the running app in the garden
   arm; the bee appeared after the next cold start. Every bees participant
   would have done the whole of their first session, the observed one, in
   the control. `armFlow` is observable now.

2. **The control arm's slider had stopped doing anything.** It used to paint
   the sky; when the sky became the hour (`SkyHour`) nothing replaced it, and
   nothing a participant saw read `settings.weather` any more. The bees arm
   meanwhile had a face on the thumb that changed as you dragged — so the arm
   under test was the responsive one and the control was inert. Fixed twice
   over, and both are wanted: a thumb that carries the answer in **both**
   arms, and — since 19 Sep — the garden arm's sky painting from the slider
   again, which is what the control arm is supposed to *be*. `SkySays` in
   `ui/FieldSky.kt` is the fork; there is no longer a way to paint that sky
   without naming an arm.

3. **The scrolling trigger could never have fired on a real phone.**
   `targetSdk 37` filters package visibility, so `getLaunchIntentForPackage`
   returned null for every third-party app and every stretch was excluded.
   A `<queries>` block fixes it. **If you raise targetSdk or touch
   `ScrollWatch.counts`, re-check this on a device with a real app.**

**And one about reading the results.** A week of zero cues has five
explanations — notifications off, no full-screen grant, no overlay grant, no
usage access, a process the OEM froze — and only one of them is a finding.
The export carries all five now (`grants`, format 4). Without them every
failure looks like behaviour.

**Still open, deliberately:** arm B is a bee on the slider and a bee in the
arch; the bee flying into the field and the mother bee watering a bud are
designed and unbuilt (`bee_tending.webp` ships unused). `GardenCanvas` and
`HomeBud` are dead code with zero callers, and `GardenCanvas` dying takes
most of `domain/Garden`, `Sky.gradient`, `Sky.veil` and `drawWheel` with it.

**One procedure item, not a code fix:** `StudyArm.fromCode` reads the first
letter, so a mistyped code silently assigns the garden arm with no feedback.
Telling the participant their arm on screen would be worse. **Check Account →
Study code on every phone before handing it over.**

---

## The two triggers

A reminder can now arrive two ways, and they are opposite in shape. Getting
that backwards is the most likely way to break this pipeline, so it is
written out rather than left in the code.

| | **After a walk** | **After a long stretch in one app** |
| --- | --- | --- |
| `TriggerSource` | `WALKING_STOP` | `SESSION_END` |
| Sensed by | Activity Recognition Transition API, Play services | `UsageStatsManager`, polled |
| Woken by | Play services, then `SettleAlarm` | `SensingService`, every two minutes |
| Fires | **after** the activity ends, once `SETTLE` has passed | **during** the stretch, the tick after the threshold |
| Threshold | `Thresholds.walkingMinutes` (3) | `Thresholds.sessionMinutes` (20) |
| On by default | yes | **no** — `UserSettings.scrollCues` |
| Permission | `ACTIVITY_RECOGNITION`, a runtime dialog | `PACKAGE_USAGE_STATS`, a Settings screen with no dialog |

Four things about the second one that are decisions, not implementation
details, and are argued in ADR-005 as amended:

1. **It is off until somebody takes it.** `CuePolicy` returns
   `Reason.SOURCE_OFF` for a `SESSION_END` when `scrollCues` is false, and it
   checks that *before* the caps, so a declined trigger cannot spend a slot
   the walk could have used.
2. **It fires mid-stretch.** `SESSION_END` is deliberately outside the
   `SETTLE` gate — `CuePolicy.waitsOutAStop` is the property that says so,
   and it used to include it. Waiting for the stretch to end means arriving
   after the phone is face down. The name stays because it is in every stored
   ledger row and in the study's wire format.
3. **It reads nothing but the package in front and since when.**
   `ScrollWatch` keeps no history; the only thing written is
   `SensingStore.firedStretch`, one package and one timestamp, so one long
   session cannot produce a reminder every two minutes. The disclosure on the
   onboarding screen is written against that file, and the policy text behind
   its link claims the export carries no app name — which is true, and
   `StudyExport` is where it would stop being true.
4. **The caps are split.** Four a day, two per trigger
   (`Thresholds.sourceCap`). Against one shared ceiling the frequent trigger
   takes every slot, and a week of running both would end with no comparison
   at all.

One thing is **not** done and is the obvious next bug:

- `ScrollWatch.current` does not know what a launcher is. Twenty minutes in
  any one app counts, Harbor's own package excepted. That is on purpose — a
  hardcoded list of "bad" apps is a judgement and goes stale — but nobody has
  yet watched a week of it to see what it actually catches.

---

## Building and handing out a build

**Gradle works on this machine, and the reason it looked broken is worth
writing down, because it will look broken again.**

It failed every time with `java.io.IOException: Unable to establish loopback
connection`, which reads like a firewall problem and is not. The real cause is
four frames further down: `Selector.open()` → `sun.nio.ch.PipeImpl` →
`UnixDomainSockets.connect0` → `SocketException: Invalid argument`. Java's NIO
selector opens its internal pipe over an AF_UNIX socket placed in
`java.io.tmpdir`, and that directory is unusable here. Nothing in Gradle can
work without a selector, so every build, daemon and worker dies at startup.

Both JDKs on this machine fail it — the bundled 23 and Studio's JBR 25 — so it
is the environment, not the toolchain. The fix is to put the AF_UNIX socket
somewhere else:

```bash
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp"
./gradlew assembleRelease
```

`JAVA_TOOL_OPTIONS` rather than `-Dorg.gradle.jvmargs=...`, and this matters:
the command-line override reaches the *client* and does not reach the daemon
it forks, so the build gets one step further and then reports "a new daemon
was started but could not be connected to". The environment variable is
inherited by every JVM in the tree.

**Which APK to hand somebody.**

- `assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. This
  is the one to send. Not debuggable, no Compose tooling, installs by tap.
- `assembleDebug` from the command line is installable but `debuggable`.
- **Studio's Run button is the one that produces an APK nobody else can
  install.** It stamps `testOnly='-1'` on it, and `adb install` then refuses
  with `INSTALL_FAILED_TEST_ONLY` unless you pass `-t`. A teammate tapping
  the file just sees it fail. This is the whole reason "make a real APK" is
  a task at all.

**Release is signed with the debug key, deliberately.** `app/build.gradle.kts`
used to leave release unsigned with a note saying a real release key is a
separate decision. It still is — but one signature across every build is what
lets an update install over the last one, and an uninstall takes the ledger
with it, which is the thing the study measures. Play will refuse this
signature for ever. Moving to a real release key is a day everybody reinstalls
once, on purpose.

**Bump `versionCode` for every build that reaches a phone.** It is 3 now. An
Android device refuses an APK whose `versionCode` is below the installed one,
so this is what stops a participant being quietly moved backwards onto a build
that may read their data differently.

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

  `sensing/SensingService.kt` now stands against this: a foreground service
  that does **no work at all** and exists only so the process is not a cached
  one and cannot be frozen. It amends ADR-008 rather than reversing it — the
  argument there was against a service that polls, and this one does not. The
  battery exemption is still worth having on top; the two fix different halves
  of the same problem.

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

**Proven since, and the entry it replaces is worth keeping in mind:** a
reminder now fires from an actual sensed walking-stop, on a real phone, with
the permissions granted. This file used to list that as the biggest unproven
thing in the product, verified only through the in-app "show me a reminder
now" button, which bypasses sensing entirely. It took three fixes that had
nothing to do with each other — the settle alarm below, the manual-cue
cooldown below, and the OEM freezing above — and any one of them alone left
it looking exactly as dead as before. If it ever goes quiet again, suspect
all three rather than the first one you find.

**Not yet proven, because nothing has driven the real path:**

- **Every single line of the backend.** `0011` has never been parsed by
  Postgres. `SupabaseClient` has never opened a socket. The RLS policies have
  never refused anything. Sign-in has never completed. Everything from the
  migration outwards is unexercised — what is verified is that the Kotlin
  compiles and that `SyncJson`, which is pure, does what its 19 tests say.
- **Harbor with the radio off.** ADR-013 keeps ADR-003's argument as a
  constraint — the reminder must fire on a train with no signal — and nobody
  has put a build in aeroplane mode and walked.

- The post-call sequence (land on Home → camera flies to the patch →
  flower opens) end to end, off a real phone call. The timings in the code
  are estimates, not calibrated against a real device.
- Every Supabase migration. None has been applied to any database, ever.
  **This is urgent now.** It used to be moot because the app could not reach a
  database; since ADR-013 it can, and `0011` is the file the next piece of work
  depends on.

---

## Completed usability changes

Cross-referenced against the nineteen items sent after usability testing on
15 Sep. Those commit hashes are on `dark-reskin`, which has since been
merged into the current branch.

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

1. ~~No `INTERNET` permission, no server dependency~~ — **reversed 17 Sep by
   ADR-013.** There is a server, an account, and the permission. What survives
   is the constraint: nothing the app needs in order to work may depend on it,
   and nothing in the cue path may await it.
2. No `READ_CONTACTS` (ADR-009) — the contact and photo pickers are the
   permission-free substitute, deliberately.
3. No location, ever, not even opt-in (ADR-010).
4. ~~The parent gets no software~~ — **reversed 17 Sep by ADR-013.** Everybody
   has the app; it is no longer a child-and-parent product. Still true, and
   still ADR-007's actual point: nobody is told anything about whether you
   answered.
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
10. **Identity is an email address**, and sign-in is Google, Microsoft or a
    code to your inbox. Supabase has **no Yahoo provider** and cannot be given
    one — the email code is the route that covers Yahoo and everybody else,
    and it is the one that always works because it needs no provider set up.
    Microsoft is the slug `azure`; there is no `microsoft` provider and asking
    for one returns a 400 that reads like a bad account.
11. **Sharing is asked for, read-only and revocable**, and only the shape of a
    week ever crosses. See `0011`'s own header, which states the three rules it
    is built to enforce rather than merely permit.
12. Everyone on this project runs their own Claude Code session against the
    same repo. There is no shared chat history between sessions — context
    that matters has to be written into a committed file (a doc, a code
    comment, this one) or it does not exist for the next person. That
    convention is why this document exists at all.

---

## Things NOT to change

Three entries here were reversed on 17 Sep. They are kept, struck through,
because a rule that vanishes looks like a rule nobody had.

- **Do not add `READ_CONTACTS`, `READ_PHONE_STATE`, or location permissions of
  any kind** without an explicit decision with Devansh — each reverses a named
  ADR. ~~or an `INTERNET` permission~~ — that one was the explicit decision,
  and it is ADR-013.
- **Do not let anything the app needs depend on the network.** The replacement
  for the rule above, and the stronger version of it. Sensing, `CuePolicy`, the
  ledger, the reminder surface and the study file must all work with the radio
  off. `SupabaseClient` returns null on every failure precisely so a caller
  cannot accidentally require it. **This has never been tested in aeroplane
  mode on a real device.**
- **Do not give `week_blocks` a label column**, or `Sharing.SharedBlock` a
  label field. `WeekBlock.label` is "Never leaves the device" and three layers
  agree on that so no single change can break it. Two tests assert it.
- **Do not rename `Moment`'s enum values, `Cue`/`CueActivity`, or any Postgres
  enum label that a shipped migration already added**, without extending a
  compatibility map the way `FlowerKind.stored()` does. Real devices hold data
  written under the current names.
- **Do not flatten a card's fill to an opaque colour.** It has to stay
  translucent (alpha, not a solid hex) or the "glass on a sunset" look degrades
  to plain dark mode — this exact mistake already shipped once.
- **Do not use `BlendMode.Multiply` for anything drawn against the app's
  near-black ground.** Use `Screen`. This was the flower-petal bug. It no
  longer applies to the flowers, which are opaque artwork now, and it still
  applies to every other thing drawn on this ground.
- **Do not add an icon library or bitmap icons.** ~~or flower artwork as
  images~~ — the flowers are images now, by ADR-012, and they are the only
  exception. Everything else is drawn geometry so it restyles with the palette.
- **Do not hand-edit `res/drawable-nodpi/` or `tools/flower-source/`.** The
  drawables are generated; change the sources and re-run
  `tools/cut_flowers.py`.
- **Do not treat the v0 prototype (`harvest-pulse`) as a visual reference any
  more.** It is behaviour-only now.
- **Do not commit the Supabase anon key** into `gradle.properties` without
  deciding to. It is public by design and protected by RLS, so it is not a
  leak — but a key in git is a key nobody rotates, and the build works without
  it. `~/.gradle/gradle.properties` keeps it off the repo.

---

## What to do next, if nobody has told you otherwise

In the order I would do them:

1. **Apply `0011` and see what breaks.** Nothing in it has ever been parsed by
   Postgres. The three things I would expect to bite: whether `request_link`
   can read `auth.users` as `security definer`, whether `gen_random_uuid()`
   needs pgcrypto enabled, and whether the `week_blocks` select policy's
   subquery into `links` behaves (I believe it does — `links`' own policy
   restricts it to rows where you are an end, which is what the subquery wants,
   and there is no recursion because `links` does not reference `week_blocks`).
2. **Get a green CI run.** Nothing on this branch has had one. Local Gradle
   works now (see *Building and handing out a build*), so this is no longer
   the only way to know the app compiles — but it is still the only thing
   that builds it on a machine that is not this one.
3. **Wire the two orphans**: `HomeBud` into `HomeScreen`, and
   `Windows.weatherFor` into `WeatherBar` as its opening state.
4. **Prove offline.** Aeroplane mode, a real walk, a reminder. If that does not
   work, ADR-013 has broken the product to add a feature.
5. Then the person's clock, the bee and the reminder it sets — which is the
   thing the backend was added for, and which has no UI at all yet.

The MCP server for Supabase is in `.mcp.json` at **project scope**, so it only
loads for a session rooted in this directory. A session rooted anywhere else
will not see it however authenticated you are.
