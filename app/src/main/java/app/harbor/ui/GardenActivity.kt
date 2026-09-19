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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import app.harbor.ui.theme.LocalReducedMotion
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.SpanPicked
import app.harbor.ui.theme.rememberTick
import kotlinx.coroutines.launch
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
            Spacer(Modifier.height(18.dp))
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

        Spacer(Modifier.height(18.dp))
        Deck(summary, contacts)
    }
}

/**
 * The three pictures as one deck of cards, swiped rather than scrolled.
 *
 * ## Why a pager and not a column
 *
 * They were stacked, and stacking made them a list of charts you scrolled
 * past on the way to something else. One at a time, at a size that fills the
 * space, they are three separate looks at the same week and you arrive at
 * each of them on purpose. It also means the card can be as tall as it wants
 * without pushing the list of calls off the bottom of the world.
 *
 * ## Why the pages are flat
 *
 * "Most grown" is per person, so the obvious shape is a pager of people
 * inside a pager of cards. Nested horizontal pagers are a gesture fight --
 * the inner one eats the swipe and the outer one only moves when you happen
 * to be on an edge page, which is the sort of thing that feels broken
 * without anybody being able to say why. So the people are unrolled into the
 * same deck: one page each, then the disc, then the bloom of blooms. Every
 * swipe does one thing and the indicator tells the truth about how many
 * there are.
 */
