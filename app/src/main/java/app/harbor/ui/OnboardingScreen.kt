package app.harbor.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.harbor.cue.CueNotifier
import app.harbor.data.HarborRepository
import app.harbor.domain.Contact
import app.harbor.domain.Moment
import app.harbor.sensing.ActivityTransitions
import app.harbor.sensing.Sensing
import app.harbor.ui.theme.Avatar
import app.harbor.ui.theme.AvatarSize
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.Gold
import app.harbor.ui.theme.Ink
import app.harbor.ui.theme.LocalReducedMotion
import app.harbor.ui.theme.Muted
import app.harbor.ui.theme.Paper
import app.harbor.ui.theme.Sand
import app.harbor.ui.theme.Notice
import app.harbor.ui.theme.SectionHeading
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The first run: five questions that grow one flower.
 *
 * Ported from the Figma flow. The spine of it is [PetalProgress] — each answer
 * earns a petal, so setting the app up *is* the first thing you grow, rather
 * than a form standing between you and the app. The five petals are your name,
 * who you would call, their sound, their picture, and when you are free.
 *
 * ## What the design did not include, and why it is still here
 *
 * Two screens in this file have no frame in Figma and must not be dropped.
 *
 * **The permission ask.** Harbor cannot notice a walk without
 * `ACTIVITY_RECOGNITION`, and cannot show a reminder without
 * `POST_NOTIFICATIONS`. The design's "while walking" screen chooses the
 * *behaviour* but never asks Android for the right, so on its own it would
 * produce an app that looks set up and never fires. It sits straight after
 * that choice, which is the moment the ask makes sense.
 *
 * **What Harbor will never do.** The promise that your family install nothing
 * and are told nothing used to be made before any permission was mentioned,
 * because it is the worry the permission dialog raises on its own. It is kept
 * on the welcome screen for the same reason, and said again, plainly, in the
 * first-run tutorial at the end.
 *
 * ## Usability pass, 2026-09-15
 *
 * The order changed: name and number used to lead into a photo and a free-time
 * question before reaching sound, three screens after the person who owns it
 * was named. It now goes straight from the number to the sound, because that
 * is the thing that makes the preview reminder feel like somebody rather than
 * a system alert. The photo and free-time questions follow it instead of
 * leading it.
 *
 * "One last thing" — the transition screen between the finished flower and the
 * calendar — is gone. The calendar already has its own skip link, so the
 * screen it used to lead into was a tap in front of a tap.
 *
 * A first-run tutorial now runs after the calendar, once, covering the four
 * things testers asked about mid-flow: the weather metaphor, the two sliders,
 * what a reminder is, and what the family sees.
 *
 * ## Not yet wired
 *
 * Searching your contacts and Spotify are drawn as the design has them but are
 * disabled, pending the integrations. Each says so rather than failing
 * silently when tapped. A contact's picture and their sound both work without
 * either: the system contact picker and photo picker need no permission, and
 * neither does the sound. Google Fit is deliberately absent: Harbor already
 * detects walking on-device, without an account or a network (ADR-008), and
 * routing that through Fit would give up both.
 */
