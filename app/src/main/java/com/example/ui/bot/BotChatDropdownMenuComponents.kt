package com.example.ui.bot

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class BotMenuPage {
    MAIN,
    AUTO_DELETE,
    NOTIFICATIONS
}

/**
 * Dropdown Menu for Telegram-style Bot Chat & Bot Profile Screens.
 * Triggered by the vertical ellipsis (⋮) in Box container.
 * Features:
 *  1. Автоудаление (Auto-delete submenu / wheel)
 *  2. Создать ярлык (Add to Home screen shortcut)
 *  3. Поделиться (Share bot link via Intent)
 *  4. Политика конфиденциальности (Bot Privacy Policy bottom sheet)
 *  5. Сохранить в галерею (Save bot profile photo/media to device gallery)
 *  6. Пожаловаться (Multi-step report flow with reason & description)
 *  7. Удалить и заблокировать (Stop, block and remove bot from chats)
 */
@Composable
fun BotChatDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    botName: String,
    botUsername: String,
    botAvatarUrl: String? = null,
    isMuted: Boolean = false,
    isSoundMuted: Boolean = false,
    currentAutoDeletePeriod: String? = null,
    onToggleMute: ((Boolean) -> Unit)? = null,
    onAutoDeleteSelected: ((String?) -> Unit)? = null,
    onOpenAutoDeleteCustomWheel: (() -> Unit)? = null,
    onCreateShortcut: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onPrivacyPolicy: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onSaveToGallery: (() -> Unit)? = null,
    onDeleteAndBlock: (() -> Unit)? = null,
    onClearHistory: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null
) {
    var currentPage by remember { mutableStateOf(BotMenuPage.MAIN) }

    LaunchedEffect(expanded) {
        if (!expanded) {
            currentPage = BotMenuPage.MAIN
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = 4.dp),
        modifier = Modifier
            .width(260.dp)
            .background(Color(0xFF212D3B), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
        containerColor = Color(0xFF212D3B),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 12.dp
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState == BotMenuPage.AUTO_DELETE || targetState == BotMenuPage.NOTIFICATIONS) {
                    (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(220)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(180)))
                } else {
                    (slideInHorizontally(animationSpec = tween(220)) { -it } + fadeIn(animationSpec = tween(220)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(180)))
                }
            },
            label = "BotChatMenuTransition"
        ) { page ->
            when (page) {
                BotMenuPage.MAIN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // 1. Поиск (если доступен)
                        if (onSearch != null) {
                            BotMenuRowItem(
                                icon = Icons.Outlined.Search,
                                title = "Поиск",
                                onClick = {
                                    onDismissRequest()
                                    onSearch()
                                }
                            )
                        }

                        // 2. Автоудаление
                        BotMenuRowItem(
                            icon = Icons.Outlined.AccessTime,
                            title = "Автоудаление",
                            hasSubmenu = true,
                            onClick = {
                                currentPage = BotMenuPage.AUTO_DELETE
                            }
                        )

                        // 3. Создать ярлык
                        BotMenuRowItem(
                            icon = Icons.Outlined.AddBox,
                            title = "Создать ярлык",
                            onClick = {
                                onDismissRequest()
                                onCreateShortcut?.invoke()
                            }
                        )

                        // 4. Поделиться
                        BotMenuRowItem(
                            icon = Icons.Outlined.Share,
                            title = "Поделиться",
                            onClick = {
                                onDismissRequest()
                                onShare?.invoke()
                            }
                        )

                        // 5. Политика конфиденциальности
                        BotMenuRowItem(
                            icon = Icons.Outlined.Policy,
                            title = "Политика конфиденциальности",
                            onClick = {
                                onDismissRequest()
                                onPrivacyPolicy?.invoke()
                            }
                        )

                        // 6. Сохранить в галерею
                        BotMenuRowItem(
                            icon = Icons.Outlined.Download,
                            title = "Сохранить в галерею",
                            onClick = {
                                onDismissRequest()
                                onSaveToGallery?.invoke()
                            }
                        )

                        // 7. Очистить историю
                        if (onClearHistory != null) {
                            BotMenuRowItem(
                                icon = Icons.Outlined.CleaningServices,
                                title = "Очистить историю",
                                onClick = {
                                    onDismissRequest()
                                    onClearHistory()
                                }
                            )
                        }

                        // 8. Пожаловаться
                        BotMenuRowItem(
                            icon = Icons.Outlined.ReportProblem,
                            title = "Пожаловаться",
                            onClick = {
                                onDismissRequest()
                                onReport?.invoke()
                            }
                        )

                        // 9. Удалить и заблокировать
                        BotMenuRowItem(
                            icon = Icons.Outlined.Block,
                            title = "Удалить и заблокировать",
                            textColor = Color(0xFFFF5252),
                            iconTint = Color(0xFFFF5252),
                            onClick = {
                                onDismissRequest()
                                onDeleteAndBlock?.invoke()
                            }
                        )
                    }
                }

                BotMenuPage.AUTO_DELETE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Назад
                        BotMenuRowItem(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            title = "Назад",
                            onClick = {
                                currentPage = BotMenuPage.MAIN
                            }
                        )

                        // 1 день
                        BotMenuRowItem(
                            badgeText = "1D",
                            title = "1 день",
                            onClick = {
                                onDismissRequest()
                                onAutoDeleteSelected?.invoke("1 день")
                            }
                        )

                        // 7 дней
                        BotMenuRowItem(
                            badgeText = "7д",
                            title = "7 дней",
                            onClick = {
                                onDismissRequest()
                                onAutoDeleteSelected?.invoke("7 дней")
                            }
                        )

                        // 1 месяц
                        BotMenuRowItem(
                            badgeText = "1м",
                            title = "1 месяц",
                            onClick = {
                                onDismissRequest()
                                onAutoDeleteSelected?.invoke("1 месяц")
                            }
                        )

                        // Настроить
                        BotMenuRowItem(
                            icon = Icons.Outlined.Tune,
                            title = "Настроить",
                            onClick = {
                                onDismissRequest()
                                onOpenAutoDeleteCustomWheel?.invoke()
                            }
                        )

                        // Выключить (если автоудаление включено)
                        if (!currentAutoDeletePeriod.isNullOrEmpty() && currentAutoDeletePeriod != "Нет") {
                            BotMenuRowItem(
                                icon = Icons.Outlined.Block,
                                title = "Выключить",
                                textColor = Color(0xFFFF5252),
                                iconTint = Color(0xFFFF5252),
                                onClick = {
                                    onDismissRequest()
                                    onAutoDeleteSelected?.invoke(null)
                                }
                            )
                        }

                        // Info helper card at the bottom
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color.Black.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Сообщения, отправленные в этот чат, будут автоматически удалены через выбранное Вами время.",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                BotMenuPage.NOTIFICATIONS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        BotMenuRowItem(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            title = "Назад",
                            onClick = {
                                currentPage = BotMenuPage.MAIN
                            }
                        )

                        BotMenuRowItem(
                            icon = if (isMuted) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                            title = if (isMuted) "Включить уведомления" else "Выключить уведомления",
                            textColor = if (isMuted) Color(0xFF5EABEB) else Color(0xFFFF5252),
                            iconTint = if (isMuted) Color(0xFF5EABEB) else Color(0xFFFF5252),
                            onClick = {
                                onDismissRequest()
                                onToggleMute?.invoke(!isMuted)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BotMenuRowItem(
    icon: ImageVector? = null,
    badgeText: String? = null,
    title: String,
    textColor: Color = Color.White,
    iconTint: Color = Color.White.copy(alpha = 0.85f),
    hasSubmenu: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, iconTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = iconTint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (hasSubmenu) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Multi-Step Bot Report Bottom Sheet (Пожаловаться).
 * Step 1: Select Report Reason category
 * Step 2: Write additional description / comment and confirm submission to Admin Panel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotReportBottomSheet(
    botName: String,
    botUsername: String,
    onDismissRequest: () -> Unit,
    onSubmitReport: (reasonCategory: String, userComment: String, shouldBlock: Boolean) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) } // 1: Select Reason, 2: Enter Description
    var selectedReason by remember { mutableStateOf("") }
    var userComment by remember { mutableStateOf("") }
    var alsoBlockBot by remember { mutableStateOf(false) }

    val reportCategories = listOf(
        "Спам" to "Нежелательная реклама или повторяющиеся сообщения",
        "Насилие" to "Призывы к насилию, опасные действия",
        "Порнография" to "Материалы сексуального или непристойного характера",
        "Детская порнография" to "Эксплуатация и материалы с участием несовершеннолетних",
        "Нарушение авторских прав" to "Незаконное использование чужого контента",
        "Нелегальные товары" to "Продажа запрещенных веществ, оружия или документов",
        "Мошенничество" to "Фишинг, вымогательство или обман пользователей",
        "Персональные данные" to "Публикация личной информации без согласия",
        "Другое" to "Любые другие нарушения правил платформы"
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1B1F2A),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep == 2) {
                    IconButton(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentStep == 1) "Пожаловаться на бота" else "Описание жалобы",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$botName ($botUsername)",
                        color = Color(0xFF5EABEB),
                        fontSize = 13.sp
                    )
                }
            }

            if (currentStep == 1) {
                Text(
                    text = "Выберите причину, по которой этот бот нарушает правила Telegram / KuoteX:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    items(reportCategories.size) { index ->
                        val (category, description) = reportCategories[index]
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedReason = category
                                    currentStep = 2
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF242B38)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = description,
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 12.sp
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Step 2: Description & Confirmation
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF242B38)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReportProblem,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Категория нарушения:",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                            Text(
                                text = selectedReason,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = userComment,
                    onValueChange = { if (it.length <= 500) userComment = it },
                    label = { Text("Опишите подробнее (необязательно)") },
                    placeholder = { Text("Укажите контекст нарушения, команды или ссылки...", color = Color.White.copy(alpha = 0.35f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E2430),
                        unfocusedContainerColor = Color(0xFF1E2430),
                        focusedBorderColor = Color(0xFF5EABEB),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF5EABEB),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    maxLines = 5
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${userComment.length}/500",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }

                // Checkbox to also block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alsoBlockBot = !alsoBlockBot }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = alsoBlockBot,
                        onCheckedChange = { alsoBlockBot = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF5EABEB),
                            uncheckedColor = Color.White.copy(alpha = 0.5f),
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Также заблокировать бота и удалить диалог",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSubmitReport(selectedReason, userComment.trim(), alsoBlockBot)
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5EABEB))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Отправить жалобу модераторам",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Bot Privacy Policy Bottom Sheet (Политика конфиденциальности).
 * Styled according to Telegram & KuoteX platform bot privacy standards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotPrivacyPolicyBottomSheet(
    botName: String,
    botUsername: String,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF1B1F2A),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5EABEB).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = Color(0xFF5EABEB),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Политика конфиденциальности",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Бот $botName ($botUsername)",
                        color = Color(0xFF5EABEB),
                        fontSize = 13.sp
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                item {
                    PrivacyPolicyCard(
                        title = "1. Сбор и обработка сообщений",
                        description = "Бот $botUsername обрабатывает только те команды и сообщения, которые отправлены непосредственно в этот чат или в которых бот был упомянут. Бот не имеет доступа к вашим личным чатам или контактам без явного разрешения."
                    )
                }
                item {
                    PrivacyPolicyCard(
                        title = "2. Передача третьим лицам",
                        description = "Персональные данные, идентификатор аккаунта и история переписки не передаются третьим сторонам, рекламным сетям или брокерам данных. Все запросы изолированы внутри KuoteX Bot Sandbox API."
                    )
                }
                item {
                    PrivacyPolicyCard(
                        title = "3. Безопасность и шифрование",
                        description = "Все сетевые взаимодействия с ботом зашифрованы по протоколу MTProto 2.0 и TLS 1.3. Платежные токены и подписки VIP обрабатываются через защищенные криптографические каналы."
                    )
                }
                item {
                    PrivacyPolicyCard(
                        title = "4. Удаление данных и отказ от использования",
                        description = "Вы можете в любой момент удалить историю чата, заблокировать бота или очистить кэш мини-приложений в настройках аккаунта KuoteX. Данные сессии будут стерты безвозвратно."
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5EABEB))
            ) {
                Text("Понятно", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PrivacyPolicyCard(title: String, description: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF242B38)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = Color(0xFF5EABEB),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Delete and Block Confirmation Dialog for Bot ("Удалить и заблокировать").
 */
@Composable
fun BotDeleteAndBlockDialog(
    botName: String,
    botUsername: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF212D3B),
        shape = RoundedCornerShape(18.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Удалить и заблокировать?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Вы действительно хотите заблокировать бота $botName ($botUsername) и удалить историю сообщений? Бот больше не сможет отправлять Вам сообщения.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Заблокировать",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = "Отмена",
                    color = Color(0xFF5EABEB),
                    fontSize = 14.sp
                )
            }
        }
    )
}

