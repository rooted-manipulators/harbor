package app.harbor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.data.HarborRepository
import app.harbor.domain.BlockKind
import app.harbor.domain.Moment
import app.harbor.domain.WeekBlock
import app.harbor.domain.Windows
import app.harbor.ui.theme.BandWarm
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Ember
import app.harbor.ui.theme.Glass
import app.harbor.ui.theme.Gold
import app.harbor.ui.theme.Hairline
import app.harbor.ui.theme.Ink
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.Paper
import app.harbor.ui.theme.Sand
import app.harbor.ui.theme.Stem
import app.harbor.ui.theme.Flow
import app.harbor.ui.theme.Eyebrow
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.pageContent
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The week, as something you plant — one day at a time.
 *
 * The data source the in-class suppression rule was built without (ADR-011).
 * Self-entered: no campus API to depend on, no calendar permission, and
 * nobody asked for their college password.
 *
 * Not a port of the prototype's Schedule, which is built around sharing
 * availability with a parent and a mutual-consent handshake. There is no
 * parent side here (ADR-007), so there is nobody to share with.
 *
 * ## Why a grid and not a form
 *
 * This used to be day pills, two hour steppers and an "Add this block"
 * button. Entering an ordinary week of five classes ran to something like
 * sixty taps, and you never saw the week you were describing — only a list
 * of times underneath it. People do not hold a timetable as text. They hold
 * it as a shape.
 *
 * So it is a day you draw on, the way a calendar or a piano roll works:
 * press empty space and drag to lay a block down, press a block to slide it
 * to another hour, and drag its bottom edge to make it longer. Everything
 * snaps to the half hour, which is the resolution a timetable actually has.
 *
 * ## Why one day and not seven
 *
 * Seven columns on a 393pt phone gave each day about forty points, which is
 * two fingers wide and one thorn wide. You could see the shape of your week
 * and could not reliably put anything into it. The frames answer that by
 * showing a single day at card width, with the days either side peeking in
 * at the edges so you still know where you are: the week is still there, it
 * is just not all under the finger at once.
 *
 * The day you are looking at is a [LocalDate] rather than a [DayOfWeek], so
 * the month picker can say "the 14th" and the strip can open on today. What
 * gets stored is still weekly — [WeekBlock] is keyed by day of week and
 * nothing below this file learned about dates. That is deliberate: a
 * timetable repeats, and the cue pipeline asks [Windows.busyAt] a question
 * about a weekday. Nothing here touched that, which is the whole point: a
 * thorn on a Wednesday still silences a reminder on every Wednesday, and a
 * flower still silences nothing.
 *
 * ## Thorns and flowers
 *
 * The Figma flow gives the day two things to place, and they are not
 * opposites. A thorn is busy, and stops a cue. A flower is time you would
 * welcome a call, and stops nothing — see [BlockKind] for why that asymmetry
 * is the whole safety of the feature. A flower is what makes an hour somebody
 * wrote down beat an hour that merely happened to be unbooked, wherever the
 * app goes on to offer one.
 *
 * Neither is picked up until somebody picks one up. The frames draw a box
 * around the chosen chip and nothing around the other, and opening this
 * screen with a kind already in hand made the first press somebody made
 * while working out what it was plant something nobody had asked for.
 *
 * ## Why the day runs midnight to midnight
 *
 * It used to run 7am to 11pm, on the reasoning that nobody is awake outside
 * that. But the cue pipeline has no hour-of-day rule in it at all — walk at
 * six in the morning and it will happily offer you your mother — so sleep is
 * exactly the kind of thing a person needs to be able to mark, and the old
 * grid gave them nowhere to mark it. The 8am–10pm clamp still applies to the
 * windows Harbor works out on its own, in [Windows.free].
 */
@Composable
fun ScheduleScreen(
    store: HarborRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Other ways a week can arrive, drawn under the day.
     *
     * A slot rather than a parameter, because what goes in it needs the
     * network client and this screen deliberately has no idea one exists —
     * everything above it is a week and a finger. See `ForwardYourChats`.
     */
    otherWays: @Composable ColumnScope.() -> Unit = {},
) {
    val skin = WeekSkin.specimen()

    WeekEditor(
        store = store,
        skin = skin,
        modifier = modifier,
        footer = {
            otherWays()

            // Importing a calendar sits under the day, not over it.
            //
            // It was the third thing on the screen, above the grid it is an
            // alternative to -- so the first offer Harbor made was a way not
            // to do the thing it had just asked for, and the offer is not even
            // available yet. Below the week it reads as what it is: something
            // coming later, for people who would rather not draw this.
            BringACalendar(skin)

            // The promise moves to the foot rather than disappearing. It is
            // the one line on this screen that is not about times, and the
            // screen where somebody types their week is the screen where it
            // most needs saying — but it does not need saying above the grid.
            //
            // It used to end "nothing leaves this phone", which stopped being
            // true the moment a message could be forwarded to a bot (ADR-014).
            // What is still true is narrower and is what this now says: the
            // week you draw stays here. The thing that does leave says so
            // itself, on the card that offers it.
            Eyebrow("Only the times · no subjects, no locations · what you draw stays on this phone")
            TextLink("Back", onDone)
        },
    ) {
        // The heading, and nothing under it.
        //
        // This screen used to open with a title, a subtitle *and* three
        // sentences explaining why it exists -- 870 pixels of a 2340 pixel
        // phone, so a screen called "drag and drop slots on your calendar"
        // showed the calendar as the smallest thing on it, cut off at 15:00
        // with the nav bar over the rest.
        //
        // The three sentences are what went. They were not wrong: they were
        // written because testers asked what the calendar was *for*, and they
        // answered that. They answered it once, and then charged every later
        // visit the same space. The how-to under the day says what to do, and
        // that is the part you come back needing.
        WeekHeading(skin)
    }
}


