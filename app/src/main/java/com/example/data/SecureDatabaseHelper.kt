package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        .addMigrations(MIGRATION_26_27)
        .fallbackToDestructiveMigration()
        .build()
    }

    companion object {
        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_ratings` (
                        `userId` TEXT NOT NULL,
                        `totalPoints` INTEGER NOT NULL,
                        `directGiftStarsPoints` INTEGER NOT NULL,
                        `marketAndPostsPoints` INTEGER NOT NULL,
                        `activityPoints` INTEGER NOT NULL,
                        `refundPenaltyPoints` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: SecureDatabaseHelper? = null

        fun getInstance(context: Context): SecureDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureDatabaseHelper(context).also { INSTANCE = it }
            }
        }
    }
}
