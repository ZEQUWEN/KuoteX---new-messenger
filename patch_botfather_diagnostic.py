import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

# Add logging when handling BotPic and DescriptionPicture
pic_handler = """            is BotFatherState.WaitingForEditBotpic -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                android.util.Log.d("BotFather", "Received BotPic URI: $messageText")
                bot.botPicUri = messageText // Usually it's URI sent as text in mock or file upload handling
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Profile photo updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
            is BotFatherState.WaitingForEditDescriptionPicture -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                android.util.Log.d("BotFather", "Received DescriptionPicture URI: $messageText")
                bot.descriptionPictureUri = messageText
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Description picture updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }"""

content = re.sub(
    r"is BotFatherState.WaitingForEditBotpic -> \{.*?(?=is BotFatherState.WaitingForEditCommands -> \{)",
    pic_handler.replace('\\n', '\n') + '\n            ',
    content,
    flags=re.DOTALL
)

with open(path, 'w') as f:
    f.write(content)
print("BotFather diagnostic logging added!")
