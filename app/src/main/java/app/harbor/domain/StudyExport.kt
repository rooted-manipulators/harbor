package app.harbor.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The week's data, in a file the participant hands over.
 *
 * Harbor has no network permission and uploads nothing (ADR-004). At the end
 * of the study the data is on the phone and nowhere else, so there has to be a
 * way to get it off deliberately — this is it. The participant saves a file
 * and gives it to us. Nothing happens in the background, and nothing leaves
 * without somebody choosing to send it.
 *
 * ## What is left out, and why
 *
 * This is a *study* export, not a backup. The study asks three questions
 * (`docs/03-week-one-study.md`) and none of them need to know what anybody
 * said or who they said it to. So the file carries the shape of what happened
 * and not its content:
 *
 *  - **No names, numbers, photos or ringtones.** A contact appears as an
 *    opaque id with its kind and colour — enough to tell "the same person
 *    again" from "somebody else", which is all the analysis needs.
 *  - **No words.** Not the text of a line, not a daily answer, not the label
 *    on a busy block, not the participant's own name. A block called
 *    "Therapy" is exactly the kind of thing that must not be in a file that
 *    leaves a phone.
 *  - **No raw movement.** It was never stored in the first place; only that a
 *    cue fired, and from what.
 *
 * The file says so itself, in an `omitted` field, so the person handing it
 * over and the person receiving it can both see what was withheld rather than
 * take it on trust.
 *
 * ## Why this writes its own JSON
 *
 * `org.json` is an Android stub on the JVM, which would make every assertion
 * about what is and is not in this file untestable. The redaction policy is
 * the part most worth testing, so the encoder is a few lines of pure Kotlin
 * and the policy is checked exactly.
 */
object StudyExport {

    /** Bump when the shape changes, so an old file is still readable. */
    // 3 adds "arm". A reader that does not know the field sees a file it can
    // still parse; a reader that needs it can refuse anything below 3, which
    // is the point of having the number at all.
    const val FORMAT = 3

    /** Everything the export is built from. */
    data class Bundle(
        val participant: UUID,
        val exportedAt: Instant,
        val appVersion: String,
        /**
         * Which metaphor this participant was shown.
         *
         * At the top of the file and not on every row, unlike
         * `threshold_snapshot`. That one is snapshotted because thresholds can
         * be moved and a later move must not rewrite what an earlier cue was
         * decided under. An arm cannot change — the store refuses to reassign
         * it — so recording it once is not a shortcut, it is the truth stated
         * in the only place it can be stated.
         */
        val arm: StudyArm,
        /**
         * The code the arm was derived from, blank if somebody went past the
         * screen without one.
         *
         * The arm alone cannot tell an assigned control participant from
         * somebody who skipped: both are [StudyArm.GARDEN]. This is how the
         * two are told apart afterwards, and how a row is matched back to
         * whatever list the study keeps on paper.
         */
        val studyCode: String?,
        val settings: UserSettings,
        val contacts: List<Contact>,
        val blocks: List<WeekBlock>,
        val cues: List<Cue>,
        val entries: List<LedgerEntry>,
        /**
         * When the system last delivered a transition.
         *
         * Without this, a week with no cues cannot be read: it looks the same
         * whether the person never walked or the app was asleep the whole
         * time. It is a timestamp, not a movement — nothing about where they
         * were or what they were doing.
         */
        val lastTransitionAt: Instant?,

        /**
         * What the participant did, as categories and timestamps.
         *
         * The ledger says what became of a cue. This says whether anybody
         * opened the app, how long they stayed, what they looked at, and how
         * many of their calls Harbor had anything to do with — the questions
         * the study was otherwise reduced to asking people afterwards.
         *
         * Shapes only, never content. See [Moment].
         */
        val beats: List<Beat>,
    )

    /** What to show someone before they hand the file over. */
    data class Summary(
        val cues: Int,
        val calls: Int,
        val messages: Int,
        val dismissed: Int,
        val days: Int,
    )

    /** Stated in the file, and on the screen that offers it. */
    val OMITTED = listOf(
        "names, phone numbers, photos and ringtones",
        "the words of any line you left",
        "your answers to the daily question",
        "the words of anything at all: what is recorded is which kind of thing happened, and when",
        "what you called any block on your week",
        "your own name",
        "anything about where you were or how you moved",
    )

    fun summarise(bundle: Bundle): Summary = Summary(
        cues = bundle.cues.size,
        calls = bundle.entries.count { it.resolution == Resolution.CALLED },
        messages = bundle.entries.count { it.resolution == Resolution.MESSAGE },
        dismissed = bundle.entries.count { it.resolution == Resolution.DISMISSED },
        days = bundle.entries.map { it.entryDate }.toSet().size,
    )