/**
 * Bringing a week in from a calendar you already keep.
 *
 * Drawn and switched off. Reading a calendar means a calendar permission and
 * an account connection, and the study's whole permission budget is spent on
 * activity recognition, which is the one the product cannot work without.
 * Worth showing that it is the plan, worth not pretending it is here.
 */
@Composable
private fun BringACalendar(skin: WeekSkin) {
    // A line, not a card.
    //
    // A bordered row with a title, a subtitle and a tag on the right is the
    // shape of something you press. This one does nothing, and drawing it as
    // the most substantial object under the grid made the one dead thing on
    // the screen the most inviting. Stripped to a sentence it reads as what it
    // is: a note about later, beside the thing that works.
    Text(
        "Bringing a week in from Outlook or Google comes later. For now it is drawn here.",
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            color = skin.muted,
        ),
    )
}

/**
 * The two lines the frames put at the top, and nothing else.
 *
 * This screen used to open with a page title, a subtitle, a privacy caption
 * and a section header before you reached the grid — four pieces of copy in
 * front of a thing whose whole job is to be looked at. The frames have two
 * lines and they are enough.
 */
@Composable
private fun WeekHeading(skin: WeekSkin) {
    Spacer(Modifier.height(4.dp))
    Text(
        "Drag and drop slots on your calendar",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, color = skin.ink),
    )
    // The frame says "We won't disturb you when you're busy". There is no "we"
    // in this product — nothing about this week leaves the phone, and nobody
    // is on the other end of it — so it is Harbor that stays quiet.
    Text(
        "Harbor stays quiet when you're busy",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp, color = skin.muted),
    )
}

/**
 * The last step of the first run: the Figma screen, on its own white ground.
 *
 * The same editor in different clothes. Finish stays dim until there is at
 * least one block on the week, which is the frames' own rule — an empty week
 * is the state this screen exists to fix. The way past it is the skip the
 * step before already offered, kept here so nobody is cornered.
 */
@Composable
fun WeekSetupScreen(
    store: HarborRepository,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WeekEditor(
        store = store,
        skin = WeekSkin.flow(),
        modifier = modifier,
        footer = { live ->
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GreyPill("Finish", enabled = live.isNotEmpty(), onClick = onFinish)
            }
            TextLink("Skip for now", onSkip)
        },
    ) {
        Spacer(Modifier.height(16.dp))
        WeekHeading(WeekSkin.flow())
    }
}

/**
 * The colours the week is drawn in.
 *
 * Two dressings, one editor. The first run is drawn on the Figma flow's white
 * with its grey pills; the Schedule tab is drawn on the specimen's bone. Which
 * of those two grounds the app settles on is still open, and keeping the
 * difference to this one object is what makes it a single edit when it closes.
 */
internal data class WeekSkin(
    val ground: Color,
    val band: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val tile: Color,
    /**
     * The day card's own face.
     *
     * Barely anything -- white at four percent over the ground -- because it
     * is not a card you read, it is a lift under twenty-four hours of
     * hairlines. Anything heavier and the lines stop being the lightest thing
     * on it.
     */
    val card: Color,
) {
    companion object {
        @Composable
        fun specimen() = WeekSkin(
            ground = MaterialTheme.colorScheme.background,
            // Its own colour rather than surfaceVariant, which sits too close
            // to a card to read as banding. Kept for the palette chips and the
            // pills; the seven-column banding it was mixed for is gone.
            band = BandWarm,
            line = MaterialTheme.colorScheme.outlineVariant,
            ink = MaterialTheme.colorScheme.onSurface,
            muted = MaterialTheme.colorScheme.onSurfaceVariant,
            tile = MaterialTheme.colorScheme.secondaryContainer,
            card = Color.White.copy(alpha = 0.04f),
        )

        /**
         * The onboarding flow's day.
         *
         * This used to be a second, lighter palette, because the flow was
         * drawn on white while the app was drawn on bone. Both are the dusk
         * ground now, so the two skins differ only in the tile -- the flow
         * wants a plainer one, without the theme's warmth behind it.
         */
        @Composable
        fun flow() = WeekSkin(
            ground = Paper,
            band = BandWarm,
            line = Hairline,
            ink = Chalk,
            muted = Muted,
            tile = Glass,
            card = Color.White.copy(alpha = 0.04f),
        )
    }
}

/**
 * The editor both screens are.
 *
 * [header] is whatever the screen wants above the palette, and [footer]
 * whatever it wants below; both are handed the live list, so a Finish button
 * can know whether anything has been planted yet.
 */