@Composable
fun OnboardingScreen(
    store: HarborRepository,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun next() { step++ }
    fun finish() {
        scope.launch {
            store.setOnboarded()
            store.note(Moment.ONBOARDING_DONE, value = step)
            onFinished()
        }
    }

    // Where people stop is the funnel, and the funnel is the number the study
    // lives on: somebody who abandons at the permission ask contributes
    // nothing to question one.
    LaunchedEffect(step) { store.note(Moment.ONBOARDING_STEP, value = step) }

    // Every step below is handed this scope rather than making its own.
    //
    // A step that saved and then advanced was launching the write into its
    // own rememberCoroutineScope and immediately leaving the composition,
    // which cancels that scope - usually before the write had finished
    // suspending on the prefs lock. The name you typed on the first question
    // simply never arrived, and the contact only arrived when it won the
    // race. This scope belongs to the flow and outlives every step in it.
    // The same dusk the app stands in.
    //
    // Onboarding is not inside HarborShell -- it runs before there is a shell
    // -- so it has to draw the ground itself or it opens on flat near-black
    // and then the first real screen lights up behind the person's back.
    Box(
        modifier
            .fillMaxSize()
            .background(FlowGround)
            .drawBehind { drawDusk() },
    ) {
        when (step) {
            0 -> Welcome(::next)
            1 -> YourName(store, scope, ::next)
            2 -> WhoToCall(store, scope, ::next)
            3 -> TheirSound(store, scope, ::next)
            4 -> TheirPicture(store, ::next)
            5 -> WhenFree(::next)
            6 -> AskPermission(store, scope, ::next)
            7 -> AlmostComplete(store, ::next)
            8 -> GoodJob(::next)
            9 -> WeekSetupScreen(store, onFinish = ::next, onSkip = ::next)
            10 -> FirstRunTutorial(::finish)
            else -> finish()
        }
    }
}

// --- the flow's own surface -----------------------------------------------
//
// Still measured off the Figma frames -- the shapes, sizes and placements are
// the frames' -- but the frames were drawn on white, and the app they open
// into is not. These are those controls restated on the dusk ground.
//
// The one thing the dark pass had to pull apart is the grey the frames used
// for everything. A single #D9D9D9 served as the text field, the enabled
// button and the selected chip, because on white all three can be the same
// grey. On this ground they cannot: a field is a hole you type into and wants
// to be glass, while a button and a chosen chip are the thing being asked for
// and want to be amber. Hence three fills where the frames had one.

/** The ground, and the app's ground -- the flow no longer changes it. */
private val FlowGround = Paper

/** What the flow says: a question, an answer being typed, a label. */
private val FlowInk = Chalk

/** A hole you type into. White at eight percent, composited. */
private val FieldGlass = Color(0xFF202124)

/** The enabled button and the chosen chip. The design's one accent. */
private val ActionFill = Gold

/** What sits on [ActionFill]. Brown-black, never white. */
private val ActionInk = Ink

/**
 * A card that is chosen, rather than a chip that is.
 *
 * Amber at eight percent with a rim at twenty, which is the design's own way
 * of marking a whole card as live -- it does the same on the cues screen. A
 * card filled solid amber would shout down the question above it.
 */
private val SelectedCard = Color(0xFF1F1C15)
private val SelectedEdge = Color(0x33F0BD3E)

/** Not yet, or not available. */
private val PillIdle = Sand
private val PillInk = Muted
private val MutedInk = Muted

/** Every question is set the same way: serif, centred, unhurried. */
@Composable
private fun Question(text: String, size: Int = 20) = Text(
    text,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth(),
    style = MaterialTheme.typography.titleLarge.copy(fontSize = size.sp, color = FlowInk),
)

/** The flow's page: flower at the top, question beneath, answer under that. */
@Composable
private fun FlowPage(
    petal: Int? = null,
    bloom: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        if (petal != null) {
            PetalProgress(step = petal, modifier = Modifier.size(210.dp))
            Spacer(Modifier.height(40.dp))
        } else if (bloom) {
            PetalProgress(step = 5, modifier = Modifier.size(230.dp))
            Spacer(Modifier.height(36.dp))
        }
        content()
        Spacer(Modifier.height(48.dp))
    }
}

/** The grey pill the design types into. */
@Composable
private fun FlowField(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Box(
        modifier
            .width(236.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(FieldGlass)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(FlowInk),
            textStyle = TextStyle(fontSize = 16.sp, color = FlowInk),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, style = TextStyle(fontSize = 16.sp, color = MutedInk))
        }
    }
}

/**
 * Bottom-right, and dimmed until the question has an answer.
 *
 * The label is a plain Text rather than [Question], which is the whole reason
 * this used to run the full width of the screen: Question carries a
 * fillMaxWidth of its own, so the pill around it stretched to the margins and
 * a small button bottom-right came out as a bar. The frames have a pill.
 */
