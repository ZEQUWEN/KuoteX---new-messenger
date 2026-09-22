sed -i '/LazyColumn {/i\
            Button(onClick = {\
                viewModel.addGroupMember(chatId, "bot_" + java.util.UUID.randomUUID().toString().take(4), "New Bot", isAdmin = false)\
            }, modifier = Modifier.align(Alignment.End)) {\
                Text("Add Bot to Group")\
            }\
            Spacer(Modifier.height(16.dp))' app/src/main/java/com/example/ui/MainScreen.kt
