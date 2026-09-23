package app.harbor.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The bee, as arithmetic.
 *
 * `docs/09-master-context.md` has listed a bee as unbuilt since the garden
 * was; this is it. It wanders the field a little above the flowers and is the
 * only thing in the garden that moves of its own accord — which is the whole
 * point of it. A meadow with wind in the grass and nothing alive in it is a
 * photograph.
 *
 * Pure, and apart from the drawing, for the same reason [DayArcs] is: a path
 * that has to *feel* like a bee is a path you want to be able to test, and
 * "it looked wrong" is not something you can debug inside a canvas.
 *
 * ## It is only there close up
 *
 * Zoomed out, the field is a map: a thumbnail of an island, with each flower a
 * couple of pixels. A bee drawn at that scale is either invisible or a fly on
 * the lens, and neither is worth having. It fades in over the same stretch of
 * zoom that brings the grass up — see [Field.grassStand] — so the field
 * gains its texture and its life together rather than in two steps.
 */
object Bee {

    /** How far above the ground it flies, in world units. */
    const val HOVER = 2.4

    /**
     * How far it strays from where the camera is looking, in world units.
     *
     * Kept near the middle deliberately. A bee that wandered the whole island
     * would be off screen most of the time, and one you have to go looking for
     * is not company.
     */
    const val ROAM = 34.0

    /**
     * The furthest it can actually get, which is not [ROAM].
     *
     * The path is two circles added together, and although each axis is
     * weighted to sum to one, both axes can be at their maximum at the same
     * moment — so the true bound is the diagonal, not the side. Written down
     * rather than left implicit because the first test of this asserted
     * [ROAM] and caught the bee eleven inches outside it, which is a bound
     * being wrong rather than a bee misbehaving.
     */
    const val REACH = ROAM * 1.4143

    /** Seconds for the long loop. The short one runs against it. */
    const val LOOP = 19.0

    /**
     * When the path comes back to exactly where it started: ten long loops.
     *
     * Not a decoration. The drawing needs a clock, and an animated float has
     * to wrap somewhere — so it wraps *here*, where the bee is already where
     * the next second would have put it, and the seam is invisible. Wrap
     * anywhere else and the bee teleports once every cycle.
     *
     * Ten because the two rates the path is built from, 2.7 and 3.1, are both
     * tenths: after ten long loops each has turned a whole number of times.
     * Change either rate and this number changes with it — there is a test
     * that says so.
     */
    const val PERIOD = LOOP * 10

    /** Wingbeats a second. Fast enough to blur, slow enough to be a wing. */
    const val WINGBEAT = 11.0

    data class Spot(val x: Double, val y: Double, val z: Double)

    /**
     * Where the bee is at [seconds], relative to the point it is roaming
     * around.
     *
     * Two circles at rates that do not divide into each other, so the path
     * never repeats inside a sitting and never looks like a circle. The
     * vertical bob is a third rate again, shallower, because a bee rises and
     * falls much less than it drifts.
     *
     * Deterministic: the same second gives the same place. Nothing here reads
     * a clock or a random, so the drawing can be asked where the bee is at any
     * moment and a test can ask the same question.
     */
    fun at(seconds: Double): Spot {
        val slow = seconds / LOOP * 2 * PI
        return Spot(
            x = (cos(slow) * 0.68 + cos(slow * RATE_X) * 0.32) * ROAM,
            y = (sin(slow) * 0.74 + sin(slow * RATE_Y) * 0.26) * ROAM,
            z = HOVER * (1.0 + 0.34 * sin(slow * RATE_Z)),
        )
    }

    // The three rates the wander is built from.
    //
    // **Every one of them must be a whole number of tenths**, or [PERIOD]
    // stops being a period and the bee jumps once a cycle. The y rate used to
    // be written as `quick * 0.8` — 2.7 times 0.8, which is 2.16, which is not
    // a tenth — and the test that says the path closes caught it. Spelling the
    // three out separately is what stops the next arithmetic of that shape.
    private const val RATE_X = 2.7
    private const val RATE_Y = 2.2
    private const val RATE_Z = 3.1

    /**
     * Which way it is facing, in radians, from where it has just come from.
     *
     * Worked out by asking [at] for a moment slightly earlier rather than by
     * differentiating the path by hand: the two agree by construction, and
     * when the path changes this does not have to be changed with it.
     */
    fun heading(seconds: Double): Double {
        val was = at(seconds - 0.14)
        val now = at(seconds)
        val dx = now.x - was.x
        val dy = now.y - was.y
        if (dx == 0.0 && dy == 0.0) return 0.0
        return kotlin.math.atan2(dy, dx)
    }

    /** Where the wings are in their beat: nought to one and back, at [seconds]. */
    fun wing(seconds: Double): Double = sin(seconds * WINGBEAT * 2 * PI)

    /**
     * How much of the bee is there, from nought to one.
     *
     * Rides the grass. [stand] is [Field.grassStand] for the middle of the
     * view, so the bee arrives as the blades do — and a field that is still a
     * map has no bee on it at all.
     *
     * Squared, so it is properly gone rather than a faint smudge through the
     * first half of the zoom.
     */
    fun showing(stand: Double): Double {
        val open = ((stand - 0.18) / 0.5).coerceIn(0.0, 1.0)
        return open * open
    }
}
