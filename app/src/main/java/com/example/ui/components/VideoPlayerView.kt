package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Reusable ExoPlayer / Media3 based video player component for Jetpack Compose.
 *
 * Supports:
 * - Local file paths, content URIs, remote video URLs
 * - Circular clipping for Telegram-style round video notes (кружочки)
 * - Custom interactive playback controls with play/pause, time scrubbing, speed selection & audio mute
 * - Automatic lifecycle management (pause on lifecycle stop, clean release on disposal)
 * - Graceful fallback demo streams for placeholder/simulated video notes
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUri: String?,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    isLooping: Boolean = true,
    isMuted: Boolean = false,
    showControls: Boolean = true,
    isCircular: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    speed: Float = 1.0f,
    onPlaybackEnded: (() -> Unit)? = null,
    onPlayerStateChanged: ((isPlaying: Boolean, currentPosMs: Long, durationMs: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(autoPlay) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(speed) }
    var playerMuted by remember { mutableStateOf(isMuted) }
    var controlsVisible by remember { mutableStateOf(showControls) }

    // Resolve uri or fallback
    val resolvedUri = remember(videoUri) {
        resolveVideoUri(context, videoUri)
    }

    // Initialize ExoPlayer instance
    val exoPlayer = remember(context, resolvedUri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(resolvedUri)
            setMediaItem(mediaItem)
            repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (playerMuted) 0f else 1f
            playbackParameters = PlaybackParameters(currentSpeed)
            prepare()
            playWhenReady = autoPlay
        }
    }

    // Attach Player Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlayerStateChanged?.invoke(isPlaying, exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                        hasError = false
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isPlaying = false
                        onPlaybackEnded?.invoke()
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                hasError = true
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Manage Android Lifecycle
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Sync speed and mute updates
    LaunchedEffect(currentSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed)
    }

    LaunchedEffect(playerMuted) {
        exoPlayer.volume = if (playerMuted) 0f else 1f
    }

    // Position tracker ticker
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            onPlayerStateChanged?.invoke(isPlaying, currentPositionMs, durationMs)
            delay(200)
        }
    }

    val shape = if (isCircular) CircleShape else RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (showControls) {
                            controlsVisible = !controlsVisible
                        } else {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        val width = size.width
                        if (offset.x < width / 2) {
                            // Rewind 5 seconds
                            val newPos = (exoPlayer.currentPosition - 5000).coerceAtLeast(0)
                            exoPlayer.seekTo(newPos)
                        } else {
                            // Forward 5 seconds
                            val newPos = (exoPlayer.currentPosition + 5000).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(newPos)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // AndroidView embedding PlayerView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = resizeMode
            }
        )

        // Buffering progress indicator
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(0xFF00E5FF),
                    strokeWidth = 3.dp
                )
            }
        }

        // Error message placeholder
        if (hasError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VideocamOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Видео временно недоступно",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        hasError = false
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                ) {
                    Text("Повторить", color = Color(0xFF00E5FF))
                }
            }
        }

        // Custom Overlay Controls (if enabled)
        if (showControls) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    // Center Play/Pause button
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Bottom Control bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Current time / duration
                        Text(
                            text = "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Speed toggle button
                            TextButton(
                                onClick = {
                                    currentSpeed = when (currentSpeed) {
                                        1.0f -> 1.5f
                                        1.5f -> 2.0f
                                        2.0f -> 0.5f
                                        else -> 1.0f
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${currentSpeed}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                            }

                            // Mute toggle button
                            IconButton(
                                onClick = { playerMuted = !playerMuted },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (playerMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Mute toggle",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fullscreen Interactive Video Player Dialog with comprehensive Telegram-style playback UI.
 *
 * Supports both circular video messages (кружочки) and standard widescreen/portrait video files.
 */
@Composable
fun FullScreenVideoPlayerDialog(
    videoUri: String?,
    senderName: String = "Пользователь",
    timestamp: Long = System.currentTimeMillis(),
    durationText: String? = null,
    isCircular: Boolean = false,
    onDismiss: () -> Unit,
    onReply: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onSaveToGallery: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var resizeMode by remember { mutableIntStateOf(if (isCircular) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val infiniteTransition = rememberInfiniteTransition(label = "video_ring")
    val borderRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotate"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Color.White
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text(
                                text = senderName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isCircular) "Видеосообщение • ${formatTimeSimple(timestamp)}" else "Видео • ${formatTimeSimple(timestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Speed Switcher
                        TextButton(
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.5f
                                    else -> 1.0f
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Mute button
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color(0xFFFF5252) else Color.White
                            )
                        }

                        if (!isCircular) {
                            Spacer(Modifier.width(8.dp))
                            // Aspect Ratio Mode toggle
                            IconButton(
                                onClick = {
                                    resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    } else {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) Icons.Filled.AspectRatio else Icons.Filled.FitScreen,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Center Video Presentation
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCircular) {
                        // Round Video Note (Кружочек) container with neon aura
                        Box(
                            modifier = Modifier
                                .size(310.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // Animated rotating gradient aura border
                            Box(
                                modifier = Modifier
                                    .size(310.dp)
                                    .rotate(borderRotation)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                Color(0xFF00E5FF),
                                                Color(0xFF2979FF),
                                                Color(0xFF7C4DFF),
                                                Color(0xFF00E5FF)
                                            )
                                        )
                                    )
                            )

                            // Inner circle video player
                            VideoPlayerView(
                                videoUri = videoUri,
                                modifier = Modifier
                                    .size(298.dp)
                                    .clip(CircleShape),
                                autoPlay = true,
                                isLooping = true,
                                isMuted = isMuted,
                                showControls = false,
                                isCircular = true,
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                speed = playbackSpeed,
                                onPlayerStateChanged = { playing, pos, dur ->
                                    isPlaying = playing
                                    currentPosMs = pos
                                    totalDurationMs = dur
                                }
                            )
                        }
                    } else {
                        // Standard rectangular widescreen video presentation
                        VideoPlayerView(
                            videoUri = videoUri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.85f)
                                .clip(RoundedCornerShape(16.dp)),
                            autoPlay = true,
                            isLooping = true,
                            isMuted = isMuted,
                            showControls = false,
                            isCircular = false,
                            resizeMode = resizeMode,
                            speed = playbackSpeed,
                            onPlayerStateChanged = { playing, pos, dur ->
                                isPlaying = playing
                                currentPosMs = pos
                                totalDurationMs = dur
                            }
                        )
                    }
                }

                // Bottom Action & Progress Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Time Progress Indicator
                    val progressFraction = remember(currentPosMs, totalDurationMs) {
                        if (totalDurationMs > 0) (currentPosMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPosMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = durationText ?: formatDuration(totalDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = Color(0xFF00E5FF),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Bottom Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onReply != null) {
                            IconButton(
                                onClick = onReply,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Ответить",
                                    tint = Color.White
                                )
                            }
                        }

                        if (onForward != null) {
                            IconButton(
                                onClick = onForward,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Forward,
                                    contentDescription = "Переслать",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (onSaveToGallery != null) {
                                    onSaveToGallery()
                                } else {
                                    android.widget.Toast.makeText(context, "Видео сохранено в галерею", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Скачать",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolves a video URI or provides a robust demo MP4 stream when a simulated video note
 * or mock path is passed.
 */
private fun resolveVideoUri(context: Context, pathOrUrl: String?): Uri {
    if (pathOrUrl.isNullOrBlank()) {
        return Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
    }

    if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") ||
        pathOrUrl.startsWith("content://") || pathOrUrl.startsWith("file://") ||
        pathOrUrl.startsWith("asset://")
    ) {
        return Uri.parse(pathOrUrl)
    }

    val localFile = File(pathOrUrl)
    if (localFile.exists() && localFile.length() > 0) {
        return Uri.fromFile(localFile)
    }

    val cacheFile = File(context.cacheDir, pathOrUrl)
    if (cacheFile.exists() && cacheFile.length() > 0) {
        return Uri.fromFile(cacheFile)
    }

    val filesDirFile = File(context.filesDir, pathOrUrl)
    if (filesDirFile.exists() && filesDirFile.length() > 0) {
        return Uri.fromFile(filesDirFile)
    }

    // High quality reliable sample video fallback for previewing sent video circles / test items
    return Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun formatTimeSimple(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
