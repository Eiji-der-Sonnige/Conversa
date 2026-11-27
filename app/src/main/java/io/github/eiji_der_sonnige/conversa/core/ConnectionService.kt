package io.github.eiji_der_sonnige.conversa.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import io.github.eiji_der_sonnige.conversa.data.AppSettings
import io.github.eiji_der_sonnige.conversa.data.SecureFiles
import kotlin.concurrent.thread

/**
 * Optionaler Vordergrunddienst: hält die Verbindung zu den Relays offen, damit Nachrichten und Anrufe
 * auch bei geschlossener App und im Standby ankommen. Android verlangt dafür einen dauerhaften Hinweis
 * in der Benachrichtigungsleiste. Kein Google-Dienst (FCM) beteiligt – dafür kostet es etwas Akku.
 */
class ConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this, Notifier.ID_SERVICE, Notifier.serviceNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (e: Exception) {
            // z. B. ForegroundServiceStartNotAllowedException, wenn Android den Start aus dem Hintergrund verbietet
            Log.w(TAG, "Hintergrunddienst nicht erlaubt: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        thread(name = "conversa-restore") {
            val enabled = AppSettings.load(this).background
            if (!enabled || Session.restore(applicationContext) == null) {
                stopSelf()
            } else {
                scheduleHeartbeat(this)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancelHeartbeat(this)
        super.onDestroy()
    }

    /** Prüft in Abständen die Verbindungen – per Wecker, weil Timer im Tiefschlaf des Geräts stehen bleiben. */
    class HeartbeatReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val wl = ctx.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "conversa:heartbeat")
            wl.acquire(10_000) // Zeit für Antwort der Relays bzw. Neuverbindung
            Session.current()?.heartbeatNow()
            if (Session.current() != null) scheduleHeartbeat(ctx)
        }
    }

    /** Startet den Dienst nach einem Neustart des Handys oder einem App-Update wieder. */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
            val pending = goAsync()
            thread(name = "conversa-boot") {
                try {
                    if (AppSettings.load(ctx).background && SecureFiles.readJson(ctx, Session.FILE) != null) start(ctx)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "ConnectionService"
        private const val HEARTBEAT_MS = 5 * 60_000L

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, ConnectionService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Start nicht möglich: ${e.message}")
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ConnectionService::class.java))
        }

        private fun alarmIntent(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, HeartbeatReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun scheduleHeartbeat(ctx: Context) {
            // Ungenauer Wecker genügt (keine Sonderberechtigung nötig); im Doze-Modus bündelt Android ihn
            // mit anderen Aufwachvorgängen, das spart zusätzlich Akku.
            ctx.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + HEARTBEAT_MS, alarmIntent(ctx)
            )
        }

        private fun cancelHeartbeat(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
        }
    }
}

/** „Ablehnen“ in der Anruf-Benachrichtigung. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_REJECT) Session.current()?.calls?.reject()
        Notifier.cancelCall(ctx)
    }

    companion object {
        const val ACTION_REJECT = "io.github.eiji_der_sonnige.conversa.REJECT_CALL"
    }
}
