package app.harbor.sensing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.harbor.cue.CueNotifier
import app.harbor.data.HarborStore
import app.harbor.domain.Cue
import app.harbor.domain.CuePolicy
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Where the pipeline actually runs.
 *
 * Play services wakes this receiver with a batch of transitions. It advances
 * [BoutTracker] (stage 1), and when a walk has ended in stillness it asks
 * [CuePolicy] (stages 2-4) whether that moment deserves a cue.
 *
 * Everything here is on-device and offline. Nothing in this path touches the
 * network, by design — the cue has to fire on a train with no signal.
 * See ADR-003.
 */
class TransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Two ways in: a batch of transitions from Play services, or our own
        // alarm coming back to ask about a stop that was too fresh the first
        // time. See [SettleAlarm].
        val settled = SettleAlarm.signalOf(intent)
        val result = if (settled != null) null else ActivityTransitionResult.extractResult(intent)
        if (settled == null && result == null) return

        val app = context.applicationContext
        val pending = goAsync()

        // The receiver's own window is a few milliseconds; goAsync buys us
        // enough to read the ledger and write a cue. It is not a licence to do
        // anything slow here.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (settled != null) settle(app, settled) else handle(app, result!!)
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

            val step = BoutTracker.advance(sensing.state, mapped)
            sensing.state = step.state

            val signal = step.signal ?: continue
            considerCue(context, store, signal)
        }
    }

    /**
     * The stop we held is now as old as the settle window. Ask again.
     *
     * Unless a walk has opened since — that stop is stale, and the new one
     * will be considered on its own terms when it ends.
     */
    private suspend fun settle(context: Context, signal: CuePolicy.Signal) {
        if (SensingStore(context).state.walkingSince != null) return
        considerCue(context, HarborStore(context), signal)
    }

    private suspend fun considerCue(
        context: Context,
        store: HarborStore,
        signal: CuePolicy.Signal,
    ) {
        val now = Instant.now()
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()

        val decision = CuePolicy.decide(
            signal = signal,
            settings = store.settings.value,
            day = store.dayState(today),
            now = now,
        )

        if (decision is CuePolicy.Decision.Hold) {
            // The one reason that is not a refusal: the stop is real and only
            // too recent, and the moment it is waiting for is in the future.
            // Every other reason means no, and no alarm is set for a no.
            if (decision.reason == CuePolicy.Reason.TRANSITION_UNSETTLED) {
                SettleAlarm.arm(context, signal)
            }
            // Held cues are not written anywhere. They are not events in the
            // user's life, and a ledger full of near-misses would make the
            // study's numbers mean something other than what they say.
            Log.i(TAG, "cue held: ${decision.reason}")
            return
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
        Log.i(TAG, "cue fired: ${cue.id} after ${signal.activeMinutes} min")
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
