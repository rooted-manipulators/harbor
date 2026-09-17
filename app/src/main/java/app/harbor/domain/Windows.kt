package app.harbor.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Room for a call: the time you marked good, and failing that, the gaps.
 *
 * The specimen sheet's schedule leads with a card it calls "a little window,
 * together" — a stretch of evening set in large serif, as the one thing on
 * that screen worth looking at. This computes the honest version of it.
 *
 * ## Why it says "you" and never "together"
 *
 * The sheet's card implies both people's calendars. Harbor cannot know the
 * second one and must never look as though it does: the parent installs
 * nothing and is never contacted (ADR-007), and no location or calendar of
 * theirs exists anywhere in this app. So this is *your* window — a time you
 * are free — and the copy that renders it has to stay on that side of the
 * line. A card that quietly implied Mum's evening was being read would be the
 * single most damaging thing this design could do.
 *
 * ## Chosen time and left-over time
 *
 * There are now two ways a window can exist, and they are not equally good.
 *
 * A **chosen** window is one the user planted a flower on: they looked at
 * their week and said *this is when I would like to be called*. A derived
 * window is only the arithmetic left over between two classes. Both are
 * offered, chosen ones first, and the caller can tell them apart — because
 * "Tuesday evening, which you said was a good time" and "you have a gap after
 * your seminar" are not the same sentence and should not be written as one.
 *
 * Everything here is derived from [WeekBlock]s the user typed in themselves.
 * Nothing is sensed, stored or sent.
 */
object Windows {

    /** Before this, nobody wants a call. */
    val DAY_START: LocalTime = LocalTime.of(8, 0)

    /** After this, neither does anybody else. */
    val DAY_END: LocalTime = LocalTime.of(22, 0)

    /** Shorter than this is a gap between classes, not room for a call. */
    val LEAST: Duration = Duration.ofMinutes(20)

    data class Window(
        val start: LocalTime,
        val end: LocalTime,
        /** True when the user planted this window rather than Harbor finding it. */
        val chosen: Boolean = false,
    ) {
        init {
            require(start < end) { "a window must end after it starts" }
        }

        val minutes: Int get() = (end.toSecondOfDay() - start.toSecondOfDay()) / 60
    }

    /**
     * Whether a cue would be landing in the middle of something.
     *
     * The one question the cue pipeline asks of the schedule, and the reason
     * [WeekBlock.covers] is geometry rather than policy: only a
     * [BlockKind.BUSY] block suppresses anything. A flower is an invitation,
     * and an invitation that silenced the app would be a trap.
     */
    fun busyAt(blocks: List<WeekBlock>, at: ZonedDateTime): Boolean =
        blocks.any { it.kind == BlockKind.BUSY && it.covers(at) }

    /**
     * Every stretch of [day] long enough to matter that is not marked busy.
     *
     * Overlapping blocks are merged first, so two classes that run into each
     * other do not produce a phantom window between them.
     *
     * Free blocks are not subtracted, obviously, but they are not added
     * either: this is the left-over time, and [planted] is where chosen time
     * comes from.
     */
    fun free(
        blocks: List<WeekBlock>,
        day: DayOfWeek,
        from: LocalTime = DAY_START,
        to: LocalTime = DAY_END,
    ): List<Window> {
        if (from >= to) return emptyList()

        val merged = busyRuns(blocks, day, from, to)

        val out = mutableListOf<Window>()
        var cursor = from
        for ((start, end) in merged) {
            if (cursor < start) out.add(Window(cursor, start))
            cursor = maxOf(cursor, end)
        }
        if (cursor < to) out.add(Window(cursor, to))

        return out.filter { it.minutes >= LEAST.toMinutes() }
    }

    /**
     * The windows the user planted on [day], with any busy block cut back out.
     *
     * Deliberately **not** clamped to [DAY_START]–[DAY_END]. That clamp exists
     * to stop the arithmetic proposing three in the morning; it has no
     * business overruling somebody who looked at a grid and said *seven is
     * when I ring home*. Nothing fires from this — it decides what a card
     * offers, not when a cue is allowed — so the worst an early flower can do
     * is offer an early window, which is what was asked for.
     *
     * A busy block placed over a flower wins, because the grid lets you put
     * one on top of the other and the later statement is the one to believe.
     */
    fun planted(
        blocks: List<WeekBlock>,
        day: DayOfWeek,
        from: LocalTime = LocalTime.MIN,
        to: LocalTime = LocalTime.MAX,
    ): List<Window> {
        val busy = blocks.filter { it.day == day && it.kind == BlockKind.BUSY }
        return blocks
            .filter { it.day == day && it.kind == BlockKind.FREE }
            .flatMap { flower ->
                val start = maxOf(flower.start, from)
                val end = minOf(flower.end, to)
                if (start >= end) emptyList() else subtract(start, end, busy)
            }
            .map { (start, end) -> Window(start, end, chosen = true) }
            .filter { it.minutes >= LEAST.toMinutes() }
            .sortedBy { it.start }
    }

    /**
     * The best window still ahead of [now], or null if the day is spent.
     *
     * A window the user chose beats one Harbor worked out, however roomy the
     * worked-out one is: six spare hours on a Sunday are not a better offer
     * than the hour somebody wrote down as the hour they ring home. Within
     * each kind it is the roomiest rather than the soonest, because the card
     * is an invitation and ten minutes before a lecture is not one.
     */
    fun next(
        blocks: List<WeekBlock>,
        day: DayOfWeek,
        now: LocalTime,
        from: LocalTime = DAY_START,
        to: LocalTime = DAY_END,
    ): Window? = planted(blocks, day, from = now).maxByOrNull { it.minutes }
        ?: free(blocks, day, maxOf(now, from), to).maxByOrNull { it.minutes }

