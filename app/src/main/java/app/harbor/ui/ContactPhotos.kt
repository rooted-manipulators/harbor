package app.harbor.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.UUID

/**
 * Keeping a picked photo where the cue can still find it.
 *
 * A URI from the photo picker grants read access that does not reliably
 * survive a reboot, and the cue may fire days after the photo was chosen. So
 * the image is copied into the app's own storage and that copy is what gets
 * referenced.
 *
 * This also makes the privacy claim on the explainer screen literally true:
 * the photo lives in Harbor's private directory on the phone and is never
 * uploaded. Only the path syncs, and a path is meaningless on another device.
 */
internal object ContactPhotos {

    /**
     * @return a `file://` URI for the stored copy, or null if the source could
     *   not be read — a photo on an unmounted volume, or one the user has
     *   since deleted.
     *
     * A URI rather than a bare path because the cue surface loads it through
     * `ContentResolver.openInputStream`, which needs a scheme.
     */
    fun store(context: Context, contactId: UUID, source: Uri): String? {
        val dir = File(context.filesDir, "contact-photos").apply { mkdirs() }
        val target = File(dir, "$contactId.jpg")

        return runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use(input::copyTo)
            } ?: return null
            Uri.fromFile(target).toString()
        }.onFailure {
            Log.w("HarborUi", "could not copy contact photo", it)
        }.getOrNull()
    }

    /** Removes a stored photo. Called when a contact is deleted. */
    /**
     * A stored photo, decoded, or null if it is gone.
     *
     * Both the onboarding flow and the contact screen were doing this inline.
     * It lives here because the failure it has to swallow is the same one
     * [store] documents: the file can be missing, the volume can be gone, and
     * a face that will not load is a reason to draw initials rather than a
     * reason to crash on a screen somebody is halfway through.
     */
    fun load(context: Context, ref: String): ImageBitmap? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(ref)).use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()

    fun delete(contactId: UUID, context: Context) {
        File(File(context.filesDir, "contact-photos"), "$contactId.jpg").delete()
    }
}
