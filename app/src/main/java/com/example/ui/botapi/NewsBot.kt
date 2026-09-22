package com.example.ui.botapi

import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.Chat
import kotlinx.coroutines.delay

class NewsBot : Bot {
    override val id = "b6"
    override val name = "NewsBot"
    override val description = "Get latest news headlines."
    override val category = "News"
    override val longDescription = "NewsBot provides the latest headlines from around the world. Use /news to see top stories."
    override val commands = listOf(BotCommand("/news", "Get latest headlines"), BotCommand("/tech", "Get technology news"))

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        delay(1200) // Simulate network call to external news API
        val text = messageText.lowercase()
        val reply = if (text.contains("/tech")) {
            "Latest Tech News:\n1. AI Models reach new heights.\n2. Quantum computing breakthrough."
        } else if (text.contains("/news")) {
            "Top Headlines:\n1. Global markets rally.\n2. New space mission launched successfully."
        } else {
            "I'm NewsBot! Use /news or /tech to get updates."
        }
        sendReply(reply, chat.id, repository, signalProtocolManager)
    }
}
