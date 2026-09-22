with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

content = content.replace('dependencies {', 'dependencies {\n  implementation(libs.androidx.credentials)\n  implementation(libs.androidx.credentials.play.services.auth)\n  implementation(libs.googleid)')

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
