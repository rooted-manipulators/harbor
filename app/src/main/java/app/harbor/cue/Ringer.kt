package app.harbor.cue

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.media.RingtoneManager
import android.util.Log

/**
 * Plays the contact's sound while the cue is on screen.
 *
 * The sound is the mechanism, not the decoration — see ADR-009. A generic
 * chime carries no association with anyone; the ringtone the user hears when
 * their mother actually calls carries years of it. That conditioned response
 * is what the cue is borrowing.
 *
 * It loops, like a call would, and stops the instant the surface goes away by
 * any route.
 */
internal class Ringer(private val context: Context) {

    private var player: MediaPlayer? = null

    /**
     * @param soundRef the contact's chosen sound, or null to fall back to the
     *   device's ringtone. Falling back is right: a cue with no sound at all
     *   would be missed entirely, which is worse than a cue that sounds
     *   generic.
     */
    fun start(soundRef: String?) {
        stop()

        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val uri: Uri = soundRef?.let(Uri::parse) ?: fallback ?: return

        // Their sound, and then the phone's if theirs will not play.
        //
        // The comment above this function has always said a cue with no sound
        // would be missed entirely, and that this is worse than one that sounds
        // generic. The code did not do that: a chosen sound that failed was
        // logged and the reminder arrived in silence. Which is the failure that
        // matters, because the sounds most likely to fail are the ones somebody
        // went out of their way to choose.
        player = play(uri) ?: run {
            if (uri != fallback && fallback != null) play(fallback) else null
        }
    }

    private fun play(uri: Uri): MediaPlayer? =
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // RINGTONE rather than NOTIFICATION so it follows the
                        // ringer volume and the phone's silent mode. Someone
                        // who has silenced their phone has already told us
                        // they do not want to be reached.
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure {
            // A picked song can be deleted, or on a shared storage volume that
            // is no longer mounted, or -- the common one before CueSounds --
            // behind a read grant that expired with the activity that asked
            // for it.
            Log.w(TAG, "could not play cue sound", it)
        }.getOrNull()

    fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
    }

    private companion object {
        const val TAG = "HarborCue"
    }
}
