package app.harbor.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import app.harbor.ui.theme.TermsGround
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
import app.harbor.sensing.ScrollWatch
import app.harbor.sensing.Sensing
import app.harbor.ui.theme.Avatar
import app.harbor.ui.theme.AvatarSize
import app.harbor.ui.theme.Chalk
import app.harbor.ui.theme.ChosenEdge
import app.harbor.ui.theme.ChosenFill
import app.harbor.ui.theme.Glass
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

    // The study code comes before onboarding, not inside it.
    //
    // It could have been step zero with everything shifted up by one, and that
    // would have quietly broken the funnel: ONBOARDING_STEP is the number the
    // study lives on, and a step 3 that used to mean the sound and now means
    // the picture makes every week of telemetry disagree with the one before
    // it. Outside the numbered flow, the steps keep meaning what they meant.
    //
    // Null while the answer is still being read off disk -- a frame or two,
    // during which nothing is drawn rather than the code screen flashing at
    // somebody who already gave one.
    var needsCode by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { needsCode = !store.hasClaimedArm() }

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
        when {
            needsCode == null -> Unit
            needsCode == true -> StudyCodeGate(store, scope) { needsCode = false }
            else -> Steps(store, scope, step, ::next, ::finish)
        }
    }
}

/**
 * The step the flow is on. Lifted out so the code gate above reads as one
 * decision rather than a `when` with eleven branches and two conditions.
 */
