package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.example.data.AvatarStorageManager
import com.example.data.EntityType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ecosystem.KuoteXEcosystemFirestoreManager
import com.example.data.ecosystem.KuoteXUserGiftDoc
import com.example.ui.Chat
import com.example.ui.gifts.CollectibleGift
import com.example.ui.gifts.CollectibleGiftDetailBottomSheet
import com.example.ui.gifts.PinnedCollectibleBadge
import com.example.ui.gifts.PinnedGift
import com.example.ui.gifts.PinnedGiftDetailBottomSheet
import com.example.ui.gifts.PinnedGiftsHeader
import kotlinx.coroutines.launch

/**
 * Full Telegram-Style ProfileScreen for user accounts in KuoteX.
 * Matches screenshots: Hero Header image with gradient, Quick Action Buttons (Чат, Звук, Звонок, Подарок),
 * Info Card (О себе, Имя пользователя, День рождения), "Добавить в контакты",
 * Tabs row ("Подарки 💕🧸🎁", "Медиа", "Ссылки"), Grid of received gifts with sender badges,
 * and bottom floating/pinned action button "Отправить подарок".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController,
    ecosystemManager: KuoteXEcosystemFirestoreManager = KuoteXEcosystemFirestoreManager
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId } ?: Chat(
        id = chatId,
        title = if (chatId == "round") "round #TYANKA #CUTIE" else "User $chatId",
        lastMessage = "",
        isGroup = false,
        isChannel = false,
        isBot = false
    )

    val userPresences by viewModel.userPresences.collectAsStateWithLifecycle()
    val presence = userPresences[chatId]

    val pinnedGiftsMap by ecosystemManager.pinnedGiftsMap.collectAsState()
    val userPinnedCollectibles by ecosystemManager.userPinnedCollectible.collectAsState()
    val collectibleMarketplace by ecosystemManager.collectibleMarketplaceGifts.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Active pinned collectible gift next to username (e.g. Nail Bracelet #2095)
    val pinnedCollectible = userPinnedCollectibles[chatId] ?: collectibleMarketplace.find { it.ownerName == chatId || it.ownerName == chat.title }

    // Initialize gifts for profile if not yet loaded in ecosystem state
    LaunchedEffect(chatId) {
        if (!pinnedGiftsMap.containsKey(chatId) || pinnedGiftsMap[chatId].isNullOrEmpty()) {
            ecosystemManager.initializeUserGiftsIfEmpty(chatId)
        }
    }

    // User's received gifts
    val userGifts = remember(pinnedGiftsMap, chatId) {
        val rawList = pinnedGiftsMap[chatId] ?: emptyList()
        if (rawList.isEmpty()) {
            // Default sample gifts matching Telegram screenshots
            listOf(
                PinnedGift(
                    id = "g_heart_1",
                    catalogGiftId = "gift_heart_box_008",
                    title = "Сердце с бантом",
                    senderName = "alex",
                    receiverId = chatId,
                    emojiIcon = "💝",
                    backdropColorHex = "#2E081E",
                    accentGlowHex = "#EC4899",
                    upgradeLevel = 2
                ),
                PinnedGift(
                    id = "g_bear_1",
                    catalogGiftId = "gift_teddy_bear_007",
                    title = "Плюшевый Мишка",
                    senderName = "Сестра.",
                    receiverId = chatId,
                    emojiIcon = "🧸",
                    backdropColorHex = "#261E14",
                    accentGlowHex = "#D97706",
                    upgradeLevel = 1
                ),
                PinnedGift(
                    id = "g_bear_2",
                    catalogGiftId = "gift_teddy_bear_007",
                    title = "Плюшевый Мишка",
                    senderName = "round_fan",
                    receiverId = chatId,
                    emojiIcon = "🧸",
                    backdropColorHex = "#261E14",
                    accentGlowHex = "#D97706",
                    upgradeLevel = 1
                ),
                PinnedGift(
                    id = "g_bear_3",
                    catalogGiftId = "gift_teddy_bear_007",
                    title = "Плюшевый Мишка",
                    senderName = "Аноним",
                    receiverId = chatId,
                    emojiIcon = "🧸",
                    backdropColorHex = "#261E14",
                    accentGlowHex = "#D97706",
                    upgradeLevel = 1
                ),
                PinnedGift(
                    id = "g_gold_1",
                    catalogGiftId = "gift_golden_present_009",
                    title = "Золотой Подарок",
                    senderName = "best_friend",
                    receiverId = chatId,
                    emojiIcon = "🎁",
                    backdropColorHex = "#2D2206",
                    accentGlowHex = "#F59E0B",
                    upgradeLevel = 3
                ),
                PinnedGift(
                    id = "g_dragon_1",
                    catalogGiftId = "gift_cyber_dragon_001",
                    title = "Cyber Dragon 2026",
                    senderName = "durov",
                    receiverId = chatId,
                    emojiIcon = "🐉",
                    backdropColorHex = "#1E1B4B",
                    accentGlowHex = "#8B5CF6",
                    upgradeLevel = 4
                )
            )
        } else {
            rawList.map { doc ->
                PinnedGift(
                    id = doc.userGiftId,
                    catalogGiftId = doc.catalogGiftId,
                    title = doc.cachedTitle,
                    senderName = doc.senderId,
                    receiverId = doc.receiverId,
                    upgradeLevel = doc.upgradeLevel,
                    backdropColorHex = doc.cachedColorHex,
                    emojiIcon = doc.cachedEmoji,
                    message = doc.message,
                    acquiredAt = doc.acquiredAt
                )
            }
        }
    }

    // Modal & Sheet state
    var selectedGiftDetail by remember { mutableStateOf<PinnedGift?>(null) }
    var selectedCollectibleDetail by remember { mutableStateOf<CollectibleGift?>(null) }
    var showRatingSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isContactAdded by remember { mutableStateOf(false) }

    // Tab state (0: Подарки, 1: Медиа, 2: Ссылки)
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedGiftFilter by remember { mutableStateOf("Все подарки") }

    // Hero Avatar URLs with persistent avatar storage
    val persistentAvatar = remember(chatId) {
        AvatarStorageManager.getAvatar(
            context,
            EntityType.ACCOUNT,
            chatId,
            "https://picsum.photos/seed/${chatId}_rem/800/800"
        )
    }
    val avatars = remember(chatId, persistentAvatar) {
        listOf(
            persistentAvatar,
            "https://picsum.photos/seed/${chatId}_1/800/800"
        )
    }

    val listState = rememberLazyListState()
    var overscrollOffset by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (overscrollOffset > 0f && available.y < 0) {
                    val consumed = available.y.coerceAtLeast(-overscrollOffset)
                    overscrollOffset += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    val damping = (1f - (overscrollOffset / 500f)).coerceIn(0.18f, 0.55f)
                    val delta = available.y * damping
                    overscrollOffset += delta
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (overscrollOffset > 0f) {
                    overscrollOffset = 0f
                }
                return Velocity.Zero
            }
        }
    }

    val animatedOverscroll by animateFloatAsState(
        targetValue = overscrollOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "profile_avatar_overscroll"
    )

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 6
        }
    }
    val atTopScale by animateFloatAsState(
        targetValue = if (isAtTop) 1.045f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "profile_avatar_top_scale"
    )
    val overscrollScale = (animatedOverscroll / 320f).coerceIn(0f, 0.32f)
    val totalAvatarScale = atTopScale + overscrollScale
    val avatarTranslationY = if (animatedOverscroll > 0f) animatedOverscroll * 0.45f else 0f

    Scaffold(
        containerColor = Color(0xFF000000),
        bottomBar = {
            // Floating "Отправить подарок" Button (exact Telegram screenshot #2 reference)
            Surface(
                color = Color(0xFF000000).copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            navController.navigate("gifts_marketplace?userId=$chatId&userName=${chat.title}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC084FC))
                    ) {
                        Text(text = "🎁", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Отправить подарок ${chat.title.split(" ").firstOrNull() ?: ""}".trim(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .padding(padding)
        ) {
            // 1. HERO HEADER IMAGE WITH OVERLAYS (matching Screenshot 1 & 3)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    // Profile Background Image with gentle scale-up animation at the top
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(avatars.first())
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = totalAvatarScale
                                scaleY = totalAvatarScale
                                transformOrigin = TransformOrigin(0.5f, 0.35f)
                                translationY = avatarTranslationY
                            },
                        contentScale = ContentScale.Crop
                    )

                    // Gradient Scrim from top and bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )

                    // Top Bar overlay with Back and 3-dots Menu
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Box {
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Опции",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // 3-dots popup menu (matching Screenshot 1)
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false },
                                modifier = Modifier.background(Color(0xFF282538))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Автоудаление", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.LightGray) },
                                    onClick = { showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Создать ярлык", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = Color.LightGray) },
                                    onClick = { showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Добавить контакт", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color.LightGray) },
                                    onClick = {
                                        isContactAdded = !isContactAdded
                                        showOptionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Отправить подарок", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Color(0xFFC084FC)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        scope.launch {
                                            kotlinx.coroutines.delay(30)
                                            navController.navigate("gifts_marketplace?userId=$chatId&userName=${chat.title}")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Начать секретный чат", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.LightGray) },
                                    onClick = { showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Запретить копирование", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Color.LightGray) },
                                    onClick = { showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Пожаловаться", color = Color(0xFFF87171)) },
                                    leadingIcon = { Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = Color(0xFFF87171)) },
                                    onClick = { showOptionsMenu = false }
                                )
                            }
                        }
                    }

                    // Bottom info on Hero Image (Name, Nickname, Pinned Collectible, Status) in a Rounded Glass Container
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Rounded Glass Effect Container around Name & Nickname
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF0D1220).copy(alpha = 0.68f),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.38f),
                                        Color.White.copy(alpha = 0.10f),
                                        Color(0xFF7C4DFF).copy(alpha = 0.28f)
                                    )
                                )
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0xFF20263C).copy(alpha = 0.65f),
                                                Color(0xFF101322).copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                // Display Name with Pinned Collectible Badge
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = chat.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                    // Pinned rare collectible badge next to username
                                    pinnedCollectible?.let { col ->
                                        PinnedCollectibleBadge(
                                            gift = col,
                                            onClick = { selectedCollectibleDetail = col }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Rating pill & Online Status
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { showRatingSheet = true },
                                        color = Color(0xFFC084FC).copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "👑", fontSize = 11.sp)
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "487 / 5K",
                                                color = Color(0xFFE9D5FF),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = "был(а) недавно",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Action Buttons Row (Чат, Звук, Звонок, Подарок)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ProfileActionButton(
                                icon = Icons.Filled.Message,
                                label = "Чат",
                                onClick = { navController.navigate("chat/$chatId") }
                            )
                            ProfileActionButton(
                                icon = if (isMuted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                                label = if (isMuted) "Вкл. звук" else "Звук",
                                onClick = { isMuted = !isMuted }
                            )
                            ProfileActionButton(
                                icon = Icons.Filled.Call,
                                label = "Звонок",
                                onClick = { navController.navigate("call/$chatId?isVideo=false") }
                            )
                            ProfileActionButton(
                                icon = Icons.Filled.ShoppingBag,
                                label = "Подарок",
                                onClick = {
                                    navController.navigate("gifts_marketplace?userId=$chatId&userName=${chat.title}")
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Audio Status Tag (matching Screenshot 3)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🎵", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "<unknown> - Armani West Piano Tiles",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 2. USER DETAILS CARD (О себе, Имя пользователя, День рождения)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14121E))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Bio / О себе
                        Column {
                            Text(
                                text = "I love programming; I’m also an animator.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Normal
                            )
                            Text(
                                text = "О себе",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        // Username / Имя пользователя
                        val username = if (chat.title.startsWith("@")) chat.title else "@roundPrisma3d"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(username))
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = username,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Имя пользователя",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            Icon(
                                Icons.Filled.QrCode,
                                contentDescription = "QR-код",
                                tint = Color.LightGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Birthday / День рождения
                        Column {
                            Text(
                                text = "25 сент.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "День рождения",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 3. "ДОБАВИТЬ В КОНТАКТЫ" Button
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { isContactAdded = !isContactAdded },
                    color = Color(0xFF14121E),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isContactAdded) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = if (isContactAdded) "Удалить из контактов" else "Добавить в контакты",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // 4. CONTENT TABS: "Подарки 💕🧸🎁", "Медиа", "Ссылки"
            item {
                Spacer(modifier = Modifier.height(10.dp))
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFFC084FC),
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selectedTab == 0) Color(0xFF4C1D95).copy(alpha = 0.5f) else Color.Transparent
                            ) {
                                Text(
                                    text = "Подарки 💕🧸🎁",
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 0) Color(0xFFE9D5FF) else Color.Gray,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Медиа", color = if (selectedTab == 1) Color.White else Color.Gray) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Ссылки", color = if (selectedTab == 2) Color.White else Color.Gray) }
                    )
                }
            }

            // Sub-filter pills: "Все подарки", "🧸 Сестра."
            if (selectedTab == 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selectedGiftFilter == "Все подарки") Color.White.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedGiftFilter = "Все подарки" }
                        ) {
                            Text(
                                text = "Все подарки",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selectedGiftFilter == "Сестра") Color.White.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedGiftFilter = "Сестра" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🧸", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Сестра.",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // 5. GIFTS 3-COLUMN GRID (matching Screenshot 2)
                item {
                    val filteredGifts = if (selectedGiftFilter == "Сестра") {
                        userGifts.filter { it.senderName.contains("Сестра") }
                    } else userGifts

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val chunkedGifts = filteredGifts.chunked(3)
                        chunkedGifts.forEach { rowGifts ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowGifts.forEach { gift ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        UserGiftProfileItem(
                                            gift = gift,
                                            onClick = { selectedGiftDetail = gift }
                                        )
                                    }
                                }
                                // Fill empty spaces in row if count < 3
                                repeat(3 - rowGifts.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                // Media / Links placeholder
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedTab == 1) "Нет общих медиафайлов" else "Нет общих ссылок",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Detail BottomSheet for clicked gift (allows upgrade & viewing sender)
    selectedGiftDetail?.let { gift ->
        PinnedGiftDetailBottomSheet(
            gift = gift,
            onDismiss = { selectedGiftDetail = null },
            onUpgradeClick = { g ->
                scope.launch {
                    val activeUser = ecosystemManager.currentUserState.value?.userId ?: "me"
                    ecosystemManager.upgradeUserGiftAtomic(activeUser, g.id)
                }
            }
        )
    }

    // Detail BottomSheet for clicked collectible gift (allows pinning next to nickname)
    selectedCollectibleDetail?.let { col ->
        CollectibleGiftDetailBottomSheet(
            gift = col,
            isOwner = true,
            isPinned = pinnedCollectible?.id == col.id,
            onPinToggle = { c ->
                val activeUser = ecosystemManager.currentUserState.value?.userId ?: "me"
                val newCol = if (pinnedCollectible?.id == c.id) null else c
                ecosystemManager.pinCollectibleToUsername(activeUser, newCol)
            },
            onDismiss = { selectedCollectibleDetail = null }
        )
    }

    // Telegram-style Rating BottomSheet (matching Screenshot 4)
    if (showRatingSheet) {
        RatingDetailsBottomSheet(
            userName = chat.title,
            currentPoints = 487,
            maxPoints = 5000,
            onDismiss = { showRatingSheet = false }
        )
    }
}

/**
 * Quick action rounded button on Hero image: Чат, Звук, Звонок, Подарок
 */
