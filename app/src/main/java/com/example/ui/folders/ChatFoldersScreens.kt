package com.example.ui.folders

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.folders.ChatFolder
import com.example.data.folders.matches
import com.example.ui.AppViewModel
import com.example.ui.Chat
import com.example.ui.navigation.AppDestinations
import java.util.UUID

private val FOLDER_COLORS = listOf(
    "#2196F3", // Blue
    "#4CAF50", // Green
    "#FFA000", // Amber / Yellow
    "#FF7043", // Coral / Orange
    "#7E57C2", // Purple
    "#26C6DA", // Cyan
    "#EC407A"  // Pink
)

private val FOLDER_EMOJIS = listOf(
    "📁", "💼", "🚀", "⭐", "📚", "🎮", "🏠", "💡", "👥", "📢", "🤖", "💬", "❤️", "🎯"
)

/**
 * Main "Папки с чатами" (Chat Folders) screen matching Telegram design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFoldersScreen(
    viewModel: AppViewModel,
    navController: NavController
) {
    val folders by viewModel.chatFolders.collectAsStateWithLifecycle()
    val chatTagsEnabled by viewModel.chatTagsEnabled.collectAsStateWithLifecycle()
    val allChats by viewModel.chats.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    var showDeleteConfirmDialog by remember { mutableStateOf<ChatFolder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Папки с чатами", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with stylish folder illustration
            item {
                FolderHeaderBanner()
            }

            // Recommended Folders Section
            item {
                Text(
                    text = "РЕКОМЕНДОВАННЫЕ ПАПКИ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val hasNew = folders.any { it.includeUnreadOnly || it.name.equals("Новые", ignoreCase = true) }
                        RecommendedFolderItem(
                            emoji = "🔔",
                            name = "Новые",
                            description = "Чаты с новыми сообщениями.",
                            colorHex = "#2196F3",
                            isAdded = hasNew,
                            onAdd = {
                                viewModel.saveChatFolder(ChatFolder.recommendedNew())
                                Toast.makeText(context, "Папка «Новые» добавлена", Toast.LENGTH_SHORT).show()
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        val hasPersonal = folders.any { it.includePersonal || it.name.equals("Личные", ignoreCase = true) }
                        RecommendedFolderItem(
                            emoji = "👤",
                            name = "Личные",
                            description = "Сообщения из личных чатов.",
                            colorHex = "#4CAF50",
                            isAdded = hasPersonal,
                            onAdd = {
                                viewModel.saveChatFolder(ChatFolder.recommendedPersonal())
                                Toast.makeText(context, "Папка «Личные» добавлена", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // User Chat Folders Section
            item {
                Text(
                    text = "ПАПКИ С ЧАТАМИ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Default "Все чаты" folder
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Все чаты",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Все чаты и каналы",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Custom folders
                        folders.forEach { folder ->
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            FolderListItem(
                                folder = folder,
                                chatCount = allChats.count { folder.matches(it) },
                                onClick = {
                                    navController.navigate("${AppDestinations.SETTINGS_FOLDERS}/edit?folderId=${folder.id}")
                                },
                                onDelete = {
                                    showDeleteConfirmDialog = folder
                                }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        // Button to create new folder
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate("${AppDestinations.SETTINGS_FOLDERS}/edit")
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Создать новую папку",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Chat Tags Switch Section
            item {
                Text(
                    text = "ТЕГИ ДЛЯ ЧАТОВ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Теги для чатов",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Названия папок отображаются рядом с чатами в списке чатов.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = chatTagsEnabled,
                                onCheckedChange = { viewModel.setChatTagsEnabled(it) }
                            )
                        }

                        if (chatTagsEnabled && folders.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Пример тегов: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                folders.take(3).forEach { f ->
                                    val fColor = runCatching { Color(android.graphics.Color.parseColor(f.colorHex)) }
                                        .getOrDefault(Color(0xFF2196F3))
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(fColor)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = f.name.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { folderToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Удалить папку?") },
            text = { Text("Папка «${folderToDelete.name}» будет удалена. Чаты останутся в общем списке.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChatFolder(folderToDelete.id)
                        showDeleteConfirmDialog = null
                        Toast.makeText(context, "Папка удалена", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Visual illustration banner at the top of the Chat Folders screen.
 */
