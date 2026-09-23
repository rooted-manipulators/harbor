package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The daily quiet stretch.
 *
 * Almost every one of these is about midnight. A quiet period that does not
 * cross it is the unusual case, and the arithmetic that is right for the
 * unusual case is wrong for the ordinary one — which is the bug this whole
 * file exists to make impossible.
 */
class QuietHoursTest {

    private fun quiet(from: String, to: String, on: Boolean = true) =
        QuietHours(LocalTime.parse(from), LocalTime.parse(to), on)

    private fun at(t: String) = LocalTime.parse(t)

    // --- off until somebody turns it on -----------------------------------

    @Test
    fun `it covers nothing at all until it is switched on`() {
        val night = quiet("22:30", "07:30", on = false)
        assertFalse(night.covers(at("03:00")))
        assertFalse(night.covers(at("23:00")))
    }

    @Test
    fun `it ships switched off`() {
        assertFalse(QuietHours().enabled)
        assertFalse(QuietHours().covers(at("03:00")))
    }

    // --- the ordinary case, which crosses midnight ------------------------

    @Test
    fun `a night that crosses midnight covers both sides of it`() {
        val night = quiet("22:30", "07:30")
        assertTrue(night.covers(at("22:30")))
        assertTrue(night.covers(at("23:59")))
        assertTrue(night.covers(at("00:00")))
        assertTrue(night.covers(at("03:00")))
        assertTrue(night.covers(at("07:29")))
    }

    @Test
    fun `and leaves the day alone`() {
        val night = quiet("22:30", "07:30")
        assertFalse(night.covers(at("07:30")))
        assertFalse(night.covers(at("12:00")))
        assertFalse(night.covers(at("22:29")))
    }

    // --- the unusual case, which does not ---------------------------------

    @Test
    fun `an afternoon nap covers only the afternoon`() {
        val nap = quiet("14:00", "15:30")
        assertTrue(nap.covers(at("14:00")))
        assertTrue(nap.covers(at("15:29")))
        assertFalse(nap.covers(at("15:30")))
        assertFalse(nap.covers(at("13:59")))
        assertFalse(nap.covers(at("03:00")))
    }

    // --- edges ------------------------------------------------------------

    @Test
    fun `start is inclusive and end is exclusive, as a block is`() {
        // Otherwise a quiet period ending at 07:30 and a lecture starting at
        // 07:30 both claim the minute, and which wins depends on call order.
        val night = quiet("22:30", "07:30")
        assertTrue(night.covers(at("22:30")))
        assertFalse(night.covers(at("07:30")))
    }

    @Test
    fun `a zero length quiet period covers nothing rather than everything`() {
        // The wrapping branch would otherwise read "at >= 9 or at < 9", which
        // is every minute of the day -- an app that never speaks again.
        val none = quiet("09:00", "09:00")
        assertFalse(none.covers(at("09:00")))
        assertFalse(none.covers(at("03:00")))
        assertFalse(none.covers(at("21:00")))
    }

    // --- how long it runs -------------------------------------------------

    @Test
    fun `a night's length counts across midnight`() {
        assertEquals(9 * 60, quiet("22:30", "07:30").minutes)
    }

    @Test
    fun `an afternoon's length is just the afternoon`() {
        assertEquals(90, quiet("14:00", "15:30").minutes)
    }

    // --- it suppresses a cue exactly as a lecture does ---------------------

    @Test
    fun `a cue in the quiet stretch is held back with no blocks at all`() {
        val threeAm = java.time.ZonedDateTime.parse("2026-09-23T03:00:00Z")
        assertTrue(Windows.busyAt(emptyList(), threeAm, quiet("22:30", "07:30")))
    }

    @Test
    fun `a cue outside it is not`() {
        val noon = java.time.ZonedDateTime.parse("2026-09-23T12:00:00Z")
        assertFalse(Windows.busyAt(emptyList(), noon, quiet("22:30", "07:30")))
    }

    @Test
    fun `switched off, it holds nothing back`() {
        val threeAm = java.time.ZonedDateTime.parse("2026-09-23T03:00:00Z")
        assertFalse(Windows.busyAt(emptyList(), threeAm, quiet("22:30", "07:30", on = false)))
    }

    @Test
    fun `it applies to every day rather than to one`() {
        // The point of it being a setting and not seven blocks. Wednesday and
        // Sunday get the same answer without anybody drawing anything twice.
        val night = quiet("22:30", "07:30")
        val wednesday = java.time.ZonedDateTime.parse("2026-09-23T23:00:00Z")
        val sunday = java.time.ZonedDateTime.parse("2026-09-27T23:00:00Z")
        assertTrue(Windows.busyAt(emptyList(), wednesday, night))
        assertTrue(Windows.busyAt(emptyList(), sunday, night))
    }

    @Test
    fun `asking without one is the question it always was`() {
        val threeAm = java.time.ZonedDateTime.parse("2026-09-23T03:00:00Z")
        assertFalse(Windows.busyAt(emptyList(), threeAm))
    }
}
