package app.harbor.ui

import java.time.LocalTime

/**
 * What the sky is doing, which is now a question about the hour.
 *
 * ## Why the slider stopped painting it
 *
 * The wash used to be the mood the user set: a stormy answer made the whole
 * app stormy. That was the most distinctive thing Harbor did, and it stopped
 * making sense the moment the slider began opening on a guess read off the
 * calendar. A timetable saying "you have six hours of lectures" is not a
 * reason to darken somebody's sky, and a wash that changed because the app
 * inferred something is a wash nobody asked for.
 *
 * So the two came apart. The slider says how full the day is; the sky says
 * what time it is. Both are true without either one speaking for the other.
 *
 * ## Why the clock rather than a weather service
 *
 * A forecast needs a network call, a key, a permission prompt and somewhere
 * for a failure to go -- and the first rule of this app is that nothing it
 * needs may depend on the network (ADR-003). The hour needs none of that, is
 * never wrong, and answers the thing a sky is actually for: it should feel
 * like the time of day it is.
 *
 * Kept deliberately coarse. Day, dusk and night are what a person notices out
 * of a window; a continuous sun position would be a lot of arithmetic for a
 * gradient behind a field of dots.
 */
enum class SkyHour {
    DAY,
    DUSK,
    NIGHT;

    companion object {
        /**
         * The hour's sky.
         *
         * Dusk is a band rather than an instant because the light really does
         * take about an hour either side, and because a sky that flipped at
         * exactly 18:00 would look like a bug to anybody watching at 17:59.
         */
        fun of(now: LocalTime): SkyHour = when (now.hour) {
            in 7..16 -> DAY
            in 17..19 -> DUSK
            in 5..6 -> DUSK
            else -> NIGHT
        }
    }
}
