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

/**
 * Verfahren der Schlüsselableitung. Der öffentliche Schlüssel ist öffentlich – wer den Benutzernamen kennt oder
 * errät, kann Passwörter offline durchprobieren. Höhere Kosten machen jeden einzelnen Versuch teurer.
 *  - V1 (bis 1.3): Argon2id, 64 MB, 3 Durchläufe. Bestehende Konten bleiben dabei, sonst wäre ihre Identität weg.
 *  - V2 (ab 1.4, für neue Konten): Argon2id, 128 MB, 4 Durchläufe – etwa 2,7-mal so teuer pro Versuch.
 */
enum class KdfVersion(val tag: String, val memoryKb: Int, val iterations: Int) {
    V1("conversa-identity-v1", 64 * 1024, 3),
    V2("conversa-identity-v2", 128 * 1024, 4),
}

object KeyDerivation {
    fun normalizeUsername(u: String) = u.trim().lowercase()

    fun derive(username: String, password: CharArray, version: KdfVersion = KdfVersion.V1): Identity {
        val user = normalizeUsername(username)
        val salt = sha256("${version.tag}:$user".toByteArray(Charsets.UTF_8))
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withIterations(version.iterations)
            .withMemoryAsKB(version.memoryKb)
            .withParallelism(2)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        var key = ByteArray(32)
        gen.generateBytes(password, key)
        while (!Secp256k1.isValidPrivKey(key)) key = sha256(key)
        return Identity(key, user)
    }

    /**
     * Prüft ein neues Passwort. Liefert die Ressourcen-ID einer Fehlermeldung oder null, wenn es stark genug ist.
     * Neben Länge und Zeichenklassen werden typische, leicht zu erratende Muster abgelehnt: der Benutzername im
     * Passwort, ein gängiges Wort mit angehängten Ziffern/Zeichen („Sommer2026!!“), Tastatur- und Zahlenfolgen
     * sowie Wiederholungen. Lange Passphrasen (ab 20 Zeichen) brauchen nur 2 Zeichenklassen.
     */
    fun passwordProblem(pw: CharArray, username: String = ""): Int? {
        val s = String(pw)
        if (pw.size < 12) return R.string.pw_min_length
        val classes = listOf(
            pw.any { it.isLowerCase() }, pw.any { it.isUpperCase() },
            pw.any { it.isDigit() }, pw.any { !it.isLetterOrDigit() }
        ).count { it }
        if (classes < (if (pw.size >= 20) 2 else 3)) return R.string.pw_classes
        val lower = s.lowercase()
        val user = normalizeUsername(username)
        if (user.length >= 3 && lower.contains(user)) return R.string.pw_contains_username
        if (Regex("(.)\\1{3,}").containsMatchIn(lower)) return R.string.pw_pattern
        if (SEQUENCES.any { seq -> (0..seq.length - 4).any { lower.contains(seq.substring(it, it + 4)) } }) {
            return R.string.pw_pattern
        }
        // Buchstabenkern ohne Ziffern/Zeichen, Leetspeak zurückübersetzt: „P@ssw0rt2026!“ -> „passwort“
        val letters = lower.filter { it.isLetter() }
        val word = lower.dropWhile { !it.isLetter() }.dropLastWhile { !it.isLetter() }
            .map { LEET[it] ?: it }.filter { it.isLetter() }.joinToString("")
        if (letters.length < 5 && word.length < 5 && pw.size < 16) return R.string.pw_pattern
        val common = { core: String -> COMMON.any { core == it || (core.startsWith(it) && core.length - it.length <= 2) } }
        if (common(letters) || common(word)) return R.string.pw_common
        return null
    }

    private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's', '!' to 'i')

    /** Tastatur- und Zahlenreihen (vorwärts und rückwärts). */
    private val SEQUENCES = listOf("abcdefghijklmnopqrstuvwxyz", "0123456789", "qwertzuiopü", "qwertyuiop", "asdfghjklöä",
        "yxcvbnm", "zxcvbnm", "1qaz2wsx").flatMap { listOf(it, it.reversed()) }

    /** Häufige Wörter in Passwörtern (Deutsch/Englisch), Kleinbuchstaben. */
    private val COMMON = listOf(
        "passwort", "password", "kennwort", "geheim", "secret", "hallo", "hello", "willkommen", "welcome", "admin",
        "sommer", "summer", "winter", "fruehling", "frühling", "herbst", "spring", "autumn", "january", "januar",
        "februar", "march", "maerz", "april", "juni", "juli", "august", "september", "oktober", "october",
        "november", "dezember", "december", "monday", "montag", "freitag", "sonntag", "sunday", "schatz", "schatzi",
        "liebe", "love", "iloveyou", "ichliebedich", "baby", "engel", "angel", "sonne", "mond", "stern", "blume",
        "katze", "hund", "dragon", "drache", "monkey", "affe", "tiger", "pferd", "fussball", "fußball", "football",
        "soccer", "baseball", "bayern", "dortmund", "schalke", "deutschland", "germany", "berlin", "hamburg",
        "muenchen", "münchen", "koeln", "köln", "master", "superman", "batman", "pokemon", "minecraft", "starwars",
        "princess", "prinzessin", "computer", "internet", "samsung", "iphone", "android", "google", "conversa",
        "freedom", "freiheit", "letmein", "trustno", "qwertz", "qwerty", "test", "testtest", "abc", "familie",
        "family", "michael", "thomas", "andreas", "stefan", "daniel", "alexander", "julia", "anna", "maria",
        "sabine", "nicole", "jennifer", "jessica", "lisa", "laura", "lukas", "leon", "sophie", "marie",
    )
}
