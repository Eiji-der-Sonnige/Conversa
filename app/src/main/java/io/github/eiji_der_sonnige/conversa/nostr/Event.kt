package io.github.eiji_der_sonnige.conversa.nostr

import io.github.eiji_der_sonnige.conversa.crypto.Secp256k1
import io.github.eiji_der_sonnige.conversa.crypto.hexToBytes
import io.github.eiji_der_sonnige.conversa.crypto.sha256
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import org.json.JSONArray
import org.json.JSONObject

data class Event(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    fun tag(name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)
    fun tagValues(name: String): List<String> = tags.filter { it.size >= 2 && it[0] == name }.map { it[1] }

    fun hasValidId(): Boolean = try {
        computeId(pubkey, createdAt, kind, tags, content).toHex() == id
    } catch (e: Exception) {
        false
    }

    fun verify(): Boolean = try {
        hasValidId() && Secp256k1.verify(sig.hexToBytes(), id.hexToBytes(), pubkey.hexToBytes())
    } catch (e: Exception) {
        false
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("pubkey", pubkey)
        put("created_at", createdAt)
        put("kind", kind)
        put("tags", JSONArray().apply { tags.forEach { t -> put(JSONArray(t)) } })
        put("content", content)
        if (sig.isNotEmpty()) put("sig", sig)
    }

    companion object {
        /** Aktuelle Zeit in Sekunden, korrigiert um die gemessene Abweichung der Geräteuhr. */
        fun now(): Long = io.github.eiji_der_sonnige.conversa.core.NetworkTime.now() / 1000

        private fun StringBuilder.appendQuoted(s: String) {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    else -> if (c.code < 0x20) append(String.format("\\u%04x", c.code)) else append(c)
                }
            }
            append('"')
        }

        /** Exakte NIP-01-Serialisierung (eigene Implementierung, da org.json "/" escaped). */
        fun serialize(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String =
            buildString {
                append("[0,\"").append(pubkey).append("\",").append(createdAt).append(',').append(kind).append(",[")
                tags.forEachIndexed { i, t ->
                    if (i > 0) append(',')
                    append('[')
                    t.forEachIndexed { j, s ->
                        if (j > 0) append(',')
                        appendQuoted(s)
                    }
                    append(']')
                }
                append("],")
                appendQuoted(content)
                append(']')
            }

        fun computeId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray =
            sha256(serialize(pubkey, createdAt, kind, tags, content).toByteArray(Charsets.UTF_8))

        fun sign(priv: ByteArray, kind: Int, tags: List<List<String>>, content: String, createdAt: Long = now()): Event {
            val pub = Secp256k1.pubKeyXOnly(priv).toHex()
            val id = computeId(pub, createdAt, kind, tags, content)
            val sig = Secp256k1.sign(id, priv)
            return Event(id.toHex(), pub, createdAt, kind, tags, content, sig.toHex())
        }

        fun unsigned(pubkey: String, kind: Int, tags: List<List<String>>, content: String, createdAt: Long = now()): Event {
            val id = computeId(pubkey, createdAt, kind, tags, content)
            return Event(id.toHex(), pubkey, createdAt, kind, tags, content, "")
        }

        fun fromJson(o: JSONObject): Event {
            val tagsArr = o.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagsArr.length()).map { i ->
                val t = tagsArr.getJSONArray(i)
                (0 until t.length()).map { t.getString(it) }
            }
            return Event(
                id = o.getString("id"),
                pubkey = o.getString("pubkey"),
                createdAt = o.getLong("created_at"),
                kind = o.getInt("kind"),
                tags = tags,
                content = o.getString("content"),
                sig = o.optString("sig", ""),
            )
        }
    }
}
