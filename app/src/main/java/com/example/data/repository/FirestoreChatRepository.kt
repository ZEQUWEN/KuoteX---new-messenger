package com.example.data.repository

import android.util.Log
import com.example.analytics.AnalyticsTracker
import com.example.analytics.FirebaseAnalyticsHelper
import com.example.data.ChatDao
import com.example.data.CryptoManager
import com.example.data.DraftDao
import com.example.data.MessageDao
import com.example.data.QueuedMessage
import com.example.data.QueuedMessageDao
import com.example.ui.Chat
import com.example.ui.Draft
import com.example.ui.Message
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firestore-based Chat Repository Interface.
 * Handles 1-on-1 direct message persistence, real-time syncing between two users,
 * read receipts, and typing status updates with cloud Firestore and local Room caching.
 */
interface FirestoreChatRepository {

    /**
     * Retrieves or creates a deterministic direct chat between two users.
     */
    suspend fun getOrCreateDirectChat(
        currentUserId: String,
        otherUserId: String,
        currentUserName: String = "",
        otherUserName: String = ""
    ): Result<Chat>

    /**
     * Sends a message between two users:
     * 1. Persists optimistically in Room.
     * 2. Pushes document to Firestore collection 'chats/{chatId}/messages/{messageId}'.
     * 3. Updates conversation metadata in 'chats/{chatId}'.
     * 4. Updates delivery status to delivered upon successful cloud push.
     */
    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        text: String,
        audioPath: String? = null,
        mediaPath: String? = null,
        mediaType: String? = null,
        documentData: String? = null,
        locationData: String? = null,
        replyToMessageId: String? = null,
        replyToMessageText: String? = null,
        expiresIn: Long? = null
    ): Result<Message>

    /**
     * Real-time stream of messages for a chat synced from Firestore.
     * Automatically updates local Room cache upon receiving remote messages from the other user.
     */
    fun observeMessagesRealtime(chatId: String): Flow<List<Message>>

    /**
     * Real-time stream of chats for a user from Firestore.
     */
    fun observeUserChatsRealtime(userId: String): Flow<List<Chat>>

    /**
     * Marks all messages from the other user as read in both Firestore and local Room.
     */
    suspend fun markMessagesAsRead(chatId: String, currentUserId: String, otherUserId: String): Result<Unit>

    /**
     * Sets user's real-time typing status in a direct chat.
     */
    suspend fun setTypingStatus(chatId: String, userId: String, isTyping: Boolean): Result<Unit>

    /**
     * Real-time stream of typing status for the other participant.
     */
    fun observeTypingStatus(chatId: String, otherUserId: String): Flow<Boolean>

    /**
     * Sets or updates reaction on a message in Firestore and local Room.
     */
    suspend fun setMessageReaction(chatId: String, messageId: String, reaction: String?): Result<Unit>

    /**
     * Deletes a message from Firestore and Room.
     */
    suspend fun deleteMessage(chatId: String, messageId: String, forEveryone: Boolean = true): Result<Unit>

    /**
     * Syncs any locally pending / offline queued messages to Firestore.
     */
    suspend fun syncPendingMessages(): Result<Int>

    /**
     * Local Room accessors
     */
    fun getLocalMessages(chatId: String): Flow<List<Message>>
    fun getLocalChats(): Flow<List<Chat>>
    suspend fun saveDraft(chatId: String, draftText: String)
    suspend fun getDraft(chatId: String): Draft?
    suspend fun clearDraft(chatId: String)
}

/**
 * Production implementation of FirestoreChatRepository.
 */
