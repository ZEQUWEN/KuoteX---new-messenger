package com.example.ui.media

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

private val PanelBgColor = Color(0xFF0E1117)
private val CardBgColor = Color(0xFF171B24)
private val AccentPurple = Color(0xFF9C68FC)

private fun formatStickerCountRu(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "стикеров"
        mod10 == 1 -> "стикер"
        mod10 in 2..4 -> "стикера"
        else -> "стикеров"
    }
    return "$count $word"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StickersAndEmojiScreen(
    navController: NavController,
    onOpenEmojiPacks: () -> Unit,
    onOpenQuickReaction: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        MediaExpressionManager.init(context)
    }

    val allPacks by MediaExpressionManager.stickerPacks.collectAsStateWithLifecycle()
    val activePacks = remember(allPacks) {
        allPacks.filter { it.isInstalled && !it.isArchived }.sortedBy { it.orderIndex }
    }
    val archivedPacks = remember(allPacks) {
        allPacks.filter { it.isInstalled && it.isArchived }.sortedBy { it.orderIndex }
    }

    val emojiHintMode by MediaExpressionManager.emojiHintMode.collectAsStateWithLifecycle()
    val largeEmojiEnabled by MediaExpressionManager.largeEmojiEnabled.collectAsStateWithLifecycle()
    val recentFirstEnabled by MediaExpressionManager.recentFirstEnabled.collectAsStateWithLifecycle()
    val sortDisabledNotice by MediaExpressionManager.recentSortDisabledNotice.collectAsStateWithLifecycle()
    val quickReaction by MediaExpressionManager.quickReaction.collectAsStateWithLifecycle()

    var showTrendingModal by remember { mutableStateOf(false) }
    var showHintModeMenu by remember { mutableStateOf(false) }

    // Multimodal Reorder & Multi-Select Mode
    var isReorderMultiSelectMode by remember { mutableStateOf(false) }
    var selectedPackIds by remember { mutableStateOf(setOf<String>()) }
    var draggingPackId by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }

    BackHandler(enabled = isReorderMultiSelectMode) {
        isReorderMultiSelectMode = false
        selectedPackIds = emptySet()
    }

    if (showTrendingModal) {
        TrendingStickersModalSheet(
            onDismiss = { showTrendingModal = false }
        )
    }

    Scaffold(
        containerColor = PanelBgColor,
        topBar = {
            if (isReorderMultiSelectMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBgColor),
                    title = {
                        Text(
                            text = selectedPackIds.size.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isReorderMultiSelectMode = false
                                selectedPackIds = emptySet()
                            }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Выйти из режима выбора", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val targets = if (selectedPackIds.isEmpty()) activePacks else activePacks.filter { it.id in selectedPackIds }
                                val linksText = targets.joinToString("\n") { "${it.title}: ${it.shareUrl}" }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, linksText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Поделиться наборами"))
                            }
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (selectedPackIds.isNotEmpty()) {
                                    MediaExpressionManager.archiveStickerPacks(selectedPackIds)
                                    Toast.makeText(context, "Наборы архивированы", Toast.LENGTH_SHORT).show()
                                    selectedPackIds = emptySet()
                                    isReorderMultiSelectMode = false
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Archive, contentDescription = "Архивировать", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (selectedPackIds.isNotEmpty()) {
                                    MediaExpressionManager.deleteStickerPacks(selectedPackIds)
                                    Toast.makeText(context, "Наборы удалены", Toast.LENGTH_SHORT).show()
                                    selectedPackIds = emptySet()
                                    isReorderMultiSelectMode = false
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = Color(0xFFEF4444))
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PanelBgColor),
                    title = {
                        Text(
                            text = "Стикеры и эмодзи",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card 1: Trending, Emoji, Quick Reaction
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTrendingModal = true }
                                .padding(horizontal = 14.dp, vertical = 15.dp)
                                .testTag("row_trending_stickers"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Whatshot,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Популярные стикеры",
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = MediaExpressionManager.totalTrendingStickersCount().toString(),
                                color = AccentPurple,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 48.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEmojiPacks() }
                                .padding(horizontal = 14.dp, vertical = 15.dp)
                                .testTag("row_emoji_packs"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SentimentSatisfied,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Эмодзи",
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(start = 48.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenQuickReaction() }
                                .padding(horizontal = 14.dp, vertical = 15.dp)
                                .testTag("row_quick_reaction"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Быстрая реакция",
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = quickReaction,
                                fontSize = 20.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Художники могут создавать собственные наборы с помощью бота @stickers.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Card 2: Настройки
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Настройки",
                            color = AccentPurple,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
                        )

                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showHintModeMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                                    .testTag("row_emoji_hint_mode"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Подсказка по эмодзи",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = emojiHintMode.label,
                                    color = AccentPurple,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showHintModeMenu,
                                onDismissRequest = { showHintModeMenu = false },
                                containerColor = Color(0xFF222834)
                            ) {
                                EmojiHintMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label, color = Color.White) },
                                        leadingIcon = {
                                            if (emojiHintMode == mode) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = AccentPurple)
                                            }
                                        },
                                        onClick = {
                                            MediaExpressionManager.setEmojiHintMode(mode)
                                            showHintModeMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { MediaExpressionManager.setLargeEmojiEnabled(!largeEmojiEnabled) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Крупные эмодзи",
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Switch(
                                checked = largeEmojiEnabled,
                                onCheckedChange = { MediaExpressionManager.setLargeEmojiEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentPurple
                                )
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { MediaExpressionManager.setRecentFirstEnabled(!recentFirstEnabled) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Сначала недавние",
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Switch(
                                checked = recentFirstEnabled,
                                onCheckedChange = { MediaExpressionManager.setRecentFirstEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentPurple
                                )
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = "Автоматически перемещать недавно использованные наборы наверх.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp
                    )
                    AnimatedVisibility(visible = sortDisabledNotice && !recentFirstEnabled) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentPurple.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Сортировка по времени отключена: порядок наборов больше не будет меняться при каждом использовании стикера.",
                                color = Color(0xFFD8B4FE),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Card 3: Мои наборы стикеров (with Drag-Reorder & Multi-select)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Мои наборы стикеров",
                                color = AccentPurple,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!isReorderMultiSelectMode && activePacks.size > 1) {
                                TextButton(
                                    onClick = {
                                        isReorderMultiSelectMode = true
                                        selectedPackIds = emptySet()
                                    }
                                ) {
                                    Text("Порядок", color = AccentPurple, fontSize = 12.sp)
                                }
                            }
                        }

                        if (activePacks.isEmpty()) {
                            Text(
                                text = "Нет активных наборов. Откройте «Популярные стикеры», чтобы добавить наборы.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            val itemHeightPx = with(density) { 64.dp.toPx() }
                            activePacks.forEachIndexed { index, pack ->
                                val isSelected = pack.id in selectedPackIds
                                val isDragging = draggingPackId == pack.id
                                var rowMenuExpanded by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragging) 10f else 0f)
                                        .graphicsLayer {
                                            if (isDragging) {
                                                translationY = dragAccumulatedPx
                                                scaleX = 1.02f
                                                scaleY = 1.02f
                                            }
                                        }
                                        .background(if (isDragging) Color(0xFF232A38) else Color.Transparent)
                                        .combinedClickable(
                                            onClick = {
                                                if (isReorderMultiSelectMode) {
                                                    selectedPackIds = if (isSelected) {
                                                        selectedPackIds - pack.id
                                                    } else {
                                                        selectedPackIds + pack.id
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Удерживайте набор или нажмите «Изменить порядок» для сортировки",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isReorderMultiSelectMode = true
                                                selectedPackIds = selectedPackIds + pack.id
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isReorderMultiSelectMode) {
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) Color(0xFF4ADE80)
                                                    else Color.White.copy(alpha = 0.14f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Выбрано",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF232936),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(pack.coverEmoji, fontSize = 24.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pack.title,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatStickerCountRu(pack.stickers.size),
                                            color = Color.White.copy(alpha = 0.52f),
                                            fontSize = 13.sp
                                        )
                                    }

                                    if (isReorderMultiSelectMode) {
                                        Icon(
                                            imageVector = Icons.Filled.DragHandle,
                                            contentDescription = "Перетащить набор",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(34.dp)
                                                .padding(4.dp)
                                                .pointerInput(pack.id, activePacks.size) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggingPackId = pack.id
                                                            dragAccumulatedPx = 0f
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragAccumulatedPx += dragAmount.y
                                                            val currentIdx = MediaExpressionManager.getActiveStickerPacks()
                                                                .indexOfFirst { it.id == pack.id }
                                                            if (currentIdx != -1) {
                                                                if (dragAccumulatedPx > itemHeightPx * 0.65f &&
                                                                    currentIdx < MediaExpressionManager.getActiveStickerPacks().lastIndex
                                                                ) {
                                                                    MediaExpressionManager.moveActivePack(currentIdx, currentIdx + 1)
                                                                    dragAccumulatedPx -= itemHeightPx
                                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                } else if (dragAccumulatedPx < -itemHeightPx * 0.65f && currentIdx > 0) {
                                                                    MediaExpressionManager.moveActivePack(currentIdx, currentIdx - 1)
                                                                    dragAccumulatedPx += itemHeightPx
                                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            draggingPackId = null
                                                            dragAccumulatedPx = 0f
                                                        },
                                                        onDragCancel = {
                                                            draggingPackId = null
                                                            dragAccumulatedPx = 0f
                                                        }
                                                    )
                                                }
                                        )
                                    } else {
                                        Box {
                                            IconButton(
                                                onClick = { rowMenuExpanded = true },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MoreVert,
                                                    contentDescription = "Действия с набором ${pack.title}",
                                                    tint = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = rowMenuExpanded,
                                                onDismissRequest = { rowMenuExpanded = false },
                                                containerColor = Color(0xFF222834)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Архивировать", color = Color.White) },
                                                    leadingIcon = { Icon(Icons.Filled.Archive, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                    onClick = {
                                                        rowMenuExpanded = false
                                                        MediaExpressionManager.archiveStickerPacks(setOf(pack.id))
                                                        Toast.makeText(context, "Набор «${pack.title}» архивирован", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Копировать ссылку", color = Color.White) },
                                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                    onClick = {
                                                        rowMenuExpanded = false
                                                        clipboardManager.setText(AnnotatedString(pack.shareUrl))
                                                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Изменить порядок", color = Color.White) },
                                                    leadingIcon = { Icon(Icons.Filled.SwapVert, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                    onClick = {
                                                        rowMenuExpanded = false
                                                        isReorderMultiSelectMode = true
                                                        selectedPackIds = setOf(pack.id)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Поделиться", color = Color.White) },
                                                    leadingIcon = { Icon(Icons.Filled.Share, null, tint = Color.White.copy(alpha = 0.8f)) },
                                                    onClick = {
                                                        rowMenuExpanded = false
                                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, "${pack.title}: ${pack.shareUrl}")
                                                        }
                                                        context.startActivity(Intent.createChooser(sendIntent, "Поделиться"))
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Удалить", color = Color(0xFFEF4444)) },
                                                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFEF4444)) },
                                                    onClick = {
                                                        rowMenuExpanded = false
                                                        MediaExpressionManager.deleteStickerPacks(setOf(pack.id))
                                                        Toast.makeText(context, "Набор «${pack.title}» удалён", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (index < activePacks.lastIndex) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.05f),
                                        modifier = Modifier.padding(start = 74.dp, end = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (archivedPacks.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = CardBgColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "Архивные наборы (${archivedPacks.size})",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            archivedPacks.forEach { pack ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(pack.coverEmoji, fontSize = 24.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pack.title, color = Color.White, fontSize = 14.sp)
                                        Text(formatStickerCountRu(pack.stickers.size), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { MediaExpressionManager.unarchiveStickerPack(pack.id) }) {
                                        Icon(Icons.Filled.Unarchive, contentDescription = "Вернуть набор", tint = AccentPurple)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingStickersModalSheet(
    onDismiss: () -> Unit
) {
    val allPacks by MediaExpressionManager.stickerPacks.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val trendingPacks = remember(allPacks, query) {
        MediaExpressionManager.getTrendingStickerPacks(query)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF151922),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = "Популярные стикеры",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Search box
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1E2430),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Поиск стикеров", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(AccentPurple),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(trendingPacks, key = { it.id }) { pack ->
                    val isAdded = pack.isInstalled && !pack.isArchived
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pack.title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (pack.isLocked) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(
                                    text = formatStickerCountRu(pack.stickers.size),
                                    color = Color.White.copy(alpha = 0.52f),
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = { MediaExpressionManager.toggleInstallTrendingPack(pack.id) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAdded) Color(0xFF232936) else AccentPurple
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isAdded) "Удалить" else "Добавить",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(pack.stickers.take(5), key = { it.id }) { sticker ->
                                StickerTileCell(
                                    sticker = sticker,
                                    onClick = { MediaExpressionManager.toggleInstallTrendingPack(pack.id) },
                                    modifier = Modifier.size(58.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPacksSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MediaExpressionManager.init(context) }

    val emojiPacks by MediaExpressionManager.emojiPacks.collectAsStateWithLifecycle()
    val suggestInstead by MediaExpressionManager.suggestEmojiInsteadOfStickers.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PanelBgColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PanelBgColor),
                title = { Text("Эмодзи", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { MediaExpressionManager.setSuggestEmojiInsteadOfStickers(!suggestInstead) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Подсказывать вместо стикеров",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = suggestInstead,
                            onCheckedChange = { MediaExpressionManager.setSuggestEmojiInsteadOfStickers(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentPurple)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Вы можете получать подсказки эмодзи при вводе слов. Эти параметры можно настроить в меню Стикеры и эмодзи.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Популярные наборы эмодзи",
                            color = AccentPurple,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        val displayed = if (showAll) emojiPacks else emojiPacks.take(4)
                        displayed.forEachIndexed { idx, pack ->
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pack.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Text("${pack.emojiCount} эмодзи", color = Color.White.copy(alpha = 0.52f), fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = { MediaExpressionManager.toggleAddEmojiPack(pack.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (pack.isAdded) Color(0xFF232936) else AccentPurple
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (pack.isAdded) "Добавлен" else "Получить",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (!pack.isAdded && pack.isLocked) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    pack.previewEmojis.forEach { emoji ->
                                        Text(emoji, fontSize = 26.sp)
                                    }
                                }
                            }
                            if (idx < displayed.lastIndex) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }

                        if (!showAll && emojiPacks.size > 4) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 16.dp))
                            Text(
                                text = "Показать ещё эмодзи",
                                color = AccentPurple,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAll = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReactionSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MediaExpressionManager.init(context) }

    val selectedEmoji by MediaExpressionManager.quickReaction.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = PanelBgColor,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PanelBgColor),
                title = { Text("Реакции", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Double-tap reaction preview card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Двойное нажатие на сообщение поставит выбранную реакцию ($selectedEmoji).",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF5B3CE6),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Отлично, договорились!", color = Color.White, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.35f)
                                ) {
                                    Text(
                                        text = selectedEmoji,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardBgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        MediaExpressionManager.reactionOptions.forEachIndexed { idx, option ->
                            val isSelected = selectedEmoji == option.emoji
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        MediaExpressionManager.setQuickReaction(option.emoji)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 13.dp)
                                    .testTag("reaction_option_${option.emoji}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = option.emoji, fontSize = 24.sp)
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = option.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Выбрано",
                                        tint = AccentPurple
                                    )
                                }
                            }
                            if (idx < MediaExpressionManager.reactionOptions.lastIndex) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.05f),
                                    modifier = Modifier.padding(start = 56.dp, end = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
