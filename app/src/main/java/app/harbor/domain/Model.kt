package app.harbor.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID

/**
 * The domain model.
 *
 * These types mirror the Postgres schema in `backend/supabase/migrations/`
 * deliberately, and they follow the UI prototype (harvest-pulse) wherever the
 * two disagreed — see docs/02-ui-reconciliation.md for the walkthrough. If you
 * change a name or a variant here, change it in the schema in the same PR: the
 * sync layer maps between them by name.
 *
 * Nothing in this file touches Android. That is on purpose.
 */

/** What woke the pipeline up. Stage 1. */
enum class TriggerSource {
    /** A walking bout ended. The only sensed source in v0.1. */
    WALKING_STOP,

    /** A watched app session ended. Not sensed yet — see ADR-005. */
    SESSION_END,

    /** Left from a note or snapshot the user sent. */
    NOTE,

    /** Left from the daily question. */
    GAME,

    /** The user opened Harbor and asked for a prompt themselves. */
    MANUAL,
}

/**
 * What the user chose at stage 6. No option is a default, and none is a
 * failure — dismissing is a legitimate answer, not a missed one.
 */
enum class Resolution {
    CALLED,
    REACTED,

    /** Sent a note. Distinct from a reaction, and counts toward the Jar. */
    MESSAGE,

    /** Played the day's family game. Counts for the Jar, but see below. */
    PLAYED,

    PROPOSED_LATER,
    DISMISSED,

    /**
     * They went to call and no conversation happened.
     *
     * Changed their mind at the dialer, or nobody picked up. The row is
     * written as [CALLED] the moment the dialer opens — before anything is
     * known — because a call that happened must be recorded even if the user
     * never comes back to say how it went. That trade means the only way
     * `called` stays honest is if there is a way to say no afterwards, and
     * this is it.
     *
     * Kept separate from [DISMISSED] on purpose: dismissing is declining the
     * cue, this is accepting it and coming away with nothing. For the study
     * those are different answers to "what happened to a cue", and collapsing
     * them would hide the more interesting one.
     */
    NOT_REACHED;

    /**
     * Whether this counts as having reached the other person today.
     *
     * Includes reactions and notes, not just calls. That is a product
     * judgement carried over from the prototype: a heart sent on purpose is
     * connection, and treating it as one is what keeps the app from nagging
     * someone who already did the thing.
     *
     * [PLAYED] is deliberately excluded, matching the prototype: playing the
     * daily game is a nice thing to have done, but nobody on the other end
     * heard from you.
     */
    val isConnection: Boolean
        get() = this == CALLED || this == REACTED || this == MESSAGE
}

/** The one-tap pulse at stage 8. Null when the user skipped it. */
enum class FeedbackPulse { GOOD_TIME, BAD_TIME }

/**
 * How a call left the user feeling, asked once afterwards.
 *
 * This is the reward, and it is also the input to it: each feeling grows a
 * particular flower in the garden, so answering honestly is what makes the
 * garden a record of the calls rather than a scoreboard of them.
 */
enum class Feeling(val flower: FlowerKind) {
    LIGHT(FlowerKind.GLAD_WE_TALKED),
    WARM(FlowerKind.FELT_LOVED),
    STEADY(FlowerKind.STEADIER_NOW),
    TENDER(FlowerKind.GLAD_SHE_PICKED_UP),
}

/**
 * What a call becomes.
 *
 * Twenty of them, named for the specific feeling a call leaves rather than for
 * a mood in general or a species before that. That is the change the flower
 * sheet makes, and it is not cosmetic: the screen after a call asks how it
 * felt, and the answer used to be translated into a botanical name nobody had
 * chosen, then into a mood untethered from the call that produced it. Now the
 * thing you pick *is* the answer, and the garden is a record of a year of
 * calls rather than a catalogue of plants or a generic mood board.
 *
 * The garden is the reward surface — there is no score, no streak, and nothing
 * that can be lost; a flower that grew stays grown. Note that the darker ones
 * are here on purpose. A week where somebody plants "wished it was longer"
 * four times is a week the study needs to be able to see, and an app that only
 * lets you say the call went great is an app people quietly stop telling the
 * truth to.
 */
