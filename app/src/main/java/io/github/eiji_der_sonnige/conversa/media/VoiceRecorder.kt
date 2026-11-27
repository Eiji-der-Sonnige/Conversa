package io.github.eiji_der_sonnige.conversa.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Nimmt Sprachnachrichten als AAC (M4A) auf: von praktisch allen Geräten und Nostr-Clients abspielbar,
 * rund 360 KB pro Minute. Die Aufnahme liegt nur kurz unverschlüsselt im privaten App-Cache und wird
 * nach dem Senden oder Verwerfen sofort gelöscht.
 */
class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** Laufzeit der aktuellen Aufnahme in ms. */
    fun elapsedMs(): Long = if (recorder != null) SystemClock.elapsedRealtime() - startedAt else 0L

    /** Startet die Aufnahme; [onMaxReached] wird aufgerufen, wenn die Höchstdauer erreicht ist. */
    fun start(onMaxReached: () -> Unit) {
        cancel()
        val dir = File(ctx.cacheDir, "voice_tmp").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // Reste einer abgebrochenen Aufnahme
        val f = File(dir, "rec_${System.nanoTime()}.m4a")
        val r = MediaRecorder(ctx).apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) // mit Echo-/Rauschunterdrückung
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(48_000)
            setMaxDuration(MAX_DURATION_MS.toInt())
            setOutputFile(f.absolutePath)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached()
            }
        }
        try {
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            f.delete()
            throw e
        }
        recorder = r
        file = f
        startedAt = SystemClock.elapsedRealtime()
    }

    /** Beendet die Aufnahme. Liefert Datei und Dauer, oder null, wenn sie zu kurz/leer war. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val f = file
        val elapsed = elapsedMs()
        recorder = null
        file = null
        val ok = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false // stop() direkt nach start(): keine Audiodaten
        } finally {
            r.release()
        }
        if (!ok || f == null || !f.exists() || f.length() == 0L || elapsed < MIN_DURATION_MS) {
            f?.delete()
            return null
        }
        return f to (measureDuration(f) ?: elapsed).coerceAtMost(MAX_DURATION_MS)
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        try { r.stop() } catch (_: RuntimeException) {}
        r.release()
        file?.delete()
        file = null
    }

    private fun measureDuration(f: File): Long? = try {
        MediaPlayer().run {
            setDataSource(f.absolutePath)
            prepare()
            val d = duration.toLong()
            release()
            d.takeIf { it > 0 }
        }
    } catch (e: Exception) {
        Log.w("VoiceRecorder", "Dauer nicht ermittelbar: ${e.message}")
        null
    }

    companion object {
        const val MIME = "audio/mp4"
        const val MAX_DURATION_MS = 5L * 60 * 1000
        private const val MIN_DURATION_MS = 700L
    }
}
