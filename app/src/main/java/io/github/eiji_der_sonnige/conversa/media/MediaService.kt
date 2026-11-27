package io.github.eiji_der_sonnige.conversa.media

import android.content.Context
import io.github.eiji_der_sonnige.conversa.crypto.Secp256k1
import io.github.eiji_der_sonnige.conversa.crypto.hexToBytes
import io.github.eiji_der_sonnige.conversa.crypto.randomBytes
import io.github.eiji_der_sonnige.conversa.crypto.sha256
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import io.github.eiji_der_sonnige.conversa.data.Media
import io.github.eiji_der_sonnige.conversa.nostr.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bilder/Videos werden auf dem Gerät mit einem zufälligen AES-256-GCM-Schlüssel verschlüsselt und
 * nur als unlesbarer Blob auf dezentrale, inhaltsadressierte Blossom-Server geladen. Der Schlüssel
 * reist ausschließlich in der Ende-zu-Ende-verschlüsselten Nachricht. Uploads werden mit einem
 * Einmal-Schlüssel signiert, damit der Server sie keiner Identität zuordnen kann.
 */
class MediaService(
    private val ctx: Context,
    private val http: OkHttpClient,
    private val servers: () -> List<String>,
) {
    class Encrypted(val data: ByteArray, val key: ByteArray, val nonce: ByteArray)

    private val cacheDir get() = File(ctx.cacheDir, "media_enc").apply { mkdirs() }
    private val tmpDir get() = File(ctx.cacheDir, "media_tmp").apply { mkdirs() }

    fun encrypt(plain: ByteArray): Encrypted {
        val key = randomBytes(32)
        val nonce = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return Encrypted(c.doFinal(plain), key, nonce)
    }

    fun decrypt(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return c.doFinal(data)
    }

    suspend fun upload(data: ByteArray): String = withContext(Dispatchers.IO) {
        val hash = sha256(data).toHex()
        File(cacheDir, hash).writeBytes(data)
        val auth = Event.sign(
            Secp256k1.generatePrivKey(), 24242,
            listOf(listOf("t", "upload"), listOf("x", hash), listOf("expiration", (Event.now() + 600).toString())),
            "Upload"
        )
        val header = "Nostr " + Base64.getEncoder().encodeToString(auth.toJson().toString().toByteArray())
        val errors = mutableListOf<String>()
        for (server in servers()) {
            val base = server.trimEnd('/')
            try {
                val req = Request.Builder()
                    .url("$base/upload")
                    .put(data.toRequestBody("application/octet-stream".toMediaType()))
                    .header("Authorization", header)
                    .header("X-SHA-256", hash)
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("HTTP ${resp.code} ${resp.header("X-Reason").orEmpty()}".trim())
                    }
                    val json = try { JSONObject(resp.body?.string().orEmpty()) } catch (e: Exception) { JSONObject() }
                    // Manche Server liefern kein "url"-Feld; nach Blossom-Standard ist die Datei dann unter /<sha256> abrufbar.
                    if (json.has("sha256") && json.optString("sha256") != hash) throw IOException("Prüfsumme abweichend")
                    val url = json.optString("url").ifEmpty { "$base/$hash" }
                    require(url.startsWith("https://")) { "Unsichere URL" }
                    return@withContext url
                }
            } catch (e: Exception) {
                errors += "${base.removePrefix("https://")}: ${e.message}"
            }
        }
        throw IOException(if (errors.isEmpty()) "Kein Medien-Server konfiguriert" else errors.joinToString("; "))
    }

    /** Lädt höchstens [MAX_DOWNLOAD] Bytes – schützt vor Speicherüberlauf durch manipulierte Links. */
    private fun fetch(url: String): ByteArray? = try {
        http.newCall(Request.Builder().url(url).build()).execute().use resp@{ r ->
            val body = r.body
            if (!r.isSuccessful || body == null || body.contentLength() > MAX_DOWNLOAD) return@resp null
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            val input = body.byteStream()
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_DOWNLOAD) return@resp null
            }
            out.toByteArray()
        }
    } catch (e: Exception) {
        null
    }

    /** Lädt (oder nimmt aus dem Cache), prüft SHA-256 und entschlüsselt. */
    suspend fun load(media: Media): ByteArray = withContext(Dispatchers.IO) {
        require(media.isWellFormed()) { "Ungültige Mediendaten" }
        val cached = File(cacheDir, media.sha256)
        val enc = if (cached.exists()) {
            cached.readBytes()
        } else {
            require(media.url.startsWith("https://")) { "Unsichere URL" }
            val candidates = listOf(media.url) + servers().map { it.trimEnd('/') + "/" + media.sha256 }
            var data: ByteArray? = null
            for (u in candidates) {
                val d = fetch(u) ?: continue
                if (sha256(d).toHex() == media.sha256) {
                    data = d
                    break
                }
            }
            (data ?: throw IOException("Datei nicht verfügbar oder manipuliert")).also { cached.writeBytes(it) }
        }
        decrypt(enc, media.keyHex.hexToBytes(), media.nonceHex.hexToBytes())
    }

    /** Für Video/Audio: entschlüsselte Datei im privaten App-Cache (wird beim Start geleert). */
    suspend fun loadToTempFile(media: Media): File {
        val bytes = load(media)
        val ext = when {
            media.mime.startsWith("audio/") -> ".m4a"
            else -> ".mp4"
        }
        return withContext(Dispatchers.IO) {
            File(tmpDir, media.sha256.take(16) + ext).apply { writeBytes(bytes) }
        }
    }

    /** Nach dem Löschen einer Nachricht: verschlüsselte und entschlüsselte Kopie auf dem Gerät entfernen. */
    fun deleteCached(media: Media) {
        if (!media.isWellFormed()) return
        File(cacheDir, media.sha256).delete()
        tmpDir.listFiles()?.filter { it.name.startsWith(media.sha256.take(16)) }?.forEach { it.delete() }
    }

    fun clearTemp() {
        tmpDir.deleteRecursively()
    }

    companion object {
        /** Maximale Dateigröße beim Senden (50 MB) plus 16 Byte GCM-Tag. */
        const val MAX_DOWNLOAD = 50L * 1024 * 1024 + 16
    }
}
