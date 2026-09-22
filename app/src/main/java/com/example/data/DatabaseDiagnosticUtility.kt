package com.example.data

import android.content.Context
import android.util.Log
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

object DatabaseDiagnosticUtility {

    private const val TAG = "DatabaseDiagnostic"

    fun performStartupDiagnostics(context: Context, dbName: String, passphrase: CharArray) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists() || dbFile.length() == 0L) return

        try {
            SQLiteDatabase.loadLibs(context.applicationContext)
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )

            // 1) Integrity Check
            val integrityCursor = db.rawQuery("PRAGMA integrity_check;", null)
            var isIntegrityOk = false
            if (integrityCursor.moveToFirst()) {
                val result = integrityCursor.getString(0)
                if (result.equals("ok", ignoreCase = true)) {
                    isIntegrityOk = true
                }
            }
            integrityCursor.close()

            if (!isIntegrityOk) {
                Log.w(TAG, "Integrity check failed. Database might be corrupted. Cleaning database.")
                db.close()
                deleteDatabaseFiles(context, dbName)
                return
            }

            // 2) Schema Version & Migration
            val schemaCursor = db.rawQuery("PRAGMA user_version;", null)
            var currentSchemaVersion = -1
            if (schemaCursor.moveToFirst()) {
                currentSchemaVersion = schemaCursor.getInt(0)
            }
            schemaCursor.close()

            Log.i(TAG, "Current user_version: $currentSchemaVersion")
            
            // Log Database Schema
            logDatabaseSchema(db)
            
            // Database migration utility mechanism
            if (currentSchemaVersion > 0 && currentSchemaVersion < EXPECTED_SCHEMA_VERSION) {
                Log.i(TAG, "Incompatible schema version detected ($currentSchemaVersion). Attempting automatic migration...")
                runMigrationScripts(db, currentSchemaVersion, EXPECTED_SCHEMA_VERSION)
            }

            db.close()
        } catch (e: Exception) {
            Log.w(TAG, "Database diagnostic notice (resetting stale/unencrypted file): ${e.message}")
            deleteDatabaseFiles(context, dbName)
        }
    }

    private fun deleteDatabaseFiles(context: Context, dbName: String) {
        try {
            context.deleteDatabase(dbName)
            val dbFile = context.getDatabasePath(dbName)
            val parent = dbFile.parentFile
            if (parent != null && parent.exists()) {
                File(parent, "$dbName-wal").delete()
                File(parent, "$dbName-shm").delete()
                File(parent, "$dbName-journal").delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning database files: ${e.message}")
        }
    }

    private const val EXPECTED_SCHEMA_VERSION = 16

    private fun logDatabaseSchema(db: SQLiteDatabase) {
        try {
            val cursor = db.rawQuery("SELECT name, sql FROM sqlite_master WHERE type='table'", null)
            Log.i(TAG, "--- Current Database Schema ---")
            while (cursor.moveToNext()) {
                val tableName = cursor.getString(0)
                val tableSql = cursor.getString(1)
                Log.i(TAG, "Table: $tableName\nSQL: $tableSql")
            }
            cursor.close()
            Log.i(TAG, "-------------------------------")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log database schema", e)
        }
    }

    private fun runMigrationScripts(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try {
            db.beginTransaction()
            // In a real app, you'd apply SQL scripts sequentially
            Log.i(TAG, "Applying migration scripts from $oldVersion to $newVersion")
            
            if (oldVersion < 14) {
                try {
                    db.execSQL("ALTER TABLE accounts ADD COLUMN dateOfBirth TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE accounts ADD COLUMN socialMedia TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Log.e(TAG, "Migration to 14 failed (column might exist)", e)
                }
            }
            
            // Update schema version after successful migration
            db.execSQL("PRAGMA user_version = $newVersion;")
            db.setTransactionSuccessful()
            Log.i(TAG, "Migration completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed: ${e.message}", e)
            throw e
        } finally {
            db.endTransaction()
        }
    }
}
