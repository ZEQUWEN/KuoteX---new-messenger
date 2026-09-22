import re
path = 'app/src/main/java/com/example/ui/botapi/BotFather.kt'
with open(path, 'r') as f:
    content = f.read()

# We can replace this specific block which ends handleInput
old_end = """                } else {
                    bot.paymentProviders[state.providerName] = messageText
                    states[chat.id] = BotFatherState.ManagingBot(bot.id)
                    sendReplyWithButtons("${state.providerName} token saved successfully.", listOf("« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
        }
    }

    private suspend fun sendBotManagementMenu("""

new_end = """                } else {
                    bot.paymentProviders[state.providerName] = messageText
                    states[chat.id] = BotFatherState.ManagingBot(bot.id)
                    sendReplyWithButtons("${state.providerName} token saved successfully.", listOf("« Back to Bot"), chat.id, repository, signalProtocolManager, messageIdToEdit)
                }
            }
        }
        
        BotRegistry.saveCustomBots() // Persist any changes made during this interaction
    }

    private suspend fun sendBotManagementMenu("""

content = content.replace(old_end, new_end)

with open(path, 'w') as f:
    f.write(content)
