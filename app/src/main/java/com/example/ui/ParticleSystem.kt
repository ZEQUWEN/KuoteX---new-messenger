package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.isActive
import kotlin.random.Random
import androidx.compose.ui.geometry.Size

class ParticleState(
    var x: Float,
    var y: Float,
    var speedX: Float,
    var speedY: Float,
    var radius: Float,
    var alpha: Float,
    var color: Color
)

@Composable
fun ParticleOverlay(theme: AppTheme) {
    // Only show overlay for snowflake or cherry blossom themes
    if (theme != AppTheme.NEON_SNOWFLAKES && theme != AppTheme.NEON_CHERRY_BLOSSOM) return
    
    val isSnowflake = theme == AppTheme.NEON_SNOWFLAKES
    val particleCount = if (isSnowflake) 40 else 30
    
    val particles = remember(theme) {
        List(particleCount) {
            ParticleState(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speedX = if (isSnowflake) Random.nextFloat() * 0.1f - 0.05f else Random.nextFloat() * 0.15f + 0.05f,
                speedY = if (isSnowflake) Random.nextFloat() * 0.2f + 0.1f else Random.nextFloat() * 0.15f + 0.1f,
                radius = if (isSnowflake) Random.nextFloat() * 6f + 2f else Random.nextFloat() * 8f + 4f,
                alpha = Random.nextFloat() * 0.6f + 0.2f,
                color = if (isSnowflake) Color.White else Color(0xFFFFB7C5)
            )
        }
    }

    var lastFrameTime by remember { mutableStateOf(0L) }

    LaunchedEffect(theme) {
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { time ->
                if (lastFrameTime != 0L) {
                    val dt = (time - lastFrameTime) / 1_000_000_000f // seconds
                    com.example.utils.PerformanceMonitor.trackFrame(dt * 1000f, time / 1_000_000L)
                    val intensity = com.example.utils.PerformanceMonitor.animationIntensity.value
        val limit = (particles.size * intensity).toInt().coerceAtLeast(2)
        for (i in 0 until limit) {
            val p = particles[i]
                        p.x += p.speedX * dt
                        p.y += p.speedY * dt
                        
                        if (p.y > 1.1f) {
                            p.y = -0.1f
                            p.x = Random.nextFloat()
                        }
                        if (p.x > 1.1f) p.x = -0.1f
                        if (p.x < -0.1f) p.x = 1.1f
                    }
                }
                lastFrameTime = time
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val currentFrame = lastFrameTime
        val w = size.width
        val h = size.height
        
        val intensity = com.example.utils.PerformanceMonitor.animationIntensity.value
        val limit = (particles.size * intensity).toInt().coerceAtLeast(2)
        for (i in 0 until limit) {
            val p = particles[i]
            drawCircle(
                color = p.color.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(p.x * w, p.y * h)
            )
        }
    }
}
