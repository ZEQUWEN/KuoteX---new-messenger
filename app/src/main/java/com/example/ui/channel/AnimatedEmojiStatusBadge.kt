package com.example.ui.channel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AnimatedEmojiStatusBadge
 * Displays Telegram-style animated custom or preset emoji badges in channel/group headers,
 * chat list items, profile headers, and top bars.
 */
@Composable
fun AnimatedEmojiStatusBadge(
    emoji: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    fontSize: TextUnit = 13.sp,
    isAnimated: Boolean = true,
    glowColor: Color = Color(0xFF00E5FF),
    showBackgroundBadge: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    if (emoji.isNullOrBlank()) return

    val infiniteTransition = rememberInfiniteTransition(label = "emoji_status_anim")

    // Pulsing scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Gentle wobble rotation
    val wobbleRotation by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble_rotation"
    )

    // Glowing aura alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val appliedModifier = modifier
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )

    Box(
        modifier = appliedModifier,
        contentAlignment = Alignment.Center
    ) {
        if (showBackgroundBadge) {
            // Neon glowing ring badge
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = if (isAnimated) 0.35f * glowAlpha else 0.25f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                glowColor.copy(alpha = if (isAnimated) glowAlpha else 0.6f),
                                Color(0xFFE040FB).copy(alpha = 0.4f),
                                glowColor.copy(alpha = if (isAnimated) glowAlpha else 0.6f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = fontSize,
                    modifier = Modifier.graphicsLayer {
                        if (isAnimated) {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            rotationZ = wobbleRotation
                        }
                    }
                )
            }
        } else {
            // Standalone floating emoji
            Text(
                text = emoji,
                fontSize = fontSize,
                modifier = Modifier.graphicsLayer {
                    if (isAnimated) {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        rotationZ = wobbleRotation
                    }
                }
            )
        }
    }
}

/**
 * Large Hero Emoji Status Banner for Profile Headers
 */
@Composable
fun LargeHeroEmojiBadge(
    emoji: String?,
    colorPalette: ProfileColorPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (emoji.isNullOrBlank()) return

    val infiniteTransition = rememberInfiniteTransition(label = "hero_emoji_anim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colorPalette.quoteBackground)
            .border(1.dp, colorPalette.quoteBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = floatY
                scaleX = ringScale
                scaleY = ringScale
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
        }
    }
}
