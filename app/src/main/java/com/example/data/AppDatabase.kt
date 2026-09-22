package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

import com.example.ui.Chat
import com.example.ui.Draft
import com.example.ui.Contact
import com.example.ui.GroupMember
import com.example.ui.Message
import com.example.ui.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Dao
interface UserDao {
    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveAccount(): Flow<UserAccount?>

    @Query("SELECT COUNT(*) > 0 FROM accounts WHERE phoneNumber = :phoneNumber")
    suspend fun checkPhoneNumberExists(phoneNumber: String): Boolean

    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<UserAccount>>

    @Query("SELECT * FROM accounts WHERE displayName LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR username LIKE '%@' || :query || '%'")
    fun searchAccounts(query: String): Flow<List<UserAccount>>

    @Query("SELECT * FROM accounts WHERE displayName LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR username LIKE '%@' || :query || '%'")
    suspend fun searchAccountsSync(query: String): List<UserAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: UserAccount)
    
    @Query("UPDATE accounts SET isActive = CASE WHEN id = :accountId THEN 1 ELSE 0 END")
    suspend fun switchActiveAccount(accountId: String)

    @Query("UPDATE accounts SET isActive = 0")
    suspend fun logoutAll()
    


    @Query("UPDATE accounts SET is2FAEnabled = :isEnabled WHERE id = :accountId")
    suspend fun update2FA(accountId: String, isEnabled: Boolean)

    @Query("UPDATE accounts SET username = :username, displayName = :displayName, bio = :bio, profilePicUrl = :profilePicUrl, customStatus = :customStatus, phoneNumber = :phoneNumber, dateOfBirth = :dateOfBirth, socialMedia = :socialMedia WHERE id = :accountId")
    suspend fun updateProfile(accountId: String, username: String, displayName: String, bio: String, profilePicUrl: String, customStatus: String, phoneNumber: String, dateOfBirth: String, socialMedia: String)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccount(accountId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageTimestamp DESC")
    fun getAllChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): Chat?

    @Query("SELECT * FROM chats WHERE (title LIKE '%' || :query || '%' OR lastMessage LIKE '%' || :query || '%') ORDER BY lastMessageTimestamp DESC")
    fun searchChats(query: String): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE isChannel = 1 AND title LIKE '%' || :query || '%' ORDER BY lastMessageTimestamp DESC")
    fun searchChannels(query: String): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE isGroup = 1 AND title LIKE '%' || :query || '%' ORDER BY lastMessageTimestamp DESC")
    fun searchGroups(query: String): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE (isChannel = 1 OR isGroup = 1) AND title LIKE '%' || :query || '%' ORDER BY lastMessageTimestamp DESC")
    fun searchPublicCommunities(query: String): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE isBot = 1 AND (title LIKE '%' || :query || '%' OR id LIKE '%' || :query || '%') ORDER BY lastMessageTimestamp DESC")
    fun searchBots(query: String): Flow<List<Chat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)

    @Query("UPDATE chats SET pinnedMessageId = :messageId WHERE id = :chatId")
    suspend fun updatePinnedMessage(chatId: String, messageId: String?)

    @Query("UPDATE chats SET isBlocked = :isBlocked WHERE id = :chatId")
    suspend fun updateBlockedStatus(chatId: String, isBlocked: Boolean)
    
    @Query("UPDATE chats SET isArchived = :isArchived WHERE id = :chatId")
    suspend fun updateArchiveStatus(chatId: String, isArchived: Boolean)
    
    @Query("UPDATE chats SET isMuted = :isMuted WHERE id = :chatId")
    suspend fun updateMuteStatus(chatId: String, isMuted: Boolean)

    @Query("UPDATE chats SET isContact = :isContact WHERE id = :chatId")
    suspend fun updateContactStatus(chatId: String, isContact: Boolean)

    @Query("UPDATE chats SET isActionMenuDismissed = :isDismissed WHERE id = :chatId")
    suspend fun updateActionMenuDismissed(chatId: String, isDismissed: Boolean)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND senderId != 'system_sync' ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM (SELECT * FROM messages WHERE chatId = :chatId AND senderId != 'system_sync' ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    fun getRecentMessagesForChat(chatId: String, limit: Int): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND senderId != 'system_sync' ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPage(chatId: String, limit: Int, offset: Int): List<Message>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND senderId != 'system_sync'")
    fun getMessageCountForChat(chatId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun updateReaction(messageId: String, reaction: String)

    @Query("UPDATE messages SET isDelivered = :isDelivered WHERE id = :messageId")
    suspend fun updateMessageDelivery(messageId: String, isDelivered: Boolean)
    @Update
    suspend fun updateMessage(message: Message)
    
    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): Message?
    
    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND senderId != :myUserId")
    suspend fun markAsRead(chatId: String, myUserId: String)
    
    @Query("UPDATE messages SET isPinned = :isPinned WHERE id = :messageId")
    suspend fun updatePinStatus(messageId: String, isPinned: Boolean)

    @Query("SELECT COUNT(*) FROM messages WHERE isDelivered = 0")
    fun getUndeliveredMessagesCount(): Flow<Int>

    @Query("SELECT * FROM messages WHERE isDelivered = 0 ORDER BY timestamp DESC")
    fun getUndeliveredMessages(): Flow<List<Message>>

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :currentTime")
    suspend fun deleteExpiredMessages(currentTime: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearHistory(chatId: String)

    @Query("DELETE FROM messages WHERE senderId = 'system_sync'")
    suspend fun deleteSystemSyncMessages()
}

@Dao
interface GroupMemberDao {
    @Query("SELECT * FROM group_members WHERE chatId = :chatId")
    fun getGroupMembers(chatId: String): Flow<List<GroupMember>>

    @Query("SELECT * FROM group_members WHERE chatId = :chatId AND userId = :userId LIMIT 1")
    suspend fun getGroupMemberSync(chatId: String, userId: String): GroupMember?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMember(member: GroupMember)

    @Query("UPDATE group_members SET isAdmin = :isAdmin WHERE chatId = :chatId AND userId = :userId")
    suspend fun updateAdminStatus(chatId: String, userId: String, isAdmin: Boolean)

    @Query("DELETE FROM group_members WHERE chatId = :chatId AND userId = :userId")
    suspend fun removeMember(chatId: String, userId: String)
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE chatId = :chatId")
    suspend fun getDraft(chatId: String): Draft?

    @Query("SELECT * FROM drafts")
    fun getAllDrafts(): Flow<List<Draft>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: Draft)

    @Query("DELETE FROM drafts WHERE chatId = :chatId")
    suspend fun clearDraft(chatId: String)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<Contact>)

    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContact(contactId: String)
}

