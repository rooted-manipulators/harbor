package app.harbor.data

import android.content.Context
import java.time.Instant
import java.util.UUID

/**
 * The signed-in account, across restarts.
 *
 * Separate from [HarborStore]'s preferences file on purpose. Everything in that
 * one is the user's own record and goes into the study export; none of this
 * does, and a token that turned up in a file a researcher opens would be a
 * credential leak by filing error.
 *
 * ## On storing a token in preferences
 *
 * It is not encrypted. On a non-rooted device an app's private directory is
 * readable only by that app, which is the same protection the ledger already
 * relies on, and a refresh token here is worth strictly less than the ledger
 * next to it — it grants somebody's schedule shape, not their call history.
 * `EncryptedSharedPreferences` would add a dependency and protect against an
 * attacker who already has root, at which point both files are theirs anyway.
 *
 * Worth revisiting if this ever holds anything but a session.
 */
internal class SessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("harbor.session", Context.MODE_PRIVATE)

    val accessToken: String? get() = prefs.getString(ACCESS, null)

    val refreshToken: String? get() = prefs.getString(REFRESH, null)

    val userId: UUID?
        get() = prefs.getString(USER, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    /**
     * Whether the access token is past its stated life.
     *
     * Read by the caller before a sync so it can refresh; nothing here acts on
     * it. A minute of slack, so a token that expires mid-request is treated as
     * expired before the request rather than after it.
     */
    val stale: Boolean
        get() {
            val at = prefs.getLong(EXPIRES, 0L)
            return at == 0L || Instant.now().plusSeconds(60).toEpochMilli() > at
        }

    fun save(session: SyncJson.Session, expiresAt: Instant) {
        prefs.edit()
            .putString(ACCESS, session.accessToken)
            .putString(REFRESH, session.refreshToken)
            .putString(USER, session.userId.toString())
            .putLong(EXPIRES, expiresAt.toEpochMilli())
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val ACCESS = "access"
        const val REFRESH = "refresh"
        const val USER = "user"
        const val EXPIRES = "expires"
    }
}
