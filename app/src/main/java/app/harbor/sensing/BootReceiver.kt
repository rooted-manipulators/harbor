package app.harbor.sensing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.harbor.data.HarborStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-registers transition updates after a reboot, and after Harbor itself is
 * replaced.
 *
 * Activity transition registrations do not survive a restart. Without this,
 * sensing would go quiet after every reboot and only come back the next time
 * the user happened to open Harbor — which, for an app whose entire premise
 * is that you forget to open it, means never.
 *
 * An update does the same damage and is easier to miss. Replacing the package
 * force-stops the app and invalidates the registration held against its old
 * PendingIntent, so a phone handed a new build mid-week stops sensing until
 * somebody opens the app by hand — and `docs/CLAUDE.md` is explicit that
 * participants will be handed new builds mid-week. Measured on 18 Sep: a
 * build landed at 02:49:40 and the walk that started seconds later produced
 * no transitions at all. `ACTION_MY_PACKAGE_REPLACED` is delivered to the app
 * being replaced, which is the earliest anything of ours can run.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "HarborSensing"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val woken = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!woken) return

        val app = context.applicationContext

        // Only if the user actually asked for cues. Re-registering for someone
        // who has them switched off would be sensing them without consent,
        // which is the one thing this app must never do.
        if (!HarborStore(app).settings.value.cuesEnabled) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val why = if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    "update"
                } else {
                    "boot"
                }
                Log.i(TAG, "$why re-register: ${ActivityTransitions.register(app)}")
                // Registrations do not survive a restart and neither does the
                // service. BOOT_COMPLETED is one of the exemptions that may
                // still start one from the background.
                SensingService.start(app)
            } catch (e: Throwable) {
                // Sensing stays down until the app is next opened. Bad, but a
                // crash in a boot receiver is worse.
                Log.e(TAG, "boot re-register failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
