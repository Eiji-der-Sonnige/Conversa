package io.github.eiji_der_sonnige.conversa

import io.github.eiji_der_sonnige.conversa.crypto.Bech32
import io.github.eiji_der_sonnige.conversa.crypto.Nip44
import io.github.eiji_der_sonnige.conversa.crypto.Npub
import io.github.eiji_der_sonnige.conversa.crypto.Secp256k1
import io.github.eiji_der_sonnige.conversa.crypto.Totp
import io.github.eiji_der_sonnige.conversa.crypto.hexToBytes
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import io.github.eiji_der_sonnige.conversa.nostr.Event
import io.github.eiji_der_sonnige.conversa.nostr.GiftWrap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {
    private fun key(n: Int) = ByteArray(32).also { it[31] = n.toByte() }

    @Test
    fun bip340_vector0() {
        val priv = key(3)
        assertEquals(
            "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9",
            Secp256k1.pubKeyXOnly(priv).toHex()
        )
        val sig = Secp256k1.sign(ByteArray(32), priv, ByteArray(32))
        assertEquals(
            "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0",
            sig.toHex()
        )
        assertTrue(Secp256k1.verify(sig, ByteArray(32), Secp256k1.pubKeyXOnly(priv)))
        sig[5] = (sig[5] + 1).toByte()
        assertFalse(Secp256k1.verify(sig, ByteArray(32), Secp256k1.pubKeyXOnly(priv)))
    }

    @Test
    fun nip44_vectors() {
        val ck = Nip44.conversationKey(key(1), Secp256k1.pubKeyXOnly(key(2)))
        assertEquals("c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d", ck.toHex())
        val payload = Nip44.encrypt("a", ck, key(1))
        assertEquals(
            // Version 0x02 + Nonce (31x 0x00, 0x01) + Chiffretext + MAC
            "Ag" + "A".repeat(41) + "Bee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb",
            payload
        )
        assertEquals("a", Nip44.decrypt(payload, ck))
        assertEquals(32, Nip44.calcPaddedLen(16))
        assertEquals(64, Nip44.calcPaddedLen(33))
        assertEquals(320, Nip44.calcPaddedLen(257))
    }

    @Test
    fun totp_rfc6238() {
        val secret = Totp.base32Encode("12345678901234567890".toByteArray())
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", secret)
        assertEquals("287082", Totp.code(secret, 59_000L))
        assertEquals("081804", Totp.code(secret, 1111111109_000L))
    }

    @Test
    fun npub_roundtrip() {
        val pub = Secp256k1.pubKeyXOnly(key(7)).toHex()
        val npub = Npub.encode(pub)
        assertTrue(npub.startsWith("npub1"))
        assertEquals(pub, Npub.parse(npub))
        assertEquals(pub, Bech32.decode(npub).second.toHex())
    }

    @Test
    fun giftwrap_roundtrip() {
        val alice = key(11)
        val bob = key(12)
        val bobPub = Secp256k1.pubKeyXOnly(bob).toHex()
        val alicePub = Secp256k1.pubKeyXOnly(alice).toHex()
        val rumor = GiftWrap.rumor(alicePub, GiftWrap.KIND_CHAT, listOf(listOf("p", bobPub)), "Hallo \"Bob\" / ünïcödé\n")
        val wrap = GiftWrap.wrap(rumor, alice, bobPub)
        assertTrue(wrap.verify())
        assertFalse(wrap.pubkey == alicePub) // Absender verborgen
        val parsed = Event.fromJson(org.json.JSONObject(wrap.toJson().toString()))
        val u = GiftWrap.unwrap(parsed, bob)
        assertNotNull(u)
        assertEquals(alicePub, u!!.sender)
        assertEquals(rumor.content, u.rumor.content)
        // Dritte können nicht entschlüsseln
        assertEquals(null, GiftWrap.unwrap(parsed, key(13)))
        assertEquals(bobPub, "${bobPub}".hexToBytes().toHex())
    }
}
