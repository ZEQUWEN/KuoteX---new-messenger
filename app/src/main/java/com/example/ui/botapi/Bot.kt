package com.example.ui.botapi

import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.Chat
import com.example.ui.Message
import java.util.UUID
import com.example.utils.MessageSanitizer

/**
 * Interface representing a Bot in the KuoteX ecosystem.
 * Developers can implement this interface to create new bots.
 */
data class BotCommand(val command: String, val description: String)

interface Bot {
    val id: String
    val name: String
    val description: String
    val category: String
    val longDescription: String
    val commands: List<BotCommand>

    /** 
     * Called when the bot is added to a group or channel.
     */
    suspend fun onJoinedGroup(chat: Chat, repository: MessengerRepository, signalProtocolManager: SignalProtocolManager) {}

    /** 
     * Called when the bot is removed from a group or channel.
     */
    suspend fun onLeftGroup(chat: Chat, repository: MessengerRepository, signalProtocolManager: SignalProtocolManager) {}


    /**
     * Called when a message is routed to this bot.
     * @param messageText The plaintext message received.
     * @param chat The chat where the message was received.
     * @param repository The repository to access and save messages.
     * @param signalProtocolManager Used for encrypting outgoing messages.
     */
    suspend fun onCallbackQuery(
        callbackData: String,
        messageId: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {}

    suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    )

    /**
     * Helper method to send a reply back to the chat.
     * Always use this method to send a message as the bot.
     */
    suspend fun sendReply(
        text: String,
        chatId: String,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val sanitizedText = MessageSanitizer.sanitize(text)
        val encryptedReply = signalProtocolManager.encryptMessage(sanitizedText)
        val replyMsg = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "bot_$id",
            text = encryptedReply,
            timestamp = System.currentTimeMillis()
        )
        repository.insertMessageAndUpdateChat(replyMsg, sanitizedText, name)
    }
}
