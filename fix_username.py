import re

with open("app/src/main/java/com/example/ui/AccountScreen.kt", "r") as f:
    content = f.read()

target1 = """                            title = if (state.username.isNotBlank()) "@${state.username}" else "Не задано","""
replacement1 = """                            title = if (state.username.isNotBlank()) "@${state.username.removePrefix("@")}" else "Не задано","""
content = content.replace(target1, replacement1)

target2 = """    var username by remember { mutableStateOf(currentUsername) }"""
replacement2 = """    var username by remember { mutableStateOf(currentUsername.removePrefix("@")) }"""
content = content.replace(target2, replacement2)

target3 = """                    onValueChange = { 
                        if (it.contains("@")) {
                            error = "Символ @ не требуется."
                            username = it.replace("@", "")
                        } else {
                            error = null
                            username = it
                        }
                    },"""
replacement3 = """                    onValueChange = { 
                        username = it
                        if (it.contains("@")) {
                            error = "Символ @ не требуется."
                        } else {
                            error = null
                        }
                    },"""
content = content.replace(target3, replacement3)

target4 = """                onClick = {
                    error = null
                    viewModel.saveUsername(username, onSuccess = { onDismiss() }, onError = { error = it })
                },
                enabled = available == true"""
replacement4 = """                onClick = {
                    error = null
                    val cleanUsername = username.replace("@", "")
                    viewModel.saveUsername(cleanUsername, onSuccess = { onDismiss() }, onError = { error = it })
                },
                enabled = available == true && error == null"""
content = content.replace(target4, replacement4)

with open("app/src/main/java/com/example/ui/AccountScreen.kt", "w") as f:
    f.write(content)
