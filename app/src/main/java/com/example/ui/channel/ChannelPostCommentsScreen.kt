@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.channel

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.AvatarStorageManager
import com.example.data.EntityType
import com.example.ui.AppViewModel
import com.example.ui.Chat
import com.example.ui.Message
import com.example.ui.components.extractLightboxMediaItem
import com.example.ui.formatTime
import com.example.ui.media.GifGridTile
import com.example.ui.media.GifSearchRepository
import com.example.ui.media.MediaExpressionManager
import com.example.ui.media.MediaPanelTab
import com.example.ui.media.TelegramMediaPanel
import com.example.ui.popBackStackSafe

private val CommentsBgDark = Color(0xFF0A0D14)
private val CommentsCardDark = Color(0xFF181D27)
private val CommentsPillDark = Color(0xFF1F2533)
private val CommentsPurple = Color(0xFF9F6BFF)
private val CommentsMuted = Color(0xFF8C94A6)

/**
 * Dedicated Telegram-style Post Comments screen ("Комментарии") for a specific channel post.
 *
 * Features:
 * - Top bar with Back button, Channel circular avatar, and single-line "Комментарии" title.
 * - Centered date pill, original channel post preview card with "канал" tag, views, timestamp,
 *   and right circular forward button.
 * - Centered "Комментариев пока нет..." empty state pill or chronological comment thread.
 * - 360dp-safe bottom composer with Channel/Personal sender switcher (Channel default for admins),
 *   GIF/sticker panel toggle, truncated single-line placeholder, attachment picker, and send action.
 */
