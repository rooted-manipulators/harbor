package app.harbor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.domain.Contact
import app.harbor.domain.FlowerKind
import app.harbor.domain.Flowers
import app.harbor.domain.Growth
import app.harbor.ui.theme.Avatar
import app.harbor.ui.theme.AvatarSize
import app.harbor.ui.theme.CardEdge
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Glass
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.SmallCopy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Your activity: three pictures of what grew, and nothing invented.
 *
 * ## The rule these were built under
 *
 * Every mark on this screen is a flower somebody actually grew. No sample
 * data, no filler, no placeholder arrangement that gets replaced by real
 * numbers later — a garden that shows you a handsome bouquet you did not earn
 * is the same lie as a streak, and this app has spent a lot of decisions not
 * telling it. Where there is nothing, these say so in words.
 *
 * The counting is [Growth], which is pure and tested. This file only draws,
 * the same division the field and [app.harbor.domain.Garden] already keep.
 *
 * ## Why three, and why these three
 *
 * They answer different questions and are deliberately not three versions of
 * one chart:
 *
 *  - **Most grown** — one page per person, their kinds sized by how much of
 *    each grew. *What does this relationship mostly feel like.*
 *  - **Everything you grew** — every flower of the span in one disc. *How
 *    much, and in what colours.*
 *  - **People you grow with** — one composite bloom each, a petal per call,
 *    coloured by kind. *Who, how often, and what mix.* No bar and no ring:
 *    the person is the flower.
 *
 * ## Why they are drawn and not the artwork
 *
 * `FlowerMark` shows the illustrator's files and is right wherever one flower
 * is the subject. These are forty marks at fourteen dp, where a decoded
 * 448-pixel bitmap costs half a megabyte to show something the size of a
 * fingernail. [drawBloom] is the same geometry [drawFlowerDot] uses for the
 * plan view, for the same reason and with the same trade: the colours and the
 * petal count still come off the flower sheet, through [Flowers.spec].
 */
@Composable
fun GardenActivity(
    summary: Growth.Summary,
    contacts: List<Contact>,
    span: Growth.Span,
    onSpan: (Growth.Span) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        ActivityHeader(summary, span, onSpan)

        if (summary.isEmpty) {
            Spacer(Modifier.height(14.dp))
            SmallCopy(
                when (span) {
                    Growth.Span.WEEK -> "Nothing grew this week. These fill in as you call."
                    Growth.Span.MONTH -> "Nothing grew this month. These fill in as you call."
                    Growth.Span.ALL ->
                        "Nothing yet. Have a call, say how it felt, and this is where " +
                            "it turns up."
                },
            )
            return@Column
        }

        Spacer(Modifier.height(22.dp))
        MostGrown(summary, contacts)

        Spacer(Modifier.height(26.dp))
        EverythingYouGrew(summary)

        Spacer(Modifier.height(26.dp))
        PeopleYouGrowWith(summary, contacts)
    }
}

// --- the header and its span picker --------------------------------------

@Composable
private fun ActivityHeader(
    summary: Growth.Summary,
    span: Growth.Span,
    onSpan: (Growth.Span) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Text(
        "Your activity",
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
            color = Chalk,
        ),
    )
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Glass)
                .border(1.dp, CardEdge, RoundedCornerShape(99.dp))
                .clickable { open = !open }
                .padding(start = 14.dp, end = 11.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                span.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    color = Chalk,
                ),
            )
            Spacer(Modifier.width(7.dp))
            Canvas(Modifier.size(9.dp)) {
                // A chevron rather than a glyph from a font, for the reason
                // every other mark in this app is drawn: it takes the ink
                // colour and stays sharp at nine dp.
                val w = size.width
                val h = size.height
                drawLine(Muted, Offset(0f, h * 0.3f), Offset(w / 2f, h * 0.8f), 1.6.dp.toPx())
                drawLine(Muted, Offset(w, h * 0.3f), Offset(w / 2f, h * 0.8f), 1.6.dp.toPx())
            }
        }
    }

    if (open) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Growth.Span.entries.forEach { option ->
                val chosen = option == span
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        color = if (chosen) Chalk else Muted,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (chosen) Glass else Color.Transparent)
                        .clickable { onSpan(option); open = false }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    // The days something actually happened on, not the window's edges. See
    // Growth.summarise: a week with one call on Friday says Friday.
    Text(
        summary.from?.let { "${stamp(it)} – ${stamp(summary.to)}" } ?: "nothing yet",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Muted),
    )
}

