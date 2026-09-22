package com.example.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.isActive
import kotlin.random.Random
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

enum class ParticleSystemType {
    Snowflakes,
    Confetti,
    SakuraPetals,
    NeonMoonAndStars,
    NeonYellowFogRoom
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var color: Color,
    var alpha: Float,
    var rotation: Float = 0f,
    var vRotation: Float = 0f,
    var type: Int = 0,
    var life: Float = 1f,
    var maxLife: Float = 1f
)

@Composable
fun NeonParticleSystem(
    modifier: Modifier = Modifier,
    type: ParticleSystemType
) {
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }
    
    LaunchedEffect(type, width, height) {
        if (width == 0f || height == 0f) return@LaunchedEffect
        
        // Initialize particles based on type
        val initialParticles = mutableListOf<Particle>()
        val count = when (type) {
            ParticleSystemType.Snowflakes -> 100
            ParticleSystemType.Confetti -> 150
            ParticleSystemType.SakuraPetals -> 80
            ParticleSystemType.NeonMoonAndStars -> 120
            ParticleSystemType.NeonYellowFogRoom -> 50
        }
        
        for (i in 0 until count) {
            initialParticles.add(createParticle(type, width, height, true))
        }
        particles = initialParticles
        
        var lastTime = 0L
        while (isActive) {
            withInfiniteAnimationFrameMillis { time ->
                if (lastTime == 0L) lastTime = time
                val dt = (time - lastTime) / 1000f
                lastTime = time
                
                particles = particles.map { p ->
                    updateParticle(p, dt, type, width, height)
                }
            }
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        width = size.width
        height = size.height
        
        when (type) {
            ParticleSystemType.NeonYellowFogRoom -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF222200),
                            Color(0xFF555500),
                            Color(0xFF888800)
                        )
                    )
                )
            }
            ParticleSystemType.NeonMoonAndStars -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF000022),
                            Color(0xFF000044)
                        )
                    )
                )
                drawCircle(
                    color = Color(0xFF00FFFF),
                    radius = 100f,
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    alpha = 0.9f
                )
                drawCircle(
                    color = Color(0xFF000022),
                    radius = 80f,
                    center = Offset(size.width * 0.75f, size.height * 0.18f),
                    alpha = 1f
                )
            }
            else -> {}
        }
        
        particles.forEach { p ->
            drawParticle(p, type)
        }
    }
}

private fun createParticle(type: ParticleSystemType, w: Float, h: Float, randomizeY: Boolean = false): Particle {
    val r = Random.Default
    val y = if (randomizeY) r.nextFloat() * h else -50f
    
    return when (type) {
        ParticleSystemType.Snowflakes -> Particle(
            x = r.nextFloat() * w,
            y = y,
            vx = (r.nextFloat() - 0.5f) * 20f,
            vy = r.nextFloat() * 30f + 20f,
            size = r.nextFloat() * 5f + 2f,
            color = Color(0xFF00FFFF), // Neon cyan
            alpha = r.nextFloat() * 0.5f + 0.3f,
            rotation = r.nextFloat() * 360f,
            vRotation = (r.nextFloat() - 0.5f) * 90f
        )
        ParticleSystemType.Confetti -> {
            val colors = listOf(Color(0xFFFF00FF), Color(0xFF00FFFF), Color(0xFFFFFF00), Color(0xFF00FF00))
            Particle(
                x = r.nextFloat() * w,
                y = y,
                vx = (r.nextFloat() - 0.5f) * 100f,
                vy = r.nextFloat() * 100f + 50f,
                size = r.nextFloat() * 10f + 5f,
                color = colors.random(),
                alpha = 1f,
                rotation = r.nextFloat() * 360f,
                vRotation = (r.nextFloat() - 0.5f) * 360f,
                type = r.nextInt(2) // 0 for rect, 1 for circle
            )
        }
        ParticleSystemType.SakuraPetals -> Particle(
            x = r.nextFloat() * w,
            y = y,
            vx = (r.nextFloat() - 0.5f) * 40f + 20f, // Wind effect
            vy = r.nextFloat() * 40f + 30f,
            size = r.nextFloat() * 8f + 6f,
            color = Color(0xFFFF66FF), // Neon pink
            alpha = r.nextFloat() * 0.4f + 0.6f,
            rotation = r.nextFloat() * 360f,
            vRotation = (r.nextFloat() - 0.5f) * 120f
        )
        ParticleSystemType.NeonMoonAndStars -> {
            val isShootingStar = r.nextFloat() < 0.05f
            val life = if (isShootingStar) r.nextFloat() * 1f + 0.5f else 1f
            Particle(
                x = r.nextFloat() * w,
                y = if (isShootingStar) r.nextFloat() * (h/2) else r.nextFloat() * h,
                vx = if (isShootingStar) r.nextFloat() * 300f + 200f else 0f,
                vy = if (isShootingStar) r.nextFloat() * 100f + 50f else 0f,
                size = if (isShootingStar) r.nextFloat() * 3f + 2f else r.nextFloat() * 3f + 1f,
                color = Color(0xFFFFFFFF),
                alpha = if (isShootingStar) 1f else r.nextFloat() * 0.5f + 0.1f,
                type = if (isShootingStar) 1 else 0,
                life = life,
                maxLife = life
            )
        }
        ParticleSystemType.NeonYellowFogRoom -> Particle(
            x = r.nextFloat() * w,
            y = r.nextFloat() * h,
            vx = (r.nextFloat() - 0.5f) * 20f,
            vy = (r.nextFloat() - 0.5f) * 10f - 10f, // Slowly moving up
            size = r.nextFloat() * 100f + 50f,
            color = Color(0xFFFFFF00), // Neon yellow
            alpha = r.nextFloat() * 0.1f + 0.05f,
            life = r.nextFloat() * 5f + 5f,
            maxLife = 10f
        )
    }
}