enum class FlowerKind {
    GLAD_WE_TALKED, LIGHTER_NOW, FELT_LOVED, SHE_REMEMBERED, EASY_SILENCE,
    STEADIER_NOW, WORTH_SLOWING_DOWN, SAID_WHAT_I_MEANT, WANT_TO_TRY_SOMETHING, ASKED_MORE_THAN_USUAL,
    LOOKING_FORWARD, STILL_THINKING_ABOUT_IT, HARD_TO_SHAKE_OFF, TIME_TO_ACTUALLY_DO_IT, NOTHING_LEFT_UNSAID,
    WONDERING_IF_THAT_LANDED, SAID_THE_HARD_THING, GLAD_SHE_PICKED_UP, WISHED_IT_WAS_LONGER, DREADED_THIS_ONE,
    ;

    companion object {
        /**
         * What the eighteen species, and then the twenty moods, became.
         *
         * Flowers are stored by name, in the ledger on the phone and as a
         * Postgres enum in the backend, so renaming them is a data change
         * rather than a rename. Anything already planted was planted under an
         * earlier name, and this is the only thing standing between those rows
         * and a reader that throws on the first one it does not recognise.
         *
         * Two generations deep now. The species (left column, added when the
         * sheet had eighteen plants) point at the mood each became a day
         * later; those moods (right column of the second block) point at the
         * call-specific feeling each became today. `stored` only ever does one
         * hop, so a species has to point at the *current* name directly, not
         * at the mood that no longer exists as a constant — that is why the
         * species entries below were repointed rather than left alone.
         *
         * Keep this forever. It costs nothing and it is the difference between
         * a participant's garden surviving an update and not.
         */
        private val LEGACY = mapOf(
            // The eighteen species, repointed straight at today's name.
            "DAISY" to GLAD_WE_TALKED,
            "MARIGOLD" to LIGHTER_NOW,
            "COSMOS" to WORTH_SLOWING_DOWN,
            "POPPY" to SAID_THE_HARD_THING,
            "TULIP" to NOTHING_LEFT_UNSAID,
            "BLUEBELL" to STILL_THINKING_ABOUT_IT,
            "ASTER" to STEADIER_NOW,
            "SUNFLOWER" to FELT_LOVED,
            "LAVENDER" to EASY_SILENCE,
            "ZINNIA" to WANT_TO_TRY_SOMETHING,
            "CAMELLIA" to SHE_REMEMBERED,
            "PERIWINKLE" to ASKED_MORE_THAN_USUAL,
            "BUTTERCUP" to SAID_WHAT_I_MEANT,
            "ANEMONE" to LOOKING_FORWARD,
            "SNOWDROP" to GLAD_SHE_PICKED_UP,
            "DAHLIA" to TIME_TO_ACTUALLY_DO_IT,
            "IRIS" to HARD_TO_SHAKE_OFF,
            "HYDRANGEA" to DREADED_THIS_ONE,
            // Yesterday's twenty moods, one hop to today's feeling.
            "HAPPY" to GLAD_WE_TALKED,
            "UPBEAT" to LIGHTER_NOW,
            "LOVED" to FELT_LOVED,
            "VALUED" to SHE_REMEMBERED,
            "PEACEFUL" to EASY_SILENCE,
            "GROUNDED" to STEADIER_NOW,
            "CALM" to WORTH_SLOWING_DOWN,
            "CONFIDENT" to SAID_WHAT_I_MEANT,
            "INSPIRED" to WANT_TO_TRY_SOMETHING,
            "CURIOUS" to ASKED_MORE_THAN_USUAL,
            "HOPEFUL" to LOOKING_FORWARD,
            "REFLECTIVE" to STILL_THINKING_ABOUT_IT,
            "TENSE" to HARD_TO_SHAKE_OFF,
            "MOTIVATED" to TIME_TO_ACTUALLY_DO_IT,
            "CONTENT" to NOTHING_LEFT_UNSAID,
            "INSECURE" to WONDERING_IF_THAT_LANDED,
            "BRAVE" to SAID_THE_HARD_THING,
            "GRATEFUL" to GLAD_SHE_PICKED_UP,
            "LONELY" to WISHED_IT_WAS_LONGER,
            "ANXIOUS" to DREADED_THIS_ONE,
        )

        /**
         * Read a stored flower, whatever era it was written in.
         *
         * Returns null rather than throwing for a name from neither era. A
         * flower nobody can identify should cost that one entry its bloom, not
         * the whole ledger — and the ledger is the study.
         */
        fun stored(value: String): FlowerKind? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: LEGACY[value.uppercase()]
    }
}


