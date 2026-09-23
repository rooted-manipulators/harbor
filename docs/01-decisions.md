# Decisions

Architecture decision records. One per irreversible-ish choice. If you are
about to do something that contradicts one of these, change the ADR in the
same PR — don't just do the thing.

---

## ADR-001 — Android-native, Kotlin + Compose, v0.1 Android only

**Status:** accepted

The trigger depends on OS activity signals. On Android that is the Activity
Recognition Transition API; on iOS it is Core Motion. Both are native. A
cross-platform shell would still need two native modules for the only part of
the product that is hard, so it buys nothing.

Android only for v0.1 because the handoff's phasing says so, and because the
week-one study cohort is an Android cohort. iOS is v0.3.

---

## ADR-002 — The call is a plain cellular call. We do not fork Signal or any
## VoIP client.

**Status:** accepted

The original concept floated integrating with Signal so Harbor could be the
user's default caller. Three things kill that, in descending order of how
fatal they are:

1. **The parent installs nothing.** This is the whole product. A student's
   mother is not installing Signal, Jami, or Linphone to receive a call from
   an app her kid is testing for a week. Any VoIP path requires software on
   both ends. A cellular call requires software on neither.
2. **Signal blocks third-party clients.** There is no public client API, and
   Signal has refused to let forks talk to their servers since the LibreSignal
   decision. A fork would be a client with no network to connect to.
3. **Licensing.** Signal's Android client is GPLv3. Linphone and Jami are
   GPLv3 too. Forking any of them makes Harbor GPLv3 and open-source. That
   may be fine — but it should be a deliberate choice, not a side effect of
   picking a calling library.

Separately: "default caller" was never a Signal capability. It is an Android
telecom role (`ROLE_DIALER`), which Harbor can request directly.

### What we build instead

**Amended 2026-09-13: Harbor places the call itself.** The button rings the
number rather than filling it into the dialer for the user to press. That
costs `CALL_PHONE`, a dangerous permission, and it is requested alongside
activity recognition during the first run. A refusal is not an error: the
button falls back to `ACTION_DIAL`, which is the behaviour described below,
so the flow still works with one extra tap.

What this gives up is the confirmation step. There used to be a dialer
between a tap and a ringing phone, and on a cue shown over the lock screen
that step was doing real work — a pocket tap landed on the dialer and stopped
there. It now rings. That is a deliberate trade for a call flow that is one
tap instead of two, made 2026-09-13.

`READ_PHONE_STATE` is still not requested and should stay that way. Harbor
does not watch the call; `CallStats.minutesAway` times how long the user was
away from the app, which is what the reflection screen states back to them.

**Amended 2026-09-10.** Harbor hands off to the phone's own dialer and asks
the user how it went. `Intent(Intent.ACTION_DIAL)` with the contact's number
opens the dialer with it filled in; the user presses the call button
themselves.

This needs **no permissions at all**, and that is the point of the amendment.
The earlier version of this ADR specified `ACTION_CALL` plus a
`TelephonyCallback` to detect call-end, which would have cost two dangerous
permissions — `CALL_PHONE` and `READ_PHONE_STATE`. Activity recognition is
already the biggest install-funnel risk in the product; putting a
phone-and-call-log prompt beside it is the worst possible place to spend more
of the user's trust.

Stage 7 fires on the user's own answer instead of on a detected call end. The
prototype already works this way: choosing "Call now" leads to a **"Mark call
completed"** button rather than anything automatic. So this costs no design
change either — it is the prototype's behaviour, and the earlier plan was the
divergence.

No dialer role, no in-call UI to own, no fork, no `ROLE_DIALER`.

### Open-source call apps evaluated

