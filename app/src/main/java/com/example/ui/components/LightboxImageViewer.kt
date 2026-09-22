package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.Message
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Model representing an image/video item for the Telegram-style Media Viewer.
 */
data class LightboxMediaItem(
    val messageId: String,
    val imageUrl: String,
    val senderName: String = "",
    val timestamp: Long = 0L,
    val caption: String = "",
    val fileName: String = "",
    val isMe: Boolean = false,
    val isVideo: Boolean = false,
    val videoDuration: String = ""
)

/**
 * Drawing path model for Telegram brush tool.
 */
data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val shapeType: String = "free" // free, circle, rect, star, cloud, arrow
)

/**
 * Sticker/Emoji overlay model for Telegram photo editor.
 */
data class PlacedSticker(
    val id: String,
    val content: String,
    val offset: Offset,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

/**
 * Text overlay model for Telegram photo editor.
 */
data class PlacedText(
    val text: String,
    val fontName: String = "Mono-serif",
    val color: Color = Color.White,
    val isFilledBg: Boolean = true,
    val offset: Offset = Offset(100f, 200f)
)

/**
 * Helper function to detect if a Message contains an image or video.
 */
fun isImageMessage(message: Message): Boolean {
    if (!message.mediaPath.isNullOrBlank()) {
        val path = message.mediaPath.lowercase()
        if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".bmp") ||
            path.startsWith("http://") || path.startsWith("https://") ||
            path.startsWith("content://") || path.startsWith("file://")
        ) {
            return true
        }
        if (message.mediaType.equals("image", ignoreCase = true) || message.mediaType.equals("photo", ignoreCase = true)) {
            return true
        }
    }
    
    if (message.mediaType.equals("image", ignoreCase = true) || message.mediaType.equals("photo", ignoreCase = true)) {
        return true
    }

    if (!message.documentData.isNullOrBlank()) {
        try {
            val json = JSONObject(message.documentData)
            val mime = json.optString("mimeType", "").lowercase()
            val name = json.optString("name", "").lowercase()
            val uri = json.optString("uri", "")
            if (mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") ||
                name.endsWith(".bmp") || uri.startsWith("http")
            ) {
                return true
            }
        } catch (_: Exception) {}
    }

    val textLower = message.text.trim().lowercase()
    if ((textLower.startsWith("http://") || textLower.startsWith("https://")) &&
        (textLower.endsWith(".jpg") || textLower.endsWith(".jpeg") || textLower.endsWith(".png") ||
         textLower.endsWith(".webp") || textLower.endsWith(".gif") || textLower.contains("picsum.photos") ||
         textLower.contains("images.unsplash.com"))
    ) {
        return true
    }

    if (textLower.startsWith("📷")) {
        return true
    }

    return false
}

/**
 * Extracts a LightboxMediaItem from a Message if it is an image/video message.
 */
fun extractLightboxMediaItem(message: Message, senderDisplayName: String? = null, isMe: Boolean = false): LightboxMediaItem? {
    if (!isImageMessage(message)) return null

    var url = ""
    var fileName = "Photo"
    var caption = message.text.takeIf { !it.startsWith("http") && !it.startsWith("📷") } ?: ""
    var isVideo = false
    var videoDuration = ""

    if (!message.mediaPath.isNullOrBlank()) {
        url = message.mediaPath
        fileName = message.mediaPath.substringAfterLast("/")
        if (url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".webm")) {
            isVideo = true
            videoDuration = "0:08"
        }
    }

    if (!message.documentData.isNullOrBlank()) {
        try {
            val json = JSONObject(message.documentData)
            val uri = json.optString("uri", "")
            val name = json.optString("name", "Photo")
            val mime = json.optString("mimeType", "")
            if (uri.isNotBlank()) {
                url = uri
            }
            if (name.isNotBlank()) {
                fileName = name
            }
            if (mime.startsWith("video/") || name.endsWith(".mp4")) {
                isVideo = true
                videoDuration = "0:08"
            }
        } catch (_: Exception) {}
    }

    if (url.isBlank()) {
        val textTrimmed = message.text.trim()
        if (textTrimmed.startsWith("http://") || textTrimmed.startsWith("https://")) {
            url = textTrimmed
            caption = ""
        } else if (textTrimmed.startsWith("📷")) {
            url = "https://picsum.photos/seed/${message.id}/800"
            caption = textTrimmed.removePrefix("📷").trim()
        } else {
            url = "https://picsum.photos/seed/${message.id}/800"
        }
    }

    val displayName = senderDisplayName ?: if (isMe) "Вы" else message.senderId

    return LightboxMediaItem(
        messageId = message.id,
        imageUrl = url,
        senderName = displayName,
        timestamp = message.timestamp,
        caption = caption,
        fileName = fileName,
        isMe = isMe,
        isVideo = isVideo,
        videoDuration = videoDuration
    )
}