private fun updateParticle(p: Particle, dt: Float, type: ParticleSystemType, w: Float, h: Float): Particle {
    p.x += p.vx * dt
    p.y += p.vy * dt
    p.rotation += p.vRotation * dt
    
    when (type) {
        ParticleSystemType.Snowflakes, ParticleSystemType.SakuraPetals, ParticleSystemType.Confetti -> {
            if (p.y > h + 50f || p.x < -50f || p.x > w + 50f) {
                return createParticle(type, w, h, false)
            }
        }
        ParticleSystemType.NeonMoonAndStars -> {
            if (p.type == 1) { // Shooting star
                p.life -= dt
                if (p.life <= 0f || p.x > w + 50f || p.y > h + 50f) {
                    return createParticle(type, w, h, false)
                }
            } else {
                // Twinkling
                p.alpha = p.alpha + (Random.Default.nextFloat() - 0.5f) * 0.1f
                p.alpha = p.alpha.coerceIn(0.1f, 0.8f)
            }
        }
        ParticleSystemType.NeonYellowFogRoom -> {
            p.life -= dt
            if (p.life <= 0f || p.y < -150f) {
                return createParticle(type, w, h, false)
            }
            // Fade in and out
            p.alpha = if (p.life > p.maxLife / 2) {
                (p.maxLife - p.life) / (p.maxLife / 2) * 0.15f
            } else {
                p.life / (p.maxLife / 2) * 0.15f
            }
        }
    }
    
    return p
}

private fun DrawScope.drawParticle(p: Particle, type: ParticleSystemType) {
    withTransform({
        translate(p.x, p.y)
        rotate(p.rotation)
    }) {
        when (type) {
            ParticleSystemType.Snowflakes -> {
                drawCircle(
                    color = p.color,
                    radius = p.size,
                    alpha = p.alpha
                )
                // Glow effect
                drawCircle(
                    color = p.color,
                    radius = p.size * 2f,
                    alpha = p.alpha * 0.3f
                )
            }
            ParticleSystemType.Confetti -> {
                if (p.type == 0) {
                    drawRect(
                        color = p.color,
                        size = androidx.compose.ui.geometry.Size(p.size, p.size * 2f),
                        alpha = p.alpha
                    )
                } else {
                    drawCircle(
                        color = p.color,
                        radius = p.size,
                        alpha = p.alpha
                    )
                }
            }
            ParticleSystemType.SakuraPetals -> {
                val path = Path().apply {
                    moveTo(0f, -p.size)
                    quadraticTo(p.size, -p.size, p.size, 0f)
                    quadraticTo(p.size, p.size, 0f, p.size * 1.5f)
                    quadraticTo(-p.size, p.size, -p.size, 0f)
                    quadraticTo(-p.size, -p.size, 0f, -p.size)
                    close()
                }
                drawPath(path, color = p.color, alpha = p.alpha)
                // Glow
                drawPath(path, color = p.color, alpha = p.alpha * 0.3f)
            }
            ParticleSystemType.NeonMoonAndStars -> {
                if (p.type == 1) { // Shooting star
                    drawLine(
                        color = p.color,
                        start = Offset(0f, 0f),
                        end = Offset(-p.size * 10f, -p.size * 3f),
                        strokeWidth = p.size,
                        alpha = p.alpha
                    )
                } else {
                    drawCircle(
                        color = p.color,
                        radius = p.size,
                        alpha = p.alpha
                    )
                    // Star glow
                    drawCircle(
                        color = p.color,
                        radius = p.size * 2.5f,
                        alpha = p.alpha * 0.4f
                    )
                }
            }
            ParticleSystemType.NeonYellowFogRoom -> {
                drawCircle(
                    color = p.color,
                    radius = p.size,
                    alpha = p.alpha
                )
            }
        }
    }
}