@Composable
private fun FlowNext(enabled: Boolean, label: String = "Next", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .clip(RoundedCornerShape(29.dp))
                .background(if (enabled) ActionFill else PillIdle)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    color = if (enabled) ActionInk else PillInk,
                ),
            )
        }
    }
}

/** A wide grey pill: the design's ordinary button. */
@Composable
private fun FlowPill(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = Box(
    modifier
        .clip(RoundedCornerShape(29.dp))
        .background(if (enabled) ActionFill else PillIdle)
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 26.dp, vertical = 11.dp),
) {
    Text(
        label,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 18.sp,
            color = if (enabled) ActionInk else PillInk,
        ),
    )
}

/** Something the design shows but nothing is wired to yet. */
@Composable
private fun ComingSoon(label: String, note: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FlowPill(label, enabled = false) {}
        Spacer(Modifier.height(6.dp))
        Text(note, style = MaterialTheme.typography.labelSmall.copy(color = PillInk))
    }
}

/** A muted line of explanation, the flow's own voice for it. */
@Composable
private fun FlowNote(text: String, modifier: Modifier = Modifier) = Text(
    text,
    modifier = modifier.fillMaxWidth(),
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = PillInk,
    ),
)

/**
 * A link styled to read as a button without being one — centred and
 * underlined, for the one place the usability pass wants that: "I have
 * already seen a reminder".
 *
 * Deliberately not a parameter added to the shared `TextLink` in
 * HomeScreen.kt. `TextLink` is called two ways across the app — positionally
 * (`TextLink("Back", onDone)`) and with a trailing lambda
 * (`TextLink("Save") { ... }`) — and those two conventions need `onClick` in
 * two different positions (second, and last) at once. Extra parameters can
 * only satisfy one of them, which is what broke every trailing-lambda call
 * site the first time this was tried here.
 */
@Composable
private fun EmphasisLink(text: String, onClick: () -> Unit) = Text(
    text,
    textAlign = TextAlign.Center,
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(vertical = 12.dp),
    style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        color = PillInk,
        textDecoration = TextDecoration.Underline,
    ),
)

// --- the five questions ---------------------------------------------------

/**
 * The frame, and only the frame.
 *
 * A card headed "What it will not do" used to sit under the button, making
 * the promise that your family install nothing and are told nothing. It was
 * not in the design and it is gone. The promise is not: it is made on the
 * permission screen, which is where the worry actually arrives — nobody
 * wonders what an app is telling their mother until it asks to watch them
 * walk.
 */
@Composable
private fun Welcome(onNext: () -> Unit) = FlowPage(bloom = true) {
    Question("Let’s build our first Flower together", size = 22)
    Spacer(Modifier.height(14.dp))
    Question("Answer the questions\nto add petals", size = 18)
    Spacer(Modifier.height(28.dp))
    FlowPill("Continue", onClick = onNext)
}

/** Petal one. */
@Composable
private fun YourName(
    store: HarborRepository,
    scope: CoroutineScope,
    onNext: () -> Unit,
) {
    val settings by store.settings.collectAsState()
    var draft by remember { mutableStateOf(settings.name) }

    FlowPage(petal = 0) {
        Question("First, a little about yourself")
        Spacer(Modifier.height(46.dp))
        Question("What do we call you?")
        Spacer(Modifier.height(20.dp))
        FlowField(draft, "your name") { draft = it.take(40) }
        Spacer(Modifier.height(52.dp))
        // Advance *after* the write, not beside it.
        FlowNext(enabled = draft.isNotBlank()) {
            scope.launch {
                store.setSettings(settings.copy(name = draft.trim()))
                onNext()
            }
        }
    }
}

/**
 * Petal two. Contacts search is drawn but not wired, so the number is typed.
 *
 * Used to lead into a picture and a free-time question before the person's
 * sound was ever asked about — three screens between naming somebody and
 * hearing what they sound like. It leads straight into [TheirSound] now.
 */
