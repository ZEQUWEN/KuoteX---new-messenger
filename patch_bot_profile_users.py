import re

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "r") as f:
    content = f.read()

target = """    // Fallback/Mock data for the bot
    val botPic = "https://picsum.photos/seed/${chat.id}/400"
    val botUsername = if (chat.title.startsWith("@")) chat.title else "@" + chat.title.lowercase().replace(" ", "_")
    val botDescription = "BotFather is the one bot to rule them all. Use it to create new bot accounts and manage your existing bots.\\n\\nAbout Telegram bots:\\nhttps://core.telegram.org/bots"
    val userCount = "7,815,477 пользователей\""""

replacement = """    // Fallback/Mock data for the bot
    val botPic = "https://picsum.photos/seed/${chat.id}/400"
    val botUsername = if (chat.title.startsWith("@")) chat.title else "@" + chat.title.lowercase().replace(" ", "_")
    val botDescription = "BotFather is the one bot to rule them all. Use it to create new bot accounts and manage your existing bots.\\n\\nAbout Telegram bots:\\nhttps://core.telegram.org/bots"
    
    val activeUsers by viewModel.getBotActiveUsersCount(chatId).collectAsStateWithLifecycle(initialValue = 0)
    val userCount = if (activeUsers > 0) "${java.text.NumberFormat.getInstance(java.util.Locale("ru", "RU")).format(activeUsers)} пользователей" else "..."
"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "w") as f:
    f.write(content)
