package app.harbor.sensing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import java.time.Duration
import java.time.Instant

/**
 * The second trigger: how long you have been in one app, right now.
 *
 * ## What it reads, and what it cannot
 *
 * [UsageStatsManager] hands back a stream of events saying *an activity of
 * package X came to the front at time T*. That is the whole of it. There is no
 * screen content here, no text, no URLs, no keystrokes — the API cannot
 * provide them and Harbor asks for nothing else. The disclosure on the
 * onboarding screen says exactly this, and the wording there is written
 * against this file.
 *
 * Nothing is stored. Each call re-reads the last few hours from the system and
 * throws the events away; Harbor keeps no history of the apps you open. The
 * only thing that survives a call is [SensingStore.firedStretch], which is one
 * package name and one timestamp, kept so a single long stretch cannot produce
 * two reminders.
 *
 * ## Why it is a poll and not a callback
 *
 * There is no "you have been in this app for twenty minutes" broadcast. The
 * two things Android will tell you about — the screen going off, and an app
 * being launched — are both the wrong end of the stretch: by the time the
 * screen is off, the moment for the reminder has gone. So the stretch has to
 * be measured while it is happening, which means asking. See
 * [SensingService], which does the asking on a timer it already keeps a
 * process alive for.
 *
 * ## Why the app is not named
 *
 * The brief said Instagram. This does not know what Instagram is, and the
 * threshold applies to whatever you have been in — a feed, a game, a
 * spreadsheet. Partly that is because a hardcoded list of "bad" apps is a
 * judgement Harbor has no standing to make, and partly because it would be
 * wrong within a week of any of them changing package names. The person who
 * spends forty minutes in one app knows which app it was.
 */
internal object ScrollWatch {

    /**
     * How far back the events are read.
     *
     * A stretch longer than this is measured as exactly this, which only ever
     * under-reports, and three hours is well past any threshold the settings
     * screen can be set to (180 minutes).
     */
    private val LOOKBACK: Duration = Duration.ofHours(3)

    /** A package that has been in front, uninterrupted, since [startedAt]. */
    data class Stretch(val packageName: String, val startedAt: Instant) {
        fun minutesAt(now: Instant): Int =
            Duration.between(startedAt, now).toMinutes().toInt()
    }

    /**
     * Whether the user has granted usage access.
     *
     * Not a runtime permission: there is no dialog to request, only a screen
     * in Settings with a switch on it. So this is an AppOps check rather than
     * a `checkSelfPermission`, and [request] opens that screen.
     *
     * `MODE_DEFAULT` means the op has no explicit setting yet and the decision
     * falls back to the manifest permission, which for a signature-level
     * permission like this one will be a refusal on any ordinary build — but
     * the fallback is asked properly rather than assumed, because assuming is
     * how you get a screen that says "not granted" on a device where it is.
     */
    fun hasPermission(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkPermission(
                android.Manifest.permission.PACKAGE_USAGE_STATS,
                Process.myPid(),
                Process.myUid(),
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * The Settings screen that grants it, or null if this device has no such
     * screen.
     *
     * Null is a real answer on some builds — the action is optional and a few
     * OEM skins have dropped it — and a caller that assumed otherwise would
     * crash on exactly the phones this study is least able to debug. Callers
     * hide the offer rather than making a promise the device cannot keep.
     */
    fun request(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        @Suppress("DEPRECATION")
        val handler = context.packageManager.resolveActivity(intent, 0)
        return if (handler != null) intent else null
    }

    /**
     * The stretch in progress, or null if there is not one.
     *
     * Null means the screen is off, or the events do not reach back to a
     * moment we can call a beginning, or the app in front is Harbor itself.
     *
     * The rules, which are the whole of the measurement:
     *
     *  - a foreground event for a *different* package starts a new stretch;
     *    one for the same package continues it, which is what makes a stretch
     *    survive the dozens of resumes a single session produces;
     *  - the screen going off, or the keyguard appearing, ends it. Otherwise
     *    an hour in a pocket would be read as an hour of scrolling, since the
     *    same app resumes on unlock;
     *  - Harbor itself never counts. A person reading their own garden for
     *    twenty minutes is not the behaviour this is looking for, and a
     *    reminder for it would be the app interrupting itself.
     */
    fun current(context: Context, now: Instant = Instant.now()): Stretch? {
        val power = context.getSystemService(PowerManager::class.java)
        if (power?.isInteractive == false) return null

        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null

        val events = try {
            usage.queryEvents(now.minus(LOOKBACK).toEpochMilli(), now.toEpochMilli())
        } catch (e: Throwable) {
            // Documented to throw nothing, observed to throw on OEM builds
            // where the service is missing. A trigger that cannot be measured
            // is a trigger that does not fire, not a crash in a foreground
            // service that keeps the other one alive.
            Log.w(TAG, "usage events unavailable", e)
            return null
        }

        var packageName: String? = null
        var startedAt = 0L
        val event = UsageEvents.Event()

        while (events.getNextEvent(event)) {
            when (event.eventType) {
                MOVE_TO_FOREGROUND -> {
                    if (event.packageName != packageName) {
                        packageName = event.packageName
                        startedAt = event.timeStamp
                    }
                }
                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN -> {
                    packageName = null
                    startedAt = 0L
                }
            }
        }

        val front = packageName ?: return null
        if (front == context.packageName) return null
        return Stretch(front, Instant.ofEpochMilli(startedAt))
    }

    /**
     * API 28 constants, written out because minSdk is 26.
     *
     * Referencing [UsageEvents.Event.SCREEN_NON_INTERACTIVE] directly compiles
     * and inlines to the same number, but lint reads it as a call into a newer
     * API and it is clearer to say what these are. They simply never appear in
     * the stream on 26 and 27, where the effect is that a stretch is not
     * ended by the screen going off — the interactive check above catches the
     * live case, and the worst a stale one can do is over-report a stretch
     * that has already stopped being one.
     */
    /**
     * Renamed `ACTIVITY_RESUMED` in API 29, same number. Written out rather
     * than suppressing a deprecation on a constant that cannot be referenced
     * by its new name below API 29.
     */
    private const val MOVE_TO_FOREGROUND = 1

    private const val SCREEN_NON_INTERACTIVE = 16
    private const val KEYGUARD_SHOWN = 17

    private const val TAG = "HarborSensing"
}
