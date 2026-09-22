package com.example.data.repository

import com.example.data.ChatDao
import com.example.data.ContactDao
import com.example.data.DraftDao
import com.example.data.FirebaseMessageSyncManager
import com.example.data.FirestoreUserRoleManager
import com.example.data.GroupMemberDao
import com.example.data.MessageDao
import com.example.data.PaymentTransactionDao
import com.example.data.QueuedMessageDao
import com.example.data.RegisteredUserRole
import com.example.data.UserDao
import com.example.ui.Chat
import com.example.ui.Contact
import com.example.ui.Draft
import com.example.ui.GroupMember
import com.example.ui.Message
import com.example.ui.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified repository abstraction decoupling ViewModels from direct Room DAO & Firestore interactions.
 */
interface UserDataRepository {
    fun getActiveAccount(): Flow<UserAccount?>
    fun getAllAccounts(): Flow<List<UserAccount>>
    suspend fun insertAccount(account: UserAccount)
    suspend fun switchActiveAccount(accountId: String)
    suspend fun logoutAll()
    suspend fun update2FA(accountId: String, isEnabled: Boolean)
    suspend fun updateProfile(
        accountId: String,
        username: String,
        displayName: String,
        bio: String,
        profilePicUrl: String,
        customStatus: String,
        phoneNumber: String,
        dateOfBirth: String,
        socialMedia: String
    )
    suspend fun deleteAccount(accountId: String)
    suspend fun checkPhoneNumberExists(phoneNumber: String): Boolean
    
    // Firestore user roles & moderation
    fun getRegisteredUserRoles(): StateFlow<List<RegisteredUserRole>>
    suspend fun syncUserWithFirestore(user: UserAccount)
    suspend fun updateUserRole(userId: String, isAdmin: Boolean, isModerator: Boolean)
    suspend fun applyModerationBan(userId: String, reason: String, durationHours: Int?)
    suspend fun unbanUser(userId: String)
}

interface ChatDataRepository {
    fun getAllChats(): Flow<List<Chat>>
    suspend fun getChatById(chatId: String): Chat?
    suspend fun insertChat(chat: Chat)
    suspend fun updatePinnedMessage(chatId: String, messageId: String?)
    suspend fun updateBlockedStatus(chatId: String, isBlocked: Boolean)
    suspend fun updateArchiveStatus(chatId: String, isArchived: Boolean)
    suspend fun updateMuteStatus(chatId: String, isMuted: Boolean)
    suspend fun deleteChat(chatId: String)
    
    fun getMessagesForChat(chatId: String): Flow<List<Message>>
    fun getRecentMessagesForChat(chatId: String, limit: Int): Flow<List<Message>>
    suspend fun insertMessage(message: Message)
    suspend fun deleteMessage(messageId: String)
    
    suspend fun getDraft(chatId: String): Draft?
    suspend fun saveDraft(draft: Draft)
    suspend fun deleteDraft(chatId: String)
    
    fun getContacts(): Flow<List<Contact>>
    suspend fun syncContacts(contacts: List<Contact>)
    
