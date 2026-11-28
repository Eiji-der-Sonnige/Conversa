package io.github.eiji_der_sonnige.conversa.nostr

import android.os.SystemClock
import android.util.Log
import io.github.eiji_der_sonnige.conversa.crypto.randomBytes
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Verbindet sich gleichzeitig mit mehreren unabhängigen Nostr-Relays (dezentral, kein zentraler Server).
 * Fällt ein Relay aus, laufen die anderen weiter. Jede eingehende Signatur wird geprüft.
 */
class RelayPool(private val scope: CoroutineScope, private val client: OkHttpClient) {
    data class Incoming(val relay: String, val subId: String, val event: Event)

    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val connected = ConcurrentHashMap<String, Boolean>()
    private val retries = ConcurrentHashMap<String, Int>()
    private val subs = ConcurrentHashMap<String, List<JSONObject>>()
    private val urls = CopyOnWriteArrayList<String>()
    private val pending = CopyOnWriteArrayList<String>()
    private val seen = object : LinkedHashMap<String, Boolean>(2048, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 10_000
    }
    @Volatile private var closed = false

    /**
     * App sichtbar? Dann wird ein ausgefallenes Relay schnell erneut versucht (1, 2, 4, 8, dann alle 10 s),
     * im Hintergrund seltener (bis 60 s), damit das Funkmodul schlafen kann.
     */
    @Volatile var foreground = true
    /** Zeitpunkt der letzten Daten bzw. des letzten Herzschlags je Relay (elapsedRealtime, ms). */
    private val lastActivity = ConcurrentHashMap<String, Long>()
    private val lastPing = ConcurrentHashMap<String, Long>()

