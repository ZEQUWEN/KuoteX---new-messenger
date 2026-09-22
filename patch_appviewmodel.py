import re

with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'r') as f:
    content = f.read()

init_block = r'''    init {
        viewModelScope.launch {'''

replacement = r'''    init {
        // Update presence to Firestore using the background Worker
        try {
            com.example.ui.PresenceManager.updatePresence(repository.context, "current_user_id", true)
        } catch(e: Exception) { e.printStackTrace() }

        viewModelScope.launch {'''

content = content.replace(init_block, replacement)

with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'w') as f:
    f.write(content)

print("AppViewModel updated!")
