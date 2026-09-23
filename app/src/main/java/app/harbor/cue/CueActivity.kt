package app.harbor.cue

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.harbor.data.HarborRepository
import app.harbor.data.HarborStore
import app.harbor.domain.CallStats
import app.harbor.domain.Contact
import app.harbor.domain.FeedbackPulse
import app.harbor.domain.Feeling
import app.harbor.domain.FlowerKind
import app.harbor.domain.LedgerEntry
import app.harbor.domain.Moment
import app.harbor.domain.Resolution
import app.harbor.domain.TriggerSource
import app.harbor.ui.theme.HarborTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * The cue surface — pipeline stages 5 through 9.
 *
 * Call-shaped by design (ADR-009): full screen, over the lock screen, with the
 * contact's photo and their own ringtone playing. The association that sound
 * carries is the mechanism the whole trigger depends on.
 *
 * It never claims to be an incoming call. No "Mom is calling", no answer and
 * decline pair. Someone far from home who thinks their mother is unexpectedly
 * ringing will assume an emergency, and one such scare costs more trust than
 * the entire feature is worth.
 */
class CueActivity : ComponentActivity() {

    private val ringer by lazy { Ringer(this) }
    private lateinit var store: HarborRepository

    private var usualMinutes by mutableStateOf<Int?>(null)
    private var chosenTopic: String? = null

    private var cueId: UUID? = null
    private var contactId: UUID? = null

    /**
     * The entry this cue produced, held so the feedback pulse can amend it
     * rather than write a second row.
     *
     * The id is generated once and reused, which makes [HarborRepository.append]
     * an idempotent replace — the same guarantee the server's upsert gives.
     */
    private var source: TriggerSource = TriggerSource.WALKING_STOP
    private var entryId: UUID? = null
    private var skipPulse = false

    /** The row as last written, so a partial amendment can build on it. */
    private var lastEntry: LedgerEntry? = null
    private var resolution: Resolution? = null
    private var proposedTime: Instant? = null

    /** Cue, or the reflection that follows a call. */
    private var phase by mutableStateOf(Phase.CUE)

    /** When we handed off to the dialer, so the call can be timed. */
    private var dialedAt: Instant? = null
    private var measuredMinutes = 1

    private enum class Phase { CUE, CALL }

    /**
     * The dialer is a different app, so the only way back here is the user
     * returning. That return is the end of the call, near enough — and it is
     * the only measurement available without reading the call log, which would
     * cost a permission this app is not willing to spend (ADR-002).
     *
     * The stepper on the reflection screen lets them correct it, which the
     * prototype has anyway.
     */
    override fun onResume() {
        super.onResume()
        val dialed = dialedAt ?: return
        if (phase != Phase.CUE) return

        measuredMinutes = Duration.between(dialed, Instant.now())
            .toMinutes().toInt().coerceIn(1, 180)
        phase = Phase.CALL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Present like a call: wake the screen, show over the keyguard. The
        // quiet moment after a walk is often a moment with the phone in a
        // pocket and the screen dark.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        store = HarborStore(applicationContext)
        cueId = intent.getStringExtra(CueNotifier.EXTRA_CUE_ID)?.let(UUID::fromString)
        contactId = intent.getStringExtra(CueNotifier.EXTRA_CONTACT_ID)?.let(UUID::fromString)
        // Whatever actually raised this cue. Hardcoding WALKING_STOP here
        // meant a cue the user asked for was written to the ledger as a sensed
        // one — which would have quietly inflated the single number the study
        // exists to measure.
        source = intent.getStringExtra(CueNotifier.EXTRA_SOURCE)
            ?.let { runCatching { TriggerSource.valueOf(it) }.getOrNull() }
            ?: TriggerSource.WALKING_STOP
        skipPulse = intent.getBooleanExtra(CueNotifier.EXTRA_SKIP_PULSE, false)
        val contact = store.contacts.value.firstOrNull { it.id == contactId }

        lifecycleScope.launch { store.note(Moment.CUE_SHOWN, source.name) }

        // The notification has done its job; the surface takes over the sound.
        CueNotifier.cancel(this)
        ringer.start(contact?.cueSoundRef)

        // "Calls with her usually run ~12 min", so the ask has a known size
        // before anyone agrees to it.
        lifecycleScope.launch {
            val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
            usualMinutes = withContext(Dispatchers.IO) {
                CallStats.usualMinutes(store.recentEntries(), contact?.id)
            }
        }

        setContent {
            HarborTheme {
                Surface(Modifier.fillMaxSize().imePadding()) {
                    when (phase) {
                        Phase.CUE -> CueSurface(
                            contact = contact,
                            usualMinutes = usualMinutes,
                            source = source,
                            onRecord = ::record,
                            onCall = ::placeCall,
                            onDismiss = ::dismissAndFinish,
                        )

                        Phase.CALL -> CallFlow(
                            who = contact?.label ?: "them",
                            measuredMinutes = measuredMinutes,
                            initialTopic = chosenTopic,
                            reducedMotion = store.settings.value.reducedMotion,
                            askPulse = !skipPulse,
                            onPlant = { minutes, flower, topic ->
                                record(
                                    resolution = Resolution.CALLED,
                                    callMinutes = minutes,
                                    flower = flower,
                                    topic = topic,
                                )
                            },
                            onPulse = { pulse ->
                                record(
                                    resolution = Resolution.CALLED,
                                    pulse = pulse,
                                    callMinutes = measuredMinutes,
                                )
                            },
                            onNotReached = {
                                record(resolution = Resolution.NOT_REACHED)
                                finish()
                            },
                            onDone = { finish() },
                        )
                    }
                }
            }
        }
    }

