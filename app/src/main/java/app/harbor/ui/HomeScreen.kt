package app.harbor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import app.harbor.ui.theme.LocalReducedMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import app.harbor.ui.theme.Paper
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harbor.cue.Dialer
import app.harbor.data.HarborRepository
import app.harbor.domain.CallStats
import app.harbor.domain.Contact
import app.harbor.domain.FlowerKind
import app.harbor.domain.Flowers
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Reminders
import app.harbor.domain.Resolution
import app.harbor.ui.theme.CardEdge
import app.harbor.ui.theme.Ember
import app.harbor.ui.theme.EmberLight
import app.harbor.ui.theme.Eyebrow
import app.harbor.ui.theme.Flow
import app.harbor.ui.theme.PrimaryAction
import app.harbor.ui.theme.QuietAction
import app.harbor.ui.theme.SectionHeading
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import app.harbor.ui.theme.pageContent
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Home, hand-translated from `components/harbor/home.tsx`.
 *
 * The garden sits high because it is the point of the app rather than a page
 * you navigate to — opening Harbor should show you what calling people has
 * grown, not a console for an app.
 *
 * But the prototype's order put how-life-feels between the garden and your
 * people, and on a real phone that pushed the call button — the single thing
 * this whole product exists to make easy — below the fold. So your people now
 * come straight after the garden, the greeting is one line shorter, the field
 * is shorter, and the weather follows rather than interrupts. Nothing was
 * removed; the one thing you might open Harbor to do is simply above the fold
 * now, which it was not.
 */
