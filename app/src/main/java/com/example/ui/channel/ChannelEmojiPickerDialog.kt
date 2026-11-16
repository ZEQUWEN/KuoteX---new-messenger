package com.example.ui.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.EmojiItem
import com.example.ui.media.MediaExpressionManager

/**
 * Telegram-style floating Emoji Picker Popup used on the Channel Appearance & Edit screens.
 * Supports Recent tab, built-in Channel VIP emojis, custom emoji packs (Duck Emoji, Crayons, etc.),
 * search, and "Нет" (clear) option.
 */
@Composable
fun TelegramChannelEmojiPickerDialog(
    title: String,
    currentEmoji: String?,
    onSelectEmoji: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        MediaExpressionManager.init(context)
    }
    val recentEmojis by MediaExpressionManager.recentEmojis.collectAsState()
    val customPacks by MediaExpressionManager.emojiPacks.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val channelEmojis = remember {
        ChannelEmojiCatalog.emojis.map {
            EmojiItem(
                emoji = it.emoji,
                name = it.name,
                keywords = listOf(it.category.lowercase(), it.name.lowercase()),
                isCustom = true,
                packName = it.category
            )
        }
    }
    val recentList = remember(recentEmojis, channelEmojis) {
        (channelEmojis.take(10) + recentEmojis).distinctBy { it.emoji }
    }

    val displayedEmojis = remember(selectedTab, searchQuery, recentList, customPacks) {
        val base = when (selectedTab) {
            0 -> recentList
            else -> {
                val pack = customPacks.getOrNull(selectedTab - 1)
                pack?.previewEmojis?.map { sym ->
                    EmojiItem(
                        emoji = sym,
                        name = pack.title,
                        keywords = listOf(pack.title.lowercase()),
                        isCustom = true,
                        packName = pack.title
                    )
                } ?: recentList
            }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) base else (channelEmojis + recentList).distinctBy { it.emoji }.filter {
            it.name.lowercase().contains(q) || it.keywords.any { k -> k.contains(q) } || it.emoji == q
        }
    }

    val activePack = if (selectedTab > 0) customPacks.getOrNull(selectedTab - 1) else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF23262F),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(410.dp)
                    .clickable(enabled = false) {}
                    .testTag("channel_emoji_picker_popup")
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF181A20),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectEmoji(null)
                                    onDismiss()
                                }
                                .testTag("emoji_picker_clear_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Нет", color = Color(0xFF64B5F6), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            val isSelected = selectedTab == 0
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF394154) else Color(0xFF1A1D24))
                                    .clickable { selectedTab = 0 },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.AccessTime,
                                    contentDescription = "Недавние",
                                    tint = if (isSelected) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        itemsIndexed(customPacks) { index, pack ->
                            val tabIdx = index + 1
                            val isSelected = selectedTab == tabIdx
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF394154) else Color(0xFF1A1D24))
                                    .clickable { selectedTab = tabIdx },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(pack.previewEmojis.firstOrNull() ?: "✨", fontSize = 19.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF181A20),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(Color(0xFF9F6BFF)),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) {
                                        Text("Поиск эмодзи...", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
                                    }
                                    inner()
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (activePack != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = activePack.title,
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${activePack.emojiCount} эмодзи",
                                color = Color(0xFF9F6BFF),
                                fontSize = 11.sp
                            )
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (currentEmoji == null) Color(0xFF3E95EA).copy(alpha = 0.25f) else Color(0xFF1A1D24))
                                    .clickable {
                                        onSelectEmoji(null)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = "Убрать", tint = Color(0xFF64B5F6), modifier = Modifier.size(22.dp))
                            }
                        }
                        items(displayedEmojis, key = { "${selectedTab}_${it.emoji}" }) { item ->
                            val isSelected = currentEmoji == item.emoji
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF9F6BFF).copy(alpha = 0.28f) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color(0xFF9F6BFF) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        MediaExpressionManager.recordUsedEmoji(item)
                                        onSelectEmoji(item.emoji)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = item.emoji, fontSize = 24.sp)
                            }
                        }
                    }

                    if (activePack != null && !activePack.isAdded) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { MediaExpressionManager.toggleAddEmojiPack(activePack.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C62E6)),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Добавить ${activePack.title}", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
