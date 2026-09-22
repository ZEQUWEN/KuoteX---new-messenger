import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

# Add logging when handling WaitingForBotUsername
username_handler = """            is BotFatherState.WaitingForBotUsername -> {
                val username = messageText.replace(" ", "")
                android.util.Log.d("BotFather", "Attempting to register bot with username: $username")
                if (!username.lowercase().endsWith("bot")) {
                    android.util.Log.w("BotFather", "Registration failed: Username $username does not end with 'bot'")
                    sendReplyWithButtons("Sorry, the username must end in 'bot'. Try again.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    return
                }
                if (BotRegistry.getBot(username) != null) {
                    android.util.Log.w("BotFather", "Registration failed: Username $username is already taken")
                    sendReplyWithButtons("Sorry, this username is already taken. Try again.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    return
                }
                
                android.util.Log.i("BotFather", "Registration successful for: $username")
                val newBot = CustomBot(
                    id = username,
                    name = pendingBotName,
                    description = "",
                    category = "Custom",
                    longDescription = "",
                    oauthToken = java.util.UUID.randomUUID().toString().replace("-", "")
                )
                BotRegistry.registerCustomBot(newBot)
                
                // Add to database
                val newChat = Chat(id = newBot.id, title = newBot.name, isBot = true)
                repository.insertChat(newChat)
                
                states[chat.id] = BotFatherState.Idle
                val successMessage = "Done! Congratulations on your new bot. You will find it at t.me/$username. You can now add a description, about section and profile picture for your bot, see /help for a list of commands.\\n\\nUse this token to access the HTTP API:\\n${newBot.oauthToken}\\nKeep your token secure and store it safely, it can be used by anyone to control your bot.\\n\\nFor a description of the Bot API, see this page: https://core.telegram.org/bots/api"
                sendReplyWithButtons(successMessage, listOf("/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }"""

content = re.sub(
    r"is BotFatherState.WaitingForBotUsername -> \{.*?(?=is BotFatherState.ManagingBot -> \{)",
    username_handler.replace('\\n', '\n') + '\n            ',
    content,
    flags=re.DOTALL
)

with open(path, 'w') as f:
    f.write(content)
print("BotFather registration logging added!")
