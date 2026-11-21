package io.github.eiji_der_sonnige.conversa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import io.github.eiji_der_sonnige.conversa.R
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.eiji_der_sonnige.conversa.MainViewModel
import io.github.eiji_der_sonnige.conversa.crypto.KeyDerivation

@Composable
fun LoginScreen(vm: MainViewModel) {
    val busy by vm.busy.collectAsState()
    var user by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var newAccount by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf<Pair<String, String>?>(null) }
    legal?.let { (title, path) -> AssetTextDialog(title, path) { legal = null } }
    val transform = if (show) VisualTransformation.None else PasswordVisualTransformation()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.conversa_logo), "Conversa-Logo", Modifier.size(120.dp))
        Text("Conversa", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Dezentral · Privat · Ende-zu-Ende-verschlüsselt",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            user, { user = it }, Modifier.fillMaxWidth(),
            label = { Text("Benutzername") }, singleLine = true, enabled = !busy
        )
        OutlinedTextField(
            pw, { pw = it }, Modifier.fillMaxWidth(),
            label = { Text("Passwort") }, singleLine = true, enabled = !busy,
            visualTransformation = transform,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton({ show = !show }) {
                    Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Anzeigen")
                }
            }
        )
        if (newAccount) {
            OutlinedTextField(
                pw2, { pw2 = it }, Modifier.fillMaxWidth(),
                label = { Text("Passwort wiederholen") }, singleLine = true, enabled = !busy,
                visualTransformation = transform,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            val problem = if (pw.isEmpty()) null else KeyDerivation.passwordProblem(pw.toCharArray())
            Text(
                problem ?: "✓ Passwort stark genug",
                color = if (problem == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Neues Konto erstellen", Modifier.weight(1f))
            Switch(newAccount, { newAccount = it }, enabled = !busy)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(accepted, { accepted = it }, enabled = !busy)
            Text(
                "Ich akzeptiere die Nutzungsbedingungen, habe die Datenschutzhinweise gelesen und bin für " +
                    "meine Nutzung und meine Inhalte selbst verantwortlich.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row {
            TextButton({ legal = "Nutzungsbedingungen" to "legal/nutzungsbedingungen.txt" }) { Text("Nutzungsbedingungen") }
            TextButton({ legal = "Datenschutzhinweise" to "legal/datenschutz.txt" }) { Text("Datenschutz") }
        }
        Button(
            onClick = {
                vm.login(user, pw.toCharArray(), if (newAccount) pw2.toCharArray() else null, newAccount)
            },
            enabled = !busy && user.isNotBlank() && pw.isNotEmpty() && accepted,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text("Schlüssel wird berechnet…")
            } else {
                Text(if (newAccount) "Konto erstellen" else "Anmelden")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Kein Server, keine E-Mail, keine Telefonnummer. Dein geheimer Schlüssel wird nur auf " +
                    "diesem Gerät aus Benutzername + Passwort berechnet. Mit denselben Daten meldest du dich " +
                    "auf jedem Gerät an.\n\n⚠ Das Passwort kann nicht zurückgesetzt werden. " +
                    "Verlierst du es, ist das Konto verloren. Wähle ein langes, einzigartiges Passwort.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
