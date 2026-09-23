package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

class CopyDayTest {

    @Test
    fun `the next day ends up looking like this one`() {
        val week = listOf(
            WeekBlock(MONDAY, t(9), t(11), BlockKind.BUSY, "Stats"),
            WeekBlock(MONDAY, t(18), t(19), BlockKind.FREE),
            WeekBlock(TUESDAY, t(13), t(15), BlockKind.BUSY, "Old"),
        )
        val next = Windows.copyDay(week, MONDAY, TUESDAY)
        val tuesday = next.filter { it.day == TUESDAY }.sortedBy { it.start }
        assertEquals(listOf(t(9) to t(11), t(18) to t(19)), tuesday.map { it.start to it.end })
        assertEquals("Stats", tuesday[0].label)
        assertEquals(BlockKind.FREE, tuesday[1].kind)
    }

    @Test
    fun `the day copied from is untouched`() {
        val week = listOf(WeekBlock(MONDAY, t(9), t(11)))
        val next = Windows.copyDay(week, MONDAY, TUESDAY)
        assertEquals(week, next.filter { it.day == MONDAY })
    }

    @Test
    fun `sunday copies onto monday`() {
        val week = listOf(WeekBlock(SUNDAY, t(10), t(12)))
        val next = Windows.copyDay(week, SUNDAY, SUNDAY.plus(1))
        assertTrue(next.any { it.day == MONDAY && it.start == t(10) })
    }

    @Test
    fun `an empty day copied clears the next one`() {
        val week = listOf(WeekBlock(TUESDAY, t(9), t(10)))
        assertTrue(Windows.copyDay(week, MONDAY, TUESDAY).isEmpty())
    }
}

class ClearDayTest {

    @Test
    fun `every block on that day goes, thorns and blooms both`() {
        val week = listOf(
            WeekBlock(MONDAY, t(9), t(11), BlockKind.BUSY, "Stats"),
            WeekBlock(MONDAY, t(18), t(19), BlockKind.FREE),
            WeekBlock(TUESDAY, t(9), t(10)),
        )
        val cleared = Windows.clearDay(week, MONDAY)
        assertEquals(listOf(week[2]), cleared)
    }

    @Test
    fun `quiet hours go with the rest`() {
        val cleared = Windows.clearDay(Windows.quietNights(), MONDAY)
        assertTrue(cleared.none { it.day == MONDAY })
        assertEquals(12, cleared.size)
    }

    @Test
    fun `an empty day is a no-op`() {
        val week = listOf(WeekBlock(TUESDAY, t(9), t(10)))
        assertEquals(week, Windows.clearDay(week, MONDAY))
    }

    @Test
    fun `other days are untouched`() {
        val week = Windows.quietNights()
        val cleared = Windows.clearDay(week, MONDAY)
        assertEquals(week.filter { it.day != MONDAY }, cleared)
    }
}

class QuietTest {

    @Test
    fun `the seeded nights read back as ten till eight`() {
        assertEquals(t(22) to t(8), Windows.quietPeriod(Windows.quietNights()))
    }

    @Test
    fun `nights seeded under the old name still count`() {
        val old = DayOfWeek.entries.flatMap {
            listOf(
                WeekBlock(it, LocalTime.MIDNIGHT, t(8), BlockKind.BUSY, "Night"),
                WeekBlock(it, t(22), LocalTime.MAX, BlockKind.BUSY, "Night"),
            )
        }
        assertEquals(t(22) to t(8), Windows.quietPeriod(old))
        val moved = Windows.setQuiet(old, t(23), t(7))
        assertTrue(moved.none { it.label == "Night" })
        assertEquals(t(23) to t(7), Windows.quietPeriod(moved))
    }

    @Test
    fun `over midnight is two blocks on every day`() {
        val week = Windows.setQuiet(emptyList(), t(23), t(6, 30))
        assertEquals(14, week.size)
        DayOfWeek.entries.forEach { d ->
            val day = week.filter { it.day == d }.sortedBy { it.start }
            assertEquals(LocalTime.MIDNIGHT to t(6, 30), day[0].start to day[0].end)
            assertEquals(t(23) to LocalTime.MAX, day[1].start to day[1].end)
        }
    }

    @Test
    fun `within a day is one block`() {
        val week = Windows.setQuiet(emptyList(), t(13), t(14))
        assertEquals(7, week.size)
        assertEquals(t(13) to t(14), Windows.quietPeriod(week))
    }