@Composable
private fun ProfileActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Single gift item in the 3-column profile grid with sender avatar badge in the top corner.
 * Matches Screenshot 2.
 */
@Composable
private fun UserGiftProfileItem(
    gift: PinnedGift,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = Color(0xFF181524),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Gift Graphic
            Text(
                text = gift.emojiIcon,
                fontSize = 46.sp,
                modifier = Modifier.align(Alignment.Center)
            )

            // Sender badge in top-left corner
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6))
                    .border(1.dp, Color.Black, CircleShape)
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = gift.senderName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Star level badge in bottom-right if upgraded
            if (gift.upgradeLevel > 1) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "⭐${gift.upgradeLevel}",
                        color = Color(0xFFFFD54F),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * BottomSheet displaying user activity rating (matching Screenshot 4)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingDetailsBottomSheet(
    userName: String,
    currentPoints: Int,
    maxPoints: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161424),
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rating pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFC084FC)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "👑", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$currentPoints / 5K",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Рейтинг",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Рейтинг отражает активность $userName в KuoteX. На него влияют:",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Criteria list (Подарки, Маркет и посты, Возвраты)
            RatingFactorRow(
                icon = "🎁",
                title = "Подарки от KuoteX",
                badgeText = "плюс",
                badgeColor = Color(0xFFC084FC),
                desc = "100% звёзд, потраченных на приобретение подарков от KuoteX."
            )

            Spacer(modifier = Modifier.height(12.dp))

            RatingFactorRow(
                icon = "👥",
                title = "Маркет и посты",
                badgeText = "плюс",
                badgeColor = Color(0xFFC084FC),
                desc = "20% звёзд, потраченных на покупку подарков у пользователей, платные сообщения и посты в каналах."
            )

            Spacer(modifier = Modifier.height(12.dp))

            RatingFactorRow(
                icon = "🔄",
                title = "Возвраты и обмены",
                badgeText = "минус",
                badgeColor = Color(0xFF64748B),
                desc = "Возвращённые звёзды, умноженные на 10, и 85% от стоимости подарков, обменянных на звёзды."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC084FC))
            ) {
                Text("👌 OK", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RatingFactorRow(
    icon: String,
    title: String,
    badgeText: String,
    badgeColor: Color,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 24.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = desc, fontSize = 12.sp, color = Color.LightGray)
            }
        }
    }
}