@Composable
fun HomeScreen(
    store: HarborRepository,
    onOpenGarden: () -> Unit,
    onOpenCues: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenPerson: (java.util.UUID) -> Unit,
    onReflect: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** The flower just planted, which home opens where the camera lands. */
    growing: FlowerKind? = null,
    /** Called when it has finished opening, so home goes back to being home. */
    onGrown: () -> Unit = {},
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState()
    val contacts by store.contacts.collectAsState()
    var entries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }

    LaunchedEffect(Unit) { entries = store.recentEntries() }

    val grown = entries
        .filter { it.resolution == Resolution.CALLED && it.flower != null }
        .sumOf { Flowers.flowerCount(it.callMinutes) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The weather, as the ground of the whole screen.
        //
        // Home is the screen the field lives on, so home is the screen the
        // weather owns. Everything below is drawn over it, and the field adds
        // only its terrain -- no second sky, no box, no seam.
        FieldSky(settings.weather, Modifier.fillMaxSize())

        // How tall the field can be, given how tall the phone actually is.
        //
        // It was a fixed number, and a fixed number cannot be right: the
        // same height that leaves room for somebody's face on a tall phone
        // pushes the call button clean off a short one, and testers are not
        // all on the same handset. A share of the viewport keeps the field
        // the largest thing on the page everywhere, and keeps the people
        // under it on screen everywhere.
        // Taller than it was, because it is now the top of the page rather
        // than a card sitting on it. The reference gives its sky a little under
        // half the phone and then lets the first card climb back over it.
        val fieldHeight = (maxHeight * 0.46f).coerceIn(280.dp, 400.dp)

        // How far down the field sits from the top of the phone. Small on
        // purpose: enough that there is sky above the land rather than land
        // against the bezel, and not so much that the greeting loses the
        // ground it stands on.
        val FieldDrop = 28.dp

        // `modifier` belongs to the BoxWithConstraints above; applying it
        // here as well would pay the Scaffold's insets twice.
        Column(
            Modifier
                .fillMaxSize()
                // No ground of its own: HarborShell paints the ground and the
                // dusk over it, and a second opaque background here covered
                // that gradient -- which is what made every screen read flat.
                .verticalScroll(rememberScrollState()),
        ) {
            // The field, full bleed, with the greeting standing on it.
            //
            // It used to be a rounded rectangle inset by 16dp with the
            // greeting stacked above it -- a picture pinned to a page. The
            // reference does the opposite and it is the whole shape of the
            // screen: the sky runs edge to edge and under the status bar, what
            // the screen has to say sits on top of it, and the first card
            // climbs back over its bottom edge so the two overlap rather than
            // stack. Nothing is inset until below the fold.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(fieldHeight),
            ) {
                FieldCanvas(
                    store,
                    Modifier
                        .fillMaxSize()
                        // Dropped a little down the screen.
                        //
                        // The horizon sits a seventh of the way down whatever
                        // canvas the field is given, and on home that canvas
                        // starts at the top of the phone -- so the horizon
                        // landed just under the status bar, with the land
                        // pressed against the top edge and nothing above it.
                        // The garden gives the same field a panel to sit in and
                        // reads correctly; home was the screen without the
                        // breathing room.
                        //
                        // An offset rather than padding, so only the drawing
                        // moves. Padding would shorten the canvas, and a
                        // shorter canvas moves the horizon back up by the same
                        // fraction -- the field would shrink and stay exactly
                        // where it was.
                        .offset(y = FieldDrop)
                        // The field is erased into the page, not covered by it.
                        //
                        // A scrim painted over the bottom of the terrain only
                        // works while the thing behind it is a known colour.
                        // Behind this is now the weather, which is five
                        // different colours and changes on a slider -- so any
                        // fixed scrim shows up as a smudge the moment the
                        // slider moves. DstIn removes the field's own pixels
                        // instead, so whatever the sky happens to be that day
                        // is what the terrain fades into.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.58f to Color.Black,
                                    1.00f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                    // Pinchable and pannable, but it always opens standing at
                    // the same flower. Pushing the field back with two fingers
                    // is how you see the whole garden without leaving home,
                    // which is what the close opening shot costs otherwise.
                    interactive = true,
                    standClose = true,
                    controls = false,
                    sky = false,
                    arriving = growing != null,
                    onTap = onOpenGarden,
                )

                // The flower you just chose, opening where the camera lands.
                //
                // Drawn over the field rather than into it. The field renders
                // a patch as a cluster of dots, which is right when you are
                // looking at a whole island and says nothing at all when you
                // are standing in front of one bloom -- and one bloom is what
                // the reference shows and what somebody has just earned.
                //
                // The timing is the camera's, not its own: it waits for the
                // pull-back and the descent to finish before it starts, so the
                // sequence reads as arrive, look, then open.
                growing?.let { kind ->
                    val open = remember(kind) { Animatable(0f) }
                    LaunchedEffect(kind) {
                        if (reducedMotion) {
                            open.snapTo(1f)
                        } else {
                            delay(1180)
                            open.animateTo(
                                1f,
                                tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                            )
                        }
                        // A beat with it fully open before home is home again.
                        delay(900)
                        onGrown()
                    }
                    val t = open.value
                    if (t > 0f) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .padding(bottom = fieldHeight * 0.10f),
                            contentAlignment = Alignment.Center,
                        ) {
                            FlowerMark(
                                kind = kind,
                                modifier = Modifier
                                    .size(176.dp)
                                    .graphicsLayer { alpha = (t * 2.2f).coerceAtMost(1f) },
                                scale = 0.06f + 0.94f * t,
                            )
                        }
                    }
                }

                // No scrim behind the greeting.
                //
                // There was one, and it ended exactly where the field's height
                // ended -- so the page was darkened above that line and not
                // below it, and the join showed as a rule straight across the
                // screen. The greeting is white serif on a lit sky, which is
                // what the reference does and is legible on all five weathers.

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, end = 24.dp, bottom = 26.dp),
                ) {
                    Text(
                        if (settings.name.isBlank()) "Hey there." else "Hey, ${settings.name}.",
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                    )
                    Spacer(Modifier.size(5.dp))
                    // The line under the greeting is the field's caption.
                    //
                    // With nothing planted it says so, because an empty field
                    // needs explaining and "a little closer, every day" is a
                    // tagline rather than an answer. The field used to print
                    // this over its own middle, which on home's short canvas
                    // landed on top of the greeting.
                    Eyebrow(
                        when (grown) {
                            0 -> "Waiting for you to grow a flower"
                            1 -> "One flower has grown here"
                            else -> "$grown flowers have grown here"
                        },
                    )
                }
            }

            // The card climbs back over the sky by 40dp. That overlap is the
            // reference's one structural move, and without it the page is two
            // things one after the other instead of one thing in front of
            // another. It has to be enough to be unmistakable -- at 22 it read
            // as a gap that had been closed rather than as a card in front.
            Flow(
                Modifier
                    .offset(y = (-40).dp)
                    .pageContent(),
            ) {
                // A plan they made and nobody has closed.
                //
                // This is the "reminder inside Harbor" the cue promises when
                // somebody taps a later time. Until this card existed there
                // was nothing behind that promise, and the plan it left behind
                // held every sensed reminder back for good -- see
                // domain/Reminders.
                //
                // It waits to be found rather than arriving. A reminder that
                // comes looking for you is a cue, and a cue is the one thing
                // this person has just said "not now" to.
                Reminders.due(entries, Instant.now())?.let { plan ->
                    val who = contacts.firstOrNull { it.id == plan.contactId }
                    val close: (Reminders.Closed) -> Unit = { how ->
                        scope.launch {
                            store.markReminderDone(plan.id, how)
                            entries = store.recentEntries()
                        }
                    }
                    Surface {
                        SectionHeading("You made room for this.")
                        SmallCopy(
                            "You thought " +
                                timeLabel(
                                    plan.proposedTime!!
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalTime(),
                                ) +
                                " might suit for " + (who?.label ?: "someone") +
                                ". It still might.",
                        )
                        if (who != null && who.phoneE164 != null) {
                            // No closing call here. The row the dialer writes
                            // closes the plan by itself, and a plan marked
                            // closed by a call that never happened is a worse
                            // record than one simply left open.
                            PrimaryAction("Call " + who.label) {
                                Dialer.handOff(context, store, scope, who)
                            }
                        }
                        // Both of these close it, and neither costs anything.
                        // "I already did" is taken at its word and writes no
                        // call -- Harbor did not see one, so Harbor does not
                        // claim one.
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuietAction("I already did") { close(Reminders.Closed.SAID_SO) }
                            QuietAction("Let it go") { close(Reminders.Closed.LET_GO) }
                        }
                    }
                }

                // A call Harbor watched you start and never heard about.
                CallStats.pendingReflection(entries, Instant.now())?.let { waiting ->
                    Surface {
                        SectionHeading("How did that go?")
                        SmallCopy(
                            "You called " +
                                (contacts.firstOrNull { it.id == waiting.contactId }?.label
                                    ?: "someone") +
                                " earlier. It only takes a moment, and it is what grows " +
                                "the flower.",
                        )
                        TextLink("Tell me", onClick = { onReflect(waiting) })
                    }
                }

                // How life feels, between the field and the people. It
                // reads as the sky over the garden rather than something to get
                // past before the call button, and it is compact enough now to
                // sit there without pushing anybody below the fold.
                WeatherBar(store)

                // No "Your people" heading. A row of faces with a call button
                // under each one does not need to be told what it is, and the
                // header was costing a line directly above the one thing this
                // whole app exists to make easy.
                if (contacts.isEmpty()) {
                    SmallCopy("Nobody yet. Add someone, and their patch appears above.")
                    TextLink("Choose someone", onOpenCues)
                }
                contacts.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { contact ->
                            PersonTile(
                                contact = contact,
                                // Flowers, not calls: one a minute, the same
                                // arithmetic the field grows by.
                                calls = entries
                                    .filter {
                                        it.resolution == Resolution.CALLED &&
                                            it.contactId == contact.id &&
                                            it.flower != null
                                    }
                                    .sumOf { Flowers.flowerCount(it.callMinutes) },
                                usual = CallStats.usualMinutes(entries, contact.id),
                                flower = entries
                                    .filter { it.contactId == contact.id && it.flower != null }
                                    .maxByOrNull { it.occurredAt }
                                    ?.flower,
                                onClick = { onOpenPerson(contact.id) },
                                // Through Dialer, not a bare intent: that is what
                                // writes the row the flower flow looks for when
                                // you come back (see cue/Dialer).
                                onCall = contact.phoneE164?.let {
                                    { Dialer.handOff(context, store, scope, contact) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                // One word about today lives inside the weather card now, and
                // finding a moment and setting your pace live under Account. Home
                // is the garden, your people, and a quick way to say something.
                SendAPetal(onOpenNotes)
            }
        }
    }
}

