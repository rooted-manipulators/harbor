package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertNotEquals
import java.time.LocalDate

/**
 * What counts as room for a call.
 *
 * The card this feeds is the most inviting thing on the schedule screen, so
 * the arithmetic behind it has to be right: a window that is not really there
 * is an invitation to interrupt yourself.
 *
 * Since the week learned to hold flowers as well as thorns, this file also
 * guards the asymmetry between them. A free block must never quieten the app,
 * and the tests that say so are the most important ones here.
 */
class WindowsTest {

    private val mon = DayOfWeek.MONDAY

    private fun at(h: Int, m: Int = 0) = LocalTime.of(h, m)

    private fun busy(from: LocalTime, to: LocalTime, day: DayOfWeek = mon) =
        WeekBlock(day, from, to, BlockKind.BUSY, "SECRET_LABEL")

    private fun flower(from: LocalTime, to: LocalTime, day: DayOfWeek = mon) =
        WeekBlock(day, from, to, BlockKind.FREE)

    @Test
    fun `an empty week is one long window`() {
        val free = Windows.free(emptyList(), mon)
        assertEquals(1, free.size)
        assertEquals(Windows.DAY_START, free[0].start)
        assertEquals(Windows.DAY_END, free[0].end)
    }

    @Test
    fun `a block in the middle leaves a window on each side`() {
        val free = Windows.free(listOf(busy(at(10), at(12))), mon)
        assertEquals(2, free.size)
        assertEquals(Windows.DAY_START to at(10), free[0].start to free[0].end)
        assertEquals(at(12) to Windows.DAY_END, free[1].start to free[1].end)
    }

    @Test
    fun `two classes that overlap do not invent a window between them`() {
        val free = Windows.free(listOf(busy(at(10), at(12)), busy(at(11), at(13))), mon)
        assertEquals(2, free.size)
        assertEquals(at(13), free[1].start)
    }

    @Test
    fun `nor do two that run straight into each other`() {
        val free = Windows.free(listOf(busy(at(10), at(12)), busy(at(12), at(14))), mon)
        assertEquals(2, free.size)
        assertEquals(at(14), free[1].start)
    }

    @Test
    fun `a block wholly inside another is swallowed`() {
        val free = Windows.free(listOf(busy(at(9), at(15)), busy(at(11), at(12))), mon)
        assertEquals(2, free.size)
        assertEquals(at(9), free[0].end)
        assertEquals(at(15), free[1].start)
    }

    @Test
    fun `ten minutes between classes is not a window`() {
        val free = Windows.free(listOf(busy(at(10), at(12)), busy(at(12, 10), at(14))), mon)
        assertTrue(free.none { it.start == at(12) })
    }

    @Test
    fun `another day's classes do not shorten this one`() {
        val free = Windows.free(listOf(busy(at(10), at(12), DayOfWeek.TUESDAY)), mon)
        assertEquals(1, free.size)
    }

    @Test
    fun `a day booked end to end has no window at all`() {
        val free = Windows.free(listOf(busy(Windows.DAY_START, Windows.DAY_END)), mon)
        assertTrue(free.isEmpty())
    }

    @Test
    fun `blocks outside the callable day are clipped away`() {
        // A 6am gym slot does not create a window before breakfast.
        val free = Windows.free(listOf(busy(at(6), at(7))), mon)
        assertEquals(1, free.size)
        assertEquals(Windows.DAY_START, free[0].start)
    }

    @Test
    fun `a flower does not carve a hole in the left-over time`() {
        // free() is the arithmetic of what is not busy. A flower is not busy,
        // so it changes nothing here.
        val free = Windows.free(listOf(flower(at(10), at(12))), mon)
        assertEquals(1, free.size)
        assertEquals(Windows.DAY_START to Windows.DAY_END, free[0].start to free[0].end)
    }

    // --- the asymmetry the whole feature rests on ---------------------------

    @Test
    fun `a busy block silences the app`() {
        assertTrue(Windows.busyAt(listOf(busy(at(9), at(11))), monday(10, 0)))
    }

