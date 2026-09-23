package app.harbor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.R
import app.harbor.domain.TourStop
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Gold
import app.harbor.ui.theme.Hairline
import app.harbor.ui.theme.Ink
import app.harbor.ui.theme.LocalReducedMotion
import app.harbor.ui.theme.Motion
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.NavGlass
import app.harbor.ui.theme.Space

/**
 * The bee that walks somebody through the app after onboarding. ADR-016.
 *
 * The eight poses the tour draws on. Two more than [BeeMood]'s eight, and a
 * different set on purpose -- those are how a call felt; these are a guide's
 * gestures, so the shelf is greet, explain, point, wonder, idea, surprise,
 * celebrate, approve rather than any feeling at all.
 *
 * ## Placeholder pending the real art
 *
 * All eight currently point at `bee_standing`, copied eight times under
 * these names. `Walkthrough`'s stops are already final; when the drawn poses
 * arrive, cut them straight onto these eight filenames (the way
 * `tools/cut_moods.py` cut the mood sheet) and nothing here has to change --
 * the seam is the asset, not the code.
 */
enum class TourBeeState { GREET, EXPLAIN, POINT, WONDER, IDEA, SURPRISE, CELEBRATE, APPROVE }

internal fun tourBeeArt(state: TourBeeState): Int = when (state) {
    TourBeeState.GREET -> R.drawable.bee_tour_greet
    TourBeeState.EXPLAIN -> R.drawable.bee_tour_explain
    TourBeeState.POINT -> R.drawable.bee_tour_point
    TourBeeState.WONDER -> R.drawable.bee_tour_wonder
    TourBeeState.IDEA -> R.drawable.bee_tour_idea
    TourBeeState.SURPRISE -> R.drawable.bee_tour_surprise
    TourBeeState.CELEBRATE -> R.drawable.bee_tour_celebrate
    TourBeeState.APPROVE -> R.drawable.bee_tour_approve
}

/** What the bee says at a stop, and which pose says it. One sentence, always. */
private data class TourLine(val text: String, val pose: TourBeeState)

private fun lineFor(stop: TourStop): TourLine = when (stop) {
    TourStop.WELCOME ->
        TourLine("This is your garden. It grows a little with every call you make.", TourBeeState.GREET)
    TourStop.HOME_PEOPLE ->
        TourLine("Tap someone to see their week, or press Call to reach them now.", TourBeeState.POINT)
    TourStop.HOME_ADD ->
        TourLine("Add anyone else here, any time.", TourBeeState.POINT)
    TourStop.GARDEN_FIELD ->
        TourLine("Every flower is a call. The weather is how your week's been going.", TourBeeState.EXPLAIN)
    TourStop.GARDEN_FLOWER ->
        TourLine("Tap a flower to remember that call.", TourBeeState.POINT)
    TourStop.GARDEN_BEE ->
        TourLine("Zoom in close, and you'll find someone tending the garden.", TourBeeState.SURPRISE)
    TourStop.PERSON_DIAL ->
        TourLine(
            "The ring is their whole day — thorns when they're busy, blooms when you're both free.",
            TourBeeState.EXPLAIN,
        )
    TourStop.PERSON_PLANT ->
        TourLine("Press a bloom to hold that time for a call.", TourBeeState.POINT)
    TourStop.SCHEDULE_VIEWS ->
        TourLine("Week shows the shape of things. Day is for drawing one properly.", TourBeeState.EXPLAIN)
    TourStop.SCHEDULE_PALETTE ->
        TourLine("Pick thorns or flowers, then press the day to plant one.", TourBeeState.POINT)
    TourStop.SCHEDULE_QUIET ->
        TourLine("Set your do-not-disturb hours once, and they hold every day.", TourBeeState.IDEA)
    TourStop.SCHEDULE_COPY ->
        TourLine("One day like the last? Copy it across.", TourBeeState.IDEA)
    TourStop.SCHEDULE_CALENDAR ->
        TourLine("Or pull the week straight from your calendar.", TourBeeState.IDEA)
    TourStop.ACCOUNT_REMINDERS ->
        TourLine("Reminders live here — off until you turn them on.", TourBeeState.EXPLAIN)
    TourStop.ACCOUNT_DONE ->
        TourLine("That's the whole garden. Off you go.", TourBeeState.CELEBRATE)
}

private val CardShape = RoundedCornerShape(Space.three)

/**
 * The floating card the tour speaks from.
 *
 * Drawn once, at the root, over whatever screen the tour has just navigated
 * to (see `MainActivity`) -- the same "over the top of everything" layer
 * `FlowerLanding` uses. It never dims or blocks the screen behind it: the
 * point is to narrate the real thing while it is visible, not to hide it
 * behind a modal.
 *
 * [onNext] and [onSkip] are both always offered. A tour that cannot be left
 * at every single stop is the one thing this app does not build (CLAUDE.md).
 */
@Composable
fun TourCard(
    stop: TourStop,
    index: Int,
    count: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().navigationBarsPadding().padding(Space.two),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(Motion.arriving()) + slideInVertically(Motion.arriving()) { it / 3 },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(NavGlass)
                    .border(1.dp, Hairline, CardShape)
                    .padding(Space.two),
            ) {
                AnimatedContent(
                    stop,
                    transitionSpec = {
                        (fadeIn(Motion.normal()) + slideInVertically(Motion.normal()) { it / 4 }) togetherWith
                            fadeOut(Motion.fast())
                    },
                    label = "tour stop",
                ) { shown ->
                    val line = lineFor(shown)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BeePose(line.pose, Modifier.size(52.dp))
                        Spacer(Modifier.width(Space.oneHalf))
                        Text(
                            line.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = Chalk),
                        )
                    }
                }

                Spacer(Modifier.size(Space.oneHalf))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Dots(count, index)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Skip",
                            modifier = Modifier
                                .clickable(role = Role.Button, onClick = onSkip)
                                .padding(Space.one),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, color = Muted),
                        )
                        Spacer(Modifier.width(Space.half))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Gold)
                                .clickable(role = Role.Button, onClick = onNext)
                                .padding(horizontal = Space.oneHalf, vertical = Space.one),
                        ) {
                            Text(
                                if (index == count - 1) "Got it" else "Next",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 13.sp,
                                    color = Ink,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One tick per stop, lit up to and including the current one. */
@Composable
private fun Dots(count: Int, at: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i <= at) Gold else Hairline),
            )
        }
    }
}

/**
 * The bee itself, breathing gently in place.
 *
 * The same shape of movement [MoodBee] uses -- a small lift and lean read in
 * the layer, off under reduced motion -- kept separate rather than shared
 * because that one crossfades between twenty flowers and this one only ever
 * shows the pose it was given.
 */
@Composable
private fun BeePose(pose: TourBeeState, modifier: Modifier = Modifier) {
    val still = LocalReducedMotion.current
    val phase: State<Float>? = if (still) null else {
        rememberInfiniteTransition(label = "tour bee").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "bob",
        )
    }
    Image(
        painter = painterResource(tourBeeArt(pose)),
        contentDescription = null,
        modifier = modifier.graphicsLayer {
            val t = (phase?.value ?: 0f) * 2f * Math.PI.toFloat()
            translationY = -3.dp.toPx() * (0.5f + 0.5f * kotlin.math.sin(t))
        },
    )
}
