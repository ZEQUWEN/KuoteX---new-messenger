import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

# Add the import and sync call to BotFather.kt
import_statement = "import com.example.api.BotApiClient\nimport kotlinx.coroutines.launch\nimport kotlinx.coroutines.GlobalScope\n"

if "import com.example.api.BotApiClient" not in content:
    content = content.replace("package com.example.ui.botapi\n", "package com.example.ui.botapi\n\n" + import_statement)

# Now, after bot.botPicUri = messageText, we want to launch the sync
pic_handler_search = r"""            is BotFatherState.WaitingForEditBotpic -> \{
                val bot = BotRegistry.getBot\(state.botId\) as\? CustomBot \?\: return
                android.util.Log.d\("BotFather", "Received BotPic URI: \$messageText"\)
                bot.botPicUri = messageText // Usually it's URI sent as text in mock or file upload handling
                states\[chat.id\] = BotFatherState.EditingBot\(bot.id\)
                sendReplyWithButtons\("Success! Profile photo updated.", listOf\("« Back to Bot", "« Back to Bot List"\), chat.id, repository, signalProtocolManager, messageIdToEdit\)
            \}"""

pic_handler_replace = """            is BotFatherState.WaitingForEditBotpic -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                android.util.Log.d("BotFather", "Received BotPic URI: $messageText")
                bot.botPicUri = messageText // Usually it's URI sent as text in mock or file upload handling
                
                // Trigger background sync with exponential backoff
                GlobalScope.launch {
                    val token = bot.oauthToken ?: "dummy_token"
                    BotApiClient.syncBotProfileWithBackoff(
                        token = token,
                        name = bot.name,
                        description = bot.description,
                        about = bot.about,
                        botPicUrl = bot.botPicUri
                    )
                }
                
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Profile photo updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }"""

content = re.sub(pic_handler_search, pic_handler_replace, content)

# And description picture
desc_pic_handler_search = r"""            is BotFatherState.WaitingForEditDescriptionPicture -> \{
                val bot = BotRegistry.getBot\(state.botId\) as\? CustomBot \?\: return
                android.util.Log.d\("BotFather", "Received DescriptionPicture URI: \$messageText"\)
                bot.descriptionPictureUri = messageText
                states\[chat.id\] = BotFatherState.EditingBot\(bot.id\)
                sendReplyWithButtons\("Success! Description picture updated.", listOf\("« Back to Bot", "« Back to Bot List"\), chat.id, repository, signalProtocolManager, messageIdToEdit\)
            \}"""

desc_pic_handler_replace = """            is BotFatherState.WaitingForEditDescriptionPicture -> {
                val bot = BotRegistry.getBot(state.botId) as? CustomBot ?: return
                android.util.Log.d("BotFather", "Received DescriptionPicture URI: $messageText")
                bot.descriptionPictureUri = messageText
                
                // Trigger background sync with exponential backoff
                GlobalScope.launch {
                    val token = bot.oauthToken ?: "dummy_token"
                    BotApiClient.syncBotProfileWithBackoff(
                        token = token,
                        name = bot.name,
                        description = bot.description,
                        about = bot.about,
                        botPicUrl = bot.botPicUri // assuming endpoint handles both or we pass both
                    )
                }
                
                states[chat.id] = BotFatherState.EditingBot(bot.id)
                sendReplyWithButtons("Success! Description picture updated.", listOf("« Back to Bot", "« Back to Bot List"), chat.id, repository, signalProtocolManager, messageIdToEdit)
            }"""
            
content = re.sub(desc_pic_handler_search, desc_pic_handler_replace, content)


with open(path, 'w') as f:
    f.write(content)
print("BotFather API sync calls injected!")
