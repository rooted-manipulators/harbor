package app.harbor.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.cue.Dialer
import app.harbor.data.HarborRepository
import app.harbor.domain.BlockKind
import app.harbor.domain.CallStats
import app.harbor.domain.Reminders
import app.harbor.domain.Contact
import app.harbor.domain.DayArcs
import app.harbor.domain.FlowerKind
import app.harbor.domain.Garden
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Moment
import app.harbor.domain.Resolution
import app.harbor.domain.TriggerSource
import app.harbor.domain.WeekBlock
import app.harbor.ui.theme.Avatar
import app.harbor.ui.theme.AvatarSize
import app.harbor.ui.theme.CardEdge
import app.harbor.ui.theme.LocalReducedMotion
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Gold
import app.harbor.ui.theme.Hairline
import app.harbor.ui.theme.Ink
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.SectionHeading
import app.harbor.ui.theme.SmallCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * One person: their face, the two ways to reach them, the day around a dial,
 * and what you have already done together.
 *
 * This is `conversation.tsx` reshaped rather than ported, per ADR-007. The
 * prototype's version is a two-way chat with a transcript — and there is no
 * second party in this build, so a transcript would be a wall of messages
 * nobody ever answered.
 *
 * ## The rewrite of 18 Sep 2026
 *
 * The page used to be a stack of cards: a header, a patch card, two text
 * links, a list of lines, a list of "lately". Six sections of prose about
 * somebody, and nothing you could *do* except read. It is now the four things
 * the frames have — who they are, how to reach them, their day, what has
 * happened — and the day is the new part.
 *
 * ## Whose day the dial shows, which matters more than how it looks
 *
 * **It is yours.** Harbor holds exactly one week: the one the user typed into
 * the schedule screen. It holds no calendar belonging to anybody else, and
 * `Windows`' own file says why in as many words — a card that quietly implied
 * Mum's evening was being read would be the single most damaging thing this
 * design could do.
 *
 * So the dial is *your* day, and the reminder is you setting yourself a time
 * to ring them. The heading says so.
 *
 * The seam for the other reading is already here and is one line wide. When a
 * contact is a linked Harbor account and they have said yes — [app.harbor.domain.Sharing],
 * ADR-013, which exists and has no caller yet — pass their week and their name
 * into [DayPanel] instead, and the same dial becomes theirs with an honest
 * heading. Nothing else on this screen changes.
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
    val haptics = LocalHapticFeedback.current
    val contacts by store.contacts.collectAsState()
    val week by store.weekBlocks.collectAsState()
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }

    LaunchedEffect(Unit) { entries = store.recentEntries() }

    val person = contacts.firstOrNull { it.id == contactId } ?: contacts.firstOrNull()

    if (person == null) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            SectionHeading("Nobody here yet.")
            SmallCopy("Add someone, and this becomes their page.")
            TextLink("Choose someone", onEdit)
        }
        return
    }

    val theirs = entries.filter { it.contactId == person.id }

    // A picture, handed straight to whatever sends it. Harbor deliberately
    // does not keep the image — same reasoning as the notes screen, which is
    // where this behaviour already lives: a snapshot is something you sent
    // somebody, not a record this app is owed.
    val pickPicture = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { picked ->
        if (picked != null) {
            scope.launch {
                store.append(sentSomething(store, person.id))
                store.note(Moment.PETAL_SENT, "picture")
                entries = store.recentEntries()
            }
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, picked)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Send your picture",
                ),
            )
        }
    }

    // The dial is raised over the page rather than opened in it, so the whole
    // screen is a box with an overlay in it. See [DayStage].
    var staged by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // No ground of its own: HarborShell paints the ground, and a
                // second opaque background here covers it.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Face(person, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Text(
                person.label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                if (person.phoneE164 != null) {
                    GlyphAction("Call", Glyph.Phone) {
                        Dialer.handOff(
                            context,
                            store,
                            scope,
                            person,
                            source = TriggerSource.MANUAL,
                        )
                    }
                }
                // Share is the send-a-line feature: a picture, or the writing
                // screen when there are words to find first.
                GlyphAction("Share", Glyph.Plane) { pickPicture.launch("image/*") }
            }

            Spacer(Modifier.height(26.dp))
            DayPanel(
                person = person,
                week = week,
                // Oldest first, and that ordering is load-bearing: a
                // flower's spot in the patch is worked out from its index, so
                // a list that put the newest at the top would shuffle every
                // flower somebody already had every time they made a call.
                // The garden sorts the same way for the same reason.
                flowers = theirs.sortedBy { it.occurredAt }.mapNotNull { it.flower },
                // The plan this person is already holding, as a minute of the
                // day. Reminders.holding is the same call the home card uses,
                // so the dial and the card cannot disagree about whether there
                // is one.
                reminderAt = Reminders.holding(theirs, Instant.now())
                    ?.proposedTime
                    ?.atZone(ZoneId.systemDefault())
                    ?.let { it.hour * 60 + it.minute },
                onStage = { staged = true },
            )

            Spacer(Modifier.height(26.dp))
            Text(
                "Recent Activity",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp, color = Chalk),
            )
            Spacer(Modifier.height(10.dp))

            if (theirs.isEmpty()) {
                SmallCopy("Nothing yet. A call or a line will show up here.")
            } else {
                var open by remember { mutableStateOf<UUID?>(null) }
                theirs.sortedByDescending { it.occurredAt }.take(12).forEach { entry ->
                    ActivityCard(
                        entry = entry,
                        open = open == entry.id,
                        onToggle = { open = if (open == entry.id) null else entry.id },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            TextLink("Leave a line", onLeaveLine)
            TextLink("Edit " + person.label, onEdit)
            Spacer(Modifier.height(24.dp))
        }

        if (staged) {
            DayStage(
                person = person,
                week = week,
                onBuzz = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                onClose = { staged = false },
                onSet = { minute ->
                    scope.launch {
                        store.append(reminderFor(store, person.id, minute))
                        store.note(Moment.REMINDER_SET, "person", minute)
                        entries = store.recentEntries()
                    }
                    staged = false
                },
            )
        }
    }
}