private fun stamp(date: java.time.LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy"))

// --- 1. most grown, one page per person ----------------------------------

@Composable
private fun MostGrown(summary: Growth.Summary, contacts: List<Contact>) {
    CardLabel("Most grown")
    Spacer(Modifier.height(10.dp))

    val people = summary.people
    val pager = rememberPagerState { people.size }

    HorizontalPager(
        state = pager,
        pageSpacing = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        val person = people[page]
        val who = contacts.firstOrNull { it.id == person.contactId }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (who != null) {
                    Avatar(who.label, who.tone, size = AvatarSize.SM)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    who?.label ?: "Someone",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        color = Chalk,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            SmallCopy(
                person.flowers.let { if (it == 1) "1 flower" else "$it flowers" } +
                    ", from " +
                    person.calls.let { if (it == 1) "1 call" else "$it calls" },
                size = 12,
            )
            Spacer(Modifier.height(10.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    // Wider than it was. At 1.35 a person with one kind sat
                    // in a tall well of empty card, which read as something
                    // failing to load rather than as one flower.
                    .aspectRatio(1.7f),
            ) {
                drawCluster(person.blooms.take(MOST_GROWN_KINDS))
            }
        }
    }

    if (people.size > 1) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(people.size) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pager.currentPage) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (index == pager.currentPage) Chalk else Muted),
                )
            }
        }
    }
}

/**
 * One person's kinds, each sized by how much of it grew.
 *
 * Area, not diameter. Doubling a count doubles the ink, which is the only
 * scaling a reader's eye reads correctly — sizing by radius would show twice
 * as many flowers as four times the bloom and overstate every difference on
 * the card.
 *
 * The arrangement is fixed rather than random: biggest just right of centre,
 * the rest around it in a set order. A picture that rearranged itself between
 * openings would read as new information every time, when nothing had
 * changed.
 */
private fun DrawScope.drawCluster(blooms: List<Growth.Bloom>) {
    if (blooms.isEmpty()) return
    val top = blooms.first().flowers.toFloat().coerceAtLeast(1f)
    val unit = min(size.width, size.height)

    val placed = blooms.mapIndexed { index, bloom ->
        val share = sqrt(bloom.flowers / top)
        val seat = SEATS[index % SEATS.size]
        Triple(bloom.kind, seat, unit * (0.11f + 0.24f * share))
    }

    // Centre the posy on what is actually in it.
    //
    // The seats are fixed so the picture is stable, but a person with one
    // kind only ever uses the first seat -- which is off-centre, because it
    // is the seat that looks right when there are five. Centring the used
    // ones puts a lone flower in the middle of the card without moving
    // anything when the card is full.
    val cx = (placed.minOf { it.second.first } + placed.maxOf { it.second.first }) / 2f
    val cy = (placed.minOf { it.second.second } + placed.maxOf { it.second.second }) / 2f

    placed.forEach { (kind, seat, radius) ->
        translate(
            size.width / 2f + (seat.first - cx) * unit,
            size.height / 2f + (seat.second - cy) * unit,
        ) {
            drawBloom(Flowers.spec(kind), radius)
        }
    }
}

/**
 * Where the first five sit, as fractions of the card's short side.
 *
 * Hand-placed so the cluster reads as a posy rather than a chart: the largest
 * takes the right of centre, the next two tuck under and left, and the
 * remainder fill outward. Overlap is intended — flowers in a bunch overlap.
 */
private val SEATS = listOf(
    0.10f to -0.04f,
    -0.16f to -0.13f,
    -0.09f to 0.17f,
    0.28f to 0.16f,
    -0.30f to 0.06f,
)

private const val MOST_GROWN_KINDS = 5

// --- 2. everything, as one disc ------------------------------------------