@Composable
private fun FolderHeaderBanner() {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF64B5F6),
                                Color(0xFF1976D2)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.FolderCopy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Папки с чатами",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Вы можете создать папки с нужными чатами и переключаться между ними.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecommendedFolderItem(
    emoji: String,
    name: String,
    description: String,
    colorHex: String,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    val folderColor = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(Color(0xFF2196F3))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(folderColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp)
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isAdded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Добавлено",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Добавлено",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Button(
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Добавить", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FolderListItem(
    folder: ChatFolder,
    chatCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val folderColor = runCatching { Color(android.graphics.Color.parseColor(folder.colorHex)) }
        .getOrDefault(Color(0xFF2196F3))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Menu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.width(16.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(folderColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (!folder.emoji.isNullOrBlank()) {
                Text(folder.emoji, fontSize = 20.sp)
            } else {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = folderColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val subtitle = when {
                chatCount == 0 -> "Нет чатов"
                chatCount % 10 == 1 && chatCount % 100 != 11 -> "$chatCount чат"
                chatCount % 10 in 2..4 && chatCount % 100 !in 12..14 -> "$chatCount чата"
                else -> "$chatCount чатов"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Опции",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Настроить папку") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Удалить папку", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Screen for Creating or Editing a Chat Folder (matching Screenshots 2, 3, 5, 6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFolderEditScreen(
    viewModel: AppViewModel,
    navController: NavController,
    folderId: String? = null
) {
    val context = LocalContext.current
    val allChats by viewModel.chats.collectAsStateWithLifecycle(initialValue = emptyList())
    val existingFolder = remember(folderId) {
        if (!folderId.isNullOrBlank()) viewModel.getChatFolder(folderId) else null
    }

    var folderName by remember { mutableStateOf(existingFolder?.name ?: "") }
    var selectedEmoji by remember { mutableStateOf(existingFolder?.emoji ?: "") }
    var selectedColorHex by remember { mutableStateOf(existingFolder?.colorHex ?: FOLDER_COLORS.first()) }

    var includedChatIds by remember { mutableStateOf(existingFolder?.includedChatIds?.toSet() ?: emptySet()) }
    var includePersonal by remember { mutableStateOf(existingFolder?.includePersonal ?: false) }
    var includeGroups by remember { mutableStateOf(existingFolder?.includeGroups ?: false) }
    var includeChannels by remember { mutableStateOf(existingFolder?.includeChannels ?: false) }
    var includeBots by remember { mutableStateOf(existingFolder?.includeBots ?: false) }
    var includeUnreadOnly by remember { mutableStateOf(existingFolder?.includeUnreadOnly ?: false) }

    var excludedChatIds by remember { mutableStateOf(existingFolder?.excludedChatIds?.toSet() ?: emptySet()) }
    var excludeMuted by remember { mutableStateOf(existingFolder?.excludeMuted ?: false) }
    var excludeRead by remember { mutableStateOf(existingFolder?.excludeRead ?: false) }
    var excludeArchived by remember { mutableStateOf(existingFolder?.excludeArchived ?: true) }

    var showAddChatsSheet by remember { mutableStateOf(false) }
    var showAddExceptionsSheet by remember { mutableStateOf(false) }
    var showEmojiDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showShareLinkDialog by remember { mutableStateOf(false) }

    val isSaveEnabled = folderName.isNotBlank()

    fun doSave() {
        if (!isSaveEnabled) return
        val folder = ChatFolder(
            id = existingFolder?.id ?: UUID.randomUUID().toString(),
            name = folderName.trim(),
            emoji = if (selectedEmoji.isNotBlank()) selectedEmoji else null,
            colorHex = selectedColorHex,
            includedChatIds = includedChatIds.toList(),
            includePersonal = includePersonal,
            includeGroups = includeGroups,
            includeChannels = includeChannels,
            includeBots = includeBots,
            includeUnreadOnly = includeUnreadOnly,
            excludedChatIds = excludedChatIds.toList(),
            excludeMuted = excludeMuted,
            excludeRead = excludeRead,
            excludeArchived = excludeArchived,
            order = existingFolder?.order ?: 0
        )
        viewModel.saveChatFolder(folder)
        Toast.makeText(context, "Папка сохранена", Toast.LENGTH_SHORT).show()
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingFolder != null) folderName.ifBlank { "Настройка папки" } else "Новая папка",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { doSave() },
                        enabled = isSaveEnabled
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Сохранить",
                            tint = if (isSaveEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Folder Name & Emoji
            item {
                Text(
                    text = "НАЗВАНИЕ ПАПКИ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = folderName,
                                onValueChange = { if (it.length <= 30) folderName = it },
                                placeholder = { Text("Название папки") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showEmojiDialog = true }) {
                                        if (selectedEmoji.isNotBlank()) {
                                            Text(selectedEmoji, fontSize = 20.sp)
                                        } else {
                                            Icon(
                                                Icons.Filled.Mood,
                                                contentDescription = "Выбрать эмодзи",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Quick emoji selector row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FOLDER_EMOJIS.take(8).forEach { emo ->
                                val isSelected = selectedEmoji == emo
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                        .clickable {
                                            selectedEmoji = if (isSelected) "" else emo
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emo, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Selected Chats (Выбранные чаты)
            item {
                Text(
                    text = "ВЫБРАННЫЕ ЧАТЫ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // "+ Добавить чаты" button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddChatsSheet = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Добавить чаты",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Category inclusion chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = includePersonal,
                                onClick = { includePersonal = !includePersonal },
                                label = { Text("Личные", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = includeGroups,
                                onClick = { includeGroups = !includeGroups },
                                label = { Text("Группы", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = includeChannels,
                                onClick = { includeChannels = !includeChannels },
                                label = { Text("Каналы", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = includeBots,
                                onClick = { includeBots = !includeBots },
                                label = { Text("Боты", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        // List of selected individual chats
                        val selectedChats = allChats.filter { it.id in includedChatIds }
                        if (selectedChats.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            selectedChats.forEach { chat ->
                                SelectedChatItemRow(
                                    chat = chat,
                                    onRemove = {
                                        includedChatIds = includedChatIds - chat.id
                                    }
                                )
                            }
                        }

                        // Subtitle hint
                        Text(
                            text = "Выберите чаты или типы чатов, которые нужно показывать в этой папке.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Section 3: Excluded Chats (Исключённые чаты)
            item {
                Text(
                    text = "ИСКЛЮЧЁННЫЕ ЧАТЫ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // "+ Добавить исключения" button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAddExceptionsSheet = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Добавить исключения",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Exclusion filter chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = excludeMuted,
                                onClick = { excludeMuted = !excludeMuted },
                                label = { Text("Без звука", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = excludeRead,
                                onClick = { excludeRead = !excludeRead },
                                label = { Text("Прочитанные", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = excludeArchived,
                                onClick = { excludeArchived = !excludeArchived },
                                label = { Text("В архиве", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        // List of excluded individual chats
                        val excludedChats = allChats.filter { it.id in excludedChatIds }
                        if (excludedChats.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            excludedChats.forEach { chat ->
                                SelectedChatItemRow(
                                    chat = chat,
                                    onRemove = {
                                        excludedChatIds = excludedChatIds - chat.id
                                    }
                                )
                            }
                        }

                        // Subtitle hint
                        Text(
                            text = "Выберите чаты или типы чатов, которые не нужно показывать в этой папке.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Section 4: Color Palette (Цвет папки в списке чатов)
            item {
                Text(
                    text = "ЦВЕТ ПАПКИ В СПИСКЕ ЧАТОВ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FOLDER_COLORS.forEach { colorHex ->
                                val color = Color(android.graphics.Color.parseColor(colorHex))
                                val isSelected = selectedColorHex.equals(colorHex, ignoreCase = true)

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { selectedColorHex = colorHex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Выбрано",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "Выберите цвет тега для чатов из этой папки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 5: Share Folder (Поделиться папкой)
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showShareLinkDialog = true }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Создать ссылку-приглашение",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Вы можете поделиться некоторыми группами и каналами из этой папки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Section 6: Delete Folder Button (if editing existing folder)
            if (existingFolder != null) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDeleteConfirmDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Удалить папку",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Emoji Picker Dialog
    if (showEmojiDialog) {
        AlertDialog(
            onDismissRequest = { showEmojiDialog = false },
            title = { Text("Выберите иконку") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        FOLDER_EMOJIS.take(7).forEach { emo ->
                            Text(
                                text = emo,
                                fontSize = 28.sp,
                                modifier = Modifier
                                    .clickable {
                                        selectedEmoji = emo
                                        showEmojiDialog = false
                                    }
                                    .padding(6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        FOLDER_EMOJIS.drop(7).forEach { emo ->
                            Text(
                                text = emo,
                                fontSize = 28.sp,
                                modifier = Modifier
                                    .clickable {
                                        selectedEmoji = emo
                                        showEmojiDialog = false
                                    }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedEmoji = ""
                    showEmojiDialog = false
                }) {
                    Text("Без иконки")
                }
            }
        )
    }

    // Share Link Dialog
    if (showShareLinkDialog) {
        val inviteLink = "https://kuotex.app/addlist/${existingFolder?.id ?: "new_folder"}"
        AlertDialog(
            onDismissRequest = { showShareLinkDialog = false },
            title = { Text("Ссылка-приглашение") },
            text = {
                Column {
                    Text(
                        text = "Любой пользователь с этой ссылкой сможет добавить чаты из этой папки к себе.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = inviteLink,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("KuoteX Folder Link", inviteLink)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Ссылка скопирована в буфер обмена", Toast.LENGTH_SHORT).show()
                        showShareLinkDialog = false
                    }
                ) {
                    Text("Копировать ссылку")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareLinkDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && existingFolder != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Удалить папку?") },
            text = { Text("Папка «${existingFolder.name}» будет удалена. Чаты останутся в общем списке.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChatFolder(existingFolder.id)
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "Папка удалена", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Chat Selector Bottom Sheets
    if (showAddChatsSheet) {
        ChatSelectionBottomSheet(
            title = "Добавить чаты",
            chats = allChats,
            selectedChatIds = includedChatIds,
            onDone = { newSelection ->
                includedChatIds = newSelection
                showAddChatsSheet = false
            },
            onDismiss = { showAddChatsSheet = false }
        )
    }

    if (showAddExceptionsSheet) {
        ChatSelectionBottomSheet(
            title = "Добавить исключения",
            chats = allChats,
            selectedChatIds = excludedChatIds,
            onDone = { newSelection ->
                excludedChatIds = newSelection
                showAddExceptionsSheet = false
            },
            onDismiss = { showAddExceptionsSheet = false }
        )
    }
}

@Composable
private fun SelectedChatItemRow(
    chat: Chat,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chatColor = when {
            chat.isChannel -> Color(0xFF2196F3)
            chat.isGroup -> Color(0xFF4CAF50)
            chat.isBot -> Color(0xFF00BCD4)
            else -> Color(0xFF9C27B0)
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(chatColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                chat.title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = chatColor
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                chat.isChannel -> "Канал"
                chat.isGroup -> "Группа"
                chat.isBot -> "Бот"
                else -> "Личный чат"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Bottom sheet to pick chats with search and multi-select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSelectionBottomSheet(
    title: String,
    chats: List<Chat>,
    selectedChatIds: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var currentSelection by remember { mutableStateOf(selectedChatIds) }

    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else chats.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { onDone(currentSelection) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("Готово (${currentSelection.size})")
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск чатов...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Chat List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(filteredChats, key = { it.id }) { chat ->
                    val isSelected = chat.id in currentSelection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentSelection = if (isSelected) {
                                    currentSelection - chat.id
                                } else {
                                    currentSelection + chat.id
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                currentSelection = if (checked == true) {
                                    currentSelection + chat.id
                                } else {
                                    currentSelection - chat.id
                                }
                            }
                        )

                        Spacer(Modifier.width(12.dp))

                        val chatColor = when {
                            chat.isChannel -> Color(0xFF2196F3)
                            chat.isGroup -> Color(0xFF4CAF50)
                            chat.isBot -> Color(0xFF00BCD4)
                            else -> Color(0xFF9C27B0)
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(chatColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                chat.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = chatColor
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = when {
                                chat.isChannel -> "Канал"
                                chat.isGroup -> "Группа"
                                chat.isBot -> "Бот"
                                else -> "Личный чат"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
