with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('if (result is AuthResult.Success) {', 'if (result is AuthResult.Success<*>) {')

with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'r') as f:
    content = f.read()

content = content.replace('if (result is AuthResult.Success) {', 'if (result is AuthResult.Success<*>) {')

with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'w') as f:
    f.write(content)

