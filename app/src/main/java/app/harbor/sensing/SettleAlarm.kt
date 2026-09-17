package app.harbor.sensing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.harbor.domain.CuePolicy
import java.time.Duration
import java.time.Instant

/**
 * The clock that comes back to a walk once it has settled.
 *
 * ## Why this exists
 *
 * [CuePolicy] will not fire on a stop until the user has been still for
 * [CuePolicy.SETTLE] — the rule that stops a reminder arriving at a traffic
 * light. But the only thing that ever ran the policy was
 * [TransitionReceiver], woken by Play services at the instant stillness was
 * detected, which is the one moment the settle check is guaranteed to refuse.
 * Nothing came back afterwards, and the closed bout was discarded, so a real
 * walk could never produce a reminder at all. This is the thing that comes
 * back.
 *
 * ## Why an alarm and not a service
 *
 * ADR-008 rules out a foreground service and its reasoning holds: the app
 * needs to be woken briefly at a known later time, not to stay running. An
 * alarm is exactly that, costs no permission, no notification and no battery
 * between firings, and leaves the receiver-shaped pipeline intact.
 *
 * ## Inexact on purpose
 *
 * [AlarmManager.setAndAllowWhileIdle] is allowed through Doze and needs no
 * permission. `setExactAndAllowWhileIdle` would be tighter, but from API 31 it
 * needs `SCHEDULE_EXACT_ALARM`, and a permission prompt for a ninety-second
 * timer is a bad trade in an app whose whole permission budget is spent on
 * activity recognition (see docs/00-product.md). Doze can hold this until a
 * maintenance window, which is why the signal carries its own timestamp and
 * [CuePolicy.settleExpired] throws away anything that lands far too late.
 *
 * Elapsed-realtime rather than wall-clock: the wait is a duration, and a
 * timezone change or an NTP correction in the middle of it should not move it.
 */
internal object SettleAlarm {

    /**
     * Wake us once [signal] has been still long enough to be asked about
     * again. Replaces any alarm already set — there is only ever one walk
     * waiting, and the newest stop is the one that matters.
     */
    fun schedule(context: Context, signal: CuePolicy.Signal, now: Instant) {
        val remaining = CuePolicy.SETTLE.minus(Duration.between(signal.stillSince, now))
        val delayMillis = remaining.toMillis().coerceAtLeast(0L)

        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + delayMillis

        // No version guard: setAndAllowWhileIdle is API 23 and minSdk is 26.
        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent(context))
        Log.i(TAG, "settle re-ask in ${delayMillis}ms")
    }

    /** The walk stopped being a walk that ended — they moved again. */
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(intent(context))
    }

    private fun intent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, SettleReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context.applicationContext, REQUEST_CODE, intent, flags)
    }

    private const val REQUEST_CODE = 2
    private const val TAG = "HarborSensing"
}
