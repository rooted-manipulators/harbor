package app.harbor.sensing

import android.content.Context
import app.harbor.domain.CuePolicy
import app.harbor.domain.TriggerSource
import java.time.Instant

/**
 * The only thing [BoutTracker] needs to remember between events.
 *
 * Deliberately its own tiny prefs file rather than part of `HarborStore`. The
 * transition receiver is woken by the system, runs for a few milliseconds and
 * dies, dozens of times a day; it should not be loading and reparsing the
 * whole ledger just to note that a walk began.
 */
internal class SensingStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("harbor_sensing", Context.MODE_PRIVATE)

    var state: BoutTracker.State
        get() {
            val since = prefs.getLong(KEY_WALKING_SINCE, ABSENT)
            val until = prefs.getLong(KEY_WALKED_UNTIL, ABSENT)
            return BoutTracker.State(
                walkingSince = if (since == ABSENT) null else Instant.ofEpochMilli(since),
                // Carried across process death like the start, and for the
                // same reason: EXIT walking and ENTER still arrive in
                // different batches, minutes apart, with the receiver dead in
                // between. Forgetting it would silently put the length of
                // every walk back to the onset of stillness.
                walkedUntil = if (until == ABSENT) null else Instant.ofEpochMilli(until),
            )
        }
        set(value) {
            // commit, not apply: the process is likely to be killed the moment
            // this receiver returns, and a lost write means a lost walk.
            prefs.edit().apply {
                val since = value.walkingSince
                if (since == null) remove(KEY_WALKING_SINCE)
                else putLong(KEY_WALKING_SINCE, since.toEpochMilli())
                val until = value.walkedUntil
                if (until == null) remove(KEY_WALKED_UNTIL)
                else putLong(KEY_WALKED_UNTIL, until.toEpochMilli())
            }.commit()
        }

    /**
     * The last walk that closed, what it measured, and what came of it.
     *
     * Kept so the app can show its working. "No reminder arrived" has at least
     * five causes — no transition at all, a bout shorter than the threshold,
     * a suppression, a settle that landed too late, a reminder posted and
     * swallowed by the system — and until this existed they were
     * indistinguishable from the outside, including to the person testing it
     * on their own phone. It is also the only way to check the measured length
     * against the walk somebody actually took.
     *
     * One record, overwritten. This is a diagnostic, not a second ledger:
     * nothing in the study reads it and it never leaves the device.
     */
    var lastBout: Recorded?
        get() {
            val started = prefs.getLong(KEY_BOUT_STARTED, ABSENT)
            if (started == ABSENT) return null
            val ended = prefs.getLong(KEY_BOUT_ENDED, ABSENT)
            if (ended == ABSENT) return null
            return Recorded(
                startedAt = Instant.ofEpochMilli(started),
                endedAt = Instant.ofEpochMilli(ended),
                minutes = prefs.getInt(KEY_BOUT_MINUTES, 0),
                outcome = prefs.getString(KEY_BOUT_OUTCOME, null),
            )
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_BOUT_STARTED)
                    remove(KEY_BOUT_ENDED)
                    remove(KEY_BOUT_MINUTES)
                    remove(KEY_BOUT_OUTCOME)
                } else {
                    putLong(KEY_BOUT_STARTED, value.startedAt.toEpochMilli())
                    putLong(KEY_BOUT_ENDED, value.endedAt.toEpochMilli())
                    putInt(KEY_BOUT_MINUTES, value.minutes)
                    if (value.outcome == null) remove(KEY_BOUT_OUTCOME)
                    else putString(KEY_BOUT_OUTCOME, value.outcome)
                }
            }.commit()
        }

    /**
     * A walk the tracker closed.
     *
     * [outcome] is a [CuePolicy.Reason] name, or null when the walk became a
     * reminder — the absence of a reason being the one good answer.
     */
    data class Recorded(
        val startedAt: Instant,
        val endedAt: Instant,
        val minutes: Int,
        val outcome: String?,
    )

    /**
     * The last time the system told us anything at all.
     *
     * Not for the tracker — for us. Without it, a week with no cues is
     * indistinguishable between "never took a walk", "dismissed one before it
     * registered" and "Play services never delivered a single transition and
     * the app was dead the whole time, while telling them cues were on". The
     * study's first question cannot survive that ambiguity, so the app records
     * its own pulse and shows it.
     *
     * Stamped for every transition, not just walking ones: the question this
     * answers is whether the pipe is alive, not what came down it.
     */
    var lastTransitionAt: Instant?
        get() {
            val millis = prefs.getLong(KEY_LAST_TRANSITION, ABSENT)
            return if (millis == ABSENT) null else Instant.ofEpochMilli(millis)
        }
        set(value) {
            if (value == null) return
            prefs.edit().putLong(KEY_LAST_TRANSITION, value.toEpochMilli()).commit()
        }

    /**
     * A walk that ended but has not settled yet, waiting for its re-ask.
     *
     * The bout is gone from [BoutTracker] the moment it closes — the tracker
     * emits the signal and resets — so without this the signal existed only
     * for the length of one receiver call, was refused for being unsettled,
     * and was then unrecoverable. That was the whole reason no reminder ever
     * fired: the one thing that could evaluate a walk ran at the only moment
     * it was guaranteed to be refused.
     *
     * Held here rather than in `HarborStore` for the same reason the bout is:
     * this file is read and written by a receiver that lives for milliseconds.
     */
    var pending: CuePolicy.Signal?
        get() {
            val stillSince = prefs.getLong(KEY_PENDING_STILL_SINCE, ABSENT)
            if (stillSince == ABSENT) return null
            val source = prefs.getString(KEY_PENDING_SOURCE, null)
                ?.let { name -> runCatching { TriggerSource.valueOf(name) }.getOrNull() }
                ?: return null
            return CuePolicy.Signal(
                source = source,
                activeMinutes = prefs.getInt(KEY_PENDING_MINUTES, 0),
                stillSince = Instant.ofEpochMilli(stillSince),
            )
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_PENDING_STILL_SINCE)
                    remove(KEY_PENDING_SOURCE)
                    remove(KEY_PENDING_MINUTES)
                } else {
                    putLong(KEY_PENDING_STILL_SINCE, value.stillSince.toEpochMilli())
                    putString(KEY_PENDING_SOURCE, value.source.name)
                    putInt(KEY_PENDING_MINUTES, value.activeMinutes)
                }
            }.commit()
        }

    private companion object {
        const val KEY_WALKING_SINCE = "walking_since"
        const val KEY_WALKED_UNTIL = "walked_until"
        const val KEY_BOUT_STARTED = "bout_started_at"
        const val KEY_BOUT_ENDED = "bout_ended_at"
        const val KEY_BOUT_MINUTES = "bout_minutes"
        const val KEY_BOUT_OUTCOME = "bout_outcome"
        const val KEY_LAST_TRANSITION = "last_transition_at"
        const val KEY_PENDING_STILL_SINCE = "pending_still_since"
        const val KEY_PENDING_SOURCE = "pending_source"
        const val KEY_PENDING_MINUTES = "pending_active_minutes"
        const val ABSENT = -1L
    }
}
