package io.github.eiji_der_sonnige.conversa.ui

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(vm: MainViewModel, m: Messenger) {
    val ctx = LocalContext.current
    val contacts by m.store.contacts.collectAsState()
    val messages by m.store.messages.collectAsState()
    val relayStatus by m.relays.status.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

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
                            if (up > 0) "● $up/${relayStatus.size} Relays verbunden" else "○ Verbinde…",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (up > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                actions = {
                    IconButton({ vm.navigate(Screen.Settings) }) { Icon(Icons.Default.Settings, "Einstellungen") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton({ showAdd = true }) { Icon(Icons.Default.PersonAdd, "Kontakt hinzufügen") }
        }
    ) { pad ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Noch keine Kontakte.\nTeile deine ID (Einstellungen) oder füge einen Kontakt über „+“ hinzu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(rows, key = { it.first.pubHex }) { (c, last) ->
                    ListItem(
                        modifier = Modifier.clickable { vm.navigate(Screen.Chat(c.pubHex)) },
                        leadingContent = { Avatar(c.name) },
                        headlineContent = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                if (!c.accepted) "Nachrichtenanfrage" else last?.preview() ?: "",
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                last?.let { Text(formatTime(it.createdAt), style = MaterialTheme.typography.labelSmall) }
                                val unread = messages[c.pubHex].orEmpty().count { !it.fromMe && it.status == MsgStatus.RECEIVED }
                                if (!c.accepted) Badge { Text("Neu") }
                                else if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
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

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kontakt hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it.trim() }, label = { Text("Kontakt-ID (npub1…)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Name (nur lokal sichtbar)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onAdd(id, name) }, enabled = id.isNotBlank()) { Text("Hinzufügen") } },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } }
    )
}
