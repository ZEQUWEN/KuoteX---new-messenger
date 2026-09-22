import re

with open('app/src/main/java/com/example/ui/MainScreen.kt', 'r') as f:
    content = f.read()

# 1. Add userPresences to ChatListScreen
content = re.sub(
    r'val typingChats by viewModel\.typingChats\.collectAsState\(\)',
    r'val typingChats by viewModel.typingChats.collectAsState()\n    val userPresences by viewModel.userPresences.collectAsStateWithLifecycle()',
    content
)

# 2. Add presence to SwipeableChatListItem calls
content = re.sub(
    r'viewModel = viewModel,\n(\s*)onClick =',
    r'viewModel = viewModel,\n\1presence = userPresences[chat.id],\n\1onClick =',
    content
)

# 3. Add presence to SwipeableChatListItem definition
content = re.sub(
    r'fun SwipeableChatListItem\(\n(\s*)chat: Chat, \n(\s*)isTyping: Boolean = false, \n(\s*)draftText: String\? = null,\n(\s*)viewModel: AppViewModel, ',
    r'fun SwipeableChatListItem(\n\1chat: Chat, \n\2isTyping: Boolean = false, \n\3draftText: String? = null,\n\4viewModel: AppViewModel, \n\4presence: com.example.ui.UserPresence? = null, ',
    content
)

# 4. Pass presence to ChatListItem from SwipeableChatListItem
content = re.sub(
    r'ChatListItem\(\n(\s*)chat = chat, \n(\s*)isTyping = isTyping, \n(\s*)draftText = draftText,\n(\s*)onClick = onClick, \n(\s*)onAvatarClick = onAvatarClick\n(\s*)\)',
    r'ChatListItem(\n\1chat = chat, \n\2isTyping = isTyping, \n\3draftText = draftText,\n\4presence = presence,\n\4onClick = onClick, \n\5onAvatarClick = onAvatarClick\n\6)',
    content
)

# 5. Add presence to ChatListItem definition
content = re.sub(
    r'fun ChatListItem\(\n(\s*)chat: Chat, \n(\s*)isTyping: Boolean = false, \n(\s*)draftText: String\? = null,\n(\s*)onClick: \(\) -> Unit, ',
    r'fun ChatListItem(\n\1chat: Chat, \n\2isTyping: Boolean = false, \n\3draftText: String? = null,\n\4presence: com.example.ui.UserPresence? = null,\n\4onClick: () -> Unit, ',
    content
)

# 6. Add "Last Active" timestamp display under contact names in the message list
# Replace the Row(verticalAlignment = Alignment.CenterVertically) block ending with Text(chat.title...)
# We can inject it right after the Row
replacement = r'''Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isSecret) {
                    Icon(Icons.Filled.Lock, contentDescription = "Secret Chat", modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(4.dp))
                } else if (chat.isChannel) {
                    Icon(Icons.Filled.Campaign, contentDescription = "Channel", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                } else if (chat.isGroup) {
                    Icon(Icons.Filled.Groups, contentDescription = "Group", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                } else if (chat.isBot) {
                    Icon(Icons.Filled.SmartToy, contentDescription = "Bot", modifier = Modifier.size(16.dp), tint = Color(0xFF00D4FF))
                    Spacer(Modifier.width(4.dp))
                }
                Text(chat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            if (!chat.isGroup && !chat.isChannel && !chat.isBot) {
                if (presence?.isOnline == true) {
                    Text("Online", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                } else if (presence != null && presence.lastSeen > 0) {
                    val diff = System.currentTimeMillis() - presence.lastSeen
                    val timeStr = when {
                        diff < 60_000 -> "just now"
                        diff < 3600_000 -> "${diff / 60_000}m ago"
                        diff < 86400_000 -> "${diff / 3600_000}h ago"
                        else -> "${diff / 86400_000}d ago"
                    }
                    Text("Last active $timeStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
'''

content = re.sub(
    r'Row\(verticalAlignment = Alignment\.CenterVertically\) \{.*?Text\(chat\.title[^\}]*?\}\n',
    replacement,
    content,
    flags=re.DOTALL
)


with open('app/src/main/java/com/example/ui/MainScreen.kt', 'w') as f:
    f.write(content)

print("MainScreen updated!")