@Entity(tableName = "payment_transactions")
data class PaymentTransaction(
    @PrimaryKey val id: String,
    val botId: String,
    val userId: String,
    val provider: String,
    val amount: Long,
    val currency: String,
    val status: String, // e.g. "pending", "completed", "failed"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface PaymentTransactionDao {
    @Query("SELECT * FROM payment_transactions WHERE botId = :botId ORDER BY timestamp DESC")
    fun getTransactionsForBot(botId: String): Flow<List<PaymentTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: PaymentTransaction)
    
    @Query("UPDATE payment_transactions SET status = :status WHERE id = :transactionId")
    suspend fun updateTransactionStatus(transactionId: String, status: String)
}

@Entity(tableName = "queued_messages")
data class QueuedMessage(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val audioPath: String? = null,
    val mediaPath: String? = null,
    val mediaType: String? = null,
    val documentData: String? = null,
    val locationData: String? = null,
    val replyToMessageId: String? = null,
    val replyToMessageText: String? = null,
    val isForwarded: Boolean = false,
    val forwardOriginalSenderId: String? = null,
    val forwardOriginalSenderName: String? = null,
    val forwardHideSender: Boolean = false,
    val expiresIn: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "QUEUED", // "QUEUED", "SYNCING", "FAILED", "SENT"
    val retryCount: Int = 0,
    val lastAttemptTimestamp: Long = 0L,
    val errorMessage: String? = null
)

@Dao
interface QueuedMessageDao {
    @Query("SELECT * FROM queued_messages ORDER BY createdAt ASC")
    fun getAllQueuedMessages(): Flow<List<QueuedMessage>>

