package app.harbor.sensing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import java.time.Instant
import app.harbor.data.HarborRepository

/**
 * Turning cues on and off.
 *
 * Two things have to move together — the user's `cuesEnabled` setting and the
 * Play services registration — and the order they move in matters in both
 * directions.
 */
object Sensing {

    /**
     * Register first, then record the setting, and only if registration was
     * actually accepted.
     *
     * The other order would let the settings screen say cues are on while
     * nothing is listening. The user would be waiting for something that can
     * never arrive, and would have no way to tell.
     *
     * @return false if the permission is missing or Play services refused, in
     *   which case nothing changed and the caller should say so.
     */
    suspend fun enable(context: Context, store: HarborRepository): Boolean {
        if (!ActivityTransitions.register(context)) return false
        store.setSettings(store.settings.value.copy(cuesEnabled = true))
        // Registration only buys the right to be woken. The service is what
        // keeps there being a process worth waking -- see SensingService.
        SensingService.start(context)
        return true
    }

    /**
     * Re-register if the user has cues on, whether or not anything is wrong.
     *
     * Registrations are lost by more than reboots, which is all [BootReceiver]
     * covers. Installing a new build force-stops the app, and Android delivers
     * nothing to a stopped app until it is launched by hand; Play services
     * updating itself can drop them too. None of that is visible — the setting
     * still says on, the screen still says on, and nothing is listening. Until
     * this existed the only cure was toggling reminders off and on again, and
     * nobody knew they had to.
     *
     * Called on every launch. Play services replaces an existing registration
     * rather than stacking another, so re-registering when nothing was wrong
     * costs a round trip and changes nothing.
     */
    suspend fun repair(context: Context, store: HarborRepository, replaced: Boolean = false) {
        if (!store.settings.value.cuesEnabled) return
        if (!ActivityTransitions.hasPermission(context)) return
        // Clear before asking again, but only after the package was replaced.
        //
        // requestActivityTransitionUpdates is documented to replace an
        // existing registration, and on an ordinary launch it plainly does --
        // that is the path sensing runs on every day, and it is working. After
        // a package replace it appears not to: measured on 18 Sep, an install
        // at 02:49:40 was followed by a successful re-register at 02:49:44 and
        // then no transition for eleven minutes, through a walk. What Play
        // services still holds there is a PendingIntent that died with the old
        // package.
        //
        // Narrow on purpose. Clearing on every launch would put an extra
        // unregister on the one path that is known to work, to fix a case that
        // only happens when the app is updated. [replaced] is true only from
        // ACTION_MY_PACKAGE_REPLACED.
        if (replaced) ActivityTransitions.unregister(context)
        ActivityTransitions.register(context)
        // Same reasoning as the registration: the service dies with the
        // process when an OEM kills it outright, and START_STICKY is the
        // system's promise, not a guarantee. Launching is a free chance to
        // put it back.
        SensingService.start(context)
    }

    /**
     * Record the setting first, then unregister.
     *
     * This order is deliberate too, and for a stronger reason. If unregistering
     * fails, transitions keep arriving — but [app.harbor.domain.CuePolicy]
     * checks `cuesEnabled` before anything else, so they are discarded and no
     * cue can reach someone who just said stop. Turning off has to be the
     * thing that cannot half-fail.
     */
    suspend fun disable(context: Context, store: HarborRepository) {
        store.setSettings(store.settings.value.copy(cuesEnabled = false))
        ActivityTransitions.unregister(context)
        // Last, and unconditionally: somebody who just turned reminders off
        // should watch the line leave the shade. Leaving it there would be the
        // app saying it had stopped while visibly still running.
        SensingService.stop(context)
    }

    /**
     * Whether sensing is genuinely running: the user asked for it, and the
     * permission that makes it possible is still granted.
     *
     * Permissions can be revoked from system settings while the app is not
     * looking, which leaves `cuesEnabled` true and nothing listening. Anything
     * that reports status to the user should ask this, not the setting alone.
     */
    /**
     * When the system last delivered a transition, or null if it never has.
     *
     * [isActive] only says the switch is on and the permission is granted. It
     * cannot tell whether Play services ever registered, whether the receiver
     * is still alive, or whether the OS put the app to sleep three days ago.
     * This can, and it is the difference between a null result you can
     * interpret and one you cannot.
     */
    fun lastTransition(context: Context): Instant? =
        SensingStore(context).lastTransitionAt

    /**
     * The last walk the tracker closed, what it measured, and what came of it.
     *
     * [lastTransition] answers "is the phone still talking to us". This answers
     * the question after it: something was heard, a walk was measured — so why
     * was there no reminder? Internal because the type is; the only caller is
     * the screen somebody visits when nothing arrives.
     */
    internal fun lastBout(context: Context): SensingStore.Recorded? =
        SensingStore(context).lastBout

    fun isActive(context: Context, store: HarborRepository): Boolean =
        store.settings.value.cuesEnabled && ActivityTransitions.hasPermission(context)

    /**
     * Whether the OS is allowed to put Harbor to sleep in the background.
     *
     * The quietest of the silent failures, and on some phones the most
     * complete. Harbor has no service of its own (ADR-008) — it lives as a
     * cached process waiting for Play services to wake it — and a cached
     * process that the OEM freezes never receives the transition at all. The
     * walk is sensed, the batch is delivered, nothing runs.
     *
     * Measured on a Galaxy S24+ (One UI, Android 16) on 2026-09-17: the
     * process was frozen roughly two minutes after being backgrounded
     * (`FreecessController: FZ ... reason: LEV`), and every transition across
     * three real walks was lost. The only ones that ever arrived came while
     * the app happened to be on screen. Nothing inside the app can tell that
     * from a week of never walking, which is why it has to be asked for
     * rather than hoped for.
     */
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    /**
     * The system's own dialog for granting it — one tap, in place, rather
     * than sending somebody hunting through Settings.
     *
     * Needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the manifest to be
     * offered this way. See the note there about what that costs.
     */
    fun unrestrictedRequest(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.fromParts("package", context.packageName, null),
    )
}
