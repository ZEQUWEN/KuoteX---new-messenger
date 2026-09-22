import re
path = 'app/src/main/java/com/example/ui/ChatScreen.kt'
with open(path, 'r') as f:
    content = f.read()

replacement = """                        val botObjAvatar = com.example.ui.botapi.BotRegistry.getBot(chatId) ?: com.example.ui.botapi.BotRegistry.getCustomBots().find { it.id == chatId }
                        val customBotAvatar = botObjAvatar as? com.example.ui.botapi.CustomBot
                        val topBarAvatarUrl = customBotAvatar?.botPicUri ?: "https://picsum.photos/seed/${chat.id}/400"
                        
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).allowHardware(false)
                                .data(topBarAvatarUrl)"""

pattern = """                        coil.compose.AsyncImage\(
                            model = coil.request.ImageRequest.Builder\(androidx.compose.ui.platform.LocalContext.current\).allowHardware\(false\)
                                .data\("https://picsum.photos/seed/\$\{chat.id\}/400"\)"""

content = re.sub(pattern, replacement, content)

with open(path, 'w') as f:
    f.write(content)
print("ChatScreen.kt topbar avatar patched!")
