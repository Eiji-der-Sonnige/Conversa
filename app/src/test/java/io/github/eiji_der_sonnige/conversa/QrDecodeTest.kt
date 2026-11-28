package io.github.eiji_der_sonnige.conversa

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.eiji_der_sonnige.conversa.crypto.Npub
import io.github.eiji_der_sonnige.conversa.crypto.Secp256k1
import io.github.eiji_der_sonnige.conversa.crypto.toHex
import io.github.eiji_der_sonnige.conversa.ui.QrDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

/** Der Scanner liest den QR-Code, den die App unter „Deine Kontakt-ID“ anzeigt. */
class QrDecodeTest {
    @Test
    fun npubQrRoundTrip() {
        val npub = Npub.encode(Secp256k1.pubKeyXOnly(Secp256k1.generatePrivKey()).toHex())
        val size = 400
        val matrix = QRCodeWriter().encode(npub, BarcodeFormat.QR_CODE, size, size)
        // Wie ein Kamerabild: Helligkeit pro Pixel, mit Zeilenauffüllung (rowStride > Breite)
        val stride = size + 16
        val y = ByteArray(stride * size) { i ->
            val x = i % stride
            val row = i / stride
            if (x < size && matrix.get(x, row)) 20 else 230.toByte()
        }
        assertEquals(npub, QrDecoder.decode(QrDecoder.reader(), y, stride, size, size))
    }
}
