import re
path = 'app/src/main/java/com/example/ui/MainScreen.kt'
with open(path, 'r') as f:
    content = f.read()

replacement = """        val botObj = com.example.ui.botapi.BotRegistry.getBot(chat.id) ?: com.example.ui.botapi.BotRegistry.getCustomBots().find { it.id == chat.id }
        val customBot = botObj as? com.example.ui.botapi.CustomBot
        val avatarUrl = customBot?.botPicUri ?: "https://picsum.photos/seed/${chat.id}/100"

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape)
                .clickable { onAvatarClick() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).allowHardware(false)
                    .data(avatarUrl)"""

# In ChatListItem, we want to replace the whole Box and AsyncImage setup
pattern = """        Box\(
            modifier = Modifier
                .size\(56.dp\)
                .clip\(CircleShape\)
                .background\(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant\)
                .border\(2.dp, MaterialTheme.colorScheme.primary.copy\(alpha = 0.8f\), CircleShape\)
                .clickable \{ onAvatarClick\(\) \},
            contentAlignment = Alignment.Center
        \) \{
            AsyncImage\(
                model = ImageRequest.Builder\(androidx.compose.ui.platform.LocalContext.current\).allowHardware\(false\)
                    .data\("https://picsum.photos/seed/\$\{chat.id\}/100"\)"""

content = re.sub(pattern, replacement, content)

with open(path, 'w') as f:
    f.write(content)
print("MainScreen.kt patched!")
