package com.example.ui
import android.content.Context
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

data class ScannedUserProfileResult(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String = "",
    val phoneNumber: String? = null,
    val isBot: Boolean = false,
    val isContact: Boolean = false,
    val isSelf: Boolean = false
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
    val forwardHideSender: Boolean = false,
    val senderIdentityId: String? = null,
    val senderIdentityType: String? = null,
    val senderDisplayName: String? = null,
    val senderAvatarUrl: String? = null,
    val senderCustomTag: String? = null,
    val senderEmojiStatus: String? = null,
    val commentCount: Int = 0,
    val mirroredGroupMessageId: String? = null
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
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val pinnedTimestamp: Long = 0L,
    val linkedDiscussionGroupId: String? = null,
    val linkedChannelId: String? = null
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
    val canSendMessages: Boolean = true,
    val role: String = "subscriber", // "creator", "admin", "subscriber", "bot"
    val joinedAt: Long = System.currentTimeMillis(),
    val promotedBy: String? = null,
    val customTitle: String = "",
    val canPostMessages: Boolean = false,
    val canEditMessages: Boolean = false,
    val canDeleteMessages: Boolean = false,
    val canManageStreams: Boolean = false,
    val canAddAdmins: Boolean = false,
    val canChangeChannelInfo: Boolean = false,
    val canPostStories: Boolean = false,
    val canEditStories: Boolean = false,
    val canDeleteStories: Boolean = false,
    val canAccessDirectMessages: Boolean = false,
    val canManageGreetings: Boolean = false,
    val canManageInvites: Boolean = false,
    val canBanUsers: Boolean = false
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
    val sandboxEngine: com.example.di.MessengerSandbox = com.example.di.Injector.get().messengerSandbox,
    val userRatingDao: com.example.ui.rating.UserRatingDao? = null
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

    val userRatingsMap: StateFlow<Map<String, com.example.ui.rating.UserRatingEntity>> =
        com.example.ui.rating.UserRatingManager.ratingsMap

    init {
        userRatingDao?.let { dao ->
            com.example.ui.rating.UserRatingManager.attachDao(dao, viewModelScope)
        }
        viewModelScope.launch {
            activeAccount.collect { acc ->
                com.example.ui.rating.UserRatingManager.setActiveOwnUserId(acc?.id)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            reconcilePersistentDiscussionLinks()
        }
    }

    private suspend fun reconcilePersistentDiscussionLinks() {
        try {
            val allChats = repository.allChats.firstOrNull().orEmpty()
            allChats.forEach { ch ->
                if (ch.isChannel) {
                    val custom = com.example.ui.channel.ChannelCustomizationManager.getCustomization(ch.id)
                    val roomGroupId = ch.linkedDiscussionGroupId?.takeIf { it.isNotBlank() }
                    val prefGroupId = custom.discussionChatId?.takeIf { it.isNotBlank() }
                    if (roomGroupId != null && prefGroupId == null) {
                        val grp = allChats.find { it.id == roomGroupId }
                        com.example.ui.channel.ChannelCustomizationManager.linkDiscussionChat(
                            chatId = ch.id,
                            discussionChatId = roomGroupId,
                            discussionTitle = grp?.title ?: custom.discussionChatTitle.ifBlank { "Чат обсуждений" }
                        )
                    } else if (roomGroupId == null && prefGroupId != null) {
                        repository.updateLinkedDiscussionGroup(ch.id, prefGroupId)
                        repository.updateLinkedChannelId(prefGroupId, ch.id)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun observeUserRating(
        userId: String,
        isOwnProfile: Boolean = false
    ): Flow<com.example.ui.rating.UserRatingState> {
        return com.example.ui.rating.UserRatingManager.observeRatingState(
            userId = userId,
            isOwnProfile = isOwnProfile
        )
    }

    fun getUserRatingState(
        userId: String,
        isOwnProfile: Boolean = false
    ): com.example.ui.rating.UserRatingState {
        return com.example.ui.rating.UserRatingManager.getRatingState(
            userId = userId,
            isOwnProfile = isOwnProfile
        )
    }

    fun recordDirectGiftSentRating(starsSpent: Int, senderId: String? = activeAccount.value?.id) {
        val targetId = senderId?.takeIf { it.isNotBlank() } ?: "me"
        com.example.ui.rating.UserRatingManager.recordDirectGiftPurchase(targetId, starsSpent)
    }

    fun recordMarketOrPaidActionRating(starsSpent: Int, userId: String? = activeAccount.value?.id) {
        val targetId = userId?.takeIf { it.isNotBlank() } ?: "me"
        com.example.ui.rating.UserRatingManager.recordMarketOrPaidAction(targetId, starsSpent)
    }

    fun recordStarsRefundRating(refundedStars: Int, userId: String? = activeAccount.value?.id) {
        val targetId = userId?.takeIf { it.isNotBlank() } ?: "me"
        com.example.ui.rating.UserRatingManager.recordStarsRefund(targetId, refundedStars)
    }

    fun recordGiftExchangeRating(giftStarsValue: Int, userId: String? = activeAccount.value?.id) {
        val targetId = userId?.takeIf { it.isNotBlank() } ?: "me"
        com.example.ui.rating.UserRatingManager.recordGiftExchangeForStars(targetId, giftStarsValue)
    }

    fun recordChannelBoostRating(userId: String? = activeAccount.value?.id) {
        val targetId = userId?.takeIf { it.isNotBlank() } ?: "me"
        com.example.ui.rating.UserRatingManager.recordBoostActivity(targetId)
    }

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

                repository.insertGroupMember(GroupMember("c1", "u1", "Sarah Connor", isAdmin = true, role = "creator"))
                repository.insertGroupMember(GroupMember("c1", "u2", "John Doe", isAdmin = false, role = "subscriber"))
                repository.insertGroupMember(GroupMember("c1", "u3", "Crypto Alpha", isAdmin = false, role = "subscriber"))
                repository.insertGroupMember(GroupMember("c1", "u4", "KuoteX Hacker", isAdmin = false, role = "subscriber"))
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

            // Ensure seeded public/other groups have explicit foreign creators so creator-only link check is accurate
            val nowTs = System.currentTimeMillis()
            val foreignGroupCreators = mapOf(
                "c1" to ("u1" to "Sarah Connor"),
                "g1" to ("u10" to "Pavel Durov"),
                "g2" to ("u12" to "Alex KuoteX"),
                "g3" to ("u12" to "Alex KuoteX"),
                "g4" to ("u10" to "Pavel Durov"),
                "g5" to ("u12" to "Alex KuoteX")
            )
            foreignGroupCreators.forEach { (gId, ownerPair) ->
                val members = repository.getGroupMembers(gId).firstOrNull().orEmpty()
                if (members.none { it.role == "creator" }) {
                    repository.insertGroupMember(
                        GroupMember(
                            chatId = gId,
                            userId = ownerPair.first,
                            userName = ownerPair.second,
                            isAdmin = true,
                            role = "creator",
                            joinedAt = nowTs - 86400_000L * 40,
                            canChangeChannelInfo = true,
                            canPostMessages = true,
                            canEditMessages = true,
                            canDeleteMessages = true,
                            canBanUsers = true,
                            canManageInvites = true
                        )
                    )
                }
            }

            // Seed channel roles so creators/admins and regular subscribers are strictly separated
            val c2Members = repository.getGroupMembers("c2").firstOrNull().orEmpty()
            if (c2Members.isEmpty()) {
                val active = repository.activeAccountFlow.firstOrNull()
                val ownerId = active?.id ?: "123456789"
                val ownerName = active?.displayName?.ifBlank { "Neo" } ?: "Neo"
                listOf(
                    GroupMember(
                        chatId = "c2",
                        userId = ownerId,
                        userName = ownerName,
                        isAdmin = true,
                        role = "creator",
                        joinedAt = nowTs - 86400_000L * 30,
                        canPostMessages = true,
                        canEditMessages = true,
                        canDeleteMessages = true,
                        canManageStreams = true,
                        canAddAdmins = true,
                        canChangeChannelInfo = true,
                        canPostStories = true,
                        canEditStories = true,
                        canDeleteStories = true,
                        canAccessDirectMessages = true,
                        canManageGreetings = true,
                        canManageInvites = true,
                        canBanUsers = true
                    ),
                    GroupMember(
                        chatId = "c2",
                        userId = "u12",
                        userName = "Alex KuoteX",
                        isAdmin = true,
                        role = "admin",
                        promotedBy = ownerName,
                        joinedAt = nowTs - 86400_000L * 20,
                        customTitle = "Редактор",
                        canPostMessages = true,
                        canEditMessages = true,
                        canDeleteMessages = true,
                        canManageStreams = true,
                        canAddAdmins = false,
                        canChangeChannelInfo = true,
                        canPostStories = true,
                        canEditStories = false,
                        canDeleteStories = false,
                        canAccessDirectMessages = true,
                        canManageGreetings = false,
                        canManageInvites = true,
                        canBanUsers = false
                    ),
                    GroupMember("c2", "shop_bot", "Shop Bot", isAdmin = false, role = "bot", canReadMessages = false, canSendMessages = false, joinedAt = nowTs - 86400_000L * 15),
                    GroupMember("c2", "u11", "Alice Hacker", isAdmin = false, role = "subscriber", joinedAt = nowTs - 86400_000L * 10),
                    GroupMember("c2", "u13", "Elena Star", isAdmin = false, role = "subscriber", joinedAt = nowTs - 86400_000L * 7),
                    GroupMember("c2", "u14", "John Satoshi", isAdmin = false, role = "subscriber", joinedAt = nowTs - 86400_000L * 3)
                ).forEach { repository.insertGroupMember(it) }
            }

            listOf("c5", "c6", "c7", "c8", "c9").forEach { chId ->
                val chMembers = repository.getGroupMembers(chId).firstOrNull().orEmpty()
                if (chMembers.isEmpty()) {
                    val active = repository.activeAccountFlow.firstOrNull()
                    val currentId = active?.id ?: "123456789"
                    val currentName = active?.displayName?.ifBlank { "Neo" } ?: "Neo"
                    val seeded = mutableListOf(
                        GroupMember(
                            chatId = chId,
                            userId = "u10",
                            userName = "Pavel Durov",
                            isAdmin = true,
                            role = "creator",
                            joinedAt = nowTs - 86400_000L * 60,
                            canPostMessages = true,
                            canEditMessages = true,
                            canDeleteMessages = true,
                            canManageStreams = true,
                            canAddAdmins = true,
                            canChangeChannelInfo = true,
                            canPostStories = true,
                            canEditStories = true,
                            canDeleteStories = true,
                            canAccessDirectMessages = true,
                            canManageGreetings = true,
                            canManageInvites = true,
                            canBanUsers = true
                        ),
                        GroupMember(chId, "u12", "Alex KuoteX", isAdmin = true, role = "admin", promotedBy = "Pavel Durov", joinedAt = nowTs - 86400_000L * 45, canPostMessages = true, canEditMessages = true),
                        GroupMember(chId, "u13", "Elena Star", isAdmin = true, role = "admin", promotedBy = "Pavel Durov", joinedAt = nowTs - 86400_000L * 30, canPostMessages = true),
                        GroupMember(chId, "shop_bot", "Shop Bot", isAdmin = false, role = "bot", canReadMessages = false, canSendMessages = false, joinedAt = nowTs - 86400_000L * 25),
                        GroupMember(chId, "u11", "Alice Hacker", isAdmin = false, role = "subscriber", joinedAt = nowTs - 86400_000L * 14),
                        GroupMember(chId, "u14", "John Satoshi", isAdmin = false, role = "subscriber", joinedAt = nowTs - 86400_000L * 8)
                    )
                    if (currentId != "u10" && currentId != "u12" && currentId != "u13") {
                        seeded.add(
                            GroupMember(chId, currentId, currentName, isAdmin = false, role = "subscriber", joinedAt = nowTs - 86400_000L * 5)
                        )
                    }
                    seeded.forEach { repository.insertGroupMember(it) }
                }
            }

            // Seed default contacts so "Добавить участников" works immediately
            val existingContacts = repository.allContacts.firstOrNull().orEmpty()
            if (existingContacts.isEmpty()) {
                listOf(
                    Contact("u11", "Alice Hacker", "+15550112233", true),
                    Contact("u12", "Alex KuoteX", "+15550123456", true),
                    Contact("u13", "Elena Star", "+15550134567", true),
                    Contact("u14", "John Satoshi", "+15550145678", true),
                    Contact("u15", "Morpheus", "+15550156789", true),
                    Contact("u16", "Trinity Hacker", "+15550167890", true)
                ).forEach { repository.insertContact(it) }
            }

            // Seed realistic user group-invite privacy policies for contacts/users
            val existingPrivacy = repository.getAllPrivacyRules().firstOrNull().orEmpty()
            if (existingPrivacy.isEmpty()) {
                listOf(
                    com.example.ui.group.UserPrivacyRuleEntity("123456789", com.example.ui.group.GroupInvitePrivacy.EVERYBODY.code),
                    com.example.ui.group.UserPrivacyRuleEntity("987654321", com.example.ui.group.GroupInvitePrivacy.EVERYBODY.code),
                    com.example.ui.group.UserPrivacyRuleEntity("456789123", com.example.ui.group.GroupInvitePrivacy.MY_CONTACTS.code),
                    com.example.ui.group.UserPrivacyRuleEntity("u10", com.example.ui.group.GroupInvitePrivacy.NOBODY.code),
                    com.example.ui.group.UserPrivacyRuleEntity("u11", com.example.ui.group.GroupInvitePrivacy.NOBODY.code),
                    com.example.ui.group.UserPrivacyRuleEntity("u12", com.example.ui.group.GroupInvitePrivacy.EVERYBODY.code),
                    com.example.ui.group.UserPrivacyRuleEntity("u13", com.example.ui.group.GroupInvitePrivacy.MY_CONTACTS.code),
                    com.example.ui.group.UserPrivacyRuleEntity("u14", com.example.ui.group.GroupInvitePrivacy.NOBODY.code),
                    com.example.ui.group.UserPrivacyRuleEntity("u15", com.example.ui.group.GroupInvitePrivacy.EVERYBODY.code),
                    com.example.ui.group.UserPrivacyRuleEntity("u16", com.example.ui.group.GroupInvitePrivacy.EVERYBODY.code)
                ).forEach { repository.upsertPrivacyRule(it) }
            }

            // Ensure every group has a primary invite link persisted in Room
            val allSeededChats = repository.allChats.firstOrNull().orEmpty()
            allSeededChats.filter { it.isGroup && !it.isChannel }.forEach { grp ->
                val links = repository.getInviteLinksForChatSync(grp.id)
                if (links.isEmpty()) {
                    val generatedUrl = com.example.ui.group.generateTelegramStyleInviteUrl(grp.id)
                    repository.insertInviteLink(
                        com.example.ui.group.GroupInviteLinkEntity(
                            id = "primary_${grp.id}",
                            chatId = grp.id,
                            url = generatedUrl,
                            label = "Основная ссылка",
                            createdByUserId = "system",
                            createdByName = "Владелец",
                            isPrimary = true
                        )
                    )
                    com.example.ui.channel.ChannelCustomizationManager.updatePrimaryInviteLink(grp.id, generatedUrl)
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

    fun syncDeviceContactsFromPhone(context: Context, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceList = com.example.utils.CountryPhoneHelper.fetchDeviceContacts(context)
            var addedCount = 0
            val existing = contacts.value
            deviceList.forEach { devContact ->
                val cleanDigits = devContact.phoneNumber.filter { it.isDigit() }
                val alreadyExists = existing.any { c ->
                    (c.phoneNumber != null && c.phoneNumber.filter { it.isDigit() } == cleanDigits) ||
                    c.name.equals(devContact.name, ignoreCase = true)
                }
                if (!alreadyExists && devContact.name.isNotBlank()) {
                    val newContact = Contact(
                        id = java.util.UUID.randomUUID().toString(),
                        name = devContact.name,
                        phoneNumber = devContact.phoneNumber,
                        isRegistered = true
                    )
                    repository.insertContact(newContact)
                    addedCount++
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete?.invoke(addedCount)
            }
        }
    }

    fun addContact(
        firstName: String,
        lastName: String = "",
        phoneNumber: String,
        notes: String = "",
        syncWithDevice: Boolean = false,
        context: Context? = null,
        onComplete: ((Contact) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val contactId = java.util.UUID.randomUUID().toString()
            val fullName = listOf(firstName.trim(), lastName.trim()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { phoneNumber.trim() }
            val cleanPhone = phoneNumber.trim()

            if (syncWithDevice && context != null) {
                com.example.utils.CountryPhoneHelper.saveContactToDevice(
                    context = context,
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = cleanPhone,
                    notes = notes
                )
            }

            val newContact = Contact(
                id = contactId,
                name = fullName,
                phoneNumber = cleanPhone,
                isRegistered = true
            )
            repository.insertContact(newContact)
            onComplete?.invoke(newContact)
        }
    }

    fun addContact(name: String, phoneNumberOrUsername: String, onComplete: ((Contact) -> Unit)? = null) {
        viewModelScope.launch {
            val contactId = java.util.UUID.randomUUID().toString()
            val cleanValue = phoneNumberOrUsername.trim()
            val newContact = Contact(
                id = contactId,
                name = name.ifBlank { cleanValue },
                phoneNumber = cleanValue,
                isRegistered = true
            )
            repository.insertContact(newContact)
            onComplete?.invoke(newContact)
        }
    }

    suspend fun findUserOrContactByQr(rawCode: String): ScannedUserProfileResult? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val trimmed = rawCode.trim()
        if (trimmed.isBlank()) return@withContext null

        val target = when {
            trimmed.startsWith("tg://resolve?domain=") -> trimmed.substringAfter("domain=").substringBefore("&").removePrefix("@")
            trimmed.startsWith("https://t.me/") || trimmed.startsWith("http://t.me/") -> trimmed.substringAfter("t.me/").substringBefore("/").substringBefore("?").removePrefix("@")
            trimmed.startsWith("https://kuotex.me/") || trimmed.startsWith("http://kuotex.me/") -> trimmed.substringAfter("kuotex.me/").substringBefore("/").substringBefore("?").removePrefix("@")
            trimmed.startsWith("kuotex://user/") -> trimmed.substringAfter("user/").substringBefore("/").substringBefore("?").removePrefix("@")
            trimmed.startsWith("tel:") -> trimmed.removePrefix("tel:").trim()
            trimmed.startsWith("@") -> trimmed.removePrefix("@").trim()
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> return@withContext null
            else -> trimmed.trim()
        }

        if (target.isBlank() || target.contains("/") || target.contains(":") || target.contains("?")) {
            return@withContext null
        }

        val cleanDigits = target.filter { it.isDigit() }
        val myId = activeAccount.value?.id
        val myUsername = activeAccount.value?.username?.removePrefix("@")

        if (myUsername != null && target.equals(myUsername, ignoreCase = true)) {
            val myAcc = activeAccount.value!!
            return@withContext ScannedUserProfileResult(
                id = myAcc.id,
                username = myAcc.username.removePrefix("@"),
                displayName = myAcc.displayName,
                avatarUrl = myAcc.profilePicUrl,
                bio = myAcc.bio,
                phoneNumber = myAcc.phoneNumber.takeIf { it.isNotBlank() },
                isBot = false,
                isContact = false,
                isSelf = true
            )
        }

        // 1. Search Room accounts
        val matchedAccount = try {
            repository.searchAccountsSync(target).firstOrNull { acc ->
                acc.username.removePrefix("@").equals(target, ignoreCase = true) ||
                acc.displayName.equals(target, ignoreCase = true) ||
                acc.id == target ||
                (cleanDigits.length >= 7 && acc.phoneNumber.filter { it.isDigit() }.endsWith(cleanDigits.takeLast(7)))
            }
        } catch (_: Exception) {
            null
        }

        if (matchedAccount != null) {
            val isAlreadyContact = contacts.value.any { c ->
                c.name.equals(matchedAccount.displayName, ignoreCase = true) ||
                (matchedAccount.phoneNumber.isNotBlank() && c.phoneNumber == matchedAccount.phoneNumber)
            }
            return@withContext ScannedUserProfileResult(
                id = matchedAccount.id,
                username = matchedAccount.username.removePrefix("@"),
                displayName = matchedAccount.displayName,
                avatarUrl = matchedAccount.profilePicUrl,
                bio = matchedAccount.bio,
                phoneNumber = matchedAccount.phoneNumber.takeIf { it.isNotBlank() },
                isBot = false,
                isContact = isAlreadyContact,
                isSelf = matchedAccount.id == myId
            )
        }

        // 2. Search local contacts
        val matchedContact = contacts.value.firstOrNull { c ->
            c.name.equals(target, ignoreCase = true) ||
            c.id == target ||
            (c.phoneNumber != null && cleanDigits.length >= 7 && c.phoneNumber.filter { it.isDigit() }.endsWith(cleanDigits.takeLast(7)))
        }

        if (matchedContact != null) {
            return@withContext ScannedUserProfileResult(
                id = matchedContact.id,
                username = target,
                displayName = matchedContact.name,
                avatarUrl = null,
                bio = "",
                phoneNumber = matchedContact.phoneNumber,
                isBot = false,
                isContact = true
            )
        }

        // 3. Search bots
        val bot = com.example.ui.botapi.BotRegistry.getAllBots().firstOrNull { b ->
            b.id.equals(target, ignoreCase = true) ||
            b.name.equals(target, ignoreCase = true)
        }
        if (bot != null) {
            return@withContext ScannedUserProfileResult(
                id = bot.id,
                username = bot.id,
                displayName = bot.name,
                avatarUrl = null,
                bio = bot.description,
                phoneNumber = null,
                isBot = true,
                isContact = false
            )
        }

        return@withContext null
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
    fun updateProfile(
        id: String, 
        username: String, 
        displayName: String, 
        bio: String, 
        profilePicUrl: String, 
        customStatus: String? = null, 
        phoneNumber: String? = null, 
        dateOfBirth: String? = null, 
        socialMedia: String? = null
    ) {
        val current = activeAccount.value
        val finalStatus = customStatus ?: current?.customStatus ?: ""
        val finalPhone = phoneNumber ?: current?.phoneNumber ?: ""
        val finalDob = dateOfBirth ?: current?.dateOfBirth ?: ""
        val finalSocial = socialMedia ?: current?.socialMedia ?: ""
        viewModelScope.launch {
            repository.updateProfile(id, username, displayName, bio, profilePicUrl, finalStatus, finalPhone, finalDob, finalSocial)
        }
    }
    
    fun addGroupMember(chatId: String, userId: String, userName: String, isAdmin: Boolean, role: String = if (isAdmin) "admin" else "subscriber") {
        viewModelScope.launch {
            repository.insertGroupMember(
                com.example.ui.GroupMember(
                    chatId = chatId,
                    userId = userId,
                    userName = userName,
                    isAdmin = isAdmin,
                    role = role,
                    joinedAt = System.currentTimeMillis(),
                    canPostMessages = isAdmin,
                    canEditMessages = isAdmin
                )
            )
            if (!isAdmin && role == "subscriber") {
                com.example.ui.channel.ChannelCustomizationManager.consumeGreetingMessagesForNewMember(chatId, userId)
            }
        }
    }

    fun subscribeToChannel(chatId: String) {
        val account = activeAccount.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getGroupMemberSync(chatId, account.id)
            if (existing == null) {
                repository.insertGroupMember(
                    com.example.ui.GroupMember(
                        chatId = chatId,
                        userId = account.id,
                        userName = account.displayName.ifBlank { account.username },
                        isAdmin = false,
                        role = "subscriber",
                        joinedAt = System.currentTimeMillis(),
                        canPostMessages = false,
                        canEditMessages = false
                    )
                )
            }
            com.example.ui.channel.ChannelCustomizationManager.consumeGreetingMessagesForNewMember(chatId, account.id)
        }
    }

    fun promoteOrUpdateChannelAdmin(
        chatId: String,
        userId: String,
        userName: String,
        customTitle: String = "",
        canPost: Boolean,
        canEdit: Boolean,
        canDelete: Boolean,
        canManageStreams: Boolean,
        canAddAdmins: Boolean,
        canChangeChannelInfo: Boolean = false,
        canPostStories: Boolean = false,
        canEditStories: Boolean = false,
        canDeleteStories: Boolean = false,
        canAccessDirectMessages: Boolean = false,
        canManageGreetings: Boolean = false,
        canManageInvites: Boolean = false,
        canBanUsers: Boolean = false,
        promotedByName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getGroupMemberSync(chatId, userId)
            val newRole = if (existing?.role == "creator") "creator" else "admin"
            val updated = (existing ?: GroupMember(chatId = chatId, userId = userId, userName = userName)).copy(
                userName = userName,
                isAdmin = true,
                role = newRole,
                promotedBy = existing?.promotedBy ?: promotedByName,
                customTitle = customTitle,
                canPostMessages = canPost,
                canEditMessages = canEdit,
                canDeleteMessages = canDelete,
                canManageStreams = canManageStreams,
                canAddAdmins = canAddAdmins,
                canChangeChannelInfo = canChangeChannelInfo,
                canPostStories = canPostStories,
                canEditStories = canEditStories,
                canDeleteStories = canDeleteStories,
                canAccessDirectMessages = canAccessDirectMessages,
                canManageGreetings = canManageGreetings,
                canManageInvites = canManageInvites,
                canBanUsers = canBanUsers
            )
            repository.insertGroupMember(updated)
        }
    }

    fun transferChannelOwnership(
        chatId: String,
        currentOwnerId: String,
        newOwnerId: String,
        newOwnerName: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentOwner = repository.getGroupMemberSync(chatId, currentOwnerId)
            val newOwner = repository.getGroupMemberSync(chatId, newOwnerId)
                ?: GroupMember(chatId = chatId, userId = newOwnerId, userName = newOwnerName)
            if (currentOwner != null && currentOwner.userId != newOwnerId) {
                repository.insertGroupMember(
                    currentOwner.copy(
                        isAdmin = true,
                        role = "admin",
                        promotedBy = newOwnerName
                    )
                )
            }
            repository.insertGroupMember(
                newOwner.copy(
                    userName = newOwnerName,
                    isAdmin = true,
                    role = "creator",
                    promotedBy = null,
                    canPostMessages = true,
                    canEditMessages = true,
                    canDeleteMessages = true,
                    canManageStreams = true,
                    canAddAdmins = true,
                    canChangeChannelInfo = true,
                    canPostStories = true,
                    canEditStories = true,
                    canDeleteStories = true,
                    canAccessDirectMessages = true,
                    canManageGreetings = true,
                    canManageInvites = true,
                    canBanUsers = true
                )
            )
        }
    }

    fun demoteChannelAdmin(chatId: String, userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getGroupMemberSync(chatId, userId) ?: return@launch
            if (existing.role == "creator") return@launch
            repository.insertGroupMember(
                existing.copy(
                    isAdmin = false,
                    role = "subscriber",
                    customTitle = "",
                    promotedBy = null,
                    canPostMessages = false,
                    canEditMessages = false,
                    canDeleteMessages = false,
                    canManageStreams = false,
                    canAddAdmins = false,
                    canChangeChannelInfo = false,
                    canPostStories = false,
                    canEditStories = false,
                    canDeleteStories = false,
                    canAccessDirectMessages = false,
                    canManageGreetings = false,
                    canManageInvites = false,
                    canBanUsers = false
                )
            )
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

    fun getCreatorOwnedGroupIds(userId: String): Flow<List<String>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return repository.getCreatorChatIds(userId)
    }

    fun linkDiscussionGroupWithVerification(
        channelId: String,
        groupId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
            val channelChat = repository.getChatById(channelId)
            val channelMembers = repository.getGroupMembers(channelId).firstOrNull().orEmpty()
            val targetGroupChat = repository.getChatById(groupId)
            val targetGroupMembers = repository.getGroupMembers(groupId).firstOrNull().orEmpty()

            val validation = com.example.ui.channel.validateDiscussionGroupLink(
                channelChat = channelChat,
                channelMembers = channelMembers,
                targetGroupChat = targetGroupChat,
                targetGroupMembers = targetGroupMembers,
                activeAccount = active
            )

            if (!validation.allowed || targetGroupChat == null) {
                val errorMsg = validation.errorMessage ?: "Не удалось привязать группу"
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(false, errorMsg)
                }
                return@launch
            }

            val previousGroupId = channelChat?.linkedDiscussionGroupId
                ?: com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId).discussionChatId
            if (!previousGroupId.isNullOrBlank() && previousGroupId != targetGroupChat.id) {
                repository.updateLinkedChannelId(previousGroupId, null)
            }
            repository.updateLinkedDiscussionGroup(channelId, targetGroupChat.id)
            repository.updateLinkedChannelId(targetGroupChat.id, channelId)
            com.example.ui.channel.ChannelCustomizationManager.linkDiscussionChat(
                chatId = channelId,
                discussionChatId = targetGroupChat.id,
                discussionTitle = targetGroupChat.title
            )
            val customAfterLink = com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId)
            if (channelChat != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    com.example.ui.channel.ChannelDiscussionFirestoreSync.syncChannelDiscussionLink(
                        channelChat = channelChat,
                        groupChat = targetGroupChat,
                        discussionTitle = targetGroupChat.title,
                        onlyMembersCanSend = customAfterLink.discussionOnlyMembersCanSend,
                        joinRequestsRequired = customAfterLink.discussionJoinRequestsRequired
                    )
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(true, "Привязана группа «${targetGroupChat.title}»")
            }
        }
    }

    fun createAndLinkDiscussionGroup(
        channelId: String,
        groupName: String,
        isPrivate: Boolean = true,
        photo: String = "",
        onResult: (Boolean, Chat?, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
            val channelChat = repository.getChatById(channelId)
            val channelMembers = repository.getGroupMembers(channelId).firstOrNull().orEmpty()
            val channelMember = com.example.ui.channel.findCurrentChannelMember(channelMembers, active)
            val canManageChannel = channelChat != null && channelChat.isChannel && channelMember != null && (
                channelMember.role == "creator" ||
                    com.example.ui.channel.hasFullChannelAdminRights(channelMember) ||
                    channelMember.canChangeChannelInfo
                )
            if (active == null || !canManageChannel) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(false, null, "Нет прав администратора канала для создания группы обсуждения")
                }
                return@launch
            }

            val trimmedName = groupName.trim().ifBlank { "${channelChat?.title ?: "Канал"} CHAT" }
            val newGroupId = java.util.UUID.randomUUID().toString()
            val newGroupChat = Chat(
                id = newGroupId,
                title = trimmedName,
                isGroup = true,
                isSecret = false,
                isChannel = false,
                lastMessage = "",
                linkedChannelId = channelId
            )
            repository.insertChat(newGroupChat)

            val ownerName = active.displayName.ifBlank { active.username.ifBlank { "Вы" } }
            repository.insertGroupMember(
                GroupMember(
                    chatId = newGroupId,
                    userId = active.id,
                    userName = ownerName,
                    isAdmin = true,
                    role = "creator",
                    joinedAt = System.currentTimeMillis(),
                    canPostMessages = true,
                    canEditMessages = true,
                    canDeleteMessages = true,
                    canManageStreams = true,
                    canAddAdmins = true,
                    canChangeChannelInfo = true,
                    canPostStories = true,
                    canEditStories = true,
                    canDeleteStories = true,
                    canAccessDirectMessages = true,
                    canManageGreetings = true,
                    canManageInvites = true,
                    canBanUsers = true
                )
            )
            val finalLink = com.example.ui.group.generateTelegramStyleInviteUrl(newGroupId)
            com.example.ui.channel.ChannelCustomizationManager.initializeCreatedChannel(
                chatId = newGroupId,
                description = "Чат обсуждений канала «${channelChat?.title ?: "Канал"}»",
                isPublic = !isPrivate,
                inviteLink = finalLink,
                avatarUrl = photo.takeIf { it.isNotBlank() }
            )
            repository.insertInviteLink(
                com.example.ui.group.GroupInviteLinkEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = newGroupId,
                    url = finalLink,
                    label = "Основная ссылка",
                    createdByUserId = active.id,
                    createdByName = ownerName,
                    isPrimary = true
                )
            )
            val previousGroupId = channelChat?.linkedDiscussionGroupId
                ?: com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId).discussionChatId
            if (!previousGroupId.isNullOrBlank() && previousGroupId != newGroupId) {
                repository.updateLinkedChannelId(previousGroupId, null)
            }
            repository.updateLinkedDiscussionGroup(channelId, newGroupId)
            repository.updateLinkedChannelId(newGroupId, channelId)
            com.example.ui.channel.ChannelCustomizationManager.linkDiscussionChat(
                chatId = channelId,
                discussionChatId = newGroupId,
                discussionTitle = trimmedName
            )
            val customAfterLink = com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId)
            if (channelChat != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    com.example.ui.channel.ChannelDiscussionFirestoreSync.syncChannelDiscussionLink(
                        channelChat = channelChat,
                        groupChat = newGroupChat,
                        discussionTitle = trimmedName,
                        onlyMembersCanSend = customAfterLink.discussionOnlyMembersCanSend,
                        joinRequestsRequired = customAfterLink.discussionJoinRequestsRequired
                    )
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(true, newGroupChat, "Группа «$trimmedName» создана и привязана")
            }
        }
    }

    fun unlinkDiscussionGroup(channelId: String, onDone: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val channelChat = repository.getChatById(channelId)
            val previousGroupId = channelChat?.linkedDiscussionGroupId
                ?: com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId).discussionChatId
            repository.updateLinkedDiscussionGroup(channelId, null)
            if (!previousGroupId.isNullOrBlank()) {
                repository.updateLinkedChannelId(previousGroupId, null)
            }
            com.example.ui.channel.ChannelCustomizationManager.unlinkDiscussionChat(channelId)
            com.example.ui.channel.ChannelDiscussionFirestoreSync.unlinkChannelDiscussionLink(
                channelId = channelId,
                previousGroupId = previousGroupId
            )
            if (onDone != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    fun updateChannelDiscussionSettings(
        channelId: String,
        onlyMembersCanSend: Boolean,
        joinRequestsRequired: Boolean
    ) {
        com.example.ui.channel.ChannelCustomizationManager.updateDiscussionSettings(
            chatId = channelId,
            onlyMembersCanSend = onlyMembersCanSend,
            joinRequestsRequired = joinRequestsRequired
        )
        viewModelScope.launch(Dispatchers.IO) {
            val channelChat = repository.getChatById(channelId) ?: return@launch
            val groupId = channelChat.linkedDiscussionGroupId
                ?: com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId).discussionChatId
            val groupChat = groupId?.let { repository.getChatById(it) }
            val title = groupChat?.title
                ?: com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId).discussionChatTitle
            if (groupChat != null) {
                com.example.ui.channel.ChannelDiscussionFirestoreSync.syncChannelDiscussionLink(
                    channelChat = channelChat,
                    groupChat = groupChat,
                    discussionTitle = title,
                    onlyMembersCanSend = onlyMembersCanSend,
                    joinRequestsRequired = joinRequestsRequired
                )
            }
        }
    }

    fun updateChatTitle(chatId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateChatTitle(chatId, newTitle.trim())
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
        mediaType: String? = null,
        sendAsIdentity: com.example.ui.group.SendAsIdentity? = null
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

            val activeAcc = activeAccount.value
            val currentGroupMember = if (targetChat?.isGroup == true) {
                repository.getGroupMemberSync(chatId, senderId)
            } else null
            val resolvedTag = sendAsIdentity?.customTag
                ?: currentGroupMember?.customTitle?.takeIf { it.isNotBlank() }
                ?: if (currentGroupMember?.role == "creator") "Владелец" else null
            val resolvedIdentityId = sendAsIdentity?.id ?: senderId
            val resolvedIdentityType = sendAsIdentity?.type ?: "PERSONAL"
            val resolvedDisplayName = sendAsIdentity?.title
                ?: activeAcc?.displayName?.takeIf { it.isNotBlank() }
            val resolvedAvatarUrl = sendAsIdentity?.avatarUrl
                ?: activeAcc?.profilePicUrl?.takeIf { it.isNotBlank() }
            val resolvedEmojiStatus = sendAsIdentity?.emojiStatus

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
                mediaType = mediaType,
                senderIdentityId = resolvedIdentityId,
                senderIdentityType = resolvedIdentityType,
                senderDisplayName = resolvedDisplayName,
                senderAvatarUrl = resolvedAvatarUrl,
                senderCustomTag = resolvedTag,
                senderEmojiStatus = resolvedEmojiStatus
            )
            repository.insertMessageAndUpdateChat(msg, sanitizedText, resolvedDisplayName ?: "You")
            repository.updateDraft(chatId, null)
            com.example.ui.rating.UserRatingManager.recordMessageActivity(
                senderId.ifBlank { activeAccount.value?.id ?: "me" }
            )

            // Auto-forward new channel publications to the linked discussion group
            if (targetChat?.isChannel == true) {
                val channelCustom = com.example.ui.channel.ChannelCustomizationManager
                    .getCustomization(chatId)
                val linkedGroupId = targetChat.linkedDiscussionGroupId?.takeIf { it.isNotBlank() }
                    ?: channelCustom.discussionChatId
                if (!linkedGroupId.isNullOrBlank() && linkedGroupId != chatId) {
                    val linkedGroup = repository.getChatById(linkedGroupId)
                    if (linkedGroup != null && linkedGroup.isGroup && !linkedGroup.isChannel) {
                        val fwdMsgId = java.util.UUID.randomUUID().toString()
                        val fwdMsg = Message(
                            id = fwdMsgId,
                            chatId = linkedGroupId,
                            senderId = senderId,
                            text = encryptedMsg,
                            audioPath = audioPath,
                            mediaPath = mediaPath,
                            mediaType = mediaType,
                            documentData = documentData,
                            timestamp = System.currentTimeMillis() + 5L,
                            isDelivered = isOnline,
                            isPinned = true,
                            isForwarded = false,
                            forwardOriginalSenderId = null,
                            forwardOriginalSenderName = null,
                            forwardHideSender = false,
                            senderIdentityId = chatId,
                            senderIdentityType = "CHANNEL",
                            senderDisplayName = targetChat.title,
                            senderAvatarUrl = channelCustom.avatarUrl,
                            senderCustomTag = "канал",
                            senderEmojiStatus = channelCustom.emojiStatus
                        )
                        repository.insertMessageAndUpdateChat(fwdMsg, sanitizedText, targetChat.title)
                        repository.updatePinStatus(fwdMsgId, true)
                        repository.updatePinnedMessage(linkedGroupId, fwdMsgId)
                        repository.updateMirroredGroupMessageId(messageId, fwdMsgId)
                        viewModelScope.launch(Dispatchers.IO) {
                            com.example.ui.channel.ChannelDiscussionFirestoreSync.syncMirroredGroupMessage(
                                groupId = linkedGroupId,
                                message = fwdMsg,
                                plainText = sanitizedText
                            )
                        }
                    }
                }
            }
            
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
            repository.updatePinnedMessage(chatId, messageId)
        }
    }
    fun unpinMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            repository.updatePinStatus(messageId, false)
            val remainingPinned = repository.getMessages(chatId).firstOrNull()
                ?.filter { it.id != messageId && it.isPinned }
                ?.maxByOrNull { it.timestamp }
            repository.updatePinnedMessage(chatId, remainingPinned?.id)
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
    fun createChat(
        name: String,
        desc: String,
        photo: String,
        isPrivate: Boolean,
        linkOrUsername: String,
        isGroup: Boolean = false,
        isChannel: Boolean = false,
        onCreated: ((Chat) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val newChatId = java.util.UUID.randomUUID().toString()
            val chat = Chat(id = newChatId, title = name, isGroup = isGroup, isSecret = false, isChannel = isChannel, lastMessage = "")
            repository.insertChat(chat)

            if (isChannel || isGroup) {
                val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
                val ownerId = active?.id ?: "123456789"
                val ownerName = active?.displayName?.ifBlank { "Вы" } ?: "Вы"
                repository.insertGroupMember(
                    GroupMember(
                        chatId = newChatId,
                        userId = ownerId,
                        userName = ownerName,
                        isAdmin = true,
                        role = "creator",
                        joinedAt = System.currentTimeMillis(),
                        canPostMessages = true,
                        canEditMessages = true,
                        canDeleteMessages = true,
                        canManageStreams = true,
                        canAddAdmins = true,
                        canChangeChannelInfo = true,
                        canPostStories = true,
                        canEditStories = true,
                        canDeleteStories = true,
                        canAccessDirectMessages = true,
                        canManageGreetings = true,
                        canManageInvites = true,
                        canBanUsers = true
                    )
                )
                val normalizedHandle = linkOrUsername.trim()
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("t.me/")
                    .removePrefix("@")
                val finalLink = if (normalizedHandle.isNotBlank()) {
                    "t.me/$normalizedHandle"
                } else {
                    com.example.ui.group.generateTelegramStyleInviteUrl(newChatId)
                }
                val defaultDesc = if (isChannel) "Добро пожаловать в канал «$name»." else "Добро пожаловать в группу «$name»."
                com.example.ui.channel.ChannelCustomizationManager.initializeCreatedChannel(
                    chatId = newChatId,
                    description = desc.ifBlank { defaultDesc },
                    isPublic = !isPrivate,
                    inviteLink = finalLink,
                    avatarUrl = photo.takeIf { it.isNotBlank() }
                )
                if (isGroup && !isChannel) {
                    repository.insertInviteLink(
                        com.example.ui.group.GroupInviteLinkEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            chatId = newChatId,
                            url = finalLink,
                            label = "Основная ссылка",
                            createdByUserId = ownerId,
                            createdByName = ownerName,
                            isPrimary = true
                        )
                    )
                }
            }
            if (onCreated != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onCreated(chat)
                }
            }
        }
    }

    // --- Group Management: Invite Links, Join Requests, Privacy Rules & Send As ---

    fun observeGroupInviteLinks(chatId: String): Flow<List<com.example.ui.group.GroupInviteLinkEntity>> {
        return repository.getInviteLinksForChat(chatId)
    }

    fun observePendingJoinRequests(chatId: String): Flow<List<com.example.ui.group.GroupJoinRequestEntity>> {
        return repository.getPendingJoinRequests(chatId)
    }

    fun observeAllPrivacyRules(): Flow<Map<String, com.example.ui.group.GroupInvitePrivacy>> {
        return repository.getAllPrivacyRules().map { list ->
            list.associate { it.userId to com.example.ui.group.GroupInvitePrivacy.fromCode(it.groupInvitePrivacy) }
        }
    }

    fun observeActiveUserGroupInvitePrivacy(): Flow<com.example.ui.group.GroupInvitePrivacy> {
        return kotlinx.coroutines.flow.combine(activeAccount, observeAllPrivacyRules()) { acc, map ->
            val uid = acc?.id ?: return@combine com.example.ui.group.GroupInvitePrivacy.EVERYBODY
            map[uid] ?: com.example.ui.group.GroupInvitePrivacy.EVERYBODY
        }
    }

    fun updateActiveUserGroupInvitePrivacy(policy: com.example.ui.group.GroupInvitePrivacy) {
        val uid = activeAccount.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.upsertPrivacyRule(
                com.example.ui.group.UserPrivacyRuleEntity(
                    userId = uid,
                    groupInvitePrivacy = policy.code,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun ensurePrimaryInviteLink(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val links = repository.getInviteLinksForChatSync(chatId)
            val activePrimary = links.firstOrNull { it.isPrimary && it.isActive() }
            if (activePrimary == null) {
                val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
                val existingCustom = com.example.ui.channel.ChannelCustomizationManager.getCustomization(chatId).inviteLink
                val url = if (existingCustom.contains("+")) {
                    com.example.ui.group.normalizeInvitePath(existingCustom)
                } else {
                    com.example.ui.group.generateTelegramStyleInviteUrl(chatId)
                }
                val newLink = com.example.ui.group.GroupInviteLinkEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = chatId,
                    url = url,
                    label = "Основная ссылка",
                    createdByUserId = active?.id ?: "system",
                    createdByName = active?.displayName?.ifBlank { "Владелец" } ?: "Владелец",
                    isPrimary = true
                )
                repository.insertInviteLink(newLink)
                com.example.ui.channel.ChannelCustomizationManager.updatePrimaryInviteLink(chatId, url)
            }
        }
    }

    fun createCustomInviteLink(
        chatId: String,
        label: String,
        expiresAt: Long?,
        maxUses: Int?,
        requiresApproval: Boolean,
        onCreated: ((com.example.ui.group.GroupInviteLinkEntity) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
            val url = com.example.ui.group.generateTelegramStyleInviteUrl()
            val entity = com.example.ui.group.GroupInviteLinkEntity(
                id = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                url = url,
                label = label.trim(),
                createdByUserId = active?.id ?: "system",
                createdByName = active?.displayName?.ifBlank { "Администратор" } ?: "Администратор",
                createdAt = System.currentTimeMillis(),
                expiresAt = expiresAt,
                maxUses = if (requiresApproval) null else maxUses,
                usageCount = 0,
                requiresApproval = requiresApproval,
                isPrimary = false,
                isRevoked = false
            )
            repository.insertInviteLink(entity)
            com.example.ui.channel.ChannelCustomizationManager.recordAdminLog(
                chatId = chatId,
                title = "Создана пригласительная ссылка",
                details = "${entity.label.ifBlank { entity.url }} (${if (requiresApproval) "по заявкам" else "свободный вход"})"
            )
            if (onCreated != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onCreated(entity) }
            }
        }
    }

    fun resetPrimaryInviteLink(
        chatId: String,
        onReset: ((com.example.ui.group.GroupInviteLinkEntity) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
            repository.revokePrimaryLinks(chatId)
            val newUrl = com.example.ui.group.generateTelegramStyleInviteUrl()
            val newPrimary = com.example.ui.group.GroupInviteLinkEntity(
                id = java.util.UUID.randomUUID().toString(),
                chatId = chatId,
                url = newUrl,
                label = "Основная ссылка",
                createdByUserId = active?.id ?: "system",
                createdByName = active?.displayName?.ifBlank { "Владелец" } ?: "Владелец",
                createdAt = System.currentTimeMillis(),
                isPrimary = true,
                isRevoked = false
            )
            repository.insertInviteLink(newPrimary)
            com.example.ui.channel.ChannelCustomizationManager.updatePrimaryInviteLink(chatId, newUrl)
            com.example.ui.channel.ChannelCustomizationManager.recordAdminLog(
                chatId = chatId,
                title = "Сброшена основная ссылка",
                details = "Новая ссылка: $newUrl"
            )
            if (onReset != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onReset(newPrimary) }
            }
        }
    }

    fun revokeGroupInviteLink(chatId: String, linkId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.revokeInviteLink(linkId)
            com.example.ui.channel.ChannelCustomizationManager.recordAdminLog(
                chatId = chatId,
                title = "Ссылка помечена неактивной",
                details = "Пригласительная ссылка отозвана"
            )
        }
    }

    fun observeInvitableUsers(chatId: String): Flow<List<com.example.ui.group.InvitableUser>> {
        return kotlinx.coroutines.flow.combine(
            contacts,
            accounts,
            repository.getGroupMembers(chatId),
            activeAccount
        ) { contactList, accountList, currentMembers, active ->
            val existingIds = currentMembers.map { it.userId }.toSet()
            val activeId = active?.id
            val map = linkedMapOf<String, com.example.ui.group.InvitableUser>()

            contactList.forEach { contact ->
                if (!existingIds.contains(contact.id) && contact.id != activeId) {
                    val acc = accountList.find { it.id == contact.id }
                    map[contact.id] = com.example.ui.group.InvitableUser(
                        userId = contact.id,
                        displayName = contact.name,
                        username = acc?.username ?: "",
                        avatarUrl = acc?.profilePicUrl?.takeIf { it.isNotBlank() },
                        statusText = "был(а) недавно",
                        isContact = true
                    )
                }
            }
            accountList.forEach { acc ->
                if (!existingIds.contains(acc.id) && acc.id != activeId && !map.containsKey(acc.id)) {
                    map[acc.id] = com.example.ui.group.InvitableUser(
                        userId = acc.id,
                        displayName = acc.displayName.ifBlank { acc.username },
                        username = acc.username,
                        avatarUrl = acc.profilePicUrl.takeIf { it.isNotBlank() },
                        statusText = "был(а) недавно",
                        isContact = false
                    )
                }
            }
            map.values.toList()
        }
    }

    fun attemptAddGroupMembers(
        chatId: String,
        candidates: List<com.example.ui.group.InvitableUser>,
        onResult: (com.example.ui.group.GroupAddMembersOutcome) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val inviterId = activeAccount.value?.id ?: "123456789"
            val rules = repository.getAllPrivacyRules().firstOrNull().orEmpty()
                .associate { it.userId to com.example.ui.group.GroupInvitePrivacy.fromCode(it.groupInvitePrivacy) }

            val added = mutableListOf<com.example.ui.group.InvitableUser>()
            val blocked = mutableListOf<com.example.ui.group.InvitableUser>()

            candidates.forEach { candidate ->
                val policy = rules[candidate.userId] ?: com.example.ui.group.GroupInvitePrivacy.EVERYBODY
                val canDirectAdd = com.example.ui.group.canAddUserDirectlyToGroup(
                    targetUserId = candidate.userId,
                    targetPrivacy = policy,
                    inviterUserId = inviterId,
                    inviterIsInTargetContacts = candidate.isContact
                )
                if (canDirectAdd) {
                    repository.insertGroupMember(
                        GroupMember(
                            chatId = chatId,
                            userId = candidate.userId,
                            userName = candidate.displayName,
                            isAdmin = false,
                            role = "subscriber",
                            joinedAt = System.currentTimeMillis()
                        )
                    )
                    com.example.ui.channel.ChannelCustomizationManager.consumeGreetingMessagesForNewMember(chatId, candidate.userId)
                    added.add(candidate)
                } else {
                    blocked.add(candidate)
                }
            }

            if (added.isNotEmpty()) {
                val inviterName = activeAccount.value?.displayName?.ifBlank { "Администратор" } ?: "Администратор"
                val namesJoined = added.joinToString(", ") { it.displayName }
                val sysMsg = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = chatId,
                    senderId = "system_group_event",
                    text = "$inviterName добавил(а) $namesJoined",
                    timestamp = System.currentTimeMillis(),
                    isDelivered = true,
                    senderIdentityType = "SYSTEM"
                )
                repository.insertMessageAndUpdateChat(sysMsg, sysMsg.text, null)
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(com.example.ui.group.GroupAddMembersOutcome(added, blocked))
            }
        }
    }

    fun sendGroupInviteLinkToUsers(
        groupChatId: String,
        targetUsers: List<com.example.ui.group.InvitableUser>,
        onComplete: (Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
            val inviterId = active?.id ?: "123456789"
            val groupChat = repository.getChatById(groupChatId)
            val links = repository.getInviteLinksForChatSync(groupChatId)
            val activePrimary = links.firstOrNull { it.isPrimary && it.isActive() }
                ?: links.firstOrNull { it.isActive() }
            val invitePath = activePrimary?.url
                ?: com.example.ui.group.normalizeInvitePath(
                    com.example.ui.channel.ChannelCustomizationManager.getCustomization(groupChatId).inviteLink
                ).ifBlank { com.example.ui.group.generateTelegramStyleInviteUrl(groupChatId) }

            if (activePrimary == null) {
                repository.insertInviteLink(
                    com.example.ui.group.GroupInviteLinkEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        chatId = groupChatId,
                        url = invitePath,
                        label = "Основная ссылка",
                        createdByUserId = inviterId,
                        createdByName = active?.displayName ?: "Владелец",
                        isPrimary = true
                    )
                )
            }

            val fullUrl = "https://$invitePath"
            val allChatsList = repository.allChats.firstOrNull().orEmpty()
            var sentCount = 0

            targetUsers.forEach { target ->
                val dmChat = allChatsList.find { ch ->
                    !ch.isGroup && !ch.isChannel && !ch.isBot && (
                        ch.id == target.userId ||
                            ch.id == "dm_${target.userId}" ||
                            (target.username.isNotBlank() && ch.title.equals(target.username, ignoreCase = true)) ||
                            ch.title.equals(target.displayName, ignoreCase = true)
                        )
                } ?: Chat(
                    id = "dm_${target.userId}",
                    title = target.displayName,
                    isGroup = false,
                    isChannel = false,
                    isBot = false,
                    lastMessage = fullUrl,
                    isContact = target.isContact
                ).also { repository.insertChat(it) }

                val cardJson = org.json.JSONObject().apply {
                    put("type", "group_invite_card")
                    put("groupId", groupChatId)
                    put("groupTitle", groupChat?.title ?: "Группа KuoteX")
                    put("inviteUrl", invitePath)
                    put("requiresApproval", activePrimary?.requiresApproval == true)
                }.toString()

                val msg = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = dmChat.id,
                    senderId = inviterId,
                    text = fullUrl,
                    timestamp = System.currentTimeMillis(),
                    isDelivered = true,
                    documentData = cardJson,
                    senderIdentityId = inviterId,
                    senderIdentityType = "PERSONAL",
                    senderDisplayName = active?.displayName ?: "Вы"
                )
                repository.insertMessageAndUpdateChat(msg, "Приглашение в группу «${groupChat?.title ?: "Группа"}»", active?.displayName)
                sentCount++
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete(sentCount)
            }
        }
    }

    fun resolveInviteLinkForPreview(
        rawUrl: String,
        onResult: (com.example.ui.group.InvitePreviewResolution) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = com.example.ui.group.normalizeInvitePath(rawUrl)
            val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
            val allLinks = repository.getAllInviteLinks().firstOrNull().orEmpty()
            val matchedLink = allLinks.find { com.example.ui.group.normalizeInvitePath(it.url) == normalized }
            val fallbackChatId = if (matchedLink == null) {
                com.example.ui.channel.ChannelCustomizationManager.getAllCustomizationsFlow().value.entries
                    .firstOrNull { com.example.ui.group.normalizeInvitePath(it.value.inviteLink) == normalized }
                    ?.key
            } else null

            val targetChatId = matchedLink?.chatId ?: fallbackChatId
            val chat = targetChatId?.let { repository.getChatById(it) }
            if (targetChatId == null || chat == null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(
                        com.example.ui.group.InvitePreviewResolution(
                            isValid = false,
                            errorMessage = "Пригласительная ссылка не найдена или больше недействительна"
                        )
                    )
                }
                return@launch
            }

            val inactiveReason = matchedLink?.inactiveReason()
            if (inactiveReason != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(
                        com.example.ui.group.InvitePreviewResolution(
                            isValid = false,
                            errorMessage = inactiveReason,
                            chat = chat,
                            link = matchedLink
                        )
                    )
                }
                return@launch
            }

            val members = repository.getGroupMembers(targetChatId).firstOrNull().orEmpty()
            val currentMember = com.example.ui.channel.findCurrentChannelMember(members, active)
            val custom = com.example.ui.channel.ChannelCustomizationManager.getCustomization(targetChatId)
            val pendingReq = active?.let { repository.getPendingRequestForUser(targetChatId, it.id) }
            val approvalNeeded = (matchedLink?.requiresApproval == true) || custom.discussionJoinRequestsRequired

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(
                    com.example.ui.group.InvitePreviewResolution(
                        isValid = true,
                        chat = chat,
                        link = matchedLink,
                        memberCount = members.size.coerceAtLeast(1),
                        requiresApproval = approvalNeeded,
                        isAlreadyMember = currentMember != null,
                        hasPendingRequest = pendingReq != null,
                        canSeePrivateBadge = com.example.ui.group.canSeePrivateGroupBadge(currentMember),
                        isPublic = custom.isPublic,
                        avatarUrl = custom.avatarUrl
                    )
                )
            }
        }
    }

    fun joinOrRequestViaInviteLink(
        rawUrl: String,
        onResult: (Boolean, String, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = com.example.ui.group.normalizeInvitePath(rawUrl)
            val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
            if (active == null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(false, "Необходимо войти в аккаунт", null)
                }
                return@launch
            }
            val allLinks = repository.getAllInviteLinks().firstOrNull().orEmpty()
            val matchedLink = allLinks.find { com.example.ui.group.normalizeInvitePath(it.url) == normalized }
            val fallbackChatId = if (matchedLink == null) {
                com.example.ui.channel.ChannelCustomizationManager.getAllCustomizationsFlow().value.entries
                    .firstOrNull { com.example.ui.group.normalizeInvitePath(it.value.inviteLink) == normalized }
                    ?.key
            } else null

            val targetChatId = matchedLink?.chatId ?: fallbackChatId
            val chat = targetChatId?.let { repository.getChatById(it) }
            if (targetChatId == null || chat == null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(false, "Ссылка недействительна", null)
                }
                return@launch
            }

            val inactiveReason = matchedLink?.inactiveReason()
            if (inactiveReason != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(false, inactiveReason, null)
                }
                return@launch
            }

            val existingMember = repository.getGroupMemberSync(targetChatId, active.id)
            if (existingMember != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(true, "Вы уже состоите в группе «${chat.title}»", targetChatId)
                }
                return@launch
            }

            val custom = com.example.ui.channel.ChannelCustomizationManager.getCustomization(targetChatId)
            val requiresApproval = (matchedLink?.requiresApproval == true) || custom.discussionJoinRequestsRequired
            val displayName = active.displayName.ifBlank { active.username.ifBlank { "Пользователь" } }

            if (requiresApproval) {
                val existingReq = repository.getPendingRequestForUser(targetChatId, active.id)
                if (existingReq == null) {
                    repository.insertJoinRequest(
                        com.example.ui.group.GroupJoinRequestEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            chatId = targetChatId,
                            userId = active.id,
                            userName = displayName,
                            userAvatarUrl = active.profilePicUrl,
                            inviteLinkId = matchedLink?.id,
                            inviteLinkLabel = matchedLink?.label ?: "",
                            status = com.example.ui.group.GroupJoinRequestEntity.STATUS_PENDING,
                            requestedAt = System.currentTimeMillis()
                        )
                    )
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(true, "Заявка на вступление отправлена администраторам", null)
                }
            } else {
                repository.insertGroupMember(
                    GroupMember(
                        chatId = targetChatId,
                        userId = active.id,
                        userName = displayName,
                        isAdmin = false,
                        role = "subscriber",
                        joinedAt = System.currentTimeMillis()
                    )
                )
                if (matchedLink != null) {
                    repository.incrementInviteUsageCount(matchedLink.id)
                }
                com.example.ui.channel.ChannelCustomizationManager.consumeGreetingMessagesForNewMember(targetChatId, active.id)
                val sysMsg = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = targetChatId,
                    senderId = "system_group_event",
                    text = "$displayName вступил(а) в группу по ссылке",
                    timestamp = System.currentTimeMillis(),
                    isDelivered = true,
                    senderIdentityType = "SYSTEM"
                )
                repository.insertMessageAndUpdateChat(sysMsg, sysMsg.text, null)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(true, "Вы вступили в группу «${chat.title}»", targetChatId)
                }
            }
        }
    }

    fun approveJoinRequest(request: com.example.ui.group.GroupJoinRequestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateJoinRequestStatus(
                request.id,
                com.example.ui.group.GroupJoinRequestEntity.STATUS_APPROVED
            )
            repository.insertGroupMember(
                GroupMember(
                    chatId = request.chatId,
                    userId = request.userId,
                    userName = request.userName,
                    isAdmin = false,
                    role = "subscriber",
                    joinedAt = System.currentTimeMillis()
                )
            )
            if (!request.inviteLinkId.isNullOrBlank()) {
                repository.incrementInviteUsageCount(request.inviteLinkId)
            }
            com.example.ui.channel.ChannelCustomizationManager.consumeGreetingMessagesForNewMember(request.chatId, request.userId)
            val sysMsg = Message(
                id = java.util.UUID.randomUUID().toString(),
                chatId = request.chatId,
                senderId = "system_group_event",
                text = "${request.userName} был(а) принят(а) в группу",
                timestamp = System.currentTimeMillis(),
                isDelivered = true,
                senderIdentityType = "SYSTEM"
            )
            repository.insertMessageAndUpdateChat(sysMsg, sysMsg.text, null)
            com.example.ui.channel.ChannelCustomizationManager.recordAdminLog(
                chatId = request.chatId,
                title = "Одобрена заявка",
                details = "${request.userName} принят(а) в группу"
            )
        }
    }

    fun declineJoinRequest(request: com.example.ui.group.GroupJoinRequestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateJoinRequestStatus(
                request.id,
                com.example.ui.group.GroupJoinRequestEntity.STATUS_DECLINED
            )
            com.example.ui.channel.ChannelCustomizationManager.recordAdminLog(
                chatId = request.chatId,
                title = "Отклонена заявка",
                details = "Заявка пользователя ${request.userName} отклонена"
            )
        }
    }

    fun observeAvailableSendAsIdentities(groupChatId: String): Flow<List<com.example.ui.group.SendAsIdentity>> {
        return kotlinx.coroutines.flow.combine(
            chats,
            activeAccount,
            repository.getAllGroupMembers(),
            com.example.ui.channel.ChannelCustomizationManager.getAllCustomizationsFlow()
        ) { allChats, active, allMembers, customizations ->
            val groupChat = allChats.find { it.id == groupChatId } ?: return@combine emptyList()
            val byChat = allMembers.groupBy { it.chatId }
            com.example.ui.group.resolveAvailableSendAsIdentities(
                groupChat = groupChat,
                activeAccount = active,
                allChats = allChats,
                allMembersByChat = byChat,
                customizations = customizations
            )
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
    
    fun togglePinChat(chatId: String, onLimitReached: (() -> Unit)? = null) {
        viewModelScope.launch {
            val currentChats = chats.value
            val chat = currentChats.find { it.id == chatId } ?: return@launch
            if (!chat.isPinned) {
                val currentPinnedCount = currentChats.count { it.isPinned && !it.isArchived && !it.isBlocked }
                if (currentPinnedCount >= 5) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onLimitReached?.invoke()
                    }
                    return@launch
                }
                repository.updateChatPinStatus(chatId, true, System.currentTimeMillis())
            } else {
                repository.updateChatPinStatus(chatId, false, 0L)
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

    fun sendChannelDirectMessage(
        channelId: String,
        subscriberId: String,
        subscriberName: String,
        subscriberAvatar: String?,
        senderId: String,
        text: String,
        mediaPath: String? = null,
        mediaType: String? = null,
        documentData: String? = null
    ) {
        viewModelScope.launch {
            val threadId = com.example.ui.channel.ChannelDirectMessageManager.buildThreadId(channelId, subscriberId)
            val sanitizedText = MessageSanitizer.sanitize(text)
            val encryptedMsg = signalProtocolManager.encryptMessage(sanitizedText)
            val activeAcc = activeAccount.value
            val msg = Message(
                id = java.util.UUID.randomUUID().toString(),
                chatId = threadId,
                senderId = senderId.ifBlank { activeAcc?.id ?: "me" },
                text = encryptedMsg,
                timestamp = System.currentTimeMillis(),
                isDelivered = true,
                mediaPath = mediaPath,
                mediaType = mediaType,
                documentData = documentData,
                senderDisplayName = activeAcc?.displayName?.takeIf { it.isNotBlank() } ?: subscriberName,
                senderAvatarUrl = activeAcc?.profilePicUrl?.takeIf { it.isNotBlank() } ?: subscriberAvatar
            )
            repository.insertMessage(msg)
            val preview = sanitizedText.ifBlank {
                when (mediaType) {
                    "gif" -> "GIF"
                    "sticker" -> "Стикер"
                    else -> "Медиа"
                }
            }
            com.example.ui.channel.ChannelDirectMessageManager.recordThreadActivity(
                channelId = channelId,
                subscriberId = subscriberId,
                subscriberName = subscriberName,
                subscriberAvatar = subscriberAvatar,
                lastPreview = preview,
                pendingDelta = 0
            )
        }
    }

    fun submitSuggestedPost(
        channelId: String,
        subscriberId: String,
        subscriberName: String,
        subscriberAvatar: String?,
        postText: String,
        mediaPath: String? = null,
        mediaType: String? = null,
        priceStars: Long,
        scheduledTimestamp: Long?,
        scheduledLabel: String,
        onSuccess: () -> Unit,
        onInsufficientBalance: (Long, Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val resolvedSubId = subscriberId.ifBlank { activeAccount.value?.id ?: "me" }
            val resolvedSubName = subscriberName.ifBlank { activeAccount.value?.displayName ?: "Вы" }
            val payload = com.example.ui.channel.SuggestedPostPayload(
                channelId = channelId,
                subscriberId = resolvedSubId,
                subscriberName = resolvedSubName,
                text = postText.ifBlank { "Предложенный пост для канала" },
                mediaPath = mediaPath,
                mediaType = mediaType,
                priceStars = priceStars.coerceAtLeast(0L),
                approxUsdText = com.example.ui.channel.SuggestedPostPayload.formatApproxUsd(priceStars),
                scheduledTimestamp = scheduledTimestamp,
                scheduledLabel = scheduledLabel.ifBlank { "Любое" },
                status = com.example.ui.channel.SuggestedPostStatus.PENDING
            )

            if (payload.priceStars > 0L) {
                val currentBalance = com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.currentUserState.value?.balance ?: 0L
                if (currentBalance < payload.priceStars) {
                    onInsufficientBalance(currentBalance, payload.priceStars)
                    return@launch
                }
                val escrowResult = com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.processSuggestedPostEscrow(
                    senderUserId = resolvedSubId,
                    channelId = channelId,
                    priceStars = payload.priceStars,
                    proposalId = payload.proposalId
                )
                if (escrowResult.isFailure) {
                    val err = escrowResult.exceptionOrNull()
                    if (err is com.example.data.ecosystem.InsufficientBalanceException) {
                        val updatedCur = com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.currentUserState.value?.balance ?: 0L
                        onInsufficientBalance(updatedCur, payload.priceStars)
                    } else {
                        onError(err?.message ?: "Не удалось зарезервировать звёзды")
                    }
                    return@launch
                }
            }

            val threadId = com.example.ui.channel.ChannelDirectMessageManager.buildThreadId(channelId, resolvedSubId)
            val sanitizedText = MessageSanitizer.sanitize(payload.text)
            val encryptedMsg = signalProtocolManager.encryptMessage(sanitizedText)
            val activeAcc = activeAccount.value
            val msg = Message(
                id = java.util.UUID.randomUUID().toString(),
                chatId = threadId,
                senderId = resolvedSubId,
                text = encryptedMsg,
                timestamp = System.currentTimeMillis(),
                isDelivered = true,
                mediaPath = mediaPath,
                mediaType = mediaType,
                documentData = payload.toJsonString(),
                senderDisplayName = activeAcc?.displayName?.takeIf { it.isNotBlank() } ?: resolvedSubName,
                senderAvatarUrl = activeAcc?.profilePicUrl?.takeIf { it.isNotBlank() } ?: subscriberAvatar
            )
            repository.insertMessage(msg)

            val priceBadge = if (payload.priceStars > 0L) "⭐ ${payload.priceStars}" else "Бесплатно"
            com.example.ui.channel.ChannelDirectMessageManager.recordThreadActivity(
                channelId = channelId,
                subscriberId = resolvedSubId,
                subscriberName = resolvedSubName,
                subscriberAvatar = subscriberAvatar,
                lastPreview = "Предложенный пост ($priceBadge)",
                pendingDelta = 1
            )
            onSuccess()
        }
    }

    fun acceptSuggestedPost(
        context: android.content.Context,
        dmThreadId: String,
        message: Message,
        scheduleForRequestedTime: Boolean
    ) {
        viewModelScope.launch {
            val payload = com.example.ui.channel.SuggestedPostPayload.fromJson(message.documentData) ?: return@launch
            if (payload.status != com.example.ui.channel.SuggestedPostStatus.PENDING) return@launch

            val activeAcc = activeAccount.value
            val ownerId = activeAcc?.id ?: "me"
            val now = System.currentTimeMillis()
            val shouldSchedule = scheduleForRequestedTime &&
                payload.scheduledTimestamp != null &&
                payload.scheduledTimestamp > now

            val nextStatus = if (shouldSchedule) {
                com.example.ui.channel.SuggestedPostStatus.SCHEDULED
            } else {
                com.example.ui.channel.SuggestedPostStatus.ACCEPTED
            }

            val updatedPayload = payload.copy(status = nextStatus)
            val updatedMsg = message.copy(documentData = updatedPayload.toJsonString())
            repository.updateMessageEncrypted(updatedMsg)

            if (payload.priceStars > 0L) {
                com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.creditChannelOwnerSuggestedPost(
                    ownerUserId = ownerId,
                    channelId = payload.channelId,
                    priceStars = payload.priceStars,
                    proposalId = payload.proposalId
                )
            }

            if (shouldSchedule && payload.scheduledTimestamp != null) {
                val delayMs = (payload.scheduledTimestamp - now).coerceAtLeast(1000L)
                val data = androidx.work.workDataOf(
                    "text" to payload.text,
                    "chatId" to payload.channelId,
                    "senderId" to ownerId
                )
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
                    .setInitialDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()
                androidx.work.WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
            } else {
                sendMessage(
                    chatId = payload.channelId,
                    senderId = ownerId,
                    text = payload.text,
                    mediaPath = payload.mediaPath,
                    mediaType = payload.mediaType
                )
            }

            com.example.ui.channel.ChannelDirectMessageManager.adjustPendingCount(payload.channelId, dmThreadId, -1)
            com.example.ui.channel.ChannelCustomizationManager.recordAdminLog(
                chatId = payload.channelId,
                title = if (shouldSchedule) "Запланирован предложенный пост" else "Опубликован предложенный пост",
                details = "От ${payload.subscriberName} (${if (payload.priceStars > 0) "⭐ ${payload.priceStars}" else "Бесплатно"})"
            )
        }
    }

    fun declineSuggestedPost(
        dmThreadId: String,
        message: Message
    ) {
        viewModelScope.launch {
            val payload = com.example.ui.channel.SuggestedPostPayload.fromJson(message.documentData) ?: return@launch
            if (payload.status != com.example.ui.channel.SuggestedPostStatus.PENDING) return@launch

            if (payload.priceStars > 0L) {
                com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.refundSuggestedPostEscrow(
                    subscriberId = payload.subscriberId,
                    channelId = payload.channelId,
                    priceStars = payload.priceStars,
                    proposalId = payload.proposalId
                )
            }

            val updatedPayload = payload.copy(status = com.example.ui.channel.SuggestedPostStatus.DECLINED)
            val updatedMsg = message.copy(documentData = updatedPayload.toJsonString())
            repository.updateMessageEncrypted(updatedMsg)

            com.example.ui.channel.ChannelDirectMessageManager.adjustPendingCount(payload.channelId, dmThreadId, -1)
            com.example.ui.channel.ChannelCustomizationManager.recordAdminLog(
                chatId = payload.channelId,
                title = "Отклонён предложенный пост",
                details = "От ${payload.subscriberName} (${if (payload.priceStars > 0) "Возвращено ⭐ ${payload.priceStars}" else "Бесплатно"})"
            )
        }
    }

    fun clearChannelDirectMessages(channelId: String, threadId: String) {
        viewModelScope.launch {
            val list = repository.getMessages(threadId).firstOrNull() ?: emptyList()
            list.forEach { msg ->
                repository.deleteMessage(msg.id)
            }
            com.example.ui.channel.ChannelDirectMessageManager.clearThreadSummary(channelId, threadId)
        }
    }

    fun purchaseStarsBundle(
        amount: Long,
        packageId: String,
        onResult: (Result<Long>) -> Unit
    ) {
        viewModelScope.launch {
            val userId = activeAccount.value?.id ?: "me"
            val result = com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.purchaseStarsBundle(
                userId = userId,
                amount = amount,
                packageId = packageId
            )
            onResult(result)
        }
    }

    // --- Channel Discussion Link Sync & Post Comments ("Комментарии") ---

    fun observeChannelPostCommentCounts(channelId: String): Flow<Map<String, Int>> {
        return repository.observeCommentCountsForChannel(channelId)
    }

    fun observePostMessage(postId: String): Flow<Message?> {
        return repository.observeMessageById(postId)
    }

    fun observePostComments(channelId: String, postId: String): Flow<List<com.example.ui.channel.ChannelPostCommentEntity>> {
        return repository.observeCommentsForPost(channelId, postId)
    }

    fun observeChannelDiscussionSync(channelId: String): Flow<Unit> {
        return com.example.ui.channel.ChannelDiscussionFirestoreSync
            .observeChannelDiscussionLink(channelId)
            .map { remote ->
                if (remote != null) {
                    val localChat = repository.getChatById(channelId)
                    val remoteGroupId = remote.linkedDiscussionGroupId?.takeIf { it.isNotBlank() }
                    if (remoteGroupId != null) {
                        val existingGroup = repository.getChatById(remoteGroupId)
                        if (existingGroup == null) {
                            repository.insertChat(
                                Chat(
                                    id = remoteGroupId,
                                    title = remote.linkedDiscussionGroupTitle.ifBlank { "Обсуждение" },
                                    isChannel = false,
                                    isGroup = true,
                                    lastMessage = "",
                                    linkedChannelId = channelId
                                )
                            )
                        } else if (existingGroup.linkedChannelId != channelId) {
                            repository.updateLinkedChannelId(remoteGroupId, channelId)
                        }
                    } else if (!localChat?.linkedDiscussionGroupId.isNullOrBlank()) {
                        repository.updateLinkedChannelId(localChat!!.linkedDiscussionGroupId!!, null)
                    }
                    if (localChat != null && localChat.linkedDiscussionGroupId != remoteGroupId) {
                        repository.updateLinkedDiscussionGroup(channelId, remoteGroupId)
                    }
                    com.example.ui.channel.ChannelCustomizationManager.syncRemoteDiscussionLink(
                        chatId = channelId,
                        discussionChatId = remoteGroupId,
                        discussionTitle = remote.linkedDiscussionGroupTitle,
                        onlyMembersCanSend = remote.onlyMembersCanSend,
                        joinRequestsRequired = remote.joinRequestsRequired
                    )
                }
            }
    }

    fun observeRemotePostCommentsSync(channelId: String, postId: String): Flow<Unit> {
        return com.example.ui.channel.ChannelDiscussionFirestoreSync
            .observePostComments(channelId, postId)
            .map { remoteList ->
                if (remoteList.isNotEmpty()) {
                    remoteList.forEach { comment ->
                        repository.insertPostComment(comment)
                    }
                    val total = repository.getCommentCountSync(channelId, postId)
                    val post = repository.getMessageById(postId)
                    if (post != null && total > post.commentCount) {
                        repository.updateMessageCommentCount(postId, total)
                    }
                }
            }
    }

    private suspend fun ensureMirroredGroupPost(
        channelId: String,
        channelChat: Chat?,
        post: Message,
        linkedGroupId: String
    ): String {
        val existingMirroredId = post.mirroredGroupMessageId
        if (!existingMirroredId.isNullOrBlank()) {
            val existingMsg = repository.getMessageById(existingMirroredId)
            if (existingMsg != null) {
                if (!existingMsg.isPinned) {
                    repository.updatePinStatus(existingMirroredId, true)
                    repository.updatePinnedMessage(linkedGroupId, existingMirroredId)
                }
                return existingMirroredId
            }
        }
        val channelCustom = com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId)
        val channelTitle = channelChat?.title?.ifBlank { "Канал" } ?: "Канал"
        val encryptedBody = signalProtocolManager.encryptMessage(post.text)
        val newMirroredId = java.util.UUID.randomUUID().toString()
        val fwdMsg = Message(
            id = newMirroredId,
            chatId = linkedGroupId,
            senderId = post.senderId,
            text = encryptedBody,
            audioPath = post.audioPath,
            mediaPath = post.mediaPath,
            mediaType = post.mediaType,
            documentData = post.documentData,
            timestamp = post.timestamp,
            isDelivered = true,
            isPinned = true,
            isForwarded = false,
            forwardOriginalSenderId = null,
            forwardOriginalSenderName = null,
            forwardHideSender = false,
            senderIdentityId = channelId,
            senderIdentityType = "CHANNEL",
            senderDisplayName = channelTitle,
            senderAvatarUrl = channelCustom.avatarUrl,
            senderCustomTag = "канал",
            senderEmojiStatus = channelCustom.emojiStatus
        )
        repository.insertMessageAndUpdateChat(fwdMsg, post.text, channelTitle)
        repository.updatePinStatus(newMirroredId, true)
        repository.updatePinnedMessage(linkedGroupId, newMirroredId)
        repository.updateMirroredGroupMessageId(post.id, newMirroredId)
        com.example.ui.channel.ChannelDiscussionFirestoreSync.syncMirroredGroupMessage(
            groupId = linkedGroupId,
            message = fwdMsg,
            plainText = post.text
        )
        return newMirroredId
    }

    fun sendPostComment(
        channelId: String,
        postId: String,
        text: String,
        postAsChannel: Boolean,
        mediaPath: String? = null,
        mediaType: String? = null,
        documentData: String? = null,
        replyToComment: com.example.ui.channel.ChannelPostCommentEntity? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val cleanText = MessageSanitizer.sanitize(text)
        if (cleanText.isBlank() && mediaPath.isNullOrBlank() && documentData.isNullOrBlank()) {
            onComplete?.invoke(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val active = activeAccount.value ?: repository.activeAccountFlow.firstOrNull()
            val channelChat = repository.getChatById(channelId)
            val channelCustom = com.example.ui.channel.ChannelCustomizationManager.getCustomization(channelId)
            val post = repository.getDecryptedMessageById(postId)
            val senderId = active?.id?.ifBlank { "me" } ?: "me"

            val channelMembers = repository.getGroupMembers(channelId).firstOrNull().orEmpty()
            val canActAsChannel = postAsChannel && (
                com.example.ui.channel.isChannelAdminOrCreator(channelMembers, active) ||
                    (channelChat != null && active == null)
                )

            val resolvedName = if (canActAsChannel) {
                channelChat?.title?.ifBlank { "Канал" } ?: "Канал"
            } else {
                active?.displayName?.ifBlank { active.username.ifBlank { "Вы" } } ?: "Вы"
            }
            val resolvedAvatar = if (canActAsChannel) channelCustom.avatarUrl else active?.profilePicUrl?.takeIf { it.isNotBlank() }

            val linkedGroupId = channelChat?.linkedDiscussionGroupId?.takeIf { it.isNotBlank() }
                ?: channelCustom.discussionChatId?.takeIf { it.isNotBlank() }

            var mirroredGroupPostId: String? = null
            var mirroredGroupReplyId: String? = null

            if (linkedGroupId != null && post != null) {
                val linkedGroup = repository.getChatById(linkedGroupId)
                if (linkedGroup != null && linkedGroup.isGroup && !linkedGroup.isChannel) {
                    mirroredGroupPostId = ensureMirroredGroupPost(channelId, channelChat, post, linkedGroupId)
                    val replyTargetId = replyToComment?.mirroredGroupReplyId?.takeIf { it.isNotBlank() }
                        ?: mirroredGroupPostId
                    val replySnippet = replyToComment?.text?.takeIf { it.isNotBlank() }
                        ?: post.text.ifBlank { "Публикация канала" }
                    val groupReplyId = java.util.UUID.randomUUID().toString()
                    val groupReplyMsg = Message(
                        id = groupReplyId,
                        chatId = linkedGroupId,
                        senderId = senderId,
                        text = signalProtocolManager.encryptMessage(cleanText),
                        mediaPath = mediaPath,
                        mediaType = mediaType,
                        documentData = documentData,
                        replyToMessageId = replyTargetId,
                        replyToMessageText = replySnippet.take(90),
                        timestamp = System.currentTimeMillis(),
                        isDelivered = true,
                        senderIdentityId = if (canActAsChannel) channelId else senderId,
                        senderIdentityType = if (canActAsChannel) "CHANNEL" else "PERSONAL",
                        senderDisplayName = resolvedName,
                        senderAvatarUrl = resolvedAvatar,
                        senderCustomTag = if (canActAsChannel) "канал" else null,
                        senderEmojiStatus = if (canActAsChannel) channelCustom.emojiStatus else null
                    )
                    repository.insertMessageAndUpdateChat(groupReplyMsg, cleanText.ifBlank { "Вложение" }, resolvedName)
                    mirroredGroupReplyId = groupReplyId
                    com.example.ui.channel.ChannelDiscussionFirestoreSync.syncMirroredGroupMessage(
                        groupId = linkedGroupId,
                        message = groupReplyMsg,
                        plainText = cleanText.ifBlank { "Вложение" }
                    )
                }
            }

            val comment = com.example.ui.channel.ChannelPostCommentEntity(
                id = java.util.UUID.randomUUID().toString(),
                channelId = channelId,
                postId = postId,
                senderId = senderId,
                senderName = resolvedName,
                senderAvatarUrl = resolvedAvatar,
                isChannelIdentity = canActAsChannel,
                text = cleanText,
                mediaPath = mediaPath,
                mediaType = mediaType,
                documentData = documentData,
                replyToCommentId = replyToComment?.id,
                replyToAuthorName = replyToComment?.senderName,
                replyToSnippet = replyToComment?.text?.take(80),
                mirroredGroupId = linkedGroupId,
                mirroredGroupPostId = mirroredGroupPostId,
                mirroredGroupReplyId = mirroredGroupReplyId,
                timestamp = System.currentTimeMillis()
            )
            repository.insertPostComment(comment)
            val totalCount = maxOf((post?.commentCount ?: 0) + 1, repository.getCommentCountSync(channelId, postId))
            repository.updateMessageCommentCount(postId, totalCount)

            com.example.ui.channel.ChannelDiscussionFirestoreSync.syncPostComment(
                comment = comment,
                updatedCommentCount = totalCount,
                mirroredGroupPostId = mirroredGroupPostId
            )

            if (onComplete != null) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            }
        }
    }
}