/**
 * How life feels at the moment, on a scale the user sets themselves.
 *
 * Replaces the earlier "season". Deliberately weather rather than a rating:
 * weather is something that happens to you and passes, which is a kinder
 * frame for a hard week than a number would be.
 */
enum class Weather { CLEAR, BRIGHT, CLOUDY, RAIN, STORM }

/** A contact that cannot be dialled reads differently in the UI. */
enum class ContactKind { PERSON, GROUP }

/** The colour a person is drawn in, across the garden and their avatar. */
enum class Tone { GREEN, GOLD, ORANGE, SKY }

/** The global default cue sound. A contact may override it. */
enum class CueSound { CHIME, SOFT, SILENT }

/**
 * The four numbers that decide whether a cue fires. Snapshotted onto every
 * ledger entry, so a later recalibration cannot rewrite the past.
 */
data class Thresholds(
    val walkingMinutes: Int,
    val sessionMinutes: Int,
    /** Hard ceiling on cues per day, across every trigger. */
    val dailyCap: Int,

    /**
     * The most any one trigger may produce in a day.
     *
     * Exists because two triggers running at once do not share a cap fairly.
     * A walking stop happens once or twice; a phone-in-hand session ends
     * dozens of times. Against a single ceiling of two, the frequent trigger
     * takes both slots most mornings and the rare one is never seen — so a
     * study comparing them would end the week with a hundred of one and four
     * of the other, and no comparison at all.
     *
     * Null means "the same as [dailyCap]", which makes it invisible while
     * there is only one sensed trigger: one source cannot out-compete itself.
     * It starts mattering the day a second one is switched on, and then it
     * should be set with the total — four a day, two from each, rather than
     * two that one trigger eats.
     *
     * Null rather than defaulting to [dailyCap] directly, because a default
     * that reads another field is a trap on `copy`: `copy(dailyCap = 1)` would
     * keep the old source cap and produce a Thresholds that fails its own
     * requirement. Deriving it on read cannot drift.
     *
     * Not a study knob dressed as a setting: a person with both triggers on
     * wants the same fairness for the same reason, study or no study.
     */
    val sourceCap: Int? = null,
    /** Minimum gap between two cues. Zero turns the gap off entirely. */
    val cooldownMinutes: Int,
) {
    init {
        // Ranges match the prototype's settings form and the CHECK
        // constraints in 0001_init.sql, so a value one layer accepts can
        // never be rejected by another.
        require(walkingMinutes in 1..120) { "walkingMinutes out of range: $walkingMinutes" }
        require(sessionMinutes in 1..180) { "sessionMinutes out of range: $sessionMinutes" }
        require(dailyCap in 1..10) { "dailyCap out of range: $dailyCap" }
        // Zero is a real setting, not a missing one: no enforced gap, with
        // the daily cap left as the only limit. 0001_init.sql's CHECK was
        // `between 1 and 1440` and would have rejected it, so 0012 widens it
        // -- this comment's promise that one layer never rejects what another
        // accepts is only kept if both move together.
        require(cooldownMinutes in 0..1440) { "cooldownMinutes out of range: $cooldownMinutes" }
        require(sourceCap == null || sourceCap in 1..dailyCap) {
            "sourceCap must be between 1 and dailyCap: $sourceCap of $dailyCap"
        }
    }

    /** The per-trigger ceiling actually in force. See [sourceCap]. */
    val perSourceCap: Int get() = sourceCap ?: dailyCap

    companion object {
        /**
         * The suggested starting point, matching the column defaults in
         * `0001_init.sql`. Ship it as a suggestion and let the user move it —
         * never lock it as a product default. Handoff, section 7.
         *
         * Study question 3 is how far people move away from this.
         */
        val SUGGESTED = Thresholds(
            // Three, not ten.
            //
            // Ten minutes of *continuous* walking followed by a stop is a
            // deliberate walk, and it is the right shape for the real study.
            // It is also a threshold almost nobody crosses while somebody is
            // watching them use the app, which meant every test session only
            // ever saw the preview reminder and never the real one -- the one
            // piece of behaviour most worth watching a stranger meet.
            //
            // Three is still a walk rather than a step to the kettle, and it
            // is reachable inside a session. The stepper on the same screen
            // moves it, and the study build should raise it again.
            walkingMinutes = 3,
            sessionMinutes = 20,
            // Four a day, two from each trigger, rather than two that one
            // trigger eats. See sourceCap above: this is the day it starts
            // mattering, because it is the day a second sensed trigger
            // exists. Somebody with only the walk switched on is unchanged in
            // practice -- its own share is two, exactly what the cap was.
            dailyCap = 4,
            sourceCap = 2,
            // No enforced gap. It was two hours, which is a long time to be
            // unable to see the feature work and, as a suggestion nobody could
            // reach, was closer to a rule than a suggestion. The daily cap is
            // the limit that remains, and this one is now on the settings
            // screen for anybody who wants the quiet back.
            cooldownMinutes = 0,
        )
    }
}

