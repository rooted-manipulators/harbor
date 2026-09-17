package app.harbor.sensing

import android.content.Context
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
    suspend fun repair(context: Context, store: HarborRepository) {
        if (!store.settings.value.cuesEnabled) return
        if (!ActivityTransitions.hasPermission(context)) return
        ActivityTransitions.register(context)
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

    fun isActive(context: Context, store: HarborRepository): Boolean =
        store.settings.value.cuesEnabled && ActivityTransitions.hasPermission(context)
}