// --- the day -------------------------------------------------------------

/**
 * The dial or the flowers, and the switch between them.
 *
 * Two views of the same relationship: what the time around a call looks like,
 * and what the calls have left behind. The frames put them under one toggle
 * because they are the same question asked forwards and backwards.
 */
@Composable
private fun DayPanel(
    person: Contact,
    week: List<WeekBlock>,
    flowers: List<FlowerKind>,
    /**
     * The minute a reminder is already set for, if there is one.
     *
     * The small clock used to pass `reminder = null` unconditionally, so a
     * reminder somebody had just set left no mark anywhere on this screen --
     * the dial closed and looked exactly as it had before they touched it.
     * The plan was in the ledger the whole time; it was simply never drawn.
     */
    reminderAt: Int?,
    onStage: () -> Unit,
) {
    var showing by remember { mutableStateOf(Panel.Day) }
    val today = LocalDate.now().dayOfWeek
    val arcs = remember(week, today) { DayArcs.of(week, today) }
    val now = nowTicking()

    Text(
        when (showing) {
            // Not "their schedule". Harbor has one week and it is the user's
            // own — see the note at the top of this file, which is the whole
            // reason this line is worded the way it is.
            Panel.Day -> "Your day, and when you could call " + person.label
            Panel.Flowers -> "Flowers grown with " + person.label
        },
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = Muted),
    )
    Spacer(Modifier.height(14.dp))

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (showing) {
            Panel.Day -> Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .clip(CircleShape)
                    .clickable(onClick = onStage),
            ) {
                DayClock(arcs = arcs, now = now, reminder = reminderAt)
            }

            Panel.Flowers -> FlowersGrown(person, flowers, Modifier.fillMaxWidth(0.72f))
        }
    }

    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PanelToggle(showing) { showing = it }
    }

    if (showing == Panel.Day && arcs.isEmpty()) {
        Spacer(Modifier.height(10.dp))
        SmallCopy(
            "Nothing marked for today, so the dial is bare. Draw your week on " +
                "the schedule and the hours you are busy and free show up here.",
        )
    }
}

private enum class Panel { Day, Flowers }

