package io.github.eiji_der_sonnige.conversa.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.eiji_der_sonnige.conversa.MainActivity
import io.github.eiji_der_sonnige.conversa.R

/** Benachrichtigungen zeigen absichtlich keinen Inhalt und keinen Absender (Sperrbildschirm-Schutz). */
object Notifier {
    private const val CHANNEL = "messages"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Nachrichten", NotificationManager.IMPORTANCE_HIGH).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun newMessage(ctx: Context, peer: String) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_conversa)
            .setContentTitle("Conversa")
            .setContentText("Neue verschlüsselte Nachricht")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(peer.hashCode(), n)
        } catch (_: SecurityException) {
        }
    }
}