    /**
     * How full [day] is, from nought to one.
     *
     * Busy time only. A flower is somebody saying *this is room I have kept*,
     * which is the opposite of a day filling up, and counting it would make
     * marking your good evenings look like work.
     *
     * Measured against the waking window rather than the whole twenty-four
     * hours, because eight hours of lectures is most of a day and a third of a
     * clock, and the number is meant to answer "how full does this feel".
     */
    fun load(blocks: List<WeekBlock>, day: DayOfWeek): Double {
        val span = DAY_END.toSecondOfDay() - DAY_START.toSecondOfDay()
        if (span <= 0) return 0.0
        val busy = busyRuns(blocks, day, DAY_START, DAY_END)
            .sumOf { (start, end) -> end.toSecondOfDay() - start.toSecondOfDay() }
        return (busy.toDouble() / span).coerceIn(0.0, 1.0)
    }

    /**
     * The weather a day this full looks like, as a place to start.
     *
     * The mood picker opened on [Weather.CLEAR] for everybody, every day,
     * which is a question disguised as an answer: a blank control asks the
     * user to do the work of noticing before they have opened the app
     * properly. The week they typed in already knows whether today is packed,
     * so the picker can arrive at a guess and be corrected.
     *
     * A guess, and nothing more. It is never written to settings by itself --
     * how a day *feels* is the user's to say, and a timetable cannot know that
     * a light day is the hard one. See the caller.
     *
     * The bands are deliberately not even. Most of the difference people feel
     * is at the bottom -- an empty day and a third-full day are not the same
     * day -- while everything past about two thirds booked is simply a lot.
     */
    fun weatherFor(blocks: List<WeekBlock>, day: DayOfWeek): Weather =
        when (load(blocks, day)) {
            in 0.0..0.12 -> Weather.CLEAR
            in 0.12..0.30 -> Weather.BRIGHT
            in 0.30..0.52 -> Weather.CLOUDY
            in 0.52..0.72 -> Weather.RAIN
            else -> Weather.STORM
        }

    /**
     * The busy stretches of [day], clamped and merged, in order.
     *
     * Merging first is what stops two classes that run into each other
     * producing a phantom gap between them, and stops an overlap being counted
     * twice when the day is weighed.
     */
    private fun busyRuns(
        blocks: List<WeekBlock>,
        day: DayOfWeek,
        from: LocalTime,
        to: LocalTime,
    ): List<Pair<LocalTime, LocalTime>> {
        val clamped = blocks
            .filter { it.day == day && it.kind == BlockKind.BUSY }
            .map { maxOf(it.start, from) to minOf(it.end, to) }
            .filter { it.first < it.second }
            .sortedBy { it.first }

        val merged = mutableListOf<Pair<LocalTime, LocalTime>>()
        for ((start, end) in clamped) {
            val last = merged.lastOrNull()
            if (last != null && start <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, end)
            } else {
                merged.add(start to end)
            }
        }
        return merged
    }

    /**
     * Put [block] on the week, clearing whatever it lands on.
     *
     * The rule the grid needs, and the reason both kinds share one list: no
     * moment is both busy and free, so placing something is always also
     * erasing what was underneath. A block straddled in the middle splits in
     * two; one covered end to end disappears.
     *
     * Same-kind overlaps are cut the same way rather than merged. The result
     * looks identical — two blocks that abut draw as one run — and it keeps
     * this to a single rule instead of two.
     */
    fun place(blocks: List<WeekBlock>, block: WeekBlock): List<WeekBlock> =
        blocks.flatMap { existing ->
            if (existing.day != block.day) {
                listOf(existing)
            } else {
                subtract(existing.start, existing.end, listOf(block))
                    .map { (start, end) -> existing.copy(start = start, end = end) }
            }
        } + block

    /**
     * [start]–[end] with every one of [cuts] taken out of it.
     *
     * Returns the pieces that survive, in order. Empty when the cuts cover the
     * whole span.
     */
    private fun subtract(
        start: LocalTime,
        end: LocalTime,
        cuts: List<WeekBlock>,
    ): List<Pair<LocalTime, LocalTime>> {
        var pieces = listOf(start to end)
        for (cut in cuts.sortedBy { it.start }) {
            pieces = pieces.flatMap { (from, to) ->
                if (cut.end <= from || cut.start >= to) {
                    listOf(from to to)
                } else {
                    buildList {
                        if (from < cut.start) add(from to cut.start)
                        if (cut.end < to) add(cut.end to to)
                    }
                }
            }
        }
        return pieces
    }

    /**
     * How long the window is, in words rather than in minutes.
     *
     * On a day with nothing blocked the arithmetic answer is "373 unhurried
     * minutes", which is true and is not what anybody means. Past an hour,
     * people count in hours.
     *
     * Rounded to the nearest hour rather than truncated, so an hour and fifty
     * minutes does not present itself as one.
     */
    fun phrase(window: Window): String {
        val m = window.minutes
        if (m < 60) return "$m unhurried minutes"
        val hours = (m + 30) / 60
        return if (hours == 1) "an unhurried hour" else "$hours unhurried hours"
    }
}
