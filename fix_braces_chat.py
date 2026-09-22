import re

with open("app/src/main/java/com/example/ui/ChatScreen.kt", "r") as f:
    content = f.read()

content = content.replace("else Color.Gray\n                        )\n                    }\n                },", "else Color.Gray\n                        )\n                    }\n                    }\n                },")

with open("app/src/main/java/com/example/ui/ChatScreen.kt", "w") as f:
    f.write(content)
