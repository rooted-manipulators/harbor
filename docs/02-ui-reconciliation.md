# Reconciling the backend with the UI prototype

**Source:** https://github.com/Bored-Kxiden/harvest-pulse — a v0-generated
Next.js prototype. The product logic lives in `lib/harbor/model.ts`; the
screens are `components/harbor/*.tsx`.

**Pinned at `433c1d3` (2026-09-11).** Previously `e60eef7`; see "The v2
rebuild" below for what changed and why the bump was worth taking.

**Previously pinned at `e60eef7` (2026-09-10).** The prototype is a live target — it
gained three features while this document was being written — so the schema
tracks a named commit rather than whatever `main` happens to be. Bump the pin
deliberately, in a PR that also updates the table below. Do not chase it
commit by commit: half of what lands there may be cut before the study, and
migrating twice for a feature that never ships is wasted work.

**Settled 2026-09-10:** v0.1 targets the whole prototype, not Slack Tide
alone. The prototype is the reference for product behaviour, resolved
divergence by divergence below. The spelling is **Harbor**. The Android app
and backend stay in this repo; `harvest-pulse` remains the v0-owned web
prototype.

Read this before extending the schema or the Kotlin model. The schema in
`backend/supabase/migrations/0001_init.sql` was derived from the Slack Tide
handoff alone, and the prototype turns out to describe a **substantially
larger product**. Slack Tide is one of eight screens.

## The prototype's shape

| Screen | What it holds |
| --- | --- |
| Home / Inbox | Per-person notes and unread state |
| Harvest | Mutual schedule sharing and the privacy/consent surface |
| Garden | Jar of Happiness — `jarDays`, seasons, milestones |
| Conversation | Per-person chat (`ChatMessage[]`) |
| Dispatch | The content pipeline — headline/photo/audio, draft→delivered |
| Slack Tide | The cue simulator |
| Settings | Thresholds, sound, reduced motion, "what counts as enough" |

The handoff explicitly put Dispatch, the Jar, and parent-side surfaces
**out of scope**. The prototype builds all of them. That gap is the main thing
to resolve — see "Decisions needed" below.

## Divergences from what is built

Confirmed by reading `lib/harbor/model.ts`. Each of these is a real
disagreement, not a naming difference.

| # | Prototype | Built here | Impact |
| --- | --- | --- | --- |
| 1 | `Resolution` has five variants — adds `message` | Four variants | DB enum + Kotlin enum |
| 2 | Four people (mom, dad, aanya, "our little tribe"); every `Moment` carries `person` | A single `Contact`; entries have no person | Schema change: contacts become a set, `ledger_entries` needs a person FK |
| 3 | `source: manual \| walking_stop \| dispatch` | `manual \| walking_stop \| session_end` | Enums disagree in both directions |
| 4 | `cues[]` tracked separately from `moments[]`; the daily cap counts **cues**, and moments carry `cueId` | Cap counts ledger entries | A cue that produces no moment is invisible to the cap here |
| 5 | `settings.cuesEnabled` — a global off switch, default **off**, gated behind a privacy dialog | No such flag | Answers the handoff's open "degraded mode" question |
| 6 | `settings.minimum: any \| call \| null` — user defines what fills the Jar | Not modelled | Needed if the Jar ships |
| 7 | `settings.sound` (chime/soft/silent), `reducedMotion` | `cueSoundRef` on the contact | Different shape |
| 8 | `sharing`, `momConsent`, `schedules[day] = {you, mom}`, `sharedWindows()` overlap | Nothing | **See the ADR-004 note below** |
| 9 | `proposed_later` sets `reminderDone`; the reminder is in-app on next open, not a notification | No `reminderDone` | Suppression can't tell a done reminder from a pending one |
| 10 | "Already connected today" counts `called`, `reacted` **and** `message` | Counts `called` only | Behavioural difference in suppression |

### One bug this surfaced, now fixed

The prototype takes its cooldown from the last cue on **any** day
(`state.cues.at(-1)`). The Kotlin version derived it from today's entries,
so a cue at 23:55 would not suppress one at 00:05 — "today" is empty by then.
`CuePolicy.decide` now takes `lastCueAt` separately, with a regression test.

### ADR-004 needs amending, not overturning

ADR-004 says "no parent-side read path anywhere in the schema". The prototype
has `schedules[day] = { you, mom }` and computes shared free windows, gated on
`sharing && momConsent`.

That is **availability**, not activity — and it is mutual and consented, which
is a different thing from the guardrail the handoff was protecting. The
guardrail stands as written for activity data. ADR-004 should be tightened to
say exactly that, rather than being read as forbidding the Harvest screen.

Note also: `cueEligibility` and the Slack Tide screen both state the privacy
boundary in user-facing copy. That copy is a commitment. Whatever the schema
ends up allowing, it must not contradict those sentences.

## Resolved: the parent gets no software

