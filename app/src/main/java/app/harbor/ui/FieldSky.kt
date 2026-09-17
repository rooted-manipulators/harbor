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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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

    Canvas(modifier.fillMaxSize()) {
        val sky = fieldTintOf(weather)
        drawRect(
            // Three stops, ending on the ground colour. The weather owns the
            // top of the screen and hands over to the page before the cards
            // start, which is what keeps a storm from turning the whole app
            // navy and a bright day from washing the text out.
            brush = Brush.verticalGradient(
                0.00f to sky.top,
                0.26f to sky.mid,
                0.48f to sky.horizon,
                0.72f to Paper,
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
    val top: Color,
    val mid: Color,
    /**
     * Where the sky meets the land, and the stop the whole fade turns on.
     *
     * The old gradient went straight from a dark sky to the page, which two
     * dark colours can do in one step. A lit sky cannot: without this the
     * meadow's pale horizon would drop to near-black across a few pixels and
     * read as a rule drawn across the screen -- the exact seam this file was
     * written to remove. It is the prototype's third sky stop, which is the
     * colour its haze fades distance into.
     */
    val horizon: Color,
    val sun: Float,
    val cloud: Float,
    val cloudColour: Color,
    val rain: Float,
    val dim: Float,
)

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
        top = meadow.sky.first,
        mid = meadow.sky.second,
        horizon = meadow.sky.third,
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
        cloudColour = meadow.cloud,
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
