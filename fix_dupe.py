with open('app/src/main/java/com/example/ui/ChatScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
'''val isOnline = presence?.isOnline == true
                val lastSeen = presence?.lastSeen ?: 0L

                val isOnline = presence?.isOnline == true
                val lastSeen = presence?.lastSeen ?: 0L''',
'''val isOnline = presence?.isOnline == true
                val lastSeen = presence?.lastSeen ?: 0L'''
)

with open('app/src/main/java/com/example/ui/ChatScreen.kt', 'w') as f:
    f.write(content)
