package io.github.eiji_der_sonnige.conversa.ui

import io.github.eiji_der_sonnige.conversa.crypto.Npub
import io.github.eiji_der_sonnige.conversa.data.groupChatKey
import io.github.eiji_der_sonnige.conversa.data.gidOf
import io.github.eiji_der_sonnige.conversa.data.isGroupChat
import io.github.eiji_der_sonnige.conversa.data.ChatMessage
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import io.github.eiji_der_sonnige.conversa.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eiji_der_sonnige.conversa.MainViewModel
import io.github.eiji_der_sonnige.conversa.Screen
import io.github.eiji_der_sonnige.conversa.core.Messenger
import io.github.eiji_der_sonnige.conversa.data.MsgStatus

private data class ChatRow(
    val key: String, val name: String, val group: Boolean, val last: ChatMessage?,
    val subtitle: String, val badge: String?, val unread: Int, val sortTime: Long,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(vm: MainViewModel, m: Messenger) {
    val ctx = LocalContext.current
    val contacts by m.store.contacts.collectAsState()
    val messages by m.store.messages.collectAsState()
    val groups by m.store.groups.collectAsState()
    val relayStatus by m.relays.status.collectAsState()
    val aliases by m.store.aliases.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<String?>(null) }
    val me = m.identity.pubHex
    val youLabel = stringResource(R.string.you)
    fun nameOf(key: String): String = aliases[key] ?: if (isGroupChat(key)) groups[gidOf(key)]?.state?.name ?: ""
        else contacts[key]?.name ?: Npub.short(key)
    fun senderName(pub: String) = if (pub == me) youLabel else nameOf(pub)

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val requestLabel = stringResource(R.string.message_request)
    val newLabel = stringResource(R.string.badge_new)
    val inviteLabel = stringResource(R.string.group_invite)
    val removedLabel = stringResource(R.string.group_removed_short)
    fun unreadOf(key: String) = messages[key].orEmpty().count { !it.fromMe && it.status == MsgStatus.RECEIVED }
    val rows = contacts.values.map { c ->
        val last = messages[c.pubHex]?.lastOrNull()
        ChatRow(c.pubHex, nameOf(c.pubHex), false, last,
            if (!c.accepted) requestLabel else last?.preview(ctx) ?: "",
            if (!c.accepted) newLabel else null, unreadOf(c.pubHex), last?.createdAt ?: 0L)
    } + groups.values.map { g ->
        val key = groupChatKey(g.gid)
        val last = messages[key]?.lastOrNull()
        val sub = when {
            g.removed -> removedLabel
            !g.accepted -> inviteLabel
            last != null -> (if (last.fromMe) youLabel else senderName(last.sender)) + ": " + last.preview(ctx)
            else -> ctx.resources.getQuantityString(R.plurals.member_count, g.state.members.size, g.state.members.size)
        }
        ChatRow(key, nameOf(key), true, last, sub, if (!g.accepted && !g.removed) newLabel else null, unreadOf(key),
            maxOf(last?.createdAt ?: 0L, g.state.createdAt))
    }
    val sorted = rows.sortedByDescending { it.sortTime }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Conversa")
                        val up = relayStatus.count { it.value }
                        Text(
                            if (up > 0) stringResource(R.string.relays_connected, up, relayStatus.size) else stringResource(R.string.connecting),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (up > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                actions = {
                    IconButton({ vm.navigate(Screen.Settings) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton({ fabMenu = true }) { Icon(Icons.Default.Add, stringResource(R.string.new_chat)) }
                DropdownMenu(fabMenu, { fabMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.add_contact)) }, { fabMenu = false; showAdd = true },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.create_group)) }, { fabMenu = false; showCreateGroup = true },
                        leadingIcon = { Icon(Icons.Default.Groups, null) })
                }
            }
        }
    ) { pad ->
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.no_contacts),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(sorted, key = { it.key }) { row ->
                    Box {
                        ListItem(
                            // Langes Drücken öffnet das Chat-Menü (Löschen, Umbenennen).
                            modifier = Modifier.combinedClickable(
                                onClick = { vm.navigate(Screen.Chat(row.key)) },
                                onLongClick = { menuFor = row.key },
                                onLongClickLabel = stringResource(R.string.chat_options),
                            ),
                            leadingContent = { Avatar(row.name, row.group) },
                            headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(row.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    row.last?.let { Text(formatTime(it.createdAt), style = MaterialTheme.typography.labelSmall) }
                                    if (row.badge != null) Badge { Text(row.badge) }
                                    else if (row.unread > 0) Badge { Text(if (row.unread > 99) "99+" else row.unread.toString()) }
                                }
                            }
                        )
                        DropdownMenu(menuFor == row.key, { menuFor = null }) {
                            DropdownMenuItem({ Text(stringResource(R.string.rename_chat)) }, { menuFor = null; renameFor = row.key })
                            DropdownMenuItem({ Text(stringResource(R.string.delete_chat)) }, { menuFor = null; deleteFor = row.key })
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    renameFor?.let { key ->
        RenameDialog(nameOf(key), onDismiss = { renameFor = null }) { m.renameLocal(key, it); renameFor = null }
    }
    deleteFor?.let { key ->
        val group = if (isGroupChat(key)) groups[gidOf(key)] else null
        if (group != null) {
            val deletesForAll = group.state.creator == me && !group.removed
            ConfirmDialog(
                title = stringResource(R.string.delete_chat_q, nameOf(key)),
                text = stringResource(when {
                    deletesForAll -> R.string.group_delete_creator_text
                    group.removed || !group.accepted -> R.string.group_delete_removed_text
                    else -> R.string.group_leave_text
                }),
                confirm = stringResource(when {
                    deletesForAll -> R.string.group_delete_all
                    group.removed -> R.string.delete
                    else -> R.string.group_leave_delete
                }),
                onDismiss = { deleteFor = null },
            ) { vm.deleteChat(key, false); deleteFor = null }
            return@let
        }
        var alsoContact by remember(key) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(stringResource(R.string.delete_chat_q, nameOf(key))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.delete_chat_text))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(alsoContact, { alsoContact = it })
                        Text(stringResource(R.string.delete_chat_contact_too))
                    }
                }
            },
            confirmButton = {
                TextButton({ vm.deleteChat(key, alsoContact); deleteFor = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ deleteFor = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showAdd) AddContactDialog(onDismiss = { showAdd = false }) { id, name ->
        if (vm.addContact(id, name)) showAdd = false
    }
    if (showCreateGroup) {
        val candidates = contacts.values.filter { it.accepted }.map { it.pubHex to nameOf(it.pubHex) }.sortedBy { it.second.lowercase() }
        CreateGroupDialog(candidates, onDismiss = { showCreateGroup = false }) { name, selected, extra ->
            if (vm.createGroup(name, selected, extra)) showCreateGroup = false
        }
    }
}

/** Einfache Bestätigung mit rot hervorgehobener Aktion. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Gruppe erstellen: Name, Kontakte auswählen und/oder weitere Nutzer-IDs (npub) eingeben. */
@Composable
fun CreateGroupDialog(
    candidates: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onCreate: (String, Set<String>, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var extra by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_group)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(64) }, label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (candidates.isNotEmpty()) Text(stringResource(R.string.group_pick_members), style = MaterialTheme.typography.labelLarge)
                candidates.forEach { (pub, n) ->
                    Row(Modifier.fillMaxWidth().clickable { selected = if (pub in selected) selected - pub else selected + pub },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pub in selected, { selected = if (it) selected + pub else selected - pub })
                        Text(n, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedTextField(extra, { extra = it }, label = { Text(stringResource(R.string.group_more_ids)) },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.group_create_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton({ onCreate(name.trim(), selected, extra) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun Avatar(name: String, group: Boolean = false) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (group) Icon(Icons.Default.Groups, null)
            else Text(name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Ändert nur den Anzeigenamen auf diesem Gerät – er wird nicht gesendet. */
@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_chat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(64) }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                Text(stringResource(R.string.rename_local_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ onSave(name.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it.trim() }, label = { Text(stringResource(R.string.contact_id_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name_local_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onAdd(id, name) }, enabled = id.isNotBlank()) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