    /**
     * Stage 9, and stage 8 when the pulse arrives afterwards.
     *
     * Called twice for most cues: once when the user picks an option, once
     * more if they answer "was this a good moment?". The second call must
     * amend the first entry, not write another — so the id is generated once
     * and the resolution and any proposed time are remembered rather than
     * passed back in. Passing them back was how an earlier version of this
     * managed to lose every feedback pulse, and to crash on a proposed-later
     * cue whose second call had no time attached.
     *
     * The cue itself was recorded when it fired and already spent one of the
     * user's daily allowance. This only records what they chose.
     */
    private fun record(
        resolution: Resolution,
        proposedTime: Instant? = null,
        pulse: FeedbackPulse? = null,
        callMinutes: Int? = null,
        feeling: Feeling? = null,
        flower: FlowerKind? = null,
        topic: String? = null,
    ) {
        // A real answer is never overwritten by a later dismissal — closing
        // the screen after choosing is not undoing the choice.
        if (this.resolution != null && resolution == Resolution.DISMISSED) return

        ringer.stop()

        this.resolution = resolution
        if (proposedTime != null) this.proposedTime = proposedTime
        val id = entryId ?: UUID.randomUUID().also { entryId = it }

        // What was already answered survives a later partial write.
        //
        // This rebuilds the whole row against a stable id, so a call that
        // carries only the pulse would otherwise erase the flower the user
        // planted a second earlier -- and the pulse is asked immediately
        // after planting, so that was very nearly every call.
        val prior = lastEntry

        val now = Instant.now()
        val entry = LedgerEntry(
            id = id,
            entryDate = now.atZone(ZoneId.systemDefault()).toLocalDate(),
            cueId = cueId,
            contactId = contactId,
            triggerSource = source,
            thresholdSnapshot = store.settings.value.thresholds,
            resolution = resolution,
            proposedTime = this.proposedTime.takeIf { resolution == Resolution.PROPOSED_LATER },
            // Carried rather than defaulted. This rebuilds the whole row
            // against a stable id and append replaces by id, so letting it
            // fall back to false would quietly re-open a plan already closed.
            reminderDone = prior?.reminderDone == true &&
                resolution == Resolution.PROPOSED_LATER,
            feedbackPulse = pulse ?: prior?.feedbackPulse,
            callMinutes = callMinutes ?: prior?.callMinutes,
            feeling = feeling ?: prior?.feeling,
            flower = flower ?: prior?.flower,
            topic = topic ?: prior?.topic,
            occurredAt = now,
        )
        lastEntry = entry

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { store.append(entry) }
            store.note(Moment.CUE_RESOLVED, resolution.name)
            if (flower != null) {
                store.note(Moment.FLOWER_PLANTED, flower.name, callMinutes)
            }
        }
    }

    /**
     * Hand off to the phone's own dialer and start the clock.
     *
     * The entry is written as soon as the dialer is in front of the user
     * rather than after the reflection, so a call that happened is recorded
     * even if they never come back to say how it went. The reflection amends
     * that same row.
     *
     * It is written *after* [Dialer] confirms the handoff, not before. This
     * cue is shown over the keyguard, and Android will not bring the dialer
     * over a locked screen: the old order recorded a call, watched the intent
     * quietly go nowhere, and then asked the user how it had gone.
     */
    private fun placeCall(topic: String?, number: String?) {
        chosenTopic = topic
        ringer.stop()

        Dialer.open(this, number) { outcome ->
            when (outcome) {
                Dialer.Outcome.Dialing -> {
                    record(Resolution.CALLED, topic = topic)
                    dialedAt = Instant.now()
                }

                // They were asked to unlock and said no. That is not a call,
                // and it is not a dismissal either: leave the cue standing.
                Dialer.Outcome.Locked -> Unit

                Dialer.Outcome.NoNumber ->
                    say("There is no number saved for them yet.")

                Dialer.Outcome.NoDialer ->
                    say("This phone has no app to make calls with.")
            }
        }
    }

    private fun say(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    /** Dismissal costs nothing, but it is still an answer, so it is recorded. */
    private fun dismissAndFinish() {
        record(Resolution.DISMISSED)
        ringer.stop()
        finish()
    }

    override fun onDestroy() {
        ringer.stop()
        super.onDestroy()
    }
}
