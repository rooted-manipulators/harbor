package app.harbor.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import app.harbor.ui.theme.Motion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.harbor.R
import app.harbor.domain.StudyArm
import app.harbor.domain.Weather
import app.harbor.ui.theme.LocalReducedMotion
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * How big the bee is out in the field.
 *
 * Thirty-two, down from forty-six. At forty-six it was the largest thing on
 * home after the headline -- larger than the flower it was supposed to be
 * visiting -- and a mascot that out-sizes the garden turns the garden into
 * its backdrop.
 */
private val BEE = 32.dp

/**
 * The bee that lives in the field. Bees arm only.
 *
 * ## What it is
 *
 * One bee, always there, wandering the garden. It is the arm's premise made
 * permanent: in the garden arm the sky tells you the time of day and the
 * field is a place you look at; in the bees arm something is living in it,
 * and how it moves is how your day is going.
 *
 * ## How the mood shows
 *
 * Not as a label and not as a colour. The same information is in the pose
 * and in the pace, which is the only way a mascot carries a mood without
 * becoming a status icon:
 *
 *  - **free, easy** — the glad face, crossing the field in long quick runs.
 *  - **filling up, busy** — the tired face, shorter hops with longer
 *    pauses between them.
 *  - **slammed** — asleep, barely drifting, staying where it is.
 *
 * Somebody who never reads a word of this should still tell a slammed day
 * from a free one out of the corner of their eye. That is the test the
 * whole arm is built on, and it is why the pace carries as much of it as
 * the face does: three drawings would be a mood ring, three *behaviours*
 * are a creature.
 *
 * ## Why waypoints and not a path
 *
 * A sine wave is the cheap way to move something and it reads as a machine
 * doing it — perfectly even, perfectly repeating, predictable inside ten
 * seconds. A bee picks somewhere, goes, thinks about it, picks again. The
 * pause is as much of the character as the travel and it is the part a sine
 * cannot do at all.
 */
