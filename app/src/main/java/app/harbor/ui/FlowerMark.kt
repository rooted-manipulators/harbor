package app.harbor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import app.harbor.domain.FlowerKind
import app.harbor.domain.FlowerSpec
import app.harbor.domain.Flowers

/**
 * Draws a flower, the way the specimen sheet draws one -- because it is the
 * sheet, traced.
 *
 * The outlines come from [FlowerArt], which is generated from the vectorised
 * plate. They are Bezier paths filled at draw time, so this is still drawn
 * geometry and not artwork: no bitmap, no icon font, resolution-free, exactly
 * the rule `docs/05-changing-the-ui.md` sets.
 *
 * ## Why it is traced rather than constructed
 *
 * This used to build every flower out of one petal function -- an ellipse or a
 * spear, walked around the centre -- and the twenty kinds differed only in
 * colour and petal count. The sheet's twenty are twenty different plants: a
 * bowl, a goblet, a crescent, a trumpet, a bell that hangs. A shape function
 * that covers all of them either has a case per flower, which is a worse way
 * of writing down the same outlines, or it approximates, which is what the
 * earlier passes did and why they never looked like the plate.
 *
 * ## The three things this adds to the trace
 *
 * - **A gradient down each band.** The vectoriser cut every bloom's shading
 *   into flat bands. Running a soft gradient across each band's own height
 *   ([FlowerArt.Mark.y0]/[FlowerArt.Mark.y1]) dissolves the stepping without
 *   inventing any shape.
 * - **A halo** under the head in the petal colour, and **a lit throat** over
 *   it in the heart colour -- the glow. Two soft radial washes, not a blur;
 *   Compose has no cheap blur and a gradient reads the same at this size.
 * - **A coarse pass.** Below [COARSE] a bloom is a handful of pixels in a
 *   garden that may hold hundreds, so only the bands that carry the shape are
 *   filled. See [FlowerArt.Mark.big].
 *
 * Note the bands are **opaque and painted in the plate's own order**, so
 * nothing here blends. The old multiply-versus-screen trap (multiplying
 * against a near-black ground gives near-black) does not apply to an opaque
 * fill -- but it still applies to the two glows, which is why they are
 * `SrcOver` and `Screen` and never `Multiply`.
 */
@Composable
fun FlowerMark(
    kind: FlowerKind,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val spec = Flowers.spec(kind)
    Canvas(modifier) {
        val radius = minOf(size.width, size.height) / 2f * 0.9f * scale
        translate(left = size.width / 2f, top = size.height / 2f) {
            drawFlower(spec, radius)
        }
    }
}

/**
 * Shared by the composable above, by the specimen arch, and by the garden.
 *
 * Draws from the origin, so callers translate to wherever the flower belongs.
 * The head is centred on its own box and reaches [radius] on its longer axis,
 * which is how the traced plate is normalised -- so it occupies the same space
 * the constructed flower used to.
 */
internal fun DrawScope.drawFlower(spec: FlowerSpec, radius: Float) {
    val light = Color(spec.petal)
    val heart = Color(spec.heart)

    // Under everything, in normal blend: the ground lit by the bloom.
    drawGlow(light, radius * 1.30f, 0f, 0.20f, BlendMode.SrcOver)

    val marks = plateOf(spec.kind)
    val coarse = radius < COARSE
    val k = radius / FlowerArt.SPAN
    scale(scaleX = k, scaleY = k, pivot = Offset.Zero) {
        for (m in marks) {
            if (coarse && !m.big) continue
            drawPath(m.path, m.brush)
        }
    }

    // The throat, lifted rather than repainted: the trace already put the
    // light where the sheet has it, and this only warms it.
    drawGlow(heart, radius * 0.46f, radius * 0.16f, 0.26f, BlendMode.Screen)
}

/** Below this, a bloom only gets the bands that carry its shape. */
private const val COARSE = 26f

/** A soft radial wash. The app's glow, since Compose has no cheap blur. */
private fun DrawScope.drawGlow(
    colour: Color,
    r: Float,
    cy: Float,
    alpha: Float,
    blend: BlendMode,
) {
    if (r <= 0f) return
    val centre = Offset(0f, cy)
    drawCircle(
        brush = Brush.radialGradient(
            0f to colour.copy(alpha = alpha),
            0.5f to colour.copy(alpha = alpha * 0.32f),
            1f to colour.copy(alpha = 0f),
            center = centre,
            radius = r,
        ),
        radius = r,
        center = centre,
        blendMode = blend,
    )
}

/** A traced band, parsed once: the path and the brush that fills it. */
private class Band(val path: Path, val brush: Brush, val big: Boolean)

/**
 * The parsed plate for one kind, built on first use and kept.
 *
 * Parsing 281 path strings on every frame would be absurd, and the paths never
 * change. Every caller of [drawFlower] draws inside a Compose draw scope, so
 * this is only ever touched from the main thread.
 */
private val plate = HashMap<FlowerKind, List<Band>>()

private fun plateOf(kind: FlowerKind): List<Band> = plate.getOrPut(kind) {
    FlowerArt.PLATE[kind].orEmpty().map { mark ->
        val colour = Color(mark.colour)
        // A band is flat in the trace; this rounds it back out. Kept small --
        // it is meant to hide the step between one band and the next, not to
        // become shading of its own.
        val span = maxOf(mark.y1 - mark.y0, FlowerArt.SPAN * 0.04f)
        Band(
            path = PathParser().parsePathString(mark.d).toPath(),
            brush = Brush.verticalGradient(
                0f to colour.lift(1.09f),
                1f to colour.lift(0.93f),
                startY = mark.y0,
                endY = mark.y0 + span,
            ),
            big = mark.big,
        )
    }
}

/** The same hue, brighter or deeper. Clamped, so a pale band cannot blow out. */
private fun Color.lift(by: Float): Color = Color(
    red = (red * by).coerceIn(0f, 1f),
    green = (green * by).coerceIn(0f, 1f),
    blue = (blue * by).coerceIn(0f, 1f),
    alpha = alpha,
)
