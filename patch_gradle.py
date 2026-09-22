import re

with open("app/build.gradle.kts", "r") as f:
    content = f.read()

target = "implementation(platform(libs.firebase.bom))"
replacement = "implementation(platform(libs.firebase.bom))\n  implementation(\"com.google.firebase:firebase-messaging-ktx\")"
content = content.replace(target, replacement)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
