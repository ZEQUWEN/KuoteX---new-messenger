import re
path = 'app/src/main/java/com/example/MainActivity.kt'
with open(path, 'r') as f:
    content = f.read()

content = content.replace(
    'val sharedPrefs = getSharedPreferences("neon_messenger_prefs", android.content.Context.MODE_PRIVATE)',
    'val sharedPrefs = getSharedPreferences("neon_messenger_prefs", android.content.Context.MODE_PRIVATE)\n            com.example.ui.botapi.BotRegistry.init(sharedPrefs)'
)

with open(path, 'w') as f:
    f.write(content)
