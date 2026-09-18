package app.harbor.data

import app.harbor.domain.Beat
import app.harbor.domain.BlockKind
import app.harbor.domain.Contact
import app.harbor.domain.ContactKind
import app.harbor.domain.Cue
import app.harbor.domain.CueSound
import app.harbor.domain.FeedbackPulse
import app.harbor.domain.Feeling
import app.harbor.domain.FlowerKind
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Moment
import app.harbor.domain.Resolution
import app.harbor.domain.Thresholds
import app.harbor.domain.Tone
import app.harbor.domain.TriggerSource
import app.harbor.domain.UserSettings
import app.harbor.domain.Weather
import app.harbor.domain.WeekBlock
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Hand-rolled JSON mapping for the persisted types.
 *
 * Deliberately not kotlinx-serialization: that needs a compiler plugin whose
 * version has to track Kotlin's exactly, and the same constraint has bitten
 * this team before on the sibling project. `org.json` ships with Android and
 * costs nothing.
 *
 * The wire names match the Postgres column names in
 * `backend/supabase/migrations/` so the sync layer does not need a second
 * mapping. Keep them in step.
 */
internal object LedgerJson {

    // --- settings ---------------------------------------------------------

    fun thresholds(t: Thresholds): JSONObject = JSONObject()
        .put("walking_minutes", t.walkingMinutes)
        .put("session_minutes", t.sessionMinutes)
        .put("daily_cap", t.dailyCap)
        .put("cooldown_minutes", t.cooldownMinutes)

    fun thresholds(o: JSONObject): Thresholds = Thresholds(
        walkingMinutes = o.getInt("walking_minutes"),
        sessionMinutes = o.getInt("session_minutes"),
        dailyCap = o.getInt("daily_cap"),
        cooldownMinutes = o.getInt("cooldown_minutes"),
    )

    fun settings(s: UserSettings): JSONObject = thresholds(s.thresholds)
        .put("cues_enabled", s.cuesEnabled)
        .put("sound", s.sound.wire)
        .put("weather", s.weather.wire)
        .put("weather_set_on", s.weatherSetOn?.toString())
        .put("name", s.name)
        .put("reduced_motion", s.reducedMotion)

    fun settings(o: JSONObject): UserSettings = UserSettings(
        thresholds = thresholds(o),
        cuesEnabled = o.optBoolean("cues_enabled", false),
        sound = o.optStringOrNull("sound")
            ?.let { CueSound.entries.fromWire(it) } ?: CueSound.CHIME,
        weather = o.optStringOrNull("weather")
            ?.let { Weather.entries.fromWire(it) } ?: Weather.CLEAR,
        // Absent on anything written before the guess existed, which reads as
        // "never set by hand" -- so an existing install gets a guess tomorrow
        // rather than keeping whatever it happened to be left on.
        weatherSetOn = o.optStringOrNull("weather_set_on")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        name = o.optStringOrNull("name").orEmpty(),
        reducedMotion = o.optBoolean("reduced_motion", false),
    )

    // --- contact ----------------------------------------------------------

    fun contact(c: Contact): JSONObject = JSONObject()
        .put("id", c.id.toString())
        .put("label", c.label)
        .put("phone_e164", c.phoneE164)
        .put("kind", c.kind.wire)
        .put("tone", c.tone.wire)
        .put("cue_sound_ref", c.cueSoundRef)
        .put("photo_ref", c.photoRef)

    fun contact(o: JSONObject): Contact = Contact(
        id = UUID.fromString(o.getString("id")),
        label = o.getString("label"),
        phoneE164 = o.optStringOrNull("phone_e164"),
        kind = o.optStringOrNull("kind")
            ?.let { ContactKind.entries.fromWire(it) } ?: ContactKind.PERSON,
        tone = o.optStringOrNull("tone")?.let { Tone.entries.fromWire(it) } ?: Tone.GREEN,
        cueSoundRef = o.optStringOrNull("cue_sound_ref"),
        photoRef = o.optStringOrNull("photo_ref"),
    )

    fun contacts(array: JSONArray): List<Contact> =
        (0 until array.length()).map { contact(array.getJSONObject(it)) }

    fun contacts(list: List<Contact>): JSONArray =
        JSONArray().apply { list.forEach { put(contact(it)) } }

    // --- the week ---------------------------------------------------------

    fun block(w: WeekBlock): JSONObject = JSONObject()
        .put("day", w.day.name)
        .put("start", w.start.toString())
        .put("end", w.end.toString())
        .put("kind", w.kind.name.lowercase())
        .put("label", w.label)

    /**
     * Missing `kind` means busy.
     *
     * Not a nicety: every phone that already has Harbor on it wrote its
     * timetable before the field existed, and a default of busy is the reading
     * under which those rows still mean what the person meant when they
     * entered them.
     */
    fun block(o: JSONObject): WeekBlock = WeekBlock(
        day = DayOfWeek.valueOf(o.getString("day")),
        start = LocalTime.parse(o.getString("start")),
        end = LocalTime.parse(o.getString("end")),
        kind = o.optStringOrNull("kind")
            ?.let { BlockKind.entries.fromWire(it) } ?: BlockKind.BUSY,
        label = o.optStringOrNull("label"),
    )

    fun blocks(array: JSONArray): List<WeekBlock> =
        (0 until array.length()).map { block(array.getJSONObject(it)) }

