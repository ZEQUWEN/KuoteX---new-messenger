import re

with open("app/src/main/java/com/example/ui/ChatScreen.kt", "r") as f:
    content = f.read()

target = """                TopAppBar(
                title = { 
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!chat.isGroup && !chat.isChannel) {
                                    navController.navigate("profile/${chat.id}")
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(chat.title)"""

replacement = """                TopAppBar(
                title = {
                    val sharedTransitionScope = LocalSharedTransitionScope.current
                    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!chat.isGroup && !chat.isChannel) {
                                    navController.navigate("profile/${chat.id}")
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        // Avatar with shared element transition
                        var avatarModifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                        
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                avatarModifier = avatarModifier.sharedElement(
                                    state = rememberSharedContentState(key = "avatar_${chat.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                        
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data("https://picsum.photos/seed/${chat.id}/400")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Avatar",
                            modifier = avatarModifier,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column {
                            var titleModifier: Modifier = Modifier
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    titleModifier = titleModifier.sharedElement(
                                        state = rememberSharedContentState(key = "title_${chat.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            }
                            
                            Text(chat.title, modifier = titleModifier)"""

if target in content:
    content = content.replace(target, replacement)
    print("ChatScreen patched successfully")
else:
    print("Target not found in ChatScreen")

# Also need to import ExperimentalSharedTransitionApi if we use @OptIn on ChatScreen, but we don't necessarily need it if we cast.
# Wait, LocalSharedTransitionScope is defined in com.example.ui, so it's already available.

with open("app/src/main/java/com/example/ui/ChatScreen.kt", "w") as f:
    f.write(content)