| Project | Licence | Why not for v0.1 |
| --- | --- | --- |
| [Signal Android](https://github.com/signalapp/Signal-Android) | GPLv3 | Servers closed to forks; parent must install it |
| [Linphone](https://gitlab.linphone.org/BC/public/linphone-android) | GPLv3 (commercial licence available) | SIP VoIP — needs a SIP account and an app on both ends. Real option *if* Harbor ever needs in-app voice; the commercial licence exists if GPL is a problem |
| [Jami](https://f-droid.org/en/packages/cx.ring/) | GPLv3 | Peer-to-peer, no server to run — genuinely nice, but still needs Jami on the parent's phone |
| [Fossify Phone](https://f-droid.org/en/packages/org.fossify.phone/) | GPLv3 | A real open-source *dialer* (Kotlin, actively maintained, Simple-Mobile-Tools successor). This is the one to fork **if** we later decide Harbor should own the in-call screen. Not needed to merely place a call |

### What it costs

A call is **self-reported**, not verified. Someone can tap "Call now", never
press dial, and still mark it completed. For the week-one study that is
acceptable — the resolution the user chose is itself the interesting signal,
and the feedback pulse was always self-reported — but it means `called` counts
should be described as "reported a call", not "made a call". Noted in
docs/03-week-one-study.md.

**Revisit this ADR if** the product ever needs to own the in-call experience
(a during-call UI, a call recording, a custom ringback), or if verified call
completion becomes worth two permissions. Then Fossify Phone is the starting
point and GPLv3 becomes the licence conversation.

---

## ADR-003 — The device is authoritative; Supabase is sync and export

**Status:** accepted

Sensing, threshold check, suppression check and kairos refinement all run
on-device against local storage. The cue must fire on a train with no signal.
Supabase receives ledger entries after the fact.

Consequence: the suppression check (stage 3) reads the *local* ledger, not the
server. Sync conflicts are resolved by `(user_id, client_id)` upsert — the
device generates the id, so a retried upload is idempotent.

Consequence: no endpoint the app blocks on before showing a cue. If you add
one, you have broken the product on campus wifi.

---

## ADR-004 — Raw activity data never leaves the device

**Status:** accepted, non-negotiable

The handoff guardrail is "share activity data with a parent, ever" = never.
We go further: raw walking and usage streams never reach *our* server either.
Only the result of a trigger — a ledger entry — syncs.

This is also what makes the permission explainer screen honest. If we ever
upload the stream, that screen becomes a lie, and the permission ask is
already the biggest install-funnel risk in the product.

There is deliberately no parent-facing table, role, or RLS policy in the
schema. A parent-side feature gets its own reviewed migration.

**Scope of this ADR, tightened 2026-09-10.** As originally written this read
as "no data about the user may ever reach anyone else", which would forbid
things the product is supposed to do. The guardrail is specifically about
**activity data** — walking, app usage, and anything derived from sensing.
Those never leave the device.

It is not a general ban on the user sharing something deliberately. If a
future feature lets someone share a moment, a note, or their own availability,
that is a product question decided on its own merits, not a violation of this
ADR. What it may never become is a channel through which a parent learns
something about the user's movements or phone habits.

---

## ADR-005 — Walking-stop trigger only in v0.1. Not Google Fit.

**Status:** accepted

Use the **Activity Recognition Transition API** (Google Play services). It is
independent of Google Fit, which is being retired — do not build against Fit,
and do not add Health Connect unless step *history* is wanted elsewhere.

The app-session trigger (`UsageStatsManager`) is v0.2. It is the
platform-harder half and the study does not need it to answer its questions.

**Amended 2026-09-19: v0.2 is now, and the study does need it.** The question
the study is being asked to answer changed: not *does a reminder after a walk
work*, but *which kind of moment works better*. That is a comparison, and a
comparison needs two triggers. The second one is the long stretch in one app,
built in `sensing/ScrollWatch.kt` on `UsageStatsManager`, exactly as this ADR
named it.

Three things about it are decisions rather than implementation, and are here
because they are the ones somebody would otherwise quietly reverse.

**It is opt-in, separately from cues.** `UserSettings.scrollCues` is off until
somebody takes it on the onboarding screen, and `CuePolicy` refuses a
`SESSION_END` when it is off — before the caps, so a declined trigger cannot
spend a slot the walk could have used. Reading which app is in front is a
different promise from reading whether the phone is moving, it is a different
grant (`PACKAGE_USAGE_STATS`, which has no dialog, only a Settings screen),
and it gets its own consent. The disclosure is shown only to the people who
take the option, at reading size, with the detail behind a link that opens in
place. Do not shrink it: that Harbor can see which app you are in is the whole
of what is being agreed to.

**It fires during the stretch, not after it.** This is the one that looks like
a bug if you come to it from the walking trigger. A walk becomes worth
interrupting when it *ends*, so `CuePolicy.SETTLE` holds it until the
stillness has lasted. A long stretch in one app is worth interrupting while it
is still going on — waiting for it to end means arriving after the phone is
face down, which is nobody's moment. So `SESSION_END` is outside the settle
gate. It keeps its name because the name is written into every stored ledger
entry and into the study's wire format; what it marks is the stretch, not the
end of one.

**It is a poll, in the foreground service.** Android has no "twenty minutes in
one app" event; the two it does offer — an app launching, the screen going off
— are both the wrong end. `SensingService` exists anyway (see ADR-008 as
amended) and now also looks at a clock every two minutes, doing nothing at all
for anybody who has not turned the trigger on, and nothing while the screen is
off. That reverses the "this service does no work" rule in that file for this
one case only.

**It takes the screen, and that costs a heavy permission.** A
full-screen intent covers the locked or dark phone and does nothing on an
unlocked one: there the system shows a heads-up banner and leaves you where
you were. Correct for almost everything and wrong for this trigger, whose
job is to interrupt the feed in front of you — a banner over a feed is a
thing people flick away unread. `SYSTEM_ALERT_WINDOW` is the only grant that
lets an activity start from the background on a phone in somebody's hand, so
Harbor asks for it: late, only of people who took the trigger, and never as
a condition of the app working. Without it the cue is the banner it already
was. Not for a Play listing as it stands; this is a one-week study build.

A consequence worth stating: with that grant, the *walking* cue takes the
screen on an unlocked phone too, where it used to be a banner. That is what
ADR-009 asks for and it had never actually happened before.

The caps move with it: four reminders a day, two from each trigger
(`Thresholds.sourceCap`). Against a single shared ceiling the frequent trigger
takes every slot and the rare one is never seen, and a week of running both
would end with no comparison at all — which is the whole reason the second
trigger exists.

---

## ADR-006 — Package name

**Status:** done (2026-09-10)

The Studio wizard generated `com.example.harbor`, which cannot be published
to Play and should not go out even to study participants. Renamed to
`app.harbor` across sources, tests and the Gradle namespace/applicationId.

---

## ADR-007 — The parent gets no software. Two prototype screens change shape.

**Status:** accepted (2026-09-10)

v0.1 targets the whole prototype (docs/02-ui-reconciliation.md), and two of its
screens quietly assume a second participant:

- **Conversation** is two-way chat. `ChatMessage.mine: boolean` means someone
  on the other end is sending.
- **Harvest** stores `schedules[day] = { you, mom }` and computes overlapping
  free windows. The mother's intervals have to come from somewhere.

In the prototype both are local fakes — a seeded sample family in
`localStorage`. That is fine for a demo and impossible for a week-long study
with real people, who will notice within a day that nobody is on the other
end.

### Decision

The parent installs nothing, and gets no web surface either. ADR-002 stands
unchanged, and the two screens are reshaped rather than the constraint being
relaxed:

- **Conversation becomes one-sided.** Notes the user writes, kept for
  themselves or handed off to the phone's own SMS or WhatsApp to actually
  send. Harbor is not a messenger and should stop implying it is.
- **Harvest becomes self-entered.** The user records when they think their
  person is usually free. It is their own guess, labelled as such, and there
  is no consent flow because there is no second party. `sharing` and
  `momConsent` disappear from the model; so does the schedules table.

### Why

The reciprocity in the prototype's copy is genuinely nicer. But building it
means a parent client, an identity for that parent, an invitation flow, and a
second consent surface — and every one of those is a place where activity data
could leak toward a parent, which is the one thing ADR-004 exists to prevent.
That is a large amount of new risk in exchange for a feature the study is not
even trying to measure.

The study's three questions are all about the trigger. None of them needs the
mother to be online.

### Cost, stated plainly

The Harvest screen gets weaker: a guess at someone's routine is worth less
than their actual availability. If the study says people want real
reciprocity, that is the moment to revisit this — with the parent surface
designed deliberately rather than arrived at by a prototype's convenience.

### Consequence for the copy

**Not a problem in the web prototype.** Checked 2026-09-10: it already labels
every simulated element as one — "Simulated consent · demo only", "You are
editing sample data, not a real parent's calendar", "This demo does not send
data to Mom", and an aria-label of "Simulate Mom consent". Nothing there
claims a consent that does not exist. Do not "fix" it.

**It becomes a problem the moment these screens are rebuilt natively.** A
study build has no demo framing to hang that honesty on. A "Mom's permission"
toggle in a real app, with no Mom behind it, stops being a labelled simulation
and becomes a false claim — the exact kind that costs trust when a participant
works out it is not true.

The rule for the native build, then: no consent switch, no "waiting for Mom",
no mutual-window language. Availability is the user's own note about someone
else's routine, and should read like one.

---

## ADR-008 — No foreground service for sensing

**Status:** accepted (2026-09-10)

The build order and the handoff both assumed a foreground service owning the
sensing loop, carried over from the sibling project where the shield genuinely
needed one.

Harbor does not. The Activity Recognition Transition API delivers to a
`PendingIntent` whether or not the app is running — that is the whole point of
it, and it is why it exists separately from the sampling API. A service of our
own would add a permanent notification, a battery footprint, a
`FOREGROUND_SERVICE` permission and, on API 34+, a foreground service *type*
that Harbor would struggle to justify, all in exchange for nothing the
platform is not already doing.

So: `TransitionReceiver` is a plain broadcast receiver, woken by Play
services, using `goAsync()` for the few milliseconds it takes to read the
ledger and write a cue.

**Amended 2026-09-17: one inexact alarm, for the settle wait.** No service
still, but the receiver-only design had a hole in it that took until the first
real walk to find. `CuePolicy` will not fire until the user has been still for
`SETTLE`, and Play services wakes the receiver at the instant stillness is
detected — inside that window, every time — and then has nothing further to
send while the user stays still. So the one chance to evaluate a walk was the
one moment it was certain to be refused, and `BoutTracker` had already cleared
the bout. No sensed walk could ever produce a cue. The unit tests all passed:
each handed `decide` a signal that was already settled, describing a caller
that did not exist.

`sensing/SettleAlarm` is that caller. The signal is parked in `SensingStore`
and an `AlarmManager` alarm re-asks once it has settled. This is not the
service ADR-008 refuses: it is nothing between firings, no notification, no
permission. The alarm is inexact (`setAndAllowWhileIdle`) because
`SCHEDULE_EXACT_ALARM` is not worth spending on a ninety-second timer, so it
can land minutes late under Doze — `CuePolicy.settleExpired` drops a signal
that lands *much* later rather than firing into a moment that has passed.

Registrations are also re-established on every launch (`Sensing.repair`), not
only on boot. Installing a build force-stops the app, and Android delivers
nothing to a stopped app until it is launched by hand; that is the state every
participant handed a new APK was in, and it reported itself as working.

### What this costs

A permanent notification is also a *disclosure* — the user can see sensing is
running. Without one, the only surface saying so is the settings screen. Given
`cues_enabled` defaults off and is gated behind a privacy explainer, that is a
defensible trade, but it puts more weight on that explainer being honest.

### What it does not fix

Aggressive OEM battery management. MIUI, ColorOS and OnePlus builds suspend
background delivery, and a foreground service would not reliably survive them
either — those OEMs kill those too. This remains the single largest threat to
the week-one study, and it fails *silently*: the data just looks like a user
who never walked. Test on a real MIUI device before recruiting, not on the
emulator.

---

## ADR-009 — The cue is call-shaped: full screen, her ringtone, her photo

**Status:** accepted (2026-09-10)

### Decision

The cue surface is a full-screen activity with the contact's photo, their
name, and the sound the user chose for them — their actual ringtone, or a song
that reminds the user of them. It has the weight and presence of an incoming
call.

### Why, and why it is not decoration

The sound is the mechanism, not the styling. A generic notification chime
carries no association with anyone; a mother's ringtone carries years of it.
The cue is trying to borrow a conditioned response that already exists, so
that the prompt lands as *her* rather than as the app. That is the whole
argument for a call-shaped surface, and it is why a quiet heads-up
notification is not an equivalent implementation of the same idea.

This is also what the original handoff specified — "Cue surface — full-screen,
always dismissible" and "Cue sound picker — parent's ringtone or a chosen
song".

### It evokes a call. It never claims to be one.

The surface must never present as an incoming call from the contact. No
"Mom is calling", no green-and-red answer/decline pair, no mimicry of the
system call UI.

A student a long way from home who believes their mother is unexpectedly
calling will assume an emergency. One such scare and that participant's
week-one data describes their alarm rather than our trigger — and the product
has spent trust it cannot earn back. The conditioned response works from the
sound, the photo and the presence; none of it requires the lie.

So: her ringtone, her face, full-screen weight, and Harbor's own voice asking
whether now is a good moment.

### Platform reality

`USE_FULL_SCREEN_INTENT` is restricted from Android 14 to apps whose core
function is calling or alarms. Harbor hands off to the dialer rather than
placing calls (ADR-002), so that classification is genuinely arguable rather
than obvious, and Play review may disagree.

The failure mode is graceful: an ungranted full-screen intent **degrades to a
heads-up notification** rather than failing. So we request it, and treat the
heads-up notification as the designed fallback rather than an error path. The
cue still rings, still shows the photo in the notification, and still opens
the full surface when tapped.

Never treat a missing full-screen permission as a reason to suppress the cue.

### Permission cost, kept deliberately small

- `POST_NOTIFICATIONS` — one runtime prompt, API 33+. Unavoidable.
- `USE_FULL_SCREEN_INTENT` — requested through a settings screen, degrades if
  refused.
- Photo: the system photo picker, which needs **no** permission. Do not read
  the contact's photo via `READ_CONTACTS` — a contacts permission next to the
  activity one would cost far more trust than a picked photo is worth.
- Sound: `RingtoneManager`'s picker, which needs **no** permission.

### Dismissal

One gesture, no cost, no penalty, exactly as before. Back dismisses. The
ringtone stops the moment the surface is dismissed by any route. A dismissed
cue writes a `dismissed` ledger entry and nothing else happens.

---

**Amended 18 Sep 2026: delivered like an alarm, worded like Harbor.**

This ADR is about what the cue *says*. Nothing in it is about how insistently
Android carries the notification, and the two had been conflated: the cue was
posted as `CATEGORY_REMINDER`, which is the bucket people learn to swipe past
unread, and its sound rode the notification volume. A cue that arrives while
the phone is face-down in a bag and makes no sound has lost the moment it
exists to catch, however carefully its words were chosen.

So it is now `CATEGORY_ALARM`, with alarm audio usage and a request to be heard
through Do Not Disturb. An alarm category claims nothing about who is calling.

Not `CATEGORY_CALL`, which is the one that would be a lie, and which on API 31+
brings `CallStyle` and the answer/decline pair this ADR exists to refuse. The
words on the surface do not change: no "Mom is calling", no imitation of the
system call UI, and the line saying the walking stays on the phone.

The DND request is honoured only if Harbor holds notification policy access and
is ignored otherwise, so a phone inside a Sleep schedule can still swallow the
cue. That is worth knowing before a silent night is read as a trigger that
failed.

---

## ADR-010 — No location, no route tracking

**Status:** accepted (2026-09-10)

Considered: reading the user's route, via location or a fitness integration,
to time the cue more precisely.

**Rejected.** Three reasons, in order of weight.

1. **The app currently promises the opposite.** The explainer screen says, in
   these words: "Whether your phone thinks you are walking or still. *Not
   where you are*, not what you are doing, not which apps you use." Route
   tracking makes that false. The promise could be changed — but it would have
   to change on screen, in ADR-004, and in the study's consent language, all
   at once, and the trust it buys is the reason anyone grants the activity
   permission at all.
2. **It buys little.** The cue is designed to fire on the *completed stop*,
   and `BoutTracker` detects that directly. A route would let us anticipate a
   walk ending, which the design deliberately does not want.
3. **It is the most-refused permission on Android**, and it would sit beside
   activity recognition, which is already the biggest install-funnel risk in
   the product.

Also worth recording: **Google Fit is not an option regardless** — it is being
retired (ADR-005). The successor is Health Connect, and Maps has no route API
for this at all; it would be `FusedLocationProvider` and `ACCESS_FINE_LOCATION`.

**Revisit if** the week-one study shows the *timing* of cues is what people
dislike, rather than their frequency or existence. Until there is evidence of
that, this buys risk and spends trust for a refinement nobody has asked for.

---

## ADR-011 — Suppress cues during class, without committing to a data source

**Status:** accepted (2026-09-10)

Cues should not fire during a class. Walking between buildings and stopping
outside a lecture hall is exactly the transition this pipeline detects, and
exactly the wrong moment to act on it.

### The rule is source-agnostic on purpose

`CuePolicy.DayState` carries a single `busyNow: Boolean`, and `WeekBlock` is
a plain weekly time range. The policy has no idea where those times came from.

A `WeekBlock` gained a `kind` on 2026-09-12, when the schedule screen learned
to place free time as well as busy time. Only `BlockKind.BUSY` reaches this
rule: a block marked free changes what the schedule screen *offers* and
suppresses nothing. If marking time free also marked everything else busy,
somebody who planted two flowers would have silently turned their cues off.

That is deliberate. The obvious integration — DigiCampus, the campus system in
use here — publishes no API that could be found, and several unrelated
products share the name. Building the suppression rule around a specific
source would have made the source an architectural commitment before anyone
established it was even possible.

### Candidate sources, in the order I would try them

1. **Self-entered timetable.** No permission, no credentials, no integration
   to maintain, and a student types their week once. The prototype's Harvest
   screen is already this shape of data (ADR-007).
2. **`READ_CALENDAR`**, if timetables already live in Google Calendar. One
   permission, no credentials.
3. **A campus API**, if BITSoM IT will grant access.

**Not acceptable:** scraping the portal with participants' login credentials.
Asking a study participant to hand a student app their college password is a
security problem we would be creating for them, and no timetable is worth it.

A fourth arrived later and sits alongside the first rather than replacing it:
**a message forwarded to a WhatsApp bot** (ADR-014). No permission and no
credential either, and it is the only one of the four that keeps a week
current *after* onboarding, because it is fed by the announcements people are
already receiving. The rule below stays source-agnostic — this is one more
place a `WeekBlock` can come from, and the policy still cannot tell.

### Windows are weekly, not dated

A timetable's real shape. A one-off engagement is not worth modelling — the
cue is capped and dismissible, so being asked once during an unusual afternoon
costs almost nothing.

### A manual request still fires during class

Someone sitting in a lecture who asks for the prompt is making their own
decision. Consistent with every other gate `MANUAL` bypasses.

---

## ADR-012 — The flowers are artwork, not drawn geometry

**17 Sep 2026. Reverses, for the flowers only, the rule in
`docs/05-changing-the-ui.md` that every mark in the app is drawn on a canvas.**

### What changed

The twenty flowers ship as image files — `res/drawable-nodpi/flower_*.webp`,
two cuts of each, made by `tools/cut_flowers.py` from the twenty source PNGs in
`tools/flower-source`. Nothing draws a flower's shape in code any more.

### Why

Three separate attempts were made to draw them, in order: one petal function
for all twenty, then a silhouette per flower, then the illustration vectorised
and its Bézier outlines ported into Compose paths. Each one produced a
recognisable flower. None produced *the* flower. The artwork carries soft light
inside the petals and a glow through the throat that a fill cannot reach, and
the gap was still obvious at the third attempt, which was tracing the real
outlines rather than approximating them.

At that point continuing to draw them is a preference for a rule over a result.
The flowers are the reward surface of the whole product — they are what a week
of calling looks like — and they are the one place in the app where the picture
being right matters more than the picture being cheap.

### What it costs, accepted knowingly

- **They no longer restyle with the palette.** This was the reason for the
  original rule and it is a real loss: a future skin changes every other mark
  for free and cannot touch these. `Flowers.kt` still holds three colours per
  flower, off the same sheet, for the things that need a colour rather than a
  picture.
- **About 1MB of APK**, and heap while they are on screen — a decoded bitmap is
  width × height × 4 bytes.
- **They do not scale past their own size.** The cuts are sized for the largest
  place each is used; going bigger will go soft.

### What is still drawn

The two top-down views — the field's cells and the garden's dots — draw a
flower a few pixels across, hundreds at a time. There is no shape to recognise
at that size, only a hue, and the artwork would mean decoding every kind the
garden holds. Those keep the cheap petals-around-a-centre mark, and the rule in
`docs/05-changing-the-ui.md` is unchanged for every other icon in the app.

### If this is ever reversed

The last drawn version is the traced one, in the history of
`ui/FlowerMark.kt` and the deleted `ui/FlowerArt.kt`. It is the closest a
drawn flower got, and it is the thing to start from rather than starting over.

---

## ADR-013 — The other person has the app too, so Harbor gets a server

**17 Sep 2026. Reverses ADR-003 and ADR-004 (no server dependency, no
`INTERNET` permission) and ADR-007 (the parent gets no software). Supersedes
the header note in `0001_init.sql` that there is "deliberately no
parent-facing role, table, or policy".**

### What changed

Harbor is no longer a kid-and-parent app with one participant and one
bystander. Everybody who uses it has it. You add your aunt; she is a Harbor
account; you can ask to see when she is free and she can say yes. Future
features are collaborative by intent.

That makes a server load-bearing for the first time. Two accounts cannot share
anything through a `SharedPreferences` file.

### What it costs, accepted knowingly

These are the reasons the old ADRs existed, and they do not stop being true
because the decision changed.

- **"Nothing leaves this phone" stops being physically true.** It was not a
  policy before; it was the absence of an `INTERNET` permission and of any HTTP
  client in the dependency list. Anyone could verify it by reading
  `app/build.gradle.kts`. From here it is a promise about what the code does,
  which is a weaker kind of promise, and it has to be kept by review.
- **A new failure mode: the network.** ADR-003's actual argument was that the
  cue has to fire on a train with no signal. That argument is untouched and
  becomes a constraint instead of an architecture: sensing, the policy, the
  ledger and the cue surface must keep working with the radio off. The device
  stays authoritative for everything it already owns. Sync is a mirror, never a
  source of truth, and nothing in the cue path may await it.
- **Identity.** There was no account. Now there is one, and with it sign-in,
  sign-out, account deletion, and somebody's phone number on a server.
- **The study's consent basis moves.** Participants agreed to an app that could
  not send anything anywhere. That is no longer what they would be running. See
  "Before this ships", below.

### What is shared, and what is not

Only the *shape* of a week: day, start, end, busy-or-free. `WeekBlock.label`
is documented "Never leaves the device" and `week_blocks` in `0011` has no
column for it, which is the cheapest way to keep that promise — there is
nowhere to put it. "Busy 2–4pm Tuesday" crosses the wire. "Therapy" does not.

Sharing is asked for and granted, never assumed; it is read-only; it is
revocable, and revoking it takes effect on the next query because the policy is
evaluated per query and cached nowhere.

Not shared, and not proposed for sharing: the ledger, the garden, cues, or
anything about whether a call happened. Who you called and how it felt is the
most private thing in this app and none of it is anybody else's business.

### Identity is an email address

Phone was the first answer and lasted about an hour. It reads well for an app
about ringing people — the number you would type to add your aunt is the number
that finds her — but it means paying an SMS provider per sign-in and per retry,
during a study, before anybody has agreed the feature is worth having.

So: sign in with Google, with Microsoft, or with a code to your inbox.

Google and Microsoft are Supabase's own providers. `azure` is the one that
covers Outlook, Hotmail and Live, which are one account wearing three names —
there is no `microsoft` provider and asking for one returns a 400 that reads
like the account is bad.

**Yahoo is not a Supabase provider and cannot be configured as one.** That is
why the email code exists, and it is not a lesser third option: it needs no
provider set up at all and works for Yahoo, for a university address, for
anybody. It is the route that always works, and the two buttons are the
convenience.

Provider sign-in goes through the browser, not a WebView. A password field on a
screen Harbor drew is the exact shape of a phishing page, and both Google and
Microsoft refuse to load in a WebView for that reason. The browser comes home to
`harbor://auth`, which is `SupabaseClient.REDIRECT` and the intent filter on
`MainActivity`; the two have to agree or the sign-in ends on a page that cannot
be found.

`request_link()` in `0011` now matches on email, case-insensitively, because
nobody types their own capitals the same way twice and an address that fails to
match because somebody wrote Gmail with a capital G looks exactly like "she has
not signed up".

It leaks whether an address is registered, to somebody who already has that
address. Every invite-by-identifier system leaks exactly that. Writing it down
is better than implying otherwise.

### Before this ships to anybody in the study

1. **Re-consent.** The information sheet describes an app with no network.
   Running a syncing build against people who agreed to the other one is not a
   thing to do quietly.
2. **Offline first, proven.** A build with the radio off must still sense a
   walk, decide, fire a cue, record it and write the study file. If that is not
   true, this change has broken the product to add a feature.
3. **Account deletion.** `on delete cascade` is in the schema. There is no way
   to ask for it from the app yet.

### Still open

- Whether the week syncs at all for a user with no links. It has no reason to,
  and not uploading it by default is the better posture.
- What the requester sees while a link is `pending`, and whether the addressee
  is notified in-app or not at all.
- Whether two accounts that link each way should collapse into one mutual
  state in the UI, while staying two rows underneath.

---

## ADR-014 — A timetable can arrive by WhatsApp

**Status:** accepted (2026-09-18). Adds a fourth candidate source to ADR-011
and does not change the rule ADR-011 built.

### What

A participant forwards a message from a class group chat to a Harbor WhatsApp
number. A Supabase edge function reads the days and times out of it, files
them in `schedule_inbox`, and the phone places them on the week the next time
Harbor comes to the front.

`backend/supabase/functions/whatsapp/`, `0013_whatsapp_inbox.sql`,
`data/WhatsAppInbox.kt`, `ui/ForwardYourChats.kt`.

### Why

ADR-011 chose a self-entered timetable and listed what to try after it. This
is none of those three, and it beats two of them on the thing that actually
matters: it needs no permission, no credential, and no campus API that does
not exist. What it needs is a message people are already receiving.

The self-entered week is not wrong, it is just not kept up. Somebody draws
their week once in onboarding and then a class moves on a Tuesday afternoon —
and the grid, which is what stops a cue landing in a lecture, is now quietly
wrong in a way nobody will fix by opening a screen. The announcement of that
move is already in a chat on the same phone.

### What it costs, and why the schedule screen changed in the same commit

A forwarded message leaves the device. It goes to WhatsApp — so to Meta — and
to our function. That is a real cost, and it contradicted a line the schedule
screen was showing: *"nothing leaves this phone"*.

So that line changed, in the same commit, to what is still true: **what you
draw stays on this phone**. The part that does not stay says so itself, on the
card that offers it, where somebody deciding whether to use this can read it.
`CLAUDE.md` requires exactly this — the explainer's copy is a promise the code
has to keep — and it is the reason this feature is a card you opt into rather
than a setting that was already on.

Three things narrow the cost:

1. **The bot keeps nothing but times.** `readTimetable` returns day, start and
   end. There is no column in `schedule_inbox` for the text, the subject, the
   room, the sender or the group, and the row is deleted as soon as the phone
   has it. This is the same move as `week_blocks` having no label column: the
   cheapest way to keep a promise is to have nowhere to break it.
2. **It is opt-in twice.** Once by messaging the bot a code read off your own
   screen, and again every time you choose to forward something. Nothing is
   read from anybody's chats; a message arrives only because somebody sent it.
3. **It is off entirely by default.** No number configured, no card. Same
   posture as `SupabaseClient.configured`.

### The bot proposes; the phone decides

Parsed blocks land in `schedule_inbox`, never in `week_blocks`.

The device is authoritative for its own week (ADR-003), and `putWeek` replaces
rather than merges for that reason — a server writing into `week_blocks` would
be erased by the next upload. More to the point, a week with two authors is a
week nobody owns. So the function fills a queue and `WhatsAppInbox.drain`
empties it through the same `Windows.place` a finger goes through, which makes
a block that arrived by message indistinguishable afterwards from one that was
drawn, and removable by the same drag.

Draining on resume rather than on opening the schedule screen, because a class
that moved should already be suppressing cues before anyone thinks to look at
the grid — and because not having to open that screen was the point.

### It only ever adds busy time

A cancelled class is read and deliberately dropped rather than clearing the
block it names. Two reasons: a busy block is what *stops* a cue, so a
misparsed "no class Monday 9-11" that booked the hour would be wrong in the
worst available direction; and marking the freed hour `FREE` would be worse
still, because a flower means *this is when I would welcome a call* (ADR-011),
which a cancelled lecture does not say. Removing time stays a gesture on the
grid.

### Rejected

- **Reading chats on the device.** A notification listener over WhatsApp would
  need no forwarding at all, and would be Harbor reading every message a
  participant receives. Not for a timetable, not for anything.
- **The bot writing `week_blocks` directly.** Above.
- **Keeping the message to improve the parser.** Tempting and refused: a
  corpus of participants' group chats is the single most sensitive thing this
  project could hold, and the parser is testable without one.

### Open

- The parser resolves "today" and "tomorrow" in Asia/Kolkata, hard-coded. Fine
  for a BITSoM study, wrong the moment it is not one.
- WhatsApp's 24-hour customer service window: outside it, free-form replies
  are refused and only a template will send. A participant who forwards
  something after a day of silence may get no confirmation. The blocks are
  still filed.
- Nothing tells the participant in-app that blocks arrived; they simply appear
  on the grid. Whether that wants a mark of its own is a question for the
  study, not for now.

---

## ADR-015 — A week can be pulled from the phone's calendar

**Status:** accepted (2026-09-23)

ADR-011 listed `READ_CALENDAR` second among the places a timetable could come
from. Participants asked for it: a lot of them already keep lectures in Google
Calendar, and drawing the same week a second time is the tedium the schedule
screen exists to remove.

### What it does

A "From calendar" button at the foot of the week view. Pressing it asks for
`READ_CALENDAR` if Harbor does not have it, reads the seven days from the start
of today (instances, so recurring events arrive expanded), and places each
timed event on its weekday as a busy block, labelled with its title. It is
placed like a drawn block, so it cuts whatever it lands on. An undo follows.

All-day events and events the calendar marks *free* are skipped: a birthday
is not a busy day, and an event its owner said does not block time should not
block a cue.

### Why the permission is acceptable here

- **Asked at the press, never at onboarding.** Activity recognition is the
  funnel's biggest risk (CLAUDE.md, ADR-002). A calendar prompt beside it
  would spend trust in the worst place; a prompt that appears because the
  person just asked for their calendar spends almost none.
- **Read once per press.** No sync adapter, no observer, no background read.
  The calendar is not touched again until the button is pressed again.
- **Nothing leaves the device.** Titles become `WeekBlock.label`, which never
  syncs. The pull adds nothing to what the explainer screen promises about.
- **Refusing costs nothing.** Denied, the button says so and the week is
  unchanged; drawing and the WhatsApp bot (ADR-014) still work.

### Dated events on a weekly week

The calendar is dated and the week is weekly (ADR-011), so next Tuesday's
lecture becomes every Tuesday's. That is the right reading for a timetable,
and a one-off that lands for a week is cheap: the cue is capped and
dismissible, and the block drags off.

### Rejected

- **Keeping it in sync.** A calendar observer would keep the week current
  without a press, at the cost of Harbor reading the calendar indefinitely.
  The WhatsApp bot already covers "stays current" without a standing read.

---

## ADR-016 — A bee-narrated tour, once, in both arms

**Status:** accepted (2026-09-23)

### What

Right after onboarding finishes, the app offers a short bee-narrated walk
through Home, the field, a person's dial, Schedule and Account -- one short
sentence per stop, `Next`/`Skip` always both there. Seen once by default;
replayable from Account. `domain/Walkthrough.kt`, `ui/TourBee.kt`, wired in
`MainActivity`.

### Why the bee narrates the garden arm too

[StudyArm] is otherwise strict: only rendering and words differ between the
two arms, and the garden arm's whole identity is *no character* -- that is
the control the bees arm is being compared against. A bee explaining the
garden arm's own screens looks, at a glance, like exactly the leak that rule
exists to prevent.

It is allowed here, narrowly, because the tour is not the studied experience:

- **It touches nothing `StudyExport` reads.** No `Moment`, no ledger row, no
  column anywhere carries whether the tour ran, how far, or that it existed.
  A participant who saw it and one who skipped it in the first second are the
  same row in every table that matters.
- **It never recurs inside the ongoing screens.** `GARDEN_BEE` -- the one
  stop that would put a bee inside the field itself -- is excluded outside
  the bees arm (`Walkthrough.stops`), so Home, the field, Schedule and
  Account look exactly as separated once the tour ends as they did before it
  ran.
- **It is symmetric.** Both arms get the same stops in the same order saying
  the same things about the same screens; only the narrator's presence is
  shared, not anything either arm measures.

If the tour ever grows to shape what a participant sees session to session,
or to record anything, this reasoning stops holding and the exception needs
revisiting -- see the note at the top of `domain/Walkthrough.kt`.

### Why after onboarding rather than inside it

`OnboardingScreen.kt` used to end with four explainer cards -- the weather
metaphor, the two thresholds, what a reminder is, what the family saw --
before ADR-013 removed the family and a later pass removed the cards
themselves. The comment left behind says why: nine screens in, past a
consent page, with the app still not visible, is the wrong moment for
content like this, and it belongs in "a tutorial somebody chooses to open."

This tour is offered rather than forced for the same reason, just one step
earlier than "chooses to open" implies: it appears the instant the real
Home is on screen, not appended to the queue that leads there, and `Skip` at
the very first stop *is* declining to open it.

### The tour drives real navigation, not mock screens

Each stop sets `MainActivity`'s own `screen` (and, for the two person stops,
`showing`) to the real destination and narrates the real thing underneath --
no illustrated stand-ins, no second copy of any screen to keep in sync. The
card that speaks never dims or blocks what it is standing in front of.

### Rejected

- ~~**A precise spotlight with a cutout round each control.**~~ Built after
  all, on 2026-09-23 -- see the amendment below.
- **Skipping the person stops when nobody has been added.** Cannot happen in
  practice -- `WhoToCall` is not skippable in onboarding -- but
  `Walkthrough.stops` still guards on it rather than assuming, so a contact
  deleted before the tour runs drops those stops instead of opening a page
  with nothing on it.

### Amended 2026-09-23: a spotlight, not a card

The floating card was too easy to read past -- a strip at the foot of a
screen that looked like one more thing on it. The tour is now a spotlight:
the app under a dark veil, the one thing being talked about cut out of it
and ringed, the bee large in a corner, and what it says in a speech bubble
beside the lit thing. A tap anywhere finishes the sentence, a second moves
on; `Skip` is still on every stop.

That needed the registry this ADR had rejected, so it is built to cost
nothing when no tour is running: `Modifier.tourAnchor` writes each tagged
element's bounds into a plain map (not snapshot state) on placement, and only
the overlay, while it is up, polls that map once a frame. Tagged elements
scroll themselves into view when the tour reaches them. The schedule turns
itself to the day view for quiet hours and back to the week for the rest,
which is why those stops now run week-first.

The veil takes every touch while the bee is talking. Nothing underneath is
live mid-tour; back, `Skip` and the last stop all end it.