package io.github.eiji_der_sonnige.conversa.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.eiji_der_sonnige.conversa.MainViewModel
import io.github.eiji_der_sonnige.conversa.Screen
import io.github.eiji_der_sonnige.conversa.core.Messenger
import io.github.eiji_der_sonnige.conversa.crypto.Npub
import io.github.eiji_der_sonnige.conversa.data.ChatMessage
import io.github.eiji_der_sonnige.conversa.data.MsgStatus
import io.github.eiji_der_sonnige.conversa.data.MsgType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainViewModel, m: Messenger, peer: String) {
    val contacts by m.store.contacts.collectAsState()
    val all by m.store.messages.collectAsState()
    val contact = contacts[peer]
    val msgs = all[peer].orEmpty()
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingCallVideo by remember { mutableStateOf<Boolean?>(null) }
    var videoToPlay by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    BackHandler { vm.navigate(Screen.Chats) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendMedia(peer, uri)
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
                    MessageBubble(m, msg, onRetry = { vm.resend(msg) }, onPlay = { videoToPlay = msg })
                }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton({
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }) { Icon(Icons.Default.AttachFile, "Bild/Video senden") }
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f).heightIn(max = 140.dp),
                    placeholder = { Text("Nachricht") }, shape = RoundedCornerShape(24.dp)
                )
                IconButton(
                    {
                        vm.sendText(peer, input)
                        input = ""
                    },
                    enabled = input.isNotBlank()
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
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
    videoToPlay?.let { VideoDialog(m, it) { videoToPlay = null } }
}

@Composable
private fun MessageBubble(m: Messenger, msg: ChatMessage, onRetry: () -> Unit, onPlay: () -> Unit) {
    val mine = msg.fromMe
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                when {
                    msg.media == null -> Text(msg.text)
                    msg.type == MsgType.IMAGE -> EncryptedImage(m, msg)
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
                        when (msg.status) {
                            MsgStatus.SENDING -> Text(" ⏳", style = MaterialTheme.typography.labelSmall)
                            MsgStatus.SENT -> Text(" ✓", style = MaterialTheme.typography.labelSmall)
                            MsgStatus.FAILED -> IconButton(onRetry, Modifier.size(24.dp)) {
                                Icon(Icons.Default.ErrorOutline, "Erneut senden", tint = MaterialTheme.colorScheme.error)
                            }
                            MsgStatus.RECEIVED -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EncryptedImage(m: Messenger, msg: ChatMessage) {
    val media = msg.media ?: return
    var bmp by remember(media.sha256) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(media.sha256) { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
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
