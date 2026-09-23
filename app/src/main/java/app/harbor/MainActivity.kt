package app.harbor

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import app.harbor.domain.StudyArm
import app.harbor.ui.LocalStudyArm
import app.harbor.ui.theme.LocalReducedMotion
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.harbor.cue.CallFlow
import android.content.Intent
import app.harbor.data.HarborStore
import app.harbor.data.SupabaseClient
import app.harbor.data.StudyFile
import app.harbor.data.WhatsAppInbox
import app.harbor.domain.FlowerKind
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Resolution
import app.harbor.domain.CallStats
import app.harbor.domain.Moment
import app.harbor.sensing.Sensing
import app.harbor.ui.ContactScreen
import app.harbor.ui.CuesSetupScreen
import app.harbor.ui.FlowerLanding
import app.harbor.ui.ForwardYourChats
import app.harbor.ui.GardenScreen
import app.harbor.ui.SignInScreen
import app.harbor.ui.HarborShell
import app.harbor.ui.HarborTab
import app.harbor.ui.HomeScreen
import app.harbor.ui.OnboardingScreen
import app.harbor.ui.NotesScreen
import app.harbor.ui.PersonScreen
import app.harbor.ui.ScheduleScreen
import app.harbor.ui.StudyCodeScreen
import app.harbor.ui.SettingsScreen
import app.harbor.ui.theme.HarborTheme
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The app shell and its destinations.
 *
 * Three tabs sit in the pill at the bottom, as the prototype has them; the
 * rest are pushed over the top and show a way back instead. A state flag is
 * still doing the work of a navigation library, which is defensible only
 * because nothing here is more than one level deep.
 */
class MainActivity : ComponentActivity() {

    /**
     * Bumped every time this activity comes back to the front.
     *
     * The composition watches it so that returning from the dialer is
     * something the UI can react to. A plain counter rather than a lifecycle
     * observer: it needs no extra dependency and there is no ambiguity about
     * which of the several `LocalLifecycleOwner`s is in scope.
     */
    private var resumes by mutableIntStateOf(0)

    private var cameForward: Instant? = null

    override fun onResume() {
        super.onResume()
        resumes++
        cameForward = Instant.now()
        lifecycleScope.launch { store.note(Moment.APP_OPENED) }
    }

    /**
     * How long they stayed, recorded on the way out.
     *
     * Paired with APP_OPENED this is the session length the study wants, and
     * it costs nothing to keep: the alternative was asking participants at the
     * end of the week how often they had opened the app, which nobody knows.
     */
    override fun onPause() {
        super.onPause()
        val since = cameForward ?: return
        cameForward = null
        val seconds = Duration.between(since, Instant.now()).seconds.toInt()
        lifecycleScope.launch {
            store.note(Moment.APP_LEFT, value = seconds)
            // And refresh the study file, so nobody has to remember to export
            // it. See data/StudyFile - it writes to this app's own folder and
            // sends nothing anywhere.
            StudyFile.refresh(applicationContext, store)
        }
    }

    private enum class Screen(val tab: HarborTab?, val title: String?) {
        Home(HarborTab.Home, null),
        Schedule(HarborTab.Schedule, null),
        Settings(HarborTab.Account, null),
        Cues(null, "Reminders"),
        Contact(null, "Your person"),
        Garden(null, "Your garden"),
        // "Send a petal", matching the page's own title. The bar used to
        // say "A petal" while the page said "Send a petal." -- two names for
        // one screen, a centimetre apart.
        Notes(null, "Send a petal"),
        Person(null, null),
        Reflect(null, null),
        SignIn(null, "Your account"),
        StudyCode(null, "Study code"),
    }

    private lateinit var store: HarborStore

    /** Built once. Reads its own preferences file and holds no state of ours. */
    private val sync by lazy { SupabaseClient(applicationContext) }

