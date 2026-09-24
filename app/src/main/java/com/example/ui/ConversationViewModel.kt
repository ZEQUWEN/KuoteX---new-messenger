package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data model representing a Firestore conversation document.
 */
data class ConversationDocument(
    val id: String = "",
    val title: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastSenderId: String = "",
    val unreadCounts: Map<String, Int> = emptyMap(),
    val isDirect: Boolean = true,
    val isGroup: Boolean = false,
    val isChannel: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val pinnedMessageId: String? = null,
    val typingUsers: List<String> = emptyList(),
    val metadata: Map<String, Any?> = emptyMap(),
    val createdAt: Long = 0L
) {
    /**
     * Formats the timestamp of the last message into a readable date or time string.
     */
    fun formattedLastMessageTime(): String {
        if (lastMessageTimestamp <= 0L) return ""
        val date = Date(lastMessageTimestamp)
        val now = System.currentTimeMillis()
        val diff = now - lastMessageTimestamp
        val sameDay = diff < 86_400_000L && Date().day == date.day
        val formatPattern = if (sameDay) "HH:mm" else "dd.MM.yyyy HH:mm"
        return SimpleDateFormat(formatPattern, Locale.getDefault()).format(date)
    }

    /**
     * Gets the unread message count for a specific user ID.
     */
    fun getUnreadForUser(userId: String): Int {
        return unreadCounts[userId] ?: 0
    }

    /**
     * Returns the other participant ID in a direct (1-on-1) conversation.
     */
    fun getOtherParticipant(currentUserId: String): String? {
        return participants.firstOrNull { it != currentUserId }
    }

    /**
     * Resolves the display name for a specific participant ID.
     */
    fun getDisplayNameForUser(userId: String): String {
        return participantNames[userId] ?: "User $userId"
    }
}

/**
 * Metadata indicating sync and caching status of the Firestore snapshot.
 */
