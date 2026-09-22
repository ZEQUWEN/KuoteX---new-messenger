sed -i '/fun createChat/i\
    fun addBot(bot: com.example.data.Chat) {\
        viewModelScope.launch {\
            val exists = repository.allChats.firstOrNull()?.any { it.id == bot.id } == true\
            if (!exists) {\
                repository.insertChat(bot)\
            }\
        }\
    }\
' app/src/main/java/com/example/ui/AppViewModel.kt
