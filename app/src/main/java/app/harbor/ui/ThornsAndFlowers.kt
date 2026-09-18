package app.harbor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.domain.BlockKind
import kotlin.math.cos
import kotlin.math.sin

/**
 * The two things you put on your week.
 *
 * A **thorn** is time you are busy: spiked, dark, obviously something you
 * would not want to be interrupted in the middle of. A **flower** is time you
 * would welcome a call. Both are drawn rather than shipped as assets, for the
 * same reason the garden's flowers are — they have to size to whatever slot
 * they land in, and a PNG stretched to four hours of a Tuesday looks like a
 * mistake.
 *
 * The colours are measured off the Figma frames, not guessed at. The one
 * liberty taken is that spikes and petal heads are allowed to overflow their
 * block: a thorn whose spikes were clipped to its own time span would read as
 * a rectangle with serrations, which is not the same drawing at all.
 */

// --- measured off the frames ----------------------------------------------

internal val ThornEdge = Color(0xFF4A6743)
internal val ThornDeep = Color(0xFF26462C)
internal val ThornLit = Color(0xFF4C6C34)
internal val SpikeTop = Color(0xFF4E6A50)
internal val SpikeFoot = Color(0xFF33502F)

internal val PetalLight = Color(0xFFFFCA8E)
internal val PetalDeep = Color(0xFFFFB987)
internal val ThroatTop = Color(0xFFFF5151)
internal val ThroatFoot = Color(0xFFFF6158)
internal val StemTop = Color(0xFFFF9778)
internal val StemFoot = Color(0xFFFF6E68)

/**
 * A thorn filling [body], with its spikes outside it.
 *
 * [body] is the block's own time span, so the top of the drawn body is exactly
 * the hour it starts. The spikes are extra.
 */
internal fun DrawScope.drawThorn(body: Rect) {
    // A spike is a size, not a fraction.
    //
    // This was `width * 0.17f` capped at nine *pixels*, which on a block one
    // seventh of a phone wide came out about right, and on a card-wide day
    // comes out at about three points: the thorn loses its silhouette and
    // reads as a green slab. Both ends of the range are real measurements now.
    val spike = (body.width * 0.17f).coerceIn(2.5f, SPIKE.toPx())
    val left = body.left + spike
    val right = body.right - spike
    if (right <= left) return
    val width = right - left

    // Light at the top right, dark through the middle, light again at the
    // bottom left: a sweep across the diagonal rather than down the face,
    // which is what stops a tall thorn reading as a flat bar.
    val skin = Brush.linearGradient(
        colors = listOf(ThornEdge, ThornDeep, ThornLit),
        start = Offset(right, body.top),
        end = Offset(left, body.bottom),
    )
    val bristle = Brush.verticalGradient(
        colors = listOf(SpikeTop, SpikeFoot),
        startY = body.top,
        endY = body.bottom,
    )

    val path = Path()

    // Down each long edge, one row offset half a step from the other so the
    // silhouette does not come out symmetrical.
    // The pitch follows the spike rather than the block's width, so a wide
    // block grows more of them instead of four enormous ones.
    val step = (spike * 2.4f).coerceAtLeast(6f)
    val rows = (body.height / step).toInt().coerceAtLeast(1)
    val pitch = body.height / rows
    val halfBase = (pitch * 0.32f).coerceAtMost(width * 0.30f)
    for (i in 0 until rows) {
        val y = body.top + pitch * (i + 0.5f)
        path.spikeAt(left, y, -spike, halfBase, vertical = true)
        val mirrored = y + pitch * 0.5f
        if (mirrored < body.bottom) {
            path.spikeAt(right, mirrored, spike, halfBase, vertical = true)
        }
    }

    // As many across the cap and the foot as fit. It was three of each, which
    // on a card-wide thorn left two hand-spans of bare edge between them.
    val across = (width / (spike * 2.6f)).toInt().coerceIn(3, 16)
    val acrossHalf = (width / across * 0.34f)
    for (i in 0 until across) {
        val x = left + width * (i + 0.5f) / across
        path.spikeAt(body.top, x, -spike, acrossHalf, vertical = false)
        path.spikeAt(body.bottom, x, spike, acrossHalf, vertical = false)
    }

    drawPath(path, bristle)
    drawRoundRect(
        brush = skin,
        topLeft = Offset(left, body.top),
        size = Size(width, body.height),
        cornerRadius = CornerRadius(minOf(width * 0.26f, CORNER.toPx())),
    )
}

/** How far a spike reaches out of the body, at the most. */
private val SPIKE = 5.dp

/** How round the body's corners get, at the most. */
private val CORNER = 10.dp

