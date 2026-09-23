package app.harbor.domain

/**
 * The tour: a short, bee-narrated walk through the app somebody just
 * onboarded into.
 *
 * Pure sequencing only -- which stops happen, in what order, for this
 * install. The words each stop says and which of the bee's poses says them
 * are presentation and live beside `MainActivity`'s navigation, the same
 * split `CuePolicy` and `DayArcs` draw between the decision and the drawing.
 *
 * ## Why the bee, in both arms
 *
 * [StudyArm] is strict elsewhere: only rendering and words differ, and the
 * garden arm's whole identity is *no character*, on purpose (see that file).
 * This is the one deliberate exception, and it is scoped tightly enough not
 * to erode that rule everywhere else:
 *
 *  - It is scaffolding, not the studied experience. The tour is shown once,
 *    can be skipped at every stop, and touches nothing `StudyExport` reads.
 *    A participant who never opens it again has an identical week either way.
 *  - It never appears again. `GARDEN_BEE` -- the one stop that would put a
 *    bee inside the field itself -- is excluded outside the bees arm, so the
 *    ongoing screens stay exactly as separated as they were.
 *
 * If this ever grows into something that shapes what a participant sees
 * session to session, that reasoning stops holding and the exception needs
 * revisiting.
 */
enum class TourStop {
    WELCOME,
    HOME_PEOPLE,
    HOME_ADD,
    GARDEN_FIELD,
    GARDEN_FLOWER,
    GARDEN_BEE,
    PERSON_DIAL,
    PERSON_PLANT,
    SCHEDULE_VIEWS,
    SCHEDULE_PALETTE,
    SCHEDULE_QUIET,
    SCHEDULE_COPY,
    SCHEDULE_CALENDAR,
    ACCOUNT_REMINDERS,
    ACCOUNT_DONE,
}

/** Which top-level screen a stop is shown on. `MainActivity` owns the rest of the navigation. */
enum class TourScreen { HOME, GARDEN, PERSON, SCHEDULE, ACCOUNT }

object Walkthrough {

    /**
     * The stops this install's tour has, in order.
     *
     * [hasPerson] narrows rather than substitutes: a stop about someone's
     * patch or their week makes no sense before anyone has been added, and
     * onboarding cannot finish without adding one (`WhoToCall` is not
     * skippable) -- so `false` only matters for whoever reaches the tour
     * with their one contact since deleted. Nothing is invented to fill the
     * gap; the stop is simply not there.
     */
    fun stops(arm: StudyArm, hasPerson: Boolean): List<TourStop> = buildList {
        add(TourStop.WELCOME)
        if (hasPerson) add(TourStop.HOME_PEOPLE)
        add(TourStop.HOME_ADD)
        add(TourStop.GARDEN_FIELD)
        if (hasPerson) add(TourStop.GARDEN_FLOWER)
        if (arm == StudyArm.BEES) add(TourStop.GARDEN_BEE)
        if (hasPerson) {
            add(TourStop.PERSON_DIAL)
            add(TourStop.PERSON_PLANT)
        }
        add(TourStop.SCHEDULE_VIEWS)
        add(TourStop.SCHEDULE_PALETTE)
        add(TourStop.SCHEDULE_QUIET)
        add(TourStop.SCHEDULE_COPY)
        add(TourStop.SCHEDULE_CALENDAR)
        add(TourStop.ACCOUNT_REMINDERS)
        add(TourStop.ACCOUNT_DONE)
    }

    /** The screen a stop is shown on -- `MainActivity` reads this to navigate there. */
    fun screenFor(stop: TourStop): TourScreen = when (stop) {
        TourStop.WELCOME, TourStop.HOME_PEOPLE, TourStop.HOME_ADD -> TourScreen.HOME
        TourStop.GARDEN_FIELD, TourStop.GARDEN_FLOWER, TourStop.GARDEN_BEE -> TourScreen.GARDEN
        TourStop.PERSON_DIAL, TourStop.PERSON_PLANT -> TourScreen.PERSON
        TourStop.SCHEDULE_VIEWS,
        TourStop.SCHEDULE_PALETTE,
        TourStop.SCHEDULE_QUIET,
        TourStop.SCHEDULE_COPY,
        TourStop.SCHEDULE_CALENDAR,
        -> TourScreen.SCHEDULE
        TourStop.ACCOUNT_REMINDERS, TourStop.ACCOUNT_DONE -> TourScreen.ACCOUNT
    }
}