**Decided 2026-09-10 — see ADR-007.** Conversation becomes one-sided (notes
the user writes, handed off to SMS or WhatsApp to actually send) and Harvest
becomes self-entered (the user's own guess at when their person is free).
`sharing`, `momConsent` and the schedules table are dropped. The UI copy
promising mutual consent has to change before the study.

The reasoning, kept because it is the argument to re-open if the study pushes
back:

### Why it was a blocker

ADR-002 says the parent installs nothing, and that is load-bearing — it is the
argument that killed the whole VoIP-fork category.

Two prototype screens assume otherwise:

- **Conversation** is two-way chat. `ChatMessage.mine: boolean` means someone
  on the other end is sending.
- **Harvest** stores `schedules[day] = { you, mom }` and computes overlapping
  free windows. Mom's intervals have to come from somewhere.

In the prototype both are local fakes — a seeded sample family in
`localStorage`. That is fine for a demo and impossible for a week-long study
with real people. Either the parent gets a surface, or these two screens mean
something different in v0.1 than they appear to.

This gated roughly half the tables in a whole-prototype build, which is why it
was settled before migration 0003 was written.

## Divergence walkthrough

All ten resolved 2026-09-10 and implemented in migrations 0003 and 0004.
"Adopt" means the prototype's behaviour is the target and the schema follows
it.

| # | Divergence | Decision | Notes |
| --- | --- | --- | --- |
| 1 | Fifth resolution, `message` | **Adopt** | A distinct user action, and `minimum: 'any'` counts it toward the Jar. Enum add on both sides |
| 2 | Per-person entries | **Adopt** | `contacts` is already multi-row; needs `ledger_entries.contact_id`. Note the sample set includes a *group* ("Our little tribe"), so contacts need a `kind` of person vs group |
| 3 | `dispatch` source; prototype has no `session_end` | **Adopt `dispatch`, keep `session_end`** | Settings already exposes `sessionMinutes` and calls it "saved for future native support". Keeping it costs nothing; removing it means a migration to add it back in v0.2 |
| 4 | Cues tracked separately from moments | **Adopt** | New `cues` table, `ledger_entries.cue_id`. The cap counts cues, and a cue that produced no moment is exactly the silent-dismissal signal the study wants |
| 5 | `cuesEnabled`, default off, behind a privacy dialog | **Adopt** | This answers the handoff's open "degraded mode" question, and a real global opt-out is a guardrail in its own right |
| 6 | `minimum: any \| call \| null` | **Adopt** | Needed for the Jar |
| 7 | Global 3-option `sound` vs per-contact `cueSoundRef` | **Open** | The two disagree. See below |
| 8 | `sharing`, `momConsent`, schedules | **Dropped** | No second party to share with. ADR-007 |
| 9 | `reminderDone` | **Adopt** | Current code infers "pending" from `proposedTime > now`, which is a proxy that breaks when the user acts early or late. Store the fact instead of guessing it |
| 10 | "Already connected today" counts `called`, `reacted`, `message` | **Adopt** | A real product judgement — it treats sending a heart as connecting. That matches the no-pressure design and the copy ("You already connected today. Enjoy the quiet.") |

### 7, in more detail

The handoff lists a per-person cue sound as an MVP screen: "the parent's
ringtone or a chosen song". That personalisation is arguably the point of the
cue — a sound that means *this person*, not a generic notification.

The prototype instead has one global choice of chime / soft / silent, which
reads like a v0 simplification rather than a design decision.

Recommendation: keep both. A global default in settings, an optional
per-contact override. It is a superset, so neither design is foreclosed.

## Outstanding at the pin

`e60eef7` (PR #1) added Lighthouse Beacon, Quick Share and a daily family
game. Partially absorbed:

| Prototype addition | Status |
| --- | --- |
| `Resolution` gains `played` | **Done** — in the enum. Deliberately *not* a connection: playing the game is nice, but nobody heard from you. Matches `cueEligibility`, which still counts only called/reacted/message |
| `source` gains `signal`, `game` | **Done** — in the enum |
| Lighthouse Beacon | **No schema needed.** `beaconWarmth()` derives entirely from existing moments |
| `Signal` type + `signals[]` (Quick Share) | **Not built** — needs its own table, plus media storage |
| `games: Record<string, string>` | **Not built** — needs a small day-keyed table |

The enum values were taken now even though two of their features do not
exist, because adding a value to a live enum cannot happen in the same
transaction that uses it. Taking them early costs a line; taking them late
costs a two-migration dance.

## Schema shape

`user_settings` (not `user_thresholds` — it holds four preferences as well as
the four numbers).

**Identity belongs to the device.** Every row the app creates uses a UUID the
phone generated as its primary key. There is no server-generated id to map
back to, so sync is a plain `on conflict (id) do update` and a phone that has
been offline for a week pushes its whole backlog in one call. An earlier draft
had server-generated `id` plus a separate `client_id`, which quietly made
`ledger_entries.cue_id` unfillable: the device cannot know a server id it has
never read back.

**Thresholds match the prototype exactly** — 10 walking minutes, 20 session,
cap 2, cooldown 120 — and so do the accepted ranges, so a value one layer
takes cannot be refused by another. The study's drift view reads its baseline
from the column defaults rather than hardcoding them, so retuning a suggestion
cannot silently desynchronise the analysis.

## Repo hygiene in harvest-pulse

Two files are committed that should not be: `harvest-pulse-updated.zip` and
`harbor-quick-share-beacon-game.patch`. Worth removing and gitignoring.


---

# The v2 rebuild

The prototype was rebuilt between `e60eef7` and `433c1d3` — commit
"Rebuild Harbor around the garden, the cue and the flower a call leaves". Its
state is now explicitly `version: 2`. This is the pin the native app tracks as
of 2026-09-11.

This was not a tweak. `lib/harbor/model.ts` changed by 282 lines, several
components were deleted, and the reward mechanic was replaced outright.

## What changed

| v1 | v2 | Consequence here |
| --- | --- | --- |
| `season: Quiet \| Steady \| Full` | `weather: clear \| bright \| cloudy \| rain \| storm` | New enum; `user_settings.weather` |
| Jar of Happiness, `jarDays`, `settings.minimum` | **Gone.** Replaced by the garden | `minimum` dropped from settings |
| `dispatches` and the Dispatch pipeline | **Gone entirely** | `dispatch` source now vestigial |
| `signals` (Quick Share) | → `notes` (`note \| snapshot`) | `signal` source → `note` |
| `people` as a hardcoded constant | `people: Person[]` in state, with `tone` and `photoId` | `contacts.tone` |
| `RewardShown` (readout / jar fill / wrapped clip) | **Gone.** A call grows a flower | Column left vestigial |
| — | `Feeling` (light / warm / steady / tender) | New enum, asked after a call |
| — | `FlowerKind` — a library of eight | New enum, derived from feeling and kept |
| — | `Moment` gains `minutes`, `feeling`, `flower`, `topic` | Four new columns |

## The garden is the reward now

The Jar counted days that met a threshold the user set. The garden does not
count anything: a call grows a flower, the flower depends on how the call felt
and how long it ran, and it stays. There is no score, no streak, and nothing
that can be lost.

That is a better fit for the guardrails than the Jar ever was — "no streak
mechanics, no breakable chain" was already a non-negotiable from the design
audit, and the Jar's "what counts as enough" setting was in tension with it.

`lib/harbor/garden.ts` derives plot shapes and flower positions from a hash of
the person's id: a plot keeps its outline and a flower keeps its spot for as
long as it exists. Porting that to Compose is arithmetic, not guesswork — the
functions are pure and the constants are all in that file.

## The cue design, and ADR-009

The v2 cue (`components/harbor/cue.tsx`) independently arrived at what
ADR-009 argued for: it carries a "harbor" mark, opens with "Looks like you're
free", and states the privacy boundary on screen. It evokes a call without
pretending to be one.

It also adds three things the native cue does not have yet:

- **The expected length** — "calls with her usually run ~12 min", from
  `usualCallMinutes`. The ask has a known size before anyone commits to it.
- **Topic chips** before the call — "Catch up", "Ask for help", "Share news",
  "Just because". The user gives the call a shape first.
- **A written line** for the reaction path, rather than a bare heart.

## What is ported and what is not, at this pin

| Prototype | Native |
| --- | --- |
| Cue (`cue.tsx`) | Built, but to the **v1** design. Needs the length line, topics, and the written reaction |
| Post-call (`call.tsx`) | **Not built.** This is where feeling and duration are captured, and therefore where flowers come from |
| Garden (`garden-view.tsx`, `flowers.tsx`, `garden.ts`) | **Not built.** The largest remaining piece |
| Home | **Not built** |
| Notes, Schedule, Settings, Daily question | **Not built** |
| Conversation | Reshape, do not port — ADR-007 |

## The quiet gap between reminders — 18 Sep 2026

The prototype suggests two hours, `0001_init.sql` shipped two hours as the
column default, and `Thresholds.SUGGESTED` matched. Harbor now suggests **no
gap at all**, which is the one calibration number that no longer agrees with
the prototype.

Not a retune of a number somebody disliked. The gap had no control on any
screen — its stepper had been removed from settings on the reasonable-sounding
argument that nobody opens a settings screen wanting to choose the minutes
between their own interruptions — so it was a two-hour rule wearing a
suggestion's clothes, against "thresholds are user-set, never a locked
default". It was also checked before almost everything else, which meant a day
of testing produced one reminder every two hours however high the daily number
was set, with nothing on any screen saying why. The trigger could not be
watched working.

So the stepper is back, it reaches zero, and zero is what is suggested. The
daily cap is the limit that remains, and it is the limit the handoff's
frequency argument actually rests on.

What this costs, and it is a real cost: two reminders can now land close
together, which is the thing the gap existed to prevent. Anybody who wants the
quiet back can set it in one place, which was never true before. If the pilot
shows people being interrupted twice in ten minutes and minding, the number to
move is this one, and moving it is now a change to a suggestion rather than to
a rule.

`0012_cooldown_may_be_nothing.sql` widens the CHECK from `1..1440` to
`0..1440` and moves the column default, so the app and the schema still agree
about what is a legal value.
