package app.harbor.ui

import androidx.compose.ui.graphics.Color
import app.harbor.domain.Weather

/**
 * The sky's own four colours, one set per weather.
 *
 * ## Why these are not [meadowFor]'s
 *
 * The wash used to be built entirely out of the prototype's landscape
 * palette: its haze overhead, its three sky tones under that, deepened on the
 * way down. That was the honest thing to do while the question was *does the
 * prototype's meadow port across*, and the answer was yes.
 *
 * It stops being the right source the moment the question changes to *what
 * does this weather feel like*. The prototype's five skies are five samples of
 * one daylight sky — a pale blue-grey seen through more or less air. Deepening
 * them pulls them apart a little, but they are still one colour family, and a
 * row of the five side by side reads as one sky at five exposures rather than
 * as five weathers. The references asked for the opposite: gradients that are
 * unmistakably different from each other, saturated, and closer to a painted
 * sky than to a photographed one.
 *
 * So the sky is hand-mixed here and the *land* is still the prototype's. That
 * split is deliberate. The land stops in the wash have to agree with the dots
 * the field actually draws — they are the colour seen between the marks — and
 * those come from [Meadow]. Inventing ground colours here would put the haze
 * and the thing standing in it on two different greens, which is the exact bug
 * the haze was added to fix.
 *
 * ## The rules every row keeps
 *
 * Lightest at the top, darkening the whole way down into the page: the wash
 * is a wash and not a view, so the weather is told by which colours it is made
 * of rather than by where the light sits in it. And every row has to arrive at
 * a green before it arrives at the ground, because the field is green and a
 * sky that meets it without warning draws a line across the screen.
 *
 * ## Where each one comes from
 *
 * | | |
 * | --- | --- |
 * | [Weather.CLEAR] | an actual blue day: pale cyan overhead, proper blue at the horizon |
 * | [Weather.BRIGHT] | the warm end of the yellow-through-green reference — a sky bleached by its own sun |
 * | [Weather.CLOUDY] | flat sage-grey, the calm register of the dark green-teal reference |
 * | [Weather.RAIN] | the cold end: teal carrying most of the colour, the light thin above it |
 * | [Weather.STORM] | the dark teal reference: heavy, cold, still lit along the top |
 *
 * **Storm has now been argued three times, and this is the third answer.** It
 * turned the whole app navy; that was reversed to the ported palette's pale
 * flat overcast; that was replaced with a lurid orange on the theory that a
 * storm should be the loudest row rather than the darkest one. The orange was
 * wrong in the way theories usually are -- it read as a sunset. Asked what a
 * storm should actually look like, the answer was the dark teal reference:
 * heavy and cold, with the light thin and grey-green along the top.
 *
 * So the old principle -- that the reflection belongs in the *quality* of the
 * light rather than the amount -- survives only in the weaker form the
 * references always implied: every row still has its light at the top and its
 * dark at the bottom, and no row washes the whole app down. Within that,
 * storm is allowed to be the dimmest, because it is the one that is supposed
 * to feel like weather you would come inside from.
 */
internal data class Wash(
    /** The lightest tone there is, at the very top. Also what the warm pool is made of. */
    val high: Color,
    val pale: Color,
    val mid: Color,
    /**
     * The last of the sky before the land takes over, and the stop that does
     * the most work: it is where the weather's own colour has to have become
     * something green enough to hand over to a field.
     */
    val deep: Color,
    /**
     * The disc, which has to be lighter than the sky it sits in.
     *
     * It used to be one warm yellow for all five, which was fine while every
     * sky was a pale blue-grey. It is not fine now — a yellow sun on
     * [Weather.BRIGHT]'s gold sky is invisible, and that is the one day that
     * most needs a sun in it.
     */
    val sun: Color,
)

/** The five, as mixed. */
internal fun washFor(weather: Weather): Wash = when (weather) {
    Weather.CLEAR -> Wash(
        high = Color(0xFFDFF3F8), pale = Color(0xFFA8DCEF),
        mid = Color(0xFF5AAEDA), deep = Color(0xFF2273A2),
        sun = Color(0xFFFFF2C4),
    )
    Weather.BRIGHT -> Wash(
        high = Color(0xFFFFE486), pale = Color(0xFFFBC63F),
        mid = Color(0xFFD9B027), deep = Color(0xFF8FA82A),
        sun = Color(0xFFFFFBEC),
    )
    Weather.CLOUDY -> Wash(
        high = Color(0xFFD5DCD8), pale = Color(0xFFAEBCB8),
        mid = Color(0xFF7C8F8B), deep = Color(0xFF4E5F5E),
        sun = Color(0xFFF2F4EE),
    )
    Weather.RAIN -> Wash(
        high = Color(0xFFBCD6D1), pale = Color(0xFF6FA79E),
        mid = Color(0xFF2F8077), deep = Color(0xFF14514F),
        sun = Color(0xFFDCE6E2),
    )
    Weather.STORM -> Wash(
        // Darker than [Weather.RAIN] at every stop, and greener with it. The
        // two are the closest pair in the table and they have to be told
        // apart at a glance: rain is the brighter, bluer teal of weather you
        // would walk in, storm the dim green-grey of weather you would not.
        high = Color(0xFF8AAC9F), pale = Color(0xFF4E857A),
        mid = Color(0xFF2F5F58), deep = Color(0xFF27544C),
        sun = Color(0xFFB9CCC4),
    )
}
