package io.github.eiji_der_sonnige.conversa.ui

import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import io.github.eiji_der_sonnige.conversa.data.gidOf
import io.github.eiji_der_sonnige.conversa.data.isGroupChat
import androidx.compose.material.icons.filled.Groups
import io.github.eiji_der_sonnige.conversa.R
import androidx.compose.ui.res.stringResource
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
    val aliases by m.store.aliases.collectAsState()
    val groups by m.store.groups.collectAsState()
    val isGroup = isGroupChat(peer)
    val group = if (isGroup) groups[gidOf(peer)] else null
    val displayName = aliases[peer] ?: group?.state?.name ?: contact?.name
    val canWrite = if (isGroup) group != null && group.accepted && !group.removed else true
    var confirmJoin by remember { mutableStateOf(false) }
    // Gruppe wurde gelöscht oder verlassen, während der Chat offen war
    if (isGroup && group == null) LaunchedEffect(Unit) { vm.navigate(Screen.Chats) }
    val youLabel = stringResource(R.string.you)
    fun memberName(pub: String) = if (pub == m.identity.pubHex) youLabel
        else aliases[pub] ?: contacts[pub]?.name ?: Npub.short(pub)
    val msgs = all[peer].orEmpty()
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
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
        if (res == null) vm.showError(ctx.getString(R.string.rec_too_short)) else vm.sendVoice(peer, res.first, res.second)
    }

    fun startRecording() {
        if (m.calls.state.value != CallState.Idle) {
            vm.showError(ctx.getString(R.string.not_during_call))
            return
        }
        player.release()
        try {
            // Höchstdauer erreicht: automatisch senden, damit nichts verloren geht.
            recorder.start(onMaxReached = { stopAndSend() })
            recording = true
        } catch (e: Exception) {
            vm.showError(ctx.getString(R.string.mic_unavailable))
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
    // Beliebige Dateien über die Android-Dateiauswahl (kein Speicherzugriff nötig)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.sendMedia(peer, uri)
    }
    var attachMenu by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else vm.showError(ctx.getString(R.string.mic_needed))
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
                    IconButton({ vm.navigate(Screen.Chats) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    Column {
                        Text(displayName ?: stringResource(R.string.chat), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (group != null) {
                            val n = group.state.members.size
                            Text(LocalContext.current.resources.getQuantityString(R.plurals.member_count, n, n) +
                                " · " + stringResource(R.string.e2e_encrypted), style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text(stringResource(R.string.e2e_encrypted), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    if (contact?.accepted == true) {
                        IconButton({ call(false) }) { Icon(Icons.Default.Call, stringResource(R.string.voice_call)) }
                        IconButton({ call(true) }) { Icon(Icons.Default.Videocam, stringResource(R.string.video_call)) }
                    }
                    if (group != null) {
                        IconButton({ vm.navigate(Screen.GroupInfo(group.gid)) }) {
                            Icon(Icons.Default.Groups, stringResource(R.string.group_info))
                        }
                    } else {
                        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.menu)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menu = false; rename = true })
                            DropdownMenuItem({ Text(stringResource(R.string.delete_contact_chat)) }, { menu = false; confirmDelete = true })
                            DropdownMenuItem({ Text(stringResource(R.string.block)) }, { menu = false; confirmBlock = true })
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            if (contact != null && !contact.accepted) {
                Card(Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.request_from, Npub.short(peer)))
                        Text(stringResource(R.string.calls_after_accept), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ rename = true }) { Text(stringResource(R.string.accept)) }
                            OutlinedButton({ m.removeContact(peer); vm.navigate(Screen.Chats) }) { Text(stringResource(R.string.reject)) }
                            TextButton({ confirmBlock = true }) { Text(stringResource(R.string.block)) }
                        }
                    }
                }
            }
            if (group != null && !group.accepted && !group.removed) {
                Card(Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.group_invite_from, memberName(group.state.signer), group.state.name))
                        Text(stringResource(R.string.group_invite_members, group.state.members.joinToString { memberName(it) }),
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ confirmJoin = true }) { Text(stringResource(R.string.group_join)) }
                            OutlinedButton({ vm.groupAction { it.leave(group.gid) }; vm.navigate(Screen.Chats) }) {
                                Text(stringResource(R.string.reject))
                            }
                        }
                    }
                }
            } else if (group != null && group.removed) {
                Card(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(stringResource(R.string.group_removed_text), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
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
                        trusted = (if (isGroup) group?.accepted == true else contact?.accepted == true) || msg.fromMe,
                        senderName = if (isGroup && !msg.fromMe) memberName(msg.sender) else null,
                        showRead = settings.readReceipts,
                        onRetry = { vm.resend(msg) },
                        onPlay = { videoToPlay = msg },
                        onCopy = { copyToClipboard(ctx, ctx.getString(R.string.message_hint), msg.text) },
                        onDelete = { deleteMsg = msg },
                        onError = { vm.showError(it) },
                    )
                }
            }
            if (canWrite) Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recording) {
                    IconButton({ recorder.cancel(); recording = false }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.discard_recording), tint = MaterialTheme.colorScheme.error)
                    }
                    Icon(Icons.Default.FiberManualRecord, null, Modifier.size(14.dp), tint = Color(0xFFE53935))
                    Text(
                        stringResource(R.string.recording_status, formatDuration(recordMs), VoiceRecorder.MAX_DURATION_MS / 60_000),
                        Modifier.weight(1f)
                    )
                    IconButton({ stopAndSend() }) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send_voice)) }
                } else {
                    Box {
                        IconButton({ attachMenu = true }) { Icon(Icons.Default.AttachFile, stringResource(R.string.attach)) }
                        DropdownMenu(attachMenu, { attachMenu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.attach_media)) }, {
                                attachMenu = false
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            })
                            DropdownMenuItem({ Text(stringResource(R.string.send_file)) }, {
                                attachMenu = false
                                filePicker.launch(arrayOf("*/*"))
                            })
                        }
                    }
                    OutlinedTextField(
                        input, { input = it }, Modifier.weight(1f).heightIn(max = 140.dp),
                        placeholder = { Text(stringResource(R.string.message_hint)) }, shape = RoundedCornerShape(24.dp)
                    )
                    if (input.isBlank()) {
                        IconButton({ mic() }) { Icon(Icons.Default.Mic, stringResource(R.string.record_voice)) }
                    } else {
                        IconButton({
                            vm.sendText(peer, input)
                            input = ""
                        }) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) }
                    }
                }
            }
        }
    }

    if (rename && contact?.accepted == true) {
        RenameDialog(displayName ?: "", onDismiss = { rename = false }) { m.renameLocal(peer, it); rename = false }
    } else if (rename) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(if (contact?.accepted == true) stringResource(R.string.rename) else stringResource(R.string.accept_contact)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true) },
            confirmButton = {
                TextButton({ m.acceptContact(peer, name.trim()); rename = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton({ rename = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_q)) },
            text = { Text(stringResource(R.string.delete_contact_text)) },
            confirmButton = {
                TextButton({ confirmDelete = false; m.removeContact(peer); vm.navigate(Screen.Chats) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text(stringResource(R.string.block_q)) },
            text = {
                Text(stringResource(R.string.block_text))
            },
            confirmButton = {
                TextButton({ confirmBlock = false; m.block(peer); vm.navigate(Screen.Chats) }) { Text(stringResource(R.string.block)) }
            },
            dismissButton = { TextButton({ confirmBlock = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    deleteMsg?.let { msg ->
        // In Gruppen nur „für mich“: Die Inhalte für alle löschen kann nur ein Admin (die ganze Gruppe).
        val canEveryone = !isGroup && msg.fromMe && msg.status.rank >= MsgStatus.SENT.rank
        AlertDialog(
            onDismissRequest = { deleteMsg = null },
            title = { Text(stringResource(R.string.delete_msg_q)) },
            text = {
                Text(
                    if (canEveryone) stringResource(R.string.delete_everyone_text)
                    else stringResource(R.string.delete_me_text)
                )
            },
            confirmButton = {
                Row {
                    if (canEveryone) {
                        TextButton({ vm.deleteMessages(peer, listOf(msg), forEveryone = true); deleteMsg = null }) {
                            Text(stringResource(R.string.delete_for_everyone))
                        }
                    }
                    TextButton({ vm.deleteMessages(peer, listOf(msg), forEveryone = false); deleteMsg = null }) {
                        Text(stringResource(R.string.delete_for_me))
                    }
                }
            },
            dismissButton = { TextButton({ deleteMsg = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (confirmJoin && group != null) {
        AlertDialog(
            onDismissRequest = { confirmJoin = false },
            title = { Text(stringResource(R.string.group_join_q, group.state.name)) },
            text = { Text(stringResource(R.string.group_join_notice)) },
            confirmButton = {
                TextButton({ confirmJoin = false; vm.groupAction { it.accept(group.gid) } }) { Text(stringResource(R.string.group_join_confirm)) }
            },
            dismissButton = { TextButton({ confirmJoin = false }) { Text(stringResource(R.string.cancel)) } }
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
    senderName: String?,
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
                    .combinedClickable(onClick = {}, onLongClick = { actions = true }, onLongClickLabel = stringResource(R.string.options))
            ) {
                Column(Modifier.padding(10.dp)) {
                    senderName?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    when {
                        msg.media == null -> Text(msg.text)
                        msg.type == MsgType.IMAGE -> EncryptedImage(m, msg, trusted)
                        msg.type == MsgType.AUDIO -> VoiceMessage(player, msg, trusted, onError)
                        msg.type == MsgType.VIDEO -> Row(
                            Modifier.clickable(onClick = onPlay).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircle, null, Modifier.size(40.dp))
                            Text(stringResource(R.string.play_video))
                        }
                        else -> FileMessage(m, msg, trusted, onError)
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
                    DropdownMenuItem({ Text(stringResource(R.string.copy_text)) }, { actions = false; onCopy() })
                }
                DropdownMenuItem({ Text(stringResource(R.string.delete_ellipsis)) }, { actions = false; onDelete() })
            }
        }
    }
}

/**
 * Datei: wird nie automatisch geöffnet oder ausgeführt, nur auf Wunsch entschlüsselt und an einem selbst gewählten
 * Ort gespeichert (Android-Dateiauswahl, keine Speicherberechtigung nötig).
 */
@Composable
private fun FileMessage(m: Messenger, msg: ChatMessage, trusted: Boolean, onError: (String) -> Unit) {
    val media = msg.media ?: return
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val failedText = stringResource(R.string.file_save_failed)
    val savedText = stringResource(R.string.file_saved)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(media.mime.ifBlank { "application/octet-stream" })) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val bytes = m.media.load(media)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw java.io.IOException()
                }
                onError(savedText)
            } catch (e: Exception) {
                onError(failedText)
            } finally {
                busy = false
            }
        }
    }
    val name = media.name.ifEmpty { stringResource(R.string.file_unnamed) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.InsertDriveFile, null, Modifier.size(36.dp))
        Column(Modifier.padding(horizontal = 8.dp).widthIn(max = 170.dp)) {
            Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(android.text.format.Formatter.formatShortFileSize(ctx, media.size) + " · " + media.mime.take(40),
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        else IconButton({ if (trusted && !msg.fromMe) confirm = true else if (msg.fromMe) saver.launch(name) else confirm = true }) {
            Icon(Icons.Default.Download, stringResource(R.string.file_save))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.file_save_q)) },
            text = { Text(stringResource(if (trusted) R.string.file_save_warning else R.string.file_save_warning_unknown)) },
            confirmButton = { TextButton({ confirm = false; saver.launch(name) }) { Text(stringResource(R.string.file_save)) } },
            dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** ⏱ wird gesendet · ✓ übermittelt (von einem Relay angenommen) · ✓✓ gelesen · ⚠ fehlgeschlagen. */
@Composable
private fun StatusIcon(status: MsgStatus, showRead: Boolean, onRetry: () -> Unit) {
    val small = Modifier.size(16.dp)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when (status) {
        MsgStatus.SENDING -> Icon(Icons.Default.Schedule, stringResource(R.string.status_sending), small, tint = tint)
        MsgStatus.SENT, MsgStatus.RECEIVED -> Icon(Icons.Default.Done, stringResource(R.string.status_sent), small, tint = tint)
        MsgStatus.READ ->
            if (showRead) Icon(Icons.Default.DoneAll, stringResource(R.string.status_read), small, tint = Color(0xFF2196F3))
            else Icon(Icons.Default.Done, stringResource(R.string.status_sent), small, tint = tint)
        MsgStatus.FAILED -> IconButton(onRetry, Modifier.size(24.dp)) {
            Icon(Icons.Default.ErrorOutline, stringResource(R.string.resend), tint = MaterialTheme.colorScheme.error)
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
        TextButton({ allowed = true }) { Text(stringResource(R.string.load_image_unknown)) }
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
            b, stringResource(R.string.image),
            Modifier.widthIn(max = 260.dp).heightIn(max = 320.dp).clickable { fullscreen = true },
            contentScale = ContentScale.Fit
        )
        failed -> Text(stringResource(R.string.image_unavailable))
        else -> CircularProgressIndicator(Modifier.size(32.dp).padding(4.dp))
    }
    if (fullscreen && b != null) {
        Dialog({ fullscreen = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { fullscreen = false }, contentAlignment = Alignment.Center) {
                Image(b, stringResource(R.string.image), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
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
                failed -> Text(stringResource(R.string.video_unavailable), color = Color.White)
                else -> CircularProgressIndicator()
            }
        }
    }
}
