package app.harbor.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.harbor.R
import app.harbor.domain.BeeMood
import app.harbor.domain.FlowerKind
import app.harbor.ui.theme.LocalReducedMotion
import app.harbor.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.sin

/** The art for each of the eight bees. Cut by `tools/cut_moods.py`. */
internal fun moodArt(mood: BeeMood): Int = when (mood) {
    BeeMood.HAPPY -> R.drawable.bee_mood_happy
    BeeMood.CALM -> R.drawable.bee_mood_calm
    BeeMood.LOVED -> R.drawable.bee_mood_loved
    BeeMood.CURIOUS -> R.drawable.bee_mood_curious
    BeeMood.GROUNDED -> R.drawable.bee_mood_grounded
    BeeMood.HOPEFUL -> R.drawable.bee_mood_hopeful
    BeeMood.BRAVE -> R.drawable.bee_mood_brave
    BeeMood.ANXIOUS -> R.drawable.bee_mood_anxious
}

/**
 * The bee and its flower, in the mood of the last call. Bees arm only.
 *
 * Each picture is a whole scene -- the bee, the flower it is holding, the
 * patch of earth -- so this stands in for the plant *and* the standing bee,
 * rather than adding a third thing to an arch that already had two.
 *
 * ## The movement
 *
 * A slow breath: the scene lifts two dp and leans a degree and a half from
 * its foot, on a four-second loop with the lean a quarter-cycle behind the
 * lift so it never simply rocks. Slow enough to notice on a second look and
 * never enough to pull the eye off the call button under it. Read in the
 * layer, so the arch is not recomposed every frame, and absent entirely under
 * reduced motion.
 *
 * A new mood crossfades in over the old one instead of cutting.
 */
@Composable
fun MoodBee(kind: FlowerKind, modifier: Modifier = Modifier) {
    val still = LocalReducedMotion.current
    val phase: State<Float>? = if (still) null else {
        rememberInfiniteTransition(label = "mood bee").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "breath",
        )
    }
    val lift = with(LocalDensity.current) { 2.dp.toPx() }

    Crossfade(BeeMood.of(kind), modifier, animationSpec = Motion.slow(), label = "mood") { mood ->
        Image(
            painter = painterResource(moodArt(mood)),
            contentDescription = null,
            alignment = Alignment.BottomCenter,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val t = (phase?.value ?: 0f) * 2f * PI.toFloat()
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationY = -lift * (0.5f + 0.5f * sin(t))
                rotationZ = SWAY_DEG * sin(t - PI.toFloat() / 2f)
            },
        )
    }
}

private const val BREATH_MS = 4000
private const val SWAY_DEG = 1.5f
