with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'r') as f:
    content = f.read()

google_auth_func = '''
    suspend fun signInWithGoogle(context: Context): AuthResult<FirebaseUserInfo> {
        return try {
            val credentialManager = CredentialManager.create(context)
            // Use the client ID from your google-services.json oauth_client type 3 (Web client)
            val webClientId = "372420700937-dummyclientidforauth.apps.googleusercontent.com"
            
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val auth = getAuth() ?: return AuthResult.Error("Firebase Auth not initialized")
                
                val authResult = kotlinx.coroutines.tasks.await(auth.signInWithCredential(firebaseCredential))
                val user = authResult.user
                if (user != null) {
                    val userInfo = mapFirebaseUser(user)
                    _currentUser.value = userInfo
                    _authState.value = AuthState.Authenticated(userInfo)
                    AuthResult.Success(userInfo)
                } else {
                    AuthResult.Error("Firebase Auth returned null user after Google Sign-In")
                }
            } else {
                AuthResult.Error("Unexpected credential type: ${credential.type}")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthManager", "Google Sign-In failed", e)
            AuthResult.Error(e.message ?: "Google Sign-In failed")
        }
    }
'''

content = content.replace('object FirebaseAuthManager {', 'import com.google.firebase.auth.FirebaseAuthException\nobject FirebaseAuthManager {\n' + google_auth_func)

with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'w') as f:
    f.write(content)

