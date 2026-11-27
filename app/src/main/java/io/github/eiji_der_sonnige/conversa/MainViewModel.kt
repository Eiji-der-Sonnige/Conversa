package io.github.eiji_der_sonnige.conversa

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eiji_der_sonnige.conversa.core.ConnectionService
import io.github.eiji_der_sonnige.conversa.core.Messenger
import io.github.eiji_der_sonnige.conversa.core.Session
import io.github.eiji_der_sonnige.conversa.crypto.Identity
import io.github.eiji_der_sonnige.conversa.crypto.KeyDerivation
import io.github.eiji_der_sonnige.conversa.crypto.Npub
import io.github.eiji_der_sonnige.conversa.crypto.Totp
import io.github.eiji_der_sonnige.conversa.crypto.hexToBytes
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import io.github.eiji_der_sonnige.conversa.data.AppSettings
import io.github.eiji_der_sonnige.conversa.data.ChatMessage
import io.github.eiji_der_sonnige.conversa.data.SecureFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface Screen {
    data object Loading : Screen
    data object Login : Screen
    /** 2FA-Abfrage beim Login bzw. App-Start (Sperre). */
    data class TotpVerify(val secret: String, val newSession: Boolean) : Screen
    data object Chats : Screen
    data class Chat(val peer: String) : Screen
    data object Settings : Screen
    data class TotpSetup(val secret: String) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen
    private val _messenger get() = Session.messenger
    val messenger: StateFlow<Messenger?> = Session.messenger
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private var failedTotp = 0
    private var totpLockedUntil = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _settings.value = AppSettings.load(ctx)
            // Läuft der Hintergrunddienst schon, wird dessen Verbindung übernommen.
            val m = Session.restore(ctx)
            if (m == null) {
                _screen.value = Screen.Login
                return@launch
            }
            m.activeChat = null
            val totp = m.store.totpSecret
            _screen.value = if (totp != null) Screen.TotpVerify(totp, newSession = false) else Screen.Chats
            if (_settings.value.background) ConnectionService.start(ctx)
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun startMessenger(identity: Identity): Messenger = Session.start(ctx, identity, _settings.value)

    fun setForeground(fg: Boolean) {
        Session.setForeground(fg)
    }

    // ---------------------------------------------------------------- Anmeldung

    fun login(username: String, password: CharArray, confirm: CharArray?, newAccount: Boolean) {
        if (_busy.value) return
        val user = KeyDerivation.normalizeUsername(username)
        if (user.length < 3) {
            _error.value = "Benutzername: mindestens 3 Zeichen"
            return
        }
        if (newAccount) {
            KeyDerivation.passwordProblem(password)?.let { _error.value = it; return }
            if (confirm == null || !password.contentEquals(confirm)) {
                _error.value = "Passwörter stimmen nicht überein"
                return
            }
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                val identity = withContext(Dispatchers.Default) { KeyDerivation.derive(user, password) }
                password.fill('\u0000')
                confirm?.fill('\u0000')
                val m = withContext(Dispatchers.IO) { startMessenger(identity) }
                val account = withContext(Dispatchers.IO) { m.fetchAccount() }
                val totp = m.applyAccount(account)
                if (newAccount && account == null) m.publishAccount()
                if (totp != null) {
                    _screen.value = Screen.TotpVerify(totp, newSession = true)
                } else {
                    finishLogin(identity)
                }
            } catch (e: Exception) {
                // Halb angemeldeten Zustand nicht weiterlaufen lassen (keine Sitzung gespeichert).
                withContext(Dispatchers.IO) { Session.stop() }
                _error.value = "Anmeldung fehlgeschlagen: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun finishLogin(identity: Identity) {
        withContext(Dispatchers.IO) {
            SecureFiles.writeJson(ctx, Session.FILE, JSONObject().put("priv", identity.privKey.toHex()).put("user", identity.username))
        }
        _screen.value = Screen.Chats
        if (_settings.value.background) ConnectionService.start(ctx)
    }

    fun verifyTotp(code: String) {
        val st = _screen.value as? Screen.TotpVerify ?: return
        val now = System.currentTimeMillis()
        if (now < totpLockedUntil) {
            _error.value = "Zu viele Fehlversuche. Bitte ${(totpLockedUntil - now) / 1000 + 1} s warten."
            return
        }
        if (checkTotp(st.secret, code)) {
            failedTotp = 0
            val m = _messenger.value ?: return
            viewModelScope.launch {
                if (st.newSession) finishLogin(m.identity) else _screen.value = Screen.Chats
            }
        } else {
            failedTotp++
            if (failedTotp >= 5) totpLockedUntil = now + 30_000L * (1L shl minOf(failedTotp - 5, 6))
            _error.value = "Falscher Code" + clockHint()
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            ConnectionService.stop(ctx)
            Session.stop()
            SecureFiles.wipeAll(ctx)
            _settings.value = AppSettings()
            _screen.value = Screen.Login
        }
    }

    /** Prüft mit Netzwerkzeit (Relays) und zusätzlich mit der Geräteuhr. */
    private fun checkTotp(secret: String, code: String): Boolean =
        Totp.verify(secret, code, io.github.eiji_der_sonnige.conversa.core.NetworkTime.now()) || Totp.verify(secret, code)

    private fun clockHint(): String {
        val nt = io.github.eiji_der_sonnige.conversa.core.NetworkTime
        val off = nt.offsetMillis / 1000
        return when {
            !nt.hasSamples -> " – noch keine Netzwerkzeit verfügbar, bitte kurz warten und erneut versuchen"
            kotlin.math.abs(off) >= 30 -> " – deine Geräteuhr weicht um $off s ab (wird ausgeglichen)"
            else -> ""
        }
    }

    // ---------------------------------------------------------------- 2FA-Einrichtung

    fun startTotpSetup() {
        _screen.value = Screen.TotpSetup(Totp.newSecret())
    }

    fun confirmTotpSetup(secret: String, code: String) {
        if (!checkTotp(secret, code)) {
            _error.value = "Code ungültig" + clockHint()
            return
        }
        _messenger.value?.setTotp(secret)
        _screen.value = Screen.Settings
    }

    fun disableTotp(code: String): Boolean {
        val m = _messenger.value ?: return false
        val secret = m.totpSecret.value ?: return true
        if (!checkTotp(secret, code)) {
            _error.value = "Falscher Code" + clockHint()
            return false
        }
        m.setTotp(null)
        return true
    }

    // ---------------------------------------------------------------- Navigation & Chat

    fun navigate(s: Screen) {
        _messenger.value?.activeChat = (s as? Screen.Chat)?.peer
        _screen.value = s
    }

    fun addContact(input: String, name: String): Boolean {
        val pub = Npub.parse(input)
        val m = _messenger.value ?: return false
        if (pub == null) {
            _error.value = "Ungültige Kontakt-ID (npub1… erwartet)"
            return false
        }
        if (pub == m.identity.pubHex) {
            _error.value = "Das ist deine eigene ID"
            return false
        }
        m.addContact(pub, name.trim())
        return true
    }

    fun sendText(peer: String, text: String) {
        val m = _messenger.value ?: return
        viewModelScope.launch {
            try { m.sendText(peer, text) } catch (e: Exception) { _error.value = e.message }
        }
    }

    fun sendMedia(peer: String, uri: Uri) {
        val m = _messenger.value ?: return
        viewModelScope.launch {
            try { m.sendMedia(peer, uri) } catch (e: Exception) { _error.value = "Senden fehlgeschlagen: ${e.message}" }
        }
    }

    fun resend(msg: ChatMessage) {
        val m = _messenger.value ?: return
        viewModelScope.launch { m.resend(msg) }
    }

    fun sendVoice(peer: String, file: java.io.File, durationMs: Long) {
        val m = _messenger.value ?: return
        viewModelScope.launch {
            try { m.sendVoice(peer, file, durationMs) } catch (e: Exception) { _error.value = "Sprachnachricht fehlgeschlagen: ${e.message}" }
        }
    }

    fun deleteMessages(peer: String, msgs: List<ChatMessage>, forEveryone: Boolean) {
        val m = _messenger.value ?: return
        viewModelScope.launch {
            try { m.deleteMessages(peer, msgs, forEveryone) } catch (e: Exception) { _error.value = "Löschen fehlgeschlagen: ${e.message}" }
        }
    }

    fun showError(msg: String) {
        _error.value = msg
    }

    /** Einstellungen ohne Netzwerkbezug: werden sofort übernommen, ohne die Verbindungen neu aufzubauen. */
    fun saveLocalSettings(readReceipts: Boolean, background: Boolean) {
        val s = _settings.value.copy(readReceipts = readReceipts, background = background)
        viewModelScope.launch(Dispatchers.IO) {
            AppSettings.save(ctx, s)
            _settings.value = s
            _messenger.value?.settings = s
            if (s.background) ConnectionService.start(ctx) else ConnectionService.stop(ctx)
        }
    }

    fun saveSettings(input: AppSettings) {
        val s = input.copy(
            relays = input.relays.ifEmpty { AppSettings.DEFAULT_RELAYS },
            blossomServers = input.blossomServers.ifEmpty { AppSettings.DEFAULT_BLOSSOM },
        )
        viewModelScope.launch(Dispatchers.IO) {
            AppSettings.save(ctx, s)
            _settings.value = s
            // Messenger mit neuen Netzwerkeinstellungen neu starten.
            _messenger.value?.let { startMessenger(it.identity) }
        }
    }

    override fun onCleared() {
        // Mit Hintergrundempfang läuft die Verbindung weiter, sonst wird sie beim Schließen der App beendet.
        if (!_settings.value.background) Session.stop()
    }
}
