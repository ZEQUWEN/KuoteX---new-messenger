import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

# Fix Chat creation
content = content.replace(
    'val newChat = Chat(id = newBot.id, title = newBot.name, isBot = true)',
    'val newChat = Chat(id = newBot.id, title = newBot.name, isBot = true, lastMessage = "")'
)

# Fix multiline string
# Let's find the successMessage block
# Just find everything from 'val successMessage =' up to 'sendReplyWithButtons(successMessage'
import re
def replacer(match):
    # The matched string is everything between the quotes.
    # Replace actual newlines with \n
    inside = match.group(1).replace('\n', '\\n')
    return f'val successMessage = "{inside}"'

content = re.sub(r'val successMessage = "(.*?)"', replacer, content, flags=re.DOTALL)

with open(path, 'w') as f:
    f.write(content)
print("Syntax fixed!")