@Composable
private fun WeekEditor(
    store: HarborRepository,
    skin: WeekSkin,
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.(List<WeekBlock>) -> Unit = {},
    header: @Composable ColumnScope.(List<WeekBlock>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val saved by store.weekBlocks.collectAsState()

    // Held locally only while a gesture is in flight. Writing every frame of a
    // drag through to storage would be a prefs write per pointer event.
    var draft by remember { mutableStateOf<List<WeekBlock>?>(null) }
    var selected by remember { mutableStateOf<WeekBlock?>(null) }

    // Nothing in hand to begin with. The frames draw both chips unlit, and the
    // box only goes round the one you press.
    var planting by remember { mutableStateOf<BlockKind?>(null) }

    // One being carried off the palette, and where the finger is in root
    // coordinates. Null the rest of the time. The day turns the position into
    // an hour, because it is the only thing that knows where it sits.
    var carrying by remember { mutableStateOf<BlockKind?>(null) }
    var carryAt by remember { mutableStateOf(Offset.Zero) }
    var dropped by remember { mutableStateOf<Pair<BlockKind, Offset>?>(null) }

    // Where the finger is while a block is in the air, in root coordinates,
    // and whether it has left the day sideways. Both null/false the rest of
    // the time.
    //
    // Lifted out of the day because the bin is anchored to the screen and the
    // day is not: the card is twenty-four hours tall, which is taller than the
    // phone, so a bin at the foot of the card is a bin you cannot see while
    // you are dragging anything from the morning. The frames put it in the
    // bottom half of the *screen*, and that is the only place it can be and
    // still be a target.
    var dragAt by remember { mutableStateOf<Offset?>(null) }
    var leftTheDay by remember { mutableStateOf(false) }
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }

    // Which day is under the finger. Today, until somebody says otherwise.
    val today = remember { LocalDate.now() }
    var showing by remember { mutableStateOf(today) }
    var pickingMonth by remember { mutableStateOf(false) }

    val blocks = draft ?: saved

    fun commit(next: List<WeekBlock>, why: String? = null) {
        draft = null
        val grew = next.size > blocks.size
        scope.launch {
            store.setWeekBlocks(next)
            store.note(
                Moment.WEEK_EDITED,
                why ?: if (grew) (planting?.name?.lowercase() ?: "block") else "removed",
                next.size,
            )
        }
    }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(skin.ground)
            .onGloballyPositioned { pageOrigin = it.positionInRoot() },
    ) {
        // Over the bin, or out of the day altogether. Either scraps it.
        val armed = dragAt?.let { at ->
            leftTheDay ||
                with(density) { (at.y - pageOrigin.y).toDp() } > maxHeight - BIN_BAND
        } ?: false

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Flow(Modifier.pageContent(), gap = 14) {
                header(blocks)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaletteChip(
                        kind = BlockKind.BUSY,
                        label = "Place thorns",
                        sub = "(Busy)",
                        selected = planting == BlockKind.BUSY,
                        tile = skin.tile,
                        line = skin.line,
                        ink = skin.ink,
                        muted = skin.muted,
                        modifier = Modifier.weight(1f),
                        onCarry = { carryAt = it; carrying = BlockKind.BUSY },
                        onDrop = {
                            carrying = null
                            if (it.isSpecified) dropped = BlockKind.BUSY to it
                        },
                    ) {
                        planting = if (planting == BlockKind.BUSY) null else BlockKind.BUSY
                    }
                    PaletteChip(
                        kind = BlockKind.FREE,
                        label = "Place Flowers",
                        sub = "(Free)",
                        selected = planting == BlockKind.FREE,
                        tile = skin.tile,
                        line = skin.line,
                        ink = skin.ink,
                        muted = skin.muted,
                        modifier = Modifier.weight(1f),
                        onCarry = { carryAt = it; carrying = BlockKind.FREE },
                        onDrop = {
                            carrying = null
                            if (it.isSpecified) dropped = BlockKind.FREE to it
                        },
                    ) {
                        planting = if (planting == BlockKind.FREE) null else BlockKind.FREE
                    }
                }

                MonthButton(showing, skin) { pickingMonth = true }

                DayStrip(showing, skin) { showing = it }

                DayBoard(
                    blocks = blocks,
                    showing = showing,
                    planting = planting,
                    selected = selected,
                    skin = skin,
                    carrying = carrying,
                    carryAt = carryAt,
                    dropped = dropped,
                    onPlanted = { dropped = null },
                    onSelect = { selected = it },
                    onPreview = { draft = it },
                    onCommit = { next, landed ->
                        selected = landed
                        commit(next)
                    },
                    onShow = { showing = it },
                    armed = armed,
                    onDragAt = { dragAt = it },
                    onLeftTheDay = { leftTheDay = it },
                    onCopyYesterday = {
                        // A timetable is the same hours on five days far more
                        // often than it is five different sets, and redrawing
                        // yesterday is the tedium this screen exists to
                        // remove. Offered only on an empty day, so there is
                        // nothing of yours for it to land on top of.
                        val from = showing.minusDays(1).dayOfWeek
                        val to = showing.dayOfWeek
                        var next = blocks
                        blocks.filter { it.day == from }.forEach {
                            next = Windows.place(next, it.copy(day = to))
                        }
                        selected = null
                        commit(next, why = "copied")
                    },
                )

                SmallCopy(
                    if (planting == null) {
                        "Pick thorns or flowers above, then press the day to " +
                            "plant one — or drag one straight down onto it."
                    } else {
                        "Press the day to plant one, or drag one down from " +
                            "above. Drag a block's bottom edge to make it " +
                            "longer, or drag it out of the day to bin it."
                    },
                    size = 13,
                )

                footer(blocks)
            }
        }

        if (dragAt != null) {
            BinTarget(
                armed = armed,
                skin = skin,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BIN_BAND),
            )
        }

        if (pickingMonth) {
            MonthSheet(
                showing = showing,
                today = today,
                skin = skin,
                onPick = { showing = it; pickingMonth = false },
                onDismiss = { pickingMonth = false },
            )
        }
    }
}

// The whole day, because the cue pipeline has no hour-of-day rule of its own
// and sleep is worth being able to mark. A line every hour, a label every two,
// as the frames have them.
private const val FIRST_HOUR = 0
private const val LAST_HOUR = 24
private const val LABEL_EVERY = 2
private const val SNAP_MINUTES = 30
private val HOUR_HEIGHT = 21.dp
// Wide enough for "00:00" at nine points on one line. At 42dp it wrapped, and
// every hour down the day read as "00:0" over "0".
private val GUTTER = 50.dp

