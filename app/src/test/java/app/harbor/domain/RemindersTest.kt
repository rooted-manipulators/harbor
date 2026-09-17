package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class RemindersTest {

    private val mom: UUID = UUID.randomUUID()
    private val dad: UUID = UUID.randomUUID()

    /** Midday, and by default a plan made an hour before it. */
    private val noon: Instant = Instant.parse("2026-09-17T12:00:00Z")

    private fun plan(
        at: Instant = noon,
        madeAt: Instant = at.minus(Duration.ofHours(1)),
        contactId: UUID? = mom,
        done: Boolean = false,
    ) = entry(
        resolution = Resolution.PROPOSED_LATER,
        occurredAt = madeAt,
        proposedTime = at,
        contactId = contactId,
        reminderDone = done,
    )

    private fun entry(
        resolution: Resolution,
        occurredAt: Instant,
        proposedTime: Instant? = null,
        contactId: UUID? = mom,
        reminderDone: Boolean = false,
    ) = LedgerEntry(
        id = UUID.randomUUID(),
        entryDate = occurredAt.atZone(ZoneId.systemDefault()).toLocalDate(),
        cueId = null,
        contactId = contactId,
        triggerSource = TriggerSource.WALKING_STOP,
        thresholdSnapshot = Thresholds.SUGGESTED,
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

    // --- the hold ----------------------------------------------------------

    @Test
    fun a_plan_holds_reminders_back_until_its_time() {
        val plans = listOf(plan())
        assertTrue(Reminders.holding(plans, noon.minus(Duration.ofMinutes(30))) != null)
    }

    @Test
    fun a_plan_holds_through_its_grace_period() {
        // Long enough that a sensed reminder cannot jump the gun on somebody
        // who is two minutes away from picking up the phone.
        val plans = listOf(plan())
        assertTrue(Reminders.holding(plans, noon.plus(Duration.ofMinutes(90))) != null)
    }

    @Test
    fun a_plan_stops_holding_once_its_moment_has_passed() {
        // The bug this file exists for. Nothing in the app ever set
        // reminderDone, so a hold that waited for it waited for good: one
        // "later" tap switched sensed reminders off for the rest of the week.
        val plans = listOf(plan())
        assertNull(Reminders.holding(plans, noon.plus(Reminders.GRACE).plusSeconds(1)))
    }

    @Test
    fun a_plan_made_today_for_tomorrow_still_holds_tomorrow() {
        // Bounded is not the same as same-day. The hold ends relative to the
        // plan the person made, never in the middle of one.
        val tomorrow = noon.plus(Duration.ofHours(30))
        val plans = listOf(plan(at = tomorrow, madeAt = noon))
        assertTrue(Reminders.holding(plans, noon.plus(Duration.ofHours(20))) != null)
    }

    @Test
    fun a_closed_plan_never_holds() {
        assertNull(Reminders.holding(listOf(plan(done = true)), noon.minusSeconds(60)))
    }

    @Test
    fun nothing_but_a_plan_holds() {
        val others = listOf(
            entry(Resolution.DISMISSED, occurredAt = noon),
            entry(Resolution.CALLED, occurredAt = noon),
            entry(Resolution.NOT_REACHED, occurredAt = noon),
        )
        assertNull(Reminders.holding(others, noon))
    }

    // --- the card ----------------------------------------------------------

    @Test
    fun no_card_before_the_time_they_chose() {
        assertNull(Reminders.due(listOf(plan()), noon.minusSeconds(1)))
    }

    @Test
    fun the_card_appears_the_moment_it_is_due() {
        val plans = listOf(plan())
        assertEquals(plans.single().id, Reminders.due(plans, noon)?.id)
    }

    @Test
    fun the_card_gives_up_rather_than_asking_about_last_week() {
        val plans = listOf(plan())
        assertNull(Reminders.due(plans, noon.plus(Reminders.WINDOW)))
    }

    @Test
    fun the_card_asks_about_the_plan_they_actually_meant() {
        // Deferred twice. The older one is not the one on their mind.
        val older = plan(at = noon.minus(Duration.ofHours(3)))
        val newer = plan(at = noon.minus(Duration.ofMinutes(20)))
        assertEquals(newer.id, Reminders.due(listOf(older, newer), noon)?.id)
    }

    @Test
    fun a_closed_plan_never_asks_again() {
        assertNull(Reminders.due(listOf(plan(done = true)), noon))
    }

    // --- closing it by actually reaching them ------------------------------

    @Test
    fun calling_them_closes_the_plan_by_itself() {
        val open = plan()
        val called = entry(Resolution.CALLED, occurredAt = noon.plusSeconds(600))
        assertEquals(listOf(open.id), Reminders.closedBy(listOf(open), called))
    }

    @Test
    fun calling_them_early_closes_it_too() {
        // The case that makes this a stored fact rather than a reading of the
        // clock: they said eight, they rang at six, the plan is done.
        val open = plan()
        val called = entry(Resolution.CALLED, occurredAt = noon.minus(Duration.ofMinutes(45)))
        assertEquals(listOf(open.id), Reminders.closedBy(listOf(open), called))
    }

    @Test
    fun a_heart_sent_on_purpose_closes_it() {
        // Same judgement as ALREADY_CONNECTED_TODAY: reaching somebody is
        // reaching somebody, and it would be strange to count it there and
        // then ask on home whether they had got round to it.
        val open = plan()
        val reacted = entry(Resolution.REACTED, occurredAt = noon.plusSeconds(60))
        assertEquals(listOf(open.id), Reminders.closedBy(listOf(open), reacted))
    }

    @Test
    fun calling_somebody_else_leaves_the_plan_open() {
        val open = plan()
        val called = entry(Resolution.CALLED, occurredAt = noon, contactId = dad)
        assertEquals(emptyList<UUID>(), Reminders.closedBy(listOf(open), called))
    }

    @Test
    fun a_call_that_came_to_nothing_leaves_the_plan_open() {
        val open = plan()
        val tried = entry(Resolution.NOT_REACHED, occurredAt = noon.plusSeconds(60))
        assertEquals(emptyList<UUID>(), Reminders.closedBy(listOf(open), tried))
    }

    @Test
    fun a_call_next_week_does_not_reach_back_and_rewrite_the_plan() {
        // By then the plan has lapsed, and a lapsed plan is an honest row.
        // Marking it kept because they happened to ring her days later would
        // put a claim in the study's data that nobody ever made.
        val open = plan()
        val later = entry(Resolution.CALLED, occurredAt = noon.plus(Duration.ofDays(7)))
        assertEquals(emptyList<UUID>(), Reminders.closedBy(listOf(open), later))
    }

    @Test
    fun a_call_placed_before_the_plan_was_made_does_not_close_it() {
        // They rang her, and afterwards deferred a reminder. The plan is about
        // a call that has not happened yet.
        val open = plan()
        val before = entry(Resolution.CALLED, occurredAt = noon.minus(Duration.ofHours(4)))
        assertEquals(emptyList<UUID>(), Reminders.closedBy(listOf(open), before))
    }

    // --- the one found on the test phone -----------------------------------

    @Test
    fun the_stranded_plan_from_the_test_phone_no_longer_blocks_anything() {
        // A proposed-later row written on 15 Sep for 12:30 the next day was
        // still holding every sensed reminder on 17 Sep, and would have gone
        // on holding them for the rest of the study. It held while it was live:
        val stranded = plan(
            at = Instant.parse("2026-09-16T12:30:00Z"),
            madeAt = Instant.parse("2026-09-15T18:40:00Z"),
        )
        val held = listOf(stranded)
        assertTrue(Reminders.holding(held, Instant.parse("2026-09-16T11:00:00Z")) != null)

        // ...and lets go by the next day, with nobody having had to do
        // anything about it.
        val today = Instant.parse("2026-09-17T09:00:00Z")
        assertNull(Reminders.holding(held, today))
        // The card stopped offering long before that, which is exactly why the
        // hold has to end on its own: a card nobody can see clears nothing.
        assertNull(Reminders.due(held, today))
        // And the row still says what really happened -- a plan that lapsed.
        assertTrue(held.none { it.reminderDone })
    }
}
