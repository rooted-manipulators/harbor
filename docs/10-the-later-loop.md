# The later loop

Written 17 Sep 2026, against the running code and one real phone.

Somebody taps "later" on a reminder. This is what happens next, why nothing
happened next until today, and what it cost.

---

## What was wrong

`HarborRepository.markReminderDone` was implemented and **had no callers**.
Nothing in the app — no screen, no flow, no receiver — ever marked a
proposed-later plan as dealt with.

That was not a dead function. `HarborStore.dayState` set
`hasPendingReminder = ledger.any { it.resolution == PROPOSED_LATER && !it.reminderDone }`
across every day, and `CuePolicy` holds every sensed reminder with
`Reason.REMINDER_PENDING` while that is true. So the first "later" anyone
tapped suppressed sensed reminders **permanently**, with no way back short of
clearing the app's data.

Found live: a proposed-later row written on 15 Sep, for 12:30 on 16 Sep, was
still blocking on 17 Sep and would have gone on blocking for the rest of the
study week. `RemindersTest` keeps that exact row as a regression test.

Two things follow that are worth saying out loud:

- **The app was breaking a promise it makes on screen.** `CueSurface` sells
  each preset as "a reminder inside Harbor". There was no reminder inside
  Harbor.
- **It inverted the one promise this product is built on.** Dismissing costs
  nothing, by design and by ADR-009. Tapping "later" — the *most* engaged
  answer short of calling — silently switched the product off. The gentlest
  answer on the surface was the only destructive one underneath.

And for the study: `reminder_done` is exported on every entry and constrained
in `0001_init.sql`. With nothing setting it, that column was guaranteed
all-false in every participant's file. A field that cannot vary measures
nothing.

## The decision

One boolean was doing two jobs:

1. **The hold** — should sensed reminders stand down *right now*? A question
   about the clock. It is not a fact about anybody and nothing about it should
   be written down.
2. **The fact** — did this person close the loop? A fact about them, which
   goes in the study file and may only be set by something they actually did.

`docs/02-ui-reconciliation.md` row 9 adopted `reminderDone` precisely so that
"pending" would stop being inferred from `proposedTime > now`, which "guesses
wrong whenever the user acts early or late". That reasoning stands. The
missing half was never the fact — it was the setter.

So the two were separated rather than one being traded for the other:

- The hold moved to `domain/Reminders.holding`, which answers it against the
  clock and writes nothing.
- The fact stays exactly where it was, and is now set by three real things:
  the card, a call, or the person saying they already called.

A plan nobody ever closes stops holding reminders back and keeps
`reminder_done = false`. That is the honest record — *they made a plan and it
lapsed* — and it is a row the study can count.

## What was built

- **`domain/Reminders.kt`** — pure, unit-tested, no Android imports.
  `holding()` for the suppression, `due()` for the card, `closedBy()` for a
  plan a call closes on its own, and `Closed` (`REACHED_THEM` / `SAID_SO` /
  `LET_GO`) as the category the study sees.
- **The card on Home** — "You made room for this." with the time they chose,
  a call button, and two quiet ways out. Modelled on the "How did that go?"
  card beside it, including its window reasoning. It *waits* to be found: a
  reminder that comes looking for you is a cue, and a cue is the one thing
  this person just said "not now" to.
- **Closing by reaching them** — `HarborStore.append` closes any live plan for
  that contact when a connection row lands, so the card never asks whether you
  called your mother seconds after Harbor dialled her.
- **`Moment.REMINDER_CLOSED`** — additive, a category, no free text. A
  proposed-later row with no beat against it is a plan that lapsed, which is
  itself a finding for study question 2.

Two constants, both in `Reminders`:

- `GRACE = 2h` — how long a plan keeps holding after its own time. The hold
  always ends relative to the plan the person made, never in the middle of
  one, so the longest possible hold is the "Tomorrow" preset plus two hours,
  about thirty. If that is too much of a study week to lose, the fix is a
  second bound on *when the plan was made* — not a shorter grace, which would
  jump the gun on somebody about to pick up the phone.
- `WINDOW = 12h` — how long the card keeps offering. Same length and same
  reason as `CallStats.WINDOW`.

## Two things it deliberately does not do

- **"I already did" writes no call.** It closes the plan and nothing else.
  Harbor did not see that call, so Harbor does not put one in the study's
  data. `called` already means "reported a call" (docs/03); it should not
  quietly start meaning "mentioned in passing".
- **Nothing auto-sets `reminder_done`.** Not on a timer, not on expiry. The
  hold expires; the fact does not. Anything that writes that field because a
  clock passed is forging a row in somebody's study file.

## Still open

- The cue's own copy says the plan "becomes today's next nudge", while a plan
  made for tomorrow evening holds into tomorrow. The presets cannot reach
  further than that, so the copy is the thing that is wrong, not the
  behaviour. Worth a word change.
- Nothing in the app can propose a time more than about thirty hours out. The
  `DayState` doc comment used to defend "a plan made on Tuesday for Friday",
  a case the UI cannot produce.
