import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

import_statement = "import com.google.firebase.FirebaseApp\nimport com.google.firebase.FirebaseOptions\n"

if "import com.google.firebase.FirebaseApp" not in content:
    content = content.replace("import com.google.firebase.FirebaseMessaging", import_statement + "import com.google.firebase.FirebaseMessaging")

firebase_init = """
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("mock-project")
                    .setApplicationId("1:1234567890:android:abcdef123456")
                    .setApiKey("mock-api-key")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            Log.e("Firebase", "Firebase initialization failed: ${e.message}")
        }
"""

if "FirebaseApp.getApps(this).isEmpty()" not in content:
    content = content.replace("super.onCreate(savedInstanceState)", "super.onCreate(savedInstanceState)\n" + firebase_init)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