@Composable
private fun WhoToCall(
    store: HarborRepository,
    scope: CoroutineScope,
    onNext: () -> Unit,
) {
    val contacts by store.contacts.collectAsState()
    val existing = contacts.firstOrNull()
    var name by remember { mutableStateOf(existing?.label.orEmpty()) }
    var number by remember { mutableStateOf(existing?.phoneE164.orEmpty()) }

    FlowPage(petal = 1) {
        Question("Who would you like to call more often")
        Spacer(Modifier.height(18.dp))
        Question("You can add more people later", size = 17)
        Spacer(Modifier.height(26.dp))

        ComingSoon("search", "reading your contacts comes later")
        Spacer(Modifier.height(22.dp))

        FlowField(name, "their name") { name = it.take(40) }
        Spacer(Modifier.height(12.dp))
        FlowField(number, "their number") { number = it.take(20) }

        Spacer(Modifier.height(36.dp))
        FlowNext(enabled = name.isNotBlank() && number.isNotBlank()) {
            scope.launch {
                store.upsertContact(
                    (existing ?: Contact(UUID.randomUUID(), name.trim(), number.trim()))
                        .copy(label = name.trim(), phoneE164 = number.trim()),
                )
                onNext()
            }
        }
    }
}

/**
 * Petal three, now straight after the number. Spotify waits on the
 * integration; the system ringtone picker does not.
 *
 * The built-in chimes are gone. They set [app.harbor.domain.UserSettings.sound],
 * a value [app.harbor.cue.Ringer] never actually reads — it only ever plays a
 * contact's own [Contact.cueSoundRef], falling back to the phone's default
 * ringtone. So the picker below writes the same field [ContactScreen] does,
 * which is the one that was ever real, rather than removing the only control
 * on this screen and leaving it empty.
 */
@Composable
private fun TheirSound(
    store: HarborRepository,
    scope: CoroutineScope,
    onNext: () -> Unit,
) {
    val contacts by store.contacts.collectAsState()
    val who = contacts.firstOrNull()

    val pickSound = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && who != null) {
            val uri = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                ?.toString()
            scope.launch { store.upsertContact(who.copy(cueSoundRef = uri)) }
        }
    }

    FlowPage(petal = 2) {
        Question("What sound do you associate\nwith this person?")
        Spacer(Modifier.height(30.dp))

        ComingSoon("search Spotify", "Spotify comes later")
        Spacer(Modifier.height(16.dp))

        FlowPill(if (who?.cueSoundRef == null) "Choose a sound from this phone" else "Change their sound") {
            pickSound.launch(
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Their sound")
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        who?.cueSoundRef?.let(Uri::parse),
                    )
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowNote("Leave it, and Harbor rings with your phone's own ringtone instead.")

        Spacer(Modifier.height(40.dp))
        FlowNext(enabled = true) { onNext() }
    }
}

/** Still petal three: the second half of choosing somebody, not a question of its own. */
@Composable
private fun TheirPicture(store: HarborRepository, onNext: () -> Unit) {
    val contacts by store.contacts.collectAsState()
    val who = contacts.firstOrNull()

    FlowPage(petal = 2) {
        Spacer(Modifier.height(20.dp))
        if (who != null) {
            Avatar(who.label, who.tone, size = AvatarSize.XL)
            Spacer(Modifier.height(14.dp))
            Question(who.label, size = 19)
        }
        Spacer(Modifier.height(30.dp))
        ComingSoon("Add a picture !", "choosing a photo comes later")
        Spacer(Modifier.height(16.dp))
        ComingSoon("Keep their profile picture", "needs your contacts")
        Spacer(Modifier.height(40.dp))
        FlowNext(enabled = true) { onNext() }
    }
}

/**
 * Petal four.
 *
 * The design attributes walking to Google Fit. Harbor reads it on-device
 * through the Activity Recognition Transition API instead, which needs no
 * account and no network (ADR-008), so the option stays and the attribution
 * goes. Watching which apps you use is a different promise entirely and is not
 * something this app is going to start doing quietly, so it is drawn and left
 * off.
 */
