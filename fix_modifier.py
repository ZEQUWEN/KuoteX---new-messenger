import re

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "r") as f:
    content = f.read()

content = content.replace(".androidx.compose.ui.graphics.graphicsLayer", ".graphicsLayer")

if "import androidx.compose.ui.graphics.graphicsLayer" not in content:
    content = content.replace("import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer")

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "w") as f:
    f.write(content)
