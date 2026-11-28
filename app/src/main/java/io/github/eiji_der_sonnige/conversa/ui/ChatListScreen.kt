package io.github.eiji_der_sonnige.conversa.ui

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(vm: MainViewModel, m: Messenger) {
    val ctx = LocalContext.current
    val contacts by m.store.contacts.collectAsState()
    val messages by m.store.messages.collectAsState()
    val relayStatus by m.relays.status.collectAsState()
    val aliases by m.store.aliases.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<String?>(null) }
    fun nameOf(pub: String) = aliases[pub] ?: contacts[pub]?.name ?: ""

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val rows = contacts.values.map { c -> c to messages[c.pubHex]?.lastOrNull() }
        .sortedByDescending { it.second?.createdAt ?: 0L }

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
            FloatingActionButton({ showAdd = true }) { Icon(Icons.Default.PersonAdd, stringResource(R.string.add_contact)) }
        }
    ) { pad ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.no_contacts),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(rows, key = { it.first.pubHex }) { (c, last) ->
                    Box {
                    ListItem(
                        // Langes Drücken öffnet das Chat-Menü (Löschen, Umbenennen).
                        modifier = Modifier.combinedClickable(
                            onClick = { vm.navigate(Screen.Chat(c.pubHex)) },
                            onLongClick = { menuFor = c.pubHex },
                            onLongClickLabel = stringResource(R.string.chat_options),
                        ),
                        leadingContent = { Avatar(nameOf(c.pubHex)) },
                        headlineContent = { Text(nameOf(c.pubHex), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                if (!c.accepted) stringResource(R.string.message_request) else last?.preview(ctx) ?: "",
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                last?.let { Text(formatTime(it.createdAt), style = MaterialTheme.typography.labelSmall) }
                                val unread = messages[c.pubHex].orEmpty().count { !it.fromMe && it.status == MsgStatus.RECEIVED }
                                if (!c.accepted) Badge { Text(stringResource(R.string.badge_new)) }
                                else if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                            }
                        }
                    )
                    DropdownMenu(menuFor == c.pubHex, { menuFor = null }) {
                        DropdownMenuItem({ Text(stringResource(R.string.rename_chat)) }, { menuFor = null; renameFor = c.pubHex })
                        DropdownMenuItem({ Text(stringResource(R.string.delete_chat)) }, { menuFor = null; deleteFor = c.pubHex })
                    }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    renameFor?.let { pub ->
        RenameDialog(nameOf(pub), onDismiss = { renameFor = null }) { m.renameLocal(pub, it); renameFor = null }
    }
    deleteFor?.let { pub ->
        var alsoContact by remember(pub) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(stringResource(R.string.delete_chat_q, nameOf(pub))) },
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
                TextButton({ vm.deleteChat(pub, alsoContact); deleteFor = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ deleteFor = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showAdd) AddContactDialog(onDismiss = { showAdd = false }) { id, name ->
        if (vm.addContact(id, name)) showAdd = false
    }
}

@Composable
fun Avatar(name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleMedium)
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
