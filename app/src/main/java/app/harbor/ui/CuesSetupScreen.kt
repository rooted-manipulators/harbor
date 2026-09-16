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
import app.harbor.domain.Cue
import app.harbor.domain.Liveness
import app.harbor.domain.TriggerSource
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import app.harbor.sensing.ActivityTransitions
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
    var canTakeScreen by remember { mutableStateOf(true) }
    var canNotify by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canTakeScreen = CueNotifier.canTakeTheScreen(context)
                canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
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
                SmallCopy(
                    "Whether your phone thinks you are walking or still. Not " +
                        "where you are, not what you are doing, not which apps " +
                        "you use.",
                )

                SectionHeading("Where it stays")
                SmallCopy(
                    "On this phone. Your movement is never sent to us and never " +
                        "shared with your family — not as a summary, not ever. " +
                        "The only things that leave are the ones you chose: that " +
                        "a reminder appeared, and what you decided to do about it.",
                )

                SectionHeading("What you keep control of")
                SmallCopy(
                    "Every reminder can be dismissed, and dismissing costs nothing — " +
                        "there is no streak to break. At most " +
                        "${settings.thresholds.dailyCap} a day, with at least " +
                        "${settings.thresholds.cooldownMinutes} minutes between " +
                        "them. You choose those numbers, and you can turn this " +
                        "off whenever you like.",
                )
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
                        Liveness.State.QUIET_TOO_LONG -> Notice(
                            "Harbor has not heard from your phone since " +
                                Liveness.phrase(heard, now) + ". It may have been put " +
                                "to sleep in the background. Opening Harbor now and " +
                                "then keeps it awake.",
                        )
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
            // Three separate things have to be true before a cue reaches
            // somebody, and turning cues on only settles the first. The other
            // two fail silently, which is the whole problem: Harbor senses the
            // walk, writes the beat, posts the cue, and the person sees
            // nothing. Reading "moving, 3 minutes ago" on this screen while
            // never having seen a cue is what that looks like from the
            // outside, and nothing anywhere said why.
            if (settings.cuesEnabled && hasPermission && (!canNotify || !canTakeScreen)) {
                Surface {
                    SectionHeading("A reminder would not reach you yet")
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
