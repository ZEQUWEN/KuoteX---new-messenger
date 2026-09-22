sed -i 's/val isAuthComplete.*/val isAuthComplete = activeAccount != null \&\& requires2FA == null \&\& \!isAddingAccount/g' app/src/main/java/com/example/ui/MainScreen.kt
