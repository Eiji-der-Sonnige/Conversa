package io.github.eiji_der_sonnige.conversa.core

import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Schätzt die Abweichung der Geräteuhr anhand der HTTP-"Date"-Header der Relays/Server
 * (über TLS übertragen). Wird für die 2FA-Prüfung genutzt: Authenticator-Apps korrigieren
 * ihre Zeit oft selbst, eine falsch gehende Handy-Uhr würde sonst gültige Codes ablehnen.
 */
object NetworkTime {
    private val offsets = CopyOnWriteArrayList<Long>()

    /** @param serverDate Zeit laut Server (Sekundengenauigkeit), sent/received = lokale Zeitpunkte der Anfrage. */
    fun addSample(serverDate: Date?, sentAtMillis: Long, receivedAtMillis: Long) {
        if (serverDate == null || sentAtMillis <= 0 || receivedAtMillis < sentAtMillis) return
        if (receivedAtMillis - sentAtMillis > 10_000) return // zu langsame Antwort, ungenau
        val localMid = (sentAtMillis + receivedAtMillis) / 2
        offsets.add(serverDate.time + 500 - localMid) // +500 ms: Date-Header ist auf Sekunden abgerundet
        while (offsets.size > 15) offsets.removeAt(0)
    }

    /** Median der gemessenen Abweichungen in Millisekunden (0, solange keine Messung vorliegt). */
    val offsetMillis: Long
        get() {
            val s = offsets.toList().sorted()
            return if (s.isEmpty()) 0L else s[s.size / 2]
        }

    val hasSamples: Boolean get() = offsets.isNotEmpty()

    fun now(): Long = System.currentTimeMillis() + offsetMillis
}
