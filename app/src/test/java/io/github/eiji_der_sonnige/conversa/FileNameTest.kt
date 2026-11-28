package io.github.eiji_der_sonnige.conversa

import io.github.eiji_der_sonnige.conversa.data.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dateinamen stammen vom Absender und werden vor Anzeige und Speichern bereinigt. */
class FileNameTest {
    @Test
    fun pathsAreStripped() {
        assertEquals("passwd", Media.cleanName("../../etc/passwd"))
        assertEquals("boot.ini", Media.cleanName("C:\\Windows\\..\\boot.ini"))
    }

    @Test
    fun hiddenAndControlCharactersAreRemoved() {
        // U+202E (Rechts-nach-links-Umschaltung) lässt "rechnung‮fdp.exe" wie "rechnungexe.pdf" aussehen
        assertEquals("rechnungfdp.exe", Media.cleanName("rechnung\u202Efdp.exe"))
        assertEquals("ab", Media.cleanName("a\u0000\nb"))
        assertEquals("bashrc", Media.cleanName(".bashrc"))
    }

    @Test
    fun lengthIsLimited() {
        assertTrue(Media.cleanName("x".repeat(500)).length <= Media.MAX_NAME)
    }
}
