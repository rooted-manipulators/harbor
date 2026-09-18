package app.harbor.sensing

import app.harbor.sensing.ScrollWatch.Ev
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The measurement, which is the part of the scrolling trigger with rules in
 * it.
 *
 * Everything else in [ScrollWatch] is a system call. This is the fold over
 * the event stream, and it decides the one number the trigger acts on — how
 * long you have been in this app — from a stream that does not say so
 * anywhere. Every test below is a real shape of that stream, written out
 * because reading the fold is not enough to be sure of any of them.
 */
class ScrollWatchTest {

    private val start: Instant = Instant.parse("2026-09-19T14:00:00Z")
    private val window: Instant = start.minus(Duration.ofHours(3))

    private fun at(minutes: Long): Instant = start.plus(Duration.ofMinutes(minutes))

    private fun front(pkg: String, minutes: Long) = Ev(FOREGROUND, pkg, at(minutes))
    private fun back(pkg: String, minutes: Long) = Ev(BACKGROUND, pkg, at(minutes))
    private fun stopped(pkg: String, minutes: Long) = Ev(STOPPED, pkg, at(minutes))
    private fun screenOff(minutes: Long) = Ev(SCREEN_OFF, "android", at(minutes))
    private fun locked(minutes: Long) = Ev(KEYGUARD, "android", at(minutes))

    private fun fold(vararg events: Ev) = ScrollWatch.fold(events.toList(), window)

    // --- the basic shape --------------------------------------------------

    @Test
    fun `nothing in front is not a stretch`() {
        assertNull(fold())
    }

    @Test
    fun `one app in front, since it came to the front`() {
        assertEquals(
            ScrollWatch.Stretch("com.feed", at(0)),
            fold(front("com.feed", 0)),
        )
    }

    @Test
    fun `switching app starts the clock again`() {
        val stretch = fold(
            front("com.feed", 0),
            front("com.chat", 10),
        )
        assertEquals(ScrollWatch.Stretch("com.chat", at(10)), stretch)
    }

    // --- the one that makes a real session measurable ---------------------

    @Test
    fun `a session survives its own churn`() {
        // What twenty minutes in one app actually looks like from outside: a
        // resume every time you open a reel, a profile, a story, each one
        // preceded by a pause of the activity you were on. If any of that
        // restarted the clock, no stretch would ever reach a threshold and
        // the trigger could not fire at all.
        val stretch = fold(
            front("com.feed", 0),
            back("com.feed", 3),
            front("com.feed", 3),
            back("com.feed", 8),
            stopped("com.feed", 8),
            front("com.feed", 8),
            back("com.feed", 19),
            front("com.feed", 19),
        )
        assertEquals(ScrollWatch.Stretch("com.feed", at(0)), stretch)
        assertEquals(25, stretch!!.minutesAt(at(25)))
    }

    @Test
    fun `a glance at something else and back is a new stretch`() {
        // Deliberate, and the conservative direction. Replying to a message
        // and returning genuinely is the same sitting to a person, but the
        // trigger cannot tell that from putting the phone down; the rule that
        // would keep the clock running is the same rule that would read an
        // afternoon as one session. Under-counting costs a late reminder.
        val stretch = fold(
            front("com.feed", 0),
            front("com.chat", 18),
            front("com.feed", 19),
        )
        assertEquals(ScrollWatch.Stretch("com.feed", at(19)), stretch)
    }

    // --- the pocket, which is where this was wrong ------------------------

    @Test
    fun `the screen going off ends the stretch`() {
        assertNull(
            fold(
                front("com.feed", 0),
                back("com.feed", 20),
                screenOff(20),
            ),
        )
    }

    @Test
    fun `an app resumed after the screen went off starts fresh`() {
        val stretch = fold(
            front("com.feed", 0),
            back("com.feed", 20),
            screenOff(20),
            locked(20),
            front("com.feed", 140),
        )
        assertEquals(ScrollWatch.Stretch("com.feed", at(140)), stretch)
    }

    @Test
    fun `two hours in a pocket is not two hours of scrolling`() {
        // The same journey with the screen events missing, which is what
        // API 26 and 27 give you and what some OEM builds give you on any
        // version. Without ScrollWatch.RESUME_GAP this returned a stretch
        // starting at minute zero -- so unlocking the phone would fire a
        // reminder instantly, every time, for a session that never happened.
        val stretch = fold(
            front("com.feed", 0),
            back("com.feed", 20),
            stopped("com.feed", 20),
            front("com.feed", 140),
        )
        assertEquals(ScrollWatch.Stretch("com.feed", at(140)), stretch)
        assertEquals(0, stretch!!.minutesAt(at(140)))
    }

    @Test
    fun `a pause shorter than the gap does not restart it`() {
        val away = ScrollWatch.RESUME_GAP.minusSeconds(30)
        val stretch = ScrollWatch.fold(
            listOf(
                front("com.feed", 0),
                back("com.feed", 10),
                Ev(FOREGROUND, "com.feed", at(10).plus(away)),
            ),
            window,
        )
        assertEquals(ScrollWatch.Stretch("com.feed", at(0)), stretch)
    }

