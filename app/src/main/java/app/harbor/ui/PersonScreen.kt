package app.harbor.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.harbor.cue.Dialer
import app.harbor.data.HarborRepository
import app.harbor.domain.CallStats
import app.harbor.domain.Flowers
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Resolution
import app.harbor.ui.theme.Avatar
import app.harbor.ui.theme.AvatarSize
import app.harbor.ui.theme.Eyebrow
import app.harbor.ui.theme.Flow
import app.harbor.ui.theme.QuietRow
import app.harbor.ui.theme.RowDivider
import app.harbor.ui.theme.SectionHeader
import app.harbor.ui.theme.SectionHeading
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import app.harbor.ui.theme.pageContent
import java.time.ZoneId
import java.util.UUID

/**
 * One person: their patch, their history, and the two ways to reach them.
 *
 * This is `conversation.tsx` reshaped rather than ported, per ADR-007. The
 * prototype's version is a two-way chat with a transcript — and there is no
 * second party in this build, so a transcript would be a wall of messages
 * nobody ever answered. Showing that would be worse than showing nothing.
 *
 * What is true instead: this is the person you call, this is what those calls
 * have grown, and here are the two ways to reach them. Every line on this
 * screen is something Harbor actually knows.
 */
@Composable
fun PersonScreen(
    store: HarborRepository,
    contactId: UUID?,
    onLeaveLine: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by store.contacts.collectAsState()
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }

    LaunchedEffect(Unit) { entries = store.recentEntries() }

    val person = contacts.firstOrNull { it.id == contactId } ?: contacts.firstOrNull()

    if (person == null) {
        Column(modifier.fillMaxSize().pageContent()) {
            SectionHeading("Nobody here yet.")
            SmallCopy("Add someone, and this becomes their page.")
            TextLink("Choose someone", onEdit)
        }
        return
    }

    val theirs = entries.filter { it.contactId == person.id }
    val calls = theirs.filter { it.resolution == Resolution.CALLED }
    val flowers = calls.mapNotNull { it.flower }
    val usual = CallStats.usualMinutes(entries, person.id)
    val dominant = CallStats.dominantFlower(entries, person.id)

    Column(
        modifier
            .fillMaxSize()
            // No ground of its own: HarborShell paints the ground and the
            // dusk over it, and a second opaque background here covered
            // that gradient -- which is what made every screen read flat.
            .verticalScroll(rememberScrollState()),
    ) {
        Flow(Modifier.pageContent()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(person.label, person.tone, size = AvatarSize.LG)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    SectionHeading(person.label)
                    SmallCopy(
                        usual?.let { "Calls run about " + CallStats.formatDuration(it) + "." }
                            ?: "No calls through Harbor yet.",
                    )
                }
                dominant?.let {
                    Box(Modifier.size(44.dp)) { FlowerMark(it, Modifier.fillMaxSize()) }
                }
            }

            Surface {
                Eyebrow("Their patch")
                Text(
                    when (flowers.size) {
                        0 -> "Nothing growing yet."
                        1 -> "One flower."
                        else -> flowers.size.toString() + " flowers."
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                dominant?.let {
                    SmallCopy(
                        "Mostly " + Flowers.spec(it).name.lowercase() +
                            " — " + Flowers.spec(it).note.lowercase(),
                    )
                }
            }

            // Two ways to reach them, and Harbor does neither itself: the
            // dialer places the call, and whatever they already use carries
            // the line.
            if (person.phoneE164 != null) {
            TextLink("Call " + person.label, onClick = {
                    Dialer.handOff(context, store, scope, person)
                })
            }
            TextLink("Leave a line", onLeaveLine)

            // The lines you have left this person, on this person's page.
            //
            // They were only ever visible on the screen that writes them,
            // which meant the one place you would go to think about somebody
            // - their page - showed their flowers and none of their words.
            // Same rows, same quotation marks, in front of the person they
            // were for.
            val lines = theirs
                .filter { it.resolution == Resolution.MESSAGE }
                .sortedByDescending { it.occurredAt }
            if (lines.isNotEmpty()) {
                SectionHeader("Lines you have left", "kept on this phone")
                lines.take(10).forEachIndexed { index, entry ->
                    if (index > 0) RowDivider()
                    QuietRow(
                        // A line left before Harbor kept the words, or a
                        // picture, has nothing to show but the fact of it.
                        text = entry.note?.takeIf { it.isNotBlank() }
                            ?.let { "\u201c$it\u201d" }
                            ?: "You sent something.",
                        meta = entry.occurredAt.atZone(ZoneId.systemDefault())
                            .toLocalDate().toString(),
                    )
                }
            }

            if (theirs.isNotEmpty()) {
                SectionHeading("Lately")
                theirs.sortedByDescending { it.occurredAt }.take(8).forEach { entry ->
                    Surface {
                        Eyebrow(
                            entry.occurredAt.atZone(ZoneId.systemDefault())
                                .toLocalDate().toString(),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.resolution.said,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                entry.callMinutes?.let {
                                    SmallCopy(CallStats.formatDuration(it), size = 13)
                                }
                            }
                            entry.flower?.let {
                                Box(Modifier.size(34.dp)) {
                                    FlowerMark(it, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }

            TextLink("Edit " + person.label, onEdit)
        }
    }
}

/** What a resolution amounts to, in the app's own voice. */
private val Resolution.said: String
    get() = when (this) {
        Resolution.CALLED -> "You called."
        Resolution.REACTED -> "You sent a little love."
        Resolution.MESSAGE -> "You left a line."
        Resolution.PLAYED -> "You played the daily question."
        Resolution.PROPOSED_LATER -> "You made room to talk later."
        Resolution.DISMISSED -> "You kept the quiet."
        // Never "you failed to reach them". They went to call, which is the
        // part this app is trying to encourage, and whether anyone picked up
        // is not something to be scored on.
        Resolution.NOT_REACHED -> "You tried. It did not happen."
    }
