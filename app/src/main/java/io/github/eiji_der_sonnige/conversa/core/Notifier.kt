package io.github.eiji_der_sonnige.conversa.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.eiji_der_sonnige.conversa.MainActivity
import io.github.eiji_der_sonnige.conversa.R

/** Benachrichtigungen zeigen absichtlich keinen Inhalt und keinen Absender (Sperrbildschirm-Schutz). */
object Notifier {
    private const val CHANNEL = "messages"
    private const val CHANNEL_CALLS = "calls"
    const val CHANNEL_SERVICE = "background"
    const val ID_SERVICE = 1001
    private const val ID_CALL = 1002

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Nachrichten", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CALLS, "Eingehende Anrufe", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Hintergrundverbindung", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Dauerhafter Hinweis, solange Conversa im Hintergrund verbunden bleibt. Kann ausgeblendet werden."
                setShowBadge(false)
            }
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun newMessage(ctx: Context, peer: String) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_conversa)
            .setContentTitle("Conversa")
            .setContentText("Neue verschlüsselte Nachricht")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(peer.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }

    fun cancelMessage(ctx: Context, peer: String) {
        NotificationManagerCompat.from(ctx).cancel(peer.hashCode())
    }

    /** Klingelt, wenn die App nicht im Vordergrund ist. Zeigt nicht, wer anruft. */
    fun incomingCall(ctx: Context, video: Boolean) {
        if (!allowed(ctx)) return
        val reject = PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_REJECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = openApp(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_conversa)
            .setContentTitle(if (video) "Eingehender Videoanruf" else "Eingehender Anruf")
            .setContentText("Tippen, um Conversa zu öffnen und anzunehmen")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "Öffnen", open)
            .addAction(0, "Ablehnen", reject)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT } // klingelt wiederholt bis zur Reaktion
        try {
            NotificationManagerCompat.from(ctx).notify(ID_CALL, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancelCall(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_CALL)
    }

    fun serviceNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_conversa)
            .setContentTitle("Conversa empfängt im Hintergrund")
            .setContentText("Verbunden mit den Relays. Abschaltbar in den Einstellungen.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openApp(ctx))
            .build()
}
