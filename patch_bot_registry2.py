import re
path = 'app/src/main/java/com/example/ui/botapi/BotRegistry.kt'
with open(path, 'r') as f:
    content = f.read()

content = content.replace('''    fun unregisterCustomBot(id: String) {
        bots.remove(id)
        customBots.remove(id)
        saveCustomBots()
    }''', '''    fun unregisterCustomBot(id: String) {
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
    }''')

with open(path, 'w') as f:
    f.write(content)
