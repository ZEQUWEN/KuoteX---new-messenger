with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'r') as f:
    content = f.read()

content = content.replace('import com.google.firebase.auth.FirebaseAuthException\nobject FirebaseAuthManager {', 'object FirebaseAuthManager {')
content = content.replace('import com.google.firebase.auth.FirebaseAuth\n', 'import com.google.firebase.auth.FirebaseAuth\nimport com.google.firebase.auth.FirebaseAuthException\n')

with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'w') as f:
    f.write(content)
