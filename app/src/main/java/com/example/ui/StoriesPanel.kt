package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

data class Story(
    val id: String, 
    val author: String, 
    val avatarUrl: String, 
    val mediaUrl: String, 
    val isViewed: Boolean = false,
    val isLive: Boolean = false,
    val isVerified: Boolean = false,
    val ringColors: List<Color> = emptyList()
)

val defaultSampleStories = listOf(
    Story(
        id = "1", 
        author = "Моя история", 
        avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200", 
        mediaUrl = "https://picsum.photos/400/800?random=1", 
        isViewed = false, 
        isLive = false, 
        isVerified = false,
        ringColors = listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF), Color(0xFF00E676), Color(0xFF7C4DFF))
    ),
    Story(
        id = "2", 
        author = "KuoteX", 
        avatarUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=200", 
        mediaUrl = "https://picsum.photos/400/800?random=2", 
        isViewed = false, 
        isLive = false, 
        isVerified = true,
        ringColors = listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF00E676))
    ),
    Story(
        id = "3", 
        author = "BIOAURA", 
        avatarUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=200", 
        mediaUrl = "https://picsum.photos/400/800?random=3", 
        isViewed = false, 
        isLive = false, 
        isVerified = true,
        ringColors = listOf(Color(0xFF9C27B0), Color(0xFF00E5FF), Color(0xFF00E676), Color(0xFF9C27B0))
    ),
    Story(
        id = "4", 
        author = "Ebout Data", 
        avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200", 
        mediaUrl = "https://picsum.photos/400/800?random=4", 
        isViewed = false, 
        isLive = false, 
        isVerified = false,
        ringColors = listOf(Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF00B0FF), Color(0xFF00E676))
    ),
    Story(
        id = "5", 
        author = "PHYGITAL+", 
        avatarUrl = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=200", 
        mediaUrl = "https://picsum.photos/400/800?random=5", 
        isViewed = false, 
        isLive = false, 
        isVerified = true,
        ringColors = listOf(Color(0xFFD500F9), Color(0xFFFF4081), Color(0xFF7C4DFF), Color(0xFFD500F9))
    ),
    Story(
        id = "6", 
        author = "Alice", 
        avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200", 
        mediaUrl = "https://picsum.photos/400/800?random=6", 
        isViewed = false, 
        isLive = true, 
        isVerified = false,
        ringColors = listOf(Color(0xFFFF1744), Color(0xFFFF4081), Color(0xFF00E5FF), Color(0xFFFF1744))
    ),
    Story(
        id = "7", 
        author = "Pavel", 
        avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200", 
        mediaUrl = "https://picsum.photos/400/800?random=7", 
        isViewed = true, 
        isLive = false, 
        isVerified = false,
        ringColors = listOf(Color(0xFF757575), Color(0xFF9E9E9E), Color(0xFF757575))
    )
)

fun getEffectiveStories(
    activeAccount: UserAccount?,
    activeStreams: Map<String, LiveStreamSession> = emptyMap(),
    isSelfStreaming: Boolean = false
): List<Story> {
    return defaultSampleStories.map { s ->
        val isMyStory = s.id == "1"
        val isHostStreaming = if (isMyStory) isSelfStreaming else (activeStreams.containsKey(s.id) || s.isLive)
        s.copy(
            author = if (isMyStory && activeAccount != null) "Моя история" else s.author,
            avatarUrl = if (isMyStory && activeAccount != null && activeAccount.profilePicUrl.isNotBlank()) activeAccount.profilePicUrl else s.avatarUrl,
            isLive = isHostStreaming
        )
    }
}

/**
 * Telegram-style Compact Overlapping Avatar Group shown in TopAppBar header
 */
