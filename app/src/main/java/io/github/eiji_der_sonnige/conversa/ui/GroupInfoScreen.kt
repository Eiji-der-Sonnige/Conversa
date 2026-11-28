package io.github.eiji_der_sonnige.conversa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eiji_der_sonnige.conversa.MainViewModel
import io.github.eiji_der_sonnige.conversa.R
import io.github.eiji_der_sonnige.conversa.Screen
import io.github.eiji_der_sonnige.conversa.core.GroupRules
import io.github.eiji_der_sonnige.conversa.core.Messenger
import io.github.eiji_der_sonnige.conversa.crypto.Npub
import io.github.eiji_der_sonnige.conversa.data.groupChatKey

/** Mitglieder, Rollen und Admin-Aktionen einer Gruppe. Jede Aktion wird signiert und von allen Geräten geprüft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(vm: MainViewModel, m: Messenger, gid: String) {
    val groups by m.store.groups.collectAsState()
    val contacts by m.store.contacts.collectAsState()
    val aliases by m.store.aliases.collectAsState()
    val entry = groups[gid]
    val back = { vm.navigate(if (entry != null) Screen.Chat(groupChatKey(gid)) else Screen.Chats) }
    BackHandler { back() }
    if (entry == null) {
        // Gruppe gelöscht oder verlassen
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.navigate(Screen.Chats) }
        return
    }
    val st = entry.state
    val me = m.identity.pubHex
    val iAmAdmin = st.isAdmin(me) && !entry.removed
    val iAmCreator = st.creator == me
    val youLabel = stringResource(R.string.you)
    fun nameOf(pub: String) = if (pub == me) youLabel else aliases[pub] ?: contacts[pub]?.name ?: Npub.short(pub)

    var memberMenu by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var invite by remember { mutableStateOf(false) }
    var renameGroup by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(st.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(st.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.group_created_by, nameOf(st.creator)), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(when {
                            entry.removed -> R.string.group_role_removed
                            iAmCreator -> R.string.group_role_creator
                            iAmAdmin -> R.string.group_role_admin
                            else -> R.string.group_role_member
                        }),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (iAmAdmin) TextButton({ renameGroup = true }) { Text(stringResource(R.string.group_rename)) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.group_members_title, st.members.size, GroupRules.MAX_MEMBERS, st.admins.size, GroupRules.MAX_ADMINS),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium
                        )
                        if (iAmAdmin && st.members.size < GroupRules.MAX_MEMBERS) {
                            IconButton({ invite = true }) { Icon(Icons.Default.PersonAdd, stringResource(R.string.group_invite_member)) }
                        }
                    }
                    st.members.sortedWith(compareBy({ it != st.creator }, { it !in st.admins }, { nameOf(it).lowercase() })).forEach { pub ->
                        Box {
                            ListItem(
                                modifier = Modifier.clickable(enabled = pub != me) { memberMenu = pub },
                                leadingContent = { Avatar(nameOf(pub)) },
                                headlineContent = { Text(nameOf(pub), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    val role = when {
                                        pub == st.creator -> stringResource(R.string.group_creator)
                                        pub in st.admins -> stringResource(R.string.group_admin)
                                        else -> null
                                    }
                                    Text(listOfNotNull(role, Npub.short(pub)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                }
                            )
                            DropdownMenu(memberMenu == pub, { memberMenu = null }) {
                                // Nur Aktionen, die die Regeln erlauben (GroupRules prüft beim Ausführen und auf allen Geräten erneut).
                                val targetAdmin = pub in st.admins
                                if (iAmAdmin && pub != st.creator && (!targetAdmin || iAmCreator)) {
                                    DropdownMenuItem({ Text(stringResource(R.string.group_remove_member)) }, { memberMenu = null; confirmRemove = pub })
                                }
                                if (iAmAdmin && !targetAdmin && st.admins.size < GroupRules.MAX_ADMINS) {
                                    DropdownMenuItem({ Text(stringResource(R.string.group_make_admin)) }, {
                                        memberMenu = null; vm.groupAction { it.setAdmin(gid, pub, true) }
                                    })
                                }
                                if (iAmCreator && targetAdmin && pub != st.creator) {
                                    DropdownMenuItem({ Text(stringResource(R.string.group_revoke_admin)) }, {
                                        memberMenu = null; vm.groupAction { it.setAdmin(gid, pub, false) }
                                    })
                                }
                                if (contacts[pub]?.accepted != true) {
                                    DropdownMenuItem({ Text(stringResource(R.string.add_contact)) }, {
                                        memberMenu = null; vm.addContact(Npub.encode(pub), "")
                                    })
                                }
                                DropdownMenuItem({ Text(stringResource(R.string.close)) }, { memberMenu = null })
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.group_security_info), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            if (iAmAdmin && st.admins.contains(me) && me != st.creator) {
                OutlinedButton({ vm.groupAction { it.setAdmin(gid, me, false) } }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.group_resign_admin))
                }
            }
            if (!iAmCreator || entry.removed) {
                OutlinedButton({ confirmLeave = true }, Modifier.fillMaxWidth()) {
                    Text(stringResource(if (entry.removed) R.string.delete_chat else R.string.group_leave_delete))
                }
            }
            if (iAmAdmin) {
                Button(
                    { confirmDelete = true }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.group_delete_for_all)) }
            }
        }
    }

    confirmRemove?.let { pub ->
        ConfirmDialog(
            stringResource(R.string.group_remove_q, nameOf(pub)), stringResource(R.string.group_remove_text),
            stringResource(R.string.group_remove_member), onDismiss = { confirmRemove = null }
        ) { confirmRemove = null; vm.groupAction { it.removeMember(gid, pub) } }
    }
    if (confirmLeave) {
        ConfirmDialog(
            stringResource(R.string.delete_chat_q, st.name), stringResource(R.string.group_leave_text),
            stringResource(R.string.group_leave_delete), onDismiss = { confirmLeave = false }
        ) { confirmLeave = false; vm.groupAction { it.leave(gid) }; vm.navigate(Screen.Chats) }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_chat_q, st.name), stringResource(R.string.group_delete_admin_text),
            stringResource(R.string.group_delete_all), onDismiss = { confirmDelete = false }
        ) { confirmDelete = false; vm.groupAction { it.deleteForAll(gid) }; vm.navigate(Screen.Chats) }
    }
    if (renameGroup) {
        var name by remember { mutableStateOf(st.name) }
        AlertDialog(
            onDismissRequest = { renameGroup = false },
            title = { Text(stringResource(R.string.group_rename)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(GroupRules.MAX_NAME) }, label = { Text(stringResource(R.string.group_name)) }, singleLine = true)
                    Text(stringResource(R.string.group_rename_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton({ renameGroup = false; vm.groupAction { it.rename(gid, name.trim()) } }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton({ renameGroup = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (invite) {
        val candidates = contacts.values.filter { it.accepted && it.pubHex !in st.members }
            .map { it.pubHex to nameOf(it.pubHex) }.sortedBy { it.second.lowercase() }
        var selected by remember { mutableStateOf(setOf<String>()) }
        var extra by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { invite = false },
            title = { Text(stringResource(R.string.group_invite_member)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    candidates.forEach { (pub, n) ->
                        Row(Modifier.fillMaxWidth().clickable { selected = if (pub in selected) selected - pub else selected + pub },
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(pub in selected, { selected = if (it) selected + pub else selected - pub })
                            Text(n, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedTextField(extra, { extra = it }, label = { Text(stringResource(R.string.group_more_ids)) },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.group_invite_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton({ if (vm.inviteToGroup(gid, selected, extra)) invite = false }) { Text(stringResource(R.string.group_invite_send)) }
            },
            dismissButton = { TextButton({ invite = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
