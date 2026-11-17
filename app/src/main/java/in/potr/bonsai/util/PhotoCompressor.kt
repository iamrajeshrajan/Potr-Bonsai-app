package `in`.potr.bonsai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresses high-resolution 50MP camera captures from the Nothing Phone down to
 * clean 1600px JPEG files (~200-350 KB) on-device before uploading to Google Drive or potr.in.
 */
object PhotoCompressor {

    data class CompressedPhoto(
        val file: File,
        val bytes: ByteArray,
        val dataUrl: String,
        val width: Int,
        val height: Int
    )

    fun compressUriToJpeg(
        context: Context,
        sourceUri: Uri,
        specimenCode: String,
        maxDimensionPx: Int = 1600,
        jpegQuality: Int = 82
    ): CompressedPhoto {
        // 1. Decode bounds only
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }

        var sampleSize = 1
        val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        if (longestSide > maxDimensionPx) {
            sampleSize = (longestSide.toFloat() / maxDimensionPx.toFloat()).roundToInt().coerceAtLeast(1)
        }

        // 2. Decode subsampled bitmap
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalStateException("Could not decode image from URI: $sourceUri")

        // 3. Respect EXIF orientation
        val rotationDegrees = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            val exif = ExifInterface(input)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val orientedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }

        // 4. Scale down precisely to maxDimensionPx if still larger
        val curMax = max(orientedBitmap.width, orientedBitmap.height)
        val finalBitmap = if (curMax > maxDimensionPx) {
            val scale = maxDimensionPx.toFloat() / curMax.toFloat()
            val targetW = (orientedBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (orientedBitmap.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
        } else {
            orientedBitmap
        }

        // 5. Encode to JPEG byte array
        val baos = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
        val jpegBytes = baos.toByteArray()

        val outDir = File(context.filesDir, "compressed").apply { mkdirs() }
        val safeCode = specimenCode.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(outDir, "${safeCode}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { it.write(jpegBytes) }

        val base64Str = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$base64Str"

        return CompressedPhoto(
            file = outFile,
            bytes = jpegBytes,
            dataUrl = dataUrl,
            width = finalBitmap.width,
            height = finalBitmap.height
        )
    }
}
