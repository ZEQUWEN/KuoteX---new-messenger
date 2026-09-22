package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Mode of the rightmost input action button in Telegram-style chat:
 * - VOICE: Microphone icon, records voice note
 * - VIDEO: Video Camera icon, records round video note (видеокружочек)
 */
enum class ChatInputMode {
    VOICE,
    VIDEO
}

/**
 * Interactive Telegram-style Audio/Video switcher button with:
 * - Single click / tap: Instant toggle between Microphone and Camera mode
 * - Press and hold: Starts recording (voice or round video)
 * - Release finger: Stops recording and sends the note to the chat
 * - Swipe left: Cancels recording
 */
@Composable
fun TelegramRecordActionButton(
    mode: ChatInputMode,
    isRecording: Boolean,
    onToggleMode: () -> Unit,
    onStartRecording: (ChatInputMode) -> Unit,
    onStopAndSend: (ChatInputMode) -> Unit,
    onCancelRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentMode by rememberUpdatedState(mode)
    val currentIsRecording by rememberUpdatedState(isRecording)

    // Pulse animation while recording
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val buttonBgColor = if (isRecording) {
        Color(0xFFE53935)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val actionButtonDescription = if (isRecording) {
        "Идет запись сообщения. Отпустите для отправки, смахните влево для отмены"
    } else if (mode == ChatInputMode.VIDEO) {
        "Кнопка видеокружочка. Нажмите для переключения на голос, удерживайте для записи"
    } else {
        "Кнопка голосового сообщения. Нажмите для переключения на видео, удерживайте для записи"
    }

    Box(
        modifier = modifier
            .size(46.dp)
            .scale(if (isRecording) pulseScale else 1f)
            .clip(CircleShape)
            .background(buttonBgColor)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = actionButtonDescription
                onClick(label = "Переключить режим записи") {
                    onToggleMode()
                    true
                }
            }
            .pointerInput(mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = System.currentTimeMillis()
                    var isHeld = false
                    var isCancelled = false

                    // Launch delay to detect hold / long press
                    val holdJob = coroutineScope.launch {
                        delay(220L)
                        isHeld = true
                        onStartRecording(currentMode)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            // Finger lifted / released
                            break
                        }

                        // Check if user slid left to cancel (dragged > 120px left)
                        val dragX = change.position.x - down.position.x
                        if (dragX < -120f && isHeld) {
                            isCancelled = true
                            holdJob.cancel()
                            onCancelRecording()
                            break
                        }
                    }

                    holdJob.cancel()
                    val totalDuration = System.currentTimeMillis() - startTime

                    if (isHeld) {
                        if (!isCancelled) {
                            // Release finger to send!
                            onStopAndSend(currentMode)
                        }
                    } else if (totalDuration < 220L && !isCancelled) {
                        // Quick click -> Toggle mode!
                        onToggleMode()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = if (isRecording) "recording" else mode.name,
            transitionSpec = {
                (scaleIn(animationSpec = tween(180)) + fadeIn()) togetherWith
                        (scaleOut(animationSpec = tween(180)) + fadeOut())
            },
            label = "icon_mode_anim"
        ) { target ->
            when {
                isRecording -> {
                    Icon(
                        imageVector = if (mode == ChatInputMode.VIDEO) Icons.Filled.Videocam else Icons.Filled.Mic,
                        contentDescription = "Recording active",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                target == ChatInputMode.VIDEO.name -> {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = "Video note mode (tap to switch to voice)",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice note mode (tap to switch to video)",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Recording indicator banner that replaces or overlays the input text field
 * during active voice or video note recording, with animated red dot, timer,
 * and slide-to-cancel chevron.
 */
@Composable
fun TelegramRecordingBar(
    mode: ChatInputMode,
    recordingSeconds: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "red_dot_anim")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    val slideTransition = rememberInfiniteTransition(label = "slide_anim")
    val slideOffset by slideTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slide_offset"
    )

    val formattedTimer = remember(recordingSeconds) {
        val minutes = recordingSeconds / 60
        val seconds = recordingSeconds % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red recording indicator dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935).copy(alpha = dotAlpha))
        )
        
        Spacer(Modifier.width(8.dp))

        // Timer
        Text(
            text = formattedTimer,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.width(16.dp))

        // Slide to cancel / instruction indicator
        Row(
            modifier = Modifier
                .weight(1f)
                .offset(x = slideOffset.dp)
                .clickable { onCancel() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Slide to cancel",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (mode == ChatInputMode.VIDEO) "Отпустите — отправить" else "Смахните для отмены",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        // Cancel trash button
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Cancel recording",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Camera preview inside circular window for video note recording.
 * Supports CameraX front/back camera with graceful simulated fallback.
 */
@Composable
fun CameraRoundPreview(
    isFrontCamera: Boolean,
    onToggleCameraFacing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasCameraPermission = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var bindError by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, isFrontCamera, hasCameraPermission) {
        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                } catch (e: Exception) {
                    bindError = true
                }
            }, ContextCompat.getMainExecutor(context))
        }

        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Color(0xFF1E1E24)),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission && cameraProvider != null && !bindError) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                update = { previewView ->
                    try {
                        val provider = cameraProvider ?: return@AndroidView
                        val selector = if (isFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    } catch (e: Exception) {
                        bindError = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Simulated live camera view with sleek ambient aesthetic & animated reticle
            SimulatedCameraView(isFront = isFrontCamera)
        }

        // Camera flip button at top-right of circle
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
                .size(34.dp)
                .clickable { onToggleCameraFacing() }
        ) {
            Icon(
                Icons.Filled.Cameraswitch,
                contentDescription = "Flip camera",
                tint = Color.White,
                modifier = Modifier.padding(7.dp)
            )
        }
    }
}

