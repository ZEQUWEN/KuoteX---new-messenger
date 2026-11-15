@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.group

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
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
import com.example.ui.popBackStackSafe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReqBg = Color(0xFF0A0D12)
private val ReqCardBg = Color(0xFF161B24)
private val ReqPillBg = Color(0xFF1F242F)
private val ReqPurple = Color(0xFFB072FF)
private val ReqPurpleStrong = Color(0xFF9C5CFF)
private val ReqCaption = Color(0xFF858C99)

@Composable
fun GroupJoinRequestsBanner(
    requests: List<GroupJoinRequestEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (requests.isEmpty()) return
    val count = requests.size
    val label = when {
        count % 10 == 1 && count % 100 != 11 -> "$count заявка на вступление"
        count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "$count заявки на вступление"
        else -> "$count заявок на вступление"
    }

    Surface(
        onClick = onClick,
        color = Color(0xFF171D28),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("group_join_requests_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                requests.take(3).forEachIndexed { index, req ->
                    Box(
                        modifier = Modifier
                            .offset(x = (-8 * index).dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFF171D28), CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF9C5CFF), Color(0xFF2AABEE)))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (req.userAvatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = req.userAvatarUrl,
                                contentDescription = req.userName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = req.userName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = ReqPurple,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Открыть заявки",
                tint = ReqCaption
            )
        }
    }
}

@Composable
fun GroupJoinRequestsScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    BackHandler { navController.popBackStackSafe() }

    val context = LocalContext.current
    val requests by viewModel.observePendingJoinRequests(chatId).collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(requests, searchQuery) {
        if (searchQuery.isBlank()) requests else requests.filter {
            it.userName.contains(searchQuery, ignoreCase = true) ||
                it.inviteLinkLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = ReqBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ReqBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        Surface(
                            onClick = { navController.popBackStackSafe() },
                            shape = CircleShape,
                            color = ReqPillBg,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("join_requests_back")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                            }
                        }
                    }
                },
                title = {
                    Text(
                        text = "Заявки на вступление",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
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
                placeholder = { Text("Поиск заявок...", color = ReqCaption) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = ReqCaption) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ReqCardBg,
                    unfocusedContainerColor = ReqCardBg,
                    focusedBorderColor = ReqPurpleStrong,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.HowToReg,
                            contentDescription = null,
                            tint = ReqCaption,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Нет активных заявок на вступление",
                            color = ReqCaption,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { req ->
                        JoinRequestCard(
                            request = req,
                            onApprove = {
                                viewModel.approveJoinRequest(req)
                                Toast.makeText(context, "${req.userName} принят(а) в группу", Toast.LENGTH_SHORT).show()
                            },
                            onDecline = {
                                viewModel.declineJoinRequest(req)
                                Toast.makeText(context, "Заявка отклонена", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinRequestCard(
    request: GroupJoinRequestEntity,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val timeStr = remember(request.requestedAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(request.requestedAt))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ReqCardBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("join_request_card_${request.userId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF9C5CFF), Color(0xFF2AABEE)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (request.userAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(request.userAvatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = request.userName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = request.userName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.userName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("подал(а) заявку сегодня в $timeStr")
                            if (request.inviteLinkLabel.isNotBlank()) {
                                append(" · ${request.inviteLinkLabel}")
                            }
                        },
                        color = ReqCaption,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = ReqPurpleStrong),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("approve_request_${request.userId}")
                ) {
                    Text("Принять в группу", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onDecline,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("decline_request_${request.userId}")
                ) {
                    Text("Отклонить", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun GroupInviteJoinPreviewSheet(
    resolution: InvitePreviewResolution,
    onJoinOrRequest: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171B24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = ReqCaption)
                }
            }

            if (!resolution.isValid || resolution.chat == null) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Ссылка недействительна",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = resolution.errorMessage ?: "Срок действия ссылки истёк или она была отозвана.",
                    color = ReqCaption,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ReqPillBg),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Закрыть", color = Color.White)
                }
            } else {
                val chat = resolution.chat
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF9C5CFF), Color(0xFF2AABEE)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (!resolution.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(resolution.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = chat.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = chat.title.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = chat.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                val subtitle = if (resolution.canSeePrivateBadge) {
                    if (resolution.isPublic) "публичная группа" else "частная группа"
                } else {
                    formatParticipantCount(resolution.memberCount)
                }
                Text(
                    text = subtitle,
                    color = ReqCaption,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(20.dp))

                when {
                    resolution.isAlreadyMember -> {
                        Button(
                            onClick = { onOpenGroup(chat.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = ReqPurpleStrong),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("invite_preview_open_group")
                        ) {
                            Text("Открыть группу", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    resolution.hasPendingRequest -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ReqPillBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Заявка на вступление отправлена",
                                    color = ReqPurple,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = onJoinOrRequest,
                            colors = ButtonDefaults.buttonColors(containerColor = ReqPurpleStrong),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("invite_preview_join_or_request")
                        ) {
                            Text(
                                text = if (resolution.requiresApproval) "Подать заявку" else "Вступить в группу",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        if (resolution.requiresApproval) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Вы сможете вступить в группу, когда администраторы одобрят Вашу заявку.",
                                color = ReqCaption,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
