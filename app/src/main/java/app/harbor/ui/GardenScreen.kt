package app.harbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.harbor.data.HarborRepository
import app.harbor.domain.CallStats
import app.harbor.domain.Flowers
import app.harbor.domain.Garden
import app.harbor.domain.Growth
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Resolution
import app.harbor.ui.theme.QuietRow
import app.harbor.ui.theme.RowDivider
import app.harbor.ui.theme.SectionHeader
import app.harbor.ui.theme.SmallCopy

/**
 * The garden: one plot per person, one flower per call.
 *
 * The reward surface. It counts nothing and cannot be failed — a flower that
 * grew stays grown, which is what makes this a record of calls rather than a
 * score for them. See ADR-009 and docs/02.
 *
 * All geometry comes from [Garden], which is pure and tested against the
 * prototype's own JavaScript. This file only draws.
 */
/**
 * The garden as a page of its own: the field, and the same thing in words.
 *
 * The field is full bleed at the top and there is no 2D/3D switch — the camera
 * tips from overhead to landscape as you zoom, so the plan view is what you
 * get by pulling back, and every framing in between is a real one. A toggle
 * asked people to classify what they wanted before they could look, and the
 * honest answer is usually "somewhere between".
 *
 * ## Why the list is on the same screen
 *
 * The design had two of these: a scattered meadow, and a plain list of what
 * had happened, split between a young person's view and a parent's. That split
 * does not survive contact with the product — there is no parent side (ADR-007)
 * — and it was never really about who was looking. A field answers "is it
 * growing"; a list answers "what was that one". People want both, usually
 * within a few seconds of each other.
 *
 * So they are one screen, in that order: the field first because it is the
 * reward, the list under it because a scroll is a cheaper question than a
 * toggle. Neither is a mode, and there is only one screen to keep.
 *
 * The field keeps its own pan and zoom, so the two occupy separate bands
 * rather than nesting — a pinch inside a vertical scroller fights itself.
 */
@Composable
fun GardenScreen(store: HarborRepository, modifier: Modifier = Modifier) {
    val contacts by store.contacts.collectAsState()
    val settings by store.settings.collectAsState()
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }

    LaunchedEffect(Unit) { entries = store.recentEntries() }

    val grown = entries
        .filter { it.resolution == Resolution.CALLED && it.flower != null }
        .sortedByDescending { it.occurredAt }

    // What the three pictures above the list are counted from. Recomputed
    // when the span changes or the ledger arrives, and never during a draw:
    // see Growth, which is where all of the arithmetic lives.
    var span by remember { mutableStateOf(Growth.Span.WEEK) }
    val summary = remember(entries, span) {
        Growth.summarise(entries, span, java.time.LocalDate.now())
    }

    Column(modifier.fillMaxSize()) {
        // Standing at the newest flower rather than out at the overview:
        // this screen is opened right after growing one, and that is what
        // somebody has come to look at.
        //
        // A fixed band rather than half the screen.
        //
        // It used to take weight(1f) against the panel's 0.85f, which is
        // most of the phone, and left the deck below it cut off at the
        // bottom edge on open -- you arrived at this screen looking at the
        // top third of a card. The field is the reward and wants room, but
        // it is also pannable and zoomable, so somebody who wants more of it
        // has a way to get more of it; the deck does not, and a card you
        // cannot see the bottom of is a card you do not know is swipeable.
        Box {
            FieldCanvas(store, Modifier.fillMaxWidth().height(FIELD_BAND), standClose = true)
            // The resident bee lives on this field too. It is the same
            // garden; a bee that existed only on home would be a home
            // decoration rather than something living in the place.
            FieldBee(weather = settings.weather, fieldHeight = FIELD_BAND)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // The pictures first, then the list.
            //
            // Same argument as the field being above the list: the question
            // people arrive with is "how is it going", which a shape answers
            // in a glance, and the question they arrive at is "what was that
            // one", which only words can answer. Scrolling between them is
            // cheaper than choosing between them.
            GardenActivity(
                summary = summary,
                contacts = contacts,
                span = span,
                onSpan = { span = it },
            )
            Spacer(Modifier.height(30.dp))

            // One row per call, but the count is of flowers, which is what
            // the field above is showing.
            val bloomed = grown.sumOf { Flowers.flowerCount(it.callMinutes) }
            SectionHeader(
                "Every flower",
                if (grown.isEmpty()) "nothing yet" else "$bloomed so far",
            )
            if (grown.isEmpty()) {
                SmallCopy(
                    "When you have a call and say how it felt, it grows something " +
                        "here — and what it grew is written out underneath.",
                )
            }
            grown.forEachIndexed { index, entry ->
                if (index > 0) RowDivider()
                val who = contacts.firstOrNull { it.id == entry.contactId }?.label
                QuietRow(
                    text = inWords(entry, who),
                    meta = CallStats.formatDuration(entry.callMinutes),
                )
            }
        }
    }
}

/**
 * One flower, said out loud.
 *
 * The list's whole job: a row here has to mean something to somebody who has
 * never been told what a cosmos stands for.
 */
private fun inWords(entry: LedgerEntry, who: String?): String {
    val spec = entry.flower?.let { Flowers.spec(it) }
    val name = spec?.name ?: "Something"
    // The flower's own note, not a feeling. Nothing asks how a call felt any
    // more — picking the flower is that answer — and the note is what the
    // person was choosing when they picked it.
    val meaning = spec?.note?.removeSuffix(".")?.replaceFirstChar { it.lowercase() }
        ?: "a call"
    val date = entry.occurredAt.atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    return "$name — $meaning" + (who?.let { ", with $it" } ?: "") + ". $date"
}

/**
 * How much of the screen the field keeps on the garden page.
 *
 * Enough to be a place rather than a thumbnail, and little enough that the
 * whole of the first card -- title, picture, caption and the indicator that
 * says there are more -- is on screen when the page opens. Those two pull in
 * opposite directions and this is where they were balanced on a 6.7in phone;
 * a much shorter screen will still need a scroll to reach the indicator,
 * which is the right thing to give up first.
 */
private val FIELD_BAND = 210.dp