/** The strip under the hours that the bin lives in. Not part of the day. */
private val CARD_FOOT = 44.dp

/** Air above the first hour, so 00:00 is not against the card's rim. */
private val CARD_HEAD = 12.dp

/** How much of the width the day gets. The rest is the days either side. */
private const val CARD_SHARE = 0.74f

private val DayCardShape = RoundedCornerShape(26.dp)

/** How far sideways counts as "show me the next day". */
private val SWIPE_DAY = 56.dp

/**
 * How much of the screen's foot the bin takes, and therefore how far down a
 * block has to come before letting go of it scraps it.
 *
 * Roughly the bottom quarter of a phone. Deep enough to be an easy target at
 * the end of a long drag, shallow enough that it is not somewhere a thumb
 * arrives by accident on the way to the day's last hours -- which it cannot
 * do anyway, because the band is only live while something is being carried.
 */
private val BIN_BAND = 220.dp

/** How many days either side of the chosen one the strip shows. */
private const val STRIP_REACH = 2

private enum class Grab { Move, ResizeEnd }

/**
 * How long a finger has to rest on empty ground before it starts planting.
 *
 * Only empty ground waits at all. Something already on the day moves the
 * instant you push it -- see [heldStill] for why the two are not the same
 * question.
 *
 * Short enough to feel like no wait, long enough that a tap is still a tap.
 * The floor here is a deliberate tap, which runs about 60-100ms: go under
 * that and every tap on an empty hour plants half an hour nobody asked for.
 */
private const val PLANT_HOLD_MS = 90L

/**
 * Whether the finger stayed put long enough to mean "plant one here".
 *
 * Returns as soon as it knows: lifting or moving first is an answer, and only
 * running out of time is a hold. Moving first has to fall through untouched,
 * because a drag down empty ground is how the page is scrolled and a drag
 * across it is now how the day is changed -- and neither may cost the user a
 * block they did not want.
 */
private suspend fun AwaitPointerEventScope.heldStill(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    val answered = withTimeoutOrNull(PLANT_HOLD_MS) {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            if ((change.position - down.position).getDistance() > slop) break
        }
    }
    return answered == null
}

/**
 * The day, and the two beside it.
 *
 * Everything on the day is painted onto one canvas rather than laid out as
 * boxes, because a thorn's spikes and a flower's head both sit outside their
 * own time span and a box would clip them.
 *
 * The block under the finger is carried separately from the rest of the list
 * for the length of a gesture. That is what lets [Windows.place] run once, at
 * the end, instead of reshuffling the list under a drag that is still going.
 */
