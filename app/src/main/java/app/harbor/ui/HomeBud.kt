package app.harbor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.harbor.domain.FlowerKind
import app.harbor.domain.Flowers
import app.harbor.ui.theme.Forest
import app.harbor.ui.theme.LeafLight
import app.harbor.ui.theme.Stem
import kotlin.math.floor
import kotlin.math.sin

/**
 * One plant on a patch of ground, at the top of home.
 *
 * The island is the whole garden and answers "how has it gone". This answers
 * the other question, which home never had a place for: *what happened just
 * now*. It is one bud, close enough to touch, on a mound of grass — and after
 * a call it is the flower that was chosen for that call, opening where the bud
 * stood.
 *
 * ## Why the bud matters more than the bloom
 *
 * An empty garden is a true picture of somebody's first day and a discouraging
 * one: a meadow with nothing in it reads as a screen that failed to load.
 * A single closed bud reads as a thing that is about to happen. It is the same
 * information — nothing has grown yet — told as a beginning rather than as an
 * absence, and it costs one drawing.
 *
 * ## The bud is drawn, the bloom is the artwork
 *
 * A bud has no artwork, because the plate has twenty open flowers and no shut
 * ones, and it should not: what it looks like before it opens must not give
 * away which of the twenty it becomes. So the closed state is geometry in the
 * ground's own greens, and [open] crossfades it into the real bloom.
 */
