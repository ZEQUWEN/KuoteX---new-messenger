package com.example.ui.media

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.EmojiCategory
import com.example.ui.components.EmojiItem
import com.example.ui.components.TelegramEmojiData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TelegramMediaPanel(
    initialTab: MediaPanelTab = MediaPanelTab.EMOJI,
    onEmojiSelected: (EmojiItem) -> Unit,
    onBackspaceClick: () -> Unit,
    onGifSelected: (GifItem) -> Unit,
    onStickerSelected: (StickerPack, StickerItem) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        MediaExpressionManager.init(context)
    }

    var activeTab by remember { mutableStateOf(initialTab) }
    var panelHeightDp by remember { mutableStateOf(336.dp) }
    val animatedHeight by animateDpAsState(targetValue = panelHeightDp, label = "panel_height")

    val stickerPacks by MediaExpressionManager.stickerPacks.collectAsStateWithLifecycle()
    val recentEmojis by MediaExpressionManager.recentEmojis.collectAsStateWithLifecycle()
    val trendingDismissed by MediaExpressionManager.trendingBannerDismissed.collectAsStateWithLifecycle()

    var showTrendingSheet by remember { mutableStateOf(false) }
    var showCreateStickerDialog by remember { mutableStateOf(false) }
    var previewSticker by remember { mutableStateOf<Pair<StickerPack, StickerItem>?>(null) }

    if (showTrendingSheet) {
        TrendingStickersModalSheet(
            onDismiss = { showTrendingSheet = false }
        )
    }

    if (showCreateStickerDialog) {
        CreateCustomStickerDialog(
            onDismiss = { showCreateStickerDialog = false },
            onCreate = { emoji, label, caption ->
                val created = MediaExpressionManager.createCustomSticker(emoji, label, caption)
                val jokerPack = stickerPacks.find { it.id == "joker" } ?: stickerPacks.first()
                onStickerSelected(jokerPack, created)
                showCreateStickerDialog = false
            }
        )
    }

    previewSticker?.let { (pack, sticker) ->
        StickerPreviewDialog(
            pack = pack,
            sticker = sticker,
            onDismiss = { previewSticker = null },
            onSend = {
                MediaExpressionManager.recordUsedSticker(pack.id, sticker)
                onStickerSelected(pack, sticker)
                previewSticker = null
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .navigationBarsPadding()
            .testTag("telegram_media_panel"),
        color = Color(0xFF12161E),
        tonalElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Draggable top resize handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                val deltaDp = (-dragAmount / density).dp
                                panelHeightDp = (panelHeightDp + deltaDp).coerceIn(270.dp, 500.dp)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                }

                // Active Tab Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (activeTab) {
                        MediaPanelTab.EMOJI -> {
                            InlineEmojiTabContent(
                                recentEmojis = recentEmojis,
                                onEmojiClick = { item ->
                                    MediaExpressionManager.recordUsedEmoji(item)
                                    onEmojiSelected(item)
                                },
                                onOpenSettings = onOpenSettings
                            )
                        }

                        MediaPanelTab.GIF -> {
                            InlineGifTabContent(
                                onGifClick = { gif ->
                                    GifSearchRepository.recordUsedGif(gif)
                                    onGifSelected(gif)
                                }
                            )
                        }

                        MediaPanelTab.STICKERS -> {
                            InlineStickersTabContent(
                                allPacks = stickerPacks,
                                trendingDismissed = trendingDismissed,
                                onDismissTrending = { MediaExpressionManager.dismissTrendingBanner(true) },
                                onOpenTrending = { showTrendingSheet = true },
                                onOpenCreateSticker = { showCreateStickerDialog = true },
                                onStickerClick = { pack, sticker ->
                                    MediaExpressionManager.recordUsedSticker(pack.id, sticker)
                                    onStickerSelected(pack, sticker)
                                },
                                onStickerLongPress = { pack, sticker ->
                                    previewSticker = pack to sticker
                                },
                                onOpenSettings = onOpenSettings
                            )
                        }
                    }
                }
            }

            // Floating bottom Telegram switcher: Эмодзи — GIF — Стикеры + Backspace/Settings
            MediaPanelBottomSwitcher(
                activeTab = activeTab,
                onSelectTab = { activeTab = it },
                onBackspaceClick = onBackspaceClick,
                onOpenSettings = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun MediaPanelBottomSwitcher(
    activeTab: MediaPanelTab,
    onSelectTab: (MediaPanelTab) -> Unit,
    onBackspaceClick: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Center floating capsule
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .height(42.dp),
            shape = CircleShape,
            color = Color(0xFF1F2633).copy(alpha = 0.95f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MediaPanelTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    Surface(
                        onClick = { onSelectTab(tab) },
                        shape = CircleShape,
                        color = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("media_tab_${tab.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Right contextual floating circular action
        if (activeTab == MediaPanelTab.EMOJI) {
            Surface(
                onClick = onBackspaceClick,
                shape = CircleShape,
                color = Color(0xFF1F2633).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(42.dp)
                    .testTag("emoji_panel_backspace")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = "Удалить символ",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else if (activeTab == MediaPanelTab.STICKERS) {
            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = Color(0xFF1F2633).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(42.dp)
                    .testTag("stickers_panel_settings")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Настройки стикеров и эмодзи",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelSearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        shape = RoundedCornerShape(19.dp),
        color = Color(0xFF1E2430)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Очистить поиск",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineEmojiTabContent(
    recentEmojis: List<EmojiItem>,
    onEmojiClick: (EmojiItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val categories = TelegramEmojiData.categories
    val emojiPacks by MediaExpressionManager.emojiPacks.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabIdx by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val searchResults by remember(searchQuery, recentEmojis, emojiPacks) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            if (q.isBlank()) {
                emptyList()
            } else {
                val categoryMatches = categories.flatMap { cat ->
                    cat.emojis.filter { item ->
                        item.emoji.contains(q) ||
                            item.name.lowercase().contains(q) ||
                            item.keywords.any { it.lowercase().contains(q) } ||
                            cat.title.lowercase().contains(q)
                    }
                }
                val customMatches = emojiPacks.filter {
                    it.title.lowercase().contains(q)
                }.flatMap { pack ->
                    pack.previewEmojis.map { em ->
                        EmojiItem(em, pack.title, listOf(pack.title.lowercase()), isCustom = true, packName = pack.title)
                    }
                }
                (categoryMatches + customMatches).distinctBy { it.emoji }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth < 340.dp -> 6
            maxWidth < 410.dp -> 7
            else -> 8
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top horizontal icon strip (Recent + Unicode categories + Custom Packs + Gear)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        EmojiStripIcon(
                            icon = "🕒",
                            selected = selectedTabIdx == 0,
                            onClick = {
                                selectedTabIdx = 0
                                searchQuery = ""
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        )
                    }
                    itemsIndexed(categories) { idx, cat ->
                        val tabIdx = idx + 1
                        EmojiStripIcon(
                            icon = cat.icon,
                            selected = selectedTabIdx == tabIdx,
                            onClick = {
                                selectedTabIdx = tabIdx
                                searchQuery = ""
                                val targetRow = calculateCategoryScrollIndex(
                                    recentCount = recentEmojis.size,
                                    categories = categories,
                                    targetCategoryIdx = idx,
                                    columns = columns
                                )
                                scope.launch { listState.animateScrollToItem(targetRow) }
                            }
                        )
                    }
                    items(emojiPacks, key = { it.id }) { pack ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onOpenSettings() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(pack.previewEmojis.firstOrNull() ?: "✨", fontSize = 16.sp)
                            if (pack.isLocked && !pack.isAdded) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF9C68FC),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(11.dp)
                                        .padding(1.dp)
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Настройки эмодзи",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Search bar
            PanelSearchPill(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Поиск эмодзи и статусов",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )

            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ничего не найдено по запросу «$searchQuery»",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val rows = searchResults.chunked(columns)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 68.dp)
                    ) {
                        item {
                            PanelSectionTitle("Результаты поиска")
                        }
                        items(rows) { row ->
                            InlineEmojiRow(
                                items = row,
                                columns = columns,
                                onEmojiClick = onEmojiClick
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 68.dp)
                ) {
                    if (recentEmojis.isNotEmpty()) {
                        item(key = "emoji_recent_header") {
                            PanelSectionTitle("Недавние")
                        }
                        val recentRows = recentEmojis.chunked(columns)
                        itemsIndexed(recentRows, key = { idx, _ -> "recent_row_$idx" }) { _, row ->
                            InlineEmojiRow(
                                items = row,
                                columns = columns,
                                onEmojiClick = onEmojiClick
                            )
                        }
                    }

                    categories.forEach { cat ->
                        item(key = "cat_hdr_${cat.id}") {
                            PanelSectionTitle(cat.title)
                        }
                        val rows = cat.emojis.chunked(columns)
                        itemsIndexed(rows, key = { idx, _ -> "${cat.id}_row_$idx" }) { _, row ->
                            InlineEmojiRow(
                                items = row,
                                columns = columns,
                                onEmojiClick = onEmojiClick
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun calculateCategoryScrollIndex(
    recentCount: Int,
    categories: List<EmojiCategory>,
    targetCategoryIdx: Int,
    columns: Int
): Int {
    var index = 0
    if (recentCount > 0) {
        index += 1 + ((recentCount + columns - 1) / columns)
    }
    for (i in 0 until targetCategoryIdx) {
        index += 1 + ((categories[i].emojis.size + columns - 1) / columns)
    }
    return index
}

@Composable
private fun EmojiStripIcon(
    icon: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF8B5CF6).copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, fontSize = 18.sp)
    }
}

@Composable
private fun PanelSectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

@Composable
private fun InlineEmojiRow(
    items: List<EmojiItem>,
    columns: Int,
    onEmojiClick: (EmojiItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { item ->
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 1.28f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "inline_emoji_scale"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onEmojiClick(item) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
            }
        }
        repeat((columns - items.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun InlineGifTabContent(
    onGifClick: (GifItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<String?>(null) }
    var gifResults by remember {
        mutableStateOf(GifSearchRepository.curatedCatalog)
    }
    var isLoading by remember { mutableStateOf(false) }
    val recentGifs by GifSearchRepository.recentGifs.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery, selectedMood) {
        if (searchQuery.isBlank() && selectedMood == null) {
            gifResults = GifSearchRepository.curatedCatalog
            isLoading = false
        } else {
            isLoading = true
            delay(180L)
            gifResults = GifSearchRepository.searchGifs(searchQuery, selectedMood)
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        PanelSearchPill(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Поиск GIF",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )

        // Mood emoji quick-filter ribbon
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(MediaExpressionManager.quickMoodFilters) { (emoji, _) ->
                val isSelected = selectedMood == emoji
                Surface(
                    onClick = { selectedMood = if (isSelected) null else emoji },
                    shape = CircleShape,
                    color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.35f) else Color(0xFF1E2430),
                    border = if (isSelected) BorderStroke(1.dp, Color(0xFF8B5CF6)) else null,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emoji, fontSize = 18.sp)
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFF8B5CF6),
                trackColor = Color.Transparent
            )
        }

        if (gifResults.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GIF не найдены",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 68.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (searchQuery.isBlank() && selectedMood == null && recentGifs.isNotEmpty()) {
                    item {
                        PanelSectionTitle("Недавние GIF")
                    }
                    val recentPairs = recentGifs.chunked(2)
                    items(recentPairs) { pair ->
                        GifPairRow(pair = pair, onGifClick = onGifClick)
                    }
                    item {
                        PanelSectionTitle("Популярные GIF")
                    }
                }
                val rows = gifResults.chunked(2)
                items(rows) { pair ->
                    GifPairRow(pair = pair, onGifClick = onGifClick)
                }
            }
        }
    }
}

@Composable
private fun GifPairRow(
    pair: List<GifItem>,
    onGifClick: (GifItem) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        pair.forEach { gif ->
            GifGridTile(
                gif = gif,
                onClick = { onGifClick(gif) },
                modifier = Modifier
                    .weight(1f)
                    .height(106.dp)
            )
        }
        if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun GifGridTile(
    gif: GifItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseColor = Color(gif.accentColorHex)
    val pulse by rememberInfiniteTransition(label = "gif_bg").animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gif_pulse"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 0.42f * pulse),
                        Color(0xFF1A2232)
                    )
                )
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "GIF: ${gif.title}" }
    ) {
        // Stylized animated fallback underneath network GIF so tile always renders crisply offline
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = gif.previewEmoji, fontSize = 30.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = gif.title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        if (gif.gifUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(gif.gifUrl)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = gif.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        ) {
            Text(
                text = "GIF",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InlineStickersTabContent(
    allPacks: List<StickerPack>,
    trendingDismissed: Boolean,
    onDismissTrending: () -> Unit,
    onOpenTrending: () -> Unit,
    onOpenCreateSticker: () -> Unit,
    onStickerClick: (StickerPack, StickerItem) -> Unit,
    onStickerLongPress: (StickerPack, StickerItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val activePacks = remember(allPacks) {
        allPacks.filter { it.isInstalled && !it.isArchived }.sortedBy { it.orderIndex }
    }
    val trendingPacks = remember(allPacks) {
        allPacks.filter { it.isTrending && (!it.isInstalled || it.isArchived) }
    }
    val recentStickersMap by MediaExpressionManager.recentStickerIds.collectAsStateWithLifecycle()
    val recentStickers = remember(recentStickersMap, allPacks) {
        MediaExpressionManager.getRecentStickers()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf<String?>(null) }
    var selectedPackId by remember { mutableStateOf(activePacks.firstOrNull()?.id ?: "") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val searchResults by remember(searchQuery, selectedMood, allPacks) {
        derivedStateOf {
            if (searchQuery.isBlank() && selectedMood == null) {
                emptyList()
            } else {
                MediaExpressionManager.searchStickers(searchQuery, selectedMood)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = if (maxWidth < 360.dp) 4 else 5

        Column(modifier = Modifier.fillMaxSize()) {
            // Top pack icon strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recentStickers.isNotEmpty()) {
                    item {
                        EmojiStripIcon(
                            icon = "🕒",
                            selected = selectedPackId == "recent",
                            onClick = {
                                selectedPackId = "recent"
                                searchQuery = ""
                                selectedMood = null
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        )
                    }
                }
                itemsIndexed(activePacks, key = { _, p -> p.id }) { idx, pack ->
                    EmojiStripIcon(
                        icon = pack.coverEmoji,
                        selected = selectedPackId == pack.id,
                        onClick = {
                            selectedPackId = pack.id
                            searchQuery = ""
                            selectedMood = null
                            val scrollIdx = computePackScrollIndex(
                                hasRecent = recentStickers.isNotEmpty(),
                                recentRows = if (recentStickers.isEmpty()) 0 else 1,
                                hasTrendingShelf = !trendingDismissed && trendingPacks.isNotEmpty(),
                                activePacks = activePacks,
                                targetPackIndex = idx,
                                columns = columns
                            )
                            scope.launch { listState.animateScrollToItem(scrollIdx) }
                        }
                    )
                }
                item {
                    Surface(
                        onClick = onOpenTrending,
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Популярные стикеры",
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Search bar
            PanelSearchPill(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Поиск стикеров",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )

            // Mood emoji chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(MediaExpressionManager.quickMoodFilters) { (emoji, _) ->
                    val selected = selectedMood == emoji
                    Surface(
                        onClick = { selectedMood = if (selected) null else emoji },
                        shape = CircleShape,
                        color = if (selected) Color(0xFF8B5CF6).copy(alpha = 0.35f) else Color(0xFF1E2430),
                        border = if (selected) BorderStroke(1.dp, Color(0xFF8B5CF6)) else null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 17.sp)
                        }
                    }
                }
            }

            if (searchQuery.isNotBlank() || selectedMood != null) {
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Стикеры не найдены",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val rows = searchResults.chunked(columns)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 68.dp)
                    ) {
                        item {
                            PanelSectionTitle("Найденные стикеры (${searchResults.size})")
                        }
                        items(rows) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { (pack, sticker) ->
                                    StickerTileCell(
                                        sticker = sticker,
                                        onClick = { onStickerClick(pack, sticker) },
                                        onLongClick = { onStickerLongPress(pack, sticker) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                    )
                                }
                                repeat((columns - row.size).coerceAtLeast(0)) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 68.dp)
                ) {
                    if (recentStickers.isNotEmpty()) {
                        item(key = "stickers_recent_hdr") {
                            PanelSectionTitle("Недавние стикеры")
                        }
                        item(key = "stickers_recent_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                items(recentStickers, key = { it.second.id }) { (pack, sticker) ->
                                    StickerTileCell(
                                        sticker = sticker,
                                        onClick = { onStickerClick(pack, sticker) },
                                        onLongClick = { onStickerLongPress(pack, sticker) },
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Dismissible "Популярные стикеры" shelf
                    if (!trendingDismissed && trendingPacks.isNotEmpty()) {
                        item(key = "trending_shelf") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1A202C),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable(onClick = onOpenTrending)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Whatshot,
                                                contentDescription = null,
                                                tint = Color(0xFF8B5CF6),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Популярные стикеры",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        IconButton(
                                            onClick = onDismissTrending,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Скрыть",
                                                tint = Color.White.copy(alpha = 0.55f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        trendingPacks.take(3).forEach { pack ->
                                            val firstSticker = pack.stickers.firstOrNull()
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.White.copy(alpha = 0.05f))
                                                    .clickable(onClick = onOpenTrending)
                                                    .padding(8.dp)
                                            ) {
                                                Text(pack.coverEmoji, fontSize = 28.sp)
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = pack.title,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "+ Добавить",
                                                    color = Color(0xFFA78BFA),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (firstSticker != null && pack.isLocked) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Lock,
                                                        contentDescription = null,
                                                        tint = Color(0xFFA78BFA),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Installed Sticker Packs
                    activePacks.forEach { pack ->
                        item(key = "pack_hdr_${pack.id}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pack.title,
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${pack.stickers.size} стик.",
                                    color = Color.White.copy(alpha = 0.42f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Include "+ Создать" tile on first pack (like Joker screenshot)
                        val entries: List<StickerItem?> = if (pack.id == "joker") {
                            listOf(null) + pack.stickers
                        } else {
                            pack.stickers
                        }
                        val rows = entries.chunked(columns)
                        itemsIndexed(rows, key = { idx, _ -> "${pack.id}_st_$idx" }) { _, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { entry ->
                                    if (entry == null) {
                                        Surface(
                                            onClick = onOpenCreateSticker,
                                            shape = RoundedCornerShape(14.dp),
                                            color = Color(0xFF1F2633),
                                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .testTag("create_sticker_tile")
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Add,
                                                    contentDescription = "Создать стикер",
                                                    tint = Color(0xFFA78BFA),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Text(
                                                    text = "Создать",
                                                    color = Color(0xFFA78BFA),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    } else {
                                        StickerTileCell(
                                            sticker = entry,
                                            onClick = { onStickerClick(pack, entry) },
                                            onLongClick = { onStickerLongPress(pack, entry) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                        )
                                    }
                                }
                                repeat((columns - row.size).coerceAtLeast(0)) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun computePackScrollIndex(
    hasRecent: Boolean,
    recentRows: Int,
    hasTrendingShelf: Boolean,
    activePacks: List<StickerPack>,
    targetPackIndex: Int,
    columns: Int
): Int {
    var index = 0
    if (hasRecent) index += 1 + recentRows
    if (hasTrendingShelf) index += 1
    for (i in 0 until targetPackIndex) {
        val pack = activePacks[i]
        val extra = if (pack.id == "joker") 1 else 0
        val rowCount = ((pack.stickers.size + extra) + columns - 1) / columns
        index += 1 + rowCount
    }
    return index
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerTileCell(
    sticker: StickerItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bg = Color(sticker.bgColorHex)
    val accent = Color(sticker.accentColorHex)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "sticker_press_scale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { contentDescription = "Стикер: ${sticker.label}" }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = sticker.emoji,
                fontSize = 34.sp,
                textAlign = TextAlign.Center
            )
            if (sticker.caption.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sticker.caption,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StickerBubbleArtwork(
    payload: StickerPayload,
    modifier: Modifier = Modifier,
    size: Dp = 152.dp
) {
    val bg = Color(payload.bgColorHex)
    val accent = Color(payload.accentColorHex)
    val pulse by rememberInfiniteTransition(label = "sticker_bubble_anim").animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sticker_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(pulse),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.88f)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.34f),
                            bg.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = payload.emoji,
                fontSize = 86.sp,
                textAlign = TextAlign.Center
            )
            if (payload.caption.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = payload.caption,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerPreviewDialog(
    pack: StickerPack,
    sticker: StickerItem,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A202C),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StickerBubbleArtwork(
                    payload = StickerPayload(
                        stickerId = sticker.id,
                        packId = pack.id,
                        emoji = sticker.emoji,
                        label = sticker.label,
                        caption = sticker.caption,
                        bgColorHex = sticker.bgColorHex,
                        accentColorHex = sticker.accentColorHex
                    ),
                    size = 148.dp
                )
                Spacer(Modifier.height(8.dp))
                Text(sticker.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Набор «${pack.title}»", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                Text("Отправить стикер")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
private fun CreateCustomStickerDialog(
    onDismiss: () -> Unit,
    onCreate: (emoji: String, label: String, caption: String) -> Unit
) {
    var selectedEmoji by remember { mutableStateOf("😎") }
    var label by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    val presets = listOf("😎", "🔥", "🤡", "👻", "🎉", "❤️", "🤯", "🦊", "💎", "⚡")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A202C),
        title = { Text("Создать стикер", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Выберите эмодзи-основу:", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { emoji ->
                        Surface(
                            onClick = { selectedEmoji = emoji },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedEmoji == emoji) Color(0xFF8B5CF6).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Название стикера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Надпись на стикере (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(selectedEmoji, label.ifBlank { "Авторский стикер" }, caption)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                Text("Создать и отправить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
