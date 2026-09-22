package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log
import kotlinx.coroutines.flow.first

class MessageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("MessageSyncWorker", "Starting background Room-to-Firebase sync...")
        return try {
            val db = com.example.data.SecureDatabaseHelper.getInstance(applicationContext).database
            val sharedPrefs = applicationContext.getSharedPreferences("neon_messenger_prefs", Context.MODE_PRIVATE)
            val okHttpClient = com.example.data.NetworkModule.provideOkHttpClient(applicationContext) { _, _, _, _ -> }
            val webSocketManager = com.example.data.WebSocketManager(okHttpClient)

            val repository = MessengerRepository(
                db.botDao(),
                db.userDao(),
                db.chatDao(),
                db.messageDao(),
                db.groupMemberDao(),
                db.draftDao(),
                db.contactDao(),
                db.paymentTransactionDao(),
                db.queuedMessageDao(),
                sharedPrefs,
                webSocketManager
            )

            // Auto-push all Room-cached outgoing messages to Firebase silently
            val syncedCount = FirebaseMessageSyncManager.syncAllCachedMessages(
                repository = repository,
                signalProtocolManager = null
            )
            Log.d("MessageSyncWorker", "Silent background sync completed. Pushed $syncedCount messages.")
            
            Result.success()
        } catch (e: Exception) {
            Log.e("MessageSyncWorker", "Background sync failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