/**
 * The dial, raised off the page with everything else turned down.
 *
 * A gradient behind it rather than a flat scrim: the clock is a lit object and
 * a flat wash over the page gave it nothing to be lit *against*. Darkest at the
 * edges, barely anything in the middle, so the eye lands where the dial is.
 *
 * The three states are the frames' three: looking at the day; having pressed a
 * free stretch, with the offer to put a reminder on it; and holding one, with
 * the time it is currently on.
 */
@Composable
private fun DayStage(
    person: Contact,
    week: List<WeekBlock>,
    onBuzz: () -> Unit,
    onClose: () -> Unit,
    onSet: (Int) -> Unit,
) {
    val today = LocalDate.now().dayOfWeek
    val arcs = remember(week, today) { DayArcs.of(week, today) }
    val now = nowTicking()

    var picked by remember { mutableStateOf<DayArcs.Arc?>(null) }
    var reminder by remember { mutableStateOf<Int?>(null) }
    var stirring by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xB3000000), Color(0xF5000000)),
                    radius = 1400f,
                ),
            )
            // No ripple: this is a press on a dark page to put the dial
            // away, not a button, and a ripple the width of the screen reads
            // as something having gone wrong.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val held = reminder
            if (held != null) {
                Text(
                    "Setting Reminder",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        color = Chalk,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    DayArcs.label(held),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp),
                )
                Spacer(Modifier.height(10.dp))
            } else if (picked != null) {
                // Sets it, rather than arming a second button that does.
                //
                // This used to place the marker and leave a "Done" chip
                // underneath as the thing that actually wrote it -- so closing
                // the dial in between, which is exactly what pressing "Set
                // reminder" reads as finishing, threw the plan away silently.
                // Two steps where the first already says it is done.
                //
                // Adjusting now means pressing another stretch rather than
                // dragging and confirming. That is a real loss of precision
                // and worth it: a control that looks finished and is not costs
                // more than one that is coarse.
                Chip("Set reminder") {
                    val arc = picked ?: return@Chip
                    onSet(DayArcs.rest(arc, arc.middle))
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Text(
                    "Press a flowering stretch to set yourself a reminder",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = Muted,
                    ),
                )
                Spacer(Modifier.height(10.dp))
            }

            DayClock(
                arcs = arcs,
                now = now,
                reminder = reminder,
                stirring = stirring,
                onPress = { minute ->
                    // Only a free stretch offers anything. Pressing a thorn or
                    // the unmarked hours clears the offer rather than arguing.
                    if (reminder == null) {
                        picked = DayArcs.arcAt(arcs, minute)
                            ?.takeIf { it.kind == BlockKind.FREE }
                    }
                },
                onDragTo = { wanted ->
                    val anchor = picked ?: return@DayClock
                    stirring = true
                    val rested = DayArcs.rest(anchor, wanted)
                    // The buzz is the difference between what the finger asked
                    // for and what the arc allowed. One rule covers both
                    // refusals the frames describe -- into a thorn, and out of
                    // the pink altogether -- because both arrive here as a
                    // minute this arc will not hold.
                    if (rested != DayArcs.snap(wanted)) onBuzz()
                    reminder = rested
                },
                onDragEnd = { stirring = false },
            )

            if (held != null) {
                Spacer(Modifier.height(16.dp))
                Chip("Done", filled = true) { onSet(held) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                person.label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = Muted),
            )
        }
    }
}

/** The clock's own minute, which has to move or the hands are a picture. */
@Composable
private fun nowTicking(): LocalTime {
    val time by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(20_000)
        }
    }
    return time
}

