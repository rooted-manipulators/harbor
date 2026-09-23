package app.harbor.domain

import app.harbor.domain.CuePolicy
import java.time.Instant

/**
 * What the week-one study needs to know, recorded as it happens.
 *
 * The study asks three questions (`docs/03-week-one-study.md`) and the ledger
 * only answers the third. It knows a cue fired and what the user chose; it has
 * nothing to say about whether anybody opened the app, how long they stayed,
 * what they looked at, or how many of their calls Harbor had anything to do
 * with. Those were being collected by asking participants afterwards, which
 * is the least reliable instrument there is.
 *
 * So Harbor keeps a beat for each of them, on the phone, as it happens.
 *
 * ## The one rule
 *
 * **This records shapes, never content.** [Beat.detail] is a category from a
 * fixed vocabulary — a screen name, an enum, a source — and never a string
 * somebody typed. No names, no numbers, no note text, no daily answers, no
 * block labels. If a new call site wants to put a person's words in here, the
 * answer is no; put a category in and leave the words where they are.
 *
 * That is not only privacy hygiene. A study file that carries content has to
 * be handled as personal data by whoever receives it, and the whole design of
 * [StudyExport] is an argument for not having to.
 *
 * ## What this is not
 *
 * Not analytics. Nothing here leaves the device on its own: Harbor holds no
 * `INTERNET` permission (ADR-004) and this adds none. The beats ride out in
 * the same study export the participant hands over at the end of the week,
 * and they are listed in that file's `omitted`/`included` preamble like
 * everything else. Somebody who never exports has told us nothing.
 */
enum class Moment {
    /** Harbor came to the front. */
    APP_OPENED,

    /** Harbor went to the back. Carries seconds spent, so sessions have length. */
    APP_LEFT,

    /** A screen was shown. `detail` is the screen's own name. */
    SCREEN,

    /** A step of the first run was reached. `value` is the step index. */
    ONBOARDING_STEP,

    /** The first run finished. */
    ONBOARDING_DONE,

    /** A cue was put in front of the user. `detail` is the trigger source. */
    CUE_SHOWN,

    /** The user answered a cue. `detail` is the resolution. */
    CUE_RESOLVED,

    /**
     * A plan somebody made by tapping "later" was closed. `detail` is how, as
     * a [Reminders.Closed] — reached them, said they already had, let it go.
     *
     * Question 2 asks what happens to a cue, and "later" was the one answer
     * whose ending the study could never see: the row went in and nothing ever
     * came back to say whether the plan was kept. A proposed-later row with no
     * beat against it is a plan that quietly lapsed, which is itself a finding.
     */
    REMINDER_CLOSED,

    /**
     * A call was placed. `detail` says from where — the cue, home, a person's
     * page, the window on the schedule.
     *
     * The difference between these is the study's second question: whether the
     * trigger is doing the work, or whether people are opening Harbor and
     * calling on their own.
     */
    CALL_STARTED,

    /** They came back from a call. `value` is minutes away. */
    CALL_RETURNED,

    /** A flower was planted. `detail` is its kind, `value` the minutes. */
    FLOWER_PLANTED,

    /** A petal went out. `detail` is line, picture or kept. */
    PETAL_SENT,

    /** The week was edited. `detail` is what was placed or removed. */
    WEEK_EDITED,

    /**
     * Somebody set themselves a reminder to call, from a person's page.
     * `value` is the minute of the day they chose.
     *
     * A third way a call can come to happen, and the study's second question
     * is exactly which of the three does the work. The other two are already
     * counted: [CUE_SHOWN] into [CALL_STARTED] is Harbor's own prompt, and a
     * [CALL_STARTED] with no cue behind it is somebody who simply decided to.
     * This one is somebody deciding *in advance*, which is neither, and
     * without it a reminder that worked would be indistinguishable from a
     * call nobody planned.
     */
    REMINDER_SET,

    /** The mood was set. `detail` is the weather. */
    WEATHER_SET,