    @Test
    fun `a free block never silences the app`() {
        // If this ever goes the other way, somebody who marked two good hours
        // has switched their cues off without being told.
        assertFalse(Windows.busyAt(listOf(flower(at(9), at(11))), monday(10, 0)))
    }

    @Test
    fun `a free block does not rescue time a busy block covers`() {
        val week = listOf(busy(at(9), at(11)), flower(at(9), at(11)))
        assertTrue(Windows.busyAt(week, monday(10, 0)))
    }

    // --- time the user chose ------------------------------------------------

    @Test
    fun `a planted window is what the user drew`() {
        val planted = Windows.planted(listOf(flower(at(19), at(21))), mon)
        assertEquals(1, planted.size)
        assertEquals(at(19) to at(21), planted[0].start to planted[0].end)
        assertTrue(planted[0].chosen)
    }

    @Test
    fun `a class on top of a flower takes its hour back`() {
        val week = listOf(flower(at(18), at(22)), busy(at(19), at(20)))
        val planted = Windows.planted(week, mon)
        assertEquals(2, planted.size)
        assertEquals(at(18) to at(19), planted[0].start to planted[0].end)
        assertEquals(at(20) to at(22), planted[1].start to planted[1].end)
    }

    @Test
    fun `a flower buried under a class offers nothing`() {
        val week = listOf(flower(at(19), at(20)), busy(at(18), at(22)))
        assertTrue(Windows.planted(week, mon).isEmpty())
    }

    @Test
    fun `a ten minute flower is not room for a call either`() {
        assertTrue(Windows.planted(listOf(flower(at(19), at(19, 10))), mon).isEmpty())
    }

    @Test
    fun `an early flower is not overruled by the callable day`() {
        // The 8am clamp stops the arithmetic proposing breakfast. It has no
        // business overruling somebody who wrote down that seven is when they
        // ring home.
        val planted = Windows.planted(listOf(flower(at(7), at(8))), mon)
        assertEquals(1, planted.size)
        assertEquals(at(7), planted[0].start)
    }

    // --- the card's own question --------------------------------------------

    @Test
    fun `the next window is the roomiest one still ahead`() {
        // 08:00-10:00 is behind us; 12:00-14:00 beats 14:30-15:00.
        val blocks = listOf(busy(at(10), at(12)), busy(at(14), at(14, 30)), busy(at(15), at(22)))
        val next = Windows.next(blocks, mon, now = at(11))
        assertEquals(at(12) to at(14), next?.start to next?.end)
    }

    @Test
    fun `a window already under way starts from now, not from its beginning`() {
        val next = Windows.next(listOf(busy(at(9), at(10))), mon, now = at(13))
        assertEquals(at(13), next?.start)
    }

    @Test
    fun `a spent day offers nothing`() {
        assertNull(Windows.next(emptyList(), mon, now = at(23)))
        assertNull(Windows.next(listOf(busy(at(8), at(22))), mon, now = at(9)))
    }

    @Test
    fun `an hour you chose beats six hours you did not`() {
        // The whole reason a flower is worth storing: an empty Sunday offers
        // fourteen hours, and none of them is the hour somebody wrote down.
        val next = Windows.next(listOf(flower(at(19), at(20))), mon, now = at(8))
        assertEquals(at(19) to at(20), next?.start to next?.end)
        assertTrue(next?.chosen == true)
    }

    @Test
    fun `a flower already behind you is not offered`() {
        // Half past eight on a Monday: this morning's good hour has gone, so
        // the card falls back to the arithmetic.
        val next = Windows.next(listOf(flower(at(7), at(8))), mon, now = at(8, 30))
        assertEquals(false, next?.chosen)
        assertEquals(at(8, 30), next?.start)
    }

    @Test
    fun `a flower under way is offered from now`() {
        val next = Windows.next(listOf(flower(at(19), at(21))), mon, now = at(20))
        assertEquals(at(20) to at(21), next?.start to next?.end)
        assertTrue(next?.chosen == true)
    }