@Composable
fun HomeBud(
    /** The flower this has become, or null while it is still a bud. */
    kind: FlowerKind?,
    /** Nought is closed, one is the bloom at full size. */
    open: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val t = open.coerceIn(0f, 1f)
        val head = maxHeight * 0.44f
        val neck = maxHeight * (0.40f - 0.03f * t)

        Canvas(Modifier.fillMaxSize()) {
            drawMound()
            drawStalk(t)
            // The bud goes out as the bloom comes in, and a little faster, so
            // there is no frame where the two are both half-there and the head
            // reads as a smudge.
            if (t < 1f) {
                drawBud(
                    centre = Offset(size.width * 0.5f, size.height * (0.40f - 0.03f * t)),
                    radius = size.height * 0.085f,
                    alpha = (1f - t * 1.6f).coerceIn(0f, 1f),
                )
            }
        }

        if (kind != null && t > 0f) {
            Image(
                painter = painterResource(bloomOf(kind)),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = neck - head / 2f)
                    .size(head)
                    .alpha(t),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** How much of the box the ground takes, from the bottom. */
private const val GROUND = 0.30f

/**
 * The patch it stands on: a low mound with grass along its back.
 *
 * Drawn rather than the field's own terrain because this is one plant seen
 * from the side, and the field is thousands seen from above — there is no
 * camera that is both.
 */
private fun DrawScope.drawMound() {
    val w = size.width
    val h = size.height
    val top = h * (1f - GROUND)

    val hill = Path().apply {
        moveTo(w * 0.06f, h)
        cubicTo(w * 0.10f, top + h * 0.02f, w * 0.30f, top, w * 0.50f, top)
        cubicTo(w * 0.70f, top, w * 0.90f, top + h * 0.02f, w * 0.94f, h)
        close()
    }
    drawPath(
        hill,
        Brush.verticalGradient(
            0f to LeafLight.copy(alpha = 0.22f),
            1f to Stem.copy(alpha = 0.10f),
            startY = top,
            endY = h,
        ),
    )

    // Grass along the crest. Deterministic, so the patch does not shimmer
    // between frames the way a running random would -- the same rule the
    // field's own tufts follow.
    val blades = 34
    for (i in 0 until blades) {
        val t = i / (blades - 1f)
        val x = w * (0.08f + 0.84f * t)
        // The crest is a shallow arc, so a blade has to start on it rather
        // than on a straight line, or the grass detaches at the shoulders.
        val lift = (1f - ((t - 0.5f) * 2f) * ((t - 0.5f) * 2f)) * h * 0.018f
        val root = top - lift + h * 0.012f
        val a = wisp(i, 1)
        val c = wisp(i, 2)
        val tall = h * (0.022f + a * 0.030f)
        drawLine(
            color = if (c > 0.6f) LeafLight.copy(alpha = 0.5f) else Forest.copy(alpha = 0.45f),
            start = Offset(x, root),
            end = Offset(x + (c - 0.5f) * tall * 0.8f, root - tall),
            strokeWidth = w * 0.006f,
            cap = StrokeCap.Round,
        )
    }
}

/** Stem and two leaves, rising out of the mound to meet the head. */
private fun DrawScope.drawStalk(open: Float) {
    val w = size.width
    val h = size.height
    val foot = h * (1f - GROUND) + h * 0.02f
    val cx = w * 0.5f
    // The head hangs lower as it opens, the way a flower's weight settles it.
    val neck = h * (0.40f - 0.03f * open.coerceIn(0f, 1f))

    drawPath(
        Path().apply {
            moveTo(cx, foot)
            cubicTo(cx - w * 0.02f, foot - h * 0.10f, cx + w * 0.015f, neck + h * 0.08f, cx, neck)
        },
        color = Stem,
        style = Stroke(width = w * 0.022f, cap = StrokeCap.Round),
    )

    drawLeaf(cx, foot - h * 0.07f, -1f, w * 0.20f, Forest)
    drawLeaf(cx, foot - h * 0.14f, 1f, w * 0.17f, LeafLight.copy(alpha = 0.85f))
}

/** A leaf: out from the stem, and back to it. */
private fun DrawScope.drawLeaf(x: Float, y: Float, dir: Float, len: Float, colour: Color) {
    drawPath(
        Path().apply {
            moveTo(x, y)
            cubicTo(
                x + dir * len * 0.50f, y - len * 0.45f,
                x + dir * len * 0.95f, y - len * 0.30f,
                x + dir * len, y - len * 0.02f,
            )
            cubicTo(
                x + dir * len * 0.62f, y + len * 0.18f,
                x + dir * len * 0.22f, y + len * 0.14f,
                x, y,
            )
        },
        color = colour,
    )
}

/**
 * The closed bud, in the ground's greens with a hint of what is inside.
 *
 * Deliberately not the flower's own colour. Which of the twenty it becomes is
 * decided after the call, and a bud that already knew would be telling the
 * user what they are about to pick.
 */
internal fun DrawScope.drawBud(centre: Offset, radius: Float, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val body = Path().apply {
        moveTo(centre.x, centre.y - radius)
        cubicTo(
            centre.x + radius * 0.78f, centre.y - radius * 0.42f,
            centre.x + radius * 0.62f, centre.y + radius * 0.72f,
            centre.x, centre.y + radius * 0.88f,
        )
        cubicTo(
            centre.x - radius * 0.62f, centre.y + radius * 0.72f,
            centre.x - radius * 0.78f, centre.y - radius * 0.42f,
            centre.x, centre.y - radius,
        )
        close()
    }
    drawPath(
        body,
        Brush.verticalGradient(
            0f to LeafLight,
            1f to Stem,
            startY = centre.y - radius,
            endY = centre.y + radius,
        ),
        alpha = alpha,
    )
    // The calyx, holding it shut.
    drawPath(
        Path().apply {
            moveTo(centre.x, centre.y + radius * 0.92f)
            cubicTo(
                centre.x - radius * 0.52f, centre.y + radius * 0.50f,
                centre.x - radius * 0.40f, centre.y - radius * 0.18f,
                centre.x, centre.y - radius * 0.06f,
            )
            cubicTo(
                centre.x + radius * 0.40f, centre.y - radius * 0.18f,
                centre.x + radius * 0.52f, centre.y + radius * 0.50f,
                centre.x, centre.y + radius * 0.92f,
            )
            close()
        },
        color = Forest,
        alpha = alpha * 0.85f,
    )
}

/** The same deterministic scatter the field's grass uses. */
private fun wisp(i: Int, n: Int): Float {
    val v = sin((i + 1.0) * 127.1 + n * 311.7) * 43758.5453
    return (v - floor(v)).toFloat()
}

/** A flower's colour, for anything around the bud that wants a hint of it. */
internal fun tintOf(kind: FlowerKind): Color = Color(Flowers.spec(kind).petal)
