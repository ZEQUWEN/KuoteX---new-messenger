import re

with open("app/src/main/java/com/example/ui/StorageSettingsScreen.kt", "r") as f:
    content = f.read()

target = "viewModel.clearCache(selectedNames)"
replacement = """viewModel.clearCache(selectedNames)
                                        CacheCalculator.clearAppCache(context)
                                        CacheCalculator.forceScan(context, scope)"""

content = content.replace(target, replacement)

with open("app/src/main/java/com/example/ui/StorageSettingsScreen.kt", "w") as f:
    f.write(content)
