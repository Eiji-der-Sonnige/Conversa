package io.github.eiji_der_sonnige.conversa

import io.github.eiji_der_sonnige.conversa.crypto.KdfVersion
import io.github.eiji_der_sonnige.conversa.crypto.KeyDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyDerivationTest {
    private val user = "Testnutzer"
    private val pw = "Kanu-Fahrt-7-Wald"

    /** Bestehende Konten dürfen sich nie ändern: Referenzwert mit dem Code von 1.3.0 berechnet. */
    @Test
    fun v1IsUnchanged() {
        assertEquals(
            "617098a9995e1573e3efc95b8cdf73486c28324f763cd5f7f95ab959db440468",
            KeyDerivation.derive(user, pw.toCharArray(), KdfVersion.V1).pubHex
        )
    }

    @Test
    fun v2GivesDifferentIdentityAndIsDeterministic() {
        val a = KeyDerivation.derive(user, pw.toCharArray(), KdfVersion.V2).pubHex
        assertNotEquals(KeyDerivation.derive(user, pw.toCharArray(), KdfVersion.V1).pubHex, a)
        assertEquals(a, KeyDerivation.derive(" testNUTZER ", pw.toCharArray(), KdfVersion.V2).pubHex)
    }

    private fun problem(p: String, u: String = "anna_k") = KeyDerivation.passwordProblem(p.toCharArray(), u)

    @Test
    fun weakPasswordsAreRejected() {
        assertEquals(R.string.pw_min_length, problem("Kurz1!"))
        assertEquals(R.string.pw_classes, problem("nurkleinbuchstaben"))
        assertEquals(R.string.pw_common, problem("Sommer2026!!"))
        assertEquals(R.string.pw_common, problem("P@ssw0rt2026!"))
        assertEquals(R.string.pw_pattern, problem("Qwertz!!x9Yb"))
        assertEquals(R.string.pw_pattern, problem("Abcd1234#xyZ"))
        assertEquals(R.string.pw_pattern, problem("Aaaaa#77bKmq"))
        assertEquals(R.string.pw_contains_username, problem("Anna_K!2026xz", "anna_k"))
    }

    @Test
    fun strongPasswordsAreAccepted() {
        assertNull(problem("Kanu-Fahrt-7-Wald"))
        assertNull(problem("vT9#mQ2!xLp4"))
        // Passphrase ab 20 Zeichen: 2 Zeichenklassen genügen
        assertNull(problem("pferd batterie heftklammer korrekt"))
    }
}
