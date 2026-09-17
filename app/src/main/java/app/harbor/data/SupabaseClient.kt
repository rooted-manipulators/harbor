package app.harbor.data

import android.content.Context
import android.util.Log
import app.harbor.BuildConfig
import app.harbor.domain.Sharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/**
 * Harbor's one connection to the outside.
 *
 * Plain REST against Supabase — PostgREST for rows, GoTrue for the sign-in —
 * over [HttpURLConnection], which is in the platform. No HTTP
 * library and no Supabase SDK: the SDK would bring ktor and a serialisation
 * plugin for six endpoints, and this repo writes its wire formats by hand
 * already (see [LedgerJson], [SyncJson]). The dependency list is a thing this
 * codebase has protected on purpose and there was no reason to spend it here.
 *
 * Every request is a GET, a POST or a DELETE. `HttpURLConnection` will not
 * reliably send a PATCH, so answering a link goes through the `answer_link`
 * function in `0011` instead — which is the better place for that rule anyway.
 *
 * ## Everything here is allowed to fail
 *
 * Every call returns null rather than throwing, and every caller must be able
 * to carry on without it. That is not defensive habit, it is the constraint
 * ADR-013 kept from ADR-003: the cue has to fire on a train with no signal.
 * Sensing, the policy, the ledger and the reminder surface must never await
 * anything in this file, and nothing in this file writes to them.
 *
 * ## No backend configured is a supported state
 *
 * With no URL set, [configured] is false and every call returns null without
 * touching the network. Harbor then behaves exactly as it did before it had a
 * server. That is what lets a build reach a participant while the backend is
 * still being stood up, and it is why the gradle properties default to empty
 * rather than to a staging URL somebody would forget about.
 */
internal class SupabaseClient(context: Context) {

    private val session = SessionStore(context)

    val configured: Boolean get() = BASE.isNotBlank() && KEY.isNotBlank()

    val userId: UUID? get() = session.userId

    val signedIn: Boolean get() = session.userId != null

    // --- signing in ----------------------------------------------------------

    /**
     * Where to send somebody to sign in with a provider.
     *
     * Handed to a browser, not loaded here. A sign-in page inside the app's own
     * WebView is a text field asking for a Google password on a screen Google
     * did not draw, which is the exact shape of a phishing page and which both
     * Google and Microsoft refuse. The browser owns this, and comes back to
     * [REDIRECT].
     */
    fun authorizeUrl(provider: SyncJson.Provider): String? {
        if (!configured || provider == SyncJson.Provider.EMAIL) return null
        return "$BASE/auth/v1/authorize?provider=${provider.slug}" +
            "&redirect_to=" + java.net.URLEncoder.encode(REDIRECT, "UTF-8")
    }

    /**
     * Finish a provider sign-in from the redirect it came home on.
     *
     * The fragment carries tokens and no account id, so the id is fetched with
     * the token before anything is saved -- a session without one is no use to
     * [putWeek] or [links], both of which need to know who they are.
     */
    suspend fun completeSignIn(redirect: String): Boolean {
        val parts = SyncJson.fragment(redirect)
        val partial = SyncJson.sessionFromFragment(parts) ?: return false
        val body = call("$BASE/auth/v1/user", "GET", null, token = partial.accessToken)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return false
        val id = SyncJson.userId(body) ?: return false
        session.save(partial.copy(userId = id), Instant.now().plusSeconds(partial.expiresInSeconds))
        return true
    }

    /**
     * Ask for a code by email. True if the server took the request.
     *
     * The route for Yahoo and for everybody whose address is not a Google or a
     * Microsoft one. It needs no provider configured, which makes it the one
     * that always works.
     */
    suspend fun requestCode(email: String): Boolean =
        call("$BASE/auth/v1/otp", "POST", SyncJson.otpRequest(email).toString(), auth = false) != null

    /**
     * Turn a code into a session. True if it is now signed in.
     *
     * The expiry is worked out here rather than in [SyncJson], because this is
     * the layer that owns a clock and that file deliberately does not read one.
     */
    suspend fun signIn(email: String, code: String): Boolean {
        val text = call(
            "$BASE/auth/v1/verify",
            "POST",
            SyncJson.otpVerify(email, code).toString(),
            auth = false,
        ) ?: return false
        val body = runCatching { JSONObject(text) }.getOrNull() ?: return false
        val fresh = SyncJson.session(body) ?: return false
        session.save(fresh, Instant.now().plusSeconds(fresh.expiresInSeconds))
        return true
    }

    fun signOut() = session.clear()

    // --- the week ------------------------------------------------------------

    /**
     * Put my week up, as shape only.
     *
     * [Sharing.forWire] is the only way to build what this takes, so there is
     * no path into this function that still has a label attached.
     *
     * Replace rather than merge: the device is authoritative for my own week,
     * so what is up there afterwards is exactly what is on the phone, including
     * the blocks that were deleted.
     */
    suspend fun putWeek(blocks: List<Sharing.SharedBlock>): Boolean {
        val me = session.userId ?: return false
        call("$BASE/rest/v1/week_blocks?user_id=eq.$me", "DELETE", null) ?: return false
        val rows = SyncJson.weekRows(me, blocks)
        if (rows.length() == 0) return true
        return call("$BASE/rest/v1/week_blocks", "POST", rows.toString()) != null
    }

