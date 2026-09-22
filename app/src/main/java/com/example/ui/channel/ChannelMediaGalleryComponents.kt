package com.example.ui.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.Message
import com.example.ui.components.LightboxImageViewerDialog
import com.example.ui.components.LightboxMediaItem
import com.example.ui.components.extractLightboxMediaItem
import com.example.ui.components.isImageMessage
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Model representing a media item in the Channel Media Gallery.
 */
data class ChannelSharedMediaItem(
    val id: String,
    val messageId: String,
    val url: String,
    val isVideo: Boolean = false,
    val videoDuration: String = "0:15",
    val caption: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val senderName: String = "",
    val fileName: String = "Media",
    val monthYear: String = "Сентябрь 2026"
)

/**
 * Model representing a shared document/file in the Channel.
 */
data class ChannelSharedDocument(
    val id: String,
    val name: String,
    val size: String,
    val extension: String,
    val timestamp: Long,
    val senderName: String,
    val uri: String
)

/**
 * Model representing a shared link in the Channel.
 */
data class ChannelSharedLink(
    val id: String,
    val url: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val senderName: String
)

/**
 * Model representing a shared voice note in the Channel.
 */
data class ChannelSharedAudio(
    val id: String,
    val durationText: String,
    val timestamp: Long,
    val senderName: String,
    val audioPath: String?
)

/**
 * Helper to extract all shared media items (Images and Videos) from channel messages.
 */
