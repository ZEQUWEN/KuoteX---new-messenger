import re

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "r") as f:
    content = f.read()

target = """                        DropdownMenuItem(
                            text = { Text("Удалить и заблокировать", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false }
                        )
                    }
                }
            )"""

replacement = """                        DropdownMenuItem(
                            text = { Text("Удалить и заблокировать", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false }
                        )
                    }
                    }
                }
            )"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "w") as f:
    f.write(content)