@Composable
private fun Deck(summary: Growth.Summary, contacts: List<Contact>) {
    val pages = remember(summary) {
        buildList {
            summary.people.forEach { add(Page.OnePerson(it)) }
            add(Page.Everything)
            if (summary.people.size > 1) add(Page.Together)
        }
    }
    val pager = rememberPagerState { pages.size }
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val stillness = LocalReducedMotion.current

    // The buzz on arrival, not on the first composition.
    //
    // settledPage changes once per completed swipe, which is the moment the
    // card has landed -- buzzing on currentPage would fire halfway through
    // the drag, while the thing you are being told about has not happened
    // yet. Skipped for the initial value so opening the screen is silent.
    var landed by remember { mutableIntStateOf(pager.settledPage) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != landed) {
            landed = pager.settledPage
            tick()
        }
    }

    HorizontalPager(
        state = pager,
        pageSpacing = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) { index ->
        // getOrNull, not [index]. The deck is rebuilt when the span
        // changes and can get shorter -- all time with two people is four
        // pages, this week with one is three -- and a pager that is on its
        // last page when that happens asks for an index that has just
        // stopped existing. PagerState clamps itself a frame later; this is
        // that frame, and the alternative to a blank card is a crash in
        // front of somebody.
        val page = pages.getOrNull(index) ?: return@HorizontalPager
        Card(Modifier.height(CARD_HEIGHT)) {
            when (page) {
                is Page.OnePerson -> MostGrown(page.person, contacts)
                Page.Everything -> EverythingYouGrew(summary)
                Page.Together -> PeopleYouGrowWith(summary, contacts)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Ticks(
        count = pages.size,
        current = pager.currentPage,
        onPick = {
            scope.launch {
                // Tapping a tick is a jump either way; for somebody who has
                // asked for less movement it should be the jump and not the
                // journey. Swiping is still a swipe -- that motion is the
                // finger's, not ours.
                if (stillness) pager.scrollToPage(it) else pager.animateScrollToPage(it)
            }
        },
    )

    Spacer(Modifier.height(18.dp))
    // The headline number, under the deck rather than on a card, because it
    // is true of all of them.
    Text(
        if (summary.flowers == 1) "1 flower" else "${summary.flowers} flowers",
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 24.sp,
            fontWeight = FontWeight.Normal,
            color = Muted,
        ),
    )
}

/** What one card in the deck is showing. */
private sealed interface Page {
    data class OnePerson(val person: Growth.Person) : Page
    data object Everything : Page
    data object Together : Page
}

/**
 * How tall a card is, for every page.
 *
 * Fixed rather than measured. A pager sizes itself to the page being shown,
 * so pages of different heights make the whole panel -- and everything below
 * it -- jump on each swipe, which is the one thing a deck must not do.
 *
 * 300, down from 430. The number is not a taste: it is what is left of a
 * 6.7in screen after the field's band, this panel's own header, the
 * indicator and the count beneath it -- so the whole first card, including
 * the thing that tells you there are more, is visible the moment the page
 * opens. A card whose bottom is off screen is a card nobody discovers they
 * can swipe.
 */
private val CARD_HEIGHT = 300.dp

/**
 * Where you are in the deck, and a way to get somewhere else.
 *
 * A tall pill for here and short ticks for the rest, which says *this one of
 * these* at a glance without asking anybody to count dots. Each one is its
 * own target: the visible tick is 3dp wide and would be an unfair thing to
 * ask a thumb for, so the touch area around it is padded out to the minimum
 * that can be hit reliably while the mark stays small.
 */
@Composable
private fun Ticks(count: Int, current: Int, onPick: (Int) -> Unit) {
    if (count <= 1) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val here = index == current
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable { onPick(index) }
                    .padding(horizontal = 7.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(if (here) 6.dp else 3.dp)
                        .height(if (here) 26.dp else 16.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (here) Chalk else Muted.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

// --- the header and its span picker --------------------------------------

@Composable
private fun ActivityHeader(
    summary: Growth.Summary,
    span: Growth.Span,
    onSpan: (Growth.Span) -> Unit,
) {
    val tick = rememberTick()

    Text(
        "Your activity",
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
            color = Chalk,
        ),
    )
    Spacer(Modifier.height(14.dp))

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Three segments rather than a menu.
        //
        // It was a chip that opened a row of three. Two taps to answer a
        // question with three answers, where all three fit on the line the
        // chip was sitting on -- and the menu hid which options existed
        // until you asked. A segmented control is the same pixels and says
        // everything up front.
        Row(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Glass)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Growth.Span.entries.forEach { option ->
                val chosen = option == span
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (chosen) SpanPicked else Color.Transparent)
                        .clickable {
                            if (!chosen) {
                                onSpan(option)
                                tick()
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.mark,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = if (option == Growth.Span.ALL) 17.sp else 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (chosen) Chalk else Muted,
                        ),
                    )
                }
            }
        }

        // The days something actually happened on, not the window's edges.
        // See Growth.summarise: a week with one call on Friday says Friday.
        Text(
            summary.from?.let { "${stamp(it)} – ${stamp(summary.to)}" } ?: "nothing yet",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                color = Muted,
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Glass)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

private fun stamp(date: java.time.LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("d.M.yy"))

// --- 1. most grown, one page per person ----------------------------------

@Composable
private fun ColumnScope.MostGrown(person: Growth.Person, contacts: List<Contact>) {
    val who = contacts.firstOrNull { it.id == person.contactId }
    CardTitle("Most grown")
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (who != null) {
            Avatar(who.label, who.tone, size = AvatarSize.SM)
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(
                who?.label ?: "Someone",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    color = Chalk,
                ),
            )
            SmallCopy(
                person.flowers.let { if (it == 1) "1 flower" else "$it flowers" } +
                    ", from " +
                    person.calls.let { if (it == 1) "1 call" else "$it calls" },
                size = 12,
            )
        }
    }
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        drawCluster(person.blooms.take(MOST_GROWN_KINDS))
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
private fun ColumnScope.EverythingYouGrew(summary: Growth.Summary) {
    CardTitle("Everything you grew")
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        drawDisc(summary.everything)
    }
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
private fun ColumnScope.PeopleYouGrowWith(summary: Growth.Summary, contacts: List<Contact>) {
    CardTitle("People you grow with")
    Spacer(Modifier.height(6.dp))
    // The bloom takes what is left, rather than claiming a square.
    //
    // It was aspectRatio(1f), which asks for a canvas as tall as the column
    // is wide. On half of a 411dp card that is 178dp, and with the avatar
    // and the name under it the column wanted 254dp of a 194dp box -- so
    // the card clipped the moment CARD_HEIGHT came down to fit the screen.
    // A weight cannot overflow: the flower is as big as the space allows
    // and the label is always under it.
    Box(Modifier.fillMaxWidth().weight(1f)) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            summary.people.take(PEOPLE_SHOWN).forEach { person ->
                val who = contacts.firstOrNull { it.id == person.contactId }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        translate(size.width / 2f, size.height / 2f) {
                            drawPersonBloom(person.petals, min(size.width, size.height) * 0.38f)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (who != null) Avatar(who.label, who.tone, size = AvatarSize.SM)
                    Spacer(Modifier.height(5.dp))
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
    }
    SmallCopy(
        if (summary.people.size > PEOPLE_SHOWN) {
            "The two you grew most with, of ${summary.people.size}"
        } else {
            "A petal for every call, coloured by what it grew"
        },
        size = 12,
    )
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

/** The card's own name, inside it, because the card is the whole view now. */
@Composable
private fun CardTitle(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleLarge.copy(
        fontSize = 21.sp,
        fontWeight = FontWeight.Normal,
        color = Chalk,
    ),
)

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(
    modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(26.dp))
        .background(Glass)
        .border(1.dp, CardEdge, RoundedCornerShape(26.dp))
        .padding(horizontal = 20.dp, vertical = 20.dp),
    content = content,
)
