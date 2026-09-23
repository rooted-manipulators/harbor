package app.harbor.domain

/**
 * Which of the eight bees a flower gets.
 *
 * The bees arm puts a bee beside each person's flower, and the bee holds the
 * flower in the mood of the call that grew it. There are twenty flowers and
 * eight bees, so this is the table that folds one into the other. The art is
 * `bee_mood_<name>` in the drawables, cut from one sheet by
 * `tools/cut_moods.py`.
 *
 * ## How the twenty fold into eight
 *
 * By what the flower's own words say about how the call left you -- its name
 * and its line in [Flowers] -- rather than by its colour. Two flowers that
 * look alike can mean opposite things, and a bee that looked anxious over
 * "glad she picked up" because the petals happened to be pale would be the
 * garden misreading the person.
 *
 * Every mood is reachable, and there is a test for that: a bee that no flower
 * ever summons is art the study paid for and nobody sees.
 */
enum class BeeMood {
    HAPPY, CALM, LOVED, CURIOUS, GROUNDED, HOPEFUL, BRAVE, ANXIOUS;

    companion object {
        fun of(kind: FlowerKind): BeeMood = when (kind) {
            FlowerKind.GLAD_WE_TALKED,
            FlowerKind.GLAD_SHE_PICKED_UP,
            -> HAPPY

            FlowerKind.LIGHTER_NOW,
            FlowerKind.EASY_SILENCE,
            -> CALM

            FlowerKind.FELT_LOVED,
            FlowerKind.SHE_REMEMBERED,
            -> LOVED

            FlowerKind.WANT_TO_TRY_SOMETHING,
            FlowerKind.ASKED_MORE_THAN_USUAL,
            FlowerKind.STILL_THINKING_ABOUT_IT,
            -> CURIOUS

            FlowerKind.STEADIER_NOW,
            FlowerKind.WORTH_SLOWING_DOWN,
            FlowerKind.NOTHING_LEFT_UNSAID,
            -> GROUNDED

            FlowerKind.LOOKING_FORWARD,
            FlowerKind.WISHED_IT_WAS_LONGER,
            -> HOPEFUL

            FlowerKind.SAID_WHAT_I_MEANT,
            FlowerKind.TIME_TO_ACTUALLY_DO_IT,
            FlowerKind.SAID_THE_HARD_THING,
            -> BRAVE

            // Anxious is not a verdict on the call. Two of these three are
            // calls the person made *anyway* -- "dreaded this one", "still
            // growing, called anyway" -- and the bee is worried for them, not
            // about them.
            FlowerKind.HARD_TO_SHAKE_OFF,
            FlowerKind.WONDERING_IF_THAT_LANDED,
            FlowerKind.DREADED_THIS_ONE,
            -> ANXIOUS
        }
    }
}