@Composable
private fun DayBoard(
    blocks: List<WeekBlock>,
    showing: LocalDate,
    planting: BlockKind?,
    selected: WeekBlock?,
    skin: WeekSkin,
    /** A kind being carried in from the palette, or null. */
    carrying: BlockKind?,
    /** Where that finger is, in root coordinates. */
    carryAt: Offset,
    /** One let go of, and where. Cleared through [onPlanted] once dealt with. */
    dropped: Pair<BlockKind, Offset>?,
    onPlanted: () -> Unit,
    onSelect: (WeekBlock?) -> Unit,
    onPreview: (List<WeekBlock>) -> Unit,
    /** The second argument is null when the block was dropped in the bin. */
    onCommit: (List<WeekBlock>, WeekBlock?) -> Unit,
    onShow: (LocalDate) -> Unit,
    /** True when letting go here would scrap the block. The page decides it. */
    armed: Boolean,
    /** Where the finger is in root coordinates, or null when nothing is held. */
    onDragAt: (Offset?) -> Unit,
    /** Whether the finger has left the day sideways or past its last hour. */
    onLeftTheDay: (Boolean) -> Unit,
    onCopyYesterday: () -> Unit,
) {
    val hours = LAST_HOUR - FIRST_HOUR
    val density = LocalDensity.current
    val hoursHeight = HOUR_HEIGHT * hours

    // The block under the finger, and the rest of the week without it.
    var carried by remember { mutableStateOf<WeekBlock?>(null) }
    var rest by remember { mutableStateOf<List<WeekBlock>>(emptyList()) }
    var grab by remember { mutableStateOf(Grab.Move) }
    var cursor by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(0) }

    // Declared out here rather than inside the constraints scope, because the
    // modifier that measures it is on the box that opens that scope.
    var hoursOrigin by remember { mutableStateOf(Offset.Zero) }

    val day = showing.dayOfWeek
    val mine = blocks.filter { it.day == day }
    val yesterday = blocks.filter { it.day == showing.minusDays(1).dayOfWeek }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(CARD_HEAD + hoursHeight + CARD_FOOT)
            // Scrolling sideways is the other way to reach a day, and it only
            // ever sees the gestures the day itself did not want: a press on a
            // block consumes, and a press on empty ground that moves before the
            // plant hold is up does not.
            .pointerInput(showing) {
                var travelled = 0f
                val far = SWIPE_DAY.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = {
                        if (travelled <= -far) onShow(showing.plusDays(1))
                        else if (travelled >= far) onShow(showing.minusDays(1))
                    },
                    onDragCancel = { travelled = 0f },
                ) { _, delta -> travelled += delta }
            },
    ) {
        val cardWidth = maxWidth * CARD_SHARE
        val widthPx = with(density) { cardWidth.toPx() }
        val gutterPx = with(density) { GUTTER.toPx() }
        val hourPx = with(density) { HOUR_HEIGHT.toPx() }
        val insetPx = with(density) { 7.dp.toPx() }
        val edgePx = with(density) { 14.dp.toPx() }
        val slackPx = with(density) { 5.dp.toPx() }

        // The days either side, peeking in at the edges. They are the week you
        // are still in; the frames show them dimmed and mostly off-screen, and
        // a press on the sliver is the third way to change day.
        NeighbourDay(
            blocks = blocks,
            day = showing.minusDays(1).dayOfWeek,
            skin = skin,
            width = cardWidth,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -(cardWidth + 16.dp)),
            onClick = { onShow(showing.minusDays(1)) },
        )
        NeighbourDay(
            blocks = blocks,
            day = showing.plusDays(1).dayOfWeek,
            skin = skin,
            width = cardWidth,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = cardWidth + 16.dp),
            onClick = { onShow(showing.plusDays(1)) },
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .width(cardWidth)
                .fillMaxHeight()
                .clip(DayCardShape)
                .background(skin.card)
                .border(1.dp, skin.line.copy(alpha = 0.45f), DayCardShape),
        ) {
            Box(
                Modifier
                    .padding(top = CARD_HEAD)
                    .fillMaxWidth()
                    .height(hoursHeight)
                    .onGloballyPositioned { hoursOrigin = it.positionInRoot() },
            ) {
                fun minuteAt(y: Float): Int {
                    val raw = FIRST_HOUR * 60 + (y / hourPx) * 60f
                    val snapped = (raw / SNAP_MINUTES).roundToInt() * SNAP_MINUTES
                    return snapped.coerceIn(FIRST_HOUR * 60, LAST_HOUR * 60)
                }

                fun boxOf(block: WeekBlock): Rect = blockBox(
                    block,
                    width = widthPx,
                    top = 0f,
                    hourPx = hourPx,
                    inset = insetPx,
                    gutter = gutterPx,
                )

                // Read through a snapshot rather than closing over `blocks`.
                //
                // pointerInput restarts whenever one of its keys changes, so
                // keying it on the list meant the first preview frame of a drag
                // tore down the detector that was producing it: a block could
                // be created but never sized, and every drag ended as a cancel.
                val latest by rememberUpdatedState(blocks)
                val nowPlanting by rememberUpdatedState(planting)
                val nowArmed by rememberUpdatedState(armed)
                val onDay by rememberUpdatedState(mine)

                fun hitTest(at: Offset): Pair<WeekBlock, Grab>? {
                    onDay.forEach { b ->
                        val box = boxOf(b)
                        // A half-hour block is only ten points tall, so the hit
                        // box gets a little slack it does not draw.
                        if (at.x >= gutterPx - slackPx && at.x <= widthPx &&
                            at.y >= box.top - slackPx && at.y <= box.bottom + slackPx
                        ) {
                            val handle = minOf(edgePx, box.height * 0.4f)
                            return b to
                                if (at.y > box.bottom - handle) Grab.ResizeEnd else Grab.Move
                        }
                    }
                    return null
                }

                /**
                 * What an hour let go of at this point would be, or null if the
                 * point is not over the day.
                 *
                 * Shared by the ghost under the finger and by the drop itself,
                 * so what you are shown while you carry it is by construction
                 * what you get when you let go.
                 */
                fun blockAt(root: Offset, kind: BlockKind): WeekBlock? {
                    if (!root.isSpecified) return null
                    val local = root - hoursOrigin
                    if (local.x < 0f || local.x > widthPx) return null
                    if (local.y < -slackPx || local.y > hourPx * hours + slackPx) return null
                    val start = minuteAt(local.y)
                    val end = (start + 60).coerceAtMost(LAST_HOUR * 60)
                    if (end <= start) return null
                    return WeekBlock(
                        day = day,
                        start = minutesToTime(start),
                        end = minutesToTime(end),
                        kind = kind,
                    )
                }

                val ghost = carrying?.let { blockAt(carryAt, it) }

                // Let go. An hour lands wherever the finger was over the day,
                // and nothing happens if it was let go anywhere else -- the
                // palette is somewhere to pick one up from, not a bin.
                LaunchedEffect(dropped) {
                    val drop = dropped ?: return@LaunchedEffect
                    blockAt(drop.second, drop.first)?.let {
                        onCommit(Windows.place(latest, it), it)
                    }
                    onPlanted()
                }

                Canvas(Modifier.fillMaxSize()) {
                    drawDay(
                        blocks = onDay,
                        skin = skin,
                        top = 0f,
                        hourPx = hourPx,
                        gutter = gutterPx,
                        inset = insetPx,
                    )

                    ghost?.let { g ->
                        val box = boxOf(g)
                        when (g.kind) {
                            BlockKind.BUSY -> drawThorn(box)
                            BlockKind.FREE -> drawFlowerBlock(box)
                        }
                        drawRoundRect(
                            color = skin.ink,
                            topLeft = Offset(box.left - 3f, box.top - 3f),
                            size = Size(box.width + 6f, box.height + 6f),
                            cornerRadius = CornerRadius(box.height * 0.3f),
                            style = Stroke(width = 2f),
                        )
                    }

                    selected?.takeIf { it in onDay }?.let { b ->
                        val box = boxOf(b)
                        drawRoundRect(
                            color = skin.ink,
                            topLeft = Offset(box.left - 3f, box.top - 3f),
                            size = Size(box.width + 6f, box.height + 6f),
                            cornerRadius = CornerRadius(box.height * 0.3f),
                            style = Stroke(width = 2f),
                        )
                    }
                }

                for (h in FIRST_HOUR until LAST_HOUR step LABEL_EVERY) {
                    Text(
                        clockLabel(h),
                        modifier = Modifier
                            .offset(y = HOUR_HEIGHT * (h - FIRST_HOUR) - 7.dp)
                            .width(GUTTER)
                            .padding(start = 8.dp, end = 6.dp),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = skin.muted,
                        ),
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(widthPx, hourPx, day) {
                            detectTapGestures(
                                onTap = { at -> onSelect(hitTest(at)?.first) },
                                // Two taps puts an hour down where you tapped.
                                // Press-and-drag still draws one at whatever
                                // length you like; this is the quick way to say
                                // "here", which is most of what anybody does.
                                //
                                // Nothing in hand, nothing planted: the palette
                                // is where you say which.
                                onDoubleTap = { at ->
                                    val kind = nowPlanting
                                    if (kind != null && hitTest(at) == null) {
                                        val start = minuteAt(at.y)
                                        val end = (start + 60).coerceAtMost(LAST_HOUR * 60)
                                        if (end > start) {
                                            val block = WeekBlock(
                                                day = day,
                                                start = minutesToTime(start),
                                                end = minutesToTime(end),
                                                kind = kind,
                                            )
                                            onCommit(Windows.place(latest, block), block)
                                        }
                                    }
                                },
                            )
                        }
                        // Picking something up and planting something are two
                        // different gestures, and used to be one.
                        //
                        // Both went through `detectDragGesturesAfterLongPress`,
                        // so moving a block you could already see meant holding
                        // it first -- and once the hold was shortened to make
                        // planting feel quick, an ordinary tap on empty ground
                        // outlasted it and put down half an hour on the way
                        // past. Every tap planted something, and nothing could
                        // be moved without waiting for a timer first.
                        //
                        // Split in two. A block that already exists moves as
                        // soon as the finger moves, because there is nothing
                        // ambiguous about pushing something that is already
                        // there. Only empty ground waits, and only long enough
                        // to tell planting from scrolling the page or changing
                        // the day.
                        .pointerInput(widthPx, hourPx, day) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val hit = hitTest(down.position)

                                val begin = if (hit != null) {
                                    awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                        change.consume()
                                    } != null
                                } else {
                                    heldStill(down)
                                }
                                if (!begin) return@awaitEachGesture

                                cursor = down.position
                                if (hit == null) {
                                    val kind = nowPlanting ?: return@awaitEachGesture
                                    // Plant one and start sizing it at once, so
                                    // a single press-and-drag both puts it down
                                    // and sets how long it runs.
                                    val start = minuteAt(down.position.y)
                                    val end =
                                        (start + SNAP_MINUTES).coerceAtMost(LAST_HOUR * 60)
                                    if (end <= start) return@awaitEachGesture
                                    rest = latest
                                    carried = WeekBlock(
                                        day = day,
                                        start = minutesToTime(start),
                                        end = minutesToTime(end),
                                        kind = kind,
                                    )
                                    grab = Grab.ResizeEnd
                                    grabOffset = 0
                                } else {
                                    rest = latest.filterNot { it == hit.first }
                                    carried = hit.first
                                    grab = hit.second
                                    grabOffset =
                                        minuteAt(down.position.y) - hit.first.start.toMinutes()
                                }
                                carried?.let {
                                    onSelect(it)
                                    onPreview(rest + it)
                                    onDragAt(hoursOrigin + down.position)
                                }

                                // Absolute positions rather than accumulated
                                // deltas, so what is under the finger stays
                                // under the finger however long the drag runs.
                                val finished = drag(down.id) { change ->
                                    change.consume()
                                    val held = carried ?: return@drag
                                    cursor = change.position
                                    // Out of the day, in any direction. One
                                    // column means sideways is no longer a way
                                    // of saying "Tuesday", so it is free to be
                                    // the way of saying "away". Dragging down
                                    // onto the bin is the page's half of this.
                                    onLeftTheDay(
                                        cursor.y > size.height ||
                                            cursor.x < 0f ||
                                            cursor.x > size.width,
                                    )
                                    onDragAt(hoursOrigin + cursor)
                                    val next = when (grab) {
                                        Grab.Move -> {
                                            val length =
                                                held.end.toMinutes() - held.start.toMinutes()
                                            val start = (minuteAt(cursor.y) - grabOffset)
                                                .coerceIn(
                                                    FIRST_HOUR * 60,
                                                    LAST_HOUR * 60 - length,
                                                )
                                            held.copy(
                                                start = minutesToTime(start),
                                                end = minutesToTime(start + length),
                                            )
                                        }

                                        Grab.ResizeEnd -> {
                                            val end = minuteAt(cursor.y).coerceIn(
                                                held.start.toMinutes() + SNAP_MINUTES,
                                                LAST_HOUR * 60,
                                            )
                                            held.copy(end = minutesToTime(end))
                                        }
                                    }
                                    carried = next
                                    onSelect(next)
                                    onPreview(rest + next)
                                }

                                carried?.let {
                                    // Let go on the bin, or outside the day:
                                    // it is taken and nothing is planted. A
                                    // gesture the system cancelled keeps its
                                    // block instead, because losing work to a
                                    // stray notification is worse than an
                                    // unwanted half hour.
                                    if (finished && nowArmed) onCommit(rest, null)
                                    else onCommit(Windows.place(rest, it), it)
                                }
                                carried = null
                                onLeftTheDay(false)
                                onDragAt(null)
                            }
                        },
                )

                // Yesterday again, in one press.
                //
                // Only offered on a day with nothing on it, because it fills an
                // empty day rather than merging into a full one, and only when
                // there is in fact something to copy.
                if (mine.isEmpty() && yesterday.isNotEmpty() && carried == null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(30.dp))
                            .background(skin.tile)
                            .border(
                                1.dp,
                                skin.line.copy(alpha = 0.6f),
                                RoundedCornerShape(30.dp),
                            )
                            .clickable(onClick = onCopyYesterday)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(
                            "Copy schedule\nfrom yesterday",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 13.sp,
                                color = skin.ink,
                            ),
                        )
                    }
                }
            }

        }
    }
}

