package app.harbor.cue

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.domain.CallStats
import app.harbor.domain.FeedbackPulse
import app.harbor.domain.FlowerKind
import app.harbor.domain.Flowers
import app.harbor.ui.FlowerMark
import app.harbor.ui.theme.Paper
import app.harbor.ui.theme.PrimaryAction
import app.harbor.ui.theme.QuietAction
import app.harbor.ui.theme.SurfaceGreen
import kotlin.math.abs

/**
 * What a call leaves behind.
 *
 * Two steps after the dialer hands control back: which flower it becomes, and
 * the bloom itself.
 *
 * The reward is the whole of it. It is asked once, nothing here can be failed,
 * and every answer grows something — the garden records that calls happened,
 * it does not score them.
 *
 * ## Why the feeling and the flower are one screen, not two
 *
 * There used to be a screen in front of this one: four feelings, a stepper for
 * how many minutes, and a box for what it was about. It was three questions
 * standing between somebody and their reward, asked in the minute after they
 * hung up on their mother, and the only one of the three that did any work was
 * the feeling — which existed to narrow the library down to four.
 *
 * Picking the flower answers that question better than a menu of adjectives
 * does, so the menu is gone and all of them are offered — the headline still
 * asks how the call felt, but the flower itself is the answer rather than a
 * label attached afterwards. The length of the call is the gap between the
 * cue appearing and this screen appearing, which Harbor already knows and no
 * longer needs anyone to type; it is stated here rather than asked, so it can
 * still be seen to be wrong.
 *
 * What survives from that screen is the way out — "we did not get to talk" —
 * because the ledger row is written the moment the dialer opens, and without
 * this the word `called` would quietly count conversations that never
 * happened.
 */
@Composable
fun CallFlow(
    who: String,
    /** Measured from handing off to the dialer until the user came back. */
    measuredMinutes: Int,
    initialTopic: String?,
    reducedMotion: Boolean = false,
    /**
     * False only for onboarding's preview cue. "Was this a good moment to be
     * asked?" is the reflection after a real call; asking it again before any
     * real call has happened would double the same study question rather than
     * add a second real answer to it.
     */
    askPulse: Boolean = true,
    onPlant: (minutes: Int, flower: FlowerKind, topic: String?) -> Unit,
    onPulse: (FeedbackPulse) -> Unit,
    /** They went to call and no conversation happened. */
    onNotReached: () -> Unit,
    onDone: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.Flower) }
    val minutes = measuredMinutes.coerceIn(1, 180)

    val shelf = FlowerKind.entries
    val pager = rememberPagerState(initialPage = 0) { shelf.size }
    val chosen by remember { derivedStateOf { shelf[pager.currentPage] } }

    // The ground takes a wash of whatever is in front of it.
    //
    // Mixed from the petal's *deep* tone rather than its light one. Washing
    // the paper with the light tone put a daisy — which is very nearly white
    // to begin with — on a ground the same colour as itself, and the flower
    // all but vanished. The deep tone is darker than any petal it belongs to,
    // so every bloom has something to sit against and the change of colour is
    // easier to see, not harder.
    val ground by animateColorAsState(
        targetValue = lerp(Paper, Color(Flowers.spec(chosen).petalDeep), 0.38f),
        animationSpec = tween(420),
        label = "flower-ground",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            Step.Flower -> {
                Text("$who's patch", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.size(8.dp))
                // The headline asks the feeling; the flower is how you answer
                // it, not a fact being reported. "Which flower was it?" read as
                // a memory test the first time somebody saw it.
                // The question, and then the flowers.
                //
                // "Choose the flower that matches" sat under this and told
                // somebody to do the thing the screen is already only
                // capable of: a shelf of flowers, one of them large and
                // named, and a button that says grow. The line was there in
                // case the shelf did not read as an answer; with the notes
                // gone it plainly does, and a sentence explaining an
                // interface is a sentence the interface has failed to make
                // unnecessary.
                Text("How did that call leave you feeling?", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.size(22.dp))

                // A shelf you push along rather than a grid you scan.
                //
                // Four at a time in a grid meant the library had to be pruned
                // to four before it was shown, which is what the feelings
                // question was for. They all fit on a shelf, the one in
                // the middle is the one you are choosing, and moving between
                // them is the good part.
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 104.dp),
                    pageSpacing = 4.dp,
                    modifier = Modifier.fillMaxWidth().height(184.dp),
                ) { page ->
                    val away = abs(page - pager.currentPage - pager.currentPageOffsetFraction)
                    val near = (1f - away).coerceIn(0f, 1f)
                    FlowerMark(
                        kind = shelf[page],
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(184.dp)
                            .graphicsLayer {
                                val s = 0.54f + 0.46f * near
                                scaleX = s
                                scaleY = s
                                alpha = 0.35f + 0.65f * near
                            },
                    )
                }

                Spacer(Modifier.size(6.dp))
                // The name, and only the name.
                //
                // The flower's note used to sit under it -- "That one left
                // me lighter" beneath "Glad we talked" -- and the two say
                // the same thing twice, once in the words somebody would use
                // and once in a gloss on them. On a shelf you push along,
                // that is a second line re-rendering on every swipe, which
                // makes the shelf feel heavier than it is and gives the eye
                // something to read instead of look at.
                //
                // The notes are not lost: FlowerSpec.note still carries them
                // and the garden's list still says them, where there is one
                // line per call and room to be discursive. Here the picture
                // is the argument and the name is the label.
                Text(
                    Flowers.spec(chosen).name,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                )

                Spacer(Modifier.size(14.dp))
                // Stated, not asked. Harbor timed it from the cue to this
                // screen; saying so is what lets somebody notice it is wrong.
                Text(
                    "About ${CallStats.formatDuration(minutes)}, by the look of it.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(8.dp))
                // "Grow this one", not "Grow glad we talked".
                //
                // With the note gone the name is two lines above the button,
                // and naming it again there is the same word twice inside an
                // inch. The flower is on screen, its name is under it, and
                // the button is the verb.
                PrimaryAction("Grow this one") {
                    onPlant(minutes, chosen, initialTopic)
                    step = Step.Bloom
                }

                // The row was written the moment the dialer opened, before
                // anything was known. Without this, changing your mind at the
                // dialer is recorded as a call, and "called" quietly counts
                // conversations that never happened.
                Spacer(Modifier.size(4.dp))
                CallOut("We did not get to talk", onNotReached)
            }

            Step.Bloom -> {
                var pulsed by remember { mutableStateOf(false) }

                Text(
                    "${CallStats.formatDuration(minutes)} together",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.size(24.dp))

                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .padding(bottom = 18.dp)
                            .size(width = 130.dp, height = 34.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceGreen),
                    )
                    Opening(
                        kind = chosen,
                        // How wide this one opens, which is a flourish and
                        // not the mechanic. What a long call actually earns
                        // is *more* flowers -- see Flowers.flowerCount, and
                        // the line under this animation, which used to
                        // describe the flourish as though it were the rule.
                        // Bounded at both ends so a short call still opens a
                        // whole flower.
                        full = Flowers.bloomScale(minutes).toFloat(),
                        reducedMotion = reducedMotion,
                    )
                }

                Spacer(Modifier.size(24.dp))
                Text("It opened.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.size(8.dp))
                // Say the rule the garden actually runs on.
                //
                // This read "A longer call opens a fuller bloom", which
                // describes the animation immediately above it and nothing
                // else. The bloom's width is a two-second flourish; what a
                // call leaves behind is one flower a minute
                // (Flowers.flowerCount), and that is the number every other
                // surface in the app counts -- "46 flowers have grown here",
                // "25 flowers, from 2 calls", the whole of Your activity.
                //
                // So the one screen whose job is to explain the reward was
                // teaching a model that contradicts every screen that shows
                // it. Somebody told a long call makes a bigger flower, who
                // then opens the garden and finds a drift of them, has to
                // work out on their own which of the two the app meant.
                //
                // It was also singular about a plural: "this one is planted"
                // for a call that planted thirty.
                val grew = Flowers.flowerCount(minutes)
                Text(
                    if (grew == 1) {
                        "Every minute of a call is a flower, and this was a minute. " +
                            "It is planted in $who's patch, and it stays there."
                    } else {
                        "Every minute of a call is a flower, so this one grew $grew. " +
                            "They are planted in $who's patch, and they stay there."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.size(24.dp))

                // Stage 8, for calls. Asked after the reward rather than
                // before it, so it never reads as the price of the flower.
                if (!pulsed && askPulse) {
                    Text(
                        "Was this a good moment to be asked?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuietAction("Good time", Modifier.weight(1f)) {
                            pulsed = true
                            onPulse(FeedbackPulse.GOOD_TIME)
                        }
                        QuietAction("Not this time", Modifier.weight(1f)) {
                            pulsed = true
                            onPulse(FeedbackPulse.BAD_TIME)
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                }

                PrimaryAction("Back to your day", onClick = onDone)
            }
        }
    }
}

