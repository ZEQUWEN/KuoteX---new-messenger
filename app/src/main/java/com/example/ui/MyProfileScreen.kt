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

    val actualAvatarCount = userAvatarsList.size
    val virtualAvatarCount = if (actualAvatarCount > 1) 100_000 else 1
    val initialVirtualPage = if (actualAvatarCount > 1) {
        val mid = virtualAvatarCount / 2
        mid - (mid % actualAvatarCount)
    } else 0
    val avatarPagerState = rememberPagerState(
        initialPage = initialVirtualPage,
        pageCount = { virtualAvatarCount }
    )
    val currentAvatarIndex = if (actualAvatarCount > 0) {
        (avatarPagerState.currentPage % actualAvatarCount).let { if (it < 0) it + actualAvatarCount else it }
    } else 0

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = AvatarStorageManager.saveAvatarFromUri(
                    context = context,
                    sourceUri = uri,
                    entityType = EntityType.ACCOUNT,
                    entityId = activeAccount.id
                )
                AvatarStorageManager.addUserAvatar(context, activeAccount.id, savedPath)
                val updatedList = AvatarStorageManager.getUserAvatarList(context, activeAccount.id, savedPath)
                userAvatarsList = updatedList
                viewModel.updateProfile(
                    id = activeAccount.id,
                    username = activeAccount.username,
                    displayName = activeAccount.displayName,
                    bio = activeAccount.bio,
                    profilePicUrl = savedPath,
                    customStatus = activeAccount.customStatus,
                    phoneNumber = activeAccount.phoneNumber,
                    dateOfBirth = activeAccount.dateOfBirth,
                    socialMedia = activeAccount.socialMedia
                )
                if (updatedList.size > 1) {
                    val mid = virtualAvatarCount / 2
                    avatarPagerState.scrollToPage(mid - (mid % updatedList.size))
                }
                Toast.makeText(context, "Фотография профиля успешно добавлена!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (overscrollAnimatable.value > 0f && available.y < 0) {
                    val consumed = available.y.coerceAtLeast(-overscrollAnimatable.value)
                    scope.launch {
                        overscrollAnimatable.snapTo(overscrollAnimatable.value + consumed)
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
                    val damping = (1f - (overscrollAnimatable.value / 600f)).coerceIn(0.18f, 0.55f)
                    val delta = available.y * damping
                    scope.launch {
                        overscrollAnimatable.snapTo(overscrollAnimatable.value + delta)
                    }
                    return Offset(0f, available.y)
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

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .nestedScroll(nestedScrollConnection)
    ) {
        // --- Header Image and Title ---
        val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
        val firstItemIndex = listState.firstVisibleItemIndex
        val actualScroll = if (firstItemIndex == 0) scrollOffset else headerHeightPx
        val collapseFraction = (actualScroll / headerHeightPx).coerceIn(0f, 1f)
        val avatarZoomScale = 1f + (overscrollAnimatable.value / 650f).coerceIn(0f, 0.40f)
        val dynamicHeaderHeight = headerHeightDp + (overscrollAnimatable.value / density.density).dp

        // Framed Telegram-Style Avatar Carousel Header with Spring Zoom Physics on Pull
        Surface(
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            border = BorderStroke(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f),
                        Color(0xFF222A3B).copy(alpha = 0.65f)
                    )
                )
            ),
            color = Color(0xFF13161F),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(dynamicHeaderHeight)
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = avatarPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val actualPage = (page % actualAvatarCount).let { if (it < 0) it + actualAvatarCount else it }
                    Box(modifier = Modifier.fillMaxSize()) {
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
                        // Gradient overlay at bottom of image
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.20f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                            MaterialTheme.colorScheme.background
                                        ),
                                        startY = headerHeightPx * 0.42f
                                    )
                                )
                        )
                    }
                }

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

                // Photo count badge (e.g. 1 / 3)
                if (actualAvatarCount > 1) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 18.dp)
                    ) {
                        Text(
                            text = "${currentAvatarIndex + 1} / $actualAvatarCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Top Drag Handle Pill indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 22.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                )

                // Touch tap zones: left 28% -> previous photo (circular loop), center 44% -> full viewer, right 28% -> next photo (circular loop)
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(0.28f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                scope.launch {
                                    avatarPagerState.animateScrollToPage(avatarPagerState.currentPage - 1)
                                }
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                showAvatarViewer = true
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.28f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                scope.launch {
                                    avatarPagerState.animateScrollToPage(avatarPagerState.currentPage + 1)
                                }
                            }
                    )
                }
            }
        }

        // --- Main Content ---
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = overscrollAnimatable.value * 0.85f
                }
        ) {
            item {
                Spacer(modifier = Modifier.height(headerHeightDp - 85.dp))
            }

            // Pinned Full-Width Liquid Glass Capsule for Nickname & Status
            // Stretched from the left to right across the avatar
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color(0xFF1E2330).copy(alpha = 0.84f),
                        border = BorderStroke(
                            width = 1.3.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.14f),
                                    Color(0xFF3B445B).copy(alpha = 0.30f)
                                )
                            )
                        ),
                        shadowElevation = 10.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF384055).copy(alpha = 0.88f),
                                            Color(0xFF1E2332).copy(alpha = 0.94f),
                                            Color(0xFF121522).copy(alpha = 0.97f)
                                        )
                                    )
                                )
                        ) {
                            // Top liquid glass sheen reflection highlight
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.28f),
                                                Color.White.copy(alpha = 0.06f),
                                                Color.Transparent
                                            ),
                                            startY = 0f,
                                            endY = 40f
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Clean Nickname (Display Name) without blue icon module
                                Text(
                                    text = activeAccount.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    letterSpacing = 0.3.sp
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
                                            .padding(horizontal = 10.dp, vertical = 2.dp)
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(Color(0xFF4ADE80), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (activeAccount.customStatus.isNotBlank()) activeAccount.customStatus else "в сети",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF4ADE80),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val isLive = viewModel.isUserStreaming(activeAccount.id)
            val activeStream = viewModel.getActiveStream(activeAccount.id)

            if (isLive) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { navController.navigate("broadcast") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE91E63).copy(alpha = 0.15f)),
                        border = BorderStroke(1.5.dp, Color(0xFFE91E63))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(0xFFE91E63), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Вы ведете прямую трансляцию",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE91E63)
                                )
                                Text(
                                    text = "Зрителей: ${activeStream?.viewerCount ?: 1} • Нажмите для перехода",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            // Action Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileActionButton(
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
                    ProfileActionButton(
                        icon = Icons.Filled.Edit,
                        text = "Изменить",
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("settings/profile") }
                    )
                    ProfileActionButton(
                        icon = Icons.Filled.Settings,
                        text = "Настройки",
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("settings") }
                    )
                }
            }

            // Pinned Exclusive Gifts in Profile Header (Phase 4)
            item {
                com.example.ui.gifts.PinnedGiftsHeader(
                    gifts = displayedPinnedGifts,
                    onGiftClick = { gift ->
                        selectedGiftForDetail = gift
                    },
                    onAddGiftClick = {
                        android.widget.Toast.makeText(context, "Открытие каталога подарков KuoteX 🎁", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Info Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        InfoItem(
                            title = activeAccount.phoneNumber.ifBlank { "+7 (922) 669-26-82" },
                            subtitle = "Телефон",
                            onClick = { clipboardManager.setText(AnnotatedString("+79226692682")) },
                            menuItems = { closeMenu ->
                                DropdownMenuItem(text = { Text("Копировать") }, onClick = { clipboardManager.setText(AnnotatedString("+79226692682")); closeMenu() })
                                DropdownMenuItem(text = { Text("Изменить номер") }, onClick = { showChangeNumberDialog = true; closeMenu() })
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        InfoItem(
                            title = activeAccount.bio.takeIf { it.isNotBlank() } ?: "✨Занимаюсь дизайном карточек товаров и вайбкодингом, это моё хобби✨",
                            subtitle = "О себе",
                            onClick = { showEditBioDialog = true },
                            menuItems = { closeMenu ->
                                DropdownMenuItem(text = { Text("Копировать") }, onClick = { clipboardManager.setText(AnnotatedString(activeAccount.bio)); closeMenu() })
                                DropdownMenuItem(text = { Text("Изменить") }, onClick = { showEditBioDialog = true; closeMenu() })
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        InfoItem(
                            title = if (activeAccount.username.startsWith("@")) activeAccount.username else "@${activeAccount.username}",
                            subtitle = "Имя пользователя",
                            onClick = { },
                            menuItems = { closeMenu ->
                                DropdownMenuItem(text = { Text("Копировать") }, onClick = { clipboardManager.setText(AnnotatedString(activeAccount.username)); closeMenu() })
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
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

            // Tabs
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Публикации") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Архив публикаций") }
                    )
                }
            }
            
            // Publications Empty State
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, bottom = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Публикаций пока нет...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Публикуйте фотографии и видео в\nсвоём профиле",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAddPublicationSheet = true },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить")
                    }
                }
            }
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
