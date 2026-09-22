import re

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "r") as f:
    content = f.read()

if "import androidx.compose.animation.ExperimentalSharedTransitionApi" not in content:
    content = content.replace("package com.example.ui", "package com.example.ui\nimport androidx.compose.animation.ExperimentalSharedTransitionApi\nimport androidx.compose.animation.AnimatedVisibilityScope\nimport androidx.compose.animation.SharedTransitionScope")

target = """                val avatarScale = (1f - (scrollOffset / 300f)).coerceIn(0.6f, 1f)
                val avatarAlpha = (1f - (scrollOffset / 250f)).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            scaleX = avatarScale
                            scaleY = avatarScale
                            alpha = avatarAlpha
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {"""

replacement = """                val avatarScale = (1f - (scrollOffset / 300f)).coerceIn(0.6f, 1f)
                val avatarAlpha = (1f - (scrollOffset / 250f)).coerceIn(0f, 1f)
                
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                
                var avatarModifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = avatarScale
                        scaleY = avatarScale
                        alpha = avatarAlpha
                    }
                    
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        avatarModifier = avatarModifier.sharedElement(
                            state = rememberSharedContentState(key = "avatar_${chat.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
                
                avatarModifier = avatarModifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)

                Box(
                    modifier = avatarModifier,
                    contentAlignment = Alignment.Center
                ) {"""

content = content.replace(target, replacement)

# Patch title
target_title = """                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )"""

replacement_title = """                    var titleModifier: Modifier = Modifier
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            titleModifier = titleModifier.sharedElement(
                                state = rememberSharedContentState(key = "title_${chat.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
                    
                    Text(
                        text = chat.title,
                        modifier = titleModifier,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )"""

content = content.replace(target_title, replacement_title)

# Update OptIn
if "ExperimentalSharedTransitionApi::class" not in content.replace("import ", ""):
    content = content.replace("@OptIn(ExperimentalMaterial3Api::class)", "@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)")

with open("app/src/main/java/com/example/ui/BotProfileScreen.kt", "w") as f:
    f.write(content)