/** Everything the user can change about how Harbor behaves. */
data class UserSettings(
    /** What Harbor calls the user. Greeted by it on the home screen. */
    val name: String = "",

    val thresholds: Thresholds = Thresholds.SUGGESTED,

    /**
     * The real opt-out. Off until the user turns it on, behind a privacy
     * explainer. Also the answer to the handoff's open "degraded mode"
     * question: if activity permission is refused, cues are off and say so —
     * nothing degrades silently.
     */
    val cuesEnabled: Boolean = false,

    val sound: CueSound = CueSound.CHIME,

    /**
     * Whether a scrolling session may offer a reminder, as well as a walk.
     *
     * Off until somebody turns it on, and turning it on is a separate consent
     * from the one that covers walking: reading which app is in front is a
     * different thing from reading whether the phone is moving, and
     * `PACKAGE_USAGE_STATS` is a different grant. See ADR-005, which named
     * `UsageStatsManager` for this and put it after the walk.
     *
     * Kept beside [cuesEnabled] rather than folded into it, because the study
     * needs to tell three states apart: never offered it, offered and
     * declined, offered and later switched off. The first is a participant
     * the trigger never reached; the other two are findings.
     */
    val scrollCues: Boolean = false,

    /**
     * How life feels at the moment.
     *
     * Weather rather than a rating, because weather happens to you and
     * passes — a kinder frame for a hard week than a number would be.
     *
     * **This used to say "the user sets it; nothing infers it", and that is no
     * longer true.** Harbor now opens each day on a guess read off the week
     * the person typed in — see [Windows.weatherFor] — because a control that
     * starts blank every morning asks somebody to do the work of noticing
     * before they have properly opened the app. The guess is written here so
     * that the sky, the bee, the ledger and the export all agree about what
     * was actually on screen.
     *
     * What protects the old principle is [weatherSetOn]: a guess is always
     * marked as a guess, always overridable, and never mistaken afterwards for
     * something the person said.
     */
    val weather: Weather = Weather.CLEAR,

    /**
     * The day the person last moved the slider themselves, or null.
     *
     * The difference between "they told us" and "we guessed", and the reason
     * the guess can be written to [weather] without lying. When this is not
     * today, what is on screen is inferred and is drawn as provisional; the
     * first touch of the slider sets it to today and the value becomes theirs.
     *
     * A date rather than a flag, because the guess should come back tomorrow.
     * Yesterday's mood is not today's, and a stale answer left standing is
     * worse than an honest guess.
     */
    val weatherSetOn: LocalDate? = null,

    val reducedMotion: Boolean = false,
)

