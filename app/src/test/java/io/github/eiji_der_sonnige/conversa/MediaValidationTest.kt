package io.github.eiji_der_sonnige.conversa

import io.github.eiji_der_sonnige.conversa.data.Media
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaValidationTest {
    private val ok = Media(
        url = "https://nostr.download/" + "a".repeat(64),
        mime = "image/jpeg",
        keyHex = "b".repeat(64),
        nonceHex = "c".repeat(24),
        sha256 = "a".repeat(64),
        size = 1234,
    )

    @Test
    fun valid_media_is_accepted() = assertTrue(ok.isWellFormed())

    @Test
    fun path_traversal_in_hash_is_rejected() {
        assertFalse(ok.copy(sha256 = "../../shared_prefs/x").isWellFormed())
        assertFalse(ok.copy(sha256 = "a".repeat(63) + "/").isWellFormed())
    }

    @Test
    fun insecure_or_malformed_fields_are_rejected() {
        assertFalse(ok.copy(url = "http://example.com/x").isWellFormed())
        assertFalse(ok.copy(url = "file:///data/data/x").isWellFormed())
        assertFalse(ok.copy(keyHex = "b".repeat(62)).isWellFormed())
        assertFalse(ok.copy(nonceHex = "c".repeat(32)).isWellFormed())
        assertFalse(ok.copy(size = -1).isWellFormed())
    }
}
