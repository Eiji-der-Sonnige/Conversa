package io.github.eiji_der_sonnige.conversa.data

import org.json.JSONArray
import org.json.JSONObject

enum class MsgType { TEXT, IMAGE, VIDEO, FILE }
enum class MsgStatus { SENDING, SENT, FAILED, RECEIVED }

data class Media(
    val url: String,
    val mime: String,
    val keyHex: String,
    val nonceHex: String,
    val sha256: String,
    val size: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url).put("mime", mime).put("key", keyHex)
        .put("nonce", nonceHex).put("sha", sha256).put("size", size)

    companion object {
        fun fromJson(o: JSONObject) = Media(
            o.getString("url"), o.getString("mime"), o.getString("key"),
            o.getString("nonce"), o.getString("sha"), o.optLong("size")
        )
    }
}

data class ChatMessage(
    val id: String,
    val peer: String,
    val fromMe: Boolean,
    val type: MsgType,
    val text: String,
    val media: Media?,
    val createdAt: Long,
    val status: MsgStatus,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("peer", peer).put("me", fromMe).put("type", type.name)
        .put("text", text).put("at", createdAt).put("status", status.name)
        .apply { media?.let { put("media", it.toJson()) } }

    fun preview(): String = when (type) {
        MsgType.TEXT -> text
        MsgType.IMAGE -> "📷 Bild"
        MsgType.VIDEO -> "🎬 Video"
        MsgType.FILE -> "📎 Datei"
    }

    companion object {
        fun fromJson(o: JSONObject) = ChatMessage(
            o.getString("id"), o.getString("peer"), o.getBoolean("me"),
            MsgType.valueOf(o.getString("type")), o.optString("text"),
            o.optJSONObject("media")?.let { Media.fromJson(it) },
            o.getLong("at"),
            MsgStatus.valueOf(o.getString("status")).let { if (it == MsgStatus.SENDING) MsgStatus.FAILED else it }
        )
    }
}

data class Contact(val pubHex: String, val name: String, val accepted: Boolean = true) {
    fun toJson(): JSONObject = JSONObject().put("pub", pubHex).put("name", name).put("ok", accepted)

    companion object {
        fun fromJson(o: JSONObject) = Contact(o.getString("pub"), o.getString("name"), o.optBoolean("ok", true))
    }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
