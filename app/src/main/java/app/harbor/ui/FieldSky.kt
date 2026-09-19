package app.harbor.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.harbor.domain.Weather
import app.harbor.domain.StudyArm
import java.time.LocalTime
import app.harbor.ui.theme.Paper

/**
 * The weather, as the whole screen's ground.
 *
 * This used to be the sky *inside* the field: a gradient painted in a rounded
 * box, with the page's own dark ground around it. Two grounds, one boxed
 * inside the other, and the seam between them was the most visible thing on
 * home — a rule across the screen where the picture stopped.
 *
 * So the sky is the page now. It fills the window, the field's terrain draws
 * straight onto it with nothing of its own behind, and the sun and the rain
 * belong to the screen rather than to a panel on it. The gradient ends at
 * [Paper] so the bottom of every screen is still the ground the cards sit on,
 * and what changes with the weather is the top two-thirds.
 *
 * This is the one place in Harbor where the weather the user set is more than
 * a label: it changes the light the whole app is seen in. That is the point of
 * asking — it takes their word for how life is and reflects it back, rather
 * than filing it away for a chart nobody sees.
 *
 * **What that reflection looks like changed on 18 Sep 2026.** It used to mean
 * *darker*: a storm turned the sky navy and washed the screen in blue, on the
 * argument that a heavy day should feel heavy. The skies went to the web
 * prototype's instead, whose storm is a pale flat overcast -- heavy, but
 * light. So the reflection is in the *quality* of the light rather than the
 * amount of it, and the dimming wash is gone.
 *
 * The old argument is written down rather than deleted because it was a real
 * one, and because whoever next wonders why a storm does not darken the app
 * deserves to find the answer here. It is still the rule: [Wash]'s storm is
 * the loudest of the five and also the brightest.
 *
 * **The sky's colours left [meadowFor] the same day.** They live in [Wash]
 * now, hand-mixed one row per weather, because a ported landscape palette
 * gave five exposures of one sky where five different skies were wanted. The
 * *ground* stops are still the prototype's -- [Wash] says why the two halves
 * are sourced differently.
 */