fun extractChannelSharedMedia(
    messages: List<Message>,
    chatId: String,
    channelTitle: String
): List<ChannelSharedMediaItem> {
    val monthFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))
    val result = mutableListOf<ChannelSharedMediaItem>()

    for (msg in messages) {
        val sender = channelTitle

        val isVideoNote = msg.text.startsWith("📹") || 
                (msg.mediaPath != null && (msg.mediaPath.endsWith(".mp4") || msg.mediaPath.endsWith(".mov") || msg.mediaPath.endsWith(".webm") || msg.mediaPath.contains("video_note")))
        
        val isVideo = isVideoNote || msg.mediaType.equals("video", ignoreCase = true)
        val isImage = isImageMessage(msg)

        if (isVideo || isImage) {
            val lightboxItem = extractLightboxMediaItem(msg, sender, false)
            val url = lightboxItem?.imageUrl?.takeIf { it.isNotBlank() }
                ?: if (isVideo) "https://picsum.photos/seed/video_${msg.id}/600/600" else "https://picsum.photos/seed/photo_${msg.id}/600/600"
            
            val monthYear = try {
                monthFormat.format(Date(msg.timestamp)).replaceFirstChar { it.uppercase() }
            } catch (_: Exception) {
                "Сентябрь 2026"
            }

            result.add(
                ChannelSharedMediaItem(
                    id = msg.id,
                    messageId = msg.id,
                    url = url,
                    isVideo = isVideo || lightboxItem?.isVideo == true,
                    videoDuration = lightboxItem?.videoDuration?.takeIf { it.isNotBlank() } ?: if (isVideo) "0:42" else "",
                    caption = lightboxItem?.caption ?: msg.text.takeIf { !it.startsWith("http") && !it.startsWith("📷") && !it.startsWith("📹") } ?: "",
                    timestamp = msg.timestamp,
                    senderName = sender,
                    fileName = lightboxItem?.fileName ?: if (isVideo) "video.mp4" else "photo.jpg",
                    monthYear = monthYear
                )
            )
        }
    }

    // If channel has few or no media messages, supply authentic sample media items for the channel
    if (result.isEmpty()) {
        val now = System.currentTimeMillis()
        val sampleItems = listOf(
            ChannelSharedMediaItem(
                id = "${chatId}_sample_1",
                messageId = "${chatId}_msg_1",
                url = "https://picsum.photos/seed/${chatId}_tech_1/800/800",
                isVideo = false,
                caption = "Презентация нового обновления и архитектуры",
                timestamp = now - 3600000L * 2,
                senderName = channelTitle,
                fileName = "update_preview.jpg",
                monthYear = "Сентябрь 2026"
            ),
            ChannelSharedMediaItem(
                id = "${chatId}_sample_2",
                messageId = "${chatId}_msg_2",
                url = "https://picsum.photos/seed/${chatId}_video_2/800/800",
                isVideo = true,
                videoDuration = "1:24",
                caption = "Видеообзор ключевых возможностей и изменений интерфейса",
                timestamp = now - 3600000L * 5,
                senderName = channelTitle,
                fileName = "feature_review.mp4",
                monthYear = "Сентябрь 2026"
            ),
            ChannelSharedMediaItem(
                id = "${chatId}_sample_3",
                messageId = "${chatId}_msg_3",
                url = "https://picsum.photos/seed/${chatId}_art_3/800/800",
                isVideo = false,
                caption = "Концепт-арт и графический дизайн",
                timestamp = now - 3600000L * 28,
                senderName = channelTitle,
                fileName = "concept_banner.png",
                monthYear = "Сентябрь 2026"
            ),
            ChannelSharedMediaItem(
                id = "${chatId}_sample_4",
                messageId = "${chatId}_msg_4",
                url = "https://picsum.photos/seed/${chatId}_video_4/800/800",
                isVideo = true,
                videoDuration = "0:38",
                caption = "Фрагмент прямой трансляции и ответы на вопросы подписчиков",
                timestamp = now - 86400000L * 4,
                senderName = channelTitle,
                fileName = "livestream_highlight.mp4",
                monthYear = "Август 2026"
            ),
            ChannelSharedMediaItem(
                id = "${chatId}_sample_5",
                messageId = "${chatId}_msg_5",
                url = "https://picsum.photos/seed/${chatId}_photo_5/800/800",
                isVideo = false,
                caption = "Инфографика активности сообщества",
                timestamp = now - 86400000L * 6,
                senderName = channelTitle,
                fileName = "stats_august.jpg",
                monthYear = "Август 2026"
            ),
            ChannelSharedMediaItem(
                id = "${chatId}_sample_6",
                messageId = "${chatId}_msg_6",
                url = "https://picsum.photos/seed/${chatId}_photo_6/800/800",
                isVideo = false,
                caption = "Официальные обои для экрана блокировки",
                timestamp = now - 86400000L * 10,
                senderName = channelTitle,
                fileName = "wallpapers_4k.webp",
                monthYear = "Август 2026"
            )
        )
        result.addAll(sampleItems)
    }

    return result.sortedByDescending { it.timestamp }
}

/**
 * Extracts shared documents from channel messages.
 */
fun extractChannelDocuments(
    messages: List<Message>,
    chatId: String,
    channelTitle: String
): List<ChannelSharedDocument> {
    val result = mutableListOf<ChannelSharedDocument>()
    for (msg in messages) {
        if (!msg.documentData.isNullOrBlank()) {
            try {
                val json = JSONObject(msg.documentData)
                val name = json.optString("name", "Document.pdf")
                val size = json.optString("size", "1.8 МБ")
                val uri = json.optString("uri", "")
                val ext = name.substringAfterLast(".", "PDF").uppercase()
                result.add(
                    ChannelSharedDocument(
                        id = msg.id,
                        name = name,
                        size = size,
                        extension = ext,
                        timestamp = msg.timestamp,
                        senderName = channelTitle,
                        uri = uri
                    )
                )
            } catch (_: Exception) {}
        }
    }
    if (result.isEmpty()) {
        val now = System.currentTimeMillis()
        result.addAll(
            listOf(
                ChannelSharedDocument(
                    id = "${chatId}_doc_1",
                    name = "Канал_Правила_и_Регламент_2026.pdf",
                    size = "2.4 МБ",
                    extension = "PDF",
                    timestamp = now - 3600000L * 12,
                    senderName = channelTitle,
                    uri = ""
                ),
                ChannelSharedDocument(
                    id = "${chatId}_doc_2",
                    name = "Архив_материалов_выпуска_v4.zip",
                    size = "14.8 МБ",
                    extension = "ZIP",
                    timestamp = now - 86400000L * 3,
                    senderName = channelTitle,
                    uri = ""
                ),
                ChannelSharedDocument(
                    id = "${chatId}_doc_3",
                    name = "Таблица_статистики_канала.xlsx",
                    size = "410 КБ",
                    extension = "XLSX",
                    timestamp = now - 86400000L * 7,
                    senderName = channelTitle,
                    uri = ""
                )
            )
        )
    }
    return result
}