/**
 * What a block on the week says about that stretch of time.
 *
 * Two kinds, and they do very different work. [BUSY] is a rule: it stops a cue
 * firing. [FREE] is a preference: it says a call would be welcome then, and it
 * gates nothing at all.
 *
 * That asymmetry is deliberate and load-bearing. If marking time free also
 * meant marking everything else busy, someone who planted two flowers would
 * have quietly switched the whole app off and would have no way of knowing.
 * The default stays "not busy", exactly as it was before anyone could say
 * "free" at all.
 */
enum class BlockKind { BUSY, FREE }

/**
 * Where a [WeekBlock] came from.
 *
 * Rendering only -- `Windows.busyAt` and everything else that decides whether
 * a block suppresses a cue reads [WeekBlock.kind] and never this. A block
 * planted by a finger, one filed by the WhatsApp bot (ADR-014), and one
 * pulled from a calendar (ADR-015) suppress a reminder identically; the only
 * thing this changes is which colour the schedule draws it in.
 */
enum class BlockOrigin { PERSON, WHATSAPP, CALENDAR }

/**
 * A recurring stretch of the week the user has said something about.
 *
 * Weekly rather than dated, because that is the shape a timetable actually
 * has. A one-off engagement is not worth modelling: the cue is capped and
 * dismissible, and being asked once during an unusual afternoon costs almost
 * nothing.
 *
 * Deliberately independent of where the times came from. They might be typed
 * in, read from the device calendar, or one day pulled from a campus system —
 * the policy does not care, and keeping it that way is what stops a data
 * source from becoming an architectural commitment.
 *
 * This used to be `BusyWindow`, with only the one meaning. It grew a [kind]
 * when the schedule screen learned to place free time as well as busy time,
 * and the old name stopped being true. Two separate lists would have been the
 * other way to do it, and would have been worse: no moment can be both busy
 * and free, and one list is what makes "placing this clears whatever was under
 * it" a single operation rather than a reconciliation between two.
 */
data class WeekBlock(
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val kind: BlockKind = BlockKind.BUSY,
    /** "Marketing 101", or null. Never leaves the device. */
    val label: String? = null,
    /**
     * Where this block came from. Default [BlockOrigin.PERSON], because that
     * is what a block was before there was anything else it could be.
     *
     * Local only, same treatment as [label] and for the same reason: a week
     * goes out as its shape, never its content, and *how somebody's calendar
     * fills up* is content. [app.harbor.domain.Sharing.SharedBlock] has no
     * field for it, `week_blocks` has no column for it, and nothing here
     * should ever add one without rereading why `label` does not have one
     * either.
     */
    val origin: BlockOrigin = BlockOrigin.PERSON,
) {
    init {
        require(start < end) { "a block must end after it starts" }
    }

    /**
     * Whether [at] falls inside this block. Geometry only: it says nothing
     * about whether this is a block that suppresses anything.
     *
     * Ask `Windows.busyAt` for that. Kept separate on purpose, because a free
     * block covers time too, and a `covers` that quietly meant "is busy" is
     * the one mistake in this change that could turn the app off.
     */
    fun covers(at: ZonedDateTime): Boolean =
        at.dayOfWeek == day && at.toLocalTime() >= start && at.toLocalTime() < end
}