    /** The daily word was answered. The word itself is not recorded. */
    ANSWER_KEPT,

    /**
     * A cue the policy refused, and why. `detail` is a [CuePolicy.Reason].
     *
     * The denominator, and without it the numerator means very little.
     *
     * Held cues are deliberately kept out of the ledger -- they are not
     * events in anybody's life, and a ledger padded with near-misses would
     * make its own counts mean something other than what they say. That
     * argument is about the ledger and it still stands. It is not an
     * argument for the study never learning they happened.
     *
     * Because right now a week of no reminders has two completely
     * different explanations and the file cannot tell them apart: the
     * trigger never fired at all, or it fired thirty times and the
     * cooldown swallowed every one. The first is a sensing problem, the
     * second is a settings problem, and they want opposite fixes. With two
     * triggers being compared the gap is worse still -- "the scrolling one
     * produced nothing" reads as a dead feature when it may be a feature
     * that was suppressed, by name, thirty times.
     */
    CUE_HELD,

    /**
     * A cue reached the phone, and how. `detail` is `screen` or `banner`.
     *
     * These are not the same event and the difference is not the
     * participant's. A cue that takes the screen is nearly impossible to
     * miss; a banner over a feed is the easiest thing in the world to flick
     * away unread. Whether a given cue got one or the other depends on a
     * permission, on whether the screen happened to be on, and on the
     * phone's own notification behaviour -- none of which the person chose.
     *
     * The export already carries the permission states, but only as they
     * stood at export time. This is per cue, which is the grain the
     * question is actually asked at: of the cues this person was sent, how
     * many were ever really put in front of them.
     */
    CUE_DELIVERED,

    /**
     * A threshold was moved. `detail` names which, `value` is the new one.
     *
     * The third study question is how far people drift from the suggested
     * calibration, and until now the file answered it only as a final
     * position: these were the numbers at the end of the week. That cannot
     * tell a participant who moved a dial on day one and left it from one
     * who fought it all week, and those are different findings about the
     * same end state.
     */
    THRESHOLD_MOVED,
}

/**
 * One thing that happened, and when.
 *
 * [detail] is a category, never anybody's words. See [Moment].
 */
data class Beat(
    val at: Instant,
    val moment: Moment,
    val detail: String? = null,
    val value: Int? = null,
)

/** Reading a week of beats back into the numbers the study actually asks for. */
object Telemetry {

    /**
     * How many beats are kept.
     *
     * A week of ordinary use is a few hundred. The cap exists so a participant
     * who leaves the app installed for a month does not end up with a
     * SharedPreferences entry measured in megabytes; when it is hit the oldest
     * go first, because the end of the week is the part being studied.
     */
    const val KEEP = 4000

    data class Summary(
        val opens: Int,
        val days: Int,
        val minutesInApp: Int,
        val cuesShown: Int,
        val callsFromCue: Int,
        val callsOnTheirOwn: Int,
        val flowers: Int,
        val petals: Int,
    )

    fun summarise(beats: List<Beat>): Summary {
        val calls = beats.filter { it.moment == Moment.CALL_STARTED }
        return Summary(
            opens = beats.count { it.moment == Moment.APP_OPENED },
            days = beats.map { it.at.epochSecond / 86_400 }.toSet().size,
            minutesInApp = beats
                .filter { it.moment == Moment.APP_LEFT }
                .sumOf { it.value ?: 0 } / 60,
            cuesShown = beats.count { it.moment == Moment.CUE_SHOWN },
            // A call the cue asked for, against one the user went and made.
            // The gap between these two numbers is the product's whole claim.
            callsFromCue = calls.count { it.detail == TriggerSource.WALKING_STOP.name },
            callsOnTheirOwn = calls.count { it.detail != TriggerSource.WALKING_STOP.name },
            flowers = beats.count { it.moment == Moment.FLOWER_PLANTED },
            petals = beats.count { it.moment == Moment.PETAL_SENT },
        )
    }
}
