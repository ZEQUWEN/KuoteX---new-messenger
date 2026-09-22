sed -i '/val chat = repository.allChats.firstOrNull()?.find { it.id == chatId }/a\
            if (chat?.isGroup == true && text.contains("@bot", ignoreCase = true)) {\
                kotlinx.coroutines.delay(2000)\
                val botReply = "Group Bot: I saw you mention a bot! Your message: $text"\
                val encryptedReply = signalProtocolManager.encryptMessage(botReply)\
                val replyMsg = Message(\
                    id = java.util.UUID.randomUUID().toString(),\
                    chatId = chatId,\
                    senderId = "bot_group",\
                    text = encryptedReply,\
                    timestamp = System.currentTimeMillis()\
                )\
                repository.insertMessage(replyMsg)\
            }\
' app/src/main/java/com/example/ui/AppViewModel.kt
