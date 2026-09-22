import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

# Fix BotRegistry.getCustomBots() casting in /diagnostics
old_diag = """                        val customBots = BotRegistry.getCustomBots()
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
                        }"""

new_diag = """                        val customBots = BotRegistry.getCustomBots()
                        val diagnosticLog = StringBuilder()
                        diagnosticLog.append("--- BotFather Diagnostic Report ---\\n")
                        diagnosticLog.append("Total Custom Bots: ${customBots.size}\\n\\n")
                        customBots.forEach { bot ->
                            val cBot = bot as? CustomBot
                            if (cBot != null) {
                                diagnosticLog.append("Bot ID: @${cBot.id}\\n")
                                diagnosticLog.append("Name Sync: ${if (cBot.name.isNotBlank()) "OK" else "FAIL (Missing)"}\\n")
                                diagnosticLog.append("Desc Sync: ${if (cBot.description.isNotBlank()) "OK" else "FAIL (Missing)"}\\n")
                                diagnosticLog.append("About Sync: ${if (cBot.about.isNotBlank()) "OK" else "FAIL (Missing)"}\\n")
                                diagnosticLog.append("Profile Pic Sync: ${if (cBot.botPicUri != null) "OK (${cBot.botPicUri})" else "FAIL (Missing)"}\\n")
                                diagnosticLog.append("Desc Pic Sync: ${if (cBot.descriptionPictureUri != null) "OK (${cBot.descriptionPictureUri})" else "FAIL (Missing)"}\\n")
                                diagnosticLog.append("-------------------------\\n")
                            }
                        }"""

content = content.replace(old_diag, new_diag)

# Also fix the /debug block where botPicUri is used
old_debug = """                        val info = customBots.joinToString("\\n\\n") { bot ->
                            "ID: ${bot.id}\\nName: ${bot.name}\\nPic: ${bot.botPicUri}\\nDescPic: ${bot.descriptionPictureUri}"
                        }"""

new_debug = """                        val info = customBots.joinToString("\\n\\n") { bot ->
                            val cBot = bot as? CustomBot
                            "ID: ${bot.id}\\nName: ${bot.name}\\nPic: ${cBot?.botPicUri}\\nDescPic: ${cBot?.descriptionPictureUri}"
                        }"""

content = content.replace(old_debug, new_debug)

with open(path, 'w') as f:
    f.write(content)
print("Diagnostic casts fixed!")
