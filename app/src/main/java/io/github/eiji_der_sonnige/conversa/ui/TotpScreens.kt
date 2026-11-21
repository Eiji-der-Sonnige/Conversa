package io.github.eiji_der_sonnige.conversa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.eiji_der_sonnige.conversa.MainViewModel
import io.github.eiji_der_sonnige.conversa.Screen
import io.github.eiji_der_sonnige.conversa.core.Messenger
import io.github.eiji_der_sonnige.conversa.crypto.Totp

@Composable
private fun CodeField(code: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        code, { v -> onChange(v.filter(Char::isDigit).take(6)) },
        Modifier.fillMaxWidth(),
        label = { Text("6-stelliger Code") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

@Composable
fun TotpVerifyScreen(vm: MainViewModel, s: Screen.TotpVerify) {
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Icon(Icons.Default.Security, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Zwei-Faktor-Authentifizierung", style = MaterialTheme.typography.titleLarge)
        Text("Gib den Code aus Google Authenticator ein.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CodeField(code) {
            code = it
            if (it.length == 6) vm.verifyTotp(it)
        }
        Button({ vm.verifyTotp(code) }, Modifier.fillMaxWidth(), enabled = code.length == 6) { Text("Bestätigen") }
        TextButton({ vm.logout() }) { Text(if (s.newSession) "Abbrechen" else "Abmelden") }
    }
}

@Composable
fun TotpSetupScreen(vm: MainViewModel, m: Messenger, secret: String) {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    val uri = remember(secret) { Totp.uri(secret, m.identity.username) }
    BackHandler { vm.navigate(Screen.Settings) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("2FA einrichten", style = MaterialTheme.typography.titleLarge)
        Text("1. Öffne Google Authenticator → „+“ → „QR-Code scannen“.")
        QrCode(uri)
        Text("Oder Schlüssel manuell eingeben:", style = MaterialTheme.typography.bodySmall)
        Text(
            secret.chunked(4).joinToString(" "),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyLarge
        )
        TextButton({ copyToClipboard(ctx, "2FA", secret, sensitive = true) }) { Text("Schlüssel kopieren") }
        // Authenticator auf demselben Gerät: otpauth-Link direkt öffnen.
        TextButton({
            try {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
            } catch (_: Exception) {
                android.widget.Toast.makeText(ctx, "Keine Authenticator-App gefunden", android.widget.Toast.LENGTH_SHORT).show()
            }
        }) { Text("In Authenticator-App öffnen") }
        Text("2. Gib den angezeigten Code ein, um die Einrichtung abzuschließen.")
        CodeField(code) { code = it }
        Button({ vm.confirmTotpSetup(secret, code) }, Modifier.fillMaxWidth(), enabled = code.length == 6) {
            Text("2FA aktivieren")
        }
        TextButton({ vm.navigate(Screen.Settings) }) { Text("Abbrechen") }
    }
}
