package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.ui.LiveStreamSession
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingPipStreamPlayer(
    session: LiveStreamSession,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onSwitchToBackgroundAudio: () -> Unit = {},
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Screen bounds in px
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val playerWidthDp = 180.dp
    val playerHeightDp = 120.dp
    val playerWidthPx = with(density) { playerWidthDp.toPx() }
    val playerHeightPx = with(density) { playerHeightDp.toPx() }

    // Bounds with margins
    val minX = with(density) { 10.dp.toPx() }
    val maxX = screenWidthPx - playerWidthPx - with(density) { 10.dp.toPx() }
    val minY = with(density) { 64.dp.toPx() }
    val maxY = screenHeightPx - playerHeightPx - with(density) { 48.dp.toPx() }

    // Initial position: Bottom right corner with margins
    val initialX = maxX
    val initialY = screenHeightPx - playerHeightPx - with(density) { 110.dp.toPx() }

    // Spring animatables for physics-based fluid dragging & snapping
    val offsetXAnim = remember { Animatable(initialX) }
    val offsetYAnim = remember { Animatable(initialY) }

    // Gentle spring scale on tap/drag for tactile responsiveness
    var isDragging by remember { mutableStateOf(false) }
    val pipScale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pip_drag_scale"
    )

    var showControls by remember { mutableStateOf(false) }
    var isExpandingAnimation by remember { mutableStateOf(false) }
    val expansionProgress = remember { Animatable(0f) }

    val buttonScale by animateFloatAsState(
        targetValue = if (isExpandingAnimation) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expand_button_scale"
    )

    fun triggerExpand() {
        if (!isExpandingAnimation) {
            isExpandingAnimation = true
            com.example.analytics.AnalyticsTracker.logButtonClick(
                buttonName = "expand_pip_to_stream",
                module = "stream",
                metadata = mapOf("stream_id" to session.id, "host_id" to session.hostUserId)
            )
            coroutineScope.launch {
                expansionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
                onExpand()
            }
        }
    }

    // Ambient animated gradient for simulated stream video
    val infiniteTransition = rememberInfiniteTransition(label = "pip_video_sim")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pip_grad"
    )
    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pip_live_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetXAnim.value.roundToInt(), offsetYAnim.value.roundToInt()) }
                .size(playerWidthDp, playerHeightDp)
                .scale(pipScale)
                .shadow(
                    elevation = if (isDragging) 24.dp else 16.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = Color(0xFF00E5FF).copy(alpha = if (isDragging) 0.5f else 0.35f)
                )
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.85f),
                            Color(0xFF0088CC).copy(alpha = 0.55f),
                            Color(0xFFE040FB).copy(alpha = 0.85f)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .background(Color(0xFF10141E))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            isDragging = false
                            // Spring snap to nearest edge (left or right) for magnetic smoothness
                            val midX = (minX + maxX) / 2f
                            val targetSnapX = if (offsetXAnim.value < midX) minX else maxX
                            val targetSnapY = offsetYAnim.value.coerceIn(minY, maxY)
                            coroutineScope.launch {
                                offsetXAnim.animateTo(
                                    targetSnapX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                            coroutineScope.launch {
                                offsetYAnim.animateTo(
                                    targetSnapY,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val newX = (offsetXAnim.value + dragAmount.x).coerceIn(minX - 20f, maxX + 20f)
                            val newY = (offsetYAnim.value + dragAmount.y).coerceIn(minY - 20f, maxY + 20f)
                            offsetXAnim.snapTo(newX)
                            offsetYAnim.snapTo(newY)
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
        ) {
            // Simulated Live Stream Video Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1A2A44),
                                Color(0xFF0F172A),
                                Color(0xFF070A12)
                            ),
                            center = Offset(gradientShift % 600f, (gradientShift * 1.3f) % 400f),
                            radius = 400f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Background Avatar / Poster
                if (session.hostAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = session.hostAvatarUrl,
                        contentDescription = "Streamer Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                    )
                    // Darkening overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                }

                // Small center avatar
                AsyncImage(
                    model = session.hostAvatarUrl.ifBlank { "https://picsum.photos/seed/${session.hostUserId}/100" },
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFF00E5FF), CircleShape)
                )
            }

            // Top Header in PiP: LIVE badge and drag handle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live indicator pill
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFFF1744).copy(alpha = livePulseAlpha), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${session.viewerCount}",
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Close Button in top right
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .clickable {
                            com.example.analytics.AnalyticsTracker.logButtonClick(
                                buttonName = "close_pip",
                                module = "stream",
                                metadata = mapOf("stream_id" to session.id)
                            )
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close PiP",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Bottom Stream Title & Streamer Name
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = session.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.hostDisplayName.ifBlank { session.hostUsername },
                    color = Color(0xFF00E5FF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Interactive Controls Overlay with Spring Animation
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin.Center,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + scaleOut(
                    targetScale = 0.85f,
                    transformOrigin = TransformOrigin.Center,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.70f))
                ) {
                    // Center Single Action: Vector Diagonal Expand Arrows Button
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanding shockwave ripple ring on tap
                        if (isExpandingAnimation) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .scale(1f + expansionProgress.value * 0.9f)
                                    .border(
                                        width = 2.dp,
                                        color = Color(0xFF00E5FF).copy(alpha = (1f - expansionProgress.value).coerceIn(0f, 1f)),
                                        shape = CircleShape
                                    )
                            )
                        }

                        // Main vector expand button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .scale(buttonScale)
                                .shadow(
                                    elevation = if (isExpandingAnimation) 12.dp else 6.dp,
                                    shape = CircleShape,
                                    spotColor = Color(0xFF00E5FF)
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.90f),
                                            Color(0xFF0088CC).copy(alpha = 0.90f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    triggerExpand()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            DiagonalExpandArrowsIcon(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.4.dp,
                                expansionFraction = expansionProgress.value
                            )
                        }
                    }

                    // Drag hint label at top center
                    Text(
                        text = "Перетащите",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Vector icon with two diagonal inclined arrows pointing in opposite directions (top-right and bottom-left).
 * Supports animated expansion where arrows slide outward along the diagonal axis.
 */
@Composable
fun DiagonalExpandArrowsIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: androidx.compose.ui.unit.Dp = 2.5.dp,
    expansionFraction: Float = 0f
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dim = minOf(w, h)
        val strokePx = strokeWidth.toPx()

        val cx = w / 2f
        val cy = h / 2f

        // Padding from Canvas boundary
        val padding = dim * 0.12f
        val expandOffset = expansionFraction * (dim * 0.14f)

        // Top-Right corner point
        val trX = w - padding
        val trY = padding

        // Bottom-Left corner point
        val blX = padding
        val blY = h - padding

        // Arrow head wing length
        val headLen = dim * 0.32f

        // Base gap from center that widens during animated expansion
        val gapDistance = (dim * 0.10f) + expandOffset
        val shaftStartTrX = cx + gapDistance * 0.7071f
        val shaftStartTrY = cy - gapDistance * 0.7071f

        val shaftStartBlX = cx - gapDistance * 0.7071f
        val shaftStartBlY = cy + gapDistance * 0.7071f

        // 1. Top-Right Arrow
        // Shaft diagonal line
        drawLine(
            color = color,
            start = Offset(shaftStartTrX, shaftStartTrY),
            end = Offset(trX, trY),
            strokeWidth = strokePx,
            cap = androidx.compose.ui.graphics.StrokeCap.Square
        )
        // Arrowhead (Horizontal left line and vertical down line meeting at tr corner)
        val trHeadPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(trX - headLen, trY)
            lineTo(trX, trY)
            lineTo(trX, trY + headLen)
        }
        drawPath(
            path = trHeadPath,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokePx,
                cap = androidx.compose.ui.graphics.StrokeCap.Square,
                join = androidx.compose.ui.graphics.StrokeJoin.Miter
            )
        )

        // 2. Bottom-Left Arrow
        // Shaft diagonal line
        drawLine(
            color = color,
            start = Offset(shaftStartBlX, shaftStartBlY),
            end = Offset(blX, blY),
            strokeWidth = strokePx,
            cap = androidx.compose.ui.graphics.StrokeCap.Square
        )
        // Arrowhead (Horizontal right line and vertical up line meeting at bl corner)
        val blHeadPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(blX + headLen, blY)
            lineTo(blX, blY)
            lineTo(blX, blY - headLen)
        }
        drawPath(
            path = blHeadPath,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokePx,
                cap = androidx.compose.ui.graphics.StrokeCap.Square,
                join = androidx.compose.ui.graphics.StrokeJoin.Miter
            )
        )
    }
}