@Composable
fun FieldSky(weather: Weather, modifier: Modifier = Modifier) {
    val clock = rememberInfiniteTransition(label = "sky")
    val slow by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "drift",
    )
    val fast by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760, easing = LinearEasing)),
        label = "rain",
    )

    // One tile and one pair of paints for the life of the screen. Rebuilding
    // either per frame would allocate a bitmap sixty times a second behind a
    // gradient.
    val grain = remember { Grain(grainTile()) }

    // How far down the clouds have to start.
    //
    // The wash is deliberately full-bleed -- it runs under the status bar,
    // because a weather that stopped at a black strip would stop being the
    // ground the whole screen stands on. The clouds are shapes rather than
    // colour, though, and shapes drawn up there collide with the clock and the
    // wifi icon. So the gradient keeps the whole window and only the clouds
    // move down, which is the same split the cue's inset makes.
    val barTop = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toFloat()
    }

    // Whose sky this is. Read here because this is the composable -- the draw
    // scope below cannot ask a CompositionLocal anything.
    val says = skySays(weather)

    Canvas(modifier.fillMaxSize()) {
        val sky = fieldTintOf(says)

        // Where the light is, and how far it carries. Every layer below reads
        // these two rather than its own copy: the wash, the warm pool and the
        // green the land stands in are one light seen through three things,
        // and a centre that drifted between them would show up as the halo
        // sliding off the glow.
        //
        // Tightened on 18 Sep against a reference: the light used to reach
        // corner to corner, which lit the whole top of the screen evenly and
        // read as a coloured panel. A glow that falls to black before the
        // edges reads as distance -- the field runs out into the dark rather
        // than stopping at the frame.
        val lit = Offset(size.width * 0.60f, size.height * 0.02f)
        val reach = size.height * 0.62f
        drawRect(
            // Light at the top, falling the whole way down into the page --
            // and the bands are bowed rather than level, because nothing in
            // weather is level.
            //
            // The bow is the gradient itself, not a wash over it. One radial
            // whose centre sits just off the top edge and to the right draws
            // bands that are arcs; a vertical gradient can only stack perfect
            // horizontals, which is what makes one read as a ramp. The centre
            // is where the light is, so the palest tone pools there and every
            // band under it curves around it.
            //
            // Eight stops, unevenly spaced. An even ladder is a machine
            // counting; a band that holds and then gives way quickly is what
            // light does through air. The pale stops are close together at the
            // top and the ground stops are further apart below, so the sky
            // opens and the land settles.
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to sky.high,
                    0.07f to sky.pale,
                    0.17f to sky.mid,
                    0.27f to sky.deep,
                    0.37f to sky.land,
                    0.45f to sky.ground,
                    0.52f to Paper,
                    1.00f to Paper,
                ),
                center = lit,
                radius = reach,
            ),
            size = size,
        )

        // A hint of warmth where the light comes from. At sixteen per cent it
        // is a suggestion that the sun is up there rather than a second light
        // source -- the first pass had this at fifty-five and the whole sky
        // went to milk.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(sky.high.copy(alpha = 0.16f), Color.Transparent),
                center = lit,
                radius = reach * 0.62f,
            ),
            size = size,
        )

        // A green haze behind the land, and only behind the land.
        //
        // The field is drawn as dots with the sky showing between them, so
        // where the land sits over a blue band the land reads blue -- the eye
        // mixes the gaps into the marks, and there is more gap than mark. The
        // ground was the right colour all along; what was behind it was not.
        //
        // So: the same radial, the same centre, the same bow, carrying nothing
        // but a soft green across the stops the land occupies. Transparent
        // above, so the sky stays sky; gone before the settle, so the bottom
        // still arrives at the page as one colour. No edge anywhere -- it is a
        // haze the land stands in rather than a shape drawn under it.
        //
        // Wider and weaker than it was, for the same reason the stops are
        // capped: at half alpha across a third of the radius this was not a
        // haze, it was a band, and a band with a light green in it is the
        // other half of the ring [noBrighterThan] describes. Spread across
        // half the radius at a bit over a quarter alpha it does the same job
        // -- the gaps between the dots stop reading blue -- without ever
        // being an edge.
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.16f to Color.Transparent,
                    0.30f to sky.grass.copy(alpha = 0.30f),
                    0.46f to sky.grass.copy(alpha = 0.26f),
                    0.66f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
                center = lit,
                radius = reach,
            ),
            size = size,
        )

        // Then flat to the page, straight across.
        //
        // The bands are arcs, and an arc arriving at the cards would put more
        // colour under one corner than the other. This settles the last of it
        // level so whatever sits below starts from one honest colour.
        drawRect(
            brush = Brush.verticalGradient(
                0.46f to Color.Transparent,
                0.60f to Paper,
                1.00f to Paper,
            ),
            size = size,
        )

        if (sky.sun > 0f) drawFieldSun(sky.sun, sky.sunColour)

        // Three clouds at different widths and speeds, so the loop never
        // reads as a loop.
        if (sky.cloud > 0f) {
            drawFieldCloud(88.dp.toPx(), barTop + 16.dp.toPx(), loopPhase(slow, 60_000f, 36_000f, 0f), sky)
            if (sky.extraClouds) {
                drawFieldCloud(66.dp.toPx(), barTop + 44.dp.toPx(), loopPhase(slow, 60_000f, 48_000f, 0.19f), sky)
                drawFieldCloud(112.dp.toPx(), barTop + 28.dp.toPx(), loopPhase(slow, 60_000f, 60_000f, 0.40f), sky)
            }
        }

        if (sky.rain > 0f) drawFieldRain(fast, sky.rain)

        if (sky.dim > 0f) {
            drawRect(color = Color(0xFF1E2C3C).copy(alpha = sky.dim), size = size)
        }

        // Grain last, over everything including the sun and the clouds, so the
        // whole wash sits in one air rather than a clean sun floating on a
        // grainy sky. Strongest where the bands are widest and the steps would
        // show, gone by the time the page has taken over -- Paper is one flat
        // colour and has no banding to cure, and noise over the cards would
        // just be dirt on them.
        grain.sized(size.height)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, grain.tint)
        }
    }
}

