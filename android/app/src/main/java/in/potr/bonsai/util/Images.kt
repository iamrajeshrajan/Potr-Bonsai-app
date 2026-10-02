package `in`.potr.bonsai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Hard limit agreed with Potr; the server refuses anything larger. */
const val MAX_PHOTO_BYTES = 100 * 1024

/** Long-side sizes tried in order; 1200 px is plenty for phone, certificate and shop. */
val PHOTO_SIDES = listOf(1200, 1000, 800, 640)

private const val MIN_QUALITY = 35
private const val MAX_QUALITY = 85

/** A fresh file Uri the camera app can write a capture into. */
fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/**
 * Finds the largest size and best quality whose encoding fits in [limit] bytes.
 * For each size (largest first) it binary-searches the quality, so a typical
 * photo needs 3–4 encodes. Pure function so it can be unit tested.
 */
fun fitUnderLimit(
    sides: List<Int>,
    limit: Int = MAX_PHOTO_BYTES,
    encode: (side: Int, quality: Int) -> ByteArray,
): ByteArray {
    for (side in sides) {
        var lo = MIN_QUALITY
        var hi = MAX_QUALITY
        var best: ByteArray? = null
        while (lo <= hi) {
            val q = (lo + hi) / 2
            val bytes = encode(side, q)
            if (bytes.size <= limit) {
                best = bytes
                lo = q + 1
            } else {
                hi = q - 1
            }
        }
        if (best != null) return best
    }
    error("Could not compress the photo under ${limit / 1024} KB")
}

/**
 * Turns a camera/gallery photo into a WebP under 100 KB: applies the EXIF
 * rotation, scales the long side to at most 1200 px and searches for the best
 * quality that fits. Re-encoding drops all metadata, including GPS location.
 */
fun compressForUpload(context: Context, uri: Uri): ByteArray {
    val source = decodeUpright(context, uri, PHOTO_SIDES.first())
    val scaled = HashMap<Int, Bitmap>()
    return fitUnderLimit(PHOTO_SIDES) { side, quality ->
        val bmp = scaled.getOrPut(side) { scaleToLongSide(source, side) }
        ByteArrayOutputStream().use {
            bmp.compress(webpFormat(), quality, it)
            it.toByteArray()
        }
    }
}

@Suppress("DEPRECATION")
private fun webpFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

private fun scaleToLongSide(bmp: Bitmap, side: Int): Bitmap {
    val scale = min(1f, side.toFloat() / max(bmp.width, bmp.height))
    if (scale >= 1f) return bmp
    return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
}

/** Decodes with subsampling (to keep memory low) and rotates per EXIF. */
private fun decodeUpright(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Could not read the photo")
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Could not read the photo")

    val rotation = resolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
    if (rotation == 0f && scale >= 1f) return decoded
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation)
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
