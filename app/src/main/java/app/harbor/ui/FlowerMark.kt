package app.harbor.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.harbor.R
import app.harbor.domain.FlowerKind
import app.harbor.domain.FlowerSpec

/**
 * A flower, as the artwork draws it.
 *
 * Twenty flowers, shipped as the illustrator's own files rather than
 * reconstructed. Two cuts of each live in `res/drawable-nodpi`: the whole
 * plant, and the bloom on its own. `tools/cut_flowers.py` makes both from the
 * twenty sources in `tools/flower-source`, and is the only thing that should
 * ever write them.
 *
 * This is a **reversal** of the rule in `docs/05-changing-the-ui.md` that every
 * mark in the app is drawn geometry. Three passes went into drawing these --
 * one shape function, then a per-flower one, then the vectorised plate traced
 * into Bezier paths -- and each was a recognisable flower that was not *this*
 * flower. The artwork has soft light inside the petals that a fill cannot
 * reach, and matching it was never going to happen by hand. What the reversal
 * costs is real and worth knowing:
 *
 * - The flowers no longer restyle with the palette. They are fixed pictures.
 *   `Flowers.petal`/`petalDeep`/`heart` are still the flower's colour for
 *   anything that needs one, and still come off the same sheet.
 * - About 1MB of APK, and heap while they are on screen: a decoded bitmap is
 *   width x height x 4 bytes, so the 448px blooms are roughly half a megabyte
 *   each. A person's ledger showing a dozen different kinds at once is the
 *   worst case and holds a few megabytes.
 * - They are pixels, so they do not scale past their own size. The cuts are
 *   sized for the largest place each is used -- see the constants in the
 *   cutter -- and going bigger than that will go soft.
 *
 * The two top-down views are deliberately **not** artwork: see [drawFlowerDot].
 */
@Composable
fun FlowerMark(
    kind: FlowerKind,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    Image(
        painter = painterResource(bloomOf(kind)),
        contentDescription = null,
        modifier = if (scale == 1f) modifier else modifier.scale(scale),
        contentScale = ContentScale.Fit,
    )
}

/** The whole plant: bloom, stem, two leaves. For the specimen arch. */
@DrawableRes
internal fun plantOf(kind: FlowerKind): Int = when (kind) {
    FlowerKind.GLAD_WE_TALKED -> R.drawable.flower_glad_we_talked
    FlowerKind.LIGHTER_NOW -> R.drawable.flower_lighter_now
    FlowerKind.FELT_LOVED -> R.drawable.flower_felt_loved
    FlowerKind.SHE_REMEMBERED -> R.drawable.flower_she_remembered
    FlowerKind.EASY_SILENCE -> R.drawable.flower_easy_silence
    FlowerKind.STEADIER_NOW -> R.drawable.flower_steadier_now
    FlowerKind.WORTH_SLOWING_DOWN -> R.drawable.flower_worth_slowing_down
    FlowerKind.SAID_WHAT_I_MEANT -> R.drawable.flower_said_what_i_meant
    FlowerKind.WANT_TO_TRY_SOMETHING -> R.drawable.flower_want_to_try_something
    FlowerKind.ASKED_MORE_THAN_USUAL -> R.drawable.flower_asked_more_than_usual
    FlowerKind.LOOKING_FORWARD -> R.drawable.flower_looking_forward
    FlowerKind.STILL_THINKING_ABOUT_IT -> R.drawable.flower_still_thinking_about_it
    FlowerKind.HARD_TO_SHAKE_OFF -> R.drawable.flower_hard_to_shake_off
    FlowerKind.TIME_TO_ACTUALLY_DO_IT -> R.drawable.flower_time_to_actually_do_it
    FlowerKind.NOTHING_LEFT_UNSAID -> R.drawable.flower_nothing_left_unsaid
    FlowerKind.WONDERING_IF_THAT_LANDED -> R.drawable.flower_wondering_if_that_landed
    FlowerKind.SAID_THE_HARD_THING -> R.drawable.flower_said_the_hard_thing
    FlowerKind.GLAD_SHE_PICKED_UP -> R.drawable.flower_glad_she_picked_up
    FlowerKind.WISHED_IT_WAS_LONGER -> R.drawable.flower_wished_it_was_longer
    FlowerKind.DREADED_THIS_ONE -> R.drawable.flower_dreaded_this_one
}

