import re
path = 'app/src/main/java/com/example/ui/ChatScreen.kt'
with open(path, 'r') as f:
    content = f.read()

bot_desc_block = """            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (chat.isBot && filteredMessages.isEmpty() && !isSearchMode) {
                    val botObj = com.example.ui.botapi.BotRegistry.getBot(chatId) ?: com.example.ui.botapi.BotRegistry.getCustomBots().find { it.id == chatId }
                    val customBot = botObj as? com.example.ui.botapi.CustomBot
                    val description = customBot?.description?.takeIf { it.isNotBlank() } ?: "What can this bot do?"
                    val descriptionPictureUri = customBot?.descriptionPictureUri

                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (descriptionPictureUri != null && descriptionPictureUri.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(context).allowHardware(false)
                                    .data(descriptionPictureUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Bot description picture",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                LazyColumn("""

content = content.replace(
    '            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {\n                LazyColumn(',
    bot_desc_block
)

# And we need to close the else block after LazyColumn
lazy_column_end = """                    }
                }"""

# Find where the LazyColumn ends. We look for the closing brace of LazyColumn.
# Let's just do a regex replace
lazy_column_pattern = r"                    }\n                }\n            }"
lazy_column_replacement = r"                    }\n                }\n                }\n            }"

content = re.sub(r'                    }\n                }\n            }', lazy_column_replacement, content, count=1)

with open(path, 'w') as f:
    f.write(content)
print("ChatScreen.kt patched!")
