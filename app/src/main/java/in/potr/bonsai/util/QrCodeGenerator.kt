package `in`.potr.bonsai.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    fun generateBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val pixels = IntArray(sizePx * sizePx)
            val fg = Color.parseColor("#1F2421")
            val bg = Color.WHITE
            for (y in 0 until sizePx) {
                val rowOffset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[rowOffset + x] = if (matrix.get(x, y)) fg else bg
                }
            }
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            }
        } catch (_: Exception) {
            null
        }
    }
}
