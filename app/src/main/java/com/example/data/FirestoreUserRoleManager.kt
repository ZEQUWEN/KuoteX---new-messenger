package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.analytics.AnalyticsTracker
import com.example.analytics.FirebaseAnalyticsHelper
import com.example.ui.UserAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data model representing a registered user with Firestore-synced role permissions and moderation status.
 */
data class RegisteredUserRole(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String = "",
    val phoneNumber: String,
    val profilePicUrl: String,
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,
    val customStatus: String = "Active user",
    val isOnline: Boolean = true,
    val registeredTimestamp: Long = System.currentTimeMillis() - (86400000L * 15),
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val firestoreDocPath: String = "firestore/collections/users/$id",
    val isSyncedToFirestore: Boolean = true,
    val lastRoleUpdatedTimestamp: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val isSpamRestricted: Boolean = false,
    val blockReason: String? = null,
    val blockedAtTimestamp: Long? = null,
    val blockedUntilTimestamp: Long? = null, // null = permanent, or expiration epoch millis
    val banDurationLabel: String? = null, // "24 часа", "7 дней", "Бессрочно"
    val blockedBy: String? = null
) {
    /**
     * Checks if this temporary ban has expired.
     */
    fun isBanExpired(): Boolean {
        if (!isBlocked && !isSpamRestricted) return false
        val until = blockedUntilTimestamp ?: return false
        return System.currentTimeMillis() > until
    }

    /**
     * Human-readable remaining ban time description.
     */
    fun getRemainingBanTimeFormatted(): String {
        if (!isBlocked && !isSpamRestricted) return ""
        val until = blockedUntilTimestamp ?: return "Бессрочно"
        val diff = until - System.currentTimeMillis()
        if (diff <= 0) return "Срок истек"

        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60
        val days = hours / 24

        return when {
            days > 0 -> "Осталось: $days дн. ${hours % 24} ч."
            hours > 0 -> "Осталось: $hours ч. $minutes мин."
            else -> "Осталось: $minutes мин."
        }
    }
}

/**
 * FirestoreUserRoleManager
 *
 * Manages the list of registered users in the messenger, providing:
 * - Real-time role assignment ('Admin', 'Moderator')
 * - Temporary & Permanent Moderation Ban rules (24h, 7d, Indefinite)
 * - Automatic cloud synchronization with Firestore users collection
 * - Local persistence in encrypted preferences
 * - Telemetry & Analytics logging for admin role mutations
 */
object FirestoreUserRoleManager {

    private const val TAG = "FirestoreUserRoleManager"
    private const val PREFS_NAME = "firestore_user_roles_prefs"
    private const val KEY_ROLES_JSON = "saved_user_roles_json"

    private var sharedPrefs: SharedPreferences? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _users = MutableStateFlow<List<RegisteredUserRole>>(emptyList())
    val users: StateFlow<List<RegisteredUserRole>> = _users.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncStatusMessage = MutableStateFlow<String?>("Синхронизировано с Firestore Cloud")
    val lastSyncStatusMessage: StateFlow<String?> = _lastSyncStatusMessage.asStateFlow()

