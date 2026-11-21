package io.github.eiji_der_sonnige.conversa.nostr

import io.github.eiji_der_sonnige.conversa.crypto.Nip44
import io.github.eiji_der_sonnige.conversa.crypto.Secp256k1
import io.github.eiji_der_sonnige.conversa.crypto.hexToBytes
import io.github.eiji_der_sonnige.conversa.crypto.secureRandom
import org.json.JSONObject

/**
 * Private Nachrichten nach NIP-17 / NIP-59 ("Gift Wrap"):
 *  rumor (unsigniert, echter Inhalt) -> seal (vom Absender signiert, verschlüsselt)
 *  -> gift wrap (mit Einmal-Schlüssel signiert, verschlüsselt).
 * Relays sehen weder Absender, Inhalt noch genauen Zeitpunkt – nur den Empfänger-Schlüssel.
 */
object GiftWrap {
    const val KIND_SEAL = 13
    const val KIND_CHAT = 14
    const val KIND_FILE = 15
    const val KIND_WRAP = 1059
    const val KIND_SIGNAL = 25050

    private fun randomPast(): Long = Event.now() - secureRandom.nextInt(2 * 24 * 3600)

    fun rumor(senderPub: String, kind: Int, tags: List<List<String>>, content: String): Event =
        Event.unsigned(senderPub, kind, tags, content)

    fun wrap(rumor: Event, senderPriv: ByteArray, recipientPub: String, expiration: Long? = null): Event {
        val recipient = recipientPub.hexToBytes()
        val seal = Event.sign(
            senderPriv, KIND_SEAL, emptyList(),
            Nip44.encrypt(rumor.toJson().toString(), Nip44.conversationKey(senderPriv, recipient)),
            randomPast()
        )
        val ephemeral = Secp256k1.generatePrivKey()
        val tags = mutableListOf(listOf("p", recipientPub))
        expiration?.let { tags.add(listOf("expiration", it.toString())) }
        return Event.sign(
            ephemeral, KIND_WRAP, tags,
            Nip44.encrypt(seal.toJson().toString(), Nip44.conversationKey(ephemeral, recipient)),
            if (expiration != null) Event.now() else randomPast()
        )
    }

    data class Unwrapped(val rumor: Event, val sender: String)

    fun unwrap(wrap: Event, myPriv: ByteArray): Unwrapped? = try {
        val sealJson = Nip44.decrypt(wrap.content, Nip44.conversationKey(myPriv, wrap.pubkey.hexToBytes()))
        val seal = Event.fromJson(JSONObject(sealJson))
        if (seal.kind != KIND_SEAL || !seal.verify()) {
            null
        } else {
            val rumorJson = Nip44.decrypt(seal.content, Nip44.conversationKey(myPriv, seal.pubkey.hexToBytes()))
            val rumor = Event.fromJson(JSONObject(rumorJson))
            // Schutz vor Identitätsfälschung: Rumor-Autor muss der Siegel-Signierer sein.
            if (rumor.pubkey != seal.pubkey || !rumor.hasValidId()) null else Unwrapped(rumor, seal.pubkey)
        }
    } catch (e: Exception) {
        null
    }
}
