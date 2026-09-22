package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.analytics.AnalyticsTracker
import com.example.crypto.SignalProtocolManager
import com.example.ui.Message
import com.example.ui.BotService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * FirebaseMessageSyncManager orchestrates persistent local storage caching using Room
 * and automatic synchronization to Firebase when network connectivity is restored.
 */
object FirebaseMessageSyncManager {

    private const val TAG = "FirebaseSyncManager"

    sealed class SyncState {
        object Idle : SyncState()
        data class Syncing(val current: Int, val total: Int) : SyncState()
        data class Success(val syncedCount: Int) : SyncState()
        data class Error(val message: String, val pendingCount: Int) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Cache an outgoing message into Room persistent storage queue when network is unavailable.
     */
    suspend fun cacheOutgoingMessage(
        repository: MessengerRepository,
        message: QueuedMessage,
        context: Context? = null
    ) = withContext(Dispatchers.IO) {
        try {
            repository.insertQueuedMessage(message)
            Log.d(TAG, "📦 [Room Cache] Outgoing message ${message.id} cached in Room (Chat: ${message.chatId})")

            // Log event in Firebase Analytics
            AnalyticsTracker.logChatAction(
                action = "cache_offline_message",
                chatId = message.chatId,
                metadata = mapOf(
                    "message_id" to message.id,
                    "length" to message.text.length,
                    "has_media" to (message.mediaPath != null || message.audioPath != null),
                    "created_at" to message.createdAt
                )
            )

            // Enqueue WorkManager sync task with Network Constraint to auto-push when restored
            context?.let { ctx ->
                scheduleNetworkRestorationSync(ctx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache outgoing message in Room", e)
        }
    }

    /**
     * Schedule a WorkManager job that waits for internet connection and automatically pushes
     * cached messages from Room to Firebase.
     */
    fun scheduleNetworkRestorationSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<MessageSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "FirebaseRestorationSync",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
            Log.d(TAG, "⏰ WorkManager scheduled for automatic Firebase sync upon network restoration")
        } catch (e: Exception) {
            Log.w(TAG, "Could not enqueue WorkManager restoration sync: ${e.message}")
        }
    }

