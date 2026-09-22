import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

# Add logging when handling WaitingForEditAbout and WaitingForEditDescription
pic_handler = """            is BotFatherState.WaitingForEditAbout -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                bot.about = if (messageText.length > 120) messageText.substring(0, 120) else messageText
                android.util.Log.d("BotFather", "Received About text: ${bot.about}")
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! About section updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }
            is BotFatherState.WaitingForEditDescription -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                bot.description = messageText
                android.util.Log.d("BotFather", "Received Description: ${bot.description}")
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Description updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }"""

content = re.sub(
    r"is BotFatherState.WaitingForEditAbout -> \{.*?(?=is BotFatherState.WaitingForEditBotpic -> \{)",
    pic_handler.replace('\\n', '\n') + '\n            ',
    content,
    flags=re.DOTALL
)

with open(path, 'w') as f:
    f.write(content)
print("BotFather description logging added!")