@Composable
fun FieldBee(
    weather: Weather,
    /** How tall the field band is. The bee stays inside it. */
    fieldHeight: Dp,
    /** Where to pick up from, when a flight has just delivered it. */
    startAt: Offset? = null,
    /** False while a flight is in the air, so there is only ever one bee. */
    visible: Boolean = true,
    /**
     * Whether the field is close enough in for a bee to be seen at all.
     *
     * Zoomed out the field is a map, a flower is a couple of pixels, and a
     * bee at its own size would be a fly on the lens. It fades out as the view
     * pulls back and in again as it closes -- the field reports which through
     * `FieldCanvas(onCloseUp = ...)`.
     */
    near: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (LocalStudyArm.current != StudyArm.BEES || !visible) return

    val presence by animateFloatAsState(
        targetValue = if (near) 1f else 0f,
        animationSpec = Motion.slow(),
        label = "bee-near",
    )
    if (presence == 0f && !near) return

    val still = LocalReducedMotion.current
    val density = LocalDensity.current
    val pace = weather.pace

    BoxWithConstraints(modifier.fillMaxWidth().height(fieldHeight)) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val beePx = with(density) { BEE.toPx() }
        if (wPx <= beePx || hPx <= beePx) return@BoxWithConstraints

        // Where the flight left it, or a spot down among the flowers.
        val begin = remember(startAt, wPx, hPx) {
            startAt ?: Offset(wPx * 0.62f, hPx * 0.55f)
        }
        val x = remember(begin) { Animatable(begin.x.coerceIn(0f, wPx - beePx)) }
        val y = remember(begin) { Animatable(begin.y.coerceIn(0f, hPx - beePx)) }
        var facingLeft by remember { mutableStateOf(false) }

        // The wander, restarted when the mood changes -- a bee that has
        // just fallen asleep should not finish the long run it was on.
        LaunchedEffect(pace, wPx, hPx, still) {
            if (still) return@LaunchedEffect
            val dice = Random(weather.ordinal * 31 + 7)
            while (true) {
                val fromX = x.value
                val fromY = y.value
                val toX = (fromX + (dice.nextFloat() * 2f - 1f) * wPx * pace.reach)
                    .coerceIn(0f, wPx - beePx)
                // Kept to the middle band: the top of the field is sky,
                // and the bottom of it is where the page's first card
                // begins -- a bee wandering down there crosses onto the
                // slider it just came from, which reads as the sprite
                // escaping rather than the garden being deep.
                val toY = (fromY + (dice.nextFloat() * 2f - 1f) * hPx * pace.reach * 0.7f)
                    .coerceIn(hPx * 0.30f, (hPx * 0.74f - beePx).coerceAtLeast(hPx * 0.32f))
                if (abs(toX - fromX) > 4f) facingLeft = toX < fromX

                val far = hypot(toX - fromX, toY - fromY)
                val ms = ((far / wPx) * pace.crossMs).toInt().coerceIn(600, 9000)
                val spec = tween<Float>(ms, easing = LinearEasing)
                coroutineScope {
                    // Both axes at once, so a leg is one movement.
                    val a = async { x.animateTo(toX, spec) }
                    val b = async { y.animateTo(toY, spec) }
                    a.await()
                    b.await()
                }
                delay(pace.restMs(dice))
            }
        }

        // A small bob under everything, so a resting bee is not a sticker.
        val bob = remember { Animatable(0f) }
        LaunchedEffect(still) {
            if (still) return@LaunchedEffect
            while (true) {
                bob.animateTo(1f, tween(1500, easing = LinearEasing))
                bob.snapTo(0f)
            }
        }

        Image(
            painter = painterResource(weather.sidePose),
            contentDescription = null,
            modifier = Modifier
                .size(BEE)
                .offset {
                    IntOffset(
                        x.value.toInt(),
                        (y.value + sin(bob.value * 2f * Math.PI.toFloat()) * 5f).toInt(),
                    )
                }
                .graphicsLayer {
                    // Facing the way it is going. The art faces right, so
                    // going left is a mirror of it.
                    scaleX = if (facingLeft) -1f else 1f
                    alpha = presence
                },
        )
    }
}

/** How a mood moves. */
private data class Pace(
    /** How far one leg reaches, as a share of the field's width. */
    val reach: Float,
    /** Milliseconds it would take to cross the whole field at this pace. */
    val crossMs: Float,
    private val restLo: Int,
    private val restHi: Int,
) {
    fun restMs(dice: Random): Long = dice.nextInt(restLo, restHi).toLong()
}

private val Weather.pace: Pace
    get() = when (this) {
        // Long quick runs and barely a pause. A free day should look like one.
        Weather.BRIGHT -> Pace(reach = 0.55f, crossMs = 2600f, restLo = 250, restHi = 900)
        Weather.CLEAR -> Pace(reach = 0.45f, crossMs = 3200f, restLo = 400, restHi = 1500)
        // Shorter hops, more thinking between them.
        Weather.CLOUDY -> Pace(reach = 0.30f, crossMs = 4200f, restLo = 900, restHi = 2600)
        Weather.RAIN -> Pace(reach = 0.22f, crossMs = 5200f, restLo = 1500, restHi = 4000)
        // Asleep: a few pixels of drift and a long wait. The point is that
        // it is visibly not going anywhere.
        Weather.STORM -> Pace(reach = 0.07f, crossMs = 9000f, restLo = 3500, restHi = 8000)
    }

/** Which of the three faces this mood wears. */
private val Weather.sidePose: Int
    @DrawableRes get() = when (this) {
        Weather.BRIGHT, Weather.CLEAR -> R.drawable.bee_side_glad
        Weather.CLOUDY, Weather.RAIN -> R.drawable.bee_side_tired
        Weather.STORM -> R.drawable.bee_side_asleep
    }
