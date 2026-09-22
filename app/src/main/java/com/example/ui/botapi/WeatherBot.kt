package com.example.ui.botapi

import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.Chat
import kotlinx.coroutines.delay

class WeatherBot : Bot {
    override val id = "b1"
    override val name = "WeatherBot"
    override val description = "Get daily weather updates."
    override val category = "Utilities"
    override val longDescription = "WeatherBot provides simulated real-time weather forecasts. Ask about the weather!"
    override val commands = listOf(BotCommand("/weather", "Get current weather"), BotCommand("/forecast", "Get weather forecast"))

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        delay(1200) // Simulate network call to external weather API
        
        val text = messageText.lowercase()
        val reply = if (text.contains("rain")) {
            "It looks like rain today! Don't forget your umbrella. 🌧️"
        } else if (text.contains("sun")) {
            "It's going to be a beautiful sunny day! ☀️"
        } else if (text.contains("snow")) {
            "Expect heavy snowfall this evening. Stay warm! ❄️"
        } else {
            "The weather is currently 72°F and partly cloudy. ⛅"
        }
        
        sendReply(reply, chat.id, repository, signalProtocolManager)
    }
}
