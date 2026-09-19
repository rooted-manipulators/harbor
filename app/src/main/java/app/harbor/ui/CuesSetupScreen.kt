package app.harbor.ui

import android.Manifest
import androidx.core.app.NotificationManagerCompat
import android.content.Context
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.harbor.cue.CueActivity
import app.harbor.cue.CueNotifier
import app.harbor.data.HarborRepository
import app.harbor.ui.theme.Avatar
import app.harbor.ui.theme.AvatarSize
import app.harbor.ui.theme.Flow
import app.harbor.ui.theme.Notice
import app.harbor.ui.theme.PageIntro
import app.harbor.ui.theme.PrimaryAction
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import app.harbor.ui.theme.QuietAction
import app.harbor.ui.theme.SectionHeading
import app.harbor.ui.theme.SmallCopy
import app.harbor.ui.theme.SoftSurface
import app.harbor.ui.theme.Surface
import app.harbor.ui.theme.pageContent
import app.harbor.domain.Contact
import app.harbor.domain.CuePolicy
import app.harbor.domain.Cue
import app.harbor.domain.Liveness
import app.harbor.domain.TriggerSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import app.harbor.sensing.ActivityTransitions
import app.harbor.sensing.ScrollWatch
import app.harbor.sensing.Sensing
import kotlinx.coroutines.launch

/**
 * The permission and privacy explainer, and the switch that turns cues on.
 *
 * This screen carries more risk than anything else in the app. Activity
 * recognition reads as invasive, and if someone declines here nothing
 * downstream matters — no trigger, no cue, no study data. So it explains
 * before it asks, and the system dialog only ever appears after the user has
 * chosen to see it.
 *
 * Every claim below is one the code actually keeps. If any of it stops being
 * true, this copy is the first thing that has to change — see ADR-004.
 */
