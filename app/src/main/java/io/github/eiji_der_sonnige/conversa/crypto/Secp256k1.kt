package io.github.eiji_der_sonnige.conversa.crypto

import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger

/**
 * secp256k1 mit BIP-340 Schnorr-Signaturen (Nostr-kompatibel) und ECDH,
 * implementiert auf Basis der Bouncy-Castle-Kurvenarithmetik.
 */
object Secp256k1 {
    private val params = CustomNamedCurves.getByName("secp256k1")
    private val curve = params.curve
    private val G: ECPoint = params.g
    private val N: BigInteger = params.n
    private val P: BigInteger = curve.field.characteristic

    fun isValidPrivKey(key: ByteArray): Boolean {
        if (key.size != 32) return false
        val d = BigInteger(1, key)
        return d.signum() > 0 && d < N
    }

    fun generatePrivKey(): ByteArray {
        while (true) {
            val k = randomBytes(32)
            if (isValidPrivKey(k)) return k
        }
    }

    fun pubKeyXOnly(priv: ByteArray): ByteArray {
        require(isValidPrivKey(priv))
        return G.multiply(BigInteger(1, priv)).normalize().affineXCoord.encoded
    }

    private fun to32(v: BigInteger): ByteArray = BigIntegers.asUnsignedByteArray(32, v)

    private fun taggedHash(tag: String, vararg msgs: ByteArray): ByteArray {
        val th = sha256(tag.toByteArray(Charsets.UTF_8))
        return sha256(th, th, *msgs)
    }

    private fun liftX(x: ByteArray): ECPoint? = try {
        if (BigInteger(1, x) >= P) null
        else curve.decodePoint(byteArrayOf(0x02) + x).normalize()
    } catch (e: Exception) {
        null
    }

    private fun hasEvenY(p: ECPoint) = !p.affineYCoord.toBigInteger().testBit(0)

    fun sign(msg: ByteArray, priv: ByteArray, aux: ByteArray = randomBytes(32)): ByteArray {
        require(msg.size == 32 && isValidPrivKey(priv))
        val d0 = BigInteger(1, priv)
        val pub = G.multiply(d0).normalize()
        val d = if (hasEvenY(pub)) d0 else N.subtract(d0)
        val px = pub.affineXCoord.encoded
        val dBytes = to32(d)
        val auxHash = taggedHash("BIP0340/aux", aux)
        val t = ByteArray(32) { (dBytes[it].toInt() xor auxHash[it].toInt()).toByte() }
        val k0 = BigInteger(1, taggedHash("BIP0340/nonce", t, px, msg)).mod(N)
        check(k0.signum() != 0)
        val r = G.multiply(k0).normalize()
        val k = if (hasEvenY(r)) k0 else N.subtract(k0)
        val rx = r.affineXCoord.encoded
        val e = BigInteger(1, taggedHash("BIP0340/challenge", rx, px, msg)).mod(N)
        return rx + to32(k.add(e.multiply(d)).mod(N))
    }

    fun verify(sig: ByteArray, msg: ByteArray, pubX: ByteArray): Boolean {
        if (sig.size != 64 || msg.size != 32 || pubX.size != 32) return false
        val pub = liftX(pubX) ?: return false
        val rBytes = sig.copyOfRange(0, 32)
        val r = BigInteger(1, rBytes)
        val s = BigInteger(1, sig.copyOfRange(32, 64))
        if (r >= P || s >= N) return false
        val e = BigInteger(1, taggedHash("BIP0340/challenge", rBytes, pubX, msg)).mod(N)
        val rp = ECAlgorithms.sumOfTwoMultiplies(G, s, pub, N.subtract(e)).normalize()
        if (rp.isInfinity || !hasEvenY(rp)) return false
        return rp.affineXCoord.toBigInteger() == r
    }

    /** ECDH: x-Koordinate von priv * pub (unhashed, wie von NIP-44 gefordert). */
    fun ecdhX(priv: ByteArray, pubX: ByteArray): ByteArray {
        val pub = requireNotNull(liftX(pubX)) { "Ungültiger öffentlicher Schlüssel" }
        return pub.multiply(BigInteger(1, priv)).normalize().affineXCoord.encoded
    }
}
