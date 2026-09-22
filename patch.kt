                    var attachmentMenuExpanded by remember { mutableStateOf(false) }
                    var botMenuExpanded by remember { mutableStateOf(false) }
                    
                    if (chat.isBot || chat.title.contains("BotFather", ignoreCase = true)) {
                        Box {
                            IconButton(onClick = { botMenuExpanded = true }) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Terminal, contentDescription = "Bot Commands", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = botMenuExpanded,
                                onDismissRequest = { botMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("/start - start/refresh bot") },
                                    onClick = {
                                        botMenuExpanded = false
                                        viewModel.sendMessage(chatId, activeAccount?.id ?: "", "/start", null, expiresIn)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("/newbot - create a new bot") },
                                    onClick = {
                                        botMenuExpanded = false
                                        viewModel.sendMessage(chatId, activeAccount?.id ?: "", "/newbot", null, expiresIn)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("/description - bot description") },
                                    onClick = {
                                        botMenuExpanded = false
                                        viewModel.sendMessage(chatId, activeAccount?.id ?: "", "/description", null, expiresIn)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("/mybots - edit your bots") },
                                    onClick = {
                                        botMenuExpanded = false
                                        viewModel.sendMessage(chatId, activeAccount?.id ?: "", "/mybots", null, expiresIn)
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { attachmentMenuExpanded = true }) {
