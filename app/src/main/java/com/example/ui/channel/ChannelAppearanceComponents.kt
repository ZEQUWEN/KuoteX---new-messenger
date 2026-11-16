package com.example.ui.channel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Telegram-style circular color swatch supporting solid, 2-tone diagonal split,
 * and 3-tone rotated center diamond variants.
 */
@Composable
fun MultiToneColorSwatch(
    scheme: MessageColorScheme,
    selected: Boolean,
    isDarkSurface: Boolean,
    size: Dp = 40.dp,
    onClick: () -> Unit
) {
    val ringColor = if (selected) Color(0xFF9F6BFF) else Color.Transparent
    val innerBorder = if (isDarkSurface) Color(0xFF1A1D24) else Color.White

    Box(
        modifier = Modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .border(width = if (selected) 2.5.dp else 0.dp, color = ringColor, shape = CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag("message_color_swatch_${scheme.id}"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(1.dp, innerBorder.copy(alpha = 0.25f), CircleShape)
        ) {
            val w = this.size.width
            val h = this.size.height
            when {
                scheme.isThreeTone && scheme.stripeColors.size >= 3 -> {
                    val topColor = scheme.stripeColors[0]
                    val bottomColor = scheme.stripeColors[1]
                    val centerColor = scheme.stripeColors[2]
                    val topPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w, 0f)
                        lineTo(0f, h)
                        close()
                    }
                    val bottomPath = Path().apply {
                        moveTo(w, 0f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(topPath, color = topColor)
                    drawPath(bottomPath, color = bottomColor)
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = w * 0.24f
                    val diamond = Path().apply {
                        moveTo(cx, cy - r)
                        lineTo(cx + r, cy)
                        lineTo(cx, cy + r)
                        lineTo(cx - r, cy)
                        close()
                    }
                    drawPath(diamond, color = centerColor)
                }
                scheme.stripeColors.size >= 2 -> {
                    val topPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w, 0f)
                        lineTo(0f, h)
                        close()
                    }
                    val bottomPath = Path().apply {
                        moveTo(w, 0f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(topPath, color = scheme.stripeColors[0])
                    drawPath(bottomPath, color = scheme.stripeColors[1])
                }
                else -> {
                    drawRect(color = scheme.accentColor)
                }
            }
        }
    }
}

/**
 * Swatch for the 16-color Profile Header 2-row selector.
 */
@Composable
fun ProfileHeaderColorSwatch(
    scheme: ProfileHeaderColorScheme,
    selected: Boolean,
    size: Dp = 36.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size + 6.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 2.5.dp else 0.dp,
                color = if (selected) Color(0xFF9F6BFF) else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
            .testTag("profile_color_swatch_${scheme.id}"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        ) {
            val w = this.size.width
            val h = this.size.height
            if (scheme.isTwoTone) {
                val topPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(0f, h)
                    close()
                }
                val bottomPath = Path().apply {
                    moveTo(w, 0f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(topPath, color = scheme.primaryColor)
                drawPath(bottomPath, color = scheme.secondaryColor)
            } else {
                drawRect(color = scheme.primaryColor)
            }
        }
    }
}

/**
 * Vertical quote accent bar supporting 1-tone solid or 2-/3-tone diagonal Telegram stripes.
 */
@Composable
fun StripedQuoteAccentBar(
    stripeColors: List<Color>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .width(3.5.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(2.dp))
    ) {
        if (stripeColors.size <= 1) {
            drawRect(color = stripeColors.firstOrNull() ?: Color(0xFF3E95EA))
        } else {
            val stripeH = 8.dp.toPx()
            val totalH = size.height
            var y = -stripeH
            var idx = 0
            while (y < totalH + stripeH) {
                val color = stripeColors[idx % stripeColors.size]
                val path = Path().apply {
                    moveTo(0f, y)
                    lineTo(size.width, y - size.width * 0.8f)
                    lineTo(size.width, y - size.width * 0.8f + stripeH)
                    lineTo(0f, y + stripeH)
                    close()
                }
                drawPath(path, color = color)
                y += stripeH
                idx++
            }
        }
    }
}

/**
 * Deterministic scattered pattern of a chosen background emoji (used in Profile Card & Quote Watermark).
 */
@Composable
fun EmojiWatermarkScatterLayer(
    emoji: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 0.2f,
    densityScale: Int = 8
) {
    if (emoji.isNullOrBlank()) return

    val placements = remember(densityScale) {
        listOf(
            Triple(0.08f, 0.14f, -14f),
            Triple(0.24f, 0.34f, 12f),
            Triple(0.16f, 0.72f, -8f),
            Triple(0.76f, 0.16f, 16f),
            Triple(0.88f, 0.38f, -18f),
            Triple(0.74f, 0.74f, 10f),
            Triple(0.38f, 0.12f, 8f),
            Triple(0.62f, 0.14f, -10f),
            Triple(0.34f, 0.82f, -12f),
            Triple(0.65f, 0.86f, 14f)
        ).take(densityScale.coerceIn(4, 10))
    }

    BoxWithConstraints(modifier = modifier.alpha(alpha)) {
        val w = maxWidth
        val h = maxHeight
        placements.forEachIndexed { index, (px, py, rot) ->
            val fontSp = if (index % 2 == 0) 15.sp else 12.sp
            Text(
                text = emoji,
                fontSize = fontSp,
                modifier = Modifier
                    .offset(x = w * px, y = h * py)
                    .graphicsLayer { rotationZ = rot }
            )
        }
    }
}

