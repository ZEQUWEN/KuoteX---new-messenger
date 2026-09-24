package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import com.example.ui.botapi.BotRegistry
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.zIndex
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.navigation.NavDestination
import coil.compose.AsyncImage
import com.example.data.folders.ChatFolder
import com.example.data.folders.matches
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent
import android.widget.Toast

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.example.ui.channel.*

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyRow
import org.koin.androidx.compose.koinViewModel
import com.example.ui.navigation.AuthNavGraph
import com.example.ui.navigation.MainAppNavGraph
import com.example.ui.navigation.AppDestinations

val LocalActiveAccount = compositionLocalOf<UserAccount?> { null }

enum class SplashTransitionState {
    ACTIVE,
    TRANSITIONING,
    DISMISSED
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun MainAppNavigation(viewModel: AppViewModel = koinViewModel()) {
    var splashState by remember { mutableStateOf(SplashTransitionState.ACTIVE) }

    val splashTransition = updateTransition(targetState = splashState, label = "TelegramSplashTransition")

    // Telegram-style entrance: Chat list scales from 0.94f to 1.0f and blooms into view
    val mainContentScale by splashTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 460, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
        },
        label = "mainContentScale"
    ) { state ->
        when (state) {
            SplashTransitionState.ACTIVE -> 0.94f
            SplashTransitionState.TRANSITIONING -> 1.0f
            SplashTransitionState.DISMISSED -> 1.0f
        }
    }

    val mainContentAlpha by splashTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 380, easing = FastOutSlowInEasing)
        },
        label = "mainContentAlpha"
    ) { state ->
        when (state) {
            SplashTransitionState.ACTIVE -> 0.6f
            SplashTransitionState.TRANSITIONING -> 1.0f
            SplashTransitionState.DISMISSED -> 1.0f
        }
    }

    // Splash overlay exit: zooms smoothly and dissolves
    val splashAlpha by splashTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 440, easing = FastOutSlowInEasing)
        },
        label = "splashAlpha"
    ) { state ->
        when (state) {
            SplashTransitionState.ACTIVE -> 1.0f
            SplashTransitionState.TRANSITIONING -> 0.0f
            SplashTransitionState.DISMISSED -> 0.0f
        }
    }

    val splashExitScale by splashTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 460, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))
        },
        label = "splashExitScale"
    ) { state ->
        when (state) {
            SplashTransitionState.ACTIVE -> 1.0f
            SplashTransitionState.TRANSITIONING -> 1.24f
            SplashTransitionState.DISMISSED -> 1.24f
        }
    }

    LaunchedEffect(splashState) {
        if (splashState == SplashTransitionState.TRANSITIONING) {
            kotlinx.coroutines.delay(460)
            splashState = SplashTransitionState.DISMISSED
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val theme by viewModel.theme.collectAsState()
    val isBatterySaverSetting by viewModel.batterySaverEnabled.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showStorageAlert by remember { mutableStateOf(false) }
    var storageAlertMessage by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        // Run asynchronously with a delay so it does not block or compete during initial frame rendering
        kotlinx.coroutines.delay(2000)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                fun getFolderSize(file: java.io.File, maxDepth: Int = 3, currentDepth: Int = 0): Long {
                    if (currentDepth > maxDepth || !file.exists()) return 0L
                    var size: Long = 0
                    if (file.isDirectory) {
                        file.listFiles()?.forEach {
                            size += getFolderSize(it, maxDepth, currentDepth + 1)
                        }
                    } else {
                        size = file.length()
                    }
                    return size
                }
                
                val dbParent = context.getDatabasePath("messenger_db").parentFile ?: context.filesDir
                val dbSize = getFolderSize(dbParent)
                val cacheSize = getFolderSize(context.cacheDir)
                val totalAppSize = dbSize + cacheSize
                
                val stat = android.os.StatFs(context.filesDir.path)
                val availableBytes = stat.availableBytes
                
                // If less than 500 MB remaining, alert the user
                if (availableBytes < 500L * 1024 * 1024 || totalAppSize > 1024L * 1024 * 1024) { 
                    storageAlertMessage = "Storage Warning: Device has ${availableBytes / (1024 * 1024)} MB free. App encrypted database and cache are currently using ${totalAppSize / (1024 * 1024)} MB. Please free up space."
                    showStorageAlert = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    if (showStorageAlert) {
        AlertDialog(
            onDismissRequest = { showStorageAlert = false },
            title = { Text("Storage Limit Warning") },
            text = { Text(storageAlertMessage) },
            confirmButton = {
                TextButton(onClick = { showStorageAlert = false }) { Text("OK") }
            }
        )
    }
    var isCharging by remember { mutableStateOf(false) }
    var batteryLevel by remember { mutableStateOf(100f) }
    androidx.compose.runtime.DisposableEffect(context) {
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                val status: Int = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                
                val level: Int = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = level * 100f / scale.toFloat()
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    val isBatterySaver = isBatterySaverSetting && batteryLevel < 20f
    val opacity by viewModel.themeOpacity.collectAsState()
    val requires2FA by viewModel.requires2FA.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val isAddingAccount by viewModel.isAddingAccount.collectAsState()
    val activeAccount = accounts.find { it.isActive }
    
    // We only consider auth complete if we have an active account AND 2FA is not required
    val isAuthComplete = activeAccount != null && requires2FA == null && !isAddingAccount
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var showCreateSecretChatDialog by remember { mutableStateOf(false) }
    var isStoryExpanded by remember { mutableStateOf(false) }
    var isSearchCollapsed by remember { mutableStateOf(false) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var storiesProgress by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main App Screen (Chat list, navigation, drawer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = mainContentScale
                    scaleY = mainContentScale
                    alpha = mainContentAlpha
                }
        ) {
            CompositionLocalProvider(LocalActiveAccount provides activeAccount) {
                Box(modifier = Modifier.fillMaxSize()) {
            // Render Active Theme Canvas in background
            androidx.compose.animation.Crossfade(
                targetState = theme,
                animationSpec = tween(1000),
                label = "ThemeTransition"
            ) { targetTheme ->
                when (targetTheme) {
                    AppTheme.NEON_SNOWFLAKES -> NeonSnowflakesBackground(isBatterySaver = isBatterySaver, opacity = 1f)
                    AppTheme.NEON_CHERRY_BLOSSOM -> NeonCherryBlossomBackground(isBatterySaver = isBatterySaver, opacity = 1f)
                    AppTheme.NEON_CONFETTI -> NeonConfettiBackground(isBatterySaver = isBatterySaver, opacity = 1f)
                    AppTheme.NEON_MOON -> NeonMoonBackground(opacity = 1f)
                    AppTheme.NEON_ROOM_FOG -> NeonRoomFogBackground(opacity = 1f)
                    AppTheme.DEFAULT -> ElegantDarkBackground(opacity = 1f)
                }
            }
            // ParticleOverlay(theme)


            if (!isAuthComplete) {
                AuthNavGraph(
                    accounts = accounts,
                    requires2FA = requires2FA,
                    isAddingAccount = isAddingAccount,
                    appViewModel = viewModel,
                    onAuthSuccess = { accountId ->
                        viewModel.clearAddingAccount()
                        viewModel.switchAccount(accountId)
                    }
                )
            } else {
                val mainNavController = rememberNavController()
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                androidx.compose.runtime.DisposableEffect(mainNavController) {
                    val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, _, _ ->
                        focusManager.clearFocus()
                    }
                    mainNavController.addOnDestinationChangedListener(listener)
                    onDispose {
                        mainNavController.removeOnDestinationChangedListener(listener)
                    }
                }
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(300.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            AccountDrawerContent(
                                viewModel = viewModel,
                                onCloseDrawer = { scope.launch { drawerState.close() } },
                                navController = mainNavController,
                                onCreateGroupClick = { showCreateGroupDialog = true },
                                onCreateChannelClick = { showCreateChannelDialog = true },
                                onCreateSecretChatClick = { showCreateSecretChatDialog = true }
                            )
                        }
                    }
                ) {
            if (showCreateSecretChatDialog) {
                var secretChatName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreateSecretChatDialog = false },
                    title = { Text("New Secret Chat") },
                    text = {
                        OutlinedTextField(
                            value = secretChatName,
                            onValueChange = { secretChatName = it },
                            label = { Text("Contact Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.createSecretChat(secretChatName)
                                showCreateSecretChatDialog = false
                            },
                            enabled = secretChatName.isNotBlank()
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateSecretChatDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            if (showCreateGroupDialog) {
                CreateChatDialog(
                    isGroup = true,
                    onDismiss = { showCreateGroupDialog = false },
                    onCreate = { name, desc, photo, isPrivate, linkOrUsername ->
                        viewModel.createChat(name, desc, photo, isPrivate, linkOrUsername, true, false)
                    }
                )
            }
            if (showCreateChannelDialog) {
                CreateChatDialog(
                    isGroup = false,
                    onDismiss = { showCreateChannelDialog = false },
                    onCreate = { name, desc, photo, isPrivate, linkOrUsername ->
                        viewModel.createChat(name, desc, photo, isPrivate, linkOrUsername, false, true)
                    }
                )
            }
                    val currentBackStackEntry by mainNavController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route?.substringBefore("/")
                    val fullRoute = currentBackStackEntry?.destination?.route
                    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
                    val totalQueuedCount by viewModel.totalQueuedMessagesCount.collectAsStateWithLifecycle()
                    val isSyncingQueue by viewModel.isSyncingQueue.collectAsStateWithLifecycle()

                    // Firebase Analytics: Track Screen Transitions
                    LaunchedEffect(fullRoute) {
                        if (!fullRoute.isNullOrEmpty()) {
                            com.example.analytics.AnalyticsTracker.logScreenView(
                                screenName = fullRoute,
                                screenClass = fullRoute.substringBefore("?").substringBefore("/{"),
                                params = mapOf("route" to fullRoute)
                            )
                        }
                    }

                    Scaffold(
                        containerColor = Color.Transparent, // Let Canvas show through
                        topBar = {
                            if (currentRoute == "chat_list") {
                                val activeAccount = LocalActiveAccount.current
                                val activeStreams by viewModel.activeStreams.collectAsStateWithLifecycle()
                                val isSelfStreaming = activeAccount?.let { viewModel.isUserStreaming(it.id) } ?: false
                                val headerStories = remember(activeAccount, activeStreams, isSelfStreaming) {
                                    getEffectiveStories(activeAccount, activeStreams, isSelfStreaming)
                                }

                                TopAppBar(
                                    title = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            // Telegram-style Compact Avatar Group shown in TopAppBar header
                                            // Smoothly scales down and fades out when big stories panel expands below!
                                            val compactAvatarScale = (1f - storiesProgress).coerceIn(0f, 1f)
                                            val compactAvatarAlpha = (1f - storiesProgress * 1.5f).coerceIn(0f, 1f)
                                            if (compactAvatarScale > 0.01f) {
                                                Box(
                                                    modifier = Modifier
                                                        .graphicsLayer {
                                                            scaleX = compactAvatarScale
                                                            scaleY = compactAvatarScale
                                                            alpha = compactAvatarAlpha
                                                            transformOrigin = TransformOrigin(0f, 0.5f)
                                                        }
                                                ) {
                                                    CompactStoryAvatarGroup(
                                                        stories = headerStories,
                                                        onClick = {
                                                            isStoryExpanded = !isStoryExpanded
                                                        }
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width((10 * compactAvatarScale).dp))
                                            }

                                            AnimatedContent(
                                                targetState = connectionStatus,
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                                                },
                                                label = "connectionStatusAnim"
                                            ) { status ->
                                                when (status) {
                                                    ConnectionStatus.OFFLINE -> {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(16.dp),
                                                                strokeWidth = 2.dp,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                                            )
                                                            Text(
                                                                text = if (totalQueuedCount > 0) "Ожидание сети ($totalQueuedCount в очереди)..." else "Ожидание сети...",
                                                                fontWeight = FontWeight.SemiBold,
                                                                style = MaterialTheme.typography.titleMedium
                                                            )
                                                        }
                                                    }
                                                    ConnectionStatus.CONNECTING -> {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(16.dp),
                                                                strokeWidth = 2.dp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Text(
                                                                text = if (totalQueuedCount > 0) "Синхронизация ($totalQueuedCount)..." else "Подключение...",
                                                                fontWeight = FontWeight.SemiBold,
                                                                style = MaterialTheme.typography.titleMedium
                                                            )
                                                        }
                                                    }
                                                    ConnectionStatus.ONLINE -> {
                                                        if (isSyncingQueue && totalQueuedCount > 0) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                            ) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(16.dp),
                                                                    strokeWidth = 2.dp,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                                Text(
                                                                    text = "Отправка очереди ($totalQueuedCount)...",
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    style = MaterialTheme.typography.titleMedium
                                                                )
                                                            }
                                                        } else {
                                                            Text(
                                                                text = "KuoteX",
                                                                fontWeight = FontWeight.Bold,
                                                                style = MaterialTheme.typography.titleLarge
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(Icons.Filled.Menu, contentDescription = "Меню")
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color(0xFF18181B).copy(alpha = 0.4f), // bg-zinc-900/40
                                        titleContentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    actions = {
                                        // Лупа (Search icon) appears when the search bar below is collapsed on scroll down!
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = isSearchCollapsed,
                                            enter = fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.8f),
                                            exit = fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.8f)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    isSearchCollapsed = false
                                                    requestSearchFocus = true
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Filled.Search,
                                                    contentDescription = "Поиск по базе данных KuoteX",
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        // Lock icon (Passcode Lock toggle as seen in Telegram top bar)
                                        IconButton(
                                            onClick = {
                                                mainNavController.navigate("settings/security")
                                            }
                                        ) {
                                            Icon(
                                                Icons.Filled.Lock,
                                                contentDescription = "Код-пароль",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                        }

                                        // Options menu (three dots)
                                        var showTopMenu by remember { mutableStateOf(false) }
                                        IconButton(onClick = { showTopMenu = true }) {
                                            Icon(
                                                Icons.Filled.MoreVert,
                                                contentDescription = "Опции",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showTopMenu,
                                            onDismissRequest = { showTopMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Создать группу") },
                                                onClick = {
                                                    showTopMenu = false
                                                    showCreateGroupDialog = true
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Group, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Создать канал") },
                                                onClick = {
                                                    showTopMenu = false
                                                    showCreateChannelDialog = true
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Campaign, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Папки с чатами") },
                                                onClick = {
                                                    showTopMenu = false
                                                    mainNavController.navigate(AppDestinations.SETTINGS_FOLDERS)
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Настройки") },
                                                onClick = {
                                                    showTopMenu = false
                                                    mainNavController.navigate("settings")
                                                },
                                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                                            )
                                        }
                                    }
                                )
                            }
                        },
                        floatingActionButton = {
                            if (currentRoute == "chat_list") {
                                FloatingActionButton(
                                    onClick = { mainNavController.navigateSafe("contacts") },
                                    containerColor = Color(0xFFDB2777), // bg-pink-600
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, "New Chat")
                                }
                            }
                        }
                    ) { padding ->
                        MainAppNavGraph(
                            navController = mainNavController,
                            modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding(),
                            viewModel = viewModel,
                            isStoryExpanded = isStoryExpanded,
                            onStoryExpandedChange = { isStoryExpanded = it },
                            isSearchCollapsed = isSearchCollapsed,
                            onSearchCollapsedChange = { isSearchCollapsed = it },
                            storiesProgress = storiesProgress,
                            onStoriesProgressChange = { storiesProgress = it },
                            requestSearchFocus = requestSearchFocus,
                            onSearchFocusHandled = { requestSearchFocus = false }
                        )
                    }
                }

                // Floating PiP Live Stream Player (renders over chats & other screens with spring physics transition)
                val pipStreamSession by viewModel.pipStreamSession.collectAsStateWithLifecycle()
                val currentBackStackEntry by mainNavController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route?.substringBefore("/")
                val isPipMuted by viewModel.isPipMuted.collectAsStateWithLifecycle()

                // Pending navigation from persistent notification click
                val pendingStreamHostId by viewModel.pendingOpenStreamHostId.collectAsStateWithLifecycle()
                LaunchedEffect(pendingStreamHostId) {
                    pendingStreamHostId?.let { hostId ->
                        viewModel.setPendingOpenStreamHostId(null)
                        mainNavController.navigate("broadcast/$hostId")
                    }
                }

                val pendingOpenChatId by viewModel.pendingOpenChatId.collectAsStateWithLifecycle()
                LaunchedEffect(pendingOpenChatId) {
                    pendingOpenChatId?.let { chatId ->
                        viewModel.setPendingOpenChatId(null)
                        mainNavController.navigate("chat/$chatId")
                    }
                }

                val showPip = pipStreamSession != null && currentRoute != "broadcast"
                AnimatedVisibility(
                    visible = showPip,
                    enter = fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + scaleIn(
                        initialScale = 0.4f,
                        transformOrigin = TransformOrigin(0.9f, 0.85f),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    exit = fadeOut(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + scaleOut(
                        targetScale = 0.4f,
                        transformOrigin = TransformOrigin(0.9f, 0.85f),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + slideOutVertically(
                        targetOffsetY = { it / 3 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                ) {
                    pipStreamSession?.let { session ->
                        com.example.ui.components.FloatingPipStreamPlayer(
                            session = session,
                            isMuted = isPipMuted,
                            onToggleMute = { viewModel.togglePipMute() },
                            onSwitchToBackgroundAudio = {
                                viewModel.switchToBackgroundAudio(context)
                                android.widget.Toast.makeText(context, "Фоновое аудио активно. Уведомление создано.", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onExpand = {
                                val hostId = session.hostUserId
                                viewModel.closePipMode()
                                mainNavController.navigate("broadcast/$hostId")
                            },
                            onClose = {
                                viewModel.closePipMode()
                            }
                        )
                    }
                }

                // In-App Background Audio Bar (when listening to stream audio in background without video window)
                val bgAudioSession by viewModel.backgroundAudioSession.collectAsStateWithLifecycle()
                val isBgAudioPlaying by viewModel.isBackgroundAudioPlaying.collectAsStateWithLifecycle()
                val isBgAudioMuted by viewModel.isBackgroundAudioMuted.collectAsStateWithLifecycle()

                val showBgAudio = bgAudioSession != null && isBgAudioPlaying && pipStreamSession == null && currentRoute != "broadcast"
                AnimatedVisibility(
                    visible = showBgAudio,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + scaleOut(
                        targetScale = 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                        .zIndex(90f)
                ) {
                    bgAudioSession?.let { audioSession ->
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF10141E).copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color(0xFFE040FB), Color(0xFF00E5FF)))),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val hostId = audioSession.hostUserId
                                    mainNavController.navigate("broadcast/$hostId")
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Pulsing audio icon
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFE040FB).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Headphones,
                                        contentDescription = "Фоновое аудио",
                                        tint = Color(0xFFE040FB),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = audioSession.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${audioSession.hostDisplayName} • Фоновый эфир",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }

                                // Mute Toggle Button
                                IconButton(
                                    onClick = { viewModel.toggleBackgroundAudioMute(context) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        if (isBgAudioMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                        contentDescription = "Mute Toggle",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Resume PiP Video Window Button
                                IconButton(
                                    onClick = {
                                        viewModel.stopBackgroundAudio(context)
                                        viewModel.enterPipMode(audioSession)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.PictureInPictureAlt,
                                        contentDescription = "Open PiP",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Close / Stop Audio Button
                                IconButton(
                                    onClick = { viewModel.stopBackgroundAudio(context) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Stop Audio",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Telegram-style Floating Bubble Notification Overlay
                com.example.ui.components.TelegramBubbleNotificationOverlay(
                    onNavigateToChat = { chatId ->
                        mainNavController.navigate("chat/$chatId")
                    }
                )
            }
        }
    }
    }

    // Seamless Telegram-style Splash Overlay (Iceberg + KuoteX Logo)
    if (splashState != SplashTransitionState.DISMISSED) {
        SplashScreen(
            splashAlpha = splashAlpha,
            exitScale = splashExitScale,
            onAnimationReadyToTransition = {
                if (splashState == SplashTransitionState.ACTIVE) {
                    splashState = SplashTransitionState.TRANSITIONING
                }
            },
            onTransitionComplete = {
                splashState = SplashTransitionState.DISMISSED
            }
        )
    }
}
}


@Composable
fun TwoFactorAuthScreen(
    onVerify: (String) -> Unit,
    onCancel: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha=0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "2FA", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Two-Factor Authentication", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Enter the 6-digit code from your authenticator app.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    placeholder = { Text("000000") },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center, letterSpacing = 8.sp, fontSize = 24.sp)
                )
                
                Spacer(Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onVerify(code) },
                        enabled = code.length == 6
                    ) {
                        Text("Verify")
                    }
                }
            }
        }
    }
}

data class FolderTagInfo(
    val name: String,
    val color: Color
)

sealed interface ChatTabItem {
    val key: String
    val title: String
    val emoji: String? get() = null
    val colorHex: String? get() = null

    data object All : ChatTabItem {
        override val key = "all"
        override val title = "All"
    }
    data object Personal : ChatTabItem {
        override val key = "personal"
        override val title = "Personal"
    }
    data object Groups : ChatTabItem {
        override val key = "groups"
        override val title = "Groups"
    }
    data object Channels : ChatTabItem {
        override val key = "channels"
        override val title = "Channels"
    }
    data object Bots : ChatTabItem {
        override val key = "bots"
        override val title = "Bots"
    }
    data class Custom(val folder: ChatFolder) : ChatTabItem {
        override val key = folder.id
        override val title = folder.name
        override val emoji = folder.emoji
        override val colorHex = folder.colorHex
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun ChatListScreen(
    viewModel: AppViewModel, 
    navController: NavController, 
    isStoryExpanded: Boolean, 
    onStoryExpandedChange: (Boolean) -> Unit,
    isSearchCollapsed: Boolean = false,
    onSearchCollapsedChange: (Boolean) -> Unit = {},
    storiesProgress: Float = 0f,
    onStoriesProgressChange: (Float) -> Unit = {},
    requestSearchFocus: Boolean = false,
    onSearchFocusHandled: () -> Unit = {}
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val tabListStates = remember { mutableStateMapOf<Int, LazyListState>() }
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val typingChats by viewModel.typingChats.collectAsState()
    val userPresences by viewModel.userPresences.collectAsStateWithLifecycle()
    val customFolders by viewModel.chatFolders.collectAsStateWithLifecycle()
    val chatTagsEnabled by viewModel.chatTagsEnabled.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val activeAccount = LocalActiveAccount.current
    val currentUserId = activeAccount?.id ?: "1"

    val tabSearchQueries = remember { mutableStateMapOf<Int, String>() }

    val tabs: List<ChatTabItem> = remember(customFolders) {
        listOf(
            ChatTabItem.All,
            ChatTabItem.Personal,
            ChatTabItem.Groups,
            ChatTabItem.Channels,
            ChatTabItem.Bots
        ) + customFolders.map { ChatTabItem.Custom(it) }
    }

    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }

    var showDeleteFolderDialog by remember { mutableStateOf<ChatFolder?>(null) }
    var showShareFolderDialog by remember { mutableStateOf<ChatFolder?>(null) }
    var activeMenuTab by remember { mutableStateOf<ChatTabItem?>(null) }
    var pressingTabIndex by remember { mutableStateOf<Int?>(null) }
    var pressHoldProgress by remember { mutableFloatStateOf(0f) }

    val safeTabIndex = pagerState.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val currentTab = tabs.getOrElse(safeTabIndex) { ChatTabItem.All }
    val currentQuery = tabSearchQueries[safeTabIndex] ?: ""

    val density = LocalDensity.current
    val storiesHeightDp = 104.dp
    val searchBarHeightDp = 58.dp
    val maxHeaderHeightDp = storiesHeightDp + searchBarHeightDp // 162.dp

    val maxHeaderHeightPx = with(density) { maxHeaderHeightDp.toPx() }
    val storiesHeightPx = with(density) { storiesHeightDp.toPx() }
    val searchHeightPx = with(density) { searchBarHeightDp.toPx() }

    // Start with stories hidden (-storiesHeightPx) unless expanded
    val headerOffsetAnimatable = remember { 
        Animatable(if (isStoryExpanded) 0f else -storiesHeightPx) 
    }
    val animJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Search bar unfolds between -maxHeaderHeightPx and -storiesHeightPx
    val searchFraction by remember {
        derivedStateOf {
            ((headerOffsetAnimatable.value + maxHeaderHeightPx) / searchHeightPx).coerceIn(0f, 1f)
        }
    }

    // Stories panel unfolds between -storiesHeightPx and 0f
    val storiesFraction by remember {
        derivedStateOf {
            ((headerOffsetAnimatable.value + storiesHeightPx) / storiesHeightPx).coerceIn(0f, 1f)
        }
    }

    // Telegram-style spring specification: fluid, bouncy yet responsive
    val springSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    // Automatic opening of stories and streams panel when reaching top edge of chat list
    val currentListState = tabListStates[safeTabIndex]
    LaunchedEffect(currentListState) {
        if (currentListState == null) return@LaunchedEffect
        var prevIndex = currentListState.firstVisibleItemIndex
        var prevOffset = currentListState.firstVisibleItemScrollOffset

        snapshotFlow {
            Triple(
                currentListState.firstVisibleItemIndex,
                currentListState.firstVisibleItemScrollOffset,
                currentListState.isScrollInProgress
            )
        }.collect { (index, offset, _) ->
            val wasScrolledDown = prevIndex > 0 || prevOffset > 15
            val isAtStart = index == 0 && offset == 0
            val isScrollingUp = index < prevIndex || (index == prevIndex && offset < prevOffset)

            // When scrolling up and reaching the very start of the chat list:
            if (isAtStart && wasScrolledDown && isScrollingUp) {
                if (headerOffsetAnimatable.value < 0f && animJob.value?.isActive != true) {
                    animJob.value?.cancel()
                    animJob.value = coroutineScope.launch {
                        headerOffsetAnimatable.animateTo(0f, springSpec)
                        onStoryExpandedChange(true)
                    }
                }
            }

            prevIndex = index
            prevOffset = offset
        }
    }

    // Telegram "Pull-to-Reveal" Stories Panel Mechanics with sequential unfolding:
    // 1. Scrolling down into chats collapses the stories panel first, then search bar.
    // 2. Scrolling up reveals search bar first, and reaching the beginning of the list reveals the stories panel.
    // 3. Elastic pull resistance and spring animation ensure smooth motion without jerks.
    val nestedScrollConnection = remember(maxHeaderHeightPx, storiesHeightPx, safeTabIndex) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val currentOffset = headerOffsetAnimatable.value

                // Cancel running animation on user drag
                if (source == NestedScrollSource.UserInput && animJob.value?.isActive == true) {
                    animJob.value?.cancel()
                }

                // Scrolling DOWN into chats (delta < 0, finger dragging up):
                // 1. If stories panel is open (offset > -storiesHeightPx), collapse stories first!
                if (delta < 0f && currentOffset > -storiesHeightPx) {
                    val newOffset = (currentOffset + delta).coerceIn(-storiesHeightPx, 0f)
                    val consumedY = newOffset - currentOffset
                    coroutineScope.launch {
                        headerOffsetAnimatable.snapTo(newOffset)
                    }
                    return Offset(0f, consumedY)
                }

                // 2. If chat list is scrolled past top items, collapse search bar
                if (delta < 0f && currentOffset > -maxHeaderHeightPx && currentOffset <= -storiesHeightPx) {
                    val currentList = tabListStates[safeTabIndex]
                    val isScrolledPastTop = (currentList?.firstVisibleItemIndex ?: 0) > 0
                    if (isScrolledPastTop) {
                        val newOffset = (currentOffset + delta).coerceIn(-maxHeaderHeightPx, -storiesHeightPx)
                        val consumedY = newOffset - currentOffset
                        coroutineScope.launch {
                            headerOffsetAnimatable.snapTo(newOffset)
                        }
                        return Offset(0f, consumedY)
                    }
                }

                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val currentOffset = headerOffsetAnimatable.value

                if (source == NestedScrollSource.UserInput && animJob.value?.isActive == true) {
                    animJob.value?.cancel()
                }

                val currentList = tabListStates[safeTabIndex]
                val isAtVeryTop = (currentList?.firstVisibleItemIndex ?: 0) == 0 &&
                        (currentList?.firstVisibleItemScrollOffset ?: 0) == 0

                // 1. If search bar is collapsed, reveal search bar first
                if (delta > 0f && currentOffset < -storiesHeightPx) {
                    val newOffset = (currentOffset + delta).coerceIn(-maxHeaderHeightPx, -storiesHeightPx)
                    val consumedY = newOffset - currentOffset
                    coroutineScope.launch {
                        headerOffsetAnimatable.snapTo(newOffset)
                    }
                    return Offset(0f, consumedY)
                }

                // 2. When at the absolute beginning of the chat list, pull down reveals stories panel
                if (delta > 0f && isAtVeryTop && currentOffset < 0f) {
                    val progress = ((currentOffset + storiesHeightPx) / storiesHeightPx).coerceIn(0f, 1f)
                    val resistance = 0.85f - (0.35f * progress)
                    val newOffset = (currentOffset + delta * resistance).coerceIn(-storiesHeightPx, 0f)
                    val consumedY = (newOffset - currentOffset) / resistance
                    coroutineScope.launch {
                        headerOffsetAnimatable.snapTo(newOffset)
                    }
                    return Offset(0f, consumedY)
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val currentOffset = headerOffsetAnimatable.value
                val currentList = tabListStates[safeTabIndex]
                val isAtVeryTop = (currentList?.firstVisibleItemIndex ?: 0) == 0 &&
                        (currentList?.firstVisibleItemScrollOffset ?: 0) == 0

                if (currentOffset > -storiesHeightPx && currentOffset < 0f) {
                    animJob.value?.cancel()
                    if (available.y < -120f) {
                        animJob.value = coroutineScope.launch {
                            headerOffsetAnimatable.animateTo(-storiesHeightPx, springSpec)
                            onStoryExpandedChange(false)
                        }
                        return available
                    } else if (available.y > 120f && isAtVeryTop) {
                        animJob.value = coroutineScope.launch {
                            headerOffsetAnimatable.animateTo(0f, springSpec)
                            onStoryExpandedChange(true)
                        }
                        return available
                    } else {
                        val threshold = -storiesHeightPx * 0.70f
                        val shouldOpen = currentOffset > threshold && isAtVeryTop
                        val target = if (shouldOpen) 0f else -storiesHeightPx
                        animJob.value = coroutineScope.launch {
                            headerOffsetAnimatable.animateTo(target, springSpec)
                            onStoryExpandedChange(shouldOpen)
                        }
                    }
                } else if (currentOffset < -storiesHeightPx && currentOffset > -maxHeaderHeightPx) {
                    val target = if (currentOffset > -maxHeaderHeightPx + searchHeightPx * 0.5f) -storiesHeightPx else -maxHeaderHeightPx
                    animJob.value?.cancel()
                    animJob.value = coroutineScope.launch {
                        headerOffsetAnimatable.animateTo(target, springSpec)
                    }
                }
                return super.onPreFling(available)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 100f) {
                    val currentList = tabListStates[safeTabIndex]
                    val isAtVeryTop = (currentList?.firstVisibleItemIndex ?: 0) == 0 &&
                            (currentList?.firstVisibleItemScrollOffset ?: 0) == 0
                    if (isAtVeryTop && headerOffsetAnimatable.value < 0f) {
                        animJob.value?.cancel()
                        animJob.value = coroutineScope.launch {
                            headerOffsetAnimatable.animateTo(0f, springSpec)
                            onStoryExpandedChange(true)
                        }
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    LaunchedEffect(storiesFraction) {
        onStoriesProgressChange(storiesFraction)
    }

    LaunchedEffect(searchFraction) {
        onSearchCollapsedChange(searchFraction < 0.2f)
    }

    LaunchedEffect(isStoryExpanded) {
        val target = if (isStoryExpanded) 0f else -storiesHeightPx
        if (headerOffsetAnimatable.value != target && animJob.value?.isActive != true) {
            animJob.value?.cancel()
            animJob.value = coroutineScope.launch {
                headerOffsetAnimatable.animateTo(target, springSpec)
            }
        }
    }

    LaunchedEffect(currentQuery) {
        if (currentQuery.isNotBlank()) {
            headerOffsetAnimatable.animateTo(-storiesHeightPx, springSpec)
        }
    }

    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            coroutineScope.launch {
                tabListStates[safeTabIndex]?.animateScrollToItem(0)
                headerOffsetAnimatable.animateTo(-storiesHeightPx, springSpec)
                kotlinx.coroutines.delay(100)
                try {
                    searchFocusRequester.requestFocus()
                    keyboardController?.show()
                } catch (_: Exception) {}
                onSearchFocusHandled()
            }
        }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        val avatarScale = (0.35f + 0.65f * storiesFraction).coerceIn(0.35f, 1f)
        val storiesAlpha = storiesFraction.coerceIn(0f, 1f)
        val searchScale = (0.75f + 0.25f * searchFraction).coerceIn(0.75f, 1f)
        val searchAlpha = searchFraction.coerceIn(0f, 1f)

        // Stage 3 element: Stories Panel (Height: 0 to storiesHeightDp, avatars scale and fade smoothly)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(storiesHeightDp * storiesFraction)
                .clipToBounds()
                .graphicsLayer {
                    alpha = storiesAlpha
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                StoriesPanel(
                    onStorySwipe = { onStoryExpandedChange(it) },
                    onAvatarClick = { story -> 
                        viewModel.addBot(Chat(story.id, story.author, isGroup = false, isChannel = false, isBot = false, lastMessage = ""))
                        navController.navigate("profile/${story.id}") 
                    },
                    onLiveClick = { streamId ->
                        navController.navigate("broadcast/$streamId")
                    },
                    viewModel = viewModel,
                    avatarScale = avatarScale
                )
            }
        }

        // Stage 2 element: Search Bar (Height: 0 to searchBarHeightDp, scales and fades smoothly)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(searchBarHeightDp * searchFraction)
                .clipToBounds()
                .graphicsLayer {
                    alpha = searchAlpha
                }
        ) {
            val searchPlaceholder = when (currentTab) {
                is ChatTabItem.All -> "Поиск по всем чатам, каналам, ботам, людям..."
                is ChatTabItem.Personal -> "Поиск личных чатов и пользователей..."
                is ChatTabItem.Groups -> "Поиск групп по названию..."
                is ChatTabItem.Channels -> "Поиск каналов по названию..."
                is ChatTabItem.Bots -> "Поиск ботов по @username или названию..."
                is ChatTabItem.Custom -> "Поиск в папке «${currentTab.title}»..."
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = searchScale
                        scaleY = searchScale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = currentQuery,
                    onValueChange = { tabSearchQueries[safeTabIndex] = it },
                    placeholder = { 
                        Text(
                            searchPlaceholder, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (currentQuery.isNotEmpty()) {
                            IconButton(onClick = { tabSearchQueries[safeTabIndex] = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Очистить поиск", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .semantics {
                            contentDescription = "Поиск в папке ${tabs.getOrNull(safeTabIndex)?.title ?: "Все"}"
                        },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
            }
        }

        ScrollableTabRow(
            selectedTabIndex = safeTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 16.dp,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) },
            indicator = { tabPositions ->
                if (safeTabIndex in tabPositions.indices) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[safeTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, tabItem ->
                val isSelected = safeTabIndex == index
                val tabSemanticLabel = when (tabItem) {
                    is ChatTabItem.All -> "Все чаты"
                    is ChatTabItem.Personal -> "Личные чаты"
                    is ChatTabItem.Groups -> "Группы"
                    is ChatTabItem.Channels -> "Каналы"
                    is ChatTabItem.Bots -> "Боты"
                    is ChatTabItem.Custom -> tabItem.title
                }
                val tabUnreadCount = remember(chats, tabItem) {
                    when (tabItem) {
                        is ChatTabItem.All -> chats.filter { !it.isArchived && !it.isBlocked }.sumOf { it.unreadCount }
                        is ChatTabItem.Personal -> chats.filter { !it.isGroup && !it.isChannel && !it.isBot && !it.isArchived && !it.isBlocked }.sumOf { it.unreadCount }
                        is ChatTabItem.Groups -> chats.filter { it.isGroup && !it.isArchived && !it.isBlocked }.sumOf { it.unreadCount }
                        is ChatTabItem.Channels -> chats.filter { it.isChannel && !it.isArchived && !it.isBlocked }.sumOf { it.unreadCount }
                        is ChatTabItem.Bots -> chats.filter { it.isBot && !it.isArchived && !it.isBlocked }.sumOf { it.unreadCount }
                        is ChatTabItem.Custom -> chats.filter { !it.isArchived && !it.isBlocked && tabItem.folder.matches(it) }.sumOf { it.unreadCount }
                    }
                }

                val isPressing = pressingTabIndex == index
                val tabScale by animateFloatAsState(
                    targetValue = if (isPressing) 0.95f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "tab_scale_$index"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = tabScale
                            scaleY = tabScale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isPressing) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .pointerInput(tabItem, index) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPosition = down.position
                                val touchSlop = viewConfiguration.touchSlop
                                val startTime = System.currentTimeMillis()
                                var holdConditionMet = false

                                pressingTabIndex = index
                                pressHoldProgress = 0f

                                // Exact 2-second (2000 ms) timer response system
                                val timerJob = coroutineScope.launch {
                                    val totalDurationMs = 2000L
                                    val stepMs = 40L
                                    var elapsedMs = 0L
                                    while (elapsedMs < totalDurationMs) {
                                        delay(stepMs)
                                        elapsedMs += stepMs
                                        if (pressingTabIndex == index) {
                                            pressHoldProgress = (elapsedMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                        }
                                    }
                                    // 2-SECOND CONDITION IS MET!
                                    holdConditionMet = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activeMenuTab = tabItem
                                    pressingTabIndex = null
                                    pressHoldProgress = 0f
                                }

                                var pointerUp: PointerInputChange? = null
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val currentChange = event.changes.firstOrNull { it.id == down.id } ?: break

                                    // Condition Check: If moved beyond touch slop, user is scrolling tabs
                                    val distance = (currentChange.position - downPosition).getDistance()
                                    if (distance > touchSlop) {
                                        timerJob.cancel()
                                        if (pressingTabIndex == index) {
                                            pressingTabIndex = null
                                            pressHoldProgress = 0f
                                        }
                                        break
                                    }

                                    if (!currentChange.pressed) {
                                        pointerUp = currentChange
                                        timerJob.cancel()
                                        if (pressingTabIndex == index) {
                                            pressingTabIndex = null
                                            pressHoldProgress = 0f
                                        }
                                        break
                                    }
                                }

                                // If released before 2 seconds and condition was not met, this is a regular tap!
                                if (pointerUp != null && !holdConditionMet) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    if (elapsed < 2000L) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .semantics {
                            role = Role.Tab
                            selected = isSelected
                            contentDescription = "Вкладка $tabSemanticLabel"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (tabItem.emoji != null) {
                                Text(tabItem.emoji!!, fontSize = 14.sp)
                                Spacer(Modifier.width(4.dp))
                            } else if (tabItem is ChatTabItem.Custom && tabItem.colorHex != null) {
                                val dotColor = runCatching { Color(android.graphics.Color.parseColor(tabItem.colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = tabItem.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tabUnreadCount > 0) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = tabUnreadCount.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Animated progress indicator during 2-second hold
                        if (isPressing && pressHoldProgress > 0.05f) {
                            Spacer(Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(pressHoldProgress)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val pageTab = tabs.getOrElse(page) { ChatTabItem.All }
            val pageQuery = tabSearchQueries[page] ?: ""

            val pageFilteredChats = remember(chats, pageTab, pageQuery) {
                chats.filter { chat ->
                    !chat.isBlocked &&
                    !chat.isArchived &&
                    (pageQuery.isBlank() || 
                    chat.title.contains(pageQuery, ignoreCase = true) || 
                    chat.lastMessage.contains(pageQuery, ignoreCase = true)) &&
                    when (pageTab) {
                        is ChatTabItem.All -> true
                        is ChatTabItem.Personal -> !chat.isGroup && !chat.isChannel && !chat.isBot
                        is ChatTabItem.Groups -> chat.isGroup
                        is ChatTabItem.Channels -> chat.isChannel
                        is ChatTabItem.Bots -> chat.isBot
                        is ChatTabItem.Custom -> pageTab.folder.matches(chat)
                    }
                }
            }

            val pageDbSearchResults by remember(pageQuery, pageTab) {
                val searchCategoryIndex = when (pageTab) {
                    is ChatTabItem.All -> 0
                    is ChatTabItem.Personal -> 1
                    is ChatTabItem.Groups -> 2
                    is ChatTabItem.Channels -> 3
                    is ChatTabItem.Bots -> 4
                    is ChatTabItem.Custom -> 0
                }
                viewModel.searchDatabase(pageQuery, searchCategoryIndex)
            }.collectAsStateWithLifecycle(initialValue = DatabaseSearchResults())

            val pageListState = remember(page) {
                tabListStates.getOrPut(page) { LazyListState() }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                LazyColumn(
                    state = pageListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (pageQuery.isBlank() && page == 0) {
                        item {
                            val archivedCount = chats.count { it.isArchived && !it.isBlocked }
                            if (archivedCount > 0) {
                                ListItem(
                                    headlineContent = { Text("Archived Chats", fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text("$archivedCount chat${if (archivedCount > 1) "s" else ""}") },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    modifier = Modifier
                                        .clickable(
                                            onClick = { navController.navigate("archived_chats") },
                                            onClickLabel = "Открыть архивные чаты"
                                        )
                                        .semantics(mergeDescendants = true) {
                                            role = Role.Button
                                            contentDescription = "Архивные чаты, $archivedCount чатов"
                                        }
                                )
                            }
                        }
                    }

                    if (pageQuery.isNotBlank() && pageFilteredChats.isNotEmpty() && !pageDbSearchResults.isEmpty) {
                        item {
                            Text(
                                "Чаты и сообщения",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                    }

                    // Empty state when folder has no chats yet
                    if (pageFilteredChats.isEmpty() && pageQuery.isBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(pageTab.emoji ?: "📁", fontSize = 44.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = if (pageTab is ChatTabItem.Custom) "В папке «${pageTab.title}» пока нет чатов" else "В этой вкладке пока нет чатов",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Нажмите «Настроить папку», чтобы добавить чаты или настроить правила включения.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    if (pageTab is ChatTabItem.Custom) {
                                        Spacer(Modifier.height(16.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                navController.navigate(AppDestinations.folderEditRoute(pageTab.folder.id))
                                            }
                                        ) {
                                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Настроить папку")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(pageFilteredChats, key = { it.id }) { chat ->
                        val chatFolderTags = remember(customFolders, chatTagsEnabled, chat) {
                            if (!chatTagsEnabled) emptyList()
                            else {
                                customFolders
                                    .filter { it.matches(chat) }
                                    .map { f ->
                                        val col = runCatching { Color(android.graphics.Color.parseColor(f.colorHex)) }
                                            .getOrDefault(Color(0xFF2196F3))
                                        FolderTagInfo(f.name, col)
                                    }
                            }
                        }

                        SwipeableChatListItem(
                            chat = chat, 
                            isTyping = typingChats.contains(chat.id),
                            draftText = drafts[chat.id],
                            viewModel = viewModel,
                            presence = userPresences[chat.id],
                            folderTags = chatFolderTags,
                            onClick = { 
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                navController.navigate("chat/${chat.id}") 
                            },
                            onAvatarClick = { 
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                navController.navigate("profile/${chat.id}") 
                            }
                        )
                    }

                    // --- Database Search Results ---
                    if (pageQuery.isNotBlank()) {
                        val nonLocalChannels = pageDbSearchResults.channels.filter { ch -> pageFilteredChats.none { it.id == ch.id } }
                        val nonLocalGroups = pageDbSearchResults.groups.filter { gr -> pageFilteredChats.none { it.id == gr.id } }
                        val nonLocalBots = pageDbSearchResults.bots.filter { b -> pageFilteredChats.none { it.id == b.id } }
                        val foundUsers = pageDbSearchResults.users

                        // Channels section
                        if (nonLocalChannels.isNotEmpty()) {
                            item {
                                DatabaseSearchSectionHeader(
                                    title = "Публичные каналы",
                                    icon = Icons.Filled.Campaign,
                                    count = nonLocalChannels.size
                                )
                            }
                            items(nonLocalChannels, key = { "db_channel_${it.id}" }) { channel ->
                                ChannelSearchResultItem(
                                    channel = channel,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.openOrCreateChat(channel)
                                        navController.navigate("chat/${channel.id}")
                                    }
                                )
                            }
                        }

                        // Groups section
                        if (nonLocalGroups.isNotEmpty()) {
                            item {
                                DatabaseSearchSectionHeader(
                                    title = "Публичные группы",
                                    icon = Icons.Filled.Group,
                                    count = nonLocalGroups.size
                                )
                            }
                            items(nonLocalGroups, key = { "db_group_${it.id}" }) { group ->
                                GroupSearchResultItem(
                                    group = group,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.openOrCreateChat(group)
                                        navController.navigate("chat/${group.id}")
                                    }
                                )
                            }
                        }

                        // Bots section
                        if (nonLocalBots.isNotEmpty()) {
                            item {
                                DatabaseSearchSectionHeader(
                                    title = "Боты KuoteX",
                                    icon = Icons.Filled.SmartToy,
                                    count = nonLocalBots.size
                                )
                            }
                            items(nonLocalBots, key = { "db_bot_${it.id}" }) { bot ->
                                BotSearchResultItem(
                                    bot = bot,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.openOrCreateBotChat(bot)
                                        navController.navigate("chat/${bot.id}")
                                    }
                                )
                            }
                        }

                        // Users section
                        if (foundUsers.isNotEmpty()) {
                            item {
                                DatabaseSearchSectionHeader(
                                    title = if (safeTabIndex == 1) "Найденные пользователи" else "Пользователи",
                                    icon = Icons.Filled.Person,
                                    count = foundUsers.size
                                )
                            }
                            items(foundUsers, key = { "db_user_${it.id}" }) { user ->
                                UserSearchResultItem(
                                    user = user,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.openOrCreateUserChat(user)
                                        navController.navigate("chat/${user.id}")
                                    },
                                    onAvatarClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.openOrCreateUserChat(user)
                                        navController.navigate("profile/${user.id}")
                                    }
                                )
                            }
                        }

                        // Empty state if nothing matches locally or in the database
                        if (pageFilteredChats.isEmpty() && pageDbSearchResults.isEmpty) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp, horizontal = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Ничего не найдено",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val hintText = when (safeTabIndex) {
                                        0 -> "Попробуйте изменить название канала, группы, @username или имя"
                                        1 -> "Попробуйте поискать пользователя по @username или имени"
                                        2 -> "Попробуйте ввести другое название группы"
                                        3 -> "Попробуйте ввести другое название канала"
                                        4 -> "Попробуйте ввести @username или имя бота"
                                        else -> "Попробуйте изменить поисковый запрос"
                                    }
                                    Text(
                                        text = hintText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Telegram Folder Context Bottom Sheet (Triggered after 2 seconds hold condition)
        if (activeMenuTab != null) {
            val menuTab = activeMenuTab!!
            val tabChats = remember(menuTab, chats) {
                when (menuTab) {
                    is ChatTabItem.All -> chats.filter { !it.isArchived && !it.isBlocked }
                    is ChatTabItem.Personal -> chats.filter { !it.isGroup && !it.isChannel && !it.isBot && !it.isArchived && !it.isBlocked }
                    is ChatTabItem.Groups -> chats.filter { it.isGroup && !it.isArchived && !it.isBlocked }
                    is ChatTabItem.Channels -> chats.filter { it.isChannel && !it.isArchived && !it.isBlocked }
                    is ChatTabItem.Bots -> chats.filter { it.isBot && !it.isArchived && !it.isBlocked }
                    is ChatTabItem.Custom -> chats.filter { !it.isArchived && !it.isBlocked && menuTab.folder.matches(it) }
                }
            }
            val isAllMuted = tabChats.isNotEmpty() && tabChats.all { it.isMuted }

            ModalBottomSheet(
                onDismissRequest = { activeMenuTab = null },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    // Header with Folder Icon/Emoji, Title, and Chat Count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (menuTab.emoji != null) {
                                Text(menuTab.emoji!!, fontSize = 22.sp)
                            } else if (menuTab is ChatTabItem.Custom && menuTab.colorHex != null) {
                                val dotColor = runCatching { Color(android.graphics.Color.parseColor(menuTab.colorHex)) }.getOrDefault(MaterialTheme.colorScheme.primary)
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = menuTab.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val unreadInFolder = tabChats.sumOf { it.unreadCount }
                            Text(
                                text = "${tabChats.size} чатов" + if (unreadInFolder > 0) " • $unreadInFolder непрочитанных" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { activeMenuTab = null }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Закрыть",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 1. Изменить порядок
                    FolderMenuActionItem(
                        icon = Icons.Filled.SwapHoriz,
                        title = "Изменить порядок",
                        subtitle = "Переместить или скрыть вкладки папок",
                        onClick = {
                            activeMenuTab = null
                            navController.navigate(AppDestinations.SETTINGS_FOLDERS)
                        }
                    )

                    // 2. Настроить папку
                    FolderMenuActionItem(
                        icon = Icons.Filled.Tune,
                        title = if (menuTab is ChatTabItem.Custom) "Настроить папку" else "Настроить папки",
                        subtitle = if (menuTab is ChatTabItem.Custom) "Редактировать фильтры и чаты" else "Управление всеми папками",
                        onClick = {
                            activeMenuTab = null
                            if (menuTab is ChatTabItem.Custom) {
                                navController.navigate(AppDestinations.folderEditRoute(menuTab.folder.id))
                            } else {
                                navController.navigate(AppDestinations.SETTINGS_FOLDERS)
                            }
                        }
                    )

                    // 3. Выкл./Вкл. уведомления
                    FolderMenuActionItem(
                        icon = if (isAllMuted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                        title = if (isAllMuted) "Вкл. уведомления" else "Выкл. уведомления",
                        subtitle = if (isAllMuted) "Включить звук для чатов этой папки" else "Отключить звук для чатов этой папки",
                        onClick = {
                            activeMenuTab = null
                            val newMuteState = !isAllMuted
                            viewModel.toggleChatsMute(tabChats.map { it.id }, newMuteState)
                            Toast.makeText(
                                context,
                                if (newMuteState) "Уведомления для папки отключены" else "Уведомления для папки включены",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    // 4. Прочитать все
                    FolderMenuActionItem(
                        icon = Icons.Default.DoneAll,
                        title = "Прочитать все",
                        subtitle = "Сбросить счетчики непрочитанных сообщений",
                        onClick = {
                            activeMenuTab = null
                            val unreadIds = tabChats.filter { it.unreadCount > 0 }.map { it.id }
                            viewModel.markChatsAsRead(unreadIds.ifEmpty { tabChats.map { it.id } }, currentUserId)
                            Toast.makeText(context, "Все чаты в папке прочитаны", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // 5. Поделиться
                    FolderMenuActionItem(
                        icon = Icons.Filled.Share,
                        title = "Поделиться",
                        subtitle = "Создать ссылку-приглашение на папку",
                        onClick = {
                            activeMenuTab = null
                            if (menuTab is ChatTabItem.Custom) {
                                showShareFolderDialog = menuTab.folder
                            } else {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, "Присоединяйтесь ко мне в KuoteX Messenger: https://kuotex.me")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Поделиться"))
                            }
                        }
                    )

                    // 6. Удалить папку (для кастомных папок)
                    if (menuTab is ChatTabItem.Custom) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        FolderMenuActionItem(
                            icon = Icons.Filled.Delete,
                            title = "Удалить папку",
                            subtitle = "Папка будет удалена, чаты останутся в общем списке",
                            isDestructive = true,
                            onClick = {
                                activeMenuTab = null
                                showDeleteFolderDialog = menuTab.folder
                            }
                        )
                    }
                }
            }
        }

        // Delete Folder Dialog
        if (showDeleteFolderDialog != null) {
            val folder = showDeleteFolderDialog!!
            AlertDialog(
                onDismissRequest = { showDeleteFolderDialog = null },
                shape = RoundedCornerShape(24.dp),
                title = { Text("Удалить папку?", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Вы уверены, что хотите удалить папку «${folder.name}»? Чаты останутся в общем списке чатов.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteChatFolder(folder.id)
                            showDeleteFolderDialog = null
                            Toast.makeText(context, "Папка «${folder.name}» удалена", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteFolderDialog = null }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Share Folder Dialog
        if (showShareFolderDialog != null) {
            val folder = showShareFolderDialog!!
            val inviteUrl = "https://kuotex.me/addlist/${folder.id.take(8)}"
            AlertDialog(
                onDismissRequest = { showShareFolderDialog = null },
                shape = RoundedCornerShape(24.dp),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(folder.emoji ?: "📁", fontSize = 28.sp)
                    }
                },
                title = {
                    Text("Поделиться папкой «${folder.name}»", textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Любой пользователь с этой ссылкой сможет добавить папку «${folder.name}» и состоящие в ней публичные чаты.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    inviteUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, "Добавить папку с чатами «${folder.name}» в KuoteX:\n$inviteUrl")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Поделиться ссылкой"))
                            showShareFolderDialog = null
                        }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Поделиться")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(inviteUrl))
                            Toast.makeText(context, "Ссылка скопирована в буфер обмена", Toast.LENGTH_SHORT).show()
                            showShareFolderDialog = null
                        }
                    ) {
                        Text("Копировать")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun SwipeableChatListItem(
    chat: Chat, 
    isTyping: Boolean = false, 
    draftText: String? = null,
    viewModel: AppViewModel, 
    presence: com.example.ui.UserPresence? = null, 
    folderTags: List<FolderTagInfo> = emptyList(),
    onClick: () -> Unit, 
    onAvatarClick: () -> Unit = {}
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                viewModel.toggleArchive(chat.id, !chat.isArchived)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.targetValue) {
                SwipeToDismissBoxValue.Settled -> Color.Transparent
                else -> if (chat.isArchived) Color(0xFF4CAF50) else Color(0xFFF44336)
            }
            val icon = if (chat.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                    Icon(icon, contentDescription = "Archive", tint = Color.White)
                }
            }
        },
        content = {
            ChatListItem(
                chat = chat, 
                isTyping = isTyping, 
                draftText = draftText,
                presence = presence,
                folderTags = folderTags,
                onClick = onClick, 
                onAvatarClick = onAvatarClick
            )
        }
    )
}

@Composable
fun ChatListItem(
    chat: Chat, 
    isTyping: Boolean = false, 
    draftText: String? = null,
    presence: com.example.ui.UserPresence? = null,
    folderTags: List<FolderTagInfo> = emptyList(),
    onClick: () -> Unit, 
    onAvatarClick: () -> Unit = {}
) {
    val chatTypeLabel = when {
        chat.isSecret -> "Секретный чат"
        chat.isChannel -> "Канал"
        chat.isGroup -> "Группа"
        chat.isBot -> "Бот"
        else -> "Личный чат"
    }
    val presenceLabel = if (!chat.isGroup && !chat.isChannel && !chat.isBot) {
        if (presence?.isOnline == true) "В сети"
        else if (presence != null && presence.lastSeen > 0) {
            val diff = System.currentTimeMillis() - presence.lastSeen
            val timeStr = when {
                diff < 60_000 -> "только что"
                diff < 3600_000 -> "${diff / 60_000} мин. назад"
                diff < 86400_000 -> "${diff / 3600_000} ч. назад"
                else -> "${diff / 86400_000} дн. назад"
            }
            "был(а) $timeStr"
        } else null
    } else null

    val messageStatusLabel = when {
        isTyping -> "Печатает..."
        !draftText.isNullOrBlank() -> "Черновик: $draftText"
        !chat.lastMessageSenderName.isNullOrEmpty() && (chat.isGroup || chat.lastMessageSenderName == "You") -> "${chat.lastMessageSenderName}: ${chat.lastMessage}"
        chat.lastMessage.isNotEmpty() -> chat.lastMessage
        else -> "Нет сообщений"
    }

    val unreadLabel = if (chat.unreadCount > 0) "${chat.unreadCount} непрочитанных сообщений" else null

    val chatItemAccessibilityDescription = listOfNotNull(
        chatTypeLabel,
        chat.title,
        presenceLabel,
        messageStatusLabel,
        unreadLabel
    ).joinToString(", ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                onClickLabel = "Открыть чат ${chat.title}"
            )
            .semantics(mergeDescendants = true) {
                contentDescription = chatItemAccessibilityDescription
                role = Role.Button
            }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        val botObj = com.example.ui.botapi.BotRegistry.getBot(chat.id) ?: com.example.ui.botapi.BotRegistry.getCustomBots().find { it.id == chat.id }
        val customBot = botObj as? com.example.ui.botapi.CustomBot
        val avatarUrl = customBot?.botPicUri?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/seed/${chat.id}/100"

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape)
                .clickable(
                    onClick = { onAvatarClick() },
                    onClickLabel = "Посмотреть профиль ${chat.title}"
                )
                .semantics {
                    contentDescription = "Аватар ${chat.title}"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).allowHardware(false)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
            // Fallback letter if image is loading or fails (AsyncImage handles this nicely, but we can just put text behind it)
            val letter = chat.title.take(1).uppercase()
            Text(letter, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.align(Alignment.Center).zIndex(-1f))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isSecret) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(4.dp))
                } else if (chat.isChannel) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                } else if (chat.isGroup) {
                    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                } else if (chat.isBot) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00D4FF))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = chat.title, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (folderTags.isNotEmpty()) {
                    folderTags.take(2).forEach { tag ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tag.color)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = tag.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
            if (!chat.isGroup && !chat.isChannel && !chat.isBot) {
                if (presence?.isOnline == true) {
                    Text("Online", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                } else if (presence != null && presence.lastSeen > 0) {
                    val diff = System.currentTimeMillis() - presence.lastSeen
                    val timeStr = when {
                        diff < 60_000 -> "just now"
                        diff < 3600_000 -> "${diff / 60_000}m ago"
                        diff < 86400_000 -> "${diff / 3600_000}h ago"
                        else -> "${diff / 86400_000}d ago"
                    }
                    Text("Last active $timeStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
            if (isTyping) {
                Text(
                    text = "typing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            } else if (!draftText.isNullOrBlank()) {
                val annotatedDraft = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFE53935))) {
                        append("Draft: ")
                    }
                    append(draftText)
                }
                Text(
                    text = annotatedDraft,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            } else {
                if (!chat.lastMessageSenderName.isNullOrEmpty() && (chat.isGroup || chat.lastMessageSenderName == "You")) {
                    val annotatedText = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                            append("${chat.lastMessageSenderName}: ")
                        }
                        append(chat.lastMessage)
                    }
                    Text(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                } else if (chat.lastMessage.isNotEmpty()) {
                    Text(
                        text = chat.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        if (chat.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(chat.unreadCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun DatabaseSearchSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun UserSearchResultItem(
    user: UserAccount,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center
        ) {
            if (user.profilePicUrl.isNotBlank()) {
                AsyncImage(
                    model = user.profilePicUrl,
                    contentDescription = user.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = (user.displayName.ifBlank { user.username }).take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName.ifBlank { user.username },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (user.username.startsWith("@")) user.username else "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                if (user.bio.isNotBlank()) {
                    Text(
                        text = " • ${user.bio}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Filled.Chat,
            contentDescription = "Написать",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun BotSearchResultItem(
    bot: BotSearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bot.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "BOT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (bot.username.startsWith("@")) bot.username else "@${bot.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
                if (bot.description.isNotBlank()) {
                    Text(
                        text = " • ${bot.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ChannelSearchResultItem(
    channel: Chat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Канал",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = channel.lastMessage.ifBlank { "Публичный канал KuoteX" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun GroupSearchResultItem(
    group: Chat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Группа",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = group.lastMessage.ifBlank { "Публичная группа сообщества" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val theme by viewModel.theme.collectAsState()
    val isAutoThemeEnabled by viewModel.isAutoThemeEnabled.collectAsState()
    val customPrimary by viewModel.customPrimaryColor.collectAsState()
    val customSecondary by viewModel.customSecondaryColor.collectAsState()
    val batterySaverEnabled by viewModel.batterySaverEnabled.collectAsState()
    val themeOpacity by viewModel.themeOpacity.collectAsState()
    val favoriteThemes by viewModel.favoriteThemes.collectAsState()
    
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    
    val activeAccount = LocalActiveAccount.current ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // 2FA Setting
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.7f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Two-Factor Authentication", style = MaterialTheme.typography.titleMedium)
                    Text("Secure your account with 2FA", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = activeAccount.is2FAEnabled,
                    onCheckedChange = { viewModel.toggle2FA(activeAccount.id, activeAccount.is2FAEnabled) }
                )
            }
        }

        // Push Notifications Setting
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.7f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Push Notifications", style = MaterialTheme.typography.titleMedium)
                    Text("Real-time message alerts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var notificationsEnabled by remember { mutableStateOf(true) }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }
        }

        // Auto Theme Seting
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.7f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto Theme Switcher", style = MaterialTheme.typography.titleMedium)
                    Text("Switch between Cherry Blossom (Day) and Neon Moon (Night)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isAutoThemeEnabled,
                    onCheckedChange = { viewModel.setAutoThemeEnabled(it) }
                )
            }
        }

        // Battery Saver Setting
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.7f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Battery Saver", style = MaterialTheme.typography.titleMedium)
                    Text("Reduce background animation frame rates", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = batterySaverEnabled,
                    onCheckedChange = { viewModel.setBatterySaverEnabled(it) }
                )
            }
        }

        // Theme Opacity
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.7f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Theme Opacity / Dimness", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = themeOpacity,
                    onValueChange = { viewModel.setThemeOpacity(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Primary Accent Color
        Spacer(Modifier.height(24.dp))
        Text("Custom UI Colors", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
             val defaultColors = listOf(Color(0xFFFF007F), Color(0xFF00FFFF), Color(0xFF39FF14), Color(0xFFFF00FF), Color(0xFFB500FF), Color(0xFFE4E1E6))
             
             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Text("Primary", style = MaterialTheme.typography.bodySmall)
                 LazyRow {
                    items(defaultColors) { color ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, if (customPrimary?.toULong() == color.value) Color.White else Color.Transparent, CircleShape)
                                .clickable { 
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.setCustomPrimaryColor(color.value.toLong()) 
                                }
                        )
                    }
                 }
             }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
             val defaultColors = listOf(Color(0xFF00E5FF), Color(0xFFFFEA00), Color(0xFFFF2A2A), Color(0xFF9000FF), Color(0xFF20202F))
             
             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Text("Secondary", style = MaterialTheme.typography.bodySmall)
                 LazyRow {
                    items(defaultColors) { color ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, if (customSecondary?.toULong() == color.value) Color.White else Color.Transparent, CircleShape)
                                .clickable { 
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    viewModel.setCustomSecondaryColor(color.value.toLong()) 
                                }
                        )
                    }
                 }
             }
        }

        Spacer(Modifier.height(24.dp))
        Text("Neon Background Themes", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val themes = listOf(
            AppTheme.NEON_SNOWFLAKES to "Snowflakes",
            AppTheme.NEON_CHERRY_BLOSSOM to "Cherry Blossom",
            AppTheme.NEON_CONFETTI to "Confetti",
            AppTheme.NEON_MOON to "Moon Sky",
            AppTheme.NEON_ROOM_FOG to "Yellow Fog",
            AppTheme.DEFAULT to "Default"
        )
        
        val sortedThemes = themes.sortedByDescending { favoriteThemes.contains(it.first.name) }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            items(sortedThemes) { (appTheme, name) ->
                val isFavorite = favoriteThemes.contains(appTheme.name)
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { 
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        viewModel.setAutoThemeEnabled(false) // Disable auto switch if picked manually
                        viewModel.switchTheme(appTheme) 
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp, 160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                2.dp, 
                                if (theme == appTheme) MaterialTheme.colorScheme.primary else Color.Transparent, 
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        // thumbnail preview
                        when (appTheme) {
                            AppTheme.NEON_SNOWFLAKES -> NeonSnowflakesBackground(isStatic = true)
                            AppTheme.NEON_CHERRY_BLOSSOM -> NeonCherryBlossomBackground(isStatic = true)
                            AppTheme.NEON_CONFETTI -> NeonConfettiBackground(isStatic = true)
                            AppTheme.NEON_MOON -> NeonMoonBackground()
                            AppTheme.NEON_ROOM_FOG -> NeonRoomFogBackground()
                            AppTheme.DEFAULT -> ElegantDarkBackground()
                        }
                        
                        // Favorite toggle
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color.Yellow else Color.White.copy(alpha=0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clickable { viewModel.toggleFavoriteTheme(appTheme.name) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (theme == appTheme) FontWeight.Bold else FontWeight.Normal,
                        color = if (theme == appTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        var showImportDialog by remember { mutableStateOf(false) }

        if (showImportDialog) {
            var importCode by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import Theme") },
                text = {
                    Column {
                        Text("Paste a theme code to apply it.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = importCode,
                            onValueChange = { importCode = it },
                            label = { Text("KuoteX Theme Code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        viewModel.importTheme(importCode)
                        showImportDialog = false 
                    }) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { 
                    viewModel.resetTheme() 
                }
            ) {
                Text("Reset to Default", color = MaterialTheme.colorScheme.error)
            }
            
            Row {
                IconButton(onClick = { showImportDialog = true }) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Download, contentDescription = "Import Theme")
                }
                Button(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "KuoteX Theme Code: ${theme.name}-${customPrimary ?: "def"}-${customSecondary ?: "def"}")
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Export Theme Layout"))
                    }
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Share, contentDescription = "Export")
                    Spacer(Modifier.width(8.dp))
                    Text("Export Theme")
                }
            }
        }
        
        Spacer(Modifier.height(48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun GroupAdminScreen(viewModel: AppViewModel, chatId: String, navController: NavController) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chat = chats.find { it.id == chatId } ?: return
    
    val members by viewModel.getGroupMembers(chatId).collectAsState(initial = emptyList())
    var groupName by remember { mutableStateOf(chat.title) }

    var selectedBotMember by remember { mutableStateOf<GroupMember?>(null) }
    
    if (selectedBotMember != null) {
        var canRead by remember { mutableStateOf(selectedBotMember!!.canReadMessages) }
        var canSend by remember { mutableStateOf(selectedBotMember!!.canSendMessages) }
        
        AlertDialog(
            onDismissRequest = { selectedBotMember = null },
            title = { Text("Bot Permissions") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canRead, onCheckedChange = { canRead = it })
                        Text("Can Read Messages")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canSend, onCheckedChange = { canSend = it })
                        Text("Can Send Messages")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateBotPermissions(chatId, selectedBotMember!!.userId, canRead, canSend)
                    selectedBotMember = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { selectedBotMember = null }) { Text("Cancel") }
            }
        )
    }

    var showAddBotDialog by remember { mutableStateOf(false) }
    if (showAddBotDialog) {
        val availableBots = BotRegistry.getAllBots()
        AlertDialog(
            onDismissRequest = { showAddBotDialog = false },
            title = { Text("Add Bot") },
            text = {
                LazyColumn {
                    items(availableBots) { bot ->
                        ListItem(
                            headlineContent = { Text(bot.name) },
                            modifier = Modifier.clickable {
                                viewModel.addGroupMember(chatId, bot.id, bot.name, isAdmin = false)
                                showAddBotDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddBotDialog = false }) { Text("Close") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Admin: ${chat.title}") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(16.dp)
        ) {
            Text("Group Configuration", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { /* Update group settings in db */ }, modifier = Modifier.align(Alignment.End)) {
                Text("Save Settings")
            }
            
            Spacer(Modifier.height(24.dp))
            Text("Group Members", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                showAddBotDialog = true
            }, modifier = Modifier.align(Alignment.End)) {
                Text("Add Bot to Group")
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(members) { member ->
                    val isBot = member.userId.startsWith("b") || BotRegistry.getBot(member.userId) != null
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(member.userName, style = MaterialTheme.typography.bodyLarge)
                                if (member.isAdmin) {
                                    Text("Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (isBot) {
                                    Text("Bot", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            if (isBot) {
                                TextButton(onClick = { selectedBotMember = member }) {
                                    Text("Permissions")
                                }
                            } else {
                                TextButton(onClick = { viewModel.updateAdminStatus(chatId, member.userId, !member.isAdmin) }) {
                                    Text(if (member.isAdmin) "Revoke Admin" else "Make Admin")
                                }
                            }
                            TextButton(onClick = { viewModel.removeGroupMember(chatId, member.userId) }) {
                                Text("Kick", color = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun ContactsScreen(viewModel: AppViewModel, navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(true) }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val dbContacts by viewModel.contacts.collectAsState()
    
    val permissionState = com.google.accompanist.permissions.rememberPermissionState(
        android.Manifest.permission.READ_CONTACTS
    )

    LaunchedEffect(permissionState.status) {
        if (permissionState.status.isGranted) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val contacts = mutableListOf<com.example.ui.Contact>()
                val cursor = context.contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    val nameIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (it.moveToNext()) {
                        val name = it.getString(nameIndex) ?: ""
                        val number = it.getString(numberIndex) ?: ""
                        if (name.isNotEmpty()) {
                            contacts.add(com.example.ui.Contact(id = java.util.UUID.randomUUID().toString(), name = name, phoneNumber = number))
                        }
                    }
                }
                val uniqueContacts = contacts.distinctBy { it.name }
                viewModel.syncContacts(uniqueContacts)
            }
        } else if (!permissionState.status.shouldShowRationale) {
            permissionState.launchPermissionRequest()
        }
    }

    val filteredContacts = dbContacts.filter { 
        it.name.contains(searchQuery, ignoreCase = true) && it.isRegistered
    }

    val sortedContacts = if (sortByName) {
        filteredContacts.sortedBy { it.name }
    } else {
        filteredContacts.sortedByDescending { it.name.hashCode() } // Pseudo-random time sort for demo
    }

    val groupedContacts = if (sortByName) sortedContacts.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' } else emptyMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новое сообщение") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { sortByName = !sortByName }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* Add contact placeholder */ },
                containerColor = Color(0xFF3B82F6),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.PersonAdd, "Add Contact")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск контактов") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            
            Spacer(Modifier.height(8.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateGroupDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(androidx.compose.material3.MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("Создать группу", style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateChannelDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(androidx.compose.material3.MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("Создать канал", style = MaterialTheme.typography.titleMedium)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val sortText = if (sortByName) "Сортировка по имени" else "Сортировка по времени захода"
                    Text(
                        text = sortText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                    
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (sortByName) {
                            groupedContacts.forEach { (letter, contactsList) ->
                                itemsIndexed(contactsList) { index: Int, contact: com.example.ui.Contact ->
                                    ContactRowItem(
                                        contact = contact,
                                        showLetter = index == 0,
                                        letter = letter.toString(),
                                        onClick = { navController.navigate("chat/${contact.id}") }
                                    )
                                }
                            }
                        } else {
                            items(sortedContacts) { contact ->
                                ContactRowItem(
                                    contact = contact,
                                    showLetter = false,
                                    letter = "",
                                    onClick = { navController.navigate("chat/${contact.id}") }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
    
    if (showCreateGroupDialog) {
        CreateChatDialog(
            isGroup = true,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, desc, photo, isPrivate, linkOrUsername ->
                viewModel.createChat(name, desc, photo, isPrivate, linkOrUsername, true, false)
                showCreateGroupDialog = false
                navController.popBackStack()
            }
        )
    }
    if (showCreateChannelDialog) {
        CreateChatDialog(
            isGroup = false,
            onDismiss = { showCreateChannelDialog = false },
            onCreate = { name, desc, photo, isPrivate, linkOrUsername ->
                viewModel.createChat(name, desc, photo, isPrivate, linkOrUsername, false, true)
                showCreateChannelDialog = false
                navController.popBackStack()
            }
        )
    }
}

@Composable
fun ContactRowItem(contact: com.example.ui.Contact, showLetter: Boolean, letter: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLetter) {
            Text(
                text = letter,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.width(32.dp))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        val color = remember(contact.id) { 
            listOf(Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5), Color(0xFF4CAF50), Color(0xFFFF9800)).random() 
        }
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            val isRecently = remember(contact.id) { Math.random() > 0.5 }
            Text(
                text = if (isRecently) "был(а) недавно" else "был(а) давно",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CreateChatDialog(
    isGroup: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, photoUri: String, isPrivate: Boolean, usernameOrLink: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var generatedLink by remember { mutableStateOf("https://t.me/joinchat/${java.util.UUID.randomUUID().toString().take(8)}") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroup) "Create Group" else "Create Channel") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    if (photoUri.isEmpty()) {
                        IconButton(
                            onClick = { photoUri = "https://picsum.photos/seed/${java.util.UUID.randomUUID()}/200" },
                            modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "Select Photo", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    } else {
                        coil.compose.AsyncImage(
                            model = photoUri,
                            contentDescription = "Selected Photo",
                            modifier = Modifier.size(80.dp).clip(CircleShape).clickable { photoUri = "https://picsum.photos/seed/${java.util.UUID.randomUUID()}/200" },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    maxLines = 3
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Private ${if(isGroup) "Group" else "Channel"}", modifier = Modifier.weight(1f))
                    Switch(checked = isPrivate, onCheckedChange = { 
                        isPrivate = it
                        if (it) generatedLink = "https://t.me/joinchat/${java.util.UUID.randomUUID().toString().take(8)}"
                    })
                }
                
                if (isPrivate) {
                    OutlinedTextField(
                        value = generatedLink,
                        onValueChange = {},
                        label = { Text("Invite Link") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )
                    Text("People can only join via this link.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            if (it.length < 5 && it.isNotEmpty()) {
                                usernameError = "Username must be at least 5 characters"
                            } else if (!it.matches(Regex("^[a-zA-Z0-9_]+$")) && it.isNotEmpty()) {
                                usernameError = "Invalid characters"
                            } else if (it == "admin" || it == "system") {
                                usernameError = "Username is already taken"
                            } else {
                                usernameError = null
                            }
                        },
                        label = { Text("@username") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = usernameError != null,
                        supportingText = { usernameError?.let { Text(it) } }
                    )
                    Text("Public ${if(isGroup) "groups" else "channels"} can be found in search.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val linkOrUsername = if (isPrivate) generatedLink else username
                    onCreate(name, description, photoUri, isPrivate, linkOrUsername)
                    onDismiss()
                },
                enabled = name.isNotBlank() && (isPrivate || (username.isNotBlank() && usernameError == null))
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
@Composable
fun AccountDrawerContent(viewModel: AppViewModel, onCloseDrawer: () -> Unit, navController: NavController, onCreateGroupClick: () -> Unit, onCreateChannelClick: () -> Unit, onCreateSecretChatClick: () -> Unit) {
    val accounts by viewModel.accounts.collectAsState()
    val activeAccount = LocalActiveAccount.current
    var isAccountsExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClick = { isAccountsExpanded = !isAccountsExpanded },
                    onClickLabel = if (isAccountsExpanded) "Свернуть список аккаунтов" else "Развернуть список аккаунтов"
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "Активный профиль: ${activeAccount?.displayName ?: ""}, ${activeAccount?.username ?: ""}. " +
                            if (isAccountsExpanded) "Список аккаунтов развернут" else "Нажмите чтобы развернуть список аккаунтов"
                    role = Role.Button
                }
                .padding(16.dp)
                .padding(top = 24.dp)
        ) {
            AnimatedContent(
                targetState = activeAccount,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400)) { height -> height })
                        .togetherWith(fadeOut(animationSpec = tween(400)) + slideOutVertically(animationSpec = tween(400)) { height -> -height })
                },
                label = "account_header_transition"
            ) { account ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).allowHardware(false)
                                .data(account?.profilePicUrl ?: "")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Фото профиля ${account?.displayName ?: ""}",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(account?.displayName ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(account?.username ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 20.dp)) {
                Icon(
                    if (isAccountsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                AnimatedVisibility(
                    visible = isAccountsExpanded,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                ) {
                    Column {
                        accounts.forEach { account ->
                            NavigationDrawerItem(
                                label = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = account.profilePicUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(account.displayName, fontWeight = if(account.isActive) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                selected = account.isActive,
                                onClick = {
                                    viewModel.switchAccount(account.id)
                                    isAccountsExpanded = false
                                    scope.launch {
                                        kotlinx.coroutines.delay(400)
                                        onCloseDrawer()
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        if (accounts.size < 5) {
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Filled.Add, "Add Account") },
                                label = { Text("Add Account") },
                                selected = false,
                                onClick = { 
                                    viewModel.startAddAccount()
                                    onCloseDrawer() 
                                }
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    }
                }
            }

            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Person, "Profile") },
                    label = { Text("Profile") },
                    selected = false,
                    onClick = { 
                        navController.navigateToTopLevel("my_profile")
                        onCloseDrawer() 
                    }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.SmartToy, "Discover Bots") },
                    label = { Text("Discover Bots") },
                    selected = false,
                    onClick = { 
                        navController.navigateToTopLevel("discover_bots")
                        onCloseDrawer() 
                    }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Group, "New Group") },
                    label = { Text("New Group") },
                    selected = false,
                    onClick = { onCreateGroupClick(); onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.PersonOutline, "Contacts") },
                    label = { Text("Contacts") },
                    selected = false,
                    onClick = { navController.navigateToTopLevel("contacts"); onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Call, "Calls") },
                    label = { Text("Calls") },
                    selected = false,
                    onClick = { navController.navigateToTopLevel("calls"); onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Bookmark, "Saved Messages") },
                    label = { Text("Saved Messages") },
                    selected = false,
                    onClick = { onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Folder, "Папки с чатами") },
                    label = { Text("Папки с чатами") },
                    selected = false,
                    onClick = { 
                        navController.navigate(AppDestinations.SETTINGS_FOLDERS)
                        onCloseDrawer() 
                    }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { 
                        navController.navigateToTopLevel("settings")
                        onCloseDrawer() 
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.PersonAdd, "Invite Friends") },
                    label = { Text("Invite Friends") },
                    selected = false,
                    onClick = { onCloseDrawer() }
                )
            }
            item {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Help, "KuoteX Features") },
                    label = { Text("KuoteX Features") },
                    selected = false,
                    onClick = { onCloseDrawer() }
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            }
            item {
                Text(
                    text = "Админ - панель",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB300)
                )
            }
            item {
                AdminPanelStarsButton(
                    onClick = {
                        navController.navigateToTopLevel("settings/developer_stats")
                        onCloseDrawer()
                    }
                )
            }
        }
    }
}

@Composable
fun AdminPanelStarsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "admin_stars_shimmer")

    // Shimmer offset for light sweep
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_sweep"
    )

    // Pulsing glow alpha for border and background
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Star icon subtle breathing scale & rotation
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    val iconRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_rotation"
    )

    // Telegram Stars gold/amber radiant palette
    val starsPrimaryGold = Color(0xFFFFB300)
    val starsBrightAmber = Color(0xFFFFD54F)
    val starsDeepOrange = Color(0xFFFF6D00)
    val starsGlowYellow = Color(0xFFFFF59D)

    // Shimmer gradient brush
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            starsDeepOrange.copy(alpha = 0.12f * glowAlpha),
            starsPrimaryGold.copy(alpha = 0.28f * glowAlpha),
            starsGlowYellow.copy(alpha = 0.65f * glowAlpha),
            starsPrimaryGold.copy(alpha = 0.28f * glowAlpha),
            starsDeepOrange.copy(alpha = 0.12f * glowAlpha)
        ),
        start = Offset(shimmerTranslate * 350f, 0f),
        end = Offset((shimmerTranslate + 1f) * 350f, 140f)
    )

    val borderGradient = Brush.horizontalGradient(
        listOf(
            starsDeepOrange.copy(alpha = 0.5f * glowAlpha),
            starsBrightAmber.copy(alpha = 0.95f * glowAlpha),
            starsGlowYellow.copy(alpha = 0.75f * glowAlpha),
            starsPrimaryGold.copy(alpha = 0.5f * glowAlpha)
        )
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF221708).copy(alpha = 0.9f),
        border = BorderStroke(1.5.dp, borderGradient),
        shadowElevation = (4.dp * glowAlpha)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(shimmerBrush)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shimmering Golden Star & Shield Badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    starsBrightAmber.copy(alpha = 0.45f),
                                    starsDeepOrange.copy(alpha = 0.2f)
                                )
                            )
                        )
                        .border(1.dp, starsBrightAmber.copy(alpha = glowAlpha), CircleShape)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            rotationZ = iconRotation
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Stars,
                        contentDescription = "Admin Stars",
                        tint = starsBrightAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Админ - панель",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFE082)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Glowing Star Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = starsDeepOrange.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, starsBrightAmber.copy(alpha = glowAlpha))
                        ) {
                            Text(
                                text = "★ DEV",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = starsBrightAmber,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Управление, телеметрия и диагностика",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFECB3).copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = starsBrightAmber.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun ArchivedChatsScreen(viewModel: AppViewModel, navController: NavController) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val typingChats by viewModel.typingChats.collectAsState()
    val userPresences by viewModel.userPresences.collectAsStateWithLifecycle()
    val archivedChats = chats.filter { it.isArchived && !it.isBlocked }

    var showMenu by remember { mutableStateOf(false) }
    var showFAQ by remember { mutableStateOf(false) }

    if (showFAQ) {
        AlertDialog(
            onDismissRequest = { showFAQ = false },
            title = { Text("How does the Archive work?") },
            text = { 
                Text("Archived chats stay hidden from the main list. If a new message arrives in an archived chat with notifications enabled, it will be unarchived. If notifications are disabled, it remains in the archive.") 
            },
            confirmButton = {
                TextButton(onClick = { showFAQ = false }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archived Chats") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Archive Settings") },
                                onClick = { 
                                    showMenu = false
                                    navController.navigate("archive_settings")
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("FAQ") },
                                onClick = { 
                                    showMenu = false
                                    showFAQ = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Help, contentDescription = null)
                                }
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
                .padding(horizontal = 8.dp)
        ) {
            items(archivedChats, key = { it.id }) { chat ->
                SwipeableChatListItem(
                    chat = chat, 
                    isTyping = typingChats.contains(chat.id),
                    viewModel = viewModel,
                    presence = userPresences[chat.id],
                    onClick = { navController.navigate("chat/${chat.id}") },
                    onAvatarClick = { navController.navigate("profile/${chat.id}") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun ArchiveSettingsScreen(navController: NavController) {
    var alwaysKeepInArchive by remember { mutableStateOf(false) }
    var autoArchive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Chats with enabled notifications",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Always keep in archive", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = alwaysKeepInArchive,
                        onCheckedChange = { alwaysKeepInArchive = it }
                    )
                }
                Text(
                    "Keep chats in the archive, even if they have notifications enabled and a new message arrives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )
                
                Text(
                    "New chats with strangers",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Automatically archive", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = autoArchive,
                        onCheckedChange = { autoArchive = it }
                    )
                }
                Text(
                    "Automatically mute new chats, groups, and channels from non-contacts and move them to the archive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderMenuActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (isDestructive) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) Color(0xFFF44336) else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDestructive) Color(0xFFF44336).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