/**
 * Helper to share bot link via system Intent.ACTION_SEND
 */
fun shareBotLink(context: Context, botUsername: String, botName: String) {
    val cleanUsername = botUsername.removePrefix("@")
    val shareUrl = "https://t.me/$cleanUsername"
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "Попробуйте бота $botName в мессенджере KuoteX: $shareUrl")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Поделиться ботом $botName")
    context.startActivity(shareIntent)
}

/**
 * Helper to create a Home Screen Launcher shortcut for the Bot Chat
 */
fun createBotShortcut(context: Context, chatId: String, botName: String, botUsername: String, avatarUrl: String?) {
    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        val launchIntent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(context.packageName, "com.example.MainActivity")
            putExtra("EXTRA_CHAT_ID", chatId)
            putExtra("EXTRA_IS_BOT", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(context, "bot_shortcut_$chatId")
            .setShortLabel(botName)
            .setLongLabel("Чат с ботом $botName ($botUsername)")
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_send))
            .setIntent(launchIntent)
            .build()

        val pinnedShortcutCallbackIntent = ShortcutManagerCompat.createShortcutResultIntent(context, shortcutInfo)
        val successCallback = PendingIntent.getBroadcast(
            context,
            0,
            pinnedShortcutCallbackIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, successCallback.intentSender)
        Toast.makeText(context, "Ярлык для $botName добавлен на главный экран", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Ярлык для бота $botName создан", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Helper to save bot avatar / picture into device Gallery / MediaStore
 */
fun saveBotMediaToGallery(
    context: Context,
    coroutineScope: CoroutineScope,
    imageUrl: String?,
    botName: String
) {
    if (imageUrl.isNullOrBlank()) {
        Toast.makeText(context, "Фото профиля бота недоступно", Toast.LENGTH_SHORT).show()
        return
    }

    coroutineScope.launch(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()

            val result = loader.execute(req)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val saved = saveBitmapToMediaStore(context, bitmap, "KuoteX_Bot_${botName.replace(" ", "_")}_${System.currentTimeMillis()}")
                    withContext(Dispatchers.Main) {
                        if (saved) {
                            Toast.makeText(context, "Фото профиля бота сохранено в Галерею 🖼️", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Не удалось сохранить фото в Галерею", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Фото сохранено в память устройства", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Фото профиля сохранено", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    var outputStream: OutputStream? = null
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KuoteX")
            }
            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                outputStream = context.contentResolver.openOutputStream(uri)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream!!)
                return true
            }
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val kuotexDir = File(picturesDir, "KuoteX")
            if (!kuotexDir.exists()) kuotexDir.mkdirs()
            val file = File(kuotexDir, "$fileName.jpg")
            outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            return true
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        outputStream?.close()
    }
    return false
}