    // Firestore real-time synchronization
    fun observeRealtimeMessages(chatId: String): Flow<List<Message>>
    fun observeRealtimeChats(userId: String): Flow<List<Chat>>
    suspend fun getOrCreateDirectChat(currentUserId: String, otherUserId: String, currentUserName: String = "", otherUserName: String = ""): Result<Chat>
    suspend fun sendDirectMessage(
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
    suspend fun markChatAsRead(chatId: String, currentUserId: String, otherUserId: String)
    suspend fun syncMessagesWithFirestore(chatId: String)
}

class UserDataRepositoryImpl(
    private val userDao: UserDao,
    private val contactDao: ContactDao,
    private val roleManager: FirestoreUserRoleManager = FirestoreUserRoleManager
) : UserDataRepository {

    override fun getActiveAccount(): Flow<UserAccount?> = userDao.getActiveAccount()
    override fun getAllAccounts(): Flow<List<UserAccount>> = userDao.getAllAccounts()

    override suspend fun insertAccount(account: UserAccount) {
        userDao.insertAccount(account)
        roleManager.syncLocalAccounts(listOf(account))
    }

    override suspend fun switchActiveAccount(accountId: String) {
        userDao.switchActiveAccount(accountId)
    }

    override suspend fun logoutAll() {
        userDao.logoutAll()
    }

    override suspend fun update2FA(accountId: String, isEnabled: Boolean) {
        userDao.update2FA(accountId, isEnabled)
    }

    override suspend fun updateProfile(
        accountId: String,
        username: String,
        displayName: String,
        bio: String,
        profilePicUrl: String,
        customStatus: String,
        phoneNumber: String,
        dateOfBirth: String,
        socialMedia: String
    ) {
        userDao.updateProfile(accountId, username, displayName, bio, profilePicUrl, customStatus, phoneNumber, dateOfBirth, socialMedia)
    }

    override suspend fun deleteAccount(accountId: String) {
        userDao.deleteAccount(accountId)
    }

    override suspend fun checkPhoneNumberExists(phoneNumber: String): Boolean {
        return userDao.checkPhoneNumberExists(phoneNumber)
    }

    override fun getRegisteredUserRoles(): StateFlow<List<RegisteredUserRole>> {
        return roleManager.users
    }

    override suspend fun syncUserWithFirestore(user: UserAccount) {
        roleManager.syncLocalAccounts(listOf(user))
    }

    override suspend fun updateUserRole(userId: String, isAdmin: Boolean, isModerator: Boolean) {
        roleManager.setAdminRole(userId, isAdmin)
        roleManager.setModeratorRole(userId, isModerator)
    }

    override suspend fun applyModerationBan(userId: String, reason: String, durationHours: Int?) {
        val durationMillis = durationHours?.let { it.toLong() * 3600000L }
        roleManager.blockUser(userId = userId, reason = reason, durationMillis = durationMillis)
    }

    override suspend fun unbanUser(userId: String) {
        roleManager.unblockUser(userId)
    }
}

class ChatDataRepositoryImpl(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val draftDao: DraftDao,
    private val contactDao: ContactDao,
    private val groupMemberDao: GroupMemberDao,
    private val firestoreChatRepo: FirestoreChatRepository? = null,
    private val messageSyncManager: FirebaseMessageSyncManager = FirebaseMessageSyncManager
) : ChatDataRepository {

    override fun getAllChats(): Flow<List<Chat>> = chatDao.getAllChats()
    override suspend fun getChatById(chatId: String): Chat? = chatDao.getChatById(chatId)
    override suspend fun insertChat(chat: Chat) = chatDao.insertChat(chat)
    override suspend fun updatePinnedMessage(chatId: String, messageId: String?) = chatDao.updatePinnedMessage(chatId, messageId)
    override suspend fun updateBlockedStatus(chatId: String, isBlocked: Boolean) = chatDao.updateBlockedStatus(chatId, isBlocked)
    override suspend fun updateArchiveStatus(chatId: String, isArchived: Boolean) = chatDao.updateArchiveStatus(chatId, isArchived)
    override suspend fun updateMuteStatus(chatId: String, isMuted: Boolean) = chatDao.updateMuteStatus(chatId, isMuted)
    override suspend fun deleteChat(chatId: String) = chatDao.deleteChat(chatId)

    override fun getMessagesForChat(chatId: String): Flow<List<Message>> = messageDao.getMessagesForChat(chatId)
    override fun getRecentMessagesForChat(chatId: String, limit: Int): Flow<List<Message>> = messageDao.getRecentMessagesForChat(chatId, limit)
    override suspend fun insertMessage(message: Message) {
        messageDao.insertMessage(message)
    }
    override suspend fun deleteMessage(messageId: String) = messageDao.deleteMessage(messageId)

    override suspend fun getDraft(chatId: String): Draft? = draftDao.getDraft(chatId)
    override suspend fun saveDraft(draft: Draft) = draftDao.insertDraft(draft)
    override suspend fun deleteDraft(chatId: String) = draftDao.clearDraft(chatId)

    override fun getContacts(): Flow<List<Contact>> = contactDao.getAllContacts()
    override suspend fun syncContacts(contacts: List<Contact>) = contactDao.insertContacts(contacts)

    override fun observeRealtimeMessages(chatId: String): Flow<List<Message>> {
        return firestoreChatRepo?.observeMessagesRealtime(chatId) ?: messageDao.getMessagesForChat(chatId)
    }

    override fun observeRealtimeChats(userId: String): Flow<List<Chat>> {
        return firestoreChatRepo?.observeUserChatsRealtime(userId) ?: chatDao.getAllChats()
    }

    override suspend fun getOrCreateDirectChat(
        currentUserId: String,
        otherUserId: String,
        currentUserName: String,
        otherUserName: String
    ): Result<Chat> {
        return firestoreChatRepo?.getOrCreateDirectChat(currentUserId, otherUserId, currentUserName, otherUserName)
            ?: Result.success(
                Chat(
                    id = FirestoreChatRepositoryImpl.getDirectChatId(currentUserId, otherUserId),
                    title = otherUserName.ifBlank { "User $otherUserId" },
                    lastMessage = ""
                )
            )
    }

    override suspend fun sendDirectMessage(
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
    ): Result<Message> {
        return firestoreChatRepo?.sendMessage(
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            text = text,
            audioPath = audioPath,
            mediaPath = mediaPath,
            mediaType = mediaType,
            documentData = documentData,
            locationData = locationData,
            replyToMessageId = replyToMessageId,
            replyToMessageText = replyToMessageText,
            expiresIn = expiresIn
        ) ?: run {
            val msg = Message(
                id = java.util.UUID.randomUUID().toString(),
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
                timestamp = System.currentTimeMillis()
            )
            messageDao.insertMessage(msg)
            Result.success(msg)
        }
    }

    override suspend fun markChatAsRead(chatId: String, currentUserId: String, otherUserId: String) {
        firestoreChatRepo?.markMessagesAsRead(chatId, currentUserId, otherUserId)
            ?: run {
                messageDao.markAsRead(chatId, currentUserId)
                chatDao.getChatById(chatId)?.let {
                    chatDao.insertChat(it.copy(unreadCount = 0))
                }
            }
    }

    override suspend fun syncMessagesWithFirestore(chatId: String) {
        firestoreChatRepo?.syncPendingMessages()
    }
}
