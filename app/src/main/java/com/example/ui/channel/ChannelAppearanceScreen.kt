package com.example.ui.channel

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.AppViewModel
import com.example.ui.popBackStackSafe
import kotlinx.coroutines.launch

private enum class ChannelEmojiTarget {
    MESSAGE_PATTERN,
    PROFILE_PATTERN,
    STATUS_EMOJI
}

/**
 * ChannelAppearanceScreen ("Оформление")
 * Matches Telegram mobile channel customization:
 * - Light/Dark live preview toggle in TopBar
 * - Live Message + Reply + Link Preview card with single/2-tone/3-tone striped quote accents
 * - Message Background Emoji & 19 multi-tone color swatches
 * - Wallpapers carousel ("Из галереи" via PickVisualMedia, "Без обоев", themed presets) + Dim/Blur sliders
 * - Profile Header live preview with 16-swatch 2-row palette, Background Emoji & "Сбросить цвет профиля"
 * - Channel Emoji Status selector & immediate "Применить изменения" CTA
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelAppearanceScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ChannelCustomizationManager.init(context)
    }

    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId }
    val channelTitle = chat?.title ?: "KuoteX Official"

    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val initialCustomization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)
    val subscriberCount = maxOf(initialCustomization.subscriberCount, members.size).coerceAtLeast(1)

    var isPreviewDark by remember { mutableStateOf(true) }
    var selectedMessageColorId by remember(chatId) { mutableIntStateOf(initialCustomization.messageColorId) }
    var selectedMessageEmoji by remember(chatId) { mutableStateOf(initialCustomization.messageEmojiPattern) }
    var selectedProfileHeaderColorId by remember(chatId) { mutableStateOf(initialCustomization.profileHeaderColorId) }
    var selectedProfileEmoji by remember(chatId) { mutableStateOf(initialCustomization.profileEmojiPattern) }
    var selectedEmojiStatus by remember(chatId) { mutableStateOf(initialCustomization.emojiStatus) }
    var selectedWallpaperId by remember(chatId) { mutableStateOf(initialCustomization.chatWallpaperId) }
    var customWallpaperUri by remember(chatId) { mutableStateOf(initialCustomization.wallpaperCustomUri) }
    var wallpaperBlur by remember(chatId) { mutableFloatStateOf(initialCustomization.wallpaperBlur) }
    var wallpaperDim by remember(chatId) { mutableFloatStateOf(initialCustomization.wallpaperDim) }

    var activeEmojiPickerTarget by remember { mutableStateOf<ChannelEmojiTarget?>(null) }

    val galleryWallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = ChannelWallpaperStorage.saveWallpaperFromUri(context, uri, chatId)
                customWallpaperUri = savedPath
                selectedWallpaperId = "custom_gallery"
                Toast.makeText(context, "Фотография загружена в предпросмотр", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val activeMessageScheme = TelegramMessageColors.getScheme(selectedMessageColorId)
    val activeProfileScheme = TelegramProfileHeaderSchemes.getOrNull(selectedProfileHeaderColorId)

    val pageBg = Color(0xFF0E1117)
    val cardBg = Color(0xFF171B24)
    val cardBorder = Color.White.copy(alpha = 0.06f)
    val captionColor = Color.White.copy(alpha = 0.56f)

    activeEmojiPickerTarget?.let { target ->
        val (dialogTitle, initialEmoji) = when (target) {
            ChannelEmojiTarget.MESSAGE_PATTERN -> "Фоновый эмодзи сообщений" to selectedMessageEmoji
            ChannelEmojiTarget.PROFILE_PATTERN -> "Фоновый эмодзи профиля" to selectedProfileEmoji
            ChannelEmojiTarget.STATUS_EMOJI -> "Эмодзи-статус канала" to selectedEmojiStatus
        }
        TelegramChannelEmojiPickerDialog(
            title = dialogTitle,
            currentEmoji = initialEmoji,
            onSelectEmoji = { chosen ->
                when (target) {
                    ChannelEmojiTarget.MESSAGE_PATTERN -> selectedMessageEmoji = chosen
                    ChannelEmojiTarget.PROFILE_PATTERN -> selectedProfileEmoji = chosen
                    ChannelEmojiTarget.STATUS_EMOJI -> selectedEmojiStatus = chosen
                }
            },
            onDismiss = { activeEmojiPickerTarget = null }
        )
    }

    Scaffold(
        containerColor = pageBg,
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Оформление", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = { navController.popBackStackSafe() },
                        shape = CircleShape,
                        color = Color(0xFF1E242E),
                        modifier = Modifier.padding(start = 8.dp).size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                actions = {
                    Surface(
                        onClick = { isPreviewDark = !isPreviewDark },
                        shape = CircleShape,
                        color = Color(0xFF1E242E),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .testTag("appearance_theme_toggle")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPreviewDark) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode,
                                contentDescription = if (isPreviewDark) "Светлый предпросмотр" else "Тёмный предпросмотр",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pageBg)
            )
        },
        bottomBar = {
            Surface(
                color = pageBg.copy(alpha = 0.96f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Button(
                        onClick = {
                            ChannelCustomizationManager.updateChannelAppearance(
                                chatId = chatId,
                                messageColorId = selectedMessageColorId,
                                messageEmojiPattern = selectedMessageEmoji,
                                profileHeaderColorId = selectedProfileHeaderColorId,
                                profileEmojiPattern = selectedProfileEmoji,
                                emojiStatus = selectedEmojiStatus,
                                wallpaperId = selectedWallpaperId,
                                customWallpaperUri = customWallpaperUri,
                                wallpaperBlur = wallpaperBlur,
                                wallpaperDim = wallpaperDim
                            )
                            Toast.makeText(context, "Оформление канала применено ✨", Toast.LENGTH_SHORT).show()
                            navController.popBackStackSafe()
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C62E6)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("apply_channel_appearance_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Применить изменения",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.78f), modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Рекомендуется уровень 3+",
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)
        ) {
            // --- SECTION 1: LIVE MESSAGE PREVIEW + SWATCHES + BACKGROUND EMOJI ---
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MessageAppearancePreviewCard(
                            channelTitle = channelTitle,
                            avatarUrl = initialCustomization.avatarUrl,
                            messageScheme = activeMessageScheme,
                            messageEmojiPattern = selectedMessageEmoji,
                            emojiStatus = selectedEmojiStatus,
                            wallpaperId = selectedWallpaperId,
                            customWallpaperUri = customWallpaperUri,
                            wallpaperBlur = wallpaperBlur,
                            wallpaperDim = wallpaperDim,
                            isDarkPreview = isPreviewDark
                        )

                        Spacer(Modifier.height(10.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(TelegramMessageColors.schemes, key = { it.id }) { scheme ->
                                MultiToneColorSwatch(
                                    scheme = scheme,
                                    selected = selectedMessageColorId == scheme.id,
                                    isDarkSurface = true,
                                    onClick = { selectedMessageColorId = scheme.id }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        AppearanceSettingRow(
                            title = "Фоновый эмодзи",
                            boostLevel = 4,
                            valueText = selectedMessageEmoji ?: "Нет",
                            valueColor = Color(0xFF9F6BFF),
                            testTag = "row_message_bg_emoji",
                            onClick = { activeEmojiPickerTarget = ChannelEmojiTarget.MESSAGE_PATTERN }
                        )
                    }
                }

                Text(
                    text = "Вы можете выбрать цвет для названия канала, а также опубликованных каналом ссылок и цитат его публикаций в ответах.",
                    color = captionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }

            // --- SECTION 2: WALLPAPER CAROUSEL + GALLERY PICKER + BLUR/DIM SLIDERS ---
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item(key = "gallery_picker") {
                                GalleryWallpaperThumbnailCard(
                                    customUri = customWallpaperUri,
                                    isSelected = selectedWallpaperId == "custom_gallery" && !customWallpaperUri.isNullOrBlank(),
                                    isDarkPreview = isPreviewDark,
                                    onClick = {
                                        galleryWallpaperLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                            }
                            items(TelegramWallpapers.presets, key = { it.id }) { preset ->
                                PresetWallpaperThumbnailCard(
                                    preset = preset,
                                    isSelected = selectedWallpaperId == preset.id,
                                    isDarkPreview = isPreviewDark,
                                    onClick = { selectedWallpaperId = preset.id }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        val wpLabel = when (selectedWallpaperId) {
                            "custom_gallery" -> "Фото из галереи"
                            else -> TelegramWallpapers.getPreset(selectedWallpaperId).name
                        }
                        AppearanceSettingRow(
                            title = "Обои в канале",
                            boostLevel = 10,
                            valueText = wpLabel,
                            valueColor = Color(0xFF9F6BFF),
                            testTag = "row_channel_wallpaper",
                            onClick = {
                                galleryWallpaperLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )

                        AnimatedVisibility(visible = selectedWallpaperId != "none") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Затемнение", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp)
                                    Text("${(wallpaperDim * 100).toInt()}%", color = Color(0xFF9F6BFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Slider(
                                    value = wallpaperDim,
                                    onValueChange = { wallpaperDim = it },
                                    valueRange = 0f..0.8f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF9F6BFF),
                                        activeTrackColor = Color(0xFF8C62E6),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                                    ),
                                    modifier = Modifier.testTag("wallpaper_dim_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Размытие", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp)
                                    Text("${wallpaperBlur.toInt()} dp", color = Color(0xFF9F6BFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Slider(
                                    value = wallpaperBlur,
                                    onValueChange = { wallpaperBlur = it },
                                    valueRange = 0f..24f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF9F6BFF),
                                        activeTrackColor = Color(0xFF8C62E6),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.14f)
                                    ),
                                    modifier = Modifier.testTag("wallpaper_blur_slider")
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Вы можете установить в канале обои, которые будут видны всем, кто его читает.",
                    color = captionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }

            // --- SECTION 3: PROFILE HEADER PREVIEW + 16 SWATCHES + RESET ---
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileHeaderPreviewCard(
                            channelTitle = channelTitle,
                            avatarUrl = initialCustomization.avatarUrl,
                            subscriberCount = subscriberCount,
                            headerScheme = activeProfileScheme,
                            profileEmojiPattern = selectedProfileEmoji,
                            emojiStatus = selectedEmojiStatus,
                            isDarkPreview = isPreviewDark
                        )

                        Spacer(Modifier.height(12.dp))

                        // 2-row 8-column palette grid matching Telegram
                        val row1 = remember { TelegramProfileHeaderSchemes.schemes.take(8) }
                        val row2 = remember { TelegramProfileHeaderSchemes.schemes.drop(8).take(8) }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                        ) {
                            val swatchGap = 4.dp
                            val outerSize = ((maxWidth - swatchGap * 7) / 8f).coerceAtMost(42.dp)
                            val innerSize = (outerSize - 6.dp).coerceAtLeast(22.dp)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    row1.forEach { scheme ->
                                        ProfileHeaderColorSwatch(
                                            scheme = scheme,
                                            selected = selectedProfileHeaderColorId == scheme.id,
                                            size = innerSize,
                                            onClick = { selectedProfileHeaderColorId = scheme.id }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    row2.forEach { scheme ->
                                        ProfileHeaderColorSwatch(
                                            scheme = scheme,
                                            selected = selectedProfileHeaderColorId == scheme.id,
                                            size = innerSize,
                                            onClick = { selectedProfileHeaderColorId = scheme.id }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        AppearanceSettingRow(
                            title = "Фоновый эмодзи",
                            boostLevel = 7,
                            valueText = selectedProfileEmoji ?: "Нет",
                            valueColor = Color(0xFF9F6BFF),
                            testTag = "row_profile_bg_emoji",
                            onClick = { activeEmojiPickerTarget = ChannelEmojiTarget.PROFILE_PATTERN }
                        )

                        AnimatedVisibility(
                            visible = selectedProfileHeaderColorId != null || selectedProfileEmoji != null
                        ) {
                            Column {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedProfileHeaderColorId = null
                                            selectedProfileEmoji = null
                                        }
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                                    .testTag("reset_profile_color_button")
                                ) {
                                    Text(
                                        text = "Сбросить цвет профиля",
                                        color = Color(0xFF9F6BFF),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Вы можете выбрать цвет и фоновый эмодзи для профиля канала.",
                    color = captionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }

            // --- SECTION 4: CHANNEL EMOJI STATUS ---
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppearanceSettingRow(
                        title = "Эмодзи-статус канала",
                        boostLevel = 8,
                        valueText = selectedEmojiStatus ?: "Нет",
                        valueColor = Color(0xFF9F6BFF),
                        testTag = "row_channel_emoji_status",
                        onClick = { activeEmojiPickerTarget = ChannelEmojiTarget.STATUS_EMOJI }
                    )
                }

                Text(
                    text = "Вы можете выбрать статус, который будет отображаться рядом с названием канала.",
                    color = captionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingRow(
    title: String,
    boostLevel: Int,
    valueText: String,
    valueColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            BoostLevelPillBadge(level = boostLevel)
        }
        Text(
            text = valueText,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 120.dp)
        )
    }
}
