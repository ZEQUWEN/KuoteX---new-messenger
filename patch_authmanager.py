import re

with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'r') as f:
    content = f.read()

new_imports = '''import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
'''

content = content.replace('import com.google.firebase.auth.FirebaseAuth', new_imports + 'import com.google.firebase.auth.FirebaseAuth')

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
                
                val authResult = kotlinx.coroutines.tasks.await(auth.signInWithCredential(firebaseCredential))
                val user = authResult.user
                if (user != null) {
                    AuthResult.Success(mapUser(user))
                } else {
                    AuthResult.Error(Exception("Firebase Auth returned null user after Google Sign-In"))
                }
            } else {
                AuthResult.Error(Exception("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            AuthResult.Error(e)
        }
    }
'''

content = content.replace('object FirebaseAuthManager {', 'object FirebaseAuthManager {' + google_auth_func)

with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'w') as f:
    f.write(content)