/** This person's flowers, clustered the way their patch in the garden is. */
@Composable
private fun FlowersGrown(
    person: Contact,
    flowers: List<FlowerKind>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, maxHeight)
        if (flowers.isEmpty()) {
            SmallCopy(
                "Nothing growing yet. A call with a flower on it starts this patch.",
                Modifier.padding(horizontal = 20.dp),
            )
            return@BoxWithConstraints
        }

        // The same glow the garden puts under a plot, so the two views are
        // recognisably the same patch seen from different distances.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3358803F), Color(0x0058803F)),
                    center = center,
                    radius = size.minDimension * 0.5f,
                ),
                radius = size.minDimension * 0.5f,
            )
        }

        val seed = remember(person.id) { Garden.hashOf(person.id.toString()) }
        val radius = side.value * 0.40
        flowers.forEachIndexed { index, kind ->
            // Worked out rather than remembered. `remember` inside a loop
            // keeps its slot by position, so it only holds while the list
            // never changes length in front of it -- and this is pure
            // arithmetic on an index, which is cheaper than the slot would be.
            val spot = Garden.flowerSpot(seed, index, radius)
            FlowerMark(
                kind,
                Modifier
                    .offset(x = spot.x.dp, y = spot.y.dp)
                    .size((side.value * 0.17f).coerceIn(18f, 34f).dp),
            )
        }
    }
}

