package app.harbor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.data.HarborRepository
import app.harbor.domain.BlockKind
import app.harbor.domain.Moment
import app.harbor.domain.WeekBlock
import app.harbor.domain.Windows
import app.harbor.ui.theme.BandWarm
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Gold
import app.harbor.ui.theme.Glass
import app.harbor.ui.theme.Hairline
import app.harbor.ui.theme.Ink
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.Paper
import app.harbor.ui.theme.Sand
import app.harbor.ui.theme.Flow
import app.harbor.ui.theme.Eyebrow
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.pageContent
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The week, as something you plant.
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
 * So it is a week you draw on, the way a calendar or a piano roll works:
 * press empty space and drag to lay a block down, press a block to slide it
 * to another day or hour, and drag its bottom edge to make it longer.
 * Everything snaps to the half hour, which is the resolution a timetable
 * actually has.
 *
 * Press-and-drag rather than plain drag is deliberate: this grid sits inside
 * a scrolling page, and if a plain vertical drag drew blocks instead of
 * scrolling, the page would become a trap. The press is also the right feel
 * for putting something down.
 *
 * ## Thorns and flowers
 *
 * The Figma flow gives the grid two things to place, and they are not
 * opposites. A thorn is busy, and stops a cue. A flower is time you would
 * welcome a call, and stops nothing — see [BlockKind] for why that asymmetry
 * is the whole safety of the feature. A flower is what makes an hour somebody
 * wrote down beat an hour that merely happened to be unbooked, wherever the
 * app goes on to offer one.
 *
 * The "a little window" card that used to sit above this grid is gone. It
 * showed your free hours and offered to ring somebody with them, which is the
 * parent's side of the product rather than this one - a student marking their
 * timetable is not publishing their availability.
 *
 * The frames show a hand cursor hovering over the thorn to say which one you
 * are holding. A phone has no cursor, so the palette is a pair of chips and
 * the chosen one is lit. The gesture on the grid is unchanged.
 *
 * ## Why the grid runs midnight to midnight
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
     * Other ways a week can arrive, drawn under the grid.
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

            // Importing a calendar sits under the grid, not over it.
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
        // Nothing above the grid.
        //
        // This screen used to open with a title, a subtitle and three
        // sentences explaining why it exists -- 870 pixels of a 2340 pixel
        // phone, so a screen called "drag and drop slots on your calendar"
        // showed the calendar as the smallest thing on it, cut off at 15:00
        // with the nav bar over the rest.
        //
        // The explanation was not wrong. It was written because testers asked
        // what the calendar was *for*, and it answered them. But it answered
        // them once, and then charged every later visit the same 870 pixels.
        // A tab you return to is not a tab that needs re-introducing, and the
        // tab is already labelled Schedule.
        //
        // The first run still gets the heading -- see WeekSetupScreen, where
        // somebody is meeting this for the first time and there is no nav bar
        // to tell them where they are.
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
) {
    companion object {
        @Composable
        fun specimen() = WeekSkin(
            ground = MaterialTheme.colorScheme.background,
            // Its own colour rather than surfaceVariant, which sits too close
            // to a card to read as banding. This is barely anything -- white at
            // three percent -- and it only has to make seven narrow columns
            // countable without anybody reading the day labels.
            band = BandWarm,
            line = MaterialTheme.colorScheme.outlineVariant,
            ink = MaterialTheme.colorScheme.onSurface,
            muted = MaterialTheme.colorScheme.onSurfaceVariant,
            tile = MaterialTheme.colorScheme.secondaryContainer,
        )

        /**
         * The onboarding flow's grid.
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
    var planting by remember { mutableStateOf(BlockKind.BUSY) }

    // One being carried off the palette, and where the finger is in root
    // coordinates. Null the rest of the time. The grid turns the position into
    // a day and an hour, because it is the only thing that knows how wide a
    // column is.
    var carrying by remember { mutableStateOf<BlockKind?>(null) }
    var carryAt by remember { mutableStateOf(Offset.Zero) }
    var dropped by remember { mutableStateOf<Pair<BlockKind, Offset>?>(null) }

    val blocks = draft ?: saved

    fun commit(next: List<WeekBlock>) {
        draft = null
        val grew = next.size > blocks.size
        scope.launch {
            store.setWeekBlocks(next)
            store.note(
                Moment.WEEK_EDITED,
                if (grew) planting.name.lowercase() else "removed",
                next.size,
            )
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(skin.ground)
            .verticalScroll(rememberScrollState()),
    ) {
        Flow(Modifier.pageContent(), gap = 14) {
            header(blocks)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PaletteChip(
                    kind = BlockKind.BUSY,
                    label = "Busy time",
                    selected = planting == BlockKind.BUSY,
                    tile = skin.tile,
                    ink = skin.ink,
                    onCarry = { carryAt = it; carrying = BlockKind.BUSY },
                    onDrop = {
                        carrying = null
                        if (it.isSpecified) dropped = BlockKind.BUSY to it
                    },
                ) { planting = BlockKind.BUSY }
                PaletteChip(
                    kind = BlockKind.FREE,
                    label = "Free time",
                    selected = planting == BlockKind.FREE,
                    tile = skin.tile,
                    ink = skin.ink,
                    onCarry = { carryAt = it; carrying = BlockKind.FREE },
                    onDrop = {
                        carrying = null
                        if (it.isSpecified) dropped = BlockKind.FREE to it
                    },
                ) { planting = BlockKind.FREE }
            }

            WeekGrid(
                blocks = blocks,
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
            )

            val chosen = selected?.takeIf { it in blocks }
            if (chosen != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BlockGlyph(chosen.kind, Modifier.size(width = 18.dp, height = 24.dp))
                    Text(
                        chosen.day.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                            "  " + timeLabel(chosen.start) + " to " + timeLabel(chosen.end),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            color = skin.muted,
                        ),
                    )
                    // A timetable is the same hour on five days far more
                    // often than it is five different hours, and drawing the
                    // same block five times is the tedium this screen exists
                    // to remove.
                    Pill(text = "Copy to next day", selected = false) {
                        val copy = chosen.copy(day = chosen.day.plus(1))
                        selected = copy
                        commit(Windows.place(blocks, copy))
                    }
                    Pill(text = "Remove", selected = false) {
                        selected = null
                        commit(blocks.filterNot { it == chosen })
                    }
                }
            } else {
                SmallCopy(
                    if (blocks.isEmpty()) {
                        "Nothing yet. Drag one down from above, or double tap the " +
                            "week to plant your first."
                    } else {
                        "Drag one down from above, or double tap the week. Drag a " +
                            "block's bottom edge to make it longer, or drag it " +
                            "down into the bin."
                    },
                    size = 13,
                )
            }

            footer(blocks)
        }
    }
}

// The whole day, because the cue pipeline has no hour-of-day rule of its own
// and sleep is worth being able to mark. Lines and labels every three hours,
// as the frames have them.
private const val FIRST_HOUR = 0
private const val LAST_HOUR = 24
private const val LABEL_EVERY = 3
private const val SNAP_MINUTES = 30
private val HOUR_HEIGHT = 21.dp
private val GUTTER = 40.dp

/** Sunday first, as the frames draw it. */
private val DAYS = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

private enum class Grab { Move, ResizeEnd }

/**
 * How long a finger has to rest on empty ground before it starts planting.
 *
 * Only empty ground waits at all. Something already on the week moves the
 * instant you push it -- see [heldStill] for why the two are not the same
 * question.
 *
 * Short enough to feel like no wait, long enough that a tap is still a tap.
 * The floor here is a deliberate tap, which runs about 60-100ms: go under
 * that and every tap on an empty hour plants half an hour nobody asked for.
 *
 * This used to be done by handing the subtree a whole [androidx.compose.ui.platform.ViewConfiguration]
 * with a shorter `longPressTimeoutMillis`, so that `detectDragGesturesAfterLongPress`
 * would pick it up. That worked, but it put the number somewhere nobody would
 * look for it and applied it to every gesture in the subtree rather than the
 * one that wanted it.
 */
private const val PLANT_HOLD_MS = 90L

/**
 * Whether the finger stayed put long enough to mean "plant one here".
 *
 * Returns as soon as it knows: lifting or moving first is an answer, and only
 * running out of time is a hold. Moving first has to fall through untouched,
 * because a drag down empty ground is how the page is scrolled -- this grid is
 * twenty-four hours tall and lives inside a scrolling column.
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
 * The week, as something you draw on.
 *
 * Everything on it is painted onto one canvas rather than laid out as boxes,
 * because a thorn's spikes and a flower's head both sit outside their own time
 * span and a box would clip them.
 *
 * The block under the finger is carried separately from the rest of the list
 * for the length of a gesture. That is what lets [Windows.place] run once, at
 * the end, instead of reshuffling the list under a drag that is still going.
 */
@Composable
private fun WeekGrid(
    blocks: List<WeekBlock>,
    planting: BlockKind,
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
) {
    val hours = LAST_HOUR - FIRST_HOUR
    val density = LocalDensity.current

    // Raised while a block is being held below the foot of the grid. The bin
    // sits directly under it, so dragging something off the bottom of your
    // week is the gesture, and the pointer stays captured once a drag starts.
    var overBin by remember { mutableStateOf(false) }

    // Declared out here rather than inside the constraints scope, because the
    // modifier that measures it is on the box that opens that scope.
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(GUTTER))
            DAYS.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = skin.muted,
                    ),
                )
            }
        }
        Spacer(Modifier.size(6.dp))

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(HOUR_HEIGHT * hours)
                .onGloballyPositioned { gridOrigin = it.positionInRoot() },
        ) {
            val columnWidth = (maxWidth - GUTTER) / DAYS.size
            val colPx = with(density) { columnWidth.toPx() }
            val gutterPx = with(density) { GUTTER.toPx() }
            val hourPx = with(density) { HOUR_HEIGHT.toPx() }
            val edgePx = with(density) { 14.dp.toPx() }
            val slackPx = with(density) { 5.dp.toPx() }

            fun dayAt(x: Float): DayOfWeek =
                DAYS[((x - gutterPx) / colPx).toInt().coerceIn(0, DAYS.size - 1)]

            fun minuteAt(y: Float): Int {
                val raw = FIRST_HOUR * 60 + (y / hourPx) * 60f
                val snapped = (raw / SNAP_MINUTES).roundToInt() * SNAP_MINUTES
                return snapped.coerceIn(FIRST_HOUR * 60, LAST_HOUR * 60)
            }

            fun topOf(block: WeekBlock): Float =
                (block.start.toMinutes() - FIRST_HOUR * 60) / 60f * hourPx

            fun bottomOf(block: WeekBlock): Float =
                (block.end.toMinutes() - FIRST_HOUR * 60) / 60f * hourPx

            fun boxOf(block: WeekBlock): Rect {
                val x = gutterPx + colPx * DAYS.indexOf(block.day)
                return Rect(
                    left = x + colPx * 0.12f,
                    top = topOf(block),
                    right = x + colPx * 0.88f,
                    bottom = bottomOf(block),
                )
            }

            // Read through a snapshot rather than closing over `blocks`.
            //
            // pointerInput restarts whenever one of its keys changes, so
            // keying it on the list meant the first preview frame of a drag
            // tore down the detector that was producing it: a block could be
            // created but never sized, and every drag ended as a cancel.
            val latest by rememberUpdatedState(blocks)
            val nowPlanting by rememberUpdatedState(planting)

            fun hitTest(at: Offset): Pair<WeekBlock, Grab>? {
                latest.forEach { b ->
                    val x = gutterPx + colPx * DAYS.indexOf(b.day)
                    val top = topOf(b)
                    val bottom = bottomOf(b)
                    // A half-hour block is only ten points tall, so the hit
                    // box gets a little slack it does not draw.
                    if (at.x >= x && at.x < x + colPx &&
                        at.y >= top - slackPx && at.y <= bottom + slackPx
                    ) {
                        val handle = minOf(edgePx, (bottom - top) * 0.4f)
                        return b to if (at.y > bottom - handle) Grab.ResizeEnd else Grab.Move
                    }
                }
                return null
            }

            // Where this grid sits on the screen, so a finger that started
            // its journey on the palette can be found in the week.
            val origin = gridOrigin

            /**
             * What an hour let go of at this point would be, or null if the
             * point is not over the week.
             *
             * Shared by the ghost under the finger and by the drop itself, so
             * what you are shown while you carry it is by construction what
             * you get when you let go.
             */
            fun blockAt(root: Offset, kind: BlockKind): WeekBlock? {
                if (!root.isSpecified) return null
                val local = root - gridOrigin
                val right = gutterPx + colPx * DAYS.size
                if (local.x < gutterPx || local.x > right) return null
                if (local.y < -slackPx || local.y > hourPx * hours + slackPx) return null
                val start = minuteAt(local.y)
                val end = (start + 60).coerceAtMost(LAST_HOUR * 60)
                if (end <= start) return null
                return WeekBlock(
                    day = dayAt(local.x),
                    start = minutesToTime(start),
                    end = minutesToTime(end),
                    kind = kind,
                )
            }

            val ghost = carrying?.let { blockAt(carryAt, it) }

            // Let go. An hour lands wherever the finger was over the week, and
            // nothing happens if it was let go anywhere else -- the palette is
            // somewhere to pick one up from, not a bin.
            LaunchedEffect(dropped) {
                val drop = dropped ?: return@LaunchedEffect
                blockAt(drop.second, drop.first)?.let {
                    onCommit(Windows.place(latest, it), it)
                }
                onPlanted()
            }

            var carried by remember { mutableStateOf<WeekBlock?>(null) }
            var rest by remember { mutableStateOf<List<WeekBlock>>(emptyList()) }
            var grab by remember { mutableStateOf(Grab.Move) }
            var cursor by remember { mutableStateOf(Offset.Zero) }
            var grabOffset by remember { mutableStateOf(0) }

            Canvas(Modifier.fillMaxSize()) {
                // Warm bands down every other column, so seven narrow
                // columns can be counted without reading the labels.
                for (i in DAYS.indices) {
                    if (i % 2 == 1) {
                        drawRect(
                            color = skin.band,
                            topLeft = Offset(gutterPx + colPx * i, 0f),
                            size = Size(colPx, size.height),
                        )
                    }
                }

                val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)
                for (h in 0..hours step LABEL_EVERY) {
                    val y = h * hourPx
                    drawLine(
                        color = skin.line,
                        start = Offset(gutterPx, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.2f,
                        pathEffect = dash,
                    )
                }

                blocks.forEach { b ->
                    val box = boxOf(b)
                    when (b.kind) {
                        BlockKind.BUSY -> drawThorn(box)
                        BlockKind.FREE -> drawFlowerBlock(box)
                    }
                }

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
                        cornerRadius = CornerRadius(box.width * 0.3f),
                        style = Stroke(width = 2f),
                    )
                }

                selected?.takeIf { it in blocks }?.let { b ->
                    val box = boxOf(b)
                    drawRoundRect(
                        color = skin.ink,
                        topLeft = Offset(box.left - 3f, box.top - 3f),
                        size = Size(box.width + 6f, box.height + 6f),
                        cornerRadius = CornerRadius(box.width * 0.3f),
                        style = Stroke(width = 2f),
                    )
                }
            }

            for (h in FIRST_HOUR..LAST_HOUR step LABEL_EVERY) {
                Text(
                    clockLabel(h),
                    modifier = Modifier
                        .offset(y = HOUR_HEIGHT * (h - FIRST_HOUR) - 7.dp)
                        .width(GUTTER)
                        .padding(start = 2.dp, end = 6.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        color = skin.muted,
                    ),
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(colPx, hourPx) {
                        detectTapGestures(
                            onTap = { at -> onSelect(hitTest(at)?.first) },
                            // Two taps puts an hour down where you tapped.
                            // Press-and-drag still draws one at whatever
                            // length you like; this is the quick way to say
                            // "here", which is most of what anybody does.
                            onDoubleTap = { at ->
                                if (hitTest(at) == null) {
                                    val start = minuteAt(at.y)
                                    val end = (start + 60).coerceAtMost(LAST_HOUR * 60)
                                    if (end > start) {
                                        val block = WeekBlock(
                                            day = dayAt(at.x),
                                            start = minutesToTime(start),
                                            end = minutesToTime(end),
                                            kind = nowPlanting,
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
                    // Both went through `detectDragGesturesAfterLongPress`, so
                    // moving a block you could already see meant holding it
                    // first -- and once the hold was shortened to make planting
                    // feel quick, an ordinary tap on empty ground outlasted it
                    // and put down half an hour on the way past. Every tap
                    // planted something, and nothing could be moved without
                    // waiting for a timer first.
                    //
                    // Split in two. A block that already exists moves as soon
                    // as the finger moves, because there is nothing ambiguous
                    // about pushing something that is already there. Only empty
                    // ground waits, and only long enough to tell planting from
                    // scrolling the page.
                    .pointerInput(colPx, hourPx) {
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
                                // Plant one and start sizing it at once, so a
                                // single press-and-drag both puts it down and
                                // sets how long it runs.
                                val start = minuteAt(down.position.y)
                                val end = (start + SNAP_MINUTES).coerceAtMost(LAST_HOUR * 60)
                                if (end <= start) return@awaitEachGesture
                                rest = latest
                                carried = WeekBlock(
                                    day = dayAt(down.position.x),
                                    start = minutesToTime(start),
                                    end = minutesToTime(end),
                                    kind = nowPlanting,
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
                            }

                            // Absolute positions rather than accumulated
                            // deltas, so what is under the finger stays under
                            // the finger however long the drag runs.
                            val finished = drag(down.id) { change ->
                                change.consume()
                                val held = carried ?: return@drag
                                cursor = change.position
                                overBin = cursor.y > size.height
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
                                            day = dayAt(cursor.x),
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
                                // Dropped past the foot of the week: the bin
                                // takes it and nothing is planted. A gesture
                                // the system cancelled keeps its block instead,
                                // because losing work to a stray notification
                                // is worse than an unwanted half hour.
                                if (finished && overBin) onCommit(rest, null)
                                else onCommit(Windows.place(rest, it), it)
                            }
                            carried = null
                            overBin = false
                        }
                    },
            )
        }

        // The bin, directly under the foot of the week.
        //
        // Removing a block used to mean tapping it and then finding a pill
        // further down the page, which is a two-step answer to a one-step
        // thought. Dragging something off the bottom of your week and letting
        // go of it is the same gesture as throwing it away.
        Spacer(Modifier.size(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (overBin) skin.band else Color.Transparent)
                .border(
                    1.dp,
                    if (overBin) skin.ink else skin.line.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp),
                )
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(width = 13.dp, height = 15.dp)) {
                val w = size.width
                val h = size.height
                val ink = skin.muted
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
            Spacer(Modifier.size(8.dp))
            Text(
                if (overBin) "Let go to remove it" else "Drag one here to remove it",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = skin.muted,
                ),
            )
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
