import re

with open("app/src/main/java/com/example/ui/MainScreen.kt", "r") as f:
    content = f.read()

target = """MyProfileScreen(viewModel, mainNavController)"""
replacement = """AccountScreen(onBack = { mainNavController.popBackStack() })"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/MainScreen.kt", "w") as f:
        f.write(content)
    print("Replaced!")
else:
    print("Target not found.")