/** Where a looping thing is, given a shared clock and its own period. */
private fun loopPhase(t: Float, clockMs: Float, periodMs: Float, offset: Float): Float {
    val turns = t * clockMs / periodMs + offset
    return turns - turns.toInt()
}

private fun DrawScope.drawFieldSun(alpha: Float, colour: Color) {
    val r = 75.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colour.copy(alpha = 0.95f * alpha),
                colour.copy(alpha = 0f),
            ),
            center = Offset(size.width / 2, -44.dp.toPx() + r),
            radius = r,
        ),
        radius = r,
        center = Offset(size.width / 2, -44.dp.toPx() + r),
    )
}

private fun DrawScope.drawFieldCloud(width: Float, top: Float, phase: Float, sky: SkyTint) {
    val travel = size.width + 260.dp.toPx()
    val x = -130.dp.toPx() + travel * phase
    val h = 22.dp.toPx()
    val colour = sky.cloudColour.copy(alpha = sky.cloud)

    drawRoundRect(
        color = colour,
        topLeft = Offset(x, top),
        size = Size(width, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2),
    )
    drawCircle(colour, radius = 14.dp.toPx(), center = Offset(x + width * 0.16f + 14.dp.toPx(), top + h - 12.dp.toPx()))
    drawCircle(colour, radius = 10.dp.toPx(), center = Offset(x + width * 0.78f, top + h - 8.dp.toPx()))
}

private fun DrawScope.drawFieldRain(phase: Float, alpha: Float) {
    val drop = 16.dp.toPx()
    val fall = size.height + drop * 2
    for (i in 0 until 26) {
        // Deterministic scatter: same drops every frame, no per-frame random.
        val lane = ((i * 3.9f + (i % 5) * 1.7f) % 100f) / 100f
        val stagger = ((i % 9) * 0.13f + (i % 4) * 0.07f)
        val p = (phase + stagger).let { it - it.toInt() }
        val y = -drop + fall * p
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF7896AF).copy(alpha = 0f),
                    Color(0xFF6888A4).copy(alpha = 0.8f * alpha),
                ),
                startY = y,
                endY = y + drop,
            ),
            start = Offset(size.width * lane, y),
            end = Offset(size.width * lane, y + drop),
            strokeWidth = 1.6.dp.toPx(),
        )
    }
}

/**
 * What the sky over the field is painted from.
 *
 * ## Why this is a choice and not a value
 *
 * The two study arms ask the same question -- how full is the day -- and put
 * the answer in different places. In the garden arm the answer *is* the
 * screen: drag the slider and the whole sky changes, which is the thing
 * Harbor has always done and the thing the bees arm is being compared
 * against. In the bees arm the answer went onto the bee, and a sky that moved
 * as well would mean both arms had the metaphor, so the sky there tells the
 * time instead. [SkyHour] is still exactly right about why a clock and not a
 * forecast; it was only ever wrong about who it applied to.
 *
 * That is the whole A/B: one variable, in one of two places. Putting the
 * clock in both arms quietly took the variable out of the control -- arm A's
 * slider moved a thumb and nothing else, while arm B's changed a face -- and
 * any difference in how much people touched it would have measured that
 * rather than the metaphor. This type is what stops it happening twice:
 * there is no longer a way to paint this sky without saying which arm it is
 * for.
 */
private sealed interface SkySays {
    /** The garden arm: the slider's own answer, as it always was. */
    data class Mood(val weather: Weather) : SkySays

    /** The bees arm: the hour, because the answer is on the bee. */
    data class Hour(val at: SkyHour) : SkySays
}

/** Which of the two this build is, asked of the arm rather than assumed. */
@Composable
private fun skySays(weather: Weather): SkySays = when (LocalStudyArm.current) {
    StudyArm.GARDEN -> SkySays.Mood(weather)
    StudyArm.BEES -> SkySays.Hour(SkyHour.of(LocalTime.now()))
}

