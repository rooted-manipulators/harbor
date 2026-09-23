package app.harbor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import app.harbor.domain.StudyArm
import androidx.compose.runtime.LaunchedEffect
import app.harbor.domain.Windows
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import app.harbor.data.HarborRepository
import app.harbor.domain.DailyQuestion
import app.harbor.domain.Moment
import app.harbor.domain.Weather
import app.harbor.widget.WeatherWidget
import app.harbor.ui.theme.Eyebrow
import app.harbor.ui.theme.Ink
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * How life feels, set the way you would read a sky.
 *
 * Hand-translated from `weather-bar.tsx` and its `.weather-*` rules: a rail,
 * a gradient fill from sky blue to gold, a stop for each weather, and a thumb
 * you can tap or slide.
 *
 * A slider rather than five buttons on purpose — this is a scale, not a set of
 * options, and "a bit worse than yesterday" is the thing someone actually
 * wants to say.
 */
@Composable
fun WeatherBar(
    store: HarborRepository,
    modifier: Modifier = Modifier,
    /**
     * Where the thumb was, in root coordinates, at the moment an answer was
     * committed. Only the bees arm has anything to do with it -- see
     * [BeeFlight] -- and a caller that does not want one passes nothing.
     */
    onBeeOff: ((Offset) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by store.settings.collectAsState()
    val blocks by store.weekBlocks.collectAsState()
    val steps = Weather.entries
    val last = steps.size - 1
    val index = steps.indexOf(settings.weather).coerceAtLeast(0)

    val answers by store.dailyAnswers.collectAsState()
    val today = remember { LocalDate.now() }
    val question = remember(today) { DailyQuestion.forDay(today) }
    val answered = answers[today]
    var expanded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    // The one-word question is not on the page until the mood has been set.
    //
    // It used to hold its own row whether or not anyone was going to answer
    // it, and this card sits above the thing people open Harbor to do. Naming
    // how the day feels is the follow-on thought to setting the weather, so it
    // arrives when that thought does - as an extension of this card, never a
    // popup. Already answered today counts as having asked.
    var moodSet by remember { mutableStateOf(false) }

    var trackWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val inset = with(density) { 22.dp.toPx() }

    // Moving the slider and having moved it are two different events, and they
    // used to be one.
    //
    // Dragging from clear to storm crosses five steps, and each crossing wrote
    // the settings, appended a beat -- which reads, rewrites and re-serialises
    // the whole beats array, up to four thousand of them -- and pushed a
    // cross-process update to the home-screen widget. Five times, during a
    // gesture, while the field behind was repainting. That is the lag.
    //
    // So [choose] now does the one cheap thing that has to happen while the
    // thumb is moving, and [settle] does the rest once it stops. The study
    // gets better data out of it too: WEATHER_SET was being written once per
    // step scrubbed past rather than once per decision, so a single drag
    // logged five weathers the person never chose.
    // Today's guess, read off the week they typed in.
    //
    // Windows.weatherFor has existed unused since the week editor shipped --
    // docs/09-master-context.md lists it as one of two finished components
    // with no caller. This is the caller it was written for.
    //
    // Written into settings rather than held beside them, so the sky, the bee,
    // the widget, the ledger and the export all agree about what was on the
    // screen. What keeps that honest is weatherSetOn: a guess is marked as a
    // guess and the first touch makes the value theirs. See UserSettings.
    //
    // Once per day at most: `today` above is remembered, so this re-guesses
    // tomorrow and not on every recomposition.
    val guessed = settings.weatherSetOn != today
    LaunchedEffect(today, blocks, guessed) {
        if (!guessed) return@LaunchedEffect
        val guess = Windows.weatherFor(blocks, today.dayOfWeek)
        if (guess != store.settings.value.weather) {
            store.setSettings(store.settings.value.copy(weather = guess))
        }
    }

    fun choose(next: Int) {
        val clamped = next.coerceIn(0, last)
        moodSet = true
        if (steps[clamped] != settings.weather || guessed) {
            scope.launch {
                // The touch is what makes it theirs, even if they land on the
                // value already showing -- agreeing with a guess is still an
                // answer, and the study should be able to tell that from
                // somebody who never looked.
                store.setSettings(
                    store.settings.value.copy(
                        weather = steps[clamped],
                        weatherSetOn = today,
                    ),
                )
            }
        }
    }

    /** Called when the gesture ends: what was actually decided, recorded once. */
    // Where the thumb is right now, in the root's space. Kept up to date
    // by the thumb's own layout rather than recomputed from the fraction,
    // so it is right whatever the card's position on the page turns out to
    // be.
    var thumbAt by remember { mutableStateOf(Offset.Zero) }

    fun settle() {
        // The bee leaves on the release, not on every pixel of the drag.
        // Dragging across four moods is one answer and should launch one
        // bee, from where the thumb finished.
        onBeeOff?.invoke(thumbAt)
        scope.launch {
            val chosen = store.settings.value.weather
            store.note(Moment.WEATHER_SET, chosen.name.lowercase())
            // Same settings the widget's rail reads.
            WeatherWidget().updateAll(context)
        }
    }

    fun chooseFromX(x: Float) {
        val usable = trackWidth - inset * 2
        if (usable <= 0) return
        choose((((x - inset) / usable) * last).roundToInt())
    }

    Surface(modifier) {
        // Label and answer on one line rather than three stacked.
        //
        // This card sits between the field and the call button now, so every
        // row it takes is a row of somebody's people pushed off the screen.
        // The caption under the weather word was the first to go: it said
        // "Room to breathe. Nothing pressing." under the word "Clear", which
        // is the same thought twice.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The same question, in the voice of the thing asking it.
            //
            // It is still the identical five values underneath -- see
            // [app.harbor.ui.beeFace]. "How is life right now" was written for
            // a sky; a bee with a face asks more plainly, and the two arms
            // should not read as though they were asking different things.
            // One question in both arms.
            //
            // It briefly differed -- a sky asked "how is life", a bee asked
            // "how are you today" -- and that was a second variable nobody
            // ordered. The slider is seeded from the calendar now, so the
            // honest question is the one the calendar can actually answer.
            Eyebrow("How busy are you today")
            // The answer, not an action.
            //
            // At titleLarge in full-strength ink, sitting alone in the top
            // right of a card, this read as a link -- the corner of a card is
            // where "See all" lives. It is the slider's current value, so it
            // now looks like one: the same muted ink as the label it answers,
            // a size below it, and nothing about it suggesting a tap.
            Text(
                settings.weather.label,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .onSizeChanged { trackWidth = it.width }
                .pointerInput(last, trackWidth) {
                    detectTapGestures {
                        chooseFromX(it.x)
                        settle()
                    }
                }
                .pointerInput(last, trackWidth) {
                    detectHorizontalDragGestures(
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                    ) { change, _ -> chooseFromX(change.position.x) }
                },
        ) {
            // The rail.
            //
            // 26dp, not 10. The design draws this as a fat pill with the
            // gradient running the whole way along it and the thumb riding
            // *inside* its height -- closer to a sunset strip than to a
            // slider. At 10dp it read as a hairline with a bead on it, which
            // is the one thing on home that looked like a stock control.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            // A notch per weather.
            //
            // The five words used to run underneath, which cost a whole row.
            // Without them the rail came out as a grey bar with a dot resting
            // at one end of it — on a clear day, indistinguishable from a
            // control that was broken or switched off. These say the same
            // thing (there are five of these, you are at this one) and cost
            // no height at all.
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                steps.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (i <= index) Ink.copy(alpha = 0.30f)
                                else Chalk.copy(alpha = 0.28f),
                            ),
                    )
                }
            }

            // How far along the scale we are: dusk through to ember.
            //
            // The design draws this rail as a five-stop gradient rather than
            // the two it had -- cool blue, a hazy middle, warm sand, ember,
            // and a burnt red at the far end. It is the one place in the app
            // where a whole spectrum appears, and it is what makes the rail
            // read as a sky going over rather than as a volume slider.
            val fraction = if (last == 0) 0f else index.toFloat() / last
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                    .height(26.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        Brush.horizontalGradient(
                            // Half-strength while it is only a guess.
                            //
                            // The signifier, and deliberately not a sentence.
                            // A line saying "we set this from your calendar"
                            // would be read once, ignored after, and cost a
                            // row every day for the life of the study. A fill
                            // that is washed out until you touch it says
                            // provisional in the only place the eye is already
                            // looking, and the difference is obvious the
                            // moment the two states sit side by side.
                            listOf(
                                Color(0xFF2B4F6B),
                                Color(0xFF6F8FA8),
                                Color(0xFFE8D6A8),
                                Color(0xFFF0A35F),
                                Color(0xFFC9542C),
                            ).map { if (guessed) it.copy(alpha = 0.45f) else it },
                        ),
                    ),
            )

            // A dashed outline while nobody has touched it.
            //
            // The washed-out fill below was the first attempt and it fails in
            // the commonest case there is: an empty calendar guesses "Free",
            // which is step zero, so there is no fill to wash out and the
            // signifier disappears exactly when most people would meet it. It
            // also asked somebody to notice an opacity they had nothing to
            // compare against -- nobody sees both states at once.
            //
            // The track is always there at every value, so the mark goes on
            // the track. A dashed edge is the oldest way there is of drawing
            // something not yet committed, it needs no legend, and at one
            // pixel and a third opacity it is quiet enough to miss until the
            // day it matters.
            if (guessed) {
                // A fifth, not a third. The dash is a *structural* difference
                // -- broken against solid -- so it survives being quiet in a
                // way an opacity change never did, and at a third it was
                // announcing itself rather than sitting there.
                val edge = Chalk.copy(alpha = 0.20f)
                Canvas(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(26.dp),
                ) {
                    drawRoundRect(
                        color = edge,
                        cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                            ),
                        ),
                    )
                }
            }

            // the thumb
            val thumbX = with(density) {
                (inset + (trackWidth - inset * 2) * fraction).toDp() - 17.dp
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbX)
                    .onGloballyPositioned { thumbAt = it.positionInRoot() }
                    .size(30.dp)
                    .clip(CircleShape)
                    // Solid white, which in this design is the brightest thing
                    // on the page and is spent here on purpose: the thumb is
                    // the one part of the rail you are meant to grab.
                    .background(Chalk),
                contentAlignment = Alignment.Center,
            ) {
                // The design's thumb is a plain white disc. It used to carry
                // an amber pip, which on a white disc on a coloured rail was
                // a third colour in a 30dp circle.
            }

            // The bee sits on the thumb rather than replacing it.
            //
            // It overhangs the disc on every side, which is the point: the
            // white circle stays the thing your thumb is aiming at and the
            // bee is what it is carrying. Drawn after the disc and outside
            // its clip, or a round mask would take the wings off.
            //
            // What the thumb carries, which is the arm.
            //
            // ## Why the garden arm has one too
            //
            // It did not, and for a day that was the study quietly breaking.
            // The garden arm's answer lives in the sky; when the sky briefly
            // became the hour in both arms, dragging the slider in the
            // control changed nothing at all, while in the bees arm it
            // changed a face. The arm under test was the responsive one and
            // the control was inert, and any difference in how much people
            // touched the slider would have measured that rather than the
            // metaphor.
            //
            // The sky is the slider's again in the garden arm (see
            // SkySays), so that particular hole is closed. The thumb stays
            // anyway, in both arms, because it is worth having the answer
            // readable without looking up: the sky is behind a field of
            // dots and a bee is off wandering, and neither is a label. Same
            // stored value, same size, same place, in both. One carries a
            // sky, one carries a bee, and that difference is the only
            // difference -- which is what makes them comparable.
            when (LocalStudyArm.current) {
                StudyArm.BEES -> Image(
                    painter = painterResource(beeFace(settings.weather)),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        // Centred on the 30dp disc: half the difference in
                        // each direction, so the bee grows around the thumb
                        // rather than drifting off it.
                        .offset(x = thumbX - 17.dp, y = (-17).dp)
                        .size(64.dp),
                )

                StudyArm.GARDEN -> Canvas(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = thumbX - 9.dp, y = (-9).dp)
                        .size(48.dp),
                ) {
                    // The emblems are drawn in a 100 wide by 110 tall box,
                    // so the fit is by height and the centring is by width.
                    val k = size.height / 110f
                    translate((size.width - 100f * k) / 2f, 0f) {
                        scale(k, pivot = Offset.Zero) {
                            with(Sky) { drawEmblem(settings.weather) }
                        }
                    }
                }
            }
        }

        // The five step labels used to run under the rail, repeating the word
        // already set in large type directly above it. The rail's shape says
        // where you are on the scale; the labels only cost a row.

        // One word about today, folded into the sky rather than asked again.
        //
        // It used to be its own card directly below this one, which meant the
        // page asked how life was and then asked how today felt -- the same
        // question twice, a thumb-scroll apart. Setting the weather and
        // naming the day are one thought, so they are one card.
        if (moodSet || answered != null) Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .clickable { if (answered == null) expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            when {
                answered != null -> Column {
                    Eyebrow(question)
                    Text(
                        answered,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                expanded -> Column {
                    Eyebrow(question)
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(40) },
                        placeholder = { Text("one word") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Pill(text = "Keep it", selected = draft.isNotBlank()) {
                        val word = draft.trim()
                        if (word.isNotEmpty()) {
                            scope.launch {
                                store.setDailyAnswer(today, word)
                                // That they answered, never the word.
                                store.note(Moment.ANSWER_KEPT)
                            }
                            expanded = false
                        }
                    }
                }

                else -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallCopy(question, size = 13)
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The five, as a day rather than as a sky.
 *
 * The values are unchanged and so is their order -- these are the same stored
 * CLEAR..STORM the export has always carried, and renaming the labels cannot
 * touch a single row. What changed is that the words now say what the ladder
 * has always meant.
 *
 * It was always a busyness scale wearing weather's clothes. The captions
 * underneath said so from the start ("Room to breathe. Nothing pressing.",
 * "Good and busy. The kind you chose."), and [app.harbor.domain.Windows.weatherFor]
 * derives the whole thing from how much of the day is booked. Once the slider
 * began opening on that guess, "Cloudy" was the app describing a timetable in
 * a metaphor the user never asked for.
 *
 * The enum keeps the weather names because they are in Postgres and in every
 * exported file. A label is a label; a stored value is a promise.
 */
internal val Weather.label: String
    get() = when (this) {
        Weather.CLEAR -> "Free"
        Weather.BRIGHT -> "Easy"
        Weather.CLOUDY -> "Filling up"
        Weather.RAIN -> "Busy"
        Weather.STORM -> "Slammed"
    }

internal val Weather.caption: String
    get() = when (this) {
        Weather.CLEAR -> "Room to breathe. Nothing pressing."
        Weather.BRIGHT -> "Good and busy. The kind you chose."
        Weather.CLOUDY -> "A little grey around the edges."
        Weather.RAIN -> "Heavy going. Steady, but heavy."
        Weather.STORM -> "Too much at once. This passes."
    }