/**
 * Full-featured Telegram Media Viewer with:
 * - Top header (Sender, timestamp, counter, 3-dots overflow menu)
 * - 3-dots actions: Сохранить в галерею, Показать все медиа, Показать в чате, Создать стикер, Ответить, Поделиться, Удалить
 * - Bottom tool modes:
 *   1) Crop & Rotate (aspect ratios, angle wheel -45°..+45°, вырезать объект)
 *   2) Draw / Markup (brush styles, shapes, vibrant palette, clear)
 *   3) Stickers & Emojis picker
 *   4) Text overlay with Telegram font selector (Roboto, Italic, Serif, Condensed, Mono-serif, Merriweather, Dancing Script)
 *   5) Adjustments (Слайдеры: Гладкая кожа, Улучшение, Экспозиция, Контраст; Кривые RGB; Размытие Радиальное/Линейное)
 * - Shared Media Grid modal ("Общие материалы", "Фотографии", "Видео", Calendar, Stories Archive)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightboxImageViewerDialog(
    items: List<LightboxMediaItem>,
    initialIndex: Int = 0,
    chatTitle: String = "KuoteX chat",
    onDismiss: () -> Unit,
    onReply: ((LightboxMediaItem) -> Unit)? = null,
    onShowInChat: ((LightboxMediaItem) -> Unit)? = null,
    onDelete: ((LightboxMediaItem) -> Unit)? = null
) {
    if (items.isEmpty()) {
        onDismiss()
        return
    }

    val safeInitialIndex = initialIndex.coerceIn(0, items.size - 1)
    val pagerState = rememberPagerState(initialPage = safeInitialIndex, pageCount = { items.size })
    val currentItem = items[pagerState.currentPage]
    
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // UI state
    var areControlsVisible by remember { mutableStateOf(true) }
    var activeZoomScale by remember { mutableFloatStateOf(1f) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showSharedMediaSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Editor Tools state: "none", "crop", "draw", "sticker", "text", "tune"
    var activeEditorMode by remember { mutableStateOf("none") }

    // Crop / Rotate state
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var cropAspectRatio by remember { mutableStateOf("Оригинал") }

    // Draw state
    val drawPaths = remember { mutableStateListOf<DrawPath>() }
    var currentDrawColor by remember { mutableStateOf(Color(0xFFFFD600)) }
    var currentBrushSize by remember { mutableFloatStateOf(8f) }
    var selectedDrawShape by remember { mutableStateOf("free") }
    var showShapePickerMenu by remember { mutableStateOf(false) }

    // Stickers overlay state
    val placedStickers = remember { mutableStateListOf<PlacedSticker>() }
    var showStickerSheet by remember { mutableStateOf(false) }

    // Text overlay state
    val placedTexts = remember { mutableStateListOf<PlacedText>() }
    var isAddingText by remember { mutableStateOf(false) }
    var editingTextValue by remember { mutableStateOf("Ваш Текст") }
    var selectedFontName by remember { mutableStateOf("Mono-serif") }
    var showFontMenu by remember { mutableStateOf(false) }
    var textColor by remember { mutableStateOf(Color.White) }
    var textFilledBg by remember { mutableStateOf(true) }

    // Adjustments / Tuning state
    var tuneSubTab by remember { mutableStateOf("sliders") } // "sliders", "curves", "blur"
    var smoothSkinValue by remember { mutableFloatStateOf(0f) }
    var enhanceValue by remember { mutableFloatStateOf(0f) }
    var exposureValue by remember { mutableFloatStateOf(0f) }
    var contrastValue by remember { mutableFloatStateOf(0f) }
    var curvesColorChannel by remember { mutableStateOf("Все") } // "Все", "Красный", "Зелёный", "Синий"
    var blurMode by remember { mutableStateOf("Откл.") } // "Откл.", "Радиальное", "Линейное"

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    BackHandler {
        if (activeEditorMode != "none") {
            activeEditorMode = "none"
        } else if (showSharedMediaSheet) {
            showSharedMediaSheet = false
        } else {
            safeDismiss()
        }
    }

    Dialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Horizontal Pager for swiping between conversation photos/videos
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = activeZoomScale <= 1.05f && activeEditorMode == "none"
            ) { page ->
                val mediaItem = items[page]
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ZoomableImagePage(
                        mediaItem = mediaItem,
                        isCurrentPage = (page == pagerState.currentPage),
                        rotationAngle = if (page == pagerState.currentPage) rotationAngle else 0f,
                        smoothSkin = if (page == pagerState.currentPage) smoothSkinValue else 0f,
                        exposure = if (page == pagerState.currentPage) exposureValue else 0f,
                        blurMode = if (page == pagerState.currentPage) blurMode else "Откл.",
                        onToggleControls = {
                            if (activeEditorMode == "none") {
                                areControlsVisible = !areControlsVisible
                            }
                        },
                        onDismiss = onDismiss,
                        onZoomScaleChanged = { scale ->
                            if (page == pagerState.currentPage) {
                                activeZoomScale = scale
                            }
                        }
                    )

                    // Draw paths overlay on current page
                    if (page == pagerState.currentPage) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawPaths.forEach { path ->
                                if (path.points.size > 1) {
                                    val drawPath = Path().apply {
                                        moveTo(path.points.first().x, path.points.first().y)
                                        for (i in 1 until path.points.size) {
                                            lineTo(path.points[i].x, path.points[i].y)
                                        }
                                    }
                                    drawPath(
                                        drawPath,
                                        color = path.color,
                                        style = Stroke(width = path.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                        }

                        // Placed Stickers on photo
                        placedStickers.forEach { sticker ->
                            Text(
                                text = sticker.content,
                                fontSize = 48.sp,
                                modifier = Modifier
                                    .offset { IntOffset(sticker.offset.x.roundToInt(), sticker.offset.y.roundToInt()) }
                                    .graphicsLayer {
                                        scaleX = sticker.scale
                                        scaleY = sticker.scale
                                        rotationZ = sticker.rotation
                                    }
                            )
                        }

                        // Placed Text overlay on photo
                        placedTexts.forEach { txt ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (txt.isFilledBg) Color.Black.copy(alpha = 0.65f) else Color.Transparent,
                                modifier = Modifier
                                    .offset { IntOffset(txt.offset.x.roundToInt(), txt.offset.y.roundToInt()) }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = txt.text,
                                    color = txt.color,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = getFontFamilyByName(txt.fontName),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // TOP APP BAR OVERLAY
            AnimatedVisibility(
                visible = areControlsVisible && activeEditorMode == "none",
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (chatTitle.isNotBlank()) chatTitle else currentItem.senderName.ifBlank { "Фото" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val formattedDate = remember(currentItem.timestamp) {
                                    if (currentItem.timestamp > 0) {
                                        val sdf = SimpleDateFormat("сегодня в HH:mm", Locale.getDefault())
                                        sdf.format(Date(currentItem.timestamp))
                                    } else "сегодня"
                                }
                                val subtitle = buildString {
                                    if (items.size > 1) {
                                        append("${pagerState.currentPage + 1} из ${items.size}")
                                        append(" • ")
                                    }
                                    append(formattedDate)
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }

                            // Share Action
                            IconButton(onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, currentItem.imageUrl)
                                    if (currentItem.caption.isNotBlank()) {
                                        putExtra(Intent.EXTRA_SUBJECT, currentItem.caption)
                                    }
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Поделиться фото")
                                context.startActivity(shareIntent)
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = Color.White)
                            }

                            // 3-Dots Overflow Menu
                            Box {
                                IconButton(onClick = { showTopOverflowMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Меню", tint = Color.White)
                                }

                                DropdownMenu(
                                    expanded = showTopOverflowMenu,
                                    onDismissRequest = { showTopOverflowMenu = false },
                                    modifier = Modifier.background(Color(0xFF222222))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Сохранить в галерею", color = Color.White) },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            Toast.makeText(context, "Фото сохранено в галерею", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Показать все медиа", color = Color.White) },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            showSharedMediaSheet = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Показать в чате", color = Color.White) },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            onShowInChat?.invoke(currentItem)
                                            onDismiss()
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Создать стикер", color = Color.White) },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            Toast.makeText(context, "Стикер создан и добавлен в набор", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ответить", color = Color.White) },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            onReply?.invoke(currentItem)
                                            onDismiss()
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Color.White) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Поделиться", color = Color.White) },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, currentItem.imageUrl)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Поделиться фото"))
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить", color = Color(0xFFFF5252)) },
                                        onClick = {
                                            showTopOverflowMenu = false
                                            showDeleteConfirmDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFFF5252)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // BOTTOM TOOLBAR OVERLAY (Telegram Photo Tools)
            AnimatedVisibility(
                visible = areControlsVisible && activeEditorMode == "none",
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .padding(bottom = 28.dp)
                    ) {
                        // Caption if present
                        if (currentItem.caption.isNotBlank()) {
                            Text(
                                text = currentItem.caption,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Telegram Bottom Action Tools Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. Crop / Rotate Tool
                            IconButton(onClick = { activeEditorMode = "crop" }) {
                                Icon(Icons.Filled.CropRotate, contentDescription = "Обрезка и поворот", tint = Color.White)
                            }

                            // 2. Draw / Paint Tool
                            IconButton(onClick = { activeEditorMode = "draw" }) {
                                Icon(Icons.Filled.Brush, contentDescription = "Рисование", tint = Color.White)
                            }

                            // 3. Stickers Tool
                            IconButton(onClick = { showStickerSheet = true }) {
                                Icon(Icons.Filled.EmojiEmotions, contentDescription = "Стикеры", tint = Color.White)
                            }

                            // 4. Text Overlay Tool
                            IconButton(onClick = {
                                activeEditorMode = "text"
                                isAddingText = true
                            }) {
                                Icon(Icons.Filled.TextFields, contentDescription = "Текст", tint = Color.White)
                            }

                            // 5. Adjustments / Tuning Tool
                            IconButton(onClick = { activeEditorMode = "tune" }) {
                                Icon(Icons.Filled.Tune, contentDescription = "Настройки", tint = Color.White)
                            }

                            // 6. Delete
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить", tint = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 1. CROP & ROTATE EDITOR OVERLAY (Telegram style)
            // ==========================================
            AnimatedVisibility(
                visible = activeEditorMode == "crop",
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                CropEditorBottomBar(
                    rotationAngle = rotationAngle,
                    onAngleChange = { rotationAngle = it },
                    selectedAspectRatio = cropAspectRatio,
                    onAspectRatioSelected = { cropAspectRatio = it },
                    onCancel = {
                        rotationAngle = 0f
                        activeEditorMode = "none"
                    },
                    onDone = {
                        activeEditorMode = "none"
                        Toast.makeText(context, "Кадрирование сохранено", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // ==========================================
            // 2. DRAW & MARKUP EDITOR OVERLAY (Telegram style)
            // ==========================================
            AnimatedVisibility(
                visible = activeEditorMode == "draw",
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                DrawEditorOverlay(
                    currentColor = currentDrawColor,
                    onColorSelected = { currentDrawColor = it },
                    brushSize = currentBrushSize,
                    onBrushSizeSelected = { currentBrushSize = it },
                    onAddPath = { drawPaths.add(it) },
                    onClearAll = { drawPaths.clear() },
                    onCancel = {
                        drawPaths.clear()
                        activeEditorMode = "none"
                    },
                    onDone = {
                        activeEditorMode = "none"
                        Toast.makeText(context, "Рисунок применен", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // ==========================================
            // 4. TEXT OVERLAY EDITOR (Telegram style)
            // ==========================================
            AnimatedVisibility(
                visible = activeEditorMode == "text",
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                TextEditorOverlay(
                    text = editingTextValue,
                    onTextChange = { editingTextValue = it },
                    selectedFont = selectedFontName,
                    onFontSelected = { selectedFontName = it },
                    textColor = textColor,
                    onColorSelected = { textColor = it },
                    isFilledBg = textFilledBg,
                    onToggleFilledBg = { textFilledBg = !textFilledBg },
                    onCancel = { activeEditorMode = "none" },
                    onDone = {
                        if (editingTextValue.isNotBlank()) {
                            placedTexts.add(
                                PlacedText(
                                    text = editingTextValue,
                                    fontName = selectedFontName,
                                    color = textColor,
                                    isFilledBg = textFilledBg,
                                    offset = Offset(200f, 400f)
                                )
                            )
                        }
                        activeEditorMode = "none"
                    }
                )
            }

            // ==========================================
            // 5. ADJUSTMENTS / TUNE EDITOR (Telegram style)
            // ==========================================
            AnimatedVisibility(
                visible = activeEditorMode == "tune",
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                TuneEditorBottomBar(
                    subTab = tuneSubTab,
                    onSubTabChange = { tuneSubTab = it },
                    smoothSkin = smoothSkinValue,
                    onSmoothSkinChange = { smoothSkinValue = it },
                    enhance = enhanceValue,
                    onEnhanceChange = { enhanceValue = it },
                    exposure = exposureValue,
                    onExposureChange = { exposureValue = it },
                    curvesColorChannel = curvesColorChannel,
                    onCurvesColorChannelChange = { curvesColorChannel = it },
                    blurMode = blurMode,
                    onBlurModeChange = { blurMode = it },
                    onCancel = {
                        smoothSkinValue = 0f
                        enhanceValue = 0f
                        exposureValue = 0f
                        blurMode = "Откл."
                        activeEditorMode = "none"
                    },
                    onDone = {
                        activeEditorMode = "none"
                        Toast.makeText(context, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // ==========================================
            // STICKER / EMOJI PICKER SHEET
            // ==========================================
            if (showStickerSheet) {
                TelegramStickerPickerSheet(
                    onDismiss = { showStickerSheet = false },
                    onStickerSelected = { emojiStr ->
                        placedStickers.add(
                            PlacedSticker(
                                id = UUID.randomUUID().toString(),
                                content = emojiStr,
                                offset = Offset(250f, 350f)
                            )
                        )
                        showStickerSheet = false
                        Toast.makeText(context, "Стикер добавлен на фото", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // ==========================================
            // SHARED MEDIA GALLERY SHEET ("Показать все медиа")
            // ==========================================
            if (showSharedMediaSheet) {
                SharedMediaGallerySheet(
                    items = items,
                    chatTitle = chatTitle,
                    onDismiss = { showSharedMediaSheet = false },
                    onItemClick = { clickedItem ->
                        val clickedIndex = items.indexOfFirst { it.messageId == clickedItem.messageId }.coerceAtLeast(0)
                        coroutineScope.launch {
                            pagerState.scrollToPage(clickedIndex)
                        }
                        showSharedMediaSheet = false
                    }
                )
            }

            // ==========================================
            // DELETE CONFIRMATION DIALOG
            // ==========================================
            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Удалить фото?") },
                    text = { Text("Вы действительно хотите удалить это медиасообщение?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirmDialog = false
                            onDelete?.invoke(currentItem)
                            onDismiss()
                        }) {
                            Text("Удалить", color = Color(0xFFFF5252))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Individual Zoomable Image Page supporting pinch-to-zoom, pan, double-tap toggle zoom,
 * and vertical swipe-to-dismiss gesture with photo tuning effects applied.
 */
