package io.github.eiji_der_sonnige.conversa.core

import io.github.eiji_der_sonnige.conversa.crypto.randomBytes
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import io.github.eiji_der_sonnige.conversa.nostr.Event
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ein signierter Gruppenzustand. Jede Änderung (Einladen, Entfernen, Admin ernennen, Umbenennen, Löschen,
 * Verlassen) ist ein neues, vom Handelnden mit seinem Schlüssel signiertes Nostr-Event (Kind 25071) mit
 * fortlaufender Nummer [seq] und Verweis auf den Vorgänger [prev]. Jedes Gerät prüft jede Änderung selbst
 * gegen [GroupRules.validateTransition] – die Rechte hängen also nicht an der Oberfläche, sondern an Signaturen.
 *
 * Die Gruppen-ID ist die Event-ID des Gründungszustands (seq 0), den nur der Ersteller signieren kann. Damit ist
 * der Ersteller fest an die Gruppe gebunden und kann von niemandem entfernt oder ersetzt werden.
 */
data class GroupState(
    val gid: String,
    val event: Event,
    val seq: Int,
    val prev: String,
    val name: String,
    val creator: String,
    val admins: Set<String>,
    val members: Set<String>,
    val deleted: Boolean,
) {
    val id: String get() = event.id
    val signer: String get() = event.pubkey
    val createdAt: Long get() = event.createdAt
    fun isAdmin(pub: String) = pub in admins
}

object GroupRules {
    const val KIND_STATE = 25071
    const val MAX_MEMBERS = 32
    const val MAX_ADMINS = 5
    const val MAX_NAME = 64
    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /** Gründung: Nur der Ersteller signiert; er ist Mitglied und (einziger) Admin. */
    fun genesis(priv: ByteArray, creator: String, name: String, members: Collection<String>): Event =
        sign(priv, gid = null, seq = 0, prev = "", name = name, creator = creator,
            admins = setOf(creator), members = members.toSet() + creator, deleted = false)

    /** Nächster Zustand auf Basis von [p]. */
    fun next(
        priv: ByteArray, p: GroupState,
        name: String = p.name, admins: Set<String> = p.admins, members: Set<String> = p.members, deleted: Boolean = false,
    ): Event = sign(priv, p.gid, p.seq + 1, p.id, name, p.creator, admins, members, deleted)

    private fun sign(
        priv: ByteArray, gid: String?, seq: Int, prev: String, name: String, creator: String,
        admins: Set<String>, members: Set<String>, deleted: Boolean,
    ): Event {
        val content = JSONObject()
            .put("v", 1)
            .put("seq", seq)
            .put("prev", prev)
            .put("name", name.trim().take(MAX_NAME))
            .put("creator", creator)
            .put("admins", JSONArray(admins.sorted()))
            .put("members", JSONArray(members.sorted()))
            .put("deleted", deleted)
        // Zufallswert in der Gründung: Zwei gleich benannte Gruppen erhalten verschiedene IDs.
        if (gid == null) content.put("nonce", randomBytes(16).toHex())
        val tags = if (gid == null) emptyList() else listOf(listOf("g", gid))
        return Event.sign(priv, KIND_STATE, tags, content.toString())
    }

    /**
     * Liest und prüft einen Zustand: gültige Schnorr-Signatur, gültige Felder, bei der Gründung muss der
     * Signierer der Ersteller sein. Liefert null bei jeder Unstimmigkeit.
     */
    fun parse(ev: Event): GroupState? = try {
        require(ev.kind == KIND_STATE && ev.verify())
        val o = JSONObject(ev.content)
        val seq = o.getInt("seq")
        val members = o.getJSONArray("members").let { a -> (0 until a.length()).map { a.getString(it) } }
        val admins = o.getJSONArray("admins").let { a -> (0 until a.length()).map { a.getString(it) } }
        val creator = o.getString("creator")
        val prev = o.getString("prev")
        val name = o.getString("name")
        require(members.size in 1..MAX_MEMBERS && members.toSet().size == members.size)
        require(admins.size in 1..MAX_ADMINS && admins.toSet().size == admins.size)
        require((members + admins + creator).all { HEX64.matches(it) })
        require(members.containsAll(admins) && creator in admins)
        require(name.length <= MAX_NAME)
        val gid = if (seq == 0) {
            require(prev.isEmpty() && ev.pubkey == creator && admins == listOf(creator) && ev.tag("g") == null)
            ev.id
        } else {
            require(seq > 0 && HEX64.matches(prev))
            ev.tag("g")!!.also { require(HEX64.matches(it)) }
        }
        GroupState(gid, ev, seq, prev, name, creator, admins.toSet(), members.toSet(), o.optBoolean("deleted"))
    } catch (e: Exception) {
        null
    }

    /**
     * Ist [n] ein erlaubter Nachfolger von [p]? Regeln:
     *  - Folgenummer +1, Verweis auf [p], gleiche Gruppe, gleicher Ersteller; gelöschte Gruppen enden.
     *  - Der Ersteller bleibt immer Mitglied und Admin (niemand kann ihn entfernen).
     *  - Höchstens [MAX_ADMINS] Admins und [MAX_MEMBERS] Mitglieder; Admins sind Mitglieder.
     *  - Jedes Mitglied (außer dem Ersteller) darf sich selbst entfernen (Gruppe verlassen) – sonst nichts.
     *  - Alle anderen Änderungen nur durch Admins; andere Admins entfernen/absetzen nur durch den Ersteller
     *    (ein Admin darf aber selbst zurücktreten).
     */
    fun validateTransition(p: GroupState, n: GroupState): Boolean {
        if (p.deleted) return false
        if (n.gid != p.gid || n.seq != p.seq + 1 || n.prev != p.id || n.creator != p.creator) return false
        if (n.creator !in n.members || n.creator !in n.admins) return false
        if (!n.members.containsAll(n.admins) || n.admins.size > MAX_ADMINS || n.members.size > MAX_MEMBERS) return false
        val s = n.signer
        if (s !in p.members) return false

        val leaving = s != p.creator && !n.deleted && n.name == p.name &&
            n.members == p.members - s && n.admins == p.admins - s
        if (leaving) return true

        if (s !in p.admins) return false
        val removedAdmins = p.admins - n.admins - s // eigener Rücktritt ist erlaubt
        if (removedAdmins.isNotEmpty() && s != p.creator) return false
        return true
    }
}
