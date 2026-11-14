@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example.ui.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.AppViewModel
import com.example.ui.Chat
import com.example.ui.GroupMember
import com.example.ui.UserAccount
import com.example.ui.components.TelegramEmojiPickerBottomSheet
import com.example.ui.popBackStackSafe
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val TelegramAdminBg = Color(0xFF0A0D12)
internal val TelegramAdminCardBg = Color(0xFF161B24)
internal val TelegramAdminPillBg = Color(0xFF1F242F)
internal val TelegramAdminPurple = Color(0xFFB072FF)
internal val TelegramAdminPurpleStrong = Color(0xFF8E5AE2)
internal val TelegramAdminCaptionColor = Color(0xFF858C99)
internal val TelegramAdminDividerColor = Color.White.copy(alpha = 0.06f)

@Composable
internal fun TelegramCircularIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String = "",
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = TelegramAdminPillBg,
        modifier = Modifier
            .size(42.dp)
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
internal fun TelegramAdminSavePill(
    text: String = "Сохранить",
    enabled: Boolean = true,
    testTag: String = "admin_save_button",
    onClick: () -> Unit
) {
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(21.dp),
        color = if (enabled) TelegramAdminPillBg else TelegramAdminPillBg.copy(alpha = 0.45f),
        modifier = Modifier
            .height(40.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * 1. Channel Type & Content Saving Screen ("Настройки" — matches Screenshots 161312 / 161315)
 */
@Composable
fun ChannelTypeAdminScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    var isPublicType by remember(chatId) { mutableStateOf(customization.isPublic) }
    val initialHandle = remember(chatId) {
        customization.inviteLink
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("t.me/")
            .removePrefix("+")
            .ifBlank { "channel_${chatId.take(6)}" }
    }
    var publicHandle by remember(chatId) { mutableStateOf(initialHandle) }
    var restrictSaving by remember(chatId) { mutableStateOf(customization.restrictSavingContent) }
    var showInviteLinksManager by remember { mutableStateOf(false) }

    val handleSanitized = publicHandle.trim().lowercase().filter { it.isLetterOrDigit() || it == '_' }
    val isHandleValid = !isPublicType || (
        handleSanitized.length >= 5 &&
            handleSanitized.all { it in 'a'..'z' || it in '0'..'9' || it == '_' } &&
            !handleSanitized.first().isDigit()
        )

    if (showInviteLinksManager) {
        InviteLinksManagementDialog(
            chatId = chatId,
            primaryLink = if (isPublicType) "t.me/$handleSanitized" else customization.inviteLink,
            additionalLinks = customization.additionalInviteLinks,
            onCreateLink = { label ->
                ChannelCustomizationManager.createInviteLink(chatId, label)
                Toast.makeText(context, "Ссылка создана", Toast.LENGTH_SHORT).show()
            },
            onRevokeLink = { linkId ->
                ChannelCustomizationManager.revokeInviteLink(chatId, linkId)
                Toast.makeText(context, "Ссылка отозвана", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showInviteLinksManager = false }
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
                            testTag = "channel_type_back"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Text(
                        text = "Настройки",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        TelegramAdminSavePill(
                            enabled = isHandleValid,
                            testTag = "channel_type_save",
                            onClick = {
                                val finalLink = if (isPublicType) {
                                    "t.me/$handleSanitized"
                                } else {
                                    if (customization.inviteLink.contains("+")) customization.inviteLink else "t.me/+${chatId.take(8)}priv"
                                }
                                ChannelCustomizationManager.updateChannelType(
                                    chatId = chatId,
                                    isPublic = isPublicType,
                                    inviteLink = finalLink,
                                    restrictSaving = restrictSaving
                                )
                                Toast.makeText(context, "Настройки канала сохранены", Toast.LENGTH_SHORT).show()
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
            // Card 1: Тип канала
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
                        Text(
                            text = "Тип канала",
                            color = TelegramAdminPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(10.dp))

                        // Частный канал
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { isPublicType = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = !isPublicType,
                                onClick = { isPublicType = false },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = TelegramAdminPurple,
                                    unselectedColor = TelegramAdminCaptionColor
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Частный канал",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "На частные каналы можно подписаться только по ссылке-приглашению.",
                                    color = TelegramAdminCaptionColor,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Публичный канал
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { isPublicType = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = isPublicType,
                                onClick = { isPublicType = true },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = TelegramAdminPurple,
                                    unselectedColor = TelegramAdminCaptionColor
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Публичный канал",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Публичные каналы можно найти через поиск, подписаться на них может любой пользователь.",
                                    color = TelegramAdminCaptionColor,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Публичная ссылка or Пригласительная ссылка
            item {
                if (isPublicType) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Публичная ссылка",
                                color = TelegramAdminPurple,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "t.me/",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                BasicTextField(
                                    value = publicHandle,
                                    onValueChange = { raw ->
                                        publicHandle = raw.filter { it.isLetterOrDigit() || it == '_' }
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(TelegramAdminPurple),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("public_link_input")
                                )
                            }
                            if (!isHandleValid) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Ссылка должна содержать минимум 5 символов (a-z, 0-9, _) и не начинаться с цифры.",
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = "Если у канала будет постоянная публичная ссылка, другие пользователи смогут найти его и подписаться.\n\nМожно использовать латиницу (a–z), цифры (0–9) и подчёркивание. Минимальная длина — 5 символов.",
                        color = TelegramAdminCaptionColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                } else {
                    val privUrl = if (customization.inviteLink.contains("+")) customization.inviteLink else "t.me/+${chatId.take(8)}priv"
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Invite", "https://$privUrl"))
                                    Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Пригласительная ссылка",
                                    color = TelegramAdminPurple,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = privUrl,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", tint = TelegramAdminPurple)
                        }
                    }
                }
            }

            // Card 3: Управление приглашениями
            item {
                Surface(
                    onClick = { showInviteLinksManager = true },
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manage_invites_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = TelegramAdminCaptionColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Управление приглашениями",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (customization.additionalInviteLinks.isNotEmpty()) {
                            Text(
                                text = "${customization.additionalInviteLinks.size + 1}",
                                color = TelegramAdminPurple,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    text = "Вы можете создать дополнительные ссылки и задать для них срок действия, ограничение на число пользователей или плату за подписку.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            // Card 4: Сохранение контента -> Запретить копирование
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Сохранение контента",
                            color = TelegramAdminPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { restrictSaving = !restrictSaving },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Запретить копирование",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Switch(
                                checked = restrictSaving,
                                onCheckedChange = { restrictSaving = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TelegramAdminPurpleStrong,
                                    uncheckedThumbColor = Color(0xFF9EA5B1),
                                    uncheckedTrackColor = Color(0xFF282E3A)
                                ),
                                modifier = Modifier.testTag("restrict_saving_switch")
                            )
                        }
                    }
                }
                Text(
                    text = "Подписчики не смогут копировать, сохранять или пересылать материалы из канала.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun InviteLinksManagementDialog(
    chatId: String,
    primaryLink: String,
    additionalLinks: List<ChannelInviteLink>,
    onCreateLink: (String) -> Unit,
    onRevokeLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171C26),
        title = { Text("Пригласительные ссылки", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F2633),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Основная ссылка", color = TelegramAdminPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(primaryLink, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Link", "https://$primaryLink"))
                                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", tint = TelegramAdminPurple, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                items(additionalLinks, key = { it.id }) { link ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F2633),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(link.label, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(link.url, color = TelegramAdminPurple, fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Link", "https://${link.url}"))
                                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onRevokeLink(link.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Отозвать", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(color = TelegramAdminDividerColor)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("Название новой ссылки") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = TelegramAdminPurple
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onCreateLink(newLabel.ifBlank { "Инвайт-${additionalLinks.size + 1}" })
                            newLabel = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramAdminPurpleStrong),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Создать новую ссылку")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово", color = TelegramAdminPurple)
            }
        }
    )
}

