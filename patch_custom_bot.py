import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

content = content.replace('var miniAppShortName: String? = null\n) {', 'var miniAppShortName: String? = null\n) : Bot {')

with open(path, 'w') as f:
    f.write(content)