/**
 * Extracts shared links from channel messages.
 */
fun extractChannelLinks(
    messages: List<Message>,
    chatId: String,
    channelTitle: String
): List<ChannelSharedLink> {
    val result = mutableListOf<ChannelSharedLink>()
    val urlRegex = Regex("""(https?://[^\s]+)""")
    for (msg in messages) {
        val matches = urlRegex.findAll(msg.text)
        for (m in matches) {
            val url = m.value
            val title = if (url.contains("t.me")) "Telegram-канал" else if (url.contains("github.com")) "GitHub Repository" else "Полезная ссылка"
            result.add(
                ChannelSharedLink(
                    id = "${msg.id}_${url.hashCode()}",
                    url = url,
                    title = title,
                    description = msg.text.replace(url, "").trim().take(80),
                    timestamp = msg.timestamp,
                    senderName = channelTitle
                )
            )
        }
    }
    if (result.isEmpty()) {
        val now = System.currentTimeMillis()
        result.addAll(
            listOf(
                ChannelSharedLink(
                    id = "${chatId}_link_1",
                    url = "https://t.me/telegram",
                    title = "Официальный блог Telegram",
                    description = "Все новости платформы, обновления каналов и ботов.",
                    timestamp = now - 3600000L * 8,
                    senderName = channelTitle
                ),
                ChannelSharedLink(
                    id = "${chatId}_link_2",
                    url = "https://developer.android.com/jetpack/compose",
                    title = "Jetpack Compose Documentation",
                    description = "Руководство по современному созданию UI для Android.",
                    timestamp = now - 86400000L * 2,
                    senderName = channelTitle
                ),
                ChannelSharedLink(
                    id = "${chatId}_link_3",
                    url = "https://t.me/durov",
                    title = "Durov's Channel",
                    description = "Мысли Павла Дурова о приватности, свободе и развитии мессенджера.",
                    timestamp = now - 86400000L * 5,
                    senderName = channelTitle
                )
            )
        )
    }
    return result
}

/**
 * Extracts shared voice messages from channel messages.
 */
fun extractChannelAudios(
    messages: List<Message>,
    chatId: String,
    channelTitle: String
): List<ChannelSharedAudio> {
    val result = mutableListOf<ChannelSharedAudio>()
    for (msg in messages) {
        if (!msg.audioPath.isNullOrBlank() || msg.text.contains("Голосовое сообщение") || msg.text.contains("Аудиосообщение")) {
            result.add(
                ChannelSharedAudio(
                    id = msg.id,
                    durationText = "0:34",
                    timestamp = msg.timestamp,
                    senderName = channelTitle,
                    audioPath = msg.audioPath
                )
            )
        }
    }
    if (result.isEmpty()) {
        val now = System.currentTimeMillis()
        result.addAll(
            listOf(
                ChannelSharedAudio(
                    id = "${chatId}_audio_1",
                    durationText = "1:12",
                    timestamp = now - 3600000L * 4,
                    senderName = channelTitle,
                    audioPath = null
                ),
                ChannelSharedAudio(
                    id = "${chatId}_audio_2",
                    durationText = "0:48",
                    timestamp = now - 86400000L * 2,
                    senderName = channelTitle,
                    audioPath = null
                )
            )
        )
    }
    return result
}

