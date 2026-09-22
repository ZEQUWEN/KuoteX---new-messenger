package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// Модель для хранения состояния редактирования/создания бота
data class BotFatherState(
    val currentMenu: MenuState = MenuState.Idle,
    val selectedBotId: String? = null,
    val pendingInputType: InputType? = null
)

enum class MenuState {
    Idle, BotList, ManagingBot, BotSettings, BotEdit, PaymentsConfig, MiniAppConfig
}

enum class InputType {
    NewBotName, NewBotUsername, EditName, EditAbout, EditDescription, 
    EditCommands, EditBotpic, WebhookUrl, MiniAppUrl, PaymentToken, TransferOwnership
}

data class InlineKeyboardButton(val text: String, val callbackData: String)

class BotFatherViewModel : ViewModel() {
    private val _botList = MutableStateFlow<List<BotModel>>(emptyList())
    val botList: StateFlow<List<BotModel>> = _botList.asStateFlow()

    private val _uiState = MutableStateFlow(BotFatherState())
    val uiState: StateFlow<BotFatherState> = _uiState.asStateFlow()

    private val _inlineKeyboard = MutableStateFlow<List<List<InlineKeyboardButton>>>(emptyList())
    val inlineKeyboard: StateFlow<List<List<InlineKeyboardButton>>> = _inlineKeyboard.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<String>>(emptyList())
    val chatMessages: StateFlow<List<String>> = _chatMessages.asStateFlow()

    fun handleCommand(command: String) {
        addMessage("You: $command")
        when (command) {
            "/start", "/help" -> {
                addMessage("BotFather: I can help you create and manage Telegram bots.\n\n/newbot - create a new bot\n/mybots - edit your bots")
            }
            "/newbot" -> {
                _uiState.value = _uiState.value.copy(pendingInputType = InputType.NewBotName)
                addMessage("BotFather: Alright, a new bot. How are we going to call it? Please choose a name for your bot.")
            }
            "/mybots" -> {
                showBotList()
            }
        }
    }

    fun handleInput(text: String) {
        addMessage("You: $text")
        val state = _uiState.value
        when (state.pendingInputType) {
            InputType.NewBotName -> {
                // Временное сохранение имени
                _uiState.value = state.copy(pendingInputType = InputType.NewBotUsername)
                addMessage("BotFather: Good. Now let's choose a username for your bot. It must end in 'bot'.")
            }
            InputType.NewBotUsername -> {
                createNewBot("Name", text)
                _uiState.value = state.copy(pendingInputType = null)
                addMessage("BotFather: Done! Congratulations on your new bot. Token: 12345:ABCDEF")
            }
            InputType.WebhookUrl -> {
                addMessage("BotFather: Webhook URL successfully set to $text")
                _uiState.value = state.copy(pendingInputType = null)
                showBotSettings(state.selectedBotId!!)
            }
            else -> {}
        }
    }

    fun handleCallbackQuery(data: String) {
        val parts = data.split(":")
        val action = parts[0]
        val botId = parts.getOrNull(1)

        when (action) {
            "manage_bot" -> showManagingMenu(botId!!)
            "bot_settings" -> showBotSettings(botId!!)
            "edit_bot" -> showEditMenu(botId!!)
            "set_webhook" -> {
                _uiState.value = _uiState.value.copy(pendingInputType = InputType.WebhookUrl)
                addMessage("BotFather: Send me the new Webhook URL for your bot.")
            }
            "revoke_token" -> {
                addMessage("BotFather: Token revoked. New token: 98765:ZYXWVU")
            }
        }
    }

    private fun showBotList() {
        val bots = _botList.value
        if (bots.isEmpty()) {
            addMessage("BotFather: You don't have any bots yet.")
        } else {
            val keyboard = bots.map { bot ->
                listOf(InlineKeyboardButton("@${bot.username}", "manage_bot:${bot.id}"))
            }
            _inlineKeyboard.value = keyboard
            addMessage("BotFather: Choose a bot to manage:")
        }
    }

    private fun showManagingMenu(botId: String) {
        _uiState.value = _uiState.value.copy(currentMenu = MenuState.ManagingBot, selectedBotId = botId)
        _inlineKeyboard.value = listOf(
            listOf(InlineKeyboardButton("API Token", "show_token:$botId"), InlineKeyboardButton("Edit Bot", "edit_bot:$botId")),
            listOf(InlineKeyboardButton("Bot Settings", "bot_settings:$botId"), InlineKeyboardButton("Payments", "payments:$botId")),
            listOf(InlineKeyboardButton("Delete Bot", "delete_bot:$botId"))
        )
        addMessage("BotFather: What do you want to do with the bot?")
    }

    private fun showBotSettings(botId: String) {
        _uiState.value = _uiState.value.copy(currentMenu = MenuState.BotSettings, selectedBotId = botId)
        _inlineKeyboard.value = listOf(
            listOf(InlineKeyboardButton("Inline Mode", "inline:$botId"), InlineKeyboardButton("Webhook", "set_webhook:$botId")),
            listOf(InlineKeyboardButton("Mini App", "mini_app:$botId"), InlineKeyboardButton("Domain", "domain:$botId")),
            listOf(InlineKeyboardButton("« Back to Bot", "manage_bot:$botId"))
        )
        addMessage("BotFather: Settings for this bot:")
    }

    private fun showEditMenu(botId: String) {
        _uiState.value = _uiState.value.copy(currentMenu = MenuState.BotEdit, selectedBotId = botId)
        _inlineKeyboard.value = listOf(
            listOf(InlineKeyboardButton("Edit Name", "edit_name:$botId"), InlineKeyboardButton("Edit About", "edit_about:$botId")),
            listOf(InlineKeyboardButton("Edit Description", "edit_desc:$botId"), InlineKeyboardButton("Edit Botpic", "edit_pic:$botId")),
            listOf(InlineKeyboardButton("Edit Commands", "edit_cmds:$botId"), InlineKeyboardButton("« Back to Bot", "manage_bot:$botId"))
        )
        addMessage("BotFather: What do you want to edit?")
    }

    private fun addMessage(msg: String) {
        _chatMessages.value = _chatMessages.value + msg
    }

    fun createNewBot(name: String, username: String) {
        val newBot = BotModel(UUID.randomUUID().toString(), name, username, "token_mock")
        _botList.value = _botList.value + newBot
    }
}

data class BotModel(
    val id: String,
    val name: String,
    val username: String,
    val token: String
)
