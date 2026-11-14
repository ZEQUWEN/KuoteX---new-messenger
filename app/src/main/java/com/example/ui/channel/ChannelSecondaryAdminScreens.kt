@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAddDisabled
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.navigation.NavController
import com.example.ui.AppViewModel
import com.example.ui.popBackStackSafe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1. Channel Blacklist Screen («Чёрный список»)
 */
@Composable
fun ChannelBlacklistScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    var showBanMemberDialog by remember { mutableStateOf(false) }

    if (showBanMemberDialog) {
        val candidates = members.filter { it.role != "creator" && !it.isAdmin }
        var reasonText by remember { mutableStateOf("Нарушение правил канала") }
        AlertDialog(
            onDismissRequest = { showBanMemberDialog = false },
            containerColor = Color(0xFF171C26),
            title = { Text("Заблокировать пользователя", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Причина блокировки") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = TelegramAdminPurple
                        )
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (candidates.isEmpty()) {
                            item {
                                Text("Нет подписчиков для блокировки.", color = TelegramAdminCaptionColor, fontSize = 14.sp)
                            }
                        }
                        items(candidates, key = { it.userId }) { m ->
                            Surface(
                                onClick = {
                                    ChannelCustomizationManager.banUser(
                                        chatId = chatId,
                                        userId = m.userId,
                                        userName = m.userName,
                                        reason = reasonText.ifBlank { "Нарушение правил канала" }
                                    )
                                    viewModel.removeGroupMember(chatId, m.userId)
                                    Toast.makeText(context, "${m.userName} добавлен в чёрный список", Toast.LENGTH_SHORT).show()
                                    showBanMemberDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1F2633)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberCircleAvatar(name = m.userName, seed = m.userId, sizeDp = 36)
                                    Spacer(Modifier.width(12.dp))
                                    Text(m.userName, color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBanMemberDialog = false }) {
                    Text("Закрыть", color = TelegramAdminPurple)
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
                            contentDescription = "Назад"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Text(
                        text = "Чёрный список",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                Surface(
                    onClick = { showBanMemberDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blacklist_add_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PersonAddDisabled, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Заблокировать пользователя", color = Color(0xFFFF5252), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Заблокированные пользователи не смогут подписываться на канал и комментировать публикации.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (customization.blacklistedUsers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = TelegramAdminCaptionColor, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Чёрный список пуст", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("В канале нет заблокированных пользователей", color = TelegramAdminCaptionColor, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            customization.blacklistedUsers.forEachIndexed { idx, banned ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MemberCircleAvatar(name = banned.userName, seed = banned.userId, sizeDp = 42)
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(banned.userName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Text("${banned.reason} • ${banned.bannedBy}", color = TelegramAdminCaptionColor, fontSize = 12.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            ChannelCustomizationManager.unbanUser(chatId, banned.userId)
                                            Toast.makeText(context, "${banned.userName} разблокирован", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Filled.LockOpen, contentDescription = null, tint = TelegramAdminPurple, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Снять", color = TelegramAdminPurple, fontSize = 13.sp)
                                    }
                                }
                                if (idx < customization.blacklistedUsers.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = TelegramAdminDividerColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2. Channel Statistics Screen («Статистика»)
 */
@Composable
fun ChannelStatsScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)
    val subTotal = maxOf(customization.subscriberCount, members.size)
    val avgReach = (subTotal * 2.4).toInt().coerceAtLeast(12)

    Scaffold(
        containerColor = TelegramAdminBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramAdminBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        TelegramCircularIconButton(
                            onClick = { navController.popBackStackSafe() },
                            contentDescription = "Назад"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Text(
                        text = "Статистика канала",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Основные показатели (за 7 дней)", color = TelegramAdminPurple, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatMetricBox("Подписчики", "$subTotal", "+12%", Color(0xFF4CAF50), Modifier.weight(1f))
                            StatMetricBox("Охват постов", "$avgReach", "+24%", Color(0xFF2AABEE), Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatMetricBox("ERR (вовлечённость)", "42.5%", "+4.2%", TelegramAdminPurple, Modifier.weight(1f))
                            StatMetricBox("Уровень бустов", "Ур. ${customization.boostLevel}", "${customization.boostCount} бустов", Color(0xFFFFD54F), Modifier.weight(1f))
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(8.dp))
                            Text("Включённые уведомления", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        LinearProgressIndicator(
                            progress = { 0.68f },
                            color = TelegramAdminPurpleStrong,
                            trackColor = Color(0xFF282E3A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text("68% подписчиков получают уведомления с включённым звуком", color = TelegramAdminCaptionColor, fontSize = 13.sp)
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BarChart, contentDescription = null, tint = TelegramAdminPurple)
                            Spacer(Modifier.width(8.dp))
                            Text("Источники просмотров", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        SourceShareRow("Подписчики канала", 0.64f, "64%")
                        SourceShareRow("Пересылки в чаты", 0.21f, "21%")
                        SourceShareRow("Глобальный поиск", 0.15f, "15%")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricBox(title: String, value: String, delta: String, accentColor: Color, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E2430),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(delta, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(title, color = TelegramAdminCaptionColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SourceShareRow(label: String, fraction: Float, percentText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
            Text(percentText, color = TelegramAdminPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = TelegramAdminPurple,
            trackColor = Color(0xFF282E3A),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

/**
 * 3. Channel Recent Actions Screen («Недавние действия»)
 */
@Composable
fun ChannelRecentActionsScreen(
    chatId: String,
    navController: NavController
) {
    val recentActionsMap by ChannelCustomizationManager.getRecentActionsFlow(chatId).collectAsState()
    val actions = recentActionsMap[chatId] ?: emptyList()

    Scaffold(
        containerColor = TelegramAdminBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramAdminBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        TelegramCircularIconButton(
                            onClick = { navController.popBackStackSafe() },
                            contentDescription = "Назад"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Text(
                        text = "Недавние действия",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
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
            if (actions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = TelegramAdminCaptionColor, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("За последние 48 часов действий не было", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        }
                    }
                }
            } else {
                items(actions, key = { it.id }) { action ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(action.actionTitle, color = TelegramAdminPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    SimpleDateFormat("HH:mm, dd.MM", Locale("ru")).format(Date(action.timestamp)),
                                    color = TelegramAdminCaptionColor,
                                    fontSize = 11.sp
                                )
                            }
                            Text(action.details, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                            Text("Выполнил(а): ${action.adminName}", color = TelegramAdminCaptionColor, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. Channel Auto-Greeting Screen («Приветствие»)
 */
@Composable
fun ChannelGreetingScreen(
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    var greetingEnabled by remember(chatId) { mutableStateOf(customization.autoGreetingEnabled) }
    var greetingText by remember(chatId) { mutableStateOf(customization.autoGreetingText) }

    Scaffold(
        containerColor = TelegramAdminBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramAdminBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        TelegramCircularIconButton(
                            onClick = { navController.popBackStackSafe() },
                            contentDescription = "Назад"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Text(
                        text = "Приветствие",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        TelegramAdminSavePill(
                            testTag = "greeting_save_button",
                            onClick = {
                                ChannelCustomizationManager.updateAutoGreeting(chatId, greetingEnabled, greetingText)
                                Toast.makeText(context, "Настройки приветствия сохранены", Toast.LENGTH_SHORT).show()
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (greetingEnabled) TelegramAdminPurpleStrong else TelegramAdminCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { greetingEnabled = !greetingEnabled }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Включить приветствие", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Switch(
                            checked = greetingEnabled,
                            onCheckedChange = { greetingEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFB794F4)
                            )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Приветствие будет автоматически отправляться новым подписчикам канала.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Текст приветствия", color = TelegramAdminPurple, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = greetingText,
                            onValueChange = { greetingText = it },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = TelegramAdminPurple,
                                unfocusedBorderColor = TelegramAdminDividerColor
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5. Channel Direct Messages Screen («Сообщения каналу»)
 */
@Composable
fun ChannelDirectMessagesScreen(
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    var dmEnabled by remember(chatId) { mutableStateOf(customization.directMessagesEnabled) }
    var starPrice by remember(chatId) { mutableFloatStateOf(customization.directMessageStarPrice.toFloat().coerceIn(0f, 100f)) }

    Scaffold(
        containerColor = TelegramAdminBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramAdminBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        TelegramCircularIconButton(
                            onClick = { navController.popBackStackSafe() },
                            contentDescription = "Назад"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Text(
                        text = "Сообщения каналу",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        TelegramAdminSavePill(
                            testTag = "direct_messages_save_button",
                            onClick = {
                                ChannelCustomizationManager.updateDirectMessages(chatId, dmEnabled, starPrice.toInt())
                                Toast.makeText(context, "Настройки сообщений сохранены", Toast.LENGTH_SHORT).show()
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (dmEnabled) TelegramAdminPurpleStrong else TelegramAdminCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dmEnabled = !dmEnabled }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Включить личные сообщения", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Switch(
                            checked = dmEnabled,
                            onCheckedChange = { dmEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFB794F4)
                            )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Позволяет подписчикам отправлять сообщения администрации канала, в том числе с оплатой Telegram Stars (⭐).",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (dmEnabled) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (starPrice.toInt() == 0) "Бесплатно" else "Стоимость сообщения: ⭐ ${starPrice.toInt()}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = starPrice,
                                onValueChange = { starPrice = it },
                                valueRange = 0f..100f,
                                steps = 99,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFD54F),
                                    activeTrackColor = Color(0xFFFFD54F),
                                    inactiveTrackColor = Color(0xFF282E3A)
                                )
                            )
                            Text("85% звёзд начисляется владельцу канала", color = TelegramAdminCaptionColor, fontSize = 12.sp)
                        }
                    }
                }

                item {
                    val directUrl = "${customization.inviteLink}?direct"
                    Surface(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Direct Link", "https://$directUrl"))
                            Toast.makeText(context, "Прямая ссылка скопирована", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, tint = TelegramAdminPurple)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Прямая ссылка", color = TelegramAdminCaptionColor, fontSize = 12.sp)
                                Text(directUrl, color = TelegramAdminPurple, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", tint = TelegramAdminCaptionColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