/**
 * One triangle, pointing out of an edge by [reach].
 *
 * [along] runs down the edge for a vertical side and across it for a
 * horizontal one; [edge] is the edge's own coordinate.
 */
private fun Path.spikeAt(
    edge: Float,
    along: Float,
    reach: Float,
    halfBase: Float,
    vertical: Boolean,
) {
    if (vertical) {
        moveTo(edge, along - halfBase)
        lineTo(edge + reach, along)
        lineTo(edge, along + halfBase)
    } else {
        moveTo(along - halfBase, edge)
        lineTo(along, edge + reach)
        lineTo(along + halfBase, edge)
    }
    close()
}

/**
 * A flower filling [body]: blooms along the top, and a bed down the rest.
 *
 * A bloom is a fixed size, so half an hour and four hours grow the same
 * flower and only the bed gets longer. It is allowed to sit slightly proud of
 * the block's top edge, exactly as the frames draw it.
 *
 * The size used to be `width * 0.46f`, which was right while a block was one
 * seventh of a phone wide and became absurd when the week became a day and a
 * block got the whole card: a single head scaled to 250dp swallowed six hours
 * of the morning either side of it. The intent was always that duration
 * changes the stem and nothing else, so the cap now says that outright — and
 * a block too wide for one bloom grows a row of them, which is how the frames
 * draw a card-wide flower and what a bed of them actually looks like.
 */
internal fun DrawScope.drawFlowerBlock(body: Rect) {
    val headR = minOf(body.width * 0.46f, BLOOM.toPx()).coerceAtLeast(3f)
    val cy = body.top + headR * 0.74f

    // Bed first: the petals overlap its shoulders, which is what gives the
    // heads somewhere to sit rather than something to float above.
    val stemLeft = body.left + body.width * 0.09f
    val stemRight = body.right - body.width * 0.09f
    if (body.bottom > cy && stemRight > stemLeft) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(StemTop, StemFoot),
                startY = cy,
                endY = body.bottom,
            ),
            topLeft = Offset(stemLeft, cy),
            size = Size(stemRight - stemLeft, body.bottom - cy),
            cornerRadius = CornerRadius(minOf(body.width * 0.22f, headR * 0.7f)),
        )
    }

    val count = (body.width / (headR * 2.1f)).toInt().coerceIn(1, 8)
    val step = body.width / count
    for (i in 0 until count) {
        drawBloomHead(Offset(body.left + step * (i + 0.5f), cy), headR)
    }
}

/** How big a bloom on the week is, whatever the block under it. */
private val BLOOM = 15.dp

/**
 * The head on its own: six lobes, a middle, and a throat across it.
 *
 * Split out of [drawFlowerBlock] when the day dial needed the same bloom
 * without a block to hang it on -- the reminder flower rides an arc rather
 * than filling an hour, so it has a centre and a radius and no rectangle
 * anywhere. One drawing in one place, so the flower you drag round a clock is
 * recognisably the flower you plant on a week.
 */
internal fun DrawScope.drawBloomHead(center: Offset, radius: Float) {
    val cx = center.x
    val cy = center.y
    val petals = Brush.verticalGradient(
        colors = listOf(PetalLight, PetalDeep),
        startY = cy - radius,
        endY = cy + radius,
    )
    val lobe = radius * 0.44f
    val orbit = radius * 0.56f
    for (i in 0 until 6) {
        val angle = Math.toRadians(i * 60.0 - 90.0)
        drawCircle(
            brush = petals,
            radius = lobe,
            center = Offset(
                cx + (orbit * cos(angle)).toFloat(),
                cy + (orbit * sin(angle)).toFloat(),
            ),
        )
    }
    drawCircle(brush = petals, radius = radius * 0.62f, center = Offset(cx, cy))

    val throatW = radius * 1.18f
    val throatH = radius * 0.80f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(ThroatTop, ThroatFoot),
            startY = cy - throatH / 2f,
            endY = cy + throatH / 2f,
        ),
        topLeft = Offset(cx - throatW / 2f, cy - throatH / 2f),
        size = Size(throatW, throatH),
        cornerRadius = CornerRadius(throatH * 0.36f),
    )
}

/**
 * A hand, small, over the corner of something you can pick up.
 *
 * The palette reads as a pair of radio buttons -- tap one, it lights up, tap
 * the other -- and nothing about it says the thing you tapped can also be
 * carried onto the week. Usability testing had people choose a kind and then
 * hunt the grid for somewhere to press, never once trying to drag from here.
 *
 * Drawn rather than an icon, like every other mark in the app, and drawn as a
 * hand rather than the usual six-dot grip because a grip means "reorder this
 * list" to anyone who has met one before, and this is not a list.
 */
