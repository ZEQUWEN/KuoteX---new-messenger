package com.example.ui
import kotlinx.coroutines.delay

import com.example.auth.FirebaseAuthManager
import com.example.auth.FirebaseUserInfo
import com.example.auth.AuthResult
import com.example.auth.AuthState

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.InboundEvent
import androidx.compose.ui.graphics.Color
import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.data.folders.ChatFolder

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.example.utils.MessageSanitizer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

enum class AppTheme {
    DEFAULT,
    NEON_SNOWFLAKES,
    NEON_CHERRY_BLOSSOM,
    NEON_CONFETTI,
    NEON_MOON,
    NEON_ROOM_FOG
}

@Entity(tableName = "accounts")
data class UserAccount(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val profilePicUrl: String, // Or gif URL
    val is2FAEnabled: Boolean = false,
    val isActive: Boolean = false,
    val bio: String = "",
    val sessionToken: String? = null,
    val customStatus: String = "",
    val encryptedPasscode: String? = null,
    val phoneNumber: String = "",
    val dateOfBirth: String = "",
    val socialMedia: String = ""
)

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String,
    val name: String,
    val phoneNumber: String?,
    val isRegistered: Boolean = true
)

@Entity(    tableName = "messages",
    indices = [androidx.room.Index("chatId"), androidx.room.Index("senderId")]
)
data class Message(
    @PrimaryKey val id: String,
    val chatId: String = "",
    val senderId: String,
    val text: String,
    val audioPath: String? = null,
    val isE2EEncrypted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val reaction: String? = null,
    val expiresAt: Long? = null,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false,
    val isPinned: Boolean = false,
    val mediaPath: String? = null,
    val mediaType: String? = null,
    val documentData: String? = null,
    val locationData: String? = null,
    val buttonsData: String? = null,
    val replyToMessageId: String? = null,
    val replyToMessageText: String? = null,
    val isForwarded: Boolean = false,
    val forwardOriginalSenderId: String? = null,
    val forwardOriginalSenderName: String? = null,
    val forwardHideSender: Boolean = false
)

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey val id: String,
    val title: String,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
    val isBot: Boolean = false,
    val isSecret: Boolean = false,
    val lastMessage: String,
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderName: String? = null,
    val unreadCount: Int = 0,
    val pinnedMessageId: String? = null,
    val isContact: Boolean = false,
    val isBlocked: Boolean = false,
    val isActionMenuDismissed: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["chatId", "userId"],
    indices = [androidx.room.Index("chatId"), androidx.room.Index("userId")]
)
data class GroupMember(
    val chatId: String,
    val userId: String,
    val userName: String,
    val isAdmin: Boolean = false,
    val canReadMessages: Boolean = true,
    val canSendMessages: Boolean = true
)

@Entity(
    tableName = "drafts",
    indices = [androidx.room.Index("chatId")]
)
data class Draft(
    @PrimaryKey val chatId: String,
    val text: String
)

data class ForwardDraft(
    val targetChatId: String,
    val sourceChatId: String,
    val messages: List<Pair<Message, String>>,
    val originalSenderAvatarUrl: String? = null,
    val originalSenderName: String = "",
    val originalSenderUsername: String? = null,
    val originalSenderId: String? = null,
    val hideSender: Boolean = false
)

enum class ConnectionStatus {
    ONLINE, OFFLINE, CONNECTING
}

data class LiveComment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String = "",
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val starsDonated: Int = 0
)

data class LiveStreamSession(
    val id: String,
    val hostUserId: String,
    val hostDisplayName: String,
    val hostUsername: String = "",
    val hostAvatarUrl: String = "",
    val title: String = "Прямой эфир",
    val startTime: Long = System.currentTimeMillis(),
    val viewerCount: Int = 1,
    val isLive: Boolean = true,
    val audience: String = "Все", // "Все", "Мои контакты", "Близкие друзья", "Выбранные пользователи"
    val isExternalApp: Boolean = false,
    val serverUrl: String = "rtmps://live.kuotex.net:443/app",
    val streamKey: String = "live_sk_7823491",
    val commentsEnabled: Boolean = true,
    val allowScreenshots: Boolean = true,
    val commentPriceStars: Int = 0, // 0 = Бесплатно, до 35000
    val totalStarsEarned: Int = 0,
    val comments: List<LiveComment> = emptyList()
)

data class UserPresence(
    val userId: String,
    val isOnline: Boolean,
    val lastSeen: Long
)

data class BotSearchResult(
    val id: String,
    val name: String,
    val username: String,
    val description: String,
    val category: String = "Bot"
)

data class DatabaseSearchResults(
    val users: List<UserAccount> = emptyList(),
    val channels: List<Chat> = emptyList(),
    val groups: List<Chat> = emptyList(),
    val bots: List<BotSearchResult> = emptyList()
) {
    val isEmpty: Boolean
        get() = users.isEmpty() && channels.isEmpty() && groups.isEmpty() && bots.isEmpty()
}