/**
 * A day you are not editing: the same card, dimmed, at the edge of the screen.
 *
 * Drawn rather than composed out of the editor, because everything that makes
 * the editor an editor -- the hit testing, the drafts, the palette drop -- is
 * exactly what a day you are only glancing at must not have.
 */
@Composable
private fun NeighbourDay(
    blocks: List<WeekBlock>,
    day: DayOfWeek,
    skin: WeekSkin,
    width: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val gutterPx = with(density) { GUTTER.toPx() }
    val insetPx = with(density) { 7.dp.toPx() }
    val headPx = with(density) { CARD_HEAD.toPx() }
    val footPx = with(density) { CARD_FOOT.toPx() }
    val mine = blocks.filter { it.day == day }

    Box(
        modifier
            .width(width)
            .fillMaxHeight(0.9f)
            .clip(DayCardShape)
            .background(skin.card)
            .border(1.dp, skin.line.copy(alpha = 0.3f), DayCardShape)
            .clickable(onClick = onClick)
            .alpha(0.5f),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val hourPx = (size.height - headPx - footPx) / (LAST_HOUR - FIRST_HOUR)
            drawDay(
                blocks = mine,
                skin = skin,
                top = headPx,
                hourPx = hourPx,
                gutter = gutterPx,
                inset = insetPx,
            )
        }
    }
}

