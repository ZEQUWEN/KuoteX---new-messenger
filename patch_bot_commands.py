import re

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "r") as f:
    content = f.read()

target = """            // Bot WebApp Integration Section
            item {"""

replacement = """            // Bot Commands Section
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Команды",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        val commands = listOf(
                            "/newbot" to "create a new bot",
                            "/mybots" to "edit your bots",
                            "/setname" to "change a bot's name",
                            "/setdescription" to "change bot description",
                            "/setabouttext" to "change bot about info",
                            "/setuserpic" to "change bot profile photo",
                            "/setcommands" to "change the list of commands",
                            "/deletebot" to "delete a bot",
                            "/token" to "get authorization token",
                            "/revoke" to "revoke bot access token",
                            "/setinline" to "toggle inline mode",
                            "/setinlinegeo" to "toggle inline location requests",
                            "/setinlinefeedback" to "change inline feedback settings",
                            "/setjoingroups" to "can your bot be added to groups?",
                            "/setprivacy" to "toggle privacy mode in groups",
                            "/myapps" to "edit your web apps",
                            "/newapp" to "create a new web app",
                            "/listapps" to "get a list of your web apps",
                            "/editapp" to "edit a web app",
                            "/deleteapp" to "delete an existing web app",
                            "/mygames" to "edit your games",
                            "/newgame" to "create a new game",
                            "/listgames" to "get a list of your games",
                            "/editgame" to "edit a game",
                            "/deletegame" to "delete an existing game"
                        )
                        
                        commands.forEach { (cmd, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate("chat/${chat.id}") }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = cmd,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(130.dp)
                                )
                                Text(
                                    text = "— $desc",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Bot WebApp Integration Section
            item {"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "w") as f:
    f.write(content)
