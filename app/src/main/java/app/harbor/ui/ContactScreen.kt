package app.harbor.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.harbor.cue.Ringer
import app.harbor.data.HarborRepository
import app.harbor.domain.Contact
import app.harbor.domain.ContactKind
import app.harbor.domain.Tone
import app.harbor.ui.theme.Avatar
import app.harbor.ui.theme.AvatarSize
import app.harbor.ui.theme.Flow
import app.harbor.ui.theme.Notice
import app.harbor.ui.theme.PageIntro
import app.harbor.ui.theme.SectionHeading
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import app.harbor.ui.theme.pageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Who the cue is about — their name, number, ringtone, photo and colour.
 *
 * The sound and the photo are not decoration. The cue borrows a conditioned
 * response that already exists: her ringtone means her, and has for years.
 * That is the mechanism (ADR-009), which makes this screen part of the trigger
 * rather than a settings page.
 *
 * Nothing here costs a permission. The ringtone comes from the system picker
 * and the photo through ACTION_GET_CONTENT. Reading the address book would
 * need READ_CONTACTS, and a contacts prompt beside the activity one would cost
 * far more trust than it buys.
 */
@Composable
fun ContactScreen(
    store: HarborRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by store.contacts.collectAsState()
    val existing = contacts.firstOrNull()

    // Fixed for the life of the screen so the photo copy lands under the name
    // the saved contact will carry.
    val id = remember(existing?.id) { existing?.id ?: UUID.randomUUID() }

    var label by remember(existing) { mutableStateOf(existing?.label ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phoneE164 ?: "") }
    var soundRef by remember(existing) { mutableStateOf(existing?.cueSoundRef) }
    var photoRef by remember(existing) { mutableStateOf(existing?.photoRef) }
    var tone by remember(existing) { mutableStateOf(existing?.tone ?: Tone.GREEN) }
    var error by remember { mutableStateOf<String?>(null) }

    val previewRinger = remember { Ringer(context) }
    DisposableEffect(Unit) { onDispose { previewRinger.stop() } }

    val pickSound = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            soundRef = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                ?.toString()
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { picked ->
        if (picked != null) {
            scope.launch {
                photoRef = withContext(Dispatchers.IO) {
                    ContactPhotos.store(context, id, picked)
                }
            }
        }
    }

    val preview by produceState<ImageBitmap?>(null, photoRef) {
        val ref = photoRef
        value = if (ref == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(ref)).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            // No ground of its own: HarborShell paints the ground and the
            // dusk over it, and a second opaque background here covered
            // that gradient -- which is what made every screen read flat.
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.padding(horizontal = 28.dp)) {
            PageIntro(
                eyebrow = "The person, not the app",
                title = "Who would you call?",
                subtitle = "Their ringtone and their face are what make a reminder feel " +
                    "like them.",
            )
        }

        Flow(Modifier.pageContent()) {
            Surface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val bitmap = preview
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            // Named, unlike the drawn flowers and the bud: this
                            // is the one image in the app carrying information
                            // a screen reader user cannot get anywhere else on
                            // the screen -- whether the photo they chose is the
                            // one that stuck.
                            contentDescription = "The photo you chose for " +
                                label.ifBlank { "them" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(CircleShape),
                        )
                    } else {
                        Avatar(
                            label.ifBlank { "?" },
                            tone,
                            size = AvatarSize.LG,
                        )
                    }
                    Spacer(Modifier.size(16.dp))
                    Pill(
                        text = if (photoRef == null) "Choose a photo" else "Change photo",
                        selected = false,
                    ) { pickPhoto.launch("image/*") }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; error = null },
                    label = { Text("What do you call them?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.filter { c -> c.isDigit() || c == '+' }
                        error = null
                    },
                    label = { Text("Their number") },
                    supportingText = { Text("With the country code, like +919876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Surface {
                SectionHeading("Their sound")
                SmallCopy(
                    "Their real ringtone works best — it is the sound you already " +
                        "associate with them. A song that reminds you of them works too.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        text = if (soundRef == null) "Choose their sound" else "Change sound",
                        selected = soundRef != null,
                    ) {
                        pickSound.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_RINGTONE,
                                )
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Their sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    soundRef?.let(Uri::parse),
                                )
                            },
                        )
                    }
                    if (soundRef != null) {
                        Pill("Play", false) { previewRinger.start(soundRef) }
                        Pill("Stop", false) { previewRinger.stop() }
                    }
                }
            }

            Surface {
                SectionHeading("Their colour")
                SmallCopy("The ground their patch grows on.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tone.entries.forEach { option ->
                        Box(
                            Modifier
                                .clickable { tone = option }
                                .padding(2.dp),
                        ) {
                            Avatar(
                                if (option == tone) "OK" else " ",
                                option,
                                size = AvatarSize.MD,
                            )
                        }
                    }
                }
            }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.error,
                    ),
                )
            }

            Notice("Everything here stays on this phone. The photo is copied into Harbor.")

            TextLink("Save", onClick = {
                previewRinger.stop()
                val trimmedLabel = label.trim()
                val trimmedPhone = phone.trim()
                when {
                    trimmedLabel.isEmpty() ->
                        error = "A name helps — it is what the reminder will say."
                    !PHONE.matches(trimmedPhone) ->
                        error = "That does not look like a full number. Include the " +
                            "country code, like +919876543210."
                    else -> scope.launch {
                        store.upsertContact(
                            Contact(
                                id = id,
                                label = trimmedLabel,
                                phoneE164 = trimmedPhone,
                                kind = ContactKind.PERSON,
                                tone = tone,
                                cueSoundRef = soundRef,
                                photoRef = photoRef,
                            ),
                        )
                        onDone()
                    }
                }
            })
            TextLink("Back", onClick = { previewRinger.stop(); onDone() })
        }
    }
}

/** Matches the phone_e164 CHECK constraint in 0001_init.sql. */
private val PHONE = Regex("^\\+[1-9]\\d{7,14}$")
