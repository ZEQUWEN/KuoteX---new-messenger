import re

# 1. Fix AuthScreens @Composable
with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'r') as f:
    content = f.read()
content = content.replace('@Composable\n@Composable\nfun LoginScreen', '@Composable\nfun LoginScreen')
with open('app/src/main/java/com/example/ui/AuthScreens.kt', 'w') as f:
    f.write(content)

# 2. Fix AppViewModel context and FirestoreUserRoleManager method
with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'r') as f:
    content = f.read()
content = content.replace('com.example.ui.PresenceManager.updatePresence(repository.context, user.uid, true)', 'com.example.ui.PresenceManager.updatePresence(context, user.uid, true)')
with open('app/src/main/java/com/example/ui/AppViewModel.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/data/FirestoreUserRoleManager.kt', 'r') as f:
    content = f.read()
content = content.replace('private fun syncUserRoleToFirestore', 'fun syncUserRoleToFirestore')
with open('app/src/main/java/com/example/data/FirestoreUserRoleManager.kt', 'w') as f:
    f.write(content)

# 3. Fix FirebaseAuthManager
with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'r') as f:
    content = f.read()

# Remove duplicate imports
import_list = ['import android.content.Context', 'import androidx.credentials.CredentialManager', 'import androidx.credentials.GetCredentialRequest', 'import androidx.credentials.CustomCredential', 'import com.google.android.libraries.identity.googleid.GetGoogleIdOption', 'import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential', 'import com.google.firebase.auth.GoogleAuthProvider']
for imp in import_list:
    content = content.replace(imp + '\n' + imp, imp)

# Replace AuthResult.Error(Exception(...)) with AuthResult.Error(Exception(...).message ?: "Error")
content = content.replace('AuthResult.Error(Exception("Firebase Auth returned null user after Google Sign-In"))', 'AuthResult.Error("Firebase Auth returned null user after Google Sign-In")')
content = content.replace('AuthResult.Error(Exception("Unexpected credential type: ${credential.type}"))', 'AuthResult.Error("Unexpected credential type: ${credential.type}")')
content = content.replace('AuthResult.Error(e)', 'AuthResult.Error(e.message ?: "Google Sign-In failed")')

with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'w') as f:
    f.write(content)

# 4. Add coroutines play services to build.gradle.kts
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()
if 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services' not in content:
    content = content.replace('dependencies {', 'dependencies {\n  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")')
with open('app/build.gradle.kts', 'w') as f:
    f.write(content)

print("Fixed build issues.")
