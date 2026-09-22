import re

with open("app/src/main/java/com/example/ui/MainScreen.kt", "r") as f:
    content = f.read()

# For ChatScreen
chat_target = """composable("chat/{chatId}") { backStackEntry -> 
                                val chatId = backStackEntry.arguments?.getString("chatId")
                                if (chatId != null) {
                                    ChatScreen(viewModel, chatId, mainNavController)
                                }"""
chat_replacement = """composable("chat/{chatId}") { backStackEntry -> 
                                val chatId = backStackEntry.arguments?.getString("chatId")
                                if (chatId != null) {
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                                        ChatScreen(viewModel, chatId, mainNavController)
                                    }
                                }"""
content = content.replace(chat_target, chat_replacement)

# For BotProfileScreen / ProfileScreen
profile_target = """composable("profile/{chatId}") { backStackEntry ->
                                val chatId = backStackEntry.arguments?.getString("chatId")
                                if (chatId != null) {
                                    val chats = viewModel.chats.value
                                    val chat = chats.find { it.id == chatId }
                                    if (chat?.isBot == true) {
                                        BotProfileScreen(viewModel, chatId, mainNavController)
                                    } else {
                                        ProfileScreen(viewModel, chatId, mainNavController)
                                    }
                                }"""
profile_replacement = """composable("profile/{chatId}") { backStackEntry ->
                                val chatId = backStackEntry.arguments?.getString("chatId")
                                if (chatId != null) {
                                    val chats = viewModel.chats.value
                                    val chat = chats.find { it.id == chatId }
                                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                                        if (chat?.isBot == true) {
                                            BotProfileScreen(viewModel, chatId, mainNavController)
                                        } else {
                                            ProfileScreen(viewModel, chatId, mainNavController)
                                        }
                                    }
                                }"""
content = content.replace(profile_target, profile_replacement)

with open("app/src/main/java/com/example/ui/MainScreen.kt", "w") as f:
    f.write(content)
