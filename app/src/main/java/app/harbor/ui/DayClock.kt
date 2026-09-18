package app.harbor.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import app.harbor.domain.BlockKind
import app.harbor.domain.DayArcs
import app.harbor.ui.theme.DialDeep
import app.harbor.ui.theme.DialLit
import app.harbor.ui.theme.LocalReducedMotion
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A day, drawn as a clock, with what the day says drawn around it.
 *
 * The arcs are the week's own two marks bent round a circle: a thorned arc
 * over the hours somebody is busy, a blooming one over the hours they kept
 * free. An hour nobody said anything about carries nothing, which is the
 * point — see [DayArcs], where all of the arithmetic lives and where the note
 * on why it lives apart from the drawing is.
 *
 * ## An ordinary clock inside a ring that is not one
 *
 * The **face** is a plain twelve-hour clock: twelve marks, an hour hand round
 * twice a day, a minute hand round once an hour. It is telling the time, and a
 * clock that reads as a clock is worth more there than a clever one — the
 * first pass made the whole dial twenty-four hours and it cost exactly what
 * you would expect, a face nobody could read at a glance.
 *
 * The **ring** outside it is a whole day, one turn for twenty-four hours,
 * because that is the only scale on which an arc means one stretch of one day.
 * On a twelve-hour ring a nine o'clock lecture would also cover the evening
 * and a reminder dragged onto it could be either.
 *
 * So the two scales sit one inside the other, which is an old watch idea
 * rather than a new one, and the seam is real: the hour hand does not point at
 * the arc for the hour it is in. Midnight is the top of the ring and noon is
 * the bottom, and because the hand no longer says where on the ring you are,
 * the ring says it itself — a faint track with a notch at midnight and at
 * noon, which is the only thing drawn on a day nobody has marked.
 *
 * ## The reminder
 *
 * A bloom that sits on the arc and can be pushed along it. It cannot leave the
 * free stretch it was planted on: not into a thorn, and not out into the
 * unmarked hours. Both refusals are answered with a buzz rather than a
 * message, and the buzz belongs to the caller — this file owns no platform at
 * all beyond a canvas.
 */
@Composable
internal fun DayClock(
    arcs: List<DayArcs.Arc>,
    /** The time to point at. Passed in rather than read, so it can be tested. */
    now: LocalTime,
    /** Where the reminder sits, in minutes since midnight, or null for none. */
    reminder: Int?,
    modifier: Modifier = Modifier,
    /** True while a finger is on the reminder. Wakes the markings up. */
    stirring: Boolean = false,
    /** Somebody pressed the dial at this minute. Not called for a miss. */
    onPress: ((Int) -> Unit)? = null,
    /** A finger is pushing the reminder towards this minute. */
    onDragTo: ((Int) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    // The gesture handlers outlive a recomposition, so everything they read
    // has to be read through one of these or they answer with whatever was
    // true when the finger first went down.
    val livePress by rememberUpdatedState(onPress)
    val liveDrag by rememberUpdatedState(onDragTo)
    val liveEnd by rememberUpdatedState(onDragEnd)
    val liveReminder by rememberUpdatedState(reminder)

    // How awake the tick marks are. A spring rather than a switch, so the
    // markings gather themselves as the flower is picked up and settle again
    // when it is let go instead of snapping to attention.
    val still = LocalReducedMotion.current
    val stir by animateFloatAsState(
        targetValue = if (stirring && !still) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "stir",
    )

    // A dial nobody handed a callback to takes no gestures at all.
    //
    // Not a tidiness: `detectTapGestures` consumes what it receives whether or
    // not it does anything with it, so a clock installed with no handlers ate
    // every press and the card it sits on could never be tapped. The small
    // dial on the page is exactly that clock, and raising it is exactly that
    // press.
    //
    // Decided once, from the callbacks a call site starts with, because a
    // given one is either the dial you can push a flower round or the picture
    // of it -- never both by turns.
    val handled = onPress != null || onDragTo != null

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (!handled) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { at -> minuteOf(at)?.let { livePress?.invoke(it) } }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (liveDrag == null) return@awaitEachGesture
                            val held = liveReminder ?: return@awaitEachGesture
                            val from = minuteOf(down.position) ?: return@awaitEachGesture
                            // Only a press that landed on the bloom moves it.
                            // Anywhere else on the dial is a press on the day,
                            // which is somebody else's gesture.
                            if (gapBetween(from, held) > GRAB_MINUTES) return@awaitEachGesture
                            drag(down.id) { change ->
                                change.consume()
                                minuteOf(change.position)?.let { liveDrag?.invoke(it) }
                            }
                            liveEnd?.invoke()
                        }
                    },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f

            val faceR = outer * 0.66f
            val arcR = outer * 0.87f
            val arcWidth = outer * 0.16f

            drawFace(centre, faceR)
            // The marks wake up about where the flower *is*, which is a place
            // on the ring rather than a time on the face. Measuring the
            // distance in minutes would light the mark for nine in the morning
            // while the flower sat over the evening.
            drawTicks(centre, faceR, stir, reminder?.let { DayArcs.degreesAt(it) })
            drawDayTrack(centre, arcR, arcWidth)
            arcs.forEach { arc -> drawDayArc(centre, arcR, arcWidth, arc) }
            drawHands(centre, faceR, now)
            reminder?.let { drawReminder(centre, arcR, arcWidth, it) }
        }
    }
}

