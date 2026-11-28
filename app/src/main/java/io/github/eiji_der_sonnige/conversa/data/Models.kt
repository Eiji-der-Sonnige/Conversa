package io.github.eiji_der_sonnige.conversa.data

import io.github.eiji_der_sonnige.conversa.R
import org.json.JSONArray
import org.json.JSONObject

enum class MsgType { TEXT, IMAGE, VIDEO, AUDIO, FILE }

/**
 * Eigene Nachrichten: SENDING -> SENT (von einem Relay angenommen, ✓) -> READ (Lesebestätigung, ✓✓).
 * Empfangene Nachrichten: RECEIVED (ungelesen) -> READ (gelesen).
 */
enum class MsgStatus(val rank: Int) {
    FAILED(0), SENDING(1), SENT(2), RECEIVED(2), READ(3);

    /** Status werden nur „hochgestuft“, damit z. B. eine erneut synchronisierte Kopie ✓✓ nicht auf ✓ zurücksetzt. */
    fun merge(other: MsgStatus): MsgStatus = if (other.rank > rank) other else this
}

fun mediaType(mime: String): MsgType = when {
    mime.startsWith("image/") -> MsgType.IMAGE
    mime.startsWith("video/") -> MsgType.VIDEO
    mime.startsWith("audio/") -> MsgType.AUDIO
    else -> MsgType.FILE
}

data class Media(
    val url: String,
    val mime: String,
    val keyHex: String,
    val nonceHex: String,
    val sha256: String,
    val size: Long,
    /** Nur bei Sprachnachrichten: Länge in Millisekunden (0 = unbekannt). */
    val durationMs: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url).put("mime", mime).put("key", keyHex)
        .put("nonce", nonceHex).put("sha", sha256).put("size", size)
        .apply { if (durationMs > 0) put("dur", durationMs) }

    /**
     * Prüft vom Absender gelieferte Felder, bevor sie verwendet werden: Die Prüfsumme dient als
     * Dateiname im Cache (Schutz vor Pfadmanipulation wie "../"), Schlüssel/Nonce müssen exakt
     * die Länge für AES-256-GCM haben, und Dateien werden nur über HTTPS geladen.
     */
    fun isWellFormed(): Boolean =
        HEX64.matches(sha256) && HEX64.matches(keyHex) && HEX24.matches(nonceHex) &&
            url.startsWith("https://") && url.length <= 2048 && size >= 0 && mime.length <= 128 &&
            durationMs in 0..MAX_DURATION_MS

    companion object {
        private val HEX64 = Regex("^[0-9a-f]{64}$")
        private val HEX24 = Regex("^[0-9a-f]{24}$")
        const val MAX_DURATION_MS = 24L * 3600 * 1000

        fun fromJson(o: JSONObject) = Media(
            o.getString("url"), o.getString("mime"), o.getString("key"),
            o.getString("nonce"), o.getString("sha"), o.optLong("size"), o.optLong("dur")
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

    fun preview(ctx: android.content.Context): String = when (type) {
        MsgType.TEXT -> text
        MsgType.IMAGE -> ctx.getString(R.string.preview_image)
        MsgType.VIDEO -> ctx.getString(R.string.preview_video)
        MsgType.AUDIO -> ctx.getString(R.string.preview_voice)
        MsgType.FILE -> ctx.getString(R.string.preview_file)
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
