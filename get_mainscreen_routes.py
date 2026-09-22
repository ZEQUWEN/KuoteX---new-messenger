import re

with open("app/src/main/java/com/example/ui/MainScreen.kt", "r") as f:
    content = f.read()

print(re.findall(r'composable\("chat/.*?\) \{.*?\n.*?ChatScreen\(.*?\)\n.*?\}', content, re.DOTALL))
print(re.findall(r'composable\("profile/.*?\) \{.*?\n.*?(?:BotProfileScreen|ProfileScreen)\(.*?\)\n.*?\}', content, re.DOTALL))
