with open('gradle/libs.versions.toml', 'r') as f:
    content = f.read()

content = content.replace('[versions]', '[versions]\ncredentials = "1.3.0"\ngoogleid = "1.1.1"')
content = content.replace('[libraries]', '[libraries]\nandroidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }\nandroidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }\ngoogleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }')

with open('gradle/libs.versions.toml', 'w') as f:
    f.write(content)