    @Query("SELECT * FROM queued_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun getQueuedMessagesForChat(chatId: String): Flow<List<QueuedMessage>>

    @Query("SELECT * FROM queued_messages ORDER BY createdAt ASC")
    suspend fun getPendingQueueList(): List<QueuedMessage>

    @Query("SELECT * FROM queued_messages WHERE id = :id LIMIT 1")
    suspend fun getQueuedMessageById(id: String): QueuedMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueuedMessage(message: QueuedMessage)

    @Update
    suspend fun updateQueuedMessage(message: QueuedMessage)

    @Query("UPDATE queued_messages SET status = :status, retryCount = :retryCount, lastAttemptTimestamp = :lastAttempt, errorMessage = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, retryCount: Int, lastAttempt: Long, error: String?)

    @Query("DELETE FROM queued_messages WHERE id = :id")
    suspend fun deleteQueuedMessage(id: String)

    @Query("DELETE FROM queued_messages WHERE chatId = :chatId")
    suspend fun clearQueueForChat(chatId: String)

    @Query("SELECT COUNT(*) FROM queued_messages")
    fun getQueuedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_messages WHERE chatId = :chatId")
    fun getQueuedCountForChat(chatId: String): Flow<Int>
}

@androidx.room.TypeConverters(BotConverters::class)
@Database(entities = [CustomBotEntity::class, UserAccount::class, Chat::class, Message::class, GroupMember::class, Draft::class, Contact::class, PaymentTransaction::class, QueuedMessage::class], version = 23, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun botDao(): BotDao
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun draftDao(): DraftDao
    abstract fun contactDao(): ContactDao
    abstract fun paymentTransactionDao(): PaymentTransactionDao
    abstract fun queuedMessageDao(): QueuedMessageDao
}

class MessengerRepository(
    val botDao: BotDao,
    private val userDao: UserDao, 
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val groupMemberDao: GroupMemberDao,
    private val draftDao: DraftDao,
    private val contactDao: ContactDao,
    private val paymentTransactionDao: PaymentTransactionDao,
    private val queuedMessageDao: QueuedMessageDao,
    private val sharedPrefs: android.content.SharedPreferences,
    val webSocketManager: com.example.data.WebSocketManager
) {

    fun getAllQueuedMessages(): Flow<List<QueuedMessage>> = queuedMessageDao.getAllQueuedMessages()
    fun getQueuedMessagesForChat(chatId: String): Flow<List<QueuedMessage>> = queuedMessageDao.getQueuedMessagesForChat(chatId)
    fun getQueuedCount(): Flow<Int> = queuedMessageDao.getQueuedCount()
    fun getQueuedCountForChat(chatId: String): Flow<Int> = queuedMessageDao.getQueuedCountForChat(chatId)
    suspend fun insertQueuedMessage(message: QueuedMessage) = queuedMessageDao.insertQueuedMessage(message)
    suspend fun updateQueuedMessage(message: QueuedMessage) = queuedMessageDao.updateQueuedMessage(message)
    suspend fun updateQueuedSyncStatus(id: String, status: String, retryCount: Int, lastAttempt: Long, error: String?) = 
        queuedMessageDao.updateSyncStatus(id, status, retryCount, lastAttempt, error)
    suspend fun deleteQueuedMessage(id: String) = queuedMessageDao.deleteQueuedMessage(id)
    suspend fun getPendingQueueList(): List<QueuedMessage> = queuedMessageDao.getPendingQueueList()
    suspend fun getQueuedMessageById(id: String): QueuedMessage? = queuedMessageDao.getQueuedMessageById(id)
    suspend fun clearQueueForChat(chatId: String) = queuedMessageDao.clearQueueForChat(chatId)

    suspend fun insertPaymentTransaction(transaction: PaymentTransaction) = paymentTransactionDao.insertTransaction(transaction)
    suspend fun updatePaymentTransactionStatus(transactionId: String, status: String) = paymentTransactionDao.updateTransactionStatus(transactionId, status)
    fun getPaymentTransactions(botId: String) = paymentTransactionDao.getTransactionsForBot(botId)

    var currentActiveChatId: String? = null

    val activeAccountFlow: Flow<UserAccount?> = userDao.getActiveAccount().map { it?.copy(sessionToken = it.sessionToken?.let { token -> CryptoManager.decrypt(token) }) }

    val allAccounts: Flow<List<UserAccount>> = userDao.getAllAccounts().map { list -> list.map { it.copy(sessionToken = it.sessionToken?.let { token -> CryptoManager.decrypt(token) }) } }
    val allChats: Flow<List<Chat>> = chatDao.getAllChats()
    val allDrafts: Flow<List<Draft>> = draftDao.getAllDrafts()
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts().map { list -> list.map { it.copy(name = CryptoManager.decrypt(it.name), phoneNumber = it.phoneNumber?.let { phone -> CryptoManager.decrypt(phone) }) } }
    val undeliveredMessagesCount: Flow<Int> = messageDao.getUndeliveredMessagesCount()
    val undeliveredMessages: Flow<List<Message>> = messageDao.getUndeliveredMessages()

    fun searchAccounts(query: String): Flow<List<UserAccount>> = userDao.searchAccounts(query)
    suspend fun searchAccountsSync(query: String): List<UserAccount> = userDao.searchAccountsSync(query)
    fun searchChats(query: String): Flow<List<Chat>> = chatDao.searchChats(query)
    fun searchChannels(query: String): Flow<List<Chat>> = chatDao.searchChannels(query)
    fun searchGroups(query: String): Flow<List<Chat>> = chatDao.searchGroups(query)
    fun searchPublicCommunities(query: String): Flow<List<Chat>> = chatDao.searchPublicCommunities(query)
    fun searchBots(query: String): Flow<List<Chat>> = chatDao.searchBots(query)
    fun searchCustomBots(query: String): Flow<List<CustomBotEntity>> = botDao.searchCustomBots(query)


    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            webSocketManager.connect()
            webSocketManager.events.collect { event ->
                when (event) {
                    is InboundEvent.NewMessage -> {
                        // Assuming no current active chat info here, pass null
                        handleNewMessage(event, currentActiveChatId)
                    }
                    is InboundEvent.ReadReceipt -> {
                        // Handle read receipt
                    }
                    is InboundEvent.UserTyping -> {
                        // Handle typing
                    }
                    is InboundEvent.PresenceUpdate -> {
                        // Handled in AppViewModel
                    }
                }
            }
        }
    }