@Composable
private fun WhenFree(onNext: () -> Unit) {
    FlowPage(petal = 3) {
        Question("When should Harbor catch you?")
        Spacer(Modifier.height(10.dp))
        Question("Pick the moment you would not mind being asked", size = 16)
        Spacer(Modifier.height(26.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SelectedCard)
                .border(1.dp, SelectedEdge, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.width(210.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(26.dp)) { drawOptionMark(OptionMark.WALKING, FlowInk) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Question("While walking", size = 18)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "noticed on this phone, never sent anywhere",
                        style = MaterialTheme.typography.labelSmall.copy(color = PillInk),
                    )
                }
            }
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(FlowGround),
                contentAlignment = Alignment.Center,
            ) { Question("✓", size = 15) }
        }

        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(PillIdle)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(26.dp)) { drawOptionMark(OptionMark.DOOMSCROLL, PillInk) }
                Spacer(Modifier.width(12.dp))
                Text(
                    "While doomscrolling",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, color = PillInk),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "would mean Harbor watching which apps you open. Not yet, and not quietly.",
                style = MaterialTheme.typography.labelSmall.copy(color = PillInk),
            )
        }

        Spacer(Modifier.height(40.dp))
        FlowNext(enabled = true) { onNext() }
    }
}

/**
 * Two marks, drawn rather than imported — see [app.harbor.ui.drawTabMark] for
 * the pattern this follows and why: no icon library, no bitmaps, everything
 * geometry so it restyles with the palette for free.
 */
private enum class OptionMark { WALKING, DOOMSCROLL }

