# Neon Messenger Bot API

This repository includes a comprehensive, native Bot API for the Android Messenger platform. It enables developers to easily create and integrate bots that interact with users both in direct messages and in group chats.

## Architecture

The Bot API consists of the following core components:

*   **`Bot` Interface**: The contract that all bots must implement.
*   **`BotRegistry`**: A central manager where all bots are registered.
*   **`BotService`**: The router that directs incoming messages to the appropriate registered bot.

## Creating a Bot

To create a new bot, simply implement the `Bot` interface.

```kotlin
import com.example.ui.botapi.Bot
import com.example.ui.Chat
import com.example.data.MessengerRepository
import com.example.crypto.SignalProtocolManager

class MyCustomBot : Bot {
    override val id = "my_custom_bot_id"
    override val name = "MyCustomBot"
    override val description = "Short description of what the bot does."
    override val category = "Utilities"
    override val longDescription = "A longer description explaining the bot's features in detail."
    override val commands = listOf(
        BotCommand("/hello", "Says hello")
    )

    override suspend fun onJoinedGroup(
        chat: Chat, 
        repository: MessengerRepository, 
        signalProtocolManager: SignalProtocolManager
    ) {
        // Called when the bot is added to a group or channel.
        sendReply("Hello everyone! I'm ${name}, ready to help.", chat.id, repository, signalProtocolManager)
    }

    override suspend fun onLeftGroup(
        chat: Chat, 
        repository: MessengerRepository, 
        signalProtocolManager: SignalProtocolManager
    ) {
        // Called when the bot is removed from a group or channel.
    }

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        // 1. Process the incoming messageText and respond to commands
        val responseText = if (messageText.startsWith("/hello")) {
            "Hello there, human!"
        } else {
            "You said: $messageText"
        }
        
        // 2. Send a reply back to the chat using the helper method
        sendReply(responseText, chat.id, repository, signalProtocolManager)
    }
}
```

## Registering the Bot

Once you have created your bot class, register it in `BotRegistry.kt` so it becomes available in the app.

```kotlin
object BotRegistry {
    // ...
    init {
        registerBot(WeatherBot())
        registerBot(EchoBot())
        registerBot(MyCustomBot()) // Add your bot here
    }
    // ...
}
```

## How It Works

*   **Direct Messages**: When a user chats with a bot directly, the `BotService` intercepts the message and routes it to the specific bot's `onMessageReceived` method.
*   **Group Chats**: When a user mentions a bot by name (e.g., `@WeatherBot`) in a group chat, the `BotService` detects the mention and forwards the message to that bot. The bot can then reply directly in the group.
*   **Commands**: Bots can expose a list of commands via the `commands` property. These are standardized actions a user can invoke.
*   **Group Lifecycle Hooks**: Override `onJoinedGroup` and `onLeftGroup` to respond when your bot is added to or removed from a conversation.
*   **Simulated Asynchrony**: Because this is a demonstration, bots may use `delay()` in their handlers to simulate network calls to external APIs.

## Webhooks

The Bot API supports receiving real-time updates from the messenger server via webhooks. This is useful for integrating your bot with an external backend.

To configure a webhook:
1. Talk to **@BotFather** in the app.
2. Select your bot and navigate to the **Webhook** setting.
3. Enter your HTTPS URL (e.g., `https://your-server.com/webhook`).

When a message is received by the bot, the messenger server will send an HTTP POST request to your configured webhook URL with a JSON payload:

```json
{
  "update_type": "message",
  "bot_id": "your_bot_id",
  "message": {
    "text": "Hello bot!",
    "chat_id": "user_id_123"
  }
}
```

If your server responds with an HTTP `200 OK` status code and a JSON body containing a `text` field, the bot will automatically reply with that text:

```json
{
  "text": "Hello human!"
}
```

If no webhook is configured or the webhook fails, the bot will fall back to its internal evaluation logic.

## Included Examples

We have included several example bots demonstrating different capabilities:

1.  **`EchoBot`**: A simple bot that demonstrates receiving a message and echoing it back.
2.  **`WeatherBot`**: Demonstrates simulated fetching of external API data (weather conditions) based on keywords.
3.  **`CryptoBot`**: Demonstrates command parsing (e.g., `/price BTC`).
4.  **`ReminderBot`**: Demonstrates a simple utility interaction.

These examples can be found in `app/src/main/java/com/example/ui/botapi/`.