    @Test
    fun `minutes are what the card prints`() {
        assertEquals(50, Windows.Window(at(20, 40), at(21, 30)).minutes)
    }

    // --- placing, which is also erasing --------------------------------------

    @Test
    fun `placing on empty space just adds it`() {
        val week = Windows.place(emptyList(), busy(at(9), at(11)))
        assertEquals(1, week.size)
    }

    @Test
    fun `a thorn dropped on a flower cuts it in two`() {
        val week = Windows.place(listOf(flower(at(18), at(22))), busy(at(19), at(20)))
        val flowers = week.filter { it.kind == BlockKind.FREE }.sortedBy { it.start }
        assertEquals(2, flowers.size)
        assertEquals(at(18) to at(19), flowers[0].start to flowers[0].end)
        assertEquals(at(20) to at(22), flowers[1].start to flowers[1].end)
    }

    @Test
    fun `a block covered end to end is gone, not shortened`() {
        val week = Windows.place(listOf(flower(at(19), at(20))), busy(at(18), at(22)))
        assertEquals(1, week.size)
        assertEquals(BlockKind.BUSY, week[0].kind)
    }

    @Test
    fun `placing clips the overlapping end and leaves the rest`() {
        val week = Windows.place(listOf(busy(at(9), at(12))), flower(at(11), at(14)))
        val classes = week.filter { it.kind == BlockKind.BUSY }
        assertEquals(1, classes.size)
        assertEquals(at(9) to at(11), classes[0].start to classes[0].end)
    }

    @Test
    fun `another day is left alone`() {
        val week = Windows.place(
            listOf(busy(at(9), at(12), DayOfWeek.TUESDAY)),
            busy(at(9), at(12), mon),
        )
        assertEquals(2, week.size)
    }

    @Test
    fun `the label on a block survives being clipped`() {
        // Splitting a class in half must not quietly drop what it was called:
        // the label is the only thing on this screen that is the user's words.
        val week = Windows.place(listOf(busy(at(9), at(13))), flower(at(10), at(11)))
        val classes = week.filter { it.kind == BlockKind.BUSY }
        assertEquals(2, classes.size)
        assertTrue(classes.all { it.label == "SECRET_LABEL" })
    }

    // --- how the card says it -----------------------------------------------

    @Test
    fun `under an hour is counted in minutes`() {
        assertEquals("50 unhurried minutes", Windows.phrase(Windows.Window(at(20, 40), at(21, 30))))
    }

    @Test
    fun `a whole free day is not three hundred and seventy three minutes`() {
        // The bug this exists for: with nothing blocked, the honest arithmetic
        // answer is absurd as copy.
        val all = Windows.Window(at(15, 46), at(22))
        assertEquals("6 unhurried hours", Windows.phrase(all))
    }

    @Test
    fun `one hour reads as one hour`() {
        assertEquals("an unhurried hour", Windows.phrase(Windows.Window(at(9), at(10))))
    }

    @Test
    fun `an hour and fifty minutes does not present itself as one`() {
        assertEquals("2 unhurried hours", Windows.phrase(Windows.Window(at(9), at(10, 50))))
    }

    @Test
    fun `the boundary between minutes and hours is exactly an hour`() {
        assertEquals("59 unhurried minutes", Windows.phrase(Windows.Window(at(9), at(9, 59))))
        assertEquals("an unhurried hour", Windows.phrase(Windows.Window(at(9), at(10))))
    }

    /** A Monday, so [busyAt] has a real instant to test against. */
    private fun monday(hour: Int, minute: Int) =
        java.time.ZonedDateTime.of(
            java.time.LocalDate.of(2026, 9, 14),
            LocalTime.of(hour, minute),
            java.time.ZoneId.of("Asia/Kolkata"),
        )

    // --- how full a day is --------------------------------------------------

    @Test
    fun `an unmarked day is empty`() {
        assertEquals(0.0, Windows.load(emptyList(), mon), 1e-9)
        assertEquals(Weather.CLEAR, Windows.weatherFor(emptyList(), mon))
    }

