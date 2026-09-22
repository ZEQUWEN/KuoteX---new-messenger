package com.example.ui

import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.botapi.BotRegistry
import com.example.utils.MessageSanitizer

object BotService {
    /**
     * Routes incoming messages to the appropriate Bot, if applicable.
     */
    suspend fun handleMessage(
        messageText: String, 
        chat: Chat, 
        repository: MessengerRepository, 
        signalProtocolManager: SignalProtocolManager
    ) {
        // Direct message to a bot
        if (chat.isBot) {
            val botId = chat.id
            val bot = BotRegistry.getBot(botId)
            bot?.onMessageReceived(MessageSanitizer.sanitize(messageText), chat, repository, signalProtocolManager)
            return
        }
        
        // Group chat where a bot might be mentioned
        if (chat.isGroup) {
            BotRegistry.getAllBots().forEach { bot ->
                if (messageText.contains("@${bot.name}", ignoreCase = true)) {
                    bot.onMessageReceived(MessageSanitizer.sanitize(messageText), chat, repository, signalProtocolManager)
                }
            }
        }
    }

    suspend fun handleJoinGroup(
        botId: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val bot = BotRegistry.getBot(botId)
        bot?.onJoinedGroup(chat, repository, signalProtocolManager)
    }

    suspend fun handleLeaveGroup(
        botId: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        val bot = BotRegistry.getBot(botId)
        bot?.onLeftGroup(chat, repository, signalProtocolManager)
    }
}
