import re
path = 'app/src/main/java/com/example/ui/SandboxScreen.kt'
with open(path, 'r') as f:
    content = f.read()

content = content.replace(
    'bot.code = codeText',
    'bot.code = codeText; com.example.ui.botapi.BotRegistry.saveCustomBots()'
)

# And also where it adds snapshot
content = content.replace(
    'bot.snapshots.add(com.example.ui.botapi.CodeSnapshot(newVersion, codeText))',
    'bot.snapshots.add(com.example.ui.botapi.CodeSnapshot(newVersion, codeText)); com.example.ui.botapi.BotRegistry.saveCustomBots()'
)

with open(path, 'w') as f:
    f.write(content)
