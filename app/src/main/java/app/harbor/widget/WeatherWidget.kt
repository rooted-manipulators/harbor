package app.harbor.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.harbor.data.HarborStore
import app.harbor.domain.Moment
import app.harbor.domain.Weather
import app.harbor.ui.label
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Cream
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.Paper
import app.harbor.ui.theme.Sand

/**
 * `WeatherBar`'s "How is life right now" — the rail, not the card underneath.
 *
 * Same card treatment (`Paper` behind `Cream`, 24dp radius), same 26dp rail
 * height and five-stop colours as `WeatherBar.kt`'s own rail, same "How is
 * life right now" / current-weather-word header row.
 *
 * Two things stay genuinely out of reach of a home-screen widget, not by
 * choice:
 *
 * - **The bundled font.** A widget renders through RemoteViews' TextView,
 *   which cannot load a custom typeface — only the handful of generic
 *   families in [FontFamily]. [FontFamily.SansSerif] is the nearest of those
 *   to Manjari; it is a substitute, not the same face.
 * - **Dragging the thumb.** Widgets receive taps on fixed regions, never a
 *   pointer stream — there is no `pointerInput`/gesture API for App Widgets
 *   at all, on any launcher. The rail below is drawn continuously, in the
 *   same five colours and the same order as `WeatherBar`'s gradient, with a
 *   thumb mark over whichever fifth is current; moving it is five taps at
 *   fixed points rather than one drag, because a drag has nowhere to attach
 *   on this surface.
 *
 * The daily one-word question folded into the same card in `WeatherBar.kt`
 * has its own text field and stays out of this widget on purpose.
 */
class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = HarborStore(context)
        val weather = store.settings.value.weather

        provideContent {
            // Paper first, Cream over it — the same two layers `Surface` in
            // Harbor.kt draws as `.background(Cream)` on a page that is
            // already Paper. A widget has no page under it to inherit that
            // colour from, so this draws its own.
            Box(GlanceModifier.fillMaxWidth().cornerRadius(24.dp).background(Paper)) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(24.dp)
                        .background(Cream)
                        .padding(18.dp),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "How is life right now",
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                color = ColorProvider(Muted),
                                fontSize = 13.sp,
                            ),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        Text(
                            weather.label,
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                color = ColorProvider(Chalk),
                                fontSize = 17.sp,
                            ),
                        )
                    }

                    Spacer(GlanceModifier.height(14.dp))

                    // The rail: one continuous pill (the outer cornerRadius
                    // clips the five square-cornered zones inside it to that
                    // shape), Sand where WeatherBar's rail-back sits, filled
                    // in its own five stop colours left to right.
                    Box(
                        GlanceModifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .cornerRadius(13.dp)
                            .background(Sand),
                    ) {
                        Row(GlanceModifier.fillMaxSize()) {
                            Weather.entries.forEach { step ->
                                RailZone(step, selected = step == weather)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.RailZone(step: Weather, selected: Boolean) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .background(railStopColor(step))
            .clickable(
                actionRunCallback<SetWeatherAction>(
                    actionParametersOf(WeatherStepKey to step.name),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The thumb: WeatherBar's own is a plain white disc riding the rail.
        if (selected) {
            Box(GlanceModifier.size(16.dp).cornerRadius(8.dp).background(Chalk)) {}
        }
    }
}

/** The rail's own five gradient stops (`WeatherBar.kt`), one solid colour per fifth. */
private fun railStopColor(step: Weather): Color = when (step) {
    Weather.CLEAR -> Color(0xFF2B4F6B)
    Weather.BRIGHT -> Color(0xFF6F8FA8)
    Weather.CLOUDY -> Color(0xFFE8D6A8)
    Weather.RAIN -> Color(0xFFF0A35F)
    Weather.STORM -> Color(0xFFC9542C)
}

internal val WeatherStepKey = ActionParameters.Key<String>("weather_step")

/** Sets [Weather] exactly as `WeatherBar.choose()` does — same settings write, same note. */
internal class SetWeatherAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val stepName = parameters[WeatherStepKey] ?: return
        val step = Weather.entries.firstOrNull { it.name == stepName } ?: return

        val store = HarborStore(context)
        val settings = store.settings.value
        if (step != settings.weather) {
            store.setSettings(settings.copy(weather = step))
            store.note(Moment.WEATHER_SET, step.name.lowercase())
        }

        WeatherWidget().update(context, glanceId)
    }
}
