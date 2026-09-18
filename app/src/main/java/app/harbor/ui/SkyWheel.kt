package app.harbor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.harbor.domain.Weather
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The sky behind the garden, hand-translated from `sky-wheel.tsx`.
 *
 * Five weathers sit on a wheel whose centre is below the frame, so only the
 * top of the arc is visible. Choosing a weather turns the wheel until that
 * emblem is overhead — the sky changes rather than a setting changing.
 *
 * The emblem geometry is the prototype's 100x110 viewBox, scaled: sun and
 * rays, clouds as three circles over a rounded bar, rain as three falling
 * strokes, storm as a bolt.
 */
internal object Sky {

    /**
     * `.garden-frame[data-weather]` — the ground the whole scene sits on.
     *
     * The same five dusks `FieldSky` draws, and they have to stay the same
     * five: this paints the garden screen and that paints the field inside
     * home, so a phone showing both at once shows the same evening twice.
     */
    fun gradient(weather: Weather): Pair<Color, Color> = when (weather) {
        Weather.CLEAR -> Color(0xFF2B4F6B) to Color(0xFF3A2018)
        Weather.BRIGHT -> Color(0xFF2F5A7D) to Color(0xFF7B4226)
        Weather.CLOUDY -> Color(0xFF2A3F52) to Color(0xFF33262A)
        Weather.RAIN -> Color(0xFF24374A) to Color(0xFF262126)
        Weather.STORM -> Color(0xFF1D2C3C) to Color(0xFF1E1B20)
    }

    /** `.garden-frame::after` — heavier weather dims the whole scene. */
    fun veil(weather: Weather): Color = when (weather) {
        Weather.CLEAR, Weather.BRIGHT -> Color.Transparent
        Weather.CLOUDY -> Color(0x123C4850)
        Weather.RAIN -> Color(0x212D3E50)
        Weather.STORM -> Color(0x3D1E2C3C)
    }

    /**
     * Draws the wheel.
     *
     * @param turn 0f when the chosen weather is exactly overhead. Callers
     *   animate it so the sky turns rather than jumps.
     */
    fun DrawScope.drawWheel(weather: Weather, turn: Float) {
        val steps = Weather.entries
        val step = 360f / steps.size
        val radius = max(size.width, 300f) * 1.06f
        val centre = Offset(size.width / 2f, size.height + 26f)
        val orbit = radius * 0.75f
        val index = steps.indexOf(weather).coerceAtLeast(0)

        steps.forEachIndexed { i, candidate ->
            // Where this emblem sits once the wheel has turned.
            val angle = Math.toRadians(((i - index) * step + turn - 90f).toDouble())
            val at = Offset(
                centre.x + (cos(angle) * orbit).toFloat(),
                centre.y + (sin(angle) * orbit).toFloat(),
            )

            // Only the arc above the frame is worth drawing.
            if (at.y > size.height + 40f) return@forEachIndexed

            translate(at.x - 75f, at.y - 82f) {
                scale(1.5f, pivot = Offset.Zero) {
                    drawEmblem(candidate)
                }
            }
        }
    }

    /**
     * One weather, drawn in the prototype's 100x110 space.
     *
     * Internal rather than private since the slider thumb carries one. These
     * were drawn for a wheel of skies that no longer turns anywhere -- the
     * only caller of [drawWheel] is `GardenCanvas`, which nothing calls --
     * and they are the app's weather, already measured off the prototype.
     * Better reused than redrawn.
     */
    internal fun DrawScope.drawEmblem(weather: Weather) {
        when (weather) {
            Weather.CLEAR -> drawSun()

            Weather.BRIGHT -> {
                translate(-9f, -11f) { scale(0.86f, pivot = Offset(50f, 50f)) { drawSun() } }
                translate(6f, 9f) {
                    scale(0.82f, pivot = Offset(50f, 50f)) { drawCloud(Color(0xFFF0C894)) }
                }
            }

            Weather.CLOUDY -> {
                translate(-14f, -12f) {
                    scale(0.66f, pivot = Offset(50f, 50f)) { drawCloud(Color(0xFF9CACB8)) }
                }
                drawCloud(Color(0xFF8FA0AC))
            }

            Weather.RAIN -> {
                drawCloud(Color(0xFF7E8A94))
                listOf(38f, 50f, 62f).forEach { x ->
                    drawLine(
                        color = Color(0xFF8FA6B8),
                        start = Offset(x, 80f),
                        end = Offset(x - 4f, 93f),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            Weather.STORM -> {
                drawCloud(Color(0xFF6B747D))
                drawPath(
                    Path().apply {
                        moveTo(55f, 68f); lineTo(44f, 86f); lineTo(53f, 86f)
                        lineTo(48f, 99f); lineTo(63f, 80f); lineTo(54f, 80f)
                        close()
                    },
                    color = Color(0xFFF0BD3E),
                )
            }
        }
    }

    private fun DrawScope.drawSun() {
        repeat(8) { i ->
            rotate(degrees = i * 45f, pivot = Offset(50f, 50f)) {
                drawLine(
                    color = Color(0xFFF8C33B),
                    start = Offset(50f, 17f),
                    end = Offset(50f, 5f),
                    strokeWidth = 5.5f,
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(Color(0xFFF8C33B), radius = 21f, center = Offset(50f, 50f))
    }

    private fun DrawScope.drawCloud(fill: Color) {
        drawCircle(fill, radius = 15f, center = Offset(36f, 56f))
        drawCircle(fill, radius = 20f, center = Offset(54f, 48f))
        drawCircle(fill, radius = 13f, center = Offset(71f, 58f))
        drawRoundRect(
            color = fill,
            topLeft = Offset(28f, 58f),
            size = Size(52f, 17f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.5f, 8.5f),
        )
    }

    /**
     * The dashed ring the emblems ride on. Faint, and mostly off-frame.
     *
     * White rather than the dark green it was. A faint *dark* line on a dusk
     * sky is not faint, it is absent.
     */
    fun DrawScope.drawRing() {
        val radius = max(size.width, 300f) * 1.06f
        val centre = Offset(size.width / 2f, size.height + 26f)
        drawCircle(
            color = Color(0x1AFFFFFF),
            radius = radius * 0.75f,
            center = centre,
            style = Stroke(width = 1.5f),
        )
    }
}
