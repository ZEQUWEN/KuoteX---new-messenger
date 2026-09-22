package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.KuoteXLogoImage
import kotlinx.coroutines.delay

/**
 * KuoteX Splash Screen featuring:
 * 1. The glowing neon Iceberg animation rising from the deep.
 * 2. The authentic KuoteX metallic emblem (the stylized "K" logo with the signature 45-degree diagonal slit).
 * 3. Telegram-like seamless zoom/fade transition into the main chat list.
 */
@Composable
fun SplashScreen(
    splashAlpha: Float = 1f,
    exitScale: Float = 1f,
    onAnimationReadyToTransition: () -> Unit = {},
    onTransitionComplete: () -> Unit = {},
    onTimeout: (() -> Unit)? = null
) {
    var isVisible by remember { mutableStateOf(false) }
    var showLogo by remember { mutableStateOf(false) }
    var animationCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
        // Iceberg rises first, then KuoteX emblem emerges into focus
        delay(700)
        showLogo = true
        // Allow user to view the full emblem and branding
        delay(1800)
        if (!animationCompleted) {
            animationCompleted = true
            onAnimationReadyToTransition()
            onTimeout?.invoke()
        }
    }

    val transition = updateTransition(targetState = isVisible, label = "splashTransition")

    val icebergScale by transition.animateFloat(
        transitionSpec = { tween(1600, easing = FastOutSlowInEasing) },
        label = "icebergScale"
    ) { visible ->
        if (visible) 1f else 0.4f
    }

    val glowOpacity by transition.animateFloat(
        transitionSpec = { tween(1400, easing = LinearEasing) },
        label = "glowOpacity"
    ) { visible ->
        if (visible) 0.85f else 0f
    }

    val yOffset by transition.animateFloat(
        transitionSpec = { tween(1600, easing = FastOutSlowInEasing) },
        label = "yOffset"
    ) { visible ->
        if (visible) 0f else 180f
    }

    // Logo entrance animation
    val logoAlpha by animateFloatAsState(
        targetValue = if (showLogo) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    val logoScale by animateFloatAsState(
        targetValue = if (showLogo) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )

    // Pulsing halo around the KuoteX logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = splashAlpha
                scaleX = exitScale
                scaleY = exitScale
            }
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF030712), // Deep carbon
                        Color(0xFF0B0F19), // Midnight navy
                        Color(0xFF10141E)  // Dark slate
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap to immediately transition into the main chat list (Telegram feature)
                if (!animationCompleted) {
                    animationCompleted = true
                    onAnimationReadyToTransition()
                    onTimeout?.invoke()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Background Canvas: Brushed metal subtle grain + Iceberg neon artwork
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2 + yOffset + 40f // positioned slightly below logo

            // Draw subtle vertical brushed steel grain lines (matching the metallic photo background)
            val step = 12f
            var x = 0f
            while (x < width) {
                val alphaVal = if (((x.toInt() / 12) % 3) == 0) 0.04f else 0.018f
                drawLine(
                    color = Color.White.copy(alpha = alphaVal),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
                x += step
            }

            // Radial Neon Aqua/Cyan Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = glowOpacity * 0.45f),
                        Color(0xFF7C3AED).copy(alpha = glowOpacity * 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = 420f * icebergScale
                ),
                radius = 420f * icebergScale,
                center = Offset(centerX, centerY)
            )

            // Iceberg Polygon Paths
            val icebergPath = Path().apply {
                moveTo(centerX, centerY - 180f * icebergScale)
                lineTo(centerX + 60f * icebergScale, centerY - 80f * icebergScale)
                lineTo(centerX + 120f * icebergScale, centerY - 20f * icebergScale)
                lineTo(centerX + 160f * icebergScale, centerY + 80f * icebergScale)
                lineTo(centerX - 140f * icebergScale, centerY + 80f * icebergScale)
                lineTo(centerX - 90f * icebergScale, centerY - 40f * icebergScale)
                lineTo(centerX - 40f * icebergScale, centerY - 90f * icebergScale)
                close()
            }

            val leftFacet = Path().apply {
                moveTo(centerX, centerY - 180f * icebergScale)
                lineTo(centerX - 40f * icebergScale, centerY - 90f * icebergScale)
                lineTo(centerX - 90f * icebergScale, centerY - 40f * icebergScale)
                lineTo(centerX - 140f * icebergScale, centerY + 80f * icebergScale)
                lineTo(centerX, centerY + 80f * icebergScale)
                close()
            }

            val rightFacet = Path().apply {
                moveTo(centerX, centerY - 180f * icebergScale)
                lineTo(centerX + 60f * icebergScale, centerY - 80f * icebergScale)
                lineTo(centerX + 120f * icebergScale, centerY - 20f * icebergScale)
                lineTo(centerX + 160f * icebergScale, centerY + 80f * icebergScale)
                lineTo(centerX, centerY + 80f * icebergScale)
                close()
            }

            val ridgePath = Path().apply {
                moveTo(centerX, centerY - 180f * icebergScale)
                lineTo(centerX - 20f * icebergScale, centerY - 50f * icebergScale)
                lineTo(centerX + 10f * icebergScale, centerY + 30f * icebergScale)
                lineTo(centerX, centerY + 80f * icebergScale)
            }

            // Facet fills with cyber-ice shading
            drawPath(
                path = leftFacet,
                color = Color(0xFFB3E5FC).copy(alpha = 0.85f),
                style = Fill
            )
            drawPath(
                path = rightFacet,
                color = Color(0xFF81D4FA).copy(alpha = 0.85f),
                style = Fill
            )

            // Neon glowing edge strokes
            drawPath(
                path = icebergPath,
                color = Color(0xFF00E5FF).copy(alpha = 0.95f),
                style = Stroke(width = 3.5f)
            )

            drawPath(
                path = ridgePath,
                color = Color(0xFF00B8D4).copy(alpha = 0.7f),
                style = Stroke(width = 2.5f)
            )

            // Dark Water Surface
            drawRect(
                color = Color(0xFF050B14).copy(alpha = 0.92f),
                topLeft = Offset(0f, centerY + 80f * icebergScale),
                size = Size(width, height - (centerY + 80f * icebergScale))
            )

            // Iceberg Subsurface Reflection
            val reflectionPath = Path().apply {
                moveTo(centerX - 140f * icebergScale, centerY + 80f * icebergScale)
                lineTo(centerX + 160f * icebergScale, centerY + 80f * icebergScale)
                lineTo(centerX + 100f * icebergScale, centerY + 180f * icebergScale)
                lineTo(centerX, centerY + 240f * icebergScale)
                lineTo(centerX - 80f * icebergScale, centerY + 180f * icebergScale)
                close()
            }
            drawPath(
                path = reflectionPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.35f),
                        Color(0xFF7C3AED).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    startY = centerY + 80f * icebergScale,
                    endY = centerY + 250f * icebergScale
                ),
                style = Fill
            )

            // Laser Waterline
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF00E5FF).copy(alpha = 0.9f),
                        Color(0xFF00E5FF).copy(alpha = 0.9f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, centerY + 80f * icebergScale),
                end = Offset(width, centerY + 80f * icebergScale),
                strokeWidth = 2.5f
            )
        }

        // Center Emblem & Brand Group (Iceberg + KuoteX Logo integration)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-20).dp)
                .graphicsLayer {
                    alpha = logoAlpha
                    scaleX = logoScale
                    scaleY = logoScale
                }
        ) {
            // KuoteX Metallic Logo Badge (Brushed aluminum badge with stylized K logo)
            KuoteXLogoImage(
                size = 116.dp,
                shape = RoundedCornerShape(26.dp),
                showGlow = true,
                glowColor = Color(0xFF00E5FF).copy(alpha = haloPulse),
                elevation = 16.dp
            )

            Spacer(modifier = Modifier.height(22.dp))

            // "KUOTEX" Title
            Text(
                text = "KUOTEX",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "SECURE MESSENGER",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp
                ),
                color = Color(0xFF00E5FF).copy(alpha = 0.85f)
            )
        }

        // Tap hint at bottom
        Text(
            text = "Нажмите для пропуска",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        )
    }
}
