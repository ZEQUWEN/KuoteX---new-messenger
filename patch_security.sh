sed -i '/SettingsSimpleItem(title = "If away for", value = deleteAccountValue, onClick = { deleteAccountDialogVisible = true })/a\
        \
        var immediateDeleteDialogVisible by remember { mutableStateOf(false) }\
        Text(\
            text = "Delete Account Now",\
            color = MaterialTheme.colorScheme.error,\
            style = MaterialTheme.typography.bodyLarge,\
            modifier = Modifier\
                .fillMaxWidth()\
                .clickable { immediateDeleteDialogVisible = true }\
                .padding(horizontal = 16.dp, vertical = 14.dp)\
        )\
        if (immediateDeleteDialogVisible) {\
            AlertDialog(\
                onDismissRequest = { immediateDeleteDialogVisible = false },\
                title = { Text("Delete Account") },\
                text = { Text("Are you sure you want to delete your account? This action will wipe all local data and initiate the server-side deletion process. This cannot be undone.") },\
                confirmButton = {\
                    TextButton(\
                        onClick = {\
                            viewModel.deleteAccount(activeAccount.id) {\
                                immediateDeleteDialogVisible = false\
                            }\
                        }\
                    ) {\
                        Text("Delete", color = MaterialTheme.colorScheme.error)\
                    }\
                },\
                dismissButton = {\
                    TextButton(onClick = { immediateDeleteDialogVisible = false }) {\
                        Text("Cancel")\
                    }\
                }\
            )\
        }' app/src/main/java/com/example/ui/SettingsScreens.kt
