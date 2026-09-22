package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.analytics.AnalyticsTracker
import com.example.analytics.FirebaseAnalyticsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Compression mode presets
 */
enum class ImageCompressionPreset(
    val title: String,
    val maxDimension: Int,
    val initialQuality: Int,
    val targetMaxBytes: Long
) {
    BALANCED_AUTO("Оптимальное (Telegram Fast)", 1280, 80, 350 * 1024L), // Max 1280px, ~350KB
    HIGH_QUALITY("Высокое качество (HD 1920p)", 1920, 88, 800 * 1024L), // Max 1920px, ~800KB
    DATA_SAVER("Экономия трафика (Fast 800p)", 800, 65, 150 * 1024L),    // Max 800px, ~150KB
    ORIGINAL("Без сжатия (Оригинал)", 4096, 100, 20 * 1024 * 1024L)
}

/**
 * Result of the image compression operation
 */
data class ImageCompressionResult(
    val originalUri: Uri?,
    val originalFileName: String,
    val originalSizeBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val compressedFile: File,
    val compressedUri: Uri,
    val compressedSizeBytes: Long,
    val compressedWidth: Int,
    val compressedHeight: Int,
    val compressionRatioPercent: Float, // e.g. 75.5% reduction
    val durationMs: Long,
    val presetUsed: ImageCompressionPreset,
    val mimeType: String = "image/jpeg"
) {
    val savedBytes: Long get() = (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0L)
    val savedKilobytes: Long get() = savedBytes / 1024L
}

/**
 * ImageCompressionManager provides high-performance asynchronous image compression
 * designed specifically for photo uploading before Firebase Storage dispatch.
 * 
 * Features:
 * - OOM-safe inSampleSize calculation
 * - EXIF orientation correction (prevents sideways or inverted photos)
 * - Proportional dimension downscaling (1280px / 1920px / 800px)
 * - Dynamic iterative quality optimization to fit bandwidth budgets
 * - Automatic logging to Firebase Analytics and Network Bandwidth Monitor
 */
object ImageCompressionManager {

    private const val TAG = "ImageCompressor"

    /**
     * Compress an image from Uri asynchronously on Dispatchers.IO.
     */
    suspend fun compressImage(
        context: Context,
        imageUri: Uri,
        originalName: String = "photo_${System.currentTimeMillis()}.jpg",
        preset: ImageCompressionPreset = ImageCompressionPreset.BALANCED_AUTO
    ): ImageCompressionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val appContext = context.applicationContext

        // 1. Get original file size
        var originalSize = 0L
        try {
            appContext.contentResolver.openFileDescriptor(imageUri, "r")?.use { pfd ->
                originalSize = pfd.statSize
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query original file size: ${e.message}")
        }

        if (originalSize <= 0L) {
            // Fallback: estimate from stream length
            try {
                appContext.contentResolver.openInputStream(imageUri)?.use { input ->
                    originalSize = input.available().toLong()
                }
            } catch (e: Exception) {}
        }

        // 2. Decode original dimensions without loading bitmap into memory (inJustDecodeBounds)
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        appContext.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }

        val origWidth = boundsOptions.outWidth.coerceAtLeast(1)
        val origHeight = boundsOptions.outHeight.coerceAtLeast(1)

        // If preset is ORIGINAL and file is already reasonably sized, create a direct copy
        if (preset == ImageCompressionPreset.ORIGINAL) {
            val cacheFile = createTempCacheFile(appContext, originalName)
            appContext.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            val compressedSize = cacheFile.length()
            val duration = System.currentTimeMillis() - startTime

            return@withContext ImageCompressionResult(
                originalUri = imageUri,
                originalFileName = originalName,
                originalSizeBytes = if (originalSize > 0) originalSize else compressedSize,
                originalWidth = origWidth,
                originalHeight = origHeight,
                compressedFile = cacheFile,
                compressedUri = Uri.fromFile(cacheFile),
                compressedSizeBytes = compressedSize,
                compressedWidth = origWidth,
                compressedHeight = origHeight,
                compressionRatioPercent = 0f,
                durationMs = duration,
                presetUsed = preset
            )
        }

        // 3. Read EXIF Orientation
        val rotationDegrees = getExifRotation(appContext, imageUri)

        // 4. Calculate optimal inSampleSize to prevent OOM
        val sampleSize = calculateInSampleSize(origWidth, origHeight, preset.maxDimension, preset.maxDimension)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap: Bitmap? = appContext.contentResolver.openInputStream(imageUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }

        if (decodedBitmap == null) {
            throw IllegalStateException("Failed to decode image bitmap from URI: $imageUri")
        }

        // 5. Apply EXIF rotation & precise dimension scaling
        val scaledBitmap = scaleAndRotateBitmap(decodedBitmap, origWidth, origHeight, preset.maxDimension, rotationDegrees)
        if (scaledBitmap != decodedBitmap) {
            decodedBitmap.recycle()
        }

        val finalWidth = scaledBitmap.width
        val finalHeight = scaledBitmap.height