@Composable
private fun EverythingYouGrew(summary: Growth.Summary) {
    CardLabel("Everything you grew")
    Spacer(Modifier.height(10.dp))
    Card {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f),
        ) {
            drawDisc(summary.everything)
        }
        Spacer(Modifier.height(10.dp))
        SmallCopy(
            buildString {
                append(if (summary.flowers == 1) "1 flower" else "${summary.flowers} flowers")
                append(", ")
                append(if (summary.calls == 1) "1 call" else "${summary.calls} calls")
                append(", ")
                val kinds = summary.everything.size
                append(if (kinds == 1) "1 kind" else "$kinds kinds")
                if (summary.flowers > DISC_MAX) append(". Showing $DISC_MAX of them")
            },
            size = 12,
        )
    }
}

/**
 * Every flower of the span, packed into a disc.
 *
 * Phyllotaxis — golden angle, radius as the square root of the index — which
 * is the arrangement a real seed head uses and the only one that fills a
 * circle evenly at any count. A grid would have to pick a width, and every
 * width is wrong at some number of flowers.
 *
 * Drawn in the order the kinds are counted, largest group first, so the
 * dominant colour occupies the middle and the rarer ones ring it. That is not
 * decoration: it means the centre of the disc is the answer to *what did this
 * month mostly feel like*, readable without counting anything.
 *
 * Capped at [DISC_MAX] marks. Past that the disc stops being a picture of a
 * number and starts being a texture, and the caption says the true total
 * rather than letting the drawing imply it.
 */
private fun DrawScope.drawDisc(blooms: List<Growth.Bloom>) {
    val marks = blooms.flatMap { bloom -> List(bloom.flowers) { bloom.kind } }.take(DISC_MAX)
    if (marks.isEmpty()) return

    val unit = min(size.width, size.height) / 2f

    // A breath of light under the cluster, tinted by whatever grew most.
    //
    // It is doing a job, not decorating: against a near-black card the
    // marks float with no ground, and the disc reads as scattered rather
    // than as one thing. Faint enough that it cannot be mistaken for a
    // mark of its own.
    val glow = Color(blooms.first().kind.let { Flowers.spec(it).petal })
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = unit * 0.95f,
        ),
        radius = unit * 0.95f,
        center = Offset(size.width / 2f, size.height / 2f),
    )
    // Room for a mark's own radius at the rim, so the outermost flowers are
    // inside the card rather than clipped by it.
    val spread = unit * 0.80f
    val mark = (unit * 0.30f / sqrt(marks.size.toFloat())).coerceIn(unit * 0.045f, unit * 0.16f)

    translate(size.width / 2f, size.height / 2f) {
        marks.forEachIndexed { index, kind ->
            // The golden angle. Any other turn and the marks fall into
            // visible spokes.
            val angle = index * 2.399963f
            val r = spread * sqrt((index + 0.5f) / marks.size)
            translate(r * cos(angle), r * sin(angle)) {
                drawBloom(Flowers.spec(kind), mark)
            }
        }
    }
}

private const val DISC_MAX = 90

// --- 3. a person, as a flower --------------------------------------------

