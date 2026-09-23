package app.harbor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.harbor.domain.TourStop
import kotlinx.coroutines.delay

/**
 * Where each thing the tour points at currently is on screen. ADR-016.
 *
 * Deliberately a plain map, not snapshot state. Every tagged element writes
 * its bounds here on every placement -- which during a scroll is every
 * frame -- and a state map would turn each of those writes into snapshot
 * traffic whether or not a tour was running. Nothing reads this unless the
 * overlay is up, and the overlay polls it once a frame while it is, so the
 * cost when nobody is touring is one hash-map put per placement.
 */
class TourAnchors {
    internal class Spot(val rect: Rect, val round: Boolean)

    internal val spots = HashMap<TourStop, Spot>()
}

val LocalTourAnchors = staticCompositionLocalOf { TourAnchors() }

/** The stop the tour is standing on, or null. Screens read it to show what it is about to point at. */
val LocalTourStop = compositionLocalOf<TourStop?> { null }

/**
 * Mark this element as what the tour points at for [stops].
 *
 * Reports its bounds to [LocalTourAnchors], and when the tour arrives at one
 * of its stops scrolls itself into view with room round it for the speech
 * bubble -- the calendar pill sits under a twenty-four-hour grid, and a
 * spotlight on something off the bottom of the screen is a dark screen.
 *
 * [round] cuts the spotlight as a circle rather than a rounded rectangle,
 * for the dial.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tourAnchor(vararg stops: TourStop, round: Boolean = false): Modifier {
    val anchors = LocalTourAnchors.current
    val here = LocalTourStop.current?.takeIf { it in stops }
    val requester = remember { BringIntoViewRequester() }
    val room = with(LocalDensity.current) { 180.dp.toPx() }
    val size = remember { floatArrayOf(0f, 0f) }

    LaunchedEffect(here) {
        if (here == null) return@LaunchedEffect
        // After the screen has faded in and been laid out; before that
        // there is nothing to scroll to.
        delay(260)
        requester.bringIntoView(Rect(0f, -room, size[0], size[1] + room))
    }
    DisposableEffect(anchors) {
        onDispose { stops.forEach { anchors.spots.remove(it) } }
    }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            size[0] = coordinates.size.width.toFloat()
            size[1] = coordinates.size.height.toFloat()
            val spot = TourAnchors.Spot(coordinates.boundsInRoot(), round)
            stops.forEach { anchors.spots[it] = spot }
        }
}