/** The two ways to look at somebody. */
@Composable
private fun PanelToggle(showing: Panel, onPick: (Panel) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .border(1.dp, Hairline, RoundedCornerShape(99.dp)),
    ) {
        Panel.entries.forEach { panel ->
            val lit = panel == showing
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (lit) Gold.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPick(panel) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(17.dp)) {
                    val tint = if (lit) Gold else Muted
                    when (panel) {
                        Panel.Day -> {
                            drawCircle(tint, radius = size.minDimension / 2f - 1f, style = Stroke(1.6f))
                            drawLine(
                                tint,
                                start = center,
                                end = Offset(center.x, center.y - size.height * 0.27f),
                                strokeWidth = 1.6f,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                tint,
                                start = center,
                                end = Offset(center.x + size.width * 0.20f, center.y),
                                strokeWidth = 1.6f,
                                cap = StrokeCap.Round,
                            )
                        }

                        Panel.Flowers -> {
                            val r = size.minDimension * 0.17f
                            for (i in 0 until 5) {
                                val a = Math.toRadians(i * 72.0 - 90.0)
                                drawCircle(
                                    tint,
                                    radius = r,
                                    center = Offset(
                                        center.x + (r * 1.45f * Math.cos(a)).toFloat(),
                                        center.y + (r * 1.45f * Math.sin(a)).toFloat(),
                                    ),
                                    style = Stroke(1.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- the header ----------------------------------------------------------

/** Their photograph if Harbor has one, their initials if not. */
@Composable
private fun Face(person: Contact, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, person.photoRef) {
        val ref = person.photoRef
        value = if (ref == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(ref)).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    val face = photo
    if (face == null) {
        Avatar(person.label, person.tone, modifier, size = AvatarSize.XL)
    } else {
        Image(
            bitmap = face,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(96.dp).clip(CircleShape),
        )
    }
}

private enum class Glyph { Phone, Plane }

/** An outlined pill with a drawn mark on it. The frames' Call and Share. */
@Composable
private fun GlyphAction(label: String, glyph: Glyph, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .border(1.dp, Hairline, RoundedCornerShape(99.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(15.dp)) {
            when (glyph) {
                // A handset held at an angle: a rounded bar across the
                // diagonal with an ear at each end. Drawn from a rotated
                // rectangle and two discs rather than from curves, because the
                // curve call in this library has been renamed once already and
                // a fifteen-point glyph is not worth a deprecation.
                // A handset, the same one the reminder draws.
                //
                // This used to be a thin rounded rect with a disc at each end,
                // rotated 34 degrees. That is a handset in principle; at 18dp
                // with a 1.5px stroke it is a pen, and it sat next to the word
                // "Call" on the primary action of the screen. The cue surface
                // already had a handset that reads correctly at this size, so
                // this is that path rather than a third attempt.
                Glyph.Phone -> {
                    val s = size.width
                    drawPath(
                        Path().apply {
                            moveTo(s * 0.26f, s * 0.12f)
                            lineTo(s * 0.44f, s * 0.12f)
                            lineTo(s * 0.52f, s * 0.36f)
                            lineTo(s * 0.38f, s * 0.46f)
                            cubicTo(s * 0.46f, s * 0.64f, s * 0.58f, s * 0.74f, s * 0.72f, s * 0.8f)
                            lineTo(s * 0.82f, s * 0.66f)
                            lineTo(s * 0.94f, s * 0.76f)
                            lineTo(s * 0.94f, s * 0.92f)
                            cubicTo(s * 0.6f, s * 0.94f, s * 0.22f, s * 0.56f, s * 0.26f, s * 0.12f)
                        },
                        color = Chalk,
                        style = Stroke(1.6f),
                    )
                }

                Glyph.Plane -> {
                    val p = Path().apply {
                        moveTo(size.width * 0.06f, size.height * 0.46f)
                        lineTo(size.width * 0.94f, size.height * 0.08f)
                        lineTo(size.width * 0.58f, size.height * 0.94f)
                        lineTo(size.width * 0.46f, size.height * 0.56f)
                        close()
                    }
                    drawPath(p, Chalk, style = Stroke(1.5f))
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, color = Chalk),
        )
    }
}

/** A small pressable label. Filled when it is the one thing to do. */
@Composable
private fun Chip(label: String, filled: Boolean = false, onClick: () -> Unit) = Box(
    Modifier
        .clip(RoundedCornerShape(99.dp))
        .background(if (filled) Gold else Color(0x1FFFFFFF))
        .border(1.dp, if (filled) Color.Transparent else Hairline, RoundedCornerShape(99.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 10.dp),
) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = 14.sp,
            color = if (filled) Ink else Chalk,
        ),
    )
}

// --- what has happened ---------------------------------------------------

/**
 * One thing that happened, and everything about it if you ask.
 *
 * Closed, it is the line the frames show: what it was, when, and a flower if
 * the call grew one. Open, it is whatever else the ledger actually holds —
 * the words of a line, how long a call ran, what it was about. Never more than
 * that: there is no photograph to show, because Harbor does not keep the
 * pictures it helps you send, and inventing a thumbnail would be a lie about
 * what this app stores.
 */
@Composable
private fun ActivityCard(entry: LedgerEntry, open: Boolean, onToggle: () -> Unit) {
    val more = entry.note?.isNotBlank() == true ||
        entry.callMinutes != null ||
        entry.topic != null ||
        entry.proposedTime != null

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, CardEdge, RoundedCornerShape(18.dp))
            .clickable(enabled = more, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 14.sp,
                            color = Chalk,
                        ),
                    )
                    Spacer(Modifier.size(8.dp))
                    // The time, not just the day.
                    //
                    // Six things can happen to one person in an afternoon, and
                    // dated to the day they were six identical rows: "Made room
                    // to talk, 18 Sept '26" three times over. The clock is what
                    // makes a log a log -- and it is the difference between
                    // rows that only some of which will open.
                    Text(
                        entry.occurredAt.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm")),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Muted,
                        ),
                    )
                }
                entry.callMinutes?.let {
                    Text(
                        "lasted " + CallStats.formatDuration(it),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Muted,
                        ),
                    )
                }
            }
            val flower = entry.flower
            if (flower != null) {
                Box(Modifier.size(28.dp)) { FlowerMark(flower, Modifier.fillMaxSize()) }
            } else if (more) {
                Canvas(Modifier.size(16.dp)) {
                    // A solid caret rather than a chevron, as the frames draw
                    // it: pointing down when there is more, up when it is open.
                    val p = Path().apply {
                        if (open) {
                            moveTo(size.width * 0.5f, size.height * 0.28f)
                            lineTo(size.width * 0.92f, size.height * 0.70f)
                            lineTo(size.width * 0.08f, size.height * 0.70f)
                        } else {
                            moveTo(size.width * 0.5f, size.height * 0.72f)
                            lineTo(size.width * 0.92f, size.height * 0.30f)
                            lineTo(size.width * 0.08f, size.height * 0.30f)
                        }
                        close()
                    }
                    drawPath(p, Muted)
                }
            }
        }

        // The row growing is movement; the text arriving is not. Somebody
        // who asked for less motion still gets the fade, so the disclosure
        // does not blink into existence -- it just does not push the rows
        // below it down over two hundred milliseconds.
        val still = LocalReducedMotion.current
        AnimatedVisibility(
            visible = open,
            enter = if (still) fadeIn(tween(120)) else expandVertically(tween(180)) + fadeIn(tween(180)),
            exit = if (still) fadeOut(tween(100)) else shrinkVertically(tween(140)) + fadeOut(tween(140)),
        ) {
            Column(Modifier.padding(top = 10.dp)) {
                entry.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "“" + it + "”",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = Chalk,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                entry.topic?.let { SmallCopy("About " + it, size = 12) }
                entry.proposedTime?.let {
                    SmallCopy(
                        "You planned " + it.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) +
                            if (entry.reminderDone) ". Closed." else ".",
                        size = 12,
                    )
                }
                SmallCopy(
                    entry.occurredAt.atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm")) + " · kept on this phone",
                    size = 12,
                )
            }
        }
    }
}

