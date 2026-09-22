package com.example.ui.gifts

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PinnedGiftsHeader - Profile header Composable that uses a LazyRow to display PinnedGift objects.
 * Encapsulated inside a Surface container with a rounded shape and a border to visually clip the gift textures.
 */
@Composable
fun PinnedGiftsHeader(
    gifts: List<PinnedGift>,
    modifier: Modifier = Modifier,
    onGiftClick: (PinnedGift) -> Unit = {},
    onAddGiftClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // Header title & count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Stars,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Закрепленные подарки",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFD54F).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${gifts.size}/6",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F)
                        )
                    }
                }

                Text(
                    text = "Каталог",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onAddGiftClick() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // LazyRow displaying PinnedGift objects
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(gifts, key = { it.id }) { gift ->
                    PinnedGiftCard(
                        gift = gift,
                        onClick = { onGiftClick(gift) }
                    )
                }

                // Add/Pin Gift Slot (if < 6 gifts)
                if (gifts.size < 6) {
                    item {
                        AddPinnedGiftSlot(onClick = onAddGiftClick)
                    }
                }
            }
        }
    }
}

/**
 * PinnedGiftsHeaderRow - Convenience wrapper delegating to PinnedGiftsHeader.
 */
@Composable
fun PinnedGiftsHeaderRow(
    gifts: List<PinnedGift>,
    onGiftClick: (PinnedGift) -> Unit,
    onAddGiftClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PinnedGiftsHeader(
        gifts = gifts,
        modifier = modifier,
        onGiftClick = onGiftClick,
        onAddGiftClick = onAddGiftClick
    )
}

/**
 * PinnedGiftCard - Rounded container that dynamically clips custom gradient textures,
 * animated pulse glowing border, upgrade stars, and 3D floating emoji/lottie asset.
 */
@Composable
fun PinnedGiftCard(
    gift: PinnedGift,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gift_shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    val baseBackdrop = gift.parsedBackdropColor
    val accentGlow = gift.parsedAccentColor

    Card(
        modifier = modifier
            .width(132.dp)
            .height(172.dp)
            .shadow(
                elevation = if (gift.upgradeLevel >= 3) 8.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = accentGlow
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = baseBackdrop),
        border = androidx.compose.foundation.BorderStroke(
            width = if (gift.upgradeLevel >= 3) 2.dp else 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentGlow.copy(alpha = shimmerAlpha),
                    accentGlow.copy(alpha = 0.3f),
                    Color(0xFF0F172A)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentGlow.copy(alpha = 0.25f),
                            baseBackdrop,
                            Color(0xFF0F172A)
                        )
                    )
                )
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Rarity Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(accentGlow.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = gift.rarityTier.title,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (gift.isExclusive) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(0xFFFFD54F), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "👑", fontSize = 9.sp)
                        }
                    }
                }

                // Center Icon with floating bounce animation
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .graphicsLayer {
                            scaleX = if (gift.upgradeLevel >= 4) bounceScale else 1f
                            scaleY = if (gift.upgradeLevel >= 4) bounceScale else 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Soft glowing ambient circle behind emoji
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(accentGlow.copy(alpha = 0.5f), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )
                    Text(
                        text = gift.emojiIcon,
                        fontSize = 38.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom Titles and Level
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = gift.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    // Upgrade Stars
                    Text(
                        text = gift.upgradeStars,
                        fontSize = 11.sp,
                        color = Color(0xFFFFD700),
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Level Indicator Pill
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LVL ${gift.upgradeLevel}/${gift.maxUpgradeLevel}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentGlow
                        )
                    }
                }
            }
        }
    }
}

/**
 * AddPinnedGiftSlot - Empty slot in carousel to add or pin a new gift.
 */
@Composable
fun AddPinnedGiftSlot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(110.dp)
            .height(172.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = Color.Gray.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF334155), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Добавить",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Закрепить\nподарок",
                fontSize = 11.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * PinnedGiftDetailBottomSheet - Modal dialog displaying full gift details,
 * message, upgrade perks, and level up button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedGiftDetailBottomSheet(
    gift: PinnedGift?,
    onDismiss: () -> Unit,
    onUpgradeClick: (PinnedGift) -> Unit
) {
    if (gift == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val formattedDate = remember(gift.acquiredAt) {
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("ru"))
        sdf.format(Date(gift.acquiredAt))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF131826),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    coroutineScope.launch {
                        try {
                            sheetState.hide()
                        } catch (_: Exception) {}
                        onDismiss()
                    }
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.LightGray)
                }
            }

            // Big Animated Gift Icon
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                gift.parsedAccentColor.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = gift.emojiIcon, fontSize = 68.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = gift.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = gift.upgradeStars,
                    color = Color(0xFFFFD700),
                    fontSize = 18.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(gift.parsedAccentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Уровень ${gift.upgradeLevel} из ${gift.maxUpgradeLevel}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gift Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2638)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow(label = "Отправитель", value = gift.senderName)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(label = "Дата получения", value = formattedDate)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(label = "Редкость", value = gift.rarityTier.title)
                    if (gift.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF263248), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Сообщение к подарку:",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "«${gift.message}»",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Upgrade Button
            if (gift.upgradeLevel < gift.maxUpgradeLevel) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                sheetState.hide()
                            } catch (_: Exception) {}
                            onUpgradeClick(gift)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Повысить уровень подарка (LVL ${gift.upgradeLevel + 1})",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            } else {
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👑 Максимальный уровень подарка достигнут!",
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/**
 * Pinned collectible gift icon and serial badge displayed directly next to user nickname/display name.
 */
@Composable
fun PinnedCollectibleBadge(
    gift: CollectibleGift,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = gift.parsedBackdropColor.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, gift.parsedAccentColor.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = gift.emojiIcon, fontSize = 14.sp)
            Text(
                text = gift.formattedSerialNumber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = gift.parsedAccentColor
            )
        }
    }
}

/**
 * BottomSheet allowing user to inspect, pin or unpin a collectible gift next to their nickname.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectibleGiftDetailBottomSheet(
    gift: CollectibleGift,
    isOwner: Boolean,
    isPinned: Boolean,
    onPinToggle: (CollectibleGift) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14121E),
        scrimColor = Color.Black.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(gift.parsedBackdropColor)
                    .border(2.dp, gift.parsedAccentColor, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = gift.emojiIcon, fontSize = 54.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = gift.baseTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Surface(
                    color = gift.parsedAccentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = gift.formattedSerialNumber,
                        color = gift.parsedAccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = gift.category,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1F1D2B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow(label = "Модель", value = gift.modelName)
                    DetailRow(label = "Узор", value = gift.patternName)
                    DetailRow(label = "Владелец", value = if (isOwner) "Вы" else "@${gift.ownerName}")
                    DetailRow(label = "Стоимость", value = "${gift.priceStars} ⭐")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isOwner) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                sheetState.hide()
                            } catch (_: Exception) {}
                            onPinToggle(gift)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPinned) Color(0xFFEF4444) else Color(0xFF8B5CF6)
                    )
                ) {
                    Icon(
                        if (isPinned) Icons.Filled.Close else Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPinned) "Открепить от никнейма" else "Закрепить у никнейма",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

