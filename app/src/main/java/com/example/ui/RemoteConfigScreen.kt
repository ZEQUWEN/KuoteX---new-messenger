package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.config.FirebaseRemoteConfigManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteConfigScreen(viewModel: AppViewModel, navController: NavController) {
    val context = LocalContext.current
    val config by viewModel.remoteConfig.collectAsState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableIntStateOf(0) } // 0: All, 1: Features, 2: Params

    val allParams = remember(config) {
        FirebaseRemoteConfigManager.getAllConfigParameters()
    }

    val filteredParams = remember(allParams, searchQuery, selectedCategory) {
        allParams.filter { (key, _) ->
            val matchesSearch = searchQuery.isBlank() || key.contains(searchQuery, ignoreCase = true)
            val matchesCat = when (selectedCategory) {
                1 -> key.startsWith("feature_")
                2 -> !key.startsWith("feature_") && !key.startsWith("last_")
                else -> true
            }
            matchesSearch && matchesCat
        }
    }

    val formattedFetchTime = remember(config.lastFetchTimeMillis) {
        if (config.lastFetchTimeMillis > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(config.lastFetchTimeMillis))
        } else {
            "Not fetched yet"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Firebase Remote Config",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            // Live indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF66))
                            )
                        }
                        Text(
                            "Динамические флаги и параметры",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            viewModel.refreshRemoteConfig { success ->
                                isRefreshing = false
                                Toast.makeText(
                                    context,
                                    if (success) "Конфигурация обновлена с сервера!" else "Конфигурация актуальна",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Status Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, Color(0xFF00E5FF))
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CloudSync,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Статус синхронизации",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    config.lastFetchStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (config.lastFetchStatus.contains("ERROR") || config.lastFetchStatus.contains("FAILED"))
                                        MaterialTheme.colorScheme.error
                                    else
                                        Color(0xFF00E5FF)
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    isRefreshing = true
                                    viewModel.refreshRemoteConfig {
                                        isRefreshing = false
                                        Toast.makeText(context, "Fetch & Activate выполнен", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Fetch", fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Последнее обновление", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formattedFetchTime, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Real-time Listener", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Активен (Live)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF00FF66), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Announcement Banner Preview (if active)
            if (config.announcementBannerText.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Активное оповещение (Remote)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                                Text(config.announcementBannerText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Maintenance Banner (if active)
            if (config.isMaintenanceMode) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Внимание: Режим обслуживания включен", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                Text(config.maintenanceMessage, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Feature Flags Overview Grid
            item {
                Text(
                    "Динамические флаги возможностей (Feature Flags)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        FeatureFlagRow(
                            icon = Icons.Filled.AutoStories,
                            name = "Истории (Stories)",
                            key = FirebaseRemoteConfigManager.KEY_FEATURE_STORIES,
                            enabled = config.isStoriesEnabled
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        FeatureFlagRow(
                            icon = Icons.Filled.Call,
                            name = "Аудио и видео звонки",
                            key = FirebaseRemoteConfigManager.KEY_FEATURE_CALLS,
                            enabled = config.isCallsEnabled
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        FeatureFlagRow(
                            icon = Icons.Filled.SmartToy,
                            name = "Магазин ботов (Bot Store)",
                            key = FirebaseRemoteConfigManager.KEY_FEATURE_BOT_STORE,
                            enabled = config.isBotStoreEnabled
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        FeatureFlagRow(
                            icon = Icons.Filled.ScheduleSend,
                            name = "Отложенные сообщения",
                            key = FirebaseRemoteConfigManager.KEY_FEATURE_SCHEDULED_MESSAGES,
                            enabled = config.isScheduledMessagesEnabled
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        FeatureFlagRow(
                            icon = Icons.Filled.AutoAwesome,
                            name = "Неоновые частицы и спецэффекты",
                            key = FirebaseRemoteConfigManager.KEY_FEATURE_NEON_PARTICLES,
                            enabled = config.isNeonParticlesEnabled
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        FeatureFlagRow(
                            icon = Icons.Filled.Apps,
                            name = "Web Apps (Мини-приложения)",
                            key = FirebaseRemoteConfigManager.KEY_FEATURE_WEB_APPS,
                            enabled = config.isWebAppsEnabled
                        )
                    }
                }
            }

            // Operational Parameters Overview
            item {
                Text(
                    "Динамические параметры приложения",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ParameterRow(
                            title = "Макс. размер файла",
                            key = FirebaseRemoteConfigManager.KEY_MAX_ATTACHMENT_SIZE_MB,
                            value = "${config.maxAttachmentSizeMb} MB"
                        )
                        ParameterRow(
                            title = "Интервал синхронизации",
                            key = FirebaseRemoteConfigManager.KEY_CHAT_SYNC_INTERVAL_SEC,
                            value = "${config.chatSyncIntervalSeconds} сек"
                        )
                        ParameterRow(
                            title = "Мин. версия приложения",
                            key = FirebaseRemoteConfigManager.KEY_MIN_REQUIRED_VERSION,
                            value = "v${config.minRequiredVersion}"
                        )
                        ParameterRow(
                            title = "Контакт поддержки",
                            key = FirebaseRemoteConfigManager.KEY_SUPPORT_CONTACT,
                            value = config.supportContactUsername
                        )
                        ParameterRow(
                            title = "Приветствие",
                            key = FirebaseRemoteConfigManager.KEY_WELCOME_MESSAGE,
                            value = config.welcomeMessage
                        )
                    }
                }
            }

            // All Key-Value Inspector & Search
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Все ключи конфигурации (${filteredParams.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск по ключу...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedCategory == 0,
                        onClick = { selectedCategory = 0 },
                        label = { Text("Все") }
                    )
                    FilterChip(
                        selected = selectedCategory == 1,
                        onClick = { selectedCategory = 1 },
                        label = { Text("Флаги (Features)") }
                    )
                    FilterChip(
                        selected = selectedCategory == 2,
                        onClick = { selectedCategory = 2 },
                        label = { Text("Параметры") }
                    )
                }
            }

            items(filteredParams.entries.toList()) { (key, value) ->
                ConfigItemCard(
                    key = key,
                    value = value.toString(),
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("RemoteConfig", "$key=$value"))
                        Toast.makeText(context, "Скопировано: $key", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FeatureFlagRow(
    icon: ImageVector,
    name: String,
    key: String,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) Color(0xFF00E5FF).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(key, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (enabled) Color(0xFF00FF66).copy(alpha = 0.15f) else Color(0xFFFF3B30).copy(alpha = 0.15f)
        ) {
            Text(
                text = if (enabled) "ВКЛЮЧЕНО" else "ОТКЛЮЧЕНО",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color(0xFF00FF66) else Color(0xFFFF3B30)
            )
        }
    }
}

@Composable
private fun ParameterRow(
    title: String,
    key: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(key, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConfigItemCard(
    key: String,
    value: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Копировать",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