/**
 * The hours, and whatever has been planted in them.
 *
 * One function so the day you are editing and the days either side cannot
 * drift apart -- the edge cards are the same drawing at a different size.
 */
private fun DrawScope.drawDay(
    blocks: List<WeekBlock>,
    skin: WeekSkin,
    top: Float,
    hourPx: Float,
    gutter: Float,
    inset: Float,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    for (h in FIRST_HOUR..LAST_HOUR) {
        val y = top + (h - FIRST_HOUR) * hourPx
        val onTheLabel = h % LABEL_EVERY == 0
        drawLine(
            color = if (onTheLabel) skin.line else skin.line.copy(alpha = 0.45f),
            start = Offset(gutter, y),
            end = Offset(size.width - inset, y),
            strokeWidth = 1.1f,
            pathEffect = if (onTheLabel) null else dash,
        )
    }

    blocks.forEach { b ->
        val box = blockBox(b, size.width, top, hourPx, inset, gutter)
        when (b.kind) {
            BlockKind.BUSY -> drawThorn(box)
            BlockKind.FREE -> drawFlowerBlock(box)
        }
    }
}

/** Where a block sits on a day this wide, at this many pixels an hour. */
private fun blockBox(
    block: WeekBlock,
    width: Float,
    top: Float,
    hourPx: Float,
    inset: Float,
    gutter: Float,
): Rect = Rect(
    left = gutter + inset,
    top = top + (block.start.toMinutes() - FIRST_HOUR * 60) / 60f * hourPx,
    right = width - inset,
    bottom = top + (block.end.toMinutes() - FIRST_HOUR * 60) / 60f * hourPx,
)

/**
 * Where a block goes to die: a gradient up the foot of the screen with a bin
 * in it, the way a story drags to one.
 *
 * It only appears while something is being carried, and it lights when the
 * finger is somewhere a let-go would scrap the block -- over this band, or out
 * of the day sideways. Anchored to the screen rather than to the card, because
 * the card is twenty-four hours tall and its own foot is off the bottom of the
 * phone: a bin down there is invisible for most of the day and unreachable for
 * all of it.
 */
@Composable
private fun BinTarget(armed: Boolean, skin: WeekSkin, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    skin.ground.copy(alpha = 0.82f),
                    skin.ground,
                ),
            ),
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (armed) skin.tile else skin.card)
                    .border(1.dp, if (armed) skin.ink else skin.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(width = 16.dp, height = 18.dp)) {
                    val w = size.width
                    val h = size.height
                    val ink = if (armed) skin.ink else skin.muted
                    // A lid, and a tub under it.
                    drawRect(
                        color = ink,
                        topLeft = Offset(0f, h * 0.10f),
                        size = Size(w, h * 0.10f),
                    )
                    drawRect(
                        color = ink,
                        topLeft = Offset(w * 0.34f, 0f),
                        size = Size(w * 0.32f, h * 0.10f),
                    )
                    drawRect(
                        color = ink,
                        topLeft = Offset(w * 0.12f, h * 0.26f),
                        size = Size(w * 0.76f, h * 0.74f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (armed) "Let go to delete" else "Drag down to delete",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = if (armed) skin.ink else skin.muted,
                ),
            )
        }
    }
}

/**
 * The month you are in, and the way into the rest of them.
 *
 * A day at a time is a small window on a year, and the strip only reaches two
 * days either side. This is the one control that admits the calendar is
 * bigger than the screen.
 */
@Composable
private fun MonthButton(showing: LocalDate, skin: WeekSkin, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(skin.tile)
            .border(1.dp, skin.line.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            showing.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                color = skin.ink,
            ),
        )
        Spacer(Modifier.width(12.dp))
        // Drawn, like every other mark in the app. See `drawTabMark`.
        Canvas(Modifier.size(width = 11.dp, height = 8.dp)) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                },
                skin.ink,
            )
        }
    }
}

/**
 * The days around the one you are on.
 *
 * Two either side, the middle one lit, arrows at the ends. The frames dim the
 * outermost pair, which is what keeps five day names from reading as a tab bar
 * with five equal tabs.
 */
