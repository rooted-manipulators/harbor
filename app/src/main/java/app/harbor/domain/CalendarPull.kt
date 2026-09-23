package app.harbor.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Turning a calendar's next week into busy blocks. ADR-015.
 *
 * The calendar has dated events; Harbor's week is weekly (ADR-011). So this
 * reads the seven days starting today and lays each event on its weekday.
 * Next Tuesday's lecture becomes every Tuesday's, which is what a timetable
 * is -- and a one-off that lands on the week once is cheap, because a cue is
 * capped and dismissible and the block can be dragged off.
 *
 * Pure: the platform query lives in `data/CalendarReader`, which hands this a
 * list and does nothing else.
 */
object CalendarPull {

    /** One event as the calendar gave it, already in local time. */
    data class Event(
        val start: LocalDateTime,
        val end: LocalDateTime,
        val title: String?,
        /** All-day events mark nothing: a birthday is not a busy day. */
        val allDay: Boolean = false,
        /** Events the calendar itself calls "free" do not make you busy. */
        val showsFree: Boolean = false,
    )

    /** How many days ahead are read. One week, because the week is weekly. */
    const val DAYS = 7L

    /**
     * The busy blocks [events] make, clipped to the week starting [today].
     *
     * An event over midnight is cut in two, because a [WeekBlock] cannot cross
     * it. A day's last block runs to [LocalTime.MAX] rather than 23:59, for the
     * reason given at [Windows.quietNights].
     */
    fun blocks(events: List<Event>, today: LocalDate): List<WeekBlock> {
        val first = today.atStartOfDay()
        val last = today.plusDays(DAYS).atStartOfDay()
        return events
            .filter { !it.allDay && !it.showsFree && it.end > it.start }
            .flatMap { e ->
                val from = maxOf(e.start, first)
                val to = minOf(e.end, last)
                buildList {
                    var day = from.toLocalDate()
                    while (day.atStartOfDay() < to) {
                        val a = if (day == from.toLocalDate()) from.toLocalTime() else LocalTime.MIDNIGHT
                        val b = if (day == to.toLocalDate()) to.toLocalTime() else LocalTime.MAX
                        if (a < b) add(WeekBlock(day.dayOfWeek, a, b, BlockKind.BUSY, e.title?.trim()?.ifEmpty { null }, BlockOrigin.CALENDAR))
                        day = day.plusDays(1)
                    }
                }
            }
    }

    /**
     * [week] with [pulled] placed on it. Each is placed like a drawn block, so
     * it clears whatever was under it -- including a flower, because the
     * calendar knowing you are in a meeting beats a guess that you were free.
     */
    fun merge(week: List<WeekBlock>, pulled: List<WeekBlock>): List<WeekBlock> =
        pulled.fold(week) { acc, b -> Windows.place(acc, b) }
}
