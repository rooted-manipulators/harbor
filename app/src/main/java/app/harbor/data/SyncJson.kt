package app.harbor.data

import app.harbor.domain.BlockKind
import app.harbor.domain.Sharing
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.util.UUID

/**
 * The wire format for everything that crosses to the server, and back.
 *
 * Hand-written against `backend/supabase/migrations/0011_people_and_sharing.sql`,
 * the same way [LedgerJson] is hand-written against the study file. A
 * serialisation library would decide the shape from the Kotlin types, which is
 * the wrong way round when the schema is the contract and two of its columns
 * exist specifically to *not* carry something.
 *
 * Pure. No IO, no clock, no Android — so the part of networking where the bugs
 * actually live can be tested, and [SupabaseClient] can stay a thin shell
 * around it.
 *
 * ## Reading is forgiving, writing is exact
 *
 * A row that cannot be parsed is dropped rather than thrown, because one bad
 * row on a server nobody controls should cost one block, not the whole week.
 * Writing has no such latitude: a malformed body is our bug and should fail
 * loudly in review, not quietly at three in the morning.
 */
internal object SyncJson {

    // --- the week, going out -----------------------------------------------

    /**
     * A week as rows for `week_blocks`.
     *
     * Takes [Sharing.SharedBlock] and not `WeekBlock`, so there is no point in
     * this file where a label is in scope and could be written by accident.
     * The type that reaches here has already been stripped, by a function whose
     * only job is to strip it.
     */
    fun weekRows(owner: UUID, blocks: List<Sharing.SharedBlock>): JSONArray {
        val out = JSONArray()
        blocks.forEach { block ->
            out.put(
                JSONObject().apply {
                    // The id is derived from the row rather than random, so
                    // sending the same week twice updates rather than
                    // duplicates. There is no local id for a block to carry --
                    // WeekBlock is a value, and two identical blocks on a
                    // Tuesday are the same block.
                    put("id", idFor(owner, block).toString())
                    put("user_id", owner.toString())
                    put("day", block.day)
                    put("starts_at", block.start.toString())
                    put("ends_at", block.end.toString())
                    put("kind", block.kind.name.lowercase())
                },
            )
        }
        return out
    }

    /**
     * A stable id for a block, from what the block is.
     *
     * `week_blocks.id` is a primary key and the client has to supply one. A
     * fresh UUID every sync would make an upsert an insert, and a week would
     * grow a copy of itself every time it was sent.
     */
    fun idFor(owner: UUID, block: Sharing.SharedBlock): UUID =
        UUID.nameUUIDFromBytes(
            "$owner|${block.day}|${block.start}|${block.end}|${block.kind}".toByteArray(),
        )

    // --- the week, coming back ---------------------------------------------

    fun blocks(rows: JSONArray): List<Sharing.SharedBlock> =
        (0 until rows.length()).mapNotNull { i ->
            runCatching {
                val row = rows.getJSONObject(i)
                Sharing.SharedBlock(
                    day = row.getInt("day"),
                    start = time(row.getString("starts_at")),
                    end = time(row.getString("ends_at")),
                    kind = BlockKind.valueOf(row.getString("kind").uppercase()),
                )
            }.getOrNull()
        }

    /**
     * Postgres `time` comes back as `14:00:00`, and sometimes with fractional
     * seconds. [LocalTime.parse] handles both; this only guards the shape.
     */
    private fun time(raw: String): LocalTime = LocalTime.parse(raw)

    // --- the schedule inbox --------------------------------------------------

    /**
     * A block the WhatsApp bot parsed out of a forwarded message.
     *
     * Carries the row's id as well as the block, because the phone deletes the
     * row once it has placed it — this is a queue, not a mirror. That id is
     * server-generated, which is the one place in this app where that happens;
     * `0013` records why it is safe here and nowhere else.
     */
    data class Arrived(val id: UUID, val block: Sharing.SharedBlock)

    /**
     * Rows from `schedule_inbox`.
     *
     * Reuses [Sharing.SharedBlock] rather than growing a type of its own. Same
     * columns, and the missing one is missing for the same reason: a block that
     * arrives has no label, because the bot has nowhere to have kept one.
     */
    fun inbox(rows: JSONArray): List<Arrived> =
        (0 until rows.length()).mapNotNull { i ->
            runCatching {
                val row = rows.getJSONObject(i)
                Arrived(
                    id = UUID.fromString(row.getString("id")),
                    block = Sharing.SharedBlock(
                        day = row.getInt("day"),
                        start = time(row.getString("starts_at")),
                        end = time(row.getString("ends_at")),
                        kind = BlockKind.valueOf(row.getString("kind").uppercase()),
                    ),
                )
            }.getOrNull()
        }