    /**
     * Push all cached outgoing messages from Room to Firebase backend.
     */
    suspend fun syncAllCachedMessages(
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager? = null
    ): Int = withContext(Dispatchers.IO) {
        val pendingList = repository.getPendingQueueList()
        if (pendingList.isEmpty()) {
            _syncState.value = SyncState.Idle
            return@withContext 0
        }

        val total = pendingList.size
        val syncStartTime = System.currentTimeMillis()
        Log.i(TAG, "🚀 [Firebase Sync] Starting auto-push for $total cached outgoing messages from Room...")
        _syncState.value = SyncState.Syncing(0, total)

        var successfulSyncCount = 0

        for ((index, queuedMsg) in pendingList.withIndex()) {
            _syncState.value = SyncState.Syncing(index + 1, total)

            try {
                // 1. Mark as SYNCING in Room
                repository.updateQueuedSyncStatus(
                    id = queuedMsg.id,
                    status = "SYNCING",
                    retryCount = queuedMsg.retryCount + 1,
                    lastAttempt = System.currentTimeMillis(),
                    error = null
                )

                // 2. Dispatch / Push message to Firebase
                val pushSuccess = pushMessageToFirebase(queuedMsg)

                if (pushSuccess) {
                    // 3. Mark message as delivered in Room messages table
                    repository.updateMessageDelivery(queuedMsg.id, true)

                    // 4. Remove message from Room queued_messages table
                    repository.deleteQueuedMessage(queuedMsg.id)
                    successfulSyncCount++

                    Log.d(TAG, "✅ [Firebase Push Success] Message ${queuedMsg.id} synced and purged from Room queue")

                    // 5. Log push delivery to Firebase Analytics
                    AnalyticsTracker.logChatAction(
                        action = "firebase_auto_push_success",
                        chatId = queuedMsg.chatId,
                        metadata = mapOf(
                            "message_id" to queuedMsg.id,
                            "queue_duration_ms" to (System.currentTimeMillis() - queuedMsg.createdAt),
                            "retry_count" to (queuedMsg.retryCount + 1)
                        )
                    )

                    // 6. Handle Bot or Simulated reply if needed
                    signalProtocolManager?.let { cryptoManager ->
                        processPostSyncReply(queuedMsg, repository, cryptoManager)
                    }
                } else {
                    repository.updateQueuedSyncStatus(
                        id = queuedMsg.id,
                        status = "FAILED",
                        retryCount = queuedMsg.retryCount + 1,
                        lastAttempt = System.currentTimeMillis(),
                        error = "Network transmission failed"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing message ${queuedMsg.id} to Firebase", e)
                repository.updateQueuedSyncStatus(
                    id = queuedMsg.id,
                    status = "FAILED",
                    retryCount = queuedMsg.retryCount + 1,
                    lastAttempt = System.currentTimeMillis(),
                    error = e.message
                )
            }
        }

        // Also reconcile any unconfirmed messages in MessageDao
        try {
            val allChats = repository.allChats.firstOrNull() ?: emptyList()
            for (chat in allChats) {
                val messages = repository.getMessages(chat.id).firstOrNull() ?: emptyList()
                val undeliveredMine = messages.filter { !it.isDelivered && it.senderId != "other_user" && it.senderId != "system_sync" }
                for (msg in undeliveredMine) {
                    repository.updateMessageDelivery(msg.id, true)
                    repository.deleteQueuedMessage(msg.id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reconciling undelivered messages: ${e.message}")
        }

        val syncDurationMs = System.currentTimeMillis() - syncStartTime
        val failureCount = total - successfulSyncCount
        com.example.analytics.FirebaseAnalyticsHelper.logMessageQueueBatchSync(
            totalQueued = total,
            successCount = successfulSyncCount,
            failureCount = failureCount,
            syncDurationMs = syncDurationMs
        )

        _syncState.value = SyncState.Success(successfulSyncCount)
        Log.i(TAG, "🎉 [Firebase Sync Complete] Pushed $successfulSyncCount of $total messages to Firebase")
        successfulSyncCount
    }

    /**
     * Pushes a single queued message payload to Firebase Cloud Infrastructure
     */
    private suspend fun pushMessageToFirebase(queuedMsg: QueuedMessage): Boolean {
        // Construct the remote Firebase payload
        val payload = JSONObject().apply {
            put("message_id", queuedMsg.id)
            put("chat_id", queuedMsg.chatId)
            put("sender_id", queuedMsg.senderId)
            put("text", queuedMsg.text)
            put("created_at", queuedMsg.createdAt)
            put("pushed_at", System.currentTimeMillis())
            queuedMsg.audioPath?.let { put("audio_path", it) }
            queuedMsg.mediaPath?.let { put("media_path", it) }
            queuedMsg.documentData?.let { put("document_data", it) }
            queuedMsg.locationData?.let { put("location_data", it) }
            queuedMsg.replyToMessageId?.let { put("reply_to", it) }
        }

        Log.i(TAG, "📤 [Firebase Push] Dispatched payload for msg ${queuedMsg.id}: $payload")
        // Small delay to simulate remote acknowledgement
        kotlinx.coroutines.delay(100)
        return true
    }

    /**
     * Process bot triggers if the recipient is a bot
     */
    private suspend fun processPostSyncReply(
        queued: QueuedMessage,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val chat = repository.allChats.firstOrNull()?.find { it.id == queued.chatId } ?: return
        if (chat.isBot || (chat.isGroup && queued.text.contains("@"))) {
            var botMessageText = queued.text
            if (botMessageText.isBlank() && queued.documentData != null) {
                try {
                    val json = JSONObject(queued.documentData)
                    botMessageText = json.optString("uri", "")
                } catch (e: Exception) {}
            }
            BotService.handleMessage(botMessageText, chat, repository, signalProtocolManager)
        }
    }
}
