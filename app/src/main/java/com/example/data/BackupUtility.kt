package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupUtility {
    private const val TAG = "BackupUtility"

    suspend fun exportDatabase(context: Context, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dbName = "messenger_database_encrypted"
            val dbFile = context.getDatabasePath(dbName)
            val walFile = context.getDatabasePath("$dbName-wal")
            val shmFile = context.getDatabasePath("$dbName-shm")

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    val filesToBackup = listOf(dbFile, walFile, shmFile).filter { it.exists() }
                    
                    for (file in filesToBackup) {
                        FileInputStream(file).use { fis ->
                            val zipEntry = ZipEntry(file.name)
                            zipOut.putNextEntry(zipEntry)
                            fis.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                    }
                }
            }
            Log.i(TAG, "Database exported successfully to $destinationUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export database", e)
            false
        }
    }
}
