package io.github.eiji_der_sonnige.conversa.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eiji_der_sonnige.conversa.core.Messenger
import io.github.eiji_der_sonnige.conversa.data.ChatMessage
import kotlinx.coroutines.launch
import java.io.File

/** Spielt immer nur eine Sprachnachricht gleichzeitig ab; entschlüsselte Datei wird danach gelöscht. */
class VoicePlayer(private val m: Messenger) {
    var playingId by mutableStateOf<String?>(null)
        private set
    var loadingId by mutableStateOf<String?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    private var player: MediaPlayer? = null
    private var file: File? = null

    val isPlaying: Boolean get() = playingId != null && !paused

    suspend fun toggle(msg: ChatMessage) {
        val media = msg.media ?: return
        val p = player
        if (playingId == msg.id && p != null) {
            if (p.isPlaying) { p.pause(); paused = true } else { p.start(); paused = false }
            return
        }
        release()
        loadingId = msg.id
        val f = try {
            m.media.loadToTempFile(media)
        } finally {
            loadingId = null
        }
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(f.absolutePath)
            mp.prepare()
        } catch (e: Exception) {
            mp.release()
            f.delete()
            throw e
        }
        mp.setOnCompletionListener { release() }
        mp.start()
        player = mp
        file = f
        playingId = msg.id
        paused = false
        durationMs = mp.duration.toLong().coerceAtLeast(0)
        positionMs = 0
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        val pos = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
        p.seekTo(pos.toInt())
        positionMs = pos
    }

    /** Fortschritt aktualisieren (vom Bildschirm regelmäßig aufgerufen). */
    fun tick() {
        player?.let { positionMs = it.currentPosition.toLong() }
    }

    fun release() {
        player?.release()
        player = null
        file?.delete()
        file = null
        playingId = null
        paused = false
        positionMs = 0
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun VoiceMessage(player: VoicePlayer, msg: ChatMessage, trusted: Boolean, onError: (String) -> Unit) {
    val media = msg.media ?: return
    val scope = rememberCoroutineScope()
    // Wie bei Bildern: Bei Nachrichtenanfragen erst nach Tippen laden (sonst IP-Leck an den Server des Absenders).
    var allowed by remember(media.sha256) { mutableStateOf(trusted) }
    if (!allowed) {
        TextButton({ allowed = true }) { Text("🎤 Sprachnachricht von unbekanntem Absender laden") }
        return
    }
    val active = player.playingId == msg.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (player.loadingId == msg.id) {
            CircularProgressIndicator(Modifier.size(40.dp).padding(8.dp))
        } else {
            IconButton({
                scope.launch {
                    try { player.toggle(msg) } catch (e: Exception) { onError("Sprachnachricht nicht verfügbar") }
                }
            }) {
                Icon(
                    if (active && !player.paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (active && !player.paused) "Pause" else "Abspielen"
                )
            }
        }
        Column {
            val total = if (active && player.durationMs > 0) player.durationMs else media.durationMs
            Slider(
                value = if (active && total > 0) (player.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f,
                onValueChange = { if (active) player.seekTo(it) },
                enabled = active,
                modifier = Modifier.width(170.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (active) "${formatDuration(player.positionMs)} / ${formatDuration(total)}"
                    else if (total > 0) formatDuration(total) else "Sprachnachricht",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
