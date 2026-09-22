import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

debug_block = """                    "/debug" -> {
                        val customBots = BotRegistry.getCustomBots()
                        val info = customBots.joinToString("\\n\\n") { bot ->
                            "ID: ${bot.id}\\nName: ${bot.name}\\nPic: ${bot.botPicUri}\\nDescPic: ${bot.descriptionPictureUri}"
                        }
                        val finalInfo = if (info.isBlank()) "No custom bots." else info
                        sendReplyWithButtons("DEBUG INFO:\\n$finalInfo", listOf("/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/mybots", "manage bots" -> {"""

content = content.replace('                    "/mybots", "manage bots" -> {', debug_block)

with open(path, 'w') as f:
    f.write(content)
print("BotFather.kt patched with debug command!")
