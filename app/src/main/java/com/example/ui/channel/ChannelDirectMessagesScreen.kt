@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.channel

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.AvatarStorageManager
import com.example.data.EntityType
import com.example.data.ecosystem.KuoteXEcosystemFirestoreManager
import com.example.ui.AppViewModel
import com.example.ui.Message
import com.example.ui.components.ChatInputMode
import com.example.ui.components.TelegramRecordActionButton
import com.example.ui.media.GifSearchRepository
import com.example.ui.media.MediaExpressionManager
import com.example.ui.media.MediaPanelTab
import com.example.ui.media.TelegramMediaPanel
import com.example.ui.navigateSafe
import com.example.ui.navigation.AppDestinations
import com.example.ui.popBackStackSafe
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DmPurple = Color(0xFF9F6BFF)
private val DmPillBg = Color(0xFF1A1D26)
private val DmMuted = Color(0xFF8C92A3)

/**
 * Telegram-style Direct Messages screen for Channel communication ("Сообщения [Channel Name]")
 * with Suggested Post composer & inline Date/Time Wheel, channel doodle/wallpaper background,
 * 360dp-optimized pill input bar, and Admin moderation support.
 */
@Composable
fun ChannelDirectMessagesChatScreen(
    viewModel: AppViewModel,
    channelId: String,
    subscriberId: String? = null,
    isAdminInboxView: Boolean = false,
    navController: NavController
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle(initialValue = emptyList())
    val groupMembers by viewModel.getGroupMembers(channelId).collectAsStateWithLifecycle(initialValue = emptyList())
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(channelId).collectAsStateWithLifecycle()
    val customization = customizationsMap[channelId] ?: ChannelCustomization(chatId = channelId)

    val userState by KuoteXEcosystemFirestoreManager.currentUserState.collectAsStateWithLifecycle()
    val mutedThreads by ChannelDirectMessageManager.mutedThreadIds.collectAsStateWithLifecycle()
    val threadsMap by ChannelDirectMessageManager.threadsByChannel.collectAsStateWithLifecycle()

    LaunchedEffect(context, activeAccount?.id) {
        ChannelDirectMessageManager.init(context)
        ChannelCustomizationManager.init(context)
        KuoteXEcosystemFirestoreManager.initLocalBalanceCache(context)
        val uid = activeAccount?.id?.ifBlank { "me" } ?: "me"
        KuoteXEcosystemFirestoreManager.ensureUserStateLoaded(
            userId = uid,
            username = activeAccount?.username ?: "user",
            displayName = activeAccount?.displayName ?: "Пользователь"
        )
    }

    val channelChat = remember(chats, channelId) { chats.find { it.id == channelId } }
    val channelTitle = channelChat?.title?.ifBlank { "Канал" } ?: "Канал"
    val myUserId = activeAccount?.id?.ifBlank { "me" } ?: "me"
    val resolvedSubscriberId = subscriberId?.takeIf { it.isNotBlank() } ?: myUserId
    val threadSummary = remember(threadsMap, channelId, resolvedSubscriberId) {
        threadsMap[channelId]?.find { it.subscriberId == resolvedSubscriberId }
    }
    val resolvedSubscriberName = when {
        isAdminInboxView && threadSummary != null -> threadSummary.subscriberName
        resolvedSubscriberId == myUserId -> activeAccount?.displayName?.ifBlank { "Вы" } ?: "Вы"
        else -> threadSummary?.subscriberName ?: "Подписчик"
    }
    val resolvedSubscriberAvatar = if (isAdminInboxView) threadSummary?.subscriberAvatar else activeAccount?.profilePicUrl
    val threadId = remember(channelId, resolvedSubscriberId) {
        ChannelDirectMessageManager.buildThreadId(channelId, resolvedSubscriberId)
    }

    val isChannelAdmin = remember(groupMembers, activeAccount, channelChat) {
        isChannelAdminOrCreator(groupMembers, activeAccount) ||
            (channelChat != null && activeAccount == null)
    }
    val effectiveAdminView = isAdminInboxView || (isChannelAdmin && resolvedSubscriberId != myUserId)
    val isThreadMuted = mutedThreads.contains(threadId)

    val dmMessages by viewModel.getMessages(threadId).collectAsStateWithLifecycle(initialValue = emptyList())
    val userStarsBalance = userState?.balance ?: 0L

    var inputText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showMediaPanel by remember { mutableStateOf(false) }
    var showSuggestedPostSheet by remember { mutableStateOf(false) }
    var isSubmittingSuggestedPost by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    var inputMode by remember { mutableStateOf(ChatInputMode.VOICE) }
    var isVoiceRecording by remember { mutableStateOf(false) }
    var isVideoRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isVoiceRecording, isVideoRecording) {
        if (isVoiceRecording || isVideoRecording) {
            recordingSeconds = 0
            while (isVoiceRecording || isVideoRecording) {
                delay(1000L)
                recordingSeconds += 1
            }
        }
    }

    val composerFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendChannelDirectMessage(
                channelId = channelId,
                subscriberId = resolvedSubscriberId,
                subscriberName = resolvedSubscriberName,
                subscriberAvatar = resolvedSubscriberAvatar,
                senderId = myUserId,
                text = "📷 Изображение",
                mediaPath = uri.toString(),
                mediaType = "image"
            )
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Файл"
            viewModel.sendChannelDirectMessage(
                channelId = channelId,
                subscriberId = resolvedSubscriberId,
                subscriberName = resolvedSubscriberName,
                subscriberAvatar = resolvedSubscriberAvatar,
                senderId = myUserId,
                text = "📎 $fileName",
                mediaPath = uri.toString(),
                mediaType = "file"
            )
        }
    }

    val filteredMessages = remember(dmMessages, searchQuery, isSearchMode) {
        if (!isSearchMode || searchQuery.isBlank()) {
            dmMessages
        } else {
            dmMessages.filter { msg ->
                msg.text.contains(searchQuery, ignoreCase = true) ||
                    (SuggestedPostPayload.fromJson(msg.documentData)?.text?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.lastIndex)
        }
    }

    if (showClearDialog) {
        TelegramClearHistoryDialog(
            channelTitle = channelTitle,
            onDismissRequest = { showClearDialog = false },
            onConfirm = {
                viewModel.clearChannelDirectMessages(channelId, threadId)
                Toast.makeText(context, "История сообщений очищена", Toast.LENGTH_SHORT).show()
            }
        )
    }

    SuggestedPostBottomSheet(
        visible = showSuggestedPostSheet,
        userStarsBalance = userStarsBalance,
        defaultStarPrice = customization.directMessageStarPrice,
        initialPostText = inputText,
        isSubmitting = isSubmittingSuggestedPost,
        onDismiss = { if (!isSubmittingSuggestedPost) showSuggestedPostSheet = false },
        onBuyStarsClick = {
            navController.navigateSafe(AppDestinations.BUY_STARS)
        },
        onSubmitSuggestedPost = { postText, priceStars, scheduledTs, scheduledLabel ->
            isSubmittingSuggestedPost = true
            viewModel.submitSuggestedPost(
                channelId = channelId,
                subscriberId = resolvedSubscriberId,
                subscriberName = resolvedSubscriberName,
                subscriberAvatar = resolvedSubscriberAvatar,
                postText = postText,
                priceStars = priceStars,
                scheduledTimestamp = scheduledTs,
                scheduledLabel = scheduledLabel,
                onSuccess = {
                    isSubmittingSuggestedPost = false
                    showSuggestedPostSheet = false
                    inputText = ""
                    Toast.makeText(
                        context,
                        if (priceStars > 0) "Пост предложен за ⭐ $priceStars" else "Пост предложен каналу",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onInsufficientBalance = { cur, req ->
                    isSubmittingSuggestedPost = false
                    Toast.makeText(
                        context,
                        "Недостаточно звёзд ($cur < $req)",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onError = { err ->
                    isSubmittingSuggestedPost = false
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            )
        }
    )

    val channelAvatarPath = remember(channelId, customization.avatarUrl) {
        AvatarStorageManager.getAvatar(
            context,
            EntityType.CHANNEL,
            channelId,
            customization.avatarUrl
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D12))
    ) {
        // Atmospheric Channel Background + Telegram Doodle Texture
        ChannelBackgroundLayer(
            customization = customization,
            modifier = Modifier.fillMaxSize()
        )
        TelegramDoodleBackdrop(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (isSearchMode) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = DmPillBg.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        isSearchMode = false
                                        searchQuery = ""
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Закрыть поиск",
                                        tint = Color.White
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                    cursorBrush = SolidColor(DmPurple),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (searchQuery.isEmpty()) {
                                            Text("Поиск в сообщениях...", color = DmMuted, fontSize = 15.sp)
                                        }
                                        inner()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Очистить", tint = DmMuted)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Back Circle Button
                            Surface(
                                onClick = { navController.popBackStackSafe() },
                                shape = CircleShape,
                                color = DmPillBg.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                                modifier = Modifier
                                    .size(46.dp)
                                    .testTag("channel_dm_back_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Назад",
                                        tint = Color.White,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }

                            // Center Title Pill: Avatar + "Сообщения [Channel Name]" + "Сообщения каналу"
                            Surface(
                                onClick = { navController.navigateSafe(AppDestinations.profile(channelId)) },
                                shape = RoundedCornerShape(24.dp),
                                color = DmPillBg.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("channel_dm_header_pill")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ChannelCircularAvatarView(
                                        avatarPath = channelAvatarPath,
                                        fallbackTitle = channelTitle,
                                        sizeDp = 34
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isAdminInboxView) resolvedSubscriberName else "Сообщения $channelTitle",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isThreadMuted) {
                                                Spacer(Modifier.width(5.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.VolumeOff,
                                                    contentDescription = "Без звука",
                                                    tint = DmMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (isAdminInboxView) "Сообщения каналу • $channelTitle" else "Сообщения каналу",
                                            color = DmMuted,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Overflow Three-Dot Circle Button
                            Box {
                                Surface(
                                    onClick = { showOverflowMenu = true },
                                    shape = CircleShape,
                                    color = DmPillBg.copy(alpha = 0.92f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                                    modifier = Modifier
                                        .size(46.dp)
                                        .testTag("channel_dm_more_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Меню",
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    containerColor = Color(0xFF212D3B),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isThreadMuted) "Вкл. уведомления" else "Выкл. уведомления", color = Color.White) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isThreadMuted) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.85f)
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            val mutedNow = ChannelDirectMessageManager.toggleMuteThread(threadId)
                                            Toast.makeText(
                                                context,
                                                if (mutedNow) "Уведомления диалога выключены" else "Уведомления диалога включены",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Поиск", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.85f)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            isSearchMode = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Предложить пост", color = Color.White) },
                                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB800)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showSuggestedPostSheet = true
                                        }
                                    )
                                    if (isChannelAdmin && !isAdminInboxView) {
                                        DropdownMenuItem(
                                            text = { Text("Входящие администратора", color = Color.White) },
                                            leadingIcon = { Icon(Icons.Outlined.Inbox, contentDescription = null, tint = DmPurple) },
                                            onClick = {
                                                showOverflowMenu = false
                                                navController.navigateSafe(AppDestinations.channelAdminDmInbox(channelId))
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Очистить историю", color = Color(0xFFFF6B6B)) },
                                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color(0xFFFF6B6B)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showClearDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    val isCompactWidth = maxWidth <= 380.dp
                    val iconButtonSize = if (isCompactWidth) 40.dp else 44.dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Composer Pill: Emoji/Sticker toggle | Input | ($) Suggested Post | Paperclip
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = DmPillBg.copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (showMediaPanel) {
                                                showMediaPanel = false
                                                composerFocusRequester.requestFocus()
                                                keyboardController?.show()
                                            } else {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                showMediaPanel = true
                                            }
                                        },
                                        modifier = Modifier
                                            .size(iconButtonSize)
                                            .testTag("channel_dm_emoji_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (showMediaPanel) Icons.Outlined.Keyboard else Icons.Outlined.SentimentSatisfied,
                                            contentDescription = if (showMediaPanel) "Показать клавиатуру" else "Эмодзи, GIF и стикеры",
                                            tint = DmMuted,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (inputText.isEmpty()) {
                                            Text(
                                                text = "Сообщение",
                                                color = DmMuted,
                                                fontSize = if (isCompactWidth) 15.sp else 16.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        BasicTextField(
                                            value = inputText,
                                            onValueChange = { inputText = it },
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = if (isCompactWidth) 15.sp else 16.sp
                                            ),
                                            cursorBrush = SolidColor(DmPurple),
                                            maxLines = 4,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(composerFocusRequester)
                                                .onFocusChanged { if (it.isFocused && showMediaPanel) showMediaPanel = false }
                                                .testTag("channel_dm_message_input")
                                        )
                                    }

                                    // Circular ($) Suggested Post Trigger Button
                                    IconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            showMediaPanel = false
                                            showSuggestedPostSheet = true
                                        },
                                        modifier = Modifier
                                            .size(iconButtonSize)
                                            .testTag("channel_dm_suggest_post_button")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(25.dp)
                                                .clip(CircleShape)
                                                .border(1.7.dp, Color(0xFFA9B0BE), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$",
                                                color = Color(0xFFA9B0BE),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Attachment Paperclip Menu
                                    Box {
                                        IconButton(
                                            onClick = { showAttachMenu = true },
                                            modifier = Modifier
                                                .size(iconButtonSize)
                                                .testTag("channel_dm_attach_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AttachFile,
                                                contentDescription = "Прикрепить вложение",
                                                tint = Color(0xFFA9B0BE),
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showAttachMenu,
                                            onDismissRequest = { showAttachMenu = false },
                                            containerColor = Color(0xFF212D3B)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Фото / Галерея", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = DmPurple) },
                                                onClick = {
                                                    showAttachMenu = false
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Файл", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null, tint = Color(0xFF2AABEE)) },
                                                onClick = {
                                                    showAttachMenu = false
                                                    filePickerLauncher.launch("*/*")
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Предложенный пост (⭐)", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB800)) },
                                                onClick = {
                                                    showAttachMenu = false
                                                    showSuggestedPostSheet = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // External Circular Send / Voice-Video Action Button
                            if (inputText.isNotBlank()) {
                                Surface(
                                    onClick = {
                                        val textToSend = inputText.trim()
                                        if (textToSend.isNotEmpty()) {
                                            viewModel.sendChannelDirectMessage(
                                                channelId = channelId,
                                                subscriberId = resolvedSubscriberId,
                                                subscriberName = resolvedSubscriberName,
                                                subscriberAvatar = resolvedSubscriberAvatar,
                                                senderId = myUserId,
                                                text = textToSend
                                            )
                                            inputText = ""
                                        }
                                    },
                                    shape = CircleShape,
                                    color = DmPurple,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("channel_dm_send_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Отправить сообщение",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                TelegramRecordActionButton(
                                    mode = inputMode,
                                    isRecording = isVoiceRecording || isVideoRecording,
                                    onToggleMode = {
                                        inputMode = if (inputMode == ChatInputMode.VOICE) {
                                            ChatInputMode.VIDEO
                                        } else {
                                            ChatInputMode.VOICE
                                        }
                                    },
                                    onStartRecording = { mode ->
                                        if (mode == ChatInputMode.VIDEO) isVideoRecording = true else isVoiceRecording = true
                                    },
                                    onStopAndSend = { mode ->
                                        val dur = recordingSeconds.coerceAtLeast(1)
                                        val durStr = String.format(Locale.US, "%d:%02d", dur / 60, dur % 60)
                                        val msgText = if (mode == ChatInputMode.VIDEO) {
                                            "📹 Видеосообщение ($durStr)"
                                        } else {
                                            "🎤 Голосовое сообщение ($durStr)"
                                        }
                                        viewModel.sendChannelDirectMessage(
                                            channelId = channelId,
                                            subscriberId = resolvedSubscriberId,
                                            subscriberName = resolvedSubscriberName,
                                            subscriberAvatar = resolvedSubscriberAvatar,
                                            senderId = myUserId,
                                            text = msgText,
                                            mediaType = if (mode == ChatInputMode.VIDEO) "video_note" else "voice"
                                        )
                                        isVoiceRecording = false
                                        isVideoRecording = false
                                        recordingSeconds = 0
                                    },
                                    onCancelRecording = {
                                        isVoiceRecording = false
                                        isVideoRecording = false
                                        recordingSeconds = 0
                                    }
                                )
                            }
                        }

                        // Inline Telegram Emoji / GIF / Stickers Panel
                        AnimatedVisibility(
                            visible = showMediaPanel,
                            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
                            exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                        ) {
                            TelegramMediaPanel(
                                initialTab = MediaPanelTab.EMOJI,
                                onEmojiSelected = { emojiItem -> inputText += emojiItem.emoji },
                                onBackspaceClick = {
                                    if (inputText.isNotEmpty()) {
                                        val cp = Character.codePointBefore(inputText, inputText.length)
                                        inputText = inputText.dropLast(Character.charCount(cp))
                                    }
                                },
                                onGifSelected = { gif ->
                                    viewModel.sendChannelDirectMessage(
                                        channelId = channelId,
                                        subscriberId = resolvedSubscriberId,
                                        subscriberName = resolvedSubscriberName,
                                        subscriberAvatar = resolvedSubscriberAvatar,
                                        senderId = myUserId,
                                        text = "🎞️ ${gif.title}",
                                        mediaPath = gif.gifUrl,
                                        mediaType = "gif",
                                        documentData = GifSearchRepository.buildGifDocumentData(gif)
                                    )
                                },
                                onStickerSelected = { pack, sticker ->
                                    viewModel.sendChannelDirectMessage(
                                        channelId = channelId,
                                        subscriberId = resolvedSubscriberId,
                                        subscriberName = resolvedSubscriberName,
                                        subscriberAvatar = resolvedSubscriberAvatar,
                                        senderId = myUserId,
                                        text = sticker.emoji,
                                        mediaPath = sticker.id,
                                        mediaType = "sticker",
                                        documentData = MediaExpressionManager.buildStickerDocumentData(pack.id, sticker)
                                    )
                                },
                                onOpenSettings = {
                                    showMediaPanel = false
                                    navController.navigateSafe(AppDestinations.SETTINGS_STICKERS_EMOJI)
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
                if (filteredMessages.isEmpty()) {
                    // Centered Rounded Dark-Gradient Empty State Card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 276.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF423A54).copy(alpha = 0.80f),
                                            Color(0xFF262736).copy(alpha = 0.88f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.09f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 22.dp)
                                .testTag("channel_dm_empty_state_card")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Forum,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Text(
                                    text = buildAnnotatedString {
                                        append("Вы можете отправить сообщение администратору ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(channelTitle)
                                        }
                                        append(".")
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredMessages, key = { it.id }) { msg ->
                            val isMe = msg.senderId == myUserId
                            val suggestedPayload = remember(msg.id, msg.documentData) {
                                SuggestedPostPayload.fromJson(msg.documentData)
                            }
                            if (suggestedPayload != null) {
                                SuggestedPostMessageCard(
                                    payload = suggestedPayload,
                                    isMe = isMe,
                                    isAdminView = effectiveAdminView,
                                    onAcceptPublishNow = {
                                        viewModel.acceptSuggestedPost(
                                            context = context,
                                            dmThreadId = threadId,
                                            message = msg,
                                            scheduleForRequestedTime = false
                                        )
                                        Toast.makeText(context, "Пост опубликован в канал", Toast.LENGTH_SHORT).show()
                                    },
                                    onScheduleForRequestedTime = {
                                        viewModel.acceptSuggestedPost(
                                            context = context,
                                            dmThreadId = threadId,
                                            message = msg,
                                            scheduleForRequestedTime = true
                                        )
                                        Toast.makeText(
                                            context,
                                            "Пост запланирован на ${suggestedPayload.scheduledLabel}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onDeclineRefund = {
                                        viewModel.declineSuggestedPost(dmThreadId = threadId, message = msg)
                                        Toast.makeText(
                                            context,
                                            if (suggestedPayload.priceStars > 0) {
                                                "Отклонено • Возвращено ⭐ ${suggestedPayload.priceStars}"
                                            } else "Отклонено",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            } else {
                                DirectThreadTextBubble(
                                    message = msg,
                                    isMe = isMe
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectThreadTextBubble(
    message: Message,
    isMe: Boolean
) {
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMe) 18.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 18.dp
            ),
            color = if (isMe) Color(0xFF6B42C8) else Color(0xFF1E222E),
            modifier = Modifier.widthIn(min = 84.dp, max = 290.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (message.mediaType == "image" && !message.mediaPath.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(message.mediaPath)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Вложение",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
                Text(
                    text = timeText,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
internal fun ChannelCircularAvatarView(
    avatarPath: String?,
    fallbackTitle: String,
    sizeDp: Int
) {
    if (!avatarPath.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarPath)
                .crossfade(true)
                .build(),
            contentDescription = fallbackTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B2F3D))
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF9F6BFF), Color(0xFF6539C6)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackTitle.take(1).uppercase(),
                color = Color.White,
                fontSize = (sizeDp * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TelegramDoodleBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lineColor = Color(0xFF9F6BFF).copy(alpha = 0.08f)
        val step = 110.dp.toPx()
        var row = 0
        var y = 30.dp.toPx()
        while (y < h) {
            var x = if (row % 2 == 0) 24.dp.toPx() else 72.dp.toPx()
            while (x < w) {
                val radius = 14.dp.toPx()
                when ((row + (x / step).toInt()) % 3) {
                    0 -> drawCircle(
                        color = lineColor,
                        radius = radius,
                        center = Offset(x, y),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    1 -> drawRoundRect(
                        color = lineColor,
                        topLeft = Offset(x - radius, y - radius * 0.7f),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 1.4f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    else -> drawLine(
                        color = lineColor,
                        start = Offset(x - radius, y),
                        end = Offset(x + radius, y - radius * 0.6f),
                        strokeWidth = 1.4.dp.toPx()
                    )
                }
                x += step
            }
            y += step * 0.85f
            row++
        }
    }
}
