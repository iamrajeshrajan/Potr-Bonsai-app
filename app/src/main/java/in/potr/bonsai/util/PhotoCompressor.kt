package `in`.potr.bonsai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresses high-resolution phone camera captures down to crisp 1600px JPEG files (~200-300 KB)
 * stored locally on disk before uploading to Google Drive or potr.in.
 */
object PhotoCompressor {

    data class CompressedPhoto(
        val file: File,
        val width: Int,
        val height: Int,
        val sizeBytes: Long
    )

    fun createTempCameraUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = uriForFile(context, file)
        return file to uri
    }

    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun stageVideoFromUri(context: Context, sourceUri: Uri, prefix: String): File {
        val outDir = File(context.filesDir, "videos").apply { mkdirs() }
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(outDir, "${safePrefix}_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open selected video")
        return outFile
    }

    fun compressUriToJpeg(
        context: Context,
        sourceUri: Uri,
        specimenCode: String,
        maxDimensionPx: Int = 1600,
        jpegQuality: Int = 82
    ): CompressedPhoto {
        // 1. Decode image bounds
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }

        val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        var sampleSize = 1
        while (longestSide > 0 && (longestSide / (sampleSize * 2)) >= maxDimensionPx) {
            sampleSize *= 2
        }

        // 2. Decode subsampled bitmap
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalStateException("Could not decode image from selected photo")

        // 3. Respect EXIF orientation (fault-tolerant for OEM camera headers)
        val rotationDegrees = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        val orientedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                if (it !== rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        // 4. Scale down precisely to maxDimensionPx if still larger
        val curMax = max(orientedBitmap.width, orientedBitmap.height)
        val finalBitmap = if (curMax > maxDimensionPx) {
            val scale = maxDimensionPx.toFloat() / curMax.toFloat()
            val targetW = (orientedBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (orientedBitmap.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true).also {
                if (it !== orientedBitmap) orientedBitmap.recycle()
            }
        } else {
            orientedBitmap
        }

        val width = finalBitmap.width
        val height = finalBitmap.height

        // 5. Encode to JPEG file on disk
        val baos = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
        finalBitmap.recycle()
        val jpegBytes = baos.toByteArray()

        val outDir = File(context.filesDir, "compressed").apply { mkdirs() }
        val safeCode = specimenCode.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outFile = File(outDir, "${safeCode}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { it.write(jpegBytes) }

        return CompressedPhoto(
            file = outFile,
            width = width,
            height = height,
            sizeBytes = jpegBytes.size.toLong()
        )
    }
}