class FirestoreChatRepositoryImpl(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val queuedMessageDao: QueuedMessageDao,
    private val draftDao: DraftDao,
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : FirestoreChatRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "FirestoreChatRepo"
        private const val CHATS_COLLECTION = "chats"
        private const val MESSAGES_SUBCOLLECTION = "messages"
        private const val TYPING_SUBCOLLECTION = "typing"

        /**
         * Computes a deterministic chat channel ID for two users so both users access the exact same document.
         */
        fun getDirectChatId(userA: String, userB: String): String {
            return if (userA < userB) "direct_${userA}_${userB}" else "direct_${userB}_${userA}"
        }
    }

    private fun getFirestore(): FirebaseFirestore? {
        return try {
            firestoreProvider()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore not available: ${e.message}")
            null
        }
    }

    override suspend fun getOrCreateDirectChat(
        currentUserId: String,
        otherUserId: String,
        currentUserName: String,
        otherUserName: String
    ): Result<Chat> = withContext(Dispatchers.IO) {
        val chatId = getDirectChatId(currentUserId, otherUserId)
        val chatTitle = otherUserName.ifBlank { "User $otherUserId" }

        // 1. Check local Room DB first
        val existingLocalChat = chatDao.getChatById(chatId)
        val localChat = existingLocalChat ?: Chat(
            id = chatId,
            title = chatTitle,
            isChannel = false,
            isGroup = false,
            isBot = false,
            isSecret = false,
            lastMessage = "",
            lastMessageTimestamp = System.currentTimeMillis(),
            unreadCount = 0
        ).also { chatDao.insertChat(it) }

        // 2. Ensure Firestore chat document exists
        val db = getFirestore()
        if (db != null) {
            try {
                val chatDocRef = db.collection(CHATS_COLLECTION).document(chatId)
                val snapshot = chatDocRef.get().await()
                if (!snapshot.exists()) {
                    val initialData = hashMapOf(
                        "id" to chatId,
                        "participants" to listOf(currentUserId, otherUserId),
                        "participantNames" to mapOf(
                            currentUserId to currentUserName,
                            otherUserId to otherUserName
                        ),
                        "lastMessage" to "",
                        "lastMessageTimestamp" to System.currentTimeMillis(),
                        "lastSenderId" to "",
                        "unreadCounts" to mapOf(
                            currentUserId to 0,
                            otherUserId to 0
                        ),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "isDirect" to true
                    )
                    chatDocRef.set(initialData, SetOptions.merge()).await()
                    Log.d(TAG, "✅ Direct Firestore chat created: $chatId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize Firestore chat doc: ${e.message}")
            }
        }

        Result.success(localChat)
    }

    override suspend fun sendMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        text: String,
        audioPath: String?,
        mediaPath: String?,
        mediaType: String?,
        documentData: String?,
        locationData: String?,
        replyToMessageId: String?,
        replyToMessageText: String?,
        expiresIn: Long?
    ): Result<Message> = withContext(Dispatchers.IO) {
        val messageId = java.util.UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val expiresAt = expiresIn?.let { timestamp + it }

        val localMessage = Message(
            id = messageId,
            chatId = chatId,
            senderId = senderId,
            text = text,
            audioPath = audioPath,
            mediaPath = mediaPath,
            mediaType = mediaType,
            documentData = documentData,
            locationData = locationData,
            replyToMessageId = replyToMessageId,
            replyToMessageText = replyToMessageText,
            timestamp = timestamp,
            expiresAt = expiresAt,
            isDelivered = false,
            isRead = false
        )

        // 1. Optimistic local persistence in Room
        try {
            messageDao.insertMessage(
                localMessage.copy(
                    text = CryptoManager.encrypt(text),
                    audioPath = audioPath?.let { CryptoManager.encrypt(it) },
                    mediaPath = mediaPath?.let { CryptoManager.encrypt(it) },
                    documentData = documentData?.let { CryptoManager.encrypt(it) }
                )
            )

            // Update parent local chat
            val currentChat = chatDao.getChatById(chatId)
            if (currentChat != null) {
                chatDao.insertChat(
                    currentChat.copy(
                        lastMessage = text,
                        lastMessageTimestamp = timestamp,
                        lastMessageSenderName = "You"
                    )
                )
            }
            draftDao.clearDraft(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "Local message persistence error", e)
        }

        // 2. Dispatch to Firebase Firestore
        val db = getFirestore()
        if (db == null) {
            // Queue for offline background sync
            val queued = QueuedMessage(
                id = messageId,
                chatId = chatId,
                senderId = senderId,
                text = text,
                audioPath = audioPath,
                mediaPath = mediaPath,
                mediaType = mediaType,
                documentData = documentData,
                replyToMessageId = replyToMessageId,
                replyToMessageText = replyToMessageText,
                expiresIn = expiresIn,
                status = "QUEUED"
            )
            queuedMessageDao.insertQueuedMessage(queued)
            return@withContext Result.success(localMessage)
        }

        try {
            val messagePayload = hashMapOf<String, Any?>(
                "id" to messageId,
                "chatId" to chatId,
                "senderId" to senderId,
                "receiverId" to receiverId,
                "text" to text,
                "audioPath" to audioPath,
                "mediaPath" to mediaPath,
                "mediaType" to mediaType,
                "documentData" to documentData,
                "locationData" to locationData,
                "replyToMessageId" to replyToMessageId,
                "replyToMessageText" to replyToMessageText,
                "timestamp" to timestamp,
                "expiresAt" to expiresAt,
                "isDelivered" to true,
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val chatRef = db.collection(CHATS_COLLECTION).document(chatId)
            val msgRef = chatRef.collection(MESSAGES_SUBCOLLECTION).document(messageId)

            // Firestore Batch write
            val batch = db.batch()
            batch.set(msgRef, messagePayload)
            batch.set(
                chatRef,
                mapOf(
                    "lastMessage" to text,
                    "lastMessageTimestamp" to timestamp,
                    "lastSenderId" to senderId,
                    "unreadCounts.$receiverId" to FieldValue.increment(1)
                ),
                SetOptions.merge()
            )

            batch.commit().await()

            // 3. Mark delivered in local Room DB
            messageDao.updateMessageDelivery(messageId, true)
            Log.d(TAG, "🔥 [Firestore Sync] Message $messageId synced to cloud chat $chatId")

            AnalyticsTracker.logChatAction(
                action = "firestore_message_sent",
                chatId = chatId,
                metadata = mapOf(
                    "message_id" to messageId,
                    "length" to text.length
                )
            )

            Result.success(localMessage.copy(isDelivered = true))
        } catch (e: Exception) {
            Log.e(TAG, "Firestore message send failed, queueing offline", e)
            val queued = QueuedMessage(
                id = messageId,
                chatId = chatId,
                senderId = senderId,
                text = text,
                audioPath = audioPath,
                mediaPath = mediaPath,
                mediaType = mediaType,
                documentData = documentData,
                replyToMessageId = replyToMessageId,
                replyToMessageText = replyToMessageText,
                expiresIn = expiresIn,
                status = "FAILED"
            )
            queuedMessageDao.insertQueuedMessage(queued)
            Result.success(localMessage)
        }
    }

    override fun observeMessagesRealtime(chatId: String): Flow<List<Message>> = callbackFlow {
        val db = getFirestore()
        if (db == null) {
            // If Firestore is offline, flow local Room messages
            val sub = repositoryScope.launch {
                messageDao.getMessagesForChat(chatId).collect { list ->
                    trySend(list.map { decryptMessage(it) })
                }
            }
            awaitClose { sub.cancel() }
            return@callbackFlow
        }

        var listener: ListenerRegistration? = null
        try {
            listener = db.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore messages listener error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val messages = snapshot.documents.mapNotNull { doc ->
                            parseMessageDocument(doc, chatId)
                        }

                        // Synchronize new/updated remote messages into local Room database
                        repositoryScope.launch {
                            for (msg in messages) {
                                val encrypted = msg.copy(
                                    text = CryptoManager.encrypt(msg.text),
                                    audioPath = msg.audioPath?.let { CryptoManager.encrypt(it) },
                                    mediaPath = msg.mediaPath?.let { CryptoManager.encrypt(it) },
                                    documentData = msg.documentData?.let { CryptoManager.encrypt(it) }
                                )
                                messageDao.insertMessage(encrypted)
                            }
                        }

                        trySend(messages)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Firestore snapshot listener", e)
        }

        awaitClose {
            listener?.remove()
        }
    }.flowOn(Dispatchers.IO)

    override fun observeUserChatsRealtime(userId: String): Flow<List<Chat>> = callbackFlow {
        val db = getFirestore()
        if (db == null) {
            val sub = repositoryScope.launch {
                chatDao.getAllChats().collect { trySend(it) }
            }
            awaitClose { sub.cancel() }
            return@callbackFlow
        }

        var listener: ListenerRegistration? = null
        try {
            listener = db.collection(CHATS_COLLECTION)
                .whereArrayContains("participants", userId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore chats listener error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val chats = snapshot.documents.mapNotNull { doc ->
                            val id = doc.getString("id") ?: doc.id
                            val participants = (doc.get("participants") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                            val otherId = participants.firstOrNull { it != userId } ?: ""
                            val participantNames = doc.get("participantNames") as? Map<*, *>
                            val title = (participantNames?.get(otherId) as? String)?.ifBlank { null }
                                ?: "User $otherId"
                            val lastMessage = doc.getString("lastMessage") ?: ""
                            val lastTimestamp = doc.getLong("lastMessageTimestamp") ?: 0L
                            val unreadMap = doc.get("unreadCounts") as? Map<*, *>
                            val unread = (unreadMap?.get(userId) as? Long)?.toInt() ?: 0

                            Chat(
                                id = id,
                                title = title,
                                lastMessage = lastMessage,
                                lastMessageTimestamp = lastTimestamp,
                                unreadCount = unread
                            )
                        }

                        // Persist to local Room
                        repositoryScope.launch {
                            for (chat in chats) {
                                val existing = chatDao.getChatById(chat.id)
                                if (existing != null) {
                                    chatDao.insertChat(existing.copy(
                                        lastMessage = chat.lastMessage,
                                        lastMessageTimestamp = chat.lastMessageTimestamp,
                                        unreadCount = chat.unreadCount
                                    ))
                                } else {
                                    chatDao.insertChat(chat)
                                }
                            }
                        }

                        trySend(chats)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to observe user chats", e)
        }

        awaitClose {
            listener?.remove()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun markMessagesAsRead(
        chatId: String,
        currentUserId: String,
        otherUserId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // 1. Update local Room DB
        messageDao.markAsRead(chatId, currentUserId)
        val chat = chatDao.getChatById(chatId)
        if (chat != null) {
            chatDao.insertChat(chat.copy(unreadCount = 0))
        }

        // 2. Update Firestore
        val db = getFirestore() ?: return@withContext Result.success(Unit)
        try {
            val unreadDocs = db.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .whereEqualTo("senderId", otherUserId)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            val batch = db.batch()
            for (doc in unreadDocs.documents) {
                batch.update(doc.reference, "isRead", true)
            }

            // Reset current user's unread counter
            val chatRef = db.collection(CHATS_COLLECTION).document(chatId)
            batch.update(chatRef, "unreadCounts.$currentUserId", 0)

            batch.commit().await()
            Log.d(TAG, "✅ Marked ${unreadDocs.size()} messages as read for chat $chatId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating read status in Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun setTypingStatus(
        chatId: String,
        userId: String,
        isTyping: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val db = getFirestore() ?: return@withContext Result.success(Unit)
        try {
            db.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(TYPING_SUBCOLLECTION)
                .document(userId)
                .set(
                    mapOf(
                        "isTyping" to isTyping,
                        "timestamp" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeTypingStatus(chatId: String, otherUserId: String): Flow<Boolean> = callbackFlow {
        val db = getFirestore()
        if (db == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        var listener: ListenerRegistration? = null
        try {
            listener = db.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(TYPING_SUBCOLLECTION)
                .document(otherUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(false)
                        return@addSnapshotListener
                    }
                    val isTyping = snapshot?.getBoolean("isTyping") ?: false
                    trySend(isTyping)
                }
        } catch (e: Exception) {
            trySend(false)
        }

        awaitClose { listener?.remove() }
    }.flowOn(Dispatchers.IO)

    override suspend fun setMessageReaction(
        chatId: String,
        messageId: String,
        reaction: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        messageDao.updateReaction(messageId, reaction ?: "")
        val db = getFirestore() ?: return@withContext Result.success(Unit)
        try {
            db.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(messageId)
                .update("reaction", reaction)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(
        chatId: String,
        messageId: String,
        forEveryone: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        messageDao.deleteMessage(messageId)
        if (!forEveryone) return@withContext Result.success(Unit)

        val db = getFirestore() ?: return@withContext Result.success(Unit)
        try {
            db.collection(CHATS_COLLECTION)
                .document(chatId)
                .collection(MESSAGES_SUBCOLLECTION)
                .document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncPendingMessages(): Result<Int> = withContext(Dispatchers.IO) {
        val db = getFirestore() ?: return@withContext Result.failure(Exception("Firestore unavailable"))
        val pending = queuedMessageDao.getPendingQueueList()
        var syncedCount = 0

        for (queued in pending) {
            try {
                val payload = hashMapOf<String, Any?>(
                    "id" to queued.id,
                    "chatId" to queued.chatId,
                    "senderId" to queued.senderId,
                    "text" to queued.text,
                    "audioPath" to queued.audioPath,
                    "mediaPath" to queued.mediaPath,
                    "mediaType" to queued.mediaType,
                    "documentData" to queued.documentData,
                    "replyToMessageId" to queued.replyToMessageId,
                    "replyToMessageText" to queued.replyToMessageText,
                    "timestamp" to queued.createdAt,
                    "isDelivered" to true,
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                val chatRef = db.collection(CHATS_COLLECTION).document(queued.chatId)
                val msgRef = chatRef.collection(MESSAGES_SUBCOLLECTION).document(queued.id)

                val batch = db.batch()
                batch.set(msgRef, payload)
                batch.set(
                    chatRef,
                    mapOf(
                        "lastMessage" to queued.text,
                        "lastMessageTimestamp" to queued.createdAt,
                        "lastSenderId" to queued.senderId
                    ),
                    SetOptions.merge()
                )
                batch.commit().await()

                messageDao.updateMessageDelivery(queued.id, true)
                queuedMessageDao.deleteQueuedMessage(queued.id)
                syncedCount++
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing queued message ${queued.id}: ${e.message}")
            }
        }

        Result.success(syncedCount)
    }

    override fun getLocalMessages(chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChat(chatId).flowOn(Dispatchers.IO)
    }

    override fun getLocalChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().flowOn(Dispatchers.IO)
    }

    override suspend fun saveDraft(chatId: String, draftText: String) {
        if (draftText.isBlank()) {
            draftDao.clearDraft(chatId)
        } else {
            draftDao.insertDraft(Draft(chatId, draftText))
        }
    }

    override suspend fun getDraft(chatId: String): Draft? = draftDao.getDraft(chatId)

    override suspend fun clearDraft(chatId: String) = draftDao.clearDraft(chatId)

    private fun parseMessageDocument(doc: DocumentSnapshot, chatId: String): Message? {
        val id = doc.getString("id") ?: doc.id
        val senderId = doc.getString("senderId") ?: return null
        val text = doc.getString("text") ?: ""
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
        val isDelivered = doc.getBoolean("isDelivered") ?: true
        val isRead = doc.getBoolean("isRead") ?: false
        val audioPath = doc.getString("audioPath")
        val mediaPath = doc.getString("mediaPath")
        val mediaType = doc.getString("mediaType")
        val documentData = doc.getString("documentData")
        val locationData = doc.getString("locationData")
        val replyToMessageId = doc.getString("replyToMessageId")
        val replyToMessageText = doc.getString("replyToMessageText")
        val reaction = doc.getString("reaction")
        val expiresAt = doc.getLong("expiresAt")

        return Message(
            id = id,
            chatId = chatId,
            senderId = senderId,
            text = text,
            audioPath = audioPath,
            mediaPath = mediaPath,
            mediaType = mediaType,
            documentData = documentData,
            locationData = locationData,
            replyToMessageId = replyToMessageId,
            replyToMessageText = replyToMessageText,
            timestamp = timestamp,
            reaction = reaction,
            expiresAt = expiresAt,
            isDelivered = isDelivered,
            isRead = isRead
        )
    }

    private fun decryptMessage(msg: Message): Message {
        return msg.copy(
            text = CryptoManager.decrypt(msg.text),
            audioPath = msg.audioPath?.let { CryptoManager.decrypt(it) },
            mediaPath = msg.mediaPath?.let { CryptoManager.decrypt(it) },
            documentData = msg.documentData?.let { CryptoManager.decrypt(it) }
        )
    }
}
