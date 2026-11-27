package io.github.eiji_der_sonnige.conversa

import io.github.eiji_der_sonnige.conversa.data.ChatMessage
import io.github.eiji_der_sonnige.conversa.data.Media
import io.github.eiji_der_sonnige.conversa.data.MsgStatus
import io.github.eiji_der_sonnige.conversa.data.MsgType
import io.github.eiji_der_sonnige.conversa.data.mediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageModelTest {
    @Test
    fun status_is_only_upgraded() {
        // Eine erneut synchronisierte Kopie (SENT) darf „gelesen“ nicht zurücksetzen.
        assertEquals(MsgStatus.READ, MsgStatus.READ.merge(MsgStatus.SENT))
        assertEquals(MsgStatus.READ, MsgStatus.SENT.merge(MsgStatus.READ))
        assertEquals(MsgStatus.SENT, MsgStatus.FAILED.merge(MsgStatus.SENT)) // späte Relay-Bestätigung
        assertEquals(MsgStatus.SENDING, MsgStatus.FAILED.merge(MsgStatus.SENDING)) // erneut senden
        assertEquals(MsgStatus.READ, MsgStatus.READ.merge(MsgStatus.RECEIVED))
    }

    @Test
    fun media_types_from_mime() {
        assertEquals(MsgType.AUDIO, mediaType("audio/mp4"))
        assertEquals(MsgType.IMAGE, mediaType("image/png"))
        assertEquals(MsgType.VIDEO, mediaType("video/mp4"))
        assertEquals(MsgType.FILE, mediaType("application/pdf"))
    }

    private val voice = Media(
        "https://nostr.download/" + "a".repeat(64), "audio/mp4", "b".repeat(64), "c".repeat(24),
        "a".repeat(64), 1000, durationMs = 12_345
    )

    @Test
    fun voice_message_roundtrip_keeps_duration() {
        val m = ChatMessage("d".repeat(64), "e".repeat(64), true, MsgType.AUDIO, "", voice, 1_700_000_000, MsgStatus.READ)
        val back = ChatMessage.fromJson(m.toJson())
        assertEquals(m, back)
        assertEquals(12_345L, back.media!!.durationMs)
    }

    @Test
    fun absurd_duration_is_rejected() {
        assertTrue(voice.isWellFormed())
        assertFalse(voice.copy(durationMs = -1).isWellFormed())
        assertFalse(voice.copy(durationMs = Long.MAX_VALUE).isWellFormed())
    }
}