    @Test
    fun `ending at midnight is one block to the end of the day`() {
        val week = Windows.setQuiet(emptyList(), t(22), LocalTime.MIDNIGHT)
        assertEquals(7, week.size)
        assertTrue(week.all { it.start == t(22) && it.end == LocalTime.MAX })
        assertEquals(t(22) to LocalTime.MIDNIGHT, Windows.quietPeriod(week))
        assertEquals(week.toSet(), Windows.setQuiet(week, t(22), LocalTime.MIDNIGHT).toSet())
    }

    @Test
    fun `changing it leaves everything else alone and cuts what it lands on`() {
        val lecture = WeekBlock(WEDNESDAY, t(9), t(11), BlockKind.BUSY, "Stats")
        val late = WeekBlock(WEDNESDAY, t(20), t(23), BlockKind.FREE)
        val week = Windows.setQuiet(Windows.quietNights() + lecture + late, t(21), t(7))
        assertTrue(lecture in week)
        val free = week.filter { it.day == WEDNESDAY && it.kind == BlockKind.FREE }
        assertEquals(listOf(t(20) to t(21)), free.map { it.start to it.end })
    }

    @Test
    fun `off removes it and nothing else`() {
        val lecture = WeekBlock(WEDNESDAY, t(9), t(11), BlockKind.BUSY, "Stats")
        val week = Windows.setQuiet(Windows.quietNights() + lecture, null, null)
        assertEquals(listOf(lecture), week)
        assertNull(Windows.quietPeriod(week))
    }

    @Test
    fun `a period of no length is off, not a silent switch`() {
        assertTrue(Windows.setQuiet(emptyList(), t(9), t(9)).isEmpty())
    }

    @Test
    fun `quiet time suppresses cues`() {
        val week = Windows.setQuiet(emptyList(), t(22), t(8))
        val at = java.time.ZonedDateTime.of(2026, 9, 23, 2, 0, 0, 0, java.time.ZoneId.of("Asia/Kolkata"))
        assertTrue(Windows.busyAt(week, at))
    }
}

class CalendarPullTest {

    // A Wednesday.
    private val today = LocalDate.of(2026, 9, 23)
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 9, day, h, m)

    @Test
    fun `an event lands on its weekday`() {
        val b = CalendarPull.blocks(listOf(CalendarPull.Event(at(24, 10), at(24, 11, 30), "Finance")), today)
        assertEquals(listOf(WeekBlock(DayOfWeek.THURSDAY, t(10), t(11, 30), BlockKind.BUSY, "Finance", BlockOrigin.CALENDAR)), b)
    }

    @Test
    fun `all-day and free events mark nothing`() {
        val b = CalendarPull.blocks(
            listOf(
                CalendarPull.Event(at(24, 0), at(25, 0), "Birthday", allDay = true),
                CalendarPull.Event(at(24, 9), at(24, 10), "Optional", showsFree = true),
            ),
            today,
        )
        assertTrue(b.isEmpty())
    }

    @Test
    fun `over midnight splits in two`() {
        val b = CalendarPull.blocks(listOf(CalendarPull.Event(at(25, 22), at(26, 2), "Trip")), today)
        assertEquals(
            listOf(
                WeekBlock(DayOfWeek.FRIDAY, t(22), LocalTime.MAX, BlockKind.BUSY, "Trip", BlockOrigin.CALENDAR),
                WeekBlock(DayOfWeek.SATURDAY, LocalTime.MIDNIGHT, t(2), BlockKind.BUSY, "Trip", BlockOrigin.CALENDAR),
            ),
            b,
        )
    }

    @Test
    fun `only the coming week is read`() {
        val b = CalendarPull.blocks(
            listOf(
                CalendarPull.Event(at(22, 9), at(22, 10), "Yesterday"),
                CalendarPull.Event(at(30, 9), at(30, 10), "Next week"),
                CalendarPull.Event(at(29, 9), at(29, 10), "Tuesday"),
            ),
            today,
        )
        assertEquals(listOf("Tuesday"), b.map { it.label })
    }

    @Test
    fun `a blank title is no label`() {
        val b = CalendarPull.blocks(listOf(CalendarPull.Event(at(24, 9), at(24, 10), "  ")), today)
        assertNull(b.single().label)
    }

    @Test
    fun `merging cuts a flower it lands on`() {
        val week = listOf(WeekBlock(DayOfWeek.THURSDAY, t(9), t(12), BlockKind.FREE))
        val pulled = listOf(WeekBlock(DayOfWeek.THURSDAY, t(10), t(11), BlockKind.BUSY, "Finance"))
        val merged = CalendarPull.merge(week, pulled)
        val free = merged.filter { it.kind == BlockKind.FREE }.map { it.start to it.end }
        assertEquals(listOf(t(9) to t(10), t(11) to t(12)), free)
        assertTrue(pulled.single() in merged)
    }
}
