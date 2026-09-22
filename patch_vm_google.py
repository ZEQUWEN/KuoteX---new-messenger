import re

with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'r') as f:
    content = f.read()

replacement = '''    suspend fun signInWithGoogle(context: android.content.Context): AuthResult<FirebaseUserInfo> {
        val result = FirebaseAuthManager.signInWithGoogle(context)
        if (result is AuthResult.Success) {
            val user = result.data
            com.example.ui.PresenceManager.updatePresence(repository.context, user.uid, true)
            com.example.data.FirestoreUserRoleManager.syncUserRoleToFirestore(
                userId = user.uid,
                isAdmin = false,
                isModerator = false,
                updatedBy = "Google Auth"
            )
        }
        return result
    }

    suspend fun signInAnonymouslyWithFirebase()'''

content = content.replace('    suspend fun signInAnonymouslyWithFirebase()', replacement)

with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'w') as f:
    f.write(content)