@Composable
fun CuesSetupScreen(
    store: HarborRepository,
    onEditContact: () -> Unit,
    onOpenGarden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState()
    val contact by store.contacts.collectAsState()

    var hasPermission by remember { mutableStateOf(ActivityTransitions.hasPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Re-read on every resume rather than once: the only way to grant this is
    // in Settings, so the interesting moment is the return from there.
    val lifecycleOwner = LocalLifecycleOwner.current
    // Read now, not optimistically. ON_RESUME only arrives on the *next*
    // resume, so starting these at true meant somebody who opened this screen
    // and stayed in the app was told nothing was wrong until they happened to
    // leave and come back.
    var canTakeScreen by remember { mutableStateOf(CueNotifier.canTakeTheScreen(context)) }
    var canNotify by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var canStayAwake by remember { mutableStateOf(Sensing.isUnrestricted(context)) }
    var canSeeApps by remember { mutableStateOf(ScrollWatch.hasPermission(context)) }
    var canOpenOver by remember { mutableStateOf(CueNotifier.hasOverlayGrant(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canTakeScreen = CueNotifier.canTakeTheScreen(context)
                canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
                canStayAwake = Sensing.isUnrestricted(context)
                canSeeApps = ScrollWatch.hasPermission(context)
                canOpenOver = CueNotifier.hasOverlayGrant(context)
                hasPermission = ActivityTransitions.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Activity recognition decides whether sensing can run at all, so it
        // is the one that decides whether cues are "on". Notifications are
        // asked for in the same breath and do not gate sensing -- but a cue
        // posted without them is dropped by the system in silence, which looks
        // from the inside exactly like a trigger that never fired. That is why
        // the answer is kept rather than discarded: the screen has to be able
        // to say so afterwards.
        val granted = results[Manifest.permission.ACTIVITY_RECOGNITION] ?: hasPermission
        hasPermission = granted
        refused = !granted
        canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (granted) {
            scope.launch { failed = !Sensing.enable(context, store) }
        }
    }

    fun turnOn() {
        failed = false
        refused = false

        val wanted = buildList {
            if (!hasPermission) add(Manifest.permission.ACTIVITY_RECOGNITION)
            // API 33+ only. Without it the cue is posted and silently dropped,
            // which looks exactly like a trigger that never fired.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (wanted.isEmpty()) {
            scope.launch { failed = !Sensing.enable(context, store) }
        } else {
            request.launch(wanted.toTypedArray())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // No ground of its own: HarborShell paints the ground and the
            // dusk over it, and a second opaque background here covered
            // that gradient -- which is what made every screen read flat.
            .verticalScroll(rememberScrollState()),
    ) {
        Flow(Modifier.pageContent()) {
            PageIntro(
                eyebrow = "Gentle reminders",
                title = "A reminder, never a demand.",
                subtitle = "Harbor can notice the quiet moment just after a walk " +
                    "ends, and offer you the chance to call home. That is the " +
                    "whole of it.",
            )

            Surface {
                SectionHeading("What Harbor reads")
                // Reads back what is switched on rather than describing the
                // walk-only app. See TermsPopup, which carries the same pair.
                SmallCopy(
                    if (settings.scrollCues) {
                        "Whether your phone thinks you are walking or still, " +
                            "and which app is in front and for how long. Not " +
                            "where you are, and not what is on your screen."
                    } else {
                        "Whether your phone thinks you are walking or still. " +
                            "Not where you are, not what you are doing, not " +
                            "which apps you use."
                    },
                )

                SectionHeading("Where it stays")
                SmallCopy(
                    "On this phone. " +
                        if (settings.scrollCues) {
                            "Neither your movement nor which apps you open is "
                        } else {
                            "Your movement is "
                        } +
                        "ever sent to us or shared with your family — not as a " +
                        "summary, not ever. The only things that leave are the " +
                        "ones you chose: that a reminder appeared, and what you " +
                        "decided to do about it.",
                )

                SectionHeading("What you keep control of")
                // "You choose those numbers" is a claim about both, and for a
                // while it was only true of one: the gap between reminders was
                // enforced with its stepper removed from settings. The stepper
                // is back, so the sentence is honest again -- but it is the
                // kind of sentence to re-read whenever a control moves, since
                // a screen whose whole job is being believed cannot offer a
                // choice that is not there.
                // The gap can now be nothing, so the sentence has to be able
                // to say so. Promising "at least 0 minutes between them" is
                // worse than saying there is no gap.
                val gap = settings.thresholds.cooldownMinutes
                val spacing = if (gap > 0) {
                    ", with at least $gap minutes between them"
                } else {
                    ", with no enforced gap between them"
                }
                SmallCopy(
                    "Every reminder can be dismissed, and dismissing costs nothing — " +
                        "there is no streak to break. At most " +
                        "${settings.thresholds.dailyCap} a day$spacing. You choose " +
                        "those numbers, and you can turn this off whenever you like.",
                )
            }

            // Which moments are live, and the only place the scrolling one
            // can be switched off once onboarding is behind you.
            //
            // The policy text on the onboarding screen promises this screen by
            // name -- "you can switch this off in Settings at any time" -- so
            // this block is load-bearing for a consent claim, not a
            // convenience. If it moves, that sentence moves with it.
            SoftSurface {
                SectionHeading("When a reminder can arrive")
                SmallCopy(
                    "After a walk of at least " +
                        "${settings.thresholds.walkingMinutes} minutes. This one " +
                        "is always on \u2014 it is what Harbor is for.",
                    size = 13,
                )
                SmallCopy(
                    if (settings.scrollCues) {
                        "And after a long stretch in one app. Harbor reads which " +
                            "app is in front and for how long, never what is on " +
                            "the screen."
                    } else {
                        "Harbor is not watching how long you spend in other apps."
                    },
                    size = 13,
                )
                QuietAction(
                    if (settings.scrollCues) {
                        "Stop watching for long stretches"
                    } else {
                        "Also catch me after a long stretch"
                    },
                ) {
                    scope.launch {
                        store.setSettings(settings.copy(scrollCues = !settings.scrollCues))
                    }
                }

                // On, but Android has not been told to allow it. Silent
                // otherwise: the setting would read as working and no
                // reminder would ever come of it.
                if (settings.scrollCues && !canSeeApps) {
                    Notice(
                        "Android keeps this behind a switch of its own. Until " +
                            "it is on, Harbor cannot tell which app is in front " +
                            "and this trigger cannot fire.",
                    )
                    ScrollWatch.request(context)?.let { intent ->
                        QuietAction("Open usage access") {
                            ScrollWatch.open(context, intent)
                        }
                    }
                }

                // Granted, but the reminder would arrive as a banner over
                // the feed rather than taking the screen. Not a failure --
                // the cue still works -- so this is quieter than the notice
                // above it.
                if (settings.scrollCues && canSeeApps && !canOpenOver) {
                    SmallCopy(
                        "A reminder while you are scrolling will arrive as a " +
                            "banner. Letting Harbor open over other apps gives it " +
                            "the whole screen instead.",
                        size = 13,
                    )
                    QuietAction("Let a reminder open over an app") {
                        context.startActivity(CueNotifier.overlaySettings(context))
                    }
                }
            }

            // Without someone to call, a cue can only say "someone at home" and
            // cannot dial. Worth surfacing before the switch, not after.
            val who = contact.firstOrNull()
            SoftSurface {
                SectionHeading("Who you would call")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (who != null) {
                        Avatar(who.label, who.tone, size = AvatarSize.SM)
                        Spacer(Modifier.size(12.dp))
                    }
                    SmallCopy(
                        who?.let { "${it.label} — ${it.phoneE164}" }
                            ?: "Nobody yet. A reminder needs someone to be about.",
                    )
                }
                QuietAction(if (who == null) "Choose someone" else "Change") {
                    onEditContact()
                }

                if (who != null) {
                    // The prototype's Slack Tide screen has the same thing: a
                    // way to see a cue without waiting for a walk. It is not
                    // debug scaffolding — TriggerSource.MANUAL is in the model
                    // and CuePolicy already lets a manual request past every
                    // gate, on the grounds that someone standing there asking
                    // for the prompt should get it.
                    QuietAction("Show me a reminder now") {
                        scope.launch { showManualCue(context, store, who) }
                    }
                    SmallCopy(
                        "Hear their sound and see the moment, without waiting " +
                            "for a walk. This does not use up today's allowance.",
                        size = 13,
                    )
                }
            }

            when {
                Sensing.isActive(context, store) -> {
                    SmallCopy(
                        "Reminders are on. Harbor will wait for a walk of at least " +
                            "${settings.thresholds.walkingMinutes} minutes.",
                        size = 15,
                    )

                    // Proof, rather than reassurance. "Cues are on" only means
                    // the switch is on and the permission was granted; this is
                    // the only thing on the screen that knows whether the
                    // phone is actually still talking to us.
                    val heard = Sensing.lastTransition(context)
                    val now = Instant.now()
                    when (Liveness.state(heard, now)) {
                        Liveness.State.NEVER -> Notice(
                            "Your phone has not told Harbor anything yet. That is " +
                                "normal for the first few minutes \u2014 it starts once " +
                                "you move about.",
                        )
                        Liveness.State.HEALTHY -> SmallCopy(
                            "Last noticed you moving " + Liveness.phrase(heard, now) + ".",
                            size = 13,
                        )
                        Liveness.State.QUIET_TOO_LONG -> {
                            // The old copy said to open Harbor now and then,
                            // which is the remedy only when there is nothing
                            // better. There is: the exemption below is the
                            // thing that stops the phone doing this at all.
                            val remedy = if (canStayAwake) {
                                " Opening Harbor now and then wakes it up again."
                            } else {
                                " The setting below is what stops that happening."
                            }
                            Notice(
                                "Harbor has not heard from your phone since " +
                                    Liveness.phrase(heard, now) +
                                    ". It has probably been put to sleep in the " +
                                    "background." + remedy,
                            )
                        }
                    }
                    // What the last walk measured, and what became of it.
                    //
                    // The liveness line above says the phone is still talking
                    // to Harbor. This says what it said. Somebody testing this
                    // on their own phone -- walking, stopping, and seeing
                    // nothing -- had no way to tell a walk that was never
                    // sensed from one that was measured at four minutes and
                    // refused for being under their threshold, and those need
                    // opposite fixes.
                    Sensing.lastBout(context)?.let { bout ->
                        SmallCopy(lastWalkPhrase(bout, settings), size = 13)
                    }
                    if (settings.scrollCues && canSeeApps) {
                        SmallCopy(lastStretchPhrase(context, settings), size = 13)
                    }

                    QuietAction("Turn reminders off") {
                        scope.launch { Sensing.disable(context, store) }
                    }
                }

                // The setting says on, but the permission has since been revoked
                // from system settings. Saying "cues are on" here would be a lie
                // the user has no way to catch.
                settings.cuesEnabled && !hasPermission -> {
                    SmallCopy(
                        "Reminders are paused. Harbor no longer has permission to " +
                            "notice when you stop walking.",
                        size = 15,
                    )
                    PrimaryAction("Give permission again", onClick = ::turnOn)
                }

                else -> {
                    PrimaryAction("Turn on gentle reminders", onClick = ::turnOn)
                    SmallCopy(
                        "You can do this later. Harbor works without it — you " +
                            "can always start a moment yourself.",
                        size = 13,
                    )
                }
            }

            // Everything a cue needs that is not "cues are on".
            //
            // Four separate things have to be true before a reminder reaches
            // somebody, and turning reminders on only settles the first. The
            // other three fail silently, which is the whole problem: Harbor
            // senses the walk, writes the beat, posts the reminder, and the
            // person sees nothing. Reading "moving, 3 minutes ago" on this
            // screen while never having seen a reminder is what that looks
            // like from the outside, and nothing anywhere said why.
            //
            // Onboarding asks for all three. This screen asked for two, which
            // meant the one somebody refused or skipped in onboarding had no
            // second chance anywhere in the app -- and battery, the one it was
            // missing, is the one that loses the walk rather than the
            // reminder.
            if (settings.cuesEnabled && hasPermission &&
                (!canNotify || !canTakeScreen || !canStayAwake)
            ) {
                Surface {
                    SectionHeading("A reminder would not reach you yet")
                    // First, because it is the one that loses the walk itself.
                    // The other two drop a reminder that was made; this one
                    // means the app was never woken to make it, and on One UI
                    // it is the measured default rather than an edge case.
                    if (!canStayAwake) {
                        SmallCopy(
                            "Your phone can put Harbor to sleep to save battery. " +
                                "Asleep, it never hears that your walk ended — the " +
                                "reminder is not late, it never happens. This is the " +
                                "one that matters most.",
                            size = 14,
                        )
                        PrimaryAction("Let Harbor keep listening") {
                            context.startActivity(Sensing.unrestrictedRequest(context))
                        }
                    }
                    if (!canNotify) {
                        SmallCopy(
                            "Notifications are off for Harbor. A reminder is posted " +
                                "as one, so with these off it is thrown away " +
                                "the moment it is made and nothing appears.",
                            size = 14,
                        )
                        PrimaryAction("Allow notifications") {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    }
                    if (!canTakeScreen) {
                        SmallCopy(
                            "Android only lets an app take over the screen if " +
                                "you allow it by hand. Without it a reminder arrives " +
                                "as a banner that fades on its own, so if your " +
                                "phone is in your pocket you will miss it.",
                            size = 14,
                        )
                        CueNotifier.fullScreenSettings(context)?.let { intent ->
                            PrimaryAction("Let a reminder open the screen") {
                                context.startActivity(intent)
                            }
                        }
                    }
                }
            }

            if (refused) {
                Surface {
                    SmallCopy(
                        "That is completely fine. Reminders stay off, and nothing " +
                            "else changes. If you change your mind, Android may " +
                            "not ask again — you can grant it from system settings.",
                    )
                    TextLink("Open system settings", onClick = { openAppSettings(context) })
                    // Rechecking on resume would need a lifecycle observer whose
                    // API has moved around between Compose versions. A link the
                    // user presses is duller and cannot break.
                    TextLink("I have granted it — check again", onClick = {
                        hasPermission = ActivityTransitions.hasPermission(context)
                    })
                }
            }

            if (failed) {
                Notice(
                    "Harbor could not start listening. Google Play services may " +
                        "be unavailable on this phone. Reminders stay off rather than " +
                        "pretending to work.",
                )
            }

            TextLink("See your garden", onOpenGarden)
        }
    }
}

/**
 * The last walk in a sentence: when it was, how long Harbor made it, and why
 * it did or did not become a reminder.
 *
 * The reason is spelled out rather than named. `BELOW_THRESHOLD` is precise
 * and means nothing to the person holding the phone; "shorter than the 10
 * minutes you asked for" is the same fact and is actionable, because the
 * number it mentions is one they can change on this screen.
 */
private fun lastWalkPhrase(
    bout: app.harbor.sensing.SensingStore.Recorded,
    settings: app.harbor.domain.UserSettings,
): String {
    val zone = ZoneId.systemDefault()
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val window = clock.format(bout.startedAt.atZone(zone)) + "–" +
        clock.format(bout.endedAt.atZone(zone))
    val length = if (bout.minutes == 1) "1 minute" else "${bout.minutes} minutes"

    val outcome = when (bout.outcome) {
        null -> "That became a reminder."
        CuePolicy.Reason.BELOW_THRESHOLD.name ->
            "No reminder — shorter than the " +
                "${settings.thresholds.walkingMinutes} minutes you asked for."
        CuePolicy.Reason.IN_CLASS.name ->
            "No reminder — you had marked that time busy."
        CuePolicy.Reason.DAILY_CAP_REACHED.name ->
            "No reminder — today's ${settings.thresholds.dailyCap} were already used."
        CuePolicy.Reason.IN_COOLDOWN.name ->
            "No reminder — less than " +
                "${settings.thresholds.cooldownMinutes} minutes since the last one."
        CuePolicy.Reason.ALREADY_CONNECTED_TODAY.name ->
            "No reminder — you had already reached them today."
        CuePolicy.Reason.REMINDER_PENDING.name ->
            "No reminder — you had planned a later time."
        CuePolicy.Reason.CUES_DISABLED.name ->
            "No reminder — reminders were off at the time."
        CuePolicy.Reason.TRANSITION_UNSETTLED.name ->
            "Waiting to see whether you stay still."
        app.harbor.sensing.SensingStore.WALKING_RESUMED ->
            "No reminder \u2014 you set off again before it was sure you had stopped."
        app.harbor.sensing.SensingStore.SETTLE_EXPIRED ->
            "No reminder \u2014 your phone woke Harbor too late, and the moment had passed."
        // A reason added later and not given words here. Better than dropping
        // the line: the walk was still measured, and that is most of the
        // answer.
        else -> "No reminder."
    }

    return "Last walk: $window, measured as $length. $outcome"
}

/**
 * The same, for a long stretch in one app.
 *
 * Says the app's own name rather than its package, because `com.instagram.
 * android` is not what somebody calls it, and because a screen that reports
 * your behaviour back to you should do it in your words. The name is read
 * from the installed package and never leaves the phone -- it is not in the
 * ledger and [app.harbor.domain.StudyExport] has no field for it.
 *
 * The "nothing yet" case is the one this exists for. A participant whose
 * usage access was revoked sees no stretches at all, which is the difference
 * between a quiet week and a broken trigger, and nothing else on this screen
 * can tell them apart.
 */
private fun lastStretchPhrase(
    context: Context,
    settings: app.harbor.domain.UserSettings,
): String {
    val watched = Sensing.lastStretch(context)
        ?: return "No long stretch in one app yet. Harbor is watching for " +
            "${settings.thresholds.sessionMinutes} minutes in the same one."

    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val began = clock.format(watched.startedAt.atZone(ZoneId.systemDefault()))
    val length = if (watched.minutes == 1) "1 minute" else "${watched.minutes} minutes"
    val app = appLabel(context, watched.packageName)

    val outcome = when (watched.outcome) {
        null -> "That became a reminder."
        CuePolicy.Reason.SOURCE_OFF.name ->
            "No reminder — this trigger was off at the time."
        CuePolicy.Reason.IN_CLASS.name ->
            "No reminder — you had marked that time busy."
        CuePolicy.Reason.DAILY_CAP_REACHED.name ->
            "No reminder — today's ${settings.thresholds.dailyCap} were already used."
        CuePolicy.Reason.SOURCE_CAP_REACHED.name ->
            "No reminder — this trigger had had its " +
                "${settings.thresholds.perSourceCap} for today."
        CuePolicy.Reason.IN_COOLDOWN.name ->
            "No reminder — less than " +
                "${settings.thresholds.cooldownMinutes} minutes since the last one."
        CuePolicy.Reason.ALREADY_CONNECTED_TODAY.name ->
            "No reminder — you had already reached them today."
        CuePolicy.Reason.REMINDER_PENDING.name ->
            "No reminder — you had planned a later time."
        CuePolicy.Reason.CUES_DISABLED.name ->
            "No reminder — reminders were off at the time."
        else -> "No reminder."
    }

    return "Last long stretch: $app from $began, $length. $outcome"
}

/** What the launcher calls a package, or the package name if it has gone. */
private fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (e: Throwable) {
    // Uninstalled since, or hidden behind package visibility. The package
    // name is worse to read and better than dropping the line.
    packageName
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * Records a manual cue and builds the intent that shows it, without starting
 * it — split out of [showManualCue] so onboarding can launch it through a
 * result launcher and find out when the preview closes.
 *
 * Not debug scaffolding: [TriggerSource.MANUAL] is in the model and
 * [CuePolicy] lets a manual request past every gate, on the grounds that
 * somebody standing there asking for the prompt should get it. It is also the
 * most persuasive thing onboarding can do — hearing her ringtone once explains
 * the app better than a screen of copy about it.
 */
internal suspend fun manualCueIntent(
    context: Context,
    store: HarborRepository,
    who: Contact,
    /** True only for onboarding's own preview — see [CueNotifier.EXTRA_SKIP_PULSE]. */
    skipPulse: Boolean = false,
): Intent {
    val now = Instant.now()
    val cue = Cue(
        id = UUID.randomUUID(),
        firedDate = now.atZone(ZoneId.systemDefault()).toLocalDate(),
        triggerSource = TriggerSource.MANUAL,
        firedAt = now,
    )
    store.recordCue(cue)
    return Intent(context, CueActivity::class.java).apply {
        putExtra(CueNotifier.EXTRA_CUE_ID, cue.id.toString())
        putExtra(CueNotifier.EXTRA_CONTACT_ID, who.id.toString())
        putExtra(CueNotifier.EXTRA_SOURCE, TriggerSource.MANUAL.name)
        putExtra(CueNotifier.EXTRA_SKIP_PULSE, skipPulse)
    }
}

/** Fires a real cue on demand and shows it immediately. See [manualCueIntent]. */
internal suspend fun showManualCue(
    context: Context,
    store: HarborRepository,
    who: Contact,
    skipPulse: Boolean = false,
) {
    context.startActivity(manualCueIntent(context, store, who, skipPulse))
}
