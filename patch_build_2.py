with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

content = content.replace('implementation("com.google.firebase:firebase-appcheck-ktx")', 'implementation("com.google.firebase:firebase-appcheck")')

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
