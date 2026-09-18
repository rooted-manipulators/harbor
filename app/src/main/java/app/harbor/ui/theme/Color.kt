package app.harbor.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Harbor's palette, taken from "Harbor Reskin" — the dark glass language.
 *
 * The design is a lit dusk: a gradient behind every screen, cards that are
 * nothing but white held at seven percent *over* it, and a single amber that
 * appears on the current tab, the primary action and the selected chip. Nowhere else. Everything the
 * app *says* is white or a muted warm grey; everything it *asks for* is amber.
 *
 * ## What the dark pass changed, and what it kept
 *
 * The names in this file are the same names the light specimen used, because
 * every screen already refers to them and the reskin is meant to move the
 * colour without moving the code. Two of them now mean something subtler:
 *
 *  - **[Ink] is still dark.** It was the page's text colour *and* the fill of
 *    every action. On a dark ground those split: text is [Chalk], and [Ink]
 *    survives as what sits *on top of* a light thing — the initials on an
 *    avatar, the label on an amber button. Reading [Ink] as "the text colour"
 *    is the one mistake that paints a screen black on black.
 *  - **A card is no longer a colour.** It is an alpha, and what it looks like
 *    depends entirely on what it is lying on. See the next section, which is
 *    the part of this file worth reading.
 *
 * ## The cards are translucent, and that is the whole design
 *
 * The light specimen composited its card fill to an opaque colour, because on
 * a flat bone page a translucent white and a composited one are the same
 * pixels and the opaque one is cheaper and safer.
 *
 * That reasoning does not carry over, and carrying it over is what made the
 * first dark pass look like a different app to the drawing. This design puts a
 * lit gradient behind every screen and then lays `rgba(255,255,255,0.07)` over
 * it. The card is a *window onto the gradient*: warm where it crosses the
 * ember, cold where it crosses the blue, near-black at the bottom of the page.
 * Composite that to one flat grey and every card becomes a box, the gradient
 * only survives in the gaps between them, and the screen reads as "dark mode"
 * rather than as glass on a sunset.
 *
 * So these are alpha, not colour, and they are meant to be laid over something
 * worth seeing. The reference the design was drawn from is a sunset with the
 * card sitting straight across the middle of it.
 *
 * The garden field does not read from here. Its terrain lives in
 * [app.harbor.domain.Field] and its weather in `FieldSky`, both hardcoded.
 */

// --- the ground and the glass -------------------------------------------

/** The ground everything sits on. The design's `Ground` token. */
val Paper = Color(0xFF0D0E11)

/**
 * A card: white at seven percent, laid over whatever is behind it.
 *
 * Not composited. See the note above — over the dusk this is the design's
 * glass, and flattened it is a grey box.
 */
val Cream = Color(0x12FFFFFF)

/** A surface that should recede rather than advance: white at five percent. */
val Sand = Color(0x0DFFFFFF)

/**
 * The floating navigation pill.
 *
 * Denser than a card and darker than the ground it crosses, because it has to
 * stay readable while the field scrolls underneath it. The design writes it as
 * `rgba(18,18,22,0.72)`, which is the one surface in the whole language that
 * is tinted *down* rather than up.
 */
val NavGlass = Color(0xB8121216)

/**
 * Ink — what sits on top of a *light* thing.
 *
 * The label on an amber button, the initials on an avatar. Never the page's
 * text colour; that is [Chalk]. The design writes this as `#1a1206`, a brown
 * black rather than a neutral one, so it belongs to the amber it sits on.
 */
val Ink = Color(0xFF1A1206)

/** What the app says. The design's `Ink` token, which on a dark ground is white. */
val Chalk = Color(0xFFFFFFFF)

/** Captions and labels. The design's `Muted ink`. */
val Muted = Color(0xFF9C978F)

/**
 * The rim.
 *
 * White at nine percent, and deliberately translucent: a card's edge has to
 * catch the dusk gradient behind it on Home and the flat ground everywhere
 * else, and a composited rim can only do one of those. This is the whole of
 * the design's elevation — there is no shadow anywhere in it.
 */
val CardEdge = Color(0x17FFFFFF)


/** The drawn line: outline chips, dividers, anything that must read as a rule. */
val Hairline = Color(0x2EFFFFFF)

/**
 * The stripe down every other day column on the week grid.
 *
 * White at eight percent, translucent like every other piece of glass in this
 * design — it used to be an opaque near-black that read as almost nothing on
 * the specimen's own near-black ground, which usability testing flagged: the
 * seven columns were not actually countable without reading the labels. This
 * is the same alpha tier as [Cream], and it is meant to be seen.
 */
val BandWarm = Color(0x14FFFFFF)

