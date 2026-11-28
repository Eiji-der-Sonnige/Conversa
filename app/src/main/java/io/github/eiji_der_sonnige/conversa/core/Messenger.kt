package io.github.eiji_der_sonnige.conversa.core

import io.github.eiji_der_sonnige.conversa.R
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.net.ConnectivityManager
import android.net.Network
import android.os.PowerManager
import io.github.eiji_der_sonnige.conversa.call.CallManager
import io.github.eiji_der_sonnige.conversa.call.CallState
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
import io.github.eiji_der_sonnige.conversa.data.mediaType
import io.github.eiji_der_sonnige.conversa.media.VoiceRecorder
import io.github.eiji_der_sonnige.conversa.data.objects
import io.github.eiji_der_sonnige.conversa.data.strings
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class Messenger(
    private val ctx: Context,
    val identity: Identity,
    /** Netzwerkeinstellungen gelten ab Start; Lesebestätigungen u. Ä. werden live übernommen. */
    @Volatile var settings: AppSettings,
) {
    private val handler = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Unerwarteter Fehler", e) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    // Keine OkHttp-Pings: Den Herzschlag übernimmt RelayPool gebündelt und im Hintergrund seltener (Akku).
    private val http: OkHttpClient = OkHttpClient.Builder()
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
    /** Bereits verpackte Nachrichten dieser Sitzung: „Erneut senden“ schickt dieselben Events (keine Duplikate). */
    private val outbox = ConcurrentHashMap<String, List<Event>>()
    /** Lesebestätigungen, die vor der zugehörigen Nachricht ankamen (z. B. bei der Erstsynchronisierung). */
    private val earlyReads = ConcurrentHashMap.newKeySet<String>()
    private var accountPublishJob: Job? = null
    private val heartbeatKick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    /** Hält die CPU kurz wach, damit eine eingetroffene Nachricht im Standby noch verarbeitet wird. */
    private val wakeLock = ctx.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "conversa:incoming")
        .apply { setReferenceCounted(false) }

    @Volatile var activeChat: String? = null
        set(value) {
            field = value
            if (value != null) {
                Notifier.cancelMessage(ctx, value)
                if (inForeground) markRead(value)
            }
        }

    @Volatile var inForeground: Boolean = true
        set(value) {
            field = value
            heartbeatKick.tryEmit(Unit) // Takt sofort anpassen bzw. Verbindung nach dem Aufwachen prüfen
            if (value) {
                Notifier.cancelCall(ctx)
                activeChat?.let { Notifier.cancelMessage(ctx, it); markRead(it) }
            } else {
                (calls.state.value as? CallState.Incoming)?.let { showIncomingCall(it) }
            }
        }

    /** Undurchsichtiger d-Tag: verrät niemandem, dass das Konto Conversa nutzt. */
    private val accountTag: String = sha256(identity.privKey, "conversa-account".toByteArray()).toHex().take(32)

    private fun inboxFilter(): JSONObject {
        // Gift Wraps tragen absichtlich einen um bis zu 2 Tage zurückdatierten Zeitstempel -> 3 Tage Puffer.
        val since = if (store.lastSync == 0L) 0L else store.lastSync - 3 * 24 * 3600
        return JSONObject()
            .put("kinds", JSONArray().put(GiftWrap.KIND_WRAP))
            .put("#p", JSONArray().put(identity.pubHex))
            .apply { if (since > 0) put("since", since) else put("limit", 2000) }
    }

    fun start() {
        media.clearTemp()
        relays.connect(settings.relays)
        scope.launch {
            relays.events.collect {
                if (it.subId == "inbox") {
                    wakeLock.acquire(5_000)
                    handleWrap(it.event)
                }
            }
        }
        scope.launch {
            relays.oks.collect { (id, ok) ->
                if (ok) pendingAcks.remove(id)?.let { (peer, msgId) -> store.updateStatus(peer, msgId, MsgStatus.SENT) }
            }
        }
        relays.subscribe("inbox", listOf(inboxFilter()))
        scope.launch {
            while (isActive) {
                delay(60_000)
                store.setLastSync(Event.now())
                // Nach einer Neuverbindung nicht wieder den gesamten Verlauf anfordern.
                relays.updateFilters("inbox", listOf(inboxFilter()))
            }
        }
        scope.launch {
            while (isActive) {
                withTimeoutOrNull(if (inForeground) HEARTBEAT_FG_MS else HEARTBEAT_BG_MS) { heartbeatKick.first() }
                relays.heartbeat()
            }
        }
        scope.launch {
            calls.state.collect { st ->
                if (st is CallState.Incoming) { if (!inForeground) showIncomingCall(st) }
                else Notifier.cancelCall(ctx)
            }
        }
        registerNetworkCallback()
    }

    fun stop() {
        store.flush()
        calls.release()
        relays.close()
        netCallback?.let {
            try { ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        Notifier.cancelCall(ctx)
        if (wakeLock.isHeld) wakeLock.release()
        scope.cancel()
    }

    /** Vom Hintergrunddienst (Wecker) aufgerufen: Verbindungen prüfen. */
    fun heartbeatNow() {
        relays.heartbeat()
    }

    private fun registerNetworkCallback() {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = relays.reconnectNow()
        }
        try {
            ctx.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "Netzwerküberwachung nicht verfügbar: ${e.message}")
        }
    }

    private fun showIncomingCall(st: CallState.Incoming) {
        Notifier.incomingCall(ctx, st.video)
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
        if (store.isBlocked(peer)) return // auch eigene, synchronisierte Kopien (Chat bleibt weg)
        val isMessage = r.kind == GiftWrap.KIND_CHAT || r.kind == GiftWrap.KIND_FILE
        val known = store.contacts.value[peer]
        if (!fromMe && known == null) {
            // Nur echte Nachrichten eines Unbekannten erzeugen eine Nachrichtenanfrage (Spam-Schutz: erst nach
            // Annahme normal sichtbar). Signale, Bestätigungen und Löschungen von Fremden werden verworfen.
            if (!isMessage) return
            // Spam-Limit: Eine Flut neuer Absender darf den Speicher nicht füllen.
            if (store.pendingRequestCount() >= MAX_PENDING_REQUESTS) return
            store.upsertContact(Contact(peer, ctx.getString(R.string.unknown_sender, Npub.short(peer)), accepted = false))
        } else if (!fromMe && known?.accepted == false && isMessage &&
            store.messageCount(peer) >= MAX_REQUEST_MESSAGES) {
            return // Pro Nachrichtenanfrage nur die ersten Nachrichten, bis sie angenommen wird
        }
        when (r.kind) {
            GiftWrap.KIND_SIGNAL -> {
                // Nur aktuelle Signale (alte Anrufangebote sollen nicht klingeln). Toleranz für
                // abweichende Uhren beider Geräte, Event.now() ist bereits netzwerkkorrigiert.
                val age = Event.now() - r.createdAt
                if (!fromMe && age in -90..90 && store.contacts.value[peer]?.accepted == true) {
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
                    r.tag("size")?.toLongOrNull() ?: 0L,
                    r.tag("duration")?.toLongOrNull()?.coerceIn(0, Media.MAX_DURATION_MS) ?: 0L)
                if (!m.isWellFormed()) return
                addIncoming(ChatMessage(r.id, peer, fromMe, mediaType(mime), "", m, r.createdAt,
                    if (fromMe) MsgStatus.SENT else MsgStatus.RECEIVED))
            }
            GiftWrap.KIND_DELETE -> {
                val ids = r.tagValues("e").filter { HEX64.matches(it) }.take(MAX_IDS_PER_EVENT)
                if (ids.isEmpty()) return
                // Eigene Löschung (von diesem oder einem anderen Gerät): gilt für alles im Chat.
                // Löschung des Partners: nur für Nachrichten, die er selbst geschrieben hat.
                store.deleteMessages(peer, ids, onlyFromPeer = !fromMe).forEach { removed ->
                    removed.media?.let { media.deleteCached(it) }
                }
            }
            GiftWrap.KIND_RECEIPT -> {
                if (fromMe) return
                r.tagValues("e").filter { HEX64.matches(it) }.take(MAX_IDS_PER_EVENT).forEach { id ->
                    val known = store.messages.value[peer]?.any { it.id == id && it.fromMe } == true
                    if (known) store.updateStatus(peer, id, MsgStatus.READ)
                    else if (earlyReads.size < 5000) earlyReads.add(id)
                }
            }
        }
    }

    private fun addIncoming(m: ChatMessage) {
        val msg = if (m.fromMe && earlyReads.remove(m.id)) m.copy(status = MsgStatus.READ) else m
        val isNew = store.upsertMessage(msg)
        if (!isNew || msg.fromMe) return
        if (inForeground && activeChat == msg.peer) {
            markRead(msg.peer)
        } else if (Event.now() - msg.createdAt < 600) {
            Notifier.newMessage(ctx, msg.peer)
        }
    }

    /**
     * Markiert den Chat als gelesen und sendet – falls eingeschaltet – eine Lesebestätigung. Nie an
     * Nachrichtenanfragen: Ein Unbekannter soll nicht erfahren, ob und wann du seine Nachricht gesehen hast.
     */
    fun markRead(peer: String) {
        val unread = store.markRead(peer)
        if (unread.isEmpty() || !settings.readReceipts) return
        if (store.contacts.value[peer]?.accepted != true) return
        val cutoff = Event.now() - 30L * 24 * 3600
        val ids = unread.filter { it.createdAt > cutoff && HEX64.matches(it.id) }.map { it.id }
        if (ids.isEmpty()) return
        scope.launch(Dispatchers.Default) {
            ids.chunked(MAX_IDS_PER_EVENT).forEach { chunk ->
                val tags = listOf(listOf("p", peer)) + chunk.map { listOf("e", it) }
                val rumor = GiftWrap.rumor(identity.pubHex, GiftWrap.KIND_RECEIPT, tags, "read")
                // Bestätigungen löschen die Relays nach 30 Tagen (NIP-40). Sie gehen nur an den Partner.
                relays.publish(GiftWrap.wrap(rumor, identity.privKey, peer, expiration = Event.now() + 30L * 24 * 3600))
            }
        }
    }

    // ------------------------------------------------------------------ Senden

    private fun publishRumor(rumor: Event, peer: String, msgId: String?) {
        val toPeer = GiftWrap.wrap(rumor, identity.privKey, peer)
        val toSelf = GiftWrap.wrap(rumor, identity.privKey, identity.pubHex) // Multi-Device-Verlauf
        if (msgId != null) outbox[msgId] = listOf(toPeer, toSelf)
        publishWraps(peer, msgId, toPeer, toSelf)
    }

    private fun publishWraps(peer: String, msgId: String?, toPeer: Event, toSelf: Event) {
        if (msgId != null) pendingAcks[toPeer.id] = peer to msgId
        relays.publish(toPeer)
        relays.publish(toSelf)
        if (msgId != null) {
            scope.launch {
                delay(20_000)
                // Zuordnung bleibt erhalten: Der Pool sendet gepufferte Events nach einer Neuverbindung
                // erneut, eine spätere Bestätigung macht aus „fehlgeschlagen“ dann noch „gesendet“.
                if (pendingAcks.containsKey(toPeer.id)) store.updateStatus(peer, msgId, MsgStatus.FAILED)
            }
        }
    }

    suspend fun sendText(peer: String, text: String) = withContext(Dispatchers.Default) {
        val clean = text.trim().take(20_000)
        if (clean.isEmpty()) return@withContext
        val rumor = GiftWrap.rumor(identity.pubHex, GiftWrap.KIND_CHAT, listOf(listOf("p", peer)), clean)
        // NIP-44 verschlüsselt höchstens 64 KiB; Rumor wird zweifach verpackt (Seal + Gift Wrap).
        if (rumor.toJson().toString().toByteArray(Charsets.UTF_8).size > MAX_RUMOR_BYTES) {
            throw IOException(ctx.getString(R.string.err_too_long))
        }
        store.upsertMessage(ChatMessage(rumor.id, peer, true, MsgType.TEXT, clean, null, rumor.createdAt, MsgStatus.SENDING))
        publishRumor(rumor, peer, rumor.id)
    }

    suspend fun resend(m: ChatMessage) {
        val wraps = outbox[m.id]
        if (wraps != null) {
            // Dieselben Events erneut senden – kommt das Original doch noch an, entsteht kein Duplikat.
            store.updateStatus(m.peer, m.id, MsgStatus.SENDING) // FAILED -> SENDING
            withContext(Dispatchers.Default) { publishWraps(m.peer, m.id, wraps[0], wraps[1]) }
            return
        }
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
        if (size > MAX_MEDIA) throw IOException(ctx.getString(R.string.err_file_too_big, (MAX_MEDIA / 1024 / 1024).toInt()))
        uploadAndSend(peer, mime, 0L) {
            cr.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException(ctx.getString(R.string.err_file_unreadable))
        }
    }

    /** Sprachnachricht: Die Aufnahme wird verschlüsselt hochgeladen und danach lokal gelöscht. */
    suspend fun sendVoice(peer: String, file: File, durationMs: Long) = withContext(Dispatchers.IO) {
        try {
            uploadAndSend(peer, VoiceRecorder.MIME, durationMs) { file.readBytes() }
        } finally {
            file.delete()
        }
    }

    private suspend fun uploadAndSend(peer: String, mime: String, durationMs: Long, read: () -> ByteArray) {
        val placeholderId = "local-" + randomBytes(8).toHex()
        store.upsertMessage(ChatMessage(placeholderId, peer, true, mediaType(mime), ctx.getString(R.string.uploading),
            null, Event.now(), MsgStatus.SENDING))
        try {
            val bytes = read()
            if (bytes.size > MAX_MEDIA) throw IOException(ctx.getString(R.string.err_file_too_big, (MAX_MEDIA / 1024 / 1024).toInt()))
            val enc = media.encrypt(bytes)
            val url = media.upload(enc.data)
            val m = Media(url, mime, enc.key.toHex(), enc.nonce.toHex(), sha256(enc.data).toHex(), bytes.size.toLong(), durationMs)
            store.removeMessage(peer, placeholderId)
            sendMediaRumor(peer, m)
        } catch (e: Exception) {
            store.removeMessage(peer, placeholderId)
            throw e
        }
    }

    private fun sendMediaRumor(peer: String, m: Media) {
        val tags = mutableListOf(
            listOf("p", peer),
            listOf("file-type", m.mime),
            listOf("encryption-algorithm", "aes-gcm"),
            listOf("decryption-key", m.keyHex),
            listOf("decryption-nonce", m.nonceHex),
            listOf("x", m.sha256),
            listOf("size", m.size.toString()),
        )
        if (m.durationMs > 0) tags.add(listOf("duration", m.durationMs.toString()))
        val rumor = GiftWrap.rumor(identity.pubHex, GiftWrap.KIND_FILE, tags, m.url)
        store.upsertMessage(ChatMessage(rumor.id, peer, true, mediaType(m.mime), "", m, rumor.createdAt, MsgStatus.SENDING))
        publishRumor(rumor, peer, rumor.id)
    }

    /**
     * Löscht Nachrichten. „Für mich“: nur auf deinen Geräten – die Löschung geht verschlüsselt nur an dich
     * selbst, damit sie auch auf anderen Geräten und nach einer Neuanmeldung gilt. „Für alle“: zusätzlich an
     * den Chatpartner, dessen App sie ebenfalls entfernt. Für alle löschbar sind nur eigene, gesendete Nachrichten.
     */
    suspend fun deleteMessages(peer: String, msgs: List<ChatMessage>, forEveryone: Boolean) = withContext(Dispatchers.Default) {
        if (msgs.isEmpty()) return@withContext
        val (local, real) = msgs.partition { !HEX64.matches(it.id) }
        local.forEach { store.removeMessage(peer, it.id) }
        if (real.isEmpty()) return@withContext
        store.deleteMessages(peer, real.map { it.id }, onlyFromPeer = false)
        real.forEach { msg ->
            msg.media?.let { media.deleteCached(it) }
            outbox.remove(msg.id)
        }
        fun publishDelete(ids: List<String>, to: String) = ids.chunked(MAX_IDS_PER_EVENT).forEach { chunk ->
            val tags = listOf(listOf("p", peer)) + chunk.map { listOf("e", it) }
            val rumor = GiftWrap.rumor(identity.pubHex, GiftWrap.KIND_DELETE, tags, "")
            relays.publish(GiftWrap.wrap(rumor, identity.privKey, to))
        }
        publishDelete(real.map { it.id }, identity.pubHex)
        if (forEveryone) {
            val mine = real.filter { it.fromMe && it.status.rank >= MsgStatus.SENT.rank }.map { it.id }
            if (mine.isNotEmpty()) publishDelete(mine, peer)
        }
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

    /** Blockieren: Chat wird entfernt, weitere Nachrichten werden ignoriert. Bleibt nur verschlüsselt bei dir. */
    fun block(pubHex: String) {
        store.block(pubHex)
        scheduleAccountPublish()
    }

    fun unblock(pubHex: String) {
        store.unblock(pubHex)
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
            throw IOException(ctx.getString(R.string.err_no_relay_answer))
        }
        val latest = res.events.filter { it.pubkey == identity.pubHex }.maxByOrNull { it.createdAt }
            ?: return null
        return try {
            val selfKey = Nip44.conversationKey(identity.privKey, identity.pubHex.hexToBytes())
            JSONObject(Nip44.decrypt(latest.content, selfKey))
        } catch (e: Exception) {
            throw IOException(ctx.getString(R.string.err_account_data))
        }
    }

    /** Übernimmt Konto-Daten vom Netzwerk. Liefert das 2FA-Geheimnis (falls aktiv). */
    fun applyAccount(acc: JSONObject?): String? {
        if (acc == null) return store.totpSecret
        acc.optJSONArray("contacts")?.objects()?.forEach { o ->
            val c = Contact.fromJson(o)
            if (store.contacts.value[c.pubHex]?.accepted != true) store.upsertContact(c)
        }
        acc.optJSONArray("blocked")?.strings()?.filter { HEX64.matches(it) }?.let { store.mergeBlocked(it) }
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
            .put("blocked", JSONArray(store.blocked.value.toList()))
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
        private const val MAX_IDS_PER_EVENT = 100
        /** Spam-Schutz: höchstens so viele offene Nachrichtenanfragen und Nachrichten je Anfrage. */
        const val MAX_PENDING_REQUESTS = 30
        const val MAX_REQUEST_MESSAGES = 20
        private val HEX64 = Regex("^[0-9a-f]{64}$")
        /** Herzschlag: im Vordergrund schnell (Anrufe), im Hintergrund selten (Akku, Funkmodul darf schlafen). */
        private const val HEARTBEAT_FG_MS = 30_000L
        private const val HEARTBEAT_BG_MS = 240_000L
    }
}
