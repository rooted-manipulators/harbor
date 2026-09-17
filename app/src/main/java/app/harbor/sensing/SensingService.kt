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
 * This service does **no work**. It starts nothing, listens to nothing and
 * holds no wake lock — [TransitionReceiver] still does all of it, woken by
 * Play services exactly as before. Its entire job is to be a reason not to
 * freeze the process. Keep it that way: anything that actually runs here is
 * battery spent on every participant's phone, all week.
 */
class SensingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

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
        // Restarted if the system kills us, which is the whole point.
        return START_STICKY
    }

    companion object {

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
                .setContentText("So a reminder can reach you after a walk.")
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
