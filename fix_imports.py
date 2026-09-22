import re

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "r") as f:
    content = f.read()

if "import androidx.compose.foundation.border" not in content:
    content = content.replace("import androidx.compose.foundation.background", "import androidx.compose.foundation.background\nimport androidx.compose.foundation.border")

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/ui/MainScreen.kt", "r") as f:
    content = f.read()

# I will just replace the ExperimentalMaterial3Api with ExperimentalMaterial3Api and ExperimentalSharedTransitionApi on the composable that is throwing it, or on top of file.
content = content.replace("@OptIn(ExperimentalMaterial3Api::class)", "@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)")

with open("app/src/main/java/com/example/ui/MainScreen.kt", "w") as f:
    f.write(content)
