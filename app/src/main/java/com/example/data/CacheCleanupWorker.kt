package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("CacheCleanupWorker", "Starting periodic cache cleanup...")
        try {
            val cacheDir = applicationContext.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                val freedSpace = deleteOldFiles(cacheDir, 7L * 24 * 60 * 60 * 1000) // 7 days in ms
                Log.d("CacheCleanupWorker", "Freed $freedSpace bytes from internal cache")
            }
            
            val externalCacheDir = applicationContext.externalCacheDir
            if (externalCacheDir != null && externalCacheDir.exists()) {
                val freedSpace = deleteOldFiles(externalCacheDir, 7L * 24 * 60 * 60 * 1000)
                Log.d("CacheCleanupWorker", "Freed $freedSpace bytes from external cache")
            }
            
            Log.d("CacheCleanupWorker", "Periodic cache cleanup completed.")
            Result.success()
        } catch (e: Exception) {
            Log.e("CacheCleanupWorker", "Periodic cache cleanup failed", e)
            Result.retry()
        }
    }

    private fun deleteOldFiles(dir: File, ageLimitMs: Long): Long {
        var freedBytes = 0L
        val currentTime = System.currentTimeMillis()
        val children = dir.listFiles()
        if (children != null) {
            for (child in children) {
                if (child.isDirectory) {
                    freedBytes += deleteOldFiles(child, ageLimitMs)
                    // If directory is empty now, we might want to delete it, but let's just leave directories alone
                } else {
                    val lastModified = child.lastModified()
                    if (currentTime - lastModified > ageLimitMs) {
                        val size = child.length()
                        if (child.delete()) {
                            freedBytes += size
                        }
                    }
                }
            }
        }
        return freedBytes
    }
}