/**
 * Someone worth calling. v0.1 assumes an ordinary cellular number: the parent
 * installs nothing (ADR-002, ADR-007).
 */
data class Contact(
    val id: UUID,
    val label: String,
    /** E.164, e.g. +919876543210. Null for a group, which cannot be dialled. */
    val phoneE164: String?,
    val kind: ContactKind = ContactKind.PERSON,
    val tone: Tone = Tone.GREEN,

    /** Overrides [UserSettings.sound] for this person. Null = use the default. */
    val cueSoundRef: String? = null,

    /**
     * A picked photo shown on the cue surface, as a device-local URI.
     *
     * Their face is half of what makes the cue land as *them* rather than as
     * an app (ADR-009). Picked with the system photo picker rather than read
     * from their contact entry, so it costs no permission.
     */
    val photoRef: String? = null,
) {
    init {
        require(kind == ContactKind.GROUP || phoneE164 != null) {
            "a person needs a number to call"
        }
    }
}

/**
 * A cue that fired, whether or not the user answered it.
 *
 * Separate from [LedgerEntry] because the daily cap counts cues, and because a
 * cue nobody engaged with is exactly the signal the study wants — no
 * resolution-based count can see it.
 */
data class Cue(
    val id: UUID,
    /** The device's local day. */
    val firedDate: LocalDate,
    val triggerSource: TriggerSource,
    val firedAt: Instant,
)

/**
 * Written at stage 9. The device generates [id], so re-uploading after a
 * failed sync is an upsert rather than a duplicate row. See ADR-003.
 */
data class LedgerEntry(
    val id: UUID,
    val entryDate: LocalDate,
    /** The cue this resolved. Null when the user started the moment themselves. */
    val cueId: UUID?,
    /** Who it was with. Null if the contact has since been deleted. */
    val contactId: UUID?,
    val triggerSource: TriggerSource,
    /** The thresholds in force when this cue fired, not the current ones. */
    val thresholdSnapshot: Thresholds,
    val resolution: Resolution,
    /** Set if and only if [resolution] is [Resolution.PROPOSED_LATER]. */
    val proposedTime: Instant?,
    /**
     * Whether a proposed-later reminder has been dealt with. Stored rather
     * than inferred from [proposedTime] having passed, which guesses wrong
     * whenever the user acts early or late.
     */
    val reminderDone: Boolean = false,
    val feedbackPulse: FeedbackPulse?,

    /**
     * How long the call ran, in minutes. Null for anything that was not a
     * call. Feeds the "calls with her usually run ~12 min" line on the cue,
     * which is there so the ask has a known size before anyone commits to it.
     */
    val callMinutes: Int?,

    /** How it left them, asked once afterwards. Null if they skipped it. */
    val feeling: Feeling?,

    /**
     * The flower this call grew. Derived from [feeling] at the time and kept,
     * rather than recomputed — the garden should not rearrange itself because
     * the mapping changed in a later release.
     */
    val flower: FlowerKind?,

    /** The shape the user gave the call before it started. */
    val topic: String?,

    /**
     * The words of a line the user left, when there were any.
     *
     * Harbor used to keep only that a line happened, so the history read as a
     * column of identical "You left a line." rows and told you nothing. It is
     * kept now, deliberately, and the screen says so rather than promising
     * otherwise.
     *
     * It syncs with the rest of the row. The study's export views are
     * aggregate rollups and do not select it, so the research extract stays
     * free of anyone's actual words -- keep it that way.
     */
    val note: String? = null,
    /** When the moment happened on the device — not when it synced. */
    val occurredAt: Instant,
) {
    init {
        require((resolution == Resolution.PROPOSED_LATER) == (proposedTime != null)) {
            "proposedTime must be set for PROPOSED_LATER and null otherwise"
        }
        require(!reminderDone || resolution == Resolution.PROPOSED_LATER) {
            "reminderDone only means anything for PROPOSED_LATER"
        }
    }
}
