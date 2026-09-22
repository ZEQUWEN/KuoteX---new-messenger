package com.example.notifications

import android.content.Context
import com.example.data.MessengerRepository
import com.example.data.SecureDatabaseHelper
import com.example.ui.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

data class FCPNotification(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatarUrl: String? = null,
    val chatTitle: String? = null,
    val text: String,
    val isMention: Boolean = false,
    val isGroup: Boolean = false,
    val isChannel: Boolean = false,
    val mentionedUsername: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

typealias TelegramBubbleNotification = FCPNotification

object InAppNotificationManager {
    private val _currentBubble = MutableStateFlow<FCPNotification?>(null)
    val currentBubble: StateFlow<FCPNotification?> = _currentBubble.asStateFlow()

    // Global App Lifecycle State: Tracks whether user is actively in the application
    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    // Current open chat ID inside the app (if any)
    private val _currentActiveChatId = MutableStateFlow<String?>(null)
    val currentActiveChatId: StateFlow<String?> = _currentActiveChatId.asStateFlow()

    fun setAppForeground(isInForeground: Boolean) {
        _isAppInForeground.value = isInForeground
        if (!isInForeground) {
            // Dismiss in-app overlay if app goes to background
            _currentBubble.value = null
        }
    }

    fun setActiveChatId(chatId: String?) {
        _currentActiveChatId.value = chatId
        if (chatId != null && _currentBubble.value?.chatId == chatId) {
            _currentBubble.value = null
        }
    }

    /**
     * Unified Divided Notification Dispatcher:
     * - IF user is IN THE APP: triggers the floating in-app FCP banner (with @username mention glow, quick reply, mark as read).
     * - IF user is NOT IN THE APP: triggers the Android Notification Shade (шторка экрана) with "Ответить" and "Отметить прочитанным".
     */
    fun dispatchIncomingNotification(
        context: Context,
        chatId: String,
        senderId: String,
        senderName: String,
        text: String,
        isMention: Boolean = false,
        chatTitle: String? = null,
        senderAvatarUrl: String? = null,
        isGroup: Boolean = false,
        isChannel: Boolean = false,
        mentionedUsername: String? = null,
        forceSystemNotification: Boolean = false
    ) {
        val inForeground = _isAppInForeground.value && !forceSystemNotification

        if (inForeground) {
            // User is actively in the app:
            // Check if they are currently inside this exact chat
            if (_currentActiveChatId.value == chatId) {
                // User is already reading this chat view; no intrusive banner needed
                return
            }

            // Display floating FCP In-App Notification banner
            val fcpNotification = FCPNotification(
                chatId = chatId,
                senderId = senderId,
                senderName = senderName,
                senderAvatarUrl = senderAvatarUrl,
                chatTitle = chatTitle,
                text = text,
                isMention = isMention,
                isGroup = isGroup,
                isChannel = isChannel,
                mentionedUsername = mentionedUsername
            )
            _currentBubble.value = fcpNotification
        } else {
            // User is NOT in the app (background/minimized/lock screen):
            // Display Android System Notification on the Notification Shade (шторка экрана)
            NotificationHelper.showMessageNotification(
                context = context,
                chatId = chatId,
                senderId = senderId,
                senderName = senderName,
                text = text,
                isMention = isMention,
                chatTitle = chatTitle,
                senderAvatarUrl = senderAvatarUrl,
                isGroup = isGroup,
                isChannel = isChannel
            )
        }
    }

    fun postNotification(notification: FCPNotification, currentActiveChatId: String? = null) {
        // If user is currently in this exact chat, we do not need to pop up an intrusive banner
        val activeChat = currentActiveChatId ?: _currentActiveChatId.value
        if (activeChat != null && activeChat == notification.chatId) {
            return
        }
        _currentBubble.value = notification
    }

    fun dismissBubble() {
        _currentBubble.value = null
    }

    fun dismissIfChatId(chatId: String) {
        if (_currentBubble.value?.chatId == chatId) {
            _currentBubble.value = null
        }
    }

    fun markAsRead(chatId: String, context: Context, onCompleted: () -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SecureDatabaseHelper.getInstance(context).database
                val activeAccount = db.userDao().getActiveAccount().firstOrNull()
                val currentUserId = activeAccount?.id ?: "123456789"
                db.messageDao().markAsRead(chatId, currentUserId)
                val chat = db.chatDao().getChatById(chatId)
                if (chat != null) {
                    db.chatDao().insertChat(chat.copy(unreadCount = 0))
                }
                NotificationHelper.cancelChatNotifications(context, chatId)
                dismissIfChatId(chatId)
            } catch (e: Exception) {
                android.util.Log.e("InAppNotification", "Error marking as read: ${e.message}")
            } finally {
                CoroutineScope(Dispatchers.Main).launch {
                    onCompleted()
                }
            }
        }
    }

    fun sendReply(
        chatId: String,
        replyText: String,
        context: Context,
        onCompleted: () -> Unit = {}
    ) {
        if (replyText.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SecureDatabaseHelper.getInstance(context).database
                val activeAccount = db.userDao().getActiveAccount().firstOrNull()
                val currentUserId = activeAccount?.id ?: "123456789"
                val sanitizedReply = com.example.utils.MessageSanitizer.sanitize(replyText)
                val encryptedText = com.example.data.CryptoManager.encrypt(sanitizedReply)
                val messageId = UUID.randomUUID().toString()

                val replyMsg = Message(
                    id = messageId,
                    chatId = chatId,
                    senderId = currentUserId,
                    text = encryptedText,
                    timestamp = System.currentTimeMillis(),
                    isDelivered = true
                )
                db.messageDao().insertMessage(replyMsg)

                val chat = db.chatDao().getChatById(chatId)
                if (chat != null) {
                    db.chatDao().insertChat(
                        chat.copy(
                            lastMessage = sanitizedReply,
                            lastMessageTimestamp = System.currentTimeMillis(),
                            lastMessageSenderName = "You",
                            unreadCount = 0
                        )
                    )
                }
                db.messageDao().markAsRead(chatId, currentUserId)
                NotificationHelper.cancelChatNotifications(context, chatId)
                dismissIfChatId(chatId)

                com.example.analytics.FirebaseAnalyticsHelper.logMessageSendSuccess(
                    messageId = messageId,
                    chatId = chatId,
                    durationMs = 25L,
                    transportType = "bubble_quick_reply",
                    retryCount = 0,
                    wasCachedOffline = false
                )
            } catch (e: Exception) {
                android.util.Log.e("InAppNotification", "Error sending reply: ${e.message}")
            } finally {
                CoroutineScope(Dispatchers.Main).launch {
                    onCompleted()
                }
            }
        }
    }
}
