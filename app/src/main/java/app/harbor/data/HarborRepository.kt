package app.harbor.data

import app.harbor.domain.Beat
import app.harbor.domain.Contact
import app.harbor.domain.Cue
import app.harbor.domain.CuePolicy
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Moment
import app.harbor.domain.Reminders
import app.harbor.domain.StudyArm
import app.harbor.domain.UserSettings
import app.harbor.domain.WeekBlock
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.util.UUID

/**
 * Everything Harbor persists, behind one interface.
 *
 * The interface exists so the storage engine can be swapped without the
 * pipeline noticing. [HarborStore] is a SharedPreferences + JSON
 * implementation, which is more than enough for the volumes here. Room is the
 * obvious upgrade if querying ever gets interesting; write it against this
 * interface when that day comes.
 *
 * This is local storage only. Sync to Supabase reads through here and pushes
 * outward — never the reverse, and never on the path to showing a cue.
 * See ADR-003.
 */
interface HarborRepository {

    /** The user's preferences and calibration. Never null. */
    val settings: StateFlow<UserSettings>

    /** Everyone the user has added. Empty until onboarding picks someone. */
    val contacts: StateFlow<List<Contact>>

    /**
     * The user's week as they drew it: classes, labs and shifts, and the
     * stretches they marked as a good time to be reached.
     *
     * Self-entered. There is no campus integration and no calendar read
     * behind this (ADR-011): the source stays the user, which costs no
     * permission and no credentials.
     */
    val weekBlocks: StateFlow<List<WeekBlock>>

    suspend fun setSettings(settings: UserSettings)

    /** Answers to the daily question, by local day. */
    val dailyAnswers: StateFlow<Map<LocalDate, String>>

    suspend fun setWeekBlocks(blocks: List<WeekBlock>)

    /**
     * Put the quiet nights on the week, once ever.
     *
     * Once, and the marker is what makes that true: somebody who drags them
     * off should not find them back the next time they open the screen. A
     * deleted night is a decision, not an accident to be corrected.
     *
     * @return true if they were added by this call.
     */
    suspend fun seedQuietNightsOnce(): Boolean

    suspend fun setDailyAnswer(day: LocalDate, answer: String)

    /**
     * Record that something happened, for the week-one study.
     *
     * Categories only — never anybody's words. See [app.harbor.domain.Moment].
     * Cheap enough to call from a tap handler: it appends and returns.
     */
    suspend fun note(moment: Moment, detail: String? = null, value: Int? = null)

    /** Every beat still held, oldest first. */
    suspend fun beats(): List<Beat>

    suspend fun upsertContact(contact: Contact)

    suspend fun deleteContact(id: UUID)

    /**
     * Assembles everything [CuePolicy.decide] needs to know about the user's
     * recent history.
     *
     * This lives here rather than in the policy because gathering it is IO,
     * and the policy has to stay pure. It is also the only place that knows
     * the three counters come from two different stores — cues for the cap and
     * the cooldown, entries for whether the user already connected.
     */
    suspend fun dayState(date: LocalDate): CuePolicy.DayState

    /**
     * Every entry still held, newest last.
     *
     * Used for the "calls with her usually run ~12 min" line, and later by the
     * garden. Bounded by the store's retention, so this stays a small read.
     */
    suspend fun recentEntries(): List<LedgerEntry>

    /** Records that a cue fired, before the user has answered it. */
    suspend fun recordCue(cue: Cue)

    /** Stage 9. */
    suspend fun append(entry: LedgerEntry)

    /**
     * Marks a plan the user made as closed, and records how it closed.
     *
     * [how] is not decoration. A proposed-later row that never gets one of
     * these is a plan that quietly lapsed, and telling that apart from a plan
     * somebody kept is the whole reason this method exists. Implementations
     * write the beat themselves so there is no way to close a plan and forget
     * to say how — see [app.harbor.domain.Moment.REMINDER_CLOSED].
     *
     * Note what this does *not* do: it never writes a call. Somebody tapping
     * "I already did" is telling us about a call Harbor had no part in, and
     * inventing a ledger row for it would put a call in the study's data that
     * nothing ever observed.
     */
    suspend fun markReminderDone(id: UUID, how: Reminders.Closed)

