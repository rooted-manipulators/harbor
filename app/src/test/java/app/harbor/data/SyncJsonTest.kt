package app.harbor.data

import app.harbor.domain.BlockKind
import app.harbor.domain.Sharing
import app.harbor.domain.WeekBlock
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * What the wire actually carries.
 *
 * [SupabaseClient] is a shell that cannot be run without a network, so the
 * shaping and parsing were pulled out into [SyncJson] where they can be. This
 * is the half of the sync that is checkable, and it is the half where the bugs
 * are: a mis-named column fails loudly, a wrong day number moves somebody's
 * whole week by a day and looks fine.
 */
class SyncJsonTest {

    private val me = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private fun block(
        day: DayOfWeek = DayOfWeek.TUESDAY,
        from: LocalTime = LocalTime.of(14, 0),
        to: LocalTime = LocalTime.of(16, 0),
        kind: BlockKind = BlockKind.BUSY,
    ) = Sharing.SharedBlock(day.value, from, to, kind)

    // --- going out -----------------------------------------------------------

    @Test
    fun `a row carries the columns 0011 declares and no others`() {
        val row = SyncJson.weekRows(me, listOf(block())).getJSONObject(0)
        val keys = row.keys().asSequence().toSet()
        assertEquals(
            setOf("id", "user_id", "day", "starts_at", "ends_at", "kind"),
            keys,
        )
    }

    @Test
    fun `nothing a label could hide in reaches the wire`() {
        // The promise, checked one layer further out than SharingTest checks
        // it. That test proves the stripping; this proves nothing puts it back.
        val labelled = WeekBlock(
            DayOfWeek.TUESDAY,
            LocalTime.of(14, 0),
            LocalTime.of(16, 0),
            BlockKind.BUSY,
            "Therapy",
        )
        val json = SyncJson.weekRows(me, Sharing.forWire(listOf(labelled))).toString()
        assertFalse("a label reached the wire: $json", json.contains("Therapy", true))
        assertFalse("a label column was invented: $json", json.contains("label", true))
    }

    @Test
    fun `sending the same week twice does not double it`() {
        // week_blocks.id is a primary key the client supplies. A fresh UUID per
        // sync would turn every upsert into an insert and a week would grow a
        // copy of itself every time it was sent.
        assertEquals(SyncJson.idFor(me, block()), SyncJson.idFor(me, block()))
    }

    @Test
    fun `two different blocks are two different rows`() {
        assertNotEquals(
            SyncJson.idFor(me, block()),
            SyncJson.idFor(me, block(to = LocalTime.of(17, 0))),
        )
        assertNotEquals(
            SyncJson.idFor(me, block()),
            SyncJson.idFor(me, block(kind = BlockKind.FREE)),
        )
        assertNotEquals(
            SyncJson.idFor(me, block()),
            SyncJson.idFor(UUID.randomUUID(), block()),
        )
    }

    @Test
    fun `the enum is written the way the column spells it`() {
        // block_kind is a Postgres enum of 'busy' and 'free'. Sending BUSY
        // fails the insert, and it fails at the server where nobody is looking.
        val rows = SyncJson.weekRows(me, listOf(block(kind = BlockKind.FREE)))
        assertEquals("free", rows.getJSONObject(0).getString("kind"))
    }

    @Test
    fun `monday is one, the way both ends count`() {
        assertEquals(1, SyncJson.weekRows(me, listOf(block(day = DayOfWeek.MONDAY)))
            .getJSONObject(0).getInt("day"))
        assertEquals(7, SyncJson.weekRows(me, listOf(block(day = DayOfWeek.SUNDAY)))
            .getJSONObject(0).getInt("day"))
    }

    // --- coming back ---------------------------------------------------------

    @Test
    fun `a week survives the round trip`() {
        val sent = listOf(block(), block(day = DayOfWeek.FRIDAY, kind = BlockKind.FREE))
        val back = SyncJson.blocks(JSONArray(SyncJson.weekRows(me, sent).toString()))
        assertEquals(sent, back)
    }

    @Test
    fun `postgres time with seconds parses`() {
        val rows = JSONArray(
            """[{"day":2,"starts_at":"14:00:00","ends_at":"16:30:00","kind":"busy"}]""",
        )
        val back = SyncJson.blocks(rows).single()
        assertEquals(LocalTime.of(14, 0), back.start)
        assertEquals(LocalTime.of(16, 30), back.end)
    }

    @Test
    fun `one unreadable row costs one block, not the week`() {
        // A server nobody controls can hand back anything. Losing the whole
        // week to one bad row would turn a shrug into an outage.
        val rows = JSONArray(
            """[
              {"day":2,"starts_at":"14:00:00","ends_at":"16:00:00","kind":"busy"},
              {"day":"Tuesday","starts_at":"nope","ends_at":"","kind":"busy"},
              {"day":5,"starts_at":"09:00:00","ends_at":"10:00:00","kind":"free"}
            ]""",
        )
        assertEquals(2, SyncJson.blocks(rows).size)
    }

    @Test
    fun `links parse, and a state we do not know is dropped`() {
        val rows = JSONArray(
            """[
              {"id":"$me","requester":"$me","addressee":"$me","state":"accepted"},
              {"id":"$me","requester":"$me","addressee":"$me","state":"whatever"}
            ]""",
        )
        val links = SyncJson.links(rows)
        assertEquals(1, links.size)
        assertEquals(Sharing.LinkState.ACCEPTED, links.single().state)
    }