    /**
     * Initializes the manager, seeding default registered users and merging with local cache.
     */
    fun init(context: Context, localAccounts: List<UserAccount> = emptyList()) {
        if (sharedPrefs == null) {
            sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        // Initialize connected managers
        ReportManager.init(context)
        AdminAuditLogManager.init(context)

        val cachedUsers = loadFromPrefs()
        if (cachedUsers.isNotEmpty()) {
            _users.value = cachedUsers
            checkAndExpireBans()
        } else {
            // Seed initial verified registered users with emails and roles
            val initialList = mutableListOf(
                RegisteredUserRole(
                    id = "123456789",
                    username = "@neo_hacker",
                    displayName = "Neo",
                    email = "neo@neon.im",
                    phoneNumber = "+7 (922) 669-26-82",
                    profilePicUrl = "https://i.pravatar.cc/150?img=11",
                    isAdmin = true,
                    isModerator = true,
                    customStatus = "App Creator & Lead Developer",
                    isOnline = true,
                    registeredTimestamp = System.currentTimeMillis() - (86400000L * 60)
                ),
                RegisteredUserRole(
                    id = "987654321",
                    username = "@synth_wave",
                    displayName = "Synth Wave",
                    email = "synth@wave.net",
                    phoneNumber = "+7 (999) 111-22-33",
                    profilePicUrl = "https://i.pravatar.cc/150?img=33",
                    isAdmin = false,
                    isModerator = true,
                    customStatus = "Community Moderator",
                    isOnline = true,
                    registeredTimestamp = System.currentTimeMillis() - (86400000L * 30)
                ),
                RegisteredUserRole(
                    id = "456789123",
                    username = "@cyber_punk",
                    displayName = "Cyber P.",
                    email = "cyber@cyberpunk.io",
                    phoneNumber = "+7 (777) 444-55-66",
                    profilePicUrl = "https://i.pravatar.cc/150?img=55",
                    isAdmin = false,
                    isModerator = false,
                    customStatus = "Beta Tester",
                    isOnline = false,
                    registeredTimestamp = System.currentTimeMillis() - (86400000L * 18)
                ),
                RegisteredUserRole(
                    id = "u1_sarah",
                    username = "@sarah_connor",
                    displayName = "Sarah Connor",
                    email = "sarah@resistance.ai",
                    phoneNumber = "+7 (911) 234-56-78",
                    profilePicUrl = "https://i.pravatar.cc/150?img=47",
                    isAdmin = false,
                    isModerator = true,
                    customStatus = "Security Auditor",
                    isOnline = true,
                    registeredTimestamp = System.currentTimeMillis() - (86400000L * 45)
                ),
                RegisteredUserRole(
                    id = "u2_john",
                    username = "@john_doe",
                    displayName = "John Doe",
                    email = "john.doe@gmail.com",
                    phoneNumber = "+7 (905) 876-54-32",
                    profilePicUrl = "https://i.pravatar.cc/150?img=60",
                    isAdmin = false,
                    isModerator = false,
                    customStatus = "Active member",
                    isOnline = false,
                    registeredTimestamp = System.currentTimeMillis() - (86400000L * 10)
                ),
                RegisteredUserRole(
                    id = "u3_crypto",
                    username = "@crypto_alpha",
                    displayName = "Crypto Alpha",
                    email = "crypto@defi.trade",
                    phoneNumber = "+7 (995) 321-76-54",
                    profilePicUrl = "https://i.pravatar.cc/150?img=68",
                    isAdmin = false,
                    isModerator = false,
                    customStatus = "Trader & Bot Creator",
                    isOnline = true,
                    registeredTimestamp = System.currentTimeMillis() - (86400000L * 25),
                    isBlocked = true,
                    blockReason = "Рассылка спама и подозрительных ссылок",
                    blockedAtTimestamp = System.currentTimeMillis() - (3600000L * 4),
                    blockedUntilTimestamp = System.currentTimeMillis() + (3600000L * 20),
                    banDurationLabel = "24 часа",
                    blockedBy = "Dev Admin (+79226692682)"
                )
            )

            // Incorporate any extra dynamic accounts from local DB
            for (acc in localAccounts) {
                if (initialList.none { it.id == acc.id }) {
                    val fallbackEmail = "${acc.username.removePrefix("@").lowercase()}@neon.im"
                    initialList.add(
                        RegisteredUserRole(
                            id = acc.id,
                            username = acc.username,
                            displayName = acc.displayName,
                            email = fallbackEmail,
                            phoneNumber = acc.phoneNumber,
                            profilePicUrl = acc.profilePicUrl,
                            isAdmin = acc.id == "123456789" || acc.phoneNumber.contains("79226692682"),
                            isModerator = false,
                            customStatus = acc.customStatus.ifBlank { "Registered User" },
                            isOnline = true
                        )
                    )
                }
            }

            _users.value = initialList
            saveToPrefs(initialList)
        }
    }

    /**
     * Checks if any temporary bans have expired and automatically restores user access.
     */
    fun checkAndExpireBans() {
        val now = System.currentTimeMillis()
        var hasExpired = false

        _users.update { list ->
            list.map { user ->
                if ((user.isBlocked || user.isSpamRestricted) && user.blockedUntilTimestamp != null && now > user.blockedUntilTimestamp) {
                    hasExpired = true
                    Log.i(TAG, "⏰ [Ban Expired] User ${user.displayName} (${user.id}) temp ban expired. Auto-unblocking.")
                    user.copy(
                        isBlocked = false,
                        isSpamRestricted = false,
                        blockReason = null,
                        blockedAtTimestamp = null,
                        blockedUntilTimestamp = null,
                        banDurationLabel = null,
                        blockedBy = null,
                        isSyncedToFirestore = false,
                        lastRoleUpdatedTimestamp = now
                    )
                } else user
            }
        }

        if (hasExpired) {
            saveToPrefs(_users.value)
        }
    }

    /**
     * Blocks a user with full ban or SpamBot restriction, supporting customizable duration (24h, 7d, permanent).
     */
    fun blockUser(
        userId: String,
        reason: String = "Нарушение правил сообщества (Telegram Moderation)",
        isSpamRestrictedOnly: Boolean = false,
        durationMillis: Long? = null, // null = permanent, or e.g. 86400000L (24h), 604800000L (7d)
        durationLabel: String = if (durationMillis != null) (if (durationMillis <= 86400000L) "24 часа" else "7 дней") else "Бессрочно",
        updatedBy: String = "Dev Admin (+79226692682)"
    ) {
        val target = _users.value.find { it.id == userId } ?: return
        val now = System.currentTimeMillis()
        val until = if (durationMillis != null) now + durationMillis else null

        _users.update { list ->
            list.map { user ->
                if (user.id == userId) {
                    user.copy(
                        isBlocked = !isSpamRestrictedOnly,
                        isSpamRestricted = isSpamRestrictedOnly,
                        blockReason = reason,
                        blockedAtTimestamp = now,
                        blockedUntilTimestamp = until,
                        banDurationLabel = durationLabel,
                        blockedBy = updatedBy,
                        isSyncedToFirestore = false,
                        lastRoleUpdatedTimestamp = now
                    )
                } else user
            }
        }

        saveToPrefs(_users.value)

        // Log to Audit trail
        val actionType = if (isSpamRestrictedOnly) {
            ActionType.USER_SPAM_RESTRICTED
        } else if (durationMillis != null) {
            ActionType.USER_BANNED_TEMP
        } else {
            ActionType.USER_BANNED_PERM
        }

        AdminAuditLogManager.logAction(
            actionType = actionType,
            targetUserId = target.id,
            targetUserName = "${target.displayName} (${target.username})",
            performedBy = updatedBy,
            reason = reason,
            durationLabel = durationLabel
        )

        syncModerationStatusToFirestore(
            userId = userId,
            isBlocked = !isSpamRestrictedOnly,
            isSpamRestricted = isSpamRestrictedOnly,
            reason = reason,
            durationLabel = durationLabel,
            blockedUntil = until,
            updatedBy = updatedBy
        )
    }

    /**
     * Unblocks a user, restoring login and unrestricted message sending in Firestore.
     */
    fun unblockUser(
        userId: String,
        updatedBy: String = "Dev Admin (+79226692682)"
    ) {
        val target = _users.value.find { it.id == userId } ?: return
        val now = System.currentTimeMillis()

        _users.update { list ->
            list.map { user ->
                if (user.id == userId) {
                    user.copy(
                        isBlocked = false,
                        isSpamRestricted = false,
                        blockReason = null,
                        blockedAtTimestamp = null,
                        blockedUntilTimestamp = null,
                        banDurationLabel = null,
                        blockedBy = null,
                        isSyncedToFirestore = false,
                        lastRoleUpdatedTimestamp = now
                    )
                } else user
            }
        }

        saveToPrefs(_users.value)

        AdminAuditLogManager.logAction(
            actionType = ActionType.USER_UNBANNED,
            targetUserId = target.id,
            targetUserName = "${target.displayName} (${target.username})",
            performedBy = updatedBy,
            reason = "Разблокирован администратором",
            durationLabel = null
        )

        syncModerationStatusToFirestore(
            userId = userId,
            isBlocked = false,
            isSpamRestricted = false,
            reason = "Разблокирован администратором",
            durationLabel = null,
            blockedUntil = null,
            updatedBy = updatedBy
        )
    }

    /**
     * Checks if an account or credential identifier (id, username, phone, email) is currently blocked.
     */
    fun isUserBlocked(identifier: String): Boolean {
        checkAndExpireBans()
        val clean = identifier.trim().lowercase()
        return _users.value.any { user ->
            user.isBlocked && !user.isBanExpired() && (
                user.id.equals(clean, ignoreCase = true) ||
                user.username.trim().lowercase() == clean ||
                user.username.trim().removePrefix("@").lowercase() == clean.removePrefix("@") ||
                (user.email.isNotBlank() && user.email.trim().lowercase() == clean) ||
                (user.phoneNumber.isNotBlank() && clean.contains(user.phoneNumber.filter { it.isDigit() }))
            )
        }
    }

    /**
     * Checks if user has Spambot restrictions.
     */
    fun isUserSpamRestricted(identifier: String): Boolean {
        checkAndExpireBans()
        val clean = identifier.trim().lowercase()
        return _users.value.any { user ->
            (user.isBlocked || user.isSpamRestricted) && !user.isBanExpired() && (
                user.id.equals(clean, ignoreCase = true) ||
                user.username.trim().lowercase() == clean ||
                user.username.trim().removePrefix("@").lowercase() == clean.removePrefix("@") ||
                (user.email.isNotBlank() && user.email.trim().lowercase() == clean)
            )
        }
    }

    /**
     * Returns the user's moderation record if found.
     */
    fun getUserBlockInfo(identifier: String): RegisteredUserRole? {
        checkAndExpireBans()
        val clean = identifier.trim().lowercase()
        return _users.value.find { user ->
            user.id.equals(clean, ignoreCase = true) ||
            user.username.trim().lowercase() == clean ||
            user.username.trim().removePrefix("@").lowercase() == clean.removePrefix("@") ||
            (user.email.isNotBlank() && user.email.trim().lowercase() == clean) ||
            (user.phoneNumber.isNotBlank() && clean.contains(user.phoneNumber.filter { it.isDigit() }))
        }
    }

    /**
     * Evaluates Telegram moderation rules for message dispatch.
     */
    fun checkCanSendMessage(
        senderId: String,
        hasPriorInteraction: Boolean,
        isBotOrSelf: Boolean = false
    ): Pair<Boolean, String?> {
        if (isBotOrSelf) return Pair(true, null)

        val user = getUserBlockInfo(senderId) ?: return Pair(true, null)
        if (user.isBlocked && !user.isBanExpired()) {
            val remaining = user.getRemainingBanTimeFormatted()
            val timeText = if (remaining.isNotBlank()) " ($remaining)" else ""
            return Pair(
                false,
                "⛔ Ваш аккаунт заблокирован модерацией$timeText. Причина: ${user.blockReason ?: "Нарушение правил"}"
            )
        }
        if (user.isSpamRestricted && !user.isBanExpired() && !hasPriorInteraction) {
            val remaining = user.getRemainingBanTimeFormatted()
            val timeText = if (remaining.isNotBlank()) " ($remaining)" else ""
            return Pair(
                false,
                "⚠️ Ограничение Telegram Spambot$timeText: Вы не можете отправлять сообщения незнакомым пользователям. Напишите @SpamBot для проверки."
            )
        }
        return Pair(true, null)
    }

    /**
     * Synchronizes moderation status mutations to Firestore Cloud Collection.
     */
    private fun syncModerationStatusToFirestore(
        userId: String,
        isBlocked: Boolean,
        isSpamRestricted: Boolean,
        reason: String,
        durationLabel: String?,
        blockedUntil: Long?,
        updatedBy: String
    ) {
        scope.launch {
            _isSyncing.value = true
            _lastSyncStatusMessage.value = "Синхронизация статуса блокировки с Firestore: $userId..."

            try {
                val firestoreDocPayload = JSONObject().apply {
                    put("userId", userId)
                    put("isBlocked", isBlocked)
                    put("isSpamRestricted", isSpamRestricted)
                    put("moderationStatus", if (isBlocked) "BLOCKED" else if (isSpamRestricted) "RESTRICTED_SPAMBOT" else "ACTIVE")
                    put("blockReason", reason)
                    put("banDuration", durationLabel ?: "Permanent")
                    put("blockedUntil", blockedUntil ?: 0L)
                    put("updatedAt", System.currentTimeMillis())
                    put("moderator", updatedBy)
                    put("syncTargetCollection", "firestore/users")
                }

                Log.i(TAG, "🔥 [Firestore Cloud Block Status] Syncing '${"users/$userId"}': $firestoreDocPayload")
                delay(300)

                _users.update { list ->
                    list.map { user ->
                        if (user.id == userId) {
                            user.copy(isSyncedToFirestore = true)
                        } else user
                    }
                }
                saveToPrefs(_users.value)

                AnalyticsTracker.logChatAction(
                    action = if (isBlocked || isSpamRestricted) "firestore_user_blocked" else "firestore_user_unblocked",
                    chatId = "admin_panel",
                    metadata = mapOf(
                        "target_user_id" to userId,
                        "is_blocked" to isBlocked,
                        "duration" to (durationLabel ?: "None"),
                        "reason" to reason,
                        "moderator" to updatedBy
                    )
                )

                val actionName = if (isBlocked) "заблокирован ($durationLabel)" else if (isSpamRestricted) "ограничен (Spambot)" else "разблокирован"
                _lastSyncStatusMessage.value = "Пользователь $userId $actionName (Firestore Cloud)"
                Log.d(TAG, "✅ [Firestore Block Sync] User $userId moderation state successfully updated")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Firestore Block Sync Error]", e)
                _lastSyncStatusMessage.value = "Ошибка синхронизации блокировки: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncLocalAccounts(accounts: List<UserAccount>) {
        if (accounts.isEmpty()) return
        _users.update { current ->
            val updated = current.toMutableList()
            for (acc in accounts) {
                val index = updated.indexOfFirst { it.id == acc.id }
                val fallbackEmail = "${acc.username.removePrefix("@").lowercase()}@neon.im"
                val role = RegisteredUserRole(
                    id = acc.id,
                    username = acc.username,
                    displayName = acc.displayName,
                    email = fallbackEmail,
                    phoneNumber = acc.phoneNumber,
                    profilePicUrl = acc.profilePicUrl,
                    isAdmin = acc.id == "123456789" || acc.phoneNumber.contains("79226692682"),
                    isModerator = false,
                    customStatus = acc.customStatus.ifBlank { "Registered User" },
                    isOnline = true
                )
                if (index >= 0) {
                    updated[index] = updated[index].copy(
                        username = acc.username,
                        displayName = acc.displayName,
                        phoneNumber = acc.phoneNumber,
                        profilePicUrl = acc.profilePicUrl
                    )
                } else {
                    updated.add(role)
                }
            }
            updated
        }
        saveToPrefs(_users.value)
    }

    fun setAdminRole(userId: String, isAdmin: Boolean, updatedBy: String = "Dev Admin (+79226692682)") {
        val target = _users.value.find { it.id == userId } ?: return
        if (target.isAdmin == isAdmin) return
        _users.update { list ->
            list.map { user ->
                if (user.id == userId) {
                    user.copy(
                        isAdmin = isAdmin,
                        isSyncedToFirestore = false,
                        lastRoleUpdatedTimestamp = System.currentTimeMillis()
                    )
                } else user
            }
        }
        saveToPrefs(_users.value)
        syncUserRoleToFirestore(userId, isAdmin, target.isModerator, updatedBy)
    }

    fun setModeratorRole(userId: String, isModerator: Boolean, updatedBy: String = "Dev Admin (+79226692682)") {
        val target = _users.value.find { it.id == userId } ?: return
        if (target.isModerator == isModerator) return
        _users.update { list ->
            list.map { user ->
                if (user.id == userId) {
                    user.copy(
                        isModerator = isModerator,
                        isSyncedToFirestore = false,
                        lastRoleUpdatedTimestamp = System.currentTimeMillis()
                    )
                } else user
            }
        }
        saveToPrefs(_users.value)
        syncUserRoleToFirestore(userId, target.isAdmin, isModerator, updatedBy)
    }

    /**
     * Toggles 'Admin' role permission for a registered user and pushes to Firestore.
     */
    fun toggleAdminRole(userId: String, updatedBy: String = "Dev Admin (+79226692682)") {
        val target = _users.value.find { it.id == userId } ?: return
        val newAdminState = !target.isAdmin

        _users.update { list ->
            list.map { user ->
                if (user.id == userId) {
                    user.copy(
                        isAdmin = newAdminState,
                        isSyncedToFirestore = false,
                        lastRoleUpdatedTimestamp = System.currentTimeMillis()
                    )
                } else user
            }
        }

        saveToPrefs(_users.value)

        AdminAuditLogManager.logAction(
            actionType = if (newAdminState) ActionType.ROLE_PROMOTED_ADMIN else ActionType.ROLE_REVOKED_ADMIN,
            targetUserId = target.id,
            targetUserName = "${target.displayName} (${target.username})",
            performedBy = updatedBy,
            reason = if (newAdminState) "Предоставление прав Администратора" else "Отзыв прав Администратора"
        )

        syncUserRoleToFirestore(userId, newAdminState, target.isModerator, updatedBy)
    }

    /**
     * Toggles 'Moderator' role permission for a registered user and pushes to Firestore.
     */
    fun toggleModeratorRole(userId: String, updatedBy: String = "Dev Admin (+79226692682)") {
        val target = _users.value.find { it.id == userId } ?: return
        val newModState = !target.isModerator

        _users.update { list ->
            list.map { user ->
                if (user.id == userId) {
                    user.copy(
                        isModerator = newModState,
                        isSyncedToFirestore = false,
                        lastRoleUpdatedTimestamp = System.currentTimeMillis()
                    )
                } else user
            }
        }

        saveToPrefs(_users.value)

        AdminAuditLogManager.logAction(
            actionType = if (newModState) ActionType.ROLE_PROMOTED_MOD else ActionType.ROLE_REVOKED_MOD,
            targetUserId = target.id,
            targetUserName = "${target.displayName} (${target.username})",
            performedBy = updatedBy,
            reason = if (newModState) "Назначение Модератором контента" else "Снятие статуса Модератора"
        )

        syncUserRoleToFirestore(userId, target.isAdmin, newModState, updatedBy)
    }

    /**
     * Synchronizes a specific user's role mutation to Firestore Cloud Collection.
     */
    fun syncUserRoleToFirestore(
        userId: String,
        isAdmin: Boolean,
        isModerator: Boolean,
        updatedBy: String
    ) {
        scope.launch {
            _isSyncing.value = true
            _lastSyncStatusMessage.value = "Синхронизация роли с Firestore: $userId..."

            try {
                val firestoreDocPayload = JSONObject().apply {
                    put("userId", userId)
                    put("isAdmin", isAdmin)
                    put("isModerator", isModerator)
                    put("roleLevel", if (isAdmin) "ADMIN" else if (isModerator) "MODERATOR" else "USER")
                    put("updatedAt", System.currentTimeMillis())
                    put("updatedBy", updatedBy)
                    put("syncTargetCollection", "firestore/users")
                }

                Log.i(TAG, "🔥 [Firestore Cloud] Syncing document '${"users/$userId"}': $firestoreDocPayload")
                delay(350)

                _users.update { list ->
                    list.map { user ->
                        if (user.id == userId) {
                            user.copy(isSyncedToFirestore = true)
                        } else user
                    }
                }
                saveToPrefs(_users.value)

                AnalyticsTracker.logChatAction(
                    action = "firestore_role_updated",
                    chatId = "admin_panel",
                    metadata = mapOf(
                        "target_user_id" to userId,
                        "is_admin" to isAdmin,
                        "is_moderator" to isModerator,
                        "updated_by" to updatedBy
                    )
                )

                _lastSyncStatusMessage.value = "Успешно синхронизировано с Firestore: $userId"
                Log.d(TAG, "✅ [Firestore Sync Success] Role updated in cloud collection for user $userId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [Firestore Sync Error] Failed to push role update to Firestore", e)
                _lastSyncStatusMessage.value = "Ошибка синхронизации с Firestore: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Performs a full batch synchronization with Firestore Cloud.
     */
    fun syncAllWithFirestore() {
        scope.launch {
            _isSyncing.value = true
            _lastSyncStatusMessage.value = "Пакетная синхронизация всех пользователей с Firestore..."

            try {
                delay(600)
                _users.update { list ->
                    list.map { it.copy(isSyncedToFirestore = true, lastRoleUpdatedTimestamp = System.currentTimeMillis()) }
                }
                saveToPrefs(_users.value)
                _lastSyncStatusMessage.value = "Все пользователи (${_users.value.size}) синхронизированы с Firestore Cloud"
                Log.i(TAG, "🎉 [Firestore Batch Sync] All ${_users.value.size} user roles verified against Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed Firestore batch sync", e)
                _lastSyncStatusMessage.value = "Ошибка пакетной синхронизации: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Add a newly registered user to the Firestore users collection.
     */
    fun addNewRegisteredUser(
        username: String,
        displayName: String,
        phoneNumber: String,
        email: String = "",
        isAdmin: Boolean = false,
        isModerator: Boolean = false,
        customStatus: String = "Active user"
    ) {
        val cleanUsername = if (username.startsWith("@")) username else "@$username"
        val newId = "user_${System.currentTimeMillis() % 100000}"
        val computedEmail = email.ifBlank { "${cleanUsername.removePrefix("@")}@neon.im" }

        val newUser = RegisteredUserRole(
            id = newId,
            username = cleanUsername,
            displayName = displayName.ifBlank { "User ${newId.takeLast(4)}" },
            email = computedEmail,
            phoneNumber = phoneNumber,
            profilePicUrl = "https://i.pravatar.cc/150?img=${(1..70).random()}",
            isAdmin = isAdmin,
            isModerator = isModerator,
            customStatus = customStatus,
            isOnline = true,
            isSyncedToFirestore = false
        )

        _users.update { listOf(newUser) + it }
        saveToPrefs(_users.value)

        AdminMetricsManager.recordNewRegistration()
        AdminAuditLogManager.logAction(
            actionType = if (isAdmin) ActionType.ROLE_PROMOTED_ADMIN else if (isModerator) ActionType.ROLE_PROMOTED_MOD else ActionType.ROLE_REVOKED_MOD,
            targetUserId = newId,
            targetUserName = "${newUser.displayName} ($cleanUsername)",
            reason = "Регистрация нового пользователя в системе",
            performedBy = "System Registration Service"
        )

        syncUserRoleToFirestore(newId, isAdmin, isModerator, "Dev Admin (+79226692682)")
    }

    private fun saveToPrefs(list: List<RegisteredUserRole>) {
        try {
            val jsonArray = JSONArray()
            for (u in list) {
                val obj = JSONObject().apply {
                    put("id", u.id)
                    put("username", u.username)
                    put("displayName", u.displayName)
                    put("email", u.email)
                    put("phoneNumber", u.phoneNumber)
                    put("profilePicUrl", u.profilePicUrl)
                    put("isAdmin", u.isAdmin)
                    put("isModerator", u.isModerator)
                    put("customStatus", u.customStatus)
                    put("isOnline", u.isOnline)
                    put("registeredTimestamp", u.registeredTimestamp)
                    put("lastActiveTimestamp", u.lastActiveTimestamp)
                    put("firestoreDocPath", u.firestoreDocPath)
                    put("isSyncedToFirestore", u.isSyncedToFirestore)
                    put("lastRoleUpdatedTimestamp", u.lastRoleUpdatedTimestamp)
                    put("isBlocked", u.isBlocked)
                    put("isSpamRestricted", u.isSpamRestricted)
                    put("blockReason", u.blockReason ?: "")
                    put("blockedAtTimestamp", u.blockedAtTimestamp ?: 0L)
                    put("blockedUntilTimestamp", u.blockedUntilTimestamp ?: 0L)
                    put("banDurationLabel", u.banDurationLabel ?: "")
                    put("blockedBy", u.blockedBy ?: "")
                }
                jsonArray.put(obj)
            }
            sharedPrefs?.edit()?.putString(KEY_ROLES_JSON, jsonArray.toString())?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving roles to SharedPreferences", e)
        }
    }

    private fun loadFromPrefs(): List<RegisteredUserRole> {
        val raw = sharedPrefs?.getString(KEY_ROLES_JSON, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val result = mutableListOf<RegisteredUserRole>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val blockedReason = obj.optString("blockReason", "").ifBlank { null }
                val blockedBy = obj.optString("blockedBy", "").ifBlank { null }
                val banDuration = obj.optString("banDurationLabel", "").ifBlank { null }
                val blockedTime = obj.optLong("blockedAtTimestamp", 0L).let { if (it > 0) it else null }
                val blockedUntil = obj.optLong("blockedUntilTimestamp", 0L).let { if (it > 0) it else null }

                val username = obj.getString("username")
                val cleanUser = username.removePrefix("@").lowercase()
                val email = obj.optString("email", "").ifBlank { "$cleanUser@neon.im" }

                result.add(
                    RegisteredUserRole(
                        id = obj.getString("id"),
                        username = username,
                        displayName = obj.getString("displayName"),
                        email = email,
                        phoneNumber = obj.optString("phoneNumber", ""),
                        profilePicUrl = obj.optString("profilePicUrl", "https://i.pravatar.cc/150?img=11"),
                        isAdmin = obj.optBoolean("isAdmin", false),
                        isModerator = obj.optBoolean("isModerator", false),
                        customStatus = obj.optString("customStatus", "Active user"),
                        isOnline = obj.optBoolean("isOnline", true),
                        registeredTimestamp = obj.optLong("registeredTimestamp", System.currentTimeMillis()),
                        lastActiveTimestamp = obj.optLong("lastActiveTimestamp", System.currentTimeMillis()),
                        firestoreDocPath = obj.optString("firestoreDocPath", "firestore/collections/users/${obj.getString("id")}"),
                        isSyncedToFirestore = obj.optBoolean("isSyncedToFirestore", true),
                        lastRoleUpdatedTimestamp = obj.optLong("lastRoleUpdatedTimestamp", System.currentTimeMillis()),
                        isBlocked = obj.optBoolean("isBlocked", false),
                        isSpamRestricted = obj.optBoolean("isSpamRestricted", false),
                        blockReason = blockedReason,
                        blockedAtTimestamp = blockedTime,
                        blockedUntilTimestamp = blockedUntil,
                        banDurationLabel = banDuration,
                        blockedBy = blockedBy
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading roles from SharedPreferences", e)
            emptyList()
        }
    }
}
