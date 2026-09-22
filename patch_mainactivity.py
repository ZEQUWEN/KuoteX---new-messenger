import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

replacement = r'''            val userPrefs = com.example.data.UserPreferencesRepository(applicationContext.dataStore)
            
            // Start presence background worker
            try {
                com.example.ui.PresenceManager.updatePresence(applicationContext, "current_user_id", true)
            } catch(e: Exception) { e.printStackTrace() }
            
            val factory = object : ViewModelProvider.Factory {'''

content = content.replace('''            val userPrefs = com.example.data.UserPreferencesRepository(applicationContext.dataStore)
            val factory = object : ViewModelProvider.Factory {''', replacement)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
