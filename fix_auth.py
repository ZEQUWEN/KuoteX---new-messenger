with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
google_auth_lines = []
in_google_auth = False

for i, line in enumerate(lines):
    if line.startswith('    suspend fun signInWithGoogle'):
        in_google_auth = True
    
    if in_google_auth:
        google_auth_lines.append(line)
        if line.strip() == '}' and lines[i-1].strip() == '}':
            in_google_auth = False
    else:
        new_lines.append(line)

# Now find where 'private val auth = FirebaseAuth.getInstance()' is and insert google_auth_lines after it.
insert_idx = -1
for i, line in enumerate(new_lines):
    if 'private val auth = FirebaseAuth.getInstance()' in line:
        insert_idx = i + 1
        break

if insert_idx != -1:
    new_lines = new_lines[:insert_idx] + google_auth_lines + new_lines[insert_idx:]

with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'w') as f:
    f.writelines(new_lines)

# Also add import kotlinx.coroutines.tasks.await
with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'r') as f:
    content = f.read()
if 'import kotlinx.coroutines.tasks.await' not in content:
    content = content.replace('import android.content.Context', 'import android.content.Context\nimport kotlinx.coroutines.tasks.await')
with open('app/src/main/java/com/example/auth/FirebaseAuthManager.kt', 'w') as f:
    f.write(content)
