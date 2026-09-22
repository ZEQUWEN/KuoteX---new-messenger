package com.example.ui.botapi

import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.Chat
import kotlinx.coroutines.delay

class CryptoBot : Bot {
    override val id = "b5"
    override val name = "CryptoBot"
    override val description = "Track crypto prices."
    override val category = "Finance"
    override val longDescription = "Send a command like /price BTC or /price ETH to get the latest simulated cryptocurrency prices."
    override val commands = listOf(BotCommand("/price", "Get price of a crypto (e.g., /price btc)"))

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        delay(600) // Simulate fetching from external exchange API
        val text = messageText.trim()
        val reply = if (text.startsWith("/price", ignoreCase = true)) {
            val parts = text.split(" ")
            if (parts.size > 1) {
                val symbol = parts[1].uppercase()
                when (symbol) {
                    "BTC" -> "Current price of BTC is $64,320.00 📈"
                    "ETH" -> "Current price of ETH is $3,450.00 📉"
                    "DOGE" -> "Current price of DOGE is $0.15 🐕"
                    "SOL" -> "Current price of SOL is $145.20 📈"
                    else -> "Sorry, I don't track $symbol right now."
                }
            } else {
                "Please specify a coin. Example: /price BTC"
            }
        } else {
            "I can track crypto prices. Try sending: /price BTC"
        }
        sendReply(reply, chat.id, repository, signalProtocolManager)
    }
}
