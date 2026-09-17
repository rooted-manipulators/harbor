package app.harbor.sensing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.harbor.domain.CuePolicy
import app.harbor.domain.TriggerSource
import java.time.Instant

/**
 * The second look at a stop that was still too fresh to act on.
 *
 * [CuePolicy] holds a cue until the person has been still for
 * [CuePolicy.SETTLE], so Harbor does not fire at a traffic light. But Play
 * services delivers the STILL transition within seconds of detecting it, which
 * is always *inside* that window — so every sensed cue was held once, dropped,
 * and never asked about again. No real walk could produce a reminder; the
 * pipeline worked and the last stage threw the result away.
 *
 * So the hold arms this, and the decision is made a second time once the
 * stillness is genuinely as old as the window claims.
 *
 * Inexact on purpose: `setAndAllowWhileIdle` needs no permission, while an
 * exact alarm would mean `SCHEDULE_EXACT_ALARM`. A reminder that arrives a
 * couple of minutes into the stillness is still the moment — another
 * permission on the install funnel is not worth those seconds.
 */
object SettleAlarm {

    const val ACTION = "app.harbor.sensing.SETTLE"

    /** One armed settle at a time: a newer stop replaces an older one. */
    private const val REQUEST_CODE = 2

    private const val EXTRA_SOURCE = "source"
    private const val EXTRA_ACTIVE_MINUTES = "active_minutes"
    private const val EXTRA_STILL_SINCE = "still_since"

    fun arm(context: Context, signal: CuePolicy.Signal) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            signal.stillSince.plus(CuePolicy.SETTLE).toEpochMilli(),
            pendingIntent(context, signal),
        )
    }

    /** The signal this alarm was armed for, or null if the intent is not ours. */
    fun signalOf(intent: Intent): CuePolicy.Signal? {
        if (intent.action != ACTION) return null
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: return null
        val stillSince = intent.getLongExtra(EXTRA_STILL_SINCE, -1L)
        if (stillSince < 0) return null
        return CuePolicy.Signal(
            source = TriggerSource.valueOf(source),
            activeMinutes = intent.getIntExtra(EXTRA_ACTIVE_MINUTES, 0),
            stillSince = Instant.ofEpochMilli(stillSince),
        )
    }

    private fun pendingIntent(context: Context, signal: CuePolicy.Signal): PendingIntent {
        val intent = Intent(context.applicationContext, TransitionReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_SOURCE, signal.source.name)
            putExtra(EXTRA_ACTIVE_MINUTES, signal.activeMinutes)
            putExtra(EXTRA_STILL_SINCE, signal.stillSince.toEpochMilli())
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context.applicationContext, REQUEST_CODE, intent, flags)
    }
}