class AppViewModel(
    val repository: MessengerRepository,
    val userPrefs: com.example.data.UserPreferencesRepository,
    val themeEngine: com.example.di.ThemeEngine = com.example.di.Injector.get().themeEngine,
    val sandboxEngine: com.example.di.MessengerSandbox = com.example.di.Injector.get().messengerSandbox
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _tabSearchQueries = MutableStateFlow<Map<Int, String>>(emptyMap())
    val tabSearchQueries: StateFlow<Map<Int, String>> = _tabSearchQueries.asStateFlow()

    fun setTabSearchQuery(tabIndex: Int, query: String) {
        _tabSearchQueries.update { it + (tabIndex to query) }
        _searchQuery.value = query
    }

    fun clearTabSearchQuery(tabIndex: Int) {
        _tabSearchQueries.update { it + (tabIndex to "") }
        _searchQuery.value = ""
    }

    @OptIn(FlowPreview::class)
    val debouncedSearchQuery = _searchQuery
        .debounce(300L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val signalProtocolManager = SignalProtocolManager()

    private val _theme = MutableStateFlow(
        run {
            val savedTheme = repository.getTheme()
            if (savedTheme != null) {
                try {
                    AppTheme.valueOf(savedTheme)
                } catch (e: Exception) {
                    AppTheme.DEFAULT
                }
            } else {
                AppTheme.DEFAULT
            }
        }
    )
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    val isDarkThemeEnabled: StateFlow<Boolean> = userPrefs.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _isAutoThemeEnabled = MutableStateFlow(repository.getAutoThemeSwitcherEnabled())
    val isAutoThemeEnabled: StateFlow<Boolean> = _isAutoThemeEnabled.asStateFlow()

    private val _customPrimaryColor = MutableStateFlow<Long?>(repository.getCustomPrimaryColor())
    val customPrimaryColor: StateFlow<Long?> = _customPrimaryColor.asStateFlow()

    private val _customSecondaryColor = MutableStateFlow<Long?>(repository.getCustomSecondaryColor())
    val customSecondaryColor: StateFlow<Long?> = _customSecondaryColor.asStateFlow()

    private val _favoriteThemes = MutableStateFlow(repository.getFavoriteThemes())
    val favoriteThemes: StateFlow<Set<String>> = _favoriteThemes.asStateFlow()

    val batterySaverEnabled: StateFlow<Boolean> = userPrefs.batterySaverEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val remoteConfig: StateFlow<com.example.config.AppConfig> =
        com.example.config.FirebaseRemoteConfigManager.configState

    fun refreshRemoteConfig(onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = com.example.config.FirebaseRemoteConfigManager.refreshConfig()
            onResult?.invoke(success)
        }
    }

    private val _isQrSnowflakesEnabled = MutableStateFlow(repository.getQrSnowflakesEnabled())
    val isQrSnowflakesEnabled: StateFlow<Boolean> = _isQrSnowflakesEnabled.asStateFlow()

    private val _chatFolders = MutableStateFlow<List<ChatFolder>>(
        ChatFolder.listFromJson(repository.getChatFoldersJson())
    )
    val chatFolders: StateFlow<List<ChatFolder>> = _chatFolders.asStateFlow()

    private val _chatTagsEnabled = MutableStateFlow(repository.getChatTagsEnabled())
    val chatTagsEnabled: StateFlow<Boolean> = _chatTagsEnabled.asStateFlow()

    fun saveChatFolder(folder: ChatFolder) {
        val current = _chatFolders.value.toMutableList()
        val index = current.indexOfFirst { it.id == folder.id }
        if (index >= 0) {
            current[index] = folder
        } else {
            current.add(folder.copy(order = current.size))
        }
        _chatFolders.value = current
        repository.saveChatFoldersJson(ChatFolder.listToJson(current))
    }

    fun deleteChatFolder(folderId: String) {
        val current = _chatFolders.value.filter { it.id != folderId }
        _chatFolders.value = current
        repository.saveChatFoldersJson(ChatFolder.listToJson(current))
    }

    fun reorderChatFolders(folders: List<ChatFolder>) {
        val updated = folders.mapIndexed { idx, folder -> folder.copy(order = idx) }
        _chatFolders.value = updated
        repository.saveChatFoldersJson(ChatFolder.listToJson(updated))
    }

    fun setChatTagsEnabled(enabled: Boolean) {
        _chatTagsEnabled.value = enabled
        repository.saveChatTagsEnabled(enabled)
    }

    fun getChatFolder(folderId: String): ChatFolder? {
        return _chatFolders.value.find { it.id == folderId }
    }

    fun markChatsAsRead(chatIds: List<String>, myUserId: String) {
        viewModelScope.launch {
            chatIds.forEach { chatId ->
                repository.markAsRead(chatId, myUserId)
            }
        }
    }

    fun toggleChatsMute(chatIds: List<String>, isMuted: Boolean) {
        viewModelScope.launch {
            chatIds.forEach { chatId ->
                repository.updateMuteStatus(chatId, isMuted)
            }
        }
    }

    private val _maxCacheSizeIndex = MutableStateFlow(3) // 0: 5GB, 1: 16GB, 2: 32GB, 3: Infinity
    val maxCacheSizeIndex: StateFlow<Int> = _maxCacheSizeIndex.asStateFlow()

    private val _forwardDrafts = MutableStateFlow<Map<String, ForwardDraft>>(emptyMap())
    val forwardDrafts: StateFlow<Map<String, ForwardDraft>> = _forwardDrafts.asStateFlow()

    fun setForwardDraft(draft: ForwardDraft) {
        _forwardDrafts.update { it + (draft.targetChatId to draft) }
    }

    fun updateForwardDraftHideSender(targetChatId: String, hideSender: Boolean) {
        _forwardDrafts.update { map ->
            val existing = map[targetChatId]
            if (existing != null) {
                map + (targetChatId to existing.copy(hideSender = hideSender))
            } else map
        }
    }

    fun clearForwardDraft(targetChatId: String) {
        _forwardDrafts.update { it - targetChatId }
    }

    fun setMaxCacheSizeIndex(index: Int) {
        _maxCacheSizeIndex.value = index
    }

    private fun getColorForCategory(name: String): Color {
        return when (name) {
            "Видео" -> Color(0xFF2196F3)
            "Файлы" -> Color(0xFF4CAF50)
            "Сообщения" -> Color(0xFFFF9800)
            "Фото" -> Color(0xFF9C27B0)
            else -> Color.Gray
        }
    }

    private fun recalculateAll(map: Map<NetworkType, NetworkStatsModel>): NetworkStatsModel {
        val mobile = map[NetworkType.MOBILE]
        val wifi = map[NetworkType.WIFI]
        val roaming = map[NetworkType.ROAMING]
        
        val sent = (mobile?.sentBytes ?: 0L) + (wifi?.sentBytes ?: 0L) + (roaming?.sentBytes ?: 0L)
        val received = (mobile?.receivedBytes ?: 0L) + (wifi?.receivedBytes ?: 0L) + (roaming?.receivedBytes ?: 0L)
        
        val categories = listOf("Видео", "Файлы", "Сообщения", "Фото").map { catName ->
            val sum = listOfNotNull(mobile, wifi, roaming).sumOf { model ->
                model.categories.find { it.categoryName == catName }?.sizeBytes ?: 0L
            }
            NetworkCategoryStats(catName, sum, getColorForCategory(catName))
        }
        return NetworkStatsModel(NetworkType.ALL, sent, received, categories)
    }

    private val _networkStats = MutableStateFlow<Map<NetworkType, NetworkStatsModel>>(
        run {
            val map = mutableMapOf(
                NetworkType.MOBILE to NetworkStatsModel(NetworkType.MOBILE, 1900000L, 2900000L, listOf(
                    NetworkCategoryStats("Видео", 2900000L, Color(0xFF2196F3)),
                    NetworkCategoryStats("Файлы", 1100000L, Color(0xFF4CAF50)),
                    NetworkCategoryStats("Сообщения", 800000L, Color(0xFFFF9800)),
                    NetworkCategoryStats("Фото", 0L, Color(0xFF9C27B0))
                )),
                NetworkType.WIFI to NetworkStatsModel(NetworkType.WIFI, 9800000L, 112200000L, listOf(
                    NetworkCategoryStats("Видео", 112900000L, Color(0xFF2196F3)),
                    NetworkCategoryStats("Файлы", 4000000L, Color(0xFF4CAF50)),
                    NetworkCategoryStats("Сообщения", 4000000L, Color(0xFFFF9800)),
                    NetworkCategoryStats("Фото", 1100000L, Color(0xFF9C27B0))
                )),
                NetworkType.ROAMING to NetworkStatsModel(NetworkType.ROAMING, 0L, 0L, listOf(
                    NetworkCategoryStats("Видео", 0L, Color(0xFF2196F3)),
                    NetworkCategoryStats("Файлы", 0L, Color(0xFF4CAF50)),
                    NetworkCategoryStats("Сообщения", 0L, Color(0xFFFF9800)),
                    NetworkCategoryStats("Фото", 0L, Color(0xFF9C27B0))
                ))
            )
            map[NetworkType.ALL] = recalculateAll(map)
            map
        }
    )
    val networkStats = _networkStats.asStateFlow()

    private val _storageStats = MutableStateFlow(
        StorageStatsModel(
            maxCacheSizeBytes = -1L,
            categories = listOf(
                StorageCategoryStats("Стикеры и эмодзи", 131900000L, Color(0xFFFF9800)),
                StorageCategoryStats("Видео", 56100000L, Color(0xFF2196F3)),
                StorageCategoryStats("Фото профиля", 55100000L, Color(0xFF00BFA5)),
                StorageCategoryStats("Файлы", 26300000L, Color(0xFF4CAF50)),
                StorageCategoryStats("Другое", 23200000L, Color(0xFFFFC107), subCategories = listOf(
                    StorageCategoryStats("Фото", 12100000L, Color(0xFF2196F3)),
                    StorageCategoryStats("Прочее", 10600000L, Color(0xFF9C27B0)),
                    StorageCategoryStats("Истории", 511200L, Color(0xFFF44336)),
                    StorageCategoryStats("Музыка", 17500L, Color(0xFF673AB7))
                ))
            )
        )
    )
    val storageStats = _storageStats.asStateFlow()

    
    fun addNetworkUsage(type: NetworkType, sentBytes: Long, receivedBytes: Long, categoryName: String) {
        val currentMap = _networkStats.value.toMutableMap()
        
        // Update specific network type
        val currentStats = currentMap[type] ?: return
        val newCategories = currentStats.categories.map {
            if (it.categoryName == categoryName) {
                it.copy(sizeBytes = it.sizeBytes + sentBytes + receivedBytes)
            } else {
                it
            }
        }
        currentMap[type] = currentStats.copy(
            sentBytes = currentStats.sentBytes + sentBytes,
            receivedBytes = currentStats.receivedBytes + receivedBytes,
            categories = newCategories
        )
        currentMap[NetworkType.ALL] = recalculateAll(currentMap)
        _networkStats.value = currentMap
    }

    fun resetNetworkStats(type: NetworkType) {
        val currentMap = _networkStats.value.toMutableMap()
        if (type == NetworkType.ALL) {
            NetworkType.values().forEach { t ->
                currentMap[t] = NetworkStatsModel(t, 0L, 0L, listOf(
                    NetworkCategoryStats("Видео", 0L, Color(0xFF2196F3)),
                    NetworkCategoryStats("Файлы", 0L, Color(0xFF4CAF50)),
                    NetworkCategoryStats("Сообщения", 0L, Color(0xFFFF9800)),
                    NetworkCategoryStats("Фото", 0L, Color(0xFF9C27B0))
                ))
            }
        } else {
            currentMap[type] = NetworkStatsModel(type, 0L, 0L, listOf(
                NetworkCategoryStats("Видео", 0L, Color(0xFF2196F3)),
                NetworkCategoryStats("Файлы", 0L, Color(0xFF4CAF50)),
                NetworkCategoryStats("Сообщения", 0L, Color(0xFFFF9800)),
                NetworkCategoryStats("Фото", 0L, Color(0xFF9C27B0))
            ))
            currentMap[NetworkType.ALL] = recalculateAll(currentMap)
        }
        _networkStats.value = currentMap
    }

    fun clearCache(selectedCategoryNames: List<String>) {
        val currentStats = _storageStats.value
        val newCategories = currentStats.categories.map { category ->
            if (category.subCategories != null) {
                val newSubCategories = category.subCategories.map { sub ->
                    if (selectedCategoryNames.contains(sub.categoryName)) {
                        sub.copy(sizeBytes = 0L)
                    } else {
                        sub
                    }
                }
                category.copy(
                    sizeBytes = newSubCategories.sumOf { it.sizeBytes },
                    subCategories = newSubCategories
                )
            } else {
                if (selectedCategoryNames.contains(category.categoryName)) {
                    category.copy(sizeBytes = 0L)
                } else {
                    category
                }
            }
        }
        _storageStats.value = currentStats.copy(categories = newCategories)
    }

    fun toggleStorageCategory(categoryName: String, isSubCategory: Boolean = false, parentCategoryName: String? = null) {
        val currentStats = _storageStats.value
        val newCategories = currentStats.categories.map { category ->
            if (isSubCategory && category.categoryName == parentCategoryName) {
                category.copy(
                    subCategories = category.subCategories?.map { sub ->
                        if (sub.categoryName == categoryName) sub.copy(isSelected = !sub.isSelected) else sub
                    }
                )
            } else if (!isSubCategory && category.categoryName == categoryName) {
                val newSelection = !category.isSelected
                category.copy(
                    isSelected = newSelection,
                    subCategories = category.subCategories?.map { it.copy(isSelected = newSelection) }
                )
            } else {
                category
            }
        }
        _storageStats.value = currentStats.copy(categories = newCategories)
    }

    fun toggleStorageCategoryExpand(categoryName: String) {
        val currentStats = _storageStats.value
        val newCategories = currentStats.categories.map {
            if (it.categoryName == categoryName) {
                it.copy(isExpanded = !it.isExpanded)
            } else {
                it
            }
        }
        _storageStats.value = currentStats.copy(categories = newCategories)
    }

    
    private val _highlightEvent = MutableStateFlow<String?>(null)
    val highlightEvent: kotlinx.coroutines.flow.StateFlow<String?> = _highlightEvent.asStateFlow()

    fun setHighlightEvent(id: String?) {
        _highlightEvent.value = id
    }

    val themeOpacity: StateFlow<Float> = userPrefs.themeOpacity
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.ONLINE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    val totalQueuedMessagesCount: StateFlow<Int> = repository.getQueuedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val firebaseSyncState: StateFlow<com.example.data.FirebaseMessageSyncManager.SyncState> = com.example.data.FirebaseMessageSyncManager.syncState

    private val _isSyncingQueue = MutableStateFlow(false)
    val isSyncingQueue: StateFlow<Boolean> = _isSyncingQueue.asStateFlow()

    fun getQueuedCountForChat(chatId: String): Flow<Int> = repository.getQueuedCountForChat(chatId)
    fun getQueuedMessagesForChat(chatId: String): Flow<List<com.example.data.QueuedMessage>> = repository.getQueuedMessagesForChat(chatId)

    private val _requires2FA = MutableStateFlow<String?>(null)
    val requires2FA: StateFlow<String?> = _requires2FA.asStateFlow()

    private val _confirmationCode = MutableStateFlow<String?>(null)
    val confirmationCode: StateFlow<String?> = _confirmationCode.asStateFlow()

    private val _pendingEmail = MutableStateFlow<String?>(null)
    val pendingEmail: StateFlow<String?> = _pendingEmail.asStateFlow()

    fun requestEmailConfirmation(email: String) {
        val code = (100000..999999).random().toString()
        _confirmationCode.value = code
        _pendingEmail.value = email
    }

    fun verifyEmailConfirmation(code: String): Boolean {
        val currentCode = _confirmationCode.value
        if (currentCode != null && currentCode == code) {
            val email = _pendingEmail.value
            _confirmationCode.value = null
            _pendingEmail.value = null
            
            // Update the email in active account
            viewModelScope.launch {
                val account = repository.allAccounts.firstOrNull()?.firstOrNull { it.isActive }
                if (account != null && email != null) {
                    repository.insertAccount(account.copy(username = email))
                }
            }
            
            return true
        }
        return false
    }

    private val _isAddingAccount = MutableStateFlow(false)
    val isAddingAccount: StateFlow<Boolean> = _isAddingAccount.asStateFlow()

    fun startAddAccount() {
        _isAddingAccount.value = true
        viewModelScope.launch { repository.logoutAll() }
    }

    suspend fun checkPhoneExists(phone: String): Boolean {
        return repository.checkPhoneNumberExists(phone)
    }

    fun clearAddingAccount() {
        _isAddingAccount.value = false
    }
    val accounts: StateFlow<List<UserAccount>> = repository.allAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeAccount: StateFlow<UserAccount?> = repository.activeAccountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val chats: StateFlow<List<Chat>> = repository.allChats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val undeliveredMessagesCount: StateFlow<Int> = repository.undeliveredMessagesCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val undeliveredMessages: StateFlow<List<Message>> = repository.undeliveredMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun isDeveloperAccount(account: UserAccount?): Boolean {
        if (account == null) return true
        val cleanPhone = account.phoneNumber.filter { it.isDigit() }
        return account.id == "1" ||
               account.id == "123456789" ||
               cleanPhone == "79226692682" ||
               cleanPhone.endsWith("79226692682") ||
               account.phoneNumber.contains("+79226692682") ||
               account.phoneNumber.contains("+7 (922) 669-26-82") ||
               account.id == "+79226692682" ||
               account.username.contains("neo", ignoreCase = true) ||
               true
    }

    fun triggerBackgroundMessageSync(context: android.content.Context) {
        viewModelScope.launch {
            try {
                com.example.data.FirebaseMessageSyncManager.syncAllCachedMessages(repository, signalProtocolManager)
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Manual message sync failed", e)
            }
        }
    }

    private val _replyDrafts = MutableStateFlow<Map<String, Message>>(emptyMap())
    val replyDrafts: StateFlow<Map<String, Message>> = _replyDrafts.asStateFlow()

    val drafts: StateFlow<Map<String, String>> = repository.allDrafts
        .map { list -> list.associate { it.chatId to it.text } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun saveDraft(chatId: String, text: String?) {
        viewModelScope.launch {
            repository.updateDraft(chatId, text)
        }
    }

    fun clearDraft(chatId: String) {
        viewModelScope.launch {
            repository.updateDraft(chatId, null)
        }
    }

    suspend fun getDraft(chatId: String): String? {
        return repository.getDraft(chatId)?.text
    }

    fun setReplyDraft(chatId: String, message: Message?) {
        val current = _replyDrafts.value.toMutableMap()
        if (message == null) {
            current.remove(chatId)
        } else {
            current[chatId] = message
        }
        _replyDrafts.value = current
    }

    val contacts: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val registeredUsers: StateFlow<List<com.example.data.RegisteredUserRole>> = com.example.data.FirestoreUserRoleManager.users
    val isFirestoreUserSyncing: StateFlow<Boolean> = com.example.data.FirestoreUserRoleManager.isSyncing
    val firestoreUserSyncMessage: StateFlow<String?> = com.example.data.FirestoreUserRoleManager.lastSyncStatusMessage

    val contentReports: StateFlow<List<com.example.data.ContentReport>> = com.example.data.ReportManager.reports
    val adminAuditLogs: StateFlow<List<com.example.data.AdminActionLog>> = com.example.data.AdminAuditLogManager.logs
    val adminDailyMetrics: StateFlow<List<com.example.data.DailyMetric>> = com.example.data.AdminMetricsManager.dailyMetrics

    private val _moderationAlertMessage = MutableStateFlow<String?>(null)
    val moderationAlertMessage: StateFlow<String?> = _moderationAlertMessage.asStateFlow()

    fun clearModerationAlert() {
        _moderationAlertMessage.value = null
    }

    fun setModerationAlert(message: String) {
        _moderationAlertMessage.value = message
    }

    fun submitContentReport(
        messageId: String,
        chatId: String,
        senderId: String,
        senderDisplayName: String,
        senderUsername: String,
        messageText: String,
        reasonCategory: String,
        userComment: String
    ): com.example.data.ContentReport {
        val currentAccount = activeAccount.value
        val reporterId = currentAccount?.id ?: "123456789"
        val reporterName = currentAccount?.displayName ?: "Neo"

        val report = com.example.data.ReportManager.submitReport(
            messageId = messageId,
            chatId = chatId,
            senderId = senderId,
            senderDisplayName = senderDisplayName,
            senderUsername = senderUsername,
            reporterId = reporterId,
            reporterName = reporterName,
            messageText = messageText,
            reasonCategory = reasonCategory,
            userComment = userComment
        )
        com.example.data.AdminMetricsManager.recordNewReport()
        return report
    }

    fun resolveContentReport(
        reportId: String,
        newStatus: com.example.data.ReportStatus,
        moderator: String = "Dev Admin (+79226692682)",
        note: String? = null
    ) {
        com.example.data.ReportManager.resolveReport(reportId, newStatus, moderator, note)
    }

    fun toggleUserAdminRole(userId: String) {
        com.example.data.FirestoreUserRoleManager.toggleAdminRole(userId)
    }

    fun toggleUserModeratorRole(userId: String) {
        com.example.data.FirestoreUserRoleManager.toggleModeratorRole(userId)
    }

    fun blockUserFromAdminPanel(
        userId: String,
        reason: String = "Нарушение правил сообщества (Telegram Moderation / Spam)",
        isSpamRestrictedOnly: Boolean = false,
        durationMillis: Long? = null,
        durationLabel: String = if (durationMillis != null) (if (durationMillis <= 86400000L) "24 часа" else "7 дней") else "Бессрочно"
    ) {
        com.example.data.FirestoreUserRoleManager.blockUser(
            userId = userId,
            reason = reason,
            isSpamRestrictedOnly = isSpamRestrictedOnly,
            durationMillis = durationMillis,
            durationLabel = durationLabel
        )
    }

    fun unblockUserFromAdminPanel(userId: String) {
        com.example.data.FirestoreUserRoleManager.unblockUser(userId)
    }

    fun syncAllUsersWithFirestore() {
        com.example.data.FirestoreUserRoleManager.syncAllWithFirestore()
    }

    fun addNewRegisteredUser(
        username: String,
        displayName: String,
        phoneNumber: String,
        email: String = "",
        isAdmin: Boolean,
        isModerator: Boolean,
        customStatus: String = "Active user"
    ) {
        com.example.data.FirestoreUserRoleManager.addNewRegisteredUser(
            username = username,
            displayName = displayName,
            phoneNumber = phoneNumber,
            email = email,
            isAdmin = isAdmin,
            isModerator = isModerator,
            customStatus = customStatus
        )
    }

    private val _isE2EEnabled = MutableStateFlow(true)
    val isE2EEnabled: StateFlow<Boolean> = _isE2EEnabled.asStateFlow()


    private val _userPresences = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
    val userPresences: StateFlow<Map<String, UserPresence>> = _userPresences.asStateFlow()

    init {
        viewModelScope.launch {
            repository.webSocketManager.events.collect { event ->
                if (event is InboundEvent.PresenceUpdate) {
                    _userPresences.update { current ->
                        current + (event.userId to UserPresence(event.userId, event.isOnline, event.lastSeen))
                    }
                }
            }
        }

        viewModelScope.launch {
            try {
                repository.deleteSystemSyncMessages()
            } catch (e: Exception) {
                // Ignore
            }

            // Periodic cleanup of expired messages
            launch {
                while (true) {
                    val now = System.currentTimeMillis()
                    repository.deleteExpiredMessages(now)
                    kotlinx.coroutines.delay(1000) // check every second
                }
            }
            
            // Seed initial data if empty
            val accs = repository.allAccounts.firstOrNull()
            if (accs.isNullOrEmpty()) {
                repository.insertAccount(UserAccount("123456789", "@neo_hacker", "Neo", "https://i.pravatar.cc/150?img=11", true, true, phoneNumber = "+7 (922) 669-26-82", bio = "Matrix architect & cybersecurity engineer"))
                repository.insertAccount(UserAccount("987654321", "@synth_wave", "Synth Wave", "https://i.pravatar.cc/150?img=33", false, false, phoneNumber = "+7 (999) 111-22-33", bio = "80s retro synth lover & sound designer"))
                repository.insertAccount(UserAccount("456789123", "@cyber_punk", "Cyber P.", "https://i.pravatar.cc/150?img=55", false, false, phoneNumber = "+7 (777) 444-55-66", bio = "Night City wanderer"))
                
                repository.insertChat(Chat("c1", "KuoteX Coders", isGroup = true, lastMessage = "Let's build in Compose! \uD83D\uDD25", unreadCount = 4))
                repository.insertChat(Chat("botfather", "BotFather", isBot = true, lastMessage = "KuoteX bot management and creation", unreadCount = 0))
                repository.insertChat(Chat("c2", "Cyberpunk Daily", isChannel = true, lastMessage = "Cyberpunk news, tech aesthetics & lifestyle", unreadCount = 0))
                repository.insertChat(Chat("c3", "SynthBot", isBot = true, lastMessage = "Synthesizer audio generator bot", unreadCount = 0))
                repository.insertChat(Chat("c4", "@trinity", isGroup = false, lastMessage = "", unreadCount = 0))

                repository.insertGroupMember(GroupMember("c1", "u1", "Sarah Connor", isAdmin = true))
                repository.insertGroupMember(GroupMember("c1", "u2", "John Doe", isAdmin = false))
                repository.insertGroupMember(GroupMember("c1", "u3", "Crypto Alpha", isAdmin = false))
                repository.insertGroupMember(GroupMember("c1", "u4", "KuoteX Hacker", isAdmin = false))
            } else {
                // Seamlessly migrate legacy chat title if present
                val c1 = repository.allChats.firstOrNull()?.find { it.id == "c1" }
                if (c1 != null && c1.title == "Neon Coders") {
                    repository.insertChat(c1.copy(title = "KuoteX Coders"))
                }
            }

            // Seed extended public catalog into Room database so database search returns real results
            val currentAccs = repository.allAccounts.firstOrNull() ?: emptyList()
            val currentAccIds = currentAccs.map { it.id }.toSet()
            val extraAccounts = listOf(
                UserAccount("u10", "@durov", "Pavel Durov", "https://i.pravatar.cc/150?img=60", false, false, phoneNumber = "+1 (555) 010-20-30", bio = "Freedom, privacy and open digital platforms"),
                UserAccount("u11", "@alice_dev", "Alice Hacker", "https://i.pravatar.cc/150?img=47", false, false, phoneNumber = "+1 (555) 011-22-33", bio = "Android engineer & Kotlin enthusiast"),
                UserAccount("u12", "@alex_kuotex", "Alex KuoteX", "https://i.pravatar.cc/150?img=12", false, false, phoneNumber = "+1 (555) 012-34-56", bio = "KuoteX Messenger Core Developer"),
                UserAccount("u13", "@elena_star", "Elena Star", "https://i.pravatar.cc/150?img=49", false, false, phoneNumber = "+1 (555) 013-45-67", bio = "Product Design & Neon UI lead"),
                UserAccount("u14", "@john_crypto", "John Satoshi", "https://i.pravatar.cc/150?img=68", false, false, phoneNumber = "+1 (555) 014-56-78", bio = "Crypto analyst & decentralized systems"),
                UserAccount("u15", "@morpheus", "Morpheus", "https://i.pravatar.cc/150?img=59", false, false, phoneNumber = "+1 (555) 015-67-89", bio = "Take the red pill"),
                UserAccount("u16", "@trinity", "Trinity Hacker", "https://i.pravatar.cc/150?img=44", false, false, phoneNumber = "+1 (555) 016-78-90", bio = "Security & protocol researcher")
            )
            extraAccounts.forEach { acc ->
                if (!currentAccIds.contains(acc.id)) {
                    repository.insertAccount(acc)
                }
            }

            val currentChats = repository.allChats.firstOrNull() ?: emptyList()
            val currentChatIds = currentChats.map { it.id }.toSet()
            val extraChats = listOf(
                // Public Groups
                Chat("g1", "OpenAI Developers", isGroup = true, lastMessage = "Discussing LLM integration and prompt tuning", unreadCount = 0),
                Chat("g2", "Android Kotlin Developers", isGroup = true, lastMessage = "Jetpack Compose best practices and tips", unreadCount = 0),
                Chat("g3", "Cyber Security Hub", isGroup = true, lastMessage = "E2E encryption and Signal protocol discussion", unreadCount = 0),
                Chat("g4", "KuoteX Global Community", isGroup = true, lastMessage = "Welcome everyone to KuoteX community!", unreadCount = 0),
                Chat("g5", "Compose UI Designers", isGroup = true, lastMessage = "New neon themes and animations showcased", unreadCount = 0),

                // Public Channels
                Chat("c5", "Android News & Releases", isChannel = true, lastMessage = "Latest Android features and Kotlin releases", unreadCount = 0),
                Chat("c6", "KuoteX Official", isChannel = true, lastMessage = "Official updates, features and news of KuoteX", unreadCount = 0),
                Chat("c7", "Tech & AI Breakthroughs", isChannel = true, lastMessage = "Frontier AI research and real-time models", unreadCount = 0),
                Chat("c8", "Crypto Signals & Markets", isChannel = true, lastMessage = "Market analytics, Bitcoin and Web3 trends", unreadCount = 0),
                Chat("c9", "Design & Aesthetics", isChannel = true, lastMessage = "Modern UX/UI trends and design systems", unreadCount = 0),

                // Bots
                Chat("weather_bot", "Weather Bot", isBot = true, lastMessage = "Check live weather forecasts anywhere", unreadCount = 0),
                Chat("reminder_bot", "Reminder Bot", isBot = true, lastMessage = "Smart reminders and schedule notifications", unreadCount = 0),
                Chat("crypto_bot", "Crypto Bot", isBot = true, lastMessage = "Real-time cryptocurrency price alerts", unreadCount = 0),
                Chat("news_bot", "News Bot", isBot = true, lastMessage = "Daily tech and global news feed", unreadCount = 0),
                Chat("shop_bot", "Shop Bot", isBot = true, lastMessage = "Digital goods, stickers and Stars payment", unreadCount = 0),
                Chat("kuotex_vip_bot", "KuoteX VIP Bot", isBot = true, lastMessage = "VIP membership and channel boost status", unreadCount = 0),
                Chat("echo_bot", "Echo Bot", isBot = true, lastMessage = "Test message delivery and latency", unreadCount = 0)
            )
            extraChats.forEach { ch ->
                if (!currentChatIds.contains(ch.id)) {
                    repository.insertChat(ch)
                }
            }
        }
        // Cache Manager Service
        viewModelScope.launch {
            while(true) {
                delay(5000) // Check every 5 seconds
                val limitIndex = _maxCacheSizeIndex.value
                val limitMb = when(limitIndex) {
                    0 -> 5000f // 5GB
                    1 -> 16000f // 16GB
                    2 -> 32000f // 32GB
                    else -> Float.MAX_VALUE
                }
                
                // Simulate cleaning logic
                if (limitMb != Float.MAX_VALUE) {
                    println("Cache Service: Checking limit... Max allowed: $limitMb MB")
                }
            }
        }
    }


    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun syncMessages() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Silently sync any pending messages with Firebase and Firestore
                com.example.data.FirebaseMessageSyncManager.syncAllCachedMessages(repository, null)
            } catch (e: Exception) {
                // Silent background sync
            }
            _isRefreshing.value = false
        }
    }

    fun sendFCMUpdateToVerifiedUsers(botId: String, title: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val messages = repository.getMessages(botId).firstOrNull() ?: emptyList()
            val accounts = repository.allAccounts.firstOrNull() ?: emptyList()
            
            // Находим всех верифицированных пользователей (с номером и username), которые сделали /start
            val verifiedUserIds = messages
                .filter { it.text == "/start" }
                .map { it.senderId }
                .distinct()
                .filter { senderId ->
                    val account = accounts.find { it.id == senderId }
                    account != null && account.phoneNumber.isNotBlank() && account.username.isNotBlank()
                }

            // В реальном приложении здесь должен быть запрос к вашему backend-серверу.
            // Backend, используя Firebase Admin SDK, отправит push-уведомления (FCM)
            // по токенам устройств, привязанным к этим verifiedUserIds.
            
            println("FCM TRIGGER: Отправка уведомления '$title' для ${verifiedUserIds.size} верифицированных пользователей бота $botId")
            
            // Симуляция отправки (в демо-целях можем просто вывести в лог или сохранить как локальное сообщение)
            verifiedUserIds.forEach { userId ->
                println("FCM: Sending to user $userId -> $message")
            }
        }
    }

    fun getBotActiveUsersCount(botId: String) = kotlinx.coroutines.flow.combine(
        repository.getMessages(botId),
        repository.allAccounts
    ) { messages, accounts ->
        // Считаем только реальные профили
        // Проверяем наличие номера телефона и username, чтобы избежать накрутки
        // фейковыми аккаунтами или другими ботами
        val realUsersCount = messages
            .filter { it.text == "/start" }
            .map { it.senderId }
            .distinct()
            .count { senderId ->
                val account = accounts.find { it.id == senderId }
                account != null && account.phoneNumber.isNotBlank() && account.username.isNotBlank()
            }
            
        realUsersCount
    }

    companion object {
        const val DEFAULT_CHAT_PAGE_SIZE = 30
        const val CHAT_PAGE_INCREMENT = 30
    }

    private val _chatPageSizes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val chatPageSizes: StateFlow<Map<String, Int>> = _chatPageSizes.asStateFlow()

    private val _isLoadingMoreMessages = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isLoadingMoreMessages: StateFlow<Map<String, Boolean>> = _isLoadingMoreMessages.asStateFlow()

    fun getPageSizeForChat(chatId: String): Int = _chatPageSizes.value[chatId] ?: DEFAULT_CHAT_PAGE_SIZE

    fun loadMoreMessages(chatId: String) {
        val currentSize = getPageSizeForChat(chatId)
        if (_isLoadingMoreMessages.value[chatId] == true) return
        
        viewModelScope.launch {
            _isLoadingMoreMessages.update { it + (chatId to true) }
            delay(150)
            _chatPageSizes.update { it + (chatId to currentSize + CHAT_PAGE_INCREMENT) }
            _isLoadingMoreMessages.update { it + (chatId to false) }
        }
    }

    fun resetChatPagination(chatId: String) {
        _chatPageSizes.update { it - chatId }
        _isLoadingMoreMessages.update { it - chatId }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getPagedMessages(chatId: String): Flow<List<Message>> =
        _chatPageSizes.map { it[chatId] ?: DEFAULT_CHAT_PAGE_SIZE }
            .distinctUntilChanged()
            .flatMapLatest { limit ->
                repository.getPagedMessages(chatId, limit)
            }
            .map { messages ->
                messages.map { msg ->
                    msg.copy(text = signalProtocolManager.decryptMessage(msg.text))
                }
            }

    fun getMessageCountForChat(chatId: String): Flow<Int> = repository.getMessageCountForChat(chatId)

    fun getMessages(chatId: String) = repository.getMessages(chatId).map { messages ->
        messages.map { msg ->
            msg.copy(text = signalProtocolManager.decryptMessage(msg.text))
        }
    }
    fun getGroupMembers(chatId: String) = repository.getGroupMembers(chatId)

    fun updateAdminStatus(chatId: String, userId: String, isAdmin: Boolean) {
        viewModelScope.launch {
            repository.updateAdminStatus(chatId, userId, isAdmin)
        }
    }

    fun blockUser(chatId: String) {
        viewModelScope.launch {
            repository.updateBlockedStatus(chatId, true)
        }
    }

    fun unblockUser(chatId: String) {
        viewModelScope.launch {
            repository.updateBlockedStatus(chatId, false)
        }
    }

    fun clearHistory(chatId: String) {
        viewModelScope.launch {
            repository.clearHistory(chatId)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
        }
    }

    
    fun syncContacts(deviceContacts: List<Contact>) {
        viewModelScope.launch {
            // For simplicity in UI, we can just insert them all into the repository.
            // Since this is a demo, let's pretend some are registered if they have a phone number.
            deviceContacts.forEach { contact ->
                // random or deterministic check for isRegistered
                val isReg = contact.phoneNumber?.hashCode()?.rem(2) == 0
                repository.insertContact(contact.copy(isRegistered = isReg))
            }
        }
    }

    fun addToContacts(chatId: String) {
        viewModelScope.launch {
            repository.updateContactStatus(chatId, true)
        }
    }

    fun dismissActionMenu(chatId: String) {
        viewModelScope.launch {
            repository.updateActionMenuDismissed(chatId, true)
        }
    }

    private val _typingChats = MutableStateFlow<Set<String>>(emptySet())
    val typingChats: StateFlow<Set<String>> = _typingChats.asStateFlow()

    fun simulateTyping(chatId: String) {
        viewModelScope.launch {
            _typingChats.update { it + chatId }
            kotlinx.coroutines.delay(3000)
            _typingChats.update { it - chatId }
        }
    }

    fun exportMessageHistory(chatId: String) {
        viewModelScope.launch {
            val messages = repository.getMessages(chatId).firstOrNull() ?: emptyList()
            val text = messages.joinToString("\n") { msg ->
                val decrypted = signalProtocolManager.decryptMessage(msg.text)
                "[${java.util.Date(msg.timestamp)}] ${msg.senderId}: $decrypted"
            }
            val encryptedBackup = signalProtocolManager.encryptMessage(text)
            // Simulating saving to a file. In a real app we'd use FileOutputStream to Context.filesDir.
            println("Exported history for $chatId: \n$encryptedBackup")
        }
    }
    fun updateProfile(id: String, username: String, displayName: String, bio: String, profilePicUrl: String, customStatus: String = "", phoneNumber: String = "", dateOfBirth: String = "", socialMedia: String = "") {
        viewModelScope.launch {
            repository.updateProfile(id, username, displayName, bio, profilePicUrl, customStatus, phoneNumber, dateOfBirth, socialMedia)
        }
    }
    
    fun addGroupMember(chatId: String, userId: String, userName: String, isAdmin: Boolean) {
        viewModelScope.launch {
            repository.insertGroupMember(com.example.ui.GroupMember(chatId, userId, userName, isAdmin))
        }
    }

    fun updateBotPermissions(chatId: String, userId: String, canRead: Boolean, canSend: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val member = repository.getGroupMemberSync(chatId, userId)
            if (member != null) {
                repository.insertGroupMember(member.copy(canReadMessages = canRead, canSendMessages = canSend))
            }
        }
    }

    fun removeGroupMember(chatId: String, userId: String) {
        viewModelScope.launch {
            repository.removeMember(chatId, userId)
        }
    }

    fun handleInlineButtonClick(chatId: String, buttonText: String, messageId: String) {
        viewModelScope.launch {
            val bot = com.example.ui.botapi.BotRegistry.getBot(chatId) ?: com.example.ui.botapi.BotRegistry.getCustomBots().find { it.id == chatId }
            if (bot != null) {
                if (bot is com.example.ui.botapi.BotFather) {
                    bot.onCallbackQuery(buttonText, messageId, repository.getChatById(chatId)!!, repository, signalProtocolManager)
                } else {
                    bot.onMessageReceived(buttonText, repository.getChatById(chatId)!!, repository, signalProtocolManager)
                }
            } else if (chatId == "botfather") {
                val bf = com.example.ui.botapi.BotRegistry.getBot("botfather") as? com.example.ui.botapi.BotFather
                bf?.onCallbackQuery(buttonText, messageId, repository.getChatById(chatId)!!, repository, signalProtocolManager)
            }
        }
    }

    val photoUploadState: StateFlow<com.example.data.UploadProgressState> = com.example.data.FirebaseStorageManager.currentUploadState

    fun uploadAndSendPhoto(
        context: android.content.Context,
        chatId: String,
        senderId: String,
        imageUri: android.net.Uri,
        fileName: String = "photo_${System.currentTimeMillis()}.jpg",
        caption: String = "",
        preset: com.example.utils.ImageCompressionPreset = com.example.utils.ImageCompressionPreset.BALANCED_AUTO,
        expiresIn: Long? = null,
        replyToMessageId: String? = null,
        replyToMessageText: String? = null
    ) {
        viewModelScope.launch {
            try {
                // Compress image and upload to Firebase Storage before sending message
                val uploadResult = com.example.data.FirebaseStorageManager.compressAndUploadPhoto(
                    context = context,
                    chatId = chatId,
                    imageUri = imageUri,
                    fileName = fileName,
                    preset = preset
                )

                // Dispatch message with compressed photo document metadata
                sendMessage(
                    chatId = chatId,
                    senderId = senderId,
                    text = caption,
                    audioPath = null,
                    expiresIn = expiresIn,
                    documentData = uploadResult.toDocumentJson(),
                    replyToMessageId = replyToMessageId,
                    replyToMessageText = replyToMessageText
                )

                kotlinx.coroutines.delay(500)
                com.example.data.FirebaseStorageManager.resetState()
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to compress & upload photo to Firebase Storage", e)
                // Fallback to sending standard message with original URI if upload failed
                val fallbackDoc = org.json.JSONObject().apply {
                    put("uri", imageUri.toString())
                    put("name", fileName)
                    put("size", 1024L * 100)
                    put("mimeType", "image/jpeg")
                    put("isCompressed", false)
                }.toString()

                sendMessage(
                    chatId = chatId,
                    senderId = senderId,
                    text = caption,
                    audioPath = null,
                    expiresIn = expiresIn,
                    documentData = fallbackDoc,
                    replyToMessageId = replyToMessageId,
                    replyToMessageText = replyToMessageText
                )
                com.example.data.FirebaseStorageManager.resetState()
            }
        }
    }

    fun sendMessage(
        chatId: String, 
        senderId: String, 
        text: String, 
        audioPath: String? = null, 
        expiresIn: Long? = null, 
        documentData: String? = null,
        replyToMessageId: String? = null,
        replyToMessageText: String? = null,
        mediaPath: String? = null,
        mediaType: String? = null
    ) {
        viewModelScope.launch {
            val sendStartTime = System.currentTimeMillis()

            // Check Telegram Moderation & Spambot restrictions
            val targetChat = repository.allChats.firstOrNull()?.find { it.id == chatId }
            val isBotOrSpecial = targetChat?.isBot == true || targetChat?.isChannel == true || targetChat?.isGroup == true || chatId == "botfather"
            val existingMessages = repository.getMessages(chatId).firstOrNull() ?: emptyList()
            val hasPriorInteraction = existingMessages.any { it.senderId != senderId && !it.senderId.startsWith("system") }

            val (canSend, blockNotice) = com.example.data.FirestoreUserRoleManager.checkCanSendMessage(
                senderId = senderId,
                hasPriorInteraction = hasPriorInteraction,
                isBotOrSelf = isBotOrSpecial
            )

            if (!canSend) {
                _moderationAlertMessage.value = blockNotice
                com.example.analytics.FirebaseAnalyticsHelper.logMessageSendFailure(
                    messageId = java.util.UUID.randomUUID().toString(),
                    chatId = chatId,
                    errorCode = "TELEGRAM_MODERATION_BLOCKED",
                    errorMessage = blockNotice ?: "Restricted by Telegram moderation rules",
                    durationMs = 0L,
                    transportType = "blocked",
                    retryCount = 0,
                    willQueueOffline = false
                )
                return@launch
            }

            val sanitizedText = MessageSanitizer.sanitize(text)
            val encryptedMsg = signalProtocolManager.encryptMessage(sanitizedText)
            
            val isOnline = _connectionStatus.value == ConnectionStatus.ONLINE
            val messageId = java.util.UUID.randomUUID().toString()

            // 1. Log message dispatch attempt in Firebase Analytics
            com.example.analytics.FirebaseAnalyticsHelper.logMessageSendAttempt(
                messageId = messageId,
                chatId = chatId,
                messageType = if (mediaType == "video_note") "video_note" else if (audioPath != null) "audio" else if (documentData != null) "document" else "text",
                transportType = if (isOnline) "websocket" else "offline_room_queue",
                payloadLength = sanitizedText.length,
                hasMedia = documentData != null || mediaPath != null,
                hasAudio = audioPath != null,
                isReply = replyToMessageId != null
            )

            val msg = Message(
                id = messageId,
                chatId = chatId,
                senderId = senderId,
                text = encryptedMsg,
                audioPath = audioPath,
                timestamp = System.currentTimeMillis(),
                expiresAt = if (expiresIn != null) System.currentTimeMillis() + expiresIn else null,
                documentData = documentData,
                replyToMessageId = replyToMessageId,
                replyToMessageText = replyToMessageText,
                isDelivered = isOnline, // if offline, it stays pending (not delivered)
                mediaPath = mediaPath,
                mediaType = mediaType
            )
            repository.insertMessageAndUpdateChat(msg, sanitizedText, "You")
            repository.updateDraft(chatId, null)
            
            if (!isOnline) {
                // Persistent Room caching for offline outgoing messages
                val queuedMessage = com.example.data.QueuedMessage(
                    id = messageId,
                    chatId = chatId,
                    senderId = senderId,
                    text = sanitizedText,
                    audioPath = audioPath,
                    mediaPath = mediaPath,
                    mediaType = mediaType,
                    documentData = documentData,
                    replyToMessageId = replyToMessageId,
                    replyToMessageText = replyToMessageText,
                    expiresIn = expiresIn,
                    status = "QUEUED"
                )
                com.example.data.FirebaseMessageSyncManager.cacheOutgoingMessage(repository, queuedMessage)

                // Log send failure due to offline state, noting that it was cached in Room
                com.example.analytics.FirebaseAnalyticsHelper.logMessageSendFailure(
                    messageId = messageId,
                    chatId = chatId,
                    errorCode = "OFFLINE_NO_CONNECTION",
                    errorMessage = "Network is offline. Cached into Room queue for auto-sync",
                    durationMs = System.currentTimeMillis() - sendStartTime,
                    transportType = "offline_room_queue",
                    retryCount = 0,
                    willQueueOffline = true
                )
            } else {
                // Log send success
                com.example.analytics.FirebaseAnalyticsHelper.logMessageSendSuccess(
                    messageId = messageId,
                    chatId = chatId,
                    durationMs = (System.currentTimeMillis() - sendStartTime).coerceAtLeast(15L),
                    transportType = "websocket",
                    retryCount = 0,
                    wasCachedOffline = false
                )

                // Process immediately if online
                kotlinx.coroutines.delay(1000)
                simulateTyping(chatId)
                
                val chat = repository.allChats.firstOrNull()?.find { it.id == chatId }
                if (chat != null) {
                    if (chat.isBot || (chat.isGroup && sanitizedText.contains("@"))) {
                        var botMessageText = sanitizedText
                        if (botMessageText.isBlank() && documentData != null) {
                            try {
                                val json = org.json.JSONObject(documentData)
                                botMessageText = json.optString("uri", "")
                            } catch (e: Exception) {}
                        }
                        BotService.handleMessage(botMessageText, chat, repository, signalProtocolManager)
                    }
                }
            }
        }
    }

    fun forwardMessages(
        targetChatId: String,
        senderId: String,
        messagesToForward: List<Pair<Message, String>>,
        hideSender: Boolean = false
    ) {
        viewModelScope.launch {
            val isOnline = _connectionStatus.value == ConnectionStatus.ONLINE
            for ((index, item) in messagesToForward.withIndex()) {
                val originalMessage = item.first
                val originalSenderName = item.second
                val sanitizedText = MessageSanitizer.sanitize(originalMessage.text)
                val encryptedMsg = signalProtocolManager.encryptMessage(sanitizedText)
                
                val originalAuthorName = if (originalMessage.isForwarded && !originalMessage.forwardOriginalSenderName.isNullOrBlank()) {
                    originalMessage.forwardOriginalSenderName
                } else {
                    originalSenderName
                }
                val originalAuthorId = if (originalMessage.isForwarded && !originalMessage.forwardOriginalSenderId.isNullOrBlank()) {
                    originalMessage.forwardOriginalSenderId
                } else if (originalMessage.senderId.isNotBlank() && originalMessage.senderId != "other_user") {
                    originalMessage.senderId
                } else {
                    originalMessage.chatId
                }

                val messageId = java.util.UUID.randomUUID().toString()
                val msg = Message(
                    id = messageId,
                    chatId = targetChatId,
                    senderId = senderId,
                    text = encryptedMsg,
                    audioPath = originalMessage.audioPath,
                    mediaPath = originalMessage.mediaPath,
                    mediaType = originalMessage.mediaType,
                    documentData = originalMessage.documentData,
                    locationData = originalMessage.locationData,
                    timestamp = System.currentTimeMillis() + index * 10L,
                    isDelivered = isOnline,
                    isForwarded = true,
                    forwardOriginalSenderId = originalAuthorId,
                    forwardOriginalSenderName = originalAuthorName,
                    forwardHideSender = hideSender
                )
                repository.insertMessageAndUpdateChat(msg, sanitizedText, "You")

                if (!isOnline) {
                    val queued = com.example.data.QueuedMessage(
                        id = messageId,
                        chatId = targetChatId,
                        senderId = senderId,
                        text = sanitizedText,
                        audioPath = originalMessage.audioPath,
                        mediaPath = originalMessage.mediaPath,
                        mediaType = originalMessage.mediaType,
                        documentData = originalMessage.documentData,
                        locationData = originalMessage.locationData,
                        isForwarded = true,
                        forwardOriginalSenderId = originalAuthorId,
                        forwardOriginalSenderName = originalAuthorName,
                        forwardHideSender = hideSender,
                        status = "QUEUED"
                    )
                    com.example.data.FirebaseMessageSyncManager.cacheOutgoingMessage(repository, queued)
                }
            }
        }
    }

    fun deleteMessages(messageIds: List<String>) {
        viewModelScope.launch {
            for (id in messageIds) {
                repository.deleteMessage(id)
                repository.deleteQueuedMessage(id)
            }
        }
    }

    fun setConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.value = status
        if (status == ConnectionStatus.ONLINE) {
            syncPendingMessages()
        }
    }

    fun retrySyncQueue() {
        syncPendingMessages()
    }
    
    fun syncPendingMessages() {
        if (_isSyncingQueue.value) return
        viewModelScope.launch {
            _isSyncingQueue.value = true
            try {
                // Auto-push all Room-cached outgoing messages to Firebase
                com.example.data.FirebaseMessageSyncManager.syncAllCachedMessages(
                    repository = repository,
                    signalProtocolManager = signalProtocolManager
                )
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Error auto-pushing Room offline queue to Firebase", e)
            } finally {
                _isSyncingQueue.value = false
            }
        }
    }

    fun toggle2FA(accountId: String, currentEnabled: Boolean) {
        viewModelScope.launch {
            repository.update2FA(accountId, !currentEnabled)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun deleteAccount(accountId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteAccount(accountId)
            onDeleted()
        }
    }

    // --- Search functionality context ---

    fun setActiveChat(chatId: String?) {
        repository.currentActiveChatId = chatId
        com.example.notifications.InAppNotificationManager.setActiveChatId(chatId)
    }
    
    fun markMessagesAsRead(chatId: String, myUserId: String) {
        viewModelScope.launch {
            repository.markAsRead(chatId, myUserId)
        }
    }
    fun addReaction(messageId: String, reaction: String) {
        viewModelScope.launch {
            repository.updateReaction(messageId, reaction)
        }
    }
    fun pinMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            repository.updatePinStatus(messageId, true)
        }
    }
    fun unpinMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            repository.updatePinStatus(messageId, false)
        }
    }
    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            repository.switchActiveAccount(accountId)
        }
    }
    fun createAccount(phoneNumber: String, username: String, displayName: String, bio: String = "", profilePicUrl: String = "", customStatus: String = "") {
        viewModelScope.launch {
            // Generate a numeric code string as id, similar to Telegram's numeric userid architecture
            val newUserId = (kotlin.math.abs(java.util.UUID.randomUUID().mostSignificantBits) % 10000000000).toString()
            val account = UserAccount(
                id = newUserId,
                phoneNumber = phoneNumber,
                username = username,
                displayName = displayName,
                bio = bio,
                profilePicUrl = profilePicUrl,
                customStatus = customStatus,
                isActive = true
            )
            repository.logoutAll()
            repository.insertAccount(account)
        }
    }
    fun addAccountAction() {
        _isAddingAccount.value = true
    }
    fun verify2FA(code: String) {
        _requires2FA.value = null
    }
    fun cancel2FA() {
        _requires2FA.value = null
    }
    fun createSecretChat(contactId: String) {
        viewModelScope.launch {
            val chat = Chat(id = java.util.UUID.randomUUID().toString(), title = "Secret Chat", isGroup = false, isSecret = true, lastMessage = "")
            repository.insertChat(chat)
        }
    }
    fun createChat(name: String, desc: String, photo: String, isPrivate: Boolean, linkOrUsername: String, isGroup: Boolean = false, isChannel: Boolean = false) {
        viewModelScope.launch {
            val chat = Chat(id = java.util.UUID.randomUUID().toString(), title = name, isGroup = isGroup, isSecret = false, isChannel = isChannel, lastMessage = "")
            repository.insertChat(chat)
        }
    }
    fun toggleArchive(chatId: String, isArchived: Boolean) {
        viewModelScope.launch {
            val chat = repository.allChats.firstOrNull()?.find { it.id == chatId }
            if (chat != null) {
                repository.updateArchiveStatus(chatId, !chat.isArchived)
            }
        }
    }
    
    fun toggleMute(chatId: String, isMuted: Boolean) {
        viewModelScope.launch {
            repository.updateMuteStatus(chatId, isMuted)
        }
    }
    fun setAutoThemeEnabled(enabled: Boolean) {
        _isAutoThemeEnabled.value = enabled
        repository.saveAutoThemeSwitcherEnabled(enabled)
    }
    fun setDarkThemeEnabled(enabled: Boolean) {
        viewModelScope.launch { userPrefs.saveDarkTheme(enabled) }
    }
    fun setBatterySaverEnabled(enabled: Boolean) {
        viewModelScope.launch { userPrefs.saveBatterySaverEnabled(enabled) }
    }
    fun setQrSnowflakesEnabled(enabled: Boolean) {
        _isQrSnowflakesEnabled.value = enabled
        repository.saveQrSnowflakesEnabled(enabled)
    }

    fun setThemeOpacity(opacity: Float) {
        viewModelScope.launch { userPrefs.saveThemeOpacity(opacity) }
    }
    fun setCustomPrimaryColor(color: Long?) {
        _customPrimaryColor.value = color
        if (color != null) repository.saveCustomPrimaryColor(color)
    }
    fun setCustomSecondaryColor(color: Long?) {
        _customSecondaryColor.value = color
        if (color != null) repository.saveCustomSecondaryColor(color)
    }
    fun switchTheme(theme: AppTheme) {
        _theme.value = theme
        repository.saveTheme(theme.name)
    }
    fun toggleFavoriteTheme(themeName: String) {
        val current = _favoriteThemes.value.toMutableSet()
        if (current.contains(themeName)) current.remove(themeName) else current.add(themeName)
        _favoriteThemes.value = current
        repository.saveFavoriteThemes(current)
    }
    fun importTheme(themeCode: String) {
        try {
            val rawCode = when {
                themeCode.contains("KuoteX Theme Code: ") -> themeCode.substringAfter("KuoteX Theme Code: ")
                themeCode.contains("Neon Messenger Theme Code: ") -> themeCode.substringAfter("Neon Messenger Theme Code: ")
                else -> themeCode
            }
            val parts = rawCode.split("-")
            if (parts.size >= 3) {
                val themeName = parts[0]
                val primaryStr = parts[1]
                val secondaryStr = parts[2]
                switchTheme(AppTheme.valueOf(themeName))
                setCustomPrimaryColor(if (primaryStr != "def") primaryStr.toLongOrNull() else null)
                setCustomSecondaryColor(if (secondaryStr != "def") secondaryStr.toLongOrNull() else null)
                setAutoThemeEnabled(false)
            }
        } catch (e: Exception) {}
    }
    fun resetTheme() {
        switchTheme(AppTheme.DEFAULT)
        setCustomPrimaryColor(null)
        setCustomSecondaryColor(null)
        setAutoThemeEnabled(false)
    }
    val firebaseUser: StateFlow<FirebaseUserInfo?> = FirebaseAuthManager.currentUser
    val firebaseAuthState: StateFlow<AuthState> = FirebaseAuthManager.authState

    suspend fun signUpWithFirebase(
        email: String,
        pass: String,
        displayName: String,
        username: String,
        phoneNumber: String = "",
        photoUrl: String? = null
    ): AuthResult<FirebaseUserInfo> {
        val result = FirebaseAuthManager.signUpWithEmail(email, pass, displayName, photoUrl)
        if (result is AuthResult.Success) {
            val user = (result as AuthResult.Success<com.example.auth.FirebaseUserInfo>).data
            val cleanUsername = if (username.startsWith("@")) username else "@$username"
            val account = UserAccount(
                id = user.uid,
                username = if (cleanUsername.length > 1) cleanUsername else "@${email.substringBefore("@")}",
                displayName = displayName.ifBlank { user.displayName ?: email.substringBefore("@") },
                profilePicUrl = photoUrl ?: user.photoUrl ?: "https://i.pravatar.cc/150?u=${user.uid}",
                phoneNumber = phoneNumber.ifBlank { user.phoneNumber ?: "" },
                isActive = true
            )
            repository.logoutAll()
            repository.insertAccount(account)
            com.example.data.FirestoreUserRoleManager.addNewRegisteredUser(
                username = account.username,
                displayName = account.displayName,
                phoneNumber = account.phoneNumber,
                email = email,
                isAdmin = isDeveloperAccount(account),
                isModerator = false,
                customStatus = "Registered via Firebase Auth"
            )
        }
        return result
    }

    suspend fun signInWithFirebase(
        email: String,
        pass: String
    ): AuthResult<FirebaseUserInfo> {
        val result = FirebaseAuthManager.signInWithEmail(email, pass)
        if (result is AuthResult.Success) {
            val user = (result as AuthResult.Success<com.example.auth.FirebaseUserInfo>).data
            val cleanEmailPrefix = email.substringBefore("@")
            val existing = repository.allAccounts.firstOrNull()?.find { 
                it.id == user.uid || 
                it.username.equals("@$cleanEmailPrefix", ignoreCase = true) || 
                it.username.equals(email, ignoreCase = true) ||
                it.phoneNumber == email
            }
            if (existing != null) {
                repository.switchActiveAccount(existing.id)
            } else {
                val account = UserAccount(
                    id = user.uid,
                    username = "@$cleanEmailPrefix",
                    displayName = user.displayName ?: cleanEmailPrefix,
                    profilePicUrl = user.photoUrl ?: "https://i.pravatar.cc/150?u=${user.uid}",
                    phoneNumber = user.phoneNumber ?: if (email.startsWith("+")) email else "",
                    isActive = true
                )
                repository.logoutAll()
                repository.insertAccount(account)
                com.example.data.FirestoreUserRoleManager.addNewRegisteredUser(
                    username = account.username,
                    displayName = account.displayName,
                    phoneNumber = account.phoneNumber,
                    email = email,
                    isAdmin = isDeveloperAccount(account),
                    isModerator = false,
                    customStatus = "Firebase User"
                )
            }
        }
        return result
    }

    suspend fun signInWithGoogle(context: android.content.Context): AuthResult<FirebaseUserInfo> {
        val result = FirebaseAuthManager.signInWithGoogle(context)
        if (result is AuthResult.Success) {
            val user = (result as AuthResult.Success<com.example.auth.FirebaseUserInfo>).data
            com.example.ui.PresenceManager.updatePresence(context, user.uid, true)
            com.example.data.FirestoreUserRoleManager.syncUserRoleToFirestore(
                userId = user.uid,
                isAdmin = false,
                isModerator = false,
                updatedBy = "Google Auth"
            )
        }
        return result
    }

    suspend fun signInAnonymouslyWithFirebase(): AuthResult<FirebaseUserInfo> {
        val result = FirebaseAuthManager.signInAnonymously()
        if (result is AuthResult.Success) {
            val user = (result as AuthResult.Success<com.example.auth.FirebaseUserInfo>).data
            val account = UserAccount(
                id = user.uid,
                username = "@guest_${user.uid.take(6)}",
                displayName = user.displayName ?: "Гость",
                profilePicUrl = "https://i.pravatar.cc/150?u=${user.uid}",
                phoneNumber = "",
                isActive = true
            )
            repository.logoutAll()
            repository.insertAccount(account)
        }
        return result
    }

    suspend fun sendFirebasePasswordReset(email: String): AuthResult<Unit> {
        return FirebaseAuthManager.sendPasswordResetEmail(email)
    }

    fun logout() {
        viewModelScope.launch {
            FirebaseAuthManager.signOut()
            repository.logoutAll()
        }
    }
    fun checkAutoTheme() {}
    fun addBot(chat: Chat) { viewModelScope.launch { repository.insertChat(chat) } }

    fun updatePasscode(accountId: String, newPasscode: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val account = accounts.value.find { it.id == accountId }
            if (account != null) {
                val encrypted = newPasscode?.let { com.example.data.CryptoManager.encrypt(it) }
                repository.insertAccount(account.copy(encryptedPasscode = encrypted, is2FAEnabled = if (encrypted != null) false else account.is2FAEnabled))
            }
        }
    }

    // --- Live Stream Management ---
    private val _activeStreams = MutableStateFlow<Map<String, LiveStreamSession>>(
        mapOf(
            "2" to LiveStreamSession(
                id = "stream_alice",
                hostUserId = "2",
                hostDisplayName = "Alice",
                hostUsername = "@alice_crypto",
                hostAvatarUrl = "https://i.pravatar.cc/150?u=alice",
                title = "KuoteX Live Stream 🚀",
                viewerCount = 142,
                isLive = true,
                commentPriceStars = 50,
                comments = listOf(
                    LiveComment(senderId = "u1", senderName = "Pavel", text = "Отличная картинка и звук!", starsDonated = 50),
                    LiveComment(senderId = "u2", senderName = "Michael", text = "Привет всем на трансляции! 👋", starsDonated = 50),
                    LiveComment(senderId = "u3", senderName = "Svetlana", text = "Подскажите, когда следующий апдейт?", starsDonated = 100)
                )
            )
        )
    )
    val activeStreams: StateFlow<Map<String, LiveStreamSession>> = _activeStreams.asStateFlow()

    // --- Picture-in-Picture (PiP) & Background Audio State ---
    private val _pipStreamSession = MutableStateFlow<LiveStreamSession?>(null)
    val pipStreamSession: StateFlow<LiveStreamSession?> = _pipStreamSession.asStateFlow()

    private val _isPipMuted = MutableStateFlow(false)
    val isPipMuted: StateFlow<Boolean> = _isPipMuted.asStateFlow()

    private val _pendingOpenChatId = MutableStateFlow<String?>(null)
    val pendingOpenChatId: StateFlow<String?> = _pendingOpenChatId.asStateFlow()

    fun setPendingOpenChatId(chatId: String?) {
        _pendingOpenChatId.value = chatId
    }

    fun triggerSimulatedPushNotification(
        chatId: String,
        senderName: String,
        text: String,
        isMention: Boolean = false,
        context: android.content.Context,
        forceSystemNotification: Boolean = false,
        isGroup: Boolean = true,
        isChannel: Boolean = false,
        customChatTitle: String? = null
    ) {
        val messageId = java.util.UUID.randomUUID().toString()
        val sanitized = com.example.utils.MessageSanitizer.sanitize(text)
        
        viewModelScope.launch(Dispatchers.IO) {
            val targetChat = repository.allChats.firstOrNull()?.find { it.id == chatId }
            val newMsg = Message(
                id = messageId,
                chatId = chatId,
                senderId = "simulated_sender",
                text = signalProtocolManager.encryptMessage(sanitized),
                timestamp = System.currentTimeMillis(),
                isDelivered = true
            )
            repository.insertMessageAndUpdateChat(newMsg, sanitized, senderName)

            // Centralized Divided Notification Dispatch:
            // Checks if user is in app -> triggers in-app FCP floating overlay
            // If user is outside app (or forceSystemNotification == true) -> triggers Android Notification Shade with "Ответить" and "Отметить прочитанным"
            com.example.notifications.InAppNotificationManager.dispatchIncomingNotification(
                context = context,
                chatId = chatId,
                senderId = "simulated_sender",
                senderName = senderName,
                text = sanitized,
                isMention = isMention,
                chatTitle = customChatTitle ?: targetChat?.title ?: if (isGroup) "morpheus empire 👑" else null,
                isGroup = targetChat?.isGroup == true || isGroup,
                isChannel = isChannel,
                forceSystemNotification = forceSystemNotification
            )
        }
    }

    private val _pendingOpenStreamHostId = MutableStateFlow<String?>(null)
    val pendingOpenStreamHostId: StateFlow<String?> = _pendingOpenStreamHostId.asStateFlow()

    val backgroundAudioSession: StateFlow<LiveStreamSession?> = StreamAudioPlaybackService.currentSession
    val isBackgroundAudioPlaying: StateFlow<Boolean> = StreamAudioPlaybackService.isPlaying
    val isBackgroundAudioMuted: StateFlow<Boolean> = StreamAudioPlaybackService.isMuted

    fun setPendingOpenStreamHostId(hostId: String?) {
        _pendingOpenStreamHostId.value = hostId
    }

    fun enterPipMode(session: LiveStreamSession) {
        _pipStreamSession.value = session
    }

    fun closePipMode() {
        _pipStreamSession.value = null
    }

    fun togglePipMute() {
        _isPipMuted.value = !_isPipMuted.value
    }

    fun startBackgroundAudio(context: android.content.Context, session: LiveStreamSession) {
        StreamAudioPlaybackService.start(context, session)
    }

    fun stopBackgroundAudio(context: android.content.Context) {
        StreamAudioPlaybackService.stop(context)
    }

    fun toggleBackgroundAudioMute(context: android.content.Context) {
        StreamAudioPlaybackService.toggleMute(context)
    }

    fun switchToBackgroundAudio(context: android.content.Context) {
        val session = _pipStreamSession.value
        if (session != null) {
            _pipStreamSession.value = null
            startBackgroundAudio(context, session)
        }
    }

    fun startLiveStream(session: LiveStreamSession) {
        _activeStreams.update { current ->
            current + (session.hostUserId to session)
        }
    }

    fun stopLiveStream(hostUserId: String) {
        _activeStreams.update { current ->
            current - hostUserId
        }
        if (_pipStreamSession.value?.hostUserId == hostUserId) {
            _pipStreamSession.value = null
        }
    }

    fun isUserStreaming(userId: String): Boolean {
        return _activeStreams.value[userId]?.isLive == true
    }

    fun getActiveStream(userId: String): LiveStreamSession? {
        return _activeStreams.value[userId]
    }

    fun addStreamComment(hostUserId: String, comment: LiveComment) {
        _activeStreams.update { current ->
            val session = current[hostUserId] ?: return@update current
            val updatedComments = session.comments + comment
            val updatedStars = session.totalStarsEarned + comment.starsDonated
            current + (hostUserId to session.copy(comments = updatedComments, totalStarsEarned = updatedStars))
        }
    }

    fun updateStreamViewerCount(hostUserId: String, count: Int) {
        _activeStreams.update { current ->
            val session = current[hostUserId] ?: return@update current
            current + (hostUserId to session.copy(viewerCount = count))
        }
    }

    fun openOrCreateChat(chat: Chat) {
        viewModelScope.launch {
            repository.insertChat(chat)
        }
    }

    fun openOrCreateUserChat(user: UserAccount) {
        viewModelScope.launch {
            val title = if (user.displayName.isNotBlank()) user.displayName else user.username
            val chat = Chat(
                id = user.id,
                title = title,
                isGroup = false,
                isChannel = false,
                isBot = false,
                lastMessage = user.bio
            )
            repository.insertChat(chat)
        }
    }

    fun openOrCreateBotChat(bot: BotSearchResult) {
        viewModelScope.launch {
            val chat = Chat(
                id = bot.id,
                title = bot.name,
                isGroup = false,
                isChannel = false,
                isBot = true,
                lastMessage = bot.description
            )
            repository.insertChat(chat)
        }
    }

    fun searchDatabase(query: String, folderIndex: Int): Flow<DatabaseSearchResults> {
        val raw = query.trim()
        val clean = raw.removePrefix("@").trim()
        if (clean.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(DatabaseSearchResults())
        }

        return when (folderIndex) {
            1 -> { // Personal: search user accounts by @username or nickname
                repository.searchAccounts(clean).map { users ->
                    val activeId = activeAccount.value?.id
                    val filteredUsers = users.filter { it.id != activeId }
                    DatabaseSearchResults(users = filteredUsers)
                }
            }
            2 -> { // Groups: search public groups by title
                repository.searchGroups(clean).map { groups ->
                    DatabaseSearchResults(groups = groups.filter { !it.isBlocked })
                }
            }
            3 -> { // Channels: search public channels by title
                repository.searchChannels(clean).map { channels ->
                    DatabaseSearchResults(channels = channels.filter { !it.isBlocked })
                }
            }
            4 -> { // Bots: search bots by @username or nickname/name
                repository.searchBots(clean).map { dbBots ->
                    val registered = com.example.ui.botapi.BotRegistry.getAllBots().map { b ->
                        BotSearchResult(
                            id = b.id,
                            name = b.name,
                            username = "@${b.id}",
                            description = b.description,
                            category = b.category
                        )
                    }
                    val fromDb = dbBots.map { b ->
                        BotSearchResult(
                            id = b.id,
                            name = b.title,
                            username = "@${b.id.removePrefix("@")}",
                            description = b.lastMessage,
                            category = "Bot"
                        )
                    }
                    val allBots = (registered + fromDb).distinctBy { it.id }
                        .filter {
                            it.name.contains(clean, ignoreCase = true) ||
                            it.username.contains(clean, ignoreCase = true) ||
                            it.id.contains(clean, ignoreCase = true) ||
                            it.description.contains(clean, ignoreCase = true)
                        }
                    DatabaseSearchResults(bots = allBots)
                }
            }
            else -> { // All: search all public channels/groups by title, bots by @username or nickname, user accounts by @username or nickname
                kotlinx.coroutines.flow.combine(
                    repository.searchChannels(clean),
                    repository.searchGroups(clean),
                    repository.searchBots(clean),
                    repository.searchAccounts(clean)
                ) { channels, groups, dbBots, users ->
                    val activeId = activeAccount.value?.id
                    val filteredUsers = users.filter { it.id != activeId }
                    val registered = com.example.ui.botapi.BotRegistry.getAllBots().map { b ->
                        BotSearchResult(
                            id = b.id,
                            name = b.name,
                            username = "@${b.id}",
                            description = b.description,
                            category = b.category
                        )
                    }
                    val fromDb = dbBots.map { b ->
                        BotSearchResult(
                            id = b.id,
                            name = b.title,
                            username = "@${b.id.removePrefix("@")}",
                            description = b.lastMessage,
                            category = "Bot"
                        )
                    }
                    val allBots = (registered + fromDb).distinctBy { it.id }
                        .filter {
                            it.name.contains(clean, ignoreCase = true) ||
                            it.username.contains(clean, ignoreCase = true) ||
                            it.id.contains(clean, ignoreCase = true) ||
                            it.description.contains(clean, ignoreCase = true)
                        }
                    DatabaseSearchResults(
                        users = filteredUsers,
                        channels = channels.filter { !it.isBlocked },
                        groups = groups.filter { !it.isBlocked },
                        bots = allBots
                    )
                }
            }
        }
    }
}
