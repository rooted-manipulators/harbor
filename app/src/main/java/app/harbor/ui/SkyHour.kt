package app.harbor.ui

import java.time.LocalTime

/**
 * What the sky is doing, in the arm where that is a question about the hour.
 *
 * ## Whose sky this is
 *
 * The bees arm's. See [SkySays]: the garden arm's sky is still the slider's
 * answer, because that is the thing being compared against and taking it
 * away left the control with nothing to show.
 *
 * ## Why the bees arm's sky is not the slider either
 *
 * The wash is the mood the user set: a stormy answer makes the whole app
 * stormy. That is the most distinctive thing Harbor does, and in the bees
 * arm it would be the second place the same answer appeared -- the bee
 * already carries it, and an arm that showed the mood twice would not be one
 * metaphor against another, it would be both.
 *
 * There is a smaller reason too, and it applies to either arm: the slider
 * began opening on a guess read off the calendar, and a timetable saying
 * "you have six hours of lectures" is a thin reason to darken somebody's
 * sky. In the garden arm that is a cost worth paying, because the sky *is*
 * the feature. In the bees arm there is nothing to pay it for.
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
