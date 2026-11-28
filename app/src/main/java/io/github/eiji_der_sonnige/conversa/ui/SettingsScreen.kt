package io.github.eiji_der_sonnige.conversa.ui

import androidx.compose.ui.res.stringResource
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import io.github.eiji_der_sonnige.conversa.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.eiji_der_sonnige.conversa.MainViewModel
import io.github.eiji_der_sonnige.conversa.Screen
import io.github.eiji_der_sonnige.conversa.core.Messenger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, m: Messenger) {
    val ctx = LocalContext.current
    val totp by m.totpSecret.collectAsState()
    val current by vm.settings.collectAsState()
    var relays by remember(current) { mutableStateOf(current.relays.joinToString("\n")) }
    var blossom by remember(current) { mutableStateOf(current.blossomServers.joinToString("\n")) }
    var turnUrl by remember(current) { mutableStateOf(current.turnUrl) }
    var turnUser by remember(current) { mutableStateOf(current.turnUser) }
    var turnPass by remember(current) { mutableStateOf(current.turnPass) }
    var relayOnly by remember(current) { mutableStateOf(current.relayOnlyCalls) }
    var tor by remember(current) { mutableStateOf(current.useTor) }
    var confirmLogout by remember { mutableStateOf(false) }
    var disable2fa by remember { mutableStateOf(false) }
    var explainBackground by remember { mutableStateOf(false) }

    BackHandler { vm.navigate(Screen.Chats) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = {
                IconButton({ vm.navigate(Screen.Chats) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            }
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section(stringResource(R.string.your_id)) {
                Text(stringResource(R.string.your_id_text),
                    style = MaterialTheme.typography.bodySmall)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(m.identity.npub)
                }
                Text(m.identity.npub, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ copyToClipboard(ctx, "Conversa-ID", m.identity.npub) }) { Text(stringResource(R.string.copy_id)) }
            }

            Section(stringResource(R.string.twofa)) {
                Text(
                    if (totp != null) stringResource(R.string.twofa_on)
                    else stringResource(R.string.twofa_off)
                )
                if (totp == null) Button({ vm.startTotpSetup() }) { Text(stringResource(R.string.twofa_enable)) }
                else OutlinedButton({ disable2fa = true }) { Text(stringResource(R.string.twofa_disable)) }
            }

            Section(stringResource(R.string.privacy_receiving)) {
                SwitchRow(stringResource(R.string.read_receipts), current.readReceipts) {
                    vm.saveLocalSettings(readReceipts = it, background = current.background)
                }
                Text(
                    stringResource(R.string.read_receipts_text),
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                SwitchRow(stringResource(R.string.background), current.background) {
                    if (it) explainBackground = true
                    else vm.saveLocalSettings(readReceipts = current.readReceipts, background = false)
                }
                Text(
                    if (current.background) stringResource(R.string.background_on)
                    else stringResource(R.string.background_off),
                    style = MaterialTheme.typography.bodySmall
                )
                if (current.background) BatteryOptimizationRow()
                HorizontalDivider()
                BlockedRow(m)
            }

            Section(stringResource(R.string.network)) {
                OutlinedTextField(relays, { relays = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.relays_hint)) }, minLines = 3)
                OutlinedTextField(blossom, { blossom = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.blossom_hint)) }, minLines = 2)
                SwitchRow(stringResource(R.string.use_tor), tor) { tor = it }
            }

            Section(stringResource(R.string.calls)) {
                Text(stringResource(R.string.calls_text),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(turnUrl, { turnUrl = it.trim() }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.turn_server)) }, singleLine = true)
                OutlinedTextField(turnUser, { turnUser = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.turn_user)) }, singleLine = true)
                OutlinedTextField(turnPass, { turnPass = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.turn_pass)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                SwitchRow(stringResource(R.string.relay_only), relayOnly) { relayOnly = it }
            }

            Button(
                {
                    vm.saveSettings(
                        current.copy(
                            relays = relays.lines().map { it.trim() }.filter { it.startsWith("wss://") },
                            blossomServers = blossom.lines().map { it.trim() }.filter { it.startsWith("https://") },
                            turnUrl = turnUrl, turnUser = turnUser, turnPass = turnPass,
                            relayOnlyCalls = relayOnly, useTor = tor,
                        )
                    )
                },
                Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save_network)) }

            InfoSection(m)

            HorizontalDivider()
            Button(
                { confirmLogout = true }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.logout_wipe)) }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_q)) },
            text = { Text(stringResource(R.string.logout_text)) },
            confirmButton = { TextButton({ confirmLogout = false; vm.logout() }) { Text(stringResource(R.string.logout)) } },
            dismissButton = { TextButton({ confirmLogout = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (explainBackground) {
        AlertDialog(
            onDismissRequest = { explainBackground = false },
            title = { Text(stringResource(R.string.background_q)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.background_text1)
                    )
                    Text(
                        stringResource(R.string.background_text2)
                    )
                    Text(
                        stringResource(R.string.background_text3),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton({
                    explainBackground = false
                    vm.saveLocalSettings(readReceipts = current.readReceipts, background = true)
                }) { Text(stringResource(R.string.turn_on)) }
            },
            dismissButton = { TextButton({ explainBackground = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (disable2fa) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { disable2fa = false },
            title = { Text(stringResource(R.string.twofa_disable)) },
            text = {
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.current_code)) }, singleLine = true)
            },
            confirmButton = { TextButton({ if (vm.disableTotp(code)) disable2fa = false }) { Text(stringResource(R.string.deactivate)) } },
            dismissButton = { TextButton({ disable2fa = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun InfoSection(m: Messenger) {
    val ctx = LocalContext.current
    val relayStatus by m.relays.status.collectAsState()
    val pkg = remember {
        ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
    }
    val dateFmt = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    var showLicenses by remember { mutableStateOf(false) }
    var asset by remember { mutableStateOf<Pair<String, String>?>(null) }

    Section(stringResource(R.string.info)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.conversa_logo), stringResource(R.string.logo), Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Conversa", style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.version_build, pkg.versionName ?: "", pkg.longVersionCode),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        InfoRow(stringResource(R.string.device), "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        InfoRow(stringResource(R.string.supported), stringResource(R.string.supported_value))
        InfoRow(stringResource(R.string.installed), dateFmt.format(Date(pkg.firstInstallTime)))
        InfoRow(stringResource(R.string.updated), dateFmt.format(Date(pkg.lastUpdateTime)))
        InfoRow(stringResource(R.string.package_label), ctx.packageName)
        InfoRow(stringResource(R.string.license), "Open Source, Apache 2.0")
        InfoRow(stringResource(R.string.project), stringResource(R.string.project_value))
        InfoRow("Relays", stringResource(R.string.relays_of, relayStatus.count { it.value }, relayStatus.size))
        InfoRow(stringResource(R.string.network_label), stringResource(if (m.settings.useTor) R.string.network_value_tor else R.string.network_value))
        HorizontalDivider()
        Text(stringResource(R.string.security), style = MaterialTheme.typography.titleSmall)
        InfoRow(stringResource(R.string.messages), "NIP-44 v2 (ChaCha20, HMAC-SHA256)")
        InfoRow(stringResource(R.string.metadata_protection), "Gift Wrap (NIP-17 / NIP-59)")
        InfoRow(stringResource(R.string.signatures), "secp256k1 Schnorr (BIP-340)")
        InfoRow(stringResource(R.string.key_derivation), "Argon2id, 64 MB")
        InfoRow(stringResource(R.string.media), "AES-256-GCM")
        InfoRow(stringResource(R.string.calls), "WebRTC P2P, DTLS-SRTP")
        InfoRow(stringResource(R.string.local_data), "Android Keystore, AES-256-GCM")
        Text(
            stringResource(R.string.about_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        Text(stringResource(R.string.legal), style = MaterialTheme.typography.titleSmall)
        val termsTitle = stringResource(R.string.terms)
        val privacyTitle = stringResource(R.string.privacy)
        TextButton({ asset = termsTitle to "legal/nutzungsbedingungen.txt" }) { Text(stringResource(R.string.terms_liability)) }
        TextButton({ asset = privacyTitle to "legal/datenschutz.txt" }) { Text(privacyTitle) }
        TextButton({ showLicenses = true }) { Text(stringResource(R.string.oss_licenses)) }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.oss_licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.oss_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    listOf(
                        stringResource(R.string.lic_notices) to "licenses/NOTICES.txt",
                        stringResource(R.string.lic_apache) to "licenses/apache-2.0.txt",
                        stringResource(R.string.lic_mit) to "licenses/bouncycastle-mit.txt",
                        "BSD 3-Clause (WebRTC)" to "licenses/webrtc-bsd-3-clause.txt",
                        stringResource(R.string.lic_webrtc_third) to "licenses/webrtc-third-party.txt",
                    ).forEach { (name, path) ->
                        TextButton({ asset = name to path }) { Text(name) }
                    }
                }
            },
            confirmButton = { TextButton({ showLicenses = false }) { Text(stringResource(R.string.close)) } }
        )
    }
    asset?.let { (title, path) -> AssetTextDialog(title, path) { asset = null } }
}

/**
 * Manche Hersteller beenden Hintergrund-Apps trotz Dienst. Die Ausnahme von der Akku-Optimierung macht den
 * Empfang zuverlässiger, erlaubt Conversa aber sonst nichts zusätzlich.
 */
@Composable
private fun BatteryOptimizationRow() {
    val ctx = LocalContext.current
    val pm = ctx.getSystemService(android.os.PowerManager::class.java)
    var exempt by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { exempt = pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    if (exempt) {
        Text(stringResource(R.string.battery_exempt), style = MaterialTheme.typography.bodySmall)
    } else {
        Text(
            stringResource(R.string.battery_tip),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton({
            try {
                launcher.launch(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                )
            } catch (e: Exception) {
                launcher.launch(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }) { Text(stringResource(R.string.battery_off)) }
    }
}

/** Blockierte Absender anzeigen und Sperre aufheben. */
@Composable
private fun BlockedRow(m: Messenger) {
    val blocked by m.store.blocked.collectAsState()
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.blocked_count, blocked.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (blocked.isNotEmpty()) TextButton({ open = true }) { Text(stringResource(R.string.manage)) }
    }
    Text(
        stringResource(R.string.spam_limit_text, io.github.eiji_der_sonnige.conversa.core.Messenger.MAX_PENDING_REQUESTS,
            io.github.eiji_der_sonnige.conversa.core.Messenger.MAX_REQUEST_MESSAGES),
        style = MaterialTheme.typography.bodySmall
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.blocked)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (blocked.isEmpty()) Text(stringResource(R.string.nobody_blocked))
                    blocked.sorted().forEach { pub ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(io.github.eiji_der_sonnige.conversa.crypto.Npub.short(pub), Modifier.weight(1f),
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            TextButton({ m.unblock(pub) }) { Text(stringResource(R.string.unblock)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ open = false }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.58f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(value, onChange)
    }
}