internal fun DrawScope.drawGrabHand(body: Rect, ink: Color) {
    val w = body.width
    val h = body.height
    val finger = w * 0.135f

    drawRoundRect(
        color = ink,
        topLeft = Offset(body.left + w * 0.20f, body.top + h * 0.46f),
        size = Size(w * 0.62f, h * 0.50f),
        cornerRadius = CornerRadius(w * 0.17f),
    )
    // Three fingers, not four, and the middle one longest. Four at this size
    // closes up into a single block with a bite out of the top; three keeps a
    // gap you can still see at fourteen points, which is all this is drawn at.
    val tops = floatArrayOf(0.16f, 0.06f, 0.14f)
    for (i in 0 until 3) {
        val x = body.left + w * (0.255f + i * 0.195f)
        val top = body.top + h * tops[i]
        drawRoundRect(
            color = ink,
            topLeft = Offset(x, top),
            size = Size(finger, body.top + h * 0.62f - top),
            cornerRadius = CornerRadius(finger * 0.5f),
        )
    }
    // The thumb, out to the side and lower. It is what stops the shape reading
    // as a fork.
    rotate(degrees = -24f, pivot = Offset(body.left + w * 0.22f, body.top + h * 0.60f)) {
        drawRoundRect(
            color = ink,
            topLeft = Offset(body.left + w * 0.06f, body.top + h * 0.60f),
            size = Size(finger, h * 0.30f),
            cornerRadius = CornerRadius(finger * 0.5f),
        )
    }
}

/**
 * What you are about to plant.
 *
 * The frames show a hand cursor resting on the thorn to say which one is
 * picked up. There is no cursor on a phone, so the chosen one takes a box
 * around it instead -- a soft tile and a rim, exactly as the frames draw the
 * selected chip -- and nothing is chosen until somebody says so. Pressing the
 * lit one again puts it down, because a palette you cannot leave is a mode.
 *
 * Laid out as a row, glyph then two lines of label, which is the shape the
 * frames have: "Place thorns" over "(Busy)".
 */
@Composable
internal fun PaletteChip(
    kind: BlockKind,
    label: String,
    sub: String,
    selected: Boolean,
    tile: Color,
    line: Color,
    ink: Color,
    muted: Color,
    modifier: Modifier = Modifier,
    /**
     * Where the finger is, in root coordinates, while one is being carried off
     * the palette. Null means this chip is only a chip.
     */
    onCarry: ((Offset) -> Unit)? = null,
    /** Let go. The offset is where, in root coordinates. */
    onDrop: ((Offset) -> Unit)? = null,
    onClick: () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var at by remember { mutableStateOf(Offset.Zero) }
    Row(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(ChipShape)
            .background(if (selected) tile else Color.Transparent)
            .border(1.dp, if (selected) line else Color.Transparent, ChipShape)
            .clickable(onClick = onClick)
            .then(
                if (onCarry == null) Modifier else Modifier.pointerInput(kind) {
                    detectDragGestures(
                        // Picking one up chooses it too. Dragging the kind you
                        // had not selected and having it land as the other one
                        // is the sort of thing nobody reports and everybody
                        // works around.
                        onDragStart = { start ->
                            if (!selected) onClick()
                            at = origin + start
                            onCarry(at)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            at = origin + change.position
                            onCarry(at)
                        },
                        onDragEnd = { onDrop?.invoke(at) },
                        onDragCancel = { onDrop?.invoke(Offset.Unspecified) },
                    )
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 46.dp, height = 50.dp)) {
            val body = Rect(
                left = size.width * 0.14f,
                top = size.height * 0.10f,
                right = size.width * 0.86f,
                bottom = size.height * 0.90f,
            )
            when (kind) {
                BlockKind.BUSY -> drawThorn(body)
                BlockKind.FREE -> drawFlowerBlock(body)
            }
            if (onCarry != null) {
                // Over the head of the thorn or the bud, where the eye already
                // is, rather than tucked in a corner it would have to find.
                val mark = Size(size.width * 0.34f, size.width * 0.34f)
                drawGrabHand(
                    Rect(
                        offset = Offset(size.width * 0.62f, 0f),
                        size = mark,
                    ),
                    ink.copy(alpha = 0.62f),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp, color = ink),
            )
            Text(
                sub,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp, color = muted),
            )
        }
    }
}

/** The box the frames draw around the chip you have picked up. */
private val ChipShape = RoundedCornerShape(16.dp)

@Composable
internal fun BlockGlyph(kind: BlockKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val body = Rect(0f, 0f, size.width, size.height)
        when (kind) {
            BlockKind.BUSY -> drawThorn(body)
            BlockKind.FREE -> drawFlowerBlock(body)
        }
    }
}