/**
 * 2. Channel Discussion Group Screen ("Обсуждение" — matches Screenshot 161303)
 */
@Composable
fun ChannelDiscussionAdminScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)
    val availableGroups = remember(chats, chatId) {
        chats.filter { it.isGroup && it.id != chatId }
    }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }

    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            containerColor = Color(0xFF171C26),
            title = { Text("Отвязать группу?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Комментарии к новым публикациям больше не будут появляться в группе «${customization.discussionChatTitle}».",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ChannelCustomizationManager.unlinkDiscussionChat(chatId)
                        Toast.makeText(context, "Группа отвязана", Toast.LENGTH_SHORT).show()
                        showUnlinkConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Отвязать", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirm = false }) {
                    Text("Отмена", color = TelegramAdminPurple)
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        val currentChannelTitle = chats.find { it.id == chatId }?.title ?: "Канал"
        var groupName by remember { mutableStateOf("$currentChannelTitle CHAT") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            containerColor = Color(0xFF171C26),
            title = { Text("Создать группу для обсуждения", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Название группы") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TelegramAdminPurple
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = groupName.trim().ifBlank { "$currentChannelTitle CHAT" }
                        viewModel.createChat(
                            name = trimmed,
                            desc = "Чат обсуждений канала «$currentChannelTitle»",
                            photo = "",
                            isPrivate = true,
                            linkOrUsername = "",
                            isGroup = true,
                            isChannel = false
                        )
                        ChannelCustomizationManager.linkDiscussionChat(chatId, null, trimmed)
                        Toast.makeText(context, "Группа «$trimmed» создана и привязана", Toast.LENGTH_SHORT).show()
                        showCreateGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramAdminPurpleStrong)
                ) {
                    Text("Создать и привязать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
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
                            testTag = "discussion_back"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Обсуждение",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                },
                actions = {
                    Spacer(Modifier.width(48.dp))
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Duck & Chat Illustration
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                ) {
                    DiscussionHeroIllustration()
                    Spacer(Modifier.height(14.dp))
                    if (customization.discussionChatTitle.isNotBlank()) {
                        Text(
                            text = buildAnnotatedString {
                                append("В группе «")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                    append(customization.discussionChatTitle)
                                }
                                append("» будут появляться комментарии к публикациям канала.")
                            },
                            color = TelegramAdminCaptionColor,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                    } else {
                        Text(
                            text = "Подключите группу для обсуждения, чтобы подписчики могли оставлять комментарии под публикациями.",
                            color = TelegramAdminCaptionColor,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                    }
                }
            }

            // Currently Linked Group Card (Screenshot 161303)
            if (customization.discussionChatTitle.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val linkedId = customization.discussionChatId
                                        if (linkedId != null) {
                                            navController.navigate("chat/$linkedId")
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(listOf(Color(0xFF42A5F5), Color(0xFF1E88E5)))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = customization.discussionChatTitle.take(2).ifBlank { "Ч" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = customization.discussionChatTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Частная группа",
                                        color = TelegramAdminCaptionColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 74.dp),
                                color = TelegramAdminDividerColor
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showUnlinkConfirm = true }
                                    .padding(horizontal = 16.dp, vertical = 15.dp)
                                    .testTag("unlink_discussion_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RemoveCircleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5A5F),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "Отвязать группу",
                                    color = Color(0xFFFF5A5F),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Pick another group or create new group
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCreateGroupDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("create_discussion_group_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(TelegramAdminPurple.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = TelegramAdminPurple, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Создать новую группу",
                                color = TelegramAdminPurple,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (availableGroups.isNotEmpty()) {
                            HorizontalDivider(color = TelegramAdminDividerColor)
                            availableGroups.forEachIndexed { idx, group ->
                                val isLinked = customization.discussionChatTitle == group.title
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            ChannelCustomizationManager.linkDiscussionChat(chatId, group.id, group.title)
                                            Toast.makeText(context, "Привязана группа «${group.title}»", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2B5278)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = group.title.take(2).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(group.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text("Группа", color = TelegramAdminCaptionColor, fontSize = 12.sp)
                                    }
                                    if (isLinked) {
                                        Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = TelegramAdminPurple)
                                    }
                                }
                                if (idx < availableGroups.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = TelegramAdminDividerColor)
                                }
                            }
                        }
                    }
                }
            }

            // Footer Caption
            item {
                Text(
                    text = "Всё, что Вы публикуете в этом канале, будет автоматически пересылаться в группу.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DiscussionHeroIllustration() {
    Box(
        modifier = Modifier
            .size(116.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Chat bubble background card
            drawRoundRect(
                color = Color(0xFFDCE2EC),
                topLeft = Offset(w * 0.14f, h * 0.06f),
                size = Size(w * 0.72f, h * 0.56f),
                cornerRadius = CornerRadius(24f, 24f)
            )
            // Message lines inside bubble
            drawRoundRect(
                color = Color(0xFF333D4B),
                topLeft = Offset(w * 0.24f, h * 0.16f),
                size = Size(w * 0.44f, h * 0.05f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFF6B778C),
                topLeft = Offset(w * 0.24f, h * 0.27f),
                size = Size(w * 0.34f, h * 0.045f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFF42A5F5),
                topLeft = Offset(w * 0.24f, h * 0.38f),
                size = Size(w * 0.26f, h * 0.045f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Two cheerful duck characters + popcorn bucket
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = w * 0.17f,
                center = Offset(w * 0.32f, h * 0.72f)
            )
            drawCircle(
                color = Color(0xFFFFCA28),
                radius = w * 0.17f,
                center = Offset(w * 0.68f, h * 0.72f)
            )
            // Red/white striped bucket accent
            drawRoundRect(
                color = Color(0xFFE53935),
                topLeft = Offset(w * 0.43f, h * 0.70f),
                size = Size(w * 0.14f, h * 0.22f),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }
    }
}

/**
 * 3. Channel Admins Screen ("Администраторы" — matches Screenshot 161241)
 */
@Composable
fun ChannelAdminsScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    var signMessages by remember(chatId, customization.isSignaturesEnabled) {
        mutableStateOf(customization.isSignaturesEnabled)
    }
    var showProfiles by remember(chatId, customization.showAuthorProfiles) {
        mutableStateOf(customization.showAuthorProfiles)
    }
    var showAddAdminPicker by remember { mutableStateOf(false) }
    var editingAdminMember by remember { mutableStateOf<GroupMember?>(null) }

    val ownerMember = members.find { it.role == "creator" }
        ?: members.find { it.isAdmin }
    val otherAdmins = members.filter {
        (it.isAdmin || it.role == "admin") && it.userId != ownerMember?.userId
    }

    if (showAddAdminPicker) {
        val candidateMembers = members.filter { !it.isAdmin && it.role != "creator" }
        val existingIds = members.map { it.userId }.toSet()
        val extraContacts = contacts.filter { !existingIds.contains(it.id) }

        AlertDialog(
            onDismissRequest = { showAddAdminPicker = false },
            containerColor = Color(0xFF171C26),
            title = { Text("Назначить администратором", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (candidateMembers.isEmpty() && extraContacts.isEmpty()) {
                        item {
                            Text(
                                "Все участники и контакты уже являются администраторами.",
                                color = TelegramAdminCaptionColor,
                                fontSize = 14.sp
                            )
                        }
                    }
                    items(candidateMembers, key = { "sub_${it.userId}" }) { m ->
                        Surface(
                            onClick = {
                                showAddAdminPicker = false
                                editingAdminMember = m
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
                                MemberCircleAvatar(name = m.userName, seed = m.userId, sizeDp = 38)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.userName, color = Color.White, fontWeight = FontWeight.Medium)
                                    Text("Подписчик", color = TelegramAdminCaptionColor, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    items(extraContacts, key = { "cnt_${it.id}" }) { c ->
                        Surface(
                            onClick = {
                                showAddAdminPicker = false
                                editingAdminMember = GroupMember(
                                    chatId = chatId,
                                    userId = c.id,
                                    userName = c.name,
                                    role = "subscriber",
                                    isAdmin = false
                                )
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
                                MemberCircleAvatar(name = c.name, seed = c.id, sizeDp = 38)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.name, color = Color.White, fontWeight = FontWeight.Medium)
                                    Text("Контакт", color = TelegramAdminPurple, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddAdminPicker = false }) {
                    Text("Отмена", color = TelegramAdminPurple)
                }
            }
        )
    }

    editingAdminMember?.let { target ->
        AdminRightsEditorDialog(
            member = target,
            onDismiss = { editingAdminMember = null },
            onSave = { customTitle, canPost, canEdit, canDelete, canStreams, canAddAdmins ->
                val promoterName = activeAccount?.displayName?.ifBlank { "Владелец" } ?: "Владелец"
                viewModel.promoteOrUpdateChannelAdmin(
                    chatId = chatId,
                    userId = target.userId,
                    userName = target.userName,
                    customTitle = customTitle,
                    canPost = canPost,
                    canEdit = canEdit,
                    canDelete = canDelete,
                    canManageStreams = canStreams,
                    canAddAdmins = canAddAdmins,
                    promotedByName = promoterName
                )
                ChannelCustomizationManager.recordAdminLog(
                    chatId = chatId,
                    title = "Обновлены права администратора",
                    details = "${target.userName} назначен администратором"
                )
                Toast.makeText(context, "Права ${target.userName} сохранены", Toast.LENGTH_SHORT).show()
                editingAdminMember = null
            },
            onDemote = if (target.isAdmin && target.role != "creator") {
                {
                    viewModel.demoteChannelAdmin(chatId, target.userId)
                    ChannelCustomizationManager.recordAdminLog(
                        chatId = chatId,
                        title = "Снятие прав администратора",
                        details = "${target.userName} больше не администратор"
                    )
                    Toast.makeText(context, "${target.userName} разжалован", Toast.LENGTH_SHORT).show()
                    editingAdminMember = null
                }
            } else null
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
                            testTag = "admins_back"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Text(
                        text = "Администраторы",
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
                            testTag = "admins_save_button",
                            onClick = {
                                ChannelCustomizationManager.updateSignatures(chatId, signMessages)
                                ChannelCustomizationManager.updateAuthorProfiles(chatId, showProfiles)
                                Toast.makeText(context, "Настройки администраторов сохранены", Toast.LENGTH_SHORT).show()
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
            // Card 1: + Добавить, Владелец, Администраторы
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // + Добавить
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddAdminPicker = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("add_admin_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddModerator,
                                contentDescription = "Добавить администратора",
                                tint = TelegramAdminPurple,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Добавить",
                                color = TelegramAdminPurple,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Owner Row
                        if (ownerMember != null) {
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = TelegramAdminDividerColor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberCircleAvatar(name = ownerMember.userName, seed = ownerMember.userId, sizeDp = 42)
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ownerMember.userName,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Владелец",
                                        color = TelegramAdminCaptionColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // Admin Rows
                        otherAdmins.forEach { admin ->
                            var menuExpanded by remember { mutableStateOf(false) }
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = TelegramAdminDividerColor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { editingAdminMember = admin },
                                        onLongClick = { menuExpanded = true }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MemberCircleAvatar(name = admin.userName, seed = admin.userId, sizeDp = 42)
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = admin.userName,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Назначил(а): ${admin.promotedBy ?: "Владелец"}",
                                        color = TelegramAdminCaptionColor,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Действия",
                                            tint = TelegramAdminCaptionColor
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                        containerColor = Color(0xFF1F2633)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Изменить права", color = Color.White) },
                                            onClick = {
                                                menuExpanded = false
                                                editingAdminMember = admin
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Удалить из администраторов", color = Color(0xFFFF5252)) },
                                            onClick = {
                                                menuExpanded = false
                                                viewModel.demoteChannelAdmin(chatId, admin.userId)
                                                Toast.makeText(context, "${admin.userName} удалён из администраторов", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Вы можете назначать помощников для управления каналом. Чтобы удалить администратора, нажмите и удерживайте.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Card 2: Sign Messages & Show Author Profiles (Screenshot 161241)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { signMessages = !signMessages }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Подписывать сообщения", color = Color.White, fontSize = 16.sp)
                            Switch(
                                checked = signMessages,
                                onCheckedChange = { signMessages = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TelegramAdminPurpleStrong,
                                    uncheckedThumbColor = Color(0xFF9EA5B1),
                                    uncheckedTrackColor = Color(0xFF282E3A)
                                )
                            )
                        }

                        HorizontalDivider(color = TelegramAdminDividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showProfiles = !showProfiles }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Показывать профили авторов", color = Color.White, fontSize = 16.sp)
                            Switch(
                                checked = showProfiles,
                                onCheckedChange = { showProfiles = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TelegramAdminPurpleStrong,
                                    uncheckedThumbColor = Color(0xFF9EA5B1),
                                    uncheckedTrackColor = Color(0xFF282E3A)
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Разрешить администраторам публикацию от имени их канала или личного аккаунта со ссылкой на профиль.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AdminRightsEditorDialog(
    member: GroupMember,
    onDismiss: () -> Unit,
    onSave: (String, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onDemote: (() -> Unit)?
) {
    var customTitle by remember { mutableStateOf(member.customTitle) }
    var canPost by remember { mutableStateOf(member.canPostMessages || !member.isAdmin) }
    var canEdit by remember { mutableStateOf(member.canEditMessages) }
    var canDelete by remember { mutableStateOf(member.canDeleteMessages) }
    var canStreams by remember { mutableStateOf(member.canManageStreams) }
    var canAddAdmins by remember { mutableStateOf(member.canAddAdmins) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171C26),
        title = { Text("Права: ${member.userName}", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Должность (напр. Редактор)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TelegramAdminPurple
                    )
                )
                AdminRightSwitchRow("Публикация сообщений", canPost) { canPost = it }
                AdminRightSwitchRow("Изменение публикаций", canEdit) { canEdit = it }
                AdminRightSwitchRow("Удаление публикаций", canDelete) { canDelete = it }
                AdminRightSwitchRow("Управление трансляциями", canStreams) { canStreams = it }
                AdminRightSwitchRow("Добавление администраторов", canAddAdmins) { canAddAdmins = it }

                if (onDemote != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onDemote,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Снять с должности администратора", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(customTitle.trim(), canPost, canEdit, canDelete, canStreams, canAddAdmins) },
                colors = ButtonDefaults.buttonColors(containerColor = TelegramAdminPurpleStrong)
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = TelegramAdminPurple)
            }
        }
    )
}

@Composable
private fun AdminRightSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TelegramAdminPurpleStrong
            )
        )
    }
}

/**
 * 4. Channel Subscribers Screen ("Подписчики" — matches Screenshot 161248)
 */
@Composable
fun ChannelSubscribersScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddSubscribersDialog by remember { mutableStateOf(false) }

    val filtered = remember(members, searchQuery) {
        if (searchQuery.isBlank()) members else members.filter {
            it.userName.contains(searchQuery, ignoreCase = true) ||
                it.userId.contains(searchQuery, ignoreCase = true)
        }
    }

    val contactOrAdminMembers = filtered.filter {
        it.role == "creator" || it.role == "admin" || contacts.any { c -> c.id == it.userId }
    }
    val botMembers = filtered.filter { it.role == "bot" || it.userId.endsWith("_bot", ignoreCase = true) }
    val otherSubscribers = filtered.filter { m ->
        !contactOrAdminMembers.any { it.userId == m.userId } && !botMembers.any { it.userId == m.userId }
    }

    if (showAddSubscribersDialog) {
        val existingIds = members.map { it.userId }.toSet()
        val addableContacts = contacts.filter { !existingIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { showAddSubscribersDialog = false },
            containerColor = Color(0xFF171C26),
            title = { Text("Добавить подписчиков", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (addableContacts.isEmpty()) {
                        item {
                            Text("Все ваши контакты уже подписаны на канал.", color = TelegramAdminCaptionColor, fontSize = 14.sp)
                        }
                    }
                    items(addableContacts, key = { it.id }) { contact ->
                        Surface(
                            onClick = {
                                viewModel.addGroupMember(
                                    chatId = chatId,
                                    userId = contact.id,
                                    userName = contact.name,
                                    isAdmin = false,
                                    role = "subscriber"
                                )
                                Toast.makeText(context, "${contact.name} добавлен в канал", Toast.LENGTH_SHORT).show()
                                showAddSubscribersDialog = false
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
                                MemberCircleAvatar(name = contact.name, seed = contact.id, sizeDp = 38)
                                Spacer(Modifier.width(12.dp))
                                Text(contact.name, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSubscribersDialog = false }) {
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
                            onClick = {
                                if (isSearchActive) {
                                    isSearchActive = false
                                    searchQuery = ""
                                } else {
                                    navController.popBackStackSafe()
                                }
                            },
                            contentDescription = "Назад",
                            testTag = "subscribers_back"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    if (isSearchActive) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                            cursorBrush = SolidColor(TelegramAdminPurple),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Поиск подписчиков...", color = TelegramAdminCaptionColor, fontSize = 17.sp)
                                }
                                inner()
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Подписчики",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        }
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        TelegramCircularIconButton(
                            onClick = {
                                if (isSearchActive && searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    isSearchActive = !isSearchActive
                                }
                            },
                            contentDescription = "Поиск",
                            testTag = "subscribers_search_button"
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Поиск",
                                tint = Color.White
                            )
                        }
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
            // Card 1: Добавить подписчиков / Пригласить по ссылке
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TelegramAdminCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddSubscribersDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("add_subscribers_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = TelegramAdminPurple, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Добавить подписчиков", color = TelegramAdminPurple, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }

                        HorizontalDivider(modifier = Modifier.padding(start = 54.dp), color = TelegramAdminDividerColor)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val fullLink = "https://${customization.inviteLink}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Channel Link", fullLink))
                                    Toast.makeText(context, "Ссылка скопирована: $fullLink", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("invite_by_link_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, tint = TelegramAdminPurple, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("Пригласить по ссылке", color = TelegramAdminPurple, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Section: Контакты в этом канале
            if (contactOrAdminMembers.isNotEmpty()) {
                item {
                    SectionLabel("Контакты в этом канале")
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            contactOrAdminMembers.forEachIndexed { idx, m ->
                                SubscriberMemberRow(
                                    member = m,
                                    subtitle = if (m.role == "creator" || m.role == "admin") "в сети" else "был(а) недавно",
                                    subtitleColor = if (m.role == "creator" || m.role == "admin") TelegramAdminPurple else TelegramAdminCaptionColor,
                                    showMenu = m.role != "creator",
                                    onPromote = {
                                        viewModel.promoteOrUpdateChannelAdmin(
                                            chatId = chatId,
                                            userId = m.userId,
                                            userName = m.userName,
                                            customTitle = "",
                                            canPost = true,
                                            canEdit = true,
                                            canDelete = true,
                                            canManageStreams = true,
                                            canAddAdmins = false,
                                            promotedByName = "Вы"
                                        )
                                        Toast.makeText(context, "${m.userName} назначен администратором", Toast.LENGTH_SHORT).show()
                                    },
                                    onBan = {
                                        ChannelCustomizationManager.banUser(chatId, m.userId, m.userName)
                                        viewModel.removeGroupMember(chatId, m.userId)
                                        Toast.makeText(context, "${m.userName} в чёрном списке", Toast.LENGTH_SHORT).show()
                                    },
                                    onRemove = {
                                        viewModel.removeGroupMember(chatId, m.userId)
                                        Toast.makeText(context, "${m.userName} удалён", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                if (idx < contactOrAdminMembers.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = TelegramAdminDividerColor)
                                }
                            }
                        }
                    }
                }
            }

            // Section: Боты
            if (botMembers.isNotEmpty()) {
                item {
                    SectionLabel("Боты")
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            botMembers.forEachIndexed { idx, m ->
                                SubscriberMemberRow(
                                    member = m,
                                    subtitle = if (m.canReadMessages) "имеет доступ к сообщениям" else "не имеет доступа к сообщениям",
                                    subtitleColor = TelegramAdminCaptionColor,
                                    showMenu = true,
                                    isBot = true,
                                    onPromote = {
                                        viewModel.updateBotPermissions(chatId, m.userId, !m.canReadMessages, m.canSendMessages)
                                    },
                                    onBan = {
                                        ChannelCustomizationManager.banUser(chatId, m.userId, m.userName, "Бот заблокирован")
                                        viewModel.removeGroupMember(chatId, m.userId)
                                    },
                                    onRemove = {
                                        viewModel.removeGroupMember(chatId, m.userId)
                                    }
                                )
                                if (idx < botMembers.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = TelegramAdminDividerColor)
                                }
                            }
                        }
                    }
                }
            }

            // Section: Другие подписчики
            if (otherSubscribers.isNotEmpty()) {
                item {
                    SectionLabel("Другие подписчики")
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            otherSubscribers.forEachIndexed { idx, m ->
                                SubscriberMemberRow(
                                    member = m,
                                    subtitle = "участник с ${formatSubscriberJoinedAt(m.joinedAt)}",
                                    subtitleColor = TelegramAdminCaptionColor,
                                    showMenu = true,
                                    onPromote = {
                                        viewModel.promoteOrUpdateChannelAdmin(
                                            chatId = chatId,
                                            userId = m.userId,
                                            userName = m.userName,
                                            customTitle = "",
                                            canPost = true,
                                            canEdit = true,
                                            canDelete = true,
                                            canManageStreams = true,
                                            canAddAdmins = false,
                                            promotedByName = "Вы"
                                        )
                                        Toast.makeText(context, "${m.userName} назначен администратором", Toast.LENGTH_SHORT).show()
                                    },
                                    onBan = {
                                        ChannelCustomizationManager.banUser(chatId, m.userId, m.userName)
                                        viewModel.removeGroupMember(chatId, m.userId)
                                        Toast.makeText(context, "${m.userName} добавлен в чёрный список", Toast.LENGTH_SHORT).show()
                                    },
                                    onRemove = {
                                        viewModel.removeGroupMember(chatId, m.userId)
                                        Toast.makeText(context, "${m.userName} удалён из канала", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                if (idx < otherSubscribers.lastIndex) {
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TelegramAdminCaptionColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun SubscriberMemberRow(
    member: GroupMember,
    subtitle: String,
    subtitleColor: Color,
    showMenu: Boolean,
    isBot: Boolean = false,
    onPromote: () -> Unit,
    onBan: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBot) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF213248)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(24.dp))
            }
        } else {
            MemberCircleAvatar(name = member.userName, seed = member.userId, sizeDp = 44)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.userName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showMenu) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Действия", tint = TelegramAdminCaptionColor)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = Color(0xFF1F2633)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isBot) "Переключить доступ" else "Назначить администратором",
                                color = Color.White
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onPromote()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Добавить в чёрный список", color = Color(0xFFFF8A65)) },
                        onClick = {
                            menuExpanded = false
                            onBan()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить из канала", color = Color(0xFFFF5252)) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 5. Channel Reactions Admin Screen ("Реакции" — matches Screenshot 161256)
 */
@Composable
fun ChannelReactionsAdminScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    var reactionsEnabled by remember(chatId) { mutableStateOf(customization.reactionsEnabled) }
    val selectedReactions = remember(chatId) {
        mutableStateListOf(*customization.availableReactions.toTypedArray())
    }
    var maxPerPost by remember(chatId) {
        mutableFloatStateOf(customization.maxReactionsPerPost.toFloat().coerceIn(1f, 11f))
    }
    var paidStarsEnabled by remember(chatId) { mutableStateOf(customization.paidStarReactionsEnabled) }
    var showReactionEmojiPicker by remember { mutableStateOf(false) }

    if (showReactionEmojiPicker) {
        TelegramEmojiPickerBottomSheet(
            onDismissRequest = { showReactionEmojiPicker = false },
            onEmojiSelected = { emojiItem ->
                if (!selectedReactions.contains(emojiItem.emoji)) {
                    selectedReactions.add(emojiItem.emoji)
                }
            },
            title = "Добавить реакцию"
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
                            testTag = "reactions_back"
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    }
                },
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Реакции",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                },
                actions = {
                    Spacer(Modifier.width(48.dp))
                }
            )
        },
        bottomBar = {
            Surface(
                color = TelegramAdminBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        ChannelCustomizationManager.updateReactionsConfig(
                            chatId = chatId,
                            enabled = reactionsEnabled,
                            available = selectedReactions.toList(),
                            maxPerPost = maxPerPost.toInt(),
                            paidStars = paidStarsEnabled
                        )
                        Toast.makeText(context, "Настройки реакций сохранены!", Toast.LENGTH_SHORT).show()
                        navController.popBackStackSafe()
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramAdminPurpleStrong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(50.dp)
                        .testTag("save_reactions_button")
                ) {
                    Text(
                        text = "Сохранить изменения",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            // 1. Purple "Включить реакции" Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (reactionsEnabled) TelegramAdminPurpleStrong else TelegramAdminCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { reactionsEnabled = !reactionsEnabled }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Включить реакции",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Switch(
                            checked = reactionsEnabled,
                            onCheckedChange = { reactionsEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFB794F4),
                                uncheckedThumbColor = Color(0xFF9EA5B1),
                                uncheckedTrackColor = Color(0xFF282E3A)
                            )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Вы можете добавить в список реакций эмодзи из любого набора.",
                    color = TelegramAdminCaptionColor,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (reactionsEnabled) {
                // 2. Available Reactions Grid
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Доступные реакции",
                                    color = TelegramAdminPurple,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${selectedReactions.size}",
                                    color = TelegramAdminCaptionColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                selectedReactions.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF212836))
                                            .clickable {
                                                if (selectedReactions.size > 1) {
                                                    selectedReactions.remove(emoji)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 20.sp)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showReactionEmojiPicker = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, TelegramAdminPurple.copy(alpha = 0.5f)),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = TelegramAdminPurple, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Добавить", color = TelegramAdminPurple, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Вы также можете создавать собственные наборы и использовать эмодзи из них.",
                        color = TelegramAdminCaptionColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // 3. Max Reactions Per Post Slider
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Количество реакций на публикацию",
                                color = TelegramAdminPurple,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("1", color = TelegramAdminCaptionColor, fontSize = 13.sp)
                                val count = maxPerPost.toInt()
                                val label = when {
                                    count == 1 -> "1 реакция"
                                    count in 2..4 -> "$count реакции"
                                    else -> "$count реакций"
                                }
                                Text(label, color = TelegramAdminPurple, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("11", color = TelegramAdminCaptionColor, fontSize = 13.sp)
                            }
                            Slider(
                                value = maxPerPost,
                                onValueChange = { maxPerPost = it },
                                valueRange = 1f..11f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = TelegramAdminPurple,
                                    activeTrackColor = TelegramAdminPurpleStrong,
                                    inactiveTrackColor = Color(0xFF282E3A)
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Задайте максимальное количество разных реакций на публикацию, в том числе для ранее опубликованных сообщений.",
                        color = TelegramAdminCaptionColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // 4. Paid Reactions Switch
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TelegramAdminCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { paidStarsEnabled = !paidStarsEnabled }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Включить платные реакции",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = paidStarsEnabled,
                                onCheckedChange = { paidStarsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = TelegramAdminPurpleStrong,
                                    uncheckedThumbColor = Color(0xFF9EA5B1),
                                    uncheckedTrackColor = Color(0xFF282E3A)
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Включите, чтобы подписчики могли отправлять Звёзды Telegram в качестве реакций на публикации.",
                        color = TelegramAdminCaptionColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun MemberCircleAvatar(name: String, seed: String, sizeDp: Int) {
    val paletteColors = listOf(
        Color(0xFFE57373),
        Color(0xFF64B5F6),
        Color(0xFF81C784),
        Color(0xFFBA68C8),
        Color(0xFFFFB74D),
        Color(0xFF4DD0E1)
    )
    val bgColor = paletteColors[Math.abs(seed.hashCode()) % paletteColors.size]
    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "U" }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (sizeDp * 0.36f).sp
        )
    }
}

private fun formatSubscriberJoinedAt(timestamp: Long): String {
    val format = SimpleDateFormat("dd.MM.yy в HH:mm", Locale("ru"))
    return format.format(Date(timestamp))
}
