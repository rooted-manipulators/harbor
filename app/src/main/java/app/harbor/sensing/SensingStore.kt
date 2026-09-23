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
     * [outcome] is null when the walk became a reminder — the absence of a
     * reason being the one good answer — and otherwise a [CuePolicy.Reason]
     * name or one of the two tokens below. Those two are not policy verdicts:
     * the policy was never asked a second time, because the deferred stop had
     * stopped being a stop worth asking about.
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

    /**
     * The last scrolling stretch Harbor offered a reminder for, and what came
     * of it.
     *
     * [ScrollWatch] is a poll, so a stretch that has crossed the threshold
     * keeps crossing it every time it is asked. Without this, twenty-one
     * minutes in one app would produce a reminder, and twenty-four minutes
     * another, until the day's allowance was gone -- which is the behaviour
     * this app exists not to have.
     *
     * The start time is part of the identity, not just the package: putting
     * the phone down and picking the same app up again is a new stretch and
     * may earn its own reminder, subject to every cap in [CuePolicy].
     *
     * ## Why a held stretch is asked about again
     *
     * The first version of this remembered only that a stretch had been
     * *offered*, fired or not, and never returned to it. That quietly lost
     * cues. Half the reasons [CuePolicy] holds are temporary -- you were
     * inside a busy block, you were in the cooldown, you had a later time
     * planned -- and all three can stop being true while the same stretch is
     * still going on. A person who hit twenty minutes at the end of a
     * lecture would be refused at the one moment they could not be reached
     * and never asked again, though they went on scrolling for an hour.
     *
     * So a hold is retried, after [SensingStore.RETRY], and a fire is not.
     * See [Offer.fired], which is the whole difference.
     *
     * This and [lastStretch] are the only things about your apps that Harbor
     * writes down, and they hold one at a time. See [ScrollWatch] for what is
     * deliberately not kept.
     */
    var offer: Offer?
        get() {
            val started = prefs.getLong(KEY_OFFER_STARTED, ABSENT)
            if (started == ABSENT) return null
            val pkg = prefs.getString(KEY_OFFER_PKG, null) ?: return null
            val at = prefs.getLong(KEY_OFFER_AT, ABSENT)
            if (at == ABSENT) return null
            return Offer(
                stretch = ScrollWatch.Stretch(pkg, Instant.ofEpochMilli(started)),
                offeredAt = Instant.ofEpochMilli(at),
                fired = prefs.getBoolean(KEY_OFFER_FIRED, false),
            )
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_OFFER_STARTED)
                    remove(KEY_OFFER_PKG)
                    remove(KEY_OFFER_AT)
                    remove(KEY_OFFER_FIRED)
                } else {
                    putLong(KEY_OFFER_STARTED, value.stretch.startedAt.toEpochMilli())
                    putString(KEY_OFFER_PKG, value.stretch.packageName)
                    putLong(KEY_OFFER_AT, value.offeredAt.toEpochMilli())
                    putBoolean(KEY_OFFER_FIRED, value.fired)
                }
            }.commit()
        }

    /** A stretch that has already been put to [CuePolicy], and when. */
    data class Offer(
        val stretch: ScrollWatch.Stretch,
        val offeredAt: Instant,
        val fired: Boolean,
    )

    /**
     * The last scrolling stretch that reached the threshold, and its verdict.
     *
     * The same job [lastBout] does for a walk, and for the same reason:
     * "nothing arrived" has half a dozen causes and they are
     * indistinguishable from outside. More so here, because this trigger has
     * a failure the walk does not -- usage access revoked from Settings,
     * which leaves the switch on, the screen saying nothing is wrong, and no
     * reminder ever coming.
     *
     * A diagnostic. Nothing in the study reads it and it never leaves the
     * device. The package name stays on the phone with everything else about
     * your apps; [StudyExport] has no field for it.
     */
    var lastStretch: Watched?
        get() {
            val started = prefs.getLong(KEY_WATCHED_AT, ABSENT)
            if (started == ABSENT) return null
            val pkg = prefs.getString(KEY_WATCHED_PKG, null) ?: return null
            return Watched(
                packageName = pkg,
                startedAt = Instant.ofEpochMilli(started),
                minutes = prefs.getInt(KEY_WATCHED_MINUTES, 0),
                outcome = prefs.getString(KEY_WATCHED_OUTCOME, null),
            )
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_WATCHED_AT)
                    remove(KEY_WATCHED_PKG)
                    remove(KEY_WATCHED_MINUTES)
                    remove(KEY_WATCHED_OUTCOME)
                } else {
                    putLong(KEY_WATCHED_AT, value.startedAt.toEpochMilli())
                    putString(KEY_WATCHED_PKG, value.packageName)
                    putInt(KEY_WATCHED_MINUTES, value.minutes)
                    if (value.outcome == null) remove(KEY_WATCHED_OUTCOME)
                    else putString(KEY_WATCHED_OUTCOME, value.outcome)
                }
            }.commit()
        }

    /**
     * A stretch that was long enough to ask about.
     *
     * [outcome] is null when it became a reminder -- the absence of a reason
     * being the one good answer -- and otherwise a [CuePolicy.Reason] name.
     */
    data class Watched(
        val packageName: String,
        val startedAt: Instant,
        val minutes: Int,
        val outcome: String?,
    )

    companion object {
        /**
         * How long before the same stretch is put to the policy again.
         *
         * Only ever after a hold, and the number is a compromise between two
         * wrongs. Too short and a stretch inside an hour-long busy block is
         * asked about thirty times, which costs nothing visible but fills the
         * log with the same refusal. Too long and the trigger misses the
         * minute a cooldown expires. Ten is roughly the shortest interval at
         * which a second offer is a different question.
         */
        val RETRY: java.time.Duration = java.time.Duration.ofMinutes(10)

        /**
         * Whether this stretch should be put to [CuePolicy] now.
         *
         * Pure, and separate from the service, because it is the decision
         * that sits between the two ways this trigger can be wrong. Say yes
         * too readily and one long session produces a reminder every couple
         * of minutes; say no too readily and a stretch refused for a reason
         * that has since expired is never asked about again. Neither is
         * visible from inside the app once it is happening, so it is tested
         * instead. See `ScrollWatchTest`.
         *
         * @param last the offer already made, if any.
         * @param stretch the one in front of us now.
         */
        fun shouldAsk(
            last: Offer?,
            stretch: ScrollWatch.Stretch,
            now: Instant,
        ): Boolean {
            // A different sitting entirely. It gets its own hearing, and
            // every cap in the policy still applies to it.
            if (last == null || last.stretch != stretch) return true
            // Asked and answered. The reminder happened; a second one for
            // the same session is the behaviour this app exists not to have.
            if (last.fired) return false
            // Refused. Half the reasons a refusal happens are temporary, so
            // the question is worth asking again -- but not every two
            // minutes, which would be the same question.
            return java.time.Duration.between(last.offeredAt, now) >= RETRY
        }

        /** Set off again before the settle window was up. */
        const val WALKING_RESUMED = "WALKING_RESUMED"

        /** The re-ask arrived so late the moment had gone. */
        const val SETTLE_EXPIRED = "SETTLE_EXPIRED"

        private const val KEY_WALKING_SINCE = "walking_since"
        private const val KEY_WALKED_UNTIL = "walked_until"
        private const val KEY_BOUT_STARTED = "bout_started_at"
        private const val KEY_BOUT_ENDED = "bout_ended_at"
        private const val KEY_BOUT_MINUTES = "bout_minutes"
        private const val KEY_BOUT_OUTCOME = "bout_outcome"
        private const val KEY_LAST_TRANSITION = "last_transition_at"
        private const val KEY_PENDING_STILL_SINCE = "pending_still_since"
        private const val KEY_PENDING_SOURCE = "pending_source"
        private const val KEY_PENDING_MINUTES = "pending_active_minutes"
        private const val KEY_OFFER_STARTED = "offer_stretch_at"
        private const val KEY_OFFER_PKG = "offer_stretch_package"
        private const val KEY_OFFER_AT = "offer_made_at"
        private const val KEY_OFFER_FIRED = "offer_fired"
        private const val KEY_WATCHED_AT = "watched_stretch_at"
        private const val KEY_WATCHED_PKG = "watched_stretch_package"
        private const val KEY_WATCHED_MINUTES = "watched_stretch_minutes"
        private const val KEY_WATCHED_OUTCOME = "watched_stretch_outcome"
        private const val ABSENT = -1L
    }
}