@Composable
private fun Steps(
    store: HarborRepository,
    scope: CoroutineScope,
    step: Int,
    next: () -> Unit,
    finish: () -> Unit,
) {
    run {
        when (step) {
            0 -> Welcome(next)
            1 -> YourName(store, scope, next)
            2 -> WhoToCall(store, scope, next)
            3 -> TheirSound(store, scope, next)
            4 -> TheirPicture(store, scope, next)
            5 -> WhenFree(store, scope, next)
            6 -> AskPermission(store, scope, next)
            7 -> AlmostComplete(store, next)
            8 -> GoodJob(next)
            // The week is the last thing onboarding asks for.
            //
            // Four explainer cards used to follow it -- the weather metaphor,
            // the two numbers, what a reminder is, and what the family sees.
            // They are gone, and the reason is not that they said the wrong
            // thing. They said the right things to somebody who had already
            // stopped reading: nine screens in, past a consent page, with the
            // app still not visible. That content belongs in a tutorial
            // somebody chooses to open, not at the end of a queue.
            9 -> WeekSetupScreen(store, onFinish = finish, onSkip = finish)
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

/** A hole you type into. See [app.harbor.ui.theme.Glass]. */
private val FieldGlass = Glass

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
private val SelectedCard = ChosenFill
private val SelectedEdge = ChosenEdge

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
    /**
     * A ground of this page's own, painted over the flow's warm wash.
     *
     * Every other page reads fine on that wash because every other page is a
     * line of type and a button. The consent page is four headings and four
     * paragraphs of body copy, and body copy is the one thing the wash is
     * worst under: it is at its brightest exactly where the text sits, and
     * grey-on-orange at 14sp is the least legible thing in the app. On the one
     * screen where somebody is agreeing to something, that is not a taste
     * problem.
     */
    ground: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (ground != null) Modifier.background(ground) else Modifier)
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
    /**
     * What the keyboard's action key says and does.
     *
     * Worth the parameter. Every text field in this flow sat above a Next
     * button the keyboard covered, so the shape of a step was: type your
     * name, dismiss the keyboard, then press the button you can now see.
     * The keyboard already has a button exactly where the thumb is -- it
     * was simply wired to nothing.
     *
     * [ImeAction.Next] moves to the field below; [ImeAction.Done] submits
     * the step.
     */
    imeAction: ImeAction = ImeAction.Default,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onAction: (() -> Unit)? = null,
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
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onAction?.invoke() },
                onDone = { onAction?.invoke() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
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
/**
 * Before anything: which version of Harbor this is.
 *
 * The participant does not choose this and mostly should not think about it.
 * A code is read off a sheet by whoever is handing the phone over, it names an
 * arm, and the arm is fixed for good from that moment — see
 * [app.harbor.domain.StudyArm] and `HarborRepository.claimCode`.
 *
 * ## Why there is a way past it
 *
 * A locked door here would mean a lost code is a brick. Going past lands in
 * the arm that already existed, which is the safe failure: somebody still gets
 * a working Harbor, and nobody can reach the other arm by guessing. The code
 * is stored either way, blank if skipped, so the export can tell an assigned
 * control participant from somebody who shrugged.
 *
 * ## Why it says so little
 *
 * "Two versions are being compared and you are in one of them" is true and
 * would change how people use it. Everything about the screen is deliberately
 * flat: no explanation, no reassurance, nothing to read into.
 */
@Composable
private fun StudyCodeGate(
    store: HarborRepository,
    scope: CoroutineScope,
    onClaimed: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    FlowPage {
        Spacer(Modifier.height(40.dp))
        Question("Enter your code", size = 21)
        Spacer(Modifier.height(18.dp))
        Question("The person setting this up has it", size = 15)
        Spacer(Modifier.height(30.dp))

        FlowField(code, "code") { code = it.take(12) }

        Spacer(Modifier.height(34.dp))
        FlowPill("Continue") {
            scope.launch {
                store.claimCode(code)
                onClaimed()
            }
        }
        Spacer(Modifier.height(16.dp))
        // Blank on purpose: claimCode records the empty string, which is how
        // the export distinguishes this from an assigned control.
        TextLink("I do not have one") {
            scope.launch {
                store.claimCode(null)
                onClaimed()
            }
        }
    }
}

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

    // Advance *after* the write, not beside it. Shared by the button and
    // by the keyboard's Done, so the two cannot come apart.
    fun submit() {
        if (draft.isBlank()) return
        scope.launch {
            store.setSettings(settings.copy(name = draft.trim()))
            onNext()
        }
    }

    FlowPage(petal = 0) {
        Question("First, a little about yourself")
        Spacer(Modifier.height(46.dp))
        Question("What do we call you?")
        Spacer(Modifier.height(20.dp))
        FlowField(
            draft,
            "your name",
            imeAction = ImeAction.Done,
            onAction = ::submit,
        ) { draft = it.take(40) }
        Spacer(Modifier.height(52.dp))
        FlowNext(enabled = draft.isNotBlank(), onClick = ::submit)
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
    val context = LocalContext.current
    val contacts by store.contacts.collectAsState()
    val existing = contacts.firstOrNull()
    var name by remember { mutableStateOf(existing?.label.orEmpty()) }
    var number by remember { mutableStateOf(existing?.phoneE164.orEmpty()) }
    var picked by remember { mutableStateOf<String?>(null) }

    // The address book, without the address book.
    //
    // ACTION_PICK against the *phone* table hands back one row's URI and a
    // one-shot read grant for it, so the name, the number and their photo can
    // all be read out of it. READ_CONTACTS would let Harbor read every contact
    // it likes whenever it likes, and asking for it here would be asking for a
    // thousand rows to save typing one. This reads exactly the row the person
    // tapped and nothing else is ever visible to us -- which is also what lets
    // the consent screen two steps later keep saying what it says.
    val search = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val row = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && row != null) {
            ContactPick.read(context, row)?.let {
                if (it.name.isNotBlank()) name = it.name.take(40)
                if (it.number.isNotBlank()) number = it.number.take(20)
                picked = it.photo
            }
        }
    }

    // One place the step is committed from, whether it was the button or
    // the keyboard's Done.
    fun submit() {
        if (name.isBlank() || number.isBlank()) return
        scope.launch {
            val who = existing ?: Contact(UUID.randomUUID(), name.trim(), number.trim())
            // Their contact photo, copied in the same step that learned
            // their number, so the next screen already has a face on it.
            val photo = picked?.let { src ->
                withContext(Dispatchers.IO) {
                    ContactPhotos.store(context, who.id, Uri.parse(src))
                }
            } ?: who.photoRef
            store.upsertContact(
                who.copy(
                    label = name.trim(),
                    phoneE164 = number.trim(),
                    photoRef = photo,
                ),
            )
            onNext()
        }
    }

    FlowPage(petal = 1) {
        Question("Who would you like to call more often?")
        Spacer(Modifier.height(18.dp))
        Question("You can add more people later", size = 17)
        Spacer(Modifier.height(26.dp))

        FlowPill("search your contacts") {
            search.launch(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowNote("Harbor only ever sees the one person you tap.")
        Spacer(Modifier.height(22.dp))

        // Name to number to done, so the keyboard is never in the way of
        // the thing you press next.
        val toNumber = remember { FocusRequester() }
        FlowField(
            name,
            "their name",
            imeAction = ImeAction.Next,
            onAction = { toNumber.requestFocus() },
        ) { name = it.take(40) }
        Spacer(Modifier.height(12.dp))
        FlowField(
            number,
            "their number",
            // A phone pad rather than a QWERTY. This field takes digits and
            // a leading plus, and it has been offering letters all along.
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            focusRequester = toNumber,
            onAction = ::submit,
        ) { number = it.take(20) }

        Spacer(Modifier.height(36.dp))
        FlowNext(
            enabled = name.isNotBlank() && number.isNotBlank(),
            onClick = ::submit,
        )
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

    val context = LocalContext.current
    val pickSound = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && who != null) {
            val uri = result.data
                ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            scope.launch {
                // Copied, not referenced. The picker will happily return a
                // file off their own storage, and that grant is gone by the
                // time a cue fires. See CueSounds.
                val ref = uri?.let {
                    withContext(Dispatchers.IO) { CueSounds.store(context, who.id, it) }
                }
                store.upsertContact(who.copy(cueSoundRef = ref))
            }
        }
    }

    FlowPage(petal = 2) {
        Question("What sound do you associate\nwith this person?")
        Spacer(Modifier.height(30.dp))

        // Spotify is not built and cannot be faked. It needs a registered
        // app, an OAuth round trip and a network call before it can return a
        // single track, so the honest thing is to say when, rather than show a
        // search box that never searches.
        ComingSoon("search Spotify", "coming after the first study week")
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
private fun TheirPicture(
    store: HarborRepository,
    scope: CoroutineScope,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val contacts by store.contacts.collectAsState()
    val who = contacts.firstOrNull()

    // The same picker and the same private copy ContactScreen already uses. A
    // photo URI's read grant does not reliably survive a reboot and the cue
    // may fire days later, so the file is copied into Harbor's own storage and
    // that copy is what the cue loads. See ContactPhotos.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { chosen ->
        val target = who ?: return@rememberLauncherForActivityResult
        if (chosen != null) {
            scope.launch {
                val ref = withContext(Dispatchers.IO) {
                    ContactPhotos.store(context, target.id, chosen)
                }
                if (ref != null) store.upsertContact(target.copy(photoRef = ref))
            }
        }
    }

    val face by produceState<ImageBitmap?>(null, who?.photoRef) {
        val ref = who?.photoRef
        value = if (ref == null) null else withContext(Dispatchers.IO) {
            ContactPhotos.load(context, ref)
        }
    }

    FlowPage(petal = 2) {
        Spacer(Modifier.height(20.dp))
        if (who != null) {
            val shot = face
            if (shot != null) {
                Image(
                    bitmap = shot,
                    contentDescription = "The photo you chose for " + who.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AvatarSize.XL.dp.dp)
                        .clip(CircleShape),
                )
            } else {
                Avatar(who.label, who.tone, size = AvatarSize.XL)
            }
            Spacer(Modifier.height(14.dp))
            Question(who.label, size = 19)
        }
        Spacer(Modifier.height(30.dp))
        FlowPill(if (who?.photoRef == null) "Add a picture" else "Change the picture") {
            pickPhoto.launch("image/*")
        }
        Spacer(Modifier.height(10.dp))
        // One line, true either way.
        //
        // This used to promise that their contact photo came across by itself
        // if you found them with search. It does on some phones and not on
        // this one: the picker's grant covers the row it returned, and the
        // photo lives behind a *separate* display_photo URI that the grant
        // does not reach, so Samsung's provider refuses it with a
        // SecurityException asking for READ_CONTACTS. Harbor tries anyway,
        // because some providers do hand it over -- but a promise that fails
        // silently on the device in front of you is worse than no promise.
        FlowNote("Copied into Harbor, on this phone. It is never uploaded.")
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
 * goes.
 *
 * Scrolling used to be drawn here and left off, with a note saying that
 * watching which apps you use was a different promise and not one this app
 * would start making quietly. It is now a promise Harbor offers to make --
 * loudly, on this screen, and only if you take it. The old note stands as the
 * reason the disclosure below is written the way it is.
 */
@Composable
private fun WhenFree(
    store: HarborRepository,
    scope: CoroutineScope,
    onNext: () -> Unit,
) {
    val settings by store.settings.collectAsState()
    // Walking is on for everybody and is not offered as a choice here -- it is
    // what the app is, and CuePolicy has no switch for it. Scrolling is the
    // one being opted into.
    val scrolling = settings.scrollCues

    var showPolicy by remember { mutableStateOf(false) }

    fun setScrolling(on: Boolean) {
        scope.launch { store.setSettings(store.settings.value.copy(scrollCues = on)) }
    }

    FlowPage(petal = 3) {
        Question("When should Harbor catch you?")
        Spacer(Modifier.height(10.dp))
        // Both, now that there are two. The old line said "pick the moment",
        // which was true when only one of them worked.
        Question("Either, or both", size = 16)
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
            Row(
                Modifier.weight(1f).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                .background(if (scrolling) SelectedCard else PillIdle)
                .then(
                    if (scrolling) {
                        Modifier.border(1.dp, SelectedEdge, RoundedCornerShape(20.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            // The toggle is the header row, not the whole card. When the card
            // carried it, every tap on the disclosure -- including the one on
            // "Privacy policy" -- was a tap that turned the option back off.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { setScrolling(!scrolling) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Weight rather than a fixed 210dp, which was narrow
                // enough to break "after a long stretch in one app" and leave
                // "app" alone on its own line.
                Row(
                    Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(26.dp)) {
                        drawOptionMark(OptionMark.DOOMSCROLL, if (scrolling) FlowInk else PillInk)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Question(
                            "While scrolling",
                            size = 18,
                            // "Doomscrolling" is a judgement, and this screen
                            // is asking permission rather than making a point.
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "after a long stretch in one app",
                            style = MaterialTheme.typography.labelSmall.copy(color = PillInk),
                        )
                    }
                }
                if (scrolling) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(FlowGround),
                        contentAlignment = Alignment.Center,
                    ) { Question("✓", size = 15) }
                }
            }

            // The disclosure, and only for the people it applies to.
            //
            // It appears when the option is taken rather than sitting under a
            // choice most people will not make -- a consent nobody has opted
            // into is noise, and noise is what teaches people to skip the
            // ones that matter.
            //
            // At normal reading size on purpose. The instruction was to make
            // it fine print under a "privacy policy" label; the fact that
            // Harbor can see which app you are in and for how long is the
            // whole of what is being agreed to, and shrinking it below the
            // text around it is how a participant ends up able to say, fairly,
            // that they never agreed. The *detail* goes behind the link. The
            // sentence does not.
            if (scrolling) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 4.dp)) {
                    Text(
                        "Harbor will see which app is in front and for how long. " +
                            "Not what is on the screen, and nothing leaves the phone.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = PillInk,
                        ),
                    )
                    // Opens in place rather than going somewhere. A policy on
                    // another screen is a policy read after the decision, and
                    // the decision is on this one.
                    TextLink(if (showPolicy) "Close" else "Privacy policy") {
                        showPolicy = !showPolicy
                    }
                    if (showPolicy) {
                        Text(
                            POLICY,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = PillInk,
                            ),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
        FlowNext(enabled = true) { onNext() }
    }
}

/**
 * What is behind "Privacy policy" on [WhenFree].
 *
 * Every sentence here is a claim about code that has to stay true. The third
 * one in particular: [app.harbor.domain.StudyExport] writes `trigger_source`,
 * which says a scrolling session ended, and has no field for an app name. If
 * one is ever added, this paragraph is wrong and has to change with it.
 */
private const val POLICY =
    "Harbor asks Android which app is in front and how long it has been " +
        "there. That is the whole of the reading.\n\n" +
        "It cannot see messages, posts, photos, what you type, or anything " +
        "else on the screen, and it keeps no history of the apps you open." +
        "\n\n" +
        "Nothing about your apps is sent anywhere. The study file records " +
        "that a reminder appeared, what you chose to do, and which kind of " +
        "moment prompted it \u2014 a walk, or a long stretch of scrolling " +
        "\u2014 never the app you were in.\n\n" +
        "You can switch this off in Settings at any time, and withdraw the " +
        "permission itself from Android's settings."

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
    val settings by store.settings.collectAsState()

    var agreedToTerms by remember { mutableStateOf(false) }
    if (!agreedToTerms) {
        TermsPopup(
            scrolling = settings.scrollCues,
            onAgree = { agreedToTerms = true },
        )
        return
    }

    val context = LocalContext.current

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
    var canStayAwake by remember { mutableStateOf(Sensing.isUnrestricted(context)) }
    // The fourth, and only for the people who asked for the second trigger.
    // Usage access has no dialog at all -- it is a screen in Settings with a
    // list of apps on it -- so somebody who chose scrolling and was never
    // sent there would have chosen a trigger that cannot fire.
    var canSeeApps by remember { mutableStateOf(ScrollWatch.hasPermission(context)) }
    // The fifth, and also only for the scrolling trigger. Separate from
    // canTakeScreen: that one is about a locked phone, this one is about a
    // phone somebody is holding. See CueNotifier.canOpenOverApps.
    var canOpenOver by remember { mutableStateOf(CueNotifier.hasOverlayGrant(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
                canTakeScreen = CueNotifier.canTakeTheScreen(context)
                canStayAwake = Sensing.isUnrestricted(context)
                canSeeApps = ScrollWatch.hasPermission(context)
                canOpenOver = CueNotifier.hasOverlayGrant(context)
                granted = ActivityTransitions.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val wouldReach = canNotify && canTakeScreen && canStayAwake &&
        (!settings.scrollCues || (canSeeApps && canOpenOver))

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
            // The other trigger's number, for the people who just asked
            // for it two screens ago.
            //
            // This card is titled "What you keep control of" and listed
            // every threshold except the one governing the trigger the
            // participant had just chosen -- which made the title not quite
            // true for exactly the people the new trigger is being tested
            // on. Same stepper as the settings screen, same five-minute
            // steps, same range.
            if (settings.scrollCues) {
                Stepper(
                    label = "Time in one app",
                    value = settings.thresholds.sessionMinutes.toString() + " min",
                    onDown = { session(store, scope, settings, -5) },
                    onUp = { session(store, scope, settings, +5) },
                )
            }
            Stepper(
                label = "Most reminders a day",
                value = settings.thresholds.dailyCap.toString(),
                onDown = { daily(store, scope, settings, -1) },
                onUp = { daily(store, scope, settings, +1) },
            )
            // The third dial, and the one people actually ask for.
            //
            // A daily cap answers "how many", and on its own it allows all of
            // them inside ten minutes. This is the one that says "not again
            // just yet", which is the complaint anything that interrupts you
            // eventually earns. It was already a setting; it simply was not on
            // the screen where somebody is deciding whether to let this run.
            Stepper(
                label = "Quiet gap between reminders",
                // The same words Account uses for the same number.
                value = gapPhrase(settings.thresholds.cooldownMinutes),
                onDown = { gap(store, scope, settings, -15) },
                onUp = { gap(store, scope, settings, +15) },
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
                        SectionHeading("A reminder would not reach you yet")
                        if (!canStayAwake) {
                            SmallCopy(
                                "Your phone can put Harbor to sleep to save " +
                                    "battery. Asleep, it never hears that your walk " +
                                    "ended — the reminder is not late, it never " +
                                    "happens. This is the one that matters most.",
                                size = 14,
                            )
                            FlowPill("Let Harbor keep listening") {
                                context.startActivity(Sensing.unrestrictedRequest(context))
                            }
                        }
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
                        if (settings.scrollCues && !canSeeApps) {
                            SmallCopy(
                                "You asked to be caught after a long stretch in one app. Android keeps " +
                                    "that behind a switch of its own, and until it is on " +
                                    "Harbor cannot tell which app is in front \u2014 so that " +
                                    "half of what you chose would quietly never happen.",
                                size = 14,
                            )
                            ScrollWatch.request(context)?.let { intent ->
                                FlowPill("Open usage access") {
                                    ScrollWatch.open(context, intent)
                                }
                            }
                        }
                        if (settings.scrollCues && !canOpenOver) {
                            SmallCopy(
                                "A reminder while you are scrolling arrives as a banner over the " +
                                    "feed, which is the easiest thing in the world to flick " +
                                    "away without reading. Let it open properly and it " +
                                    "takes the screen instead.",
                                size = 14,
                            )
                            FlowPill("Let a reminder open over an app") {
                                context.startActivity(
                                    CueNotifier.overlaySettings(context),
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
private fun TermsPopup(
    /** Whether the scrolling trigger was taken on the screen before this one. */
    scrolling: Boolean,
    onAgree: () -> Unit,
) = FlowPage(ground = TermsGround) {
    Question("A few things, once", size = 20)
    Spacer(Modifier.height(22.dp))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading("What Harbor reads")
        FlowNote(
            if (scrolling) {
                "Whether your phone thinks you're walking or still, and — " +
                    "because you asked for scrolling reminders — which app is " +
                    "in front and for how long. Not where you are, and not " +
                    "what is on your screen."
            } else {
                "Whether your phone thinks you're walking or still. Not where " +
                    "you are, not what you're doing, not which apps you use."
            },
        )
        SectionHeading("Where it stays")
        FlowNote(
            if (scrolling) {
                "On this phone. Neither your walking nor which apps you open " +
                    "ever leaves it, and neither is shared with your family — " +
                    "not as a summary, not ever."
            } else {
                "On this phone. Your walking never leaves it and is never " +
                    "shared with your family — not as a summary, not ever."
            },
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

private fun session(
    store: HarborRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    settings: app.harbor.domain.UserSettings,
    by: Int,
) = scope.launch {
    store.setSettings(
        settings.copy(
            thresholds = settings.thresholds.copy(
                sessionMinutes = (settings.thresholds.sessionMinutes + by).coerceIn(1, 180),
            ),
        ),
    )
}

private fun gap(
    store: HarborRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    settings: app.harbor.domain.UserSettings,
    by: Int,
) = scope.launch {
    store.setSettings(
        settings.copy(
            thresholds = settings.thresholds.copy(
                // Zero is a real setting and means the gap is off, so the
                // floor is zero rather than one step of it.
                cooldownMinutes = (settings.thresholds.cooldownMinutes + by).coerceIn(0, 240),
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