/**
 * The bloom on its own. For every small mark.
 *
 * A 34dp ledger row, a 44dp avatar, the 46dp field chip: in a box that size a
 * whole plant leaves the actual flower about a third of the height, which is
 * smaller than the bloom is today and smaller than the thing is worth.
 */
@DrawableRes
internal fun bloomOf(kind: FlowerKind): Int = when (kind) {
    FlowerKind.GLAD_WE_TALKED -> R.drawable.flower_glad_we_talked_bloom
    FlowerKind.LIGHTER_NOW -> R.drawable.flower_lighter_now_bloom
    FlowerKind.FELT_LOVED -> R.drawable.flower_felt_loved_bloom
    FlowerKind.SHE_REMEMBERED -> R.drawable.flower_she_remembered_bloom
    FlowerKind.EASY_SILENCE -> R.drawable.flower_easy_silence_bloom
    FlowerKind.STEADIER_NOW -> R.drawable.flower_steadier_now_bloom
    FlowerKind.WORTH_SLOWING_DOWN -> R.drawable.flower_worth_slowing_down_bloom
    FlowerKind.SAID_WHAT_I_MEANT -> R.drawable.flower_said_what_i_meant_bloom
    FlowerKind.WANT_TO_TRY_SOMETHING -> R.drawable.flower_want_to_try_something_bloom
    FlowerKind.ASKED_MORE_THAN_USUAL -> R.drawable.flower_asked_more_than_usual_bloom
    FlowerKind.LOOKING_FORWARD -> R.drawable.flower_looking_forward_bloom
    FlowerKind.STILL_THINKING_ABOUT_IT -> R.drawable.flower_still_thinking_about_it_bloom
    FlowerKind.HARD_TO_SHAKE_OFF -> R.drawable.flower_hard_to_shake_off_bloom
    FlowerKind.TIME_TO_ACTUALLY_DO_IT -> R.drawable.flower_time_to_actually_do_it_bloom
    FlowerKind.NOTHING_LEFT_UNSAID -> R.drawable.flower_nothing_left_unsaid_bloom
    FlowerKind.WONDERING_IF_THAT_LANDED -> R.drawable.flower_wondering_if_that_landed_bloom
    FlowerKind.SAID_THE_HARD_THING -> R.drawable.flower_said_the_hard_thing_bloom
    FlowerKind.GLAD_SHE_PICKED_UP -> R.drawable.flower_glad_she_picked_up_bloom
    FlowerKind.WISHED_IT_WAS_LONGER -> R.drawable.flower_wished_it_was_longer_bloom
    FlowerKind.DREADED_THIS_ONE -> R.drawable.flower_dreaded_this_one_bloom
}

/**
 * A flower seen from above, in a garden, eleven pixels across.
 *
 * Not the artwork, on purpose, and the same call the field makes for the same
 * reason. At this size a photographed bloom is a coloured smudge -- there is
 * no shape left to recognise, only a hue -- and a plot view can put hundreds
 * on screen at once, where the artwork would mean decoding every kind the
 * garden holds into a bitmap that is two hundred times the size it is drawn
 * at. Petals walked around a centre cost one fill each and read the same.
 *
 * Tell a flower apart here by its colour; anything wanting its shape is being
 * drawn at [FlowerMark]'s size instead.
 */
internal fun DrawScope.drawFlowerDot(spec: FlowerSpec, radius: Float) {
    val rx = radius * 0.38f
    val ry = radius * 0.60f
    val lift = radius * 0.40f
    for (i in 0 until spec.petals) {
        rotate(degrees = i * 360f / spec.petals, pivot = Offset.Zero) {
            drawOval(
                color = Color(spec.petal),
                topLeft = Offset(-rx, -lift - ry),
                size = Size(rx * 2f, ry * 2f),
                alpha = 0.7f,
            )
        }
    }
    drawCircle(Color(spec.heart), radius * 0.22f, Offset.Zero, alpha = 0.8f)
}