@Composable
fun ChannelPostCommentsScreen(
    viewModel: AppViewModel,
    channelId: String,
    postId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(channelId, postId) {
        ChannelCustomizationManager.init(context)
        MediaExpressionManager.init(context)
    }

    LaunchedEffect(channelId) {
        viewModel.observeChannelDiscussionSync(channelId).collect { }
    }

    LaunchedEffect(channelId, postId) {
        viewModel.observeRemotePostCommentsSync(channelId, postId).collect { }
    }

    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val channelMembers by viewModel.getGroupMembers(channelId).collectAsStateWithLifecycle(initialValue = emptyList())
    val customizationsMap by ChannelCustomizationManager.getAllCustomizationsFlow().collectAsStateWithLifecycle()
    val channelCustomization = customizationsMap[channelId] ?: ChannelCustomization(chatId = channelId)
    val channelScheme = channelCustomization.activeMessageScheme

    val channelChat = remember(chats, channelId) { chats.find { it.id == channelId } }
    val channelTitle = channelChat?.title?.ifBlank { "Канал" } ?: "Канал"
    val post by viewModel.observePostMessage(postId).collectAsStateWithLifecycle(initialValue = null)
    val comments by viewModel.observePostComments(channelId, postId).collectAsStateWithLifecycle(initialValue = emptyList())

    val canPostAsChannel = remember(channelMembers, activeAccount, channelChat) {
        isChannelAdminOrCreator(channelMembers, activeAccount) ||
            (channelChat != null && activeAccount == null)
    }

    // Confirmed clarification: default to Channel identity for admins with toggle switcher
    var isCommentingAsChannel by remember(channelId, canPostAsChannel) {
        mutableStateOf(canPostAsChannel)
    }
    LaunchedEffect(canPostAsChannel) {
        if (!canPostAsChannel) {
            isCommentingAsChannel = false
        }
    }

    var inputText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<ChannelPostCommentEntity?>(null) }
    var showMediaPanel by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showForwardSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    BackHandler(enabled = showMediaPanel || showForwardSheet) {
        when {
            showForwardSheet -> showForwardSheet = false
            showMediaPanel -> showMediaPanel = false
        }
    }

    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(comments.size)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendPostComment(
                channelId = channelId,
                postId = postId,
                text = inputText.trim().ifBlank { "📷 Фотография" },
                postAsChannel = isCommentingAsChannel,
                mediaPath = uri.toString(),
                mediaType = "image",
                replyToComment = replyingToComment
            ) {
                inputText = ""
                replyingToComment = null
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Файл"
            viewModel.sendPostComment(
                channelId = channelId,
                postId = postId,
                text = "📎 $fileName",
                postAsChannel = isCommentingAsChannel,
                mediaPath = uri.toString(),
                mediaType = "file",
                replyToComment = replyingToComment
            ) {
                replyingToComment = null
            }
        }
    }

    val forwardTargets = remember(chats, channelId) {
        chats.filter { it.id != channelId && !it.isBlocked }
    }

    if (showForwardSheet && post != null) {
        ModalBottomSheet(
            onDismissRequest = { showForwardSheet = false },
            containerColor = CommentsCardDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Переслать публикацию",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                if (forwardTargets.isEmpty()) {
                    Text(
                        text = "Нет доступных чатов для пересылки",
                        color = CommentsMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(forwardTargets, key = { it.id }) { targetChat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clickable {
                                        val origPost = post ?: return@clickable
                                        viewModel.forwardMessages(
                                            targetChatId = targetChat.id,
                                            senderId = activeAccount?.id ?: "me",
                                            messagesToForward = listOf(origPost to channelTitle)
                                        )
                                        showForwardSheet = false
                                        Toast.makeText(
                                            context,
                                            "Переслано в «${targetChat.title}»",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CommentsMonogramAvatar(
                                    id = targetChat.id,
                                    title = targetChat.title,
                                    avatarUrl = customizationsMap[targetChat.id]?.avatarUrl,
                                    size = 40.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = targetChat.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when {
                                            targetChat.isChannel -> "Канал"
                                            targetChat.isGroup -> "Группа"
                                            targetChat.isBot -> "Бот"
                                            else -> "Личный чат"
                                        },
                                        color = CommentsMuted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Переслать в ${targetChat.title}",
                                    tint = CommentsPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CommentsBgDark)
    ) {
        // Subtle doodle / themed channel backdrop
        ChannelBackgroundLayer(
            customization = channelCustomization,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    color = CommentsBgDark.copy(alpha = 0.90f),
                    tonalElevation = 0.dp
                ) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    navController.popBackStackSafe()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("post_comments_back")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = Color.White
                                )
                            }
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CommentsMonogramAvatar(
                                    id = channelId,
                                    title = channelTitle,
                                    avatarUrl = channelCustomization.avatarUrl,
                                    accentColor = channelScheme.accentColor,
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Комментарии",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("post_comments_header_title")
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CommentsBgDark.copy(alpha = 0.92f))
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Active reply banner if replying to a specific comment
                    AnimatedVisibility(visible = replyingToComment != null) {
                        val targetReply = replyingToComment
                        if (targetReply != null) {
                            Surface(
                                color = CommentsCardDark,
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = null,
                                        tint = CommentsPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = targetReply.senderName,
                                            color = CommentsPurple,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = targetReply.text.ifBlank { "Вложение" },
                                            color = Color.White.copy(alpha = 0.78f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { replyingToComment = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Отменить ответ",
                                            tint = CommentsMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 360dp-safe composer row matching Telegram screenshot
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        val isCompactWidth = maxWidth < 385.dp
                        val actionCircleSize = if (isCompactWidth) 44.dp else 48.dp
                        val pillIconSize = if (isCompactWidth) 34.dp else 38.dp
                        val badgeAvatarSize = if (isCompactWidth) 30.dp else 32.dp

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 6.dp else 8.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = actionCircleSize),
                                shape = RoundedCornerShape(24.dp),
                                color = CommentsPillDark.copy(alpha = 0.92f),
                                border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = if (isCompactWidth) 6.dp else 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Sender identity switcher badge (Channel default for admins)
                                    Box(
                                        modifier = Modifier
                                            .size(if (isCompactWidth) 36.dp else 40.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = canPostAsChannel) {
                                                isCommentingAsChannel = !isCommentingAsChannel
                                                Toast.makeText(
                                                    context,
                                                    if (isCommentingAsChannel) {
                                                        "Комментарий от имени канала «$channelTitle»"
                                                    } else {
                                                        "Комментарий от Вашего профиля"
                                                    },
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            .testTag("post_comments_sender_switcher"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCommentingAsChannel) {
                                            CommentsMonogramAvatar(
                                                id = channelId,
                                                title = channelTitle,
                                                avatarUrl = channelCustomization.avatarUrl,
                                                accentColor = channelScheme.accentColor,
                                                size = badgeAvatarSize
                                            )
                                        } else {
                                            CommentsMonogramAvatar(
                                                id = activeAccount?.id ?: "me",
                                                title = activeAccount?.displayName ?: "Вы",
                                                avatarUrl = activeAccount?.profilePicUrl,
                                                accentColor = CommentsPurple,
                                                size = badgeAvatarSize
                                            )
                                        }
                                    }

                                    // GIF / Sticker toggle button
                                    IconButton(
                                        onClick = {
                                            showMediaPanel = !showMediaPanel
                                            if (showMediaPanel) {
                                                keyboardController?.hide()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(pillIconSize)
                                            .testTag("post_comments_media_button")
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Transparent,
                                            border = BorderStroke(
                                                1.5.dp,
                                                if (showMediaPanel) CommentsPurple else CommentsMuted
                                            )
                                        ) {
                                            Text(
                                                text = "GIF",
                                                color = if (showMediaPanel) CommentsPurple else CommentsMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    // Comment text field with 360dp-safe truncated placeholder
                                    val placeholderText = if (isCommentingAsChannel) {
                                        "Анонимное сообщение..."
                                    } else {
                                        "Комментарий..."
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (inputText.isEmpty()) {
                                            Text(
                                                text = placeholderText,
                                                color = CommentsMuted,
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
                                            cursorBrush = SolidColor(CommentsPurple),
                                            maxLines = 4,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("post_comments_input")
                                        )
                                    }

                                    // Paperclip attachment menu
                                    Box {
                                        IconButton(
                                            onClick = { showAttachMenu = true },
                                            modifier = Modifier
                                                .size(pillIconSize)
                                                .testTag("post_comments_attach_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AttachFile,
                                                contentDescription = "Прикрепить вложение",
                                                tint = CommentsMuted,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showAttachMenu,
                                            onDismissRequest = { showAttachMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Фото / Галерея") },
                                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                                onClick = {
                                                    showAttachMenu = false
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Файл") },
                                                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                                                onClick = {
                                                    showAttachMenu = false
                                                    filePickerLauncher.launch("*/*")
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Circular purple send / action button
                            val canSend = inputText.isNotBlank()
                            Surface(
                                shape = CircleShape,
                                color = CommentsPurple,
                                modifier = Modifier
                                    .size(actionCircleSize)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (canSend) {
                                            val textToSend = inputText.trim()
                                            viewModel.sendPostComment(
                                                channelId = channelId,
                                                postId = postId,
                                                text = textToSend,
                                                postAsChannel = isCommentingAsChannel,
                                                replyToComment = replyingToComment
                                            ) {
                                                inputText = ""
                                                replyingToComment = null
                                                showMediaPanel = false
                                            }
                                        } else {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    }
                                    .testTag("post_comments_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Outlined.PhotoCamera,
                                        contentDescription = if (canSend) "Отправить комментарий" else "Прикрепить медиа",
                                        tint = Color.White,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Inline Emoji / Sticker / GIF Picker Panel
                    AnimatedVisibility(
                        visible = showMediaPanel,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                    ) {
                        TelegramMediaPanel(
                            initialTab = MediaPanelTab.GIF,
                            onEmojiSelected = { emojiItem ->
                                inputText += emojiItem.emoji
                                MediaExpressionManager.recordUsedEmoji(emojiItem)
                            },
                            onBackspaceClick = {
                                if (inputText.isNotEmpty()) {
                                    inputText = inputText.dropLast(1)
                                }
                            },
                            onStickerSelected = { pack, sticker ->
                                MediaExpressionManager.recordUsedSticker(pack.id, sticker)
                                viewModel.sendPostComment(
                                    channelId = channelId,
                                    postId = postId,
                                    text = sticker.emoji,
                                    postAsChannel = isCommentingAsChannel,
                                    mediaPath = sticker.id,
                                    mediaType = "sticker",
                                    documentData = MediaExpressionManager.buildStickerDocumentData(pack.id, sticker),
                                    replyToComment = replyingToComment
                                ) {
                                    replyingToComment = null
                                    showMediaPanel = false
                                }
                            },
                            onGifSelected = { gif ->
                                viewModel.sendPostComment(
                                    channelId = channelId,
                                    postId = postId,
                                    text = gif.title.ifBlank { "GIF" },
                                    postAsChannel = isCommentingAsChannel,
                                    mediaPath = gif.gifUrl,
                                    mediaType = "gif",
                                    documentData = GifSearchRepository.buildGifDocumentData(gif),
                                    replyToComment = replyingToComment
                                ) {
                                    replyingToComment = null
                                    showMediaPanel = false
                                }
                            },
                            onOpenSettings = { showMediaPanel = false }
                        )
                    }
                }
            }
        ) { padding ->
            val currentPost = post
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentPost != null) {
                    // Centered date pill (e.g., "16 ноября")
                    item(key = "post_date_pill") {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = CommentsPillDark.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = formatPostDatePill(currentPost.timestamp),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                        .testTag("post_comments_date_pill")
                                )
                            }
                        }
                    }

                    // Original Channel Post Preview Card + Right Circular Forward Button
                    item(key = "original_post_card") {
                        OriginalChannelPostPreviewRow(
                            post = currentPost,
                            channelTitle = channelTitle,
                            channelEmojiStatus = channelCustomization.emojiStatus,
                            channelAccentColor = channelScheme.accentColor,
                            onForwardClick = { showForwardSheet = true }
                        )
                    }
                }

                if (comments.isEmpty()) {
                    item(key = "comments_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CommentsPillDark.copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = "Комментариев пока нет...",
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 7.dp)
                                        .testTag("post_comments_empty_pill")
                                )
                            }
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        PostCommentBubble(
                            comment = comment,
                            channelAccentColor = channelScheme.accentColor,
                            channelEmojiStatus = if (comment.isChannelIdentity) channelCustomization.emojiStatus else null,
                            onReplyClick = { replyingToComment = comment }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginalChannelPostPreviewRow(
    post: Message,
    channelTitle: String,
    channelEmojiStatus: String?,
    channelAccentColor: Color,
    onForwardClick: () -> Unit
) {
    val lightboxItem = remember(post) {
        extractLightboxMediaItem(post, channelTitle, isMe = false)
    }
    val gifItem = remember(post) {
        GifSearchRepository.parseGifFromMessage(post)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .testTag("post_comments_original_card"),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 6.dp,
                bottomEnd = 18.dp
            ),
            color = CommentsCardDark.copy(alpha = 0.96f),
            border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Channel Title + Emoji status on left, "канал" tag on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channelTitle,
                            color = channelAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!channelEmojiStatus.isNullOrBlank()) {
                            Spacer(Modifier.width(5.dp))
                            Text(text = channelEmojiStatus, fontSize = 13.sp)
                        }
                    }
                    Text(
                        text = "канал",
                        color = CommentsMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                // Media preview if present
                if (lightboxItem != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(lightboxItem.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = lightboxItem.caption.ifBlank { "Изображение публикации" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 260.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    if (lightboxItem.caption.isNotBlank()) {
                        Text(
                            text = lightboxItem.caption,
                            color = Color.White,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                } else if (gifItem != null) {
                    GifGridTile(
                        gif = gifItem,
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else if (post.text.isNotBlank()) {
                    Text(
                        text = post.text,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                }

                // Views + Pin + Timestamp row
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = CommentsMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "2",
                        color = CommentsMuted,
                        fontSize = 11.sp
                    )
                    if (post.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Закреплено",
                            tint = CommentsMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = formatTime(post.timestamp),
                        color = CommentsMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Circular forward/share action button on right of card
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onForwardClick)
                .testTag("post_comments_forward_button"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = CommentsPillDark.copy(alpha = 0.90f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Forward,
                        contentDescription = "Переслать публикацию",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PostCommentBubble(
    comment: ChannelPostCommentEntity,
    channelAccentColor: Color,
    channelEmojiStatus: String?,
    onReplyClick: () -> Unit
) {
    val gifMsg = remember(comment.documentData, comment.mediaPath, comment.mediaType) {
        if (comment.mediaType == "gif" && !comment.documentData.isNullOrBlank()) {
            GifSearchRepository.parseGifFromMessage(
                Message(
                    id = comment.id,
                    senderId = comment.senderId,
                    text = comment.text,
                    mediaPath = comment.mediaPath,
                    mediaType = comment.mediaType,
                    documentData = comment.documentData
                )
            )
        } else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_comment_item_${comment.id}"),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommentsMonogramAvatar(
            id = if (comment.isChannelIdentity) comment.channelId else comment.senderId,
            title = comment.senderName,
            avatarUrl = comment.senderAvatarUrl,
            accentColor = if (comment.isChannelIdentity) channelAccentColor else CommentsPurple,
            size = 34.dp
        )

        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(min = 140.dp, max = 290.dp)
                .clickable(onClick = onReplyClick),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            color = CommentsCardDark.copy(alpha = 0.95f),
            border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = comment.senderName,
                            color = if (comment.isChannelIdentity) channelAccentColor else Color(0xFFFF9E66),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!channelEmojiStatus.isNullOrBlank()) {
                            Spacer(Modifier.width(4.dp))
                            Text(text = channelEmojiStatus, fontSize = 12.sp)
                        }
                    }
                    if (comment.isChannelIdentity) {
                        Text(
                            text = "канал",
                            color = CommentsMuted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                if (!comment.replyToSnippet.isNullOrBlank()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                text = comment.replyToAuthorName ?: "Ответ",
                                color = CommentsPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = comment.replyToSnippet,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (comment.mediaType == "image" && !comment.mediaPath.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(comment.mediaPath)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Изображение комментария",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 200.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else if (gifMsg != null) {
                    GifGridTile(
                        gif = gifMsg,
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                if (comment.text.isNotBlank()) {
                    Text(
                        text = comment.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }

                Text(
                    text = formatTime(comment.timestamp),
                    color = CommentsMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun CommentsMonogramAvatar(
    id: String,
    title: String,
    avatarUrl: String?,
    accentColor: Color = Color(0xFFE59A4C),
    size: Dp
) {
    val context = LocalContext.current
    val resolvedModel = remember(avatarUrl, id) {
        when {
            !avatarUrl.isNullOrBlank() -> avatarUrl
            AvatarStorageManager.hasCustomAvatar(context, EntityType.CHANNEL, id) ->
                AvatarStorageManager.getAvatar(context, EntityType.CHANNEL, id)
            AvatarStorageManager.hasCustomAvatar(context, EntityType.GROUP, id) ->
                AvatarStorageManager.getAvatar(context, EntityType.GROUP, id)
            AvatarStorageManager.hasCustomAvatar(context, EntityType.ACCOUNT, id) ->
                AvatarStorageManager.getAvatar(context, EntityType.ACCOUNT, id)
            else -> null
        }
    }
    val monogram = remember(title) {
        val cleaned = title.trim()
        if (cleaned.length >= 2) cleaned.take(2).uppercase() else cleaned.take(1).uppercase().ifBlank { "K" }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor,
                        accentColor.copy(alpha = 0.72f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = monogram,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
            maxLines = 1
        )
        if (!resolvedModel.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedModel)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}
