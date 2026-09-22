sed -i 's/onLoginSuccess: (String) -> Unit/onLoginSuccess: (String) -> Unit,\n    forceManualLogin: Boolean = false/g' app/src/main/java/com/example/ui/AuthScreens.kt
sed -i 's/var showManualLogin by remember { mutableStateOf(false) }/var showManualLogin by remember(forceManualLogin) { mutableStateOf(forceManualLogin) }/g' app/src/main/java/com/example/ui/AuthScreens.kt