    @Test
    fun `the gap is measured from when it went away, not from the last flicker`() {
        // A pause, then a second pause without an intervening resume, can
        // arrive when an app has several activities coming down. The stretch
        // has been away since the first one; measuring from the last would
        // shorten every gap and let the pocket case back in.
        val stretch = fold(
            front("com.feed", 0),
            back("com.feed", 10),
            stopped("com.feed", 12),
            front("com.feed", 14),
        )
        assertEquals(ScrollWatch.Stretch("com.feed", at(14)), stretch)
    }

    @Test
    fun `another app pausing does not touch our stretch`() {
        val stretch = fold(
            front("com.feed", 0),
            back("com.chat", 5),
            stopped("com.chat", 5),
        )
        assertEquals(ScrollWatch.Stretch("com.feed", at(0)), stretch)
    }

    // --- the edges of the window ------------------------------------------

    @Test
    fun `a stretch older than the window is dated to the window`() {
        // The events do not reach back far enough to say when this began, so
        // the only honest answer is the edge. It under-reports -- a four-hour
        // session is measured as three -- which delays a reminder and never
        // invents one.
        val stretch = ScrollWatch.fold(listOf(Ev(FOREGROUND, "com.feed", window.minusSeconds(60))), window)
        assertEquals(ScrollWatch.Stretch("com.feed", window), stretch)
    }

    @Test
    fun `a stretch with no start at all is dated to the window`() {
        // Possible on a device that reports a pause without the resume that
        // preceded it, because the resume fell outside the window.
        val stretch = ScrollWatch.fold(
            listOf(Ev(FOREGROUND, "com.feed", window), back("com.feed", 5)),
            window,
        )
        assertEquals(ScrollWatch.Stretch("com.feed", window), stretch)
    }

    @Test
    fun `events we do not understand are ignored rather than feared`() {
        // The stream carries a couple of dozen types and grows with each
        // Android version. Anything unrecognised must leave the measurement
        // exactly as it was -- a new event type should never be able to
        // silence the trigger or invent a session.
        val stretch = fold(
            front("com.feed", 0),
            Ev(7, "com.feed", at(4)),
            Ev(12, "com.other", at(6)),
            Ev(31, "android", at(9)),
        )
        assertEquals(ScrollWatch.Stretch("com.feed", at(0)), stretch)
    }

    @Test
    fun `minutes round down, so a threshold is reached and not approached`() {
        val stretch = ScrollWatch.Stretch("com.feed", at(0))
        assertEquals(19, stretch.minutesAt(at(0).plus(Duration.ofSeconds(19 * 60 + 59))))
        assertEquals(20, stretch.minutesAt(at(20)))
    }

    // --- asking twice, and not asking twice -------------------------------

    private val feed = ScrollWatch.Stretch("com.feed", at(0))

    private fun offer(fired: Boolean, atMinutes: Long) =
        SensingStore.Offer(feed, at(atMinutes), fired)

    @Test
    fun `a stretch nobody has asked about is asked about`() {
        assertEquals(true, SensingStore.shouldAsk(null, feed, at(20)))
    }

    @Test
    fun `a stretch that fired is never asked about again`() {
        // The poll keeps seeing the same session for as long as it lasts. If
        // a fire did not close the question, twenty minutes in one app would
        // produce a reminder, and twenty-two minutes another, until the
        // day's allowance was gone.
        assertEquals(false, SensingStore.shouldAsk(offer(true, 20), feed, at(22)))
        assertEquals(false, SensingStore.shouldAsk(offer(true, 20), feed, at(200)))
    }

    @Test
    fun `a stretch that was held is asked again, but not straight away`() {
        val held = offer(fired = false, atMinutes = 20)
        // Two minutes later is the same question.
        assertEquals(false, SensingStore.shouldAsk(held, feed, at(22)))
        // Ten minutes later, the reasons have had time to stop being true.
        // This is the case that was silently losing cues: a stretch refused
        // at the end of a busy block, with the block over and the scrolling
        // still going on.
        val retry = at(20).plus(SensingStore.RETRY)
        assertEquals(true, SensingStore.shouldAsk(held, feed, retry))
    }

    @Test
    fun `a new sitting gets its own hearing`() {
        val held = offer(fired = true, atMinutes = 20)
        val later = ScrollWatch.Stretch("com.feed", at(60))
        assertEquals(true, SensingStore.shouldAsk(held, later, at(85)))
    }

    @Test
    fun `a different app gets its own hearing`() {
        val held = offer(fired = true, atMinutes = 20)
        val other = ScrollWatch.Stretch("com.chat", at(0))
        assertEquals(true, SensingStore.shouldAsk(held, other, at(25)))
    }

    private companion object {
        // The platform numbers ScrollWatch works in, repeated here on
        // purpose. A test that imported the constants would agree with the
        // code by construction and could not catch one of them being wrong;
        // these are the values from the UsageEvents.Event documentation.
        const val FOREGROUND = 1
        const val BACKGROUND = 2
        const val SCREEN_OFF = 16
        const val KEYGUARD = 17
        const val STOPPED = 23
    }
}