/**
 * How near the bloom a finger has to land to be holding it, in minutes of arc.
 *
 * Generous on purpose. The bloom is about a centimetre across and rides a
 * ring, so its honest hit box is an arc rather than a circle, and forty
 * minutes either side of it is roughly a thumb.
 */
private const val GRAB_MINUTES = 40

/**
 * Which minute of the day a touch landed on, or null if it missed the dial.
 *
 * On [PointerInputScope] rather than closing over a measured size, because the
 * gesture scope already knows how big it is. Reading the canvas's size during
 * the draw and stashing it in state is the other way to do this, and it is the
 * way that quietly loops: assigning state while drawing schedules a
 * recomposition, which redraws, which assigns.
 */
private fun PointerInputScope.minuteOf(at: Offset): Int? {
    val outer = minOf(size.width, size.height) / 2f
    if (outer <= 0f) return null
    val v = at - Offset(size.width / 2f, size.height / 2f)
    if (hypot(v.x, v.y) > outer) return null
    // atan2 measures anticlockwise from three o'clock; the dial measures
    // clockwise from midnight.
    val deg = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble())).toFloat() + 90f
    return DayArcs.minuteAt(deg)
}

/** The shorter way round the dial between two minutes. */
private fun gapBetween(a: Int, b: Int): Int {
    val raw = abs(a - b)
    return minOf(raw, DayArcs.DAY_MINUTES - raw)
}

/**
 * A point [radius] out from [centre], [degrees] clockwise from the top.
 *
 * Not `at`: that name is a parameter on [minuteOf] and a function on [DayArcs]
 * that means a time, and three of those in one file is two too many.
 */
private fun pointAt(centre: Offset, radius: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble() - 90.0)
    return Offset(
        centre.x + (radius * cos(rad)).toFloat(),
        centre.y + (radius * sin(rad)).toFloat(),
    )
}

/**
 * A point on the **ring**: [minute] of the day, [radius] out from [centre].
 *
 * Named for the day rather than for the dial, because the face has its own
 * scale now and a helper called `on` would happily put an evening arc over the
 * morning without anybody noticing at the call site.
 */
private fun onDay(centre: Offset, radius: Float, minute: Int): Offset =
    pointAt(centre, radius, DayArcs.degreesAt(minute))

/** Degrees of arc, as the minutes of day they cover. */
private fun degreesToMinutes(degrees: Float): Int =
    (degrees / 360f * DayArcs.DAY_MINUTES).toInt()

