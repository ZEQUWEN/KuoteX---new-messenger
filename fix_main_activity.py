import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    lines = f.readlines()

new_lines = []
imports_seen = set()

for line in lines:
    if line.startswith("import "):
        if line not in imports_seen:
            imports_seen.add(line)
            new_lines.append(line)
    else:
        new_lines.append(line)

content = "".join(new_lines)
content = content.replace("import com.google.firebase.FirebaseMessaging", "import com.google.firebase.FirebaseMessaging\nimport com.google.firebase.FirebaseApp\nimport com.google.firebase.FirebaseOptions")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
