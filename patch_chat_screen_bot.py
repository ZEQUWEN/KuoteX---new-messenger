import re

with open("app/src/main/java/com/example/ui/ChatScreen.kt", "r") as f:
    content = f.read()

target = """                actions = {
                    IconButton(onClick = { navController.navigate("call/${chat.id}?isVideo=false") }) {
                        Icon(Icons.Filled.Call, contentDescription = "Voice Call")
                    }
                    IconButton(onClick = { navController.navigate("call/${chat.id}?isVideo=true") }) {
                        Icon(Icons.Filled.VideoCall, contentDescription = "Video Call")
                    }"""

replacement = """                actions = {
                    if (!chat.isBot) {
                        IconButton(onClick = { navController.navigate("call/${chat.id}?isVideo=false") }) {
                            Icon(Icons.Filled.Call, contentDescription = "Voice Call")
                        }
                        IconButton(onClick = { navController.navigate("call/${chat.id}?isVideo=true") }) {
                            Icon(Icons.Filled.VideoCall, contentDescription = "Video Call")
                        }
                    }"""

content = content.replace(target, replacement)

# Commands highlighting in MessageBubble
# Let's add basic highlight for text starting with /
target_bubble = """        Text(
            text = message.content,
            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )"""

replacement_bubble = """        val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
            val words = message.content.split(" ")
            for ((index, word) in words.withIndex()) {
                if (word.startsWith("/") && word.length > 1) {
                    withStyle(style = androidx.compose.ui.text.SpanStyle(color = if (isMe) Color.White else MaterialTheme.colorScheme.primary)) {
                        append(word)
                    }
                } else {
                    append(word)
                }
                if (index < words.size - 1) append(" ")
            }
        }
        
        Text(
            text = annotatedString,
            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )"""

content = content.replace(target_bubble, replacement_bubble)
if "import androidx.compose.ui.text.withStyle" not in content:
    content = content.replace("import androidx.compose.ui.text.font.FontWeight", "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.withStyle")


with open("app/src/main/java/com/example/ui/ChatScreen.kt", "w") as f:
    f.write(content)
