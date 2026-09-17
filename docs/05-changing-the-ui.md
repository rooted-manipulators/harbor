# Changing the UI in Harbor

**Who this is for:** whoever wrote the prototype and now wants to restyle,
reskin or redesign the real app. You know this design better than the code
does. This document exists so you do not have to read 7,300 lines of Kotlin to
find the four files that decide what Harbor looks like.

The other docs explain *why* things are the way they are. This one explains
*where they are* and *how to change them*.

---

## The short version

Your prototype at `Bored-Kxiden/harvest-pulse` is the **design authority**. It
was not embedded, wrapped or iframed — every screen was hand-translated into
Jetpack Compose, because a WebView would have meant no lock-screen cue, no
ringtone, and no activity sensing, which is the entire product.

So there is no TSX in this repository. There is Kotlin that was written by
reading your TSX and CSS line by line, with each piece naming the rule it came
from. `docs/04-how-the-repos-fit.md` covers why the repos stay separate and
how changes flow between them; the current pin is `433c1d3`, recorded at the
top of `docs/02-ui-reconciliation.md`.

**What this means for you in practice:** you cannot change Harbor's appearance
by editing CSS. But you also do not have to learn Android. Almost everything
visual is concentrated in four files, and they were deliberately built to be
the CSS-equivalent layer.

---

## Where your files went

| Prototype | Harbor | Notes |
| --- | --- | --- |
| `app/globals.css` — variables | `ui/theme/Color.kt` | The palette, one constant per CSS variable |
| `app/globals.css` — type | `ui/theme/Type.kt` | The type scale. **See the font gap below** |
| `app/globals.css` — classes | `ui/theme/Harbor.kt` | `.flow`, `.surface`, `.eyebrow` and friends, as composables |
| `app/globals.css` — radius | `ui/theme/Theme.kt` | Shapes, plus the full Material colour scheme |
| `components/harbor/home.tsx` | `ui/HomeScreen.kt` | Reordered by review — see below |
| `components/harbor/call.tsx` | `cue/CallFlow.kt` | The post-call reflection, flower, bloom |
| `components/harbor/conversation.tsx` | `ui/PersonScreen.kt` | One-sided, per ADR-007 |
| `components/harbor/sky-wheel.tsx` | `ui/SkyWheel.kt` | Drawn on canvas |
| the garden | `ui/GardenScreen.kt`, `ui/FlowerMark.kt`, `domain/Garden.kt`, `domain/Flowers.kt` | Geometry checked against your own JavaScript |
| `.cue-*` rules | `cue/CueSurface.kt` | The full-screen cue |
| `.app-header`, `.bottom-nav` | `ui/HarborShell.kt` | Header deliberately removed |
| `.weather-*` | `ui/WeatherBar.kt` | Now also holds the daily question |
| the schedule | `ui/ScheduleScreen.kt` | **Rebuilt, not ported** — see below |

Screens with no prototype counterpart: `ui/CuesSetupScreen.kt` (the permission
explainer), `ui/ContactScreen.kt` (who to call, ringtone, photo),
`ui/NotesScreen.kt` (reshaped from the two-sided strip).

---

## The design system

### Colour — `ui/theme/Color.kt`

Every value came from `globals.css`. Change these and the whole app moves.

| Constant | Hex | Role |
| --- | --- | --- |
| `Paper` | `#FBF1DE` | The ground everything sits on |
| `Cream` | `#FFFCF3` | Cards and raised surfaces |
| `DeepGreen` | `#2F4A37` | Ink |
| `Forest` | `#33553D` | Buttons, anything asking to be pressed |
| `PaleGreen` | `#DFEAD6` | Chips, quiet fills |
| `Sand` | `#F2E7CD` | A surface that should recede |
| `Sage` | `#78816D` | Captions, labels, outlines |
| `Gold` | `#F0BD3E` | The accent, deliberately rare |
| `Bark` | `#78513D` | Errors — brown, not red, on purpose |
| `SurfaceGreen` / `SurfaceOrange` / `SurfaceSky` | `#C9DFC0` / `#F0A35F` / `#C9D8E9` | Garden plot tones |

A reskin is mostly this table plus `Theme.kt`.

### The component vocabulary — `ui/theme/Harbor.kt`

These are the CSS classes, as composables. Use them rather than styling
anything by hand; that is what keeps the app coherent.

