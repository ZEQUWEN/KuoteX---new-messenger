package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.data.CryptoManager
import com.example.data.SecureDatabaseHelper
import com.example.ui.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val chatId = intent.getStringExtra(NotificationHelper.EXTRA_CHAT_ID) ?: return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SecureDatabaseHelper.getInstance(context).database
                val activeAccount = db.userDao().getActiveAccount().firstOrNull()
                val currentUserId = activeAccount?.id ?: "123456789"

                when (action) {
                    NotificationHelper.ACTION_REPLY -> {
                        val remoteInput = RemoteInput.getResultsFromIntent(intent)
                        val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()
                            ?: intent.getStringExtra(NotificationHelper.EXTRA_REPLY_TEXT)

                        if (!replyText.isNullOrBlank()) {
                            val sanitizedReply = com.example.utils.MessageSanitizer.sanitize(replyText)
                            val encryptedText = CryptoManager.encrypt(sanitizedReply)
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

                            // Cancel or update notification
                            if (notificationId != -1) {
                                NotificationHelper.cancelNotification(context, notificationId)
                            }
                            InAppNotificationManager.dismissIfChatId(chatId)

                            com.example.analytics.FirebaseAnalyticsHelper.logMessageSendSuccess(
                                messageId = messageId,
                                chatId = chatId,
                                durationMs = 30L,
                                transportType = "quick_reply_notification",
                                retryCount = 0,
                                wasCachedOffline = false
                            )
                        }
                    }

                    NotificationHelper.ACTION_MARK_AS_READ -> {
                        db.messageDao().markAsRead(chatId, currentUserId)
                        val chat = db.chatDao().getChatById(chatId)
                        if (chat != null) {
                            db.chatDao().insertChat(chat.copy(unreadCount = 0))
                        }
                        if (notificationId != -1) {
                            NotificationHelper.cancelNotification(context, notificationId)
                        }
                        InAppNotificationManager.dismissIfChatId(chatId)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationReceiver", "Error processing notification action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
