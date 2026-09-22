package com.example.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.random.Random

// 0. Elegant Dark (Default)
@Composable
fun ElegantDarkBackground(opacity: Float = 1.0f) {
    val bgColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val pColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val sColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
    Canvas(modifier = Modifier.fillMaxSize().background(bgColor)) {
        val width = size.width
        val height = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(pColor.copy(alpha = 0.20f * opacity), Color.Transparent),
                center = Offset(width * 0.2f, height * 0.3f),
                radius = min(width, height) * 0.8f
            ),
            center = Offset(width * 0.2f, height * 0.3f),
            radius = min(width, height) * 0.8f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sColor.copy(alpha = 0.20f * opacity), Color.Transparent),
                center = Offset(width * 0.8f, height * 0.7f),
                radius = min(width, height) * 0.8f
            ),
            center = Offset(width * 0.8f, height * 0.7f),
            radius = min(width, height) * 0.8f
        )
    }
}

// 1. Neon Snowflakes
@Composable
fun NeonSnowflakesBackground(isStatic: Boolean = false, isBatterySaver: Boolean = false, opacity: Float = 1.0f) {
    var dt by remember { mutableStateOf(0f) }
    var lastTime by remember { mutableStateOf(0L) }
    LaunchedEffect(isStatic, isBatterySaver) {
        if (!isStatic) {
            while (isActive) {
                androidx.compose.runtime.withFrameNanos { time ->
                    if (lastTime != 0L) {
                        val dtSec = (time - lastTime) / 1_000_000_000f
                        dt = dtSec
                        com.example.utils.PerformanceMonitor.trackFrame(dtSec * 1000f, time / 1_000_000L)
                        if (isBatterySaver) dt = min(dt, 1f/30f) // limit to 30fps simulation if needed or just let it drop frames. Actually, to save battery we should delay.
                    }
                    lastTime = time
                }
                if (isBatterySaver) kotlinx.coroutines.delay(33) // Throttle to ~30 fps
            }
        }
    }

    val snowflakes = remember { List(20) { Snowflake() } }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Trigger recomposition on dt change
        val currentDt = dt
        val intensity = com.example.utils.PerformanceMonitor.animationIntensity.value
        val limit = (snowflakes.size * intensity).toInt().coerceAtLeast(2)
        snowflakes.take(limit).forEach { flake ->
            flake.update(size.width, size.height, currentDt)
            drawCircle(
                color = Color(0xFF00FFFF).copy(alpha = flake.alpha * opacity),
                center = Offset(flake.x, flake.y),
                radius = flake.radius
            )
        }
    }
}

class Snowflake {
    var x = Random.nextFloat() * 1500f
    var y = Random.nextFloat() * 2500f
    var radius = Random.nextFloat() * 4f + 1f
    var speedY = (Random.nextFloat() * 1.5f + 0.5f) * 60f // normalized to 60fps
    var alpha = Random.nextFloat() * 0.5f + 0.5f

    fun update(width: Float, height: Float, dt: Float) {
        if (dt == 0f) return
        y += speedY * dt
        x += sin(y / 100f) * 30f * dt
        if (y > height) {
            y = 0f
            x = Random.nextFloat() * width
        }
    }
}

// 2. Cherry Blossom Petals
@Composable
fun NeonCherryBlossomBackground(isStatic: Boolean = false, isBatterySaver: Boolean = false, opacity: Float = 1.0f) {
    var dt by remember { mutableStateOf(0f) }
    var lastTime by remember { mutableStateOf(0L) }
    LaunchedEffect(isStatic, isBatterySaver) {
        if (!isStatic) {
            while (isActive) {
                androidx.compose.runtime.withFrameNanos { time ->
                    if (lastTime != 0L) {
                        val dtSec = (time - lastTime) / 1_000_000_000f
                        dt = dtSec
                        com.example.utils.PerformanceMonitor.trackFrame(dtSec * 1000f, time / 1_000_000L)
                        if (isBatterySaver) dt = min(dt, 1f/30f)
                    }
                    lastTime = time
                }
                if (isBatterySaver) kotlinx.coroutines.delay(33)
            }
        }
    }
    val petals = remember { List(15) { Petal() } }

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A10))) {
        val currentDt = dt
        val intensity = com.example.utils.PerformanceMonitor.animationIntensity.value
        val limit = (petals.size * intensity).toInt().coerceAtLeast(2)
        petals.take(limit).forEach { petal ->
            petal.update(size.width, size.height, currentDt)
            translate(left = petal.x, top = petal.y) {
                rotate(degrees = petal.rotation) {
                    drawPath(
                        path = petal.path,
                        color = Color(0xFFFF107A).copy(alpha = (petal.alpha + 0.2f) * opacity),
                        style = Fill
                    )
                }
            }
        }
    }
}