        // 6. Iterative Quality Compression to meet target bandwidth budget
        var quality = preset.initialQuality
        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        while (stream.size() > preset.targetMaxBytes && quality > 45) {
            stream.reset()
            quality -= 10
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        val compressedBytes = stream.toByteArray()
        scaledBitmap.recycle()

        // 7. Write to cache file
        val cleanName = originalName.substringBeforeLast(".") + "_compressed.jpg"
        val outputFile = createTempCacheFile(appContext, cleanName)
        FileOutputStream(outputFile).use { fos ->
            fos.write(compressedBytes)
            fos.flush()
        }

        val compressedSize = outputFile.length()
        val effectiveOriginalSize = if (originalSize > 0) originalSize else (origWidth * origHeight * 4L).coerceAtLeast(compressedSize)
        val savedPercent = if (effectiveOriginalSize > 0) {
            ((effectiveOriginalSize - compressedSize).toFloat() / effectiveOriginalSize.toFloat() * 100f).coerceIn(0f, 99.9f)
        } else 0f

        val duration = System.currentTimeMillis() - startTime

        val result = ImageCompressionResult(
            originalUri = imageUri,
            originalFileName = originalName,
            originalSizeBytes = effectiveOriginalSize,
            originalWidth = origWidth,
            originalHeight = origHeight,
            compressedFile = outputFile,
            compressedUri = Uri.fromFile(outputFile),
            compressedSizeBytes = compressedSize,
            compressedWidth = finalWidth,
            compressedHeight = finalHeight,
            compressionRatioPercent = savedPercent,
            durationMs = duration,
            presetUsed = preset
        )

        // 8. Log Analytics & Bandwidth Telemetry
        logCompressionMetrics(result)

        Log.i(
            TAG,
            "⚡ [Image Compression Completed] $originalName: " +
            "${effectiveOriginalSize / 1024} KB -> ${compressedSize / 1024} KB (-${String.format("%.1f", savedPercent)}%), " +
            "${origWidth}x${origHeight} -> ${finalWidth}x${finalHeight}, Duration: ${duration}ms"
        )

        result
    }

    /**
     * Compress bitmap in-memory (e.g. from camera capture)
     */
    suspend fun compressBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "camera_photo_${System.currentTimeMillis()}.jpg",
        preset: ImageCompressionPreset = ImageCompressionPreset.BALANCED_AUTO
    ): ImageCompressionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val appContext = context.applicationContext
        val origWidth = bitmap.width
        val origHeight = bitmap.height
        val originalEstimatedSize = (bitmap.byteCount).toLong()

        val scaledBitmap = scaleAndRotateBitmap(bitmap, origWidth, origHeight, preset.maxDimension, 0)
        val finalWidth = scaledBitmap.width
        val finalHeight = scaledBitmap.height

        var quality = preset.initialQuality
        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        while (stream.size() > preset.targetMaxBytes && quality > 45) {
            stream.reset()
            quality -= 10
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        val compressedBytes = stream.toByteArray()
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        val outputFile = createTempCacheFile(appContext, fileName)
        FileOutputStream(outputFile).use { fos ->
            fos.write(compressedBytes)
            fos.flush()
        }

        val compressedSize = outputFile.length()
        val savedPercent = if (originalEstimatedSize > 0) {
            ((originalEstimatedSize - compressedSize).toFloat() / originalEstimatedSize.toFloat() * 100f).coerceIn(0f, 99.9f)
        } else 0f

        val duration = System.currentTimeMillis() - startTime

        val result = ImageCompressionResult(
            originalUri = null,
            originalFileName = fileName,
            originalSizeBytes = originalEstimatedSize,
            originalWidth = origWidth,
            originalHeight = origHeight,
            compressedFile = outputFile,
            compressedUri = Uri.fromFile(outputFile),
            compressedSizeBytes = compressedSize,
            compressedWidth = finalWidth,
            compressedHeight = finalHeight,
            compressionRatioPercent = savedPercent,
            durationMs = duration,
            presetUsed = preset
        )

        logCompressionMetrics(result)
        result
    }

    private fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun getExifRotation(context: Context, uri: Uri): Int {
        var rotation = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                rotation = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation: ${e.message}")
        }
        return rotation
    }

    private fun scaleAndRotateBitmap(
        src: Bitmap,
        origWidth: Int,
        origHeight: Int,
        maxDimension: Int,
        rotationDegrees: Int
    ): Bitmap {
        val currentW = src.width
        val currentH = src.height

        val maxSrcDim = maxOf(currentW, currentH)
        val scale = if (maxSrcDim > maxDimension) {
            maxDimension.toFloat() / maxSrcDim.toFloat()
        } else {
            1.0f
        }

        val matrix = Matrix()
        if (scale < 1.0f) {
            matrix.postScale(scale, scale)
        }
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }

        return if (scale < 1.0f || rotationDegrees != 0) {
            Bitmap.createBitmap(src, 0, 0, currentW, currentH, matrix, true)
        } else {
            src
        }
    }

    private fun createTempCacheFile(context: Context, fileName: String): File {
        val cacheDir = File(context.cacheDir, "compressed_uploads").apply {
            if (!exists()) mkdirs()
        }
        val safeName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(cacheDir, "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}_$safeName")
    }

    private fun logCompressionMetrics(result: ImageCompressionResult) {
        try {
            AnalyticsTracker.logChatAction(
                action = "photo_compression_executed",
                chatId = "media_pipeline",
                metadata = mapOf(
                    "original_size_kb" to (result.originalSizeBytes / 1024L),
                    "compressed_size_kb" to (result.compressedSizeBytes / 1024L),
                    "saved_bandwidth_kb" to result.savedKilobytes,
                    "savings_percent" to result.compressionRatioPercent,
                    "duration_ms" to result.durationMs,
                    "preset" to result.presetUsed.name,
                    "resolution" to "${result.compressedWidth}x${result.compressedHeight}"
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error logging compression analytics: ${e.message}")
        }
    }
}
