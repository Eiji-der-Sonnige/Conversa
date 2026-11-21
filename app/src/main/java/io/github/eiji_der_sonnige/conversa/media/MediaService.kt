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
        var lastError: Exception? = null
        for (server in servers()) {
            try {
                val req = Request.Builder()
                    .url(server.trimEnd('/') + "/upload")
                    .put(data.toRequestBody("application/octet-stream".toMediaType()))
                    .header("Authorization", header)
                    .header("X-SHA-256", hash)
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("$server: HTTP ${resp.code} ${resp.header("X-Reason").orEmpty()}")
                    }
                    val url = JSONObject(resp.body!!.string()).getString("url")
                    require(url.startsWith("https://")) { "Unsichere URL" }
                    return@withContext url
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Kein Medien-Server konfiguriert")
    }

    private fun fetch(url: String): ByteArray? = try {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }
    } catch (e: Exception) {
        null
    }

    /** Lädt (oder nimmt aus dem Cache), prüft SHA-256 und entschlüsselt. */
    suspend fun load(media: Media): ByteArray = withContext(Dispatchers.IO) {
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

    /** Für die Videowiedergabe: entschlüsselte Datei im privaten App-Cache (wird beim Start geleert). */
    suspend fun loadToTempFile(media: Media): File {
        val bytes = load(media)
        return withContext(Dispatchers.IO) {
            File(tmpDir, media.sha256.take(16) + ".mp4").apply { writeBytes(bytes) }
        }
    }

    fun clearTemp() {
        tmpDir.deleteRecursively()
    }
}
