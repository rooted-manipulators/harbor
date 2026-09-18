package app.harbor.sensing

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
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
 * only thing that survives a call is [SensingStore.offer], which is one
 * package name and two timestamps, kept so a single long stretch cannot
 * produce a reminder every two minutes.
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
 *
 * ## The split between [fold] and [current]
 *
 * [fold] is the measurement and is pure: a list of events in, a stretch out.
 * [current] is everything that needs a device — the system query, the screen
 * state, the exclusions. The measurement is the part with rules in it and the
 * part that was impossible to be sure of by reading, so it is the part that
 * has tests. See `ScrollWatchTest`.
 */
internal object ScrollWatch {

    /**
     * How far back the events are read.
     *
     * A stretch longer than this is measured as exactly this, which only ever
     * under-reports, and three hours is well past any threshold the settings
     * screen can be set to (180 minutes). The poll runs every couple of
     * minutes, so the only way to meet a stretch already older than this is
     * for the service to have been dead for three hours — in which case a
     * missed reminder is the least of it.
     */
    val LOOKBACK: Duration = Duration.ofHours(3)

    /**
     * How long a package may be away from the front and still be the same
     * stretch when it comes back.
     *
     * This is the rule that stops a phone in a pocket reading as four hours
     * of scrolling, and it exists because the obvious defence is not
     * reliable. [UsageEvents.Event.SCREEN_NON_INTERACTIVE] and `KEYGUARD_SHOWN`
     * are API 28, Harbor's minSdk is 26, and OEM builds vary in whether they
     * report them at all. Without them the stream for *open Instagram, screen
     * off for two hours, unlock* is a pause and then a resume of the same
     * package, which the continuity rule would happily treat as one session.
     *
     * Three minutes rather than seconds, because a stretch genuinely does
     * survive small interruptions: answering a notification, checking the
     * time, a system dialog. Those are the same sitting. Two hours in a
     * pocket is not.
     */
    val RESUME_GAP: Duration = Duration.ofMinutes(3)

    /** A package that has been in front, uninterrupted, since [startedAt]. */
    data class Stretch(val packageName: String, val startedAt: Instant) {
        fun minutesAt(now: Instant): Int =
            Duration.between(startedAt, now).toMinutes().toInt()
    }

