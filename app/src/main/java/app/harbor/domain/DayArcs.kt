package app.harbor.domain

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * One day of a week, as two kinds of arc around a dial.
 *
 * The person's page shows their day as a clock: a thorned arc over the hours
 * they are busy, a blooming one over the hours they kept free, and nothing at
 * all over hours nobody said anything about. It is the same two marks the
 * schedule grid uses — see [BlockKind] — bent around a circle, which is the
 * shape a day actually has and a column is not.
 *
 * This file is the arithmetic of that, and it is pure: no Compose, no clock of
 * its own, no IO. The drawing reads angles out of here and the drag asks it
 * where a flower is allowed to rest. Keeping both on this side of the line is
 * what makes a gesture that has to *feel* right something you can also prove
 * is right.
 *
 * ## Two scales, and which one is which
 *
 * A twelve-hour face puts nine in the morning and nine at night in the same
 * place. For hands that is a convention everybody has absorbed; for an *arc*
 * it is a lie — a lecture from 9 to 11 would draw over the evening as well,
 * and a reminder dragged onto it could be either.
 *
 * So the two things get the two scales they each need. The **ring** outside
 * the face is a whole day: one turn is twenty-four hours, midnight at the top
 * and noon at the bottom, which is the only mapping under which an arc means
 * one stretch of one day. The **face** inside it is an ordinary twelve-hour
 * clock, because it is telling the time and not placing anything, and a
 * familiar clock is worth more there than a consistent one.
 *
 * [degreesAt] is the ring's and [faceDegreesAt] is the face's. Everything that
 * has to line up with an arc — a press, a drag, the reminder — uses the first.
 * Only the hands and the hour marks use the second. Mixing them up puts a
 * nine-o'clock reminder over the morning, so they are named apart rather than
 * separated by an argument.
 *
 * ## What an arc is not
 *
 * An arc is a statement somebody made, never an inference. A day with nothing
 * entered has no arcs — not a full circle of "free", which would be Harbor
 * asserting something nobody told it. That is the same rule [Windows.planted]
 * keeps, and for the same reason: chosen time and left-over time are not the
 * same claim, and this dial only ever draws the chosen kind.
 */
object DayArcs {

    /** One turn of the dial. */
    const val DAY_MINUTES: Int = 24 * 60

    /**
     * What a dragged reminder snaps to.
     *
     * Quarter hours. A dial this size gives about a degree and a half per
     * minute, which is finer than a thumb can mean, and "call her at 21:07" is
     * not a thing anybody intends.
     */
    const val STEP_MINUTES: Int = 15

    /**
     * A stretch of the day, in minutes since midnight, with the mark it
     * carries.
     *
     * Minutes rather than [LocalTime] because everything downstream is
     * arithmetic — angles, midpoints, clamping — and doing that on a clock type
     * means converting at every step. [at] converts back when something has to
     * be shown or written down.
     */
    data class Arc(val from: Int, val to: Int, val kind: BlockKind) {
        init {
            require(from < to) { "an arc must end after it starts" }
        }

        val minutes: Int get() = to - from

        /** The midpoint, which is where a reminder first lands. */
        val middle: Int get() = (from + to) / 2

        /** Whether [minute] falls on this arc, ends included. */
        fun holds(minute: Int): Boolean = minute in from..to
    }

    /**
     * The arcs [day] carries, busy first, in order.
     *
     * Busy runs are merged so two lectures that touch draw as one thorn rather
     * than two with a seam. Free runs have the busy cut back out of them,
     * because the grid lets one be placed on top of the other and the rule
     * everywhere else in this app is that the later statement wins — see
     * [Windows.planted], which is the same subtraction for the same reason.
     *
     * Nothing entered for the day gives nothing back. See the note above.
     */
    fun of(blocks: List<WeekBlock>, day: DayOfWeek): List<Arc> {
        val mine = blocks.filter { it.day == day }
        if (mine.isEmpty()) return emptyList()

        val busy = merge(mine.filter { it.kind == BlockKind.BUSY }.map { it.span() })
        val free = merge(
            mine.filter { it.kind == BlockKind.FREE }
                .flatMap { subtract(it.span(), busy) },
        )

        return (
            busy.map { Arc(it.first, it.second, BlockKind.BUSY) } +
                free.map { Arc(it.first, it.second, BlockKind.FREE) }
            ).sortedBy { it.from }
    }

    /** Half a turn of the face, which is a whole turn of it. */
    const val FACE_MINUTES: Int = 12 * 60

