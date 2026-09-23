package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class BeeTest {

    @Test
    fun `the same moment always gives the same place`() {
        assertEquals(Bee.at(7.5), Bee.at(7.5))
    }

    @Test
    fun `it stays within reach of where you are looking`() {
        // Bee.REACH and not Bee.ROAM: the path is two circles added, and both
        // axes can peak together, so the bound is the diagonal. This test
        // asserted the side first and caught the bee just outside it.
        var t = 0.0
        var furthest = 0.0
        while (t < 240.0) {
            val at = Bee.at(t)
            furthest = maxOf(furthest, hypot(at.x, at.y))
            t += 0.05
        }
        assertTrue("strayed to $furthest", furthest <= Bee.REACH)
        // And it does use most of the room it is given -- a bound nothing
        // approaches is not describing the path.
        assertTrue("only reached $furthest", furthest > Bee.ROAM * 0.8)
    }

    @Test
    fun `it stays above the ground and under a sensible ceiling`() {
        var t = 0.0
        while (t < 120.0) {
            val z = Bee.at(t).z
            assertTrue("dipped to $z", z > 0.0)
            assertTrue("climbed to $z", z <= Bee.HOVER * 1.35)
            t += 0.05
        }
    }

    @Test
    fun `the path does not close on itself inside a sitting`() {
        // Two circles at rates that do not divide: if they did, the bee would
        // retrace one loop and the eye would catch it inside a minute.
        val start = Bee.at(0.0)
        val oneLoopLater = Bee.at(Bee.LOOP)
        assertTrue(
            "the path repeats after one loop",
            hypot(oneLoopLater.x - start.x, oneLoopLater.y - start.y) > 0.5,
        )
    }

    @Test
    fun `it never stops moving`() {
        var t = 0.0
        var stalls = 0
        while (t < 60.0) {
            val a = Bee.at(t)
            val b = Bee.at(t + 0.1)
            if (hypot(b.x - a.x, b.y - a.y) < 0.02) stalls++
            t += 0.1
        }
        // A hover or two at a turn is a bee; a hundred is a bug on the screen.
        assertTrue("stalled $stalls times", stalls < 12)
    }

    @Test
    fun `it faces the way it is going`() {
        val t = 3.0
        val was = Bee.at(t)
        val now = Bee.at(t + 0.14)
        val expected = kotlin.math.atan2(now.y - was.y, now.x - was.x)
        val got = Bee.heading(t + 0.14)
        val apart = abs(((got - expected) + Math.PI) % (2 * Math.PI) - Math.PI)
        assertTrue("facing $got, going $expected", apart < 0.001)
    }

    @Test
    fun `the path closes exactly at its period, so the clock can wrap there`() {
        // The drawing animates seconds 0..PERIOD and starts again. If the path
        // is not back where it began at that instant, the bee teleports once
        // every cycle -- which is the sort of thing nobody reports and
        // everybody notices.
        val start = Bee.at(0.0)
        val wrapped = Bee.at(Bee.PERIOD)
        assertEquals(start.x, wrapped.x, 0.0001)
        assertEquals(start.y, wrapped.y, 0.0001)
        assertEquals(start.z, wrapped.z, 0.0001)
    }

    @Test
    fun `the wings beat both ways`() {
        var high = false
        var low = false
        var t = 0.0
        while (t < 1.0) {
            if (Bee.wing(t) > 0.8) high = true
            if (Bee.wing(t) < -0.8) low = true
            t += 0.005
        }
        assertTrue(high && low)
    }

    // --- only close up ----------------------------------------------------

    @Test
    fun `there is no bee on a field that is still a map`() {
        assertEquals(0.0, Bee.showing(0.0), 0.0001)
        assertEquals(0.0, Bee.showing(0.18), 0.0001)
    }

    @Test
    fun `it is all the way there once the grass is up`() {
        assertEquals(1.0, Bee.showing(1.0), 0.0001)
    }

    @Test
    fun `it arrives gradually rather than switching on`() {
        val early = Bee.showing(0.3)
        val mid = Bee.showing(0.45)
        val late = Bee.showing(0.6)
        assertTrue(early > 0.0 && early < mid && mid < late && late < 1.0)
    }

    @Test
    fun `it is barely there at the start of the fade rather than half there`() {
        // Squared on purpose: a linear fade left a smudge visible through most
        // of the zoom, which read as dirt on the screen rather than as a bee.
        assertTrue(Bee.showing(0.3) < 0.1)
    }
}
