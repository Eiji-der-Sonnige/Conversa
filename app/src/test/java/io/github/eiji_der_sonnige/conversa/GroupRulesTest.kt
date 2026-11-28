package io.github.eiji_der_sonnige.conversa

import io.github.eiji_der_sonnige.conversa.core.GroupRules
import io.github.eiji_der_sonnige.conversa.core.GroupState
import io.github.eiji_der_sonnige.conversa.crypto.Secp256k1
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import io.github.eiji_der_sonnige.conversa.nostr.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Admin-Rechte in Gruppen werden über Signaturen geprüft, nicht über die Oberfläche. */
class GroupRulesTest {
    private val creatorKey = Secp256k1.generatePrivKey()
    private val adminKey = Secp256k1.generatePrivKey()
    private val memberKey = Secp256k1.generatePrivKey()
    private val outsiderKey = Secp256k1.generatePrivKey()
    private fun pub(k: ByteArray) = Secp256k1.pubKeyXOnly(k).toHex()
    private val creator = pub(creatorKey)
    private val admin = pub(adminKey)
    private val member = pub(memberKey)
    private val outsider = pub(outsiderKey)

    private fun parsed(ev: Event): GroupState = assertNotNull(GroupRules.parse(ev)).let { GroupRules.parse(ev)!! }

    /** Gruppe mit Ersteller, einem weiteren Admin und einem Mitglied. */
    private fun base(): GroupState {
        val g = parsed(GroupRules.genesis(creatorKey, creator, "Test", listOf(admin, member)))
        val s1 = parsed(GroupRules.next(creatorKey, g, admins = setOf(creator, admin)))
        assertTrue(GroupRules.validateTransition(g, s1))
        return s1
    }

    @Test
    fun genesisBindsCreatorToGroupId() {
        val ev = GroupRules.genesis(creatorKey, creator, "Test", listOf(member))
        val g = parsed(ev)
        assertEquals(ev.id, g.gid)
        assertEquals(setOf(creator), g.admins)
        assertTrue(member in g.members && creator in g.members)
        // Jemand anderes kann keine Gründung im Namen des Erstellers signieren.
        val forged = Event.sign(outsiderKey, GroupRules.KIND_STATE, emptyList(), ev.content)
        assertNull(GroupRules.parse(forged))
    }

    @Test
    fun tamperedStateIsRejected() {
        val s = base()
        val ev = GroupRules.next(adminKey, s, members = s.members + outsider)
        val tampered = ev.copy(content = ev.content.replace(outsider, member))
        assertNull(GroupRules.parse(tampered))
    }

    @Test
    fun onlyAdminsInviteAndRemove() {
        val s = base()
        assertTrue(GroupRules.validateTransition(s, parsed(GroupRules.next(adminKey, s, members = s.members + outsider))))
        assertFalse(GroupRules.validateTransition(s, parsed(GroupRules.next(memberKey, s, members = s.members + outsider))))
        assertFalse(GroupRules.validateTransition(s, parsed(GroupRules.next(outsiderKey, s, members = s.members + outsider))))
        assertTrue(GroupRules.validateTransition(s, parsed(GroupRules.next(adminKey, s, members = s.members - member))))
    }

    @Test
    fun adminsPromoteButAtMostFive() {
        val s = base()
        assertTrue(GroupRules.validateTransition(s, parsed(GroupRules.next(adminKey, s, admins = s.admins + member))))
        assertFalse(GroupRules.validateTransition(s, parsed(GroupRules.next(memberKey, s, admins = s.admins + member))))
        val many = (1..4).map { pub(Secp256k1.generatePrivKey()) }
        val big = parsed(GroupRules.next(creatorKey, s, members = s.members + many))
        assertTrue(GroupRules.validateTransition(s, big))
        val sixAdmins = big.admins + many.take(4)
        assertEquals(6, sixAdmins.size)
        // Sechs Admins: wird schon beim Lesen verworfen
        assertNull(GroupRules.parse(GroupRules.next(creatorKey, big, admins = sixAdmins)))
    }

    @Test
    fun onlyCreatorRemovesAdminsAndNobodyRemovesCreator() {
        val s = base()
        // Admin darf anderen Admin nicht absetzen, der Ersteller schon
        val withThird = parsed(GroupRules.next(creatorKey, s, admins = s.admins + member))
        assertFalse(GroupRules.validateTransition(withThird, parsed(GroupRules.next(adminKey, withThird, admins = withThird.admins - member))))
        assertTrue(GroupRules.validateTransition(withThird, parsed(GroupRules.next(creatorKey, withThird, admins = withThird.admins - member))))
        assertFalse(GroupRules.validateTransition(withThird, parsed(GroupRules.next(adminKey, withThird, members = withThird.members - member, admins = withThird.admins - member))))
        // Admin darf selbst zurücktreten
        assertTrue(GroupRules.validateTransition(s, parsed(GroupRules.next(adminKey, s, admins = s.admins - admin))))
        // Ersteller entfernen: ungültig (wird schon beim Lesen verworfen oder bei der Prüfung abgelehnt)
        val noCreator = GroupRules.parse(GroupRules.next(adminKey, s, members = s.members - creator, admins = s.admins - creator))
        assertTrue(noCreator == null || !GroupRules.validateTransition(s, noCreator))
    }

    @Test
    fun membersMayOnlyLeaveThemselves() {
        val s = base()
        assertTrue(GroupRules.validateTransition(s, parsed(GroupRules.next(memberKey, s, members = s.members - member))))
        assertFalse(GroupRules.validateTransition(s, parsed(GroupRules.next(memberKey, s, members = s.members - admin, admins = s.admins - admin))))
        assertFalse(GroupRules.validateTransition(s, parsed(GroupRules.next(memberKey, s, name = "Neu"))))
    }

    @Test
    fun onlyAdminsDeleteAndChainMustBeContinuous() {
        val s = base()
        assertFalse(GroupRules.validateTransition(s, parsed(GroupRules.next(memberKey, s, deleted = true))))
        val del = parsed(GroupRules.next(adminKey, s, deleted = true))
        assertTrue(GroupRules.validateTransition(s, del))
        // Nach dem Löschen ist nichts mehr möglich
        assertFalse(GroupRules.validateTransition(del, parsed(GroupRules.next(creatorKey, del))))
        // Lücke in der Kette oder falscher Vorgänger
        val n1 = parsed(GroupRules.next(adminKey, s, name = "A"))
        val n2 = parsed(GroupRules.next(adminKey, n1, name = "B"))
        assertFalse(GroupRules.validateTransition(s, n2))
    }
}
