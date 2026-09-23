package app.harbor.ui

import app.harbor.domain.TourStop
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import app.harbor.data.CalendarReader
import app.harbor.domain.CalendarPull
import app.harbor.ui.theme.Motion
import app.harbor.ui.theme.Space
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import app.harbor.ui.theme.LocalReducedMotion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.data.HarborRepository
import app.harbor.domain.BlockKind
import app.harbor.domain.BlockOrigin
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
import app.harbor.ui.theme.rememberTick
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

    // The nights, the first time anybody opens a week.
    //
    // Here rather than at install, so they arrive on a screen where they can
    // be seen and argued with. Both editors run through this composable, so
    // whichever one somebody reaches first is the one that seeds them, and
    // the marker means it never happens twice. See Windows.quietNights.
    LaunchedEffect(Unit) { store.seedQuietNightsOnce() }

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
    /**
     * Where the day card stops, in root coordinates.
     *
     * The bin used to arm on a flat 220dp band at the foot of the screen, and
     * the card runs straight through it -- so the day's last hours *were* the
     * bin, and placing anything in the evening threw it away. The comment
     * defending the band argued a thumb could not arrive there by accident
     * "because the band is only live while something is being carried", which
     * is exactly backwards: you are always carrying something when you place
     * it at ten at night.
     *
     * Measured rather than guessed, so the bin cannot creep back over the
     * card when the header or the hour rows change height.
     */
    var cardBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var leftTheDay by remember { mutableStateOf(false) }
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }

    // Which day is under the finger. Today, until somebody says otherwise.
    val today = remember { LocalDate.now() }
    var showing by remember { mutableStateOf(today) }
    // The swipe lives here rather than in the board, because the strip of
    // days has to move with the cards and the two are siblings. One
    // Animatable, read by both, so they cannot drift apart by a frame.
    //
    // It carries a *fraction* of one day, -1 to 1, not pixels. The cards and
    // the strip are different widths, so pixels would have to be converted
    // anyway -- and the first attempt passed the card's pixel step upwards
    // from inside the board's layout, which meant writing the parent's state
    // during the child's composition. That silently stayed zero, and a zero
    // step is a swipe that cannot move.
    val slide = remember { Animatable(0f) }

    // Which of the two editors is showing.
    //
    // They are two readings of one week rather than two features: the day is
    // for drawing a day properly, the grid is for seeing the shape of the
    // week at once. Neither is a better version of the other, which is why
    // this is a switch and not a replacement.
    //
    // Not remembered across visits on purpose. It is one tap to change, and a
    // Schedule tab that opened in whichever view you last used would be a
    // screen that greets different people differently for no reason they
    // could name.
    //
    // The week first: the whole shape at a glance is what somebody opening
    // their schedule wants to see, and the day is one tap in.
    var view by remember { mutableStateOf(WeekView.Week) }

    // The tour turns to whichever view holds what it is about to point at:
    // quiet hours live on the day, everything else it shows is on the week.
    // Outside a tour this reads null and does nothing.
    val touringAt = LocalTourStop.current
    LaunchedEffect(touringAt) {
        when (touringAt) {
            TourStop.SCHEDULE_QUIET -> view = WeekView.Day
            TourStop.SCHEDULE_VIEWS,
            TourStop.SCHEDULE_PALETTE,
            TourStop.SCHEDULE_COPY,
            TourStop.SCHEDULE_CALENDAR,
            -> view = WeekView.Week
            else -> Unit
        }
    }

    val blocks = draft ?: saved

    // The last one-tap change and what the week was before it, for the undo
    // bar. Cleared by itself after a few seconds, or by the next change.
    var undo by remember { mutableStateOf<Pair<String, List<WeekBlock>?>?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(UNDO_MS)
            undo = null
        }
    }

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

    fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun copyOn(from: DayOfWeek) {
        val to = from.plus(1)
        val before = blocks
        selected = null
        commit(Windows.copyDay(blocks, from, to), why = "copied")
        undo = "${dayName(from)} copied to ${dayName(to)}" to before
    }

    // Pulling from the calendar. The permission is asked for here, at the
    // press, and nowhere else (ADR-015).
    val context = LocalContext.current
    fun pull() {
        scope.launch {
            val events = CalendarReader.nextWeek(context)
            val pulled = events?.let { CalendarPull.blocks(it, LocalDate.now()) }.orEmpty()
            if (pulled.isEmpty()) {
                undo = (if (events == null) "Couldn't read your calendar" else "Nothing on your calendar this week") to null
            } else {
                // The stored week, not the one this closure saw: the read
                // above suspended, and the screen may have moved on.
                val before = store.weekBlocks.value
                selected = null
                commit(CalendarPull.merge(before, pulled), why = "calendar")
                undo = "${pulled.size} added from your calendar" to before
            }
        }
    }
    val askCalendar = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pull() else undo = "Calendar not allowed" to null
    }
    fun pullFromCalendar() {
        val have = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (have) pull() else askCalendar.launch(Manifest.permission.READ_CALENDAR)
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
            // Below the day, or out of it sideways. Never *inside* it.
            leftTheDay || at.y > cardBottom
        } ?: false

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Flow(Modifier.pageContent()) {
                header(blocks)

                Row(
                    Modifier.fillMaxWidth().tourAnchor(TourStop.SCHEDULE_PALETTE),
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

                // The month button is gone. It named the month you were
                // already looking at and opened a picker for jumping to
                // another one -- a lot of chrome above a control whose whole
                // job is the next day and the one before it. The dates on the
                // strip say the same thing in the place you are already
                // reading.
                ViewSwitch(view, skin, Modifier.tourAnchor(TourStop.SCHEDULE_VIEWS)) { view = it }

                if (view == WeekView.Day) {
                    QuietRow(
                        Windows.quietPeriod(blocks),
                        skin,
                        Modifier.tourAnchor(TourStop.SCHEDULE_QUIET),
                    ) { from, to ->
                        selected = null
                        commit(Windows.setQuiet(blocks, from, to), why = "quiet")
                    }
                    DayStrip(showing, skin, slide) { showing = it }
                    ToolPill(
                        "Copy to ${dayName(showing.dayOfWeek.plus(1))}",
                        skin,
                        icon = { CopyMark(skin.ink) },
                    ) { copyOn(showing.dayOfWeek) }
                }

                if (view == WeekView.Week) {
                    WeekGrid(
                        chosen = showing.dayOfWeek,
                        onChoose = { d ->
                            // The same date the day view will open on, so
                            // choosing Thursday here and switching to the day
                            // lands on Thursday.
                            val ahead = (d.value - today.dayOfWeek.value + 7) % 7
                            showing = today.plusDays(ahead.toLong())
                        },
                        blocks = blocks,
                        planting = planting ?: BlockKind.BUSY,
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
                    // The week's tools, at its foot: carry the chosen day
                    // on, or fill the week from the phone's calendar.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.one),
                    ) {
                        ToolPill(
                            "${dayName(showing.dayOfWeek)} → ${dayName(showing.dayOfWeek.plus(1))}",
                            skin,
                            modifier = Modifier.weight(1f).tourAnchor(TourStop.SCHEDULE_COPY),
                            icon = { CopyMark(skin.ink) },
                        ) { copyOn(showing.dayOfWeek) }
                        ToolPill(
                            "From calendar",
                            skin,
                            modifier = Modifier.weight(1f).tourAnchor(TourStop.SCHEDULE_CALENDAR),
                            icon = { CalendarMark(skin.ink) },
                        ) { pullFromCalendar() }
                    }
                } else {
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
                        slide = slide,
                        modifier = Modifier.onGloballyPositioned {
                            cardBottom = it.positionInRoot().y + it.size.height
                        },
                    )

                    // At the day's own foot, the way the week's tools sit at
                    // the week's. Thorns and blooms both go -- a blank day
                    // to start over on, not one half-cleared.
                    ToolPill(
                        "Clear ${dayName(showing.dayOfWeek)}",
                        skin,
                        icon = { ClearMark(skin.ink) },
                    ) {
                        val cleared = Windows.clearDay(blocks, showing.dayOfWeek)
                        if (cleared.size != blocks.size) {
                            val before = blocks
                            selected = null
                            commit(cleared, why = "cleared")
                            undo = "${dayName(showing.dayOfWeek)} cleared" to before
                        }
                    }
                }

                // One line each. The gestures teach themselves after the
                // first go; this only has to get somebody to that first go.
                SmallCopy(
                    if (planting == null) {
                        "Pick thorns or flowers, then press the day."
                    } else {
                        "Drag an edge to stretch. Drag off the day to bin."
                    },
                    size = 13,
                )

                footer(blocks)
            }
        }

        UndoBar(
            message = undo?.first.takeIf { dragAt == null },
            onUndo = undo?.second?.let { before ->
                {
                    selected = null
                    commit(before, why = "undone")
                    undo = null
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Space.four + Space.four + Space.two),
        )

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

    }
}

