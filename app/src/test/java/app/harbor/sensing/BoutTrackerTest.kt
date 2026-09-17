package app.harbor.sensing

import app.harbor.domain.CuePolicy.Signal
import app.harbor.domain.TriggerSource
import app.harbor.sensing.BoutTracker.Activity
import app.harbor.sensing.BoutTracker.Event
import app.harbor.sensing.BoutTracker.Kind
import app.harbor.sensing.BoutTracker.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BoutTrackerTest {

    private val t0: Instant = Instant.parse("2026-09-10T09:00:00Z")

    private fun at(minutes: Long) = t0.plus(Duration.ofMinutes(minutes))

    private fun enter(activity: Activity, minutes: Long) =
        Event(activity, Kind.ENTER, at(minutes))

    private fun exit(activity: Activity, minutes: Long) =
        Event(activity, Kind.EXIT, at(minutes))

    /** Feed a sequence from empty state, returning every signal produced. */
    private fun signalsFrom(vararg events: Event): List<Signal> {
        var state = State()
        return events.mapNotNull { event ->
            val step = BoutTracker.advance(state, event)
            state = step.state
            step.signal
        }
    }

    // --- the shape we are actually looking for ----------------------------

    @Test
    fun a_walk_that_ends_in_stillness_produces_one_signal() {
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            exit(Activity.WALKING, 25),
            enter(Activity.STILL, 25),
        )
        assertEquals(1, signals.size)

        val signal = signals.single()
        assertEquals(TriggerSource.WALKING_STOP, signal.source)
        assertEquals(25, signal.activeMinutes)
        assertEquals(at(25), signal.stillSince)
    }

    @Test
    fun the_walk_is_measured_to_the_exit_not_to_the_onset_of_stillness() {
        // The detector does not declare stillness the moment somebody stops.
        // Standing at a door for two minutes before it catches up is not
        // walking, and counting it made every bout longer than the walk.
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            exit(Activity.WALKING, 10),
            enter(Activity.STILL, 12),
        )
        assertEquals(10, signals.single().activeMinutes)
    }

    @Test
    fun the_stop_is_still_the_onset_of_stillness_not_the_exit() {
        // The length comes from the EXIT; the moment does not. The settle
        // window is measured from the stop, and firing against the EXIT would
        // let a reminder arrive before somebody had finished stopping.
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            exit(Activity.WALKING, 10),
            enter(Activity.STILL, 12),
        )
        assertEquals(at(12), signals.single().stillSince)
    }

    @Test
    fun walking_again_after_an_exit_measures_to_the_later_exit() {
        // Paused at a crossing long enough for an EXIT but not long enough for
        // stillness. The walk resumed, so the first EXIT is not where it
        // ended.
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            exit(Activity.WALKING, 4),
            enter(Activity.WALKING, 5),
            exit(Activity.WALKING, 20),
            enter(Activity.STILL, 22),
        )
        assertEquals(20, signals.single().activeMinutes)
    }

    @Test
    fun an_exit_with_no_walk_open_is_ignored() {
        // A stray EXIT must not open a bout or leave anything behind that a
        // later stillness could close against.
        val signals = signalsFrom(
            exit(Activity.WALKING, 3),
            enter(Activity.STILL, 5),
        )
        assertEquals(emptyList<Signal>(), signals)
    }

    @Test
    fun an_exit_stamped_after_the_stillness_cannot_stretch_the_walk() {
        // Events inside one batch are sorted before they reach the tracker,
        // but a clock correction can still put an EXIT past the stop it
        // precedes. The walk ends at the stop at the latest.
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            exit(Activity.WALKING, 30),
            enter(Activity.STILL, 20),
        )
        assertEquals(20, signals.single().activeMinutes)
    }

    @Test
    fun a_closed_bout_reports_the_window_it_measured() {
        val step = run {
            var state = State()
            var last: BoutTracker.Step? = null
            for (event in listOf(
                enter(Activity.WALKING, 0),
                exit(Activity.WALKING, 10),
                enter(Activity.STILL, 12),
            )) {
                last = BoutTracker.advance(state, event)
                state = last.state
            }
            last!!
        }
        assertNotNull(step.bout)
        val bout = step.bout!!
        assertEquals(at(0), bout.startedAt)
        assertEquals(at(10), bout.endedAt)
        assertEquals(at(12), bout.stillSince)
    }

    @Test
    fun stillness_ends_the_bout_even_without_an_exit_event() {
        // EXIT is not guaranteed to arrive. Stillness is the real terminator.
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            enter(Activity.STILL, 15),
        )
        assertEquals(15, signals.single().activeMinutes)
    }

    // --- the messy stream -------------------------------------------------

    @Test
    fun stillness_with_no_walk_behind_it_produces_nothing() {
        // The overwhelmingly common case: stillness following more stillness.
        assertEquals(emptyList<Signal>(), signalsFrom(enter(Activity.STILL, 5)))
    }

    @Test
    fun a_duplicate_walking_enter_keeps_the_earlier_start() {
        // Two ENTERs and no EXIT is far more likely to be a repeated event
        // than a second walk, so the bout should measure from the first.
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            enter(Activity.WALKING, 3),
            enter(Activity.STILL, 20),
        )
        assertEquals(20, signals.single().activeMinutes)
    }

    @Test
    fun getting_into_a_vehicle_discards_the_bout() {
        // The walk ended, but not in the stillness the cue is about. If this
        // were left open, sitting still in the car later would fire a cue for
        // a "walk" that was really a commute.
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            enter(Activity.OTHER, 12),
            enter(Activity.STILL, 30),
        )
        assertEquals(emptyList<Signal>(), signals)
    }

    @Test
    fun a_bout_longer_than_the_ceiling_is_discarded() {
        // A missed EXIT leaves walkingSince stale. Without the ceiling this
        // becomes a huge bout that clears any threshold the user could set.
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            enter(Activity.STILL, BoutTracker.MAX_BOUT.toMinutes() + 1),
        )
        assertEquals(emptyList<Signal>(), signals)
    }

    @Test
    fun a_bout_exactly_at_the_ceiling_still_counts() {
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            enter(Activity.STILL, BoutTracker.MAX_BOUT.toMinutes()),
        )
        assertEquals(1, signals.size)
    }

    @Test
    fun a_backwards_clock_produces_nothing_rather_than_a_negative_walk() {
        // Timezone change, NTP correction, or a stale queued event.
        val state = State(walkingSince = at(30))
        val step = BoutTracker.advance(state, enter(Activity.STILL, 10))
        assertNull(step.signal)
        assertNull(step.state.walkingSince)
    }

    @Test
    fun partial_minutes_are_truncated_not_rounded() {
        // A 9:59 walk must not clear a 10-minute threshold the user chose.
        val state = State(walkingSince = t0)
        val step = BoutTracker.advance(
            state,
            Event(Activity.STILL, Kind.ENTER, t0.plus(Duration.ofSeconds(599))),
        )
        assertEquals(9, (step.signal!!).activeMinutes)
    }

    // --- state hygiene ----------------------------------------------------

    @Test
    fun the_bout_is_cleared_after_firing_so_it_cannot_fire_twice() {
        var state = State()
        for (event in listOf(enter(Activity.WALKING, 0), enter(Activity.STILL, 20))) {
            state = BoutTracker.advance(state, event).state
        }
        assertNull(state.walkingSince)

        // More stillness arrives, as it always does.
        val again = BoutTracker.advance(state, enter(Activity.STILL, 21))
        assertNull(again.signal)
    }

    @Test
    fun an_exit_event_alone_changes_nothing() {
        val state = State(walkingSince = t0)
        val step = BoutTracker.advance(state, exit(Activity.WALKING, 10))
        assertEquals(t0, step.state.walkingSince)
        assertNull(step.signal)
    }

    @Test
    fun two_separate_walks_produce_two_signals() {
        val signals = signalsFrom(
            enter(Activity.WALKING, 0),
            enter(Activity.STILL, 20),
            enter(Activity.WALKING, 60),
            enter(Activity.STILL, 75),
        )
        assertEquals(2, signals.size)
        assertNotNull(signals[1])
    }
}
