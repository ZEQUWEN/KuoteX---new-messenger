package com.example.ui.botapi

import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.Chat
import kotlinx.coroutines.delay

class ReminderBot : Bot {
    override val id = "b2"
    override val name = "ReminderBot"
    override val description = "I can remind you of things."
    override val category = "Productivity"
    override val longDescription = "Set timers and reminders using natural language. For example: '@ReminderBot remind me to call Mom in 2 hours'."
    override val commands = listOf(BotCommand("/remind", "Set a reminder (e.g., /remind me to call Mom)"))

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        delay(500)
        sendReply("I have set a reminder for you! ⏰", chat.id, repository, signalProtocolManager)
    }
}
