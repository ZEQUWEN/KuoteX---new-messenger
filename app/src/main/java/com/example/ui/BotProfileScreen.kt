package com.example.ui
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.Chat
import com.example.ui.AppViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.botapi.BotRegistry
import com.example.ui.botapi.CustomBot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import com.example.data.AvatarStorageManager
import com.example.data.EntityType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlinx.coroutines.launch

import com.example.ui.bot.BotChatDropdownMenu
import com.example.ui.bot.BotReportBottomSheet
import com.example.ui.bot.BotPrivacyPolicyBottomSheet
import com.example.ui.bot.BotDeleteAndBlockDialog
import com.example.ui.bot.createBotShortcut
import com.example.ui.bot.shareBotLink
import com.example.ui.bot.saveBotMediaToGallery
import com.example.ui.channel.TelegramAutoDeleteWheelBottomSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BotProfileScreen(viewModel: AppViewModel, chatId: String, navController: NavController) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId } ?: Chat(
        id = chatId,
        title = "Bot $chatId",
        lastMessage = "",
        isGroup = false,
        isChannel = false,
        isBot = true
    )

    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showDeleteAndBlockDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showAutoDeleteDialog by remember { mutableStateOf(false) }
    var showFcmDialog by remember { mutableStateOf(false) }
    var currentAutoDeletePeriod by remember { mutableStateOf<String?>(null) }
    var fcmTitle by remember { mutableStateOf("") }
    var fcmMessage by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val botObj = BotRegistry.getBot(chatId)
    val customBot = botObj as? CustomBot
    val isBotFather = botObj is com.example.ui.botapi.BotFather

    val rawBotPic = customBot?.botPicUri?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/seed/${chat.id}/400"
    var currentBotPic by remember(chat.id, rawBotPic) {
        mutableStateOf(AvatarStorageManager.getAvatar(context, EntityType.BOT, chat.id, rawBotPic))
    }
    val botPic = currentBotPic
    val botPhotoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = AvatarStorageManager.saveAvatarFromUri(context, uri, EntityType.BOT, chat.id)
                currentBotPic = savedPath
                BotRegistry.updateBotPic(chat.id, savedPath)
                Toast.makeText(context, "Фото бота успешно сохранено!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val botUsername = "@" + (customBot?.id ?: chat.id)
    val botDescription = customBot?.description?.takeIf { it.isNotBlank() } ?: "No description provided."
    val botAbout = customBot?.about?.takeIf { it.isNotBlank() } ?: (if (isBotFather) "BotFather is the one bot to rule them all." else "")
    val botCommands = customBot?.customCommands ?: emptyList()
    
    val activeUsers by viewModel.getBotActiveUsersCount(chatId).collectAsStateWithLifecycle(initialValue = 0)
    val userCount = if (activeUsers > 0) "${java.text.NumberFormat.getInstance(java.util.Locale("ru", "RU")).format(activeUsers)} пользователей" else "..."


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        BotChatDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            botName = chat.title,
                            botUsername = botUsername,
                            botAvatarUrl = botPic,
                            isMuted = chat.isMuted,
                            currentAutoDeletePeriod = currentAutoDeletePeriod,
                            onToggleMute = { muted ->
                                viewModel.toggleMute(chatId, muted)
                                android.widget.Toast.makeText(
                                    context,
                                    if (muted) "Уведомления выключены" else "Уведомления включены",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onAutoDeleteSelected = { period ->
                                currentAutoDeletePeriod = period
                                android.widget.Toast.makeText(
                                    context,
                                    if (period != null) "Автоудаление установлено: $period" else "Автоудаление выключено",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onOpenAutoDeleteCustomWheel = {
                                showAutoDeleteDialog = true
                            },
                            onCreateShortcut = {
                                createBotShortcut(context, chat.id, chat.title, botUsername, botPic)
                            },
                            onShare = {
                                shareBotLink(context, botUsername, chat.title)
                            },
                            onPrivacyPolicy = {
                                showPrivacyPolicyDialog = true
                            },
                            onSaveToGallery = {
                                saveBotMediaToGallery(context, coroutineScope, botPic, chat.title)
                            },
                            onReport = {
                                showReportDialog = true
                            },
                            onDeleteAndBlock = {
                                showDeleteAndBlockDialog = true
                            },
                            onClearHistory = {
                                viewModel.clearHistory(chatId)
                                android.widget.Toast.makeText(context, "История сообщений очищена", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section
            item {
                val scrollOffset = if (listState.firstVisibleItemIndex == 0) {
                    listState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    1000f // effectively fully scrolled passed
                }
                
                val avatarScale = (1f - (scrollOffset / 300f)).coerceIn(0.6f, 1f)
                val avatarAlpha = (1f - (scrollOffset / 250f)).coerceIn(0f, 1f)
                
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                
                var avatarModifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        scaleX = avatarScale
                        scaleY = avatarScale
                        alpha = avatarAlpha
                    }
                    
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        avatarModifier = avatarModifier.sharedElement(
                            state = rememberSharedContentState(key = "avatar_${chat.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
                
                avatarModifier = avatarModifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)

                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = avatarModifier
                            .size(100.dp)
                            .clickable {
                                botPhotoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).allowHardware(false)
                                .data(currentBotPic)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Bot Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Camera edit badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2AABEE))
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .clickable {
                                botPhotoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "Сменить фото бота",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rounded Glass Container for Title & Nickname
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F1424).copy(alpha = 0.65f),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f),
                                Color(0xFF7C4DFF).copy(alpha = 0.25f)
                            )
                        )
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF1F263A).copy(alpha = 0.65f),
                                        Color(0xFF101322).copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var titleModifier: Modifier = Modifier
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    titleModifier = titleModifier.sharedElement(
                                        state = rememberSharedContentState(key = "title_${chat.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            }
                            
                            Text(
                                text = chat.title,
                                modifier = titleModifier,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (isBotFather) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        // Liquid Glass Chip for Nickname (@botUsername) with fully rounded sides
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = Color(0xFF242834).copy(alpha = 0.88f),
                            border = BorderStroke(
                                width = 1.1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.50f),
                                        Color.White.copy(alpha = 0.14f),
                                        Color(0xFF2AABEE).copy(alpha = 0.20f)
                                    )
                                )
                            ),
                            shadowElevation = 6.dp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF384055).copy(alpha = 0.85f),
                                                Color(0xFF1B202E).copy(alpha = 0.92f)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = botUsername,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = userCount,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Quick Actions Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BotActionItem(icon = Icons.Filled.ChatBubbleOutline, label = "Чат", onClick = { navController.navigate("chat/${chat.id}") })
                    AnimatedMuteActionItem(
                        isMuted = chat?.isMuted == true,
                        onClick = {
                            if (chat != null) {
                                viewModel.toggleMute(chat.id, !chat.isMuted)
                            }
                        }
                    )
                    BotActionItem(icon = Icons.Filled.Share, label = "Ссылка", onClick = { })
                    BotActionItem(icon = Icons.Filled.RemoveCircleOutline, label = "Стоп", onClick = { })
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Information Cards
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = botDescription,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "О себе",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = botUsername,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Имя пользователя",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showQrDialog = true }) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = "QR Code", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Shared Media Section
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Общие медиафайлы",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "24",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(8) { index ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context).allowHardware(false)
                                        .data("https://picsum.photos/seed/${chat.id}_media_$index/200")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Media item",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bot Commands Section
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Команды",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        val commands = listOf(
                            "/newbot" to "create a new bot",
                            "/mybots" to "edit your bots",
                            "/setname" to "change a bot's name",
                            "/setdescription" to "change bot description",
                            "/setabouttext" to "change bot about info",
                            "/setuserpic" to "change bot profile photo",
                            "/setcommands" to "change the list of commands",
                            "/deletebot" to "delete a bot",
                            "/token" to "get authorization token",
                            "/revoke" to "revoke bot access token",
                            "/setinline" to "toggle inline mode",
                            "/setinlinegeo" to "toggle inline location requests",
                            "/setinlinefeedback" to "change inline feedback settings",
                            "/setjoingroups" to "can your bot be added to groups?",
                            "/setprivacy" to "toggle privacy mode in groups",
                            "/myapps" to "edit your web apps",
                            "/newapp" to "create a new web app",
                            "/listapps" to "get a list of your web apps",
                            "/editapp" to "edit a web app",
                            "/deleteapp" to "delete an existing web app",
                            "/mygames" to "edit your games",
                            "/newgame" to "create a new game",
                            "/listgames" to "get a list of your games",
                            "/editgame" to "edit a game",
                            "/deletegame" to "delete an existing game"
                        )
                        
                        commands.forEach { (cmd, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate("chat/${chat.id}") }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = cmd,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(130.dp)
                                )
                                Text(
                                    text = "— $desc",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // FCM Broadcasting Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(
                        onClick = { showFcmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = "FCM", modifier = Modifier.padding(end = 8.dp))
                        Text("Отправить push-уведомление (FCM)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Bot WebApp Integration Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { /* Open App */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Открыть приложение", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Открывая это мини-приложение, Вы принимаете Условия использования мини-приложений.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Bottom Links & Media Trigger
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ссылки",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "12",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        BotReportBottomSheet(
            botName = chat.title,
            botUsername = botUsername,
            onDismissRequest = { showReportDialog = false },
            onSubmitReport = { reasonCategory, userComment, shouldBlock ->
                viewModel.submitContentReport(
                    messageId = "bot_${chat.id}_${System.currentTimeMillis()}",
                    chatId = chat.id,
                    senderId = chat.id,
                    senderDisplayName = chat.title,
                    senderUsername = botUsername,
                    messageText = "[Жалоба на профиль бота]: $botDescription",
                    reasonCategory = reasonCategory,
                    userComment = userComment
                )
                if (shouldBlock) {
                    viewModel.blockUser(chat.id)
                    viewModel.deleteChat(chat.id)
                    navController.popBackStack()
                }
                android.widget.Toast.makeText(context, "Жалоба отправлена модераторам", android.widget.Toast.LENGTH_SHORT).show()
                showReportDialog = false
            }
        )
    }

    if (showPrivacyPolicyDialog) {
        BotPrivacyPolicyBottomSheet(
            botName = chat.title,
            botUsername = botUsername,
            onDismissRequest = { showPrivacyPolicyDialog = false }
        )
    }

    if (showDeleteAndBlockDialog) {
        BotDeleteAndBlockDialog(
            botName = chat.title,
            botUsername = botUsername,
            onDismissRequest = { showDeleteAndBlockDialog = false },
            onConfirm = {
                viewModel.blockUser(chat.id)
                viewModel.deleteChat(chat.id)
                android.widget.Toast.makeText(context, "Бот ${chat.title} заблокирован", android.widget.Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        )
    }

    if (showAutoDeleteDialog) {
        TelegramAutoDeleteWheelBottomSheet(
            currentValue = currentAutoDeletePeriod,
            onDismissRequest = { showAutoDeleteDialog = false },
            onApply = { period ->
                currentAutoDeletePeriod = period
                android.widget.Toast.makeText(
                    context,
                    if (period != null) "Автоудаление установлено: $period" else "Автоудаление выключено",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showQrDialog) {
        var selectedThemeIndex by androidx.compose.runtime.remember { mutableStateOf(0) }
        val themes = listOf(
            listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
            listOf(Color(0xFFFF9933), Color(0xFF66B3FF)),
            listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
            listOf(Color(0xFFfc4a1a), Color(0xFFf7b733)),
            listOf(Color(0xFF1D976C), Color(0xFF93F9B9))
        )
        
        ModalBottomSheet(
            onDismissRequest = { showQrDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(themes[selectedThemeIndex])),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.size(240.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                coil.compose.AsyncImage(
                                    model = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=t.me/${botUsername.removePrefix("@")}",
                                    contentDescription = "Real QR Code",
                                    modifier = Modifier.size(160.dp),
                                    contentScale = ContentScale.Fit
                                )
                                coil.compose.AsyncImage(
                                    model = botPic,
                                    contentDescription = "Avatar inside QR",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color.White, CircleShape)
                                        .padding(4.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = botUsername.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
                
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp)
                ) {
                    items(themes.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(themes[index]))
                                .clickable { selectedThemeIndex = index }
                                .then(
                                    if (selectedThemeIndex == index) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    } else Modifier
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { showQrDialog = false },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Поделиться")
                    }
                    
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Button(
                        onClick = { 
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("https://t.me/${botUsername.removePrefix("@")}"))
                            android.widget.Toast.makeText(context, "Ссылка скопирована", android.widget.Toast.LENGTH_SHORT).show()
                            showQrDialog = false 
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Скопировать")
                    }
                }
            }
        }
    }
}


@Composable
fun BotActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(64.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AnimatedMuteActionItem(isMuted: Boolean, onClick: () -> Unit) {
    val transition = androidx.compose.animation.core.updateTransition(targetState = isMuted, label = "muteTransition")
    val lineProgress by transition.animateFloat(
        transitionSpec = { androidx.compose.animation.core.tween(durationMillis = 300) },
        label = "lineProgress"
    ) { muted ->
        if (muted) 1f else 0f
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(64.dp)
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.NotificationsNone,
                contentDescription = "Звук",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
            val lineColor = MaterialTheme.colorScheme.onSurface
            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidth = 2.dp.toPx()
                // Coordinates to draw a diagonal line across the bell
                val startOffset = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f)
                val endOffset = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.8f)
                
                if (lineProgress > 0f) {
                    val currentEnd = androidx.compose.ui.geometry.Offset(
                        startOffset.x + (endOffset.x - startOffset.x) * lineProgress,
                        startOffset.y + (endOffset.y - startOffset.y) * lineProgress
                    )
                    drawLine(
                        color = lineColor,
                        start = startOffset,
                        end = currentEnd,
                        strokeWidth = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Звук",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
