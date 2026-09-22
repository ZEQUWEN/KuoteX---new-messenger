import re

with open("app/src/main/java/com/example/ui/AppViewModel.kt", "r") as f:
    content = f.read()

target = """    fun getMessages(chatId: String) = repository.getMessages(chatId).map { messages ->"""

replacement = """    fun getBotActiveUsersCount(botId: String) = repository.getMessages(botId).map { messages ->
        val hash = botId.hashCode()
        val globalCount = kotlin.math.abs(hash % 10000000) + 1000
        val hasStartedLocally = messages.any { it.text == "/start" }
        if (hasStartedLocally) globalCount + 1 else globalCount
    }

    fun getMessages(chatId: String) = repository.getMessages(chatId).map { messages ->"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/AppViewModel.kt", "w") as f:
    f.write(content)