    private val _events = MutableSharedFlow<Incoming>(extraBufferCapacity = 1024)
    val events: SharedFlow<Incoming> = _events
    private val _eose = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 128)
    private val _oks = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 256)
    val oks: SharedFlow<Pair<String, Boolean>> = _oks
    private val _status = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val status: StateFlow<Map<String, Boolean>> = _status

    fun connect(relayUrls: List<String>) {
        urls.clear()
        urls.addAll(relayUrls.map { it.trim() }.filter { it.startsWith("wss://") }.distinct())
        urls.forEach { connected[it] = false; open(it) }
        updateStatus()
    }

    private fun open(url: String) {
        if (closed) return
        try {
            sockets[url] = client.newWebSocket(Request.Builder().url(url).build(), Listener(url))
        } catch (e: Exception) {
            Log.w(TAG, "Relay $url ungültig: ${e.message}")
        }
    }

    private fun updateStatus() {
        _status.value = urls.associateWith { connected[it] == true }
    }

    private fun reqMessage(id: String, filters: List<JSONObject>): String =
        JSONArray().put("REQ").put(id).apply { filters.forEach { put(it) } }.toString()

    private inner class Listener(private val url: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            io.github.eiji_der_sonnige.conversa.core.NetworkTime.addSample(
                response.headers.getDate("Date"), response.sentRequestAtMillis, response.receivedResponseAtMillis
            )
            connected[url] = true
            retries[url] = 0
            lastActivity[url] = SystemClock.elapsedRealtime()
            lastPing.remove(url)
            updateStatus()
            subs.forEach { (id, f) -> webSocket.send(reqMessage(id, f)) }
            pending.forEach { webSocket.send(it) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            lastActivity[url] = SystemClock.elapsedRealtime()
            handle(url, text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = down(url, webSocket)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = down(url, webSocket)
    }

    private fun down(url: String, ws: WebSocket) {
        if (sockets[url] !== ws) return
        sockets.remove(url)
        connected[url] = false
        updateStatus()
        if (closed || url !in urls) return
        val n = (retries[url] ?: 0) + 1
        retries[url] = n
        scope.launch {
            delay(retryDelayMs(n, foreground))
            if (!closed && !sockets.containsKey(url) && url in urls) open(url)
        }
    }

    private fun handle(url: String, text: String) {
        if (text.length > 600_000) return
        val arr = try {
            JSONArray(text)
        } catch (e: Exception) {
            return
        }
        when (arr.optString(0)) {
            "EVENT" -> {
                val subId = arr.optString(1)
                val ev = try {
                    Event.fromJson(arr.getJSONObject(2))
                } catch (e: Exception) {
                    return
                }
                val key = subId + ev.id
                val known = synchronized(seen) { seen.containsKey(key) }
                if (known) return
                if (!ev.verify()) {
                    Log.w(TAG, "Ungültige Signatur von $url verworfen")
                    return
                }
                // Atomar prüfen und eintragen: Jedes Relay hat einen eigenen Thread. Kommt dasselbe
                // Event gleichzeitig von mehreren Relays, darf es trotzdem nur einmal weitergegeben werden
                // (sonst wurde z. B. ein Anrufangebot doppelt verarbeitet und mit "besetzt" beantwortet).
                val first = synchronized(seen) { seen.put(key, true) == null }
                if (!first) return
                _events.tryEmit(Incoming(url, subId, ev))
            }
            "EOSE" -> _eose.tryEmit(url to arr.optString(1))
            "OK" -> {
                val id = arr.optString(1)
                val ok = arr.optBoolean(2)
                if (ok) pending.removeAll { it.contains(id) }
                else Log.w(TAG, "$url lehnte Event ab: ${arr.optString(3)}")
                _oks.tryEmit(id to ok)
            }
            "NOTICE", "CLOSED" -> Log.i(TAG, "$url: $text")
        }
    }

    /**
     * Herzschlag für alle Relays gleichzeitig (ein Funk-Aufwachen statt mehrerer versetzter). Ersetzt die
     * WebSocket-Pings von OkHttp, deren Takt sich nicht ändern lässt. Ein Relay, das seit dem letzten
     * Herzschlag gar nichts gesendet hat, gilt als tot (z. B. nach Netzwechsel) und wird neu verbunden.
     * Als Herzschlag dient eine Abfrage, die nie Treffer hat; jedes Relay antwortet darauf mit EOSE.
     */
    fun heartbeat() {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        sockets.forEach { (url, ws) ->
            if (connected[url] != true) return@forEach
            val pinged = lastPing[url]
            val active = lastActivity[url] ?: 0L
            if (pinged != null && active < pinged && now - pinged > PING_TIMEOUT_MS) {
                Log.i(TAG, "$url antwortet nicht mehr – neu verbinden")
                ws.cancel() // löst onFailure -> down() -> Neuverbindung aus
                return@forEach
            }
            if (pinged == null || now - pinged > PING_TIMEOUT_MS) {
                lastPing[url] = now
                ws.send(HEARTBEAT)
            }
        }
    }

    /** Nach einem Netzwechsel: getrennte Relays sofort statt erst nach Ablauf der Wartezeit verbinden. */
    fun reconnectNow() {
        if (closed) return
        urls.forEach { url ->
            if (!sockets.containsKey(url)) {
                retries[url] = 0
                open(url)
            }
        }
        heartbeat()
    }

    /** Ändert den Filter eines Abos für künftige (Neu-)Verbindungen, ohne ihn jetzt erneut zu senden. */
    fun updateFilters(id: String, filters: List<JSONObject>) {
        if (subs.containsKey(id)) subs[id] = filters
    }

    fun subscribe(id: String, filters: List<JSONObject>) {
        subs[id] = filters
        val msg = reqMessage(id, filters)
        sockets.forEach { (u, ws) -> if (connected[u] == true) ws.send(msg) }
    }

    fun unsubscribe(id: String) {
        subs.remove(id)
        val msg = JSONArray().put("CLOSE").put(id).toString()
        sockets.forEach { (u, ws) -> if (connected[u] == true) ws.send(msg) }
    }

    /** Sendet an alle verbundenen Relays; wenn keines verbunden ist, wird gepuffert. */
    fun publish(event: Event) {
        val msg = JSONArray().put("EVENT").put(event.toJson()).toString()
        var sent = 0
        sockets.forEach { (u, ws) -> if (connected[u] == true && ws.send(msg)) sent++ }
        if (pending.size < 200) pending.add(msg)
        if (sent == 0) Log.i(TAG, "Kein Relay verbunden – Event gepuffert")
    }

    /** Einmalige Abfrage, wartet auf EOSE der verbundenen Relays oder Timeout. */
    suspend fun query(filters: List<JSONObject>, timeoutMs: Long = 7000): List<Event> =
        queryWithEose(filters, timeoutMs).events

    /** Ergebnis einer Abfrage samt Anzahl der Relays, die sie vollständig beantwortet haben (EOSE). */
    data class QueryResult(val events: List<Event>, val eoseCount: Int)

    suspend fun queryWithEose(filters: List<JSONObject>, timeoutMs: Long = 7000): QueryResult = coroutineScope {
        val id = "q" + randomBytes(6).toHex()
        val result = ConcurrentHashMap<String, Event>()
        val eoseFrom = ConcurrentHashMap.newKeySet<String>()
        val j1 = launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { if (it.subId == id) result[it.event.id] = it.event }
        }
        val j2 = launch(start = CoroutineStart.UNDISPATCHED) {
            _eose.collect { (r, s) -> if (s == id) eoseFrom.add(r) }
        }
        subscribe(id, filters)
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val up = connected.filterValues { it }.keys
                if (up.isNotEmpty() && eoseFrom.size >= minOf(2, urls.size) && eoseFrom.containsAll(up)) break
                delay(150)
            }
        }
        unsubscribe(id)
        j1.cancel()
        j2.cancel()
        QueryResult(result.values.toList(), eoseFrom.size)
    }

    fun close() {
        closed = true
        sockets.values.forEach { it.close(1000, null) }
        sockets.clear()
        subs.clear()
        pending.clear()
    }

    companion object {
        private const val TAG = "RelayPool"

        /** Wartezeit vor dem [attempt]-ten erneuten Versuch. */
        fun retryDelayMs(attempt: Int, foreground: Boolean): Long {
            val exp = 1_000L * (1L shl (attempt - 1).coerceIn(0, 6))
            return minOf(exp, if (foreground) 10_000L else 60_000L)
        }
        private const val PING_TIMEOUT_MS = 20_000L
        private val HEARTBEAT = JSONArray().put("REQ").put("hb")
            .put(JSONObject().put("ids", JSONArray().put("0".repeat(64))).put("limit", 1)).toString()
    }
}
