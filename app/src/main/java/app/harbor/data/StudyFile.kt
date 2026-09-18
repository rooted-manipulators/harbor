package app.harbor.data

import android.content.Context
import android.provider.MediaStore
import android.os.Build
import android.content.ContentValues
import android.content.ContentUris
import app.harbor.domain.StudyExport
import app.harbor.sensing.Sensing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * The week's export, written without anybody being asked to.
 *
 * Handing the week over used to be a card in Account: a participant had to
 * remember, at the end of the study, to go and find it and save a file. That
 * asks the person being studied to do the study's own admin, and a week of
 * somebody's data is lost every time one of them forgets.
 *
 * So the file writes itself, whenever Harbor goes to the back, and it writes
 * itself twice.
 *
 * ## Why twice
 *
 * The first copy goes to the app's own external files directory. That was
 * meant to be collectable "over USB or from the Files app", and on Android 11
 * and later it is neither: `Android/data/<package>` is hidden from the Files
 * app and from MTP, so a phone handed back at the end of a week looks empty to
 * anybody without adb and a cable. A study whose data can only be recovered by
 * a developer with the handset in front of them is not a study with five
 * participants, it is five appointments.
 *
 * The second copy goes to the shared Downloads collection, which needs no
 * permission on API 29+, is the first place anybody looks, and can be sent on
 * from the Files app in two taps. Same bytes, same name, rewritten in place
 * rather than accumulating one file per pause.
 *
 * ## This is not a network
 *
 * Nothing here sends anything. Harbor still holds no `INTERNET` permission
 * (ADR-004) and this adds none — it writes a file to the phone it is running
 * on, and getting that file off the phone is still a deliberate act by
 * somebody holding it. The contents are exactly what [StudyExport] allows:
 * shapes, counts and timestamps, with the redactions that file documents.
 */
object StudyFile {

    /** Where a collected file will be, on any phone running the study build. */
    const val FOLDER = "study"

    /**
     * Rewrite the export.
     *
     * Cheap enough to do on every trip to the background: a week of beats is a
     * few hundred rows, and the whole file is tens of kilobytes. Failure is
     * swallowed on purpose — a study file that cannot be written is not a
     * reason to crash somebody's phone, and the next pause will try again.
     */
    suspend fun refresh(context: Context, store: HarborRepository): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bundle = StudyExport.Bundle(
                    participant = store.participantId(),
                    exportedAt = Instant.now(),
                    appVersion = versionOf(context),
                    settings = store.settings.value,
                    contacts = store.contacts.value,
                    blocks = store.weekBlocks.value,
                    beats = store.beats(),
                    cues = store.allCues(),
                    entries = store.recentEntries(),
                    arm = store.arm(),
                    studyCode = store.studyCode(),
                    lastTransitionAt = Sensing.lastTransition(context),
                )
                val dir = File(context.getExternalFilesDir(null), FOLDER).apply { mkdirs() }
                // One file, overwritten, rather than one per pause. The
                // participant id is in the name so a folder of them collected
                // from several phones can still be told apart.
                val file = File(dir, StudyExport.filename(bundle))
                val json = StudyExport.json(bundle)
                file.writeText(json)
                publish(context, file.name, json)
                file
            }.getOrNull()
        }

    /**
     * The copy somebody can actually find.
     *
     * Downloads, via MediaStore, which on API 29+ an app may write to without
     * asking for anything. Failure is swallowed for the same reason the rest
     * of this file swallows it: the private copy has already been written, and
     * a phone that will not take the second one is not a reason to crash.
     *
     * Updates the existing row rather than inserting, or a week of pauses
     * leaves a Downloads folder with four hundred files in it called
     * harbor-2026-09-14-b56e3d24 (1) … (400).
     */
    private fun publish(context: Context, name: String, json: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            val existing = resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )?.use { if (it.moveToFirst()) it.getLong(0) else null }

            val uri = if (existing != null) {
                ContentUris.withAppendedId(collection, existing)
            } else {
                resolver.insert(
                    collection,
                    ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    },
                )
            } ?: return

            resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
        }
    }

    /** Which build produced a file, so an odd export can be traced to a version. */
    private fun versionOf(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName} ($code)"
    }.getOrDefault("unknown")
}
