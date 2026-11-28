package io.github.eiji_der_sonnige.conversa.crypto

import io.github.eiji_der_sonnige.conversa.R
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Anonyme Identität: Der private Schlüssel wird ausschließlich aus Benutzername + Passwort
 * abgeleitet (Argon2id, speicherhart). Es gibt keinen Server, keine E-Mail, keine Telefonnummer.
 * Gleiche Zugangsdaten auf einem neuen Gerät = gleiche Identität.
 */
class Identity(val privKey: ByteArray, val username: String) {
    val pubHex: String = Secp256k1.pubKeyXOnly(privKey).toHex()
    val npub: String get() = Npub.encode(pubHex)
}

object KeyDerivation {
    fun normalizeUsername(u: String) = u.trim().lowercase()

    fun derive(username: String, password: CharArray): Identity {
        val user = normalizeUsername(username)
        val salt = sha256("conversa-identity-v1:$user".toByteArray(Charsets.UTF_8))
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(3)
            .withMemoryAsKB(64 * 1024)
            .withParallelism(2)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        var key = ByteArray(32)
        gen.generateBytes(password, key)
        while (!Secp256k1.isValidPrivKey(key)) key = sha256(key)
        return Identity(key, user)
    }

    /** Liefert die Ressourcen-ID einer Fehlermeldung oder null, wenn das Passwort stark genug ist. */
    fun passwordProblem(pw: CharArray): Int? {
        if (pw.size < 12) return R.string.pw_min_length
        val classes = listOf(
            pw.any { it.isLowerCase() }, pw.any { it.isUpperCase() },
            pw.any { it.isDigit() }, pw.any { !it.isLetterOrDigit() }
        ).count { it }
        if (classes < 3) return R.string.pw_classes
        return null
    }
}