@Composable
fun CompactStoryAvatarGroup(
    stories: List<Story>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val displayStories = remember(stories) {
        val unviewed = stories.filter { !it.isViewed && it.id != "1" }
        if (unviewed.isNotEmpty()) unviewed.take(3) else stories.take(3)
    }

    if (displayStories.isEmpty()) return

    val avatarSize = 28.dp
    val overlapOffset = 16.dp
    val totalWidth = avatarSize + overlapOffset * (displayStories.size - 1).coerceAtLeast(0)

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(avatarSize)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Истории, нажмите чтобы показать все"
            },
        contentAlignment = Alignment.CenterStart
    ) {
        displayStories.forEachIndexed { index, story ->
            val ringColors = story.ringColors.ifEmpty {
                listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFF00E676))
            }
            Box(
                modifier = Modifier
                    .offset(x = overlapOffset * index)
                    .size(avatarSize)
                    .zIndex((displayStories.size - index).toFloat())
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .drawWithCache {
                        val brush = Brush.sweepGradient(ringColors)
                        onDrawBehind {
                            drawCircle(
                                brush = brush,
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    .padding(2.dp)
            ) {
                AsyncImage(
                    model = story.avatarUrl,
                    contentDescription = story.author,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun LivePulsatingRing(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_ring")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val gradientColors = listOf(
        Color(0xFFFF1744), // Vivid Red
        Color(0xFFFF4081), // Pink
        Color(0xFF9C27B0), // Purple
        Color(0xFF00E5FF), // Cyan
        Color(0xFFFF1744)  // Loop
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        },
        contentAlignment = Alignment.Center
    ) {
        // Rotating gradient border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val brush = Brush.sweepGradient(
                        colors = gradientColors
                    )
                    onDrawBehind {
                        drawCircle(
                            brush = brush,
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
                .padding(4.dp)
        ) {
            content()
        }

        // "LIVE" Badge overlay at the bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 4.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFE50914), Color(0xFFFF2A6D))
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Color.White, CircleShape)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "LIVE",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun JoinStreamConfirmationDialog(
    streamerName: String,
    streamerAvatar: String,
    streamTitle: String = "Прямой эфир",
    viewerCount: Int = 120,
    priceStars: Int = 0,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .clickable(enabled = false) {}, // Prevent dismiss when clicking card
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Streamer Avatar with Live Pulse
                    LivePulsatingRing(modifier = Modifier.size(80.dp)) {
                        AsyncImage(
                            model = streamerAvatar.takeIf { it.isNotBlank() } ?: "https://i.pravatar.cc/150?u=streamer",
                            contentDescription = "Streamer Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = streamerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(Color(0xFFE91E63).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFFE91E63), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ПРЯМОЙ ЭФИР • 👁 $viewerCount зрителей",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE91E63)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = streamTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (priceStars > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFFFFD700).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "⭐️ Платные сообщения: $priceStars звезд", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6A100))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Вы собираетесь подключиться к видеотрансляции. Желаете войти в эфир прямо сейчас?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(23.dp)
                        ) {
                            Text("Отмена")
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                            shape = RoundedCornerShape(23.dp)
                        ) {
                            Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Войти", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoriesPanel(
    onStorySwipe: (Boolean) -> Unit, 
    onAvatarClick: (Story) -> Unit = {},
    onLiveClick: (String) -> Unit = {},
    viewModel: AppViewModel? = null,
    avatarScale: Float = 1f
) {
    var selectedStory by remember { mutableStateOf<Story?>(null) }
    var storyToConfirmLive by remember { mutableStateOf<Story?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    val fallbackStreams = remember { MutableStateFlow(emptyMap<String, LiveStreamSession>()) }
    val activeStreams by (viewModel?.activeStreams ?: fallbackStreams).collectAsStateWithLifecycle()
    
    val fallbackAccount = remember { MutableStateFlow<UserAccount?>(null) }
    val activeAccount by (viewModel?.activeAccount ?: fallbackAccount).collectAsStateWithLifecycle()
    val isSelfStreaming = activeAccount?.let { viewModel?.isUserStreaming(it.id) } ?: false

    val stories = remember(activeStreams, isSelfStreaming, activeAccount) {
        defaultSampleStories.map { s ->
            val isMyStory = s.id == "1"
            val isHostStreaming = if (isMyStory) isSelfStreaming else (activeStreams.containsKey(s.id) || s.isLive)
            s.copy(
                author = if (isMyStory && activeAccount != null) "Моя история" else s.author,
                avatarUrl = if (isMyStory && activeAccount != null && activeAccount?.profilePicUrl?.isNotBlank() == true) activeAccount!!.profilePicUrl else s.avatarUrl,
                isLive = isHostStreaming
            )
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            onStorySwipe(true)
        } else if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            onStorySwipe(false)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(stories, key = { it.id }) { story ->
            val isMyStory = story.id == "1"
            val currentScale = avatarScale.coerceIn(0.2f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .clickable { 
                        if (story.isLive) {
                            if (isMyStory && isSelfStreaming) {
                                // If user is the host streaming, enter directly
                                onLiveClick(story.id)
                            } else {
                                // Show confirmation dialog before joining
                                storyToConfirmLive = story
                            }
                        } else {
                            selectedStory = story 
                        }
                    }
            ) {
                if (story.isLive) {
                    LivePulsatingRing(
                        modifier = Modifier.size(68.dp)
                    ) {
                        AsyncImage(
                            model = story.avatarUrl,
                            contentDescription = "Live Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                } else {
                    val ringColors = remember(story) {
                        when {
                            story.isViewed -> listOf(Color(0xFF555555), Color(0xFF666666), Color(0xFF555555))
                            story.ringColors.isNotEmpty() -> story.ringColors
                            story.id == "1" -> listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF), Color(0xFF00E676), Color(0xFF7C4DFF))
                            story.id == "2" -> listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF00E676))
                            else -> listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFF00E676))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .drawWithCache {
                                val brush = Brush.sweepGradient(ringColors)
                                onDrawBehind {
                                    drawCircle(
                                        brush = brush,
                                        radius = size.minDimension / 2f,
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                            }
                            .padding(3.5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        AsyncImage(
                            model = story.avatarUrl,
                            contentDescription = story.author,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )

                        if (isMyStory) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF))),
                                        CircleShape
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Добавить историю",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.widthIn(max = 76.dp)
                ) {
                    Text(
                        text = if (isMyStory && story.isLive) "🔴 В эфире" else story.author,
                        fontSize = 11.5.sp,
                        color = if (story.isLive) Color(0xFFFF2A6D) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (story.isLive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (story.isVerified) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Верифицирован",
                            tint = Color(0xFF29B6F6),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }

    val currentStoryToConfirm = storyToConfirmLive
    if (currentStoryToConfirm != null) {
        val streamSession = activeStreams[currentStoryToConfirm.id]
        val targetStreamId = currentStoryToConfirm.id
        JoinStreamConfirmationDialog(
            streamerName = currentStoryToConfirm.author,
            streamerAvatar = currentStoryToConfirm.avatarUrl,
            streamTitle = streamSession?.title ?: "KuoteX Live Broadcast 🚀",
            viewerCount = streamSession?.viewerCount ?: 142,
            priceStars = streamSession?.commentPriceStars ?: 0,
            onConfirm = {
                storyToConfirmLive = null
                onLiveClick(targetStreamId)
            },
            onDismiss = {
                storyToConfirmLive = null
            }
        )
    }

    if (selectedStory != null) {
        StoryViewerPopup(
            story = selectedStory!!, 
            onAvatarClick = onAvatarClick,
            onJoinLive = { streamId -> 
                val story = stories.find { it.id == streamId } ?: selectedStory!!
                selectedStory = null
                storyToConfirmLive = story
            }
        ) {
            selectedStory = null
        }
    }
}

@Composable
fun StoryViewerPopup(
    story: Story, 
    onAvatarClick: (Story) -> Unit = {}, 
    onJoinLive: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(story) {
        val duration = 15000
        val steps = 100
        val delayTime = (duration / steps).toLong()
        for (i in 1..steps) {
            delay(delayTime)
            progress = i.toFloat() / steps
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = story.mediaUrl,
                contentDescription = "Story Media",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        onDismiss()
                        onAvatarClick(story)
                    }.padding(end = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    if (story.isLive) {
                        LivePulsatingRing(modifier = Modifier.size(44.dp)) {
                            AsyncImage(
                                model = story.avatarUrl,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                    } else {
                        AsyncImage(
                            model = story.avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = story.author,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (story.isLive) {
                            Text(
                                text = "🔴 В эфире",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                if (story.isLive) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            onDismiss()
                            onJoinLive(story.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Смотреть трансляцию", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            
            // Allow tap to skip if not clicking the live button
            if (!story.isLive) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { onDismiss() })
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { onDismiss() })
                }
            }
        }
    }
}