private class SkyTint(
    /**
     * The lightest tone there is, at the very top.
     *
     * The sky used to run the other way — its own darkest blue overhead and
     * its palest at the horizon, which is what a sky actually does. This is
     * the asked-for arrangement instead: lightest at the top of the frame,
     * darkening the whole way down until it is the page. It reads as a wash
     * rather than as a view, and the weather is told by which colours the
     * wash is made of rather than by where the light sits in it.
     */
    val high: Color,
    val pale: Color,
    val mid: Color,
    /** The last of the sky before the land takes over. */
    val deep: Color,
    /** Land, a long way off. The first stop that is ground rather than air. */
    val land: Color,
    /**
     * The green the land stands in, laid behind the field and nothing else.
     *
     * Lit ground rather than the deep green under it: this sits *behind* the
     * dots and is seen between them, so it has to be the colour the gaps
     * should be. Made from the same palette entry the field's own marks come
     * from, so the haze and the thing standing in it cannot drift apart.
     */
    val grass: Color,
    /**
     * The dark green the sky lands on before the page takes over.
     *
     * The fourth stop, and the one that makes the bottom of the gradient a
     * fade rather than a handover. Sky to [Paper] in one step is two very
     * different colours meeting: a lit horizon and a near-black page have
     * nothing in common to pass through, and the eye finds the join whatever
     * the distance between the stops. Going through the ground's own colour
     * gives it something to travel along -- the sky settles into the land, and
     * the land settles into the app.
     *
     * Taken from the weather's own [Meadow.fieldDeep] rather than invented, so
     * a storm's is duller than a bright day's for the same reason its sky is,
     * and then carried part of the way to [Paper]. The palette's darkest green
     * is still a mid green; left as it is, it merely moves the seam down the
     * screen to where green meets black.
     */
    val ground: Color,
    val sun: Float,
    /** The disc's own colour, which is the weather's rather than one yellow. */
    val sunColour: Color,
    val cloud: Float,
    /**
     * Whether to draw the other two.
     *
     * One cloud on the clearest sky and three on every other -- which both
     * halves of [SkySays] want and neither can say as a number. [cloud] is
     * how strongly a cloud is drawn, and a bright noon wants its one cloud at
     * full strength rather than three faint ones.
     */
    val extraClouds: Boolean,
    val cloudColour: Color,
    val rain: Float,
    val dim: Float,
)

/**
 * The same hue, carrying more of itself and standing a little further back.
 *
 * In HSL rather than HSV on purpose: saturating in HSV drags light colours
 * toward white as much as toward their hue, and every colour in this palette
 * is a light colour.
 */
