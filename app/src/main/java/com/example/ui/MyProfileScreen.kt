package com.example.ui
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.widget.Toast
import com.example.data.AvatarStorageManager
import com.example.data.EntityType
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.navigation.AppDestinations

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MyProfileScreen(viewModel: AppViewModel, navController: NavController) {
    val activeAccount = LocalActiveAccount.current ?: return
    val isQrSnowflakesEnabled by viewModel.isQrSnowflakesEnabled.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val headerHeightDp = 380.dp
    val headerHeightPx = with(density) { headerHeightDp.toPx() }
    
    val profileSpringSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }
    val overscrollAnimatable = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    var userAvatarsList by remember(activeAccount.id, activeAccount.profilePicUrl) {
        mutableStateOf(
            AvatarStorageManager.getUserAvatarList(context, activeAccount.id, activeAccount.profilePicUrl)
        )
    }

    val actualAvatarCount = userAvatarsList.size.coerceAtLeast(1)
    val avatarPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { actualAvatarCount }
    )
    val currentAvatarIndex = avatarPagerState.currentPage.coerceIn(0, actualAvatarCount - 1)

    var isUploadingAvatars by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf("") }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isUploadingAvatars = true
                uploadStatusText = "Загрузка фото в Firebase Storage..."
                try {
                    val uploadedUrls = AvatarStorageManager.uploadAvatarsToFirebase(
                        context = context,
                        userId = activeAccount.id,
                        sourceUris = uris,
                        onProgress = { current, total ->
                            uploadStatusText = "Загрузка в Firebase Storage: $current/$total"
                        }
                    )
                    val updatedList = AvatarStorageManager.getUserAvatarList(
                        context = context, 
                        userId = activeAccount.id, 
                        currentAvatar = uploadedUrls.firstOrNull()
                    )
                    userAvatarsList = updatedList
                    val primaryAvatar = uploadedUrls.firstOrNull() ?: activeAccount.profilePicUrl
                    viewModel.updateProfile(
                        id = activeAccount.id,
                        username = activeAccount.username,
                        displayName = activeAccount.displayName,
                        bio = activeAccount.bio,
                        profilePicUrl = primaryAvatar,
                        customStatus = activeAccount.customStatus,
                        phoneNumber = activeAccount.phoneNumber,
                        dateOfBirth = activeAccount.dateOfBirth,
                        socialMedia = activeAccount.socialMedia
                    )
                    if (updatedList.isNotEmpty()) {
                        avatarPagerState.animateScrollToPage(0)
                    }
                    Toast.makeText(
                        context,
                        if (uris.size == 1) "Фото успешно загружено в Firebase Storage!"
                        else "Загружено ${uris.size} фото в Firebase Storage!",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingAvatars = false
                    uploadStatusText = ""
                }
            }
        }
    }
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (overscrollAnimatable.value > 0f && available.y < 0) {
                    val consumed = available.y.coerceAtLeast(-overscrollAnimatable.value)
                    scope.launch {
                        overscrollAnimatable.snapTo((overscrollAnimatable.value + consumed).coerceAtLeast(0f))
                    }
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
                    // Strict limit on pull-down overscroll distance (max 120f)
                    val maxOverscroll = 120f
                    val current = overscrollAnimatable.value
                    if (current < maxOverscroll) {
                        val damping = (1f - (current / maxOverscroll)).coerceIn(0.12f, 0.40f)
                        val delta = available.y * damping
                        val next = (current + delta).coerceIn(0f, maxOverscroll)
                        scope.launch {
                            overscrollAnimatable.snapTo(next)
                        }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (overscrollAnimatable.value > 0f) {
                    scope.launch {
                        overscrollAnimatable.animateTo(0f, profileSpringSpec)
                    }
                }
                return Velocity.Zero
            }
        }
    }
    
    var showEditDateDialog by remember { mutableStateOf(false) }
    var showChangeNumberDialog by remember { mutableStateOf(false) }
    var showEditBioDialog by remember { mutableStateOf(false) }
    var showAvatarViewer by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showAddPublicationSheet by remember { mutableStateOf(false) }

    val pinnedGiftsMap by com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.pinnedGiftsMap.collectAsState()
    val catalogGifts by com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.catalogGifts.collectAsState()

    // Initialize gifts for current account if not already in ecosystem state
    LaunchedEffect(activeAccount.id) {
        if (!pinnedGiftsMap.containsKey(activeAccount.id) || pinnedGiftsMap[activeAccount.id].isNullOrEmpty()) {
            val sampleDocs = com.example.ui.gifts.PinnedGift.samplePinnedGifts().map { it.toUserGiftDoc(activeAccount.id) }
            com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.initializeUserGiftsIfEmpty(activeAccount.id, sampleDocs)
        }
    }

    val userPinnedDocs = pinnedGiftsMap[activeAccount.id] ?: emptyList()
    val displayedPinnedGifts = remember(userPinnedDocs, catalogGifts) {
        if (userPinnedDocs.isNotEmpty()) {
            userPinnedDocs.map { doc ->
                val catalog = catalogGifts.find { it.catalogGiftId == doc.catalogGiftId }
                com.example.ui.gifts.PinnedGift.fromUserGift(doc, catalog)
            }
        } else {
            com.example.ui.gifts.PinnedGift.samplePinnedGifts()
        }
    }
    var selectedGiftForDetail by remember { mutableStateOf<com.example.ui.gifts.PinnedGift?>(null) }
    
    var selectedTab by remember { mutableStateOf(0) }

    if (showAvatarViewer) {
        AvatarViewerDialog(
            avatars = userAvatarsList,
            initialPage = currentAvatarIndex,
            onDismiss = { showAvatarViewer = false }
        )
    }

    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .allowHardware(false)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .nestedScroll(nestedScrollConnection)
    ) {
        val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
        val firstItemIndex = listState.firstVisibleItemIndex
        val actualScroll = if (firstItemIndex == 0) scrollOffset else headerHeightPx
        val collapseFraction = (actualScroll / headerHeightPx).coerceIn(0f, 1f)
        val avatarZoomScale = (1f + (overscrollAnimatable.value / 600f)).coerceIn(1.0f, 1.15f)
        val dynamicHeaderHeight = headerHeightDp + (overscrollAnimatable.value / density.density).dp.coerceIn(0.dp, 40.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Item 0: Framed Telegram-Style Avatar Carousel Header
            item {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f)
                    ),
                    color = Color(0xFF13151B),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dynamicHeaderHeight)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = avatarPagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = actualAvatarCount > 1
                        ) { page ->
                            val actualPage = page.coerceIn(0, userAvatarsList.lastIndex)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        showAvatarViewer = true
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .allowHardware(false)
                                        .data(userAvatarsList[actualPage])
                                        .crossfade(true)
                                        .build(),
                                    imageLoader = imageLoader,
                                    contentDescription = "Avatar $actualPage",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = avatarZoomScale
                                            scaleY = avatarZoomScale
                                            transformOrigin = TransformOrigin(0.5f, 0.35f)
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // Gradient overlay at bottom of photo for text & buttons readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.40f),
                                            Color(0xFF0F1014).copy(alpha = 0.88f),
                                            Color(0xFF0F1014)
                                        ),
                                        startY = 0f,
                                        endY = with(density) { dynamicHeaderHeight.toPx() }
                                    )
                                )
                        )

                        // Top Segmented Dash Indicators for Profile Avatars (Telegram style)
                        if (actualAvatarCount > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(top = 10.dp, start = 16.dp, end = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(actualAvatarCount) { index ->
                                    val isActive = index == currentAvatarIndex
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(2.5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isActive) Color.White else Color.White.copy(alpha = 0.35f)
                                            )
                                    )
                                }
                            }
                        }

                        // Firebase Storage Uploading Banner Indicator
                        AnimatedVisibility(
                            visible = isUploadingAvatars,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 44.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF673AB7).copy(alpha = 0.95f),
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uploadStatusText.ifBlank { "Загрузка в Firebase Storage..." },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Bottom section on photo: Name, Status, and the 3 Action Buttons (matching Screenshot 1)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = activeAccount.displayName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 23.sp
                            )
                            
                            val isLive = viewModel.isUserStreaming(activeAccount.id)
                            val activeStream = viewModel.getActiveStream(activeAccount.id)

                            if (isLive) {
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFFE91E63), RoundedCornerShape(percent = 50))
                                        .clickable { navController.navigate("broadcast") }
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "🔴 В ЭФИРЕ • LIVE (👁 ${activeStream?.viewerCount ?: 1})",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (activeAccount.customStatus.isNotBlank()) activeAccount.customStatus else "в сети",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            // 3 Action Buttons directly on the photo header (Screenshot 1)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TelegramProfileButton(
                                    icon = Icons.Filled.AddAPhoto,
                                    text = "Выбрать фото",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                )
                                TelegramProfileButton(
                                    icon = Icons.Filled.Edit,
                                    text = "Изменить",
                                    modifier = Modifier.weight(1f),
                                    onClick = { navController.navigate("settings/profile") }
                                )
                                TelegramProfileButton(
                                    icon = Icons.Filled.Settings,
                                    text = "Настройки",
                                    modifier = Modifier.weight(1f),
                                    onClick = { navController.navigate("settings") }
                                )
                            }
                        }
                    }
                }
            }

            // Distinct visual separation between Avatar Gallery and User Information
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Pinned Exclusive Gifts in Profile Section
            item {
                com.example.ui.gifts.PinnedGiftsHeader(
                    gifts = displayedPinnedGifts,
                    onGiftClick = { gift ->
                        selectedGiftForDetail = gift
                    },
                    onAddGiftClick = {
                        navController.navigate("gifts_marketplace?userId=${activeAccount.id}&userName=${activeAccount.displayName}")
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(14.dp))
            }

            // User Information Section (Solid, Opaque, Elevated Card with distinct border and padding)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D23)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        InfoItem(
                            title = activeAccount.phoneNumber.ifBlank { "+7 (922) 669-26-82" },
                            subtitle = "Телефон",
                            onClick = { clipboardManager.setText(AnnotatedString("+79226692682")) },
                            menuItems = { closeMenu ->
                                DropdownMenuItem(text = { Text("Копировать") }, onClick = { clipboardManager.setText(AnnotatedString("+79226692682")); closeMenu() })
                                DropdownMenuItem(text = { Text("Изменить номер") }, onClick = { showChangeNumberDialog = true; closeMenu() })
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.07f))
                        InfoItem(
                            title = activeAccount.bio.takeIf { it.isNotBlank() } ?: "✨Занимаюсь дизайном карточек товаров и вайбкодингом, это моё хобби✨",
                            subtitle = "О себе",
                            onClick = { showEditBioDialog = true },
                            menuItems = { closeMenu ->
                                DropdownMenuItem(text = { Text("Копировать") }, onClick = { clipboardManager.setText(AnnotatedString(activeAccount.bio)); closeMenu() })
                                DropdownMenuItem(text = { Text("Изменить") }, onClick = { showEditBioDialog = true; closeMenu() })
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.07f))
                        InfoItem(
                            title = if (activeAccount.username.startsWith("@")) activeAccount.username else "@${activeAccount.username}",
                            subtitle = "Имя пользователя",
                            onClick = { },
                            menuItems = { closeMenu ->
                                DropdownMenuItem(text = { Text("Копировать") }, onClick = { clipboardManager.setText(AnnotatedString(activeAccount.username)); closeMenu() })
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.07f))
                        InfoItem(
                            title = formatBirthDateWithAge(activeAccount.dateOfBirth).takeIf { activeAccount.dateOfBirth.isNotBlank() } ?: "Укажите дату рождения",
                            subtitle = "День рождения",
                            onClick = { showEditDateDialog = true },
                            menuItems = { closeMenu ->
                                if (activeAccount.dateOfBirth.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Копировать") },
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(activeAccount.dateOfBirth))
                                            closeMenu()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Изменить дату") },
                                    onClick = {
                                        showEditDateDialog = true
                                        closeMenu()
                                    }
                                )
                                if (activeAccount.dateOfBirth.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Удалить", color = Color.Red) },
                                        onClick = {
                                            viewModel.updateProfile(
                                                id = activeAccount.id,
                                                username = activeAccount.username,
                                                displayName = activeAccount.displayName,
                                                bio = activeAccount.bio,
                                                profilePicUrl = activeAccount.profilePicUrl,
                                                customStatus = activeAccount.customStatus,
                                                phoneNumber = activeAccount.phoneNumber,
                                                dateOfBirth = "",
                                                socialMedia = activeAccount.socialMedia
                                            )
                                            closeMenu()
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Tabs (matching Screenshot 4: Pill-styled tabs)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        onClick = { selectedTab = 0 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedTab == 0) Color(0xFF2C223C) else Color.Transparent
                    ) {
                        Text(
                            text = "Публикации",
                            color = if (selectedTab == 0) Color(0xFFD8B4FE) else Color.Gray,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = { selectedTab = 1 },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selectedTab == 1) Color(0xFF2C223C) else Color.Transparent
                    ) {
                        Text(
                            text = "Архив публикаций",
                            color = if (selectedTab == 1) Color(0xFFD8B4FE) else Color.Gray,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }
            
            // Publications Grid (matching Screenshot 4)
            item {
                val samplePubs = listOf(
                    "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=400" to "👁 3",
                    "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=400" to "▶ 0:57 👁 1",
                    "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=400" to "👁 3"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        samplePubs.forEach { (imgUrl, viewsText) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E2026))
                            ) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = "Publication",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = viewsText,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(110.dp))
                }
            }
        }

        // Floating Pill Button "[ 📷 Добавить ]" matching Screenshot 4
        Button(
            onClick = { showAddPublicationSheet = true },
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB57EDC)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .height(46.dp)
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Добавить", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // --- Top App Bar ---
        val showTopBar = collapseFraction > 0.8f
        AnimatedVisibility(
            visible = showTopBar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val imageUrl = activeAccount.profilePicUrl.takeIf { it.isNotEmpty() } ?: "https://picsum.photos/seed/${activeAccount.id}/100"
                        AsyncImage(
                            model = ImageRequest.Builder(context).allowHardware(false).data(imageUrl).build(),
                            imageLoader = imageLoader,
                            contentDescription = "Mini Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(activeAccount.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("в сети", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Filled.QrCode, contentDescription = "QR Code")
                    }
                    var showMoreMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Изменить профиль") },
                                onClick = {
                                    showMoreMenu = false
                                    navController.navigate("settings/profile")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Изменить цвет профиля") },
                                onClick = { showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Изменить имя") },
                                onClick = { showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Копировать ссылку") },
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("tg://resolve?domain=${activeAccount.username}"))
                                    showMoreMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
        
        // Back button and top-right actions always visible if top bar is hidden
        if (!showTopBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .padding(top = 8.dp, start = 8.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp, end = 8.dp)
                        .align(Alignment.TopEnd)
                ) {
                    IconButton(
                        onClick = { showQrDialog = true },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Filled.QrCode, contentDescription = "QR Code", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    var showMoreMenu2 by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showMoreMenu2 = true },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu2,
                            onDismissRequest = { showMoreMenu2 = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Изменить профиль") },
                                onClick = {
                                    showMoreMenu2 = false
                                    navController.navigate("settings/profile")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Изменить цвет профиля") },
                                onClick = { showMoreMenu2 = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Изменить имя") },
                                onClick = { showMoreMenu2 = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Копировать ссылку") },
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("tg://resolve?domain=${activeAccount.username}"))
                                    showMoreMenu2 = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Dialogs
        if (selectedGiftForDetail != null) {
            com.example.ui.gifts.PinnedGiftDetailBottomSheet(
                gift = selectedGiftForDetail,
                onDismiss = { selectedGiftForDetail = null },
                onUpgradeClick = { gift ->
                    scope.launch {
                        val result = com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.upgradeUserGiftAtomic(
                            userId = activeAccount.id,
                            userGiftId = gift.id,
                            upgradeCostStars = 50L
                        )
                        if (result.isSuccess) {
                            android.widget.Toast.makeText(context, "Уровень подарка повышен! ⭐", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Ошибка повышения: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        if (showAddPublicationSheet) {
            val pickMedia = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                showAddPublicationSheet = false
            }
            val takePicture = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
            ) { bitmap ->
                showAddPublicationSheet = false
            }
            val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    takePicture.launch(null)
                } else {
                    // Permission denied
                    showAddPublicationSheet = false
                }
            }
            val broadcastPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[android.Manifest.permission.CAMERA] == true) {
                    showAddPublicationSheet = false
                    navController.navigate("broadcast")
                } else {
                    showAddPublicationSheet = false
                }
            }
            
            ModalBottomSheet(
                onDismissRequest = { showAddPublicationSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text(
                        text = "Создать",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Выбрать из галереи") },
                        leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    )
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Сделать фото") },
                        leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            )
                            if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                takePicture.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        }
                    )
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Записать видео") },
                        leadingContent = { Icon(Icons.Filled.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            showAddPublicationSheet = false
                        }
                    )
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Трансляция") },
                        leadingContent = { Icon(Icons.Filled.LiveTv, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            val cameraCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            )
                            val audioCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            )
                            if (cameraCheck == android.content.pm.PackageManager.PERMISSION_GRANTED && audioCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                showAddPublicationSheet = false
                                navController.navigate("broadcast")
                            } else {
                                broadcastPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.CAMERA,
                                        android.Manifest.permission.RECORD_AUDIO
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }

        if (showQrDialog) {
            ModalBottomSheet(
                onDismissRequest = { showQrDialog = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                    // Iridescent Gradient
                    val infiniteTransition = rememberInfiniteTransition()
                    val gradientOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(10000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE0C3FC).copy(alpha = 0.5f),
                                    Color(0xFF8EC5FC).copy(alpha = 0.5f),
                                    Color(0xFFE0C3FC).copy(alpha = 0.5f)
                                ),
                                start = Offset(gradientOffset, gradientOffset),
                                end = Offset(gradientOffset + 500f, gradientOffset + 500f)
                            )
                        )
                    )
                    
                    if (isQrSnowflakesEnabled) {
                        val snowflakes = remember { List(30) { com.example.ui.Snowflake() } }
                        var dt by remember { mutableStateOf(0f) }
                        var lastTime by remember { mutableStateOf(0L) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                androidx.compose.runtime.withFrameNanos { time ->
                                    if (lastTime != 0L) {
                                        dt = (time - lastTime) / 1_000_000_000f
                                    }
                                    lastTime = time
                                }
                            }
                        }
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val currentDt = dt
                            snowflakes.forEach { flake ->
                                flake.update(size.width, size.height, currentDt)
                                drawCircle(
                                    color = Color.White.copy(alpha = flake.alpha),
                                    center = Offset(flake.x, flake.y),
                                    radius = flake.radius
                                )
                            }
                        }
                    }
                    
                    Column(modifier = Modifier.fillMaxSize()) {

                var selectedThemeIndex by remember { mutableStateOf(0) }
                val themes = listOf(
                    Triple(Color.White, Color(0xFF1E88E5), Color(0xFFE3F2FD)), // Blue
                    Triple(Color(0xFF202020), Color(0xFFFF9800), Color(0xFF3E2723)), // Orange/Dark
                    Triple(Color(0xFF101010), Color(0xFFE91E63), Color(0xFF4A148C)), // Pink/Purple
                    Triple(Color(0xFF002200), Color(0xFF00E676), Color(0xFF1B5E20)), // Green
                    Triple(Color.White, Color(0xFF673AB7), Color(0xFFEDE7F6))  // Purple/Light
                )
                val currentTheme = themes[selectedThemeIndex]
                
                val qrBitmap = remember(activeAccount.username, currentTheme) {
                    com.example.utils.generateQrCode(
                        text = "tg://resolve?domain=${activeAccount.username}",
                        fgColor = android.graphics.Color.argb(
                            (currentTheme.second.alpha * 255).toInt(),
                            (currentTheme.second.red * 255).toInt(),
                            (currentTheme.second.green * 255).toInt(),
                            (currentTheme.second.blue * 255).toInt()
                        ),
                        bgColor = android.graphics.Color.argb(
                            (currentTheme.first.alpha * 255).toInt(),
                            (currentTheme.first.red * 255).toInt(),
                            (currentTheme.first.green * 255).toInt(),
                            (currentTheme.first.blue * 255).toInt()
                        )
                    )
                }
                var showScanner by remember { mutableStateOf(false) }

                androidx.compose.animation.AnimatedContent(
                    targetState = showScanner,
                    label = "qr_scanner_transition"
                ) { isScanner ->
                    if (isScanner) {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(550.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("Сканировать QR-код", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(32.dp))
                            val infiniteTransition = rememberInfiniteTransition()
                            val scanAnim by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 250f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(250.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.3f))
                                
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val y = scanAnim.dp.toPx()
                                    drawLine(
                                        color = androidx.compose.ui.graphics.Color(0xFF00E676),
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 4.dp.toPx()
                                    )
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, androidx.compose.ui.graphics.Color(0xFF00E676).copy(alpha = 0.3f)),
                                            startY = y - 40.dp.toPx(),
                                            endY = y
                                        ),
                                        topLeft = Offset(0f, y - 40.dp.toPx()),
                                        size = androidx.compose.ui.geometry.Size(size.width, 40.dp.toPx())
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = {
                                showScanner = false
                                showQrDialog = false
                                viewModel.startAddAccount()
                            }) {
                                Text("Эмуляция сканирования (Добавить аккаунт)")
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showScanner = false },
                                modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                            ) {
                                Text("Мой QR-код", fontSize = 16.sp)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // QR Code Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .aspectRatio(0.65f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(currentTheme.third),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (activeAccount.profilePicUrl.isNotEmpty()) {
                                        coil.compose.AsyncImage(
                                            model = activeAccount.profilePicUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp).clip(CircleShape).border(2.dp, currentTheme.first, CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).border(2.dp, currentTheme.first, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                activeAccount.displayName.take(1).uppercase(),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                style = MaterialTheme.typography.headlineMedium
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    qrBitmap?.let { bmp ->
                                        androidx.compose.foundation.Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier
                                                .size(200.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "@${activeAccount.username}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = currentTheme.second
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            // Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("QR-код", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                
                                IconButton(onClick = { showScanner = true }) {
                                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Theme selector
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(themes.size) { index ->
                                    val theme = themes[index]
                                    val isSelected = selectedThemeIndex == index
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp, 80.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(theme.third)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedThemeIndex = index },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.QrCode, 
                                            contentDescription = null, 
                                            tint = theme.second,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, "tg://resolve?domain=${activeAccount.username}")
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Поделиться профилем")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text("Поделиться", fontSize = 16.sp)
                            }
                            
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
            }
        }
        
        if (showChangeNumberDialog) {
            AlertDialog(
                onDismissRequest = { showChangeNumberDialog = false },
                title = { Text("Сменить номер") },
                text = { Text("Здесь Вы можете сменить номер телефона. Ваш аккаунт и все данные будут перенесены на новый номер.") },
                confirmButton = {
                    Button(onClick = { showChangeNumberDialog = false }) { Text("Сменить номер") }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeNumberDialog = false }) { Text("Отмена") }
                }
            )
        }
        if (showEditDateDialog) {
            BirthdayPickerDialog(
                initialDate = activeAccount.dateOfBirth,
                onDateSaved = { newDate ->
                    viewModel.updateProfile(
                        id = activeAccount.id,
                        username = activeAccount.username,
                        displayName = activeAccount.displayName,
                        bio = activeAccount.bio,
                        profilePicUrl = activeAccount.profilePicUrl,
                        customStatus = activeAccount.customStatus,
                        phoneNumber = activeAccount.phoneNumber,
                        dateOfBirth = newDate,
                        socialMedia = activeAccount.socialMedia
                    )
                    showEditDateDialog = false
                    Toast.makeText(context, "Дата рождения сохранена: $newDate", Toast.LENGTH_SHORT).show()
                },
                onDateDeleted = {
                    viewModel.updateProfile(
                        id = activeAccount.id,
                        username = activeAccount.username,
                        displayName = activeAccount.displayName,
                        bio = activeAccount.bio,
                        profilePicUrl = activeAccount.profilePicUrl,
                        customStatus = activeAccount.customStatus,
                        phoneNumber = activeAccount.phoneNumber,
                        dateOfBirth = "",
                        socialMedia = activeAccount.socialMedia
                    )
                    showEditDateDialog = false
                    Toast.makeText(context, "Дата рождения удалена", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showEditDateDialog = false }
            )
        }
        if (showEditBioDialog) {
            var bioInput by remember { mutableStateOf(activeAccount.bio) }
            AlertDialog(
                onDismissRequest = { showEditBioDialog = false },
                title = { Text("О себе") },
                text = {
                    OutlinedTextField(
                        value = bioInput,
                        onValueChange = { bioInput = it },
                        label = { Text("Краткая информация") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateProfile(
                            activeAccount.id,
                            activeAccount.username,
                            activeAccount.displayName,
                            bioInput,
                            activeAccount.profilePicUrl,
                            activeAccount.customStatus
                        )
                        showEditBioDialog = false
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditBioDialog = false }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
fun TelegramProfileButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E2128).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ProfileActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    menuItems: @Composable (closeMenu: () -> Unit) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            menuItems { showMenu = false }
        }
    }
}