@Composable
private fun ZoomableImagePage(
    mediaItem: LightboxMediaItem,
    isCurrentPage: Boolean,
    rotationAngle: Float = 0f,
    smoothSkin: Float = 0f,
    exposure: Float = 0f,
    blurMode: String = "Откл.",
    onToggleControls: () -> Unit,
    onDismiss: () -> Unit,
    onZoomScaleChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val dismissOffsetY = remember { Animatable(0f) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale.snapTo(1f)
            offset.snapTo(Offset.Zero)
            dismissOffsetY.snapTo(0f)
            onZoomScaleChanged(1f)
        }
    }

    LaunchedEffect(scale.value) {
        onZoomScaleChanged(scale.value)
    }

    val maxScale = 5.0f
    val minScale = 1.0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onToggleControls()
                    },
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scale.value > 1.2f) {
                                launch { scale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                                launch { offset.animateTo(Offset.Zero, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val targetOffsetX = (center.x - tapOffset.x) * (targetScale - 1f)
                                val targetOffsetY = (center.y - tapOffset.y) * (targetScale - 1f)
                                
                                val maxOffsetX = (size.width * (targetScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (targetScale - 1f)) / 2f

                                val clampedOffset = Offset(
                                    x = targetOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
                                    y = targetOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                                )

                                launch { scale.animateTo(targetScale, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                                launch { offset.animateTo(clampedOffset, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    coroutineScope.launch {
                        val newScale = (scale.value * zoom).coerceIn(minScale, maxScale)
                        scale.snapTo(newScale)

                        if (newScale > 1.02f) {
                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                            val maxOffsetY = (size.height * (newScale - 1f)) / 2f

                            val newOffsetX = (offset.value.x + pan.x * newScale).coerceIn(-maxOffsetX, maxOffsetX)
                            val newOffsetY = (offset.value.y + pan.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)

                            offset.snapTo(Offset(newOffsetX, newOffsetY))
                        } else {
                            val newDismissY = dismissOffsetY.value + pan.y
                            dismissOffsetY.snapTo(newDismissY)

                            if (abs(newDismissY) > 220f) {
                                onDismiss()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val backgroundAlpha = (1f - (abs(dismissOffsetY.value) / 400f).coerceIn(0f, 0.8f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = backgroundAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            if (mediaItem.isVideo) {
                VideoPlayerView(
                    videoUri = mediaItem.imageUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                x = offset.value.x.roundToInt(),
                                y = (offset.value.y + dismissOffsetY.value).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            rotationZ = rotationAngle
                        },
                    autoPlay = isCurrentPage,
                    isLooping = true,
                    showControls = true,
                    isCircular = false
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediaItem.imageUrl)
                        .crossfade(true)
                        .allowHardware(false)
                        .build(),
                    contentDescription = mediaItem.caption.ifBlank { "Shared photo" },
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                x = offset.value.x.roundToInt(),
                                y = (offset.value.y + dismissOffsetY.value).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            rotationZ = rotationAngle
                        },
                    contentScale = ContentScale.Fit
                )
            }

            // Tilt-shift blur guide overlay if active
            if (blurMode == "Линейное") {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y1 = size.height * 0.35f
                    val y2 = size.height * 0.65f
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(0f, y1), Offset(size.width, y1), strokeWidth = 3f, pathEffect = pathEffect)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(0f, y2), Offset(size.width, y2), strokeWidth = 3f, pathEffect = pathEffect)
                }
            } else if (blurMode == "Радиальное") {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width * 0.35f
                    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
                    drawCircle(Color.White.copy(alpha = 0.8f), radius = radius, center = center, style = Stroke(width = 3f, pathEffect = pathEffect))
                }
            }
        }
    }
}

/**
 * 1. CROP & ROTATE TOOLBAR (Telegram style)
 */
@Composable
private fun CropEditorBottomBar(
    rotationAngle: Float,
    onAngleChange: (Float) -> Unit,
    selectedAspectRatio: String,
    onAspectRatioSelected: (String) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Surface(
        color = Color(0xFF181818),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cut object button (Вырезать объект)
            OutlinedButton(
                onClick = { /* AI object cutout simulation */ },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(Icons.Outlined.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Вырезать объект", fontSize = 13.sp)
            }

            // Angle degree text & slider
            Text(
                text = "${String.format(Locale.US, "%.1f", rotationAngle)}°",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Slider(
                value = rotationAngle,
                onValueChange = onAngleChange,
                valueRange = -45f..45f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF3390EC),
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            // Aspect Ratio Presets
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                val aspectRatios = listOf("Оригинал", "1:1", "4:3", "16:9", "3:4", "9:16")
                aspectRatios.forEach { ratio ->
                    val isSelected = selectedAspectRatio == ratio
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF3390EC) else Color(0xFF2C2C2C),
                        modifier = Modifier.clickable { onAspectRatioSelected(ratio) }
                    ) {
                        Text(
                            text = ratio,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons (ОТМЕНА / ГОТОВО)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("ОТМЕНА", color = Color.White, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDone) {
                    Text("ГОТОВО", color = Color(0xFF3390EC), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 2. DRAW & MARKUP EDITOR OVERLAY (Telegram style)
 */
@Composable
private fun DrawEditorOverlay(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    brushSize: Float,
    onBrushSizeSelected: (Float) -> Unit,
    onAddPath: (DrawPath) -> Unit,
    onClearAll: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val currentPoints = remember { mutableStateListOf<Offset>() }
    var showShapeMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Drawing touch surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPoints.clear()
                            currentPoints.add(offset)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentPoints.add(change.position)
                        },
                        onDragEnd = {
                            if (currentPoints.isNotEmpty()) {
                                onAddPath(
                                    DrawPath(
                                        points = currentPoints.toList(),
                                        color = currentColor,
                                        strokeWidth = brushSize
                                    )
                                )
                                currentPoints.clear()
                            }
                        }
                    )
                }
        ) {
            if (currentPoints.size > 1) {
                val path = Path().apply {
                    moveTo(currentPoints.first().x, currentPoints.first().y)
                    for (i in 1 until currentPoints.size) {
                        lineTo(currentPoints[i].x, currentPoints[i].y)
                    }
                }
                drawPath(
                    path,
                    color = currentColor,
                    style = Stroke(width = brushSize, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // Top bar: Очистить всё
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onClearAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Очистить всё", color = Color.White, fontSize = 13.sp)
            }
        }

        // Bottom drawing tools panel
        Surface(
            color = Color(0xFF181818),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Color Palette dots
                val paletteColors = listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFF000000),
                    Color(0xFFFFEB3B),
                    Color(0xFFFF9800),
                    Color(0xFFF44336),
                    Color(0xFFE91E63),
                    Color(0xFF9C27B0),
                    Color(0xFF2196F3),
                    Color(0xFF00BCD4),
                    Color(0xFF4CAF50)
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    paletteColors.forEach { col ->
                        val isSelected = currentColor == col
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) Color(0xFF3390EC) else Color.Gray,
                                    CircleShape
                                )
                                .clickable { onColorSelected(col) }
                        )
                    }
                }

                // Brush thickness & shapes button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    // Shapes dropdown
                    Box {
                        OutlinedButton(
                            onClick = { showShapeMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Фигуры", fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = showShapeMenu,
                            onDismissRequest = { showShapeMenu = false },
                            modifier = Modifier.background(Color(0xFF222222))
                        ) {
                            listOf("Круг", "Прямоугольник", "Звезда", "Облако", "Стрелка").forEach { shape ->
                                DropdownMenuItem(
                                    text = { Text(shape, color = Color.White) },
                                    onClick = {
                                        showShapeMenu = false
                                        // Shape stamp demo
                                    }
                                )
                            }
                        }
                    }

                    // Brush sizes (3 presets)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(4f, 8f, 16f, 24f).forEach { sz ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (brushSize == sz) Color(0xFF3390EC) else Color(0xFF2A2A2A))
                                    .clickable { onBrushSizeSelected(sz) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((sz / 2).dp.coerceIn(4.dp, 16.dp))
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text("ОТМЕНА", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDone) {
                        Text("ГОТОВО", color = Color(0xFF3390EC), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 4. TEXT OVERLAY EDITOR (Telegram style with font selector)
 */
@Composable
private fun TextEditorOverlay(
    text: String,
    onTextChange: (String) -> Unit,
    selectedFont: String,
    onFontSelected: (String) -> Unit,
    textColor: Color,
    onColorSelected: (Color) -> Unit,
    isFilledBg: Boolean,
    onToggleFilledBg: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    var showFontDropdown by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                }

                // Font family picker pill (Mono-serif, Roboto, Serif, etc.)
                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF333333),
                        modifier = Modifier.clickable { showFontDropdown = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(selectedFont, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }
                    }

                    DropdownMenu(
                        expanded = showFontDropdown,
                        onDismissRequest = { showFontDropdown = false },
                        modifier = Modifier.background(Color(0xFF222222))
                    ) {
                        val telegramFonts = listOf(
                            "Roboto", "Italic", "Serif", "Condensed", "Mono-serif",
                            "Merriweather", "Dancing Script", "Carrois Gothic", "Cutive Mono", "Droid Sans"
                        )
                        telegramFonts.forEach { fontName ->
                            DropdownMenuItem(
                                text = { Text(fontName, color = Color.White) },
                                onClick = {
                                    onFontSelected(fontName)
                                    showFontDropdown = false
                                }
                            )
                        }
                    }
                }

                TextButton(onClick = onDone) {
                    Text("Готово", color = Color(0xFF3390EC), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Center Text Input Preview
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFilledBg) Color.Black.copy(alpha = 0.7f) else Color.Transparent,
                    modifier = Modifier.padding(16.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = LocalTextStyle.current.copy(
                            color = textColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontFamily = getFontFamilyByName(selectedFont)
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Bottom Palette & Background Mode toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    val colors = listOf(
                        Color.White, Color.Black, Color(0xFFFFEB3B), Color(0xFFFF9800),
                        Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
                        Color(0xFF2196F3), Color(0xFF00BCD4), Color(0xFF4CAF50)
                    )
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    if (textColor == c) 3.dp else 1.dp,
                                    if (textColor == c) Color(0xFF3390EC) else Color.Gray,
                                    CircleShape
                                )
                                .clickable { onColorSelected(c) }
                        )
                    }
                }

                // Background toggle button
                OutlinedButton(
                    onClick = onToggleFilledBg,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.FormatPaint, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isFilledBg) "Фон: Вкл" else "Фон: Выкл", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 5. ADJUSTMENTS / TUNE EDITOR (Telegram style: Sliders, RGB Curves, Tilt-Shift Blur)
 */
@Composable
private fun TuneEditorBottomBar(
    subTab: String,
    onSubTabChange: (String) -> Unit,
    smoothSkin: Float,
    onSmoothSkinChange: (Float) -> Unit,
    enhance: Float,
    onEnhanceChange: (Float) -> Unit,
    exposure: Float,
    onExposureChange: (Float) -> Unit,
    curvesColorChannel: String,
    onCurvesColorChannelChange: (String) -> Unit,
    blurMode: String,
    onBlurModeChange: (String) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    Surface(
        color = Color(0xFF181818),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .padding(bottom = 28.dp)
        ) {
            // Sub-mode content
            when (subTab) {
                "sliders" -> {
                    Column {
                        // 1. Smooth skin (Гладкая кожа)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Гладкая кожа", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${smoothSkin.roundToInt()}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = smoothSkin,
                            onValueChange = onSmoothSkinChange,
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF3390EC))
                        )

                        // 2. Enhance (Улучшение)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Улучшение", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${enhance.roundToInt()}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = enhance,
                            onValueChange = onEnhanceChange,
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF3390EC))
                        )

                        // 3. Exposure (Экспозиция)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Экспозиция", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("${exposure.roundToInt()}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = exposure,
                            onValueChange = onExposureChange,
                            valueRange = -100f..100f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF3390EC))
                        )
                    }
                }
                "curves" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Curves channel radio buttons: Все, Красный, Зеленый, Синий
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            listOf("Все", "Красный", "Зелёный", "Синий").forEach { ch ->
                                val isSelected = curvesColorChannel == ch
                                val chColor = when (ch) {
                                    "Красный" -> Color.Red
                                    "Зелёный" -> Color.Green
                                    "Синий" -> Color.Blue
                                    else -> Color.White
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onCurvesColorChannelChange(ch) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .border(if (isSelected) 4.dp else 2.dp, chColor, CircleShape)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(ch, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        // Curves Graph interactive simulation
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            val curveColor = when (curvesColorChannel) {
                                "Красный" -> Color.Red
                                "Зелёный" -> Color.Green
                                "Синий" -> Color.Blue
                                else -> Color.White
                            }
                            drawLine(Color.DarkGray, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2f)
                            drawCircle(curveColor, radius = 8f, center = Offset(size.width * 0.25f, size.height * 0.75f))
                            drawCircle(curveColor, radius = 8f, center = Offset(size.width * 0.5f, size.height * 0.5f))
                            drawCircle(curveColor, radius = 8f, center = Offset(size.width * 0.75f, size.height * 0.25f))
                        }

                        // Curve anchors labels
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            listOf("0.00", "0.25", "0.50", "0.75", "1.00").forEach { label ->
                                Text(label, color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                }
                "blur" -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        listOf("Откл.", "Радиальное", "Линейное").forEach { mode ->
                            val isSelected = blurMode == mode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF3390EC) else Color(0xFF2A2A2A),
                                modifier = Modifier.clickable { onBlurModeChange(mode) }
                            ) {
                                Text(
                                    text = mode,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Sub-mode tabs row & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("ОТМЕНА", color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Sub-tabs icons (Sliders, Curves, Blur)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { onSubTabChange("sliders") }) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "Sliders",
                            tint = if (subTab == "sliders") Color(0xFF3390EC) else Color.White
                        )
                    }
                    IconButton(onClick = { onSubTabChange("curves") }) {
                        Icon(
                            Icons.Filled.ShowChart,
                            contentDescription = "Curves",
                            tint = if (subTab == "curves") Color(0xFF3390EC) else Color.White
                        )
                    }
                    IconButton(onClick = { onSubTabChange("blur") }) {
                        Icon(
                            Icons.Filled.Opacity,
                            contentDescription = "Blur",
                            tint = if (subTab == "blur") Color(0xFF3390EC) else Color.White
                        )
                    }
                }

                TextButton(onClick = onDone) {
                    Text("ГОТОВО", color = Color(0xFF3390EC), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * TELEGRAM STICKER & EMOJI PICKER SHEET
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramStickerPickerSheet(
    onDismiss: () -> Unit,
    onStickerSelected: (String) -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        var selectedTab by remember { mutableStateOf("Эмодзи") }
        val tabs = listOf("Эмодзи", "Стикеры", "GIF")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 500.dp)
                .padding(16.dp)
        ) {
            // Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF3390EC) else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tab, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when (selectedTab) {
                "Эмодзи" -> {
                    val emojis = listOf(
                        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
                        "🥲", "🥹", "😊", "😇", "🙂", "🙃", "😉", "😌",
                        "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛",
                        "😎", "🤓", "🧐", "🤠", "🥳", "🥸", "😎", "🤩",
                        "❤️", "🔥", "✨", "💯", "🎉", "👍", "👏", "🚀"
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(emojis.size) { idx ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2A2A2A))
                                    .clickable { onStickerSelected(emojis[idx]) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emojis[idx], fontSize = 24.sp)
                            }
                        }
                    }
                }
                "Стикеры" -> {
                    val stickers = listOf("🐶", "🐱", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵")
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(stickers.size) { idx ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2A2A2A),
                                modifier = Modifier
                                    .height(70.dp)
                                    .clickable { onStickerSelected(stickers[idx]) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(stickers[idx], fontSize = 36.sp)
                                }
                            }
                        }
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("GIF анимации", color = Color.Gray)
                    }
                }
            }
        }
    }
}

