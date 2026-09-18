package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The dial's arithmetic.
 *
 * Worth testing properly for the same reason [CuePolicy] is: the reminder drag
 * is a gesture people will judge by feel, and "it felt wrong" is not something
 * you can debug. Everything the finger is allowed to do is decided here.
 */
class DayArcsTest {

    private fun block(
        from: String,
        to: String,
        kind: BlockKind = BlockKind.BUSY,
        day: DayOfWeek = DayOfWeek.TUESDAY,
    ) = WeekBlock(day, LocalTime.parse(from), LocalTime.parse(to), kind)

    // --- what the day says ------------------------------------------------

    @Test
    fun `a day with nothing entered has no arcs at all`() {
        assertEquals(emptyList<DayArcs.Arc>(), DayArcs.of(emptyList(), DayOfWeek.TUESDAY))
    }

    @Test
    fun `another day's blocks are not this day's arcs`() {
        val week = listOf(block("09:00", "11:00", day = DayOfWeek.MONDAY))
        assertEquals(emptyList<DayArcs.Arc>(), DayArcs.of(week, DayOfWeek.TUESDAY))
    }

    @Test
    fun `a busy block becomes a thorn arc in minutes`() {
        val arcs = DayArcs.of(listOf(block("09:00", "11:00")), DayOfWeek.TUESDAY)
        assertEquals(listOf(DayArcs.Arc(540, 660, BlockKind.BUSY)), arcs)
    }

    @Test
    fun `two lectures that touch draw as one thorn`() {
        val arcs = DayArcs.of(
            listOf(block("09:00", "11:00"), block("11:00", "12:30")),
            DayOfWeek.TUESDAY,
        )
        assertEquals(listOf(DayArcs.Arc(540, 750, BlockKind.BUSY)), arcs)
    }

    @Test
    fun `overlapping lectures merge rather than double up`() {
        val arcs = DayArcs.of(
            listOf(block("09:00", "11:00"), block("10:00", "12:00")),
            DayOfWeek.TUESDAY,
        )
        assertEquals(listOf(DayArcs.Arc(540, 720, BlockKind.BUSY)), arcs)
    }

    @Test
    fun `busy laid over free cuts the free arc in two`() {
        val arcs = DayArcs.of(
            listOf(
                block("18:00", "22:00", BlockKind.FREE),
                block("19:00", "20:00", BlockKind.BUSY),
            ),
            DayOfWeek.TUESDAY,
        )
        assertEquals(
            listOf(
                DayArcs.Arc(1080, 1140, BlockKind.FREE),
                DayArcs.Arc(1140, 1200, BlockKind.BUSY),
                DayArcs.Arc(1200, 1320, BlockKind.FREE),
            ),
            arcs,
        )
    }

    @Test
    fun `free time buried under busy time disappears entirely`() {
        val arcs = DayArcs.of(
            listOf(
                block("19:00", "20:00", BlockKind.FREE),
                block("18:00", "22:00", BlockKind.BUSY),
            ),
            DayOfWeek.TUESDAY,
        )
        assertEquals(listOf(DayArcs.Arc(1080, 1320, BlockKind.BUSY)), arcs)
    }

    @Test
    fun `arcs come back in the order they happen`() {
        val arcs = DayArcs.of(
            listOf(
                block("20:00", "21:00", BlockKind.FREE),
                block("09:00", "10:00"),
                block("13:00", "14:00", BlockKind.FREE),
            ),
            DayOfWeek.TUESDAY,
        )
        assertEquals(listOf(540, 780, 1200), arcs.map { it.from })
    }

    // --- the dial ---------------------------------------------------------

    @Test
    fun `midnight is the top of the dial and noon is the bottom`() {
        assertEquals(0f, DayArcs.degreesAt(0), 0.001f)
        assertEquals(180f, DayArcs.degreesAt(12 * 60), 0.001f)
        assertEquals(270f, DayArcs.degreesAt(18 * 60), 0.001f)
    }

