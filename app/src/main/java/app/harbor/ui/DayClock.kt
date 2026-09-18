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
 * ## Why the hands look wrong for a second
 *
 * This is a twenty-four hour dial: the hour hand goes round once a day rather
 * than twice, so eight in the morning is a third of the way round and eight at
 * night is two thirds. That is a real convention rather than an invention — it
 * is how a twenty-four hour watch works — and it is the only way an arc can
 * mean one stretch of one day. On a twelve-hour face a nine o'clock lecture
 * also covers the evening, and a reminder dragged onto it could be either.
 *
 * The minute hand keeps the ordinary scale, one turn an hour, because it is
 * placing nothing on the day and because a minute hand on a day's scale would
 * barely move.
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

            val faceR = outer * 0.70f
            val arcR = outer * 0.87f
            val arcWidth = outer * 0.15f

            drawFace(centre, faceR)
            drawTicks(centre, faceR, stir, reminder)
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

/** A point on the dial: [minute] of the day, [radius] out from [centre]. */
private fun on(centre: Offset, radius: Float, minute: Int): Offset {
    val rad = Math.toRadians(DayArcs.degreesAt(minute).toDouble() - 90.0)
    return Offset(
        centre.x + (radius * cos(rad)).toFloat(),
        centre.y + (radius * sin(rad)).toFloat(),
    )
}

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
 * Twenty-four marks, four of them long.
 *
 * [stir] is how awake they are and [near] is the minute they are awake
 * *about*. While the reminder is being pushed round, the marks it is passing
 * stand up and brighten and the ones behind it settle back — the same
 * information the time readout gives, said in the shape of the dial, so an eye
 * following the flower never has to leave it to know where it has got to.
 */
private fun DrawScope.drawTicks(centre: Offset, faceR: Float, stir: Float, near: Int?) {
    for (hour in 0 until 24) {
        val minute = hour * 60
        val major = hour % 6 == 0

        // Nought when the flower is on the other side of the dial, one when it
        // is right here. Three hours of reach, so a handful of marks move
        // together rather than one blinking on its own.
        val reach = if (near == null) 0f else {
            (1f - gapBetween(minute, near) / 180f).coerceAtLeast(0f)
        }
        val woken = stir * reach

        val length = faceR * (if (major) 0.17f else 0.11f) * (1f + 0.55f * woken)
        val from = faceR * 0.87f
        drawLine(
            color = Color.White.copy(
                alpha = ((if (major) 0.85f else 0.42f) + 0.15f * woken).coerceAtMost(1f),
            ),
            start = on(centre, from, minute),
            end = on(centre, from - length, minute),
            strokeWidth = (if (major) 3.2f else 2.2f) * (1f + 0.5f * woken),
            cap = StrokeCap.Round,
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
                start = on(centre, radius, arc.from),
                end = on(centre, radius, arc.to),
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
        val tip = on(centre, outer + reach, minute)
        val left = on(centre, outer - 1f, minute - halfMinutes)
        val right = on(centre, outer - 1f, minute + halfMinutes)
        path.moveTo(left.x, left.y)
        path.lineTo(tip.x, tip.y)
        path.lineTo(right.x, right.y)
        path.close()
        minute += every
    }
    drawPath(path, Brush.linearGradient(listOf(SpikeTop, SpikeFoot)))
}

/**
 * An hour hand on the day's scale and a minute hand on the hour's.
 *
 * See the note at the top of the file: the pairing is a twenty-four hour
 * watch's, not a mistake.
 */
private fun DrawScope.drawHands(centre: Offset, faceR: Float, now: LocalTime) {
    val minuteOfDay = now.hour * 60 + now.minute
    val half = DayArcs.DAY_MINUTES / 2

    drawLine(
        color = Color(0xFFF6E7CE),
        start = on(centre, faceR * 0.12f, minuteOfDay + half),
        end = on(centre, faceR * 0.50f, minuteOfDay),
        strokeWidth = faceR * 0.075f,
        cap = StrokeCap.Round,
    )
    // One turn an hour, so it has to be put back on the dial's own scale.
    val minuteHand = now.minute * DayArcs.DAY_MINUTES / 60
    drawLine(
        color = Color(0xFFFFF6E6),
        start = on(centre, faceR * 0.12f, minuteHand + half),
        end = on(centre, faceR * 0.76f, minuteHand),
        strokeWidth = faceR * 0.05f,
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
    val at = on(centre, radius, minute)
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
