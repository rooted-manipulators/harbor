package app.harbor.sensing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.harbor.data.HarborStore
import app.harbor.domain.CuePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The second half of a walk that ended: the re-ask, once it has settled.
 *
 * [TransitionReceiver] parks the signal and [SettleAlarm] wakes this. By the
 * time it runs the only gate that was failing — [CuePolicy.Reason]
 * `TRANSITION_UNSETTLED` — has resolved by the passage of time, so the same
 * gauntlet runs again and this time it can pass.
 *
 * Every path through here clears the parked signal. A walk gets exactly one
 * re-ask: whatever the answer, it is not asked a third time, and a signal that
 * somehow outlived its alarm cannot sit in storage waiting to surprise
 * somebody tomorrow.
 */
class SettleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handle(app)
            } catch (e: Throwable) {
                Log.e(TAG, "settle re-ask failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context) {
        val sensing = SensingStore(context)
        val signal = sensing.pending ?: return
        sensing.pending = null

        val now = Instant.now()

        // Dropped rather than fired. The alarm is inexact and Doze can hold it
        // for minutes; a stop that finished long ago is not the moment this
        // reminder was for, and arriving late at the wrong moment is the one
        // failure the policy exists to prevent.
        if (CuePolicy.settleExpired(signal.stillSince, now)) {
            Log.i(TAG, "settle re-ask too late, dropped")
            return
        }

        CueGate.consider(context, HarborStore(context), signal, now)
    }

    private companion object {
        const val TAG = "HarborSensing"
    }
}