class Petal {
    var x = Random.nextFloat() * 1500f
    var y = Random.nextFloat() * 2500f
    var size = Random.nextFloat() * 8f + 4f
    var speedY = (Random.nextFloat() * 2f + 1f) * 60f
    var speedX = (Random.nextFloat() * 2f - 1f) * 60f
    var rotation = Random.nextFloat() * 360f
    var rotSpeed = (Random.nextFloat() * 2f - 1f) * 60f
    var alpha = Random.nextFloat() * 0.6f + 0.2f

    val path = Path().apply {
        moveTo(0f, -size)
        quadraticTo(size, -size, size, 0f)
        quadraticTo(size, size, 0f, size)
        quadraticTo(-size, size, -size, 0f)
        quadraticTo(-size, -size, 0f, -size)
        close()
    }

    fun update(width: Float, height: Float, dt: Float) {
        if (dt == 0f) return
        y += speedY * dt
        x += speedX * dt + sin(y / 150f) * 60f * dt
        rotation += rotSpeed * dt
        if (y > height) {
            y = -50f
            x = Random.nextFloat() * width
        }
    }
}

// 3. Neon Confetti
@Composable
fun NeonConfettiBackground(isStatic: Boolean = false, isBatterySaver: Boolean = false, opacity: Float = 1.0f) {
    var dt by remember { mutableStateOf(0f) }
    var lastTime by remember { mutableStateOf(0L) }
    LaunchedEffect(isStatic, isBatterySaver) {
        if (!isStatic) {
            while (isActive) {
                androidx.compose.runtime.withFrameNanos { time ->
                    if (lastTime != 0L) {
                        val dtSec = (time - lastTime) / 1_000_000_000f
                        dt = dtSec
                        com.example.utils.PerformanceMonitor.trackFrame(dtSec * 1000f, time / 1_000_000L)
                        if (isBatterySaver) dt = min(dt, 1f/30f)
                    }
                    lastTime = time
                }
                if (isBatterySaver) kotlinx.coroutines.delay(33)
            }
        }
    }
    val colors = listOf(Color(0xFF00FFFF), Color(0xFFFF00FF), Color(0xFFFFFF00), Color(0xFF39FF14))
    val confettiList = remember { List(30) { Confetti(colors.random()) } }

    val bgColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val pColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val sColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
    Canvas(modifier = Modifier.fillMaxSize().background(bgColor)) {
        val currentDt = dt
        val intensity = com.example.utils.PerformanceMonitor.animationIntensity.value
        val limit = (confettiList.size * intensity).toInt().coerceAtLeast(2)
        confettiList.take(limit).forEach { c ->
            c.update(size.width, size.height, currentDt)
            translate(c.x, c.y) {
                rotate(c.rotation) {
                    drawRect(
                        color = c.color.copy(alpha = 0.8f * opacity),
                        size = Size(c.w, c.h)
                    )
                }
            }
        }
    }
}

class Confetti(val color: Color) {
    var x = Random.nextFloat() * 1500f
    var y = -Random.nextFloat() * 2000f
    var w = Random.nextFloat() * 12f + 4f
    var h = Random.nextFloat() * 18f + 6f
    var speedY = (Random.nextFloat() * 4f + 3f) * 60f
    var rotation = Random.nextFloat() * 360f
    var rotSpeed = (Random.nextFloat() * 5f - 2.5f) * 60f

    fun update(width: Float, height: Float, dt: Float) {
        if (dt == 0f) return
        y += speedY * dt
        rotation += rotSpeed * dt
        if (y > height) {
            y = -50f
            x = Random.nextFloat() * width
        }
    }
}

// 4. Neon Moon System
@Composable
fun NeonMoonBackground(opacity: Float = 1.0f) {
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF020205))) {
        // Starry Sky
        val intensity = com.example.utils.PerformanceMonitor.animationIntensity.value
        val limit = (200 * intensity).toInt().coerceAtLeast(10)
        for (i in 0..limit) {
            val sx = sin((i * 73).toFloat()) * size.width + size.width
            val sy = cos((i * 31).toFloat()) * size.height + size.height
            val alpha = (sin(time * 0.1f + i) + 1f) / 2f
            val r = ((i * 17) % 20) / 10f + 0.5f
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.8f * opacity),
                center = Offset(sx % size.width, sy % size.height),
                radius = r
            )
        }
        
        // Neon Moon (more realistic via overlapping gradients/shapes)
        val moonCenter = Offset(size.width * 0.8f, size.height * 0.2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFB500FF).copy(alpha = 0.5f * opacity), Color.Transparent),
                center = moonCenter,
                radius = 200f
            ),
            center = moonCenter,
            radius = 200f
        )
        drawCircle(
            color = Color(0xFFF2F0F5).copy(alpha = opacity),
            center = moonCenter,
            radius = 100f
        )
        // Realistic moon craters (simple layered circles)
        drawCircle(color = Color(0xFFD0CBD5).copy(alpha = 0.8f * opacity), center = Offset(moonCenter.x + 20f, moonCenter.y - 30f), radius = 15f)
        drawCircle(color = Color(0xFFD0CBD5).copy(alpha = 0.6f * opacity), center = Offset(moonCenter.x - 40f, moonCenter.y + 10f), radius = 22f)
        drawCircle(color = Color(0xFFD0CBD5).copy(alpha = 0.7f * opacity), center = Offset(moonCenter.x + 10f, moonCenter.y + 40f), radius = 12f)
        drawCircle(color = Color(0xFFD0CBD5).copy(alpha = 0.5f * opacity), center = Offset(moonCenter.x - 20f, moonCenter.y - 50f), radius = 18f)
    }
}