    fun getTheme(): String? = sharedPrefs.getString("app_theme", null)
    
    fun saveTheme(theme: String) {
        sharedPrefs.edit().putString("app_theme", theme).apply()
    }

    fun getDarkThemeEnabled(): Boolean = sharedPrefs.getBoolean("dark_theme", true)
    fun saveDarkThemeEnabled(enabled: Boolean) = sharedPrefs.edit().putBoolean("dark_theme", enabled).apply()

    suspend fun handleNewMessage(event: InboundEvent.NewMessage, currentActiveChatId: String?) {
        // Save the message
        val message = Message(
            id = event.messageId,
            chatId = event.chatId,
            senderId = event.senderId,
            text = event.text,
            timestamp = event.timestamp
        )
        messageDao.insertMessage(message)

        // Update the chat
        val chat = chatDao.getChatById(event.chatId)
        if (chat != null) {
            val unreadIncrement = if (currentActiveChatId != event.chatId) 1 else 0
            val updatedChat = chat.copy(
                lastMessage = event.text,
                lastMessageTimestamp = event.timestamp,
                unreadCount = chat.unreadCount + unreadIncrement
            )
            chatDao.insertChat(updatedChat)
        }
    }
    
    suspend fun markChatAsRead(chatId: String) {
        val chat = chatDao.getChatById(chatId)
        if (chat != null) {
            chatDao.insertChat(chat.copy(unreadCount = 0))
        }
    }

    fun getAutoThemeSwitcherEnabled(): Boolean = sharedPrefs.getBoolean("auto_theme", false)
    fun saveAutoThemeSwitcherEnabled(enabled: Boolean) = sharedPrefs.edit().putBoolean("auto_theme", enabled).apply()

    fun getCustomPrimaryColor(): Long? = if (sharedPrefs.contains("custom_primary")) sharedPrefs.getLong("custom_primary", 0L) else null
    fun saveCustomPrimaryColor(color: Long) = sharedPrefs.edit().putLong("custom_primary", color).apply()
    
    fun getCustomSecondaryColor(): Long? = if (sharedPrefs.contains("custom_secondary")) sharedPrefs.getLong("custom_secondary", 0L) else null
    fun saveCustomSecondaryColor(color: Long) = sharedPrefs.edit().putLong("custom_secondary", color).apply()

