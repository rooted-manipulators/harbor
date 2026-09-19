# Harbor pre-study audit — overnight, 19 Sep 2026

Branch `dark-reskin`. Everything below was checked against the running code
or the running app on the Galaxy S24+, not recalled. **Fixed** items are
committed; **open** items are not.

---

## Blockers found and fixed

### 1. The study arm did not apply on the run it was claimed on — FIXED
`6dc60d7`. The arm was read once in a `LaunchedEffect` at process start,
which is *before* the code screen. Typing `B-01` wrote `bees` to
preferences and left the running app in the garden arm; the bee appeared
only after the next cold start.

Every bees participant would have done the whole of their first session —
the part somebody sits and watches them through — in the control arm, with
nothing anywhere saying so. `armFlow` is observable now, still write-once.

Verified on a wiped install: enter a B code, walk onboarding, bee is on the
slider before any restart. It was a sun before the fix.

### 2. The control arm's slider had stopped doing anything — FIXED
`31fc74a`. The slider used to paint the sky. When the sky became the hour
(`SkyHour`) it stopped being the slider's answer, and nothing replaced it:
nothing a participant sees reads `settings.weather` any more. Meanwhile the
bees arm had a face on the thumb that changed as you dragged.

The comparison had inverted — the arm under test was the responsive one and
the control was inert. Both arms now answer on the thumb, same value, same
place, same size. `Sky.drawEmblem` already existed and was unreachable.

### 3. The scrolling trigger could never have fired on a real phone — FIXED
`52034a6`. `targetSdk 37` filters package visibility;
`getLaunchIntentForPackage` returned null for every third-party app, so
every stretch was excluded. Confirmed empty on the phone before the fix.
`<queries>` for LAUNCHER and HOME.

### 4. The scrolling cue said something untrue — FIXED
`768b657`. "You just put something down" — written when the trigger was to
fire *after* a session, and it fires during one. ADR-009 allows Harbor to be
wrong about whether this is a good moment, never about what just happened.

---

## Majors found and fixed

### 5. A week of zero cues had five explanations and the file recorded none
`0fa9f6f`-ish (grants commit). Notifications off, no full-screen grant, no
overlay grant, no usage access, a frozen process — each turns the study's
first question into a zero that reads as behaviour. Export format 4 now
carries all five plus `source_cap`.

### 6. A temporarily-refused scroll stretch was refused for ever
`52034a6`. Half of `CuePolicy`'s reasons expire. Hitting twenty minutes at
the end of a busy block meant refused at the one moment you could not be
reached and never asked again, though the scrolling continued. Holds retry
after ten minutes; fires never do.

### 7. One bad tick killed the trigger for the rest of the week
`52034a6`. A single throw ended the coroutine with nothing to restart it.
Separately, an uncaught throw in a `launch` took the process down on every
start, so the app could not be opened to fix itself.

### 8. Revoking activity recognition stopped the scrolling trigger too
`52034a6`. `Sensing.repair` returned early before starting the foreground
service, so the process went back to being frozen — including for the
trigger that does not use activity recognition at all.

### 9. Two hours in a pocket read as two hours of scrolling
`52034a6`. The screen-off events that end a stretch are API 28; minSdk is
26. `RESUME_GAP` closes it without depending on them.

### 10. Consent copy understated what was kept
`768b657` and the copy commit. Three places said "your walking" or "your
movement" to people who had also turned on app-reading: the terms screen,
the cues settings screen, and the cue surface's own footer.

### 11. The bees arm introduced its mascot by overlapping the sentence
`31fc74a`. `bee_standing` is placed for an arch with a flower in it. An
empty arch has three centred lines instead and they run through it — and an
empty arch is what every participant sees before their first call.

### 12. A forty-minute video call was forty minutes in one app
`52034a6`. `AudioManager.mode`, which costs no permission.

---

## Minors found and fixed

- **13.** The session threshold had no control anywhere, making it the
  locked default `01-decisions.md` forbids. Stepper on Settings (`6b77d6c`)
  and on the onboarding limits card, which is titled "What you keep control
  of" and listed every threshold except the one for the trigger the
  participant had just chosen.