    /** Somebody else's week, if they have said yes. Null if not, or offline. */
    suspend fun weekOf(owner: UUID): List<Sharing.SharedBlock>? =
        array("$BASE/rest/v1/week_blocks?user_id=eq.$owner&select=*")?.let { SyncJson.blocks(it) }

    // --- the WhatsApp bot ----------------------------------------------------

    /**
     * The six characters that bind a WhatsApp number to this account.
     *
     * Minted by `whatsapp_code()` rather than here, so two devices signed into
     * one account cannot race each other into two codes, and asking twice gives
     * the same answer. Null when signed out, offline, or with no backend.
     */
    suspend fun whatsappCode(): String? {
        if (session.userId == null) return null
        val text = call("$BASE/rest/v1/rpc/whatsapp_code", "POST", "{}") ?: return null
        // PostgREST returns a scalar function's result as a bare JSON string,
        // quotes and all.
        return text.trim().trim('"').takeIf { it.isNotBlank() }
    }

    /**
     * Blocks the bot has parsed and not yet handed over.
     *
     * A queue, not a view: whatever comes back here is meant to be placed on
     * the week and then cleared with [clearInbox]. Empty and null are different
     * — nothing waiting, versus no answer — and only the caller can tell which
     * one it can act on.
     */
    suspend fun inbox(): List<SyncJson.Arrived>? {
        val me = session.userId ?: return null
        return array("$BASE/rest/v1/schedule_inbox?user_id=eq.$me&select=*&order=arrived_at")
            ?.let { SyncJson.inbox(it) }
    }

    /**
     * Drop the rows now that the phone has them.
     *
     * Deleted rather than flagged: the row has done its whole job once the
     * block is on the week, and a row that stays is a message the server is
     * keeping about somebody's timetable for no reason.
     */
    suspend fun clearInbox(ids: List<UUID>): Boolean {
        if (ids.isEmpty()) return true
        val list = ids.joinToString(",") { it.toString() }
        return call("$BASE/rest/v1/schedule_inbox?id=in.($list)", "DELETE", null) != null
    }

    // --- links ---------------------------------------------------------------

    /** Every link I am either end of. */
    suspend fun links(): List<Sharing.Link>? {
        val me = session.userId ?: return null
        return array("$BASE/rest/v1/links?or=(requester.eq.$me,addressee.eq.$me)&select=*")
            ?.let { SyncJson.links(it) }
    }

    /**
     * Ask somebody if I may see their week.
     *
     * Through `request_link` rather than an insert, because the client cannot
     * see other accounts and so cannot know the id to insert. The function
     * takes an address you must already have and gives back a link, never an
     * account.
     */
    suspend fun ask(email: String): Boolean =
        call(
            "$BASE/rest/v1/rpc/request_link",
            "POST",
            JSONObject().apply { put("target_email", email) }.toString(),
        ) != null

    /** Answer one that was put to me, or take back one I granted. */
    suspend fun answer(link: UUID, state: Sharing.LinkState): Boolean =
        call(
            "$BASE/rest/v1/rpc/answer_link",
            "POST",
            JSONObject().apply {
                put("link_id", link.toString())
                put("decision", state.name.lowercase())
            }.toString(),
        ) != null

    // --- the plumbing --------------------------------------------------------

    private suspend fun array(url: String): JSONArray? =
        call(url, "GET", null)?.let { runCatching { JSONArray(it) }.getOrNull() }

    /**
     * One request. Null on anything that is not a 2xx, including no network.
     *
     * Nothing is retried. A failed sync is a sync that happens next time, and a
     * retry loop in a coroutine nobody is waiting on is how an app ends up
     * hammering a server from a phone in a drawer.
     */
    private suspend fun call(
        url: String,
        method: String,
        body: String?,
        auth: Boolean = true,
        /** Sign with this instead of the stored one, mid sign-in. */
        token: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        var connection: HttpURLConnection? = null
        runCatching {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                // Short. A sync that has not answered in ten seconds has failed
                // as far as anybody looking at the screen is concerned, and
                // holding on longer only delays falling back to the device.
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("apikey", KEY)
                setRequestProperty(
                    "Authorization",
                    "Bearer " + (token ?: if (auth) session.accessToken ?: KEY else KEY),
                )
                setRequestProperty("Content-Type", "application/json")
                // Ask PostgREST for the rows back, so a write can be checked
                // without a second round trip.
                setRequestProperty("Prefer", "return=representation")
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val connected = connection!!
            val code = connected.responseCode
            val stream = if (code in 200..299) connected.inputStream else connected.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                // A PostgREST failure body names the policy that refused, which
                // is most of debugging RLS.
                Log.w(TAG, "$method $url -> $code $text")
                null
            } else {
                text
            }
        }.getOrElse {
            Log.i(TAG, "$method failed: ${it.javaClass.simpleName}")
            null
        }.also { connection?.disconnect() }
    }

    internal companion object {
        const val TAG = "HarborSync"

        /**
         * Where a provider sends the browser back to. Matches the intent
         * filter on the launcher activity in the manifest; change one and the
         * sign-in ends on a page that says the page cannot be found.
         */
        const val REDIRECT = "harbor://auth"
        val BASE = BuildConfig.SUPABASE_URL.trimEnd('/')
        val KEY = BuildConfig.SUPABASE_ANON_KEY
    }
}
