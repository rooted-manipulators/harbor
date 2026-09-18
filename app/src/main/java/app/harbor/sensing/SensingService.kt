package app.harbor.sensing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.harbor.MainActivity
import app.harbor.R
import app.harbor.data.HarborStore
import app.harbor.domain.CuePolicy
import app.harbor.domain.TriggerSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * The process that stays alive so a walk can become a reminder.
 *
 * ## Why this exists, against ADR-008
 *
 * ADR-008 said no foreground service: Play services delivers transitions to a
 * PendingIntent whether or not Harbor is running, so a service of our own
 * would add a permanent notification and a battery footprint in exchange for
 * nothing. That reasoning was right about Android and wrong about phones.
 *
 * What it missed is that "whether or not Harbor is running" is only true while
 * the OS is willing to wake it. A cached process is frozen by every OEM
 * skin worth worrying about — measured on a Galaxy S24+ (One UI, Android 16)
 * on 17 Sep 2026: frozen roughly two minutes after backgrounding
 * (`FreecessController: FZ ... reason: LEV`), and three real walks produced
 * nothing at all. The only transitions that ever arrived came while the app
 * happened to be on screen. Xiaomi, Realme, Oppo and Vivo are all more
 * aggressive than Samsung, and those are the phones this study runs on.
 *
 * So the trade ADR-008 made is reversed knowingly: one quiet, permanent line
 * in the notification shade, in exchange for the cue working at all. A process
 * holding a foreground service is never cached, so it is never frozen, and the
 * transition arrives. The amendment is recorded in `docs/01-decisions.md`.
 *
 * For the walking trigger this service does **no work**. It starts nothing,
 * listens to nothing and holds no wake lock — [TransitionReceiver] still does
 * all of it, woken by Play services exactly as before. Its entire job there is
 * to be a reason not to freeze the process.
 *
 * ## The one thing it does run
 *
 * The scrolling trigger has no Play services to wake it. Nothing in Android
 * will say "this person has been in one app for twenty minutes"; the events
 * it *will* offer — an app launching, the screen going off — are both the
 * wrong end of the stretch. So it has to be asked for, and [watch] below is
 * the asking.
 *
 * This is put here rather than in an alarm because the cost is already paid:
 * a process that must stay unfrozen anyway can look at a clock for a great
 * deal less than a `setRepeating` costs in wakeups, and the loop stops
 * entirely for anybody who has not turned the trigger on. The old rule still
 * holds for everything else — anything that runs here is battery spent on
 * every participant's phone, all week, so it had better be the only way.
 */
class SensingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watching: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Typed explicitly rather than left to the manifest: from Android 14
        // the type is part of the call, and HEALTH is the one that covers
        // activity recognition -- which is the permission we already hold and
        // the only reason this service exists.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                0
            },
        )
        // One loop, however many times the service is started. Every
        // caller of start() is allowed to call it repeatedly -- launch,
        // boot, package replace, the settings screen -- and each one arrives
        // here as another onStartCommand.
        if (watching?.isActive != true) {
            watching = scope.launch {
                // The outer net. tick() catches a bad tick; this catches a
                // bad start -- a prefs file that will not parse, a system
                // service missing on an OEM build. An uncaught throw in a
                // launch goes to the thread's default handler, which means
                // the whole app dies every time the service starts, which
                // means the app cannot be opened to fix it. A trigger that
                // does not run is survivable. That is not.
                try {
                    watch()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "scroll watch stopped", e)
                }
            }
        }

        // Restarted if the system kills us, which is the whole point.
        return START_STICKY
    }

    /**
     * Run the ticker, but only while somebody wants it.
     *
     * `collectLatest` on the switch rather than a check inside the loop: a
     * participant who never takes the scrolling option should not have a
     * coroutine waking their phone every two minutes all week for the
     * privilege of reading a boolean and going back to sleep. Turning it off
     * in Settings cancels the ticker within a frame; turning it on starts
     * one.
     */
    private suspend fun watch() {
        val store = HarborStore(this)
        store.settings
            .map { it.cuesEnabled && it.scrollCues }
            .distinctUntilChanged()
            .collectLatest { wanted -> if (wanted) tick(store) }
    }

    /**
     * Ask, every so often, how long the person has been in one app, and hand
     * the answer to the policy when it gets long enough.
     *
     * Everything after the measurement is [CuePolicy]'s. The caps, the
     * cooldown, the busy blocks and the off switch are all checked there, by
     * the same code that judges a walk, which is the point of routing through
     * [CueGate] rather than posting a notification from here.
     *
     * ## Why the whole body is inside a try
     *
     * Because this runs for a week without supervision. A single throw --
     * a prefs file mid-write, an OEM usage service that 404s, an
     * `IllegalStateException` out of the notification manager -- would kill
     * the coroutine, and nothing restarts it until the next `onStartCommand`,
     * which on a phone that is never rebooted may be never. The trigger would
     * go quiet for the rest of the study and the app would still say it was
     * on. One bad tick has to cost one tick.
     *
     * `ensureActive` before the catch so cancellation still cancels:
     * [collectLatest] cancels this body by throwing into it, and a bare
     * `catch (Throwable)` would swallow that and spin forever.
     */
    private suspend fun tick(store: HarborStore) {
        val sensing = SensingStore(this)
        var wait = POLL

        while (true) {
            delay(wait.toMillis())
            wait = POLL
            try {
                wait = consider(store, sensing)
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "scroll tick failed", e)
            }
        }
    }

    /**
     * One look at the clock.
     *
     * @return how long to wait before the next one. Normally [POLL], but
     *   shortened when a stretch is already running and its threshold falls
     *   inside the next tick — so the reminder lands near the twenty minutes
     *   somebody asked for rather than up to two minutes past it. Nothing
     *   here is allowed to wait less than [FLOOR]: a poll that tightens
     *   without limit is a spin.
     */
    private suspend fun consider(store: HarborStore, sensing: SensingStore): Duration {
        val settings = store.settings.value
        if (!ScrollWatch.hasPermission(this)) return POLL

        val now = Instant.now()
        val stretch = ScrollWatch.current(this, now) ?: return POLL

        val minutes = stretch.minutesAt(now)
        val threshold = settings.thresholds.sessionMinutes
        if (minutes < threshold) {
            val left = Duration.ofMinutes((threshold - minutes).toLong())
            return if (left < POLL) maxOf(left, FLOOR) else POLL
        }

        // Asked already? A stretch that fired is finished with; one that was
        // held gets another go once the reasons have had time to change. See
        // SensingStore.shouldAsk, where the argument and the tests live.
        if (!SensingStore.shouldAsk(sensing.offer, stretch, now)) return POLL

        val decision = CueGate.consider(
            this,
            store,
            CuePolicy.Signal(
                source = TriggerSource.SESSION_END,
                activeMinutes = minutes,
                // There is no earlier moment this refers to. A walk's signal
                // points back at the instant the person went still; a stretch
                // is happening now, and now is when it is worth interrupting.
                stillSince = now,
            ),
            now,
        )

        val held = decision as? CuePolicy.Decision.Hold
        sensing.offer = SensingStore.Offer(stretch, now, fired = held == null)
        // Kept whatever the answer, because the refusals are what somebody
        // goes looking for when nothing arrives. See SensingStore.lastStretch.
        sensing.lastStretch = SensingStore.Watched(
            packageName = stretch.packageName,
            startedAt = stretch.startedAt,
            minutes = minutes,
            outcome = held?.reason?.name,
        )
        return POLL
    }

    companion object {

        /**
         * How often the scrolling stretch is measured.
         *
         * Two minutes against a threshold measured in tens of them. Finer
         * would buy precision nobody can feel -- a reminder at twenty-one
         * minutes rather than twenty is the same reminder -- and would cost
         * it on every phone in the study, all week.
         */
        private val POLL: Duration = Duration.ofMinutes(2)

        /**
         * The shortest the poll may ever be, however close a threshold looks.
         *
         * A guard rather than a tuning knob. The arithmetic above shortens
         * the wait to land on the threshold, and a clock that jumps backwards
         * -- an NTP correction, a timezone with a DST rule -- can make that
         * arithmetic ask for zero. Zero is a spin on a foreground service.
         */
        private val FLOOR: Duration = Duration.ofSeconds(20)

        private const val CHANNEL = "sensing"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "HarborSensing"

        /**
         * Safe to call repeatedly — starting a running service just delivers
         * another `onStartCommand`.
         *
         * Swallows its own failure on purpose. Background starts are
         * restricted from Android 12, and while every caller here is either
         * user-initiated or boot (both exempt), an exemption we have misread
         * should cost a log line rather than take down sensing or crash a
         * receiver.
         */
        fun start(context: Context) {
            val intent = Intent(context, SensingService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "could not start sensing service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SensingService::class.java))
        }

        /**
         * Quiet by construction: a low-importance channel, silent, no
         * vibration, no timestamp. It is a receipt, not an announcement — the
         * one notification Harbor shows that is not asking for anything.
         *
         * Do not raise its importance to make it more visible. The cue has its
         * own channel for that, and a second thing competing for attention is
         * exactly what this app is trying not to be.
         */
        private fun notification(context: Context): Notification {
            ensureChannel(context)

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Listening for the quiet moment")
                // Not "after a walk" any more. There are two triggers, and
                // the permanent line in somebody's shade should not describe
                // half of what the app is doing -- least of all the half
                // that does not involve reading which app is in front.
                .setContentText("So a reminder can reach you at a good moment.")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Listening",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description =
                        "The quiet line that keeps Harbor awake enough to notice a walk."
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }
    }
}
