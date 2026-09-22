with open('app/src/main/java/com/example/MyApplication.kt', 'r') as f:
    content = f.read()

imports = '''import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
'''

content = content.replace('import com.google.firebase.FirebaseApp', imports + 'import com.google.firebase.FirebaseApp')

appcheck_init = '''FirebaseApp.initializeApp(this, options)
                Log.i("MyApplication", "FirebaseApp initialized with project: ${options.projectId}")
                
                Firebase.appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.i("MyApplication", "Firebase App Check initialized with Play Integrity")'''

content = content.replace('FirebaseApp.initializeApp(this, options)\n                Log.i("MyApplication", "FirebaseApp initialized with project: ${options.projectId}")', appcheck_init)

with open('app/src/main/java/com/example/MyApplication.kt', 'w') as f:
    f.write(content)
