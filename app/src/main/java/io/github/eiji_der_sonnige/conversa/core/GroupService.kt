package io.github.eiji_der_sonnige.conversa.core

import android.content.Context
import io.github.eiji_der_sonnige.conversa.R
import io.github.eiji_der_sonnige.conversa.crypto.Identity
import io.github.eiji_der_sonnige.conversa.data.ChatMessage
import io.github.eiji_der_sonnige.conversa.data.ChatStore
import io.github.eiji_der_sonnige.conversa.data.GroupEntry
import io.github.eiji_der_sonnige.conversa.data.Media
import io.github.eiji_der_sonnige.conversa.data.groupChatKey
import io.github.eiji_der_sonnige.conversa.nostr.Event
import io.github.eiji_der_sonnige.conversa.nostr.GiftWrap
import org.json.JSONObject
import java.io.IOException

/**
 * Gruppenchats nach dem Muster von NIP-17: Jede Nachricht wird einzeln für jedes aktuelle Mitglied
 * verschlüsselt und als Gift Wrap verpackt (Fan-out). Es gibt keinen gemeinsamen Gruppenschlüssel, den ein
 * entferntes Mitglied behalten könnte – wer entfernt ist, bekommt ab sofort schlicht nichts Neues mehr.
 * Relays sehen wie bei 1:1-Chats nur einzelne Empfänger-Schlüssel, aber keine Gruppe und keine Mitgliederliste.
 *
 * Mitglieder und Rechte stehen in signierten Zuständen ([GroupRules]); sie werden ebenfalls verschlüsselt an
 * alle (auch die gerade entfernten) Mitglieder verteilt und von jedem Gerät selbst geprüft.
 */