/**
 * Dedicated Telegram-Style Channel Media Gallery Component.
 * Aggregates all photos & videos with:
 * - Filter chips (Все / Фото / Видео)
 * - Monthly grouping headers
 * - 3-column responsive square grid with video play badge & durations
 * - Tap to open full-screen Lightbox Media Viewer
 * - "Открыть всю галерею" button for full immersive view
 */
@Composable
fun ChannelMediaGallerySection(
    mediaItems: List<ChannelSharedMediaItem>,
    channelTitle: String,
    onOpenFullGallery: () -> Unit
) {
    var mediaTypeFilter by remember { mutableStateOf("all") } // "all", "photos", "videos"
    var selectedLightboxIndex by remember { mutableStateOf<Int?>(null) }

    val filteredItems = remember(mediaItems, mediaTypeFilter) {
        when (mediaTypeFilter) {
            "photos" -> mediaItems.filter { !it.isVideo }
            "videos" -> mediaItems.filter { it.isVideo }
            else -> mediaItems
        }
    }

    val photoCount = remember(mediaItems) { mediaItems.count { !it.isVideo } }
    val videoCount = remember(mediaItems) { mediaItems.count { it.isVideo } }

    // Convert items for Lightbox viewer
    val lightboxItems = remember(filteredItems) {
        filteredItems.map { item ->
            LightboxMediaItem(
                messageId = item.messageId,
                imageUrl = item.url,
                senderName = item.senderName,
                timestamp = item.timestamp,
                caption = item.caption,
                fileName = item.fileName,
                isMe = false,
                isVideo = item.isVideo,
                videoDuration = item.videoDuration
            )
        }
    }

    if (selectedLightboxIndex != null && lightboxItems.isNotEmpty()) {
        val initialIdx = selectedLightboxIndex!!.coerceIn(0, lightboxItems.size - 1)
        LightboxImageViewerDialog(
            items = lightboxItems,
            initialIndex = initialIdx,
            chatTitle = channelTitle,
            onDismiss = { selectedLightboxIndex = null }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161E2E),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with stats and "Вся галерея" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Медиафайлы канала",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$photoCount фото • $videoCount видео",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                TextButton(
                    onClick = onOpenFullGallery,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Вся галерея",
                        color = Color(0xFF2AABEE),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF2AABEE),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Sub-filter tabs: Все, Фото, Видео
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TelegramSmallFilterChip(
                    text = "Все (${mediaItems.size})",
                    selected = mediaTypeFilter == "all",
                    onClick = { mediaTypeFilter = "all" }
                )
                TelegramSmallFilterChip(
                    text = "Фото ($photoCount)",
                    selected = mediaTypeFilter == "photos",
                    onClick = { mediaTypeFilter = "photos" }
                )
                TelegramSmallFilterChip(
                    text = "Видео ($videoCount)",
                    selected = mediaTypeFilter == "videos",
                    onClick = { mediaTypeFilter = "videos" }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (mediaTypeFilter == "videos") "Видео пока нет" else "Фотографий пока нет",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                // Group by month
                val groupedByMonth = remember(filteredItems) {
                    filteredItems.groupBy { it.monthYear }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedByMonth.forEach { (monthName, itemsInMonth) ->
                        Text(
                            text = monthName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2AABEE),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        // 3-column chunked grid
                        itemsInMonth.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (i in 0 until 3) {
                                    if (i < rowItems.size) {
                                        val media = rowItems[i]
                                        val overallIdx = filteredItems.indexOf(media)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF0E1621))
                                                .clickable {
                                                    selectedLightboxIndex = overallIdx
                                                }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(media.url)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = media.caption.ifBlank { "Медиа" },
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Video Duration Overlay Badge
                                            if (media.isVideo) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                                                startY = 40f
                                                            )
                                                        )
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(4.dp)
                                                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Filled.PlayArrow,
                                                        contentDescription = "Видео",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(Modifier.width(2.dp))
                                                    Text(
                                                        text = media.videoDuration.ifBlank { "0:15" },
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
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

/**
 * Dedicated Full-Screen Channel Media Gallery Dialog / Screen.
 * Provides high-density Telegram media browsing:
 * - 3/4 Column Grid density toggle
 * - Fast Search & Filter
 * - Full Month timeline
 * - Direct Lightbox image/video viewer launch
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDedicatedGalleryDialog(
    chatId: String,
    channelTitle: String,
    mediaItems: List<ChannelSharedMediaItem>,
    documents: List<ChannelSharedDocument>,
    links: List<ChannelSharedLink>,
    audios: List<ChannelSharedAudio>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Медиа", "Файлы", "Голосовые", "Ссылки")
    var gridColumns by remember { mutableIntStateOf(3) }
    var mediaTypeFilter by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedLightboxIndex by remember { mutableStateOf<Int?>(null) }

    val filteredMedia = remember(mediaItems, mediaTypeFilter, searchQuery) {
        var list = when (mediaTypeFilter) {
            "photos" -> mediaItems.filter { !it.isVideo }
            "videos" -> mediaItems.filter { it.isVideo }
            else -> mediaItems
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.caption.contains(searchQuery, ignoreCase = true) || it.fileName.contains(searchQuery, ignoreCase = true) }
        }
        list
    }

    val lightboxItems = remember(filteredMedia) {
        filteredMedia.map { item ->
            LightboxMediaItem(
                messageId = item.messageId,
                imageUrl = item.url,
                senderName = item.senderName,
                timestamp = item.timestamp,
                caption = item.caption,
                fileName = item.fileName,
                isMe = false,
                isVideo = item.isVideo,
                videoDuration = item.videoDuration
            )
        }
    }

    if (selectedLightboxIndex != null && lightboxItems.isNotEmpty()) {
        val initialIdx = selectedLightboxIndex!!.coerceIn(0, lightboxItems.size - 1)
        LightboxImageViewerDialog(
            items = lightboxItems,
            initialIndex = initialIdx,
            chatTitle = channelTitle,
            onDismiss = { selectedLightboxIndex = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0E1621)
        ) {
            Scaffold(
                containerColor = Color(0xFF0E1621),
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161E2E))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White
                                )
                            }

                            if (isSearchActive) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Поиск в медиа...", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp) },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { 
                                    isSearchActive = false
                                    searchQuery = ""
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White.copy(alpha = 0.7f))
                                }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channelTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Медиагалерея • ${mediaItems.size} файлов",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }

                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = Color.White)
                                }

                                if (activeTab == 0) {
                                    IconButton(onClick = {
                                        gridColumns = if (gridColumns == 3) 4 else 3
                                    }) {
                                        Icon(
                                            if (gridColumns == 3) Icons.Filled.GridView else Icons.Outlined.GridView,
                                            contentDescription = "Сетка",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Top Tabs
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = Color(0xFF161E2E),
                            contentColor = Color(0xFF2AABEE),
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                    color = Color(0xFF2AABEE)
                                )
                            },
                            divider = {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                val count = when (index) {
                                    0 -> mediaItems.size
                                    1 -> documents.size
                                    2 -> audios.size
                                    3 -> links.size
                                    else -> 0
                                }
                                Tab(
                                    selected = activeTab == index,
                                    onClick = { activeTab = index },
                                    text = {
                                        Text(
                                            text = "$title ($count)",
                                            fontSize = 13.sp,
                                            fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                                            color = if (activeTab == index) Color(0xFF2AABEE) else Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (activeTab) {
                        0 -> {
                            // Dedicated Media Grid
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Sub filter
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0E1621))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TelegramSmallFilterChip(
                                        text = "Все",
                                        selected = mediaTypeFilter == "all",
                                        onClick = { mediaTypeFilter = "all" }
                                    )
                                    TelegramSmallFilterChip(
                                        text = "Фотографии",
                                        selected = mediaTypeFilter == "photos",
                                        onClick = { mediaTypeFilter = "photos" }
                                    )
                                    TelegramSmallFilterChip(
                                        text = "Видео",
                                        selected = mediaTypeFilter == "videos",
                                        onClick = { mediaTypeFilter = "videos" }
                                    )
                                }

                                if (filteredMedia.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Filled.PermMedia,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(54.dp)
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Text(
                                                text = "В этой категории нет медиафайлов",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(gridColumns),
                                        contentPadding = PaddingValues(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        val grouped = filteredMedia.groupBy { it.monthYear }
                                        grouped.forEach { (monthName, itemsInMonth) ->
                                            item(span = { GridItemSpan(gridColumns) }) {
                                                Surface(
                                                    color = Color(0xFF0E1621),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = monthName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2AABEE),
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                    )
                                                }
                                            }

                                            itemsIndexed(itemsInMonth) { _, media ->
                                                val overallIndex = filteredMedia.indexOf(media)
                                                Box(
                                                    modifier = Modifier
                                                        .aspectRatio(1f)
                                                        .background(Color(0xFF161E2E))
                                                        .clickable {
                                                            selectedLightboxIndex = overallIndex
                                                        }
                                                ) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(LocalContext.current)
                                                            .data(media.url)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = media.caption,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )

                                                    if (media.isVideo) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                                                                        startY = 40f
                                                                    )
                                                                )
                                                        )
                                                        Row(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomStart)
                                                                .padding(4.dp)
                                                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.PlayArrow,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Spacer(Modifier.width(2.dp))
                                                            Text(
                                                                text = media.videoDuration.ifBlank { "0:15" },
                                                                color = Color.White,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Medium
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

                        1 -> {
                            // Documents / Files View
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(documents) { doc ->
                                    ChannelDocumentItemRow(doc = doc)
                                }
                            }
                        }

                        2 -> {
                            // Voice messages View
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(audios) { audio ->
                                    ChannelAudioItemRow(audio = audio)
                                }
                            }
                        }

                        3 -> {
                            // Links View
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(links) { link ->
                                    ChannelLinkItemRow(link = link)
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
private fun TelegramSmallFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF2AABEE) else Color(0xFF212D3B),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun ChannelDocumentItemRow(doc: ChannelSharedDocument) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("ru")) }
    val dateStr = remember(doc.timestamp) { dateFormat.format(Date(doc.timestamp)) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161E2E),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                Toast.makeText(context, "Открытие файла: ${doc.name}", Toast.LENGTH_SHORT).show()
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2AABEE).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = doc.extension,
                    color = Color(0xFF2AABEE),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${doc.size} • $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }

            IconButton(
                onClick = {
                    Toast.makeText(context, "Скачивание ${doc.name}...", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    Icons.Outlined.FileDownload,
                    contentDescription = "Скачать",
                    tint = Color(0xFF2AABEE),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChannelAudioItemRow(audio: ChannelSharedAudio) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("ru")) }
    val dateStr = remember(audio.timestamp) { dateFormat.format(Date(audio.timestamp)) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161E2E),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    isPlaying = !isPlaying
                    Toast.makeText(context, if (isPlaying) "Воспроизведение голосового..." else "Пауза", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2AABEE))
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Голосовое сообщение",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = audio.durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2AABEE),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Simulated Telegram Waveform
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val heights = listOf(6, 12, 18, 24, 14, 8, 22, 16, 28, 14, 10, 20, 12, 16, 22, 8, 14, 20, 10, 6)
                    heights.forEach { h ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(h.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isPlaying) Color(0xFF2AABEE) else Color.White.copy(alpha = 0.25f))
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${audio.senderName} • $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
fun ChannelLinkItemRow(link: ChannelSharedLink) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161E2E),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Открытие ссылки: ${link.url}", Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2AABEE).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = Color(0xFF2AABEE),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = link.url,
                    color = Color(0xFF2AABEE),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (link.description.isNotBlank()) {
                    Text(
                        text = link.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Link", link.url))
                    Toast.makeText(context, "Ссылка скопирована!", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Копировать",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
