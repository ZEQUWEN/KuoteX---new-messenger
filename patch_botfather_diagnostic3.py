import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

diagnostic_block = """                    "/diagnostics" -> {
                        val customBots = BotRegistry.getCustomBots()
                        val diagnosticLog = StringBuilder()
                        diagnosticLog.append("--- BotFather Diagnostic Report ---\\n")
                        diagnosticLog.append("Total Custom Bots: ${customBots.size}\\n\\n")
                        customBots.forEach { bot ->
                            diagnosticLog.append("Bot ID: @${bot.id}\\n")
                            diagnosticLog.append("Name Sync: ${if (bot.name.isNotBlank()) "OK" else "FAIL (Missing)"}\\n")
                            diagnosticLog.append("Desc Sync: ${if (bot.description.isNotBlank()) "OK" else "FAIL (Missing)"}\\n")
                            diagnosticLog.append("About Sync: ${if (bot.about.isNotBlank()) "OK" else "FAIL (Missing)"}\\n")
                            diagnosticLog.append("Profile Pic Sync: ${if (bot.botPicUri != null) "OK (${bot.botPicUri})" else "FAIL (Missing)"}\\n")
                            diagnosticLog.append("Desc Pic Sync: ${if (bot.descriptionPictureUri != null) "OK (${bot.descriptionPictureUri})" else "FAIL (Missing)"}\\n")
                            diagnosticLog.append("-------------------------\\n")
                        }
                        
                        sendReplyWithButtons(diagnosticLog.toString(), listOf("/mybots"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                    }
                    "/debug" -> {"""

content = content.replace('                    "/debug" -> {', diagnostic_block)

with open(path, 'w') as f:
    f.write(content)
print("BotFather /diagnostics added!")
