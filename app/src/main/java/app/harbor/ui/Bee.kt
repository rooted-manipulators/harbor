package app.harbor.ui

import androidx.compose.runtime.compositionLocalOf
import app.harbor.R
import app.harbor.domain.StudyArm
import app.harbor.domain.Weather

/**
 * Which arm of the study this install is in, for anything that draws.
 *
 * A [compositionLocalOf] rather than a parameter threaded through twenty
 * composables, and rather than a suspend call at each site: the arm is read
 * once when the app starts and cannot change while it is running, which is
 * exactly the shape a composition local is for. See
 * [app.harbor.ui.theme.LocalReducedMotion] for the same pattern.
 *
 * Defaults to [StudyArm.GARDEN] — the app as it already existed — so a screen
 * rendered outside the provider is the control rather than a half-dressed
 * experiment.
 */
val LocalStudyArm = compositionLocalOf { StudyArm.GARDEN }

/**
 * The bee's face for a given weather.
 *
 * ## Why the mood still travels as a weather
 *
 * The slider writes the same five [Weather] values in both arms. In the
 * garden they paint a sky; here they move a face. One stored value, two
 * renderings — which is what lets both arms be read into one table and
 * compared at all. An arm that invented its own scale would only measure
 * itself.
 *
 * So the names below are weather names describing a bee, and that is
 * deliberate rather than an oversight.
 *
 * ## The ladder
 *
 * Delighted, content, tired, sad, distraught. Two of the five source files
 * arrived called "storm"; the sadder of them is [Weather.STORM] and the other
 * is [Weather.RAIN], which is the reading that completes the ladder rather
 * than leaving a gap in the middle and two takes at the end.
 */
internal fun beeFace(weather: Weather): Int = when (weather) {
    Weather.BRIGHT -> R.drawable.bee_bright
    Weather.CLEAR -> R.drawable.bee_clear
    Weather.CLOUDY -> R.drawable.bee_cloudy
    Weather.RAIN -> R.drawable.bee_rain
    Weather.STORM -> R.drawable.bee_storm
}
