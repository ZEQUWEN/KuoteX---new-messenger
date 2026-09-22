package com.example.ui.botapi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BotFatherViewModel : ViewModel() {
    sealed class BotUIState {
        object Idle : BotUIState()
        data class CreatingBot(val step: Int = 0) : BotUIState()
        data class EditingBot(val botId: String) : BotUIState()
        data class ReorderingButtons(val botId: String) : BotUIState()
    }

    private val _uiState = MutableStateFlow<BotUIState>(BotUIState.Idle)
    val uiState: StateFlow<BotUIState> = _uiState.asStateFlow()

    fun startCreatingBot() {
        _uiState.value = BotUIState.CreatingBot(0)
    }

    fun startEditingBot(botId: String) {
        _uiState.value = BotUIState.EditingBot(botId)
    }

    fun startReordering(botId: String) {
        _uiState.value = BotUIState.ReorderingButtons(botId)
    }

    fun resetState() {
        _uiState.value = BotUIState.Idle
    }
    
    fun saveReorderedButtons(botId: String, newOrder: List<String>) {
        val bot = BotRegistry.getBot(botId) as? CustomBot
        if (bot != null) {
            bot.keyboardLayoutConfig = newOrder.joinToString("||")
        }
        resetState()
    }
}
