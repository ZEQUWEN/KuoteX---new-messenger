import re

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "r") as f:
    content = f.read()

content = content.replace("""                        )
                    }
                },
                colors""", """                        )
                    }
                    }
                },
                colors""")

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "w") as f:
    f.write(content)
