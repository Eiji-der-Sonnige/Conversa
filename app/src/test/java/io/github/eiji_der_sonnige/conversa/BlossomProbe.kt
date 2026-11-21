package io.github.eiji_der_sonnige.conversa

import io.github.eiji_der_sonnige.conversa.crypto.Secp256k1
import io.github.eiji_der_sonnige.conversa.crypto.randomBytes
import io.github.eiji_der_sonnige.conversa.crypto.sha256
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import io.github.eiji_der_sonnige.conversa.nostr.Event
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume
import org.junit.Test
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Manuelle Netzwerkprobe (nur mit -Dblossom.probe=true): Welche Blossom-Server akzeptieren verschlüsselte Blobs? */
class BlossomProbe {
    @Test
    fun probe() {
        Assume.assumeTrue(System.getProperty("blossom.probe") == "true")
        val http = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build()
        val servers = listOf(
            "https://blossom.primal.net", "https://blossom.band", "https://nostr.download",
            "https://blossom.yakihonne.com", "https://files.sovbit.host", "https://cdn.nostrcheck.me",
            "https://blossom.nostr.hu", "https://blossom.f7z.io", "https://24242.io",
        )
        val data = randomBytes(2048) // wie verschlüsselte Daten: kein erkennbarer Dateityp
        val hash = sha256(data).toHex()
        for (s in servers) {
            val auth = Event.sign(
                Secp256k1.generatePrivKey(), 24242,
                listOf(listOf("t", "upload"), listOf("x", hash), listOf("expiration", (Event.now() + 600).toString())),
                "Upload"
            )
            val header = "Nostr " + Base64.getEncoder().encodeToString(auth.toJson().toString().toByteArray())
            val result = try {
                http.newCall(
                    Request.Builder().url("$s/upload")
                        .put(data.toRequestBody("application/octet-stream".toMediaType()))
                        .header("Authorization", header).header("X-SHA-256", hash).build()
                ).execute().use { r ->
                    val body = r.body?.string().orEmpty().take(160).replace("\n", " ")
                    "HTTP ${r.code} ${r.header("X-Reason").orEmpty()} | $body"
                }
            } catch (e: Exception) {
                "FEHLER ${e.javaClass.simpleName}: ${e.message}"
            }
            println("BLOSSOM $s -> $result")
        }
    }
}