/** The lit face the hands live on. */
private fun DrawScope.drawFace(centre: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(DialLit, DialDeep),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/**
 * Twelve marks, four of them long: an ordinary clock face.
 *
 * [stir] is how awake they are and [nearDeg] is the angle they are awake
 * *about* — where the flower is on the ring, not what time it says. The two
 * differ now that the face and the ring run on different scales, and the angle
 * is the one that matters: the marks that stand up and brighten should be the
 * marks the flower is passing, so that an eye following it never has to leave
 * it to know where it has got to. Lighting the mark for the *hour* would send
 * an evening flower's glow round to the morning side of the face.
 */
private fun DrawScope.drawTicks(centre: Offset, faceR: Float, stir: Float, nearDeg: Float?) {
    for (hour in 0 until 12) {
        val deg = hour * 30f
        val major = hour % 3 == 0

        // Nought when the flower is across the dial from this mark, one when
        // it is right beside it. Ninety degrees of reach, so a handful move
        // together rather than one blinking on its own.
        val reach = if (nearDeg == null) 0f else {
            (1f - degreesApart(deg, nearDeg) / 90f).coerceAtLeast(0f)
        }
        val woken = stir * reach

        val length = faceR * (if (major) 0.19f else 0.12f) * (1f + 0.55f * woken)
        val from = faceR * 0.88f
        drawLine(
            color = Color.White.copy(
                alpha = ((if (major) 0.88f else 0.45f) + 0.12f * woken).coerceAtMost(1f),
            ),
            start = pointAt(centre, from, deg),
            end = pointAt(centre, from - length, deg),
            strokeWidth = (if (major) 3.4f else 2.2f) * (1f + 0.5f * woken),
            cap = StrokeCap.Round,
        )
    }
}

/** The shorter way round a circle between two angles, in degrees. */
private fun degreesApart(a: Float, b: Float): Float {
    val raw = abs(a - b) % 360f
    return minOf(raw, 360f - raw)
}

/**
 * The day the arcs are laid on, when there are none.
 *
 * With a twelve-hour face the hands no longer say where on the ring anything
 * is, so the ring has to. A hairline track with a notch at the top for
 * midnight and one at the bottom for noon is the least that can be drawn and
 * still answer "which half of this is the evening" — and it is deliberately
 * not an arc: an arc is a statement somebody made, and this is only the shape
 * their statements would go on.
 */
private fun DrawScope.drawDayTrack(centre: Offset, radius: Float, width: Float) {
    val box = Rect(centre - Offset(radius, radius), Size(radius * 2, radius * 2))
    drawArc(
        color = Color.White.copy(alpha = 0.07f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = box.topLeft,
        size = box.size,
        style = Stroke(width = width),
    )
    for (deg in listOf(0f, 180f)) {
        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = pointAt(centre, radius - width / 2f, deg),
            end = pointAt(centre, radius + width / 2f, deg),
            strokeWidth = 1.6f,
        )
    }
}

/** One stretch of the day, on the ring outside the face. */
private fun DrawScope.drawDayArc(
    centre: Offset,
    radius: Float,
    width: Float,
    arc: DayArcs.Arc,
) {
    val box = Rect(centre - Offset(radius, radius), Size(radius * 2, radius * 2))
    val start = DayArcs.degreesAt(arc.from) - 90f
    val sweep = DayArcs.degreesAt(arc.to) - DayArcs.degreesAt(arc.from)

    when (arc.kind) {
        BlockKind.BUSY -> {
            drawThornSpikes(centre, radius, width, arc)
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(ThornEdge, ThornDeep, ThornLit, ThornEdge),
                    centre,
                ),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = box.topLeft,
                size = box.size,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }

        // Peach into pink, the flower's own two colours, so the stretch you
        // may be called in is drawn in the same ink as the bloom you put on it.
        BlockKind.FREE -> drawArc(
            brush = Brush.linearGradient(
                colors = listOf(PetalLight, StemTop, StemFoot),
                start = onDay(centre, radius, arc.from),
                end = onDay(centre, radius, arc.to),
            ),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = box.topLeft,
            size = box.size,
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }
}

/**
 * The spikes on a thorn arc, outside it.
 *
 * Spaced by time rather than by count, so a seven-hour block of lectures is
 * not a hedgehog and a one-hour one is not bald.
 */
private fun DrawScope.drawThornSpikes(
    centre: Offset,
    radius: Float,
    width: Float,
    arc: DayArcs.Arc,
) {
    val every = 45
    val outer = radius + width / 2f
    val reach = width * 0.55f
    // The base half-width is a length along the rim; the dial is addressed in
    // minutes, so it has to be turned into an angle and then into minutes
    // before it can be used. Skipping either conversion is how a spike ends up
    // four times too wide, which on this dial is most of a morning.
    val halfMinutes = degreesToMinutes(
        Math.toDegrees((width * 0.30f / outer).toDouble()).toFloat(),
    ).coerceAtLeast(1)

    val path = Path()
    var minute = arc.from + every / 2
    while (minute < arc.to) {
        val tip = onDay(centre, outer + reach, minute)
        val left = onDay(centre, outer - 1f, minute - halfMinutes)
        val right = onDay(centre, outer - 1f, minute + halfMinutes)
        path.moveTo(left.x, left.y)
        path.lineTo(tip.x, tip.y)
        path.lineTo(right.x, right.y)
        path.close()
        minute += every
    }
    drawPath(path, Brush.linearGradient(listOf(SpikeTop, SpikeFoot)))
}

/**
 * An ordinary pair of hands, on the face's own twelve-hour scale.
 *
 * Neither of these has anything to do with the ring. See the note at the top
 * of the file: the hour hand pointing away from the arc for the hour it is in
 * is the accepted cost of a face that reads as a clock.
 */
private fun DrawScope.drawHands(centre: Offset, faceR: Float, now: LocalTime) {
    val hourDeg = DayArcs.faceDegreesAt(now.hour * 60 + now.minute)
    val minuteDeg = now.minute * 6f

    drawLine(
        color = Color(0xFFF6E7CE),
        start = pointAt(centre, faceR * 0.12f, hourDeg + 180f),
        end = pointAt(centre, faceR * 0.50f, hourDeg),
        strokeWidth = faceR * 0.078f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Color(0xFFFFF6E6),
        start = pointAt(centre, faceR * 0.12f, minuteDeg + 180f),
        end = pointAt(centre, faceR * 0.76f, minuteDeg),
        strokeWidth = faceR * 0.052f,
        cap = StrokeCap.Round,
    )
    drawCircle(Color(0xFFF6E7CE), radius = faceR * 0.055f, center = centre)
}

/** The bloom you can push along the arc. */
private fun DrawScope.drawReminder(
    centre: Offset,
    radius: Float,
    width: Float,
    minute: Int,
) {
    val at = onDay(centre, radius, minute)
    val head = width * 0.85f
    // A little light under it, so it reads as sitting on the arc rather than
    // as a hole punched through it.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x66FFCA8E), Color(0x00FFCA8E)),
            center = at,
            radius = head * 2.1f,
        ),
        radius = head * 2.1f,
        center = at,
    )
    drawBloomHead(at, head)
}