    @Test
    fun `a day of lectures reads as a heavy one`() {
        val week = listOf(
            busy(at(9), at(13)),
            busy(at(14), at(18)),
            busy(at(19), at(21)),
        )
        // Ten of the fourteen waking hours: heavy, and still not the worst a
        // week can do. Storm is kept for a day with almost nothing left in it.
        assertEquals(10.0 / 14.0, Windows.load(week, mon), 1e-9)
        assertEquals(Weather.RAIN, Windows.weatherFor(week, mon))
        assertEquals(
            Weather.STORM,
            Windows.weatherFor(listOf(busy(at(8), at(21, 30))), mon),
        )
    }

    @Test
    fun `two lectures that run into each other are not counted twice`() {
        // The overlap is an hour. Weighed naively the day comes out fuller
        // than it is, and an overlap is easy to make on a grid you drag on.
        val overlapping = listOf(busy(at(9), at(13)), busy(at(12), at(15)))
        val same = listOf(busy(at(9), at(15)))
        assertEquals(Windows.load(same, mon), Windows.load(overlapping, mon), 1e-9)
    }

    @Test
    fun `time outside the waking window does not weigh on the day`() {
        // Somebody marking sleep busy is describing a night, not a full day.
        val asleep = listOf(busy(LocalTime.MIDNIGHT, at(7)))
        assertEquals(0.0, Windows.load(asleep, mon), 1e-9)
    }

    @Test
    fun `a flower never makes the day look busier`() {
        // The asymmetry this file exists to guard, in one more place: room you
        // kept is not work you took on.
        val week = listOf(busy(at(9), at(12)), flower(at(18), at(21)))
        assertEquals(Windows.load(listOf(busy(at(9), at(12))), mon), Windows.load(week, mon), 1e-9)
    }

    @Test
    fun `a fuller day never suggests lighter weather`() {
        // Monotonic, so the guess cannot go backwards as the week fills up.
        val order = Weather.entries
        var last = -1
        for (hours in 0..14) {
            val week = if (hours == 0) emptyList() else listOf(busy(at(8), at(8 + hours)))
            val here = order.indexOf(Windows.weatherFor(week, mon))
            assertTrue("$hours hours went backwards", here >= last)
            last = here
        }
        assertEquals(Weather.STORM, last.let { order[it] })
    }

    @Test
    fun `the day is never fuller than full`() {
        val week = listOf(busy(LocalTime.MIDNIGHT, LocalTime.of(23, 59)))
        assertTrue(Windows.load(week, mon) <= 1.0)
    }
    @Test
    fun `a guess is only a guess until somebody touches the slider`() {
        // weatherSetOn is the whole protection for writing an inferred mood
        // into settings: the value is real, and the marker says whose it is.
        val today = LocalDate.of(2026, 9, 19)
        val fresh = UserSettings()
        assertNotEquals(today, fresh.weatherSetOn)

        val theirs = fresh.copy(weather = Weather.STORM, weatherSetOn = today)
        assertEquals(today, theirs.weatherSetOn)

        // Yesterday's answer does not stand in for today's. A stale mood left
        // showing is worse than an honest guess.
        val yesterday = theirs.copy(weatherSetOn = today.minusDays(1))
        assertNotEquals(today, yesterday.weatherSetOn)
    }

    @Test
    fun `a busier day guesses a heavier weather`() {
        // The property that matters, rather than the exact bands: more of the
        // day booked can never guess a lighter weather than less of it.
        val order = Weather.entries
        var last = -1
        for (hours in 0..12) {
            val blocks = if (hours == 0) emptyList() else listOf(
                WeekBlock(
                    day = DayOfWeek.MONDAY,
                    start = LocalTime.of(9, 0),
                    end = LocalTime.of(9 + hours, 0),
                ),
            )
            val here = order.indexOf(Windows.weatherFor(blocks, DayOfWeek.MONDAY))
            assertTrue("a fuller day guessed lighter at " + hours + "h", here >= last)
            last = here
        }
    }

}