    /** Where [minute] sits on the ring: degrees clockwise from midnight. */
    fun degreesAt(minute: Int): Float =
        minute.toFloat() / DAY_MINUTES * 360f

    /**
     * Where [minute] sits on the twelve-hour face: degrees clockwise from
     * twelve o'clock.
     *
     * Wraps at noon, which is the whole reason this is a different function
     * from [degreesAt] rather than a scale factor on it — half past nine in the
     * evening and half past nine in the morning are the same place here, and
     * are emphatically not the same place on the ring.
     */
    fun faceDegreesAt(minute: Int): Float =
        (minute.mod(FACE_MINUTES)).toFloat() / FACE_MINUTES * 360f

    /**
     * The inverse, wrapped.
     *
     * A finger dragged round the dial crosses midnight without meaning
     * anything by it, so the angle is brought back into one turn rather than
     * treated as an error.
     */
    fun minuteAt(degrees: Float): Int {
        val turn = ((degrees % 360f) + 360f) % 360f
        // Rounded, not truncated. Truncating loses a minute wherever the
        // floating-point angle lands a hair under the one it means, so a
        // flower dragged and redrawn from its own angle walks backwards a
        // minute at a time -- which looks exactly like a gesture that does not
        // track the finger.
        val minute = Math.round(turn / 360f * DAY_MINUTES)
        // The far edge of the last minute is the top of the dial again, not a
        // twenty-fifth hour.
        return if (minute >= DAY_MINUTES) 0 else minute
    }

    /** The arc [minute] falls on, or null where the day says nothing. */
    fun arcAt(arcs: List<Arc>, minute: Int): Arc? = arcs.firstOrNull { it.holds(minute) }

    /**
     * Whether a reminder may rest at [minute], given the arc it was planted on.
     *
     * The whole of the drag's rule, in one place. A reminder belongs to the
     * free stretch it was put down on and cannot leave it — not into a thorn,
     * and not out into the unmarked hours either. Both refusals are the same
     * refusal and the surface answers them the same way, with a buzz.
     */
    fun mayRest(anchor: Arc, minute: Int): Boolean =
        anchor.kind == BlockKind.FREE && anchor.holds(minute)

    /**
     * Where a reminder dragged towards [wanted] actually ends up.
     *
     * Snapped, then held inside its own arc. The caller compares what it asked
     * for with what it got: different means the drag hit the edge, which is
     * the moment to buzz.
     */
    fun rest(anchor: Arc, wanted: Int): Int =
        snap(wanted).coerceIn(anchor.from, anchor.to)

    /** [minute] to the nearest [step]. */
    fun snap(minute: Int, step: Int = STEP_MINUTES): Int {
        if (step <= 0) return minute
        return ((minute + step / 2) / step * step).coerceIn(0, DAY_MINUTES)
    }

    /** Minutes since midnight, as a time that can be shown or stored. */
    fun at(minute: Int): LocalTime =
        LocalTime.ofSecondOfDay((minute.coerceIn(0, DAY_MINUTES - 1)).toLong() * 60)

    /** "21:30" — the way this dial says a time. */
    fun label(minute: Int): String {
        val m = minute.coerceIn(0, DAY_MINUTES)
        return (m / 60 % 24).toString().padStart(2, '0') + ":" +
            (m % 60).toString().padStart(2, '0')
    }

    /** A block's span in minutes since midnight. */
    private fun WeekBlock.span(): Pair<Int, Int> =
        start.toSecondOfDay() / 60 to end.toSecondOfDay() / 60

    /** Overlapping and touching spans joined into one, in order. */
    private fun merge(spans: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for ((start, end) in spans.filter { it.first < it.second }.sortedBy { it.first }) {
            val last = out.lastOrNull()
            if (last != null && start <= last.second) {
                out[out.lastIndex] = last.first to maxOf(last.second, end)
            } else {
                out.add(start to end)
            }
        }
        return out
    }

    /** [span] with every one of [cuts] taken out of it. */
    private fun subtract(
        span: Pair<Int, Int>,
        cuts: List<Pair<Int, Int>>,
    ): List<Pair<Int, Int>> {
        var pieces = listOf(span)
        for ((cutFrom, cutTo) in cuts) {
            pieces = pieces.flatMap { (from, to) ->
                if (cutTo <= from || cutFrom >= to) {
                    listOf(from to to)
                } else {
                    buildList {
                        if (from < cutFrom) add(from to cutFrom)
                        if (cutTo < to) add(cutTo to to)
                    }
                }
            }
        }
        return pieces.filter { it.first < it.second }
    }
}
