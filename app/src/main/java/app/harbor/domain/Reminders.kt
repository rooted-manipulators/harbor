package app.harbor.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * What becomes of a plan after somebody taps "later".
 *
 * Tapping a later time writes a [Resolution.PROPOSED_LATER] row and the cue
 * promises, in those words, "a reminder inside Harbor". Two separate things
 * have to happen for that promise to be kept, and conflating them is what went
 * wrong here before:
 *
 * 1. **The hold.** While a plan is live, sensed reminders stand down —
 *    [CuePolicy.Reason.REMINDER_PENDING]. That is a question about *now*, so
 *    it is answered here against the clock and nothing is written down.
 * 2. **The fact.** [LedgerEntry.reminderDone] records that the person closed
 *    the loop. That is a question about *them*, it goes in the study's export,
 *    and it is only ever set by something the person actually did — the card
 *    on home, or reaching the person for real.
 *
 * Until this file existed, one boolean did both jobs and nothing set it. The
 * first "later" anyone tapped therefore switched sensed reminders off for the
 * rest of the study, with no way back: `hasPendingReminder` spanned every day,
 * and `markReminderDone` had no callers. See `docs/10-the-later-loop.md`.
 *
 * The hold is bounded and the fact is not. A plan nobody ever closed stops
 * holding cues back a couple of hours after its time, and its `reminder_done`
 * stays `false` — which is the honest record of what happened, and is exactly
 * the row the study should be able to count.
 */
object Reminders {

    /**
     * How long a plan keeps holding reminders back after its own time.
     *
     * The grace is there so a sensed reminder cannot jump the gun on somebody
     * who is two minutes from picking up the phone. It is deliberately short:
     * past it, the plan has had its moment, and a week-long study cannot
     * afford a single deferral to cost days of sensing.
     *
     * The hold always ends relative to the plan the person actually made,
     * never in the middle of one — the presets reach as far as tomorrow
     * evening, so the longest possible hold is about thirty hours. If that
     * turns out to be too much of a study week, the cap belongs here as a
     * second bound on when the plan was *made*, not as a shorter grace.
     */
    val GRACE: Duration = Duration.ofHours(2)

    /**
     * How long the card on home keeps offering to close a plan.
     *
     * Same reasoning as [CallStats.WINDOW], which is the same length for the
     * same reason: being asked on Friday about a plan made on Tuesday is worse
     * than not being asked at all.
     */
    val WINDOW: Duration = Duration.ofHours(12)

    /** How a plan came to be closed. A category for the study, never a note. */
    enum class Closed {
        /** They reached the person — Harbor saw the row go in. */
        REACHED_THEM,

        /** They said on the card that they already had. Nothing is inferred. */
        SAID_SO,

        /** They let it go. A legitimate answer, and it costs nothing. */
        LET_GO,
    }

    /**
     * The plan that is currently holding sensed reminders back, if any.
     *
     * Oldest first, which is what the ledger hands us — with more than one
     * outstanding it does not matter which is returned, only whether one is.
     */
    fun holding(entries: List<LedgerEntry>, now: Instant): LedgerEntry? =
        outstanding(entries).firstOrNull { now < it.proposedTime!!.plus(GRACE) }

    /**
     * The plan whose time has come and which nobody has closed: the card.
     *
     * The newest, so that somebody who deferred twice is asked about the plan
     * they actually meant rather than the one they have already moved past.
     */
    fun due(entries: List<LedgerEntry>, now: Instant): LedgerEntry? =
        outstanding(entries)
            .filter {
                !now.isBefore(it.proposedTime!!) && now < it.proposedTime!!.plus(WINDOW)
            }
            .maxByOrNull { it.proposedTime!! }

    /**
     * The plans [moment] closes by itself, as ids to mark done.
     *
     * Somebody who planned to call their mother and then called their mother
     * has closed that plan, and a card asking whether they did — seconds after
     * Harbor dialled her itself — reads as an app that was not paying
     * attention. Reaching them any way that counts as connection does it, on
     * the same judgement that decides [CuePolicy.Reason.ALREADY_CONNECTED_TODAY]:
     * a heart sent on purpose is reaching somebody.
     *
     * Bounded by [WINDOW] on the plan, so a call next week never reaches back
     * and rewrites a plan the person had long forgotten. Not bounded below by
     * the proposed time: acting early closes a plan just as properly as acting
     * on time, and getting that case wrong is why this is a stored fact rather
     * than an inference from the clock (docs/02, row 9).
     */
    fun closedBy(entries: List<LedgerEntry>, moment: LedgerEntry): List<UUID> {
        if (!moment.resolution.isConnection) return emptyList()
        val who = moment.contactId ?: return emptyList()
        return outstanding(entries)
            .filter {
                it.id != moment.id &&
                    it.contactId == who &&
                    !moment.occurredAt.isBefore(it.occurredAt) &&
                    moment.occurredAt < it.proposedTime!!.plus(WINDOW)
            }
            .map { it.id }
    }

    /**
     * Every plan still open, whatever the clock says.
     *
     * The null check is belt and braces — [LedgerEntry] already requires a
     * proposed time on exactly these rows — but it is what lets every caller
     * above read `proposedTime!!` without arguing about it.
     */
    private fun outstanding(entries: List<LedgerEntry>): List<LedgerEntry> =
        entries.filter {
            it.resolution == Resolution.PROPOSED_LATER &&
                !it.reminderDone &&
                it.proposedTime != null
        }
}
