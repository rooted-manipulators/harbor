package app.harbor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.harbor.R
import app.harbor.domain.StudyArm
import app.harbor.domain.Weather
import app.harbor.ui.theme.LocalReducedMotion
import kotlin.math.sin
import kotlin.random.Random

/** How long a bee takes to get from the slider into the field. */
private const val FLIGHT_MS = 1500

/**
 * The flapping, as frames.
 *
 * Twenty stills cut from the illustrator's ten-second reel -- the last one
 * and two-thirds seconds of it, where the bee is airborne rather than
 * standing on a ground line this app does not have. Each one is cropped
 * around the bee itself, so the sprite is a bee flapping in place and the
 * travel is entirely the app's. See `tools`-side notes in the commit: a
 * shared crop box left the bee wandering inside its own frame, and that
 * plus the arc read as two motions fighting.
 *
 * Twelve a second, which is the reel's own cadence halved and still well
 * inside what reads as continuous for a wing.
 */
private val FLY_FRAMES = intArrayOf(
    R.drawable.bee_fly_00, R.drawable.bee_fly_01, R.drawable.bee_fly_02,
    R.drawable.bee_fly_03, R.drawable.bee_fly_04, R.drawable.bee_fly_05,
    R.drawable.bee_fly_06, R.drawable.bee_fly_07, R.drawable.bee_fly_08,
    R.drawable.bee_fly_09, R.drawable.bee_fly_10, R.drawable.bee_fly_11,
    R.drawable.bee_fly_12, R.drawable.bee_fly_13, R.drawable.bee_fly_14,
    R.drawable.bee_fly_15, R.drawable.bee_fly_16, R.drawable.bee_fly_17,
    R.drawable.bee_fly_18, R.drawable.bee_fly_19,
)

private const val FLY_FPS = 12

/**
 * A bee leaves the slider and goes into the field. Bees arm only.
 *
 * ## What it is for
 *
 * The bees arm moves the mood question off the weather and onto a bee, and
 * until now that was one face on the slider thumb. Changing the slider
 * therefore changed a 64dp sticker and nothing else, which is a smaller
 * thing than the arm is meant to be: the point of the metaphor is that the
 * bees are *in* the garden with you, not that a bee is a dial.
 *
 * So the answer goes somewhere. Let go of the slider and the bee you just
 * set lifts off the thumb, climbs into the field and is gone — the mood
 * carried into the garden rather than left sitting on the control that set
 * it.
 *
 * ## Why a one-shot rather than a bee that stays
 *
 * A resident bee would be a second thing moving on a screen whose top half
 * already moves, and it would be there at every glance rather than at the
 * moment it means something. This fires on the answer and clears, so the
 * arm's difference is felt exactly when the arm's question is answered.
 *
 * ## Why it is drawn over the page and not inside the field
 *
 * [FieldCanvas] draws through its own camera in its own space, which is the
 * wrong frame for something that starts at a control outside it. This is an
 * overlay in the page's coordinates: it takes the thumb's position in root
 * space, curves up to [toY], and fades. It never touches the field's own
 * drawing, so nothing about the garden depends on it.
 */
@Composable
fun BeeFlight(
    /** The thumb's position in root coordinates, or null when nothing flies. */
    from: Offset?,
    /** The mood just chosen. The bee wears its face on the way up. */
    weather: Weather,
    /** Root y to climb to — somewhere inside the field's band. */
    toY: Float,
    /**
     * Called when the flight is over, with where it ended, so the resident
     * bee can pick up from exactly there rather than appearing elsewhere.
     */
    onDone: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    
    if (LocalStudyArm.current != StudyArm.BEES || from == null) return

    val still = LocalReducedMotion.current

    // One flight per launch. Keyed on the point it left from, so a second
    // answer while the first is still climbing restarts it rather than
    // queueing -- somebody dragging across four moods should see one bee
    // leave, not four stack up.
    val travel = remember(from) { Animatable(0f) }

    // Which way it wanders on the way up. Fixed per flight, from the launch
    // point, so it is not a different arc on every frame and not the same
    // arc every time either.
    val drift = remember(from) { Random(from.hashCode()).nextFloat() * 2f - 1f }

    val landing = Offset(from.x, toY)

    LaunchedEffect(from) {
        // Nothing to watch for somebody who asked for less movement, and
        // this is the one place that can be skipped without taking any
        // information with it: the thumb still wears the face, which is
        // where the answer actually lives, and the bee is already in the
        // field waiting.
        if (!still) {
            travel.snapTo(0f)
            travel.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
        }
        onDone(landing)
    }

    if (still) return

    val t = travel.value
    val rise = (from.y - toY).coerceAtLeast(0f)

    // Which still to show. Wrapped, so a slow flight keeps flapping rather
    // than freezing on the last frame halfway up.
    val step = ((t * FLIGHT_MS / 1000f) * FLY_FPS).toInt() % FLY_FRAMES.size

    Image(
        painter = painterResource(FLY_FRAMES[step]),
        contentDescription = null,
        modifier = modifier
            // Bigger than the thumb's bee, because this art has the
            // wings spread and a good deal of transparent air around it.
            .size(92.dp)
            .offset {
                IntOffset(
                    // Sideways drift, plus a wobble across it. Bees do not
                    // fly in straight lines and a straight line here would
                    // read as a cursor moving.
                    (from.x + drift * 90f * t + sin(t * 9f) * 13f).toInt(),
                    (from.y - rise * t).toInt(),
                )
            }
            .graphicsLayer {
                // Smaller as it goes, but only a little.
                //
                // It was 0.45, which by mid-flight left a 29dp bee at
                // three-quarters alpha over a field of green dots -- present
                // in the logs, invisible on the phone. Depth is worth less
                // here than being seen at all.
                val shrink = 1f - 0.22f * t
                scaleX = shrink
                scaleY = shrink
            }
            // Solid all the way, and out only at the very end, where the
            // resident bee takes over from it. Anything more than a hand-off
            // frame or two of fade and the two of them are visibly two.
            .alpha(((1f - t) / 0.12f).coerceIn(0f, 1f)),
    )
}
