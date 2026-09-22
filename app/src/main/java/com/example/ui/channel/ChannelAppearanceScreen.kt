package com.example.ui.channel

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.AppViewModel

/**
 * ChannelAppearanceScreen
 * Telegram-style Channel/Group Appearance and Profile Customization:
 * - 7 Profile Colors & Quote Gradients
 * - Animated Custom Emoji in Channel Header
 * - Chat Wallpapers with Blur & Dimming
 * - Live Interactive Preview Card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelAppearanceScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val initialCustomization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    var selectedColorId by remember { mutableIntStateOf(initialCustomization.profileColorId) }
    var selectedEmoji by remember { mutableStateOf(initialCustomization.emojiStatus) }
    var selectedWallpaperId by remember { mutableStateOf(initialCustomization.chatWallpaperId) }
    var wallpaperBlur by remember { mutableFloatStateOf(initialCustomization.wallpaperBlur) }
    var wallpaperDim by remember { mutableFloatStateOf(initialCustomization.wallpaperDim) }

    val currentPalette = TelegramProfilePalettes.getPalette(selectedColorId)
    val currentWallpaper = TelegramWallpapers.getPreset(selectedWallpaperId)

    val infiniteTransition = rememberInfiniteTransition(label = "appearance_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оформление канала", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            ChannelCustomizationManager.updateProfileColor(chatId, selectedColorId, selectedEmoji)
                            ChannelCustomizationManager.updateEmojiStatus(chatId, selectedEmoji, isAnimated = true)
                            ChannelCustomizationManager.updateWallpaper(chatId, selectedWallpaperId, blur = wallpaperBlur, dim = wallpaperDim)
                            Toast.makeText(context, "Оформление канала сохранено! ✨", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    ) {
                        Text("Применить", fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F141C)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F141C), Color(0xFF141A24), Color(0xFF0C0F14))
                    )
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // --- LIVE PREVIEW CARD ---
                Text(
                    text = "Предпросмотр оформления",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(6.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF18202E),
                    border = BorderStroke(1.5.dp, currentPalette.brush),
                    shadowElevation = 8.dp
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Wallpaper preview background
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.linearGradient(currentWallpaper.previewGradient)
                                )
                                .background(Color.Black.copy(alpha = wallpaperDim))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Channel Header in Preview
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(currentPalette.brush),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Campaign,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "KuoteX Official",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = currentPalette.primaryColor
                                        )
                                        if (selectedEmoji != null) {
                                            Spacer(Modifier.width(6.dp))
                                            AnimatedEmojiStatusBadge(
                                                emoji = selectedEmoji,
                                                glowColor = currentPalette.primaryColor,
                                                size = 22.dp,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = "12 450 подписчиков",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Sample Channel Post Bubble with Quote Border
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E2838).copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, currentPalette.quoteBorder.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Quote Block
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = currentPalette.quoteBackground,
                                        border = BorderStroke(0.5.dp, currentPalette.quoteBorder.copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(30.dp)
                                                    .background(currentPalette.primaryColor, RoundedCornerShape(2.dp))
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "KuoteX",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = currentPalette.primaryColor
                                                )
                                                Text(
                                                    text = "Новые возможности каналов и групп доступны всем пользователям!",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 11.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = "⚡ Добавлены кастомные анимированные эмодзи в шапку, Telegram-бусты и темы!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "14:20 • KuoteX Admin",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 1: PROFILE & QUOTE COLORS ---
            item {
                Text(
                    text = "1. Цвет названия и цитат",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Выберите один из градиентов для акцента профиля, ссылок и репостов",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(TelegramProfilePalettes.palettes) { palette ->
                        val isSelected = selectedColorId == palette.id
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(palette.brush)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorId = palette.id },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: ANIMATED CUSTOM EMOJI IN HEADER ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "2. Анимированный эмодзи в шапку",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Отображается рядом с названием канала",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    if (selectedEmoji != null) {
                        TextButton(onClick = { selectedEmoji = null }) {
                            Text("Убрать", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ChannelEmojiCatalog.emojis) { emojiItem ->
                        val isSelected = selectedEmoji == emojiItem.emoji
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) currentPalette.quoteBackground else Color(0xFF161C26),
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) currentPalette.primaryColor else Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.clickable {
                                selectedEmoji = emojiItem.emoji
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = emojiItem.emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier.graphicsLayer {
                                        if (isSelected) {
                                            scaleX = pulseScale
                                            scaleY = pulseScale
                                        }
                                    }
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = emojiItem.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) currentPalette.primaryColor else Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION 3: WALLPAPER CUSTOMIZATION ---
            item {
                Text(
                    text = "3. Обои канала / группы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Фон диалога для всех подписчиков и гостей канала",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(TelegramWallpapers.presets) { wp ->
                        val isSelected = selectedWallpaperId == wp.id
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            border = BorderStroke(
                                2.dp,
                                if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier
                                .size(width = 80.dp, height = 110.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(wp.previewGradient))
                                .clickable { selectedWallpaperId = wp.id }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E5FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Text(
                                        text = wp.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Wallpaper Dimming Slider
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141923),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Затемнение обоев", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text("${(wallpaperDim * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF00E5FF))
                        }
                        Slider(
                            value = wallpaperDim,
                            onValueChange = { wallpaperDim = it },
                            valueRange = 0f..0.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )
                    }
                }
            }

            // Bottom Save Button
            item {
                Button(
                    onClick = {
                        ChannelCustomizationManager.updateProfileColor(chatId, selectedColorId, selectedEmoji)
                        ChannelCustomizationManager.updateEmojiStatus(chatId, selectedEmoji, isAnimated = true)
                        ChannelCustomizationManager.updateWallpaper(chatId, selectedWallpaperId, blur = wallpaperBlur, dim = wallpaperDim)
                        Toast.makeText(context, "Оформление успешно применено! ✨", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить и применить", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 15.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
