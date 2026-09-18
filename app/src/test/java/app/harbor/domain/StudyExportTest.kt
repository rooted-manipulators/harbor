package app.harbor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * What the export does and does not carry.
 *
 * The redaction is the whole point of this file existing, and it is the kind
 * of thing that decays quietly: someone adds a field to [LedgerEntry], the
 * encoder picks it up, and a participant's words leave their phone without
 * anyone noticing. So every secret below is a real string that must not appear
 * in the output, checked against the finished text rather than the intent.
 */
class StudyExportTest {

    private val who = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val cueId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val at = Instant.parse("2026-09-12T09:30:00Z")

    private fun bundle(
        note: String? = "SECRET_WORDS",
        topic: String? = "the tomatoes",
        arm: StudyArm = StudyArm.GARDEN,
        grants: StudyExport.Grants = allGranted,
    ) = StudyExport.Bundle(
        participant = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        exportedAt = at,
        appVersion = "1.0 (2)",
        settings = UserSettings(
            name = "SECRET_MYNAME",
            thresholds = Thresholds.SUGGESTED,
            cuesEnabled = true,
        ),
        contacts = listOf(
            Contact(
                id = who,
                label = "SECRET_HERNAME",
                phoneE164 = "+SECRETNUMBER",
                kind = ContactKind.PERSON,
                tone = Tone.GOLD,
                cueSoundRef = "SECRET_RINGTONE",
                photoRef = "SECRET_PHOTO",
            ),
        ),
        blocks = listOf(
            WeekBlock(
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                BlockKind.BUSY,
                "SECRET_CLASS",
            ),
            WeekBlock(
                DayOfWeek.SUNDAY,
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                BlockKind.FREE,
            ),
        ),
        cues = listOf(Cue(cueId, LocalDate.of(2026, 9, 11), TriggerSource.WALKING_STOP, at)),
        entries = listOf(
            LedgerEntry(
                id = UUID.randomUUID(),
                entryDate = LocalDate.of(2026, 9, 11),
                cueId = cueId,
                contactId = who,
                triggerSource = TriggerSource.WALKING_STOP,
                thresholdSnapshot = Thresholds.SUGGESTED,
                resolution = Resolution.CALLED,
                proposedTime = null,
                feedbackPulse = FeedbackPulse.GOOD_TIME,
                callMinutes = 15,
                feeling = Feeling.WARM,
                flower = FlowerKind.LIGHTER_NOW,
                topic = topic,
                note = note,
                occurredAt = at,
            ),
        ),
        lastTransitionAt = at,
        grants = grants,
        arm = arm,
        studyCode = if (arm == StudyArm.BEES) "B-07" else "A-07",
        beats = listOf(
            Beat(at, Moment.APP_OPENED),
            Beat(at, Moment.CALL_STARTED, TriggerSource.WALKING_STOP.name),
            Beat(at, Moment.ANSWER_KEPT),
        ),
    )

    /**
     * Everything granted, which is the uninteresting case and therefore the
     * right default: a test that is not about permissions should not have to
     * think about them, and the tests below that *are* about them pass their
     * own.
     */
    private val allGranted = StudyExport.Grants(
        activityRecognition = true,
        notifications = true,
        fullScreen = true,
        overApps = true,
        usageAccess = true,
        unrestricted = true,
    )

    // --- redaction ----------------------------------------------------------

    @Test
    fun `the file says which switches were on, so a quiet week can be read`() {
        // A week with no cues has five explanations and only one of them is
        // a finding. Without these the other four are invisible and every
        // one of them looks like "the trigger did not fire".
        val blind = StudyExport.json(
            bundle(
                grants = StudyExport.Grants(
                    activityRecognition = true,
                    notifications = false,
                    fullScreen = false,
                    overApps = false,
                    usageAccess = false,
                    unrestricted = false,
                ),
            ),
        )
        assertTrue(blind.contains("\"notifications\":false"))
        assertTrue(blind.contains("\"usage_access\":false"))
        assertTrue(blind.contains("\"over_apps\":false"))
        assertTrue(blind.contains("\"unrestricted\":false"))
        assertTrue(StudyExport.json(bundle()).contains("\"notifications\":true"))
    }

