package app.harbor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.harbor.data.HarborRepository
import app.harbor.domain.CallStats
import app.harbor.domain.Contact
import app.harbor.domain.FlowerKind
import app.harbor.domain.Flowers
import app.harbor.domain.Garden
import app.harbor.domain.Growth
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Resolution
import app.harbor.domain.Tone
import app.harbor.ui.theme.Gold
import app.harbor.ui.theme.QuietRow
import app.harbor.ui.theme.RowDivider
import app.harbor.ui.theme.SectionHeader
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.SurfaceGreen
import app.harbor.ui.theme.SurfaceOrange
import app.harbor.ui.theme.SurfaceSky

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
        FieldCanvas(store, Modifier.fillMaxWidth().weight(1f), standClose = true)

        Column(
            Modifier
                .fillMaxWidth()
                .weight(0.85f)
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

@Composable
fun GardenCanvas(store: HarborRepository, modifier: Modifier = Modifier) {
    val contacts by store.contacts.collectAsState()
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }

    LaunchedEffect(Unit) { entries = store.recentEntries() }

    val plots = remember(contacts) {
        contacts.mapIndexed { index, contact -> contact to Garden.plotFor(contact.id.toString(), index) }
    }

    // Calls with a flower, oldest first, so a flower's index — and therefore
    // its spot — never changes once planted.
    val flowersByContact = remember(entries) {
        entries
            .filter { it.resolution == Resolution.CALLED && it.flower != null }
            .sortedBy { it.occurredAt }
            .groupBy { it.contactId }
    }

    var camera by remember { mutableStateOf(Garden.Camera(0.0, 0.0, 1.0)) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.labelMedium
        .copy(color = MaterialTheme.colorScheme.onBackground)

    // Fit once the Canvas has a size, and again if the garden grows. Done in
    // a side effect rather than during the draw phase: assigning state while
    // drawing is how you get a recomposition loop.
    LaunchedEffect(viewport, plots.size) {
        if (viewport.width > 0 && viewport.height > 0) {
            camera = Garden.fitCamera(
                plots.map { it.second },
                viewport.width.toDouble(),
                viewport.height.toDouble(),
                // No sky wheel yet, so there is nothing for that band to hold.
                skyBand = 0.0,
            )
        }
    }

    val settings by store.settings.collectAsState()
    val sky = Sky.gradient(settings.weather)

    // The sky turns rather than jumping, so changing the weather reads as
    // time passing rather than a setting being applied.
    val turn by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(980),
        label = "sky-turn",
    )

    Box(
        modifier
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(sky.first, sky.second))),
    ) {
        if (plots.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Bare ground, for now.", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Add someone, and the first call you have plants the first flower.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            return@Box
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Zoom about the centroid so the ground under the
                        // fingers stays under them, then apply the pan.
                        val zoomed = Garden.zoomAt(
                            camera,
                            centroid.x.toDouble(),
                            centroid.y.toDouble(),
                            zoom.toDouble(),
                        )
                        camera = zoomed.copy(
                            x = zoomed.x + pan.x,
                            y = zoomed.y + pan.y,
                        )
                    }
                },
        ) {
            // Behind everything: the wheel of weathers, and the faint ring
            // they ride on.
            with(Sky) {
                drawRing()
                drawWheel(settings.weather, turn)
            }

            translate(camera.x.toFloat(), camera.y.toFloat()) {
                scale(camera.k.toFloat(), pivot = Offset.Zero) {
                    // Painter's order: plots further back are drawn first, so
                    // nearer ones overlap them the way an isometric scene
                    // should.
                    plots.sortedBy { it.second.depth }.forEach { (contact, plot) ->
                        drawPlot(
                            plot = plot,
                            contact = contact,
                            // One flower a minute here as well, so the plot
                            // view and the field agree about how much grew.
                            flowers = flowersByContact[contact.id].orEmpty()
                                .flatMap { entry ->
                                    val kind = entry.flower
                                    if (kind == null) emptyList()
                                    else List(Flowers.flowerCount(entry.callMinutes)) { kind }
                                },
                            detailed = camera.k >= Garden.DETAIL_ZOOM,
                        )
                    }
                }
            }

            // Names are drawn outside the camera transform, in screen space.
            // Inside it they were multiplied by the zoom, which at a normal
            // fit made them larger than the plots they labelled.
            plots.forEach { (contact, plot) ->
                val label = measurer.measure(contact.label, nameStyle)
                val x = (plot.x * camera.k + camera.x).toFloat() - label.size.width / 2f
                val y = ((plot.y + plot.radius * Garden.GROUND_SQUASH) * camera.k + camera.y)
                    .toFloat() + 8f
                drawText(textLayoutResult = label, topLeft = Offset(x, y))
            }

            val veil = Sky.veil(settings.weather)
            if (veil != Color.Transparent) drawRect(veil)
        }
    }
}

private fun DrawScope.drawPlot(
    plot: Garden.Plot,
    contact: Contact,
    flowers: List<FlowerKind>,
    detailed: Boolean,
) {
    translate(plot.x.toFloat(), plot.y.toFloat()) {
        // The ground: a closed Catmull-Rom loop around a noisy radius, so a
        // plot is organic and never a circle — and always the same shape for
        // this person.
        val ground = blobPath(Garden.blobPoints(plot.seed, plot.radius))
        scale(1f, Garden.GROUND_SQUASH.toFloat(), pivot = Offset.Zero) {
            drawPath(ground, color = toneOf(contact))
        }

        if (flowers.isEmpty()) return@translate

        if (detailed) {
            flowers.forEachIndexed { index, kind ->
                val spot = Garden.flowerSpot(plot.seed, index, plot.radius)
                translate(spot.x.toFloat(), spot.y.toFloat()) {
                    drawFlowerDot(Flowers.spec(kind), radius = 11f)
                }
            }
        } else {
            // Zoomed out, one flower stands for the patch — otherwise a busy
            // garden turns to mush at a distance.
            val dominant = flowers.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            if (dominant != null) {
                drawFlowerDot(Flowers.spec(dominant), radius = 18f)
            }
        }
    }
}

/**
 * A closed Catmull-Rom loop through the given points, as cubic segments.
 *
 * The control-point formula is the prototype's, so the outline drawn here is
 * the outline drawn on the web.
 */
private fun blobPath(points: List<Garden.Spot>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    val n = points.size
    moveTo(points[0].x.toFloat(), points[0].y.toFloat())

    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]

        cubicTo(
            (p1.x + (p2.x - p0.x) / 6).toFloat(), (p1.y + (p2.y - p0.y) / 6).toFloat(),
            (p2.x - (p3.x - p1.x) / 6).toFloat(), (p2.y - (p3.y - p1.y) / 6).toFloat(),
            p2.x.toFloat(), p2.y.toFloat(),
        )
    }
    close()
}

/** Plot colours come from the palette, not from numbers invented here. */
private fun toneOf(contact: Contact): Color = when (contact.tone) {
    Tone.GREEN -> SurfaceGreen
    Tone.GOLD -> Gold
    Tone.ORANGE -> SurfaceOrange
    Tone.SKY -> SurfaceSky
}
