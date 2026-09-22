import re

with open('/app/src/main/java/com/example/ui/botapi/BotFather.kt', 'r') as f:
    content = f.read()

# Add WaitingForBotSelection state
content = content.replace(
    "object WaitingForBotUsername : BotFatherState()",
    "object WaitingForBotUsername : BotFatherState()\n        data class WaitingForBotSelection(val command: String) : BotFatherState()"
)

# Update /start and /help text
content = content.replace(
    "/newbot - create a new bot\\n/mybots - edit your bots",
    "/newbot - create a new bot\\n/mybots - edit your bots\\n\\nEdit Bots\\n/setname - change a bot's name\\n/setdescription - change bot description\\n/setabouttext - change bot about info\\n/setuserpic - change bot profile photo\\n/setcommands - change the list of commands\\n/deletebot - delete a bot"
)

# Handle commands in Idle state
idle_replacement = """                    "/mybots", "manage bots" -> {
                        val customBots = BotRegistry.getCustomBots()
                        if (customBots.isEmpty()) {
                            sendReplyWithButtons("You don't have any bots yet. Create one with '/newbot'.", listOf("/newbot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                        } else {
                            val buttons = customBots.map { "@${it.id}" }
                            sendReplyWithButtons("Choose a bot from the list below:", buttons, chat.id, repository, signalProtocolManager, messageIdToEdit)
                        }
                    }
                    "/setname", "/setdescription", "/setabouttext", "/setuserpic", "/setcommands", "/deletebot" -> {
                        val customBots = BotRegistry.getCustomBots()
                        if (customBots.isEmpty()) {
                            sendReplyWithButtons("You don't have any bots yet. Create one with '/newbot'.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                        } else {
                            val cmd = messageText.lowercase().trim()
                            states[chat.id] = BotFatherState.WaitingForBotSelection(cmd)
                            val buttons = customBots.map { "@${it.id}" }
                            sendReplyWithButtons("Choose a bot to change its settings:", buttons, chat.id, repository, signalProtocolManager, messageIdToEdit)
                        }
                    }"""

content = content.replace(
    '                    "/mybots", "manage bots" -> {\n                        val customBots = BotRegistry.getCustomBots()\n                        if (customBots.isEmpty()) {\n                            sendReplyWithButtons("You don\'t have any bots yet. Create one with \'/newbot\'.", listOf("/newbot"), chat.id, repository, signalProtocolManager, messageIdToEdit)\n                        } else {\n                            val buttons = customBots.map { "@${it.id}" }\n                            sendReplyWithButtons("Choose a bot from the list below:", buttons, chat.id, repository, signalProtocolManager, messageIdToEdit)\n                        }\n                    }',
    idle_replacement
)

# Handle WaitingForBotSelection state
bot_selection_handler = """            is BotFatherState.WaitingForBotSelection -> {
                val username = messageText.removePrefix("@").trim()
                val customBot = BotRegistry.getCustomBots().find { it.id == username }
                if (customBot == null) {
                    sendReplyWithButtons("Invalid bot. Please choose a bot from the list below:", BotRegistry.getCustomBots().map { "@${it.id}" }, chat.id, repository, signalProtocolManager, messageIdToEdit)
                    return
                }
                
                when (state.command) {
                    "/setname" -> {
                        states[chat.id] = BotFatherState.WaitingForEditName(customBot.id)
                        sendReplyWithButtons("OK. Send me the new name for your bot.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/setdescription" -> {
                        states[chat.id] = BotFatherState.WaitingForEditDescription(customBot.id)
                        sendReplyWithButtons("OK. Send me the new description for the bot. People will see this description when they open a chat with your bot.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/setabouttext" -> {
                        states[chat.id] = BotFatherState.WaitingForEditAbout(customBot.id)
                        sendReplyWithButtons("OK. Send me the new 'About' text. People will see this text on the bot's profile page.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/setuserpic" -> {
                        states[chat.id] = BotFatherState.WaitingForEditBotpic(customBot.id)
                        sendReplyWithButtons("OK. Send me the new profile photo for the bot.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/setcommands" -> {
                        states[chat.id] = BotFatherState.WaitingForEditCommands(customBot.id)
                        sendReplyWithButtons("OK. Send me a list of commands for your bot. Please use this format:\\n\\ncommand1 - Description\\ncommand2 - Another description", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/deletebot" -> {
                        BotRegistry.unregisterCustomBot(customBot.id)
                        repository.deleteChat(customBot.id)
                        states[chat.id] = BotFatherState.Idle
                        sendReplyWithButtons("Bot deleted.", listOf("/newbot", "/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                }
            }
            is BotFatherState.WaitingForBotName -> {"""

content = content.replace(
    "            is BotFatherState.WaitingForBotName -> {",
    bot_selection_handler
)

with open('/app/src/main/java/com/example/ui/botapi/BotFather.kt', 'w') as f:
    f.write(content)
print("BotFather.kt patched!")
