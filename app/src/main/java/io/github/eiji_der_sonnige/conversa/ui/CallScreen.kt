package io.github.eiji_der_sonnige.conversa.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.eiji_der_sonnige.conversa.call.CallState
import io.github.eiji_der_sonnige.conversa.core.Messenger
import io.github.eiji_der_sonnige.conversa.crypto.Npub
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallScreen(m: Messenger, state: CallState) {
    val calls = m.calls
    val contacts by m.store.contacts.collectAsState()
    val local by calls.localVideo.collectAsState()
    val remote by calls.remoteVideo.collectAsState()
    val mic by calls.micOn.collectAsState()
    val cam by calls.camOn.collectAsState()
    val speaker by calls.speakerOn.collectAsState()

    // Eine offene Tastatur (z. B. aus dem Chat) würde sonst die Anrufknöpfe verdecken.
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }

    val (peer, video) = when (state) {
        is CallState.Outgoing -> state.peer to state.video
        is CallState.Incoming -> state.peer to state.video
        is CallState.Active -> state.peer to state.video
        CallState.Idle -> return
    }
    val name = contacts[peer]?.name ?: Npub.short(peer)
    val status = when (state) {
        is CallState.Outgoing -> "Klingelt…"
        is CallState.Incoming -> if (video) "Eingehender Videoanruf" else "Eingehender Anruf"
        is CallState.Active -> if (state.connected) "🔒 Verbunden (P2P, verschlüsselt)" else "Verbinde…"
        CallState.Idle -> ""
    }

    val acceptPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res[Manifest.permission.RECORD_AUDIO] == true && (!video || res[Manifest.permission.CAMERA] == true)
        if (ok) calls.accept() else calls.reject()
    }

    BackHandler { /* Anruf bleibt im Vordergrund */ }

    Box(Modifier.fillMaxSize().background(Color(0xFF101418))) {
        if (video && remote != null) {
            VideoRenderer(remote, calls.eglBase, Modifier.fillMaxSize(), mirror = false, overlay = false)
        }
        if (video && local != null) {
            VideoRenderer(
                local, calls.eglBase,
                Modifier.systemBarsPadding().padding(16.dp).size(110.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp)).align(Alignment.TopEnd),
                mirror = true, overlay = true
            )
        }
        Column(
            Modifier.fillMaxWidth().systemBarsPadding().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(status, color = Color.White.copy(alpha = 0.8f))
        }
        Row(
            Modifier.fillMaxWidth().systemBarsPadding().padding(bottom = 40.dp).align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state is CallState.Incoming) {
                RoundButton(Icons.Default.CallEnd, "Ablehnen", Color(0xFFD32F2F)) { calls.reject() }
                RoundButton(Icons.Default.Call, "Annehmen", Color(0xFF2E7D32)) {
                    acceptPerms.launch(
                        if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                        else arrayOf(Manifest.permission.RECORD_AUDIO)
                    )
                }
            } else {
                RoundButton(if (mic) Icons.Default.Mic else Icons.Default.MicOff, "Mikrofon", Color(0x33FFFFFF)) { calls.toggleMic() }
                if (video) {
                    RoundButton(if (cam) Icons.Default.Videocam else Icons.Default.VideocamOff, "Kamera", Color(0x33FFFFFF)) { calls.toggleCamera() }
                    RoundButton(Icons.Default.Cameraswitch, "Kamera wechseln", Color(0x33FFFFFF)) { calls.switchCamera() }
                }
                RoundButton(if (speaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Hearing, "Lautsprecher", Color(0x33FFFFFF)) { calls.toggleSpeaker() }
                RoundButton(Icons.Default.CallEnd, "Auflegen", Color(0xFFD32F2F)) { calls.hangup() }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick, Modifier.size(60.dp), shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White)
    ) { Icon(icon, label) }
}

@Composable
private fun VideoRenderer(track: VideoTrack?, egl: EglBase, modifier: Modifier, mirror: Boolean, overlay: Boolean) {
    var view by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(egl.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                if (overlay) setZOrderMediaOverlay(true)
                setEnableHardwareScaler(true)
                view = this
            }
        },
        modifier = modifier,
        onRelease = { it.release() }
    )
    DisposableEffect(track, view) {
        val v = view
        if (track != null && v != null) {
            try { track.addSink(v) } catch (_: Exception) {}
        }
        onDispose {
            if (track != null && v != null) {
                try { track.removeSink(v) } catch (_: Exception) {}
            }
        }
    }
}