// --- the one accent -----------------------------------------------------
//
// The design is strict about this: amber is the current tab, the primary
// action and the selected chip, and it appears nowhere else. Everything that
// wants to be noticed and is not one of those three gets brightness instead.

/** Amber. The design's `Accent`, and the brighter of the two. */
val Gold = Color(0xFFF0BD3E)

/** The primary action's fill. The design's `Primary action`. */
val Ember = Color(0xFFE08A3C)

/** The top of the primary action's gradient, which runs [EmberLight] to [Ember]. */
val EmberLight = Color(0xFFF5B85C)
/**
 * A hole you type into: white at eight per cent, composited onto [Paper].
 *
 * Composited rather than left as alpha, unlike a card, and for a reason the
 * note above does not cover: a field is a hole, not a window. It wants to read
 * as the same depth wherever it sits on the page, including on top of another
 * translucent thing, and an alpha that stacked with whatever was under it
 * would make the same field a different colour on two screens.
 *
 * Derived rather than written out. The onboarding flow and the week editor had
 * arrived at `0xFF202124` independently, under two different names, and a
 * literal that two files have separately worked out by hand is a literal that
 * will drift the first time [Paper] moves.
 */
val Glass = Color.White.copy(alpha = 0.08f).compositeOver(Paper)

/**
 * Frosted glass: white at fifteen per cent, composited onto [Paper].
 *
 * The one surface in this language that is deliberately *not* a window. A card
 * is [Cream] — seven percent, left as alpha, so whatever is behind it shows
 * through and the dusk carries on across it. That is right for a card holding
 * two lines of text and wrong for the week grid, which is a drawing in its own
 * right: seven narrow columns, a dashed rule every three hours and up to a
 * dozen small painted objects, all of it competing with whatever the page
 * happens to be lying on. Read through, it turned into noise.
 *
 * So this is opaque, and it is meant to be. Compose has no backdrop blur to
 * reach for — [androidx.compose.ui.draw.blur] blurs a composable's own content,
 * not what is behind it — so "frosted" here is what frosting actually does:
 * enough white to stop the ground being legible through the glass, with the
 * rim ([CardEdge]) still catching the light so the panel reads as a pane laid
 * on the page rather than a hole cut in it.
 */
val Frosted = Color.White.copy(alpha = 0.15f).compositeOver(Paper)

/**
 * A whole card marked live: [Gold] at eight per cent, composited onto [Paper].
 *
 * A card filled solid amber would shout down the question above it, so the
 * accent arrives as a tint and a rim instead. Same reasoning as [Glass] for
 * compositing, and same reason for deriving it.
 */
val ChosenFill = Gold.copy(alpha = 0.08f).compositeOver(Paper)

/**
 * The rim on a chosen card: [Gold] at twenty per cent.
 *
 * Alpha, not composited, because a rim is drawn over whatever it crosses and
 * is meant to pick that up.
 */
val ChosenEdge = Gold.copy(alpha = 0.20f)

/** The cool end of the dusk, behind the cards on Account and Schedule. */
val Dusk = Color(0xFF2F4A63)

/** Brown rather than red: this app has nothing angry to say. */
val Bark = Color(0xFF78513D)

/** The green a switch takes when it is on — see the note in `SettingsScreen`. */
val Leaf = Color(0xFF8FB25C)

// --- the greens of the garden -------------------------------------------
//
// Only ever illustration: a stem, a leaf, a bloom. Lifted from the light
// specimen's values, which were chosen to sit *under* a near-white page and
// vanish almost completely against a near-black one.

/** The deep green of a stem. */
val Stem = Color(0xFF5C7F4E)

/** A leaf in shadow. */
val Forest = Color(0xFF6F9A56)

/** A leaf in light. */
val LeafLight = Color(0xFF8FB25C)

// --- contact tones ------------------------------------------------------
//
// The design's four tones, and they are pale on purpose: a person's avatar is
// one of the few genuinely light objects in the app, so it reads as a lit
// thing on a dark page. All four carry [Ink] rather than [Chalk].

val SurfaceGreen = Color(0xFFCFE0C6)
val SurfaceGold = Color(0xFFF5C77A)
val SurfaceOrange = Color(0xFFE9C7A1)
val SurfaceSky = Color(0xFFC9D8E9)

// --- the marks ----------------------------------------------------------
//
// The same four tones for a mark too small to carry a tint. On a light page
// these had to be darkened to stay visible; here they need the opposite, so
// the mark and the surface are much closer than they used to be.

val MarkGreen = Color(0xFFCFE0C6)
val MarkGold = Gold
val MarkOrange = Color(0xFFF0A35F)
val MarkSky = Color(0xFFC9D8E9)
