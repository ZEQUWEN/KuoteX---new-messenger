package com.example.ui.botapi

import com.example.api.BotApiClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope

import com.example.crypto.SignalProtocolManager
import com.example.data.MessengerRepository
import com.example.ui.Chat
import com.example.ui.Message
import com.example.utils.MessageSanitizer
import java.util.UUID

class BotFather : Bot {
    override val id: String = "botfather"
    override val name: String = "BotFather"
    override val description: String = "BotFather is the one bot to rule them all."
    override val category: String = "Utility"
    override val longDescription: String = "BotFather is the one bot to rule them all. Use it to create new bot accounts and manage your existing bots."
    override val commands: List<BotCommand> = listOf(
        BotCommand("/newbot", "create a new bot"),
        BotCommand("/mybots", "edit your bots")
    )

    private val states = mutableMapOf<String, BotFatherState>()

    sealed class BotFatherState {
        object Idle : BotFatherState()
        object WaitingForBotName : BotFatherState()
        object WaitingForBotUsername : BotFatherState()
        data class WaitingForBotSelection(val command: String) : BotFatherState()
        data class ManagingBot(val botId: String) : BotFatherState()
        data class EditingBot(val botId: String) : BotFatherState()
        data class WaitingForEditName(val botId: String) : BotFatherState()
        data class WaitingForEditAbout(val botId: String) : BotFatherState()
        data class WaitingForEditDescription(val botId: String) : BotFatherState()
        data class WaitingForEditCommands(val botId: String) : BotFatherState()
        data class WaitingForEditBotpic(val botId: String) : BotFatherState()
        data class WaitingForEditDescriptionPicture(val botId: String) : BotFatherState()
        data class BotSettings(val botId: String) : BotFatherState()
        data class ApiToken(val botId: String) : BotFatherState()
        data class Payments(val botId: String) : BotFatherState()
        data class TransferOwnership(val botId: String) : BotFatherState()
        data class WaitingForDomain(val botId: String) : BotFatherState()
        data class WaitingForMiniAppUrl(val botId: String) : BotFatherState()
        data class WaitingForMiniAppTitle(val botId: String) : BotFatherState()
        data class WaitingForMiniAppShortName(val botId: String) : BotFatherState()
        data class WaitingForPaymentProvider(val botId: String, val providerName: String) : BotFatherState()
        data class WaitingForWebhookUrl(val botId: String) : BotFatherState()
    }

    private var pendingBotName: String = ""

