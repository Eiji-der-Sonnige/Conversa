package io.github.eiji_der_sonnige.conversa.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * NIP-44 v2: ECDH(secp256k1) + HKDF-SHA256 + ChaCha20 + HMAC-SHA256 mit Längen-Padding.
 * Ende-zu-Ende-Verschlüsselung aller Nachrichten.
 */
object Nip44 {
    private val SALT = "nip44-v2".toByteArray()

    private fun hmac(key: ByteArray, vararg data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        data.forEach { mac.update(it) }
        return mac.doFinal()
    }

    fun conversationKey(priv: ByteArray, pubX: ByteArray): ByteArray =
        hmac(SALT, Secp256k1.ecdhX(priv, pubX))

    private fun messageKeys(convKey: ByteArray, nonce: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters.skipExtractParameters(convKey, nonce))
        val out = ByteArray(76)
        gen.generateBytes(out, 0, 76)
        return Triple(out.copyOfRange(0, 32), out.copyOfRange(32, 44), out.copyOfRange(44, 76))
    }

    fun calcPaddedLen(len: Int): Int {
        if (len <= 32) return 32
        val nextPower = 1 shl (32 - Integer.numberOfLeadingZeros(len - 1))
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((len - 1) / chunk + 1)
    }

    private fun pad(plaintext: String): ByteArray {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..65535) { "Nachricht zu lang oder leer" }
        val out = ByteArray(2 + calcPaddedLen(bytes.size))
        out[0] = (bytes.size ushr 8).toByte()
        out[1] = bytes.size.toByte()
        System.arraycopy(bytes, 0, out, 2, bytes.size)
        return out
    }

    private fun unpad(padded: ByteArray): String {
        val len = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        require(len > 0 && padded.size == 2 + calcPaddedLen(len)) { "Ungültiges Padding" }
        return String(padded, 2, len, Charsets.UTF_8)
    }

    private fun chacha(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        val out = ByteArray(data.size)
        engine.processBytes(data, 0, data.size, out, 0)
        return out
    }

    fun encrypt(plaintext: String, convKey: ByteArray, nonce: ByteArray = randomBytes(32)): String {
        val (ck, cn, hk) = messageKeys(convKey, nonce)
        val ct = chacha(ck, cn, pad(plaintext))
        val mac = hmac(hk, nonce, ct)
        return Base64.getEncoder().encodeToString(byteArrayOf(2) + nonce + ct + mac)
    }

    fun decrypt(payload: String, convKey: ByteArray): String {
        require(payload.isNotEmpty() && payload[0] != '#') { "Unbekannte Version" }
        val data = Base64.getDecoder().decode(payload)
        require(data.size in 99..65603 && data[0].toInt() == 2) { "Ungültige Nutzlast" }
        val nonce = data.copyOfRange(1, 33)
        val ct = data.copyOfRange(33, data.size - 32)
        val mac = data.copyOfRange(data.size - 32, data.size)
        val (ck, cn, hk) = messageKeys(convKey, nonce)
        require(MessageDigest.isEqual(hmac(hk, nonce, ct), mac)) { "MAC ungültig – Nachricht manipuliert" }
        return unpad(chacha(ck, cn, ct))
    }
}