    @Test
    fun `the per-trigger cap is on the file`() {
        // Four a day means nothing to a reader who cannot see that only two
        // of them could ever have come from the walk.
        assertTrue(StudyExport.json(bundle()).contains("\"source_cap\":2"))
    }

    @Test
    fun `the words of a line never leave the phone`() {
        assertFalse(StudyExport.json(bundle()).contains("SECRET_WORDS"))
    }

    @Test
    fun `no name, number, photo or ringtone goes with it`() {
        val json = StudyExport.json(bundle())
        listOf(
            "SECRET_HERNAME", "SECRETNUMBER", "SECRET_PHOTO", "SECRET_RINGTONE",
            "SECRET_MYNAME",
        ).forEach {
            assertFalse("$it leaked into the export", json.contains(it))
        }
    }

    @Test
    fun `a beat carries a category and never a word`() {
        // The whole safety of the study log: it says which kind of thing
        // happened, and when, and nothing about what was in it.
        val json = StudyExport.json(bundle())
        assertTrue(json.contains("app_opened"))
        assertTrue(json.contains("call_started"))
        assertTrue("the daily word must not ride out with the beat", !json.contains("SECRET_NOTE"))
    }

    @Test
    fun `what you called a busy block stays yours`() {
        // "Therapy, 4pm" is exactly the sort of label this protects.
        assertFalse(StudyExport.json(bundle()).contains("SECRET_CLASS"))
    }

    @Test
    fun `the times of a busy block do go, because suppression depends on them`() {
        val json = StudyExport.json(bundle())
        assertTrue(json.contains("MONDAY"))
        assertTrue(json.contains("09:00"))
    }

    @Test
    fun `which kind a block was goes too, because that is the study's question`() {
        // A cue that landed in a stretch the participant had marked good is
        // the closest thing week one gets to ground truth, and the times
        // alone cannot say which stretches those were.
        val json = StudyExport.json(bundle())
        assertTrue(json.contains("\"kind\":\"busy\""))
        assertTrue(json.contains("\"kind\":\"free\""))
    }

    @Test
    fun `a contact is an id, a kind and a colour`() {
        val json = StudyExport.json(bundle())
        assertTrue("the id is needed to tell one person from another", json.contains(who.toString()))
        assertTrue(json.contains("\"tone\":\"gold\""))
        assertTrue(json.contains("\"kind\":\"person\""))
    }

    @Test
    fun `the file states what it withheld`() {
        val json = StudyExport.json(bundle())
        assertTrue(json.contains("\"omitted\""))
        StudyExport.OMITTED.forEach { assertTrue(json.contains(it)) }
    }

    // --- what the study actually needs --------------------------------------

    @Test
    fun `the three study questions can be answered from the file`() {
        val json = StudyExport.json(bundle())
        // 1: did the trigger land well — the pulse and its source.
        assertTrue(json.contains("\"feedback_pulse\":\"good_time\""))
        assertTrue(json.contains("\"trigger_source\":\"walking_stop\""))
        // 2: what happened to a cue.
        assertTrue(json.contains("\"resolution\":\"called\""))
        // 3: where people moved their thresholds, as in force at the time.
        assertTrue(json.contains("\"threshold_snapshot\""))
        assertTrue(json.contains("\"walking_minutes\":3"))
    }

    @Test
    fun `the arm is on the file, so two arms are one table`() {
        // Without this the study is two piles of results nobody can tell
        // apart, and nothing recovers it after the fact.
        assertTrue(StudyExport.json(bundle(arm = StudyArm.GARDEN)).contains("\"arm\":\"garden\""))
        assertTrue(StudyExport.json(bundle(arm = StudyArm.BEES)).contains("\"arm\":\"bees\""))
        // And the format number moved with it, so a reader can refuse a file
        // that predates the field rather than quietly treating it as control.
        assertTrue(StudyExport.json(bundle()).contains("\"format\":4"))
    }

    @Test
    fun `an arm is a letter, not a coin flip`() {
        // Batches are decided on paper before anybody is handed a phone.
        assertEquals(StudyArm.BEES, StudyArm.fromCode("B-07"))
        assertEquals(StudyArm.BEES, StudyArm.fromCode("  b12 "))
        assertEquals(StudyArm.GARDEN, StudyArm.fromCode("A-07"))
        // The two ways somebody ends up with no code at all.
        assertEquals(StudyArm.GARDEN, StudyArm.fromCode(null))
        assertEquals(StudyArm.GARDEN, StudyArm.fromCode("   "))
    }

