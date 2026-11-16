package com.example.ui.channel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * ChannelWallpaperStorage
 * Persistently stores custom channel wallpapers picked via zero-permission Android Photo Picker
 * into private app filesDir so Coil always renders them reliably across restarts.
 */
object ChannelWallpaperStorage {
    private const val TAG = "ChannelWallpaperStorage"
    private const val DIR_NAME = "channel_wallpapers"

    private fun getDirectory(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getWallpaperFile(context: Context, chatId: String): File {
        val cleanId = chatId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(getDirectory(context), "wp_${cleanId}.jpg")
    }

    suspend fun saveWallpaperFromUri(
        context: Context,
        sourceUri: Uri,
        chatId: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val targetFile = getWallpaperFile(context, chatId)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    val scaled = scaleDownIfNeeded(bitmap, 1600)
                    FileOutputStream(targetFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.flush()
                    }
                    if (scaled !== bitmap) {
                        scaled.recycle()
                    }
                    bitmap.recycle()
                } else {
                    context.contentResolver.openInputStream(sourceUri)?.use { fallback ->
                        FileOutputStream(targetFile).use { out ->
                            fallback.copyTo(out)
                            out.flush()
                        }
                    }
                }
            }
            "file://${targetFile.absolutePath}?t=${System.currentTimeMillis()}"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save channel wallpaper for $chatId", e)
            sourceUri.toString()
        }
    }

    fun deleteCustomWallpaper(context: Context, chatId: String) {
        try {
            val file = getWallpaperFile(context, chatId)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete channel wallpaper for $chatId", e)
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