    override suspend fun onCallbackQuery(
        callbackData: String,
        messageId: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        handleInput(callbackData, chat, repository, signalProtocolManager, messageId)
    }

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        handleInput(messageText, chat, repository, signalProtocolManager, null)
    }

    private suspend fun handleInput(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager,
        messageIdToEdit: String?
    ) {
        val state = states[chat.id] ?: BotFatherState.Idle

        when (state) {
            is BotFatherState.Idle -> {
                when (messageText.lowercase().trim()) {
                    "/start", "/help" -> {
                        sendReplyWithButtons("I can help you create and manage Telegram bots. If you're new to the Bot API, please see the manual.\n\nYou can control me by sending these commands:\n\n/newbot - create a new bot\n/mybots - edit your bots\n\nEdit Bots\n/setname - change a bot's name\n/setdescription - change bot description\n/setabouttext - change bot about info\n/setuserpic - change bot profile photo\n/setcommands - change the list of commands\n/deletebot - delete a bot", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/newbot", "create bot" -> {
                        states[chat.id] = BotFatherState.WaitingForBotName
                        sendReplyWithButtons("Alright, a new bot. How are we going to call it? Please choose a name for your bot.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/diagnostics" -> {
                        val customBots = BotRegistry.getCustomBots()
                        val diagnosticLog = StringBuilder()
                        diagnosticLog.append("--- BotFather Diagnostic Report ---\n")
                        diagnosticLog.append("Total Custom Bots: ${customBots.size}\n\n")
                        customBots.forEach { bot ->
                            val cBot = bot as? CustomBot
                            if (cBot != null) {
                                diagnosticLog.append("Bot ID: @${cBot.id}\n")
                                diagnosticLog.append("Name Sync: ${if (cBot.name.isNotBlank()) "OK" else "FAIL (Missing)"}\n")
                                diagnosticLog.append("Desc Sync: ${if (cBot.description.isNotBlank()) "OK" else "FAIL (Missing)"}\n")
                                diagnosticLog.append("About Sync: ${if (cBot.about.isNotBlank()) "OK" else "FAIL (Missing)"}\n")
                                diagnosticLog.append("Profile Pic Sync: ${if (cBot.botPicUri != null) "OK (${cBot.botPicUri})" else "FAIL (Missing)"}\n")
                                diagnosticLog.append("Desc Pic Sync: ${if (cBot.descriptionPictureUri != null) "OK (${cBot.descriptionPictureUri})" else "FAIL (Missing)"}\n")
                                diagnosticLog.append("-------------------------\n")
                            }
                        }
                        
                        sendReplyWithButtons(diagnosticLog.toString(), listOf("/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/debug" -> {
                        val customBots = BotRegistry.getCustomBots()
                        val info = customBots.joinToString("\n\n") { bot ->
                            val cBot = bot as? CustomBot
                            "ID: ${bot.id}\nName: ${bot.name}\nPic: ${cBot?.botPicUri}\nDescPic: ${cBot?.descriptionPictureUri}"
                        }
                        val finalInfo = if (info.isBlank()) "No custom bots." else info
                        sendReplyWithButtons("DEBUG INFO:\n$finalInfo", listOf("/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/mybots", "manage bots" -> {
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
                    }
                    else -> {
                        val username = messageText.removePrefix("@").trim()
                        val customBot = BotRegistry.getCustomBots().find { it.id == username }
                        if (customBot != null) {
                            states[chat.id] = BotFatherState.ManagingBot(customBot.id)
                            sendBotManagementMenu(customBot as CustomBot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                        } else {
                            sendReplyWithButtons(
                                "I am the BotFather. You can create new bots and manage them here.",
                                listOf("/newbot", "/mybots"),
                                chat.id, repository, signalProtocolManager
                            )
                        }
                    }
                }
            }
            is BotFatherState.WaitingForBotSelection -> {
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
                        sendReplyWithButtons("OK. Send me a list of commands for your bot. Please use this format:\n\ncommand1 - Description\ncommand2 - Another description", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/deletebot" -> {
                        BotRegistry.unregisterCustomBot(customBot.id)
                        repository.deleteChat(customBot.id)
                        states[chat.id] = BotFatherState.Idle
                        sendReplyWithButtons("Bot deleted.", listOf("/newbot", "/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                }
            }
            is BotFatherState.WaitingForBotName -> {
                pendingBotName = messageText
                states[chat.id] = BotFatherState.WaitingForBotUsername
                sendReplyWithButtons("Good. Now let's choose a username for your bot. It must end in 'bot'. Like this: TetrisBot or tetris_bot.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
                        is BotFatherState.WaitingForBotUsername -> {
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
                val newChat = Chat(id = newBot.id, title = newBot.name, isBot = true, lastMessage = "")
                repository.insertChat(newChat)
                
                states[chat.id] = BotFatherState.Idle
                val successMessage = "Done! Congratulations on your new bot. You will find it at t.me/$username. You can now add a description, about section and profile picture for your bot, see /help for a list of commands.\n\nUse this token to access the HTTP API:\n${newBot.oauthToken}\nKeep your token secure and store it safely, it can be used by anyone to control your bot.\n\nFor a description of the Bot API, see this page: https://core.telegram.org/bots/api"
                sendReplyWithButtons(successMessage, listOf("/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
            is BotFatherState.ManagingBot -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot
                if (bot == null) {
                    states[chat.id] = BotFatherState.Idle
                    return
                }
                when (messageText) {
                    "API Token" -> {
                        states[chat.id] = BotFatherState.ApiToken(bot.id)
                        val msg = "Here is the token for bot ${bot.name} @${bot.id}:\n${bot.oauthToken}\nKeep your token secure and store it safely."
                        sendReplyWithButtons(msg, listOf("Revoke current token", "« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Edit Bot" -> {
                        states[chat.id] = BotFatherState.EditingBot(bot.id)
                        sendEditBotMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Payments" -> {
                        states[chat.id] = BotFatherState.Payments(bot.id)
                        sendReplyWithButtons("Choose a payment provider for @${bot.id}:", listOf("YooKassa", "Stripe", "DonationAlerts", "Ko-Fi", "« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Transfer Ownership" -> {
                        states[chat.id] = BotFatherState.TransferOwnership(bot.id)
                        sendReplyWithButtons("To transfer ownership, please send the username of the new owner.", listOf("« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Bot Settings" -> {
                        states[chat.id] = BotFatherState.BotSettings(bot.id)
                        val msg = "Settings for @${bot.id}."
                        val btns = listOf("Inline Mode", "Webhook", "Payments", "Domain", "Menu Button", "Configure Mini App", "« Back to Bot")
                        sendReplyWithButtons(msg, btns, chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Delete Bot" -> {
                        BotRegistry.unregisterCustomBot(bot.id)
                        repository.deleteChat(bot.id)
                        states[chat.id] = BotFatherState.Idle
                        sendReplyWithButtons("Bot deleted.", listOf("/newbot", "/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "« Back to Bot List" -> {
                        states[chat.id] = BotFatherState.Idle
                        val customBots = BotRegistry.getCustomBots()
                        val buttons = customBots.map { "@${it.id}" }
                        sendReplyWithButtons("Choose a bot from the list below:", buttons, chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    else -> sendBotManagementMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.ApiToken -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                when (messageText) {
                    "Revoke current token" -> {
                        bot.oauthToken = "${bot.id}:${UUID.randomUUID().toString().replace("-", "").take(35)}"
                        sendReplyWithButtons("Token has been revoked. New token:\n${bot.oauthToken}", listOf("Revoke current token", "« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "« Back to Bot" -> {
                        states[chat.id] = BotFatherState.ManagingBot(bot.id)
                        sendBotManagementMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                }
            }
            is BotFatherState.BotSettings -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                when (messageText) {
                    "« Back to Bot" -> {
                        states[chat.id] = BotFatherState.ManagingBot(bot.id)
                        sendBotManagementMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Menu Button" -> {
                        sendReplyWithButtons("Web login is currently unavailable for ${bot.name} @${bot.id}.", listOf("Set domain", "« Back to Settings"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "« Back to Settings" -> {
                        val msg = "Settings for @${bot.id}."
                        val btns = listOf("Inline Mode", "Webhook", "Payments", "Domain", "Menu Button", "Configure Mini App", "« Back to Bot")
                        sendReplyWithButtons(msg, btns, chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Inline Mode" -> {
                        sendReplyWithButtons("Inline mode is currently disabled for ${bot.name} @${bot.id}.", listOf("Turn on", "« Back to Settings"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Payments" -> {
                        states[chat.id] = BotFatherState.Payments(bot.id)
                        sendReplyWithButtons("Choose a payment provider for @${bot.id}:", listOf("YooKassa", "Stripe", "DonationAlerts", "Ko-Fi", "« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Webhook" -> {
                        states[chat.id] = BotFatherState.WaitingForWebhookUrl(bot.id)
                        sendReplyWithButtons("Send me the new Webhook URL for your bot (e.g. https://api.kuotex.msg/webhook):", listOf("« Back to Settings"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Domain" -> {
                        states[chat.id] = BotFatherState.WaitingForDomain(bot.id)
                        sendReplyWithButtons("Send me the new domain name for your bot (e.g. example.com):", listOf("« Back to Settings"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Configure Mini App" -> {
                        states[chat.id] = BotFatherState.WaitingForMiniAppUrl(bot.id)
                        sendReplyWithButtons("Send me the Web App URL for your Mini App (e.g. https://yourapp.com/):", listOf("« Back to Settings"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                }
            }
            is BotFatherState.EditingBot -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                when (messageText) {
                    "Edit Name" -> {
                        states[chat.id] = BotFatherState.WaitingForEditName(bot.id)
                        sendReplyWithButtons("OK. Send me the new name for your bot.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Edit About" -> {
                        states[chat.id] = BotFatherState.WaitingForEditAbout(bot.id)
                        sendReplyWithButtons("OK. Send me the new 'About' text. People will see this text on the bot's profile page.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Edit Description" -> {
                        states[chat.id] = BotFatherState.WaitingForEditDescription(bot.id)
                        sendReplyWithButtons("OK. Send me the new description for the bot. People will see this description when they open a chat with your bot.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Edit Botpic" -> {
                        states[chat.id] = BotFatherState.WaitingForEditBotpic(bot.id)
                        sendReplyWithButtons("OK. Send me the new profile photo for the bot.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Edit Description Picture" -> {
                        states[chat.id] = BotFatherState.WaitingForEditDescriptionPicture(bot.id)
                        sendReplyWithButtons("OK. Send me the new description picture.", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "Edit Commands" -> {
                        states[chat.id] = BotFatherState.WaitingForEditCommands(bot.id)
                        sendReplyWithButtons("OK. Send me a list of commands for your bot. Please use this format:\n\ncommand1 - Description\ncommand2 - Another description", emptyList(), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "« Back to Bot" -> {
                        states[chat.id] = BotFatherState.ManagingBot(bot.id)
                        sendBotManagementMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    else -> sendEditBotMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.WaitingForEditName -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                bot.name = messageText
                repository.insertChat(Chat(id = bot.id, title = bot.name, isBot = true, lastMessage = bot.description))
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Name updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
                        is BotFatherState.WaitingForEditAbout -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                bot.about = if (messageText.length > 120) messageText.substring(0, 120) else messageText
                android.util.Log.d("BotFather", "Received About text: ${bot.about}")
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! About section updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
            is BotFatherState.WaitingForEditDescription -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                bot.description = messageText
                android.util.Log.d("BotFather", "Received Description: ${bot.description}")
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Description updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
            is BotFatherState.WaitingForEditBotpic -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                android.util.Log.d("BotFather", "Received BotPic URI: $messageText")
                bot.botPicUri = messageText // Usually it's URI sent as text in mock or file upload handling
                
                // Trigger background sync with exponential backoff
                GlobalScope.launch {
                    val token = bot.oauthToken ?: "dummy_token"
                    BotApiClient.syncBotProfileWithBackoff(
                        token = token,
                        name = bot.name,
                        description = bot.description,
                        about = bot.about,
                        botPicUrl = bot.botPicUri
                    )
                }
                
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Profile photo updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
            is BotFatherState.WaitingForEditDescriptionPicture -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                android.util.Log.d("BotFather", "Received DescriptionPicture URI: $messageText")
                bot.descriptionPictureUri = messageText
                
                // Trigger background sync with exponential backoff
                GlobalScope.launch {
                    val token = bot.oauthToken ?: "dummy_token"
                    BotApiClient.syncBotProfileWithBackoff(
                        token = token,
                        name = bot.name,
                        description = bot.description,
                        about = bot.about,
                        botPicUrl = bot.botPicUri // assuming endpoint handles both or we pass both
                    )
                }
                
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Description picture updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
            is BotFatherState.WaitingForEditCommands -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                val parsedCommands = messageText.lines().mapNotNull { line ->
                    val parts = line.split(" - ", limit = 2)
                    if (parts.size == 2) BotCommand(parts[0].trim(), parts[1].trim()) else null
                }
                bot.customCommands = parsedCommands
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Command list updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
            is BotFatherState.Payments -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                if (messageText == "« Back to Bot") {
                    states[chat.id] = BotFatherState.ManagingBot(bot.id)
                    sendBotManagementMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else if (messageText in listOf("YooKassa", "Stripe", "DonationAlerts", "Ko-Fi")) {
                    states[chat.id] = BotFatherState.WaitingForPaymentProvider(bot.id, messageText)
                    val hint = if (messageText in listOf("YooKassa", "Stripe")) "provider token" else "API Key / Webhook Secret"
                    sendReplyWithButtons("You selected $messageText. Please send the $hint:", listOf("« Back to Payments"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else {
                    states[chat.id] = BotFatherState.ManagingBot(bot.id)
                    sendBotManagementMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.TransferOwnership -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                if (messageText == "« Back to Bot") {
                    states[chat.id] = BotFatherState.ManagingBot(bot.id)
                    sendBotManagementMenu(bot, chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else {
                    states[chat.id] = BotFatherState.ManagingBot(bot.id)
                    sendReplyWithButtons("Ownership of @${bot.id} has been transferred to $messageText.", listOf("« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.WaitingForWebhookUrl -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                if (messageText == "« Back to Settings") {
                    states[chat.id] = BotFatherState.BotSettings(bot.id)
                    val msg = "Settings for @${bot.id}."
                    val btns = listOf("Inline Mode", "Webhook", "Payments", "Domain", "Menu Button", "Configure Mini App", "« Back to Bot")
                    sendReplyWithButtons(msg, btns, chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else {
                    bot.webhookUrl = messageText
                    states[chat.id] = BotFatherState.BotSettings(bot.id)
                    sendReplyWithButtons("Webhook updated to $messageText.", listOf("« Back to Settings", "« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.WaitingForDomain -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                if (messageText == "« Back to Settings") {
                    states[chat.id] = BotFatherState.BotSettings(bot.id)
                    val msg = "Settings for @${bot.id}."
                    val btns = listOf("Inline Mode", "Webhook", "Payments", "Domain", "Menu Button", "Configure Mini App", "« Back to Bot")
                    sendReplyWithButtons(msg, btns, chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else {
                    bot.domain = messageText
                    states[chat.id] = BotFatherState.BotSettings(bot.id)
                    sendReplyWithButtons("Domain updated to $messageText.", listOf("« Back to Settings", "« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.WaitingForMiniAppUrl -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                if (messageText == "« Back to Settings") {
                    states[chat.id] = BotFatherState.BotSettings(bot.id)
                    val msg = "Settings for @${bot.id}."
                    val btns = listOf("Inline Mode", "Webhook", "Payments", "Domain", "Menu Button", "Configure Mini App", "« Back to Bot")
                    sendReplyWithButtons(msg, btns, chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else {
                    bot.miniAppUrl = messageText
                    states[chat.id] = BotFatherState.WaitingForMiniAppShortName(bot.id)
                    sendReplyWithButtons("URL saved. Now send me a short name for the Mini App (e.g. myapp):", listOf("« Back to Settings"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.WaitingForMiniAppShortName -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                if (messageText == "« Back to Settings") {
                    states[chat.id] = BotFatherState.BotSettings(bot.id)
                    val msg = "Settings for @${bot.id}."
                    val btns = listOf("Inline Mode", "Webhook", "Payments", "Domain", "Menu Button", "Configure Mini App", "« Back to Bot")
                    sendReplyWithButtons(msg, btns, chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else {
                    bot.miniAppShortName = messageText
                    states[chat.id] = BotFatherState.WaitingForMiniAppTitle(bot.id)
                    sendReplyWithButtons("Short name saved. Now send me a Title for the Mini App:", listOf("« Back to Settings"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.WaitingForMiniAppTitle -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                if (messageText == "« Back to Settings") {
                    states[chat.id] = BotFatherState.BotSettings(bot.id)
                    val msg = "Settings for @${bot.id}."
                    val btns = listOf("Inline Mode", "Webhook", "Payments", "Domain", "Menu Button", "Configure Mini App", "« Back to Bot")
                    sendReplyWithButtons(msg, btns, chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else {
                    bot.miniAppTitle = messageText
                    states[chat.id] = BotFatherState.BotSettings(bot.id)
                    sendReplyWithButtons("Mini App configured successfully! Link: https://t.me/${bot.id}/${bot.miniAppShortName}", listOf("« Back to Settings", "« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
            is BotFatherState.WaitingForPaymentProvider -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                if (messageText == "« Back to Payments") {
                    states[chat.id] = BotFatherState.Payments(bot.id)
                    sendReplyWithButtons("Choose a payment provider for @${bot.id}:", listOf("YooKassa", "Stripe", "DonationAlerts", "Ko-Fi", "« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                } else {
                    bot.paymentProviders[state.providerName] = messageText
                    states[chat.id] = BotFatherState.ManagingBot(bot.id)
                    sendReplyWithButtons("${state.providerName} token saved successfully.", listOf("« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
        }
        
        BotRegistry.saveCustomBots() // Persist any changes made during this interaction
    }

    private suspend fun sendBotManagementMenu(
        bot: CustomBot,
        chatId: String,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager,
        messageIdToEdit: String? = null
    ) {
        val menu = "Here it is: ${bot.name} @${bot.id}.\nWhat do you want to do with the bot?"
        val buttons = listOf(
            "API Token", "Edit Bot", "Bot Settings", "Payments", "Transfer Ownership", "Delete Bot", "« Back to Bot List"
        )
        sendReplyWithButtons(menu, buttons, chatId, repository, signalProtocolManager, messageIdToEdit)
    }
    
    private suspend fun sendEditBotMenu(
        bot: CustomBot,
        chatId: String,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager,
        messageIdToEdit: String? = null
    ) {
        val msg = "Edit @${bot.id} info.\n\nName: ${bot.name}\nAbout: ${if(bot.about.isNullOrBlank()) "🚫" else bot.about}\nDescription: ${if(bot.description.isNullOrBlank()) "🚫" else bot.description}\nDescription picture: ${if(bot.descriptionPictureUri.isNullOrBlank()) "🚫 no description picture" else "✅"}\nBotpic: ${if(bot.botPicUri.isNullOrBlank()) "🚫 no botpic" else "✅"}\nCommands: ${if(bot.customCommands.isEmpty()) "no commands yet" else "✅"}\nPrivacy Policy: 🚫"
        val btns = listOf("Edit Name", "Edit About", "Edit Description", "Edit Description Picture", "Edit Botpic", "Edit Commands", "Edit Privacy Policy", "« Back to Bot")
        sendReplyWithButtons(msg, btns, chatId, repository, signalProtocolManager, messageIdToEdit)
    }

    private suspend fun sendReplyWithButtons(
        text: String,
        buttons: List<String>,
        chatId: String,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager,
        messageIdToEdit: String? = null
    ) {
        val sanitizedText = MessageSanitizer.sanitize(text)
        val encryptedReply = signalProtocolManager.encryptMessage(sanitizedText)
        val buttonsData = if (buttons.isNotEmpty()) buttons.joinToString("||") else null
        
        if (messageIdToEdit != null) {
            val existingMsg = repository.getMessageById(messageIdToEdit)
            if (existingMsg != null) {
                repository.updateMessage(existingMsg.copy(text = encryptedReply, buttonsData = buttonsData))
                return
            }
        }

        val replyMsg = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "bot_$id",
            text = encryptedReply,
            buttonsData = buttonsData,
            timestamp = System.currentTimeMillis()
        )
        repository.insertMessageAndUpdateChat(replyMsg, sanitizedText, name)
    }
}


class CustomBot(
    override var id: String,
    override var name: String,
    override var description: String,
    override var category: String,
    override var longDescription: String,
    override var commands: List<BotCommand> = emptyList(),
    var oauthToken: String? = null,
    var webhookUrl: String? = null,
    var rateLimit: Int = 100,
    var code: String = "fun handleMessage(msg: String): String {\n    return \"Echo from \$name: \$msg\"\n}",
    var snapshots: MutableList<CodeSnapshot> = mutableListOf(),
    var collaborators: MutableList<Collaborator> = mutableListOf(),
    var stats: BotStats = BotStats(),
    val logs: MutableList<LogEntry> = mutableListOf(),
    var about: String = "",
    var botPicUri: String? = null,
    var descriptionPictureUri: String? = null,
    var keyboardLayoutConfig: String? = null,
    var customCommands: List<BotCommand> = emptyList(),
    var paymentProviderToken: String? = null, // Deprecated, use paymentProviders
    val paymentProviders: MutableMap<String, String> = mutableMapOf(), // e.g. "Stripe" -> "token"
    var paymentsEnabled: Boolean = true,
    var domain: String? = null,
    var miniAppUrl: String? = null,
    var miniAppTitle: String? = null,
    var miniAppShortName: String? = null
) : Bot {
    override suspend fun onCallbackQuery(
        callbackData: String,
        messageId: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        handleInput(callbackData, chat, repository, signalProtocolManager, messageId)
    }

    override suspend fun onMessageReceived(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager
    ) {
        handleInput(messageText, chat, repository, signalProtocolManager, null)
    }

    private suspend fun handleInput(
        messageText: String,
        chat: Chat,
        repository: MessengerRepository,
        signalProtocolManager: SignalProtocolManager,
        messageIdToEdit: String?
    ) {
        stats.totalMessages++
        stats.activeUsers = (stats.activeUsers + 1).coerceAtMost(100) // Simulated unique users
        stats.interactionRate = if (stats.activeUsers > 0) stats.totalMessages.toFloat() / stats.activeUsers else 0f
        
        logs.add(LogEntry(message = "Received message from ${chat.id}: $messageText"))
        
        if (!webhookUrl.isNullOrBlank()) {
            logs.add(LogEntry(message = "Dispatching to webhook: $webhookUrl"))
            try {
                val responseText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val url = java.net.URL(webhookUrl)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    
                    val payload = "{ \"update_type\": \"message\", \"bot_id\": \"$id\", \"message\": { \"text\": \"$messageText\", \"chat_id\": \"${chat.id}\" } }"
                    
                    conn.outputStream.use { os ->
                        val input = payload.toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }
                    
                    val responseCode = conn.responseCode
                    if (responseCode in 200..299) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        null
                    }
                }
                logs.add(LogEntry(message = "Webhook dispatched successfully."))
                if (!responseText.isNullOrBlank()) {
                    var reply = responseText
                    if (responseText!!.contains("\"text\":")) {
                        val textMatch = "\"text\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(responseText!!)
                        if (textMatch != null) {
                            reply = textMatch.groupValues[1]
                        }
                    }
                    logs.add(LogEntry(message = "Webhook reply: $reply"))
                    sendReply(reply, chat.id, repository, signalProtocolManager)
                }
                return
            } catch (e: Exception) {
                logs.add(LogEntry(level = "ERROR", message = "Webhook failed: ${e.message}"))
            }
        }
        // Simple echo for custom bots. In a real system, this would evaluate `code` or call `webhookUrl`.
        val reply = "Echo from $name: $messageText"
        logs.add(LogEntry(message = "Replied: $reply"))
        sendReply(reply, chat.id, repository, signalProtocolManager)
    }
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO",
    val message: String
)

data class BotStats(
    var activeUsers: Int = 0,
    var totalMessages: Int = 0,
    var interactionRate: Float = 0f
)

data class CodeSnapshot(
    val version: Int,
    val code: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class Collaborator(
    val username: String,
    val role: String
)
