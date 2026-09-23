package app.harbor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import app.harbor.R
import app.harbor.domain.TourStop
import app.harbor.ui.theme.Gold
import app.harbor.ui.theme.Ink
import app.harbor.ui.theme.LocalReducedMotion
import app.harbor.ui.theme.Motion
import app.harbor.ui.theme.Space
import app.harbor.ui.theme.emberFill
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The bee that walks somebody through the app after onboarding. ADR-016.
 *
 * The eight poses the tour draws on. A different set from [BeeMood]'s on
 * purpose -- those are how a call felt; these are a guide's gestures.
 *
 * ## Placeholder pending the real art
 *
 * All eight currently point at `bee_standing`, copied eight times under
 * these names. When the drawn poses arrive, cut them straight onto these
 * eight filenames (the way `tools/cut_moods.py` cut the mood sheet) and
 * nothing here has to change -- the seam is the asset, not the code.
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

/** What the bee says at a stop, and which pose says it. One sentence or two, in its own voice. */
private data class TourLine(val text: String, val pose: TourBeeState)

private fun lineFor(stop: TourStop): TourLine = when (stop) {
    TourStop.WELCOME ->
        TourLine("Hi! This is your garden. Every call you make grows something here.", TourBeeState.GREET)
    TourStop.HOME_PEOPLE ->
        TourLine("Here's your person. Tap to see more, or call them right from here.", TourBeeState.POINT)
    TourStop.HOME_ADD ->
        TourLine("Want to stay close to someone else too? Add them here.", TourBeeState.IDEA)
    TourStop.GARDEN_FIELD ->
        TourLine("Every flower here is a call you made. Pinch to look closer.", TourBeeState.EXPLAIN)
    TourStop.GARDEN_FLOWER ->
        TourLine("Tap any flower to see when you called and how it felt.", TourBeeState.POINT)
    TourStop.GARDEN_BEE ->
        TourLine("Zoom right in and you'll find me, tending the garden.", TourBeeState.SURPRISE)
    // Your day, not theirs: Harbor holds one week, and the dial is it
    // (CLAUDE.md). Calling it their day would be a claim the app cannot back.
    TourStop.PERSON_DIAL ->
        TourLine("This ring is your day. Thorns are busy hours, blooms are hours you keep free.", TourBeeState.EXPLAIN)
    TourStop.PERSON_PLANT ->
        TourLine("Press a bloom and I'll hold that time for a call.", TourBeeState.APPROVE)
    TourStop.SCHEDULE_VIEWS ->
        TourLine("See your whole week, or switch to a single day.", TourBeeState.EXPLAIN)
    TourStop.SCHEDULE_PALETTE ->
        TourLine("Thorns for busy, flowers for free. Pick one, then tap your week.", TourBeeState.POINT)
    TourStop.SCHEDULE_COPY ->
        TourLine("One day just like the last? Copy it across in a tap.", TourBeeState.IDEA)
    TourStop.SCHEDULE_CALENDAR ->
        TourLine("Or bring your week straight in from your calendar.", TourBeeState.POINT)
    TourStop.SCHEDULE_QUIET ->
        TourLine("Set quiet hours once and I'll stay out of them, every day.", TourBeeState.WONDER)
    TourStop.ACCOUNT_REMINDERS ->
        TourLine("Reminders start here. They stay off until you turn them on.", TourBeeState.EXPLAIN)
    TourStop.ACCOUNT_DONE ->
        TourLine("That's the whole garden. Happy growing!", TourBeeState.CELEBRATE)
}

// --- the look --------------------------------------------------------------

/** Near-black at about 80%: dark enough that the one lit thing is obviously the point. */
private val Scrim = Color(0xCC05070A)

/** Cream, like paper held up to the light -- a speech bubble is the one light thing here. */
private val BubbleFill = Color(0xFFFFF6E6)