    // --- auth ----------------------------------------------------------------

    @Test
    fun `an address is trimmed and sent as an address`() {
        assertEquals("dev@yahoo.com", SyncJson.otpRequest("  dev@yahoo.com ").getString("email"))
        assertEquals("email", SyncJson.otpVerify("dev@yahoo.com", "123456").getString("type"))
        assertEquals("123456", SyncJson.otpVerify("dev@yahoo.com", " 123456 ").getString("token"))
    }

    @Test
    fun `outlook is azure, because supabase calls it that`() {
        // The one that is easy to get wrong: there is no 'microsoft' provider,
        // and asking for one gets a 400 that reads like the account is bad.
        assertEquals("google", SyncJson.Provider.GOOGLE.slug)
        assertEquals("azure", SyncJson.Provider.MICROSOFT.slug)
    }

    @Test
    fun `tokens are read out of the fragment, not the query`() {
        // GoTrue puts them after the # so they never reach a web log. A parser
        // that read the query would find nothing and the sign-in would look
        // like a refusal.
        val parts = SyncJson.fragment(
            "harbor://auth#access_token=abc&refresh_token=def&expires_in=3600&token_type=bearer",
        )
        assertEquals("abc", parts["access_token"])
        assertEquals("def", parts["refresh_token"])
        val session = SyncJson.sessionFromFragment(parts)!!
        assertEquals("abc", session.accessToken)
        assertEquals(3600L, session.expiresInSeconds)
        assertNull("a fragment carries no account id", session.userId)
    }

    @Test
    fun `a bare fragment works, with or without the hash`() {
        listOf(
            "#access_token=a&refresh_token=b",
            "harbor://auth#access_token=a&refresh_token=b",
            "harbor://auth?access_token=a&refresh_token=b",
        ).forEach {
            assertEquals("a", SyncJson.fragment(it)["access_token"])
        }
    }

    @Test
    fun `a percent-encoded value comes back whole`() {
        val parts = SyncJson.fragment("#access_token=a.b-c&error_description=Invalid%20code")
        assertEquals("Invalid code", parts["error_description"])
    }

    @Test
    fun `a redirect that refused is not read as a sign-in`() {
        // Providers come home on the same URI when somebody cancels. Reading
        // that as a session would sign them in as nobody.
        val parts = SyncJson.fragment("harbor://auth#error=access_denied&error_description=nope")
        assertNull(SyncJson.sessionFromFragment(parts))
        assertNull(SyncJson.sessionFromFragment(emptyMap()))
        assertEquals(emptyMap<String, String>(), SyncJson.fragment("harbor://auth"))
    }

    @Test
    fun `the account id is read off the user body`() {
        assertEquals(me, SyncJson.userId(org.json.JSONObject("""{"id":"$me"}""")))
        assertNull(SyncJson.userId(org.json.JSONObject("""{"nope":1}""")))
    }

    @Test
    fun `a body that is not a session is not read as one`() {
        assertNull(SyncJson.session(org.json.JSONObject("""{"error":"bad code"}""")))
    }

    @Test
    fun `a session is read whole`() {
        val body = org.json.JSONObject(
            """{"access_token":"a","refresh_token":"r","expires_in":3600,
                "user":{"id":"$me"}}""",
        )
        val session = SyncJson.session(body)!!
        assertEquals("a", session.accessToken)
        assertEquals(me, session.userId)
        assertTrue(session.expiresInSeconds > 0)
    }

    // --- the schedule inbox --------------------------------------------------

    private val row = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `an inbox row comes back as a block and the id that clears it`() {
        val rows = JSONArray(
            """[{"id":"$row","user_id":"$me","day":4,"starts_at":"14:00:00",
                 "ends_at":"16:00:00","kind":"busy","arrived_at":"2026-09-16T10:00:00Z"}]""",
        )
        val arrived = SyncJson.inbox(rows)
        assertEquals(1, arrived.size)
        // The id matters as much as the block: without it the row is never
        // deleted and the same lecture is placed again on every resume.
        assertEquals(row, arrived[0].id)
        assertEquals(block(DayOfWeek.THURSDAY), arrived[0].block)
    }

    @Test
    fun `a bad inbox row costs one block, not the batch`() {
        val rows = JSONArray(
            """[{"id":"not-a-uuid","day":1,"starts_at":"09:00:00","ends_at":"10:00:00","kind":"busy"},
                {"id":"$row","day":2,"starts_at":"14:00:00","ends_at":"16:00:00","kind":"busy"}]""",
        )
        assertEquals(1, SyncJson.inbox(rows).size)
    }

    @Test
    fun `a block that arrived has no label, because there was nowhere to send one`() {
        // The bot reads a whole group message and keeps only the times. A
        // label appearing here would mean the server had started keeping what
        // somebody's class chat actually said.
        val rows = JSONArray(
            """[{"id":"$row","day":2,"starts_at":"14:00:00","ends_at":"16:00:00","kind":"busy"}]""",
        )
        val week = Sharing.fromWire(SyncJson.inbox(rows).map { it.block })
        assertNull(week.single().label)
    }
}
