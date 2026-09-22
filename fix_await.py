with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'r') as f:
    content = f.read()

content = content.replace('import kotlinx.coroutines.tasks.await', '')
content = content.replace('import com.google.firebase.auth.FirebaseAuthException', 'import com.google.firebase.auth.FirebaseAuthException\nimport kotlinx.coroutines.tasks.await')

with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'w') as f:
    f.write(content)
