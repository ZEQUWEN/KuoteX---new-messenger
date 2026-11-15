@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.group

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.ui.navigateSafe
import com.example.ui.navigation.AppDestinations
import com.example.ui.popBackStackSafe

private val AddMembersBg = Color(0xFF0A0D12)
private val AddMembersCardBg = Color(0xFF161B24)
private val AddMembersPillBg = Color(0xFF1F242F)
private val AddMembersPurple = Color(0xFFB072FF)
private val AddMembersPurpleStrong = Color(0xFF9C5CFF)
private val AddMembersCaption = Color(0xFF858C99)

@Composable
fun GroupAddMembersScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    BackHandler { navController.popBackStackSafe() }

    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId }
    val candidates by viewModel.observeInvitableUsers(chatId).collectAsStateWithLifecycle(initialValue = emptyList())
    val inviteLinks by viewModel.observeGroupInviteLinks(chatId).collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(chatId) {
        viewModel.ensurePrimaryInviteLink(chatId)
    }

    val primaryInviteUrl = remember(inviteLinks) {
        inviteLinks.firstOrNull { it.isPrimary && it.isActive() }?.url
            ?: inviteLinks.firstOrNull { it.isActive() }?.url
            ?: "t.me/+$chatId"
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showConfirmAddDialog by remember { mutableStateOf(false) }
    var showQuickInviteSheet by remember { mutableStateOf(false) }
    var showQuickQrDialog by remember { mutableStateOf(false) }
    var privacyBlockedUsers by remember { mutableStateOf<List<InvitableUser>>(emptyList()) }

    val filtered = remember(candidates, searchQuery) {
        if (searchQuery.isBlank()) candidates else candidates.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    val selectedCandidates = remember(candidates, selectedIds) {
        candidates.filter { selectedIds.contains(it.userId) }
    }

    if (showQuickQrDialog) {
        GroupInviteQrDialog(
            title = chat?.title ?: "Группа KuoteX",
            inviteUrl = primaryInviteUrl,
            onDismiss = { showQuickQrDialog = false }
        )
    }

    if (showQuickInviteSheet) {
        GroupInviteLinkQuickSheet(
            inviteUrl = primaryInviteUrl,
            groupTitle = chat?.title ?: "Группа KuoteX",
            onDismiss = { showQuickInviteSheet = false },
            onManageInvites = {
                navController.navigateSafe(AppDestinations.groupInviteLinks(chatId))
            },
            onOpenQr = {
                showQuickInviteSheet = false
                showQuickQrDialog = true
            }
        )
    }

    if (showConfirmAddDialog && selectedCandidates.isNotEmpty()) {
        val count = selectedCandidates.size
        AlertDialog(
            onDismissRequest = { showConfirmAddDialog = false },
            containerColor = Color(0xFF171C26),
            title = {
                Text(
                    text = if (count == 1) "Добавить участника?" else "Добавить участников?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val names = selectedCandidates.joinToString(", ") { it.displayName }
                Text(
                    text = "Добавить в группу «${chat?.title ?: "Группа"}»: $names?",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmAddDialog = false
                        viewModel.attemptAddGroupMembers(chatId, selectedCandidates) { outcome ->
                            selectedIds = emptySet()
                            if (outcome.addedUsers.isNotEmpty()) {
                                val addedNames = outcome.addedUsers.joinToString(", ") { it.displayName }
                                Toast.makeText(context, "Добавлено в группу: $addedNames", Toast.LENGTH_SHORT).show()
                            }
                            if (outcome.privacyBlockedUsers.isNotEmpty()) {
                                privacyBlockedUsers = outcome.privacyBlockedUsers
                            } else {
                                navController.popBackStackSafe()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AddMembersPurpleStrong),
                    modifier = Modifier.testTag("dialog_confirm_add_members")
                ) {
                    Text("Добавить", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmAddDialog = false }) {
                    Text("Отмена", color = AddMembersPurple)
                }
            }
        )
    }

    if (privacyBlockedUsers.isNotEmpty()) {
        PrivacyBlockedInviteSheet(
            blockedUsers = privacyBlockedUsers,
            groupTitle = chat?.title ?: "Группа KuoteX",
            onSendInviteLinks = { usersToSend ->
                viewModel.sendGroupInviteLinkToUsers(chatId, usersToSend) { sent ->
                    privacyBlockedUsers = emptyList()
                    Toast.makeText(
                        context,
                        "Пригласительная ссылка отправлена в личные сообщения ($sent)",
                        Toast.LENGTH_SHORT
                    ).show()
                    navController.popBackStackSafe()
                }
            },
            onDismiss = { privacyBlockedUsers = emptyList() }
        )
    }

    Scaffold(
        containerColor = AddMembersBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AddMembersBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        Surface(
                            onClick = { navController.popBackStackSafe() },
                            shape = CircleShape,
                            color = AddMembersPillBg,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("add_members_back")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                            }
                        }
                    }
                },
                title = {
                    Text(
                        text = "Добавить участников",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                actions = {
                    if (selectedCandidates.isNotEmpty()) {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            Surface(
                                onClick = { showConfirmAddDialog = true },
                                shape = RoundedCornerShape(21.dp),
                                color = AddMembersPurpleStrong,
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("confirm_add_members_button")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Добавить (${selectedCandidates.size})",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск людей...", color = AddMembersCaption) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = AddMembersCaption) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AddMembersCardBg,
                    unfocusedContainerColor = AddMembersCardBg,
                    focusedBorderColor = AddMembersPurpleStrong,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("add_members_search_input")
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        onClick = { showQuickInviteSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        color = AddMembersCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invite_by_link_quick_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, tint = AddMembersPurple, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Пригласить в группу по ссылке",
                                color = AddMembersPurple,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Нет доступных пользователей для добавления",
                                color = AddMembersCaption,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.userId }) { user ->
                        val isSelected = selectedIds.contains(user.userId)
                        Surface(
                            onClick = {
                                selectedIds = if (isSelected) selectedIds - user.userId else selectedIds + user.userId
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = AddMembersCardBg,
                            border = if (isSelected) BorderStroke(1.dp, AddMembersPurpleStrong) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("invitable_user_${user.userId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InvitableUserAvatar(user.displayName, user.avatarUrl, user.userId)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = user.statusText,
                                        color = AddMembersCaption,
                                        fontSize = 13.sp
                                    )
                                }
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = if (isSelected) "Выбран" else "Не выбран",
                                    tint = if (isSelected) AddMembersPurpleStrong else AddMembersCaption,
                                    modifier = Modifier.size(22.dp)
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
fun PrivacyBlockedInviteSheet(
    blockedUsers: List<InvitableUser>,
    groupTitle: String,
    onSendInviteLinks: (List<InvitableUser>) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171B24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Пригласить по ссылке",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(10.dp))

            val messageText = if (blockedUsers.size == 1) {
                "${blockedUsers.first().displayName} запретил(а) добавлять себя в группы, но Вы можете отправить пригласительную ссылку."
            } else {
                val names = blockedUsers.joinToString(", ") { it.displayName }
                "$names запретили добавлять себя в группы, но Вы можете отправить пригласительную ссылку в группу «$groupTitle»."
            }

            Text(
                text = messageText,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                blockedUsers.forEach { user ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AddMembersCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InvitableUserAvatar(user.displayName, user.avatarUrl, user.userId)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = user.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Выбран",
                                tint = AddMembersPurpleStrong,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { onSendInviteLinks(blockedUsers) },
                colors = ButtonDefaults.buttonColors(containerColor = AddMembersPurpleStrong),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("send_privacy_invite_link_button")
            ) {
                Text(
                    text = "Отправить ссылку (${blockedUsers.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun InvitableUserAvatar(
    displayName: String,
    avatarUrl: String?,
    seed: String
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF7B61FF), Color(0xFF2AABEE)))),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = displayName.trim().take(1).ifBlank { seed.take(1) }.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
    }
}
