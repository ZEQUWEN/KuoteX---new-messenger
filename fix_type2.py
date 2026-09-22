with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'r') as f:
    content = f.read()
content = content.replace('if (result is AuthResult.Success<*>) {\\n            val user = result.data', 'if (result is AuthResult.Success) {\\n            val user = result.data as com.example.auth.FirebaseUserInfo')
with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'r') as f:
    content = f.read()
content = content.replace('if (result is AuthResult.Success<*>) {\\n                                            onLoginSuccess(result.data.uid)', 'if (result is AuthResult.Success) {\\n                                            val data = result.data as com.example.auth.FirebaseUserInfo\\n                                            onLoginSuccess(data.uid)')
with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'w') as f:
    f.write(content)
