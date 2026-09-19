package app.harbor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.harbor.domain.Weather

/**
 * The five weathers, drawn.
 *
 * Hand-translated from the prototype's `sky-wheel.tsx`, whose emblem
 * geometry is a 100x110 viewBox: sun and rays, clouds as three circles over
 * a rounded bar, rain as three falling strokes, storm as a bolt.
 *
 * ## What used to be here
 *
 * A wheel. Five weathers rode an arc whose centre sat below the frame, and
 * choosing one turned it until that emblem was overhead — the sky changing
 * rather than a setting changing. It was drawn by `GardenCanvas`, the plot
 * view, which stopped being called when the garden screen moved to the
 * field and was deleted with it. `gradient`, `veil`, `drawWheel` and
 * `drawRing` went at the same time; they are in the history if the plot
 * view ever comes back.
 *
 * The emblems outlived it. They are what the mood slider's thumb wears in
 * the garden arm — see `WeatherBar` — which is the one place the five
 * weathers are still drawn for anybody.
 */
internal object Sky {

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
}
