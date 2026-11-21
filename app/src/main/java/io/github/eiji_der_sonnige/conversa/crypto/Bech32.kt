package io.github.eiji_der_sonnige.conversa.crypto

/** Bech32 (BIP-173), verwendet für die öffentliche Kontakt-ID "npub1…". */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GEN = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val b = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0..4) if ((b ushr i) and 1 == 1) chk = chk xor GEN[i]
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray {
        val r = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) {
            r[i] = hrp[i].code ushr 5
            r[i + hrp.length + 1] = hrp[i].code and 31
        }
        return r
    }

    private fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): IntArray {
        var acc = 0
        var bits = 0
        val out = ArrayList<Int>()
        val maxv = (1 shl to) - 1
        val maxAcc = (1 shl (from + to - 1)) - 1
        for (v in data) {
            require(v ushr from == 0) { "Ungültiger Wert" }
            acc = ((acc shl from) or v) and maxAcc
            bits += from
            while (bits >= to) {
                bits -= to
                out.add((acc ushr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) out.add((acc shl (to - bits)) and maxv)
        } else {
            require(bits < from && ((acc shl (to - bits)) and maxv) == 0) { "Ungültiges Padding" }
        }
        return out.toIntArray()
    }

    fun encode(hrp: String, data: ByteArray): String {
        val d5 = convertBits(IntArray(data.size) { data[it].toInt() and 0xff }, 8, 5, true)
        val pm = polymod(hrpExpand(hrp) + d5 + IntArray(6)) xor 1
        val sb = StringBuilder(hrp).append('1')
        d5.forEach { sb.append(CHARSET[it]) }
        for (i in 0 until 6) sb.append(CHARSET[(pm ushr (5 * (5 - i))) and 31])
        return sb.toString()
    }

    fun decode(input: String): Pair<String, ByteArray> {
        val s = input.trim().lowercase()
        val pos = s.lastIndexOf('1')
        require(pos >= 1 && pos + 7 <= s.length) { "Ungültiges Bech32" }
        val hrp = s.substring(0, pos)
        val values = s.substring(pos + 1).map { c ->
            CHARSET.indexOf(c).also { require(it >= 0) { "Ungültiges Zeichen" } }
        }.toIntArray()
        require(polymod(hrpExpand(hrp) + values) == 1) { "Ungültige Prüfsumme" }
        val d8 = convertBits(values.copyOfRange(0, values.size - 6), 5, 8, false)
        return hrp to ByteArray(d8.size) { d8[it].toByte() }
    }
}

object Npub {
    fun encode(pubHex: String): String = Bech32.encode("npub", pubHex.hexToBytes())

    /** Akzeptiert "npub1…", "nostr:npub1…" oder 64-stelligen Hex-Schlüssel. Liefert Hex oder null. */
    fun parse(input: String): String? {
        val s = input.trim().removePrefix("nostr:")
        if (Hex.isHex(s.lowercase(), 64)) return s.lowercase()
        return try {
            val (hrp, data) = Bech32.decode(s)
            if (hrp == "npub" && data.size == 32) data.toHex() else null
        } catch (e: Exception) {
            null
        }
    }

    fun short(pubHex: String): String = encode(pubHex).let { it.take(12) + "…" + it.takeLast(6) }
}