    fun getFavoriteThemes(): Set<String> = sharedPrefs.getStringSet("favorite_themes", emptySet()) ?: emptySet()
    fun saveFavoriteThemes(themes: Set<String>) = sharedPrefs.edit().putStringSet("favorite_themes", themes).apply()

    fun getBatterySaverEnabled(): Boolean = sharedPrefs.getBoolean("battery_saver", false)
    fun saveBatterySaverEnabled(enabled: Boolean) = sharedPrefs.edit().putBoolean("battery_saver", enabled).apply()

    fun getQrSnowflakesEnabled(): Boolean = sharedPrefs.getBoolean("qr_snowflakes", false)
    fun saveQrSnowflakesEnabled(enabled: Boolean) = sharedPrefs.edit().putBoolean("qr_snowflakes", enabled).apply()

    fun getThemeOpacity(): Float = sharedPrefs.getFloat("theme_opacity", 1.0f)
    fun saveThemeOpacity(opacity: Float) = sharedPrefs.edit().putFloat("theme_opacity", opacity).apply()

    fun getChatFoldersJson(): String? = sharedPrefs.getString("chat_folders_json", null)
    fun saveChatFoldersJson(json: String) = sharedPrefs.edit().putString("chat_folders_json", json).apply()

    fun getChatTagsEnabled(): Boolean = sharedPrefs.getBoolean("chat_tags_enabled", true)
    fun saveChatTagsEnabled(enabled: Boolean) = sharedPrefs.edit().putBoolean("chat_tags_enabled", enabled).apply()

    fun getMessages(chatId: String) = messageDao.getMessagesForChat(chatId).map { list -> list.map { it.copy(text = CryptoManager.decrypt(it.text), audioPath = it.audioPath?.let { p -> CryptoManager.decrypt(p) }, mediaPath = it.mediaPath?.let { p -> CryptoManager.decrypt(p) }, documentData = it.documentData?.let { p -> CryptoManager.decrypt(p) }) } }
    fun getPagedMessages(chatId: String, limit: Int) = messageDao.getRecentMessagesForChat(chatId, limit).map { list -> list.map { it.copy(text = CryptoManager.decrypt(it.text), audioPath = it.audioPath?.let { p -> CryptoManager.decrypt(p) }, mediaPath = it.mediaPath?.let { p -> CryptoManager.decrypt(p) }, documentData = it.documentData?.let { p -> CryptoManager.decrypt(p) }) } }
    fun getMessageCountForChat(chatId: String): Flow<Int> = messageDao.getMessageCountForChat(chatId)
    suspend fun getMessagesPage(chatId: String, limit: Int, offset: Int): List<Message> = messageDao.getMessagesPage(chatId, limit, offset).map { it.copy(text = CryptoManager.decrypt(it.text), audioPath = it.audioPath?.let { p -> CryptoManager.decrypt(p) }, mediaPath = it.mediaPath?.let { p -> CryptoManager.decrypt(p) }, documentData = it.documentData?.let { p -> CryptoManager.decrypt(p) }) }
    fun getGroupMembers(chatId: String) = groupMemberDao.getGroupMembers(chatId)
    suspend fun getGroupMemberSync(chatId: String, userId: String) = groupMemberDao.getGroupMemberSync(chatId, userId)
    
    suspend fun getDraft(chatId: String) = draftDao.getDraft(chatId)

    suspend fun checkPhoneNumberExists(phoneNumber: String): Boolean = userDao.checkPhoneNumberExists(phoneNumber)

    suspend fun insertAccount(account: UserAccount) = userDao.insertAccount(account.copy(sessionToken = account.sessionToken?.let { CryptoManager.encrypt(it) }))
    suspend fun switchActiveAccount(accountId: String) = userDao.switchActiveAccount(accountId)
    