| Composable | Was |
| --- | --- |
| `Flow { }` | `.flow` — the vertical rhythm every page is built on |
| `Modifier.pageContent()` | `.page-content` — 24px/28px |
| `Surface { }` | a card, in `Cream` |
| `SoftSurface { }` | `.soft-surface` — pale green |
| `GoldSurface { }` | `.gold-surface` — used sparingly |
| `PageIntro(title, subtitle, eyebrow)` | `.page-intro` |
| `SectionHeading(text)` | `.section-heading h2` |
| `Eyebrow(text)` | `.eyebrow` — small, spaced, upper, muted |
| `SmallCopy(text, size)` | `.small-copy` — the muted explaining voice |
| `Notice(text)` | `.notice` — a quiet reassurance with a mark |
| `Avatar(label, tone, size)` | `.avatar`, `avatar-xs`…`xl` |
| `PrimaryAction(text) { }` | `.btn-primary` — the one thing a screen asks for |
| `QuietAction(text) { }` | `.btn-quiet` — must not compete with the primary |
| `Pill(text, selected) { }` | `.cue-topic` — a chip that fills when chosen |
| `TextLink(text) { }` | `.text-link` — a quiet way onward |

`PrimaryAction` and `QuietAction` are written by hand rather than using
Material's `Button` on purpose: a Material button brings its own shape,
elevation and ripple, and those are the three things this design most wants to
not have.

---

## The font gap — worth your attention

`Type.kt` currently uses `FontFamily.Serif` and `FontFamily.SansSerif`, which
are **the device's system fonts, not Lora and DM Sans**. No font files ship in
this repo (`app/src/main/res/font/` does not exist).

The proportions, weights and sizes are all yours. The typefaces are not. On a
Pixel that means Noto Serif and Roboto, and on a Samsung or a Xiaomi it means
something else again — so Harbor currently looks slightly different on every
phone, which is exactly what the fixed palette was meant to prevent.

Fixing it is small and would probably be the single highest-impact visual
change available: drop the `.ttf` files into `app/src/main/res/font/`, declare
a `FontFamily`, and point `Serif`/`Sans` in `Type.kt` at them. Check the
licences allow redistribution in an app binary.

---

## Things that are drawn, not styled

These have no CSS equivalent and no image files. They are vector paths in
Kotlin, drawn on a canvas, and to change them you change the geometry.

**Except the flowers, since 17 Sep.** They are artwork now — twenty files in
`res/drawable-nodpi`, cut by `tools/cut_flowers.py` from the sources in
`tools/flower-source`. See ADR-012. Everything else in this table still holds.

| What | Where |
| --- | --- |
| ~~Flowers~~ — artwork now, see ADR-012 | `res/drawable-nodpi`, `ui/FlowerMark.kt` |
| The garden: plots, layout, camera | `ui/GardenScreen.kt`, `domain/Garden.kt` |
| Sky, sun, cloud, weather dimming | `ui/SkyWheel.kt` |
| Nav icons (home, clock, person) | `ui/HarborShell.kt` |
| Cue path icons (phone, heart, clock) | `cue/CueSurface.kt` |
| Quick-share icons (bubble, picture) | `ui/HomeScreen.kt` |
| The week grid | `ui/ScheduleScreen.kt` |

There are no icon libraries and no shipped SVGs. If you want a different icon
set, that is a real decision to make together — right now every mark is a few
lines of path data, which is why they restyle with the palette for free.

---

## Deliberate divergences — please do not "fix" these

Each of these looks like a mistake against the prototype and is not. The
reasons are in `docs/01-decisions.md`.

| Difference | Why |
| --- | --- |
| **Nobody replies. Ever.** Conversation is one-sided, the daily question shows no family answers, the notes strip has no incoming half | ADR-007. The parent installs nothing. Inventing their replies is the one thing the prototype itself never does |
| **No in-app calling.** The call hands off to the phone's dialer | ADR-002. Keeps Harbor out of `CALL_PHONE` and `READ_PHONE_STATE` entirely |
| **The cue is call-shaped but never claims to be a call.** It shows her photo and plays her ringtone, but says "Looks like you're free", not "Incoming call" | ADR-009. The Pavlovian pull is the mechanism; lying about it is not |
| **No map, no route, no location** | ADR-010 |
| **No wordmark header** | It cost 70dp on every screen telling someone which app they had just opened |
| **The feedback pulse is still here** although v2 dropped it | Kept deliberately — it is how the study learns whether cues land at good moments |

