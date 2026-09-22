sed -i 's/onLoginSuccess = { username ->/onLoginSuccess = { username ->\n                                viewModel.clearAddingAccount()/g' app/src/main/java/com/example/ui/MainScreen.kt
sed -i '/onLoginSuccess = { username ->/i \                            forceManualLogin = isAddingAccount,' app/src/main/java/com/example/ui/MainScreen.kt
