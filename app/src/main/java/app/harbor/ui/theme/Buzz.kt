package app.harbor.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The small physical tick that tells you a thing moved.
 *
 * ## Why this is not `LocalHapticFeedback`
 *
 * Compose offers `HapticFeedbackType.LongPress`, which is what the schedule
 * used for its day swipe and what a first pass reaches for. It is the wrong
 * shape: a long press is a heavy, single thud designed to announce *you have
 * held this down long enough for something to happen*. Used on a swipe it
 * lands after the motion and reads as a bump rather than a detent — several
 * of them in a row feel like the phone complaining.
 *
 * What a card moving one place wants is the tick a physical dial makes as it
 * passes a notch: short, dry, and slightly quieter than you expect. Android
 * has one — `SEGMENT_TICK`, added in API 34 for exactly this — and it is
 * only reachable through the platform `View`, which is why this goes around
 * Compose rather than through it.
 *
 * Below 34 the closest is `CLOCK_TICK`, which is the old time-picker detent
 * and the nearest thing any older phone has. Neither is guaranteed: a device
 * with haptics switched off, or no motor worth using, does nothing, and that
 * is a correct outcome rather than a case to work around.
 *
 * ## Reduced motion
 *
 * Deliberately **not** gated on [LocalReducedMotion]. That setting is about
 * things moving on screen, which is a vestibular and attention concern; a
 * haptic is neither, and for somebody who has turned animation off it is
 * often the only remaining signal that the swipe took. Silencing it there
 * would take away the feedback from the person most relying on it.
 */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.tick() } }
}

/**
 * The heavier one, for something arriving rather than passing.
 *
 * Reserved for a completion — a card that has landed somewhere new, a block
 * that has been dropped. If everything uses this, nothing means anything.
 */
@Composable
fun rememberThud(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.thud() } }
}

private fun View.tick() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            @Suppress("DEPRECATION")
            HapticFeedbackConstants.CLOCK_TICK
        },
    )
}

private fun View.thud() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )
}