private fun DrawScope.drawOptionMark(mark: OptionMark, colour: Color) {
    val u = size.minDimension / 100f
    val line = Stroke(width = 9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(build: Path.() -> Unit) = drawPath(Path().apply(build), colour, style = line)
    when (mark) {
        OptionMark.WALKING -> {
            drawCircle(colour, radius = 9f * u, center = Offset(40f * u, 16f * u))
            path {
                moveTo(40f * u, 27f * u)
                lineTo(46f * u, 52f * u)
                lineTo(32f * u, 86f * u)
            }
            path { moveTo(46f * u, 52f * u); lineTo(70f * u, 62f * u) }
            path { moveTo(43f * u, 42f * u); lineTo(18f * u, 50f * u) }
        }

        OptionMark.DOOMSCROLL -> {
            path {
                moveTo(32f * u, 8f * u)
                lineTo(68f * u, 8f * u)
                lineTo(68f * u, 92f * u)
                lineTo(32f * u, 92f * u)
                close()
            }
            path { moveTo(50f * u, 28f * u); lineTo(50f * u, 68f * u) }
            path { moveTo(37f * u, 57f * u); lineTo(50f * u, 71f * u); lineTo(63f * u, 57f * u) }
        }
    }
}

// --- the parts the design did not draw, and the finish --------------------

/**
 * The screen the study lives or dies on.
 *
 * Kept from the previous onboarding, unchanged in behaviour. The system dialog
 * only ever appears after a deliberate tap, a refusal is accepted rather than
 * argued with, and because Android stops asking after two refusals the only
 * route left is system settings — which is what the recheck is for.
 *
 * It sits here, right after the walking choice, because that is the moment the
 * ask reads as *so I can catch a good moment to call her* rather than *so I can
 * watch you walk*.
 *
 * Gated on a terms pop-up, added in the usability pass: scrollable terms and
 * an Agree button, over the screen that sets the limits (walk-before-a-reminder,
 * reminders-a-day). It is also the new home for the load-bearing privacy
 * sentences that used to sit as subtext under half a dozen buttons through the
 * rest of the flow — saying it once here beats repeating it under each one.
 */
@Composable
private fun AskPermission(
    store: HarborRepository,
    scope: CoroutineScope,
    onNext: () -> Unit,
) {
    var agreedToTerms by remember { mutableStateOf(false) }
    if (!agreedToTerms) {
        TermsPopup(onAgree = { agreedToTerms = true })
        return
    }

    val context = LocalContext.current
    val settings by store.settings.collectAsState()

    var granted by remember { mutableStateOf(ActivityTransitions.hasPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Set only for the instant between Sensing.enable succeeding and the
    // LaunchedEffect below moving on -- long enough for "Reminders are on"
    // to be seen, not long enough to need a tap.
    var justEnabled by remember { mutableStateOf(false) }

    // Granting the permission is only the first of three things a reminder
    // needs, and the other two fail in silence -- the same pair the cues
    // settings screen has to surface. Asked here as well because somebody who
    // finishes onboarding believing reminders are on, and then walks, is the
    // exact person the study loses. Re-read on resume: both can only be fixed
    // by a trip to system settings and back.
    var canNotify by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var canTakeScreen by remember { mutableStateOf(CueNotifier.canTakeTheScreen(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
                canTakeScreen = CueNotifier.canTakeTheScreen(context)
                granted = ActivityTransitions.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val wouldReach = canNotify && canTakeScreen

    // Only move on by itself when there is nothing left to fix. With a gap
    // open the step waits, shows what it is, and keeps a Continue under it --
    // fixing either one is a trip out of the app, and coming back to find the
    // flow had walked on without you is worse than the gap.
    LaunchedEffect(justEnabled, wouldReach) {
        if (justEnabled && wouldReach) {
            delay(900)
            onNext()
        }
    }

    suspend fun enable() {
        failed = !Sensing.enable(context, store)
        justEnabled = !failed
    }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val ok = results[Manifest.permission.ACTIVITY_RECOGNITION] ?: granted
        granted = ok
        refused = !ok
        if (ok) scope.launch { enable() }
    }

    fun ask() {
        refused = false
        failed = false
        val wanted = buildList {
            if (!granted) add(Manifest.permission.ACTIVITY_RECOGNITION)
            // Without this the reminder is posted and silently dropped, which
            // looks exactly like a trigger that never fired.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // So the call button rings rather than opening the dialer with
            // the number filled in. Refusing it costs nothing: the button
            // falls back to handing the number over, which is what it did
            // before. See cue/Dialer.
            add(Manifest.permission.CALL_PHONE)
        }
        if (wanted.isEmpty()) scope.launch { enable() }
        else request.launch(wanted.toTypedArray())
    }

    FlowPage {
        Question("May Harbor notice when\nyou stop walking?")
        Spacer(Modifier.height(10.dp))
        Question("This is the part that makes a reminder arrive on its own.", size = 16)
        Spacer(Modifier.height(26.dp))

        Surface {
            SectionHeading("What you keep control of")
            Stepper(
                label = "Walk before a reminder",
                value = settings.thresholds.walkingMinutes.toString() + " min",
                onDown = { walking(store, scope, settings, -1) },
                onUp = { walking(store, scope, settings, +1) },
            )
            Stepper(
                label = "Most reminders a day",
                value = settings.thresholds.dailyCap.toString(),
                onDown = { daily(store, scope, settings, -1) },
                onUp = { daily(store, scope, settings, +1) },
            )
            SmallCopy(
                "Suggestions, not rules — move them now or later. You already " +
                    "agreed nothing about this leaves your phone; this is just " +
                    "how often it asks.",
            )
        }
        Spacer(Modifier.height(26.dp))

        when {
            Sensing.isActive(context, store) -> {
                Notice("Reminders are on. Harbor will wait for a real walk.")
                if (!wouldReach) {
                    Spacer(Modifier.height(18.dp))
                    Surface {
                        SectionHeading("One more thing, or you will not see it")
                        if (!canNotify) {
                            SmallCopy(
                                "Notifications are off for Harbor. A reminder is " +
                                    "posted as one, so with these off it is thrown " +
                                    "away the moment it is made and nothing appears.",
                                size = 14,
                            )
                            FlowPill("Allow notifications") {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(
                                            Settings.EXTRA_APP_PACKAGE,
                                            context.packageName,
                                        ),
                                )
                            }
                        }
                        if (!canTakeScreen) {
                            SmallCopy(
                                "Android only lets an app take over the screen if " +
                                    "you allow it by hand. Without it a reminder " +
                                    "arrives as a banner that fades on its own, so " +
                                    "in your pocket you would miss it.",
                                size = 14,
                            )
                            CueNotifier.fullScreenSettings(context)?.let { intent ->
                                FlowPill("Let a reminder open the screen") {
                                    context.startActivity(intent)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                FlowPill("Continue", onClick = onNext)
            }

            refused -> {
                SmallCopy(
                    "That is completely fine. Reminders stay off and nothing " +
                        "else changes — you can still start a moment yourself, and " +
                        "turn these on later under Account. Android may not ask " +
                        "again, so from here it would have to be system settings.",
                )
                Spacer(Modifier.height(14.dp))
                TextLink("I have granted it — check again", onClick = {
                    granted = ActivityTransitions.hasPermission(context)
                    if (granted) ask()
                })
                Spacer(Modifier.height(10.dp))
                FlowPill("Continue without reminders", onClick = onNext)
            }

            failed -> {
                SmallCopy(
                    "Harbor could not start listening. Google Play services may " +
                        "be unavailable on this phone. Reminders stay off rather than " +
                        "pretending to work.",
                )
                Spacer(Modifier.height(14.dp))
                FlowPill("Continue", onClick = onNext)
            }

            else -> {
                FlowPill("Yes, notice for me", onClick = ::ask)
                Spacer(Modifier.height(12.dp))
                TextLink("Not now", onNext)
            }
        }
    }
}

/**
 * Once, before the limits screen: everything the flow used to repeat as
 * subtext under half a dozen buttons, said plainly and agreed to once.
 *
 * Two lines were deleted rather than moved. "You asked for this one" was
 * filler under the preview-reminder button — it explained nothing a reader
 * could not already see. "Harbor only ever hands this to your dialler" was
 * moved here too, except it turned out to describe a version of the app that
 * no longer exists: ADR-002 was amended 2026-09-13 so Harbor places the call
 * itself. This screen states the current behaviour instead of the old one.
 */
@Composable
private fun TermsPopup(onAgree: () -> Unit) = FlowPage {
    Question("A few things, once", size = 20)
    Spacer(Modifier.height(22.dp))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("What Harbor reads")
        FlowNote(
            "Whether your phone thinks you're walking or still. Not where " +
                "you are, not what you're doing, not which apps you use.",
        )
        SectionHeading("Where it stays")
        FlowNote(
            "On this phone. Your walking never leaves it and is never shared " +
                "with your family — not as a summary, not ever.",
        )
        SectionHeading("What a reminder does")
        FlowNote(
            "Offers you one person, and rings them if you say yes. It asks " +
                "for the phone permission so the call button rings instead of " +
                "opening your dialler — say no and it still works, with one " +
                "extra tap.",
        )
        SectionHeading("What costs nothing")
        FlowNote(
            "Every reminder can be dismissed. There is no streak to break, " +
                "and you can turn this off whenever you like.",
        )
    }

    Spacer(Modifier.height(30.dp))
    FlowPill("Agree", onClick = onAgree)
}

// The three numbers that decide when a cue may arrive, nudged in place.
//
// Each clamps to the range `Thresholds` enforces, so a stepper can never build
// a value its own `require` would reject.

private fun walking(
    store: HarborRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    settings: app.harbor.domain.UserSettings,
    by: Int,
) = scope.launch {
    store.setSettings(
        settings.copy(
            thresholds = settings.thresholds.copy(
                walkingMinutes = (settings.thresholds.walkingMinutes + by).coerceIn(1, 120),
            ),
        ),
    )
}

private fun daily(
    store: HarborRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    settings: app.harbor.domain.UserSettings,
    by: Int,
) = scope.launch {
    store.setSettings(
        settings.copy(
            thresholds = settings.thresholds.copy(
                dailyCap = (settings.thresholds.dailyCap + by).coerceIn(1, 10),
            ),
        ),
    )
}

/**
 * Four petals in, and a real reminder to look at.
 *
 * Seeing one explains the product better than any screen about it, and it
 * costs nothing to show: a manual reminder does not touch the daily
 * allowance, and [manualCueIntent] is built with `skipPulse = true` so the
 * preview does not ask stage 8's "was this a good moment" question — that
 * stays for after a real call, not a walkthrough of one.
 *
 * Launched through a result launcher rather than a plain `startActivity` so
 * onboarding finds out when the preview closes and can move on by itself —
 * "I have already seen a reminder" stays as the way past this step for
 * anyone who does not want to watch it again.
 */
@Composable
private fun AlmostComplete(store: HarborRepository, onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by store.contacts.collectAsState()
    val who = contacts.firstOrNull()

    val showCue = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onNext() }

    FlowPage(petal = 4) {
        Question("Our flower is almost complete")
        Spacer(Modifier.height(34.dp))
        if (who != null) {
            FlowPill("show me a reminder") {
                scope.launch { showCue.launch(manualCueIntent(context, store, who, skipPulse = true)) }
            }
            Spacer(Modifier.height(22.dp))
        }
        EmphasisLink("I have already seen a reminder", onNext)
    }
}

/**
 * The flower, whole — the reward, not a form to submit.
 *
 * Used to hold a Continue button under it. Usability testing wanted it
 * centred with nothing to press: it fades in, holds for a moment, fades out
 * and moves on by itself — under two seconds in total, not the five the
 * first pass used, which read as the screen having stalled. A tap anywhere
 * moves on sooner for anyone who does not want to wait. [LocalReducedMotion]
 * skips straight to the next step rather than playing a shortened version of
 * the same fade.
 */
@Composable
private fun GoodJob(onNext: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            onNext()
            return@LaunchedEffect
        }
        alpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
        delay(1_100)
        alpha.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        onNext()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNext,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PetalProgress(step = 5, modifier = Modifier.size(230.dp))
            Spacer(Modifier.height(36.dp))
            Question("Good job!", size = 22)
        }
    }
}

/**
 * After the calendar, once: the four things testers asked about mid-flow
 * rather than reading a screen about — the weather metaphor, the two
 * sliders, what a reminder is, and what the family sees.
 *
 * The last page is the one that matters most. Nothing here may leave it
 * vague: a tutorial that hints at reciprocity is the single most damaging
 * sentence this app could contain (ADR-007).
 */
@Composable
private fun FirstRunTutorial(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        "The weather is how you're doing" to
            "Set it yourself on Home, clear to stormy. Nothing reads it off " +
                "you — it's yours to change whenever your week does.",
        "Two numbers you set, not Harbor" to
            "Walk before a reminder, and reminders a day. What you saw on the " +
                "last screen was a suggestion, not a rule — move either one, " +
                "any time, under Account.",
        "A reminder is not a call" to
            "It looks and sounds like one on purpose — that's what gets " +
                "noticed — but it never claims to be one. Dismissing it costs " +
                "nothing, every time.",
        "What your family sees" to
            "Nothing. They install nothing, and Harbor never contacts them — " +
                "not a summary, not a notification, not once. This is one-sided " +
                "by design.",
    )
    val last = page == pages.lastIndex

    FlowPage {
        Question(pages[page].first, size = 21)
        Spacer(Modifier.height(20.dp))
        FlowNote(pages[page].second)
        Spacer(Modifier.height(34.dp))
        FlowPill(if (last) "Start using Harbor" else "Next") {
            if (last) onDone() else page++
        }
        if (!last) {
            Spacer(Modifier.height(14.dp))
            TextLink("Skip", onDone)
        }
    }
}