    suspend fun logoutAll() = userDao.logoutAll()
    suspend fun update2FA(accountId: String, isEnabled: Boolean) = userDao.update2FA(accountId, isEnabled)
    suspend fun updateProfile(accountId: String, username: String, displayName: String, bio: String, profilePicUrl: String, customStatus: String, phoneNumber: String, dateOfBirth: String, socialMedia: String) = userDao.updateProfile(accountId, username, displayName, bio, profilePicUrl, customStatus, phoneNumber, dateOfBirth, socialMedia)
    suspend fun insertChat(chat: Chat) = chatDao.insertChat(chat)
    suspend fun updateBlockedStatus(chatId: String, isBlocked: Boolean) = chatDao.updateBlockedStatus(chatId, isBlocked)
    suspend fun updateArchiveStatus(chatId: String, isArchived: Boolean) = chatDao.updateArchiveStatus(chatId, isArchived)
    suspend fun updateMuteStatus(chatId: String, isMuted: Boolean) = chatDao.updateMuteStatus(chatId, isMuted)
    suspend fun updateContactStatus(chatId: String, isContact: Boolean) = chatDao.updateContactStatus(chatId, isContact)
    suspend fun updateActionMenuDismissed(chatId: String, isDismissed: Boolean) = chatDao.updateActionMenuDismissed(chatId, isDismissed)
    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message.copy(text = CryptoManager.encrypt(message.text), audioPath = message.audioPath?.let { CryptoManager.encrypt(it) }, mediaPath = message.mediaPath?.let { CryptoManager.encrypt(it) }, documentData = message.documentData?.let { CryptoManager.encrypt(it) }))
    
    suspend fun insertMessageAndUpdateChat(message: Message, plainText: String, senderName: String? = null) {
        insertMessage(message)
        val chat = chatDao.getChatById(message.chatId)
        if (chat != null) {
            chatDao.insertChat(chat.copy(lastMessage = plainText, lastMessageTimestamp = message.timestamp, lastMessageSenderName = senderName))
        }
    }

    suspend fun updateReaction(messageId: String, reaction: String) = messageDao.updateReaction(messageId, reaction)
    suspend fun updateDraft(chatId: String, draft: String?) {
        if (draft.isNullOrBlank()) {
            draftDao.clearDraft(chatId)
        } else {
            draftDao.insertDraft(com.example.ui.Draft(chatId, draft))
        }
    }
    suspend fun updatePinnedMessage(chatId: String, messageId: String?) = chatDao.updatePinnedMessage(chatId, messageId)
    suspend fun updatePinStatus(messageId: String, isPinned: Boolean) = messageDao.updatePinStatus(messageId, isPinned)
    suspend fun updateMessageDelivery(messageId: String, isDelivered: Boolean) = messageDao.updateMessageDelivery(messageId, isDelivered)
        suspend fun updateMessage(message: Message) = messageDao.updateMessage(message)
    suspend fun getMessageById(messageId: String): Message? = messageDao.getMessageById(messageId)
    suspend fun getChatById(chatId: String): Chat? = chatDao.getChatById(chatId)

    suspend fun markAsRead(chatId: String, myUserId: String) {
        messageDao.markAsRead(chatId, myUserId)
        val chat = chatDao.getChatById(chatId)
        if (chat != null) {
            chatDao.insertChat(chat.copy(unreadCount = 0))
        }
    }

    suspend fun insertGroupMember(member: GroupMember) = groupMemberDao.insertGroupMember(member)
    suspend fun updateAdminStatus(chatId: String, userId: String, isAdmin: Boolean) = groupMemberDao.updateAdminStatus(chatId, userId, isAdmin)
    suspend fun removeMember(chatId: String, userId: String) = groupMemberDao.removeMember(chatId, userId)

    suspend fun deleteMessage(messageId: String) = messageDao.deleteMessage(messageId)
    suspend fun deleteSystemSyncMessages() = messageDao.deleteSystemSyncMessages()
    suspend fun deleteExpiredMessages(time: Long) = messageDao.deleteExpiredMessages(time)
    suspend fun clearHistory(chatId: String) = messageDao.clearHistory(chatId)
    suspend fun deleteChat(chatId: String) {
        messageDao.clearHistory(chatId)
        chatDao.deleteChat(chatId)
    }
    suspend fun deleteAccount(accountId: String) = userDao.deleteAccount(accountId)
    suspend fun insertContact(contact: Contact) = contactDao.insertContact(contact.copy(name = CryptoManager.encrypt(contact.name), phoneNumber = contact.phoneNumber?.let { CryptoManager.encrypt(it) }))
    suspend fun deleteContact(contactId: String) = contactDao.deleteContact(contactId)
}
