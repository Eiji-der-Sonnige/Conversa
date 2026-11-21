package io.github.eiji_der_sonnige.conversa.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lokaler, verschlüsselter Cache für Kontakte und Nachrichten. Die maßgebliche Kopie liegt
 * verschlüsselt (Gift-Wrap) auf den dezentralen Relays und wird bei jedem Login neu synchronisiert.
 */
class ChatStore(private val ctx: Context, owner: String, private val scope: CoroutineScope) {
    private val fileName = "chats_${owner.take(16)}.json"

    private val _contacts = MutableStateFlow<Map<String, Contact>>(emptyMap())
    val contacts: StateFlow<Map<String, Contact>> = _contacts
    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages

    @Volatile var lastSync: Long = 0L
        private set
    @Volatile var totpSecret: String? = null
        private set

    private var saveJob: Job? = null

    init {
        load()
    }

    private fun load() {
        val o = SecureFiles.readJson(ctx, fileName) ?: return
        lastSync = o.optLong("lastSync")
        totpSecret = o.optString("totp").takeIf { it.isNotEmpty() }
        _contacts.value = o.optJSONArray("contacts")?.objects()
            ?.map { Contact.fromJson(it) }?.associateBy { it.pubHex } ?: emptyMap()
        _messages.value = o.optJSONArray("messages")?.objects()
            ?.map { ChatMessage.fromJson(it) }
            ?.groupBy { it.peer }
            ?.mapValues { (_, v) -> v.sortedBy { it.createdAt } } ?: emptyMap()
    }

    private fun save() {
        val o = JSONObject()
            .put("lastSync", lastSync)
            .put("totp", totpSecret ?: "")
            .put("contacts", JSONArray().apply { _contacts.value.values.forEach { put(it.toJson()) } })
            .put("messages", JSONArray().apply {
                _messages.value.values.forEach { list -> list.forEach { put(it.toJson()) } }
            })
        SecureFiles.writeJson(ctx, fileName, o)
    }

    private fun scheduleSave() {
        synchronized(this) {
            saveJob?.cancel()
            saveJob = scope.launch {
                delay(400)
                save()
            }
        }
    }

    fun setLastSync(ts: Long) {
        lastSync = ts
        scheduleSave()
    }

    fun setTotp(secret: String?) {
        totpSecret = secret
        scheduleSave()
    }

    fun upsertContact(c: Contact) {
        _contacts.update { it + (c.pubHex to c) }
        scheduleSave()
    }

    fun removeContact(pub: String) {
        _contacts.update { it - pub }
        _messages.update { it - pub }
        scheduleSave()
    }

    /** Fügt hinzu oder ersetzt; liefert true, wenn die Nachricht neu war. */
    fun upsertMessage(m: ChatMessage): Boolean {
        var isNew = false
        _messages.update { all ->
            val list = all[m.peer].orEmpty()
            val idx = list.indexOfFirst { it.id == m.id }
            val newList = if (idx >= 0) {
                list.toMutableList().also { it[idx] = m }
            } else {
                isNew = true
                (list + m).sortedBy { it.createdAt }
            }
            all + (m.peer to newList)
        }
        scheduleSave()
        return isNew
    }

    fun removeMessage(peer: String, id: String) {
        _messages.update { all -> all + (peer to all[peer].orEmpty().filterNot { it.id == id }) }
        scheduleSave()
    }

    fun updateStatus(peer: String, id: String, status: MsgStatus) {
        _messages.update { all ->
            val list = all[peer] ?: return@update all
            all + (peer to list.map { if (it.id == id && it.status != MsgStatus.SENT) it.copy(status = status) else it })
        }
        scheduleSave()
    }

    fun flush() {
        synchronized(this) { saveJob?.cancel() }
        save()
    }
}
