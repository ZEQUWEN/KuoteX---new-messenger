package com.example.ui.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun MessageAppearancePreviewCard(
    channelTitle: String,
    avatarUrl: String?,
    messageScheme: MessageColorScheme,
    messageEmojiPattern: String?,
    emojiStatus: String?,
    wallpaperId: String,
    customWallpaperUri: String?,
    wallpaperBlur: Float,
    wallpaperDim: Float,
    isDarkPreview: Boolean
) {
    val context = LocalContext.current
    val effectiveWallpaperId = if (wallpaperId == "none") "diamond_doodles" else wallpaperId
    val bubbleBg = if (isDarkPreview) Color(0xFF1D2430).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
    val quoteBg = if (isDarkPreview) messageScheme.quoteBackgroundDark else messageScheme.quoteBackgroundLight
    val primaryText = if (isDarkPreview) Color.White else Color(0xFF161B22)
    val secondaryText = if (isDarkPreview) Color.White.copy(alpha = 0.72f) else Color(0xFF4A5568)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("appearance_message_preview_card"),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ChannelWallpaperCanvas(
                wallpaperId = effectiveWallpaperId,
                customUri = customWallpaperUri,
                blurRadiusDp = wallpaperBlur,
                dimAlpha = if (wallpaperId == "none") 0.1f else wallpaperDim,
                isDark = isDarkPreview,
                modifier = Modifier.matchParentSize()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(messageScheme.accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                            contentDescription = "Аватар канала",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = channelTitle.take(2).uppercase().ifBlank { "KU" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                    color = bubbleBg,
                    shadowElevation = 4.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (!messageEmojiPattern.isNullOrBlank()) {
                            EmojiWatermarkScatterLayer(
                                emoji = messageEmojiPattern,
                                alpha = if (isDarkPreview) 0.2f else 0.16f,
                                densityScale = 5,
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(6.dp)
                            )
                        }

                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channelTitle,
                                    color = messageScheme.accentColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!emojiStatus.isNullOrBlank()) {
                                    Spacer(Modifier.width(5.dp))
                                    AnimatedEmojiStatusBadge(
                                        emoji = emojiStatus,
                                        glowColor = messageScheme.accentColor,
                                        size = 18.dp,
                                        fontSize = 11.sp,
                                        showBackgroundBadge = false
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Reply / Quote preview block with 1-/2-/3-tone vertical stripe
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = quoteBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(IntrinsicSize.Min)
                                        .padding(end = 8.dp)
                                ) {
                                    StripedQuoteAccentBar(stripeColors = messageScheme.stripeColors)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = channelTitle,
                                                color = messageScheme.accentColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                            if (!emojiStatus.isNullOrBlank()) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(text = emojiStatus, fontSize = 11.sp)
                                            }
                                        }
                                        Text(
                                            text = "Публикация в канале",
                                            color = secondaryText,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "Ссылки, цитаты и название канала в ответах окрашиваются в выбранный стиль.",
                                color = primaryText,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(8.dp))

                            // Link preview box tinted in messageScheme
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = quoteBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(IntrinsicSize.Min)
                                        .padding(end = 8.dp)
                                ) {
                                    StripedQuoteAccentBar(stripeColors = messageScheme.stripeColors)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            text = "KuoteX Channels",
                                            color = messageScheme.accentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Предпросмотр ссылки",
                                            color = primaryText,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Пример отображения ссылок в публикациях канала",
                                            color = secondaryText,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeaderPreviewCard(
    channelTitle: String,
    avatarUrl: String?,
    subscriberCount: Int,
    headerScheme: ProfileHeaderColorScheme?,
    profileEmojiPattern: String?,
    emojiStatus: String?,
    isDarkPreview: Boolean
) {
    val context = LocalContext.current
    val fallbackBg = if (isDarkPreview) {
        Brush.verticalGradient(listOf(Color(0xFF1F232C), Color(0xFF181B22)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF3F6FA), Color(0xFFE7ECF4)))
    }
    val cardBrush = headerScheme?.let { Brush.verticalGradient(it.headerGradient) } ?: fallbackBg
    val titleColor = if (headerScheme != null || isDarkPreview) Color.White else Color(0xFF161B22)
    val subColor = if (headerScheme != null || isDarkPreview) Color.White.copy(alpha = 0.72f) else Color(0xFF5A6577)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBrush)
            .padding(vertical = 22.dp, horizontal = 16.dp)
            .testTag("appearance_profile_preview_card"),
        contentAlignment = Alignment.Center
    ) {
        if (!profileEmojiPattern.isNullOrBlank()) {
            EmojiWatermarkScatterLayer(
                emoji = profileEmojiPattern,
                alpha = 0.22f,
                densityScale = 10,
                modifier = Modifier.matchParentSize()
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val ringBrush = headerScheme?.let { Brush.sweepGradient(it.ringColors + it.ringColors.first()) }
                ?: Brush.linearGradient(listOf(Color(0xFF4CD964), Color(0xFF3E95EA)))

            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, ringBrush, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(
                        headerScheme?.primaryColor ?: Color(0xFF4B9AE8)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(avatarUrl).crossfade(true).build(),
                        contentDescription = "Аватар канала",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = channelTitle.take(2).uppercase().ifBlank { "KU" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channelTitle,
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!emojiStatus.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    AnimatedEmojiStatusBadge(
                        emoji = emojiStatus,
                        glowColor = headerScheme?.primaryColor ?: Color(0xFF4B9AE8),
                        size = 22.dp,
                        fontSize = 14.sp,
                        showBackgroundBadge = false
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            val subLabel = when {
                subscriberCount % 10 == 1 && subscriberCount % 100 != 11 -> "$subscriberCount подписчик"
                subscriberCount % 10 in 2..4 && (subscriberCount % 100 < 10 || subscriberCount % 100 >= 20) -> "$subscriberCount подписчика"
                else -> "$subscriberCount подписчиков"
            }
            Text(
                text = subLabel,
                color = subColor,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun GalleryWallpaperThumbnailCard(
    customUri: String?,
    isSelected: Boolean,
    isDarkPreview: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val borderColor = if (isSelected) Color(0xFF9F6BFF) else Color.White.copy(alpha = 0.14f)
    val bgColor = if (isDarkPreview) Color(0xFF1B2029) else Color(0xFFE7EDF5)
    val textColor = if (isDarkPreview) Color.White else Color(0xFF1D222B)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .size(width = 84.dp, height = 124.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("wallpaper_gallery_picker_card")
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!customUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(customUri).crossfade(true).build(),
                    contentDescription = "Выбранные обои из галереи",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8C62E6).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected && !customUri.isNullOrBlank()) Icons.Filled.Check else Icons.Filled.AddPhotoAlternate,
                        contentDescription = "Выбрать фото из галереи",
                        tint = if (!customUri.isNullOrBlank()) Color.White else Color(0xFF9F6BFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (!customUri.isNullOrBlank()) "Сменить\nфото" else "Из\nгалереи",
                    color = if (!customUri.isNullOrBlank()) Color.White else textColor.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PresetWallpaperThumbnailCard(
    preset: WallpaperPreset,
    isSelected: Boolean,
    isDarkPreview: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF9F6BFF) else Color.White.copy(alpha = 0.12f)
    val bubbleTint = if (isDarkPreview) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .size(width = 84.dp, height = 124.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("wallpaper_preset_card_${preset.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ChannelWallpaperCanvas(
                wallpaperId = preset.id,
                customUri = null,
                blurRadiusDp = 0f,
                dimAlpha = 0f,
                isDark = isDarkPreview,
                modifier = Modifier.fillMaxSize()
            )

            if (preset.id == "none") {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Без обоев",
                        color = if (isDarkPreview) Color.White.copy(alpha = 0.72f) else Color(0xFF4A5568),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Без обоев",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                // Miniature chat bubbles + centerpiece icon matching Telegram screenshots
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.76f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bubbleTint)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.54f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bubbleTint)
                        )
                    }
                    if (preset.centerIcon.isNotBlank()) {
                        Text(text = preset.centerIcon, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BoostLevelPillBadge(level: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFF8C62E6)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Уровень $level",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