    @Test
    fun `an angle and a minute are the same fact`() {
        for (minute in 0 until DayArcs.DAY_MINUTES step 37) {
            assertEquals(minute, DayArcs.minuteAt(DayArcs.degreesAt(minute)))
        }
    }

    @Test
    fun `dragging past midnight wraps rather than failing`() {
        assertEquals(0, DayArcs.minuteAt(360f))
        assertEquals(DayArcs.minuteAt(10f), DayArcs.minuteAt(370f))
        assertEquals(DayArcs.minuteAt(350f), DayArcs.minuteAt(-10f))
    }

    // --- where a reminder may rest ----------------------------------------

    @Test
    fun `a reminder may rest anywhere on its own free arc`() {
        val arc = DayArcs.Arc(1080, 1320, BlockKind.FREE)
        assertTrue(DayArcs.mayRest(arc, 1080))
        assertTrue(DayArcs.mayRest(arc, 1200))
        assertTrue(DayArcs.mayRest(arc, 1320))
    }

    @Test
    fun `a reminder may not rest outside its own arc`() {
        val arc = DayArcs.Arc(1080, 1320, BlockKind.FREE)
        assertFalse(DayArcs.mayRest(arc, 1079))
        assertFalse(DayArcs.mayRest(arc, 1321))
    }

    @Test
    fun `a thorn never holds a reminder however far inside it you drag`() {
        val arc = DayArcs.Arc(540, 660, BlockKind.BUSY)
        assertFalse(DayArcs.mayRest(arc, 600))
    }

    @Test
    fun `dragging off the end of an arc stops at the end`() {
        val arc = DayArcs.Arc(1080, 1320, BlockKind.FREE)
        assertEquals(1320, DayArcs.rest(arc, 1400))
        assertEquals(1080, DayArcs.rest(arc, 900))
    }

    @Test
    fun `a rest that was clamped is not the rest that was asked for`() {
        // This difference is the whole haptic: the surface buzzes when what it
        // asked for and what it got are not the same minute.
        val arc = DayArcs.Arc(1080, 1320, BlockKind.FREE)
        assertEquals(DayArcs.snap(1200), DayArcs.rest(arc, 1200))
        assertTrue(DayArcs.rest(arc, 1400) != DayArcs.snap(1400))
    }

    @Test
    fun `a reminder lands on a quarter hour`() {
        assertEquals(1290, DayArcs.snap(1291))
        assertEquals(1290, DayArcs.snap(1284))
        assertEquals(1305, DayArcs.snap(1300))
        assertEquals(0, DayArcs.snap(7))
    }

    @Test
    fun `the arc under a minute is the one that holds it`() {
        val arcs = listOf(
            DayArcs.Arc(540, 660, BlockKind.BUSY),
            DayArcs.Arc(1080, 1320, BlockKind.FREE),
        )
        assertEquals(BlockKind.BUSY, DayArcs.arcAt(arcs, 600)?.kind)
        assertEquals(BlockKind.FREE, DayArcs.arcAt(arcs, 1200)?.kind)
        assertNull(DayArcs.arcAt(arcs, 900))
    }

    @Test
    fun `a reminder first lands in the middle of the arc it was planted on`() {
        assertEquals(1200, DayArcs.Arc(1080, 1320, BlockKind.FREE).middle)
    }

    // --- saying the time --------------------------------------------------

    @Test
    fun `the dial says a time the way the reference does`() {
        assertEquals("21:30", DayArcs.label(21 * 60 + 30))
        assertEquals("00:00", DayArcs.label(0))
        assertEquals("09:05", DayArcs.label(9 * 60 + 5))
    }

    @Test
    fun `the last minute of the day is still a time`() {
        assertEquals(LocalTime.of(23, 59), DayArcs.at(DayArcs.DAY_MINUTES - 1))
        assertEquals(LocalTime.of(23, 59), DayArcs.at(DayArcs.DAY_MINUTES))
    }
}