@Composable
private fun PeopleYouGrowWith(summary: Growth.Summary, contacts: List<Contact>) {
    CardLabel("People you grow with")
    Spacer(Modifier.height(10.dp))
    Card {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            summary.people.take(PEOPLE_SHOWN).forEach { person ->
                val who = contacts.firstOrNull { it.id == person.contactId }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        translate(size.width / 2f, size.height / 2f) {
                            drawPersonBloom(person.petals, min(size.width, size.height) * 0.38f)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (who != null) Avatar(who.label, who.tone, size = AvatarSize.SM)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        who?.label ?: "Someone",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = Chalk,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            // Keeps two people on the left rather than stretched across the
            // card. A row of one enormous flower reads as a hero image, not
            // as one of several.
            repeat((PEOPLE_SHOWN - summary.people.size).coerceAtLeast(0)) {
                Spacer(Modifier.weight(1f))
            }
        }
        if (summary.people.size > PEOPLE_SHOWN) {
            Spacer(Modifier.height(12.dp))
            SmallCopy("and ${summary.people.size - PEOPLE_SHOWN} more", size = 12)
        }
    }
}

/**
 * One person as a single composite bloom: a petal per call, coloured by what
 * that call grew.
 *
 * This is the one with no bar and no ring in it. The count *is* the petal
 * count and the breakdown *is* the colours, so there is nothing left over to
 * put an axis on — somebody who called four times and felt three different
 * things has a four-petal flower in three colours, and that sentence and the
 * picture carry exactly the same information.
 *
 * Petals go on in call order, oldest first and clockwise from the top, so the
 * flower has a direction: the newest call is the petal just before the one
 * you started at. Nothing in the UI says so, and nobody needs to know it for
 * the picture to work — it is there so the shape is stable as calls are
 * added, rather than reshuffling the colours every time.
 *
 * Above [PETAL_MAX] the petals stop being countable, so they stop being added
 * and the caption on the card carries the number instead. A ninety-petal
 * flower is a disc.
 */
private fun DrawScope.drawPersonBloom(petals: List<FlowerKind>, radius: Float) {
    if (petals.isEmpty()) return
    val shown = petals.takeLast(PETAL_MAX)
    val n = shown.size

    // Narrow enough to read as petals. Wider than this and four of them
    // merge into a disc with a notch in it, which is what the first version
    // drew: the count stops being countable, and the count is the whole
    // point of this one.
    val slice = 360f / n.coerceAtLeast(3)
    val half = (slice * 0.46f * PI / 180f).toFloat()
    val rx = (radius * 0.30f).coerceAtMost(radius * sin(half) * 1.35f)

    // One petal points somewhere, so a bloom made of one is all on one side
    // of its own centre and hangs in the card like a balloon. Shifted back
    // by half its own reach, which centres the shape without pretending
    // there is more than one of it. From three up the petals balance each
    // other and nothing moves.
    val lift = if (n == 1) radius * 0.45f else 0f

    translate(0f, lift) {
        shown.forEachIndexed { index, kind ->
            val spec = Flowers.spec(kind)
            rotate(degrees = index * (360f / n), pivot = Offset.Zero) {
                drawOval(
                    color = Color(spec.petal),
                    topLeft = Offset(-rx, -radius),
                    size = Size(rx * 2f, radius * 1.18f),
                )
                // The deep tone as a seam down the middle of each petal. It
                // is what stops two neighbouring petals of the same kind
                // reading as one wide blob, which is most of what a
                // four-call flower is.
                drawOval(
                    color = Color(spec.petalDeep),
                    topLeft = Offset(-rx * 0.34f, -radius * 0.94f),
                    size = Size(rx * 0.68f, radius * 0.92f),
                    alpha = 0.5f,
                )
            }
        }

        val heart = shown.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        if (heart != null) {
            drawCircle(Color(Flowers.spec(heart).heart), radius * 0.26f, Offset.Zero)
        }
    }

}

private const val PEOPLE_SHOWN = 2
private const val PETAL_MAX = 16

// --- the shared marks ----------------------------------------------------

/**
 * One flower, drawn.
 *
 * The same construction as [drawFlowerDot] — the plan view's rosette — but
 * opaque and a little fuller, because here it is the subject rather than a
 * dot standing for one. Petal count, petal colour and heart all come from
 * [Flowers.spec], so a flower is the same flower here as it is in the field.
 */
private fun DrawScope.drawBloom(spec: app.harbor.domain.FlowerSpec, radius: Float) {
    val rx = radius * 0.42f
    val ry = radius * 0.62f
    val lift = radius * 0.38f
    for (i in 0 until spec.petals) {
        rotate(degrees = i * 360f / spec.petals, pivot = Offset.Zero) {
            drawOval(
                color = Color(spec.petal),
                topLeft = Offset(-rx, -lift - ry),
                size = Size(rx * 2f, ry * 2f),
            )
        }
    }
    drawCircle(Color(spec.heart), radius * 0.30f, Offset.Zero)
}

@Composable
private fun CardLabel(text: String) = Text(
    text,
    modifier = Modifier.fillMaxWidth(),
    style = MaterialTheme.typography.labelLarge.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = Muted,
    ),
    textAlign = TextAlign.Center,
)

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(Glass)
        .border(1.dp, CardEdge, RoundedCornerShape(22.dp))
        .padding(horizontal = 18.dp, vertical = 18.dp),
    content = content,
)
