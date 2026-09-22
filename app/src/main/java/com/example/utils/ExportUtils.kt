package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportUtils {
    fun exportCacheToZip(context: Context): File? {
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val zipFile = File(exportDir, "cache_export_${System.currentTimeMillis()}.zip")
            
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val entry = ZipEntry("export_info.txt")
                zos.putNextEntry(entry)
                zos.write("Exported cache data".toByteArray())
                zos.closeEntry()
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Экспорт кэша"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
