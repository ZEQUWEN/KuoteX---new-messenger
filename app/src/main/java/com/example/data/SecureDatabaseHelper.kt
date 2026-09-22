package com.example.data

import android.content.Context
import androidx.room.Room
import net.sqlcipher.database.SupportFactory

class SecureDatabaseHelper private constructor(context: Context) {

    val database: AppDatabase

    init {
        val appContext = context.applicationContext
        val dbName = "messenger_database_encrypted"
        
        val sqlCipherFactory = try {
            net.sqlcipher.database.SQLiteDatabase.loadLibs(appContext)
            val passphrase = CryptoManager.getDatabasePassphrase(appContext)
            val passphraseBytes = net.sqlcipher.database.SQLiteDatabase.getBytes(passphrase)
            try {
                DatabaseDiagnosticUtility.performStartupDiagnostics(appContext, dbName, passphrase)
            } catch (t: Throwable) {
                // Ignore diagnostic failures
            }
            SupportFactory(passphraseBytes)
        } catch (t: Throwable) {
            null
        }

        database = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            dbName
        ).apply {
            if (sqlCipherFactory != null) {
                openHelperFactory(sqlCipherFactory)
            }
        }
        .fallbackToDestructiveMigration()
        .build()
    }

    companion object {
        @Volatile
        private var INSTANCE: SecureDatabaseHelper? = null

        fun getInstance(context: Context): SecureDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureDatabaseHelper(context).also { INSTANCE = it }
            }
        }
    }
}
