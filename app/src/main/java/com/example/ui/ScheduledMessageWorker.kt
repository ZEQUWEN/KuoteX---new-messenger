package com.example.ui

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SecureDatabaseHelper
import com.example.crypto.SignalProtocolManager
import com.example.utils.MessageSanitizer

class ScheduledMessageWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val text = inputData.getString("text") ?: return Result.failure()
        val chatId = inputData.getString("chatId") ?: return Result.failure()
        val senderId = inputData.getString("senderId") ?: return Result.failure()
        
        val sanitizedText = MessageSanitizer.sanitize(text)
        val signalManager = SignalProtocolManager()
        val encryptedMsg = signalManager.encryptMessage(sanitizedText)
        
        val msg = Message(
            id = java.util.UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = senderId,
            text = encryptedMsg,
            timestamp = System.currentTimeMillis(),
            isDelivered = true
        )
        
        val db = SecureDatabaseHelper.getInstance(applicationContext).database
        db.messageDao().insertMessage(msg)
        
        val chat = db.chatDao().getChatById(chatId)
        if (chat != null) {
            db.chatDao().insertChat(chat.copy(lastMessage = sanitizedText, lastMessageTimestamp = msg.timestamp, lastMessageSenderName = "You"))
        }

        return Result.success()
    }
}
