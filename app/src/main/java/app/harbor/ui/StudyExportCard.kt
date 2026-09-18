package app.harbor.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.harbor.data.HarborRepository
import app.harbor.domain.StudyExport
import app.harbor.sensing.Sensing
import app.harbor.ui.theme.Notice
import app.harbor.ui.theme.SectionHeading
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Handing the week over.
 *
 * Harbor uploads nothing, so at the end of the study the data is on the phone
 * and nowhere else. This is the only way it leaves, and it leaves the way
 * everything else in this app does: because somebody chose to send it.
 *
 * The file is written through the system's own save dialog rather than into a
 * folder of ours. That is deliberate — the participant picks where it goes and
 * can look at it before passing it on, and Harbor never holds a copy of its
 * own export lying around.
 *
 * The list of what is left out is on the screen as well as inside the file.
 * The people in this study are being asked to let an app watch when they walk;
 * the least it can do is be specific about what it hands back.
 */
@Composable
fun StudyExportCard(store: HarborRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState()
    val contacts by store.contacts.collectAsState()
    val blocks by store.weekBlocks.collectAsState()

    var bundle by remember { mutableStateOf<StudyExport.Bundle?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(settings, contacts, blocks) {
        bundle = withContext(Dispatchers.IO) {
            StudyExport.Bundle(
                participant = store.participantId(),
                exportedAt = Instant.now(),
                appVersion = versionOf(context),
                settings = settings,
                contacts = contacts,
                blocks = blocks,
                beats = store.beats(),
                cues = store.allCues(),
                entries = store.recentEntries(),
                arm = store.arm(),
                lastTransitionAt = Sensing.lastTransition(context),
            )
        }
    }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val ready = bundle
        if (uri == null || ready == null) return@rememberLauncherForActivityResult
        scope.launch {
            failed = !withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(StudyExport.json(ready).toByteArray())
                    } ?: error("no stream")
                }.isSuccess
            }
            if (!failed) saved = StudyExport.filename(ready)
        }
    }

    val ready = bundle ?: return
    val counted = remember(ready) { StudyExport.summarise(ready) }

    Surface {
        SectionHeading("Hand over your week")
        SmallCopy(
            "Harbor never sends anything by itself. When the study is over, " +
                "save this file and give it to us — that is the whole of how " +
                "your week reaches anyone.",
        )

        SmallCopy(
            "${counted.cues} reminders · ${counted.calls} calls · " +
                "${counted.messages} lines · ${counted.dismissed} dismissed, " +
                "across ${counted.days} days.",
            size = 13,
        )

        Pill(text = "Save a copy", selected = true) { save.launch(StudyExport.filename(ready)) }

        saved?.let { SmallCopy("Saved as $it.", size = 13) }
        if (failed) {
            SmallCopy(
                "That did not save. Try somewhere else — Downloads is a safe " +
                    "choice — and nothing was sent either way.",
                size = 13,
            )
        }

        SectionHeading("What is not in it")
        StudyExport.OMITTED.forEach { Notice(it) }
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
