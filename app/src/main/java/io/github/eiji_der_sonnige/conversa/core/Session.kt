package io.github.eiji_der_sonnige.conversa.core

import android.content.Context
import io.github.eiji_der_sonnige.conversa.crypto.Identity
import io.github.eiji_der_sonnige.conversa.crypto.hexToBytes
import io.github.eiji_der_sonnige.conversa.data.AppSettings
import io.github.eiji_der_sonnige.conversa.data.SecureFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hält den laufenden Messenger app-weit (nicht in der Activity). So kann der Hintergrunddienst die
 * Verbindung offen halten, auch wenn die Oberfläche geschlossen wurde, und beim erneuten Öffnen wird
 * dieselbe Verbindung weiterverwendet.
 */
object Session {
    const val FILE = "session.json"

    private val _messenger = MutableStateFlow<Messenger?>(null)
    val messenger: StateFlow<Messenger?> = _messenger

    /** Ist gerade eine Activity sichtbar? Wird auf neu gestartete Messenger übertragen. */
    @Volatile private var foreground = false

    fun current(): Messenger? = _messenger.value

    @Synchronized
    fun start(ctx: Context, identity: Identity, settings: AppSettings): Messenger {
        _messenger.value?.stop()
        val m = Messenger(ctx.applicationContext, identity, settings)
        m.inForeground = foreground
        m.start()
        _messenger.value = m
        return m
    }

    /** Startet aus der gespeicherten Sitzung, falls noch kein Messenger läuft (z. B. nach Neustart des Handys). */
    @Synchronized
    fun restore(ctx: Context): Messenger? {
        _messenger.value?.let { return it }
        val s = SecureFiles.readJson(ctx, FILE) ?: return null
        return try {
            start(ctx, Identity(s.getString("priv").hexToBytes(), s.getString("user")), AppSettings.load(ctx))
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun stop() {
        _messenger.value?.stop()
        _messenger.value = null
    }

    fun setForeground(fg: Boolean) {
        foreground = fg
        _messenger.value?.inForeground = fg
    }
}
