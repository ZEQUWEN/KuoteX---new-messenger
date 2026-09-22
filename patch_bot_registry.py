import re
path = 'app/src/main/java/com/example/ui/botapi/BotRegistry.kt'
with open(path, 'r') as f:
    content = f.read()

imports = """package com.example.ui.botapi

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
                    val botsList = dao.getAllCustomBotsSync()
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
                        dao.insertCustomBot(it)
                    }
                } catch (e: Exception) {
                    Log.e("BotRegistry", "Failed to save custom bots", e)
                }
            }
        }
    }
"""

content = re.sub(r'package com.example.ui.botapi.*?fun saveCustomBots\(\) \{.*?\}\n    \}', imports, content, flags=re.DOTALL)

with open(path, 'w') as f:
    f.write(content)
