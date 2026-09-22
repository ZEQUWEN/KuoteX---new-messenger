import re

with open('app/src/main/java/com/example/ui/ChatScreen.kt', 'r') as f:
    content = f.read()

# Find the ProfileScreen function
profile_screen_start = content.find('fun ProfileScreen(viewModel: AppViewModel, chatId: String, navController: NavController) {')

# The block to replace is:
# val statusText = if (isLive) {
# ...
# } else {
#     "last seen recently"
# }
# 
# val statusColor = if (isLive) Color(0xFFFF1744) else if (isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
# 
# Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor, fontWeight = if (isLive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)

replacement = '''
                val isOnline = presence?.isOnline == true
                val lastSeen = presence?.lastSeen ?: 0L

                // Rotating indicator logic
                val rotatingStatuses = listOf("Online", "Recently Online", "Month Ago", "Long Ago")
                val rotatingColors = listOf(Color(0xFF4CAF50), Color(0xFF81C784), MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f))
                
                var rotatingIndex by remember { mutableIntStateOf(0) }
                
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(2000)
                        rotatingIndex = (rotatingIndex + 1) % rotatingStatuses.size
                    }
                }

                androidx.compose.animation.AnimatedContent(
                    targetState = rotatingIndex,
                    transitionSpec = {
                        androidx.compose.animation.slideInVertically { height -> height } + androidx.compose.animation.fadeIn() togetherWith
                        androidx.compose.animation.slideOutVertically { height -> -height } + androidx.compose.animation.fadeOut()
                    },
                    label = "status_rotation"
                ) { targetIndex ->
                    Text(
                        text = rotatingStatuses[targetIndex], 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = rotatingColors[targetIndex]
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
'''

# We need to inject this inside the Column of Scaffold topBar is empty but content has Column
# Let's use regex to replace it carefully.

pattern = re.compile(r'val statusText = if \(isLive\) \{.*?Text\(statusText, style = MaterialTheme\.typography\.bodyMedium, color = statusColor, fontWeight = if \(isLive\) androidx\.compose\.ui\.text\.font\.FontWeight\.Bold else androidx\.compose\.ui\.text\.font\.FontWeight\.Normal\)\s*Spacer\(modifier = Modifier\.height\(32\.dp\)\)', re.DOTALL)

new_content = pattern.sub(replacement.strip(), content)

with open('app/src/main/java/com/example/ui/ChatScreen.kt', 'w') as f:
    f.write(new_content)

print("ProfileScreen updated!")
