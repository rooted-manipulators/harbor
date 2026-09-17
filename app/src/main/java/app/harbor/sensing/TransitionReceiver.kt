package app.harbor.sensing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.harbor.data.HarborStore
import app.harbor.domain.CuePolicy
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Where the pipeline actually runs.
 *
 * Play services wakes this receiver with a batch of transitions. It advances
 * [BoutTracker] (stage 1), and when a walk has ended in stillness it asks
 * [CueGate] (stages 2-5) whether that moment deserves a reminder.
 *
 * A walk that ends is always asked about twice: once here, the instant
 * stillness is reported, and once more when [SettleAlarm] brings it back
 * settled. The first ask can only ever be refused — see [considerCue] — and
 * for a long time it was also the only one, which is why no sensed walk had
 * ever produced a reminder.
 *
 * Everything here is on-device and offline. Nothing in this path touches the
 * network, by design — the reminder has to fire on a train with no signal.
 * See ADR-003.
 */
class TransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        val app = context.applicationContext
        val pending = goAsync()

        // The receiver's own window is a few milliseconds; goAsync buys us
        // enough to read the ledger and write a cue. It is not a licence to do
        // anything slow here.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handle(app, result)
            } catch (e: Throwable) {
                // A crash here would be invisible to the user and would take
                // sensing down until the next reboot. Losing one transition is
                // the better failure.
                Log.e(TAG, "transition batch failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, result: ActivityTransitionResult) {
        val sensing = SensingStore(context)

        // Proof of life, written before anything else can go wrong with this
        // batch. Whether it becomes a cue is a separate question.
        sensing.lastTransitionAt = Instant.now()
        val store = HarborStore(context)

        // Events arrive batched and are documented as chronological, but the
        // tracker is a state machine and the order is load-bearing, so sort
        // rather than trust.
        val events = result.transitionEvents.sortedBy { it.elapsedRealTimeNanos }

        for (event in events) {
            val mapped = BoutTracker.Event(
                activity = activityOf(event.activityType),
                kind = kindOf(event.transitionType),
                at = ActivityTransitions.toInstant(event.elapsedRealTimeNanos),
            )

            // Moving again, by any means. Whatever stop was waiting to settle
            // is over, and firing into the middle of the next walk is the
            // "never mid-activity" rule broken by the back door.
            if (mapped.kind == BoutTracker.Kind.ENTER &&
                mapped.activity != BoutTracker.Activity.STILL
            ) {
                if (sensing.pending != null) {
                    sensing.pending = null
                    SettleAlarm.cancel(context)
                }
            }

            val step = BoutTracker.advance(sensing.state, mapped)
            sensing.state = step.state

            val signal = step.signal ?: continue
            considerCue(context, sensing, store, signal)
        }
    }

    /**
     * Ask about a walk that just ended — and park it when the only thing
     * standing in the way is that it ended a moment ago.
     *
     * This runs at the instant Play services reports stillness, which is
     * always inside [CuePolicy.SETTLE], so the settle gate refuses every time.
     * Before, that refusal was the end of it: the bout had already been
     * cleared by [BoutTracker] and nothing would wake the pipeline again, so
     * no sensed walk could ever become a reminder. Now the signal is stored
     * and [SettleAlarm] brings it back once it has settled.
     *
     * Only `TRANSITION_UNSETTLED` is deferred. It is the one refusal that time
     * alone resolves; a day at its cap or a user inside a lecture is not going
     * to be different in ninety seconds, and re-asking those would be badgering
     * the policy rather than respecting it.
     */
    private suspend fun considerCue(
        context: Context,
        sensing: SensingStore,
        store: HarborStore,
        signal: CuePolicy.Signal,
    ) {
        val now = Instant.now()
        val decision = CueGate.consider(context, store, signal, now)

        if (decision is CuePolicy.Decision.Hold &&
            decision.reason == CuePolicy.Reason.TRANSITION_UNSETTLED
        ) {
            sensing.pending = signal
            SettleAlarm.schedule(context, signal, now)
        }
    }

    private fun activityOf(type: Int): BoutTracker.Activity = when (type) {
        // ON_FOOT is the umbrella Play services reports when it cannot
        // separate walking from running. Treating it as walking is the
        // forgiving read, and the threshold still has to be cleared.
        DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> BoutTracker.Activity.WALKING
        DetectedActivity.STILL -> BoutTracker.Activity.STILL
        else -> BoutTracker.Activity.OTHER
    }

    private fun kindOf(type: Int): BoutTracker.Kind =
        if (type == ActivityTransition.ACTIVITY_TRANSITION_ENTER) BoutTracker.Kind.ENTER
        else BoutTracker.Kind.EXIT

    private companion object {
        const val TAG = "HarborSensing"
    }
}
