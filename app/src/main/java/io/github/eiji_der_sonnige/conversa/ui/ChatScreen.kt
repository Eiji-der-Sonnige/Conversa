package io.github.eiji_der_sonnige.conversa.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.eiji_der_sonnige.conversa.MainViewModel
import io.github.eiji_der_sonnige.conversa.Screen
import io.github.eiji_der_sonnige.conversa.call.CallState
import io.github.eiji_der_sonnige.conversa.core.Messenger
import io.github.eiji_der_sonnige.conversa.crypto.Npub
import io.github.eiji_der_sonnige.conversa.data.ChatMessage
import io.github.eiji_der_sonnige.conversa.data.MsgStatus
import io.github.eiji_der_sonnige.conversa.data.MsgType
import io.github.eiji_der_sonnige.conversa.media.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainViewModel, m: Messenger, peer: String) {
    val ctx = LocalContext.current
    val contacts by m.store.contacts.collectAsState()
    val all by m.store.messages.collectAsState()
    val settings by vm.settings.collectAsState()
    val contact = contacts[peer]
    val msgs = all[peer].orEmpty()
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingCallVideo by remember { mutableStateOf<Boolean?>(null) }
    var videoToPlay by remember { mutableStateOf<ChatMessage?>(null) }
    var deleteMsg by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    val recorder = remember { VoiceRecorder(ctx.applicationContext) }
    val player = remember(m) { VoicePlayer(m) }
    var recording by remember { mutableStateOf(false) }
    var recordMs by remember { mutableLongStateOf(0L) }

    fun stopAndSend() {
        val res = recorder.stop()
        recording = false
        if (res == null) vm.showError("Aufnahme zu kurz") else vm.sendVoice(peer, res.first, res.second)
    }

    fun startRecording() {
        if (m.calls.state.value != CallState.Idle) {
            vm.showError("Während eines Anrufs nicht möglich")
            return
        }
        player.release()
        try {
            // Höchstdauer erreicht: automatisch senden, damit nichts verloren geht.
            recorder.start(onMaxReached = { stopAndSend() })
            recording = true
        } catch (e: Exception) {
            vm.showError("Mikrofon nicht verfügbar")
        }
    }

    BackHandler {
        if (recording) { recorder.cancel(); recording = false } else vm.navigate(Screen.Chats)
    }

    // App verlässt den Vordergrund: Aufnahme verwerfen (Android sperrt das Mikrofon im Hintergrund ohnehin)
    // und Wiedergabe beenden.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) {
                recorder.cancel()
                recording = false
                player.release()
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            recorder.cancel()
            player.release()
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            recordMs = recorder.elapsedMs()
            delay(200)
        }
    }
    LaunchedEffect(player.playingId, player.paused) {
        while (player.isPlaying) {
            player.tick()
            delay(200)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendMedia(peer, uri)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else vm.showError("Für Sprachnachrichten wird das Mikrofon benötigt")
    }
    val callPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val video = pendingCallVideo ?: return@rememberLauncherForActivityResult
        val ok = res[Manifest.permission.RECORD_AUDIO] == true && (!video || res[Manifest.permission.CAMERA] == true)
        if (ok) m.calls.startCall(peer, video)
        pendingCallVideo = null
    }
    fun call(video: Boolean) {
        pendingCallVideo = video
        val perms = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
        callPermissions.launch(perms)
    }
    fun mic() {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton({ vm.navigate(Screen.Chats) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                title = {
                    Column {
                        Text(contact?.name ?: "Chat", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("🔒 Ende-zu-Ende-verschlüsselt", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    if (contact?.accepted == true) {
                        IconButton({ call(false) }) { Icon(Icons.Default.Call, "Sprachanruf") }
                        IconButton({ call(true) }) { Icon(Icons.Default.Videocam, "Videoanruf") }
                    }
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Menü") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Umbenennen") }, { menu = false; rename = true })
                        DropdownMenuItem({ Text("Kontakt & Chat löschen") }, { menu = false; confirmDelete = true })
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            if (contact != null && !contact.accepted) {
                Card(Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nachrichtenanfrage von ${Npub.short(peer)}")
                        Text("Anrufe sind erst nach dem Annehmen möglich.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ rename = true }) { Text("Annehmen") }
                            OutlinedButton({ m.removeContact(peer); vm.navigate(Screen.Chats) }) { Text("Ablehnen") }
                        }
                    }
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(msgs, key = { it.id }) { msg ->
                    MessageBubble(
                        m, player, msg,
                        trusted = contact?.accepted == true || msg.fromMe,
                        showRead = settings.readReceipts,
                        onRetry = { vm.resend(msg) },
                        onPlay = { videoToPlay = msg },
                        onCopy = { copyToClipboard(ctx, "Nachricht", msg.text) },
                        onDelete = { deleteMsg = msg },
                        onError = { vm.showError(it) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recording) {
                    IconButton({ recorder.cancel(); recording = false }) {
                        Icon(Icons.Default.Delete, "Aufnahme verwerfen", tint = MaterialTheme.colorScheme.error)
                    }
                    Icon(Icons.Default.FiberManualRecord, null, Modifier.size(14.dp), tint = Color(0xFFE53935))
                    Text(
                        "  Aufnahme ${formatDuration(recordMs)}  (max. ${VoiceRecorder.MAX_DURATION_MS / 60_000} Min.)",
                        Modifier.weight(1f)
                    )
                    IconButton({ stopAndSend() }) { Icon(Icons.AutoMirrored.Filled.Send, "Sprachnachricht senden") }
                } else {
                    IconButton({
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) { Icon(Icons.Default.AttachFile, "Bild/Video senden") }
                    OutlinedTextField(
                        input, { input = it }, Modifier.weight(1f).heightIn(max = 140.dp),
                        placeholder = { Text("Nachricht") }, shape = RoundedCornerShape(24.dp)
                    )
                    if (input.isBlank()) {
                        IconButton({ mic() }) { Icon(Icons.Default.Mic, "Sprachnachricht aufnehmen") }
                    } else {
                        IconButton({
                            vm.sendText(peer, input)
                            input = ""
                        }) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
                    }
                }
            }
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(if (contact?.accepted == true) contact.name else "") }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(if (contact?.accepted == true) "Umbenennen" else "Kontakt annehmen") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton({ m.acceptContact(peer, name.trim()); rename = false }) { Text("Speichern") }
            },
            dismissButton = { TextButton({ rename = false }) { Text("Abbrechen") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Löschen?") },
            text = { Text("Kontakt und lokaler Verlauf werden entfernt.") },
            confirmButton = {
                TextButton({ confirmDelete = false; m.removeContact(peer); vm.navigate(Screen.Chats) }) { Text("Löschen") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
    deleteMsg?.let { msg ->
        val canEveryone = msg.fromMe && msg.status.rank >= MsgStatus.SENT.rank
        AlertDialog(
            onDismissRequest = { deleteMsg = null },
            title = { Text("Nachricht löschen?") },
            text = {
                Text(
                    if (canEveryone) "„Für alle löschen“ entfernt die Nachricht auch beim Empfänger (ab Conversa 1.3). " +
                        "Hat er sie schon gesehen, kann er sie trotzdem kopiert oder fotografiert haben."
                    else "Die Nachricht wird auf deinen Geräten entfernt. Beim Gesprächspartner bleibt sie erhalten."
                )
            },
            confirmButton = {
                Row {
                    if (canEveryone) {
                        TextButton({ vm.deleteMessages(peer, listOf(msg), forEveryone = true); deleteMsg = null }) {
                            Text("Für alle löschen")
                        }
                    }
                    TextButton({ vm.deleteMessages(peer, listOf(msg), forEveryone = false); deleteMsg = null }) {
                        Text("Für mich löschen")
                    }
                }
            },
            dismissButton = { TextButton({ deleteMsg = null }) { Text("Abbrechen") } }
        )
    }
    videoToPlay?.let { VideoDialog(m, it) { videoToPlay = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: Messenger,
    player: VoicePlayer,
    msg: ChatMessage,
    trusted: Boolean,
    showRead: Boolean,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onError: (String) -> Unit,
) {
    val mine = msg.fromMe
    var actions by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box {
            Surface(
                color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = { actions = true }, onLongClickLabel = "Optionen")
            ) {
                Column(Modifier.padding(10.dp)) {
                    when {
                        msg.media == null -> Text(msg.text)
                        msg.type == MsgType.IMAGE -> EncryptedImage(m, msg, trusted)
                        msg.type == MsgType.AUDIO -> VoiceMessage(player, msg, trusted, onError)
                        msg.type == MsgType.VIDEO -> Row(
                            Modifier.clickable(onClick = onPlay).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircle, null, Modifier.size(40.dp))
                            Text("  Video abspielen")
                        }
                        else -> Text("📎 Datei (${msg.media.mime})")
                    }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(msg.createdAt), style = MaterialTheme.typography.labelSmall)
                        if (mine) {
                            Spacer(Modifier.width(4.dp))
                            StatusIcon(msg.status, showRead, onRetry)
                        }
                    }
                }
            }
            DropdownMenu(actions, { actions = false }) {
                if (msg.type == MsgType.TEXT && msg.media == null) {
                    DropdownMenuItem({ Text("Text kopieren") }, { actions = false; onCopy() })
                }
                DropdownMenuItem({ Text("Löschen …") }, { actions = false; onDelete() })
            }
        }
    }
}

/** ⏱ wird gesendet · ✓ übermittelt (von einem Relay angenommen) · ✓✓ gelesen · ⚠ fehlgeschlagen. */
@Composable
private fun StatusIcon(status: MsgStatus, showRead: Boolean, onRetry: () -> Unit) {
    val small = Modifier.size(16.dp)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when (status) {
        MsgStatus.SENDING -> Icon(Icons.Default.Schedule, "Wird gesendet", small, tint = tint)
        MsgStatus.SENT, MsgStatus.RECEIVED -> Icon(Icons.Default.Done, "Übermittelt", small, tint = tint)
        MsgStatus.READ ->
            if (showRead) Icon(Icons.Default.DoneAll, "Gelesen", small, tint = Color(0xFF2196F3))
            else Icon(Icons.Default.Done, "Übermittelt", small, tint = tint)
        MsgStatus.FAILED -> IconButton(onRetry, Modifier.size(24.dp)) {
            Icon(Icons.Default.ErrorOutline, "Erneut senden", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EncryptedImage(m: Messenger, msg: ChatMessage, trusted: Boolean) {
    val media = msg.media ?: return
    var bmp by remember(media.sha256) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(media.sha256) { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    // Bei Nachrichtenanfragen erst nach ausdrücklichem Tippen laden: Der Link stammt vom Absender,
    // und schon der Abruf würde dem dortigen Server die eigene IP-Adresse verraten.
    var allowed by remember(media.sha256) { mutableStateOf(trusted) }
    if (!allowed) {
        TextButton({ allowed = true }) { Text("🖼 Bild von unbekanntem Absender laden") }
        return
    }
    LaunchedEffect(media.sha256) {
        try {
            val bytes = m.media.load(media)
            bmp = withContext(Dispatchers.Default) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                var sample = 1
                while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }
            if (bmp == null) failed = true
        } catch (e: Exception) {
            failed = true
        }
    }
    val b = bmp
    when {
        b != null -> Image(
            b, "Bild",
            Modifier.widthIn(max = 260.dp).heightIn(max = 320.dp).clickable { fullscreen = true },
            contentScale = ContentScale.Fit
        )
        failed -> Text("⚠ Bild nicht verfügbar")
        else -> CircularProgressIndicator(Modifier.size(32.dp).padding(4.dp))
    }
    if (fullscreen && b != null) {
        Dialog({ fullscreen = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { fullscreen = false }, contentAlignment = Alignment.Center) {
                Image(b, "Bild", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable
private fun VideoDialog(m: Messenger, msg: ChatMessage, onDismiss: () -> Unit) {
    val media = msg.media ?: return
    var file by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(media.sha256) {
        try { file = m.media.loadToTempFile(media) } catch (e: Exception) { failed = true }
    }
    Dialog({ file?.delete(); onDismiss() }, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val f = file
            when {
                f != null -> AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val mc = MediaController(ctx)
                            mc.setAnchorView(this)
                            setMediaController(mc)
                            setVideoPath(f.absolutePath)
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    onRelease = { it.stopPlayback() }
                )
                failed -> Text("⚠ Video nicht verfügbar", color = Color.White)
                else -> CircularProgressIndicator()
            }
        }
    }
}