    // --- links ---------------------------------------------------------------

    fun links(rows: JSONArray): List<Sharing.Link> =
        (0 until rows.length()).mapNotNull { i ->
            runCatching {
                val row = rows.getJSONObject(i)
                Sharing.Link(
                    id = UUID.fromString(row.getString("id")),
                    requester = UUID.fromString(row.getString("requester")),
                    addressee = UUID.fromString(row.getString("addressee")),
                    state = Sharing.LinkState.valueOf(row.getString("state").uppercase()),
                )
            }.getOrNull()
        }

    // --- auth ----------------------------------------------------------------

    /**
     * Where somebody signs in, and how the answer gets back.
     *
     * Google and Microsoft are Supabase's own providers -- `azure` covers
     * Outlook, Hotmail and Live, which are one account wearing three names.
     *
     * Yahoo is not on that list and cannot be, so [EMAIL] is not a lesser
     * third option: it is the one that covers Yahoo, and every other address
     * besides. A code to your inbox needs no provider configured at all and
     * works for anybody, which makes it the honest default rather than the
     * fallback.
     */
    enum class Provider(val slug: String) {
        GOOGLE("google"),
        MICROSOFT("azure"),
        EMAIL("email"),
    }

    /** Ask for a code by email. */
    fun otpRequest(email: String): JSONObject =
        JSONObject().apply { put("email", email.trim()) }

    /** Turn a code into a session. */
    fun otpVerify(email: String, code: String): JSONObject =
        JSONObject().apply {
            put("email", email.trim())
            put("token", code.trim())
            put("type", "email")
        }

    /**
     * What a provider hands back, parsed out of the redirect it comes home on.
     *
     * GoTrue puts the tokens in the URL **fragment** rather than the query, so
     * they are never sent to a server as part of the request line and never
     * reach a web log. That is the point of the design, and it means this has
     * to read the part after the `#`, which is the bit most URL parsers throw
     * away.
     *
     * Forgiving about what it is given: a whole redirect URI, a bare fragment,
     * with or without the leading `#`. The caller is handing over whatever
     * Android put in an Intent and should not have to tidy it first.
     */
    fun fragment(raw: String): Map<String, String> {
        val body = raw.substringAfter('#', missingDelimiterValue = "")
            .ifBlank { raw.substringAfter('?', missingDelimiterValue = "") }
            .ifBlank { return emptyMap() }
        return body.split('&').mapNotNull { pair ->
            val name = pair.substringBefore('=', missingDelimiterValue = "")
            val value = pair.substringAfter('=', missingDelimiterValue = "")
            if (name.isBlank() || value.isBlank()) null else name to decode(value)
        }.toMap()
    }

    /**
     * A session from a redirect's fragment, or null if it is not one.
     *
     * The id is absent on purpose -- the fragment carries tokens and nothing
     * else -- so the caller fetches it with [userId] once it has a token to ask
     * with.
     */
    fun sessionFromFragment(parts: Map<String, String>): Session? {
        val access = parts["access_token"] ?: return null
        val refresh = parts["refresh_token"] ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            userId = null,
            expiresInSeconds = parts["expires_in"]?.toLongOrNull() ?: 3600L,
        )
    }

    /** The account id out of a `GET /auth/v1/user` body. */
    fun userId(body: JSONObject): UUID? =
        runCatching { UUID.fromString(body.getString("id")) }.getOrNull()

    /**
     * What a sign-in gives back, or null if the body is not one.
     *
     * `expires_in` is seconds from now and is turned into an instant by the
     * caller, which owns the clock. This file does not read one.
     */
    fun session(body: JSONObject): Session? = runCatching {
        Session(
            accessToken = body.getString("access_token"),
            refreshToken = body.getString("refresh_token"),
            userId = UUID.fromString(body.getJSONObject("user").getString("id")),
            expiresInSeconds = body.optLong("expires_in", 3600L),
        )
    }.getOrNull()

    /** Percent-decoding, without pulling in a URL parser for one job. */
    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    /** A signed-in account, as the server described it. */
    internal data class Session(
        val accessToken: String,
        val refreshToken: String,
        /** Null when it came off a redirect, which carries no id. */
        val userId: UUID?,
        val expiresInSeconds: Long,
    )
}
