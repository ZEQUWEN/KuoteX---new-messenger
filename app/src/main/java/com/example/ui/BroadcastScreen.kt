package com.example.ui

import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

enum class BroadcastCameraMode {
    STREAM, PHOTO, VIDEO
}

data class FloatingReaction(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val startXFraction: Float = Random.nextFloat() * 0.4f + 0.5f,
    val scale: Float = Random.nextFloat() * 0.5f + 0.8f
)

private fun getUsernameColor(username: String): Color {
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), 
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFF8A65)
    )
    return colors[kotlin.math.abs(username.hashCode()) % colors.size]
}

private fun formatStreamDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BroadcastScreen(
    viewModel: AppViewModel,
    navController: NavController,
    targetStreamId: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val activeStreams by viewModel.activeStreams.collectAsStateWithLifecycle()
    
    val myUserId = activeAccount?.id ?: "1"
    val isHost = targetStreamId == null || targetStreamId == myUserId || targetStreamId == "1"
    
    var cameraMode by remember { mutableStateOf(BroadcastCameraMode.STREAM) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var isFlashOn by remember { mutableStateOf(false) }
    val targetResolution = remember { Size(1280, 720) }
    
    // Stream Player Settings State (Resolution, Hardware Acceleration, Latency)
    var showPlayerSettingsSheet by remember { mutableStateOf(false) }
    var selectedResolution by remember { mutableStateOf("1080p") } // "Auto", "1080p", "720p", "480p", "360p"
    var isHardwareAccelerationEnabled by remember { mutableStateOf(true) }
    var isLowLatencyMode by remember { mutableStateOf(true) }

    // AI Image-to-Image Real-Time Stylization State
    var isImageToImageEnabled by remember { mutableStateOf(false) }
    var selectedAiStyle by remember { mutableStateOf("Киберпанк Неон") }
    var aiStylingStrength by remember { mutableFloatStateOf(0.75f) }
    
    // Stream Reporting State
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("Спам или мошенничество") }
    var reportDetails by remember { mutableStateOf("") }
    var blockStreamerOnReport by remember { mutableStateOf(false) }
    
    // Host Settings State (matching Telegram video)
    var showStartStreamSheet by remember { mutableStateOf(false) }
    var showAccountPickerSheet by remember { mutableStateOf(false) }
    var showRtmpDetailsDialog by remember { mutableStateOf(false) }
    var showExclusionsDialog by remember { mutableStateOf(false) }
    var showEndStreamConfirmDialog by remember { mutableStateOf(false) }
    
    var selectedAudience by remember { mutableStateOf("Все") }
    var excludedUserIds by remember { mutableStateOf(setOf<String>()) }
    var closeFriendIds by remember { mutableStateOf(setOf("u1", "u2")) }
    var selectedUserIds by remember { mutableStateOf(setOf("u1", "u3")) }
    
    var commentsEnabled by remember { mutableStateOf(true) }
    var isPaidMessagingEnabled by remember { mutableStateOf(false) }
    var screenshotsEnabled by remember { mutableStateOf(true) }
    var commentPriceStars by remember { mutableFloatStateOf(50f) } // 0 = Free, max 35000
    var isExternalAppBroadcast by remember { mutableStateOf(false) }
    
    val isCurrentlyLive = if (isHost) {
        viewModel.isUserStreaming(myUserId)
    } else {
        true
    }
    
    val currentStream = if (isHost) {
        viewModel.getActiveStream(myUserId)
    } else {
        viewModel.getActiveStream(targetStreamId ?: "2") ?: LiveStreamSession(
            id = targetStreamId ?: "2",
            hostUserId = targetStreamId ?: "2",
            hostDisplayName = "Alice",
            hostUsername = "@alice_crypto",
            hostAvatarUrl = "https://i.pravatar.cc/150?u=alice",
            title = "KuoteX Live Stream 🚀",
            viewerCount = 142,
            commentPriceStars = 50,
            isLive = true
        )
    }
    
    var durationSeconds by remember { mutableIntStateOf(0) }
    var viewerCount by remember { mutableIntStateOf(currentStream?.viewerCount ?: 128) }
    var peakViewersSeen by remember { mutableIntStateOf(currentStream?.viewerCount ?: 128) }
    var isMuted by remember { mutableStateOf(false) }
    var showHostMenu by remember { mutableStateOf(false) }
    var commentInputText by remember { mutableStateOf("") }
    
    var floatingReactions by remember { mutableStateOf(listOf<FloatingReaction>()) }

    // Stream Session Analytics State
    val joinStartTime = remember { System.currentTimeMillis() }
    var myCommentsSentCount by remember { mutableIntStateOf(0) }
    var myReactionsSentCount by remember { mutableIntStateOf(0) }
    var myStarsDonatedAmount by remember { mutableIntStateOf(0) }
    var dropOffReason by remember { mutableStateOf("user_exit") }
    
    // Local list of stream comments
    var streamComments by remember {
        mutableStateOf(
            currentStream?.comments?.ifEmpty {
                listOf(
                    LiveComment(senderId = "u1", senderName = "Pavel", text = "Качество супер! 🔥", starsDonated = 0),
                    LiveComment(senderId = "u2", senderName = "Daria", text = "Всем привет из KuoteX Messenger!", starsDonated = 50),
                    LiveComment(senderId = "u3", senderName = "Alex", text = "Когда запуск маркетплейса ботов?", starsDonated = 100)
                )
            } ?: emptyList()
        )
    }

    // Track screen view in Firebase Analytics
    com.example.analytics.TrackScreen(
        screenName = if (isHost) "stream_host_screen" else "stream_viewer_screen",
        screenClass = "BroadcastScreen",
        params = mapOf(
            "stream_id" to (targetStreamId ?: "host"),
            "is_host" to isHost,
            "is_live" to isCurrentlyLive
        )
    )

    // Track Stream Join and Lifecycle Discard (Drop-off Rate & Watch Duration)
    LaunchedEffect(isCurrentlyLive) {
        if (isCurrentlyLive) {
            val joinLatency = (System.currentTimeMillis() - joinStartTime).coerceAtLeast(150L)
            val streamId = currentStream?.id ?: targetStreamId ?: "host"
            val hostId = if (isHost) myUserId else (targetStreamId ?: currentStream?.hostUserId)
            com.example.analytics.AnalyticsTracker.logStreamJoin(
                streamId = streamId,
                hostId = hostId,
                isHost = isHost,
                joinDurationMs = joinLatency,
                initialViewerCount = viewerCount,
                streamTitle = currentStream?.title ?: (if (isHost) "My Broadcast" else "Live Stream"),
                source = if (isHost) "host_launch" else "live_badge"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val sessionDuration = ((System.currentTimeMillis() - joinStartTime) / 1000).coerceAtLeast(1)
            val streamId = currentStream?.id ?: targetStreamId ?: "host"
            val hostId = if (isHost) myUserId else (targetStreamId ?: currentStream?.hostUserId)
            val finalDuration = maxOf(durationSeconds.toLong(), sessionDuration)
            
            // Firebase Analytics Drop-off and Retention Tracking
            com.example.analytics.AnalyticsTracker.logStreamDropOff(
                streamId = streamId,
                hostId = hostId,
                isHost = isHost,
                watchDurationSeconds = finalDuration,
                commentsSent = myCommentsSentCount,
                reactionsSent = myReactionsSentCount,
                starsDonated = myStarsDonatedAmount,
                peakViewersSeen = peakViewersSeen,
                dropOffReason = dropOffReason
            )

            // Firebase Analytics Helper: Stream Duration Summary Metric
            com.example.analytics.FirebaseAnalyticsHelper.logStreamDurationSummary(
                streamId = streamId,
                hostId = hostId,
                isHost = isHost,
                durationSeconds = finalDuration,
                peakViewers = peakViewersSeen,
                commentsCount = myCommentsSentCount,
                reactionsCount = myReactionsSentCount,
                starsEarned = myStarsDonatedAmount,
                endReason = dropOffReason
            )
        }
    }

    // Dismiss any active PiP overlay and stop background audio when entering fullscreen BroadcastScreen
    LaunchedEffect(Unit) {
        viewModel.closePipMode()
        viewModel.stopBackgroundAudio(context)
    }

    // Intercept back gesture for live stream viewer and transition smoothly into PiP
    BackHandler(enabled = !isHost && isCurrentlyLive) {
        dropOffReason = "pip_minimize"
        val sessionToPip = currentStream ?: LiveStreamSession(
            id = targetStreamId ?: "2",
            hostUserId = targetStreamId ?: "2",
            hostDisplayName = "Alice",
            hostUsername = "@alice_crypto",
            hostAvatarUrl = "https://i.pravatar.cc/150?u=alice",
            title = "KuoteX Live Stream 🚀",
            viewerCount = viewerCount,
            isLive = true
        )
        viewModel.enterPipMode(sessionToPip)
        navController.popBackStack()
        Toast.makeText(context, "Трансляция свернута в режим «Картинка в картинке»", Toast.LENGTH_SHORT).show()
    }
    
    // Simulated live timer and dynamic viewers
    LaunchedEffect(isCurrentlyLive) {
        if (isCurrentlyLive) {
            while (true) {
                delay(1000)
                durationSeconds++
                if (durationSeconds % 5 == 0) {
                    val delta = Random.nextInt(-3, 6)
                    viewerCount = (viewerCount + delta).coerceAtLeast(1)
                    peakViewersSeen = maxOf(peakViewersSeen, viewerCount)
                    if (isHost) {
                        viewModel.updateStreamViewerCount(myUserId, viewerCount)
                    }
                }
            }
        }
    }
    
    // Simulated incoming comments from audience during live stream
    LaunchedEffect(isCurrentlyLive) {
        if (isCurrentlyLive) {
            val sampleAudienceComments = listOf(
                "Отличный стрим!",
                "⭐️ Отправил 100 звезд на развитие проекта!",
                "Очень крутой дизайн и анимации в KuoteX",
                "Привет из Москвы! 🇷🇺",
                "Как работает шифрование?",
                "Ура, прямой эфир! 🎉",
                "Супер качество видео!",
                "⭐️ Донат 250 звезд!",
                "KuoteX лучший мессенджер 🚀"
            )
            val sampleSenders = listOf("Artem", "Elena", "Dmitry", "Olga", "Nikita", "Sophia", "Vlad")
            
            while (true) {
                delay(Random.nextLong(4000, 9000))
                val randomSender = sampleSenders.random()
                val randomText = sampleAudienceComments.random()
                val stars = if (randomText.contains("⭐️")) Random.nextInt(50, 300) else 0
                val newComment = LiveComment(
                    senderId = UUID.randomUUID().toString(),
                    senderName = randomSender,
                    text = randomText,
                    starsDonated = stars
                )
                streamComments = (streamComments + newComment).takeLast(40)
                if (isHost) {
                    viewModel.addStreamComment(myUserId, newComment)
                }
            }
        }
    }
    
    // Clean up reaction particles
    LaunchedEffect(floatingReactions) {
        if (floatingReactions.isNotEmpty()) {
            delay(2800)
            floatingReactions = floatingReactions.drop(1)
        }
    }
    
    fun triggerReaction(emoji: String) {
        floatingReactions = floatingReactions + FloatingReaction(emoji = emoji)
        myReactionsSentCount++
        com.example.analytics.AnalyticsTracker.logStreamAction(
            action = "send_reaction",
            streamId = currentStream?.id,
            hostId = currentStream?.hostUserId,
            metadata = mapOf("reaction_emoji" to emoji, "is_host" to isHost)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isHost) {
            // Camera surface for Host
            BroadcastCameraPreview(
                modifier = Modifier.fillMaxSize(),
                lensFacing = lensFacing,
                targetResolution = targetResolution
            )
        } else {
            // Simulated video feed for Viewer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1A1A2E),
                                Color(0xFF16213E),
                                Color(0xFF0F3460)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LivePulsatingRing(modifier = Modifier.size(120.dp)) {
                        AsyncImage(
                            model = currentStream?.hostAvatarUrl?.takeIf { it.isNotBlank() } ?: "https://i.pravatar.cc/150?u=alice",
                            contentDescription = "Host Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = currentStream?.hostDisplayName ?: "Host",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentStream?.title ?: "Прямой эфир",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        // AI Image-to-Image visual filter overlay when enabled
        if (isImageToImageEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF9C27B0).copy(alpha = 0.15f * aiStylingStrength),
                                Color(0xFF00E5FF).copy(alpha = 0.12f * aiStylingStrength),
                                Color.Transparent
                            )
                        )
                    )
            )
            // AI Stylization indicator badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 58.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF9C27B0).copy(alpha = 0.85f), Color(0xFF0088CC).copy(alpha = 0.85f))
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AI: $selectedAiStyle (${(aiStylingStrength * 100).toInt()}%)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Gradient scrim at top and bottom for overlay readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // --- TOP BAR OVERLAY ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = {
                    if (isHost && isCurrentlyLive) {
                        showEndStreamConfirmDialog = true
                    } else if (!isHost && isCurrentlyLive) {
                        dropOffReason = "pip_minimize"
                        val sessionToPip = currentStream ?: LiveStreamSession(
                            id = targetStreamId ?: "2",
                            hostUserId = targetStreamId ?: "2",
                            hostDisplayName = "Alice",
                            hostUsername = "@alice_crypto",
                            hostAvatarUrl = "https://i.pravatar.cc/150?u=alice",
                            title = "KuoteX Live Stream 🚀",
                            viewerCount = viewerCount,
                            isLive = true
                        )
                        viewModel.enterPipMode(sessionToPip)
                        navController.popBackStack()
                        Toast.makeText(context, "Трансляция свернута в режим «Картинка в картинке»", Toast.LENGTH_SHORT).show()
                    } else {
                        dropOffReason = "navigated_back"
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            if (isCurrentlyLive) {
                // Live Stream Info Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_pulse"
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFF1744).copy(alpha = dotAlpha), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatStreamDuration(durationSeconds),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = "Viewers",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$viewerCount",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (isHost && (currentStream?.totalStarsEarned ?: 0) > 0) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "⭐️ ${currentStream?.totalStarsEarned}",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (isHost) {
                // Header account selector (SARATOSHI / изменить >) as shown in the video
                val userDisplayName = activeAccount?.displayName?.ifBlank { "SARATOSHI" } ?: "SARATOSHI"
                val avatarUrl = activeAccount?.profilePicUrl?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/seed/my/100"
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                        .clickable { showAccountPickerSheet = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Account Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = userDisplayName,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "изменить >",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Top Right Actions (Cleaned up: Quality, Image-to-Image & End Stream moved into Settings)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isHost && !isCurrentlyLive) {
                    IconButton(
                        onClick = { isFlashOn = !isFlashOn },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            if (isFlashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = "Flash",
                            tint = if (isFlashOn) Color(0xFFFFD700) else Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box {
                    IconButton(
                        onClick = { showHostMenu = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = Color.White)
                    }

                    DropdownMenu(
                        expanded = showHostMenu,
                        onDismissRequest = { showHostMenu = false }
                    ) {
                        if (isCurrentlyLive) {
                            DropdownMenuItem(
                                text = { Text("Настройки стрима и качество ($selectedResolution)") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Tune, contentDescription = null, tint = Color(0xFF0088CC))
                                },
                                onClick = {
                                    showHostMenu = false
                                    showPlayerSettingsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Режим Image-to-Image (AI)")
                                        if (isImageToImageEnabled) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFF9C27B0),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "ВКЛ",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isImageToImageEnabled) Color(0xFFE040FB) else Color(0xFF0088CC)
                                    )
                                },
                                onClick = {
                                    showHostMenu = false
                                    showPlayerSettingsSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Картинка в картинке (PiP)") },
                                leadingIcon = {
                                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = null, tint = Color(0xFF0088CC))
                                },
                                onClick = {
                                    showHostMenu = false
                                    val sessionToPip = currentStream ?: LiveStreamSession(
                                        id = targetStreamId ?: (if (isHost) myUserId else "2"),
                                        hostUserId = if (isHost) myUserId else (targetStreamId ?: "2"),
                                        hostDisplayName = if (isHost) (activeAccount?.displayName ?: "Host") else (currentStream?.hostDisplayName ?: "Alice"),
                                        hostUsername = if (isHost) (activeAccount?.username ?: "@host") else (currentStream?.hostUsername ?: "@alice_crypto"),
                                        hostAvatarUrl = if (isHost) (activeAccount?.profilePicUrl ?: "") else (currentStream?.hostAvatarUrl ?: ""),
                                        title = currentStream?.title ?: "KuoteX Live Stream 🚀",
                                        viewerCount = viewerCount,
                                        isLive = true
                                    )
                                    viewModel.enterPipMode(sessionToPip)
                                    navController.popBackStack()
                                    Toast.makeText(context, "Режим «Картинка в картинке» включен", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Слушать в фоне (Только аудио)") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Headphones, contentDescription = null, tint = Color(0xFFE040FB))
                                },
                                onClick = {
                                    showHostMenu = false
                                    val sessionToAudio = currentStream ?: LiveStreamSession(
                                        id = targetStreamId ?: (if (isHost) myUserId else "2"),
                                        hostUserId = if (isHost) myUserId else (targetStreamId ?: "2"),
                                        hostDisplayName = if (isHost) (activeAccount?.displayName ?: "Host") else (currentStream?.hostDisplayName ?: "Alice"),
                                        hostUsername = if (isHost) (activeAccount?.username ?: "@host") else (currentStream?.hostUsername ?: "@alice_crypto"),
                                        hostAvatarUrl = if (isHost) (activeAccount?.profilePicUrl ?: "") else (currentStream?.hostAvatarUrl ?: ""),
                                        title = currentStream?.title ?: "KuoteX Live Stream 🚀",
                                        viewerCount = viewerCount,
                                        isLive = true
                                    )
                                    viewModel.startBackgroundAudio(context, sessionToAudio)
                                    navController.popBackStack()
                                    Toast.makeText(context, "Фоновое аудио включено. Уведомление создано.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        if (isHost) {
                            DropdownMenuItem(
                                text = { Text(if (isMuted) "Включить микрофон" else "Выключить микрофон") },
                                leadingIcon = {
                                    Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = null)
                                },
                                onClick = {
                                    isMuted = !isMuted
                                    showHostMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Переключить камеру") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Cameraswitch, contentDescription = null)
                                },
                                onClick = {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                                    showHostMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Настройки RTMP") },
                                leadingIcon = {
                                    Icon(Icons.Filled.SettingsInputAntenna, contentDescription = null)
                                },
                                onClick = {
                                    showRtmpDetailsDialog = true
                                    showHostMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Скопировать ссылку") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                clipboardManager.setText(AnnotatedString("https://kuotex.app/live/${currentStream?.id ?: myUserId}"))
                                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                showHostMenu = false
                            }
                        )
                        if (isHost && isCurrentlyLive) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Завершить трансляцию", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showHostMenu = false
                                    showEndStreamConfirmDialog = true
                                }
                            )
                        }
                        if (!isHost && isCurrentlyLive) {
                            DropdownMenuItem(
                                text = { Text("Пожаловаться", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Report, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showHostMenu = false
                                    showReportDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- FLOATING COMMENTS STREAM OVERLAY ---
        if (isCurrentlyLive && (currentStream?.commentsEnabled != false)) {
            val listState = rememberLazyListState()
            LaunchedEffect(streamComments.size) {
                if (streamComments.isNotEmpty()) {
                    listState.animateScrollToItem(streamComments.size - 1)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 80.dp, bottom = 90.dp)
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(streamComments, key = { it.id }) { comment ->
                        val isStarDonation = comment.starsDonated > 0
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    color = if (isStarDonation) Color(0xFF3E2723).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = if (isStarDonation) 1.5.dp else 0.5.dp,
                                    color = if (isStarDonation) Color(0xFFFFD700) else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = comment.senderName,
                                color = if (isStarDonation) Color(0xFFFFD700) else getUsernameColor(comment.senderName),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (isStarDonation) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFFFFD700), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "⭐️ ${comment.starsDonated}",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = comment.text,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // --- FLOATING REACTIONS PARTICLES ---
        floatingReactions.forEach { reaction ->
            val infiniteTransition = rememberInfiniteTransition(label = reaction.id)
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -450f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "offsetY"
            )
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = FastOutLinearInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-30).dp, y = (-100).dp + offsetY.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = reaction.scale
                        scaleY = reaction.scale
                    }
            ) {
                Text(text = reaction.emoji, fontSize = 28.sp)
            }
        }

        // --- BOTTOM CONTROLS & CAPTURE SECTION ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isCurrentlyLive) {
                // BEFORE LIVE IS STARTED: Mode selector and Start Button matching Telegram video!
                when (cameraMode) {
                    BroadcastCameraMode.STREAM -> {
                        Button(
                            onClick = { showStartStreamSheet = true },
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE91E63)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                        ) {
                            Text(
                                text = "Начать трансляцию",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    BroadcastCameraMode.PHOTO -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White, CircleShape)
                                .padding(4.dp)
                                .border(3.dp, Color.Black, CircleShape)
                                .clickable {
                                    Toast.makeText(context, "Фото сохранено", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                    BroadcastCameraMode.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .border(3.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .background(Color.Red, CircleShape)
                                .clickable {
                                    Toast.makeText(context, "Запись видео", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Mode Bar (Flip Camera, [Трансляция | Фото | Видео], Settings Gear)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Camera switch button
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                            com.example.analytics.AnalyticsTracker.logButtonClick(
                                buttonName = "switch_camera",
                                module = "stream",
                                metadata = mapOf("lens" to if (lensFacing == CameraSelector.LENS_FACING_FRONT) "front" else "back")
                            )
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                    }

                    // Mode labels (Трансляция, Фото, Видео)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Трансляция",
                            color = if (cameraMode == BroadcastCameraMode.STREAM) Color.White else Color.Gray,
                            fontWeight = if (cameraMode == BroadcastCameraMode.STREAM) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { cameraMode = BroadcastCameraMode.STREAM }
                        )
                        Text(
                            text = "Фото",
                            color = if (cameraMode == BroadcastCameraMode.PHOTO) Color.White else Color.Gray,
                            fontWeight = if (cameraMode == BroadcastCameraMode.PHOTO) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { cameraMode = BroadcastCameraMode.PHOTO }
                        )
                        Text(
                            text = "Видео",
                            color = if (cameraMode == BroadcastCameraMode.VIDEO) Color.White else Color.Gray,
                            fontWeight = if (cameraMode == BroadcastCameraMode.VIDEO) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { cameraMode = BroadcastCameraMode.VIDEO }
                        )
                    }

                    // Settings Gear Button
                    IconButton(
                        onClick = { showStartStreamSheet = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            } else {
                // DURING ACTIVE LIVE STREAM: Sleek bottom bar matching messenger theme with interactive comments, stars donation & reactions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val price = currentStream?.commentPriceStars ?: 0
                    
                    // Quick Reaction Emoji Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("❤️", "🔥", "⭐️", "👏", "🎉", "🚀").forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(38.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                    .clickable { triggerReaction(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }

                    // Bottom Bar Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(28.dp)
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Comment input field
                        OutlinedTextField(
                            value = commentInputText,
                            onValueChange = { commentInputText = it },
                            placeholder = {
                                Text(
                                    if (price > 0 && !isHost) "⭐️ $price за сообщение..." else "Написать комментарий...",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(22.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedBorderColor = if (price > 0 && !isHost) Color(0xFFFFD700) else Color.White.copy(alpha = 0.3f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            trailingIcon = {
                                if (commentInputText.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val senderName = if (isHost) "Вы" else (activeAccount?.displayName?.ifBlank { "User" } ?: "User")
                                            val newComment = LiveComment(
                                                senderId = myUserId,
                                                senderName = senderName,
                                                text = commentInputText.trim(),
                                                starsDonated = if (!isHost) price else 0
                                            )
                                            streamComments = (streamComments + newComment).takeLast(40)
                                            myCommentsSentCount++
                                            if (!isHost && price > 0) {
                                                myStarsDonatedAmount += price
                                            }
                                            val hostId = if (isHost) myUserId else (targetStreamId ?: "2")
                                            viewModel.addStreamComment(hostId, newComment)
                                            com.example.analytics.AnalyticsTracker.logStreamAction(
                                                action = "send_comment",
                                                streamId = hostId,
                                                hostId = hostId,
                                                metadata = mapOf(
                                                    "length" to commentInputText.trim().length,
                                                    "stars_paid" to (if (!isHost) price else 0),
                                                    "is_host" to isHost
                                                )
                                            )
                                            commentInputText = ""
                                            triggerReaction(if (price > 0) "⭐️" else "💬")
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = if (price > 0 && !isHost) Color(0xFFFFD700) else Color(0xFF0088CC)
                                        )
                                    }
                                }
                            }
                        )

                        if (!isHost) {
                            // Quick Star Donate Pill for Viewers
                            IconButton(
                                onClick = {
                                    val senderName = activeAccount?.displayName?.ifBlank { "User" } ?: "User"
                                    val newComment = LiveComment(
                                        senderId = myUserId,
                                        senderName = senderName,
                                        text = "⭐️ Отправил 50 звезд!",
                                        starsDonated = 50
                                    )
                                    streamComments = (streamComments + newComment).takeLast(40)
                                    myCommentsSentCount++
                                    myStarsDonatedAmount += 50
                                    val hostId = targetStreamId ?: "2"
                                    viewModel.addStreamComment(hostId, newComment)
                                    com.example.analytics.AnalyticsTracker.logStreamAction(
                                        action = "donate_stars",
                                        streamId = hostId,
                                        hostId = hostId,
                                        metadata = mapOf("amount" to 50)
                                    )
                                    triggerReaction("⭐️")
                                    Toast.makeText(context, "⭐️ 50 звезд отправлено ведущему!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFFFD700).copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), CircleShape)
                            ) {
                                Text("⭐️", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- TELEGRAM STREAM SETTINGS BOTTOM SHEET (MATCHING VIDEO EXACTLY) ---
    if (showStartStreamSheet) {
        val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
        
        ModalBottomSheet(
            onDismissRequest = { showStartStreamSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "Начать трансляцию",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Выберите, кто будет видеть Вашу трансляцию",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Privacy / Access Control Options with Radio selection
                val audienceOptions = listOf(
                    Triple(
                        "Все", 
                        if (excludedUserIds.isEmpty()) "добавить исключения >" else "Исключено: ${excludedUserIds.size} польз. >", 
                        Icons.Filled.Public
                    ),
                    Triple(
                        "Мои контакты", 
                        if (excludedUserIds.isEmpty()) "добавить исключения >" else "Исключено: ${excludedUserIds.size} польз. >", 
                        Icons.Filled.Person
                    ),
                    Triple(
                        "Близкие друзья", 
                        "Выбрано: ${closeFriendIds.size} польз. >", 
                        Icons.Filled.Star
                    ),
                    Triple(
                        "Выбранные пользователи", 
                        "Выбрано: ${selectedUserIds.size} польз. >", 
                        Icons.Filled.Group
                    )
                )

                audienceOptions.forEach { (name, actionHint, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAudience = name }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAudience == name,
                            onClick = { selectedAudience = name },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF0088CC)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (name == "Близкие друзья") Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFF0088CC).copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (name == "Близкие друзья") Color(0xFF4CAF50) else Color(0xFF0088CC),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = actionHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0088CC),
                                modifier = Modifier.clickable { showExclusionsDialog = true }
                            )
                        }
                    }
                }

                Text(
                    text = "Настройте список пользователей и исключений для полного контроля приватности.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Подключить трансляцию (RTMP / External app stream)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Подключить трансляцию",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Трансляция с помощью OBS или другого ПО.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isExternalAppBroadcast,
                        onCheckedChange = {
                            isExternalAppBroadcast = it
                            if (it) showRtmpDetailsDialog = true
                        }
                    )
                }

                // Switches for comments & screenshots
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Включить комментарии",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = commentsEnabled,
                        onCheckedChange = { commentsEnabled = it }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Разрешить снимки экрана",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = screenshotsEnabled,
                        onCheckedChange = { screenshotsEnabled = it }
                    )
                }

                // --- PAID MESSAGING TOGGLE ---
                if (commentsEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⭐️ ", fontSize = 16.sp)
                                Text(
                                    text = "Платные сообщения",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Требовать оплату за отправку каждого сообщения в чате стрима.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPaidMessagingEnabled,
                            onCheckedChange = { isPaidMessagingEnabled = it }
                        )
                    }

                    // Стоимость комментария (Stars Slider & Quick Presets)
                    if (isPaidMessagingEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Стоимость за сообщение",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            val starsInt = commentPriceStars.toInt()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⭐️ ", fontSize = 16.sp)
                                Text(
                                    text = "${numberFormat.format(starsInt)} звезд",
                                    color = Color(0xFFE6A100),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Slider(
                            value = commentPriceStars,
                            onValueChange = { commentPriceStars = it },
                            valueRange = 10f..5000f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFD700),
                                activeTrackColor = Color(0xFFFFD700)
                            )
                        )

                        // Quick presets chips (50, 100, 250, 500, 1000, 2500)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(50, 100, 250, 500, 1000).forEach { preset ->
                                val isSelected = commentPriceStars.toInt() == preset
                                Surface(
                                    onClick = { commentPriceStars = preset.toFloat() },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Color(0xFFFFD700).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = if (isSelected) BorderStroke(1.dp, Color(0xFFFFD700)) else null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "$preset",
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFFE6A100) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Зрители должны будут заплатить указанную сумму ⭐️ звёзд за каждый свой комментарий.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Start Button
                Button(
                    onClick = {
                        showStartStreamSheet = false
                        val hostName = activeAccount?.displayName?.ifBlank { "SARATOSHI" } ?: "SARATOSHI"
                        val hostUser = activeAccount?.username ?: "@saratoshi"
                        val hostAvatar = activeAccount?.profilePicUrl?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/seed/$myUserId/100"
                        val finalCommentPrice = if (commentsEnabled && isPaidMessagingEnabled) commentPriceStars.toInt() else 0
                        val newSession = LiveStreamSession(
                            id = "live_${myUserId}_${System.currentTimeMillis()}",
                            hostUserId = myUserId,
                            hostDisplayName = hostName,
                            hostUsername = hostUser,
                            hostAvatarUrl = hostAvatar,
                            title = "Прямой эфир",
                            audience = selectedAudience,
                            isExternalApp = isExternalAppBroadcast,
                            commentsEnabled = commentsEnabled,
                            allowScreenshots = screenshotsEnabled,
                            commentPriceStars = finalCommentPrice,
                            viewerCount = Random.nextInt(40, 120)
                        )
                        viewModel.startLiveStream(newSession)
                        durationSeconds = 0
                        com.example.analytics.AnalyticsTracker.logStreamAction(
                            action = "start_stream",
                            streamId = newSession.id,
                            hostId = myUserId,
                            metadata = mapOf(
                                "audience" to selectedAudience,
                                "price" to finalCommentPrice,
                                "is_external_app" to isExternalAppBroadcast
                            )
                        )
                        Toast.makeText(context, "Трансляция начата!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0088CC)
                    )
                ) {
                    Text("Начать трансляцию", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- "ВЕСТИ ТРАНСЛЯЦИЮ ОТ ИМЕНИ" BOTTOM SHEET ---
    if (showAccountPickerSheet) {
        val hostName = activeAccount?.displayName?.ifBlank { "SARATOSHI" } ?: "SARATOSHI"
        val hostAvatar = activeAccount?.profilePicUrl?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/seed/$myUserId/100"

        ModalBottomSheet(
            onDismissRequest = { showAccountPickerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Вести трансляцию от имени",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAccountPickerSheet = false }
                        .padding(vertical = 12.dp)
                ) {
                    RadioButton(selected = true, onClick = { showAccountPickerSheet = false })
                    Spacer(modifier = Modifier.width(12.dp))
                    AsyncImage(
                        model = hostAvatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = hostName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "личный аккаунт",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // --- RTMP SERVER DETAILS DIALOG ---
    if (showRtmpDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showRtmpDetailsDialog = false },
            title = { Text("Параметры RTMP трансляции") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Используйте эти данные в OBS Studio или другом приложении:")
                    
                    OutlinedTextField(
                        value = "rtmps://live.kuotex.net:443/app",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("URL сервера") },
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString("rtmps://live.kuotex.net:443/app"))
                                Toast.makeText(context, "URL скопирован", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = "live_sk_${myUserId}_783941",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ключ трансляции (Stream Key)") },
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString("live_sk_${myUserId}_783941"))
                                Toast.makeText(context, "Ключ скопирован", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showRtmpDetailsDialog = false }) {
                    Text("Готово")
                }
            }
        )
    }

    // --- EXCLUSIONS / PRIVACY USERS PICKER DIALOG ---
    if (showExclusionsDialog) {
        val sampleContacts = remember {
            listOf(
                Triple("u1", "Alice Vance", "@alice"),
                Triple("u2", "Bob Martin", "@bob_m"),
                Triple("u3", "Crypto Whale", "@cwhale"),
                Triple("u4", "Daria Design", "@daria_ui"),
                Triple("u5", "Elena Smirnova", "@elena_s"),
                Triple("u6", "Pavel Durov", "@durov")
            )
        }
        var searchQuery by remember { mutableStateOf("") }
        val isExclusionMode = selectedAudience == "Все" || selectedAudience == "Мои контакты"
        val currentSet = when (selectedAudience) {
            "Близкие друзья" -> closeFriendIds
            "Выбранные пользователи" -> selectedUserIds
            else -> excludedUserIds
        }
        var workingSet by remember { mutableStateOf(currentSet) }

        Dialog(
            onDismissRequest = { showExclusionsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.75f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        text = if (isExclusionMode) "Исключения из трансляции" else "Настройка аудитории ($selectedAudience)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isExclusionMode) 
                            "Выбранные пользователи НЕ смогут просматривать данный прямой эфир." 
                        else 
                            "Только выбранные пользователи получат доступ к этому эфиру.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск контактов...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filteredContacts = sampleContacts.filter {
                            it.second.contains(searchQuery, ignoreCase = true) || it.third.contains(searchQuery, ignoreCase = true)
                        }
                        items(filteredContacts, key = { it.first }) { (id, name, username) ->
                            val isChecked = workingSet.contains(id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        workingSet = if (isChecked) workingSet - id else workingSet + id
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = "https://i.pravatar.cc/150?u=$id",
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(text = username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        workingSet = if (checked) workingSet + id else workingSet - id
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showExclusionsDialog = false }) {
                            Text("Отмена")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                when (selectedAudience) {
                                    "Близкие друзья" -> closeFriendIds = workingSet
                                    "Выбранные пользователи" -> selectedUserIds = workingSet
                                    else -> excludedUserIds = workingSet
                                }
                                showExclusionsDialog = false
                                Toast.makeText(context, "Настройки сохранены (${workingSet.size} польз.)", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
                        ) {
                            Text("Применить (${workingSet.size})")
                        }
                    }
                }
            }
        }
    }

    // --- STREAM PLAYER SETTINGS DIALOG (Resolution & Hardware Acceleration) ---
    if (showPlayerSettingsSheet) {
        val resolutions = listOf(
            Triple("1080p", "1080p Full HD • 60 FPS", "Наилучшее качество (~6.5 Мбит/с)"),
            Triple("720p", "720p HD • 60 FPS", "Оптимальный баланс (~3.5 Мбит/с)"),
            Triple("480p", "480p SD • 30 FPS", "Экономия трафика (~1.5 Мбит/с)"),
            Triple("360p", "360p Data Saver • 30 FPS", "Для слабого интернета (~0.8 Мбит/с)"),
            Triple("Авто", "Автоматически (Adaptive)", "Подстройка под скорость соединения")
        )

        Dialog(
            onDismissRequest = { showPlayerSettingsSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = null,
                                tint = Color(0xFF0088CC),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Параметры плеера",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Качество видео и аппаратное декодирование",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Section: Resolution Selector
                        Text(
                            text = "РАЗРЕШЕНИЕ ВИДЕО",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0088CC),
                            letterSpacing = 0.5.sp
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            resolutions.forEach { (resKey, resTitle, resSubtitle) ->
                                val isSelected = selectedResolution == resKey
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            if (isSelected) 1.dp else 0.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedResolution = resKey }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedResolution = resKey }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = resTitle,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = resSubtitle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "АКТИВНО",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section: AI Image-to-Image Real-Time Stylization
                        Text(
                            text = "РЕЖИМ IMAGE-TO-IMAGE (AI СТИЛИЗАЦИЯ)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9C27B0),
                            letterSpacing = 0.5.sp
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .border(
                                    if (isImageToImageEnabled) 1.dp else 0.dp,
                                    if (isImageToImageEnabled) Color(0xFF9C27B0).copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (isImageToImageEnabled) Color(0xFFE040FB) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Генеративный AI-фильтр",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Трансформирует видеопоток с помощью нейросетевых стилей и диффузии в реальном времени.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isImageToImageEnabled,
                                    onCheckedChange = { isImageToImageEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF9C27B0)
                                    )
                                )
                            }

                            if (isImageToImageEnabled) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                Text(
                                    text = "Выберите AI-стиль:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                val aiStyles = listOf("Киберпанк Неон", "Аниме Студия", "Кинематографичный", "Ретро Вейв", "Студийный свет")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    aiStyles.forEach { style ->
                                        val isCurrentStyle = selectedAiStyle == style
                                        Surface(
                                            onClick = { selectedAiStyle = style },
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (isCurrentStyle) Color(0xFF9C27B0) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCurrentStyle) Color(0xFFE040FB) else Color.Transparent
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isCurrentStyle) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = style,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isCurrentStyle) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrentStyle) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Интенсивность стилизации",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${(aiStylingStrength * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9C27B0)
                                    )
                                }

                                Slider(
                                    value = aiStylingStrength,
                                    onValueChange = { aiStylingStrength = it },
                                    valueRange = 0.2f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF9C27B0),
                                        activeTrackColor = Color(0xFF9C27B0)
                                    )
                                )
                            }
                        }

                        // Section: Performance & Hardware Acceleration
                        Text(
                            text = "ПРОИЗВОДИТЕЛЬНОСТЬ И ДЕКОДЕР",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0088CC),
                            letterSpacing = 0.5.sp
                        )

                        // Hardware Acceleration Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .clickable { isHardwareAccelerationEnabled = !isHardwareAccelerationEnabled }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = if (isHardwareAccelerationEnabled) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Аппаратное ускорение (GPU)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Использовать аппаратный чип GPU (MediaCodec). Снижает энергопотребление и предотвращает пропуск кадров при нестабильной сети.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isHardwareAccelerationEnabled,
                                onCheckedChange = { isHardwareAccelerationEnabled = it }
                            )
                        }

                        // Low Latency Mode Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .clickable { isLowLatencyMode = !isLowLatencyMode }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Режим ультра-низкой задержки",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Минимизирует буфер воспроизведения до < 1.2 сек для мгновенной реакции на сообщения и донаты.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isLowLatencyMode,
                                onCheckedChange = { isLowLatencyMode = it }
                            )
                        }

                        // Diagnostic Stats Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isHardwareAccelerationEnabled) Color(0xFF00E5FF).copy(alpha = 0.08f) else Color(0xFFFF9800).copy(alpha = 0.08f),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isHardwareAccelerationEnabled) Color(0xFF00E5FF).copy(alpha = 0.3f) else Color(0xFFFF9800).copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Speed,
                                contentDescription = null,
                                tint = if (isHardwareAccelerationEnabled) Color(0xFF0088CC) else Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Декодер: ${if (isHardwareAccelerationEnabled) "GPU MediaCodec (Аппаратный)" else "CPU Software (Программный)"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHardwareAccelerationEnabled) Color(0xFF0088CC) else Color(0xFFFF9800)
                                )
                                Text(
                                    text = "Пинг: 24 мс • Потери пакетов: 0.0% • Буфер: ${if (isLowLatencyMode) "0.8с" else "2.5с"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // PiP Option in Settings Sheet
                        Surface(
                            onClick = {
                                showPlayerSettingsSheet = false
                                val sessionToPip = currentStream ?: LiveStreamSession(
                                    id = targetStreamId ?: (if (isHost) myUserId else "2"),
                                    hostUserId = if (isHost) myUserId else (targetStreamId ?: "2"),
                                    hostDisplayName = if (isHost) (activeAccount?.displayName ?: "Host") else (currentStream?.hostDisplayName ?: "Alice"),
                                    hostUsername = if (isHost) (activeAccount?.username ?: "@host") else (currentStream?.hostUsername ?: "@alice_crypto"),
                                    hostAvatarUrl = if (isHost) (activeAccount?.profilePicUrl ?: "") else (currentStream?.hostAvatarUrl ?: ""),
                                    title = currentStream?.title ?: "KuoteX Live Stream 🚀",
                                    viewerCount = viewerCount,
                                    isLive = true
                                )
                                viewModel.enterPipMode(sessionToPip)
                                navController.popBackStack()
                                Toast.makeText(context, "Режим «Картинка в картинке» включен", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PictureInPictureAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Режим «Картинка в картинке» (PiP)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Свернуть стрим в плавающее окно и продолжить чаты",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Background Audio Option in Settings Sheet
                        Surface(
                            onClick = {
                                showPlayerSettingsSheet = false
                                val sessionToAudio = currentStream ?: LiveStreamSession(
                                    id = targetStreamId ?: (if (isHost) myUserId else "2"),
                                    hostUserId = if (isHost) myUserId else (targetStreamId ?: "2"),
                                    hostDisplayName = if (isHost) (activeAccount?.displayName ?: "Host") else (currentStream?.hostDisplayName ?: "Alice"),
                                    hostUsername = if (isHost) (activeAccount?.username ?: "@host") else (currentStream?.hostUsername ?: "@alice_crypto"),
                                    hostAvatarUrl = if (isHost) (activeAccount?.profilePicUrl ?: "") else (currentStream?.hostAvatarUrl ?: ""),
                                    title = currentStream?.title ?: "KuoteX Live Stream 🚀",
                                    viewerCount = viewerCount,
                                    isLive = true
                                )
                                viewModel.startBackgroundAudio(context, sessionToAudio)
                                navController.popBackStack()
                                Toast.makeText(context, "Фоновое воспроизведение звука включено", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFE040FB).copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color(0xFFE040FB).copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Headphones,
                                    contentDescription = null,
                                    tint = Color(0xFFE040FB),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Фоновое аудио (Слушать в фоне)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE040FB)
                                    )
                                    Text(
                                        text = "Продолжать воспроизведение звука через уведомление",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = Color(0xFFE040FB).copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        if (!isHost) {
                            Surface(
                                onClick = {
                                    showPlayerSettingsSheet = false
                                    showReportDialog = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Report,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Пожаловаться на трансляцию",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Сообщить модераторам о неприемлемом контенте",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        if (isHost && isCurrentlyLive) {
                            Surface(
                                onClick = {
                                    showPlayerSettingsSheet = false
                                    showEndStreamConfirmDialog = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Завершить трансляцию",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Остановить прямой эфир для всех зрителей",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPlayerSettingsSheet = false }) {
                            Text("Закрыть")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showPlayerSettingsSheet = false
                                Toast.makeText(
                                    context,
                                    "Качество: $selectedResolution • Аппаратное ускорение: ${if (isHardwareAccelerationEnabled) "ВКЛ" else "ВЫКЛ"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC))
                        ) {
                            Text("Применить")
                        }
                    }
                }
            }
        }
    }

    // --- REPORT STREAM DIALOG ---
    if (showReportDialog) {
        val reportReasons = listOf(
            Pair("Спам или мошенничество", "Нерелевантная реклама, фишинг или вымогательство"),
            Pair("Опасный или насильственный контент", "Угрозы, насилие или опасные действия"),
            Pair("Оскорбления или преследование", "Травля, дискриминация или язык вражды"),
            Pair("Материалы сексуального характера", "Контент 18+, нагота или домогательства"),
            Pair("Нарушение авторских прав", "Трансляция чужого контента без разрешения"),
            Pair("Продажа запрещенных товаров", "Наркотики, оружие или нелегальные услуги"),
            Pair("Другое", "Иные нарушения правил сообщества")
        )

        Dialog(
            onDismissRequest = { showReportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Пожаловаться на эфир",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ваша жалоба анонимна и будет проверена модераторами",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ПРИЧИНА ЖАЛОБЫ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )

                        reportReasons.forEach { (reason, description) ->
                            val isSelected = reportReason == reason
                            Surface(
                                onClick = { reportReason = reason },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { reportReason = reason }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reason,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "ДОПОЛНИТЕЛЬНЫЕ СВЕДЕНИЯ (НЕОБЯЗАТЕЛЬНО)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )

                        OutlinedTextField(
                            value = reportDetails,
                            onValueChange = { reportDetails = it },
                            placeholder = { Text("Укажите детали нарушения или таймкод...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 72.dp),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3
                        )

                        // Also block streamer checkbox
                        Surface(
                            onClick = { blockStreamerOnReport = !blockStreamerOnReport },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = blockStreamerOnReport,
                                    onCheckedChange = { blockStreamerOnReport = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Заблокировать автора",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Вы больше не увидите стримы и сообщения от этого автора",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showReportDialog = false }) {
                            Text("Отмена")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val streamerTitle = currentStream?.hostUsername ?: "этого автора"
                                showReportDialog = false
                                val detailsText = if (reportDetails.isNotBlank()) " Подробности: \"$reportDetails\"." else ""
                                val blockText = if (blockStreamerOnReport) " Автор заблокирован." else ""
                                Toast.makeText(
                                    context,
                                    "Жалоба на трансляцию ($reportReason) отправлена модераторам.$blockText",
                                    Toast.LENGTH_LONG
                                ).show()
                                if (blockStreamerOnReport) {
                                    navController.popBackStack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Отправить жалобу", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // --- END STREAM CONFIRM DIALOG ---
    if (showEndStreamConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndStreamConfirmDialog = false },
            title = { Text("Завершить трансляцию?") },
            text = { Text("Прямой эфир будет остановлен для всех зрителей.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndStreamConfirmDialog = false
                        viewModel.stopLiveStream(myUserId)
                        com.example.analytics.AnalyticsTracker.logStreamAction(
                            action = "stop_stream",
                            streamId = currentStream?.id,
                            hostId = myUserId,
                            metadata = mapOf(
                                "duration_seconds" to durationSeconds,
                                "final_viewer_count" to viewerCount,
                                "stars_earned" to (currentStream?.totalStarsEarned ?: 0)
                            )
                        )
                        Toast.makeText(context, "Трансляция завершена", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndStreamConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun BroadcastCameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int,
    targetResolution: Size
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    
    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        onDispose {
            if (cameraProviderFuture.isDone) {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    @Suppress("DEPRECATION")
                    val preview = Preview.Builder()
                        .setTargetResolution(targetResolution)
                        .build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    
                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    // Handled gracefully in preview container
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}