@Composable
private fun DayStrip(showing: LocalDate, skin: WeekSkin, onPick: (LocalDate) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StripArrow(back = true, skin = skin) { onPick(showing.minusDays(1)) }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (step in -STRIP_REACH..STRIP_REACH) {
                val date = showing.plusDays(step.toLong())
                val chosen = step == 0
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (chosen) skin.tile else Color.Transparent)
                        .clickable { onPick(date) }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            color = when {
                                chosen -> skin.ink
                                abs(step) == STRIP_REACH -> skin.muted.copy(alpha = 0.5f)
                                else -> skin.muted
                            },
                        ),
                    )
                }
            }
        }
        StripArrow(back = false, skin = skin) { onPick(showing.plusDays(1)) }
    }
}

/** Drawn geometry, not an icon. See `drawTabMark` for the pattern. */
@Composable
private fun StripArrow(back: Boolean, skin: WeekSkin, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Canvas(Modifier.size(width = 9.dp, height = 12.dp)) {
            drawPath(
                Path().apply {
                    if (back) {
                        moveTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height / 2f)
                    } else {
                        moveTo(0f, 0f)
                        lineTo(0f, size.height)
                        lineTo(size.width, size.height / 2f)
                    }
                    close()
                },
                skin.muted,
            )
        }
    }
}

/**
 * The mini calendar: months, and the one date you are looking at.
 *
 * Scrolls rather than steps, because what people do here is find a date a few
 * weeks out, not page through a year one month at a time.
 *
 * Nothing below this screen has ever heard of a date — [WeekBlock] is keyed by
 * day of week, and so is everything the cue pipeline asks. Picking the 14th is
 * picking a Monday; the date is only what makes that easy to say.
 */
@Composable
private fun MonthSheet(
    showing: LocalDate,
    today: LocalDate,
    skin: WeekSkin,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember(today) { YearMonth.from(today).minusMonths(12) }
    val months = remember(first) { (0L until 25L).map { first.plusMonths(it) } }
    val opensAt = remember(showing, months) {
        months.indexOf(YearMonth.from(showing)).coerceAtLeast(0)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(28.dp))
                // An opaque ground, and the warm wash over it.
                //
                // The wash alone was the whole background, and both its stops
                // are translucent -- `tile` is white at five percent and the
                // ember at sixteen -- so the sheet came out about a tenth
                // opaque and you read the day strip and a row of flowers
                // straight through the month. A panel that covers the page has
                // to actually cover it; the warmth is a tint on top, not the
                // thing holding the light out.
                .background(skin.ground)
                .background(
                    Brush.linearGradient(
                        listOf(skin.tile, Ember.copy(alpha = 0.16f)),
                    ),
                )
                .border(1.dp, skin.line.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                // Pressing the sheet is not pressing past it.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                // Sunday first, as the frames draw it.
                for (letter in listOf("S", "M", "T", "W", "T", "F", "S")) {
                    Text(
                        letter,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            color = skin.ink,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(state = rememberLazyListState(opensAt)) {
                items(months) { month ->
                    MonthGrid(month, showing, today, skin, onPick)
                }
            }
        }
    }
}

/** One month, Sunday first, six rows deep at the most. */
@Composable
private fun MonthGrid(
    month: YearMonth,
    showing: LocalDate,
    today: LocalDate,
    skin: WeekSkin,
    onPick: (LocalDate) -> Unit,
) {
    // DayOfWeek counts Monday as 1 and Sunday as 7, so the remainder is the
    // Sunday-first column index without a table to look it up in.
    val lead = month.atDay(1).dayOfWeek.value % 7
    val length = month.lengthOfMonth()
    val rows = (lead + length + 6) / 7

    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(
            month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                " " + month.year,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                color = skin.muted,
            ),
        )
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                for (column in 0 until 7) {
                    val dayOfMonth = row * 7 + column - lead + 1
                    if (dayOfMonth < 1 || dayOfMonth > length) {
                        Spacer(Modifier.weight(1f).height(38.dp))
                        continue
                    }
                    val date = month.atDay(dayOfMonth)
                    val chosen = date == showing
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clickable { onPick(date) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (chosen) Stem else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dayOfMonth.toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 13.sp,
                                    color = when {
                                        chosen -> Chalk
                                        date == today -> Gold
                                        else -> skin.muted
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The flow's pill, so the last step matches the ten before it.
 *
 * Named for the grey it used to be. It is amber now, like every other enabled
 * control in onboarding, and the name is kept only because this is the step
 * that closes that flow and the two files are read together.
 */
@Composable
private fun GreyPill(label: String, enabled: Boolean, onClick: () -> Unit) = Box(
    Modifier
        .clip(RoundedCornerShape(29.dp))
        .background(if (enabled) Gold else Sand)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 26.dp, vertical = 8.dp),
) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 17.sp,
            color = if (enabled) Ink else Muted,
        ),
    )
}

private fun LocalTime.toMinutes(): Int = hour * 60 + minute

private fun minutesToTime(total: Int): LocalTime {
    val clamped = total.coerceIn(0, 23 * 60 + 59)
    return LocalTime.of(clamped / 60, clamped % 60)
}

/** "00:00", as the frames label the hours. */
private fun clockLabel(hour: Int): String =
    (hour % 24).toString().padStart(2, '0') + ":00"

/** "8pm", "12:30pm" -- the way the week's labels say a time. */
internal fun timeLabel(at: LocalTime): String {
    val display = if (at.hour % 12 == 0) 12 else at.hour % 12
    val suffix = if (at.hour < 12) "am" else "pm"
    return if (at.minute == 0) {
        display.toString() + suffix
    } else {
        display.toString() + ":" + at.minute.toString().padStart(2, '0') + suffix
    }
}