/**
 * Renders the channel chat background for both the live preview card and ChatScreen:
 * - "none": clean solid/theme canvas
 * - "custom_gallery": saved photo from gallery with optional blur & dimming scrim
 * - Preset wallpaper: gradient + Telegram doodle / star / grid canvas pattern + dimming
 */
@Composable
fun ChannelWallpaperCanvas(
    wallpaperId: String,
    customUri: String?,
    blurRadiusDp: Float,
    dimAlpha: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preset = remember(wallpaperId) { TelegramWallpapers.getPreset(wallpaperId) }

    Box(modifier = modifier) {
        if (wallpaperId == "custom_gallery" && !customUri.isNullOrBlank()) {
            val blurModifier = if (blurRadiusDp > 0.5f) {
                Modifier.blur(blurRadiusDp.dp)
            } else {
                Modifier
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(customUri)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = "Обои канала из галереи",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )
            if (dimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 0.85f)))
                )
            }
        } else {
            val gradient = if (isDark) preset.previewGradient else preset.lightGradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(gradient))
            )

            if (preset.patternType != "none") {
                val patternTint = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF2D5937).copy(alpha = 0.13f)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurRadiusDp > 0.5f) Modifier.blur((blurRadiusDp * 0.5f).dp) else Modifier)
                ) {
                    val stepX = 56.dp.toPx()
                    val stepY = 56.dp.toPx()
                    var row = 0
                    var y = stepY * 0.4f
                    while (y < size.height) {
                        var col = 0
                        var x = stepX * 0.4f + (if (row % 2 == 1) stepX * 0.45f else 0f)
                        while (x < size.width) {
                            when (preset.patternType) {
                                "cyber_grid", "matrix" -> {
                                    drawRect(
                                        color = patternTint,
                                        topLeft = Offset(x - 10f, y - 10f),
                                        size = Size(20f, 20f),
                                        style = Stroke(width = 1.6f)
                                    )
                                }
                                "hearts" -> {
                                    drawCircle(color = patternTint, radius = 9f, center = Offset(x - 5f, y), style = Stroke(width = 1.6f))
                                    drawCircle(color = patternTint, radius = 9f, center = Offset(x + 5f, y), style = Stroke(width = 1.6f))
                                }
                                "stars", "aurora" -> {
                                    val r = 11f
                                    for (i in 0 until 4) {
                                        val angle = i * (PI / 4.0)
                                        drawLine(
                                            color = patternTint,
                                            start = Offset(x - (cos(angle) * r).toFloat(), y - (sin(angle) * r).toFloat()),
                                            end = Offset(x + (cos(angle) * r).toFloat(), y + (sin(angle) * r).toFloat()),
                                            strokeWidth = 1.6f
                                        )
                                    }
                                }
                                else -> {
                                    when ((row + col) % 3) {
                                        0 -> drawCircle(color = patternTint, radius = 12f, center = Offset(x, y), style = Stroke(width = 1.6f))
                                        1 -> drawRect(color = patternTint, topLeft = Offset(x - 9f, y - 9f), size = Size(18f, 18f), style = Stroke(width = 1.6f))
                                        else -> drawLine(
                                            color = patternTint,
                                            start = Offset(x - 12f, y + 6f),
                                            end = Offset(x + 12f, y - 6f),
                                            strokeWidth = 1.8f
                                        )
                                    }
                                }
                            }
                            x += stepX
                            col++
                        }
                        y += stepY
                        row++
                    }
                }
            }

            if (isDark && preset.patternType != "none" && dimAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (dimAlpha * 0.45f).coerceIn(0f, 0.5f)))
                )
            }
        }
    }
}

/**
 * Reusable ChatScreen background layer honoring "none", preset wallpapers, and custom gallery images.
 */
@Composable
fun ChannelBackgroundLayer(
    customization: ChannelCustomization,
    modifier: Modifier = Modifier
) {
    if (customization.chatWallpaperId == "none" && customization.wallpaperCustomUri.isNullOrBlank()) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.background)
        )
    } else {
        ChannelWallpaperCanvas(
            wallpaperId = customization.chatWallpaperId,
            customUri = customization.wallpaperCustomUri,
            blurRadiusDp = customization.wallpaperBlur,
            dimAlpha = customization.wallpaperDim,
            isDark = true,
            modifier = modifier
        )
    }
}