class GroupService(
    private val ctx: Context,
    private val identity: Identity,
    private val store: ChatStore,
    /** Verpackt [rumor] für jeden Empfänger und für die eigenen Geräte und sendet. */
    private val publish: (rumor: Event, recipients: List<String>, chatKey: String, msgId: String?) -> Unit,
    private val toMessage: (rumor: Event, chatKey: String, fromMe: Boolean, sender: String) -> ChatMessage?,
    private val addIncoming: (ChatMessage) -> Unit,
    private val dropMedia: (Media) -> Unit,
    private val accountChanged: () -> Unit,
) {
    private val me get() = identity.pubHex

    /** Zustände, deren Vorgänger noch fehlt (Relays liefern nicht unbedingt in der richtigen Reihenfolge). */
    private val pendingStates = HashMap<String, MutableList<Pair<GroupState, Event>>>()
    /** Nachrichten an Gruppen, deren Zustand noch nicht angekommen ist. */
    private val early = HashMap<String, MutableList<Triple<Event, String, Boolean>>>()
    /** Laut Konto-Event auf einem anderen Gerät bereits bestätigte Beitritte. */
    private val acceptedElsewhere = HashSet<String>()

    // ------------------------------------------------------------------ Empfang

    @Synchronized
    fun onRumor(r: Event, sender: String, fromMe: Boolean, gid: String) {
        if (!HEX64.matches(gid)) return
        if (!fromMe && store.isBlocked(sender)) return
        when (r.kind) {
            KIND_CONTROL -> onControl(r, sender, fromMe, gid)
            // Von einem eigenen Gerät verlassen/gelöscht: auch hier entfernen (und nach Neuanmeldung nicht wiederherstellen).
            KIND_HIDE -> if (fromMe) wipe(gid)
            GiftWrap.KIND_CHAT, GiftWrap.KIND_FILE -> onMessage(r, sender, fromMe, gid)
            GiftWrap.KIND_DELETE -> if (fromMe) {
                val ids = r.tagValues("e").filter { HEX64.matches(it) }.take(100)
                store.deleteMessages(groupChatKey(gid), ids, onlyFromPeer = false).forEach { it.media?.let(dropMedia) }
            }
        }
    }

    private fun onControl(r: Event, sender: String, fromMe: Boolean, gid: String) {
        val (n, genesis) = try {
            val o = JSONObject(r.content)
            val stateEv = Event.fromJson(o.getJSONObject("state"))
            val genesisEv = Event.fromJson(o.getJSONObject("genesis"))
            (GroupRules.parse(stateEv) ?: return) to genesisEv
        } catch (e: Exception) {
            return
        }
        val g = GroupRules.parse(genesis) ?: return
        // Gründung beweist den Ersteller; der Zustand muss zur Gruppe gehören und vom Absender selbst signiert sein.
        if (g.seq != 0 || g.gid != gid || n.gid != gid || n.creator != g.creator || n.signer != sender) return
        applyState(n, genesis, fromMe)
    }

    private fun applyState(n: GroupState, genesis: Event, fromMe: Boolean) {
        val gid = n.gid
        if (store.isGroupDeleted(gid)) return
        val e = store.groups.value[gid]
        if (e == null) {
            // Neue Gruppe bzw. Einladung. Ohne die frühere Kette prüfbar: Signatur, Bindung an den Ersteller und dass
            // der Absender laut Zustand Admin ist (Vertrauen beim Beitritt). Alles Weitere prüft validateTransition.
            if (n.deleted || me !in n.members || n.signer !in n.admins) {
                buffer(gid, n, genesis)
                return
            }
            if (!fromMe && n.signer != me && store.pendingInviteCount() >= MAX_PENDING_INVITES) return
            val accepted = fromMe || n.signer == me || gid in acceptedElsewhere
            store.putGroup(GroupEntry(n, genesis, null, accepted, removed = false))
            drainPending(gid)
            early.remove(gid)?.forEach { (r, s, mine) -> onMessage(r, s, mine, gid) }
            return
        }
        val cur = e.state
        val prev = e.prev
        when {
            n.id == cur.id -> return
            n.seq == cur.seq + 1 && n.prev == cur.id && GroupRules.validateTransition(cur, n) -> commit(e, n)
            // Zwei Admins haben gleichzeitig etwas geändert: Alle Geräte einigen sich auf den Zustand mit der kleineren ID.
            prev != null && n.seq == cur.seq && n.prev == cur.prev && n.id < cur.id &&
                GroupRules.validateTransition(prev, n) -> commit(e.copy(state = prev, prev = null), n)
            n.seq > cur.seq + 1 -> buffer(gid, n, genesis)
            else -> return
        }
        drainPending(gid)
    }

    private fun commit(e: GroupEntry, n: GroupState) {
        if (n.deleted) {
            wipe(n.gid)
            return
        }
        val left = e.left.toMutableMap()
        (e.state.members - n.members).forEach { left[it] = n.createdAt }
        n.members.forEach { left.remove(it) }
        store.putGroup(e.copy(state = n, prev = e.state, removed = me !in n.members, left = left))
    }

    private fun buffer(gid: String, n: GroupState, genesis: Event) {
        if (pendingStates.size > 50 && gid !in pendingStates) return
        val list = pendingStates.getOrPut(gid) { mutableListOf() }
        if (list.size < 64 && list.none { it.first.id == n.id }) list.add(n to genesis)
    }

    private fun drainPending(gid: String) {
        val list = pendingStates[gid] ?: return
        while (true) {
            val e = store.groups.value[gid] ?: break
            list.removeAll { it.first.seq <= e.state.seq }
            val next = list.firstOrNull { it.first.seq == e.state.seq + 1 && it.first.prev == e.state.id } ?: break
            list.remove(next)
            if (GroupRules.validateTransition(e.state, next.first)) commit(e, next.first)
        }
        if (list.isEmpty()) pendingStates.remove(gid)
    }

    private fun onMessage(r: Event, sender: String, fromMe: Boolean, gid: String) {
        val e = store.groups.value[gid]
        if (e == null) {
            if (store.isGroupDeleted(gid)) return
            val list = early.getOrPut(gid) { mutableListOf() }
            if (early.size <= 50 && list.size < 200) list.add(Triple(r, sender, fromMe))
            return
        }
        val st = e.state
        // Nur Mitglieder – bzw. ehemalige Mitglieder für Nachrichten von vor ihrem Austritt. Hinweis: Den Zeitpunkt
        // einer Nachricht legt der Absender fest; ein Entfernter könnte eine Nachricht also zurückdatieren.
        val allowed = fromMe || sender in st.members || (e.left[sender]?.let { r.createdAt <= it } == true)
        if (!allowed) return
        val key = groupChatKey(gid)
        if (!e.accepted && !fromMe && store.messageCount(key) >= Messenger.MAX_REQUEST_MESSAGES) return
        val msg = toMessage(r, key, fromMe, sender) ?: return
        addIncoming(msg)
    }

    // ------------------------------------------------------------------ Senden

    /** Empfänger und Tags für eine Gruppennachricht (NIP-17: ein p-Tag je Mitglied, dazu die Gruppen-ID). */
    fun targets(gid: String): Pair<List<String>, List<List<String>>> {
        val e = store.groups.value[gid]
        if (e == null || e.removed || !e.accepted) throw IOException(ctx.getString(R.string.err_group_inactive))
        val to = (e.state.members - me).sorted()
        return to to (to.map { listOf("p", it) } + listOf(listOf("g", gid)))
    }

    private fun sendState(n: GroupState, genesis: Event, recipients: Collection<String>) {
        val content = JSONObject().put("state", n.event.toJson()).put("genesis", genesis.toJson())
        val rumor = GiftWrap.rumor(me, KIND_CONTROL, listOf(listOf("g", n.gid)), content.toString())
        publish(rumor, (recipients.toSet() - me).toList(), groupChatKey(n.gid), null)
    }

    @Synchronized
    fun create(name: String, members: Collection<String>): String {
        val invited = members.filter { HEX64.matches(it) && it != me }.toSet()
        if (invited.size + 1 > GroupRules.MAX_MEMBERS) {
            throw IOException(ctx.getString(R.string.err_group_full, GroupRules.MAX_MEMBERS))
        }
        val genesis = GroupRules.genesis(identity.privKey, me, name, invited)
        val g = GroupRules.parse(genesis) ?: throw IOException(ctx.getString(R.string.err_group_not_allowed))
        store.putGroup(GroupEntry(g, genesis, null, accepted = true, removed = false))
        sendState(g, genesis, invited)
        return g.gid
    }

    /** Führt eine eigene Änderung aus: gleiche Prüfung wie bei empfangenen Änderungen, dann Verteilung an alle. */
    @Synchronized
    private fun act(gid: String, build: (GroupState) -> Event) {
        val e = store.groups.value[gid] ?: throw IOException(ctx.getString(R.string.err_group_inactive))
        if (e.removed) throw IOException(ctx.getString(R.string.err_group_inactive))
        val n = GroupRules.parse(build(e.state))
        if (n == null || !GroupRules.validateTransition(e.state, n)) {
            throw IOException(ctx.getString(R.string.err_group_not_allowed))
        }
        commit(e, n)
        // Auch Entfernte erhalten den neuen Zustand – so erfahren sie, dass sie nicht mehr dazugehören.
        sendState(n, e.genesis, e.state.members + n.members)
    }

    private fun state(gid: String) = store.groups.value[gid]?.state ?: throw IOException(ctx.getString(R.string.err_group_inactive))

    fun addMember(gid: String, pub: String) {
        if (state(gid).members.size >= GroupRules.MAX_MEMBERS) {
            throw IOException(ctx.getString(R.string.err_group_full, GroupRules.MAX_MEMBERS))
        }
        act(gid) { GroupRules.next(identity.privKey, it, members = it.members + pub) }
    }

    fun removeMember(gid: String, pub: String) =
        act(gid) { GroupRules.next(identity.privKey, it, members = it.members - pub, admins = it.admins - pub) }

    fun setAdmin(gid: String, pub: String, admin: Boolean) {
        if (admin && state(gid).admins.size >= GroupRules.MAX_ADMINS) {
            throw IOException(ctx.getString(R.string.err_admin_limit, GroupRules.MAX_ADMINS))
        }
        act(gid) { GroupRules.next(identity.privKey, it, admins = if (admin) it.admins + pub else it.admins - pub) }
    }

    fun rename(gid: String, name: String) = act(gid) { GroupRules.next(identity.privKey, it, name = name) }

    /** Nur Admins: löscht die Gruppe für alle Mitglieder (sofern deren Geräte die Löschung empfangen). */
    fun deleteForAll(gid: String) = act(gid) { GroupRules.next(identity.privKey, it, deleted = true) }

    /** Verlassen (bzw. Einladung ablehnen) und den Chat auf deinen Geräten entfernen. Der Ersteller kann nur löschen. */
    fun leave(gid: String) {
        val e = store.groups.value[gid] ?: return
        if (!e.removed && me in e.state.members) {
            act(gid) { GroupRules.next(identity.privKey, it, members = it.members - me, admins = it.admins - me) }
        }
        wipe(gid)
        // Verschlüsselte Notiz nur an die eigenen Geräte (Semantik wie „für mich löschen“).
        publish(GiftWrap.rumor(me, KIND_HIDE, listOf(listOf("g", gid)), ""), emptyList(), groupChatKey(gid), null)
    }

    /** Beitritt bestätigen (nach dem Hinweis). Gilt dank Konto-Event auch auf deinen anderen Geräten. */
    fun accept(gid: String) {
        val e = store.groups.value[gid] ?: return
        store.putGroup(e.copy(accepted = true))
        accountChanged()
    }

    fun acceptedGroups(): List<String> = store.groups.value.values.filter { it.accepted }.map { it.gid }

    @Synchronized
    fun markAcceptedElsewhere(gids: Collection<String>) {
        gids.filter { HEX64.matches(it) }.forEach { gid ->
            acceptedElsewhere.add(gid)
            store.groups.value[gid]?.let { if (!it.accepted) store.putGroup(it.copy(accepted = true)) }
        }
    }

    private fun wipe(gid: String) {
        store.messages.value[groupChatKey(gid)].orEmpty().forEach { it.media?.let(dropMedia) }
        Notifier.cancelMessage(ctx, groupChatKey(gid))
        store.removeGroup(gid, tombstone = true)
        pendingStates.remove(gid)
        early.remove(gid)
    }

    companion object {
        /** Conversa-eigener Rumor für Gruppenzustände (Inhalt: signierter Zustand + Gründung). */
        const val KIND_CONTROL = 25070
        /** Nur an die eigenen Geräte: Gruppe wurde verlassen bzw. lokal gelöscht. */
        const val KIND_HIDE = 25072
        const val MAX_PENDING_INVITES = 10
        private val HEX64 = Regex("^[0-9a-f]{64}$")
    }
}
