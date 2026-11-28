package io.github.eiji_der_sonnige.conversa.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.github.eiji_der_sonnige.conversa.R
import java.util.concurrent.Executors

/**
 * Knopf, der den QR-Scanner öffnet. Das Kamerabild wird ausschließlich auf dem Gerät ausgewertet
 * (ZXing), nichts wird gespeichert oder gesendet.
 */
@Composable
fun ScanQrButton(onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) open = true }
    IconButton({
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open = true
        else permission.launch(Manifest.permission.CAMERA)
    }) { Icon(Icons.Default.QrCodeScanner, stringResource(R.string.scan_qr)) }
    if (open) QrScannerDialog(onDismiss = { open = false }) { open = false; onResult(it) }
}

@Composable
fun QrScannerDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var result by remember { mutableStateOf<String?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(ctx) }
    LaunchedEffect(result) { result?.let(onResult) }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val p = providerFuture.get()
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val reader = QrDecoder.reader()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                if (result == null) decode(reader, image)?.let { text -> result = text }
                image.close()
            }
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                try {
                    p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                } catch (_: Exception) {
                }
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.scan_qr_hint), color = Color.White)
                TextButton(onDismiss) { Text(stringResource(R.string.cancel), color = Color.White) }
            }
        }
    }
}

/** Wertet nur die Helligkeitsebene (Y) aus – reicht für QR-Codes und ist schnell. */
private fun decode(reader: MultiFormatReader, image: ImageProxy): String? {
    val plane = image.planes[0]
    val buf = plane.buffer
    val data = ByteArray(buf.remaining()).also { buf.get(it) }
    return QrDecoder.decode(reader, data, plane.rowStride, image.width, image.height)
}

object QrDecoder {
    fun reader() = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    /** [data]: Helligkeitswerte (Y-Ebene), [rowStride] Bytes pro Zeile. */
    fun decode(reader: MultiFormatReader, data: ByteArray, rowStride: Int, width: Int, height: Int): String? = try {
        val source = PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }
}
