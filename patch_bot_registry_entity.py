import re
path = 'app/src/main/java/com/example/ui/botapi/BotRegistry.kt'
with open(path, 'r') as f:
    content = f.read()

content = content.replace("dao.getAllCustomBotsSync()", "dao.getAllCustomBotsSync().map { it.toCustomBot() }")
content = content.replace("dao.insertCustomBot(it)", "dao.insertCustomBot(com.example.data.CustomBotEntity.fromCustomBot(it))")

with open(path, 'w') as f:
    f.write(content)
