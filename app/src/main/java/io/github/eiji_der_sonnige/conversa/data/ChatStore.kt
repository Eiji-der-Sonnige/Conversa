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

    /**
     * IDs gelöschter Nachrichten. Die verschlüsselten Kopien bleiben auf den Relays liegen und kämen bei
     * der nächsten Synchronisierung sonst wieder zurück. Begrenzt, älteste Einträge fallen zuerst heraus.
     */
    private val deleted = LinkedHashSet<String>()
    /** Vom Chatpartner „für alle“ gelöscht: gilt nur für seine eigenen Nachrichten, nie für unsere. */
    private val deletedByPeer = LinkedHashSet<String>()

    private var saveJob: Job? = null

    init {
        load()
    }

    private fun load() {
        val o = SecureFiles.readJson(ctx, fileName) ?: return
        lastSync = o.optLong("lastSync")
        totpSecret = o.optString("totp").takeIf { it.isNotEmpty() }
        synchronized(deleted) {
            o.optJSONArray("deleted")?.strings()?.let { deleted.addAll(it) }
            o.optJSONArray("deletedByPeer")?.strings()?.let { deletedByPeer.addAll(it) }
        }
        // Ab Version 2 gibt es „ungelesen“. Ältere Verläufe gelten als gelesen, sonst wäre alles plötzlich neu.
        val legacy = o.optInt("v", 1) < 2
        _contacts.value = o.optJSONArray("contacts")?.objects()
            ?.map { Contact.fromJson(it) }?.associateBy { it.pubHex } ?: emptyMap()
        _messages.value = o.optJSONArray("messages")?.objects()
            ?.map { ChatMessage.fromJson(it) }
            ?.map { if (legacy && it.status == MsgStatus.RECEIVED) it.copy(status = MsgStatus.READ) else it }
            ?.groupBy { it.peer }
            ?.mapValues { (_, v) -> v.sortedBy { it.createdAt } } ?: emptyMap()
    }

    private fun save() {
        val o = JSONObject()
            .put("v", 2)
            .put("lastSync", lastSync)
            .put("deleted", JSONArray().apply { synchronized(deleted) { deleted.forEach { put(it) } } })
            .put("deletedByPeer", JSONArray().apply { synchronized(deleted) { deletedByPeer.forEach { put(it) } } })
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

    private fun isDeleted(m: ChatMessage): Boolean =
        synchronized(deleted) { m.id in deleted || (!m.fromMe && "${m.peer}:${m.id}" in deletedByPeer) }

    /**
     * Fügt hinzu oder aktualisiert; liefert true, wenn die Nachricht neu war. Gelöschte Nachrichten werden
     * ignoriert. Bei einer bereits bekannten Nachricht bleibt der höhere Status erhalten.
     */
    fun upsertMessage(m: ChatMessage): Boolean {
        if (isDeleted(m)) return false
        var isNew = false
        _messages.update { all ->
            val list = all[m.peer].orEmpty()
            val idx = list.indexOfFirst { it.id == m.id }
            val newList = if (idx >= 0) {
                val old = list[idx]
                list.toMutableList().also { it[idx] = m.copy(status = old.status.merge(m.status)) }
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

    /**
     * Löscht Nachrichten dauerhaft (auch künftige Kopien vom Netzwerk). [onlyFromPeer]: nur Nachrichten,
     * die der Chatpartner selbst geschrieben hat – er darf „für alle“ nur seine eigenen löschen.
     * Liefert die tatsächlich entfernten Nachrichten (z. B. um zwischengespeicherte Medien zu löschen).
     */
    fun deleteMessages(peer: String, ids: Collection<String>, onlyFromPeer: Boolean): List<ChatMessage> {
        var removed: List<ChatMessage> = emptyList()
        _messages.update { all ->
            val list = all[peer].orEmpty()
            val hit = list.filter { it.id in ids && (!onlyFromPeer || !it.fromMe) }
            removed = hit
            val hitIds = hit.map { it.id }.toSet()
            all + (peer to list.filterNot { it.id in hitIds })
        }
        // Auch noch nicht vorhandene IDs merken: Bei einer Neuanmeldung kann die Löschung vor der Nachricht
        // selbst ankommen.
        synchronized(deleted) {
            // Löschungen eines Partners gelten nur in seinem eigenen Chat.
            val target = if (onlyFromPeer) deletedByPeer else deleted
            target.addAll(if (onlyFromPeer) ids.map { "$peer:$it" } else ids)
            while (target.size > MAX_DELETED) target.remove(target.first())
        }
        scheduleSave()
        return removed
    }

    /** Ändert den Status; nur „Hochstufen“ (FAILED nur aus SENDING heraus). */
    fun updateStatus(peer: String, id: String, status: MsgStatus) {
        _messages.update { all ->
            val list = all[peer] ?: return@update all
            all + (peer to list.map {
                when {
                    it.id != id -> it
                    status == MsgStatus.FAILED -> if (it.status == MsgStatus.SENDING) it.copy(status = status) else it
                    else -> it.copy(status = it.status.merge(status))
                }
            })
        }
        scheduleSave()
    }

    /** Markiert alle ungelesenen empfangenen Nachrichten eines Chats als gelesen und liefert sie. */
    fun markRead(peer: String): List<ChatMessage> {
        var unread: List<ChatMessage> = emptyList()
        _messages.update { all ->
            val list = all[peer] ?: return@update all
            unread = list.filter { !it.fromMe && it.status == MsgStatus.RECEIVED }
            if (unread.isEmpty()) all
            else all + (peer to list.map { if (!it.fromMe && it.status == MsgStatus.RECEIVED) it.copy(status = MsgStatus.READ) else it })
        }
        if (unread.isNotEmpty()) scheduleSave()
        return unread
    }

    fun flush() {
        synchronized(this) { saveJob?.cancel() }
        save()
    }

    companion object {
        private const val MAX_DELETED = 5000
    }
}
