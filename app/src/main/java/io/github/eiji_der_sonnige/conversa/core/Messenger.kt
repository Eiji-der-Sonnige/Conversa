package io.github.eiji_der_sonnige.conversa.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import io.github.eiji_der_sonnige.conversa.call.CallManager
import io.github.eiji_der_sonnige.conversa.crypto.Identity
import io.github.eiji_der_sonnige.conversa.crypto.Nip44
import io.github.eiji_der_sonnige.conversa.crypto.Npub
import io.github.eiji_der_sonnige.conversa.crypto.hexToBytes
import io.github.eiji_der_sonnige.conversa.crypto.randomBytes
import io.github.eiji_der_sonnige.conversa.crypto.sha256
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import io.github.eiji_der_sonnige.conversa.data.AppSettings
import io.github.eiji_der_sonnige.conversa.data.ChatMessage
import io.github.eiji_der_sonnige.conversa.data.ChatStore
import io.github.eiji_der_sonnige.conversa.data.Contact
import io.github.eiji_der_sonnige.conversa.data.Media
import io.github.eiji_der_sonnige.conversa.data.MsgStatus
import io.github.eiji_der_sonnige.conversa.data.MsgType
import io.github.eiji_der_sonnige.conversa.data.objects
import io.github.eiji_der_sonnige.conversa.media.MediaService
import io.github.eiji_der_sonnige.conversa.nostr.Event
import io.github.eiji_der_sonnige.conversa.nostr.GiftWrap
import io.github.eiji_der_sonnige.conversa.nostr.RelayPool
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class Messenger(
    private val ctx: Context,
    val identity: Identity,
    val settings: AppSettings,
) {
    private val handler = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Unerwarteter Fehler", e) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .apply {
            // Optional: gesamter Datenverkehr über Tor (Orbot), versteckt die IP-Adresse vor Relays.
            if (settings.useTor) proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050)))
        }
        .build()

    val relays = RelayPool(scope, http)
    val store = ChatStore(ctx, identity.pubHex, scope)
    val media = MediaService(ctx, http) { settings.blossomServers }
    val calls = CallManager(ctx, scope, ::iceConfig) { peer, payload -> sendSignal(peer, payload) }

    private val _totpSecret = MutableStateFlow(store.totpSecret)
    val totpSecret: StateFlow<String?> = _totpSecret

    /** Zuordnung Gift-Wrap-ID -> (Kontakt, Nachrichten-ID) für Zustellbestätigungen der Relays. */
    private val pendingAcks = ConcurrentHashMap<String, Pair<String, String>>()
    private var accountPublishJob: Job? = null

    @Volatile var activeChat: String? = null
    @Volatile var inForeground: Boolean = true

    /** Undurchsichtiger d-Tag: verrät niemandem, dass das Konto Conversa nutzt. */
    private val accountTag: String = sha256(identity.privKey, "conversa-account".toByteArray()).toHex().take(32)

    fun start() {
        media.clearTemp()
        relays.connect(settings.relays)
        val since = if (store.lastSync == 0L) 0L else store.lastSync - 3 * 24 * 3600
        val filter = JSONObject()
            .put("kinds", JSONArray().put(GiftWrap.KIND_WRAP))
            .put("#p", JSONArray().put(identity.pubHex))
            .apply { if (since > 0) put("since", since) else put("limit", 2000) }
        scope.launch {
            relays.events.collect { if (it.subId == "inbox") handleWrap(it.event) }
        }
        scope.launch {
            relays.oks.collect { (id, ok) ->
                if (ok) pendingAcks.remove(id)?.let { (peer, msgId) -> store.updateStatus(peer, msgId, MsgStatus.SENT) }
            }
        }
        relays.subscribe("inbox", listOf(filter))
        scope.launch {
            while (isActive) {
                delay(60_000)
                store.setLastSync(Event.now())
            }
        }
    }

    fun stop() {
        store.flush()
        calls.release()
        relays.close()
        scope.cancel()
    }

    private fun iceConfig(): CallManager.IceConfig {
        val servers = mutableListOf(
            PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302", "stun:stun.cloudflare.com:3478"))
                .createIceServer()
        )
        if (settings.turnUrl.isNotBlank()) {
            servers.add(
                PeerConnection.IceServer.builder(settings.turnUrl.trim())
                    .setUsername(settings.turnUser).setPassword(settings.turnPass).createIceServer()
            )
        }
        // Mit Tor wäre ein direkter Anruf ein IP-Leck: Ist ein TURN-Server eingetragen, wird er erzwungen.
        val forceRelay = (settings.relayOnlyCalls || settings.useTor) && settings.turnUrl.isNotBlank()
        return CallManager.IceConfig(servers, forceRelay)
    }

    // ------------------------------------------------------------------ Empfang

    private fun handleWrap(wrap: Event) {
        if (wrap.kind != GiftWrap.KIND_WRAP) return
        val u = GiftWrap.unwrap(wrap, identity.privKey) ?: return
        val r = u.rumor
        val fromMe = u.sender == identity.pubHex
        val peer = if (fromMe) r.tagValues("p").firstOrNull { it != identity.pubHex } ?: return else u.sender
        if (!fromMe && peer !in store.contacts.value) {
            // Unbekannter Absender = Nachrichtenanfrage (Spam-Schutz: erst nach Annahme normal sichtbar).
            store.upsertContact(Contact(peer, "Unbekannt ${Npub.short(peer)}", accepted = false))
        }
        when (r.kind) {
            GiftWrap.KIND_SIGNAL -> {
                if (!fromMe && Event.now() - r.createdAt < 60 && store.contacts.value[peer]?.accepted == true) {
                    try { calls.onSignal(peer, JSONObject(r.content)) } catch (_: Exception) {}
                }
            }
            GiftWrap.KIND_CHAT -> addIncoming(
                ChatMessage(r.id, peer, fromMe, MsgType.TEXT, r.content, null, r.createdAt,
                    if (fromMe) MsgStatus.SENT else MsgStatus.RECEIVED)
            )
            GiftWrap.KIND_FILE -> {
                val mime = r.tag("file-type") ?: "application/octet-stream"
                val key = r.tag("decryption-key") ?: return
                val nonce = r.tag("decryption-nonce") ?: return
                val sha = r.tag("x") ?: return
                val m = Media(r.content, mime, key.lowercase(), nonce.lowercase(), sha.lowercase(),
                    r.tag("size")?.toLongOrNull() ?: 0L)
                if (!m.isWellFormed()) return
                val type = when {
                    mime.startsWith("image/") -> MsgType.IMAGE
                    mime.startsWith("video/") -> MsgType.VIDEO
                    else -> MsgType.FILE
                }
                addIncoming(ChatMessage(r.id, peer, fromMe, type, "", m, r.createdAt,
                    if (fromMe) MsgStatus.SENT else MsgStatus.RECEIVED))
            }
        }
    }

    private fun addIncoming(m: ChatMessage) {
        val isNew = store.upsertMessage(m)
        val recent = Event.now() - m.createdAt < 600
        if (isNew && !m.fromMe && recent && (!inForeground || activeChat != m.peer)) {
            Notifier.newMessage(ctx, m.peer)
        }
    }

    // ------------------------------------------------------------------ Senden

    private fun publishRumor(rumor: Event, peer: String, msgId: String?) {
        val toPeer = GiftWrap.wrap(rumor, identity.privKey, peer)
        val toSelf = GiftWrap.wrap(rumor, identity.privKey, identity.pubHex) // Multi-Device-Verlauf
        if (msgId != null) pendingAcks[toPeer.id] = peer to msgId
        relays.publish(toPeer)
        relays.publish(toSelf)
        if (msgId != null) {
            scope.launch {
                delay(20_000)
                if (pendingAcks.remove(toPeer.id) != null) store.updateStatus(peer, msgId, MsgStatus.FAILED)
            }
        }
    }

    suspend fun sendText(peer: String, text: String) = withContext(Dispatchers.Default) {
        val clean = text.trim().take(20_000)
        if (clean.isEmpty()) return@withContext
        val rumor = GiftWrap.rumor(identity.pubHex, GiftWrap.KIND_CHAT, listOf(listOf("p", peer)), clean)
        // NIP-44 verschlüsselt höchstens 64 KiB; Rumor wird zweifach verpackt (Seal + Gift Wrap).
        if (rumor.toJson().toString().toByteArray(Charsets.UTF_8).size > MAX_RUMOR_BYTES) {
            throw IOException("Nachricht zu lang – bitte aufteilen")
        }
        store.upsertMessage(ChatMessage(rumor.id, peer, true, MsgType.TEXT, clean, null, rumor.createdAt, MsgStatus.SENDING))
        publishRumor(rumor, peer, rumor.id)
    }

    suspend fun resend(m: ChatMessage) {
        store.removeMessage(m.peer, m.id)
        when (m.type) {
            MsgType.TEXT -> sendText(m.peer, m.text)
            else -> m.media?.let { sendMediaRumor(m.peer, it) }
        }
    }

    suspend fun sendMedia(peer: String, uri: Uri) = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val size = cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        } ?: -1L
        if (size > MAX_MEDIA) throw IOException("Datei zu groß (max. ${MAX_MEDIA / 1024 / 1024} MB)")
        val placeholderId = "local-" + randomBytes(8).toHex()
        val type = if (mime.startsWith("video/")) MsgType.VIDEO else if (mime.startsWith("image/")) MsgType.IMAGE else MsgType.FILE
        store.upsertMessage(ChatMessage(placeholderId, peer, true, type, "Wird verschlüsselt & hochgeladen…", null, Event.now(), MsgStatus.SENDING))
        try {
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Datei nicht lesbar")
            if (bytes.size > MAX_MEDIA) throw IOException("Datei zu groß")
            val enc = media.encrypt(bytes)
            val url = media.upload(enc.data)
            val m = Media(url, mime, enc.key.toHex(), enc.nonce.toHex(), sha256(enc.data).toHex(), bytes.size.toLong())
            store.removeMessage(peer, placeholderId)
            sendMediaRumor(peer, m)
        } catch (e: Exception) {
            store.removeMessage(peer, placeholderId)
            throw e
        }
    }

    private fun sendMediaRumor(peer: String, m: Media) {
        val tags = listOf(
            listOf("p", peer),
            listOf("file-type", m.mime),
            listOf("encryption-algorithm", "aes-gcm"),
            listOf("decryption-key", m.keyHex),
            listOf("decryption-nonce", m.nonceHex),
            listOf("x", m.sha256),
            listOf("size", m.size.toString()),
        )
        val rumor = GiftWrap.rumor(identity.pubHex, GiftWrap.KIND_FILE, tags, m.url)
        val type = when {
            m.mime.startsWith("image/") -> MsgType.IMAGE
            m.mime.startsWith("video/") -> MsgType.VIDEO
            else -> MsgType.FILE
        }
        store.upsertMessage(ChatMessage(rumor.id, peer, true, type, "", m, rumor.createdAt, MsgStatus.SENDING))
        publishRumor(rumor, peer, rumor.id)
    }

    private suspend fun sendSignal(peer: String, payload: JSONObject) = withContext(Dispatchers.Default) {
        val rumor = GiftWrap.rumor(identity.pubHex, GiftWrap.KIND_SIGNAL, listOf(listOf("p", peer)), payload.toString())
        relays.publish(GiftWrap.wrap(rumor, identity.privKey, peer, expiration = Event.now() + 120))
    }

    // ------------------------------------------------------------------ Kontakte & Konto (dezentral)

    fun addContact(pubHex: String, name: String) {
        store.upsertContact(Contact(pubHex, name.ifBlank { Npub.short(pubHex) }, true))
        scheduleAccountPublish()
    }

    fun acceptContact(pubHex: String, name: String) = addContact(pubHex, name)

    fun removeContact(pubHex: String) {
        store.removeContact(pubHex)
        scheduleAccountPublish()
    }

    /**
     * Kontaktliste und 2FA-Geheimnis liegen verschlüsselt (NIP-44, nur für dich lesbar) als
     * ersetzbares Event (Kind 30078) auf den Relays – so funktioniert das Konto auf jedem Gerät.
     */
    /**
     * Liefert null nur, wenn mindestens ein Relay die Abfrage vollständig beantwortet hat und es
     * wirklich kein Konto-Event gibt. Ohne Antwort wird abgebrochen: Sonst würde ein Login im
     * Flugmodus die 2FA überspringen und „Konto erstellen“ das bestehende Konto (Kontakte, 2FA)
     * mit einem leeren Konto überschreiben.
     */
    suspend fun fetchAccount(): JSONObject? {
        val filter = JSONObject()
            .put("kinds", JSONArray().put(30078))
            .put("authors", JSONArray().put(identity.pubHex))
            .put("#d", JSONArray().put(accountTag))
        val res = relays.queryWithEose(listOf(filter), timeoutMs = 12_000)
        if (res.eoseCount == 0) {
            throw IOException("Keine Antwort von den Relays. Bitte Internetverbindung (bzw. Orbot) prüfen und erneut versuchen.")
        }
        val latest = res.events.filter { it.pubkey == identity.pubHex }.maxByOrNull { it.createdAt }
            ?: return null
        return try {
            val selfKey = Nip44.conversationKey(identity.privKey, identity.pubHex.hexToBytes())
            JSONObject(Nip44.decrypt(latest.content, selfKey))
        } catch (e: Exception) {
            throw IOException("Konto-Daten konnten nicht gelesen werden")
        }
    }

    /** Übernimmt Konto-Daten vom Netzwerk. Liefert das 2FA-Geheimnis (falls aktiv). */
    fun applyAccount(acc: JSONObject?): String? {
        if (acc == null) return store.totpSecret
        acc.optJSONArray("contacts")?.objects()?.forEach { o ->
            val c = Contact.fromJson(o)
            if (store.contacts.value[c.pubHex]?.accepted != true) store.upsertContact(c)
        }
        val totp = acc.optString("totp").takeIf { it.isNotEmpty() }
        store.setTotp(totp)
        _totpSecret.value = totp
        return totp
    }

    private fun scheduleAccountPublish() {
        synchronized(this) {
            accountPublishJob?.cancel()
            accountPublishJob = scope.launch {
                delay(1500)
                publishAccount()
            }
        }
    }

    fun publishAccount() {
        val acc = JSONObject()
            .put("v", 1)
            .put("totp", store.totpSecret ?: "")
            .put("contacts", JSONArray().apply {
                store.contacts.value.values.filter { it.accepted }.forEach { put(it.toJson()) }
            })
        val selfKey = Nip44.conversationKey(identity.privKey, identity.pubHex.hexToBytes())
        val ev = Event.sign(identity.privKey, 30078, listOf(listOf("d", accountTag)), Nip44.encrypt(acc.toString(), selfKey))
        relays.publish(ev)
    }

    fun setTotp(secret: String?) {
        store.setTotp(secret)
        _totpSecret.value = secret
        publishAccount()
    }

    companion object {
        private const val TAG = "Messenger"
        const val MAX_MEDIA = 50L * 1024 * 1024
        private const val MAX_RUMOR_BYTES = 40_000
    }
}