    /**
     * Whether the first run is behind us.
     *
     * Deliberately not part of [UserSettings]: it is a fact about this
     * install, not about the person, and it has no business syncing to a
     * server or appearing in a study export.
     */
    suspend fun hasOnboarded(): Boolean

    suspend fun setOnboarded()

    /**
     * Which arm of the study this install is in.
     *
     * Decided once and then fixed for the life of the install. Like
     * [hasOnboarded] it is a fact about this device rather than about the
     * person — but unlike it, this one *does* belong in the export, because
     * every row of behaviour is meaningless without it.
     *
     * Reading it before it has been set answers [StudyArm.GARDEN], which is
     * the arm that already existed. There is no setter that can change it
     * afterwards: see [claimArm].
     */
    suspend fun arm(): StudyArm

    /** Whether an arm has been claimed yet. False only before the code screen. */
    suspend fun hasClaimedArm(): Boolean

    /**
     * The study code this install was opened with, or null if it was never
     * asked, or empty if somebody went past without one.
     *
     * Kept beside the arm so the export can tell an assigned participant from
     * one who skipped: both are in [StudyArm.GARDEN], and only one of them was
     * meant to be.
     */
    suspend fun studyCode(): String?

    /**
     * Put this install in the arm a code names, if it is not in one already.
     *
     * Claim rather than set, and it is the whole design. A participant who
     * could be moved between arms halfway through is a participant whose
     * ledger belongs to neither, so the first call wins and every later one is
     * ignored — including one from a reinstall-and-retype, which would
     * otherwise silently relabel a week of data.
     *
     * @return the arm this install is in, which may not be the one asked for.
     */
    suspend fun claimCode(code: String?): StudyArm

    /**
     * Throw this install away and start again on a new code.
     *
     * The escape hatch for a code typed wrong at setup, and the *only* way an
     * arm ever changes. It is destructive on purpose: everything goes — the
     * person, the week, the ledger, the settings — and the app comes back at
     * onboarding as though it had just been installed.
     *
     * ## Why it cannot be a quiet switch
     *
     * [claimCode] refuses to reassign an arm because rows already written
     * under one arm cannot honestly be relabelled as the other. A "change the
     * code" that kept the ledger would produce exactly that file: some
     * behaviour from the garden, some from the bees, and a single `arm` field
     * at the top claiming all of it. Unreadable, and unreadable in a way
     * nobody would notice until the study was over.
     *
     * So the arm stays immutable and the *install* is what gets replaced. The
     * screen offering this has to say so before it happens.
     *
     * @return the arm the fresh install is in.
     */
    suspend fun startOver(code: String?): StudyArm

    /**
     * Every cue still held, for the study export.
     *
     * Distinct from [unsyncedCues]: the export is not a sync, and a
     * participant who exports twice should get the same rows both times
     * rather than an empty file the second time.
     */
    suspend fun allCues(): List<Cue>

    /**
     * A stable, meaningless id for this install.
     *
     * Generated on first use and kept. It is what lets a folder of exports be
     * told apart without any of them carrying a name; it is not derived from
     * anything about the device or the person, so it identifies the file and
     * nothing else.
     */
    suspend fun participantId(): UUID

    /** Cues and entries not yet accepted by the server, oldest first. */
    suspend fun unsyncedCues(): List<Cue>

    suspend fun unsyncedEntries(): List<LedgerEntry>

    /** Called after the server has acknowledged an upsert. */
    suspend fun markSynced(cueIds: List<UUID>, entryIds: List<UUID>)
}
