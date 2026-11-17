@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.ui.navigateSafe
import com.example.ui.navigation.AppDestinations
import com.example.ui.popBackStackSafe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Channel Admin Inbox ("Входящие сообщения") displaying subscriber direct message threads
 * and pending Suggested Post counts for moderation.
 */
@Composable
fun ChannelAdminDirectInboxScreen(
    viewModel: AppViewModel,
    channelId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle(initialValue = emptyList())
    val threadsMap by ChannelDirectMessageManager.threadsByChannel.collectAsStateWithLifecycle()

    LaunchedEffect(context) {
        ChannelDirectMessageManager.init(context)
    }

    val channelTitle = chats.find { it.id == channelId }?.title ?: "Канал"
    val threads = threadsMap[channelId].orEmpty()

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
                            testTag = "admin_dm_inbox_back"
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Color.White
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Входящие сообщения",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channelTitle,
                            color = TelegramAdminCaptionColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            if (threads.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Forum,
                                contentDescription = null,
                                tint = TelegramAdminPurple,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Пока нет входящих сообщений",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Когда подписчики напишут каналу или предложат платный пост, переписки появятся здесь.",
                                color = TelegramAdminCaptionColor,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                items(threads, key = { it.threadId }) { thread ->
                    val updatedTime = remember(thread.updatedAt) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(thread.updatedAt))
                    }
                    Surface(
                        onClick = {
                            navController.navigateSafe(
                                AppDestinations.channelDirectMessages(
                                    channelId = channelId,
                                    subscriberId = thread.subscriberId,
                                    adminMode = true
                                )
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_dm_thread_${thread.subscriberId}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChannelCircularAvatarView(
                                avatarPath = thread.subscriberAvatar,
                                fallbackTitle = thread.subscriberName,
                                sizeDp = 44
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = thread.subscriberName,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    Text(
                                        text = updatedTime,
                                        color = TelegramAdminCaptionColor,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = thread.lastMessagePreview.ifBlank { "Новое сообщение" },
                                        color = TelegramAdminCaptionColor,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    if (thread.pendingSuggestionsCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(TelegramAdminPurpleStrong)
                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${thread.pendingSuggestionsCount}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TelegramAdminCaptionColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