- **14.** Migrations `0012` and `0014` both said `alter table settings`;
  there is no such table. Neither had ever been applied. Corrected in place.
- **15.** The schedule's day swipe used `LongPress` — a heavy thud meant for
  "you have held this long enough", landing after the motion. Now a detent
  tick (`ui/theme/Buzz.kt`).
- **16.** Tapping a deck indicator animated even with reduced motion on.
- **17.** Switching span can shrink the deck under the pager; the last page
  briefly asks for an index that has stopped existing. Guarded.

---

## Open — worked through later the same morning

### A. Arm B is still a bee on a thumb and a bee in an arch
Agreed in the A/B design and not built: the bee flying into the field on a
slider change, and the mother bee watering a bud in place of the flower
animation. `bee_tending.webp` ships unused — that is the asset for the
second one. The arms are now *comparable* (see 2), which is what the study
needs; they are not yet as different as the design intends.

### B. Dead code that will mislead the next reader
`GardenCanvas` (GardenScreen.kt) and `HomeBud` are declared with zero
callers. `GardenCanvas` dying takes `Sky.gradient`, `Sky.veil`,
`Sky.drawWheel`, `drawPlot`, `blobPath`, `toneOf` and most of
`domain/Garden` with it. Left alone deliberately the night before a study.

### C. The study code is not confirmed back to the researcher
`fromCode` takes the first letter: a mistyped `8-01` silently becomes the
garden arm with no feedback. **Procedure, not code: check Account → Study
code on every phone before handing it over.** Telling the participant their
arm on screen would be worse.

### D. The keyboard covers Next on the name step
Dismissing it reveals the button. Minor friction on the first text field a
participant meets.

### E. Reduced motion is not honoured everywhere
`FlowerLanding`'s 900ms bloom, the onboarding petals and the field's own
motion do not check it. One setting, several screens.

### F. Walk cues now take the screen too
A consequence of the overlay grant, not a bug: with "display over other
apps" on, `CueNotifier` opens the surface itself whenever the screen is on,
so a *walking* cue is now full screen on an unlocked phone where it used to
be a banner. That is what ADR-009 asks for. Worth knowing before somebody
reports it as a change.

### G. Supabase schema does not carry the new export fields
`grants` and `scroll_cues` exist in the JSON the study actually reads.
Nothing syncs settings to the backend, so this is only a tidiness gap.


---

## Second pass — the open list, worked through

All seven revisited. Two of the notes above turned out to be wrong and are
corrected here rather than quietly fixed.

**A — done, in the half that could be built.** A bee leaves the slider and
goes into the field, where it now lives permanently: one bee, always there,
its pose and its pace set by the mood. The flight is animated from the
illustrator's reel. The mother bee watering a bud is still blocked —
`bee_tending.webp` is a still and the animation was never sent.

**B — done, and the note was wrong twice.** `GardenCanvas`, `HomeBud`,
`drawPlot`, `blobPath`, `toneOf`, `Sky.gradient`, `Sky.veil`, `drawWheel`
and `drawRing` are gone: 593 lines. But `domain/Garden` is **not** dead —
`Field`, `Terrain` and `PersonScreen` all use its geometry — and
`Sky.drawEmblem` is **not** dead either, since the mood slider's thumb
wears it in the garden arm. Both were checked rather than assumed before
anything was deleted.

**C — nothing to change.** Still procedure. Check Account → Study code on
every phone before handing it over.

**D — done.** The keyboard's own action key now submits: Done on the name
step, Next-then-Done across the contact pair. The number field gets a phone
pad, which it should always have had.

**E — done, and narrower than the note claimed.** `FlowerLanding` and
`Petal` already honoured the setting through a parameter rather than the
composition local, which is why a grep for the local missed them. The two
real gaps — the nav tab's springy scale and the person screen's disclosure
— are closed. Colour cross-fades deliberately stay: colour is not motion,
and snapping it is a harsher screen rather than a calmer one.

**F — nothing to change.** Informational: with the overlay grant, walk cues
take the screen too. That is what ADR-009 asks for.

**G — done.** Six nullable grant columns, folded into 0015 rather than
stacked on top of it, since 0015 has never been applied.