    /**
     * The address a provider sign-in came home on, waiting to be dealt with.
     *
     * A provider hands the browser back to `harbor://auth#access_token=...`,
     * which Android delivers as an Intent rather than as a return value. The
     * activity is `singleTask`, so that arrives through [onNewIntent] while
     * Harbor is already running -- and through [onCreate]'s own intent if the
     * app was killed while the browser had the screen.
     *
     * Held as state so the composition can see it, and cleared by the screen
     * that consumes it: replaying a sign-in on every rotation would sign
     * somebody in twice and read as the app flickering.
     */
    private var signInRedirect by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeRedirect(intent)
    }

    /**
     * Whether this intent is a sign-in coming home, and if so, keep it.
     *
     * Checked by scheme rather than by trusting that only our own filter can
     * reach here, because any app can send an Intent with any data.
     */
    private fun takeRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "harbor" || data.host != "auth") return
        signInRedirect = data.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The browser may have finished while Harbor was not running.
        takeRedirect(intent)
        // Dark bars, stated rather than inferred.
        //
        // enableEdgeToEdge() with no arguments picks its bar style from the
        // system's light/dark setting, not from the app's. Harbor is dark on
        // every phone (see HarborTheme), so on a phone in light mode the
        // platform would draw a dark clock and battery over the dusk ground,
        // where they all but disappear.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // A field as well as a local, because onResume and onPause need it
        // too and they run outside the composition.
        store = HarborStore(applicationContext)

        // Put sensing back if it has fallen over. Installing a build
        // force-stops the app, which stops delivery until it is launched by
        // hand -- exactly the state a participant handed a new APK is in, and
        // one that reports itself as working. See Sensing.repair.
        lifecycleScope.launch { Sensing.repair(applicationContext, store) }

        setContent {
            HarborTheme {
                var screen by remember { mutableStateOf(Screen.Home) }

                // Read once here and handed down, rather than reached for in
                // each place that moves. Everything that animates has to
                // honour this -- it is an accessibility setting, not a taste
                // one, and a screen that still slides for somebody who asked
                // it not to has ignored them where it matters most.
                val liveSettings by store.settings.collectAsState()
                val reduceMotion = liveSettings.reducedMotion

                // Which screens get looked at, and in what order. A category
                // per screen; nothing about what was on it.
                LaunchedEffect(screen) { store.note(Moment.SCREEN, screen.name) }

                // Anything the WhatsApp bot has parsed goes onto the week
                // (ADR-014). Keyed on `resumes` rather than on the schedule
                // screen, because a class that moved should already be
                // suppressing cues by the time anybody thinks to look at the
                // grid -- and because the point of forwarding a message was
                // not having to open that screen. Returns 0 and costs nothing
                // when signed out, offline, or with no backend.
                LaunchedEffect(resumes) { WhatsAppInbox.drain(sync, store) }
                var reflecting by remember { mutableStateOf<LedgerEntry?>(null) }

                // The flower on its way into the field, drawn over whatever
                // is underneath. Null the rest of the time.
                var landing by remember { mutableStateOf<FlowerKind?>(null) }

                // What happens after the flower has gone into the ground.
                //
                // The landing drops it in and stops, which left the reward
                // finished at the exact moment it became real. This carries it
                // one step further: home pulls back to the whole field, flies
                // down to the patch that gained it, and opens it there.
                var growing by remember { mutableStateOf<FlowerKind?>(null) }
                var landed by remember { mutableStateOf<java.util.UUID?>(null) }
                var showing by remember { mutableStateOf<java.util.UUID?>(null) }
                // Who the contact screen opens on (null is somebody new), and
                // where it goes back to. It used to always edit the first
                // contact and return to Cues, which was right while there
                // could only be one.
                var editing by remember { mutableStateOf<java.util.UUID?>(null) }
                var contactBack by remember { mutableStateOf(Screen.Cues) }
                val scope = rememberCoroutineScope()
                val home = { screen = Screen.Home }

                // Null until we know, so the first frame is not the wrong
                // screen: flashing home at somebody who has never set the app
                // up would be the worst possible first impression of it.
                var onboarded by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) { onboarded = store.hasOnboarded() }

                // Shown once per call, so backing out of the reflection does
                // not fling you straight back into it on the next resume.
                var offered by remember { mutableStateOf<java.util.UUID?>(null) }

                /**
                 * Coming back from the dialer *is* the end of the call, near
                 * enough — it is the only signal available without reading the
                 * call log, which would cost a permission this app will not
                 * spend (ADR-002). So the flower flow opens on return rather
                 * than waiting behind a card on home: the reward should arrive
                 * while the call is still in the room.
                 *
                 * CueActivity does the same on its own resume, but it lives in
                 * a task excluded from recents, so returning to Harbor any
                 * other way lands here instead. This is the path that actually
                 * fires most of the time.
                 */
                LaunchedEffect(resumes, onboarded) {
                    if (onboarded != true) return@LaunchedEffect
                    val waiting = CallStats.pendingReflection(
                        store.recentEntries(),
                        Instant.now(),
                    ) ?: return@LaunchedEffect
                    // Not while they are mid-way through typing something of
                    // their own. Anywhere else, the flower takes the screen.
                    //
                    // Schedule used to be on this list, which is why a call
                    // started from the little window on that screen was the
                    // one call in the app that never got a flower: you came
                    // back to the screen you left, and the screen you left
                    // suppressed the reward. Drawing a week is not the kind
                    // of half-finished thought this guard is for.
                    val busy = screen == Screen.Reflect || screen == Screen.Contact ||
                        screen == Screen.Notes
                    if (waiting.id != offered && !busy) {
                        offered = waiting.id
                        reflecting = waiting
                        screen = Screen.Reflect
                        store.note(
                            Moment.CALL_RETURNED,
                            value = CallStats.minutesAway(waiting.occurredAt, Instant.now()),
                        )
                    }
                }

                /**
                 * The same landing, for a flower planted in the cue's own
                 * activity.
                 *
                 * CueActivity runs the reflection itself and then finishes,
                 * which drops the user back here with the flower already in
                 * the ground and nothing having been seen to happen. Rather
                 * than a second channel between the two, this notices a
                 * freshly planted row on the way back in. `landed` makes it
                 * once per flower.
                 */
                LaunchedEffect(resumes, onboarded) {
                    if (onboarded != true) return@LaunchedEffect
                    val fresh = store.recentEntries()
                        .filter { it.flower != null }
                        .maxByOrNull { it.occurredAt } ?: return@LaunchedEffect
                    val age = java.time.Duration.between(fresh.occurredAt, Instant.now())
                    if (fresh.id != landed && !age.isNegative &&
                        age < java.time.Duration.ofMinutes(3)
                    ) {
                        landed = fresh.id
                        landing = fresh.flower
                    }
                }

                BackHandler(enabled = onboarded == true && screen != Screen.Home) { home() }

                // The arm, watched rather than read once.
                //
                // It used to be a single read in a LaunchedEffect, on the
                // reasoning that an arm cannot be reassigned so there was
                // nothing to observe. True of every moment except the one
                // that matters: the arm is *claimed* on the first screen of
                // the first run, which is after this composable has already
                // read it. So a bees participant typed their code and then
                // did the whole of onboarding -- the part somebody sits and
                // watches them through -- in the control arm, with the bee
                // turning up only after the next cold start. See
                // HarborRepository.armFlow.
                val arm by store.armFlow.collectAsState()

                CompositionLocalProvider(
                    LocalReducedMotion provides reduceMotion,
                    LocalStudyArm provides arm,
                ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    // imePadding here rather than on each screen: the app is
                    // edge to edge, so the window no longer resizes itself
                    // when the keyboard opens and every screen has to give
                    // back the inset. Without it the field you are typing in
                    // sits behind the keyboard — which is exactly what
                    // happened all through onboarding.
                    val inset = Modifier.padding(padding).imePadding()

                    // Home keeps the bottom inset and gives up the top one.
                    //
                    // Its field is full bleed and is meant to run under the
                    // status bar, the way the reference runs its sky under the
                    // clock. Nothing on home needs the top inset: the greeting
                    // sits at the *foot* of the field, so the only thing level
                    // with the clock is sky.
                    val homeInset = Modifier
                        .padding(bottom = padding.calculateBottomPadding())
                        .imePadding()

                    if (onboarded != true) {
                        if (onboarded == false) {
                            OnboardingScreen(
                                store = store,
                                onFinished = { onboarded = true },
                                modifier = inset,
                            )
                        }
                        return@Scaffold
                    }

                    HarborShell(
                        tab = screen.tab,
                        onSelect = { tab ->
                            screen = when (tab) {
                                HarborTab.Home -> Screen.Home
                                HarborTab.Schedule -> Screen.Schedule
                                HarborTab.Account -> Screen.Settings
                            }
                        },
                        onBack = if (screen.tab == null) home else null,
                        title = screen.title,
                    ) {
                        // One screen dissolving into the next.
                        //
                        // A tab switch used to be a cut: the old screen was
                        // simply not there and the new one simply was. A
                        // crossfade is the quietest fix -- no slide, because
                        // the three tabs are peers and sliding implies an
                        // order they do not have, and no scale, because the
                        // field behind them is a photograph and scaling it
                        // reads as a camera move nobody asked for.
                        //
                        // Keyed on the screen, so a redraw within one screen
                        // does not replay it.
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                val d = if (reduceMotion) 0 else 200
                                fadeIn(tween(d)) togetherWith fadeOut(tween(d))
                            },
                            label = "screen",
                            // Not `showing` -- that name is already taken in
                            // this scope by the contact whose page is open,
                            // and shadowing it compiles into nonsense rather
                            // than an error at the point of the mistake.
                        ) { visible ->
                        when (visible) {
                            Screen.Home -> HomeScreen(
                                store = store,
                                onOpenGarden = { screen = Screen.Garden },
                                onOpenNotes = { screen = Screen.Notes },
                                onAddContact = {
                                    editing = null
                                    contactBack = Screen.Home
                                    screen = Screen.Contact
                                },
                                onOpenPerson = { id ->
                                    showing = id
                                    screen = Screen.Person
                                },
                                onReflect = { entry ->
                                    reflecting = entry
                                    screen = Screen.Reflect
                                },
                                modifier = homeInset,
                                growing = growing,
                                onGrown = { growing = null },
                            )

                            Screen.Cues -> CuesSetupScreen(
                                store = store,
                                onEditContact = {
                                    editing = store.contacts.value.firstOrNull()?.id
                                    contactBack = Screen.Cues
                                    screen = Screen.Contact
                                },
                                onOpenGarden = { screen = Screen.Garden },
                                modifier = inset,
                            )

                            Screen.Contact -> ContactScreen(
                                store = store,
                                contactId = editing,
                                onDone = { screen = contactBack },
                                modifier = inset,
                            )

                            Screen.Garden -> GardenScreen(store = store, modifier = inset)

                            Screen.Person -> PersonScreen(
                                store = store,
                                contactId = showing,
                                onLeaveLine = { screen = Screen.Notes },
                                onEdit = {
                                    editing = showing
                                    contactBack = Screen.Person
                                    screen = Screen.Contact
                                },
                                modifier = inset,
                            )

                            Screen.Notes -> NotesScreen(
                                store = store,
                                onDone = home,
                                modifier = inset,
                            )

                            Screen.Schedule -> ScheduleScreen(
                                store = store,
                                onDone = home,
                                modifier = inset,
                                otherWays = {
                                    ForwardYourChats(
                                        client = sync,
                                        onOpenAccount = { screen = Screen.SignIn },
                                    )
                                },
                            )

                            Screen.Settings -> SettingsScreen(
                                store = store,
                                onEditSchedule = { screen = Screen.Schedule },
                                onOpenCues = { screen = Screen.Cues },
                                onOpenAccount = { screen = Screen.SignIn },
                                onOpenStudyCode = { screen = Screen.StudyCode },
                                onDone = home,
                                modifier = inset,
                            )

                            Screen.StudyCode -> StudyCodeScreen(
                                store = store,
                                // Back to the very first screen, because that
                                // is what startOver leaves behind: an install
                                // with nothing in it.
                                onStartOver = {
                                    onboarded = false
                                    screen = Screen.Home
                                },
                                onDone = { screen = Screen.Settings },
                                modifier = inset,
                            )

                            Screen.SignIn -> SignInScreen(
                                client = sync,
                                redirect = signInRedirect,
                                onRedirectHandled = { signInRedirect = null },
                                onDone = { screen = Screen.Settings },
                                modifier = inset,
                            )

                            Screen.Reflect -> reflecting?.let { entry ->
                                // Each amendment builds on the last write, not
                                // on the row as it was when this screen opened.
                                // Both callbacks touch the same row, and
                                // copying twice from the original meant the
                                // pulse answer put the flower back to null.
                                var amended by remember(entry.id) {
                                    mutableStateOf(entry)
                                }
                                CallFlow(
                                    who = store.contacts.value
                                        .firstOrNull { it.id == entry.contactId }?.label
                                        ?: "them",
                                    // Timed from when the call was placed to
                                    // when they came back, not a stand-in ten
                                    // minutes. The row's own occurredAt is the
                                    // moment Harbor dialled.
                                    measuredMinutes = entry.callMinutes
                                        ?: CallStats.minutesAway(
                                            entry.occurredAt,
                                            Instant.now(),
                                        ),
                                    initialTopic = entry.topic,
                                    reducedMotion = store.settings.value.reducedMotion,
                                    onPlant = { minutes, flower, topic ->
                                        // Amends the existing row: append is
                                        // keyed on the id, so this replaces
                                        // rather than duplicates.
                                        val next = amended.copy(
                                            callMinutes = minutes,
                                            flower = flower,
                                            topic = topic ?: amended.topic,
                                        )
                                        amended = next
                                        scope.launch {
                                            store.append(next)
                                            store.note(
                                                Moment.FLOWER_PLANTED,
                                                flower.name,
                                                minutes,
                                            )
                                        }
                                    },
                                    onPulse = { pulse ->
                                        val next = amended.copy(feedbackPulse = pulse)
                                        amended = next
                                        scope.launch { store.append(next) }
                                    },
                                    onNotReached = {
                                        // Nothing to plant, and nothing to
                                        // ask about. The row stops claiming a
                                        // call happened and the flow ends.
                                        val next = amended.copy(
                                            resolution = Resolution.NOT_REACHED,
                                            callMinutes = null,
                                            feeling = null,
                                            flower = null,
                                        )
                                        amended = next
                                        scope.launch { store.append(next) }
                                        reflecting = null
                                        screen = Screen.Home
                                    },
                                    onDone = {
                                        // Home rather than the garden, and the
                                        // flower goes with them: the bloom
                                        // travels from the picker into the
                                        // field it was added to, so the reward
                                        // is something you watch happen rather
                                        // than something you go and verify.
                                        landing = amended.flower
                                        landed = amended.id
                                        reflecting = null
                                        screen = Screen.Home
                                    },
                                )
                            } ?: run { screen = Screen.Home }
                        }
                        }
                        }

                    // Over the top of everything, including the shell's nav
                    // pill: the flower is passing in front of the app, not
                    // inside one of its screens.
                    landing?.let { kind ->
                        FlowerLanding(
                            kind = kind,
                            modifier = inset,
                            reducedMotion = store.settings.value.reducedMotion,
                        ) {
                            landing = null
                            growing = kind
                            // Home, so there is a field to land in. Choosing a
                            // flower can end on the cue's own screen, and the
                            // arrival has nowhere to happen there.
                            screen = Screen.Home
                        }
                    }
                }
                }
            }
        }
    }
}
