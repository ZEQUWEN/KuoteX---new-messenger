@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.channel

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.AvatarStorageManager
import com.example.data.EntityType
import com.example.ui.AppViewModel
import com.example.ui.components.TelegramEmojiPickerBottomSheet
import com.example.ui.navigateSafe
import com.example.ui.popBackStackSafe
import kotlinx.coroutines.launch

/**
 * ChannelGroupAdminScreen ("Изменить")
 * Matches Telegram mobile's Channel Edit & Administration hub (Screenshots 161233 / 161237).
 * All administration sections navigate to dedicated full screens guarded by ChannelAdminGuard.
 */
@Composable
fun ChannelGroupAdminScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId }
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()

    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    val currentMember = findCurrentChannelMember(members, activeAccount)
    val isCreator = currentMember?.role == "creator"
    val hasFullRights = currentMember?.let { hasFullChannelAdminRights(it) } == true
    val canChangeInfo = isCreator || hasFullRights || (currentMember?.canChangeChannelInfo == true)
    val canAccessDm = isCreator || hasFullRights || (currentMember?.canAccessDirectMessages == true)
    val canManageGreetings = isCreator || hasFullRights || (currentMember?.canManageGreetings == true)
    val canBanUsers = isCreator || hasFullRights || (currentMember?.canBanUsers == true)

    val adminsCount = members.count {
        it.isAdmin || it.role == "creator" || it.role == "admin"
    }.coerceAtLeast(1)
    val isGroup = chat?.isGroup == true && chat.isChannel != true
    val subscribersCount = if (isGroup) members.size.coerceAtLeast(1) else maxOf(customization.subscriberCount, members.size)
    val blacklistCount = customization.blacklistedUsers.size
    val inviteLinks by viewModel.observeGroupInviteLinks(chatId).collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingJoinRequests by viewModel.observePendingJoinRequests(chatId).collectAsStateWithLifecycle(initialValue = emptyList())

    var channelTitle by remember(chatId, chat?.title) { mutableStateOf(chat?.title ?: "Канал") }
    var channelDesc by remember(chatId) { mutableStateOf(customization.description) }
    var selectedStatusEmoji by remember(chatId) { mutableStateOf(customization.emojiStatus ?: "🪫") }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showBoostRequirementDialog by remember { mutableStateOf(false) }
    var showAffiliateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var deleteForAllSubscribers by remember { mutableStateOf(true) }
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
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val entityType = if (chat?.isGroup == true) EntityType.GROUP else EntityType.CHANNEL
                val savedPath = AvatarStorageManager.saveAvatarFromUri(context, uri, entityType, chatId)
                ChannelCustomizationManager.updateAvatar(chatId, savedPath)
                Toast.makeText(context, "Фотография успешно сохранена!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showEmojiPicker) {
        TelegramEmojiPickerBottomSheet(
            onDismissRequest = { showEmojiPicker = false },
            selectedEmoji = selectedStatusEmoji,
            onEmojiSelected = { emojiItem ->
                selectedStatusEmoji = emojiItem.emoji
            },
            title = "Выберите статус канала"
        )
    }

    if (showBoostRequirementDialog) {
        AlertDialog(
            onDismissRequest = { showBoostRequirementDialog = false },
            containerColor = Color(0xFF171C26),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFFFD54F))
                    Spacer(Modifier.width(8.dp))
                    Text("Требуется Уровень 3", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Text(
                    "Автоматический перевод сообщений доступен каналам с Уровнем 3 бустов.\n\nТекущий статус: Уровень ${customization.boostLevel} (${customization.boostCount}/${customization.boostsRequiredForNextLevel})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBoostRequirementDialog = false
                        navController.navigateSafe("channel_boost/$chatId")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F))
                ) {
                    Text("Получить бусты", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBoostRequirementDialog = false }) {
                    Text("Понятно", color = TelegramAdminCaptionColor)
                }
            }
        )
    }

    if (showAffiliateDialog) {
        AlertDialog(
            onDismissRequest = { showAffiliateDialog = false },
            containerColor = Color(0xFF171C26),
            title = { Text("⭐ Партнёрские программы", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Зарабатывайте Звёзды (⭐) и вознаграждение, рекомендуя каналы, ботов и мини-приложения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0E131D),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Комиссия канала: до 20%", fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F), fontSize = 13.sp)
                            Text("Выплаты автоматически в Звёздах Telegram.", style = MaterialTheme.typography.labelSmall, color = TelegramAdminCaptionColor)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAffiliateDialog = false }) {
                    Text("Понятно", color = TelegramAdminPurple)
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF191B28),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Удалить канал?", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Вы действительно хотите удалить канал «$channelTitle»? Все сообщения и подписчики будут удалены безвозвратно.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(14.dp))
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
                        Text("Удалить для всех подписчиков", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteChat(chatId)
                        Toast.makeText(context, "Канал «$channelTitle» удалён", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Отмена", color = TelegramAdminPurple)
                }
            }
        )
    }

    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            containerColor = Color(0xFF191B28),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFFF5252))
                    Spacer(Modifier.width(10.dp))
                    Text("Покинуть канал?", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "Вы действительно хотите покинуть канал «$channelTitle»?",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmDialog = false
                        viewModel.deleteChat(chatId)
                        Toast.makeText(context, "Вы покинули канал «$channelTitle»", Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { showLeaveConfirmDialog = false }) {
                    Text("Отмена", color = TelegramAdminPurple)
                }
            }
        )
    }

    Scaffold(
        containerColor = TelegramAdminBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramAdminBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        TelegramCircularIconButton(
                            onClick = { navController.popBackStackSafe() },
                            contentDescription = "Назад",
                            testTag = "channel_admin_back"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Изменить",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        TelegramAdminSavePill(
                            testTag = "channel_admin_save",
                            onClick = {
                                if (canChangeInfo) {
                                    if (channelTitle.isNotBlank()) {
                                        viewModel.updateChatTitle(chatId, channelTitle)
                                    }
                                    ChannelCustomizationManager.updateChannelInfo(chatId, channelDesc, customization.inviteLink)
                                    ChannelCustomizationManager.updateEmojiStatus(chatId, selectedStatusEmoji, isAnimated = true)
                                    Toast.makeText(context, "Изменения сохранены!", Toast.LENGTH_SHORT).show()
                                    navController.popBackStackSafe()
                                } else {
                                    Toast.makeText(context, "Нет прав на изменение профиля канала", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp)
        ) {
            // --- TOP HEADER CARD (Avatar, Title + Underline, Photo Picker, Description) ---
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFFBA7EFC), Color(0xFF8F52E6))))
                                    .clickable(enabled = canChangeInfo) {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasCustomChannelAvatar || !customization.avatarUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(channelAvatar)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Аватар канала",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = channelTitle.take(2).uppercase().ifEmpty { "KU" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = channelTitle,
                                        onValueChange = { if (canChangeInfo) channelTitle = it },
                                        readOnly = !canChangeInfo,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("channel_title_input"),
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(TelegramAdminPurple),
                                        decorationBox = { innerTextField ->
                                            if (channelTitle.isEmpty()) {
                                                Text("Название канала", color = Color.White.copy(alpha = 0.4f), fontSize = 18.sp)
                                            }
                                            innerTextField()
                                        }
                                    )

                                    Spacer(Modifier.width(4.dp))
                                    AnimatedEmojiStatusBadge(
                                        emoji = selectedStatusEmoji,
                                        onClick = { showEmojiPicker = true }
                                    )
                                    IconButton(
                                        onClick = { showEmojiPicker = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.SentimentSatisfied,
                                            contentDescription = "Выбрать эмодзи статуса",
                                            tint = Color.White.copy(alpha = 0.55f),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.5.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF9C68FC), TelegramAdminPurple.copy(alpha = 0.6f))
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        if (canChangeInfo) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            Icon(
                                Icons.Outlined.AddAPhoto,
                                contentDescription = "Выбрать фотографию",
                                tint = TelegramAdminPurple,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Выбрать фотографию",
                                color = TelegramAdminPurple,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        Spacer(Modifier.height(12.dp))

                        BasicTextField(
                            value = channelDesc,
                            onValueChange = { if (canChangeInfo) channelDesc = it },
                            readOnly = !canChangeInfo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("channel_description_input"),
                            minLines = 2,
                            maxLines = 8,
                            textStyle = TextStyle(
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                            cursorBrush = SolidColor(TelegramAdminPurple),
                            decorationBox = { innerTextField ->
                                if (channelDesc.isEmpty()) {
                                    Text("Описание", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                                }
                                innerTextField()
                            }
                        )
                    }
                }
            }

            // --- CARD 1: Тип канала, Обсуждение, Сообщения каналу, Оформление, Автоперевод ---
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        AdminMenuRow(
                            icon = Icons.Filled.Campaign,
                            title = if (isGroup) "Тип группы" else "Тип канала",
                            value = if (customization.isPublic) (if (isGroup) "Публичная" else "Публичный") else (if (isGroup) "Частная" else "Частный"),
                            testTag = "admin_row_channel_type",
                            onClick = {
                                if (canChangeInfo) {
                                    navController.navigateSafe("channel_admin/$chatId/type")
                                } else {
                                    Toast.makeText(context, "Нет прав на изменение настроек", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        val linkedGroup = customization.discussionChatId?.let { linkedId ->
                            chats.find { it.id == linkedId && it.isGroup }
                        }
                        val linkedGroupCustom = linkedGroup?.id?.let { customizationsMap[it] }
                        AdminMenuRow(
                            icon = Icons.Filled.ChatBubbleOutline,
                            title = "Обсуждение",
                            value = if (linkedGroup != null) linkedGroup.title else "Добавить",
                            testTag = "admin_row_discussion",
                            trailingContent = if (linkedGroup != null) {
                                {
                                    LinkedGroupAvatar(
                                        groupId = linkedGroup.id,
                                        groupTitle = linkedGroup.title,
                                        avatarUrl = linkedGroupCustom?.avatarUrl,
                                        avatarSize = 32.dp,
                                        testTag = "admin_linked_group_avatar",
                                        onClick = {
                                            navController.navigateSafe("chat/${linkedGroup.id}")
                                        }
                                    )
                                }
                            } else null,
                            onClick = {
                                if (canChangeInfo) {
                                    navController.navigateSafe("channel_admin/$chatId/discussion")
                                } else {
                                    Toast.makeText(context, "Нет прав на изменение профиля канала", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminMenuRow(
                            icon = Icons.Filled.Star,
                            title = "Сообщения каналу",
                            value = if (customization.directMessagesEnabled) "⭐ ${customization.directMessageStarPrice}" else "Выкл.",
                            iconTint = Color(0xFFFFD54F),
                            testTag = "admin_row_direct_messages",
                            onClick = {
                                if (canAccessDm) {
                                    navController.navigateSafe("channel_admin/$chatId/direct_messages")
                                } else {
                                    Toast.makeText(context, "Нет прав на доступ к сообщениям каналу", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminMenuRow(
                            icon = Icons.Filled.Palette,
                            title = "Оформление",
                            value = chat?.title?.take(10) ?: "KuoteX ✨",
                            badgeText = "🔒 Уровень 1+",
                            testTag = "admin_row_appearance",
                            onClick = { navController.navigateSafe("channel_appearance/$chatId") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminSwitchMenuRow(
                            icon = Icons.Filled.Translate,
                            title = "Автоперевод сообщений",
                            checked = customization.autoTranslateEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && customization.boostLevel < 3) {
                                    showBoostRequirementDialog = true
                                } else {
                                    ChannelCustomizationManager.updateAutoTranslate(chatId, isChecked)
                                }
                            }
                        )
                    }
                }
            }

            // --- CARD 2: Реакции, Приветствие, Администраторы, Подписчики, Чёрный список, Статистика, Недавние действия, Партнёрские программы ---
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        AdminMenuRow(
                            icon = Icons.Filled.FavoriteBorder,
                            title = "Реакции",
                            value = if (customization.reactionsEnabled) "${customization.availableReactions.size}" else "Выкл.",
                            testTag = "admin_row_reactions",
                            onClick = { navController.navigateSafe("channel_admin/$chatId/reactions") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        val effectiveGreetings = ChannelCustomizationManager.getEffectiveGreetingMessages(customization)
                        AdminMenuRow(
                            icon = Icons.Filled.WavingHand,
                            title = "Приветствие",
                            value = when {
                                effectiveGreetings.isNotEmpty() -> "${effectiveGreetings.size} сообщ."
                                customization.autoGreetingEnabled -> "Вкл."
                                else -> "Выкл."
                            },
                            testTag = "admin_row_greeting",
                            onClick = {
                                if (canManageGreetings) {
                                    navController.navigateSafe("channel_admin/$chatId/greeting")
                                } else {
                                    Toast.makeText(context, "Нет прав на управление приветствиями", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminMenuRow(
                            icon = Icons.Filled.Shield,
                            title = "Администраторы",
                            value = "$adminsCount",
                            testTag = "admin_row_admins",
                            onClick = { navController.navigateSafe("channel_admin/$chatId/admins") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminMenuRow(
                            icon = Icons.Filled.People,
                            title = if (isGroup) "Участники" else "Подписчики",
                            value = "$subscribersCount",
                            testTag = "admin_row_subscribers",
                            onClick = { navController.navigateSafe("channel_admin/$chatId/subscribers") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminMenuRow(
                            icon = Icons.Filled.Groups,
                            title = "Пригласительные ссылки",
                            value = if (inviteLinks.isNotEmpty()) "${inviteLinks.size}" else "1",
                            testTag = "admin_row_invite_links",
                            onClick = { navController.navigateSafe(com.example.ui.navigation.AppDestinations.groupInviteLinks(chatId)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        if (isGroup && pendingJoinRequests.isNotEmpty()) {
                            AdminMenuRow(
                                icon = Icons.Filled.People,
                                title = "Заявки на вступление",
                                value = "${pendingJoinRequests.size}",
                                testTag = "admin_row_join_requests",
                                onClick = { navController.navigateSafe(com.example.ui.navigation.AppDestinations.groupJoinRequests(chatId)) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)
                        }

                        AdminMenuRow(
                            icon = Icons.Filled.Block,
                            title = "Чёрный список",
                            value = "$blacklistCount",
                            testTag = "admin_row_blacklist",
                            onClick = {
                                if (canBanUsers) {
                                    navController.navigateSafe("channel_admin/$chatId/blacklist")
                                } else {
                                    Toast.makeText(context, "Нет прав на блокировку пользователей", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminMenuRow(
                            icon = Icons.Filled.BarChart,
                            title = "Статистика",
                            testTag = "admin_row_stats",
                            onClick = { navController.navigateSafe("channel_admin/$chatId/stats") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminMenuRow(
                            icon = Icons.Filled.History,
                            title = "Недавние действия",
                            testTag = "admin_row_recent_actions",
                            onClick = { navController.navigateSafe("channel_admin/$chatId/recent_actions") }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = TelegramAdminDividerColor)

                        AdminMenuRow(
                            icon = Icons.Filled.WorkspacePremium,
                            title = "Партнёрские программы",
                            badgeText = "NEW",
                            badgeColor = Color(0xFF2B8DF8),
                            testTag = "admin_row_affiliate",
                            onClick = { showAffiliateDialog = true }
                        )
                    }
                }
            }

            // --- CARD 3: Добавить канал в сообщество + Caption ---
            item {
                Surface(
                    onClick = {
                        Toast.makeText(context, "Выбор сообщества чатов...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = null, tint = TelegramAdminPurple, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Добавить канал в сообщество",
                            color = TelegramAdminPurple,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Вы можете добавить канал в сообщество чатов, связанных между собой.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // --- DANGER CARD: Удалить канал (or Покинуть канал for non-creator admin) ---
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        if (!isCreator) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLeaveConfirmDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Покинуть канал",
                                    color = Color(0xFFFF5252),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                            }
                            HorizontalDivider(color = TelegramAdminDividerColor)
                        }
                        if (isCreator) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDeleteConfirmDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 15.dp)
                                    .testTag("delete_channel_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Удалить канал",
                                    color = Color(0xFFFF5252),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
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
private fun AnimatedEmojiStatusBadge(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_emoji_anim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emoji_scale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp, modifier = Modifier.scale(scale))
    }
}

@Composable
private fun AdminMenuRow(
    icon: ImageVector,
    title: String,
    value: String = "",
    badgeText: String? = null,
    badgeColor: Color = Color(0xFF8E44EC),
    valueColor: Color = TelegramAdminPurple,
    iconTint: Color = Color.White.copy(alpha = 0.65f),
    testTag: String = "",
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        if (badgeText != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
            Spacer(Modifier.width(6.dp))
        }

        if (value.isNotEmpty()) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AdminSwitchMenuRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = Color.White.copy(alpha = 0.65f)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TelegramAdminPurpleStrong,
                uncheckedThumbColor = Color(0xFF9EA5B1),
                uncheckedTrackColor = Color(0xFF282E3A)
            )
        )
    }
}