data class SnapshotSyncMetadata(
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

/**
 * Connection status for the Firestore real-time listener.
 */
enum class FirestoreConnectionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * UI State for the observed conversation document.
 */
sealed interface ConversationUiState {
    data object Initial : ConversationUiState
    data class Loading(val conversationId: String) : ConversationUiState
    data class Success(
        val conversation: ConversationDocument,
        val syncMetadata: SnapshotSyncMetadata
    ) : ConversationUiState
    data class NotFound(val conversationId: String) : ConversationUiState
    data class Error(
        val message: String,
        val conversationId: String,
        val cause: Throwable? = null
    ) : ConversationUiState
}

/**
 * ViewModel that connects to Firebase Firestore to observe real-time updates
 * for a specific conversation document.
 *
 * It attaches a real-time snapshot listener to `chats/{conversationId}` (or configurable collection),
 * parses document fields into a strongly-typed [ConversationDocument], exposes the data via [uiState],
 * tracks offline pending writes and cache status, and provides mutation helper methods.
 *
 * The listener is cleanly unsubscribed when [stopObserving] is called or when the ViewModel is destroyed.
 */
class ConversationViewModel(
    initialConversationId: String? = null,
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
    private val collectionPath: String = DEFAULT_COLLECTION
) : ViewModel() {

    companion object {
        private const val TAG = "ConversationViewModel"
        const val DEFAULT_COLLECTION = "chats"
        const val CONVERSATIONS_COLLECTION = "conversations"
    }

    // Backing Firestore instance
    private val firestore: FirebaseFirestore? by lazy {
        try {
            firestoreProvider()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize FirebaseFirestore instance", e)
            null
        }
    }

    // Active Firestore snapshot listener
    private var snapshotListenerRegistration: ListenerRegistration? = null

    // Track current observed conversation ID
    private var currentConversationId: String? = null

    // Observable UI State
    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Initial)
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // Observable connection status
    private val _connectionStatus = MutableStateFlow(FirestoreConnectionStatus.IDLE)
    val connectionStatus: StateFlow<FirestoreConnectionStatus> = _connectionStatus.asStateFlow()

    // Observable action feedback messages (errors or confirmations)
    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    init {
        initialConversationId?.let { id ->
            if (id.isNotBlank()) {
                observeConversation(id)
            }
        }
    }

    /**
     * Connects to Firestore and attaches a real-time snapshot listener
     * for the specified conversation document.
     *
     * Any previous listener is safely removed before attaching the new one.
     */
    fun observeConversation(conversationId: String) {
        if (conversationId.isBlank()) {
            _uiState.value = ConversationUiState.Error(
                message = "Conversation ID cannot be blank",
                conversationId = conversationId
            )
            return
        }

        // Avoid re-attaching if already observing this exact conversation and state is Success/Loading
        if (currentConversationId == conversationId && snapshotListenerRegistration != null && _uiState.value !is ConversationUiState.Error) {
            Log.d(TAG, "Already observing conversation document: $conversationId")
            return
        }

        // Clean up previous registration
        stopObserving(resetState = false)

        currentConversationId = conversationId
        _uiState.value = ConversationUiState.Loading(conversationId)
        _connectionStatus.value = FirestoreConnectionStatus.CONNECTING

        val db = firestore
        if (db == null) {
            _connectionStatus.value = FirestoreConnectionStatus.ERROR
            _uiState.value = ConversationUiState.Error(
                message = "FirebaseFirestore service is not initialized or unavailable",
                conversationId = conversationId
            )
            return
        }

        try {
            val docRef = db.collection(collectionPath).document(conversationId)
            Log.i(TAG, "Attaching real-time snapshot listener to document '${collectionPath}/$conversationId'...")

            snapshotListenerRegistration = docRef.addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e(TAG, "Firestore error observing conversation '$conversationId'", exception)
                    _connectionStatus.value = FirestoreConnectionStatus.ERROR
                    _uiState.value = ConversationUiState.Error(
                        message = exception.localizedMessage ?: "Failed to observe conversation document in Firestore",
                        conversationId = conversationId,
                        cause = exception
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w(TAG, "Conversation document '$conversationId' does not exist in Firestore")
                    _connectionStatus.value = FirestoreConnectionStatus.CONNECTED
                    _uiState.value = ConversationUiState.NotFound(conversationId)
                    return@addSnapshotListener
                }

                try {
                    val conversation = parseConversationSnapshot(snapshot, conversationId)
                    val metadata = SnapshotSyncMetadata(
                        hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        isFromCache = snapshot.metadata.isFromCache(),
                        lastSyncTimestamp = System.currentTimeMillis()
                    )

                    _connectionStatus.value = FirestoreConnectionStatus.CONNECTED
                    _uiState.value = ConversationUiState.Success(
                        conversation = conversation,
                        syncMetadata = metadata
                    )
                    Log.d(TAG, "Real-time update received for conversation '$conversationId': ${conversation.title} (cached=${metadata.isFromCache}, pendingWrites=${metadata.hasPendingWrites})")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing conversation snapshot for '$conversationId'", e)
                    _connectionStatus.value = FirestoreConnectionStatus.ERROR
                    _uiState.value = ConversationUiState.Error(
                        message = "Error deserializing conversation document: ${e.message}",
                        conversationId = conversationId,
                        cause = e
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create snapshot listener for conversation '$conversationId'", e)
            _connectionStatus.value = FirestoreConnectionStatus.ERROR
            _uiState.value = ConversationUiState.Error(
                message = "Failed to attach snapshot listener: ${e.message}",
                conversationId = conversationId,
                cause = e
            )
        }
    }

    /**
     * Returns a reactive Kotlin Flow that emits [ConversationUiState] for a conversation document.
     * Useful for composing with other flows or using in repository layers.
     */
    fun conversationFlow(conversationId: String): Flow<ConversationUiState> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(ConversationUiState.Error(
                message = "Firestore not available",
                conversationId = conversationId
            ))
            close()
            return@callbackFlow
        }

        trySend(ConversationUiState.Loading(conversationId))

        val registration = db.collection(collectionPath)
            .document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ConversationUiState.Error(
                        message = error.localizedMessage ?: "Firestore listener error",
                        conversationId = conversationId,
                        cause = error
                    ))
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    trySend(ConversationUiState.NotFound(conversationId))
                    return@addSnapshotListener
                }

                val conversation = parseConversationSnapshot(snapshot, conversationId)
                val metadata = SnapshotSyncMetadata(
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    isFromCache = snapshot.metadata.isFromCache()
                )
                trySend(ConversationUiState.Success(conversation, metadata))
            }

        awaitClose {
            registration.remove()
        }
    }

    /**
     * Retries connecting to the currently selected conversation document.
     */
    fun retry() {
        val id = currentConversationId
        if (!id.isNullOrBlank()) {
            observeConversation(id)
        }
    }

    /**
     * Stops observing real-time updates and releases the Firestore [ListenerRegistration].
     */
    fun stopObserving(resetState: Boolean = true) {
        snapshotListenerRegistration?.let {
            Log.d(TAG, "Removing Firestore snapshot listener for conversation '$currentConversationId'")
            it.remove()
        }
        snapshotListenerRegistration = null
        if (resetState) {
            currentConversationId = null
            _uiState.value = ConversationUiState.Initial
            _connectionStatus.value = FirestoreConnectionStatus.IDLE
        }
    }

    /**
     * Updates the last message information on the conversation document in Firestore.
     */
    fun updateLastMessage(
        messageText: String,
        senderId: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val conversationId = currentConversationId ?: return
        val db = firestore ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updates = hashMapOf<String, Any>(
                    "lastMessage" to messageText,
                    "lastMessageTimestamp" to timestamp,
                    "lastSenderId" to senderId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection(collectionPath)
                    .document(conversationId)
                    .set(updates, SetOptions.merge())
                    .await()

                Log.d(TAG, "Successfully updated last message on conversation '$conversationId'")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update last message on conversation '$conversationId'", e)
                _actionFeedback.value = "Failed to update conversation: ${e.message}"
            }
        }
    }

    /**
     * Marks the conversation as read for a given user by resetting their unread count.
     */
    fun markAsRead(userId: String) {
        val conversationId = currentConversationId ?: return
        val db = firestore ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fieldKey = "unreadCounts.$userId"
                db.collection(collectionPath)
                    .document(conversationId)
                    .update(fieldKey, 0)
                    .await()
                Log.d(TAG, "Marked conversation '$conversationId' as read for user '$userId'")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark conversation as read for user '$userId': ${e.message}")
            }
        }
    }

    /**
     * Updates the real-time typing status of a user in the conversation document.
     */
    fun setTypingStatus(userId: String, isTyping: Boolean) {
        val conversationId = currentConversationId ?: return
        val db = firestore ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docRef = db.collection(collectionPath).document(conversationId)
                if (isTyping) {
                    docRef.update("typingUsers", FieldValue.arrayUnion(userId)).await()
                } else {
                    docRef.update("typingUsers", FieldValue.arrayRemove(userId)).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not update typing status for user '$userId': ${e.message}")
            }
        }
    }

    /**
     * Toggles mute status for the conversation.
     */
    fun setMuteStatus(isMuted: Boolean) {
        val conversationId = currentConversationId ?: return
        val db = firestore ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection(collectionPath)
                    .document(conversationId)
                    .update("isMuted", isMuted)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update mute status: ${e.message}")
                _actionFeedback.value = "Failed to toggle mute"
            }
        }
    }

    /**
     * Toggles archive status for the conversation.
     */
    fun setArchiveStatus(isArchived: Boolean) {
        val conversationId = currentConversationId ?: return
        val db = firestore ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection(collectionPath)
                    .document(conversationId)
                    .update("isArchived", isArchived)
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update archive status: ${e.message}")
                _actionFeedback.value = "Failed to toggle archive"
            }
        }
    }

    /**
     * Updates pinned message reference in the conversation document.
     */
    fun setPinnedMessage(messageId: String?) {
        val conversationId = currentConversationId ?: return
        val db = firestore ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updates = hashMapOf<String, Any?>("pinnedMessageId" to messageId)
                db.collection(collectionPath)
                    .document(conversationId)
                    .set(updates, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set pinned message: ${e.message}")
            }
        }
    }

    /**
     * Updates conversation title in Firestore.
     */
    fun updateTitle(newTitle: String) {
        val conversationId = currentConversationId ?: return
        val db = firestore ?: return

        if (newTitle.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection(collectionPath)
                    .document(conversationId)
                    .update("title", newTitle.trim())
                    .await()
                _actionFeedback.value = "Title updated"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update title: ${e.message}")
                _actionFeedback.value = "Failed to update title: ${e.message}"
            }
        }
    }

    /**
     * Clears transient action feedback message.
     */
    fun clearActionFeedback() {
        _actionFeedback.value = null
    }

    /**
     * Deserializes a Firestore [DocumentSnapshot] into a [ConversationDocument].
     */
    private fun parseConversationSnapshot(snapshot: DocumentSnapshot, defaultId: String): ConversationDocument {
        val id = snapshot.getString("id") ?: snapshot.id.ifBlank { defaultId }
        val title = snapshot.getString("title") ?: snapshot.getString("name") ?: ""

        val rawParticipants = snapshot.get("participants")
        val participants: List<String> = when (rawParticipants) {
            is List<*> -> rawParticipants.mapNotNull { it?.toString() }
            else -> emptyList()
        }

        val rawNames = snapshot.get("participantNames")
        val participantNames: Map<String, String> = when (rawNames) {
            is Map<*, *> -> rawNames.entries.associate { (k, v) ->
                (k?.toString() ?: "") to (v?.toString() ?: "")
            }.filterKeys { it.isNotBlank() }
            else -> emptyMap()
        }

        val lastMessage = snapshot.getString("lastMessage") ?: ""

        val lastTimestamp = extractTimestamp(snapshot, "lastMessageTimestamp")

        val lastSenderId = snapshot.getString("lastSenderId") ?: ""

        val rawUnread = snapshot.get("unreadCounts")
        val unreadCounts: Map<String, Int> = when (rawUnread) {
            is Map<*, *> -> rawUnread.entries.associate { (k, v) ->
                val count = when (v) {
                    is Number -> v.toInt()
                    is String -> v.toIntOrNull() ?: 0
                    else -> 0
                }
                (k?.toString() ?: "") to count
            }.filterKeys { it.isNotBlank() }
            else -> emptyMap()
        }

        val isDirect = snapshot.getBoolean("isDirect") ?: (participants.size <= 2 && !snapshot.getBoolean("isGroup").let { it == true })
        val isGroup = snapshot.getBoolean("isGroup") ?: false
        val isChannel = snapshot.getBoolean("isChannel") ?: false
        val isMuted = snapshot.getBoolean("isMuted") ?: false
        val isArchived = snapshot.getBoolean("isArchived") ?: false
        val pinnedMessageId = snapshot.getString("pinnedMessageId")

        val rawTyping = snapshot.get("typingUsers")
        val typingUsers: List<String> = when (rawTyping) {
            is List<*> -> rawTyping.mapNotNull { it?.toString() }
            else -> emptyList()
        }

        val createdTimestamp = extractTimestamp(snapshot, "createdAt")

        val metadataMap = snapshot.data ?: emptyMap<String, Any?>()

        return ConversationDocument(
            id = id,
            title = title,
            participants = participants,
            participantNames = participantNames,
            lastMessage = lastMessage,
            lastMessageTimestamp = lastTimestamp,
            lastSenderId = lastSenderId,
            unreadCounts = unreadCounts,
            isDirect = isDirect,
            isGroup = isGroup,
            isChannel = isChannel,
            isMuted = isMuted,
            isArchived = isArchived,
            pinnedMessageId = pinnedMessageId,
            typingUsers = typingUsers,
            metadata = metadataMap,
            createdAt = createdTimestamp
        )
    }

    /**
     * Helper to safely extract epoch milliseconds from Firestore Timestamps, Longs, or Strings.
     */
    private fun extractTimestamp(snapshot: DocumentSnapshot, field: String): Long {
        val value = snapshot.get(field)
        return when (value) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            is Date -> value.time
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    /**
     * Clean up active listeners when the ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        stopObserving(resetState = true)
        Log.d(TAG, "ConversationViewModel onCleared: released Firestore listener")
    }
}
