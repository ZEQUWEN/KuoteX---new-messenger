sed -i '/simulateTyping(chatId)/a\
            val chat = repository.allChats.firstOrNull()?.find { it.id == chatId }\
            if (chat?.isBot == true) {\
                kotlinx.coroutines.delay(2000)\
                val botReply = "Beep boop! You said: $text"\
                val encryptedReply = signalProtocolManager.encryptMessage(botReply)\
                val replyMsg = Message(\
                    id = java.util.UUID.randomUUID().toString(),\
                    chatId = chatId,\
                    senderId = "bot_${chat.id}",\
                    text = encryptedReply,\
                    timestamp = System.currentTimeMillis()\
                )\
                repository.insertMessage(replyMsg)\
            }\
' app/src/main/java/com/example/ui/AppViewModel.kt