    /**
     * A filename that sorts and identifies without naming anybody.
     *
     * The short participant id is what lets a folder of these be told apart
     * when twelve people send one in the same week.
     */
    fun filename(bundle: Bundle): String {
        val day = DateTimeFormatter.ISO_LOCAL_DATE
            .format(bundle.exportedAt.atZone(ZoneId.systemDefault()).toLocalDate())
        return "harbor-$day-${bundle.participant.toString().take(8)}.json"
    }

    fun json(bundle: Bundle): String = obj(
        "format" to num(FORMAT),
        "app_version" to str(bundle.appVersion),
        "participant" to str(bundle.participant.toString()),
        "arm" to str(bundle.arm.wire),
        "study_code" to str(bundle.studyCode.orEmpty()),
        "exported_at" to str(bundle.exportedAt.toString()),
        "last_transition_at" to str(bundle.lastTransitionAt?.toString()),

        "settings" to obj(
            "cues_enabled" to bool(bundle.settings.cuesEnabled),
            "sound" to str(bundle.settings.sound.wire),
            "weather" to str(bundle.settings.weather.wire),
            // Whether the weather above is theirs or ours. Without it the
            // column cannot answer the question worth asking of it -- do
            // people accept the guess, or correct it.
            "weather_set_by_hand" to bool(
                bundle.settings.weatherSetOn == bundle.exportedAt
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            ),
            "thresholds" to thresholds(bundle.settings.thresholds),
        ),

        // Id, kind and colour only.
        "contacts" to arr(bundle.contacts) {
            obj(
                "id" to str(it.id.toString()),
                "kind" to str(it.kind.wire),
                "tone" to str(it.tone.wire),
            )
        },

        // Times and which kind, never labels.
        //
        // The kind is worth having and costs nothing: a cue that landed in a
        // stretch the participant had marked as a good time is the closest
        // thing this study gets to ground truth on question 1, and it cannot
        // be reconstructed from the times alone.
        "week_blocks" to arr(bundle.blocks) {
            obj(
                "day" to str(it.day.name),
                "start" to str(it.start.toString()),
                "end" to str(it.end.toString()),
                "kind" to str(it.kind.name.lowercase()),
            )
        },

        // Categories and timestamps. No words, ever - a beat's detail is an
        // enum name or a screen name and nothing else.
        "beats" to arr(bundle.beats) {
            obj(
                "at" to str(it.at.toString()),
                "moment" to str(it.moment.name.lowercase()),
                "detail" to str(it.detail),
                "value" to num(it.value),
            )
        },

        "cues" to arr(bundle.cues) {
            obj(
                "id" to str(it.id.toString()),
                "fired_date" to str(it.firedDate.toString()),
                "trigger_source" to str(it.triggerSource.wire),
                "fired_at" to str(it.firedAt.toString()),
            )
        },

        "entries" to arr(bundle.entries) {
            obj(
                "id" to str(it.id.toString()),
                "entry_date" to str(it.entryDate.toString()),
                "cue_id" to str(it.cueId?.toString()),
                "contact_id" to str(it.contactId?.toString()),
                "trigger_source" to str(it.triggerSource.wire),
                "threshold_snapshot" to thresholds(it.thresholdSnapshot),
                "resolution" to str(it.resolution.wire),
                "proposed_time" to str(it.proposedTime?.toString()),
                "reminder_done" to bool(it.reminderDone),
                "feedback_pulse" to str(it.feedbackPulse?.wire),
                "call_minutes" to num(it.callMinutes),
                "feeling" to str(it.feeling?.wire),
                "flower" to str(it.flower?.wire),
                "topic" to str(it.topic),
                // `note` is deliberately absent. See the class comment.
                "occurred_at" to str(it.occurredAt.toString()),
            )
        },

        "omitted" to arr(OMITTED) { str(it) },
    )

    // --- a very small JSON writer -----------------------------------------
    //
    // Values arrive already encoded, so an object is a join and an array is a
    // join. No state, nothing to get out of step.

    private fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(",", "{", "}") { (k, v) -> quote(k) + ":" + v }

    private fun <T> arr(items: Iterable<T>, encode: (T) -> String): String =
        items.joinToString(",", "[", "]", transform = encode)

    private fun str(value: String?): String = if (value == null) "null" else quote(value)

    private fun num(value: Int?): String = value?.toString() ?: "null"

    private fun bool(value: Boolean): String = if (value) "true" else "false"

    private fun thresholds(t: Thresholds): String = obj(
        "walking_minutes" to num(t.walkingMinutes),
        "session_minutes" to num(t.sessionMinutes),
        "daily_cap" to num(t.dailyCap),
        "cooldown_minutes" to num(t.cooldownMinutes),
    )

    private fun quote(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (c in value) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u").append("%04x".format(c.code))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }

    /** Matches the wire names the Postgres enums use. */
    private val Enum<*>.wire: String get() = name.lowercase()
}