    fun blocks(list: List<WeekBlock>): JSONArray =
        JSONArray().apply { list.forEach { put(block(it)) } }

    // --- study beats ------------------------------------------------------

    fun beat(b: Beat): JSONObject = JSONObject()
        .put("at", b.at.toString())
        .put("moment", b.moment.name.lowercase())
        .put("detail", b.detail)
        .put("value", b.value)

    fun beat(o: JSONObject): Beat = Beat(
        at = Instant.parse(o.getString("at")),
        moment = Moment.entries.fromWire(o.getString("moment")),
        detail = o.optStringOrNull("detail"),
        value = if (o.isNull("value")) null else o.optInt("value"),
    )

    fun beats(array: JSONArray): List<Beat> =
        (0 until array.length()).map { beat(array.getJSONObject(it)) }

    fun beats(list: List<Beat>): JSONArray =
        JSONArray().apply { list.forEach { put(beat(it)) } }

    // --- cue --------------------------------------------------------------

    fun cue(c: Cue): JSONObject = JSONObject()
        .put("id", c.id.toString())
        .put("fired_date", c.firedDate.toString())
        .put("trigger_source", c.triggerSource.wire)
        .put("fired_at", c.firedAt.toString())

    fun cue(o: JSONObject): Cue = Cue(
        id = UUID.fromString(o.getString("id")),
        firedDate = LocalDate.parse(o.getString("fired_date")),
        triggerSource = TriggerSource.entries.fromWire(o.getString("trigger_source")),
        firedAt = Instant.parse(o.getString("fired_at")),
    )

    fun cues(array: JSONArray): List<Cue> =
        (0 until array.length()).map { cue(array.getJSONObject(it)) }

    fun cues(list: List<Cue>): JSONArray =
        JSONArray().apply { list.forEach { put(cue(it)) } }

    // --- ledger -----------------------------------------------------------

    fun entry(e: LedgerEntry): JSONObject = JSONObject()
        .put("id", e.id.toString())
        .put("entry_date", e.entryDate.toString())
        .put("cue_id", e.cueId?.toString())
        .put("contact_id", e.contactId?.toString())
        .put("trigger_source", e.triggerSource.wire)
        .put("threshold_snapshot", thresholds(e.thresholdSnapshot))
        .put("resolution", e.resolution.wire)
        .put("proposed_time", e.proposedTime?.toString())
        .put("reminder_done", e.reminderDone)
        .put("feedback_pulse", e.feedbackPulse?.wire)
        .put("call_minutes", e.callMinutes)
        .put("feeling", e.feeling?.wire)
        .put("flower", e.flower?.wire)
        .put("topic", e.topic)
        .put("note", e.note)
        .put("occurred_at", e.occurredAt.toString())

    fun entry(o: JSONObject): LedgerEntry = LedgerEntry(
        id = UUID.fromString(o.getString("id")),
        entryDate = LocalDate.parse(o.getString("entry_date")),
        cueId = o.optStringOrNull("cue_id")?.let(UUID::fromString),
        contactId = o.optStringOrNull("contact_id")?.let(UUID::fromString),
        triggerSource = TriggerSource.entries.fromWire(o.getString("trigger_source")),
        thresholdSnapshot = thresholds(o.getJSONObject("threshold_snapshot")),
        resolution = Resolution.entries.fromWire(o.getString("resolution")),
        proposedTime = o.optStringOrNull("proposed_time")?.let(Instant::parse),
        reminderDone = o.optBoolean("reminder_done", false),
        feedbackPulse = o.optStringOrNull("feedback_pulse")
            ?.let { FeedbackPulse.entries.fromWire(it) },
        callMinutes = if (o.isNull("call_minutes")) null else o.optInt("call_minutes"),
        feeling = o.optStringOrNull("feeling")?.let { Feeling.entries.fromWire(it) },
        // Tolerant on purpose, and the only field that is.
        //
        // Flowers were renamed from species to feelings, so a ledger written
        // before that holds names this enum has never heard of. fromWire
        // throws on an unknown value, which is right for every other field --
        // a resolution or a trigger it cannot read means the row is not what
        // it claims to be -- but wrong here: FlowerKind.stored maps the old
        // eighteen across, and returns null for anything from neither era, so
        // at worst one entry loses its bloom instead of the ledger refusing
        // to load at all.
        flower = o.optStringOrNull("flower")?.let { FlowerKind.stored(it) },
        topic = o.optStringOrNull("topic"),
        note = o.optStringOrNull("note"),
        occurredAt = Instant.parse(o.getString("occurred_at")),
    )

    fun entries(array: JSONArray): List<LedgerEntry> =
        (0 until array.length()).map { entry(array.getJSONObject(it)) }

    fun entries(list: List<LedgerEntry>): JSONArray =
        JSONArray().apply { list.forEach { put(entry(it)) } }
}

/**
 * Postgres enum labels are lower_snake_case; Kotlin's are UPPER_SNAKE. One
 * lowercase() keeps the two in step without a hand-written table to drift.
 */
private val Enum<*>.wire: String get() = name.lowercase()

private fun <E : Enum<E>> List<E>.fromWire(value: String): E =
    firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: error("unknown ${first()::class.simpleName} '$value' in stored data")

/**
 * `JSONObject.optString` returns the string "null" for a JSON null, which has
 * caused more bugs than it has ever prevented.
 */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