private enum class Step { Flower, Bloom }

/**
 * The flower opening, from a bud.
 *
 * It used to appear at full size the instant the screen did, which made the
 * reward a picture of a flower rather than something that happened. So the
 * casing is there first, the bloom pushes out of it, and the twist comes off
 * as the petals settle — which is the shape of the thing it is drawing.
 *
 * Somebody who has asked for less movement gets the flower, open, immediately.
 */
@Composable
private fun Opening(kind: FlowerKind, full: Float, reducedMotion: Boolean) {
    val open = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(kind) {
        if (!reducedMotion) {
            open.animateTo(1f, tween(durationMillis = 1250, easing = FastOutSlowInEasing))
        }
    }

    val t = open.value
    Box(contentAlignment = Alignment.Center) {
        // The casing, gone by the time the bloom is half out.
        val casing = (1f - t * 2.4f).coerceIn(0f, 1f)
        if (casing > 0f) {
            Canvas(Modifier.size(200.dp).graphicsLayer { alpha = casing }) {
                val r = size.minDimension * 0.13f
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawOval(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF7E9C5A), Color(0xFF3E6B33)),
                        startY = cy - r * 1.7f,
                        endY = cy + r * 1.7f,
                    ),
                    topLeft = Offset(cx - r * 0.72f, cy - r * 1.7f),
                    size = Size(r * 1.44f, r * 3.4f),
                )
            }
        }

        FlowerMark(
            kind = kind,
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer { rotationZ = -26f * (1f - t) },
            // Starts inside the casing and pushes out of it.
            scale = 0.10f + (full - 0.10f) * t,
        )
    }
}

/** A way out that is not a failure. Quiet, and never the loudest thing here. */
@Composable
private fun CallOut(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
