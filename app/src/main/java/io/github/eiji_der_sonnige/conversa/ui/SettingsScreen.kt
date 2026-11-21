package io.github.eiji_der_sonnige.conversa.ui

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

    BackHandler { vm.navigate(Screen.Chats) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Einstellungen") },
            navigationIcon = {
                IconButton({ vm.navigate(Screen.Chats) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            }
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("Deine Kontakt-ID") {
                Text("Teile diese ID, damit andere dich hinzufügen können. Sie verrät nichts über dich.",
                    style = MaterialTheme.typography.bodySmall)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(m.identity.npub)
                }
                Text(m.identity.npub, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ copyToClipboard(ctx, "Conversa-ID", m.identity.npub) }) { Text("ID kopieren") }
            }

            Section("Zwei-Faktor-Authentifizierung") {
                Text(
                    if (totp != null) "✓ Aktiv – Code wird bei Login und App-Start abgefragt."
                    else "Nicht aktiv. Schützt dein Konto zusätzlich mit Google Authenticator."
                )
                if (totp == null) Button({ vm.startTotpSetup() }) { Text("2FA aktivieren") }
                else OutlinedButton({ disable2fa = true }) { Text("2FA deaktivieren") }
            }

            Section("Netzwerk (dezentral)") {
                OutlinedTextField(relays, { relays = it }, Modifier.fillMaxWidth(),
                    label = { Text("Nostr-Relays (eine wss://-URL pro Zeile)") }, minLines = 3)
                OutlinedTextField(blossom, { blossom = it }, Modifier.fillMaxWidth(),
                    label = { Text("Medien-Server (Blossom, https://)") }, minLines = 2)
                SwitchRow("Über Tor verbinden (Orbot muss laufen, SOCKS 9050)", tor) { tor = it }
            }

            Section("Anrufe") {
                Text("Anrufe laufen direkt P2P. Ohne TURN-Server sieht der Gesprächspartner deine IP-Adresse.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(turnUrl, { turnUrl = it.trim() }, Modifier.fillMaxWidth(),
                    label = { Text("TURN-Server (turn:host:3478)") }, singleLine = true)
                OutlinedTextField(turnUser, { turnUser = it }, Modifier.fillMaxWidth(),
                    label = { Text("TURN-Benutzer") }, singleLine = true)
                OutlinedTextField(turnPass, { turnPass = it }, Modifier.fillMaxWidth(),
                    label = { Text("TURN-Passwort") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                SwitchRow("Nur über TURN (IP-Adresse verbergen)", relayOnly) { relayOnly = it }
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
            ) { Text("Netzwerkeinstellungen speichern") }

            InfoSection(m)

            HorizontalDivider()
            Button(
                { confirmLogout = true }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Abmelden & lokale Daten löschen") }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Abmelden?") },
            text = { Text("Alle lokalen Daten werden sicher gelöscht. Chats und Kontakte lassen sich mit Benutzername + Passwort aus dem Netzwerk wiederherstellen, solange die Relays sie speichern.") },
            confirmButton = { TextButton({ confirmLogout = false; vm.logout() }) { Text("Abmelden") } },
            dismissButton = { TextButton({ confirmLogout = false }) { Text("Abbrechen") } }
        )
    }
    if (disable2fa) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { disable2fa = false },
            title = { Text("2FA deaktivieren") },
            text = {
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("Aktueller Code") }, singleLine = true)
            },
            confirmButton = { TextButton({ if (vm.disableTotp(code)) disable2fa = false }) { Text("Deaktivieren") } },
            dismissButton = { TextButton({ disable2fa = false }) { Text("Abbrechen") } }
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
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    var showLicenses by remember { mutableStateOf(false) }
    var asset by remember { mutableStateOf<Pair<String, String>?>(null) }

    Section("Info") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.conversa_logo), "Conversa-Logo", Modifier.size(64.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Conversa", style = MaterialTheme.typography.titleLarge)
                Text("Version ${pkg.versionName} (Build ${pkg.longVersionCode})",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        InfoRow("Gerät", "${Build.MANUFACTURER} ${Build.MODEL}")
        InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        InfoRow("Unterstützt", "ab Android 13 (API 33)")
        InfoRow("Installiert", dateFmt.format(Date(pkg.firstInstallTime)))
        InfoRow("Aktualisiert", dateFmt.format(Date(pkg.lastUpdateTime)))
        InfoRow("Paket", ctx.packageName)
        InfoRow("Lizenz", "Open Source, Apache 2.0")
        InfoRow("Projekt", "Nicht-kommerzielles Hobbyprojekt")
        InfoRow("Relays", "${relayStatus.count { it.value }} von ${relayStatus.size} verbunden")
        InfoRow("Netzwerk", "Nostr (dezentral)" + if (m.settings.useTor) " über Tor" else "")
        HorizontalDivider()
        Text("Sicherheit", style = MaterialTheme.typography.titleSmall)
        InfoRow("Nachrichten", "NIP-44 v2 (ChaCha20, HMAC-SHA256)")
        InfoRow("Metadatenschutz", "Gift Wrap (NIP-17 / NIP-59)")
        InfoRow("Signaturen", "secp256k1 Schnorr (BIP-340)")
        InfoRow("Schlüsselableitung", "Argon2id, 64 MB")
        InfoRow("Medien", "AES-256-GCM")
        InfoRow("Anrufe", "WebRTC P2P, DTLS-SRTP")
        InfoRow("Lokale Daten", "Android Keystore, AES-256-GCM")
        Text(
            "Auf Anonymität und Ende-zu-Ende-Verschlüsselung ausgelegt – ohne Werbung, Tracking oder " +
                "eigenen Server. Relays, Medien- und STUN-Server sind austauschbare Voreinstellungen und werden " +
                "von unabhängigen Dritten betrieben; diese sehen technisch bedingt z. B. die IP-Adresse. " +
                "Kein unabhängiges Sicherheits-Audit, keine Gewährleistung – Nutzung auf eigenes Risiko. " +
                "Jede Person ist für ihre Nutzung und Inhalte selbst verantwortlich.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        Text("Rechtliches", style = MaterialTheme.typography.titleSmall)
        TextButton({ asset = "Nutzungsbedingungen" to "legal/nutzungsbedingungen.txt" }) { Text("Nutzungsbedingungen & Haftung") }
        TextButton({ asset = "Datenschutzhinweise" to "legal/datenschutz.txt" }) { Text("Datenschutzhinweise") }
        TextButton({ showLicenses = true }) { Text("Open-Source-Lizenzen") }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("Open-Source-Lizenzen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Conversa nutzt Open-Source-Software. Tippe auf einen Eintrag, um den vollständigen Text zu lesen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    listOf(
                        "Übersicht & Copyright-Hinweise" to "licenses/NOTICES.txt",
                        "Apache License 2.0 (AndroidX, Kotlin, OkHttp, Okio, ZXing, Stream WebRTC u. a.)" to "licenses/apache-2.0.txt",
                        "MIT-Lizenz (Bouncy Castle)" to "licenses/bouncycastle-mit.txt",
                        "BSD 3-Clause (WebRTC)" to "licenses/webrtc-bsd-3-clause.txt",
                        "In WebRTC enthaltene Komponenten (BoringSSL, Opus, libvpx, AV1 u. a.)" to "licenses/webrtc-third-party.txt",
                    ).forEach { (name, path) ->
                        TextButton({ asset = name to path }) { Text(name) }
                    }
                }
            },
            confirmButton = { TextButton({ showLicenses = false }) { Text("Schließen") } }
        )
    }
    asset?.let { (title, path) -> AssetTextDialog(title, path) { asset = null } }
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
