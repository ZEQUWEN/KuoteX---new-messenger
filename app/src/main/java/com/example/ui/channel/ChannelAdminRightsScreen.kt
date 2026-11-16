@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.channel

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.AppViewModel
import com.example.ui.popBackStackSafe

/**
 * Telegram-style Admin Rights configuration screen ("Права администратора").
 * Accessible exclusively to the channel creator and administrators who hold
 * creator-equivalent permissions, typically opened via the 3-dots icon on an admin row.
 */
@Composable
fun ChannelAdminRightsScreen(
    viewModel: AppViewModel,
    chatId: String,
    targetUserId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()

    val chat = chats.find { it.id == chatId }
    val currentMember = findCurrentChannelMember(members, activeAccount)
    val isCurrentUserCreator = currentMember?.role == "creator"

    val targetMember = members.find { it.userId == targetUserId }
    val targetContact = contacts.find { it.id == targetUserId }
    val targetName = targetMember?.userName ?: targetContact?.name ?: "Администратор"

    val isTargetExistingAdmin = targetMember != null && (targetMember.isAdmin || targetMember.role == "admin")
    val isTargetCreator = targetMember?.role == "creator"

    var handledInvalidTarget by remember(targetUserId) { mutableStateOf(false) }
    LaunchedEffect(isTargetCreator) {
        if (isTargetCreator && !handledInvalidTarget) {
            handledInvalidTarget = true
            Toast.makeText(context, "Права владельца канала нельзя изменить", Toast.LENGTH_SHORT).show()
            navController.popBackStackSafe()
        }
    }

    val initKey = "${targetUserId}_${isTargetExistingAdmin}_${targetMember != null}"

    var canChangeChannelInfo by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canChangeChannelInfo else true)
    }
    var canPostMessages by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canPostMessages else true)
    }
    var canEditMessages by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canEditMessages else true)
    }
    var canDeleteMessages by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canDeleteMessages else true)
    }
    var canPostStories by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canPostStories else true)
    }
    var canEditStories by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canEditStories else true)
    }
    var canDeleteStories by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canDeleteStories else true)
    }
    var canAccessDirectMessages by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canAccessDirectMessages else true)
    }
    var canManageGreetings by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canManageGreetings else true)
    }
    var canManageInvites by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canManageInvites else true)
    }
    var canManageStreams by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canManageStreams else true)
    }
    var canAddAdmins by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canAddAdmins else false)
    }
    var canBanUsers by remember(initKey) {
        mutableStateOf(if (isTargetExistingAdmin) targetMember!!.canBanUsers else true)
    }

    var publicationsExpanded by remember { mutableStateOf(true) }
    var storiesExpanded by remember { mutableStateOf(true) }
    var showTransferConfirm by remember { mutableStateOf(false) }
    var showDemoteConfirm by remember { mutableStateOf(false) }

    val publicationsSelectedCount = listOf(canPostMessages, canEditMessages, canDeleteMessages).count { it }
    val storiesSelectedCount = listOf(canPostStories, canEditStories, canDeleteStories).count { it }

    if (showTransferConfirm) {
        AlertDialog(
            onDismissRequest = { showTransferConfirm = false },
            containerColor = Color(0xFF171C26),
            title = {
                Text("Передать права на канал?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Вы передаёте права владельца канала «${chat?.title ?: "Канал"}» пользователю $targetName. Вы останетесь администратором, а $targetName станет владельцем.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTransferConfirm = false
                        val currentOwnerId = currentMember?.userId ?: activeAccount?.id ?: ""
                        viewModel.transferChannelOwnership(
                            chatId = chatId,
                            currentOwnerId = currentOwnerId,
                            newOwnerId = targetUserId,
                            newOwnerName = targetName
                        )
                        ChannelCustomizationManager.recordAdminLog(
                            chatId = chatId,
                            title = "Передача прав владельца",
                            details = "Права на канал переданы пользователю $targetName"
                        )
                        Toast.makeText(context, "Права владельца переданы $targetName", Toast.LENGTH_SHORT).show()
                        navController.popBackStackSafe()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramAdminPurpleStrong),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Передать", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferConfirm = false }) {
                    Text("Отмена", color = TelegramAdminPurple)
                }
            }
        )
    }

    if (showDemoteConfirm) {
        AlertDialog(
            onDismissRequest = { showDemoteConfirm = false },
            containerColor = Color(0xFF171C26),
            title = {
                Text("Разжаловать администратора?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Пользователь $targetName потеряет права администратора и станет обычным подписчиком канала.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDemoteConfirm = false
                        viewModel.demoteChannelAdmin(chatId, targetUserId)
                        ChannelCustomizationManager.recordAdminLog(
                            chatId = chatId,
                            title = "Снятие прав администратора",
                            details = "$targetName разжалован до подписчика"
                        )
                        Toast.makeText(context, "$targetName разжалован", Toast.LENGTH_SHORT).show()
                        navController.popBackStackSafe()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Разжаловать", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoteConfirm = false }) {
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
                            testTag = "admin_rights_back"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Text(
                        text = "Права администратора",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        TelegramAdminSavePill(
                            testTag = "admin_rights_save",
                            onClick = {
                                val promoterName = activeAccount?.displayName?.ifBlank { "Владелец" } ?: "Владелец"
                                viewModel.promoteOrUpdateChannelAdmin(
                                    chatId = chatId,
                                    userId = targetUserId,
                                    userName = targetName,
                                    customTitle = targetMember?.customTitle ?: "",
                                    canPost = canPostMessages,
                                    canEdit = canEditMessages,
                                    canDelete = canDeleteMessages,
                                    canManageStreams = canManageStreams,
                                    canAddAdmins = canAddAdmins,
                                    canChangeChannelInfo = canChangeChannelInfo,
                                    canPostStories = canPostStories,
                                    canEditStories = canEditStories,
                                    canDeleteStories = canDeleteStories,
                                    canAccessDirectMessages = canAccessDirectMessages,
                                    canManageGreetings = canManageGreetings,
                                    canManageInvites = canManageInvites,
                                    canBanUsers = canBanUsers,
                                    promotedByName = promoterName
                                )
                                val actionTitle = if (isTargetExistingAdmin) {
                                    "Обновлены права администратора"
                                } else {
                                    "Назначен новый администратор"
                                }
                                ChannelCustomizationManager.recordAdminLog(
                                    chatId = chatId,
                                    title = actionTitle,
                                    details = "$targetName (права: публ. $publicationsSelectedCount/3, ист. $storiesSelectedCount/3, полные права: ${if (canAddAdmins && canChangeChannelInfo && canBanUsers) "да" else "нет"})"
                                )
                                Toast.makeText(context, "Права $targetName сохранены", Toast.LENGTH_SHORT).show()
                                navController.popBackStackSafe()
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
            contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
        ) {
            // Card 1: Target Admin Header Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberCircleAvatar(name = targetName, seed = targetUserId, sizeDp = 46)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = targetName,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isTargetExistingAdmin) "был(а) недавно" else "новый администратор",
                                color = TelegramAdminCaptionColor,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Card 2: Возможности администратора
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Возможности администратора:",
                            color = TelegramAdminPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                        )

                        AdminRightToggleRow(
                            title = "Изменение профиля канала",
                            checked = canChangeChannelInfo,
                            testTag = "right_change_channel_info",
                            onCheckedChange = { canChangeChannelInfo = it }
                        )
                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 16.dp))

                        // Expandable Group 1: Управление публикациями
                        AdminExpandableGroupHeader(
                            title = "Управление публикациями",
                            selectedCount = publicationsSelectedCount,
                            totalCount = 3,
                            expanded = publicationsExpanded,
                            masterChecked = publicationsSelectedCount > 0,
                            headerTestTag = "right_group_publications_header",
                            switchTestTag = "right_group_publications_switch",
                            onToggleExpand = { publicationsExpanded = !publicationsExpanded },
                            onMasterCheckedChange = { enable ->
                                canPostMessages = enable
                                canEditMessages = enable
                                canDeleteMessages = enable
                            }
                        )
                        AnimatedVisibility(visible = publicationsExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AdminSubCheckRow(
                                    title = "Отправка публикаций",
                                    checked = canPostMessages,
                                    testTag = "right_post_messages",
                                    onClick = { canPostMessages = !canPostMessages }
                                )
                                HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 58.dp))
                                AdminSubCheckRow(
                                    title = "Изменение чужих публикаций",
                                    checked = canEditMessages,
                                    testTag = "right_edit_messages",
                                    onClick = { canEditMessages = !canEditMessages }
                                )
                                HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 58.dp))
                                AdminSubCheckRow(
                                    title = "Удаление чужих публикаций",
                                    checked = canDeleteMessages,
                                    testTag = "right_delete_messages",
                                    onClick = { canDeleteMessages = !canDeleteMessages }
                                )
                            }
                        }
                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 16.dp))

                        // Expandable Group 2: Управление историями
                        AdminExpandableGroupHeader(
                            title = "Управление историями",
                            selectedCount = storiesSelectedCount,
                            totalCount = 3,
                            expanded = storiesExpanded,
                            masterChecked = storiesSelectedCount > 0,
                            headerTestTag = "right_group_stories_header",
                            switchTestTag = "right_group_stories_switch",
                            onToggleExpand = { storiesExpanded = !storiesExpanded },
                            onMasterCheckedChange = { enable ->
                                canPostStories = enable
                                canEditStories = enable
                                canDeleteStories = enable
                            }
                        )
                        AnimatedVisibility(visible = storiesExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AdminSubCheckRow(
                                    title = "Публикация историй",
                                    checked = canPostStories,
                                    testTag = "right_post_stories",
                                    onClick = { canPostStories = !canPostStories }
                                )
                                HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 58.dp))
                                AdminSubCheckRow(
                                    title = "Изменение чужих историй",
                                    checked = canEditStories,
                                    testTag = "right_edit_stories",
                                    onClick = { canEditStories = !canEditStories }
                                )
                                HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 58.dp))
                                AdminSubCheckRow(
                                    title = "Удаление чужих историй",
                                    checked = canDeleteStories,
                                    testTag = "right_delete_stories",
                                    onClick = { canDeleteStories = !canDeleteStories }
                                )
                            }
                        }
                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 16.dp))

                        AdminRightToggleRow(
                            title = "Доступ к сообщениям каналу",
                            checked = canAccessDirectMessages,
                            testTag = "right_direct_messages",
                            onCheckedChange = { canAccessDirectMessages = it }
                        )
                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 16.dp))

                        AdminRightToggleRow(
                            title = "Управление приветствиями",
                            checked = canManageGreetings,
                            testTag = "right_manage_greetings",
                            onCheckedChange = { canManageGreetings = it }
                        )
                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 16.dp))

                        AdminRightToggleRow(
                            title = "Пригласительные ссылки",
                            checked = canManageInvites,
                            testTag = "right_manage_invites",
                            onCheckedChange = { canManageInvites = it }
                        )
                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 16.dp))

                        AdminRightToggleRow(
                            title = "Управление трансляциями",
                            checked = canManageStreams,
                            testTag = "right_manage_streams",
                            onCheckedChange = { canManageStreams = it }
                        )
                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 16.dp))

                        AdminRightToggleRow(
                            title = "Назначение администраторов",
                            checked = canAddAdmins,
                            testTag = "right_add_admins",
                            onCheckedChange = { canAddAdmins = it }
                        )
                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(start = 16.dp))

                        AdminRightToggleRow(
                            title = "Блокировка пользователей",
                            checked = canBanUsers,
                            testTag = "right_ban_users",
                            onCheckedChange = { canBanUsers = it }
                        )
                    }
                }
            }

            // Card 3: Передать права на канал (Creator only)
            if (isCurrentUserCreator && !isTargetCreator && targetMember?.role != "bot" && targetUserId != activeAccount?.id) {
                item {
                    Surface(
                        onClick = { showTransferConfirm = true },
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_channel_ownership_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Передать права на канал",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Card 4: Разжаловать (Red action card when editing an existing admin)
            if (isTargetExistingAdmin && !isTargetCreator && targetUserId != activeAccount?.id) {
                item {
                    Surface(
                        onClick = { showDemoteConfirm = true },
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("demote_admin_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Разжаловать",
                                color = Color(0xFFFF5252),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TelegramAdminPurpleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = TelegramAdminPurple,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color(0xFF171126),
            checkedTrackColor = TelegramAdminPurple,
            checkedBorderColor = TelegramAdminPurple,
            uncheckedThumbColor = Color(0xFF9EA5B1),
            uncheckedTrackColor = Color(0xFF282E3A),
            uncheckedBorderColor = Color(0xFF3E4554)
        )
    )
}

@Composable
private fun AdminRightToggleRow(
    title: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        TelegramAdminPurpleSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun AdminExpandableGroupHeader(
    title: String,
    selectedCount: Int,
    totalCount: Int,
    expanded: Boolean,
    masterChecked: Boolean,
    headerTestTag: String,
    switchTestTag: String,
    onToggleExpand: () -> Unit,
    onMasterCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(headerTestTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$selectedCount/$totalCount",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
        TelegramAdminPurpleSwitch(
            checked = masterChecked,
            onCheckedChange = onMasterCheckedChange,
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}

@Composable
private fun AdminSubCheckRow(
    title: String,
    checked: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) TelegramAdminPurple else Color.Transparent)
                .then(
                    if (!checked) {
                        Modifier.border(BorderStroke(1.5.dp, TelegramAdminCaptionColor), CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Включено",
                    tint = Color(0xFF0E1117),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
    }
}
