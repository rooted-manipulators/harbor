package app.harbor.domain

import app.harbor.domain.CuePolicy.DayState
import app.harbor.domain.CuePolicy.Decision
import app.harbor.domain.CuePolicy.Reason
import app.harbor.domain.CuePolicy.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class CuePolicyTest {

    private val now: Instant = Instant.parse("2026-09-10T17:30:00Z")
    private val today: LocalDate = now.atZone(ZoneOffset.UTC).toLocalDate()

    /** Cues on, suggested calibration. The state a consenting user is in. */
    private val settings = UserSettings(cuesEnabled = true)
    private val thresholds = settings.thresholds

    /**
     * Someone who has asked for a quiet gap.
     *
     * The suggestion no longer carries one, so the gap has to be set on
     * purpose to test it. That is the point of the tests below: the mechanism
     * still works for anybody who wants it, it is simply no longer imposed.
     */
    private val spaced = settings.copy(thresholds = thresholds.copy(cooldownMinutes = 120))

    /** A walk that just ended, long enough and settled enough to fire. */
    private fun walkSignal(
        activeMinutes: Int = 20,
        stillFor: Duration = CuePolicy.SETTLE.plusSeconds(30),
    ) = Signal(
        source = TriggerSource.WALKING_STOP,
        activeMinutes = activeMinutes,
        stillSince = now.minus(stillFor),
    )

    private fun entry(
        resolution: Resolution = Resolution.DISMISSED,
        occurredAt: Instant = now.minus(Duration.ofHours(6)),
        proposedTime: Instant? = null,
        reminderDone: Boolean = false,
    ) = LedgerEntry(
        id = UUID.randomUUID(),
        entryDate = today,
        cueId = UUID.randomUUID(),
        contactId = UUID.randomUUID(),
        triggerSource = TriggerSource.WALKING_STOP,
        thresholdSnapshot = thresholds,
        resolution = resolution,
        proposedTime = proposedTime,
        reminderDone = reminderDone,
        feedbackPulse = null,
        callMinutes = null,
        feeling = null,
        flower = null,
        topic = null,
        occurredAt = occurredAt,
    )

    private fun decide(
        signal: Signal = walkSignal(),
        settings: UserSettings = this.settings,
        entriesToday: List<LedgerEntry> = emptyList(),
        cuesToday: Int = entriesToday.size,
        lastCueAt: Instant? = entriesToday.maxOfOrNull { it.occurredAt },
        hasPendingReminder: Boolean = false,
        busyNow: Boolean = false,
        cuesTodayBySource: Map<TriggerSource, Int> = emptyMap(),
        source: TriggerSource? = null,
    ) = CuePolicy.decide(
        source?.let { signal.copy(source = it) } ?: signal,
        settings,
        DayState(entriesToday, cuesToday, lastCueAt, hasPendingReminder, busyNow, cuesTodayBySource),
        now,
    )

    // --- the happy path ---------------------------------------------------

    @Test
    fun fires_on_a_settled_walk_past_the_threshold_with_a_clean_day() {
        assertEquals(Decision.Fire, decide())
    }

    // --- the opt-out ------------------------------------------------------

    @Test
    fun holds_everything_when_the_user_has_cues_switched_off() {
        assertEquals(
            Decision.Hold(Reason.CUES_DISABLED),
            decide(settings = settings.copy(cuesEnabled = false)),
        )
    }

    @Test
    fun cues_are_off_by_default() {
        assertEquals(
            Decision.Hold(Reason.CUES_DISABLED),
            decide(settings = UserSettings()),
        )
    }

    // --- stage 2: threshold ----------------------------------------------

    @Test
    fun holds_when_the_walk_was_shorter_than_the_users_threshold() {
        assertEquals(
            Decision.Hold(Reason.BELOW_THRESHOLD),
            decide(walkSignal(activeMinutes = thresholds.walkingMinutes - 1)),
        )
    }

    @Test
    fun fires_when_the_walk_exactly_meets_the_threshold() {
        assertEquals(
            Decision.Fire,
            decide(walkSignal(activeMinutes = thresholds.walkingMinutes)),
        )
    }

    @Test
    fun a_session_signal_is_measured_against_the_session_threshold() {
        val signal = Signal(
            source = TriggerSource.SESSION_END,
            // Past the walking threshold, short of the session one. If the
            // policy reached for the wrong field this would fire.
            activeMinutes = thresholds.sessionMinutes - 1,
            stillSince = now.minus(CuePolicy.SETTLE.plusSeconds(30)),
        )
        assertEquals(Decision.Hold(Reason.BELOW_THRESHOLD), decide(signal))
    }

    // --- stage 3: suppression --------------------------------------------

    @Test
    fun a_call_today_suppresses() {
        assertEquals(
            Decision.Hold(Reason.ALREADY_CONNECTED_TODAY),
            decide(entriesToday = listOf(entry(resolution = Resolution.CALLED))),
        )
    }

    @Test
    fun a_reaction_counts_as_connecting_and_suppresses() {
        assertEquals(
            Decision.Hold(Reason.ALREADY_CONNECTED_TODAY),
            decide(entriesToday = listOf(entry(resolution = Resolution.REACTED))),
        )
    }

    @Test
    fun a_note_counts_as_connecting_and_suppresses() {
        assertEquals(
            Decision.Hold(Reason.ALREADY_CONNECTED_TODAY),
            decide(entriesToday = listOf(entry(resolution = Resolution.MESSAGE))),
        )
    }

    @Test
    fun a_dismissal_is_not_a_connection_and_does_not_suppress_on_its_own() {
        assertEquals(
            Decision.Fire,
            decide(
                entriesToday = listOf(entry(resolution = Resolution.DISMISSED)),
                cuesToday = 1,
                lastCueAt = now.minus(Duration.ofHours(6)),
            ),
        )
    }

    @Test
    fun holds_at_the_daily_cap() {
        assertEquals(
            Decision.Hold(Reason.DAILY_CAP_REACHED),
            decide(cuesToday = thresholds.dailyCap, lastCueAt = null),
        )
    }

    @Test
    fun the_cap_counts_cues_that_fired_not_entries_that_were_written() {
        // Two cues fired, only one was answered. The unanswered one still
        // spent part of the user's budget — this is the whole reason cues are
        // tracked separately from moments.
        assertEquals(
            Decision.Hold(Reason.DAILY_CAP_REACHED),
            decide(
                entriesToday = listOf(entry()),
                cuesToday = 2,
                lastCueAt = now.minus(Duration.ofHours(9)),
            ),
        )
    }

    @Test
    fun a_cap_of_one_suppresses_after_a_single_cue() {
        // A cap of zero is not expressible: the schema and the prototype both
        // require at least 1, because cues_enabled is how you turn cues off.
        // Two ways to say the same thing would only be a way to disagree.
        assertEquals(
            Decision.Hold(Reason.DAILY_CAP_REACHED),
            decide(
                settings = settings.copy(thresholds = thresholds.copy(dailyCap = 1)),
                cuesToday = 1,
                lastCueAt = now.minus(Duration.ofHours(9)),
            ),
        )
    }

    @Test
    fun `one trigger cannot eat the whole day's allowance`() {
        // The reason this exists: a walking stop happens once or twice a day,
        // a phone-in-hand session ends dozens of times. Against a shared
        // ceiling the frequent one takes every slot and the rare one is never
        // seen -- so a week of running both would end with a hundred of one
        // and four of the other.
        val split = settings.copy(
            thresholds = thresholds.copy(dailyCap = 4, sourceCap = 2),
        )
        // Two from this source already, and the day is only half spent.
        assertEquals(
            Decision.Hold(Reason.SOURCE_CAP_REACHED),
            decide(
                settings = split,
                cuesToday = 2,
                cuesTodayBySource = mapOf(TriggerSource.WALKING_STOP to 2),
                lastCueAt = now.minus(Duration.ofHours(9)),
            ),
        )
        // The other source still has its own share. This is the whole point:
        // the day is not full, only one trigger's part of it is.
        assertEquals(
            Decision.Fire,
            decide(
                settings = split,
                cuesToday = 2,
                cuesTodayBySource = mapOf(TriggerSource.WALKING_STOP to 2),
                source = TriggerSource.SESSION_END,
                lastCueAt = now.minus(Duration.ofHours(9)),
            ),
        )
    }

    @Test
    fun `a source cap left unset is just the daily cap`() {
        // One sensed trigger cannot out-compete itself, so nothing should
        // change for an install that never sets this.
        assertEquals(2, thresholds.copy(dailyCap = 2).perSourceCap)
        // And it must survive copy(): a default that read dailyCap directly
        // would leave a stale source cap behind and fail the requirement.
        assertEquals(1, thresholds.copy(dailyCap = 1).perSourceCap)
    }

    @Test
    fun a_cap_below_one_is_rejected_rather_than_silently_accepted() {
        val error = runCatching { thresholds.copy(dailyCap = 0) }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun the_suggested_calibration_matches_the_prototype() {
        // These are the numbers the study measures drift against, and they
        // are duplicated in the schema as column defaults. If you retune one,
        // retune both -- 0014 carries this one.
        //
        // Three, not the prototype's ten. Ten is a deliberate walk and the
        // right number for the study; it is also a threshold nobody crosses
        // while somebody is watching them use the app, so no test session ever
        // saw a reminder fire on its own. Watching a stranger meet the real
        // trigger is worth more right now than matching the prototype, and the
        // stepper on the sensing screen moves it either way.
        assertEquals(3, Thresholds.SUGGESTED.walkingMinutes)
        assertEquals(20, Thresholds.SUGGESTED.sessionMinutes)
        assertEquals(2, Thresholds.SUGGESTED.dailyCap)
        // The one that no longer matches the prototype, which suggested two
        // hours. Deliberate, and recorded in docs/02: a gap nobody could see
        // or change was a rule wearing a suggestion's clothes, and it made the
        // trigger impossible to watch work. 0012 moves the column default to
        // match. The cap is the limit that remains.
        assertEquals(0, Thresholds.SUGGESTED.cooldownMinutes)
    }

    @Test
    fun a_zero_gap_lets_a_second_cue_follow_immediately() {
        assertEquals(
            Decision.Fire,
            decide(lastCueAt = now.minus(Duration.ofSeconds(1))),
        )
    }

    @Test
    fun a_zero_gap_does_not_hold_a_cue_when_the_clock_has_gone_backwards() {
        // lastCueAt in the future, which an NTP correction can produce. With
        // the gate off there is nothing to compare against and nothing to
        // hold; the arithmetic alone would have refused for ever.
        assertEquals(
            Decision.Fire,
            decide(lastCueAt = now.plus(Duration.ofMinutes(5))),
        )
    }

    @Test
    fun a_gap_that_was_asked_for_is_still_enforced() {
        assertEquals(
            Decision.Hold(Reason.IN_COOLDOWN),
            decide(settings = spaced, lastCueAt = now.minus(Duration.ofMinutes(119))),
        )
    }

    @Test
    fun holds_inside_the_cooldown_window() {
        assertEquals(
            Decision.Hold(Reason.IN_COOLDOWN),
            decide(
                settings = spaced,
                lastCueAt = now.minus(
                    Duration.ofMinutes(spaced.thresholds.cooldownMinutes - 1L),
                ),
            ),
        )
    }

    @Test
    fun fires_once_the_cooldown_has_cleared() {
        assertEquals(
            Decision.Fire,
            decide(
                settings = spaced,
                lastCueAt = now.minus(
                    Duration.ofMinutes(spaced.thresholds.cooldownMinutes + 1L),
                ),
            ),
        )
    }

    @Test
    fun cooldown_survives_midnight_when_today_is_empty_but_a_cue_just_fired() {
        // The 23:55 / 00:05 case. Before lastCueAt was tracked separately this
        // fired, because the cooldown was derived from today's entries and
        // today had just rolled over.
        assertEquals(
            Decision.Hold(Reason.IN_COOLDOWN),
            decide(
                settings = spaced,
                entriesToday = emptyList(),
                lastCueAt = now.minus(Duration.ofMinutes(10)),
            ),
        )
    }

    @Test
    fun no_previous_cue_anywhere_means_no_cooldown() {
        assertEquals(Decision.Fire, decide(lastCueAt = null))
    }

    // --- busy windows ------------------------------------------------------

    @Test
    fun holds_while_the_user_is_in_class() {
        assertEquals(Decision.Hold(Reason.IN_CLASS), decide(busyNow = true))
    }

    @Test
    fun being_in_class_is_reported_ahead_of_the_other_suppressions() {
        // Walking between buildings and stopping outside a lecture hall is
        // exactly the shape this pipeline detects, so this is the reason worth
        // seeing in a log when a cue is held.
        assertEquals(
            Decision.Hold(Reason.IN_CLASS),
            decide(
                entriesToday = listOf(entry(resolution = Resolution.CALLED)),
                busyNow = true,
            ),
        )
    }

    @Test
    fun the_threshold_still_comes_before_being_in_class() {
        // Stage 2 before stage 3, as the handoff orders them.
        assertEquals(
            Decision.Hold(Reason.BELOW_THRESHOLD),
            decide(walkSignal(activeMinutes = 1), busyNow = true),
        )
    }

    @Test
    fun a_manual_request_during_class_still_fires() {
        // They are sitting in a lecture asking for the prompt. That is their
        // business, not ours.
        val signal = Signal(TriggerSource.MANUAL, activeMinutes = 0, stillSince = now)
        assertEquals(Decision.Fire, decide(signal, busyNow = true))
    }

    @Test
    fun holds_while_a_plan_is_still_outstanding() {
        assertEquals(
            Decision.Hold(Reason.REMINDER_PENDING),
            decide(hasPendingReminder = true),
        )
    }

    // --- stage 4: kairos --------------------------------------------------

    @Test
    fun holds_while_the_user_has_only_just_stopped() {
        assertEquals(
            Decision.Hold(Reason.TRANSITION_UNSETTLED),
            decide(walkSignal(stillFor = CuePolicy.SETTLE.minusSeconds(1))),
        )
    }

    @Test
    fun fires_the_moment_the_settle_window_is_met() {
        assertEquals(Decision.Fire, decide(walkSignal(stillFor = CuePolicy.SETTLE)))
    }

    // --- stage 4, deferred: the re-ask once it has settled ------------------
    //
    // Every test above hands `decide` a signal that is already settled, which
    // is what the production path could never do: the transition arrives the
    // instant stillness is detected, so the first ask is always inside SETTLE
    // and always refused. Nothing came back afterwards, so no sensed walk ever
    // became a reminder, and none of these tests could see it — they were
    // describing a caller that did not exist. `sensing/SettleAlarm` is now
    // that caller, and these cover the part of its contract that is pure.

    @Test
    fun a_walk_waiting_to_settle_has_not_expired() {
        assertFalse(CuePolicy.settleExpired(now, now.plus(CuePolicy.SETTLE)))
    }

    @Test
    fun a_late_alarm_still_fires_inside_the_window() {
        // Doze holds an inexact alarm until a maintenance window, so landing
        // minutes late is normal rather than exceptional.
        val late = now.plus(CuePolicy.SETTLE).plus(Duration.ofMinutes(5))
        assertFalse(CuePolicy.settleExpired(now, late))
    }

    @Test
    fun a_stop_that_finished_long_ago_is_dropped_rather_than_fired() {
        // The worst thing this file can do is fire at the wrong moment, and a
        // reminder for a walk that ended an hour ago is exactly that.
        val muchLater = now.plus(Duration.ofHours(1))
        assertTrue(CuePolicy.settleExpired(now, muchLater))
    }

    @Test
    fun a_note_cue_has_no_transition_to_settle() {
        val signal = Signal(
            source = TriggerSource.NOTE,
            activeMinutes = 0,
            stillSince = now,
        )
        assertEquals(Decision.Fire, decide(signal))
    }

    // --- manual ------------------------------------------------------------

    @Test
    fun a_manual_request_ignores_every_gate_including_the_off_switch() {
        val signal = Signal(TriggerSource.MANUAL, activeMinutes = 0, stillSince = now)
        assertEquals(
            Decision.Fire,
            decide(
                signal,
                settings = UserSettings(cuesEnabled = false),
                entriesToday = listOf(
                    entry(
                        resolution = Resolution.CALLED,
                        occurredAt = now.minus(Duration.ofMinutes(2)),
                    ),
                ),
                cuesToday = 99,
                hasPendingReminder = true,
            ),
        )
    }

    // --- ordering ----------------------------------------------------------

    @Test
    fun the_off_switch_is_reported_before_anything_else() {
        assertEquals(
            Decision.Hold(Reason.CUES_DISABLED),
            decide(
                walkSignal(activeMinutes = 1),
                settings = settings.copy(cuesEnabled = false),
                entriesToday = listOf(entry(resolution = Resolution.CALLED)),
            ),
        )
    }

    @Test
    fun threshold_is_reported_before_suppression_when_both_would_hold() {
        // The handoff orders the stages 2 then 3, and the hold reason is the
        // main thing we will have to debug from, so the order is load-bearing.
        assertEquals(
            Decision.Hold(Reason.BELOW_THRESHOLD),
            decide(
                walkSignal(activeMinutes = 1),
                entriesToday = listOf(entry(resolution = Resolution.CALLED)),
            ),
        )
    }
}
