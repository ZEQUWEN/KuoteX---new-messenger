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
        .addMigrations(MIGRATION_26_27, MIGRATION_27_28)
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

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `group_invite_links` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `createdByUserId` TEXT NOT NULL,
                        `createdByName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER,
                        `maxUses` INTEGER,
                        `usageCount` INTEGER NOT NULL,
                        `requiresApproval` INTEGER NOT NULL,
                        `isPrimary` INTEGER NOT NULL,
                        `isRevoked` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_invite_links_chatId` ON `group_invite_links` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_invite_links_url` ON `group_invite_links` (`url`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `group_join_requests` (
                        `id` TEXT NOT NULL,
                        `chatId` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `userName` TEXT NOT NULL,
                        `userAvatarUrl` TEXT NOT NULL,
                        `inviteLinkId` TEXT,
                        `inviteLinkLabel` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `requestedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_join_requests_chatId` ON `group_join_requests` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_join_requests_userId` ON `group_join_requests` (`userId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_privacy_rules` (
                        `userId` TEXT NOT NULL,
                        `groupInvitePrivacy` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`)
                    )
                    """.trimIndent()
                )

                db.execSQL("ALTER TABLE `messages` ADD COLUMN `senderIdentityId` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `senderIdentityType` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `senderDisplayName` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `senderAvatarUrl` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `senderCustomTag` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `senderEmojiStatus` TEXT")
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