private val MASCOT = 132.dp
private val EDGE = Space.two
private val GAP = Space.oneHalf
private val HOLE_PAD = Space.one
private val HOLE_CORNER = 20.dp
private val BUBBLE_MAX = 300.dp
private val BUBBLE_CORNER = 20.dp
private val TAIL = 12.dp

/** A character a beat. Fast enough not to wait on, slow enough to read as speech. */
private const val TYPE_MS = 22L

private class Ref<T>(var value: T)

/** Where the mascot and the bubble go for one hole on one screen. Pixels, overlay coordinates. */
private data class Staging(
    val mascot: Offset,
    val mirrored: Boolean,
    val bubble: Offset,
    val tailOnTop: Boolean,
    val tailX: Float,
)

/**
 * Put the mascot in the first corner the spotlight leaves free, and the
 * bubble beside the spotlight on whichever side has room and does not land
 * on the mascot.
 *
 * With nothing to point at, the bubble sits over the mascot instead.
 */
private fun stage(
    hole: Rect?,
    screen: Size,
    bubble: Size,
    mascot: Float,
    insetTop: Float,
    insetBottom: Float,
    edge: Float,
    gap: Float,
    tailInset: Float,
): Staging {
    val safe = Rect(edge, insetTop + edge, screen.width - edge, screen.height - insetBottom - edge)
    fun box(o: Offset) = Rect(o, Size(mascot, mascot))

    val corners = listOf(
        Offset(safe.left, safe.bottom - mascot),
        Offset(safe.right - mascot, safe.bottom - mascot),
        Offset(safe.right - mascot, safe.top),
        Offset(safe.left, safe.top),
    )
    val avoid = hole?.inflate(gap)
    val m = box(corners.firstOrNull { avoid == null || !box(it).overlaps(avoid) } ?: corners.first())

    val bw = bubble.width
    val bh = bubble.height
    fun fitX(x: Float) = x.coerceIn(safe.left, (safe.right - bw).coerceAtLeast(safe.left))
    fun fitY(y: Float) = y.coerceIn(safe.top, (safe.bottom - bh).coerceAtLeast(safe.top))

    val candidates: List<Offset> = if (hole == null) {
        val x = fitX(if (m.center.x < screen.width / 2) safe.left else safe.right - bw)
        listOf(Offset(x, m.top - gap - bh), Offset(x, m.bottom + gap))
    } else {
        val x = fitX(hole.center.x - bw / 2)
        val below = Offset(x, hole.bottom + gap)
        val above = Offset(x, hole.top - gap - bh)
        if (hole.center.y < screen.height / 2) listOf(below, above) else listOf(above, below)
    }
    fun fits(o: Offset) = o.y >= safe.top && o.y + bh <= safe.bottom
    fun clearOf(o: Offset) = !Rect(o, Size(bw, bh)).overlaps(m)
    val chosen = candidates.firstOrNull { fits(it) && clearOf(it) }
        ?: candidates.firstOrNull { fits(it) }
        ?: candidates.first()
    val b = Offset(chosen.x, fitY(chosen.y))

    return Staging(
        mascot = m.topLeft,
        mirrored = m.center.x > screen.width / 2,
        bubble = b,
        tailOnTop = m.center.y < b.y + bh / 2,
        tailX = (m.center.x - b.x).coerceIn(tailInset, (bw - tailInset).coerceAtLeast(tailInset)),
    )
}

/**
 * The tour, as a spotlight: the app under a dark veil with one thing lit,
 * the bee large in a corner, and what it is saying beside the lit thing.
 *
 * Drawn once at the root, over every screen the tour walks through (see
 * `MainActivity`), and it takes the touches -- a tap anywhere finishes the
 * sentence the bee is saying, and a second one moves on. `Skip` is always
 * there too: a tour that cannot be left at every stop is the one thing this
 * app does not build (CLAUDE.md).
 *
 * ## Why it stays smooth
 *
 * Everything that moves is read in a draw or layer lambda, never in
 * composition, so a frame of animation is a redraw rather than a recompose:
 * the hole, the ring's pulse, the mascot's bob and the bubble's glide. The
 * veil is its own offscreen layer and only redraws when the hole moves; the
 * pulsing ring is a separate, cheap layer on top. The one thing that does
 * recompose per frame is the few characters of the sentence being typed,
 * and only its own small scope.
 */
