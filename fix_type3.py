with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'r') as f:
    content = f.read()
content = content.replace('AuthResult.Success<*>', 'AuthResult.Success')
content = content.replace('val user = result.data', 'val user = (result as AuthResult.Success<com.example.auth.FirebaseUserInfo>).data')
with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'r') as f:
    content = f.read()
content = content.replace('AuthResult.Success<*>', 'AuthResult.Success')
content = content.replace('onLoginSuccess(result.data.uid)', 'onLoginSuccess((result as AuthResult.Success<com.example.auth.FirebaseUserInfo>).data.uid)')
with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'w') as f:
    f.write(content)
