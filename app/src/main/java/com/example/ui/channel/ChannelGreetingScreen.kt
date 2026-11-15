@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.ui.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.AppViewModel
import com.example.ui.components.TelegramEmojiPickerBottomSheet
import com.example.ui.popBackStackSafe
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val GreetingBgBase = Color(0xFF0A0D12)
private val GreetingPillSurface = Color(0xFF1A2029)
private val GreetingBubbleSurface = Color(0xFF1B212C)
private val GreetingAccentPurple = Color(0xFF9C5BFF)
private val GreetingSenderOrange = Color(0xFFED9E45)
private val GreetingMenuSurface = Color(0xFF212834)
private val GreetingDialogSurface = Color(0xFF1E242E)

/**
 * Telegram-style chat-based Channel Greeting Settings screen («Приветствие»).
 * Allows administrators to compose, edit, copy, and delete greeting messages
 * shown automatically to new participants/subscribers when they join the channel.
 */
@Composable
fun ChannelGreetingScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId }
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)
    val greetingMessages = ChannelCustomizationManager.getEffectiveGreetingMessages(customization)

    var inputText by remember(chatId) { mutableStateOf("") }
    var pendingPhotoUri by remember(chatId) { mutableStateOf<String?>(null) }
    var editingMessage by remember(chatId) { mutableStateOf<ChannelGreetingMessage?>(null) }
    var activeMenuMessageId by remember(chatId) { mutableStateOf<String?>(null) }
    var messagePendingDelete by remember(chatId) { mutableStateOf<ChannelGreetingMessage?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingPhotoUri = uri.toString()
        }
    }

    LaunchedEffect(greetingMessages.size) {
        if (greetingMessages.isNotEmpty()) {
            val lastIndex = greetingMessages.size
            listState.animateScrollToItem(lastIndex.coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GreetingBgBase)
    ) {
        TelegramDoodleBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Floating Top Header Bar: Circular Back + Centered "Приветствие" pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelegramCircularIconButton(
                    onClick = { navController.popBackStackSafe() },
                    contentDescription = "Назад",
                    testTag = "greeting_back_button"
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = TelegramAdminPillBg.copy(alpha = 0.94f),
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = "Приветствие",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                    )
                }

                // Balancing spacer so the title pill stays centered
                Spacer(modifier = Modifier.size(42.dp))
            }

            // Messages + Hint Pills Area (anchored near bottom like Telegram chat view)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
            ) {
                item(key = "greeting_hints_header") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GreetingHintPill(
                            text = "Новые участники увидят эти сообщения, когда присоединятся к чату"
                        )
                        GreetingHintPill(
                            text = if (greetingMessages.isNotEmpty()) {
                                "Чтобы удалить сообщение, нажмите на него"
                            } else {
                                "Напишите сообщение ниже, чтобы настроить приветствие"
                            }
                        )
                    }
                }

                items(greetingMessages, key = { it.id }) { msg ->
                    val isMenuExpanded = activeMenuMessageId == msg.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomEnd = 18.dp,
                                bottomStart = 6.dp
                            ),
                            color = GreetingBubbleSurface,
                            modifier = Modifier
                                .widthIn(min = 160.dp, max = 315.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomEnd = 18.dp,
                                        bottomStart = 6.dp
                                    )
                                )
                                .combinedClickable(
                                    onClick = { activeMenuMessageId = msg.id },
                                    onLongClick = { activeMenuMessageId = msg.id }
                                )
                                .testTag("greeting_message_bubble_${msg.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = chat?.title ?: "Канал",
                                        color = GreetingSenderOrange,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val emojiStatus = customization.emojiStatus
                                    if (!emojiStatus.isNullOrBlank()) {
                                        Text(text = emojiStatus, fontSize = 13.sp)
                                    }
                                }

                                if (!msg.mediaPath.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(msg.mediaPath)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Вложение приветствия",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 120.dp, max = 210.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }

                                if (msg.text.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = msg.text,
                                        color = Color(0xFFECEFF4),
                                        fontSize = 16.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { activeMenuMessageId = null },
                            containerColor = GreetingMenuSurface,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Копировать",
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Копировать",
                                        tint = Color(0xFFCFD6E2),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.testTag("greeting_menu_copy"),
                                onClick = {
                                    activeMenuMessageId = null
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Greeting", msg.text))
                                    Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Изменить",
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Изменить",
                                        tint = Color(0xFFCFD6E2),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.testTag("greeting_menu_edit"),
                                onClick = {
                                    activeMenuMessageId = null
                                    if (msg.id.startsWith("legacy_")) {
                                        // Materialize legacy greeting before editing
                                        val created = ChannelCustomizationManager.addGreetingMessage(
                                            chatId = chatId,
                                            text = msg.text
                                        )
                                        editingMessage = created
                                        inputText = created.text
                                    } else {
                                        editingMessage = msg
                                        inputText = msg.text
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Удалить",
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Удалить",
                                        tint = Color(0xFFCFD6E2),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier.testTag("greeting_menu_delete"),
                                onClick = {
                                    activeMenuMessageId = null
                                    messagePendingDelete = msg
                                }
                            )
                        }
                    }
                }
            }

            // Editing indicator bar
            val currentEditing = editingMessage
            if (currentEditing != null) {
                Surface(
                    color = GreetingPillSurface.copy(alpha = 0.96f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = TelegramAdminPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Редактирование сообщения",
                                color = TelegramAdminPurple,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = currentEditing.text,
                                color = Color(0xFFB5BCC9),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                editingMessage = null
                                inputText = ""
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("greeting_cancel_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Отменить редактирование",
                                tint = Color(0xFF9EA6B5)
                            )
                        }
                    }
                }
            }

            // Pending attachment banner
            val attachmentUri = pendingPhotoUri
            if (attachmentUri != null) {
                Surface(
                    color = GreetingPillSurface.copy(alpha = 0.96f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(attachmentUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Выбранное фото",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Фотография для приветствия",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { pendingPhotoUri = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Убрать фото",
                                tint = Color(0xFF9EA6B5)
                            )
                        }
                    }
                }
            }

            // Bottom Telegram Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GreetingPillSurface.copy(alpha = 0.96f),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Emoji/GIF icon button
                        IconButton(
                            onClick = { showEmojiPicker = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("greeting_emoji_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SentimentSatisfiedAlt,
                                contentDescription = "Выбрать эмодзи",
                                tint = Color(0xFF9EA6B5),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Text input ("Сообщение")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Сообщение",
                                    color = Color(0xFF7B8392),
                                    fontSize = 16.sp
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp
                                ),
                                cursorBrush = SolidColor(TelegramAdminPurple),
                                maxLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("greeting_message_input")
                            )
                        }

                        // Attachment paperclip (only shown when not actively editing text-only)
                        if (editingMessage == null) {
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("greeting_attach_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AttachFile,
                                    contentDescription = "Прикрепить фото",
                                    tint = Color(0xFF9EA6B5),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .rotate(45f)
                                )
                            }
                        }
                    }
                }

                // Right circular purple action button (Send / Save edit)
                Surface(
                    onClick = {
                        val editing = editingMessage
                        if (editing != null) {
                            if (inputText.isBlank()) {
                                Toast.makeText(context, "Введите текст сообщения", Toast.LENGTH_SHORT).show()
                            } else {
                                ChannelCustomizationManager.updateGreetingMessage(
                                    chatId = chatId,
                                    messageId = editing.id,
                                    newText = inputText
                                )
                                editingMessage = null
                                inputText = ""
                                Toast.makeText(context, "Сообщение обновлено", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (inputText.isNotBlank() || pendingPhotoUri != null) {
                                ChannelCustomizationManager.addGreetingMessage(
                                    chatId = chatId,
                                    text = inputText,
                                    mediaPath = pendingPhotoUri,
                                    mediaType = if (pendingPhotoUri != null) "image" else null
                                )
                                inputText = ""
                                pendingPhotoUri = null
                            } else {
                                Toast.makeText(context, "Введите сообщение приветствия", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = CircleShape,
                    color = GreetingAccentPurple,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("greeting_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (editingMessage != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (editingMessage != null) "Сохранить изменения" else "Отправить приветствие",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showEmojiPicker) {
        TelegramEmojiPickerBottomSheet(
            onDismissRequest = { showEmojiPicker = false },
            onEmojiSelected = { item ->
                inputText += item.emoji
            },
            title = "Эмодзи"
        )
    }

    val pendingDelete = messagePendingDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { messagePendingDelete = null },
            containerColor = GreetingDialogSurface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Удалить сообщение",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            },
            text = {
                Text(
                    text = "Вы точно хотите удалить это сообщение?",
                    color = Color(0xFFD0D6E0),
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { messagePendingDelete = null },
                    modifier = Modifier.testTag("greeting_delete_cancel")
                ) {
                    Text(
                        text = "Отмена",
                        color = Color(0xFF9C8CE8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pendingDelete.id.startsWith("legacy_")) {
                            ChannelCustomizationManager.updateAutoGreeting(chatId, false, "")
                        } else {
                            ChannelCustomizationManager.deleteGreetingMessage(chatId, pendingDelete.id)
                        }
                        if (editingMessage?.id == pendingDelete.id) {
                            editingMessage = null
                            inputText = ""
                        }
                        messagePendingDelete = null
                        Toast.makeText(context, "Сообщение удалено", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("greeting_delete_confirm")
                ) {
                    Text(
                        text = "Удалить",
                        color = Color(0xFFE56363),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        )
    }
}

@Composable
private fun GreetingHintPill(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = GreetingPillSurface.copy(alpha = 0.88f)
    ) {
        Text(
            text = text,
            color = Color(0xFFECEFF4),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * Subtle dark Telegram-style line-art doodle pattern rendered smoothly via Canvas.
 */
@Composable
private fun TelegramDoodleBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeColor = Color.White.copy(alpha = 0.045f)
        val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        val cell = 110.dp.toPx()
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = c * cell + (if (r % 2 == 1) cell * 0.45f else cell * 0.15f)
                val cy = r * cell + cell * 0.35f
                when ((r * 3 + c) % 5) {
                    0 -> {
                        // 5-point star outline
                        val outerR = 14.dp.toPx()
                        val innerR = 6.dp.toPx()
                        val path = Path()
                        for (i in 0 until 10) {
                            val angle = (i * 36.0 - 90.0) * PI / 180.0
                            val rad = if (i % 2 == 0) outerR else innerR
                            val px = cx + (cos(angle) * rad).toFloat()
                            val py = cy + (sin(angle) * rad).toFloat()
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        drawPath(path, color = strokeColor, style = stroke)
                    }
                    1 -> {
                        // Camera / photo frame outline
                        val w = 26.dp.toPx()
                        val h = 20.dp.toPx()
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = Offset(cx - w / 2, cy - h / 2),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = stroke
                        )
                        drawCircle(
                            color = strokeColor,
                            radius = 5.dp.toPx(),
                            center = Offset(cx, cy),
                            style = stroke
                        )
                    }
                    2 -> {
                        // Chat bubble outline
                        val w = 24.dp.toPx()
                        val h = 18.dp.toPx()
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = Offset(cx - w / 2, cy - h / 2),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = stroke
                        )
                        drawCircle(
                            color = strokeColor,
                            radius = 1.6.dp.toPx(),
                            center = Offset(cx - 5.dp.toPx(), cy),
                            style = stroke
                        )
                        drawCircle(
                            color = strokeColor,
                            radius = 1.6.dp.toPx(),
                            center = Offset(cx, cy),
                            style = stroke
                        )
                        drawCircle(
                            color = strokeColor,
                            radius = 1.6.dp.toPx(),
                            center = Offset(cx + 5.dp.toPx(), cy),
                            style = stroke
                        )
                    }
                    3 -> {
                        // Heart outline
                        val s = 12.dp.toPx()
                        val path = Path().apply {
                            moveTo(cx, cy + s)
                            cubicTo(cx - s * 1.4f, cy + s * 0.2f, cx - s * 1.1f, cy - s * 0.9f, cx, cy - s * 0.25f)
                            cubicTo(cx + s * 1.1f, cy - s * 0.9f, cx + s * 1.4f, cy + s * 0.2f, cx, cy + s)
                        }
                        drawPath(path, color = strokeColor, style = stroke)
                    }
                    4 -> {
                        // Shell / wave spiral outline
                        drawCircle(
                            color = strokeColor,
                            radius = 12.dp.toPx(),
                            center = Offset(cx, cy),
                            style = stroke
                        )
                        drawCircle(
                            color = strokeColor,
                            radius = 5.dp.toPx(),
                            center = Offset(cx + 2.dp.toPx(), cy - 1.dp.toPx()),
                            style = stroke
                        )
                    }
                }
            }
        }
    }
}