@Composable
fun TourOverlay(
    stop: TourStop,
    index: Int,
    count: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchors = LocalTourAnchors.current
    val still = LocalReducedMotion.current
    val density = LocalDensity.current
    val line = lineFor(stop)

    val origin = remember { Ref(Offset.Zero) }
    var screen by remember { mutableStateOf(Size.Zero) }
    var bubbleSize by remember { mutableStateOf(Size.Zero) }

    // Where the light wants to be. Polled from the plain registry once a
    // frame while the tour is up, rather than every tagged element pushing
    // snapshot state on every scroll whether a tour is running or not.
    var target by remember { mutableStateOf<TourAnchors.Spot?>(null) }
    LaunchedEffect(stop) {
        val arrived = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                val spot = anchors.spots[stop]?.takeIf { it.rect.width > 1f && it.rect.height > 1f }
                val local = spot?.let { TourAnchors.Spot(it.rect.translate(-origin.value), it.round) }
                // Hold the old light for a moment while the next screen
                // fades in, so it glides across rather than blinking out.
                val next = local ?: if (now - arrived > 900) null else target
                if (next?.rect != target?.rect || next?.round != target?.round) target = next
            }
        }
    }

    // --- the hole ----------------------------------------------------------

    val holePad = with(density) { HOLE_PAD.toPx() }
    val hole = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val open = remember { Animatable(0f) }
    val roundness = remember { Animatable(0f) }
    LaunchedEffect(target) {
        val t = target
        if (t == null) {
            open.animateTo(0f, if (still) snap() else tween(Motion.NORMAL, easing = Motion.Standard))
            return@LaunchedEffect
        }
        val r = t.rect.inflate(holePad)
        if (still || open.value < 0.05f) hole.snapTo(r)
        coroutineScope {
            launch { if (still) hole.snapTo(r) else hole.animateTo(r, spring(0.9f, Spring.StiffnessMediumLow)) }
            launch { roundness.animateTo(if (t.round) 1f else 0f, if (still) snap() else tween(Motion.NORMAL)) }
            launch { open.animateTo(1f, if (still) snap() else spring(0.72f, Spring.StiffnessLow)) }
        }
    }

    // --- the staging -------------------------------------------------------

    val mascotPx = with(density) { MASCOT.toPx() }
    val edgePx = with(density) { EDGE.toPx() }
    val gapPx = with(density) { (GAP + TAIL).toPx() }
    val tailInset = with(density) { (BUBBLE_CORNER + TAIL).toPx() }
    val insetTop = WindowInsets.statusBars.getTop(density).toFloat()
    val insetBottom = WindowInsets.navigationBars.getBottom(density).toFloat()

    val mascotAt = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val bubbleAt = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    var mirrored by remember { mutableStateOf(false) }
    var tailOnTop by remember { mutableStateOf(false) }
    var tailX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target, screen, bubbleSize) {
        if (screen == Size.Zero || bubbleSize == Size.Zero) return@LaunchedEffect
        val s = stage(
            target?.rect?.inflate(holePad), screen, bubbleSize, mascotPx,
            insetTop, insetBottom, edgePx, gapPx, tailInset,
        )
        mirrored = s.mirrored
        tailOnTop = s.tailOnTop
        tailX = s.tailX
        if (!placed || still) {
            mascotAt.snapTo(s.mascot)
            bubbleAt.snapTo(s.bubble)
            placed = true
            return@LaunchedEffect
        }
        val glide = spring<Offset>(0.82f, Spring.StiffnessLow)
        coroutineScope {
            launch { mascotAt.animateTo(s.mascot, glide) }
            launch { bubbleAt.animateTo(s.bubble, glide) }
        }
    }

    // --- the speech --------------------------------------------------------

    var typed by remember(stop) { mutableIntStateOf(if (still) line.text.length else 0) }
    LaunchedEffect(stop) {
        delay(160)
        while (typed < line.text.length) {
            delay(TYPE_MS)
            typed++
        }
    }
    // A hop and a pop each time the bee starts a new line.
    val pop = remember { Animatable(0f) }
    LaunchedEffect(stop) {
        if (still) pop.snapTo(1f) else {
            pop.snapTo(0f)
            pop.animateTo(1f, spring(0.55f, Spring.StiffnessMediumLow))
        }
    }
    // First tap finishes the sentence; the next moves on.
    val advanceNow: () -> Unit = {
        if (typed < line.text.length) {
            typed = line.text.length
        } else {
            onNext()
        }
    }
    val advance by rememberUpdatedState(advanceNow)

    val flip by animateFloatAsState(if (mirrored) -1f else 1f, Motion.settling(), label = "flip")
    val bob: State<Float>? = if (still) null else {
        rememberInfiniteTransition(label = "tour").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "bob",
        )
    }
    val pulse: State<Float>? = if (still) null else {
        rememberInfiniteTransition(label = "ring").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1600, easing = Motion.Decelerate), RepeatMode.Restart), label = "pulse",
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { origin.value = it.positionInRoot() }
            .onSizeChanged { screen = it.toSize() }
            // Takes every touch the bubble does not: nothing underneath is
            // live while the bee is talking.
            .pointerInput(Unit) { detectTapGestures { advance() } },
    ) {
        val corner = with(density) { HOLE_CORNER.toPx() }

        // The veil, with the hole cut through it. Its own offscreen layer, so
        // Clear punches through to the app rather than to black, and so it
        // only redraws when the hole itself moves.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Scrim)
            val o = open.value
            if (o > 0.001f) {
                val r = spotlight(hole.value, o)
                drawRoundRect(
                    Color.Black, r.topLeft, r.size, radius(r, corner, roundness.value),
                    blendMode = BlendMode.Clear,
                )
            }
        }

        // The ring round the hole, and a ping that spreads from it. Separate
        // from the veil so the pulse never makes the full-screen layer redraw.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val o = open.value
            if (o <= 0.001f) return@Canvas
            val r = spotlight(hole.value, o)
            val stroke = 2.dp.toPx()
            drawRoundRect(
                Gold.copy(alpha = 0.95f * o), r.topLeft, r.size, radius(r, corner, roundness.value),
                style = Stroke(stroke),
            )
            val p = pulse?.value ?: return@Canvas
            val grow = 14.dp.toPx() * p
            val ping = r.inflate(grow)
            drawRoundRect(
                Gold.copy(alpha = 0.55f * (1f - p) * o), ping.topLeft, ping.size,
                radius(ping, corner + grow, roundness.value),
                style = Stroke(stroke * (1.5f - p)),
            )
        }

        // The bee, big, in its corner -- bobbing, turning to face the middle
        // of the screen, and rocking a little while it talks.
        Image(
            painter = painterResource(tourBeeArt(line.pose)),
            contentDescription = null,
            modifier = Modifier
                .offset { mascotAt.value.round() }
                .size(MASCOT)
                .graphicsLayer {
                    alpha = if (placed) 1f else 0f
                    val t = (bob?.value ?: 0f) * 2f * PI.toFloat()
                    val hop = 1f - pop.value
                    translationY = -6.dp.toPx() * (0.5f + 0.5f * sin(t)) - 18.dp.toPx() * hop
                    val s = 0.9f + 0.1f * pop.value
                    scaleX = s * flip
                    scaleY = s
                    val talking = typed < line.text.length
                    rotationZ = if (talking && bob != null) 3f * sin(t * 9f) else 0f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        )

        // What it says.
        val bubbleWidth = with(density) {
            val room = if (screen.width > 0f) screen.width - 2 * EDGE.toPx() else BUBBLE_MAX.toPx()
            min(BUBBLE_MAX.toPx(), room).toDp()
        }
        Column(
            Modifier
                .offset { bubbleAt.value.round() }
                .width(bubbleWidth)
                .onSizeChanged { bubbleSize = it.toSize() }
                .graphicsLayer {
                    val v = pop.value
                    alpha = if (placed) v.coerceIn(0f, 1f) else 0f
                    val s = 0.82f + 0.18f * v
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(
                        if (size.width > 0f) tailX / size.width else 0.5f,
                        if (tailOnTop) 0f else 1f,
                    )
                }
                .drawBehind { drawBubble(tailOnTop, tailX, BUBBLE_CORNER.toPx(), TAIL.toPx()) }
                .padding(horizontal = Space.two, vertical = Space.two),
        ) {
            Typed(line.text) { typed }
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
                            .clip(RoundedCornerShape(99.dp))
                            .clickable(role = Role.Button, onClick = onSkip)
                            .padding(horizontal = Space.oneHalf, vertical = Space.one),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            color = Ink.copy(alpha = 0.55f),
                        ),
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .emberFill()
                            .clickable(role = Role.Button) { advance() }
                            .padding(horizontal = Space.two, vertical = Space.one),
                    ) {
                        Text(
                            if (index == count - 1) "Got it" else "Next",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, color = Ink),
                        )
                    }
                }
            }
        }
    }
}

