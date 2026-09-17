package app.harbor.sensing

import app.harbor.domain.CuePolicy
import app.harbor.domain.TriggerSource
import java.time.Duration
import java.time.Instant

/**
 * Stage 1 of the pipeline, as a pure state machine.
 *
 * Google Play services hands us a stream of activity transitions — entered
 * walking, exited walking, entered still — and this turns that stream into
 * the one thing the rest of the pipeline cares about: a walk that ended in
 * stillness, and how long it was.
 *
 * Pure and testable for the same reason [CuePolicy] is. Transition streams in
 * the wild are messy — events arrive late, out of order, duplicated, or not at
 * all — and every one of those cases is trivial to write down here and
 * miserable to reproduce on a phone.
 */
object BoutTracker {

    /**
     * A bout longer than this is not a walk, it is a missed EXIT event.
     *
     * The transition API drops events — the process is killed, the OEM
     * suspends Play services, the phone is off. Without a ceiling, a stale
     * `walkingSince` from Tuesday produces a "2,400 minute walk" on Thursday
     * that sails past any threshold the user could set. Discarding is right:
     * a bout we cannot vouch for should not become a cue.
     */
    val MAX_BOUT: Duration = Duration.ofHours(4)

    /** The activities we care about. Everything else is [Activity.OTHER]. */
    enum class Activity { WALKING, STILL, OTHER }

    enum class Kind { ENTER, EXIT }

    data class Event(val activity: Activity, val kind: Kind, val at: Instant)

    /**
     * Everything the tracker remembers between events.
     *
     * It has to survive process death — the receiver is woken, runs for
     * milliseconds, and dies — so it is a small serialisable value rather than
     * anything held in memory. See [SensingStore].
     *
     * [walkedUntil] is the last EXIT walking seen while this bout was open, or
     * null if none has arrived. It is how long the walking lasted; the bout
     * still ends at stillness. See [closeBout].
     */
    data class State(
        val walkingSince: Instant? = null,
        val walkedUntil: Instant? = null,
    )

    /**
     * The window a closed bout occupied, for anything that wants to show its
     * working. [startedAt] to [endedAt] is what was measured as walking;
     * [stillSince] is when the stop began, which is the same instant only when
     * no EXIT arrived.
     */
    data class Bout(
        val startedAt: Instant,
        val endedAt: Instant,
        val stillSince: Instant,
    )

    data class Step(
        val state: State,
        val signal: CuePolicy.Signal?,
        /** Set exactly when [signal] is, and describing the same bout. */
        val bout: Bout? = null,
    )

    /**
     * Advance the machine by one transition.
     *
     * @return the new state, and a signal if a walk just ended in stillness.
     */
    fun advance(state: State, event: Event): Step = when {
        // A walk begins. If one was already open we keep the earlier start:
        // a duplicate ENTER is far more likely than the user genuinely
        // starting a second walk without stopping the first.
        //
        // Either way they are walking now, so any EXIT we had recorded is no
        // longer where the walking stopped.
        event.activity == Activity.WALKING && event.kind == Kind.ENTER ->
            Step(State(walkingSince = state.walkingSince ?: event.at), null)

        // Walking stopped. This does not end the bout — the cue belongs to the
        // *stop*, and EXIT also fires for breaking into a run or getting into
        // a car — but it is the honest end of the walking, so it is remembered
        // as the length. Without it the bout ran to whenever the detector got
        // round to declaring stillness, which is tens of seconds of standing
        // about counted as walking.
        event.activity == Activity.WALKING && event.kind == Kind.EXIT ->
            if (state.walkingSince == null) Step(state, null)
            else Step(state.copy(walkedUntil = event.at), null)

        // Stillness is what ends a bout — not EXIT walking, which also fires
        // when someone starts running or gets into a car. The handoff is
        // specific that the cue belongs to the *stop*, not to the end of
        // movement.
        event.activity == Activity.STILL && event.kind == Kind.ENTER ->
            closeBout(state, stillSince = event.at)

        // Got into a vehicle, started cycling, started running: the walk ended,
        // but not in the stillness we are looking for. Drop the bout rather
        // than let it hang open and later close against unrelated stillness.
        event.activity == Activity.OTHER && event.kind == Kind.ENTER ->
            Step(State(), null)

        // Every other EXIT, and anything else, moves nothing.
        else -> Step(state, null)
    }

    private fun closeBout(state: State, stillSince: Instant): Step {
        val startedAt = state.walkingSince
            // Still, but no walk was open. The common case by far — most
            // stillness follows more stillness.
            ?: return Step(State(), null)

        // The walking ended at the EXIT when there was one, and at the onset
        // of stillness when there was not. Clamped, because a batch can carry
        // an EXIT stamped after the stillness it precedes and a walk cannot
        // end after it ended.
        val endedAt = state.walkedUntil?.coerceIn(startedAt, stillSince) ?: stillSince

        val bout = Duration.between(startedAt, endedAt)

        // Negative means the clock moved backwards (timezone change, NTP
        // correction, a stale event). Not measurable, so not a cue.
        if (bout.isNegative || bout > MAX_BOUT) {
            return Step(State(), null)
        }

        return Step(
            State(),
            CuePolicy.Signal(
                source = TriggerSource.WALKING_STOP,
                // Truncating, not rounding: a 9-minute-59-second walk should
                // not clear a 10-minute threshold the user set deliberately.
                activeMinutes = bout.toMinutes().toInt(),
                // The stop, not the end of the walking. This is what the
                // settle window is measured from and it is the moment the
                // reminder belongs to.
                stillSince = stillSince,
            ),
            Bout(startedAt = startedAt, endedAt = endedAt, stillSince = stillSince),
        )
    }
}
