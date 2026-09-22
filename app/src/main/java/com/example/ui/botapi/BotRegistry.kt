package com.example.ui.botapi

import com.example.data.BotDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

object BotRegistry {
    private val bots = mutableMapOf<String, Bot>()
    private val customBots = mutableMapOf<String, Bot>()
    
    private var botDao: BotDao? = null
    private var isInitialized = false

    fun init(dao: BotDao) {
        if (isInitialized) return
        botDao = dao
        isInitialized = true
        loadCustomBots()
    }

    private fun loadCustomBots() {
        botDao?.let { dao ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val botsList = dao.getAllCustomBotsSync().map { it.toCustomBot() }
                    botsList.forEach {
                        bots[it.id] = it
                        customBots[it.id] = it
                    }
                } catch (e: Exception) {
                    Log.e("BotRegistry", "Failed to load custom bots", e)
                }
            }
        }
    }

    fun saveCustomBots() {
        botDao?.let { dao ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val botsList = customBots.values.filterIsInstance<CustomBot>()
                    botsList.forEach {
                        dao.insertCustomBot(com.example.data.CustomBotEntity.fromCustomBot(it))
                    }
                } catch (e: Exception) {
                    Log.e("BotRegistry", "Failed to save custom bots", e)
                }
            }
        }
    }



    init {
        // Register all available bots
        registerBot(WeatherBot())
        registerBot(ReminderBot())
        registerBot(EchoBot())
        registerBot(CryptoBot())
        registerBot(BotFather())
        registerBot(NewsBot())
        registerBot(ShopBot())
        registerBot(KuoteXVipBot())
    }

    fun registerBot(bot: Bot) {
        bots[bot.id] = bot
    }
    
    fun registerCustomBot(bot: Bot) {
        bots[bot.id] = bot
        customBots[bot.id] = bot
        saveCustomBots()
    }
    
    fun unregisterCustomBot(id: String) {
        bots.remove(id)
        customBots.remove(id)
        botDao?.let { dao ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dao.deleteCustomBot(id)
                } catch (e: Exception) {
                    Log.e("BotRegistry", "Failed to delete custom bot", e)
                }
            }
        }
    }
    
    fun getCustomBots(): List<Bot> {
        return customBots.values.toList()
    }

    fun updateBotPic(id: String, newPicUri: String) {
        val existing = customBots[id] as? CustomBot
        if (existing != null) {
            existing.botPicUri = newPicUri
            saveCustomBots()
        }
    }

    fun getBot(id: String): Bot? {
        return bots[id]
    }

    fun getAllBots(): List<Bot> {
        return bots.values.toList().sortedBy { it.id }
    }
}