/** The hole, opened [o] of the way from its centre. */
private fun spotlight(r: Rect, o: Float): Rect {
    val w = r.width * o
    val h = r.height * o
    return Rect(Offset(r.center.x - w / 2, r.center.y - h / 2), Size(w, h))
}

private fun radius(r: Rect, corner: Float, round: Float): CornerRadius {
    val circle = min(r.width, r.height) / 2
    return CornerRadius(lerp(min(corner, circle), circle, round))
}

/** A rounded bubble with its tail on the side facing the bee. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBubble(
    tailOnTop: Boolean,
    tailX: Float,
    corner: Float,
    tail: Float,
) {
    // Two paths rather than one: a tail wound the other way round from the
    // rounded rect would cancel out where they overlap and leave a seam.
    drawRoundRect(BubbleFill, cornerRadius = CornerRadius(corner))
    val tailPath = Path().apply {
        val x = tailX.coerceIn(corner + tail, (size.width - corner - tail).coerceAtLeast(corner + tail))
        if (tailOnTop) {
            moveTo(x - tail, 1f)
            lineTo(x - tail * 0.2f, -tail)
            lineTo(x + tail, 1f)
        } else {
            moveTo(x - tail, size.height - 1f)
            lineTo(x - tail * 0.2f, size.height + tail)
            lineTo(x + tail, size.height - 1f)
        }
        close()
    }
    drawPath(tailPath, BubbleFill)
}

/**
 * The sentence, [shown] characters of it so far.
 *
 * The rest is there but transparent, so the bubble is its full size from
 * the first letter and does not grow under the reader's eye. Its own scope,
 * so the typing recomposes a Text and nothing else.
 */
@Composable
private fun Typed(text: String, shown: () -> Int) {
    val n = shown().coerceIn(0, text.length)
    Text(
        buildAnnotatedString {
            append(text.substring(0, n))
            withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(n)) }
        },
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp, color = Ink),
    )
}

/** One tick per stop, lit up to and including the current one. */
@Composable
private fun Dots(count: Int, at: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == at) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (i <= at) Gold else Ink.copy(alpha = 0.18f)),
            )
        }
    }
}

private fun Offset.round(): IntOffset = IntOffset(x.roundToInt(), y.roundToInt())