private fun deepen(colour: Color, saturation: Float, darken: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(colour.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturation).coerceIn(0f, 1f)
    hsl[2] = (hsl[2] * (1f - darken)).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * The same colour, but never lighter than the one above it in the ladder.
 *
 * ## The ring this exists to stop
 *
 * The wash runs light at the top and dark at the bottom, and it is easy to
 * assume that writing the stops in that order is enough. It is not. The sky
 * stops are hand-mixed and the ground stops are derived from a different
 * palette, and four of the five weathers came out with a *land* stop lighter
 * than the sky stop above it -- rain by a third of the whole range. The light
 * fell off and then went back up.
 *
 * A ladder that brightens again draws a ring, and the eye is very good at
 * finding one: it reads as an outline around the glow, a warm yellow-green
 * annulus sitting where the land should just be quietly arriving. Which is
 * exactly what it was called when it was seen on a phone.
 *
 * Real light does not do that. A flare has a bright core and a long smooth
 * tail and nothing anywhere along it gets brighter than what it came from.
 *
 * ## Why a rule and not five fixed colours
 *
 * Because the two halves of the ladder will go on being edited separately --
 * that split is the whole design, see [Wash] -- and every future edit to
 * either half can reintroduce this. Capping lightness costs nothing, cannot
 * be forgotten, and leaves hue and saturation alone, so the land is still the
 * land's own green; it just is not allowed to be a light one.
 */
private fun noBrighterThan(ceiling: Color, colour: Color): Color {
    val above = FloatArray(3)
    val here = FloatArray(3)
    ColorUtils.colorToHSL(ceiling.toArgb(), above)
    ColorUtils.colorToHSL(colour.toArgb(), here)
    if (here[2] <= above[2]) return colour
    here[2] = above[2]
    return Color(ColorUtils.HSLToColor(here))
}

/**
 * A tile of fixed noise, laid over the whole wash.
 *
 * Every reference for this gradient has grain in it, and grain is not a
 * texture here so much as a cure: eight stops across two thousand pixels still
 * leaves each band a couple of hundred pixels of nearly one colour, and a
 * phone's dither will draw visible steps across it. Noise at a few per cent
 * breaks the step up and the eye reads the result as continuous.
 *
 * Built once and tiled. A hundred and twenty-eight squared is small enough to
 * be nothing in memory and large enough that the repeat does not read as a
 * pattern, and the seed is fixed so the grain is the same grain every launch
 * rather than crawling between frames.
 */
/**
 * The grain, its paint, and the mask that rubs it out toward the page.
 *
 * Held together because the mask depends on the height and the height is only
 * known while drawing. Rebuilt when that changes and not otherwise, so a
 * rotation costs one shader and a frame costs none.
 */
private class Grain(tile: Bitmap) {

    private val noise = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    val tint = android.graphics.Paint().apply {
        isAntiAlias = false
        alpha = (255 * 0.55f).toInt()
    }

    private var height = -1f

    /**
     * Noise and its fade composed into one shader, rather than two passes
     * through an offscreen layer.
     *
     * This was a `saveLayer` and a `DST_OUT` rect: correct, and it allocated a
     * full-screen offscreen buffer on every single frame the field drew, then
     * composited it back. That is one of the most expensive things a draw can
     * do, and it was happening behind every pan, every stir, and every drag of
     * the weather slider.
     *
     * [ComposeShader] does the same arithmetic once, at the size change. The
     * fade runs opaque to transparent, and `DST_IN` keeps the noise in
     * proportion to it, so the grain still stops before the page without any
     * layer at all -- and the whole wash is one ordinary rect again.
     */
    fun sized(h: Float) {
        if (h == height || h <= 0f) return
        height = h
        val fade = android.graphics.LinearGradient(
            0f, h * 0.58f, 0f, h * 0.74f,
            0xFFFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP,
        )
        tint.shader = android.graphics.ComposeShader(
            noise,
            fade,
            android.graphics.PorterDuff.Mode.DST_IN,
        )
    }
}

private fun grainTile(size: Int = 128): Bitmap {
    val random = java.util.Random(20260918)
    val pixels = IntArray(size * size)
    for (i in pixels.indices) {
        // Grey, and only in the alpha: tinted noise would shift the colour of
        // whatever band it fell on, and these bands are the whole point.
        val a = (random.nextInt(46)) shl 24
        pixels[i] = a or 0x00FFFFFF
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/**
 * One weather, assembled: a sky from [Wash], a ground from [meadowFor].
 *
 * The seam between the two is the whole job of this function, and it is why
 * the two halves are allowed to come from different places. Above the horizon
 * the wash can be any colour the weather wants, because nothing is drawn on
 * it. Below it, every stop is seen *between* the field's own dots and has to
 * be the colour those gaps should be, so it comes from the same table the
 * dots do.
 *
 * What is Harbor's rather than either source is the bottom of the gradient: it
 * hands over to [Paper] before the cards start, because the meadow is a lit
 * band at the top of a dark app and not the whole page. That is what keeps the
 * flowers fading into the UI below them.
 */
private fun fieldTintOf(says: SkySays): SkyTint {
    val meadow = meadowFor(
        when (says) {
            // The land moves with the sky, which is what it did for the whole
            // of this app's life before the clock briefly owned both.
            is SkySays.Mood -> says.weather
            // An hour is not a weather and the meadow palette has no row for
            // one. Clear is the daylight version of those greens and is what
            // the terrain was tuned against, so the dots still stand on
            // ground that agrees with them.
            is SkySays.Hour -> Weather.CLEAR
        },
    )
    val wash = when (says) {
        is SkySays.Mood -> washFor(says.weather)
        is SkySays.Hour -> washForHour(says.at)
    }
    // The ground stops, each capped against the one above it so the ladder
    // can only darken. Hoisted out of the constructor because each cap needs
    // the colour before it -- see [noBrighterThan] for the ring this stops.
    val land = noBrighterThan(wash.deep, deepen(meadow.hills.last(), 1.30f, 0.25f))
    val grass = noBrighterThan(land, deepen(meadow.field.first(), 1.30f, 0.12f))
    val ground = noBrighterThan(
        grass,
        // Just under half way to the page. Far enough that it reads as dark
        // green rather than as the meadow repeated, close enough that it is
        // still recognisably the ground and not a grey.
        lerp(deepen(meadow.fieldDeep.last(), 1.16f, 0.18f), Paper, 0.45f),
    )
    return SkyTint(
        // The four sky stops come from the wash table, already mixed, and
        // are used as they are.
        //
        // They used to be the prototype's own tones deepened on the way down,
        // which is why the deepening is gone from these four and still on the
        // land stops below. A colour chosen for this gradient does not want a
        // blanket 1.45x saturation on top of it -- that was a correction for
        // borrowing a landscape palette, and there is nothing left to correct.
        high = wash.high,
        pale = wash.pale,
        mid = wash.mid,
        deep = wash.deep,
        // Land is still the meadow's. See [Wash]: the sky is free to be
        // whatever the weather wants, and the ground is not, because the
        // ground has to agree with the dots drawn on it.
        land = land,
        grass = grass,
        ground = ground,
        sun = when (says) {
            // A sun is only a sun on the two days that have one. On the
            // others the prototype still names a disc, but it is the
            // overcast's bright patch rather than the sun itself.
            is SkySays.Mood -> when (says.weather) {
                Weather.CLEAR -> 0.55f
                Weather.BRIGHT -> 1f
                else -> 0.12f
            }
            // A sun by day, a softer one at dusk, none at night.
            is SkySays.Hour -> when (says.at) {
                SkyHour.DAY -> 1f
                SkyHour.DUSK -> 0.45f
                SkyHour.NIGHT -> 0f
            }
        },
        sunColour = wash.sun,
        cloud = when (says) {
            // Straight from the prototype's own count, scaled to the three
            // this canvas draws.
            is SkySays.Mood -> (meadow.cloudCount / 8f).coerceIn(0f, 1f)
            // Fewer clouds after dark, and none of them lit.
            is SkySays.Hour -> when (says.at) {
                SkyHour.DAY -> 0.62f
                SkyHour.DUSK -> 0.45f
                SkyHour.NIGHT -> 0.22f
            }
        },
        extraClouds = when (says) {
            is SkySays.Mood -> says.weather != Weather.BRIGHT
            is SkySays.Hour -> says.at != SkyHour.DAY
        },
        // Clouds were white against a blue overhead. Overhead is now the
        // palest tone in the palette, and white on near-white is nothing at
        // all, so they take a third of the sky's own middle and read as shape
        // rather than as brightness. They are still the lightest thing in the
        // band they sit in.
        //
        // The third they take is now the wash's middle rather than the
        // prototype's. A cloud tinted with a sky it is no longer floating in
        // is the one thing in the frame that would still be the old colour.
        cloudColour = lerp(meadow.cloud, wash.mid, 0.34f),
        rain = when (says) {
            is SkySays.Mood -> when (says.weather) {
                Weather.RAIN -> 0.7f
                Weather.STORM -> 1f
                else -> 0f
            }
            // No rain on a clock. An hour is not a forecast.
            is SkySays.Hour -> 0f
        },
        // Nothing dims any more. See the note above: the heavy weathers are
        // pale now, and a wash over a pale sky only makes it muddy.
        dim = 0f,
    )
}
