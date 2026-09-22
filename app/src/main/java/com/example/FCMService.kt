package com.example

import android.util.Log
import com.example.data.CryptoManager
import com.example.data.SecureDatabaseHelper
import com.example.notifications.InAppNotificationManager
import com.example.notifications.NotificationHelper
import com.example.notifications.TelegramBubbleNotification
import com.example.ui.Message
import com.example.utils.MessageSanitizer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "FCM Registration Token refreshed: $token")
        // Token is registered for backend dispatch
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCMService", "FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data

        val rawChatId = data["chat_id"] ?: data["chatId"] ?: "c1"
        val rawSenderId = data["sender_id"] ?: data["senderId"] ?: "remote_user"
        val rawSenderName = data["sender_name"] ?: data["senderName"] ?: remoteMessage.notification?.title ?: "Пользователь"
        val rawText = data["text"] ?: data["body"] ?: data["message"] ?: remoteMessage.notification?.body ?: "Новое сообщение"
        val chatTitle = data["chat_title"] ?: data["chatTitle"]
        val avatarUrl = data["avatar_url"] ?: data["sender_avatar"]
        val isGroup = data["is_group"]?.toBooleanStrictOrNull() ?: false

        val sanitizedText = MessageSanitizer.sanitize(rawText)

        // Check if message mentions active user
        val isExplicitMention = data["is_mention"]?.toBooleanStrictOrNull() == true ||
                data["isMention"]?.toBooleanStrictOrNull() == true ||
                sanitizedText.contains("@neo", ignoreCase = true) ||
                sanitizedText.contains("@")

        // Persist message to encrypted Room DB and update UI & Notifications
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SecureDatabaseHelper.getInstance(applicationContext).database
                val activeAccount = db.userDao().getActiveAccount().firstOrNull()
                val myUserId = activeAccount?.id ?: "123456789"
                val myUsername = activeAccount?.username ?: "@neo_hacker"

                val isMention = isExplicitMention ||
                        (myUsername.isNotBlank() && sanitizedText.contains(myUsername.removePrefix("@"), ignoreCase = true))

                // Insert into Room
                val messageId = data["message_id"] ?: UUID.randomUUID().toString()
                val encryptedText = CryptoManager.encrypt(sanitizedText)
                val msg = Message(
                    id = messageId,
                    chatId = rawChatId,
                    senderId = rawSenderId,
                    text = encryptedText,
                    timestamp = System.currentTimeMillis(),
                    isDelivered = true
                )
                db.messageDao().insertMessage(msg)

                // Update Chat info
                val existingChat = db.chatDao().getChatById(rawChatId)
                if (existingChat != null) {
                    db.chatDao().insertChat(
                        existingChat.copy(
                            lastMessage = sanitizedText,
                            lastMessageTimestamp = System.currentTimeMillis(),
                            lastMessageSenderName = rawSenderName,
                            unreadCount = existingChat.unreadCount + 1
                        )
                    )
                }

                // Unified Divided Notification Dispatcher:
                // Routes to in-app FCP floating bubble if app is active, or to Android Notification Shade if background/minimized
                InAppNotificationManager.dispatchIncomingNotification(
                    context = applicationContext,
                    chatId = rawChatId,
                    senderId = rawSenderId,
                    senderName = rawSenderName,
                    text = sanitizedText,
                    isMention = isMention,
                    chatTitle = chatTitle ?: existingChat?.title,
                    senderAvatarUrl = avatarUrl,
                    isGroup = isGroup,
                    isChannel = data["is_channel"]?.toBooleanStrictOrNull() ?: false,
                    mentionedUsername = if (isMention) myUsername else null
                )

                com.example.analytics.FirebaseAnalyticsHelper.logMessageSendSuccess(
                    messageId = messageId,
                    chatId = rawChatId,
                    durationMs = 20L,
                    transportType = "fcm_push",
                    retryCount = 0,
                    wasCachedOffline = false
                )
            } catch (e: Exception) {
                Log.e("FCMService", "Error processing FCM push notification: ${e.message}", e)
            }
        }
    }
}