// The whole day, because the cue pipeline has no hour-of-day rule of its own
// and sleep is worth being able to mark. A line every hour, a label every two,
// as the frames have them.
private const val FIRST_HOUR = 0
private const val LAST_HOUR = 24
private const val LABEL_EVERY = 2
private const val SNAP_MINUTES = 30
/**
 * Sunday first, as the frames draw it.
 *
 * Restored with WeekGrid. It went out with the week view and is the only
 * thing that view needed which the day view did not.
 */
private val DAYS = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

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
 * Completing a swipe: brisk, and with no bounce at the end.
 *
 * The card is going somewhere it was always going to go, so the motion should
 * read as arrival rather than as a decision. An overshoot here would suggest
 * the day had been thrown.
 */
private val DAY_SETTLE = tween<Float>(durationMillis = 230, easing = FastOutSlowInEasing)

/**
 * Falling short: a spring, with enough give to be felt.
 *
 * This is the one that has to be nice. A swipe that did not reach the
 * threshold is the app declining, and a linear slide back reads as a refusal
 * where a spring reads as the card simply settling where it belongs.
 */
private val DAY_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

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

/** How long the undo bar stays up. Long enough to read and reach. */
private const val UNDO_MS = 5000L

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
    /** The swipe as a fraction of one day, shared with the strip. */
    slide: Animatable<Float, *>,
    modifier: Modifier = Modifier,
) {
    val hours = LAST_HOUR - FIRST_HOUR
    val density = LocalDensity.current

    // One card's travel. Local, and never leaves: the fraction is what the
    // strip needs, and the fraction is what slide carries.
    var stepPx by remember { mutableFloatStateOf(0f) }
    val swipes = rememberCoroutineScope()
    val stillness = LocalReducedMotion.current
    // The detent, not the thud. See ui/theme/Buzz.kt: LongPress is a
    // heavy single bump meant to say "you have held this long enough",
    // and on a swipe it lands after the motion and reads as a complaint.
    val buzz = rememberTick()
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
        modifier
            .fillMaxWidth()
            .height(CARD_HEAD + hoursHeight + CARD_FOOT)
            // Scrolling sideways is the other way to reach a day, and it only
            // ever sees the gestures the day itself did not want: a press on a
            // block consumes, and a press on empty ground that moves before the
            // plant hold is up does not.
            // The day follows the finger and then keeps going.
            //
            // It used to count how far a drag had travelled and swap the day
            // on release -- nothing moved until the new day was simply
            // *there*. The neighbours were already drawn a card's width to
            // either side, which is a carousel in every respect except that
            // it never moved, so this is mostly a matter of letting it.
            //
            // The slide carries all three cards, so the day you are pulling
            // towards arrives from where you can already see it waiting. Past
            // the threshold it completes on its own; short of it, it springs
            // back with a little overshoot, which is the difference between a
            // control that refused you and one that simply did not agree.
            .pointerInput(showing, stepPx, stillness) {
                val step = stepPx
                val far = if (step > 0f) SWIPE_DAY.toPx() / step else Float.MAX_VALUE
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val went = slide.value
                        swipes.launch {
                            when {
                                step <= 0f -> slide.snapTo(0f)
                                went <= -far -> {
                                    if (!stillness) slide.animateTo(-1f, DAY_SETTLE)
                                    // Felt at the moment it lands, not when
                                    // the finger lifts: the point of it is to
                                    // confirm the day changed, and the day
                                    // changes here.
                                    buzz()
                                    onShow(showing.plusDays(1))
                                    slide.snapTo(0f)
                                }
                                went >= far -> {
                                    if (!stillness) slide.animateTo(1f, DAY_SETTLE)
                                    buzz()
                                    onShow(showing.minusDays(1))
                                    slide.snapTo(0f)
                                }
                                stillness -> slide.snapTo(0f)
                                else -> slide.animateTo(0f, DAY_SPRING)
                            }
                        }
                    },
                    onDragCancel = { swipes.launch { slide.animateTo(0f, DAY_SPRING) } },
                ) { _, delta ->
                    if (step > 0f) {
                        swipes.launch {
                            // Never further than one day either way: the strip
                            // holds three cards, and dragging past the third
                            // would pull emptiness in behind it.
                            slide.snapTo((slide.value + delta / step).coerceIn(-1f, 1f))
                        }
                    }
                }
            },
    ) {
        val cardWidth = maxWidth * CARD_SHARE
        val widthPx = with(density) { cardWidth.toPx() }
        // The same distance the neighbours are offset by, so a completed
        // swipe lands the next card exactly where the centre one was.
        stepPx = with(density) { (cardWidth + 16.dp).toPx() }
        val gutterPx = with(density) { GUTTER.toPx() }
        val hourPx = with(density) { HOUR_HEIGHT.toPx() }
        val insetPx = with(density) { 7.dp.toPx() }
        val edgePx = with(density) { 14.dp.toPx() }
        val slackPx = with(density) { 5.dp.toPx() }

        // The days either side, peeking in at the edges. They are the week you
        // are still in; the frames show them dimmed and mostly off-screen, and
        // a press on the sliver is the third way to change day.
        // How far through a swipe we are, -1 to 1. The card being pulled
        // towards brightens as it comes and the middle one dims to meet it,
        // so the moment the day commits nothing has to change.
        val progress = slide.value.coerceIn(-1f, 1f)

        NeighbourDay(
            blocks = blocks,
            day = showing.minusDays(1).dayOfWeek,
            skin = skin,
            width = cardWidth,
            presence = 0.5f + 0.5f * progress.coerceAtLeast(0f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -(cardWidth + 16.dp))
                .offset { IntOffset((slide.value * stepPx).roundToInt(), 0) },
            onClick = { onShow(showing.minusDays(1)) },
        )
        NeighbourDay(
            blocks = blocks,
            day = showing.plusDays(1).dayOfWeek,
            skin = skin,
            width = cardWidth,
            presence = 0.5f + 0.5f * (-progress).coerceAtLeast(0f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = cardWidth + 16.dp)
                .offset { IntOffset((slide.value * stepPx).roundToInt(), 0) },
            onClick = { onShow(showing.plusDays(1)) },
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .width(cardWidth)
                .fillMaxHeight()
                .offset { IntOffset((slide.value * stepPx).roundToInt(), 0) }
                // Dims on its way out by exactly what the incoming card
                // gains, so the two cross over rather than swapping.
                .alpha(1f - 0.5f * kotlin.math.abs(progress))
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
                            .padding(start = 8.dp, end = 8.dp),
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
                            .padding(horizontal = 20.dp, vertical = 16.dp),
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
    /**
     * How present this day is, 0.5 at rest and 1 when it has arrived.
     *
     * Interpolated by the caller from the swipe, so a card that slides into
     * the middle is already at full strength by the time it becomes the
     * centre one. It used to be a flat 0.5 and the day changed underneath it,
     * which meant every swipe ended on a step from half to whole.
     */
    presence: Float,
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
            // Full height, like the middle one.
            //
            // This was 0.9f, so a card that slid into the centre grew by a
            // tenth at the instant the day committed -- the jolt at the end of
            // every swipe. A carousel only reads as one if the thing arriving
            // is the same size as the thing leaving.
            .fillMaxHeight()
            .clip(DayCardShape)
            .background(skin.card)
            .border(1.dp, skin.line.copy(alpha = 0.3f), DayCardShape)
            .clickable(onClick = onClick)
            .alpha(presence),
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
            BlockKind.BUSY -> drawThorn(box, whatsapp = b.origin == BlockOrigin.WHATSAPP)
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
 * The days around the one you are on.
 *
 * Two either side, the middle one lit, arrows at the ends. The frames dim the
 * outermost pair, which is what keeps five day names from reading as a tab bar
 * with five equal tabs.
 */
@Composable
private fun DayStrip(
    showing: LocalDate,
    skin: WeekSkin,
    /** The cards' swipe, as a fraction of one day. The strip travels with it. */
    slide: Animatable<Float, *>,
    onPick: (LocalDate) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StripArrow(back = true, skin = skin) { onPick(showing.minusDays(1)) }
        BoxWithConstraints(Modifier.weight(1f)) {
            // One day's width on the strip, which is not one day's width on
            // the cards -- so the strip is moved by the *fraction* of a swipe
            // rather than by its pixels. A whole swipe moves the strip exactly
            // one name, which is what makes the two read as one gesture.
            val pitch = with(LocalDensity.current) {
                (maxWidth / (STRIP_REACH * 2 + 1)).toPx()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .offset {
                        IntOffset((slide.value.coerceIn(-1f, 1f) * pitch).roundToInt(), 0)
                    },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (step in -STRIP_REACH..STRIP_REACH) {
                    val date = showing.plusDays(step.toLong())
                    val chosen = step == 0
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (chosen) skin.tile else Color.Transparent)
                            .clickable { onPick(date) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val ink = when {
                            chosen -> skin.ink
                            abs(step) == STRIP_REACH -> skin.muted.copy(alpha = 0.5f)
                            else -> skin.muted
                        }
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                color = ink,
                            ),
                        )
                        // The date under the name, which is what the month
                        // button used to be for. Smaller and quieter: you
                        // read the day first and check the number second.
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = ink.copy(alpha = if (chosen) 0.75f else 0.55f),
                            ),
                        )
                    }
                }
            }
        }
        StripArrow(back = false, skin = skin) { onPick(showing.plusDays(1)) }
    }
}

