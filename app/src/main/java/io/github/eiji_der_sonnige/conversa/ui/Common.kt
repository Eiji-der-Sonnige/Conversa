package io.github.eiji_der_sonnige.conversa.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

fun qrBitmap(text: String, size: Int = 640): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i ->
        if (m.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

@Composable
fun QrCode(text: String, size: Dp = 220.dp) {
    val bmp = remember(text) { qrBitmap(text) }
    Image(bmp.asImageBitmap(), contentDescription = "QR-Code", modifier = Modifier.size(size).background(Color.White))
}

/** Kopiert in die Zwischenablage; sensible Inhalte werden als solche markiert (keine Vorschau). */
fun copyToClipboard(ctx: Context, label: String, text: String, sensitive: Boolean = false) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    cm.setPrimaryClip(clip)
}

/** Zeigt einen Text aus den App-Assets (Lizenzen, Rechtstexte) vollständig an. */
@Composable
fun AssetTextDialog(title: String, assetPath: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val text = remember(assetPath) {
        try {
            ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Text nicht verfügbar."
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(title) },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                androidx.compose.material3.Text(
                    text,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onDismiss) { androidx.compose.material3.Text("Schließen") } }
    )
}

fun formatTime(epochSec: Long): String {
    val fmt = java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY)
    return fmt.format(java.util.Date(epochSec * 1000))
}
