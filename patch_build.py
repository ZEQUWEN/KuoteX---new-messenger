with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

content = content.replace(
    'implementation("com.google.firebase:firebase-analytics")',
    'implementation("com.google.firebase:firebase-analytics")\n  implementation("com.google.firebase:firebase-appcheck-playintegrity")\n  implementation("com.google.firebase:firebase-appcheck-ktx")'
)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
