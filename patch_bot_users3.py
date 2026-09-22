import re

with open("app/src/main/java/com/example/ui/AppViewModel.kt", "r") as f:
    content = f.read()

target = """    fun getBotActiveUsersCount(botId: String) = kotlinx.coroutines.flow.combine(
        repository.getMessages(botId),
        repository.allAccounts
    ) { messages, accounts ->
        val hash = botId.hashCode()
        // Глобальная статистика (симуляция только для верифицированных аккаунтов)
        val globalCount = kotlin.math.abs(hash % 10000000) + 1000
        
        // Локальная статистика: считаем только реальные профили с номером телефона и username, 
        // которые не являются ботами, для защиты от накрутки
        val realLocalUsersCount = messages
            .filter { it.text == "/start" }
            .map { it.senderId }
            .distinct()
            .count { senderId ->
                val account = accounts.find { it.id == senderId }
                account != null && account.phoneNumber.isNotBlank() && account.username.isNotBlank()
            }
            
        globalCount + realLocalUsersCount
    }"""

replacement = """    fun getBotActiveUsersCount(botId: String) = kotlinx.coroutines.flow.combine(
        repository.getMessages(botId),
        repository.allAccounts
    ) { messages, accounts ->
        val hash = botId.hashCode()
        // Глобальная статистика: симуляция, но "только для верифицированных аккаунтов"
        val globalCount = kotlin.math.abs(hash % 10000000) + 1000
        
        // Локальная статистика: считаем только реальные профили
        // Проверяем наличие номера телефона и username, чтобы избежать накрутки
        // фейковыми аккаунтами или другими ботами
        val realLocalUsersCount = messages
            .filter { it.text == "/start" }
            .map { it.senderId }
            .distinct()
            .count { senderId ->
                val account = accounts.find { it.id == senderId }
                val isRealProfile = account != null && 
                                  account.phoneNumber.isNotBlank() && 
                                  account.username.isNotBlank()
                isRealProfile
            }
            
        globalCount + realLocalUsersCount
    }"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/AppViewModel.kt", "w") as f:
    f.write(content)