/** Which reading of the week is on screen. */
private enum class WeekView { Day, Week }

/**
 * The switch between the day and the week.
 *
 * Two words in a pill rather than an icon pair, because "day" and "week" are
 * short, exact, and impossible to mistake for each other -- where a calendar
 * glyph and a grid glyph are two rectangles with lines in them.
 *
 * ## The colour
 *
 * Amber on the chosen half, dark ink on top of it. `docs/09-master-context.md`
 * gives the accent to exactly three things -- the current tab, the primary
 * action, and the selected chip -- and this is the third of those. The first
 * pass used the neutral tile the day strip uses for its chosen day, which was
 * consistent with the strip and wrong for the design: two greys a shade apart
 * is not a choice you can see across a room, and a control nobody notices is
 * a view nobody knows they can change.
 *
 * The track around it is a hairline rather than a fill, so the pair reads as
 * a control rather than as another card on a screen that already has several.
 */
@Composable
private fun ViewSwitch(
    view: WeekView,
    skin: WeekSkin,
    modifier: Modifier = Modifier,
    onPick: (WeekView) -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .border(1.dp, skin.line.copy(alpha = 0.7f), RoundedCornerShape(99.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        WeekView.entries.forEach { option ->
            val here = option == view
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (here) Gold else Color.Transparent)
                    .clickable { onPick(option) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    if (option == WeekView.Day) "Day" else "Week",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
                        // Ink on amber, not the skin's ink: the pill is the
                        // same amber in both dressings, so the text on it has
                        // to be the same dark in both too.
                        color = if (here) Ink else skin.muted,
                    ),
                )
            }
        }
    }
}

/** Drawn geometry, not an icon. See `drawTabMark` for the pattern. */
@Composable
private fun StripArrow(back: Boolean, skin: WeekSkin, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
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

    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                " " + month.year,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
        .padding(horizontal = 24.dp, vertical = 8.dp),
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
    /** The day the tools under the grid act on. Lit in the header. */
    chosen: DayOfWeek,
    onChoose: (DayOfWeek) -> Unit,
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
                val lit = d == chosen
                val fill by animateColorAsState(
                    if (lit) skin.tile else Color.Transparent,
                    Motion.normal(),
                    label = "day",
                )
                Text(
                    d.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(fill)
                        .clickable { onChoose(d) }
                        .padding(vertical = Space.half),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                        color = if (lit) skin.ink else skin.muted,
                    ),
                )
            }
        }
        Spacer(Modifier.size(8.dp))

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
                        BlockKind.BUSY -> drawThorn(box, whatsapp = b.origin == BlockOrigin.WHATSAPP)
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
                        .padding(start = 2.dp, end = 8.dp),
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
                .padding(vertical = 8.dp),
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
