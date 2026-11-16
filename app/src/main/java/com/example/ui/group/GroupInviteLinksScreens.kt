@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.AppViewModel
import com.example.ui.navigateSafe
import com.example.ui.navigation.AppDestinations
import com.example.ui.popBackStackSafe
import com.example.utils.QrCodeGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val GroupBg = Color(0xFF0A0D12)
private val GroupCardBg = Color(0xFF161B24)
private val GroupPillBg = Color(0xFF1F242F)
private val GroupPurple = Color(0xFFB072FF)
private val GroupPurpleStrong = Color(0xFF9C5CFF)
private val GroupCaption = Color(0xFF858C99)

@Composable
fun GroupInviteLinksScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    BackHandler { navController.popBackStackSafe() }

    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId }
    val links by viewModel.observeGroupInviteLinks(chatId).collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(chatId) {
        viewModel.ensurePrimaryInviteLink(chatId)
    }

    val primaryLink = remember(links) {
        links.firstOrNull { it.isPrimary && it.isActive() } ?: links.firstOrNull { it.isPrimary }
    }
    val secondaryLinks = remember(links, primaryLink) {
        links.filter { it.id != primaryLink?.id }
    }

    var primaryMenuExpanded by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var qrUrlToShow by remember { mutableStateOf<String?>(null) }

    if (qrUrlToShow != null) {
        GroupInviteQrDialog(
            title = chat?.title ?: "Группа KuoteX",
            inviteUrl = qrUrlToShow!!,
            onDismiss = { qrUrlToShow = null }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = Color(0xFF171C26),
            title = { Text("Сбросить ссылку?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Предыдущая основная ссылка станет неактивной и останется в истории как отозванная. Вместо неё будет создана новая ссылка.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        viewModel.resetPrimaryInviteLink(chatId) {
                            Toast.makeText(context, "Ссылка сброшена и обновлена", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Сбросить", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Отмена", color = GroupPurple)
                }
            }
        )
    }

    Scaffold(
        containerColor = GroupBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GroupBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        Surface(
                            onClick = { navController.popBackStackSafe() },
                            shape = CircleShape,
                            color = GroupPillBg,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("invite_links_back")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                            }
                        }
                    }
                },
                title = {
                    Text(
                        text = "Пригласительные ссылки",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        GroupPurpleStrong.copy(alpha = 0.45f),
                                        Color(0xFF3E206D).copy(alpha = 0.25f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "Пригласительная ссылка",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(46.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Пользователи KuoteX смогут присоединиться к Вашей группе, перейдя по ссылке.",
                        color = GroupCaption,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Primary Invite Link Card
            item {
                val url = primaryLink?.url ?: "t.me/+..."
                val isPrimaryRevoked = primaryLink?.isActive() == false
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GroupCardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ссылка-приглашение",
                            color = GroupPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0E121A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = url,
                                    color = if (isPrimaryRevoked) GroupCaption else Color.White,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box {
                                    IconButton(
                                        onClick = { primaryMenuExpanded = true },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("primary_link_menu_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Действия со ссылкой",
                                            tint = GroupCaption
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = primaryMenuExpanded,
                                        onDismissRequest = { primaryMenuExpanded = false },
                                        containerColor = Color(0xFF1E232F)
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color.White)
                                            },
                                            text = { Text("Получить QR-код", color = Color.White) },
                                            modifier = Modifier.testTag("primary_link_qr_menu_item"),
                                            onClick = {
                                                primaryMenuExpanded = false
                                                qrUrlToShow = url
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = Color(0xFFFF5252))
                                            },
                                            text = { Text("Сбросить ссылку", color = Color(0xFFFF5252)) },
                                            modifier = Modifier.testTag("primary_link_reset_menu_item"),
                                            onClick = {
                                                primaryMenuExpanded = false
                                                showResetConfirm = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    copyInviteUrl(context, url)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GroupPurpleStrong),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("primary_link_copy_button")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Скопировать", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = {
                                    shareInviteUrl(context, chat?.title ?: "Группа KuoteX", url)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GroupPurpleStrong),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("primary_link_share_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Поделиться", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Create Additional Link Card
            item {
                Surface(
                    onClick = {
                        navController.navigateSafe(AppDestinations.groupNewInviteLink(chatId))
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = GroupCardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_invite_link_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(GroupPurpleStrong),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "Создать ссылку",
                            color = GroupPurple,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Вы можете создать дополнительные ссылки и задать для них срок действия, ограничение на число пользователей или заявки на вступление.",
                    color = GroupCaption,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            if (secondaryLinks.isNotEmpty()) {
                item {
                    Text(
                        text = "Дополнительные и неактивные ссылки",
                        color = GroupPurple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                items(secondaryLinks, key = { it.id }) { link ->
                    AdditionalInviteLinkItem(
                        link = link,
                        onCopy = { copyInviteUrl(context, link.url) },
                        onShowQr = { qrUrlToShow = link.url },
                        onRevoke = {
                            viewModel.revokeGroupInviteLink(chatId, link.id)
                            Toast.makeText(context, "Ссылка отмечена как отозванная", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdditionalInviteLinkItem(
    link: GroupInviteLinkEntity,
    onCopy: () -> Unit,
    onShowQr: () -> Unit,
    onRevoke: () -> Unit
) {
    val active = link.isActive()
    val inactiveReason = link.inactiveReason()
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GroupCardBg,
        border = BorderStroke(
            1.dp,
            if (active) Color.White.copy(alpha = 0.05f) else Color(0xFFFF5252).copy(alpha = 0.22f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invite_link_item_${link.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = active) { onCopy() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (active) GroupPurpleStrong.copy(alpha = 0.22f) else Color(0xFF282D38)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (active) Icons.Filled.Link else Icons.Filled.LinkOff,
                    contentDescription = null,
                    tint = if (active) GroupPurple else GroupCaption,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.label.ifBlank { link.url },
                    color = if (active) Color.White else Color.White.copy(alpha = 0.55f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (link.label.isNotBlank()) {
                    Text(
                        text = link.url,
                        color = if (active) GroupPurple else GroupCaption,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(3.dp))
                val usageSummary = buildString {
                    append("Использований: ${link.usageCount}")
                    if (link.maxUses != null) append(" / ${link.maxUses}")
                    if (link.requiresApproval) append(" · По заявкам")
                    if (link.expiresAt != null && active) {
                        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                        append(" · до ${fmt.format(Date(link.expiresAt))}")
                    }
                }
                Text(
                    text = usageSummary,
                    color = GroupCaption,
                    fontSize = 12.sp,
                    maxLines = 2
                )
                if (inactiveReason != null) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFF5252).copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = inactiveReason,
                            color = Color(0xFFFF8A80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Опции ссылки", tint = GroupCaption)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = Color(0xFF1E232F)
                ) {
                    if (active) {
                        DropdownMenuItem(
                            text = { Text("Скопировать", color = Color.White) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = Color.White) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Получить QR-код", color = Color.White) },
                        leadingIcon = { Icon(Icons.Filled.QrCode2, null, tint = Color.White) },
                        onClick = {
                            menuExpanded = false
                            onShowQr()
                        }
                    )
                    if (active) {
                        DropdownMenuItem(
                            text = { Text("Отозвать", color = Color(0xFFFF5252)) },
                            leadingIcon = { Icon(Icons.Filled.Block, null, tint = Color(0xFFFF5252)) },
                            onClick = {
                                menuExpanded = false
                                onRevoke()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupNewInviteLinkScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    BackHandler { navController.popBackStackSafe() }

    val context = LocalContext.current
    var durationSliderIndex by remember { mutableFloatStateOf(3f) } // 0=1h, 1=1d, 2=1w, 3=∞
    var customDurationMillis by remember { mutableStateOf<Long?>(null) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }

    var limitSliderIndex by remember { mutableFloatStateOf(3f) } // 0=1, 1=10, 2=100, 3=∞
    var customMaxUses by remember { mutableStateOf<Int?>(null) }
    var showCustomLimitDialog by remember { mutableStateOf(false) }

    var requiresApproval by remember { mutableStateOf(false) }
    var linkTitle by remember { mutableStateOf("") }

    if (showCustomDurationDialog) {
        var hoursText by remember { mutableStateOf("12") }
        AlertDialog(
            onDismissRequest = { showCustomDurationDialog = false },
            containerColor = Color(0xFF171C26),
            title = { Text("Свой срок действия (в часах)", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Часов") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GroupPurple
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val h = hoursText.toLongOrNull()?.coerceIn(1L, 8760L) ?: 12L
                        customDurationMillis = h * 3600_000L
                        showCustomDurationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GroupPurpleStrong)
                ) {
                    Text("Применить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDurationDialog = false }) {
                    Text("Отмена", color = GroupPurple)
                }
            }
        )
    }

    if (showCustomLimitDialog) {
        var usersText by remember { mutableStateOf("25") }
        AlertDialog(
            onDismissRequest = { showCustomLimitDialog = false },
            containerColor = Color(0xFF171C26),
            title = { Text("Число пользователей", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = usersText,
                    onValueChange = { usersText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Максимум участников") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GroupPurple
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        customMaxUses = usersText.toIntOrNull()?.coerceIn(1, 100000) ?: 25
                        showCustomLimitDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GroupPurpleStrong)
                ) {
                    Text("Применить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomLimitDialog = false }) {
                    Text("Отмена", color = GroupPurple)
                }
            }
        )
    }

    Scaffold(
        containerColor = GroupBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GroupBg),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        Surface(
                            onClick = { navController.popBackStackSafe() },
                            shape = CircleShape,
                            color = GroupPillBg,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("new_link_back")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                            }
                        }
                    }
                },
                title = {
                    Text(
                        text = "Новая ссылка",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        Surface(
                            onClick = {
                                val now = System.currentTimeMillis()
                                val expiresAt = customDurationMillis?.let { now + it } ?: when (durationSliderIndex.roundToInt()) {
                                    0 -> now + 3600_000L
                                    1 -> now + 86_400_000L
                                    2 -> now + 7L * 86_400_000L
                                    else -> null
                                }
                                val maxUses = if (requiresApproval) {
                                    null
                                } else {
                                    customMaxUses ?: when (limitSliderIndex.roundToInt()) {
                                        0 -> 1
                                        1 -> 10
                                        2 -> 100
                                        else -> null
                                    }
                                }
                                viewModel.createCustomInviteLink(
                                    chatId = chatId,
                                    label = linkTitle,
                                    expiresAt = expiresAt,
                                    maxUses = maxUses,
                                    requiresApproval = requiresApproval
                                ) {
                                    Toast.makeText(context, "Пригласительная ссылка создана", Toast.LENGTH_SHORT).show()
                                    navController.popBackStackSafe()
                                }
                            },
                            shape = RoundedCornerShape(21.dp),
                            color = GroupPillBg,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("create_new_link_submit")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Создать", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 36.dp)
        ) {
            // 1. Ограничение по времени
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GroupCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ограничение по времени",
                            color = GroupPurple,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        val durLabels = listOf("1 час", "1 день", "1 нед.", "∞")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val selectedIdx = durationSliderIndex.roundToInt()
                            durLabels.forEachIndexed { idx, lbl ->
                                Text(
                                    text = lbl,
                                    color = if (customDurationMillis == null && selectedIdx == idx) GroupPurple else GroupCaption,
                                    fontSize = 13.sp,
                                    fontWeight = if (customDurationMillis == null && selectedIdx == idx) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        Slider(
                            value = durationSliderIndex,
                            onValueChange = {
                                durationSliderIndex = it.roundToInt().toFloat()
                                customDurationMillis = null
                            },
                            valueRange = 0f..3f,
                            steps = 2,
                            colors = SliderDefaults.colors(
                                thumbColor = GroupPurple,
                                activeTrackColor = GroupPurpleStrong,
                                inactiveTrackColor = Color(0xFF282E3A)
                            )
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCustomDurationDialog = true }
                                .padding(top = 12.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Выбрать другой срок",
                                color = GroupCaption,
                                fontSize = 15.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            if (customDurationMillis != null) {
                                Text(
                                    text = "${customDurationMillis!! / 3600_000L} ч.",
                                    color = GroupPurple,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Вы можете выбрать срок действия для этой ссылки.",
                    color = GroupCaption,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // 2. Число пользователей (hidden when requiresApproval is ON)
            item {
                AnimatedVisibility(visible = !requiresApproval) {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = GroupCardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Число пользователей",
                                    color = GroupPurple,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                val limLabels = listOf("1", "10", "100", "∞")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val selectedIdx = limitSliderIndex.roundToInt()
                                    limLabels.forEachIndexed { idx, lbl ->
                                        Text(
                                            text = lbl,
                                            color = if (customMaxUses == null && selectedIdx == idx) GroupPurple else GroupCaption,
                                            fontSize = 13.sp,
                                            fontWeight = if (customMaxUses == null && selectedIdx == idx) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                                Slider(
                                    value = limitSliderIndex,
                                    onValueChange = {
                                        limitSliderIndex = it.roundToInt().toFloat()
                                        customMaxUses = null
                                    },
                                    valueRange = 0f..3f,
                                    steps = 2,
                                    colors = SliderDefaults.colors(
                                        thumbColor = GroupPurple,
                                        activeTrackColor = GroupPurpleStrong,
                                        inactiveTrackColor = Color(0xFF282E3A)
                                    )
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCustomLimitDialog = true }
                                        .padding(top = 12.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Выбрать другое число",
                                        color = GroupCaption,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    if (customMaxUses != null) {
                                        Text(
                                            text = "$customMaxUses",
                                            color = GroupPurple,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.widthIn(max = 140.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Вы можете выбрать, сколько пользователей смогут присоединиться по этой ссылке.",
                            color = GroupCaption,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // 3. Заявки на вступление
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GroupCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { requiresApproval = !requiresApproval }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Заявки на вступление",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Switch(
                            checked = requiresApproval,
                            onCheckedChange = { requiresApproval = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GroupPurpleStrong,
                                uncheckedThumbColor = Color(0xFF9EA5B1),
                                uncheckedTrackColor = Color(0xFF282E3A)
                            ),
                            modifier = Modifier.testTag("join_requests_switch")
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Новые пользователи смогут присоединиться по ссылке только после подтверждения администратором.",
                    color = GroupCaption,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // 4. Название (необязательно)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GroupCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = linkTitle,
                        onValueChange = { linkTitle = it },
                        placeholder = { Text("Название (необязательно)", color = GroupCaption) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invite_link_label_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = GroupPurple
                        )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Название ссылки будут видеть только администраторы.",
                    color = GroupCaption,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
fun GroupInviteLinkQuickSheet(
    inviteUrl: String,
    groupTitle: String,
    onDismiss: () -> Unit,
    onManageInvites: () -> Unit,
    onOpenQr: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF171B24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(GroupPurpleStrong),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Ссылка-приглашение",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Любой пользователь KuoteX сможет вступить в Вашу группу, перейдя по этой ссылке.",
                color = GroupCaption,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0E121A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = inviteUrl,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Опции ссылки", tint = GroupCaption)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = Color(0xFF1E232F)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Получить QR-код", color = Color.White) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenQr()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { copyInviteUrl(context, inviteUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = GroupPurpleStrong),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Скопировать", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { shareInviteUrl(context, groupTitle, inviteUrl) },
                    colors = ButtonDefaults.buttonColors(containerColor = GroupPurpleStrong),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Поделиться", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    onDismiss()
                    onManageInvites()
                },
                modifier = Modifier.testTag("manage_invites_sheet_button")
            ) {
                Text(
                    text = "Управление приглашениями",
                    color = GroupPurple,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun GroupInviteQrDialog(
    title: String,
    inviteUrl: String,
    onDismiss: () -> Unit
) {
    val fullUrl = if (inviteUrl.startsWith("http")) inviteUrl else "https://$inviteUrl"
    val qrBitmap = remember(fullUrl) { QrCodeGenerator.generateQrCode(fullUrl, 680) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101319))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR-код приглашения",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = title,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = inviteUrl,
                            color = Color(0xFF5A5E6B),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = GroupPurpleStrong),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Закрыть", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

internal fun copyInviteUrl(context: Context, url: String) {
    val full = if (url.startsWith("http")) url else "https://$url"
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Group Invite Link", full))
    Toast.makeText(context, "Ссылка скопирована: $full", Toast.LENGTH_SHORT).show()
}

internal fun shareInviteUrl(context: Context, title: String, url: String) {
    val full = if (url.startsWith("http")) url else "https://$url"
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, full)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться ссылкой"))
    } catch (_: Exception) {
        copyInviteUrl(context, url)
    }
}
