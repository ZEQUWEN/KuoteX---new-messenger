package com.example.ui.botapi

import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.Chat
import kotlinx.coroutines.delay

class EchoBot : Bot {
    override val id = "b4"
    override val name = "EchoBot"
    override val description = "I repeat what you say."
    override val category = "Utilities"
    override val longDescription = "A simple bot that echoes back whatever message you send it. Useful for testing connectivity."
    override val commands = emptyList<BotCommand>()

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        delay(800) // Simulate processing delay
        val cleanText = messageText.replace("@EchoBot", "", ignoreCase = true).trim()
        val reply = if (cleanText.isEmpty()) "You didn't say anything!" else "You said: $cleanText"
        sendReply(reply, chat.id, repository, signalProtocolManager)
    }
}
