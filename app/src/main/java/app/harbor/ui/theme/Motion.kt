package app.harbor.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * How Harbor moves.
 *
 * Four durations and three easings, so that two things happening at once look
 * like one app rather than two developers. Material's own motion system in
 * miniature: emphasised easing for anything entering or leaving, standard for
 * anything already on screen changing shape, and a spring for anything a
 * finger is touching.
 *
 * ## Everything here honours reduced motion
 *
 * Not by shortening — by going to zero. [LocalReducedMotion] is set for people
 * who find movement genuinely unpleasant, and a 90ms slide is still a slide.
 * Use [Motion.fast] and friends rather than `tween(180)` and the setting
 * reaches every corner for free.
 */
object Motion {

    // --- durations ---------------------------------------------------------
    //
    // Material's scale, trimmed to four, and pitched at its slow end on
    // purpose: the first pass sat at 120-420 and read as nothing happening,
    // which is the one thing motion in this app is not allowed to be. Past
    // about 600 a transition starts to feel like waiting, so SLOW stops short.

    /** A press answering: a chip filling, a tick lighting. */
    const val FAST = 180

    /** The default. A row opening, a label swapping, a colour moving. */
    const val NORMAL = 320

    /** Something arriving on screen for the first time. */
    const val ENTER = 420

    /** A whole surface changing: a screen, the dial coming up. */
    const val SLOW = 560

    // --- easings -----------------------------------------------------------

    /**
     * Emphasised. Leaves fast and settles slow, which is what makes an arrival
     * feel like it was placed rather than teleported.
     */
    val Emphasised: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Standard. For something already here that is changing. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)

    /** Decelerate. For something coming to rest at the edge of the screen. */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    // --- the specs everything should reach for -----------------------------

    @Composable
    @ReadOnlyComposable
    fun <T> fast(): FiniteAnimationSpec<T> = tween(still(FAST), easing = Standard)

    @Composable
    @ReadOnlyComposable
    fun <T> normal(): FiniteAnimationSpec<T> = tween(still(NORMAL), easing = Standard)

    @Composable
    @ReadOnlyComposable
    fun <T> arriving(): FiniteAnimationSpec<T> = tween(still(ENTER), easing = Emphasised)

    @Composable
    @ReadOnlyComposable
    fun <T> slow(): FiniteAnimationSpec<T> = tween(still(SLOW), easing = Emphasised)

    /**
     * A press that gives, and gives back with a little overshoot.
     *
     * The one place Harbor is allowed to bounce. A bounce says "that was a
     * thing you did"; on anything that was not touched it says "look at me".
     */
    @Composable
    @ReadOnlyComposable
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )

    /** A settle with no overshoot, for something being dragged or resized. */
    @Composable
    @ReadOnlyComposable
    fun <T> settling(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    // The two springs used to be plain functions, and a spring cannot be
    // shortened to zero -- so they went on bouncing for exactly the people who
    // had asked for nothing to bounce, while the doc above promised otherwise.
    // They snap now.

    /** Nought when somebody has asked the app to be still. */
    @Composable
    @ReadOnlyComposable
    private fun still(ms: Int): Int = if (LocalReducedMotion.current) 0 else ms
}

// --- the micro-interactions, named so they are used rather than written -----

/** Fade, and rise a little. What a card does when it arrives. */
@Composable
fun risesIn(from: Dp = Space.two): EnterTransition {
    // A distance in dp, not the raw pixels it used to take: twelve pixels is
    // a visible rise on one phone and a flicker on a denser one.
    val px = with(LocalDensity.current) { from.roundToPx() }
    return fadeIn(Motion.arriving()) + slideInVertically(Motion.arriving()) { px }
}

/** Its opposite, quicker, because leaving should not be dwelt on. */
@Composable
fun fadesOut(): ExitTransition = fadeOut(Motion.fast())

/** Fade and grow from slightly small: for something that appeared because you asked. */
@Composable
fun popsIn(): EnterTransition =
    fadeIn(Motion.arriving()) + scaleIn(Motion.bouncy(), initialScale = 0.88f)

/** Its opposite. */
@Composable
fun popsOut(): ExitTransition =
    fadeOut(Motion.fast()) + scaleOut(Motion.fast(), targetScale = 0.92f)

/**
 * Slide in from the side something came from, and out towards where it went.
 *
 * [forward] is which way the content moved, not which way the finger did —
 * those are opposites and getting them the wrong way round is the one mistake
 * that makes a directional animation read as broken rather than as absent.
 */
@Composable
fun slidesAcross(forward: Boolean): Pair<EnterTransition, ExitTransition> {
    val way = if (forward) 1 else -1
    return (
        fadeIn(Motion.normal()) +
            slideInHorizontally(Motion.normal()) { w -> way * w / 3 }
        ) to (
        fadeOut(Motion.fast()) +
            slideOutHorizontally(Motion.normal()) { w -> -way * w / 3 }
        )
}
