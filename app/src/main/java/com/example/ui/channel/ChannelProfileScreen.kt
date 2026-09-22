package com.example.ui.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import com.example.data.AvatarStorageManager
import com.example.data.EntityType
import com.example.ui.AppViewModel
import com.example.ui.components.TelegramEmojiPickerBottomSheet
import com.example.ui.navigateSafe
import com.example.ui.popBackStackSafe
import com.example.utils.QrCodeGenerator

/**
 * ChannelProfileScreen
 * Matches Telegram's exact Channel Profile UX/UI:
 * - Circular avatar with initials or custom photo
 * - Channel Title with interactive custom status emoji badge
 * - "публичный канал" / subscriber count subtitle
 * - 4 Circular Action Buttons: Live Stream, Звук, Обсуждение, Новая история
 * - Info Card: Description with "ещё" expandable text & Ссылка-приглашение with QR Code modal
 * - Settings Section Card: Подписчики, Администраторы, Настройки канала
 * - Content tabs & "Общие материалы" media previews
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelProfileScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId }
    val isChannel = chat?.isChannel == true

    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)
    val palette = TelegramProfilePalettes.getPalette(customization.profileColorId)

    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val isAdmin = members.find { it.userId == (activeAccount?.id ?: "me") }?.isAdmin ?: true
    val pollsMap by ChannelCustomizationManager.getPollsFlow(chatId).collectAsState()
    val polls = pollsMap[chatId] ?: emptyList()

    var isDescExpanded by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAutoDeleteWheel by remember { mutableStateOf(false) }
    var showStoriesArchive by remember { mutableStateOf(false) }
    var showSendGiftDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val channelAvatar = remember(chatId, customization.avatarUrl) {
        AvatarStorageManager.getAvatar(
            context,
            if (chat?.isGroup == true) EntityType.GROUP else EntityType.CHANNEL,
            chatId,
            customization.avatarUrl
        )
    }
    val hasCustomChannelAvatar = remember(chatId, channelAvatar) {
        AvatarStorageManager.hasCustomAvatar(
            context,
            if (chat?.isGroup == true) EntityType.GROUP else EntityType.CHANNEL,
            chatId
        )
    }
    val channelPhotoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val entityType = if (chat?.isGroup == true) EntityType.GROUP else EntityType.CHANNEL
                val savedPath = AvatarStorageManager.saveAvatarFromUri(context, uri, entityType, chatId)
                ChannelCustomizationManager.updateAvatar(chatId, savedPath)
                Toast.makeText(context, "Фото успешно обновлено и сохранено!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var showMuteDurationPicker by remember { mutableStateOf(false) }
    var isSoundMuted by remember { mutableStateOf(false) }
    var deleteForAllSubscribers by remember { mutableStateOf(true) }
    var selectedMediaTab by remember { mutableIntStateOf(0) }
    var showDedicatedGalleryDialog by remember { mutableStateOf(false) }

    val messagesWithSender by viewModel.getMessages(chatId).collectAsStateWithLifecycle(initialValue = emptyList())
    val channelMediaItems = remember(messagesWithSender, chatId, chat?.title) {
        extractChannelSharedMedia(messagesWithSender, chatId, chat?.title ?: "Канал")
    }
    val channelDocs = remember(messagesWithSender, chatId, chat?.title) {
        extractChannelDocuments(messagesWithSender, chatId, chat?.title ?: "Канал")
    }
    val channelLinks = remember(messagesWithSender, chatId, chat?.title) {
        extractChannelLinks(messagesWithSender, chatId, chat?.title ?: "Канал")
    }
    val channelAudios = remember(messagesWithSender, chatId, chat?.title) {
        extractChannelAudios(messagesWithSender, chatId, chat?.title ?: "Канал")
    }

    val mediaTabs = listOf(
        "Медиа (${channelMediaItems.size})",
        "Файлы (${channelDocs.size})",
        "Голосовые (${channelAudios.size})",
        "Ссылки (${channelLinks.size})",
        "Опросы (${polls.size})"
    )

    if (showDedicatedGalleryDialog) {
        ChannelDedicatedGalleryDialog(
            chatId = chatId,
            channelTitle = chat?.title ?: "Канал",
            mediaItems = channelMediaItems,
            documents = channelDocs,
            links = channelLinks,
            audios = channelAudios,
            onDismiss = { showDedicatedGalleryDialog = false }
        )
    }

    // QR Code Dialog
    if (showQrDialog) {
        val qrBitmap = remember(customization.inviteLink) {
            QrCodeGenerator.generateQrCode("https://${customization.inviteLink}", 512)
        }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            containerColor = Color(0xFF161E2E),
            title = {
                Text(
                    "QR-код канала",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier.size(220.dp).padding(12.dp)
                    ) {
                        qrBitmap?.let { bitmap ->
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "https://${customization.inviteLink}",
                        color = Color(0xFF2AABEE),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Invite Link", "https://${customization.inviteLink}"))
                        Toast.makeText(context, "Ссылка скопирована!", Toast.LENGTH_SHORT).show()
                        showQrDialog = false
                    }
                ) {
                    Text("Копировать", color = Color(0xFF2AABEE), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Закрыть", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Live Status Emoji Picker
    if (showEmojiPicker) {
        TelegramEmojiPickerBottomSheet(
            onDismissRequest = { showEmojiPicker = false },
            selectedEmoji = customization.emojiStatus,
            onEmojiSelected = { emojiItem ->
                ChannelCustomizationManager.updateEmojiStatus(chatId, emojiItem.emoji, isAnimated = true)
                Toast.makeText(context, "Статус обновлен: ${emojiItem.emoji}", Toast.LENGTH_SHORT).show()
            },
            title = "Выберите статус канала"
        )
    }

    // --- DELETE CHANNEL CONFIRMATION DIALOG ---
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF191B28),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Удалить канал?",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Вы действительно хотите удалить канал «${chat?.title ?: "Канал"}»? Это действие необратимо: все сообщения, медиафайлы и подписчики будут удалены.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { deleteForAllSubscribers = !deleteForAllSubscribers }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteForAllSubscribers,
                            onCheckedChange = { deleteForAllSubscribers = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFFF5252),
                                uncheckedColor = Color.White.copy(alpha = 0.4f),
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Удалить для всех подписчиков",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteChat(chatId)
                        Toast.makeText(context, "Канал «${chat?.title ?: "Канал"}» удален", Toast.LENGTH_SHORT).show()
                        navController.navigateSafe("chat_list") {
                            popUpTo("chat_list") { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Удалить канал", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Отмена", color = Color(0xFFB072FF))
                }
            }
        )
    }

    // --- LEAVE CHANNEL CONFIRMATION DIALOG ---
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            containerColor = Color(0xFF191B28),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Покинуть канал?",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    "Вы действительно хотите покинуть канал «${chat?.title ?: "Канал"}»? Вы перестанете получать обновления и публикации этого канала.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmDialog = false
                        viewModel.deleteChat(chatId)
                        Toast.makeText(context, "Вы покинули канал «${chat?.title ?: "Канал"}»", Toast.LENGTH_SHORT).show()
                        navController.navigateSafe("chat_list") {
                            popUpTo("chat_list") { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Покинуть канал", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLeaveConfirmDialog = false }
                ) {
                    Text("Отмена", color = Color(0xFFB072FF))
                }
            }
        )
    }

    // --- CLEAR HISTORY DIALOG ---
    if (showClearHistoryDialog) {
        TelegramClearHistoryDialog(
            channelTitle = chat?.title ?: "Канал",
            onDismissRequest = { showClearHistoryDialog = false },
            onConfirm = {
                viewModel.clearHistory(chatId)
                Toast.makeText(context, "История канала очищена", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- MUTE DURATION PICKER DIALOG ---
    if (showMuteDurationPicker) {
        TelegramMuteDurationPickerDialog(
            onDismissRequest = { showMuteDurationPicker = false },
            onDurationSelected = { duration ->
                viewModel.toggleMute(chatId, true)
                Toast.makeText(context, "Уведомления выключены ($duration)", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- AUTO-DELETE WHEEL BOTTOM SHEET ---
    if (showAutoDeleteWheel) {
        TelegramAutoDeleteWheelBottomSheet(
            currentValue = customization.autoDeletePeriod ?: "Нет",
            onDismissRequest = { showAutoDeleteWheel = false },
            onApply = { selectedPeriod ->
                ChannelCustomizationManager.updateAutoDelete(chatId, selectedPeriod)
                if (selectedPeriod != null && selectedPeriod != "Нет") {
                    Toast.makeText(
                        context,
                        "Новые сообщения в чате будут автоматически удаляться через $selectedPeriod",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Автоудаление выключено.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // --- STORIES ARCHIVE DIALOG ---
    if (showStoriesArchive) {
        TelegramStoriesArchiveDialog(
            onDismissRequest = { showStoriesArchive = false },
            onAddNewStory = {
                Toast.makeText(context, "Открытие камеры для публикации истории...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- SEND GIFT DIALOG ---
    if (showSendGiftDialog) {
        TelegramSendGiftDialog(
            channelTitle = chat?.title ?: "Канал",
            onDismissRequest = { showSendGiftDialog = false },
            onGiftSent = { giftName, stars ->
                Toast.makeText(
                    context,
                    "Вы успешно отправили подарок «$giftName» за $stars ⭐!",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStackSafe() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { navController.navigateSafe("channel_admin/$chatId") }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Изменить", tint = Color.White)
                        }
                    }

                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Опции", tint = Color.White)
                        }

                        ChannelProfileDropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            isAdmin = isAdmin,
                            isChannel = isChannel,
                            channelTitle = chat?.title ?: "Канал",
                            currentAutoDeletePeriod = customization.autoDeletePeriod,
                            onAutoDeleteSelected = { period ->
                                ChannelCustomizationManager.updateAutoDelete(chatId, period)
                                if (period != null) {
                                    Toast.makeText(
                                        context,
                                        "Новые сообщения в чате будут автоматически удаляться через $period",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Автоудаление выключено.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onOpenAutoDeleteCustomWheel = {
                                showAutoDeleteWheel = true
                            },
                            onStartLiveStream = {
                                navController.navigateSafe("broadcast")
                            },
                            onOpenStats = {
                                navController.navigateSafe("channel_boost/$chatId")
                            },
                            onOpenStoriesArchive = {
                                showStoriesArchive = true
                            },
                            onShare = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, chat?.title ?: "Канал")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Присоединяйтесь к каналу «${chat?.title ?: "Канал"}»: https://${customization.inviteLink}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Поделиться каналом"))
                                } catch (e: Exception) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Channel Link", "https://${customization.inviteLink}"))
                                    Toast.makeText(context, "Ссылка на канал скопирована!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSendGift = {
                                showSendGiftDialog = true
                            },
                            onViewDiscussion = {
                                if (customization.discussionChatId != null) {
                                    navController.navigateSafe("chat/${customization.discussionChatId}")
                                } else {
                                    Toast.makeText(context, "Обсуждение: ${customization.discussionChatTitle}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCreateShortcut = {
                                Toast.makeText(
                                    context,
                                    "Ярлык канала «${chat?.title ?: "Канал"}» добавлен на главный экран",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onLeaveChannel = {
                                showLeaveConfirmDialog = true
                            },
                            onDeleteChannel = {
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F141C))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F141C), Color(0xFF141A25), Color(0xFF0B0E14))
                    )
                )
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- HEADER PROFILE CARD (Telegram Exact Layout) ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Circular Avatar with Camera Badge (Persistent Avatar)
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .clickable {
                                    channelPhotoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFF9900), Color(0xFFFF5E36))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasCustomChannelAvatar || !customization.avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(channelAvatar)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Аватар",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = (chat?.title?.take(2) ?: "KU").uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp
                                )
                            }
                        }

                        // Camera edit badge on avatar
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2AABEE))
                                .border(2.dp, Color(0xFF18222D), CircleShape)
                                .clickable {
                                    channelPhotoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = "Сменить фото",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Rounded Glass Container for Title & Nickname
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF0F1422).copy(alpha = 0.65f),
                        border = BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color(0xFF2AABEE).copy(alpha = 0.25f)
                                )
                            )
                        ),
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF1E2638).copy(alpha = 0.65f),
                                            Color(0xFF121724).copy(alpha = 0.85f)
                                        )
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Title with Status Emoji Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = chat?.title ?: "KuoteX Officiall",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .clickable { showEmojiPicker = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = customization.emojiStatus ?: "🪫",
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(5.dp))

                            // Liquid Glass badge for nickname / handle with fully rounded sides
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = Color(0xFF242834).copy(alpha = 0.88f),
                                border = BorderStroke(
                                    width = 1.1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.50f),
                                            Color.White.copy(alpha = 0.14f),
                                            Color(0xFF2AABEE).copy(alpha = 0.20f)
                                        )
                                    )
                                ),
                                shadowElevation = 6.dp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0xFF384055).copy(alpha = 0.85f),
                                                    Color(0xFF1B202E).copy(alpha = 0.92f)
                                                )
                                            )
                                        )
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "@${chat?.title?.replace(" ", "_")?.lowercase() ?: "channel"}",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // Subtitle: публичный канал / группа
                            Text(
                                text = if (chat?.isGroup == true) {
                                    if (customization.isPublic) "публичная группа" else "частная группа"
                                } else {
                                    if (customization.isPublic) "публичный канал" else "частный канал"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // --- 4 ACTION BUTTONS ROW (Live Stream, Звук, Обсуждение, Новая история) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TelegramProfileActionButton(
                            icon = Icons.Filled.Podcasts,
                            title = "Live Stream",
                            onClick = {
                                navController.navigateSafe("broadcast/$chatId")
                            }
                        )

                        TelegramProfileActionButton(
                            icon = if (chat?.isMuted == true) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                            title = if (chat?.isMuted == true) "Вкл. звук" else "Звук",
                            onClick = {
                                viewModel.toggleMute(chatId, chat?.isMuted != true)
                                Toast.makeText(context, if (chat?.isMuted == true) "Звук включен" else "Уведомления отключены", Toast.LENGTH_SHORT).show()
                            }
                        )

                        TelegramProfileActionButton(
                            icon = Icons.Filled.ChatBubbleOutline,
                            title = "Обсуждение",
                            onClick = {
                                if (customization.discussionChatId != null) {
                                    navController.navigateSafe("chat/${customization.discussionChatId}")
                                } else {
                                    Toast.makeText(context, "Переход в группу обсуждения: ${customization.discussionChatTitle}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        TelegramProfileActionButton(
                            icon = Icons.Filled.AddCircleOutline,
                            title = "Новая история",
                            onClick = {
                                showStoriesArchive = true
                            }
                        )
                    }
                }
            }

            // --- INFO CARD (Description & Link) ---
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF161E2E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Description
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = customization.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                lineHeight = 20.sp,
                                maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (customization.description.length > 70) {
                                Text(
                                    text = if (isDescExpanded) "скрыть" else "ещё",
                                    color = Color(0xFF2AABEE),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { isDescExpanded = !isDescExpanded }
                                        .padding(top = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Описание",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(14.dp))

                        // Link & QR Code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Link", "https://${customization.inviteLink}"))
                                    Toast.makeText(context, "Ссылка скопирована в буфер!", Toast.LENGTH_SHORT).show()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = customization.inviteLink,
                                    color = Color(0xFF2AABEE),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Ссылка-приглашение",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }

                            IconButton(
                                onClick = { showQrDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.QrCode,
                                    contentDescription = "QR-код",
                                    tint = Color(0xFF2AABEE),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- SECTION CARD: SUBSCRIBERS, ADMINS, SETTINGS ---
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF161E2E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // 1. Подписчики
                        TelegramProfileListRow(
                            icon = Icons.Filled.People,
                            title = "Подписчики",
                            badgeValue = "${customization.subscriberCount}",
                            onClick = {
                                navController.navigateSafe("channel_admin/$chatId")
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        )

                        // 2. Администраторы
                        TelegramProfileListRow(
                            icon = Icons.Filled.Shield,
                            title = "Администраторы",
                            badgeValue = "1",
                            onClick = {
                                navController.navigateSafe("channel_admin/$chatId")
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = Color.White.copy(alpha = 0.06f)
                        )

                        // 3. Настройки канала
                        TelegramProfileListRow(
                            icon = Icons.Filled.Settings,
                            title = "Настройки канала",
                            badgeValue = "",
                            onClick = {
                                navController.navigateSafe("channel_admin/$chatId")
                            }
                        )
                    }
                }
            }

            // --- MEDIA & CONTENT TABS ("Общие материалы") ---
            item {
                Text(
                    text = "Общие материалы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(mediaTabs.indices.toList()) { index ->
                        val isSelected = selectedMediaTab == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMediaTab = index },
                            label = { Text(mediaTabs[index]) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2AABEE),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF161E2E),
                                labelColor = Color.White.copy(alpha = 0.7f)
                            ),
                            border = null
                        )
                    }
                }
            }

            // Media Grid / Tab content
            when (selectedMediaTab) {
                0 -> {
                    // Dedicated Photos / Videos Grid
                    item {
                        ChannelMediaGallerySection(
                            mediaItems = channelMediaItems,
                            channelTitle = chat?.title ?: "Канал",
                            onOpenFullGallery = { showDedicatedGalleryDialog = true }
                        )
                    }
                }
                1 -> {
                    // Documents / Files
                    if (channelDocs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Файлов пока нет", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(channelDocs) { doc ->
                            ChannelDocumentItemRow(doc = doc)
                        }
                    }
                }
                2 -> {
                    // Voice messages / Audios
                    if (channelAudios.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Голосовых сообщений пока нет", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(channelAudios) { audio ->
                            ChannelAudioItemRow(audio = audio)
                        }
                    }
                }
                3 -> {
                    // Links
                    if (channelLinks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Ссылок пока нет", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(channelLinks) { link ->
                            ChannelLinkItemRow(link = link)
                        }
                    }
                }
                4 -> {
                    // Polls
                    if (polls.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Опросов пока нет", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(polls) { poll ->
                            PollMessageView(poll = poll, chatId = chatId)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun TelegramProfileActionButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF161E2E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF2AABEE),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TelegramProfileListRow(
    icon: ImageVector,
    title: String,
    badgeValue: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF2AABEE),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        if (badgeValue.isNotEmpty()) {
            Text(
                text = badgeValue,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}
