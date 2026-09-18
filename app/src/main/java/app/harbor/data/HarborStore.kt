package app.harbor.data

import android.content.Context
import android.content.SharedPreferences
import app.harbor.domain.Beat
import app.harbor.domain.Contact
import app.harbor.domain.Cue
import app.harbor.domain.CuePolicy
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Moment
import app.harbor.domain.Reminders
import app.harbor.domain.Telemetry
import app.harbor.domain.TriggerSource
import app.harbor.domain.StudyArm
import app.harbor.domain.UserSettings
import app.harbor.domain.WeekBlock
import app.harbor.domain.Windows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

/**
 * SharedPreferences-backed [HarborRepository].
 *
 * Volumes here are tiny — a couple of cues a day, capped — so each collection
 * is held as one JSON array and rewritten on change. If that ever stops being
 * true, the fix is Room behind the same interface, not a cleverer version of
 * this.
 *
 * Writes are serialised through [writeLock] because the sensing service and
 * the UI can both reach this, and read-modify-write on a JSON blob is exactly
 * the shape that loses data under concurrency.
 */
class HarborStore(context: Context) : HarborRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val writeLock = Mutex()

    private val _settings = MutableStateFlow(readSettings())
    override val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _contacts = MutableStateFlow(readContacts())
    override val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _weekBlocks = MutableStateFlow(readWeekBlocks())
    override val weekBlocks: StateFlow<List<WeekBlock>> = _weekBlocks.asStateFlow()

    private val _dailyAnswers = MutableStateFlow(readDailyAnswers())
    override val dailyAnswers: StateFlow<Map<LocalDate, String>> = _dailyAnswers.asStateFlow()

    // Android hands every caller in this process the same underlying
    // SharedPreferences object for a given file name, so a write from one
    // HarborStore instance (the widget's own, for instance) reaches every
    // other instance's listener -- this is what that's for. Without it, a
    // long-lived instance such as MainActivity's keeps whatever it read at
    // construction and a change made elsewhere in the same process never
    // reaches its StateFlows until the process restarts.
    //
    // Held in a field rather than passed inline: registerOnSharedPreferenceChangeListener
    // keeps only a weak reference, so an unheld lambda is eligible for
    // collection and can silently stop firing.
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_SETTINGS -> _settings.value = readSettings()
                KEY_CONTACTS -> _contacts.value = readContacts()
                KEY_BUSY -> _weekBlocks.value = readWeekBlocks()
                KEY_ANSWERS -> _dailyAnswers.value = readDailyAnswers()
            }
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    // --- settings ---------------------------------------------------------

    private fun readSettings(): UserSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return UserSettings()
        // Corrupt, or written by an older shape. Defaults are a safe landing
        // spot: losing a calibration is bad, but crashing on boot is worse.
        // Note the default has cues OFF, so a failure here can never
        // over-notify someone.
        return runCatching { LedgerJson.settings(JSONObject(raw)) }
            .getOrDefault(UserSettings())
    }

    override suspend fun setSettings(settings: UserSettings) {
        write { putString(KEY_SETTINGS, LedgerJson.settings(settings).toString()) }
        _settings.value = settings
    }

    // --- contacts ---------------------------------------------------------

    private fun readContacts(): List<Contact> {
        val raw = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        return runCatching { LedgerJson.contacts(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    override suspend fun upsertContact(contact: Contact) {
        val updated = (readContacts().filterNot { it.id == contact.id } + contact)
            .sortedBy { it.label }
        write { putString(KEY_CONTACTS, LedgerJson.contacts(updated).toString()) }
        _contacts.value = updated
    }

    override suspend fun deleteContact(id: UUID) {
        val updated = readContacts().filterNot { it.id == id }
        write { putString(KEY_CONTACTS, LedgerJson.contacts(updated).toString()) }
        _contacts.value = updated
    }

    // --- the week ---------------------------------------------------------

    private fun readWeekBlocks(): List<WeekBlock> {
        val raw = prefs.getString(KEY_BUSY, null) ?: return emptyList()
        return runCatching { LedgerJson.blocks(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    override suspend fun setWeekBlocks(blocks: List<WeekBlock>) {
        val sorted = blocks.sortedWith(compareBy({ it.day }, { it.start }))
        write { putString(KEY_BUSY, LedgerJson.blocks(sorted).toString()) }
        _weekBlocks.value = sorted
    }

    // --- study beats ------------------------------------------------------

    /**
     * Append one beat, oldest dropped once [Telemetry.KEEP] is reached.
     *
     * Read-modify-write under the same lock every other write uses, so two
     * taps in the same frame cannot lose one another. The list is small and
     * the write is off the main thread.
     */
    override suspend fun note(moment: Moment, detail: String?, value: Int?) {
        val beat = Beat(Instant.now(), moment, detail, value)
        writeList(KEY_BEATS) {
            (readBeats() + beat).takeLast(Telemetry.KEEP).let(LedgerJson::beats)
        }
    }

    override suspend fun beats(): List<Beat> = withContext(Dispatchers.IO) { readBeats() }

    private fun readBeats(): List<Beat> {
        val raw = prefs.getString(KEY_BEATS, null) ?: return emptyList()
        return runCatching { LedgerJson.beats(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    // --- the daily question -----------------------------------------------

    private fun readDailyAnswers(): Map<LocalDate, String> {
        val raw = prefs.getString(KEY_ANSWERS, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associate { LocalDate.parse(it) to o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    override suspend fun setDailyAnswer(day: LocalDate, answer: String) {
        val updated = _dailyAnswers.value + (day to answer)
        write {
            putString(
                KEY_ANSWERS,
                JSONObject().apply {
                    updated.forEach { (d, text) -> put(d.toString(), text) }
                }.toString(),
            )
        }
        _dailyAnswers.value = updated
    }

    // --- reads ------------------------------------------------------------

    private fun readLedger(): List<LedgerEntry> {
        val raw = prefs.getString(KEY_LEDGER, null) ?: return emptyList()
        return runCatching { LedgerJson.entries(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    private fun readCues(): List<Cue> {
        val raw = prefs.getString(KEY_CUES, null) ?: return emptyList()
        return runCatching { LedgerJson.cues(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    override suspend fun dayState(date: LocalDate): CuePolicy.DayState =
        withContext(Dispatchers.IO) {
            val ledger = readLedger()
            val cues = readCues()
            CuePolicy.DayState(
                entriesToday = ledger.filter { it.entryDate == date },
                // Manual cues are excluded on purpose. The daily cap limits
                // how often Harbor interrupts someone, and a cue they asked
                // for is not an interruption — it would be perverse for
                // trying the feature to use up the day's allowance.
                cuesToday = cues.count {
                    it.firedDate == date && it.triggerSource != TriggerSource.MANUAL
                },
                // Across every day, not just today: the cooldown has to
                // survive midnight. Manual cues are left out for the same
                // reason they are left out of the cap — onboarding's preview
                // is one, so counting it started a two-hour cooldown on the
                // way out of the flow, and the first real walk after setting
                // Harbor up could never produce anything.
                lastCueAt = cues
                    .filter { it.triggerSource != TriggerSource.MANUAL }
                    .maxOfOrNull { it.firedAt },
                // Across days when the plan was made for tomorrow, and then
                // over: a plan holds reminders back until its own time has
                // been and gone, whether or not anybody closed it.
                //
                // It used to hold until `reminderDone`, which nothing in the
                // app ever set -- so one "later" tap switched sensed reminders
                // off for the rest of the study with no way back. The hold is
                // a question about now and is answered against the clock; the
                // closing is a fact about the person and is written down. See
                // domain/Reminders.
                hasPendingReminder = Reminders.holding(ledger, Instant.now()) != null,
                busyNow = Windows.busyAt(_weekBlocks.value, ZonedDateTime.now()),
            )
        }

    override suspend fun recentEntries(): List<LedgerEntry> =
        withContext(Dispatchers.IO) { readLedger().sortedBy { it.occurredAt } }

    // --- writes -----------------------------------------------------------

    override suspend fun recordCue(cue: Cue) {
        writeList(KEY_CUES) {
            (readCues() + cue)
                .associateBy { it.id }
                .values
                .sortedBy { it.firedAt }
                .takeLast(RETAINED)
                .let(LedgerJson::cues)
        }
    }

    override suspend fun append(entry: LedgerEntry) {
        // Any plan this moment closes on its own: they meant to call her, and
        // then they called her. Worked out inside the lambda, which runs under
        // the same lock as the write, so two rows landing at once cannot each
        // miss what the other did.
        val closed = mutableListOf<UUID>()
        writeList(KEY_LEDGER) {
            val held = readLedger()
            closed += Reminders.closedBy(held, entry)
            // Idempotent: re-appending the same moment replaces it rather than
            // duplicating, matching the server's upsert key.
            (held + entry)
                .associateBy { it.id }
                .values
                .map { if (it.id in closed) it.copy(reminderDone = true) else it }
                .sortedBy { it.occurredAt }
                .takeLast(RETAINED)
                .let(LedgerJson::entries)
        }
        if (closed.isNotEmpty()) {
            unsync(closed)
            closed.forEach { note(Moment.REMINDER_CLOSED, Reminders.Closed.REACHED_THEM.name) }
        }
    }

    override suspend fun markReminderDone(id: UUID, how: Reminders.Closed) {
        writeList(KEY_LEDGER) {
            readLedger()
                .map { if (it.id == id) it.copy(reminderDone = true) else it }
                .let(LedgerJson::entries)
        }
        unsync(listOf(id))
        // How it closed, as a category and never anybody's words. This is the
        // half of the study's second question that "later" never had: whether
        // a deferred call is one that eventually happens.
        note(Moment.REMINDER_CLOSED, how.name)
    }

    /** An amended entry has to go up to the server again. */
    private suspend fun unsync(ids: List<UUID>) {
        write {
            val synced = prefs.getStringSet(KEY_SYNCED_ENTRIES, emptySet()).orEmpty()
            putStringSet(KEY_SYNCED_ENTRIES, synced - ids.map(UUID::toString).toSet())
        }
    }

    // --- first run ---------------------------------------------------------

    override suspend fun hasOnboarded(): Boolean =
        withContext(Dispatchers.IO) { prefs.getBoolean(KEY_ONBOARDED, false) }

    override suspend fun setOnboarded() {
        write { putBoolean(KEY_ONBOARDED, true) }
    }

    override suspend fun arm(): StudyArm = withContext(Dispatchers.IO) {
        StudyArm.of(prefs.getString(KEY_ARM, null))
    }

    override suspend fun startOver(code: String?): StudyArm = withContext(Dispatchers.IO) {
        // Everything, not a selection. Choosing which keys survive is how a
        // reset quietly keeps a contact whose ledger rows have gone, or a
        // "seen the reminder" flag from a study arm that no longer exists.
        prefs.edit().clear().commit()
        // Republish every flow by hand. The listener at the top of this class
        // only watches two keys, and a clear() fires it for none of them --
        // so without this the UI would keep showing the person and the week
        // that no longer exist until the process died.
        _settings.value = readSettings()
        _contacts.value = readContacts()
        _weekBlocks.value = readWeekBlocks()
        _dailyAnswers.value = readDailyAnswers()
        val arm = StudyArm.fromCode(code)
        write {
            putString(KEY_ARM, arm.wire)
            putString(KEY_CODE, code?.trim().orEmpty())
        }
        arm
    }

    override suspend fun hasClaimedArm(): Boolean =
        withContext(Dispatchers.IO) { prefs.getString(KEY_ARM, null) != null }

    override suspend fun studyCode(): String? =
        withContext(Dispatchers.IO) { prefs.getString(KEY_CODE, null) }

    override suspend fun claimCode(code: String?): StudyArm = withContext(Dispatchers.IO) {
        // First call wins. See HarborRepository.claimCode: an arm that could
        // be reassigned is an arm that cannot be trusted on the rows already
        // written under it.
        val held = prefs.getString(KEY_ARM, null)
        if (held != null) {
            StudyArm.of(held)
        } else {
            val arm = StudyArm.fromCode(code)
            // The code is stored even when it is blank, because blank is
            // itself the answer: somebody went past the screen without one.
            write {
                putString(KEY_ARM, arm.wire)
                putString(KEY_CODE, code?.trim().orEmpty())
            }
            arm
        }
    }

    // --- the study export -------------------------------------------------

    override suspend fun allCues(): List<Cue> =
        withContext(Dispatchers.IO) { readCues().sortedBy { it.firedAt } }

    override suspend fun participantId(): UUID = withContext(Dispatchers.IO) {
        val held = prefs.getString(KEY_PARTICIPANT, null)
        if (held != null) {
            runCatching { UUID.fromString(held) }.getOrNull()
        } else {
            null
        } ?: UUID.randomUUID().also { fresh ->
            write { putString(KEY_PARTICIPANT, fresh.toString()) }
        }
    }

    // --- sync bookkeeping -------------------------------------------------

    override suspend fun unsyncedCues(): List<Cue> = withContext(Dispatchers.IO) {
        val synced = prefs.getStringSet(KEY_SYNCED_CUES, emptySet()).orEmpty()
        readCues().filter { it.id.toString() !in synced }.sortedBy { it.firedAt }
    }

    override suspend fun unsyncedEntries(): List<LedgerEntry> = withContext(Dispatchers.IO) {
        val synced = prefs.getStringSet(KEY_SYNCED_ENTRIES, emptySet()).orEmpty()
        readLedger().filter { it.id.toString() !in synced }.sortedBy { it.occurredAt }
    }

    override suspend fun markSynced(cueIds: List<UUID>, entryIds: List<UUID>) {
        if (cueIds.isEmpty() && entryIds.isEmpty()) return
        write {
            // Only track ids we still hold, so these sets cannot grow forever
            // as old rows age out of the retained window.
            val heldCues = readCues().mapTo(mutableSetOf()) { it.id.toString() }
            val heldEntries = readLedger().mapTo(mutableSetOf()) { it.id.toString() }
            val cues = prefs.getStringSet(KEY_SYNCED_CUES, emptySet()).orEmpty()
            val entries = prefs.getStringSet(KEY_SYNCED_ENTRIES, emptySet()).orEmpty()

            putStringSet(
                KEY_SYNCED_CUES,
                (cues + cueIds.map(UUID::toString)) intersect heldCues,
            )
            putStringSet(
                KEY_SYNCED_ENTRIES,
                (entries + entryIds.map(UUID::toString)) intersect heldEntries,
            )
        }
    }

    // --- plumbing ---------------------------------------------------------

    private suspend fun write(block: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            writeLock.withLock { prefs.edit().apply(block).commit() }
        }
    }

    private suspend fun writeList(key: String, build: () -> JSONArray) {
        withContext(Dispatchers.IO) {
            writeLock.withLock { prefs.edit().putString(key, build().toString()).commit() }
        }
    }

    private companion object {
        const val PREFS = "harbor"
        const val KEY_SETTINGS = "settings"
        const val KEY_CONTACTS = "contacts"
        // Still "busy_windows" though it now holds free blocks too. The
        // key is a storage address, not a description, and changing it would
        // lose the timetable of every phone that already has Harbor on it.
        const val KEY_BUSY = "busy_windows"
        const val KEY_ANSWERS = "daily_answers"
        const val KEY_BEATS = "study_beats"
        const val KEY_LEDGER = "ledger"
        const val KEY_CUES = "cues"
        const val KEY_ONBOARDED = "onboarded"

        /**
         * Written once, never rewritten. Stored as the wire name rather than
         * an ordinal so that reordering the enum cannot silently move every
         * participant into the other arm.
         */
        const val KEY_ARM = "study_arm"

        /** The code as typed, trimmed. Empty means asked and skipped. */
        const val KEY_CODE = "study_code"
        const val KEY_PARTICIPANT = "participant_id"
        const val KEY_SYNCED_CUES = "synced_cue_ids"
        const val KEY_SYNCED_ENTRIES = "synced_entry_ids"

        /**
         * Roughly a month at the daily cap. Enough for the week-one study and
         * for any sync backlog worth retrying; older rows are the server's
         * problem, not the phone's.
         */
        const val RETAINED = 90
    }
}
