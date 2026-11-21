package io.github.eiji_der_sonnige.conversa.crypto

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 6238 TOTP – kompatibel mit Google Authenticator (SHA1, 6 Stellen, 30 s). */
object Totp {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun newSecret(): String = base32Encode(randomBytes(20))

    fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = ((buffer shl 8) or (b.toInt() and 0xff)) and 0xffff
            bits += 8
            while (bits >= 5) {
                sb.append(B32[(buffer ushr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(B32[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    fun base32Decode(s: String): ByteArray {
        val clean = s.uppercase().filter { it != '=' && !it.isWhitespace() }
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = B32.indexOf(c)
            require(v >= 0) { "Ungültiges Base32" }
            buffer = ((buffer shl 5) or v) and 0xffff
            bits += 5
            if (bits >= 8) {
                out.write((buffer ushr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun code(secret: String, timeMillis: Long = System.currentTimeMillis(), offset: Int = 0): String {
        val counter = timeMillis / 1000 / 30 + offset
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(base32Decode(secret), "HmacSHA1"))
        val h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val o = h[h.size - 1].toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or
            ((h[o + 1].toInt() and 0xff) shl 16) or
            ((h[o + 2].toInt() and 0xff) shl 8) or
            (h[o + 3].toInt() and 0xff)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }

    /** Prüft den Code mit ±30 s Toleranz, zeitkonstanter Vergleich. */
    fun verify(secret: String, input: String): Boolean {
        val c = input.filter(Char::isDigit)
        if (c.length != 6) return false
        var ok = false
        for (off in -1..1) {
            if (MessageDigest.isEqual(code(secret, offset = off).toByteArray(), c.toByteArray())) ok = true
        }
        return ok
    }

    fun uri(secret: String, account: String): String {
        val label = URLEncoder.encode("Conversa:$account", "UTF-8").replace("+", "%20")
        return "otpauth://totp/$label?secret=$secret&issuer=Conversa&algorithm=SHA1&digits=6&period=30"
    }
}
