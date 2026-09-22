package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.LightboxImageViewerDialog
import com.example.ui.components.LightboxMediaItem
import com.example.ui.components.extractLightboxMediaItem
import com.example.ui.components.isImageMessage
import com.example.ui.components.TelegramEmojiPickerBottomSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.bot.BotChatDropdownMenu
import com.example.ui.bot.BotReportBottomSheet
import com.example.ui.bot.BotPrivacyPolicyBottomSheet
import com.example.ui.bot.BotDeleteAndBlockDialog
import com.example.ui.bot.createBotShortcut
import com.example.ui.bot.shareBotLink
import com.example.ui.bot.saveBotMediaToGallery
import com.example.ui.channel.TelegramAutoDeleteWheelBottomSheet
import java.util.Date
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.channel.*

@Composable
fun Modifier.telegramMessageGestures(
    onClick: () -> Unit,
    onLongClick3s: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick3s by rememberUpdatedState(onLongClick3s)
    val coroutineScope = rememberCoroutineScope()
    
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            var isLongPressTriggered = false
            
            val timerJob = coroutineScope.launch {
                delay(600L) // Standard Telegram message long press
                isLongPressTriggered = true
                currentOnLongClick3s()
            }
            
            var moved = false
            var consumedByChild = false
            while (true) {
                val event = awaitPointerEvent()
                val currentChange = event.changes.firstOrNull { it.id == down.id }
                if (currentChange == null || !currentChange.pressed) {
                    if (currentChange != null && currentChange.isConsumed) {
                        consumedByChild = true
                    }
                    break
                }
                if (currentChange.isConsumed) {
                    consumedByChild = true
                    timerJob.cancel()
                    break
                }
                val distance = (currentChange.position - down.position).getDistance()
                if (distance > 16f) {
                    moved = true
                    timerJob.cancel()
                    break
                }
            }
            timerJob.cancel()
            
            // If released without moving and unconsumed by a child element (like photo or audio), trigger message menu
            if (!moved && !consumedByChild && !isLongPressTriggered && !down.isConsumed) {
                currentOnClick()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@androidx.compose.animation.ExperimentalSharedTransitionApi
fun ChatScreen(viewModel: AppViewModel, chatId: String, navController: NavController) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId } ?: Chat(
        id = chatId,
        title = if (chatId.toIntOrNull() != null) "User $chatId" else chatId,
        lastMessage = "",
        isGroup = false,
        isChannel = false,
        isBot = false
    )

    com.example.analytics.TrackScreen(
        screenName = "chat_screen",
        screenClass = "ChatScreen",
        params = mapOf(
            "chat_id" to chatId,
            "is_group" to chat.isGroup,
            "is_channel" to chat.isChannel,
            "is_bot" to chat.isBot
        )
    )
    
        val messages by viewModel.getPagedMessages(chatId).collectAsStateWithLifecycle(initialValue = emptyList())
        val totalMessageCount by viewModel.getMessageCountForChat(chatId).collectAsStateWithLifecycle(initialValue = 0)
        val isLoadingMoreMap by viewModel.isLoadingMoreMessages.collectAsStateWithLifecycle()
        val isLoadingMore = isLoadingMoreMap[chatId] == true
        val hasMoreMessages = totalMessageCount > messages.size
    
    var inputText by remember { mutableStateOf("") }
    val currentInputText by androidx.compose.runtime.rememberUpdatedState(inputText)

    // Load persisted draft from Room database on entering chat
    androidx.compose.runtime.LaunchedEffect(chatId) {
        val savedDraft = viewModel.getDraft(chatId)
        if (!savedDraft.isNullOrBlank() && inputText.isEmpty()) {
            inputText = savedDraft
        }
    }

    DisposableEffect(chatId) {
        viewModel.setActiveChat(chatId)
        onDispose {
            viewModel.setActiveChat(null)
            // Persist unsent draft locally in Room when navigating away
            if (currentInputText.isNotBlank()) {
                viewModel.saveDraft(chatId, currentInputText)
            } else {
                viewModel.clearDraft(chatId)
            }
        }
    }

    // Auto-save draft changes to Room database with debouncing
    androidx.compose.runtime.LaunchedEffect(inputText) {
        if (inputText.isNotBlank()) {
            kotlinx.coroutines.delay(350L)
            viewModel.saveDraft(chatId, inputText)
        } else {
            viewModel.clearDraft(chatId)
        }
    }

    val groupMembers by if (chat.isGroup || chat.isChannel) viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList()) else remember { mutableStateOf(emptyList()) }
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val chatQueuedCount by viewModel.getQueuedCountForChat(chatId).collectAsStateWithLifecycle(initialValue = 0)
    val isSyncingQueue by viewModel.isSyncingQueue.collectAsStateWithLifecycle()
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var customQuoteText by remember { mutableStateOf<String?>(null) }
    var showQuoteSelector by remember { mutableStateOf(false) }
    var messagesToForward by remember { mutableStateOf<List<Message>>(emptyList()) }
    var selectedMessageIds by remember { mutableStateOf(emptySet<String>()) }
    val isSelectionMode = selectedMessageIds.isNotEmpty()
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showReplyOptions by remember { mutableStateOf(false) }
    var showReplyChatPicker by remember { mutableStateOf(false) }

    val globalReplyDrafts by viewModel.replyDrafts.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(globalReplyDrafts) {
        globalReplyDrafts[chatId]?.let {
            replyingToMessage = it
            viewModel.setReplyDraft(chatId, null)
        }
    }
    
    val forwardDraftMap by viewModel.forwardDrafts.collectAsStateWithLifecycle()
    val currentForwardDraft = forwardDraftMap[chatId]
    var showForwardDraftOptions by remember { mutableStateOf(false) }
    var showForwardChangeRecipientPicker by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchSender by remember { mutableStateOf("") }
    var searchMediaOnly by remember { mutableStateOf(false) }
    var showSearchFilters by remember { mutableStateOf(false) }
    var searchStartDate by remember { mutableStateOf<Long?>(null) }
    var searchEndDate by remember { mutableStateOf<Long?>(null) }
    
    val filteredMessages = if (!isSearchMode) messages else messages.filter { msg ->
        val queryMatch = searchQuery.isBlank() || msg.text.contains(searchQuery, ignoreCase = true) || msg.senderId.contains(searchQuery, ignoreCase = true)
        val senderMatch = searchSender.isBlank() || msg.senderId.contains(searchSender, ignoreCase = true)
        val mediaMatch = if (searchMediaOnly) (msg.mediaPath != null || msg.audioPath != null || msg.documentData != null) else true
        val startDateMatch = if (searchStartDate != null) msg.timestamp >= searchStartDate!! else true
        val endDateMatch = if (searchEndDate != null) msg.timestamp <= searchEndDate!! else true
        queryMatch && senderMatch && mediaMatch && startDateMatch && endDateMatch
    }

    val activeAccount = LocalActiveAccount.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var expiresIn by remember { mutableStateOf<Long?>(null) }
    
    var showScheduleDialog by remember { mutableStateOf(false) }
    
    var showReactionDialogFor by remember { mutableStateOf<String?>(null) }
    var messageToReport by remember { mutableStateOf<Message?>(null) }
    
    var inputMode by remember { mutableStateOf(com.example.ui.components.ChatInputMode.VOICE) }
    var isVoiceRecording by remember { mutableStateOf(false) }
    var isVideoRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isVoiceRecording, isVideoRecording) {
        if (isVoiceRecording || isVideoRecording) {
            recordingSeconds = 0
            while (isVoiceRecording || isVideoRecording) {
                kotlinx.coroutines.delay(1000L)
                recordingSeconds++
            }
        } else {
            recordingSeconds = 0
        }
    }

    // Notification states
    var showNotificationSubmenu by remember { mutableStateOf(false) }
    var showMuteDurationPicker by remember { mutableStateOf(false) }
    var showCustomNotificationSettings by remember { mutableStateOf(false) }
    var isSoundMuted by remember { mutableStateOf(false) }
    var selectedMuteDuration by remember { mutableStateOf("30 мин.") }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showChannelMessagesDialog by remember { mutableStateOf(false) }
    var showLeaveChannelDialog by remember { mutableStateOf(false) }

    var showBotReportDialog by remember { mutableStateOf(false) }
    var showBotPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showBotDeleteAndBlockDialog by remember { mutableStateOf(false) }
    var showBotAutoDeleteDialog by remember { mutableStateOf(false) }
    var botAutoDeletePeriod by remember { mutableStateOf<String?>(null) }

    // Custom notification settings states
    var notifShowText by remember { mutableStateOf(true) }
    var notifShowHistory by remember { mutableStateOf(false) }
    var notifSound by remember { mutableStateOf("По умолчанию") }
    var notifVibration by remember { mutableStateOf("По умолчанию") }
    var notifPriority by remember { mutableStateOf("Как в настройках") }
    var notifPopupEnabled by remember { mutableStateOf(false) }
    var notifCallVibration by remember { mutableStateOf("По умолчанию") }
    var notifCallRingtone by remember { mutableStateOf("По умолчанию") }
    var notifLedColor by remember { mutableStateOf(Color(0xFF00B0FF)) }

    var showVibrationPicker by remember { mutableStateOf(false) }
    var showPriorityPicker by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    
    
    val userPresences by viewModel.userPresences.collectAsStateWithLifecycle()
    val presence = userPresences[chatId]

    val typingChats by viewModel.typingChats.collectAsState()
    val isTyping = typingChats.contains(chatId)
    
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableStateOf(0L) }
    var selectedFileMime by remember { mutableStateOf("") }
    var showFilePreviewDialog by remember { mutableStateOf(false) }

    var selectedCompressionPreset by remember { mutableStateOf(com.example.utils.ImageCompressionPreset.BALANCED_AUTO) }
    var compressionPreviewStats by remember { mutableStateOf<com.example.utils.ImageCompressionResult?>(null) }
    var isAnalyzingCompression by remember { mutableStateOf(false) }

    val uploadProgressState by viewModel.photoUploadState.collectAsState()

    androidx.compose.runtime.LaunchedEffect(selectedFileUri, selectedCompressionPreset, showFilePreviewDialog) {
        if (showFilePreviewDialog && selectedFileUri != null && selectedFileMime.startsWith("image/")) {
            isAnalyzingCompression = true
            try {
                val res = com.example.utils.ImageCompressionManager.compressImage(
                    context = context,
                    imageUri = selectedFileUri!!,
                    originalName = selectedFileName,
                    preset = selectedCompressionPreset
                )
                compressionPreviewStats = res
            } catch (e: Exception) {
                compressionPreviewStats = null
            } finally {
                isAnalyzingCompression = false
            }
        } else if (!showFilePreviewDialog) {
            compressionPreviewStats = null
        }
    }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let {
            selectedFileUri = it
            var name = "Unknown File"
            var size = 0L
            var mime = context.contentResolver.getType(it) ?: "application/octet-stream"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
            if (size > 5L * 1024 * 1024 * 1024) { // 5 GB limit
                android.widget.Toast.makeText(context, "File exceeds 5 GB limit", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                selectedFileName = name
                selectedFileSize = size
                selectedFileMime = mime
                showFilePreviewDialog = true
            }
        }
    }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            selectedFileUri = it
            var name = "Photo_${System.currentTimeMillis()}.jpg"
            var size = 0L
            var mime = context.contentResolver.getType(it) ?: "image/jpeg"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
            selectedFileName = name
            selectedFileSize = size
            selectedFileMime = mime
            showFilePreviewDialog = true
        }
    }

    var lightboxInitialIndex by remember { mutableIntStateOf(0) }
    var activeLightboxItems by remember { mutableStateOf<List<LightboxMediaItem>>(emptyList()) }
    var showLightboxViewer by remember { mutableStateOf(false) }
    var activeVideoNoteToView by remember { mutableStateOf<Message?>(null) }
    val activity = remember(context) {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is android.app.Activity) {
                break
            }
            currentContext = currentContext.baseContext
        }
        currentContext as? android.app.Activity
    }
    
    DisposableEffect(chat.isSecret) {
        if (chat.isSecret) {
            activity?.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        onDispose {
            if (chat.isSecret) {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
    
    LaunchedEffect(chatId) {
        viewModel.markMessagesAsRead(chatId, activeAccount?.id ?: "")
    }

    BackHandler(enabled = isSelectionMode || isSearchMode) {
        if (isSelectionMode) {
            selectedMessageIds = emptySet()
        } else if (isSearchMode) {
            focusManager.clearFocus()
            keyboardController?.hide()
            isSearchMode = false
            searchQuery = ""
        }
    }
    
    var showAttachmentMenu by remember { mutableStateOf(false) }
    
    val channelCustomizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val channelCustomization = channelCustomizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)
    val channelPalette = TelegramProfilePalettes.getPalette(channelCustomization.profileColorId)
    val channelWallpaper = TelegramWallpapers.getWallpaper(channelCustomization.wallpaperId)
    val channelPollsMap by ChannelCustomizationManager.getPollsFlow(chatId).collectAsState()
    val channelPolls = channelPollsMap[chatId] ?: emptyList()
    var showCreatePollDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Выбрано: ${selectedMessageIds.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.semantics {
                                contentDescription = "Выбрано сообщений: ${selectedMessageIds.size}"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedMessageIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Отменить выбор сообщений")
                        }
                    },
                    actions = {
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        val selectedMsgs = messages.filter { selectedMessageIds.contains(it.id) }
                        
                        // Pin/Unpin if single message selected
                        if (selectedMessageIds.size == 1) {
                            val singleMsg = selectedMsgs.firstOrNull()
                            val isPinned = singleMsg?.isPinned == true
                            IconButton(onClick = {
                                singleMsg?.let {
                                    if (it.isPinned) {
                                        viewModel.unpinMessage(chatId, it.id)
                                    } else {
                                        viewModel.pinMessage(chatId, it.id)
                                    }
                                    selectedMessageIds = emptySet()
                                }
                            }) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = if (isPinned) "Открепить выбранное сообщение" else "Закрепить выбранное сообщение"
                                )
                            }
                        }

                        IconButton(onClick = {
                            val combinedText = selectedMsgs.joinToString("\n\n") { it.text }
                            if (combinedText.isNotBlank()) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(combinedText))
                                android.widget.Toast.makeText(context, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            selectedMessageIds = emptySet()
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать выбранные сообщения")
                        }
                        if (!chat.isSecret) {
                            IconButton(onClick = {
                                messagesToForward = selectedMsgs
                                selectedMessageIds = emptySet()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = "Переслать выбранные сообщения")
                            }
                        }
                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить выбранные сообщения", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else if (isSearchMode) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .semantics {
                                    contentDescription = "Поле поиска сообщений в чате"
                                },
                            placeholder = { Text("Поиск по сообщениям...") },
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            isSearchMode = false
                            searchQuery = "" 
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Закрыть поиск")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearchFilters = true }) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Фильтры поиска сообщений")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                )
            } else {
                TopAppBar(
                title = {
                    val sharedTransitionScope = LocalSharedTransitionScope.current
                    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                    
                    var botUsersText by remember { mutableStateOf<String?>(null) }
                    if (chat.isBot) {
                        val activeUsers by viewModel.getBotActiveUsersCount(chat.id).collectAsStateWithLifecycle(initialValue = 0)
                        if (activeUsers > 0) {
                            botUsersText = "${java.text.NumberFormat.getInstance(java.util.Locale("ru", "RU")).format(activeUsers)} пользователей"
                        } else {
                            botUsersText = "бот"
                        }
                    }

                    val subCount = groupMembers.size.coerceAtLeast(1)
                    val channelSubText = when {
                        subCount % 10 == 1 && subCount % 100 != 11 -> "$subCount подписчик"
                        subCount % 10 in 2..4 && (subCount % 100 < 10 || subCount % 100 >= 20) -> "$subCount подписчика"
                        else -> "$subCount подписчиков"
                    }
                    val groupSubText = when {
                        subCount % 10 == 1 && subCount % 100 != 11 -> "$subCount участник"
                        subCount % 10 in 2..4 && (subCount % 100 < 10 || subCount % 100 >= 20) -> "$subCount участника"
                        else -> "$subCount участников"
                    }

                    val isOnline = presence?.isOnline == true
                    val lastSeen = presence?.lastSeen ?: 0L

                    val statusText = if (chat.isBot) {
                        botUsersText ?: "бот"
                    } else if (chat.isChannel) {
                        channelSubText
                    } else if (chat.isGroup) {
                        groupSubText
                    } else {
                        when {
                            isTyping -> "печатает..."
                            isOnline -> "в сети"
                            lastSeen > 0 -> {
                                val diff = System.currentTimeMillis() - lastSeen
                                if (diff < 60_000) "был(а) только что"
                                else if (diff < 3600_000) "был(а) ${diff / 60_000} мин. назад"
                                else "был(а) ${diff / 3600_000} ч. назад"
                            }
                            else -> "был(а) недавно"
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = { navController.navigate("profile/${chat.id}") },
                                onClickLabel = "Открыть профиль ${chat.title}"
                            )
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = "Чат ${chat.title}, $statusText"
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        // Avatar with shared element transition
                        var avatarModifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                        
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                avatarModifier = avatarModifier.sharedElement(
                                    state = rememberSharedContentState(key = "avatar_${chat.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                        
                        val botObjAvatar = com.example.ui.botapi.BotRegistry.getBot(chatId) ?: com.example.ui.botapi.BotRegistry.getCustomBots().find { it.id == chatId }
                        val customBotAvatar = botObjAvatar as? com.example.ui.botapi.CustomBot
                        val topBarAvatarUrl = customBotAvatar?.botPicUri?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/seed/${chat.id}/400"
                        
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).allowHardware(false)
                                .data(topBarAvatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = avatarModifier,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column {
                            var titleModifier: Modifier = Modifier
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    titleModifier = titleModifier.sharedElement(
                                        state = rememberSharedContentState(key = "title_${chat.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(chat.title, modifier = titleModifier)
                                if (channelCustomization.emojiStatus != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AnimatedEmojiStatusBadge(
                                        emoji = channelCustomization.emojiStatus,
                                        glowColor = channelPalette.primaryColor,
                                        size = 20.dp,
                                        fontSize = 12.sp,
                                        onClick = { navController.navigate("channel_appearance/$chatId") }
                                    )
                                }
                            }
                        
                            Text(
                                text = statusText, 
                                style = MaterialTheme.typography.labelSmall, 
                                color = if(isTyping) MaterialTheme.colorScheme.primary else if(isOnline && !chat.isBot && !chat.isChannel && !chat.isGroup) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        navController.popBackStack() 
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад к списку чатов")
                    }
                },
                actions = {
                    if (!chat.isBot && !chat.isGroup && !chat.isChannel) {
                        IconButton(onClick = { navController.navigate("call/${chat.id}?isVideo=false") }) {
                            Icon(Icons.Filled.Call, contentDescription = "Голосовой звонок")
                        }
                    }
                    
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Меню опций")
                        }
                        if (chat.isBot) {
                            BotChatDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                botName = chat.title,
                                botUsername = "@${chat.id}",
                                botAvatarUrl = "https://picsum.photos/seed/${chat.id}/400",
                                isMuted = chat.isMuted,
                                isSoundMuted = isSoundMuted,
                                currentAutoDeletePeriod = botAutoDeletePeriod,
                                onToggleMute = { muted ->
                                    viewModel.toggleMute(chatId, muted)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (muted) "Уведомления выключены." else "Уведомления включены.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onAutoDeleteSelected = { period ->
                                    botAutoDeletePeriod = period
                                    android.widget.Toast.makeText(
                                        context,
                                        if (period != null) "Автоудаление установлено: $period" else "Автоудаление выключено",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onOpenAutoDeleteCustomWheel = {
                                    showBotAutoDeleteDialog = true
                                },
                                onCreateShortcut = {
                                    createBotShortcut(context, chat.id, chat.title, "@${chat.id}", null)
                                },
                                onShare = {
                                    shareBotLink(context, "@${chat.id}", chat.title)
                                },
                                onPrivacyPolicy = {
                                    showBotPrivacyPolicyDialog = true
                                },
                                onSaveToGallery = {
                                    saveBotMediaToGallery(context, coroutineScope, "https://picsum.photos/seed/${chat.id}/400", chat.title)
                                },
                                onReport = {
                                    showBotReportDialog = true
                                },
                                onDeleteAndBlock = {
                                    showBotDeleteAndBlockDialog = true
                                },
                                onClearHistory = {
                                    showClearHistoryDialog = true
                                },
                                onSearch = {
                                    isSearchMode = true
                                }
                            )
                        } else {
                            com.example.ui.channel.ChannelChatDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                isAdmin = true,
                                isChannel = chat.isChannel,
                                isGroup = chat.isGroup,
                                isMuted = chat.isMuted,
                                isSoundMuted = isSoundMuted,
                                channelTitle = chat.title,
                                onToggleMute = { muted ->
                                    viewModel.toggleMute(chatId, muted)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (muted) "Уведомления выключены." else "Уведомления включены.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onToggleSoundMute = {
                                    isSoundMuted = !isSoundMuted
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isSoundMuted) "Уведомления будут беззвучными." else "Уведомления будут со звуком.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onMuteForDuration = { duration ->
                                    if (duration == "custom") {
                                        showMuteDurationPicker = true
                                    } else {
                                        viewModel.toggleMute(chatId, true)
                                        android.widget.Toast.makeText(context, "Уведомления выключены на $duration.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenCustomNotificationSettings = {
                                    showCustomNotificationSettings = true
                                },
                                onSearch = {
                                    isSearchMode = true
                                },
                                onViewDiscussion = {
                                    showChannelMessagesDialog = true
                                },
                                onOpenBoosts = {
                                    navController.navigate("channel_boost/${chat.id}")
                                },
                                onClearHistory = {
                                    showClearHistoryDialog = true
                                },
                                onReport = {
                                    android.widget.Toast.makeText(context, "Жалоба отправлена модераторам", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onLeaveChannel = {
                                    showLeaveChannelDialog = true
                                },
                                onDeleteChannel = {
                                    viewModel.deleteChat(chatId)
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                )
            )
            }
        },
        bottomBar = {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) { height -> height } + fadeIn(animationSpec = tween(280)))
                        .togetherWith(slideOutVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) { height -> height } + fadeOut(animationSpec = tween(200)))
                },
                label = "multimodal_bottom_bar_anim"
            ) { inSelection ->
                if (inSelection) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 8.dp
                    ) {
                        val selMsgs = messages.filter { selectedMessageIds.contains(it.id) }
                        val singleMsg = if (selectedMessageIds.size == 1) selMsgs.firstOrNull() else null

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(bottom = 8.dp)
                        ) {
                            // Quick Emoji Reactions Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val emojis = listOf("👍", "❤️", "🔥", "😂", "👏", "😮", "🎉")
                                emojis.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .clickable {
                                                selectedMessageIds.forEach { msgId ->
                                                    viewModel.addReaction(msgId, emoji)
                                                }
                                                android.widget.Toast.makeText(context, "Реакция добавлена", android.widget.Toast.LENGTH_SHORT).show()
                                                selectedMessageIds = emptySet()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                            )

                            // Main Selection Actions Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Reply / Delete
                                if (selectedMessageIds.size == 1) {
                                    TextButton(
                                        onClick = {
                                            replyingToMessage = singleMsg
                                            customQuoteText = null
                                            selectedMessageIds = emptySet()
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Ответить")
                                    }
                                } else {
                                    TextButton(
                                        onClick = { showBatchDeleteDialog = true }
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Удалить (${selectedMessageIds.size})", color = MaterialTheme.colorScheme.error)
                                    }
                                }

                                // Right: Forward
                                if (!chat.isSecret) {
                                    Button(
                                        onClick = {
                                            messagesToForward = selMsgs
                                            selectedMessageIds = emptySet()
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (selectedMessageIds.size > 1) "Переслать (${selectedMessageIds.size})" else "Переслать")
                                    }
                                }
                            }
                        }
                    }
                } else if (chat.isBlocked) {
                    Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), color = Color.Transparent) {
                        Text(
                            "You blocked this user.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                        )
                    }
                } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Telegram-style Forward Draft Banner
                        if (currentForwardDraft != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showForwardDraftOptions = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Forward,
                                    contentDescription = "Переслать",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(34.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val forwardHeading = if (currentForwardDraft.messages.size > 1) {
                                        "Переслать ${currentForwardDraft.messages.size} сообщений"
                                    } else {
                                        "Переслать 1 сообщение"
                                    }
                                    Text(
                                        text = forwardHeading,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    val firstMsg = currentForwardDraft.messages.firstOrNull()?.first
                                    val previewContent = when {
                                        firstMsg?.documentData != null -> "Файл / Документ"
                                        firstMsg?.audioPath != null -> "Голосовое сообщение"
                                        firstMsg?.text?.isNotBlank() == true -> firstMsg.text
                                        else -> "Медиа"
                                    }
                                    val senderDisplayName = if (currentForwardDraft.hideSender) {
                                        "Без имени автора"
                                    } else {
                                        val nickname = currentForwardDraft.originalSenderUsername
                                        if (!nickname.isNullOrBlank()) {
                                            "${currentForwardDraft.originalSenderName} (@$nickname)"
                                        } else {
                                            currentForwardDraft.originalSenderName
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!currentForwardDraft.hideSender) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!currentForwardDraft.originalSenderAvatarUrl.isNullOrBlank()) {
                                                    coil.compose.AsyncImage(
                                                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                            .data(currentForwardDraft.originalSenderAvatarUrl)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = "Avatar",
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                    )
                                                }
                                                val initialLetter = (currentForwardDraft.originalSenderName.take(1).ifBlank { "U" }).uppercase()
                                                Text(
                                                    text = initialLetter,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = "$senderDisplayName: $previewContent",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { 
                                        viewModel.clearForwardDraft(chatId) 
                                    }, 
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close, 
                                        contentDescription = "Убрать пересылку", 
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                thickness = 0.8.dp
                            )
                        }

                        if (replyingToMessage != null) {
                            val replyMsg = replyingToMessage!!
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showReplyOptions = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Reply",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(30.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "В ответ ${replyMsg.senderId}", 
                                        style = MaterialTheme.typography.labelMedium, 
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        text = customQuoteText ?: replyMsg.text, 
                                        style = MaterialTheme.typography.bodySmall, 
                                        maxLines = 1, 
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { 
                                        replyingToMessage = null
                                        customQuoteText = null 
                                    }, 
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Filled.Close, 
                                        contentDescription = "Убрать ответ", 
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                thickness = 0.8.dp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                    var attachmentMenuExpanded by remember { mutableStateOf(false) }
                    var botMenuExpanded by remember { mutableStateOf(false) }
                    
                    if (chat.isBot || chat.title.contains("BotFather", ignoreCase = true)) {
                        Box {
                            IconButton(onClick = { botMenuExpanded = true }) {
                                Icon(Icons.Filled.Terminal, contentDescription = "Команды бота", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(
                                expanded = botMenuExpanded,
                                onDismissRequest = { botMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("/start - старт/перезапуск бота") },
                                    onClick = {
                                        botMenuExpanded = false
                                        viewModel.sendMessage(chatId, activeAccount?.id ?: "", "/start", null, expiresIn)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("/newbot - создать нового бота") },
                                    onClick = {
                                        botMenuExpanded = false
                                        viewModel.sendMessage(chatId, activeAccount?.id ?: "", "/newbot", null, expiresIn)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("/description - описание бота") },
                                    onClick = {
                                        botMenuExpanded = false
                                        viewModel.sendMessage(chatId, activeAccount?.id ?: "", "/description", null, expiresIn)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("/mybots - список моих ботов") },
                                    onClick = {
                                        botMenuExpanded = false
                                        viewModel.sendMessage(chatId, activeAccount?.id ?: "", "/mybots", null, expiresIn)
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { attachmentMenuExpanded = true }) {
                            Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить вложение")
                        }
                        DropdownMenu(
                            expanded = attachmentMenuExpanded,
                            onDismissRequest = { attachmentMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Фото / Галерея") },
                                onClick = { 
                                    attachmentMenuExpanded = false
                                    photoPickerLauncher.launch("image/*")
                                },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Камера") },
                                onClick = { 
                                    attachmentMenuExpanded = false
                                    val photoDoc = org.json.JSONObject().apply {
                                        put("uri", "https://picsum.photos/seed/${System.currentTimeMillis()}/800")
                                        put("name", "Photo_${System.currentTimeMillis()}.jpg")
                                        put("size", 1024L * 320)
                                        put("mimeType", "image/jpeg")
                                    }.toString()
                                    viewModel.sendMessage(
                                        chatId = chatId,
                                        senderId = activeAccount?.id ?: "",
                                        text = "📷 Сделанное фото",
                                        audioPath = null,
                                        expiresIn = expiresIn,
                                        documentData = photoDoc
                                    )
                                },
                                leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Файл") },
                                onClick = { 
                                    attachmentMenuExpanded = false
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                },
                                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Геолокация") },
                                onClick = { 
                                    attachmentMenuExpanded = false
                                    viewModel.sendMessage(chatId, activeAccount?.id ?: "", "📍 Местоположение: 37.4221° N, 122.0841° W", null, expiresIn)
                                },
                                leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Опрос") },
                                onClick = { 
                                    attachmentMenuExpanded = false
                                    showCreatePollDialog = true
                                },
                                leadingIcon = { Icon(Icons.Filled.Poll, contentDescription = null, tint = Color(0xFF00E5FF)) }
                            )
                        }
                    }

                    if (chat.isBot) {
                        var showBotMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showBotMenu = true }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Меню команд бота", tint = MaterialTheme.colorScheme.primary)
                        }
                        if (showBotMenu) {
                            DropdownMenu(
                                expanded = showBotMenu,
                                onDismissRequest = { showBotMenu = false }
                            ) {
                                val botConfig = com.example.ui.botapi.BotRegistry.getBot(chat.id)
                                val baseCommands = botConfig?.commands ?: listOf(com.example.ui.botapi.BotCommand("/start", "Start bot"), com.example.ui.botapi.BotCommand("/help", "Help"))
                                val customCommands = (botConfig as? com.example.ui.botapi.CustomBot)?.customCommands ?: emptyList()
                                val allCommands = baseCommands + customCommands

                                allCommands.forEach { cmd ->
                                    DropdownMenuItem(
                                        text = { Text("${cmd.command} - ${cmd.description}") },
                                        onClick = { 
                                            viewModel.sendMessage(chatId, activeAccount?.id ?: "", cmd.command, null, expiresIn)
                                            showBotMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (isVoiceRecording || isVideoRecording) {
                        com.example.ui.components.TelegramRecordingBar(
                            mode = inputMode,
                            recordingSeconds = recordingSeconds,
                            onCancel = {
                                isVoiceRecording = false
                                isVideoRecording = false
                                recordingSeconds = 0
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        var showEmojiPicker by remember { mutableStateOf(false) }
                        IconButton(onClick = { showEmojiPicker = true }) {
                            Text("😀", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "Выбрать эмодзи и стикеры" })
                        }
                        if (showEmojiPicker) {
                            TelegramEmojiPickerBottomSheet(
                                onDismissRequest = { showEmojiPicker = false },
                                onEmojiSelected = { emojiItem ->
                                    inputText += emojiItem.emoji
                                },
                                title = "Эмодзи и стикеры"
                            )
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Сообщение...") },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    contentDescription = "Поле ввода сообщения"
                                },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    }

                    if (inputText.isNotBlank() || currentForwardDraft != null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        if (currentForwardDraft != null) {
                                            com.example.analytics.AnalyticsTracker.logChatAction(
                                                action = "forward_messages",
                                                chatId = chatId,
                                                metadata = mapOf("count" to currentForwardDraft.messages.size)
                                            )
                                            viewModel.forwardMessages(
                                                targetChatId = chatId,
                                                senderId = activeAccount?.id ?: "",
                                                messagesToForward = currentForwardDraft.messages,
                                                hideSender = currentForwardDraft.hideSender
                                            )
                                            viewModel.clearForwardDraft(chatId)
                                        }
                                        if (inputText.isNotBlank()) {
                                            com.example.analytics.AnalyticsTracker.logChatAction(
                                                action = "send_message",
                                                chatId = chatId,
                                                metadata = mapOf(
                                                    "length" to inputText.length,
                                                    "has_reply" to (replyingToMessage != null),
                                                    "is_custom_quote" to (customQuoteText != null)
                                                )
                                            )
                                            viewModel.sendMessage(
                                                chatId = chatId, 
                                                senderId = activeAccount?.id ?: "", 
                                                text = inputText, 
                                                audioPath = null, 
                                                expiresIn = expiresIn,
                                                documentData = null,
                                                replyToMessageId = replyingToMessage?.id,
                                                replyToMessageText = customQuoteText ?: replyingToMessage?.text
                                            )
                                            inputText = ""
                                            replyingToMessage = null
                                            customQuoteText = null
                                        }
                                    },
                                    onClickLabel = "Отправить сообщение",
                                    onLongClick = {
                                        com.example.analytics.AnalyticsTracker.logButtonClick(
                                            buttonName = "schedule_message",
                                            module = "chat",
                                            metadata = mapOf("chat_id" to chatId)
                                        )
                                        showScheduleDialog = true
                                    },
                                    onLongClickLabel = "Запланировать отправку сообщения"
                                )
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Отправить сообщение. Долгое нажатие для отложенной отправки"
                                }
                                .padding(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        com.example.ui.components.TelegramRecordActionButton(
                            mode = inputMode,
                            isRecording = isVoiceRecording || isVideoRecording,
                            onToggleMode = {
                                inputMode = if (inputMode == com.example.ui.components.ChatInputMode.VOICE) {
                                    com.example.ui.components.ChatInputMode.VIDEO
                                } else {
                                    com.example.ui.components.ChatInputMode.VOICE
                                }
                                com.example.analytics.AnalyticsTracker.logButtonClick(
                                    buttonName = "switch_record_mode_${inputMode.name.lowercase()}",
                                    module = "chat",
                                    metadata = mapOf("chat_id" to chatId)
                                )
                            },
                            onStartRecording = { mode ->
                                if (mode == com.example.ui.components.ChatInputMode.VIDEO) {
                                    isVideoRecording = true
                                    com.example.analytics.AnalyticsTracker.logButtonClick(
                                        buttonName = "start_video_note_record",
                                        module = "chat",
                                        metadata = mapOf("chat_id" to chatId)
                                    )
                                } else {
                                    isVoiceRecording = true
                                    com.example.analytics.AnalyticsTracker.logButtonClick(
                                        buttonName = "start_voice_record",
                                        module = "chat",
                                        metadata = mapOf("chat_id" to chatId)
                                    )
                                }
                            },
                            onStopAndSend = { mode ->
                                if (mode == com.example.ui.components.ChatInputMode.VIDEO) {
                                    if (isVideoRecording) {
                                        val duration = recordingSeconds.coerceAtLeast(1)
                                        val minutes = duration / 60
                                        val seconds = duration % 60
                                        val durationStr = if (minutes > 0) String.format("%d:%02d", minutes, seconds) else String.format("0:%02d", seconds)
                                        
                                        com.example.analytics.AnalyticsTracker.logButtonClick(
                                            buttonName = "send_video_note",
                                            module = "chat",
                                            metadata = mapOf("chat_id" to chatId, "duration" to duration)
                                        )
                                        val videoDocData = """{"type":"video_note","duration":"$durationStr","durationSeconds":$duration,"isRound":true,"width":480,"height":480}"""
                                        viewModel.sendMessage(
                                            chatId = chatId,
                                            senderId = activeAccount?.id ?: "",
                                            text = "📹 Видеосообщение ($durationStr)",
                                            audioPath = null,
                                            expiresIn = expiresIn,
                                            documentData = videoDocData,
                                            replyToMessageId = replyingToMessage?.id,
                                            replyToMessageText = customQuoteText ?: replyingToMessage?.text,
                                            mediaPath = "video_note_${System.currentTimeMillis()}.mp4",
                                            mediaType = "video_note"
                                        )
                                        replyingToMessage = null
                                        customQuoteText = null
                                    }
                                    isVideoRecording = false
                                } else {
                                    if (isVoiceRecording) {
                                        val duration = recordingSeconds.coerceAtLeast(1)
                                        val minutes = duration / 60
                                        val seconds = duration % 60
                                        val durationStr = if (minutes > 0) String.format("%d:%02d", minutes, seconds) else String.format("0:%02d", seconds)
                                        
                                        com.example.analytics.AnalyticsTracker.logButtonClick(
                                            buttonName = "send_voice_record",
                                            module = "chat",
                                            metadata = mapOf("chat_id" to chatId, "duration" to duration)
                                        )
                                        viewModel.sendMessage(
                                            chatId = chatId,
                                            senderId = activeAccount?.id ?: "",
                                            text = "🎤 Голосовое сообщение ($durationStr)",
                                            audioPath = "voice_note_${System.currentTimeMillis()}.mp3",
                                            expiresIn = expiresIn,
                                            documentData = """{"type":"voice_note","duration":"$durationStr","durationSeconds":$duration}""",
                                            replyToMessageId = replyingToMessage?.id,
                                            replyToMessageText = customQuoteText ?: replyingToMessage?.text
                                        )
                                        replyingToMessage = null
                                        customQuoteText = null
                                    }
                                    isVoiceRecording = false
                                }
                                recordingSeconds = 0
                            },
                            onCancelRecording = {
                                isVoiceRecording = false
                                isVideoRecording = false
                                recordingSeconds = 0
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
}
    ) { padding ->
        
        if (showQuoteSelector && replyingToMessage != null) {
            val fullText = replyingToMessage!!.text
            var selectedFragment by remember { mutableStateOf(customQuoteText ?: fullText) }
            ModalBottomSheet(
                onDismissRequest = { 
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showQuoteSelector = false 
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Цитировать фрагмент",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "Отредактируйте или выберите текст, который хотите процитировать",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = selectedFragment,
                        onValueChange = { selectedFragment = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                selectedFragment = fullText 
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Весь текст")
                        }
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                customQuoteText = if (selectedFragment.isNotBlank()) selectedFragment.trim() else fullText
                                showQuoteSelector = false
                                showReplyOptions = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Цитировать")
                        }
                    }
                }
            }
        }

        if (showReplyOptions) {
            ModalBottomSheet(
                onDismissRequest = { 
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showReplyOptions = false 
                },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Ответ на сообщение", style = MaterialTheme.typography.titleMedium)
                        Text("Вы можете процитировать фрагмент текста", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    replyingToMessage?.let { replyMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = replyMsg.senderId,
                                    style = MaterialTheme.typography.labelMedium, 
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                    text = customQuoteText ?: replyMsg.text, 
                                    style = MaterialTheme.typography.bodySmall, 
                                    maxLines = 3, 
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Выбрать фрагмент") },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Filled.Subject, contentDescription = null) },
                        modifier = Modifier.clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showQuoteSelector = true
                            showReplyOptions = false
                        }
                    )
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Ответить в другом чате") },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                        modifier = Modifier.clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showReplyOptions = false
                            showReplyChatPicker = true
                        }
                    )
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Применить изменения") },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Filled.Check, contentDescription = null) },
                        modifier = Modifier.clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showReplyOptions = false
                        }
                    )
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Убрать ответ", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showReplyOptions = false
                            replyingToMessage = null
                            customQuoteText = null
                        }
                    )
                }
            }
        }
        
        if (showForwardDraftOptions && currentForwardDraft != null) {
            var tempHideSender by remember(currentForwardDraft.hideSender) { mutableStateOf(currentForwardDraft.hideSender) }
            ModalBottomSheet(
                onDismissRequest = { 
                    viewModel.updateForwardDraftHideSender(chatId, tempHideSender)
                    showForwardDraftOptions = false 
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    val count = currentForwardDraft.messages.size
                    val headingText = if (count == 1) "Переслать 1 сообщение" else "Переслать $count сообщений"
                    val senderNameForSubtitle = currentForwardDraft.originalSenderName.ifBlank { "Получатель" }
                    Text(
                        headingText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = if (tempHideSender) "$senderNameForSubtitle не увидит, что сообщение переслано" else "$senderNameForSubtitle увидит, что сообщение переслано",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Telegram Interactive Message Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
                            .padding(16.dp)
                    ) {
                        val firstMsg = currentForwardDraft.messages.firstOrNull()?.first
                        Surface(
                            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (!tempHideSender) {
                                    val authTargetId = currentForwardDraft.originalSenderId ?: currentForwardDraft.sourceChatId
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(bottom = 4.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .then(
                                                if (authTargetId.isNotBlank()) {
                                                    Modifier.clickable {
                                                        showForwardDraftOptions = false
                                                        navController.navigate("profile/$authTargetId")
                                                    }
                                                } else Modifier
                                            )
                                            .padding(2.dp)
                                    ) {
                                        Text(
                                            "Переслано от ",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!currentForwardDraft.originalSenderAvatarUrl.isNullOrBlank()) {
                                                coil.compose.AsyncImage(
                                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                        .data(currentForwardDraft.originalSenderAvatarUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Avatar",
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                )
                                            }
                                            val initialLetter = (currentForwardDraft.originalSenderName.take(1).ifBlank { "U" }).uppercase()
                                            Text(
                                                text = initialLetter,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentForwardDraft.originalSenderName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                val textContent = when {
                                    firstMsg?.documentData != null -> "📄 Файл / Документ"
                                    firstMsg?.audioPath != null -> "🎤 Голосовое сообщение"
                                    firstMsg?.text?.isNotBlank() == true -> firstMsg.text
                                    else -> "Медиафайл"
                                }

                                Text(
                                    text = textContent,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(firstMsg?.timestamp ?: System.currentTimeMillis()))
                                    Text(
                                        text = "$timeStr  ✓✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Menu Options Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // 1. Скрыть / Показать имя отправителя
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempHideSender = !tempHideSender
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (tempHideSender) Icons.Filled.Visibility else Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = if (tempHideSender) "Показать имя отправителя" else "Скрыть имя отправителя",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            // 2. Изменить получателя
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateForwardDraftHideSender(chatId, tempHideSender)
                                        showForwardChangeRecipientPicker = true
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "Изменить получателя",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            // 3. Применить изменения
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateForwardDraftHideSender(chatId, tempHideSender)
                                        showForwardDraftOptions = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "Применить изменения",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            // 4. Не пересылать
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.clearForwardDraft(chatId)
                                        showForwardDraftOptions = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "Не пересылать",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showForwardChangeRecipientPicker && currentForwardDraft != null) {
            var forwardPickerSearch by remember { mutableStateOf("") }
            val filteredTargetChats = if (forwardPickerSearch.isBlank()) chats else chats.filter { it.title.contains(forwardPickerSearch, ignoreCase = true) }
            ModalBottomSheet(
                onDismissRequest = { 
                    showForwardChangeRecipientPicker = false 
                },
                modifier = Modifier.fillMaxHeight(0.9f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Переслать в...", 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    OutlinedTextField(
                        value = forwardPickerSearch,
                        onValueChange = { forwardPickerSearch = it },
                        placeholder = { Text("Поиск чатов") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Поиск") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(filteredTargetChats) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updatedDraft = currentForwardDraft.copy(targetChatId = c.id)
                                        viewModel.clearForwardDraft(chatId)
                                        viewModel.setForwardDraft(updatedDraft)
                                        showForwardChangeRecipientPicker = false
                                        showForwardDraftOptions = false
                                        if (c.id != chatId) {
                                            navController.navigate("chat/${c.id}")
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = c.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = c.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    )
                                    Text(
                                        text = if (c.isChannel) "Канал" else if (c.isGroup) "Группа" else "Личный чат",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showReplyChatPicker) {
            var replyPickerSearch by remember { mutableStateOf("") }
            val filteredChats = if (replyPickerSearch.isBlank()) chats else chats.filter { it.title.contains(replyPickerSearch, ignoreCase = true) }
            ModalBottomSheet(
                onDismissRequest = { 
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showReplyChatPicker = false 
                },
                modifier = Modifier.fillMaxHeight(0.9f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Ответить в...", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    OutlinedTextField(
                        value = replyPickerSearch,
                        onValueChange = { replyPickerSearch = it },
                        placeholder = { Text("Поиск чатов") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Поиск") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(filteredChats) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        val quoteToForward = replyingToMessage?.copy(
                                            text = customQuoteText ?: replyingToMessage!!.text
                                        ) ?: replyingToMessage
                                        viewModel.setReplyDraft(c.id, quoteToForward)
                                        showReplyChatPicker = false
                                        replyingToMessage = null
                                        customQuoteText = null
                                        navController.navigate("chat/${c.id}") {
                                            popUpTo("chat/${chatId}") { inclusive = true }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = c.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text(
                                        if (c.isChannel) "Канал" else if (c.isGroup) "Группа" else if (c.isBot) "Бот" else "Личный чат",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showScheduleDialog) {
            ScheduleMessageDialog(
                onDismissRequest = { showScheduleDialog = false },
                onSchedule = { timeInMillis ->
                    showScheduleDialog = false
                    val delay = timeInMillis - System.currentTimeMillis()
                    if (delay > 0) {
                        val workRequest = androidx.work.OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
                            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .setInputData(
                                androidx.work.workDataOf(
                                    "text" to inputText,
                                    "chatId" to chatId,
                                    "senderId" to (activeAccount?.id ?: "")
                                )
                            )
                            .build()
                        androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                        android.widget.Toast.makeText(context, "Message scheduled", android.widget.Toast.LENGTH_SHORT).show()
                        inputText = ""
                    } else {
                        android.widget.Toast.makeText(context, "Cannot schedule in the past", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        
        if (showFilePreviewDialog) {
            val isPhoto = selectedFileMime.startsWith("image/")
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { 
                    if (uploadProgressState !is com.example.data.UploadProgressState.Uploading && uploadProgressState !is com.example.data.UploadProgressState.Compressing) {
                        showFilePreviewDialog = false 
                    }
                },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPhoto) Icons.Filled.PhotoLibrary else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPhoto) "Отправить фото (Сжатие)" else "Отправить файл")
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isPhoto && selectedFileUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(compressionPreviewStats?.compressedUri ?: selectedFileUri)
                                        .crossfade(true)
                                        .allowHardware(false)
                                        .build(),
                                    contentDescription = "Selected Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )

                                // Real-time compression status chip over image
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Bolt,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        val stat = compressionPreviewStats
                                        if (isAnalyzingCompression) {
                                            Text("Оптимизация...", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        } else if (stat != null) {
                                            Text(
                                                "${stat.compressedWidth}x${stat.compressedHeight} • ${stat.compressedSizeBytes / 1024} KB (-${String.format("%.0f", stat.compressionRatioPercent)}%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        } else {
                                            Text("${selectedFileSize / 1024} KB", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))

                            // Compression Quality Preset Selector
                            Text(
                                "Качество и оптимизация сжатия:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                com.example.utils.ImageCompressionPreset.values().forEach { preset ->
                                    val isSelected = selectedCompressionPreset == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedCompressionPreset = preset },
                                        label = {
                                            Text(
                                                when (preset) {
                                                    com.example.utils.ImageCompressionPreset.BALANCED_AUTO -> "Telegram Fast (1280p)"
                                                    com.example.utils.ImageCompressionPreset.HIGH_QUALITY -> "HD (1920p)"
                                                    com.example.utils.ImageCompressionPreset.DATA_SAVER -> "Экономия (800p)"
                                                    com.example.utils.ImageCompressionPreset.ORIGINAL -> "Оригинал"
                                                },
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                        } else null
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Compression Savings Stats Card
                            compressionPreviewStats?.let { stat ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "⚡ Экономия трафика Firebase:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "-${String.format("%.1f", stat.compressionRatioPercent)}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Исходный: ${stat.originalSizeBytes / 1024} KB (${stat.originalWidth}x${stat.originalHeight}) ➔ Сжатый: ${stat.compressedSizeBytes / 1024} KB (${stat.compressedWidth}x${stat.compressedHeight})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Сэкономлено: ${stat.savedKilobytes} KB • Обработка за: ${stat.durationMs} мс",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        } else {
                            val icon = if (selectedFileMime.startsWith("video/")) Icons.Filled.VideoFile
                                else if (selectedFileMime.startsWith("audio/")) Icons.Filled.AudioFile
                                else Icons.Filled.InsertDriveFile
                                
                            Icon(
                                icon,
                                contentDescription = "File Icon",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(selectedFileName, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("Размер: ${selectedFileSize / 1024} KB", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                        }

                        // Upload progress indicator if active
                        when (val state = uploadProgressState) {
                            is com.example.data.UploadProgressState.Compressing -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                                Text("Сжатие изображения перед загрузкой...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                            }
                            is com.example.data.UploadProgressState.Uploading -> {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                )
                                Text("Загрузка в Firebase Storage (${state.bytesUploaded / 1024} / ${state.totalBytes / 1024} KB)...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                            }
                            else -> {}
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("Добавить подпись...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val uriToSend = selectedFileUri
                            val fileNameToSend = selectedFileName
                            val captionToSend = inputText
                            val isPhotoAttachment = isPhoto && uriToSend != null

                            showFilePreviewDialog = false
                            inputText = ""

                            if (isPhotoAttachment) {
                                viewModel.uploadAndSendPhoto(
                                    context = context,
                                    chatId = chatId,
                                    senderId = activeAccount?.id ?: "",
                                    imageUri = uriToSend!!,
                                    fileName = fileNameToSend,
                                    caption = captionToSend,
                                    preset = selectedCompressionPreset,
                                    expiresIn = expiresIn,
                                    replyToMessageId = replyingToMessage?.id,
                                    replyToMessageText = customQuoteText ?: replyingToMessage?.text
                                )
                            } else {
                                val documentJson = org.json.JSONObject().apply {
                                    put("uri", uriToSend.toString())
                                    put("name", fileNameToSend)
                                    put("size", selectedFileSize)
                                    put("mimeType", selectedFileMime)
                                }.toString()

                                viewModel.sendMessage(
                                    chatId = chatId,
                                    senderId = activeAccount?.id ?: "",
                                    text = captionToSend,
                                    audioPath = null,
                                    expiresIn = expiresIn,
                                    documentData = documentJson,
                                    replyToMessageId = replyingToMessage?.id,
                                    replyToMessageText = customQuoteText ?: replyingToMessage?.text
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Отправить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFilePreviewDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        if (showLightboxViewer && activeLightboxItems.isNotEmpty()) {
            LightboxImageViewerDialog(
                items = activeLightboxItems,
                initialIndex = lightboxInitialIndex,
                chatTitle = chat.title,
                onDismiss = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showLightboxViewer = false
                },
                onReply = { item ->
                    val msg = messages.find { it.id == item.messageId }
                    if (msg != null) {
                        replyingToMessage = msg
                        customQuoteText = null
                    }
                    showLightboxViewer = false
                },
                onShowInChat = { item ->
                    showLightboxViewer = false
                    val targetIndex = messages.indexOfFirst { it.id == item.messageId }
                    if (targetIndex != -1) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(targetIndex)
                        }
                    }
                },
                onDelete = { item ->
                    val msg = messages.find { it.id == item.messageId }
                    if (msg != null) {
                        viewModel.deleteMessage(msg.id)
                    }
                    showLightboxViewer = false
                }
            )
        }

        if (activeVideoNoteToView != null) {
            val vMsg = activeVideoNoteToView!!
            val senderName = when {
                vMsg.senderId == activeAccount?.id -> "Вы"
                chat.isGroup -> groupMembers.find { it.userId == vMsg.senderId }?.userName ?: vMsg.senderId
                else -> chat.title
            }
            var durText = "0:04"
            var videoPath: String? = vMsg.mediaPath
            var isCircular = true
            if (!vMsg.documentData.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(vMsg.documentData)
                    val dur = json.optString("duration", "")
                    if (dur.isNotBlank()) durText = dur
                    val uri = json.optString("uri", "")
                    if (uri.isNotBlank()) videoPath = uri
                    if (json.has("isRound")) {
                        isCircular = json.optBoolean("isRound", true)
                    } else if (json.optString("type", "") == "video_note") {
                        isCircular = true
                    } else {
                        val mime = json.optString("mimeType", "")
                        val name = json.optString("name", "")
                        if (mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm")) {
                            isCircular = false
                        }
                    }
                } catch (_: Exception) {}
            } else if (vMsg.text.contains("(") && vMsg.text.contains(")")) {
                durText = vMsg.text.substringAfter("(").substringBefore(")")
            }

            if (vMsg.mediaType == "video" || (videoPath != null && (videoPath.endsWith(".mp4") || videoPath.endsWith(".mov") || videoPath.endsWith(".webm")) && !vMsg.mediaType.equals("video_note", true) && !vMsg.text.startsWith("📹"))) {
                isCircular = false
            }

            com.example.ui.components.FullScreenVideoPlayerDialog(
                videoUri = videoPath,
                senderName = senderName,
                durationText = durText,
                timestamp = vMsg.timestamp,
                isCircular = isCircular,
                onDismiss = { activeVideoNoteToView = null },
                onReply = {
                    replyingToMessage = vMsg
                    customQuoteText = null
                    activeVideoNoteToView = null
                },
                onForward = if (!chat.isSecret) {
                    {
                        messagesToForward = listOf(vMsg)
                        activeVideoNoteToView = null
                    }
                } else null
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (!chat.isGroup && !chat.isChannel && !chat.isBot && !chat.isContact && !chat.isBlocked && !chat.isActionMenuDismissed) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.addToContacts(chatId) }) {
                            Text("Add to Contacts")
                        }
                        TextButton(
                            onClick = { viewModel.blockUser(chatId) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Block User")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.dismissActionMenu(chatId) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                }
            }
            if (chat.pinnedMessageId != null) {
                val pinnedMessage = messages.find { it.id == chat.pinnedMessageId }
                if (pinnedMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.8f), 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Pinned Message", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(pinnedMessage.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }



            val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
            val prevMessageCount = remember { mutableIntStateOf(0) }
            
            // Smart scroll handling for initial load, new messages, and prepending paginated history
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    if (prevMessageCount.intValue == 0) {
                        // Initial load -> scroll to bottom
                        listState.scrollToItem(messages.size - 1)
                    } else if (messages.size > prevMessageCount.intValue) {
                        val addedCount = messages.size - prevMessageCount.intValue
                        if (!listState.canScrollForward) {
                            // User is at the bottom and received a new message -> smooth scroll to bottom
                            listState.animateScrollToItem(messages.size - 1)
                        } else if (listState.firstVisibleItemIndex <= 1) {
                            // Prepended older history at top -> maintain relative viewing position
                            listState.scrollToItem(listState.firstVisibleItemIndex + addedCount, listState.firstVisibleItemScrollOffset)
                        }
                    }
                }
                prevMessageCount.intValue = messages.size
            }

            // Automatic infinite scroll load when scrolling near the top of history
            LaunchedEffect(listState, hasMoreMessages, isLoadingMore, isSearchMode) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .collect { index ->
                        if (index <= 1 && hasMoreMessages && !isLoadingMore && !isSearchMode && messages.isNotEmpty()) {
                            viewModel.loadMoreMessages(chatId)
                        }
                    }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Channel / Chat Custom Wallpaper Background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(channelWallpaper.previewGradient))
                )
                if (chat.isBot && filteredMessages.isEmpty() && !isSearchMode) {
                    val botObj = com.example.ui.botapi.BotRegistry.getBot(chatId) ?: com.example.ui.botapi.BotRegistry.getCustomBots().find { it.id == chatId }
                    val customBot = botObj as? com.example.ui.botapi.CustomBot
                    val description = customBot?.description?.takeIf { it.isNotBlank() } ?: "What can this bot do?"
                    val descriptionPictureUri = customBot?.descriptionPictureUri

                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (descriptionPictureUri != null && descriptionPictureUri.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(context).allowHardware(false)
                                    .data(descriptionPictureUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Bot description picture",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    reverseLayout = false
                ) {
                    if (hasMoreMessages && !isSearchMode) {
                        item(key = "pagination_history_header") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Загрузка истории...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { viewModel.loadMoreMessages(chatId) },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val remaining = totalMessageCount - messages.size
                                        Text(
                                            text = if (remaining > 0) "Предыдущие сообщения ($remaining)" else "Загрузить ещё",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(filteredMessages, key = { it.id }) { message ->
                        val isMe = message.senderId == activeAccount?.id
                        val visibleState = androidx.compose.runtime.remember(message.id) { 
                            androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } 
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = visibleState,
                            enter = androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                            ) + androidx.compose.animation.scaleIn(
                                initialScale = 0.85f,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (isMe) 1f else 0f, 1f),
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                            ) + androidx.compose.animation.slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                            ),
                            exit = androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.spring(
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                )
                            ) + androidx.compose.animation.scaleOut(
                                targetScale = 0.85f,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (isMe) 1f else 0f, 1f),
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                )
                            ) + androidx.compose.animation.shrinkVertically(
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                            ),
                            modifier = Modifier.animateItem(
                                fadeInSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                ),
                                fadeOutSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                ),
                                placementSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                            )
                        ) {
                        var senderName: String? = null
                        var senderStatus: String? = null
                        var isBot = false
                        if (!isMe) {
                            if (chat.isGroup) {
                                val member = groupMembers.find { it.userId == message.senderId }
                                senderName = member?.userName ?: message.senderId
                                isBot = message.senderId.startsWith("bot_")
                            } else {
                                if (chat.isBot) {
                                    senderName = chat.title
                                    isBot = true
                                } else {
                                    senderName = chat.title
                                }
                            }
                        } else {
                            senderName = activeAccount?.displayName
                            senderStatus = activeAccount?.customStatus
                        }
                        val isSelected = selectedMessageIds.contains(message.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelectionMode) {
                                        Modifier.clickable {
                                            selectedMessageIds = if (isSelected) {
                                                selectedMessageIds - message.id
                                            } else {
                                                selectedMessageIds + message.id
                                            }
                                        }
                                    } else Modifier
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp)
                                        .background(
                                            if (isSelected) Color(0xFF4CAF50) else Color.Transparent,
                                            CircleShape
                                        )
                                        .border(
                                            2.dp,
                                            if (isSelected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Выбрано",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val matchedPoll = remember(message.id, channelPolls, message.documentData, message.text) {
                                    if (message.documentData?.contains("\"type\":\"telegram_poll\"") == true) {
                                        try {
                                            val json = org.json.JSONObject(message.documentData)
                                            val pollId = json.optString("pollId")
                                            channelPolls.find { it.id == pollId }
                                        } catch (_: Exception) { null }
                                    } else if (message.text.startsWith("📊 [Опрос]")) {
                                        val q = message.text.removePrefix("📊 [Опрос]").trim()
                                        channelPolls.find { it.question == q }
                                    } else {
                                        channelPolls.find { it.id == message.id }
                                    }
                                }

                                if (matchedPoll != null) {
                                    PollMessageView(
                                        poll = matchedPoll,
                                        chatId = chatId,
                                        onVote = { optIndex ->
                                            ChannelCustomizationManager.votePoll(chatId, matchedPoll.id, optIndex, matchedPoll.isMultipleChoice)
                                        },
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    MessageBubble(
                                        senderName = senderName,
                                        senderStatus = senderStatus,
                                        isBot = isBot,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        message = message, 
                                        isMe = isMe,
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedMessageIds = if (isSelected) selectedMessageIds - message.id else selectedMessageIds + message.id
                                            } else {
                                                showReactionDialogFor = message.id
                                            }
                                        },
                                        onLongClick3s = {
                                            if (isSelectionMode) {
                                                selectedMessageIds = if (isSelected) selectedMessageIds - message.id else selectedMessageIds + message.id
                                            } else {
                                                selectedMessageIds = setOf(message.id)
                                            }
                                        },
                                        onAuthorClick = { authorId ->
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            navController.navigate("profile/$authorId")
                                        },
                                        onButtonClick = { buttonText ->
                                            if (buttonText.startsWith("Sandbox::")) {
                                                val botId = buttonText.substringAfter("::")
                                                navController.navigate("sandbox/$botId")
                                            } else if (buttonText.startsWith("Dashboard::")) {
                                                val botId = buttonText.substringAfter("::")
                                                navController.navigate("dashboard/$botId")
                                            } else {
                                                if (chat.isBot || chat.id == "botfather") {
                                                    viewModel.handleInlineButtonClick(chatId, buttonText, message.id)
                                                } else {
                                                    viewModel.sendMessage(chatId, activeAccount?.id ?: "", buttonText, null, expiresIn)
                                                }
                                            }
                                        },
                                        onImageClick = { clickedItem ->
                                            val allChatImages = messages.mapNotNull { msg ->
                                                val msgSender = when {
                                                    msg.senderId == activeAccount?.id -> "Вы"
                                                    chat.isGroup -> groupMembers.find { it.userId == msg.senderId }?.userName ?: msg.senderId
                                                    else -> chat.title
                                                }
                                                extractLightboxMediaItem(msg, msgSender, msg.senderId == activeAccount?.id)
                                            }
                                            val clickedIndex = allChatImages.indexOfFirst { it.messageId == clickedItem.messageId }.coerceAtLeast(0)
                                            activeLightboxItems = if (allChatImages.isNotEmpty()) allChatImages else listOf(clickedItem)
                                            lightboxInitialIndex = clickedIndex
                                            showLightboxViewer = true
                                        },
                                        onOpenVideoNote = {
                                            activeVideoNoteToView = message
                                        }
                                    )
                                }
                            }
                        }
                        }
                    }
                }
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = canScrollForward,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    enter = androidx.compose.animation.scaleIn(),
                    exit = androidx.compose.animation.scaleOut()
                ) {
                    FloatingActionButton(
                        onClick = { 
                            coroutineScope.launch { 
                                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) 
                            } 
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                    }
                }
                }
            }
        }
        
        
        if (showSearchFilters) {
            AlertDialog(
                onDismissRequest = { showSearchFilters = false },
                title = { Text("Search Filters") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchSender,
                            onValueChange = { searchSender = it },
                            label = { Text("Sender ID/Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = searchMediaOnly,
                                onCheckedChange = { searchMediaOnly = it }
                            )
                            Text("Has Media (Photos/Voice/Files)")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { searchStartDate = if (searchStartDate == null) System.currentTimeMillis() - 86400000L * 7 else null }) {
                                Text(if (searchStartDate == null) "Start: 7 Days Ago" else "Clear Start")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { searchEndDate = if (searchEndDate == null) System.currentTimeMillis() else null }) {
                                Text(if (searchEndDate == null) "End: Now" else "Clear End")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSearchFilters = false }) { Text("Apply") }
                }
            )
        }
        
        if (showDisappearingDialog) {
            AlertDialog(
                onDismissRequest = { showDisappearingDialog = false },
                title = { Text("Disappearing Messages") },
                text = {
                    Column {
                        Text("Set a timer for new messages to disappear automatically.")
                        Spacer(Modifier.height(8.dp))
                        val options = listOf(
                            "10 Seconds" to 10_000L,
                            "1 Minute" to 60_000L,
                            "1 Hour" to 3_600_000L,
                            "1 Day" to 86_400_000L,
                            "1 Week" to 604_800_000L
                        )
                        options.forEach { (label, duration) ->
                            TextButton(onClick = { 
                                expiresIn = duration
                                showDisappearingDialog = false
                            }) { Text(label) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { 
                        expiresIn = null
                        showDisappearingDialog = false
                    }) { Text("Off") }
                }
            )
        }
        
        if (showReactionDialogFor != null) {
            val selectedMessage = messages.find { it.id == showReactionDialogFor }
            AlertDialog(
                onDismissRequest = { showReactionDialogFor = null },
                title = { Text("Действия с сообщением") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
                            for (emoji in emojis) {
                                Text(
                                    text = emoji,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .combinedClickable(onClick = {
                                            viewModel.addReaction(showReactionDialogFor!!, emoji)
                                            showReactionDialogFor = null
                                        }),
                                    style = LocalTextStyle.current.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp))
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        
                        // Reply
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    replyingToMessage = selectedMessage
                                    customQuoteText = null
                                    showReactionDialogFor = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Ответить", style = MaterialTheme.typography.bodyLarge)
                        }
                        
                        // Copy
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMessage?.let {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it.text))
                                    }
                                    android.widget.Toast.makeText(context, "Текст скопирован", android.widget.Toast.LENGTH_SHORT).show()
                                    showReactionDialogFor = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Копировать", style = MaterialTheme.typography.bodyLarge)
                        }

                        // Copy link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("https://neon.im/c/${chatId}/${showReactionDialogFor}"))
                                    android.widget.Toast.makeText(context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT).show()
                                    showReactionDialogFor = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Копировать ссылку", style = MaterialTheme.typography.bodyLarge)
                        }

                        // Forward
                        if (!chat.isSecret) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMessage?.let {
                                            messagesToForward = listOf(it)
                                        }
                                        showReactionDialogFor = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text("Переслать", style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        // Select (Telegram style)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMessageIds = setOf(showReactionDialogFor!!)
                                    showReactionDialogFor = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Выбрать", style = MaterialTheme.typography.bodyLarge)
                        }

                        // Pin/Unpin
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedMessage?.isPinned == true) {
                                        viewModel.unpinMessage(chatId, showReactionDialogFor!!)
                                    } else {
                                        viewModel.pinMessage(chatId, showReactionDialogFor!!)
                                    }
                                    showReactionDialogFor = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(if (selectedMessage?.isPinned == true) "Открепить сообщение" else "Закрепить сообщение", style = MaterialTheme.typography.bodyLarge)
                        }

                        // Report message (Telegram moderation feature)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    messageToReport = selectedMessage
                                    showReactionDialogFor = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ReportProblem, contentDescription = "Пожаловаться", tint = Color(0xFFFF5252))
                            Spacer(Modifier.width(12.dp))
                            Text("Пожаловаться", color = Color(0xFFFF5252), style = MaterialTheme.typography.bodyLarge)
                        }

                        // Delete
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.deleteMessage(showReactionDialogFor!!)
                                    showReactionDialogFor = null
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text("Удалить сообщение", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (messageToReport != null) {
            val targetMsg = messageToReport!!
            val msgSenderName = when {
                targetMsg.senderId == "1" || targetMsg.senderId == "123456789" -> "Вы"
                targetMsg.senderId == "botfather" -> "BotFather"
                targetMsg.senderId == "c3" -> "SynthBot"
                targetMsg.senderId == "456789123" -> "Cyber P."
                targetMsg.senderId == "987654321" -> "Synth Wave"
                else -> "Пользователь (${targetMsg.senderId.take(8)})"
            }
            val msgSenderUsername = when {
                targetMsg.senderId == "1" || targetMsg.senderId == "123456789" -> "@neo_hacker"
                targetMsg.senderId == "botfather" -> "@BotFather"
                targetMsg.senderId == "c3" -> "@SynthBot"
                targetMsg.senderId == "456789123" -> "@cyber_punk"
                targetMsg.senderId == "987654321" -> "@synth_wave"
                else -> "@user_${targetMsg.senderId.takeLast(4)}"
            }

            ReportMessageDialog(
                messageText = targetMsg.text,
                senderDisplayName = msgSenderName,
                onDismiss = { messageToReport = null },
                onSubmitReport = { reasonCategory, userComment ->
                    viewModel.submitContentReport(
                        messageId = targetMsg.id,
                        chatId = chatId,
                        senderId = targetMsg.senderId,
                        senderDisplayName = msgSenderName,
                        senderUsername = msgSenderUsername,
                        messageText = targetMsg.text,
                        reasonCategory = reasonCategory,
                        userComment = userComment
                    )
                    android.widget.Toast.makeText(
                        context,
                        "Жалоба отправлена в панель администратора. Спасибо!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    messageToReport = null
                }
            )
        }

        if (showBatchDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showBatchDeleteDialog = false },
                title = { Text("Удалить ${selectedMessageIds.size} сообщений") },
                text = { Text("Вы точно хотите удалить эти сообщения?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteMessages(selectedMessageIds.toList())
                            selectedMessageIds = emptySet()
                            showBatchDeleteDialog = false
                        }
                    ) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatchDeleteDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        if (showClearHistoryDialog) {
            com.example.ui.channel.TelegramClearHistoryDialog(
                channelTitle = chat.title,
                onDismissRequest = { showClearHistoryDialog = false },
                onConfirm = {
                    showClearHistoryDialog = false
                    viewModel.clearHistory(chatId)
                    android.widget.Toast.makeText(context, "История очищена", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showLeaveChannelDialog) {
            com.example.ui.channel.TelegramLeaveDiscussionDialog(
                chatTitle = chat.title,
                isChannel = chat.isChannel,
                onDismissRequest = { showLeaveChannelDialog = false },
                onConfirm = {
                    showLeaveChannelDialog = false
                    viewModel.deleteChat(chatId)
                    navController.popBackStack()
                    android.widget.Toast.makeText(context, "Вы покинули чат", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showChannelMessagesDialog) {
            var channelMsgText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showChannelMessagesDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сообщения каналу")
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "${chat.title} принимает сообщения от подписчиков и предложения для публикаций.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = channelMsgText,
                            onValueChange = { channelMsgText = it },
                            placeholder = { Text("Напишите сообщение или вопрос каналу...") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                            maxLines = 4
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (channelMsgText.isNotBlank()) {
                                viewModel.sendMessage(
                                    chatId = chatId,
                                    senderId = activeAccount?.id ?: "me",
                                    text = "📨 [Сообщение каналу]: $channelMsgText"
                                )
                                showChannelMessagesDialog = false
                                android.widget.Toast.makeText(context, "Сообщение отправлено каналу", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Отправить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChannelMessagesDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        if (showBotReportDialog) {
            BotReportBottomSheet(
                botName = chat.title,
                botUsername = "@${chat.id}",
                onDismissRequest = { showBotReportDialog = false },
                onSubmitReport = { reasonCategory, userComment, shouldBlock ->
                    viewModel.submitContentReport(
                        messageId = "bot_${chat.id}_${System.currentTimeMillis()}",
                        chatId = chat.id,
                        senderId = chat.id,
                        senderDisplayName = chat.title,
                        senderUsername = "@${chat.id}",
                        messageText = "[Жалоба на чат бота]: ${chat.title}",
                        reasonCategory = reasonCategory,
                        userComment = userComment
                    )
                    if (shouldBlock) {
                        viewModel.blockUser(chat.id)
                        viewModel.deleteChat(chat.id)
                        navController.popBackStack()
                    }
                    android.widget.Toast.makeText(context, "Жалоба отправлена модераторам", android.widget.Toast.LENGTH_SHORT).show()
                    showBotReportDialog = false
                }
            )
        }

        if (showBotPrivacyPolicyDialog) {
            BotPrivacyPolicyBottomSheet(
                botName = chat.title,
                botUsername = "@${chat.id}",
                onDismissRequest = { showBotPrivacyPolicyDialog = false }
            )
        }

        if (showBotDeleteAndBlockDialog) {
            BotDeleteAndBlockDialog(
                botName = chat.title,
                botUsername = "@${chat.id}",
                onDismissRequest = { showBotDeleteAndBlockDialog = false },
                onConfirm = {
                    viewModel.blockUser(chat.id)
                    viewModel.deleteChat(chat.id)
                    android.widget.Toast.makeText(context, "Бот ${chat.title} заблокирован", android.widget.Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            )
        }

        if (showBotAutoDeleteDialog) {
            TelegramAutoDeleteWheelBottomSheet(
                currentValue = botAutoDeletePeriod,
                onDismissRequest = { showBotAutoDeleteDialog = false },
                onApply = { period ->
                    botAutoDeletePeriod = period
                    android.widget.Toast.makeText(
                        context,
                        if (period != null) "Автоудаление установлено: $period" else "Автоудаление выключено",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        
        if (messagesToForward.isNotEmpty()) {
            val forwardMsgs = messagesToForward.sortedBy { it.timestamp }
            var forwardSearch by remember { mutableStateOf("") }
            var forwardSelectedTargetChat by remember { mutableStateOf<Chat?>(null) }
            var forwardHideSender by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = { 
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    messagesToForward = emptyList() 
                    forwardSelectedTargetChat = null
                    forwardHideSender = false
                },
                modifier = Modifier.fillMaxHeight(0.92f),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = forwardSelectedTargetChat,
                    label = "ForwardFlowAnimation"
                ) { targetChat ->
                    if (targetChat == null) {
                        // --- STEP 1: Выбор получателя ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = "Переслать...",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            
                            OutlinedTextField(
                                value = forwardSearch,
                                onValueChange = { forwardSearch = it },
                                placeholder = { Text("Поиск чатов") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Поиск") },
                                trailingIcon = {
                                    if (forwardSearch.isNotEmpty()) {
                                        IconButton(onClick = { forwardSearch = "" }) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val filteredForwardChats = remember(forwardSearch, chats) {
                                if (forwardSearch.isBlank()) chats else chats.filter { it.title.contains(forwardSearch, ignoreCase = true) }
                            }

                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                // "Избранное" (Saved Messages) top item
                                if (forwardSearch.isBlank() || "избранное".contains(forwardSearch, ignoreCase = true)) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    forwardSelectedTargetChat = Chat(
                                                        id = "saved_messages",
                                                        title = "Избранное",
                                                        lastMessage = ""
                                                    )
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(Color(0xFF9C27B0), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Filled.Bookmark,
                                                    contentDescription = "Избранное",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Избранное",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                                )
                                                Text(
                                                    "Сохранить на память",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                items(filteredForwardChats) { c ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                forwardSelectedTargetChat = c
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    when {
                                                        c.isChannel -> Color(0xFFE65100)
                                                        c.isGroup -> Color(0xFF00897B)
                                                        c.isBot -> Color(0xFF3949AB)
                                                        else -> MaterialTheme.colorScheme.primaryContainer
                                                    },
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (c.isBot) {
                                                Icon(
                                                    Icons.Filled.SmartToy,
                                                    contentDescription = "Bot",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = c.title.take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = if (c.isChannel || c.isGroup) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                c.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                when {
                                                    c.isChannel -> "Канал"
                                                    c.isGroup -> "Группа"
                                                    c.isBot -> "Бот"
                                                    else -> "Личный чат"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // --- STEP 2: Интерактивный предпросмотр сообщений ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 24.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { forwardSelectedTargetChat = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад к выбору чата")
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (forwardMsgs.size == 1) "Переслать 1 сообщение" else "Переслать ${forwardMsgs.size} сообщений",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        text = "в ${targetChat.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Preview Container
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(forwardMsgs) { singleMsg ->
                                        val singleAuthorName = if (singleMsg.isForwarded && !singleMsg.forwardOriginalSenderName.isNullOrBlank()) {
                                            singleMsg.forwardOriginalSenderName
                                        } else if (singleMsg.senderId == activeAccount?.id) {
                                            activeAccount?.displayName?.ifBlank { activeAccount?.username } ?: "Вы"
                                        } else {
                                            chat.title
                                        }

                                        Column {
                                            // Header: "Переслано от"
                                            if (!forwardHideSender) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Forward,
                                                        contentDescription = "Forwarded",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = "Переслано от ",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = singleAuthorName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            // Message preview bubble
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .height(IntrinsicSize.Min)
                                                        .padding(8.dp)
                                                ) {
                                                    if (!forwardHideSender) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(3.dp)
                                                                .fillMaxHeight()
                                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        if (singleMsg.documentData != null) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Filled.Description, contentDescription = "Документ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Файл / Документ", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                                            }
                                                        } else if (singleMsg.audioPath != null) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Filled.PlayArrow, contentDescription = "Аудио", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Голосовое сообщение", style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                                            }
                                                        } else {
                                                            Text(
                                                                text = singleMsg.text,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                maxLines = 3,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Options Controls (Telegram style)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { forwardHideSender = !forwardHideSender }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            if (forwardHideSender) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = if (forwardHideSender) "Показать имя отправителя" else "Скрыть имя отправителя",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = forwardHideSender,
                                            onCheckedChange = { forwardHideSender = it }
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { forwardSelectedTargetChat = null }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.SwapHoriz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = "Изменить получателя",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bottom Send / Cancel Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        messagesToForward = emptyList()
                                        forwardSelectedTargetChat = null
                                        forwardHideSender = false
                                    }
                                ) {
                                    Text("Отмена")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        val destChat = targetChat
                                        val forwardItems = forwardMsgs.map { m ->
                                            val authorName = if (m.isForwarded && !m.forwardOriginalSenderName.isNullOrBlank()) {
                                                m.forwardOriginalSenderName
                                            } else if (m.senderId == activeAccount?.id) {
                                                activeAccount?.displayName?.ifBlank { activeAccount?.username } ?: "Вы"
                                            } else {
                                                chat.title
                                            }
                                            Pair(m, authorName)
                                        }

                                        val firstMsg = forwardMsgs.firstOrNull()
                                        val primarySenderName = if (firstMsg?.isForwarded == true && !firstMsg.forwardOriginalSenderName.isNullOrBlank()) {
                                            firstMsg.forwardOriginalSenderName
                                        } else if (firstMsg?.senderId == activeAccount?.id) {
                                            activeAccount?.displayName?.ifBlank { activeAccount?.username } ?: "Вы"
                                        } else {
                                            chat.title
                                        }

                                        val primarySenderUsername = if (firstMsg?.senderId == activeAccount?.id) {
                                            activeAccount?.username
                                        } else if (chat.id == firstMsg?.senderId) {
                                            chat.title.lowercase().replace(" ", "_")
                                        } else null

                                        val botObj = com.example.ui.botapi.BotRegistry.getBot(chat.id) ?: com.example.ui.botapi.BotRegistry.getCustomBots().find { it.id == chat.id }
                                        val customBot = botObj as? com.example.ui.botapi.CustomBot
                                        val avatarUrl = if (firstMsg?.senderId == activeAccount?.id) {
                                            activeAccount?.profilePicUrl?.takeIf { it.isNotBlank() }
                                        } else {
                                            customBot?.botPicUri?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/seed/${chat.id}/100"
                                        }

                                        val primarySenderId = if (firstMsg?.isForwarded == true && !firstMsg.forwardOriginalSenderId.isNullOrBlank()) {
                                            firstMsg.forwardOriginalSenderId
                                        } else if (firstMsg?.senderId == activeAccount?.id) {
                                            activeAccount?.id
                                        } else {
                                            chat.id
                                        }

                                        val draft = ForwardDraft(
                                            targetChatId = destChat.id,
                                            sourceChatId = chatId,
                                            messages = forwardItems,
                                            originalSenderAvatarUrl = avatarUrl,
                                            originalSenderName = primarySenderName,
                                            originalSenderUsername = primarySenderUsername,
                                            originalSenderId = primarySenderId,
                                            hideSender = forwardHideSender
                                        )
                                        viewModel.setForwardDraft(draft)
                                        selectedMessageIds = emptySet()
                                        messagesToForward = emptyList()
                                        forwardSelectedTargetChat = null
                                        forwardHideSender = false

                                        if (destChat.id != chatId) {
                                            navController.navigate("chat/${destChat.id}")
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Далее")
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Notifications: Duration Picker Bottom Sheet ---
        if (showMuteDurationPicker) {
            val durationOptions = remember {
                listOf(
                    "30 мин.", "1 час", "2 часа", "3 часа", "8 часов",
                    "1 день", "2 дня", "3 дня", "4 дня", "5 дней", "6 дней",
                    "1 нед.", "2 нед.", "3 нед.",
                    "1 месяц", "2 месяца", "3 месяца", "6 месяцев", "1 год"
                )
            }
            ModalBottomSheet(
                onDismissRequest = { 
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showMuteDurationPicker = false 
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Выключить уведомления на...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(durationOptions) { duration ->
                            val isSelected = selectedMuteDuration == duration
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        else Color.Transparent
                                    )
                                    .clickable { selectedMuteDuration = duration }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedMuteDuration = duration },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = duration,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.toggleMute(chatId, true)
                            showMuteDurationPicker = false
                            android.widget.Toast.makeText(context, "Уведомления выключены на $selectedMuteDuration.", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Подтвердить", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                }
            }
        }

        // --- Notifications: Custom Notification Settings Screen / BottomSheet ---
        if (showCustomNotificationSettings) {
            ModalBottomSheet(
                onDismissRequest = { 
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showCustomNotificationSettings = false 
                },
                modifier = Modifier.fillMaxHeight(0.92f),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showCustomNotificationSettings = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                text = "Особые уведомления",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        // Section: Основные
                        item {
                            Text(
                                text = "Основные",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Показывать текст", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = notifShowText,
                                    onCheckedChange = { notifShowText = it }
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("История", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = notifShowHistory,
                                    onCheckedChange = { notifShowHistory = it }
                                )
                            }
                        }
                        item {
                            ListItem(
                                headlineContent = { Text("Звук") },
                                supportingContent = { Text(notifSound, color = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { showSoundPicker = true }
                            )
                        }
                        item {
                            ListItem(
                                headlineContent = { Text("Вибросигнал") },
                                supportingContent = { Text(notifVibration, color = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { showVibrationPicker = true }
                            )
                        }
                        item {
                            ListItem(
                                headlineContent = { Text("Приоритет") },
                                supportingContent = { Text(notifPriority, color = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { showPriorityPicker = true }
                            )
                        }
                        item {
                            Text(
                                text = "Уведомления с максимальным приоритетом будут срабатывать, даже когда устройство в режиме «Не беспокоить».",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        // Section: Всплывающие уведомления
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Всплывающие уведомления",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { notifPopupEnabled = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = notifPopupEnabled,
                                    onClick = { notifPopupEnabled = true }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Включены", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { notifPopupEnabled = false }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !notifPopupEnabled,
                                    onClick = { notifPopupEnabled = false }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Отключены", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        item {
                            Text(
                                text = "Новые сообщения от этого контакта будут всплывать поверх экрана, когда Вы не в приложении.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        // Section: Звонки
                        if (!chat.isChannel && !chat.isGroup) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Звонки",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            item {
                                ListItem(
                                    headlineContent = { Text("Вибросигнал") },
                                    supportingContent = { Text(notifCallVibration, color = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { showVibrationPicker = true }
                                )
                            }
                            item {
                                ListItem(
                                    headlineContent = { Text("Рингтон") },
                                    supportingContent = { Text(notifCallRingtone, color = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { showSoundPicker = true }
                                )
                            }
                            item {
                                Text(
                                    text = "Вы можете настроить рингтон, который используется, когда этот контакт звонит Вам в мессенджере.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Section: Светодиод
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Светодиод",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Цвет", style = MaterialTheme.typography.bodyLarge)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(notifLedColor, CircleShape)
                                )
                            }
                        }
                        item {
                            Text(
                                text = "Маленький мерцающий индикатор на некоторых устройствах, используемый для уведомления о новых сообщениях.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        // Section: Сбросить настройки
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        notifShowText = true
                                        notifShowHistory = false
                                        notifSound = "По умолчанию"
                                        notifVibration = "По умолчанию"
                                        notifPriority = "Как в настройках"
                                        notifPopupEnabled = false
                                        notifCallVibration = "По умолчанию"
                                        notifCallRingtone = "По умолчанию"
                                        android.widget.Toast.makeText(context, "Настройки сброшены", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Сбросить настройки",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        // Sub-dialog: Вибросигнал
        if (showVibrationPicker) {
            val vibOptions = listOf("По умолчанию", "Короткий", "Долгий", "Откл.")
            AlertDialog(
                onDismissRequest = { showVibrationPicker = false },
                title = { Text("Вибросигнал") },
                text = {
                    Column {
                        vibOptions.forEach { opt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        notifVibration = opt
                                        notifCallVibration = opt
                                        showVibrationPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = notifVibration == opt,
                                    onClick = {
                                        notifVibration = opt
                                        notifCallVibration = opt
                                        showVibrationPicker = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(opt, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showVibrationPicker = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Sub-dialog: Приоритет
        if (showPriorityPicker) {
            val priorityOptions = listOf("Как в настройках", "Низкий", "Средний", "Высокий", "Срочный")
            AlertDialog(
                onDismissRequest = { showPriorityPicker = false },
                title = { Text("Приоритет") },
                text = {
                    Column {
                        priorityOptions.forEach { opt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        notifPriority = opt
                                        showPriorityPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = notifPriority == opt,
                                    onClick = {
                                        notifPriority = opt
                                        showPriorityPicker = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(opt, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPriorityPicker = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Sub-dialog: Звук уведомлений
        if (showSoundPicker) {
            val soundOptions = listOf(
                "Без звука", "По умолчанию", "Beckon", "Bell", "Bell Interest",
                "Boiling", "Bubble", "Celesta", "Chess", "Clockwork",
                "Consonance", "Copper", "Delay", "Digital Decoder", "Doorbell",
                "Ecoin", "Flash", "Flute", "Frog Fun", "Grainy"
            )
            AlertDialog(
                onDismissRequest = { showSoundPicker = false },
                title = { Text("Звук уведомлений") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(soundOptions) { sound ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        notifSound = sound
                                        notifCallRingtone = sound
                                        showSoundPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = notifSound == sound,
                                    onClick = {
                                        notifSound = sound
                                        notifCallRingtone = sound
                                        showSoundPicker = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(sound, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSoundPicker = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        if (showCreatePollDialog) {
            CreateTelegramPollDialog(
                chatId = chatId,
                onDismiss = { showCreatePollDialog = false },
                onPollCreated = { newPoll ->
                    showCreatePollDialog = false
                    val pollDoc = org.json.JSONObject().apply {
                        put("type", "telegram_poll")
                        put("pollId", newPoll.id)
                        put("question", newPoll.question)
                        put("isQuiz", newPoll.isQuiz)
                    }.toString()
                    viewModel.sendMessage(
                        chatId = chatId,
                        senderId = activeAccount?.id ?: "",
                        text = "📊 [Опрос] ${newPoll.question}",
                        audioPath = null,
                        expiresIn = expiresIn,
                        documentData = pollDoc
                    )
                }
            )
        }

        if (isVideoRecording) {
            com.example.ui.components.TelegramVideoNoteRecordingOverlay(
                recordingSeconds = recordingSeconds,
                onCancel = {
                    isVideoRecording = false
                    recordingSeconds = 0
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: Message, 
    isMe: Boolean, 
    senderName: String? = null, 
    senderStatus: String? = null, 
    isBot: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit, 
    onLongClick3s: () -> Unit, 
    onAuthorClick: ((String) -> Unit)? = null,
    onButtonClick: ((String) -> Unit)? = null,
    onImageClick: ((LightboxMediaItem) -> Unit)? = null,
    onOpenVideoNote: (() -> Unit)? = null
) {
    val isVideoNote = message.mediaType == "video_note" ||
            (message.documentData != null && message.documentData.contains("\"video_note\"")) ||
            (message.text.startsWith("📹") && (message.mediaPath != null || message.documentData != null || message.text.contains("Видеосообщение")))

    if (isVideoNote) {
        var durationText = "0:04"
        if (!message.documentData.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(message.documentData)
                val dur = json.optString("duration", "")
                if (dur.isNotBlank()) durationText = dur
            } catch (_: Exception) {}
        } else if (message.text.contains("(") && message.text.contains(")")) {
            durationText = message.text.substringAfter("(").substringBefore(")")
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (senderName != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp, start = if(isMe) 0.dp else 8.dp, end = if(isMe) 8.dp else 0.dp)) {
                    if (isBot) {
                        Icon(Icons.Filled.SmartToy, contentDescription = "Bot", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                        Text(senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        if (senderStatus != null && senderStatus.isNotEmpty()) {
                            Text(senderStatus, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            com.example.ui.components.TelegramRoundVideoBubble(
                durationText = durationText,
                timestamp = message.timestamp,
                isMe = isMe,
                isDelivered = message.isDelivered,
                isRead = message.isRead,
                isE2EEncrypted = message.isE2EEncrypted,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onToggleSelect = onClick,
                onOpenViewer = onOpenVideoNote,
                onLongClick = onLongClick3s
            )
        }
        return
    }

    val lightboxItem = remember(message, senderName, isMe) {
        extractLightboxMediaItem(message, senderName, isMe)
    }

    val messageAccessibilityDescription = remember(message, senderName, isMe, lightboxItem) {
        buildString {
            if (isMe) append("Вы: ") else if (!senderName.isNullOrBlank()) append("$senderName: ")
            if (message.isPinned) append("Закрепленное сообщение. ")
            if (message.isForwarded && !message.forwardOriginalSenderName.isNullOrBlank()) {
                append("Переслано от ${message.forwardOriginalSenderName}. ")
            }
            if (message.replyToMessageText != null) {
                append("В ответ на: ${message.replyToMessageText}. ")
            }
            if (lightboxItem != null) {
                append("Фотография")
                if (lightboxItem.caption.isNotBlank()) append(": ${lightboxItem.caption}")
                append(". ")
            } else if (message.documentData != null) {
                try {
                    val json = org.json.JSONObject(message.documentData)
                    val type = json.optString("type")
                    val name = json.optString("name", "Файл")
                    val size = json.optLong("size", 0L)
                    if (type == "voice_note") {
                        append("Голосовое сообщение. ")
                    } else if (type == "video_note") {
                        append("Видеосообщение. ")
                    } else {
                        append("Файл $name, ${size / 1024} КБ. ")
                    }
                } catch (_: Exception) {
                    append("Файл. ")
                }
            } else if (message.audioPath != null) {
                append("Аудиосообщение: ${message.text}. ")
            } else if (message.text.isNotBlank()) {
                append("${message.text}. ")
            }
            if (message.reaction != null) {
                append("Реакция: ${message.reaction}. ")
            }
            append("Время: ${formatTime(message.timestamp)}. ")
            if (message.expiresAt != null) {
                append("Исчезающее. ")
            }
            if (isMe) {
                if (message.isRead) append("Прочитано.")
                else if (message.isDelivered) append("Доставлено.")
                else append("В очереди.")
            }
            if (message.isE2EEncrypted) {
                append(" Сквозное шифрование.")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (senderName != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp, start = if(isMe) 0.dp else 8.dp, end = if(isMe) 8.dp else 0.dp)) {
                if (isBot) {
                    Icon(Icons.Filled.SmartToy, contentDescription = "Бот", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                    Text(senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (senderStatus != null && senderStatus.isNotEmpty()) {
                        Text(senderStatus, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .semantics(mergeDescendants = true) {
                    contentDescription = messageAccessibilityDescription
                    if (isSelectionMode) {
                        selected = isSelected
                        role = Role.Checkbox
                    }
                }
                .background(
                    if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(
                        topStart = 16.dp, 
                        topEnd = 16.dp, 
                        bottomStart = if (isMe) 16.dp else 0.dp, 
                        bottomEnd = if (isMe) 0.dp else 16.dp
                    )
                )
                .telegramMessageGestures(
                    onClick = if (lightboxItem != null) {
                        {
                            if (isSelectionMode) {
                                onClick()
                            } else {
                                onImageClick?.invoke(lightboxItem)
                            }
                            Unit
                        }
                    } else onClick,
                    onLongClick3s = onLongClick3s
                )
                .padding(12.dp)
        ) {
            Column {
                // Telegram-style Forwarded Message Header
                if (message.isForwarded && !message.forwardHideSender && !message.forwardOriginalSenderName.isNullOrBlank()) {
                    val authorId = message.forwardOriginalSenderId?.takeIf { it.isNotBlank() } ?: message.chatId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (authorId.isNotBlank() && onAuthorClick != null) {
                                    Modifier.clickable { onAuthorClick(authorId) }
                                } else Modifier
                            )
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Переслано от ",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val authorAvatarUrl = "https://picsum.photos/seed/$authorId/100"
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(authorAvatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Author Avatar",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                            val initialLetter = (message.forwardOriginalSenderName.take(1).ifBlank { "U" }).uppercase()
                            Text(
                                text = initialLetter,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = message.forwardOriginalSenderName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                if (message.replyToMessageText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .height(IntrinsicSize.Min)
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Reply",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Text(
                                    text = message.replyToMessageText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = (if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (lightboxItem != null) {
                    // High-quality Lightbox Photo Message with Telegram Selection and Viewer
                    Column(modifier = Modifier.widthIn(min = 180.dp, max = 280.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp, max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.15f))
                                .clickable {
                                    if (isSelectionMode) {
                                        onClick()
                                    } else {
                                        onImageClick?.invoke(lightboxItem)
                                    }
                                }
                        ) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(lightboxItem.imageUrl)
                                    .crossfade(true)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = lightboxItem.caption.ifBlank { "Shared photo" },
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )

                            if (isSelectionMode) {
                                // Telegram Photo Checkmark Selection Badge
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFF4CAF50) else Color.Black.copy(alpha = 0.5f),
                                    border = BorderStroke(2.dp, if (isSelected) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.8f)),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(26.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Выбрано",
                                            tint = Color.White,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ZoomIn,
                                        contentDescription = "Zoom",
                                        tint = Color.White,
                                        modifier = Modifier.padding(5.dp)
                                    )
                                }
                            }
                        }

                        if (lightboxItem.caption.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (message.isE2EEncrypted) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = "Encrypted",
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = lightboxItem.caption,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else if (message.documentData != null) {
                    val documentData = message.documentData
                    val parsedData = androidx.compose.runtime.remember(documentData) {
                        var name = "File"
                        var size = 0L
                        var mime = ""
                        try {
                            val json = org.json.JSONObject(documentData)
                            name = json.optString("name", "File")
                            size = json.optLong("size", 0L)
                            mime = json.optString("mimeType", "")
                        } catch(e: Exception) {}
                        Triple(name, size, mime)
                    }
                    val (name, size, mime) = parsedData

                    
                    val icon = if (mime.startsWith("image/")) Icons.Filled.Image
                        else if (mime.startsWith("video/")) Icons.Filled.VideoFile
                        else if (mime.startsWith("audio/")) Icons.Filled.AudioFile
                        else Icons.AutoMirrored.Filled.InsertDriveFile

                    var showDownloadDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                    if (showDownloadDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showDownloadDialog = false },
                            title = { Text("File Preview") },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(16.dp))
                                    Text(name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Text("${size / 1024} KB", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(16.dp))
                                    Text("Open or download this file to your device?", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showDownloadDialog = false }) { Text("Download") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val isVideo = mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mov") || message.mediaType == "video"
                                    if (isVideo && onOpenVideoNote != null) {
                                        onOpenVideoNote()
                                    } else {
                                        showDownloadDialog = true
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            Icon(icon, contentDescription = "File", tint = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(name, color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${size / 1024} KB", color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (message.isE2EEncrypted) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Encrypted", modifier = Modifier.size(12.dp), tint = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha=0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(message.text, color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                } else if (message.audioPath != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play Audio", tint = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(message.text, color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.isE2EEncrypted) {
                            Icon(Icons.Filled.Lock, contentDescription = "Encrypted", modifier = Modifier.size(12.dp), tint = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha=0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(message.text, color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                
                if (message.reaction != null) {
                    Box(modifier = Modifier.offset(y = 8.dp).background(Color.DarkGray, CircleShape).padding(4.dp)) {
                        Text(message.reaction)
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                    Text(
                        formatTime(message.timestamp), 
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), 
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha=0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)
                    )
                    if (message.expiresAt != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Timer, contentDescription = "Disappearing", modifier = Modifier.size(12.dp), tint = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha=0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f))
                    }
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        if (message.isRead) {
                            Icon(Icons.Filled.DoneAll, contentDescription = "Read", modifier = Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                        } else if (message.isDelivered) {
                            Icon(Icons.Filled.DoneAll, contentDescription = "Delivered", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha=0.7f))
                        } else {
                            Icon(Icons.Filled.Schedule, contentDescription = "В очереди", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha=0.7f))
                        }
                    }
                }
            }
        }
        
        if (!message.buttonsData.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            val buttonsData = message.buttonsData
            if (buttonsData.startsWith("{")) {
                var markup: com.example.botapi.InlineKeyboardMarkup? = null
                try {
                    val adapter = com.example.botapi.BotApiMoshi.moshi.adapter(com.example.botapi.InlineKeyboardMarkup::class.java)
                    markup = adapter.fromJson(buttonsData)
                } catch (e: Exception) {
                    // Fallback in case of parse error
                }
                
                if (markup != null) {
                    com.example.ui.botapi.InlineKeyboardRenderer(
                        markup = markup,
                        onCallbackQuery = { onButtonClick?.invoke(it) }
                    )
                }
            } else {
                val buttons = buttonsData.split("||")
                InlineButtonGrid(
                    buttons = buttons,
                    onButtonClick = { onButtonClick?.invoke(it) },
                    onReorderComplete = { newOrder -> 
                        // Update layout
                    },
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                )
            }
        }
    }
}


private val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
fun formatTime(timestamp: Long): String {
    val date = Date(timestamp)
    return timeFormatter.format(date)
}

/**
 * ReportMessageDialog
 *
 * Material 3 Dialog presenting predefined Telegram-style complaint categories
 * and an optional custom message description for submitting content moderation reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportMessageDialog(
    messageText: String,
    senderDisplayName: String,
    onDismiss: () -> Unit,
    onSubmitReport: (reasonCategory: String, userComment: String) -> Unit
) {
    val reportCategories = listOf(
        "Спам и реклама",
        "Оскорбления и угрозы",
        "Неприемлемый контент (18+)",
        "Мошенничество / Фишинг",
        "Другое"
    )

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var userComment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ReportProblem,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Пожаловаться",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        "Сообщение от: $senderDisplayName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quoted snippet of reported message
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(28.dp)
                                .background(Color(0xFFFF5252), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (messageText.isBlank()) "«Медиа/вложение»" else "«${messageText.take(90)}${if (messageText.length > 90) "..." else ""}»",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                    }
                }

                Text(
                    "Укажите причину нарушения:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Selectable Category chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reportCategories.forEachIndexed { index, category ->
                        val isSelected = selectedCategoryIndex == index
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategoryIndex = index },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFFF5252).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFFF5252) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedCategoryIndex = index },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFFF5252)
                                    )
                                )
                            }
                        }
                    }
                }

                // Optional comment field
                OutlinedTextField(
                    value = userComment,
                    onValueChange = { userComment = it },
                    label = { Text("Комментарий (необязательно)") },
                    placeholder = { Text("Опишите подробности нарушения...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF5252),
                        focusedLabelColor = Color(0xFFFF5252)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitReport(reportCategories[selectedCategoryIndex], userComment.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Отправить жалобу", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Отмена")
            }
        }
    )
}

