package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.analytics.AnalyticsTracker
import com.example.data.AppDatabase
import com.example.data.CryptoManager
import com.example.data.FirebaseMessageSyncManager
import com.example.data.SecureDatabaseHelper
import com.example.data.repository.FirestoreChatRepositoryImpl
import com.example.ui.Chat
import com.example.ui.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager Worker that periodically synchronizes local Room database
 * caches with the Firebase / Firestore backend silently without user notifications.
 *
 * Responsibilities:
 * 1. Synchronize outgoing offline queued messages to Firestore.
 * 2. Fetch remote incoming messages and conversation metadata into local Room cache.
 * 3. Update user profiles, roles, and contacts from Firestore into Room.
 * 4. Clean up expired disappearing messages silently.
 */
class FirestoreBackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PERIODIC = "firestore_periodic_cache_sync"
        const val WORK_NAME_EXPEDIENT = "firestore_immediate_cache_sync"
        private const val TAG = "FirestoreSyncWorker"
        private const val DEFAULT_SYNC_INTERVAL_MINUTES = 15L
        private const val DEFAULT_FLEX_INTERVAL_MINUTES = 5L

        /**
         * Schedules periodic background sync using Android WorkManager.
         * Runs quietly every [intervalMinutes] when network connectivity is available.
         */
        fun schedulePeriodicSync(
            context: Context,
            intervalMinutes: Long = DEFAULT_SYNC_INTERVAL_MINUTES,
            flexIntervalMinutes: Long = DEFAULT_FLEX_INTERVAL_MINUTES
        ) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicWorkRequest = PeriodicWorkRequestBuilder<FirestoreBackgroundSyncWorker>(
                    intervalMinutes, TimeUnit.MINUTES,
                    flexIntervalMinutes, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                    )
                    .addTag("firestore_sync")
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest
                )

                Log.d(TAG, "Scheduled periodic background cache sync (every $intervalMinutes min)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to schedule periodic sync: ${e.message}")
            }
        }

        /**
         * Triggers an immediate one-time background sync pass (e.g. on network reconnection).
         */
        fun syncImmediately(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneTimeWork = OneTimeWorkRequestBuilder<FirestoreBackgroundSyncWorker>()
                    .setConstraints(constraints)
                    .addTag("firestore_sync_immediate")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_EXPEDIENT,
                    ExistingWorkPolicy.REPLACE,
                    oneTimeWork
                )

                Log.d(TAG, "Triggered immediate background cache sync")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to trigger immediate sync: ${e.message}")
            }
        }

        /**
         * Cancels all scheduled background sync tasks.
         */
        fun cancelSync(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_EXPEDIENT)
                Log.d(TAG, "Cancelled background cache sync tasks")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel sync tasks: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting silent background Firestore cache sync...")

        val db = try {
            SecureDatabaseHelper.getInstance(applicationContext).database
        } catch (e: Exception) {
            Log.e(TAG, "Failed to access local Room DB", e)
            return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        try {
            var totalSyncedMessages = 0
            var totalSyncedChats = 0

            // 1. Sync pending offline queued messages to Firestore & Firebase
            try {
                val queuedDao = db.queuedMessageDao()
                val messageDao = db.messageDao()
                val firestoreChatRepo = FirestoreChatRepositoryImpl(
                    chatDao = db.chatDao(),
                    messageDao = messageDao,
                    queuedMessageDao = queuedDao,
                    draftDao = db.draftDao()
                )
                val syncResult = firestoreChatRepo.syncPendingMessages()
                if (syncResult.isSuccess) {
                    totalSyncedMessages += syncResult.getOrDefault(0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error pushing queued messages: ${e.message}")
            }

            // 2. Fetch updated Firestore remote chats and messages into Room cache
            try {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "current_user"
                val firestore = FirebaseFirestore.getInstance()

                // Query all direct/group chats where current user is a participant
                val chatsSnapshot = firestore.collection("chats")
                    .whereArrayContains("participants", currentUserId)
                    .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                val chatDao = db.chatDao()
                val messageDao = db.messageDao()

                for (chatDoc in chatsSnapshot.documents) {
                    val chatId = chatDoc.getString("id") ?: chatDoc.id
                    val participants = (chatDoc.get("participants") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    val otherUserId = participants.firstOrNull { it != currentUserId } ?: ""
                    val participantNames = chatDoc.get("participantNames") as? Map<*, *>
                    val title = (participantNames?.get(otherUserId) as? String)?.ifBlank { null }
                        ?: chatDoc.getString("title")
                        ?: "Chat with $otherUserId"
                    val lastMessage = chatDoc.getString("lastMessage") ?: ""
                    val lastTimestamp = chatDoc.getLong("lastMessageTimestamp") ?: System.currentTimeMillis()
                    val unreadMap = chatDoc.get("unreadCounts") as? Map<*, *>
                    val unread = (unreadMap?.get(currentUserId) as? Long)?.toInt() ?: 0

                    val existingChat = chatDao.getChatById(chatId)
                    if (existingChat != null) {
                        chatDao.insertChat(
                            existingChat.copy(
                                lastMessage = lastMessage,
                                lastMessageTimestamp = lastTimestamp,
                                unreadCount = unread
                            )
                        )
                    } else {
                        chatDao.insertChat(
                            Chat(
                                id = chatId,
                                title = title,
                                lastMessage = lastMessage,
                                lastMessageTimestamp = lastTimestamp,
                                unreadCount = unread
                            )
                        )
                    }
                    totalSyncedChats++

                    // Pull latest messages for this chat and update Room cache
                    try {
                        val messagesSnapshot = chatDoc.reference.collection("messages")
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .limit(30)
                            .get()
                            .await()

                        for (msgDoc in messagesSnapshot.documents) {
                            val msgId = msgDoc.getString("id") ?: msgDoc.id
                            val senderId = msgDoc.getString("senderId") ?: continue
                            val text = msgDoc.getString("text") ?: ""
                            val timestamp = msgDoc.getLong("timestamp") ?: System.currentTimeMillis()
                            val isDelivered = msgDoc.getBoolean("isDelivered") ?: true
                            val isRead = msgDoc.getBoolean("isRead") ?: false
                            val audioPath = msgDoc.getString("audioPath")
                            val mediaPath = msgDoc.getString("mediaPath")
                            val mediaType = msgDoc.getString("mediaType")
                            val documentData = msgDoc.getString("documentData")
                            val locationData = msgDoc.getString("locationData")
                            val replyToMessageId = msgDoc.getString("replyToMessageId")
                            val replyToMessageText = msgDoc.getString("replyToMessageText")
                            val reaction = msgDoc.getString("reaction")
                            val expiresAt = msgDoc.getLong("expiresAt")

                            val encryptedMsg = Message(
                                id = msgId,
                                chatId = chatId,
                                senderId = senderId,
                                text = CryptoManager.encrypt(text),
                                audioPath = audioPath?.let { CryptoManager.encrypt(it) },
                                mediaPath = mediaPath?.let { CryptoManager.encrypt(it) },
                                mediaType = mediaType,
                                documentData = documentData?.let { CryptoManager.encrypt(it) },
                                locationData = locationData,
                                replyToMessageId = replyToMessageId,
                                replyToMessageText = replyToMessageText,
                                timestamp = timestamp,
                                reaction = reaction,
                                expiresAt = expiresAt,
                                isDelivered = isDelivered,
                                isRead = isRead
                            )
                            messageDao.insertMessage(encryptedMsg)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Messages sync for chat $chatId: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Chats pull skipped or offline: ${e.message}")
            }

            // 3. Purge expired disappearing messages silently
            try {
                db.messageDao().deleteExpiredMessages(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.d(TAG, "Expired messages purge: ${e.message}")
            }

            Log.d(TAG, "✅ Silent background Firestore cache sync complete: $totalSyncedMessages messages pushed, $totalSyncedChats chats refreshed.")
            
            AnalyticsTracker.logEvent(
                "firestore_cache_synced",
                mapOf(
                    "messages_pushed" to totalSyncedMessages,
                    "chats_refreshed" to totalSyncedChats
                )
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore background sync encountered error", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
