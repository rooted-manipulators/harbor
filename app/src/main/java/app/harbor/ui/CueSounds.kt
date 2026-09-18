package app.harbor.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Keeping a chosen sound where the cue can still play it.
 *
 * ## The bug this exists to stop
 *
 * The ringtone picker will let somebody reach past the built-in ringtones and
 * choose a file — a song, a voice note, an mp4 off their own storage. It hands
 * back a `content://` URI and a read grant that belongs to *that moment*: the
 * activity that asked for it, still running, before the next reboot.
 *
 * A cue fires days later, from a broadcast, in a process that was not the one
 * that asked. By then the grant is gone. [app.harbor.cue.Ringer] catches the
 * `SecurityException`, logs it, and the reminder arrives in silence — which is
 * exactly how it was reported: "pick an mp4 and the cue has no sound".
 *
 * Nothing about that is visible when you choose the sound. It previews fine,
 * because the preview happens in the activity that still holds the grant. It
 * is the same shape as the contact-photo problem in [ContactPhotos], and it
 * has the same answer.
 *
 * ## The answer
 *
 * Copy it, and reference the copy. A file in Harbor's own directory needs no
 * grant, survives a reboot, and cannot be deleted out from under the cue by
 * somebody tidying their downloads.
 *
 * Copying also means a *decision* rather than a reference: the sound they
 * picked is the sound that plays, even if they later move or rename the
 * original. For a sound whose whole job is to make one person recognisable in
 * a second, that is the behaviour you want.
 */
internal object CueSounds {

    /**
     * Past this, keep the original URI rather than copy.
     *
     * A ringtone is a few megabytes. Somebody who picks a feature-length video
     * should not silently lose a third of a gigabyte of storage to Harbor, so
     * over the cap we hand back the URI unchanged and accept that it may not
     * survive — which is no worse than the behaviour this class replaces, and
     * only ever applies to a file nobody would choose as a ringtone.
     */
    private const val CAP_BYTES = 25L * 1024 * 1024

    /**
     * @return a `file://` URI for the stored copy, the source URI unchanged if
     *   it was too large to copy, or null if it could not be read at all.
     *
     * The extension is deliberately absent: MediaPlayer sniffs the container
     * rather than trusting a name, and an mp4 renamed `.mp3` would be a lie
     * told to nobody.
     */
    fun store(context: Context, contactId: UUID, source: Uri): String? {
        val dir = File(context.filesDir, "cue-sounds").apply { mkdirs() }
        val target = File(dir, contactId.toString())

        return runCatching {
            val size = context.contentResolver
                .openAssetFileDescriptor(source, "r")
                ?.use { it.length }
                ?: -1L
            if (size in 1..CAP_BYTES || size == -1L) {
                context.contentResolver.openInputStream(source)?.use { input ->
                    target.outputStream().use(input::copyTo)
                } ?: return null
                Uri.fromFile(target).toString()
            } else {
                // Too big to keep. Better a reference that might work than no
                // sound at all, and better than eating the storage.
                source.toString()
            }
        }.onFailure {
            Log.w("HarborUi", "could not copy cue sound", it)
        }.getOrNull()
    }

    /** Their sound, gone with them. */
    fun delete(contactId: UUID, context: Context) {
        runCatching { File(File(context.filesDir, "cue-sounds"), contactId.toString()).delete() }
    }
}
