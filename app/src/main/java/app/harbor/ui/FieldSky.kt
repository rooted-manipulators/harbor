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
import androidx.compose.ui.unit.dp
import app.harbor.domain.Weather
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
 * argument that a heavy day should feel heavy. The skies are now the web
 * prototype's, ported in [meadowFor], and its storm is a pale flat overcast
 * instead — heavy, but light. So the reflection is in the *quality* of the
 * light rather than the amount of it, and the dimming wash is gone.
 *
 * The old argument is written down rather than deleted because it was a real
 * one, and because whoever next wonders why a storm does not darken the app
 * deserves to find the answer here.
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

    Canvas(modifier.fillMaxSize()) {
        val sky = fieldTintOf(weather)

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
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.20f to Color.Transparent,
                    0.32f to sky.grass.copy(alpha = 0.50f),
                    0.44f to sky.grass.copy(alpha = 0.42f),
                    0.58f to Color.Transparent,
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

        if (sky.sun > 0f) drawFieldSun(sky.sun)

        // Three clouds at different widths and speeds, so the loop never
        // reads as a loop.
        if (sky.cloud > 0f) {
            drawFieldCloud(88.dp.toPx(), 16.dp.toPx(), loopPhase(slow, 60_000f, 36_000f, 0f), sky)
            if (weather != Weather.BRIGHT) {
                drawFieldCloud(66.dp.toPx(), 44.dp.toPx(), loopPhase(slow, 60_000f, 48_000f, 0.19f), sky)
                drawFieldCloud(112.dp.toPx(), 28.dp.toPx(), loopPhase(slow, 60_000f, 60_000f, 0.40f), sky)
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
            val native = canvas.nativeCanvas
            val layer = native.saveLayer(0f, 0f, size.width, size.height, null)
            native.drawRect(0f, 0f, size.width, size.height, grain.tint)
            // Rubbed out from the bottom rather than stopped at a line. Cut
            // flat, the grain ends in an edge straight across the picture --
            // which is the one thing every part of this file exists to avoid.
            native.drawRect(0f, 0f, size.width, size.height, grain.fade)
            native.restoreToCount(layer)
        }
    }
}

/** Where a looping thing is, given a shared clock and its own period. */
private fun loopPhase(t: Float, clockMs: Float, periodMs: Float, offset: Float): Float {
    val turns = t * clockMs / periodMs + offset
    return turns - turns.toInt()
}

private fun DrawScope.drawFieldSun(alpha: Float) {
    val r = 75.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD678).copy(alpha = 0.95f * alpha),
                Color(0xFFFFD678).copy(alpha = 0f),
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
    val cloud: Float,
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

    val tint = android.graphics.Paint().apply {
        isAntiAlias = false
        alpha = (255 * 0.55f).toInt()
        shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    val fade = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }

    private var height = -1f

    fun sized(h: Float) {
        if (h == height || h <= 0f) return
        height = h
        fade.shader = android.graphics.LinearGradient(
            0f, h * 0.58f, 0f, h * 0.74f,
            0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP,
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
 * The sky, taken from the prototype's palette rather than written twice.
 *
 * These used to be five hand-mixed dark skies. They are now read from
 * [meadowFor], which is the web prototype's own table ported across, so the
 * app and the drawing it was drawn from cannot drift apart -- and so a change
 * to the weather's look is a change in one file rather than two.
 *
 * What is still Harbor's rather than the prototype's is the bottom of the
 * gradient: it hands over to [Paper] before the cards start, because the
 * meadow is a lit band at the top of a dark app and not the whole page. That
 * is what keeps the flowers fading into the UI below them.
 *
 * **Storm is now the prototype's, on request, and that is a reversal worth
 * naming.** This file used to argue that a storm should make the whole app
 * darker -- that taking somebody's word for how life is and reflecting it back
 * is the point of asking. The prototype's storm is a pale, flat overcast
 * instead: heavy, but light. It is the sky that was asked for, the dimming
 * wash is gone with it, and the old argument is recorded here rather than
 * quietly deleted, because it was a real one.
 */
private fun fieldTintOf(weather: Weather): SkyTint {
    val meadow = meadowFor(weather)
    return SkyTint(
        // Read top to bottom as the wash runs: the haze the prototype fades
        // distance into is the palest thing in the table, so it goes overhead,
        // and the sky's own three follow it down in reverse. The land stop is
        // the furthest hill, which is the colour distance already turns green
        // into, so the sky does not meet the field without warning.
        high = meadow.haze,
        pale = meadow.sky.third,
        // The two sky stops under the pale top carry the colour, so they are
        // the ones deepened. The prototype's palette is a daylight sky seen
        // through air -- correct for a landscape, and far too milky for a wash
        // that has to hold its own against a near-black page. Deepening only
        // these keeps the hues the prototype chose and gives them somewhere to
        // travel between the pale top and the dark ground.
        mid = deepen(meadow.sky.second, 1.45f, 0.07f),
        deep = deepen(meadow.sky.first, 1.45f, 0.18f),
        land = deepen(meadow.hills.last(), 1.30f, 0.25f),
        grass = deepen(meadow.field.first(), 1.30f, 0.12f),
        // Just under half way to the page. Far enough that it reads as dark
        // green rather than as the meadow repeated, close enough that it is
        // still recognisably the ground and not a grey.
        ground = lerp(deepen(meadow.fieldDeep.last(), 1.16f, 0.18f), Paper, 0.45f),
        // A sun is only a sun on the two days that have one. On the others the
        // prototype still names a disc, but it is the overcast's bright patch
        // and it belongs at a fraction of the strength.
        sun = when (weather) {
            Weather.CLEAR -> 0.55f
            Weather.BRIGHT -> 1f
            else -> 0.12f
        },
        // Straight from the prototype's own count, scaled to the three this
        // canvas draws.
        cloud = (meadow.cloudCount / 8f).coerceIn(0f, 1f),
        // Clouds were white against a blue overhead. Overhead is now the
        // palest tone in the palette, and white on near-white is nothing at
        // all, so they take a third of the sky's own middle and read as shape
        // rather than as brightness. They are still the lightest thing in the
        // band they sit in.
        cloudColour = lerp(meadow.cloud, meadow.sky.second, 0.34f),
        rain = when (weather) {
            Weather.RAIN -> 0.7f
            Weather.STORM -> 1f
            else -> 0f
        },
        // Nothing dims any more. See the note above: the heavy weathers are
        // pale now, and a wash over a pale sky only makes it muddy.
        dim = 0f,
    )
}