    @Test
    fun `unknown arm text reads as the control rather than throwing`() {
        // A file written by a later build should not stop somebody opening
        // this week's data.
        assertEquals(StudyArm.GARDEN, StudyArm.of("moths"))
        assertEquals(StudyArm.GARDEN, StudyArm.of(null))
        assertEquals(StudyArm.BEES, StudyArm.of("BEES"))
    }

    @Test
    fun `enum names match the wire names Postgres uses`() {
        val json = StudyExport.json(bundle())
        assertTrue(json.contains("\"feeling\":\"warm\""))
        assertTrue(json.contains("\"flower\":\"lighter_now\""))
    }

    @Test
    fun `a call that did not happen is not counted as one`() {
        val notReached = bundle().let { b ->
            b.copy(entries = b.entries.map { it.copy(resolution = Resolution.NOT_REACHED) })
        }
        val json = StudyExport.json(notReached)
        assertTrue(json.contains("\"resolution\":\"not_reached\""))
        assertEquals(0, StudyExport.summarise(notReached).calls)
    }

    @Test
    fun `the file records whether the phone was ever heard from`() {
        // A week with no cues is uninterpretable without this.
        assertTrue(StudyExport.json(bundle()).contains("\"last_transition_at\""))
        val silent = bundle().copy(lastTransitionAt = null)
        assertTrue(StudyExport.json(silent).contains("\"last_transition_at\":null"))
    }

    // --- the encoder ---------------------------------------------------------

    @Test
    fun `the output is structurally valid json`() {
        assertValid(StudyExport.json(bundle()))
    }

    @Test
    fun `an empty week still produces a valid file`() {
        val empty = bundle().copy(contacts = emptyList(), blocks = emptyList(), cues = emptyList(), entries = emptyList())
        val json = StudyExport.json(empty)
        assertValid(json)
        assertTrue(json.contains("\"entries\":[]"))
    }

    @Test
    fun `quotes and newlines in a topic do not break the file`() {
        val json = StudyExport.json(bundle(topic = "say \"hi\"\nto mum\tplease"))
        assertValid(json)
        assertTrue(json.contains("\\\""))
        assertTrue(json.contains("\\n"))
        assertFalse("a raw newline escaped into the json", json.contains("\nto mum"))
    }

    @Test
    fun `a missing value is null rather than absent`() {
        val json = StudyExport.json(bundle(note = null, topic = null))
        assertTrue(json.contains("\"topic\":null"))
        assertValid(json)
    }

    // --- the wrapper ---------------------------------------------------------

    @Test
    fun `the filename sorts by day and names nobody`() {
        val name = StudyExport.filename(bundle())
        assertEquals("harbor-2026-09-12-33333333.json", name)
        assertFalse(name.contains("SECRET"))
    }

    @Test
    fun `the summary counts what the screen shows`() {
        val s = StudyExport.summarise(bundle())
        assertEquals(1, s.cues)
        assertEquals(1, s.calls)
        assertEquals(0, s.messages)
        assertEquals(1, s.days)
    }

    /**
     * A structural check, not a parser: balanced containers, no empty members
     * and no trailing commas, with anything inside a string ignored. It
     * catches the mistakes an encoder actually makes.
     */
    private fun assertValid(json: String) {
        assertTrue("must be an object", json.startsWith("{") && json.endsWith("}"))
        var depth = 0
        var inString = false
        var escaped = false
        var previous = ' '
        json.forEachIndexed { i, c ->
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' || c == '[' -> depth++
                c == '}' || c == ']' -> {
                    depth--
                    assertTrue("unbalanced at $i", depth >= 0)
                    assertTrue("trailing comma at $i", previous != ',')
                }
                c == ',' -> {
                    assertTrue("empty member at $i", previous != ',' && previous != '{' && previous != '[')
                }
            }
            if (!inString || c == '"') previous = c
        }
        assertEquals("containers left open", 0, depth)
        assertFalse("unterminated string", inString)
    }
}