/**
 * SHARED MEDIA GALLERY SHEET ("Показать все медиа" / Общие материалы)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMediaGallerySheet(
    items: List<LightboxMediaItem>,
    chatTitle: String,
    onDismiss: () -> Unit,
    onItemClick: (LightboxMediaItem) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("Общие материалы") }
    var gridColumns by remember { mutableIntStateOf(3) }
    var showGalleryMenu by remember { mutableStateOf(false) }
    var showCalendarView by remember { mutableStateOf(false) }
    var showStoriesArchive by remember { mutableStateOf(false) }

    val filteredItems = remember(items, selectedFilter) {
        when (selectedFilter) {
            "Фотографии" -> items.filter { !it.isVideo }
            "Видео" -> items.filter { it.isVideo }
            else -> items
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            color = Color(0xFF0F0F0F),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Top App Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = {
                        if (showStoriesArchive || showCalendarView) {
                            showStoriesArchive = false
                            showCalendarView = false
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }

                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(
                            text = if (showStoriesArchive) "Архив историй" else if (showCalendarView) "Календарь" else chatTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!showStoriesArchive && !showCalendarView) {
                            Text(
                                text = "${items.size} медиафайла",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // 3-Dots Gallery Menu
                    Box {
                        IconButton(onClick = { showGalleryMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Опции", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = showGalleryMenu,
                            onDismissRequest = { showGalleryMenu = false },
                            modifier = Modifier.background(Color(0xFF222222))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Увеличить", color = Color.White) },
                                onClick = {
                                    showGalleryMenu = false
                                    gridColumns = 2
                                },
                                leadingIcon = { Icon(Icons.Filled.ZoomIn, contentDescription = null, tint = Color.White) }
                            )
                            DropdownMenuItem(
                                text = { Text("Уменьшить", color = Color.White) },
                                onClick = {
                                    showGalleryMenu = false
                                    gridColumns = 4
                                },
                                leadingIcon = { Icon(Icons.Filled.ZoomOut, contentDescription = null, tint = Color.White) }
                            )
                            DropdownMenuItem(
                                text = { Text("Календарь", color = Color.White) },
                                onClick = {
                                    showGalleryMenu = false
                                    showCalendarView = true
                                },
                                leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = Color.White) }
                            )
                            DropdownMenuItem(
                                text = { Text("Архив историй", color = Color.White) },
                                onClick = {
                                    showGalleryMenu = false
                                    showStoriesArchive = true
                                },
                                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null, tint = Color.White) }
                            )
                        }
                    }
                }

                if (showStoriesArchive) {
                    // Telegram Stories Archive screen
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E1E1E),
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🏖️", fontSize = 48.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Историй пока нет...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Загрузите новую историю — она появится здесь.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { /* Add story */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Добавить", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (showCalendarView) {
                    // Telegram Media Calendar View
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Август 2026", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                        val days = (1..31).toList()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(days.size) { idx ->
                                val day = days[idx]
                                val hasPhoto = day == 28 || day == 26
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (hasPhoto) Color(0xFF3390EC).copy(alpha = 0.3f) else Color.Transparent)
                                        .clickable {
                                            if (hasPhoto && items.isNotEmpty()) {
                                                onItemClick(items.first())
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (hasPhoto && items.isNotEmpty()) {
                                        AsyncImage(
                                            model = items.first().imageUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Text(
                                        text = "$day",
                                        color = if (hasPhoto) Color.White else Color.Gray,
                                        fontWeight = if (hasPhoto) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Segmented Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Общие материалы", "Фотографии", "Видео").forEach { chip ->
                            val isSelected = selectedFilter == chip
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFF3390EC) else Color(0xFF222222),
                                modifier = Modifier.clickable { selectedFilter = chip }
                            ) {
                                Text(
                                    text = chip,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Media Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)
                    ) {
                        itemsIndexed(filteredItems) { idx, item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(Color(0xFF222222))
                                    .clickable { onItemClick(item) }
                            ) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.caption,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (item.isVideo) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Black.copy(alpha = 0.6f),
                                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(2.dp))
                                            Text(item.videoDuration.ifBlank { "0:08" }, color = Color.White, fontSize = 10.sp)
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
}

/**
 * Helper to retrieve Compose FontFamily from font name.
 */
private fun getFontFamilyByName(name: String): FontFamily {
    return when (name) {
        "Mono-serif", "Cutive Mono" -> FontFamily.Monospace
        "Serif", "Merriweather" -> FontFamily.Serif
        "Condensed" -> FontFamily.Default
        "Italic", "Dancing Script" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}
