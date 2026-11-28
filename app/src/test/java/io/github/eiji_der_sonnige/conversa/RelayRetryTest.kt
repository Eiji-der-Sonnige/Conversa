package io.github.eiji_der_sonnige.conversa

import io.github.eiji_der_sonnige.conversa.nostr.RelayPool
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ausgefallene Relays: im Vordergrund schnell erneut versuchen, im Hintergrund akkuschonend. */
class RelayRetryTest {
    @Test
    fun foregroundRetriesQuickly() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 10_000L, 10_000L),
            (1..6).map { RelayPool.retryDelayMs(it, foreground = true) })
    }

    @Test
    fun backgroundIsCappedAtOneMinute() {
        assertEquals(60_000L, RelayPool.retryDelayMs(10, foreground = false))
        assertEquals(1_000L, RelayPool.retryDelayMs(1, foreground = false))
    }
}
