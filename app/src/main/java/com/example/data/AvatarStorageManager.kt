package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

enum class EntityType(val prefix: String) {
    ACCOUNT("user"),
    CHANNEL("channel"),
    GROUP("group"),
    BOT("bot")
}

data class AvatarUpdateEvent(
    val entityType: EntityType,
    val entityId: String,
    val avatarPath: String
)

/**
 * AvatarStorageManager
 * Provides rock-solid, persistent storage for avatars across accounts, channels, groups, and bots.
 * Saves picked images directly into the application's internal private storage directory,
 * ensuring they never expire, survive app restarts and re-entering profile screens.
 */
object AvatarStorageManager {
    private const val TAG = "AvatarStorageManager"
    private const val PREFS_NAME = "kuotex_avatar_prefs"
    private const val DIR_NAME = "avatars"

    private val _avatarEvents = MutableSharedFlow<AvatarUpdateEvent>(extraBufferCapacity = 64)
    val avatarEvents: SharedFlow<AvatarUpdateEvent> = _avatarEvents.asSharedFlow()

    private fun getAvatarDirectory(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getAvatarFile(context: Context, entityType: EntityType, entityId: String): File {
        val cleanId = entityId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(getAvatarDirectory(context), "${entityType.prefix}_${cleanId}.jpg")
    }

    /**
     * Persistently copies and stores an avatar image from a content Uri into internal storage.
     * Returns a stable file:// URI with cache-busting timestamp.
     */
    suspend fun saveAvatarFromUri(
        context: Context,
        sourceUri: Uri,
        entityType: EntityType,
        entityId: String
    ): String = withContext(Dispatchers.IO) {
        try {
            val targetFile = getAvatarFile(context, entityType, entityId)
            
            // Read input stream and decode/compress bitmap to ensure valid image data
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (inputStream != null) {
                inputStream.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        FileOutputStream(targetFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            out.flush()
                        }
                    } else {
                        // Fallback direct byte copy if bitmap decoding was not needed
                        context.contentResolver.openInputStream(sourceUri)?.use { fallbackIn ->
                            FileOutputStream(targetFile).use { out ->
                                fallbackIn.copyTo(out)
                                out.flush()
                            }
                        }
                    }
                }
            }

            val savedUri = "file://${targetFile.absolutePath}?t=${System.currentTimeMillis()}"
            
            // Persist path in SharedPreferences as well
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "${entityType.prefix}_$entityId"
            prefs.edit().putString(key, targetFile.absolutePath).apply()

            _avatarEvents.tryEmit(AvatarUpdateEvent(entityType, entityId, savedUri))
            Log.d(TAG, "Saved avatar for $entityType $entityId at ${targetFile.absolutePath}")
            savedUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save avatar for $entityType $entityId", e)
            sourceUri.toString()
        }
    }

    /**
     * Retrieves the persistent avatar for an entity.
     * If a local file exists, returns file:// path.
     * Otherwise returns fallbackUrl or seed url.
     */
    fun getAvatar(
        context: Context,
        entityType: EntityType,
        entityId: String,
        fallbackUrl: String? = null
    ): String {
        try {
            val targetFile = getAvatarFile(context, entityType, entityId)
            if (targetFile.exists() && targetFile.length() > 0) {
                return "file://${targetFile.absolutePath}"
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = "${entityType.prefix}_$entityId"
            val savedPath = prefs.getString(key, null)
            if (!savedPath.isNullOrBlank()) {
                val f = File(savedPath)
                if (f.exists() && f.length() > 0) {
                    return "file://${f.absolutePath}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking avatar file", e)
        }

        if (!fallbackUrl.isNullOrBlank()) {
            return fallbackUrl
        }

        return "https://picsum.photos/seed/${entityType.prefix}_${entityId}/800"
    }

    /**
     * Directly store path in preferences
     */
    fun saveAvatarPathSync(context: Context, entityType: EntityType, entityId: String, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${entityType.prefix}_$entityId"
        prefs.edit().putString(key, path).apply()
        _avatarEvents.tryEmit(AvatarUpdateEvent(entityType, entityId, path))
    }

    /**
     * Checks if a custom avatar has been saved for this entity.
     */
    fun hasCustomAvatar(context: Context, entityType: EntityType, entityId: String): Boolean {
        val file = getAvatarFile(context, entityType, entityId)
        return file.exists() && file.length() > 0
    }

    /**
     * Retrieves the list of profile avatars for the user to support avatar carousel.
     */
    fun getUserAvatarList(context: Context, userId: String, currentAvatar: String? = null): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "user_avatars_$userId"
        val raw = prefs.getString(key, null)
        val list = mutableListOf<String>()
        if (!raw.isNullOrBlank()) {
            try {
                val jsonArray = org.json.JSONArray(raw)
                for (i in 0 until jsonArray.length()) {
                    val url = jsonArray.optString(i)
                    if (url.isNotBlank() && !list.contains(url)) {
                        list.add(url)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing avatars json", e)
            }
        }
        val persistent = getAvatar(context, EntityType.ACCOUNT, userId, currentAvatar)
        if (persistent.isNotBlank() && !list.contains(persistent)) {
            list.add(0, persistent)
        }
        if (list.size < 2) {
            val sample1 = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800"
            val sample2 = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800"
            if (!list.contains(sample1)) list.add(sample1)
            if (!list.contains(sample2)) list.add(sample2)
        }
        return list
    }

    /**
     * Adds an avatar URL to the top of the user's avatar carousel list.
     */
    fun addUserAvatar(context: Context, userId: String, avatarUrl: String) {
        val currentList = getUserAvatarList(context, userId).toMutableList()
        currentList.remove(avatarUrl)
        currentList.add(0, avatarUrl)
        val jsonArray = org.json.JSONArray()
        currentList.forEach { jsonArray.put(it) }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("user_avatars_$userId", jsonArray.toString()).apply()
        _avatarEvents.tryEmit(AvatarUpdateEvent(EntityType.ACCOUNT, userId, avatarUrl))
    }

    /**
     * Deletes an avatar from the user's avatar carousel list.
     */
    fun deleteUserAvatar(context: Context, userId: String, avatarUrl: String) {
        val currentList = getUserAvatarList(context, userId).toMutableList()
        currentList.remove(avatarUrl)
        val jsonArray = org.json.JSONArray()
        currentList.forEach { jsonArray.put(it) }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("user_avatars_$userId", jsonArray.toString()).apply()
    }
}