If one of these is wrong as *design*, that is a conversation worth having — but
it is a product decision, not a styling one, and several of them are what make
the privacy copy on screen true.

---

## What changed after the first review

A design review produced seven changes, most of which moved things around on
home. If you are diffing against your prototype, these are deliberate:

- **The daily question is gone as a separate card.** It lives inside the
  weather card now — the page was asking how life was, then a thumb-scroll
  later how today felt. Same question twice. Setting the sky and naming the day
  are one thought, so they are one card. The rotating question survived as the
  prompt inside it.
- **Calling is now the loudest thing on a person's tile**, as a filled button.
  It used to be a text link one screen in, which made the commonest reason to
  open Harbor the least visible thing on the page.
- **Leaving a line is two icon actions**, not a text link in a stack of text
  links.
- **"Find a quiet moment" and "Your pace" moved off home into Account.** Home
  is the garden, your people, and a quick way to say something.
- **Lines keep their words now.** The history used to be a column of identical
  "You left a line." rows. This reversed an earlier privacy position and the
  on-screen copy changed with it.
- **The schedule was rebuilt as a week grid** you press and drag on, like a
  calendar or a piano roll. It was day pills and two hour steppers; an ordinary
  week of five classes ran to about sixty taps, and you never saw the week you
  were describing.

---

## The trap that will bite you

Harbor sets its own Material colour scheme in `Theme.kt`. Material fills **any
slot you leave unset** from its baseline palette, which is purple.

This already happened once. `surfaceContainer` and `surfaceContainerHigh` were
set, but `surfaceContainerHighest` was not — and that is the slot a Material
`Card` resolves to. Every card in the app rendered `#E6E0E9`, Material's
baseline lavender. On the cues screen it was **50.7% of the pixels**: the most
common colour on screen, ahead of Harbor Paper, in an app whose palette
contains no purple at all. It survived code review because nothing in the
palette was wrong; only what was missing from it.

**If you add a Material component and it comes out lavender-grey, you have
found an unset slot, not a bug in the component.** Set it in `Theme.kt`. The
whole container ladder is explicit there now, with a comment saying why.

---

## Making a change and seeing it

**Gradle does not run in every environment here** (it needs a loopback
connection). CI is the reliable build, and for Compose it is the *only*
type-check — Compose code cannot be checked locally in this setup, so a push is
how you find out whether it compiles.

1. Edit, commit, push.
2. GitHub Actions builds and attaches `harbor-debug-apk` to the run.
3. Download it and `adb install -r app-debug.apk`.

`install -r` works and keeps your data, because every build now shares one
committed debug keystore (`app/debug.keystore`). Before that, every CI run
generated its own key, so updating meant uninstalling, which wiped the garden.
Do not change that signing config without reading the comment on it.

Android Studio builds work normally if you have it set up.

---

## Copy that is load-bearing

Some strings are promises the code actually keeps, and a few are the reason a
permission dialog is acceptable. Changing them is not a copy edit.

- The privacy lines on the cue and the cues-setup screen describe exactly what
  is read and where it stays. `CuesSetupScreen.kt` says it outright: if any of
  it stops being true, that copy is the first thing that has to change.
- The cue must never tell the user something about themselves that did not
  happen. Its opening line is chosen from the trigger source for that reason —
  it used to say "You just stopped walking" even when you had asked for the cue
  yourself from a settings screen.
- "Dismissing costs nothing — there is no streak to break" is true, and the cue
  policy is what makes it true.

Restyle these freely. Rewrite what they claim only with the whole team.

---

## Where to look next

| Document | For |
| --- | --- |
| `docs/00-product.md` | What Harbor is, and the build order |
| `docs/01-decisions.md` | Every architectural decision, with reasoning |
| `docs/02-ui-reconciliation.md` | The prototype pin, and the v1→v2 delta |
| `docs/03-week-one-study.md` | What the study measures |
| `docs/04-how-the-repos-fit.md` | Why two repos, and how changes flow |
| `CLAUDE.md` | Build environment, deploy traps, on-device gotchas |

If you change the prototype in a way Harbor should follow, say which commit —
the pin is how the two stay honest with each other.