/** A person as a specimen: their patch under glass, and a way to call them. */
@Composable
private fun PersonTile(
    contact: Contact,
    calls: Int,
    usual: Int?,
    flower: FlowerKind?,
    onClick: () -> Unit,
    onCall: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Specimen(
            name = contact.label,
            caption = when (calls) {
                0 -> "nothing yet"
                1 -> "one flower"
                else -> "$calls flowers"
            },
            tone = contact.tone,
            flower = flower,
            // Shorter than the 150 a specimen gets on its own page. Here the
            // arch is what stands between the field and the call button, and
            // the face still reads at this height.
            archHeight = 116,
        )

        // The whole point of the app, said out loud.
        //
        // Calling used to be a text link one screen in, which made the
        // commonest thing someone opens Harbor to do the least visible thing
        // on the page. Tapping the specimen still opens them; this dials.
        if (onCall != null) {
            val onInk = MaterialTheme.colorScheme.onPrimary
            Spacer(Modifier.size(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    // A pill with a top-lit amber fill, like every other
                    // action in the design. It was an 8dp rectangle in flat
                    // primary, which is what the light specimen asked for.
                    .clip(RoundedCornerShape(99.dp))
                    .background(Brush.verticalGradient(listOf(EmberLight, Ember)))
                    .clickable(onClick = onCall)
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(13.dp)) {
                    drawHandset(this, onInk)
                }
                Spacer(Modifier.size(7.dp))
                Text(
                    "Call " + contact.label,
                    maxLines = 1,
                    // Sans, not serif. Serif is Harbor's own voice in this
                    // design; a button label is the interface talking about
                    // itself, and set in serif it reads as a pull-quote.
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 13.sp,
                        color = onInk,
                    ),
                )
            }
        }

        usual?.let {
            Spacer(Modifier.size(7.dp))
            Eyebrow("usually ${CallStats.formatDuration(it)}")
        }
    }
}

/** `.text-link` — a quiet way onward, never a button competing for attention. */
@Composable
internal fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            // 15 + a 13sp line clears 48dp. Quiet is about weight and colour,
            // not about being hard to press.
            .padding(vertical = 15.dp),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/**
 * Saying something without opening anything.
 *
 * This used to be two tiles — "Leave a line" behind a speech bubble and "Send
 * a picture" behind a camera. Two borrowed icons for two things the app is
 * not, in front of what is really one act: the smallest thing you can send
 * somebody. One name and one mark now, and the mark is Harbor's own. Which of
 * the two you actually send is chosen on the screen it opens, where it is a
 * choice rather than a fork in the road.
 */
@Composable
private fun SendAPetal(onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, CardEdge, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            PetalMark(Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Send a petal",
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            )
            SmallCopy("A line or a picture. Nothing owed back.", size = 12)
        }
    }
}

private fun drawHandset(scope: DrawScope, ink: Color) = with(scope) {
    val s = size.minDimension
    val line = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
        color = ink,
        style = line,
    )
}