    /**
     * One usage event, reduced to the three fields the measurement uses.
     *
     * A hand-rolled type rather than [UsageEvents.Event] because that class
     * cannot be constructed in a unit test — it is filled in by the framework
     * through a package-private setter — and the measurement is the part most
     * worth testing.
     */
    data class Ev(val type: Int, val packageName: String, val at: Instant)

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
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        } catch (e: Throwable) {
            // Not documented to throw. Seen to, on builds where the op is not
            // known. A permission we cannot ask about is one we do not have,
            // and saying so beats taking the settings screen down with us.
            Log.w(TAG, "could not check usage access", e)
            return false
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
     * Open it, and survive a device that resolved it and then refuses.
     *
     * [request] asking the package manager and the activity actually
     * starting are two different claims, and on OEM builds they disagree --
     * a settings screen can be present, resolvable, and guarded by a
     * permission the caller does not hold. An uncaught
     * `ActivityNotFoundException` or `SecurityException` here takes down the
     * onboarding step it is offered from, which is the worst place in the
     * app to crash.
     */
    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) {
            Log.w(TAG, "could not open usage access settings", e)
        }
    }

    /**
     * The stretch in progress, or null if there is not one.
     *
     * Null means the screen is off, or a call is in progress, or the app in
     * front is one that does not count, or the events do not reach back to a
     * moment we can call a beginning.
     */
    fun current(context: Context, now: Instant = Instant.now()): Stretch? {
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return null

        // A forty-minute video call is forty minutes in one app, and a
        // reminder to ring your mother in the middle of it would be the
        // worst thing this trigger could do. Free to check and needs no
        // permission, unlike TelephonyManager.getCallState.
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio != null &&
            (audio.mode == AudioManager.MODE_IN_CALL ||
                audio.mode == AudioManager.MODE_IN_COMMUNICATION)
        ) {
            return null
        }

        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null

        val events = try {
            read(usage, now)
        } catch (e: Throwable) {
            // Documented to throw nothing, observed to throw on OEM builds
            // where the service is missing or the permission was revoked
            // between the check and the call. A trigger that cannot be
            // measured is a trigger that does not fire, not a crash in a
            // foreground service that keeps the other one alive.
            Log.w(TAG, "usage events unavailable", e)
            return null
        }

        val stretch = fold(events, LOOKBACK.let { now.minus(it) }) ?: return null
        return if (counts(context, stretch.packageName)) stretch else null
    }

    /**
     * Walk the events and work out what has been in front, and since when.
     *
     * The rules, which are the whole of the measurement:
     *
     *  - a foreground event for a **different** package starts a new stretch;
     *  - one for the **same** package continues it, which is what makes a
     *    stretch survive the dozens of resumes a single session produces as
     *    you open a reel, a profile, a story;
     *  - unless that package had been paused for longer than [RESUME_GAP], in
     *    which case it is a new sitting. See that constant: this is the rule
     *    that stops two hours in a pocket reading as two hours of scrolling
     *    on the phones that do not report the screen going off;
     *  - a pause on its own does **not** end the stretch. Pauses fire
     *    constantly and are followed half a second later by a resume of the
     *    same app; treating one as an ending would mean no stretch ever
     *    reached twenty minutes;
     *  - the screen going off, or the keyguard appearing, ends it outright,
     *    where the device says so.
     *
     * @param since the start of the window the events came from. A stretch
     *   whose beginning is older than this cannot be seen, so it is reported
     *   as starting here — which under-reports, never over-reports.
     */
    fun fold(events: List<Ev>, since: Instant): Stretch? {
        var front: String? = null
        var startedAt: Instant? = null
        var pausedAt: Instant? = null

        for (event in events) {
            when (event.type) {
                MOVE_TO_FOREGROUND -> {
                    val gapped = pausedAt != null &&
                        Duration.between(pausedAt, event.at) > RESUME_GAP
                    if (event.packageName != front || gapped) {
                        front = event.packageName
                        startedAt = event.at
                    }
                    pausedAt = null
                }
                MOVE_TO_BACKGROUND, ACTIVITY_STOPPED -> {
                    // Only the app we are following, and only the first pause
                    // of a run: a pause-resume-pause inside one app should
                    // measure the gap from when it actually went away, not
                    // from the most recent flicker.
                    if (event.packageName == front && pausedAt == null) {
                        pausedAt = event.at
                    }
                }
                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN -> {
                    front = null
                    startedAt = null
                    pausedAt = null
                }
            }
        }

        val packageName = front ?: return null
        // A stretch that began before the window can only be dated to its
        // edge. Reporting the edge is the honest under-report; reporting the
        // first event we happened to see would be a guess.
        return Stretch(packageName, maxOf(startedAt ?: since, since))
    }

    /**
     * Whether time spent in this package is the behaviour the trigger is for.
     *
     * Three exclusions, and no list of app names among them:
     *
     *  - **Harbor.** Somebody reading their own garden for twenty minutes is
     *    not doomscrolling, and a reminder for it would be the app
     *    interrupting itself.
     *  - **The launcher.** Sitting on a home screen is not a sitting.
     *  - **Anything with no way to launch it.** System UI, the keyguard, and
     *    the various overlays that can be reported as foreground are not apps
     *    anybody chose to be in.
     */
    private fun counts(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(packageName) == null) return false
        return packageName != defaultLauncher(context)
    }

    private fun defaultLauncher(context: Context): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val match = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return match?.activityInfo?.packageName
    }

    private fun read(usage: UsageStatsManager, now: Instant): List<Ev> {
        val stream = usage.queryEvents(now.minus(LOOKBACK).toEpochMilli(), now.toEpochMilli())
        val out = ArrayList<Ev>()
        val event = UsageEvents.Event()
        while (stream.getNextEvent(event)) {
            val name = event.packageName ?: continue
            out += Ev(event.eventType, name, Instant.ofEpochMilli(event.timeStamp))
        }
        return out
    }

    /**
     * Event type numbers, written out because minSdk is 26.
     *
     * The first two were renamed `ACTIVITY_RESUMED` and `ACTIVITY_PAUSED` in
     * API 29 and the old names are deprecated; the last three cannot be
     * referenced by name below API 28 and 29 respectively. The numbers are
     * platform constants and do not move. Where a device does not report one,
     * the effect is described in [fold] — [RESUME_GAP] is there because
     * [SCREEN_NON_INTERACTIVE] cannot be relied on.
     */
    private const val MOVE_TO_FOREGROUND = 1
    private const val MOVE_TO_BACKGROUND = 2
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val KEYGUARD_SHOWN = 17
    private const val ACTIVITY_STOPPED = 23

    private const val TAG = "HarborSensing"
}