/**
 * What a row is called, in the frames' voice.
 *
 * "Afternoon Call" rather than "You called", because this is a log and a log
 * is read by scanning down the left edge of it. The time of day is part of how
 * anybody remembers which call it was.
 */
private val LedgerEntry.title: String
    get() {
        val hour = occurredAt.atZone(ZoneId.systemDefault()).hour
        val part = when {
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            else -> "Evening"
        }
        // Sentence case, against the frames.
        //
        // These were Title Case because the frames were, and they were the
        // only Title Case strings in the app -- every other screen writes like
        // a person talking. Six of them stacked in a list read like proper
        // nouns, as though "Kept the Quiet" were the name of something rather
        // than a description of an evening.
        return when (resolution) {
            Resolution.CALLED -> "$part call"
            Resolution.MESSAGE -> if (note.isNullOrBlank()) "Sent a photograph" else "Left a line"
            Resolution.REACTED -> "Sent a little love"
            Resolution.PLAYED -> "Played the daily question"
            Resolution.PROPOSED_LATER -> "Made room to talk"
            Resolution.DISMISSED -> "Kept the quiet"
            // Never "you failed to reach them". They went to call, which is
            // the part this app is trying to encourage.
            Resolution.NOT_REACHED -> "Tried to call"
        }
    }

// --- writing things down -------------------------------------------------

/** A picture went out. The ledger holds that it happened, and nothing else. */
private fun sentSomething(store: HarborRepository, contactId: UUID): LedgerEntry {
    val now = Instant.now()
    return LedgerEntry(
        id = UUID.randomUUID(),
        entryDate = now.atZone(ZoneId.systemDefault()).toLocalDate(),
        cueId = null,
        contactId = contactId,
        triggerSource = TriggerSource.NOTE,
        thresholdSnapshot = store.settings.value.thresholds,
        resolution = Resolution.MESSAGE,
        proposedTime = null,
        feedbackPulse = null,
        callMinutes = null,
        feeling = null,
        flower = null,
        topic = null,
        occurredAt = now,
    )
}

/**
 * A reminder the user set for themselves, as the row the rest of the app
 * already understands.
 *
 * Deliberately the same [Resolution.PROPOSED_LATER] row that tapping "later"
 * on a cue writes, rather than a new kind of thing. Everything downstream is
 * then free: `Reminders.holding` stands sensed reminders down while it is
 * live, `Reminders.due` puts the card on home when its time comes, and the
 * study's export counts it with the others. A second mechanism would have had
 * to re-earn all three.
 *
 * A time already gone today means tomorrow. Setting a reminder for nine in the
 * morning at lunchtime obviously means the next one.
 */
private fun reminderFor(store: HarborRepository, contactId: UUID, minute: Int): LedgerEntry {
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val today = now.atZone(zone).with(DayArcs.at(minute))
    val at = if (today.toInstant().isAfter(now)) today else today.plusDays(1)
    return LedgerEntry(
        id = UUID.randomUUID(),
        entryDate = now.atZone(zone).toLocalDate(),
        cueId = null,
        contactId = contactId,
        triggerSource = TriggerSource.MANUAL,
        thresholdSnapshot = store.settings.value.thresholds,
        resolution = Resolution.PROPOSED_LATER,
        proposedTime = at.toInstant(),
        feedbackPulse = null,
        callMinutes = null,
        feeling = null,
        flower = null,
        topic = null,
        occurredAt = now,
    )
}
