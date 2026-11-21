package io.github.eiji_der_sonnige.conversa.crypto

import java.security.MessageDigest
import java.security.SecureRandom

object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0f])
        }
        return sb.toString()
    }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Ungültige Hex-Länge" }
        return ByteArray(hex.length / 2) { i ->
            val hi = Character.digit(hex[2 * i], 16)
            val lo = Character.digit(hex[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "Ungültiges Hex-Zeichen" }
            ((hi shl 4) or lo).toByte()
        }
    }

    fun isHex(s: String, length: Int) = s.length == length && s.all { it in DIGITS }
}

val secureRandom = SecureRandom()

fun randomBytes(n: Int): ByteArray = ByteArray(n).also { secureRandom.nextBytes(it) }

fun sha256(vararg parts: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    parts.forEach { md.update(it) }
    return md.digest()
}

fun ByteArray.toHex(): String = Hex.encode(this)
fun String.hexToBytes(): ByteArray = Hex.decode(this)
