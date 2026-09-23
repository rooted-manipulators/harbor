package app.harbor.sensing

import android.content.Context
import android.util.Log
import app.harbor.cue.CueNotifier
import app.harbor.data.HarborStore
import app.harbor.domain.Cue
import app.harbor.domain.Moment
import app.harbor.domain.CuePolicy
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Stages 2-5 in one place: ask [CuePolicy], and act on the answer.
 *
 * Shared by the two things that can reach it — [TransitionReceiver] when a
 * walk ends, and [SettleReceiver] when that walk has since settled. They are
 * woken by different parts of the system and must reach identical verdicts,
 * so the gauntlet lives here rather than being written out twice.
 */
internal object CueGate {

    /**
     * Run the policy and fire if it says so.
     *
     * @return the decision, so the caller can decide whether it is worth
     *   coming back later — only the caller knows whether it *is* the later.
     */
    suspend fun consider(
        context: Context,
        store: HarborStore,
        signal: CuePolicy.Signal,
        now: Instant = Instant.now(),
    ): CuePolicy.Decision {
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()

        val decision = CuePolicy.decide(
            signal = signal,
            settings = store.settings.value,
            day = store.dayState(today),
            now = now,
        )

        if (decision is CuePolicy.Decision.Hold) {
            // Still not written to the ledger -- a held cue is not an event
            // in anybody's life and the ledger's counts have to stay clean.
            // It does go in the beats, which is the shapes log and exists
            // for exactly this: without it, a quiet week cannot be told
            // apart from a week of suppressed ones. See Moment.CUE_HELD.
            store.note(Moment.CUE_HELD, decision.reason.name)
            Log.i(TAG, "reminder held: ${decision.reason}")
            return decision
        }

        val cue = Cue(
            id = UUID.randomUUID(),
            firedDate = today,
            triggerSource = signal.source,
            firedAt = now,
        )
        // Recorded before it is shown, so the daily cap counts it even if the
        // process dies between here and the surface appearing. The surface
        // records only the resolution, never a second cue.
        store.recordCue(cue)

        CueNotifier.post(context, cue, store.contacts.value.firstOrNull())
        // Whether it will take the screen or arrive as a banner. Asked here
        // rather than inside post(), so the answer recorded is the same one
        // post() acted on. See Moment.CUE_DELIVERED.
        store.note(
            Moment.CUE_DELIVERED,
            if (CueNotifier.canOpenOverApps(context)) "screen" else "banner",
        )
        Log.i(TAG, "reminder fired: ${cue.id} after ${signal.activeMinutes} min")
        return decision
    }

    private const val TAG = "HarborSensing"
}
