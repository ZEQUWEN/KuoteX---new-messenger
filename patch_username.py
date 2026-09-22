import re

with open("app/src/main/java/com/example/ui/SettingsScreens.kt", "r") as f:
    content = f.read()

target1 = "var username by remember { mutableStateOf(activeAccount.username) }"
replacement1 = "var username by remember { mutableStateOf(activeAccount.username.removePrefix(\"@\")) }"
content = content.replace(target1, replacement1)

target2 = """                    if (it.length < 5 && it.isNotEmpty()) {
                        usernameError = "Username must be at least 5 characters"
                    } else if (!it.matches(Regex("^[a-zA-Z0-9_]+$")) && it.isNotEmpty()) {
                        usernameError = "Invalid characters"
                    }"""
replacement2 = """                    if (it.contains("@")) {
                        usernameError = "Символ @ не требуется"
                    } else if (it.length < 5 && it.isNotEmpty()) {
                        usernameError = "Username must be at least 5 characters"
                    } else if (!it.matches(Regex("^[a-zA-Z0-9_]+$")) && it.isNotEmpty()) {
                        usernameError = "Invalid characters"
                    }"""
content = content.replace(target2, replacement2)

target3 = """                onClick = {
                    if (usernameError == null) {
                        viewModel.updateProfile(activeAccount.id, username, displayName, bio, profilePicUrl, customStatus)"""
replacement3 = """                onClick = {
                    if (usernameError == null) {
                        val finalUsername = "@${username.removePrefix("@")}"
                        viewModel.updateProfile(activeAccount.id, finalUsername, displayName, bio, profilePicUrl, customStatus)"""
content = content.replace(target3, replacement3)

with open("app/src/main/java/com/example/ui/SettingsScreens.kt", "w") as f:
    f.write(content)