// 5. Yellow Neon Room with Fog 
@Composable
fun NeonRoomFogBackground(opacity: Float = 1.0f) {
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        val yellowNeon = Color(0xFFFFF000).copy(alpha = opacity)
        val yellowGlow = Color(0xFFFFF000).copy(alpha = 0.4f * opacity)
        val yellowGlowOuter = Color(0xFFFFF000).copy(alpha = 0.15f * opacity)
        val h = size.height
        val w = size.width

        // Draw a subtle radial gradient for the background depth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1A1A1A), Color(0xFF050505)),
                center = Offset(w / 2, h / 2),
                radius = h
            )
        )

        // The 3 lines on left
        for (i in 0..2) {
            val startX = 0f
            val endX = w * 0.35f
            val dropY = w * 0.25f
            val spacingY = h * 0.22f
            val startY = h * 0.15f + (i * spacingY)
            val endY = startY + dropY
            
            drawLine(color = yellowGlowOuter, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 60f)
            drawLine(color = yellowGlow, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 24f)
            drawLine(color = yellowNeon, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 8f)
        }

        // The 3 lines on right
        for (i in 0..2) {
            val startX = w
            val endX = w * 0.65f
            val dropY = w * 0.25f
            val spacingY = h * 0.22f
            val startY = h * 0.15f + (i * spacingY)
            val endY = startY + dropY
            
            drawLine(color = yellowGlowOuter, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 60f)
            drawLine(color = yellowGlow, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 24f)
            drawLine(color = yellowNeon, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 8f)
        }

        // Platforms
        drawRect(
            color = Color(0xFF111111),
            topLeft = Offset(w * 0.1f, h * 0.75f),
            size = Size(w * 0.8f, h * 0.08f)
        )
        drawRect(
            color = Color(0xFF1A1A1A),
            topLeft = Offset(w * 0.25f, h * 0.83f),
            size = Size(w * 0.5f, h * 0.17f)
        )

        // Animated light fog at bottom
        val fogAlpha = 0.15f + 0.15f * sin(time * 2 * Math.PI).toFloat()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xFFFFF000).copy(alpha = fogAlpha * opacity)),
                startY = h * 0.65f,
                endY = h
            ),
            topLeft = Offset(0f, h * 0.65f),
            size = Size(w, h * 0.35f)
        )
    }
}

// 7. Neon Line Chart
@Composable
fun NeonLineChart(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF00FF)
) {
    if (dataPoints.isEmpty()) return
    
    val maxPoint = dataPoints.maxOrNull() ?: 1f
    val minPoint = dataPoints.minOrNull() ?: 0f
    val range = (maxPoint - minPoint).coerceAtLeast(1f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        val pointSpacing = if (dataPoints.size > 1) width / (dataPoints.size - 1) else width
        val strokeWidth = 3.dp.toPx()
        
        val path = androidx.compose.ui.graphics.Path()
        
        dataPoints.forEachIndexed { index, value ->
            val x = index * pointSpacing
            // Invert Y axis
            val normalizedY = 1f - ((value - minPoint) / range)
            val y = normalizedY * height
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Draw glow
        drawPath(
            path = path,
            color = color.copy(alpha = 0.3f),
            style = Stroke(width = strokeWidth * 3, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
        
        // Draw core
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        )
        
        // Draw points
        dataPoints.forEachIndexed { index, value ->
            val x = index * pointSpacing
            val normalizedY = 1f - ((value - minPoint) / range)
            val y = normalizedY * height
            drawCircle(
                color = Color.White,
                radius = strokeWidth * 1.5f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun NeonLoadingSpinner(modifier: Modifier = Modifier, size: Dp = 48.dp, color: Color = Color(0xFF00FFFF)) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier.size(size)) {
        val sweepAngle = 270f
        val strokeWidth = 4.dp.toPx()
        val radius = this.size.width / 2f - strokeWidth / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Outer glow
        drawArc(
            color = color.copy(alpha = 0.3f),
            startAngle = rotation,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth * 3, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        // Inner core
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}
