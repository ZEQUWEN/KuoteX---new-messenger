import re

with open("app/src/main/AndroidManifest.xml", "r") as f:
    content = f.read()

# Add POST_NOTIFICATIONS
if "android.permission.POST_NOTIFICATIONS" not in content:
    target = '<uses-permission android:name="android.permission.INTERNET" />'
    replacement = '<uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />'
    content = content.replace(target, replacement)

# Add FCM Service
service_xml = """        <service
            android:name=".FCMService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>"""

if "com.google.firebase.MESSAGING_EVENT" not in content:
    target2 = "</application>"
    replacement2 = service_xml + "\n    </application>"
    content = content.replace(target2, replacement2)

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(content)