/**
 * Simulated front/rear camera video view for emulator/environments without physical camera.
 */
@Composable
private fun SimulatedCameraView(isFront: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "sim_cam")
    val scannerPos by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanner"
    )

    val gradientColors = if (isFront) {
        listOf(Color(0xFF1A237E), Color(0xFF006064), Color(0xFF004D40))
    } else {
        listOf(Color(0xFF2E7D32), Color(0xFF00838F), Color(0xFF1565C0))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        // Hologram user avatar silhouette
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isFront) Icons.Filled.Person else Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isFront) "Фронтальная камера" else "Основная камера",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // Live grid and scanning line overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * scannerPos
            drawLine(
                color = Color(0x6600E5FF),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 3f
            )
        }
    }
}

/**
 * Full Telegram Round Video Note Recording Overlay!
 * Appears floating on screen while the user holds down the video camera button.
 */
@Composable
fun TelegramVideoNoteRecordingOverlay(
    recordingSeconds: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFrontCamera by remember { mutableStateOf(true) }

    // Pulsing glowing border animation around the circle
    val infiniteTransition = rememberInfiniteTransition(label = "circle_border_anim")
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_pulse"
    )
    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "border_rot"
    )

    val formattedTimer = remember(recordingSeconds) {
        val minutes = recordingSeconds / 60
        val seconds = recordingSeconds % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(enabled = false) {}, // intercept clicks
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Header: REC indicator and active timer
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "REC $formattedTimer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = Color.White
                    )
                }
            }

            // Circular Camera Viewfinder (240dp)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(
                            (4 * borderPulse).dp,
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFF2979FF),
                                    Color(0xFFE040FB),
                                    Color(0xFF00E5FF)
                                )
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CameraRoundPreview(
                    isFrontCamera = isFrontCamera,
                    onToggleCameraFacing = { isFrontCamera = !isFrontCamera },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(24.dp))

            // Instructions text
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Отпустите палец для отправки\nСмахните влево для отмены",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Telegram Circular Video Note Bubble (Кружочек) in Chat!
 * Features:
 * - Perfectly circular video frame (210dp x 210dp)
 * - Play/Pause tap interaction with animated circular progress border
 * - Duration badge and volume toggle
 * - E2E encryption lock and delivery / read status checkmarks
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TelegramRoundVideoBubble(
    durationText: String,
    timestamp: Long,
    isMe: Boolean,
    isDelivered: Boolean,
    isRead: Boolean,
    isE2EEncrypted: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onOpenViewer: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }

    // Animate progress ring while playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            playbackProgress = 0f
            while (isPlaying && playbackProgress < 1f) {
                delay(80L)
                playbackProgress += 0.02f
            }
            if (playbackProgress >= 1f) {
                isPlaying = false
                playbackProgress = 0f
            }
        } else {
            playbackProgress = 0f
        }
    }

    val rotationTransition = rememberInfiniteTransition(label = "video_play_spin")
    val spinAngle by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    val videoAccessibilityLabel = remember(durationText, isMe, isPlaying, isDelivered, isRead, isE2EEncrypted) {
        buildString {
            if (isMe) append("Вы: ") else append("Собеседник: ")
            append("Видеокружочек, длительность $durationText. ")
            if (isPlaying) append("Воспроизводится. ")
            if (isMe) {
                if (isRead) append("Прочитано. ")
                else if (isDelivered) append("Доставлено. ")
                else append("В очереди. ")
            }
            if (isE2EEncrypted) append("Сквозное шифрование. ")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = videoAccessibilityLabel
                    role = Role.Button
                    if (isSelectionMode) {
                        selected = isSelected
                        role = Role.Checkbox
                    }
                }
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelect?.invoke()
                        } else {
                            isPlaying = !isPlaying
                        }
                    },
                    onClickLabel = if (isSelectionMode) "Выбрать видеосообщение" else if (isPlaying) "Приостановить воспроизведение" else "Воспроизвести видеосообщение",
                    onLongClick = {
                        onLongClick?.invoke()
                    },
                    onLongClickLabel = "Действия с видеосообщением"
                ),
            contentAlignment = Alignment.Center
        ) {
            // Background & Simulated Video Visual Content
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF0D47A1),
                                Color(0xFF00695C),
                                Color(0xFF1A237E)
                            )
                        )
                    )
            ) {
                // Video visual preview simulation with dynamic waves
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                    
                    // Concentric aesthetic ripples
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = radius * 0.75f,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        radius = radius * 0.5f,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                }

                // Silhouette / Face thumbnail simulation
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Face,
                        contentDescription = "Video Note",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(80.dp)
                    )
                }

                // Play / Pause center action button overlay
                if (!isPlaying) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play Video Note",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(30.dp)
                        )
                    }
                }

                // Expand / Fullscreen viewer button at top left
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(28.dp)
                        .clickable { onOpenViewer?.invoke() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Открыть видеокружочек",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp)
                    )
                }

                // Sound Mute/Unmute toggle at top right of the circle
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(28.dp)
                        .clickable { isMuted = !isMuted }
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp)
                    )
                }

                // Duration badge at bottom left
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = durationText.ifBlank { "0:04" },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Timestamp and Checkmark status at bottom right
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        if (isE2EEncrypted) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "E2E",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            text = formatTime(timestamp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White
                        )
                        if (isMe) {
                            Spacer(Modifier.width(3.dp))
                            if (isRead) {
                                Icon(
                                    Icons.Filled.DoneAll,
                                    contentDescription = "Read",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(12.dp)
                                )
                            } else if (isDelivered) {
                                Icon(
                                    Icons.Filled.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(12.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = "Pending",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Selection Checkmark Badge
            if (isSelectionMode) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) Color(0xFF4CAF50) else Color.Black.copy(alpha = 0.6f),
                    border = BorderStroke(2.dp, if (isSelected) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(28.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Выбрано",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }

            // Telegram Circular Playback Progress Ring
            Canvas(modifier = Modifier.size(208.dp)) {
                if (isPlaying) {
                    drawArc(
                        color = Color(0xFF00E5FF),
                        startAngle = -90f,
                        sweepAngle = 360f * playbackProgress,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Interactive Video Note (Кружочек) Full Viewer Dialog!
 * Displays the video circle in a prominent enlarged view with full playback controls,
 * speed multiplier, scrub bar, audio volume, and quick action options.
 */
@Composable
fun TelegramVideoNoteViewerDialog(
    senderName: String,
    durationText: String,
    timestamp: Long,
    videoUri: String? = null,
    onDismiss: () -> Unit,
    onReply: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null
) {
    FullScreenVideoPlayerDialog(
        videoUri = videoUri,
        senderName = senderName,
        timestamp = timestamp,
        durationText = durationText,
        isCircular = true,
        onDismiss = onDismiss,
        onReply = onReply,
        onForward = onForward
    )
}

private val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private fun formatTime(timestamp: Long): String {
    return timeFormatter.format(java.util.Date(timestamp))
}
