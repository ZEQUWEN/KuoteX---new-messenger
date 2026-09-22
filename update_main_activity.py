import re
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    text = f.read()

text = text.replace(
    "val isDarkThemeEnabled by viewModel.isDarkThemeEnabled.collectAsState()",
    "val isDarkThemeEnabled by viewModel.isDarkThemeEnabled.collectAsState()\n                val isAutoThemeEnabled by viewModel.isAutoThemeEnabled.collectAsState()\n                val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()"
)

text = text.replace(
    "NeonMessengerTheme(darkTheme = isDarkThemeEnabled,",
    "NeonMessengerTheme(darkTheme = if (isAutoThemeEnabled) systemDarkTheme else isDarkThemeEnabled,"
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(text)
