package com.example.ui
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import com.example.ui.SettingsRegistry
import com.example.ui.SettingsItem



val LocalHighlightId = androidx.compose.runtime.compositionLocalOf<String?> { null }
val LocalSettingsScrollState = androidx.compose.runtime.compositionLocalOf<androidx.compose.foundation.ScrollState?> { null }







@androidx.compose.runtime.Composable
fun HighlightText(text: String, query: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
    if (query.isEmpty()) {
        androidx.compose.material3.Text(text, style = style, color = color)
        return
    }
    val startIndex = text.lowercase().indexOf(query)
    if (startIndex == -1) {
        androidx.compose.material3.Text(text, style = style, color = color)
        return
    }
    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        append(text.substring(0, startIndex))
        withStyle(style = androidx.compose.ui.text.SpanStyle(color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
            append(text.substring(startIndex, startIndex + query.length))
        }
        append(text.substring(startIndex + query.length))
    }
    androidx.compose.material3.Text(annotatedString, style = style, color = color)
}

@androidx.compose.runtime.Composable
fun SearchEmptyState() {
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            )
        )
        androidx.compose.material3.Icon(
            Icons.Filled.SearchOff,
            contentDescription = null,
            modifier = androidx.compose.ui.Modifier.size(64.dp).scale(scale),
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Попробуйте изменить запрос", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@androidx.compose.runtime.Composable
fun SearchResultTile(item: SettingsItem, query: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(item.color), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Icon(item.icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = androidx.compose.ui.Modifier.size(20.dp))
        }
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            HighlightText(
                text = item.title,
                query = query,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(2.dp))
            HighlightText(
                text = item.categoryPath,
                query = query,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@androidx.compose.runtime.Composable
fun Modifier.settingTarget(id: String): Modifier {
    val highlightId = LocalHighlightId.current
    val scrollState = LocalSettingsScrollState.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var highlighted by remember { mutableStateOf(false) }
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (highlighted) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(1500)
    )

    LaunchedEffect(highlightId) {
        if (highlightId == id) {
            highlighted = true
            kotlinx.coroutines.delay(1500)
            highlighted = false
        }
    }

    return this
        .background(backgroundColor)
        .onGloballyPositioned { coordinates ->
            if (highlighted && highlightId == id && scrollState != null) {
                coroutineScope.launch {
                    val y = coordinates.positionInParent().y
                    scrollState.animateScrollTo(y.toInt())
                }
            }
        }
}






data class DeepSettingItem(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val categoryPath: String,
    val routeAction: String
)



@androidx.compose.runtime.Composable
fun HighlightedText(text: String, query: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
    if (query.isEmpty()) {
        androidx.compose.material3.Text(text, style = style, color = color)
        return
    }
    val startIndex = text.lowercase().indexOf(query)
    if (startIndex == -1) {
        androidx.compose.material3.Text(text, style = style, color = color)
        return
    }
    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        append(text.substring(0, startIndex))
        withStyle(style = androidx.compose.ui.text.SpanStyle(color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
            append(text.substring(startIndex, startIndex + query.length))
        }
        append(text.substring(startIndex + query.length))
    }
    androidx.compose.material3.Text(annotatedString, style = style, color = color)
}







@Composable
fun SettingsListItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsSimpleItem(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsMenuScreen(viewModel: AppViewModel, navController: NavController) {
    val activeAccount = LocalActiveAccount.current ?: return
    val accounts by viewModel.accounts.collectAsState()
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    
    var isSearchMode by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val debouncedQuery by viewModel.debouncedSearchQuery.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out from this account?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirmDialog = false
                    viewModel.logout()
                    navController.popBackStack()
                }) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (isSearchMode) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search settings...") },
                            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchMode = false 
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            } else {
                TopAppBar(
                    title = { 
                        Text(
                            "KuoteX",
                            modifier = Modifier.alpha(1f - scrollBehavior.state.collapsedFraction)
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showLogoutConfirmDialog = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isSearchMode) {
            val filtered = if (debouncedQuery.isEmpty()) {
                emptyList()
            } else {
                SettingsRegistry.items.filter { item ->
                    item.title.lowercase().contains(debouncedQuery) ||
                    item.categoryPath.lowercase().contains(debouncedQuery) ||
                    item.keywords.any { it.contains(debouncedQuery) }
                }
            }
            
            Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (filtered.isEmpty() && debouncedQuery.isNotEmpty()) {
                    SearchEmptyState()
                } else if (debouncedQuery.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered) { item ->
                            SearchResultTile(item = item, query = debouncedQuery) {
                                // Simulate Deep Navigation
                                                                navController.navigate("${item.routeAction}?highlightId=${item.id}")
                                isSearchMode = false
                                viewModel.setSearchQuery("")
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
            // Profile section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    val imageUrl = activeAccount.profilePicUrl.ifEmpty { "https://picsum.photos/seed/${activeAccount.id}/150" }
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier.size(100.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6))
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                            .clickable { navController.navigateSafe("settings/profile") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = activeAccount.displayName.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                val phoneNumber = activeAccount.phoneNumber.ifBlank { "+7 (922) 669-26-82" }
                val usernameText = if(activeAccount.username.startsWith("@")) activeAccount.username else "@${activeAccount.username}"
                Text(
                    text = "$phoneNumber • $usernameText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Accounts Block
            Text(
                "Аккаунты", 
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp), 
                color = MaterialTheme.colorScheme.primary, 
                style = MaterialTheme.typography.titleMedium
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column {
                    accounts.forEachIndexed { index, account ->
                        val isCurrent = account.id == activeAccount.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isCurrent) {
                                        viewModel.switchAccount(account.id)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val accountImageUrl = account.profilePicUrl.ifEmpty { "https://picsum.photos/seed/${account.id}/100" }
                            AsyncImage(
                                model = accountImageUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = account.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isCurrent) {
                                Icon(Icons.Filled.Check, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Switch", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.addAccountAction() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Account", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Добавить аккаунт",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Settings Categories Block
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column {
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF3B82F6)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Аккаунт",
                        subtitle = "Номер, имя пользователя, «О себе»",
                        onClick = { navController.navigateSafe("settings/profile") }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF59E0B)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.ChatBubble, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Настройки чатов",
                        subtitle = "Обои, ночной режим, анимации",
                        onClick = { navController.navigateSafe("settings/themes") }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF10B981)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.VpnKey, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Конфиденциальность",
                        subtitle = "Время захода, устройства, ключи доступа",
                        onClick = { navController.navigateSafe("settings/security") }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFEF4444)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Notifications, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Уведомления",
                        subtitle = "Звуки, звонки, счётчик сообщений",
                        onClick = { navController.navigateSafe("settings/general") }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF3B82F6)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.DataUsage, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Данные и память",
                        subtitle = "Настройки загрузки медиафайлов",
                        onClick = { navController.navigateSafe("settings/storage") }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2196F3)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Folder, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Папки с чатами",
                        subtitle = "Сортировка чатов по папкам",
                        onClick = { navController.navigateSafe(com.example.ui.navigation.AppDestinations.SETTINGS_FOLDERS) }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF00BCD4)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Devices, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Устройства",
                        subtitle = "Управление активными сеансами",
                        onClick = { navController.navigateSafe("settings/devices") }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFF9800)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.BatterySaver, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Энергосбережение",
                        subtitle = "Экономия энергии при низком заряде",
                        onClick = { navController.navigateSafe("settings/battery") }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF9C27B0)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Language, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                        title = "Язык",
                        subtitle = "Русский",
                        onClick = { navController.navigateSafe("settings/language") }
                    )
                    SettingsListItem(
                        icon = { Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF00E5FF)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.CloudSync, null, tint = Color.Black, modifier = Modifier.size(20.dp)) } },
                        title = "Remote Config",
                        subtitle = "Динамические флаги возможностей и параметры",
                        onClick = { navController.navigateSafe("settings/remote_config") }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        }
    }
}

// Added from replacement_profile.txt
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsProfileScreen(viewModel: AppViewModel, navController: NavController) {
    val activeAccount = com.example.ui.LocalActiveAccount.current ?: return
    
    var username by remember { mutableStateOf(activeAccount.username.removePrefix("@")) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf(activeAccount.displayName) }
    var bio by remember { mutableStateOf(activeAccount.bio) }
    var customStatus by remember { mutableStateOf(activeAccount.customStatus) }
    var profilePicUrl by remember { mutableStateOf(activeAccount.profilePicUrl) }
    
    val avatars = remember(profilePicUrl) {
        listOf(
            profilePicUrl.ifEmpty { "https://picsum.photos/seed/${activeAccount.id}/400" },
            "https://picsum.photos/seed/${activeAccount.id}_1/400",
            "https://picsum.photos/seed/${activeAccount.id}_2/400"
        )
    }
    val pagerState = rememberPagerState(pageCount = { avatars.size })
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("Profile Management") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.size(160.dp)) {
                val context = androidx.compose.ui.platform.LocalContext.current
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
                
                Box(modifier = Modifier.fillMaxSize().clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1) % avatars.size)
                            }
                        }
                    ) { page ->
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).allowHardware(false)
                                .data(avatars[page])
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    
                    // Pager Indicators
                    Row(
                        Modifier
                            .height(20.dp)
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(avatars.size) { iteration ->
                            val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .height(4.dp)
                                    .width(if (pagerState.currentPage == iteration) 16.dp else 8.dp)
                            )
                        }
                    }
                }
                
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        profilePicUrl = uri.toString()
                    }
                }
                
                IconButton(
                    onClick = { 
                        launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(42.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Change Photo", tint = Color.White, modifier = Modifier.padding(8.dp))
                }
            }
            
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    if (it.contains("@")) {
                        usernameError = "Символ @ не требуется"
                    } else if (it.length < 5 && it.isNotEmpty()) {
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
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = customStatus,
                onValueChange = { customStatus = it },
                label = { Text("Custom Status") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    if (usernameError == null) {
                        val finalUsername = "@${username.removePrefix("@")}"
                        viewModel.updateProfile(activeAccount.id, finalUsername, displayName, bio, profilePicUrl, customStatus)
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes")
            }
        }
    }
}

// Added from replacement.txt
// calculatePasswordStrength removed because it is in Utils.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoStepVerificationScreen(navController: NavController) {
    var password by remember { mutableStateOf("") }
    val strength = remember(password) { calculatePasswordStrength(password) }
    
    val strengthColor = when (strength) {
        0 -> Color.Gray
        1, 2 -> Color(0xFFFF1744) // Neon Red
        3 -> Color(0xFFFFEA00) // Neon Yellow
        4, 5 -> Color(0xFF00E676) // Neon Green
        else -> Color.Gray
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = strength / 5f,
        animationSpec = tween(300)
    )
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = { Text("Cloud Password") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text("🙈", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Create a password", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Enter password") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Password Strength",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (strength) {
                        0 -> ""
                        1, 2 -> "Weak"
                        3 -> "Fair"
                        4, 5 -> "Strong"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = strengthColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color.DarkGray, CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animatedProgress)
                        .height(6.dp)
                        .background(strengthColor, CircleShape)
                        .border(1.dp, strengthColor, CircleShape)
                        .shadow(
                            elevation = if (strength > 0) 8.dp else 0.dp,
                            shape = CircleShape,
                            ambientColor = strengthColor,
                            spotColor = strengthColor
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSecurityScreen(viewModel: AppViewModel, navController: NavController) {
    var deleteAccountDialogVisible by remember { mutableStateOf(false) }
    var immediateDeleteDialogVisible by remember { mutableStateOf(false) }
    val activeAccount = LocalActiveAccount.current ?: return
    var deleteAccountValue by remember { mutableStateOf("6 months") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy and Security") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        val highlightId by viewModel.highlightEvent.collectAsState()
        val scrollState = rememberScrollState()
        androidx.compose.runtime.CompositionLocalProvider(
            LocalHighlightId provides highlightId,
            LocalSettingsScrollState provides scrollState
        ) {
                        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(scrollState)) {
                
                @Composable
                fun PrivacySectionCard(title: String, content: @Composable () -> Unit) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(title, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        androidx.compose.material3.Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.fillMaxWidth().background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    )
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                content()
                            }
                        }
                    }
                }
                
                PrivacySectionCard("Security") {
                    val isPasscodeEnabled = activeAccount.encryptedPasscode != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigateSafe("settings/passcode") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Passcode Lock", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (isPasscodeEnabled) "On" else "Off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isPasscodeEnabled, onCheckedChange = { navController.navigateSafe("settings/passcode") })
                    }
                    SettingsListItem(icon = { Icon(Icons.Filled.VpnKey, null, tint = MaterialTheme.colorScheme.primary) }, title = "Two-Step Verification", subtitle = "Off", onClick = { navController.navigateSafe("settings/two_step") })
                    SettingsListItem(icon = { Icon(Icons.Filled.Email, null, tint = MaterialTheme.colorScheme.primary) }, title = "Login Email", subtitle = "None", onClick = { navController.navigateSafe("settings/email") })
                }
                
                PrivacySectionCard("Privacy") {
                    SettingsListItem(icon = { Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.primary) }, title = "Blocked Users", subtitle = "0 users", onClick = { navController.navigateSafe("settings/blocked_users") })
                    SettingsSimpleItem(title = "Phone Number", value = "Nobody", onClick = { navController.navigateSafe("settings/privacy/Phone Number") })
                    SettingsSimpleItem(title = "Last Seen & Online", value = "Everybody", onClick = { navController.navigateSafe("settings/privacy/Last Seen") })
                    SettingsSimpleItem(title = "Profile Photos", value = "Everybody", onClick = { navController.navigateSafe("settings/privacy/Profile Photos") })
                    SettingsSimpleItem(title = "Calls", value = "Everybody", onClick = { navController.navigateSafe("settings/privacy/Calls") })
                }
                
                PrivacySectionCard("Data and Backup") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    var isExporting by remember { mutableStateOf(false) }
                    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
                    ) { uri ->
                        if (uri != null) {
                            isExporting = true
                            coroutineScope.launch {
                                val success = com.example.data.BackupUtility.exportDatabase(context, uri)
                                isExporting = false
                                android.widget.Toast.makeText(context, if (success) "Backup exported successfully" else "Backup failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    SettingsListItem(
                        icon = { Icon(Icons.Filled.Backup, null, tint = MaterialTheme.colorScheme.primary) },
                        title = "Export Local Backup",
                        subtitle = if (isExporting) "Exporting..." else "Save encrypted database backup",
                        onClick = {
                            exportLauncher.launch("messenger_backup_${System.currentTimeMillis()}.zip")
                        }
                    )
                }

                PrivacySectionCard("Delete My Account") {
                    SettingsSimpleItem(title = "If away for", value = deleteAccountValue, onClick = { deleteAccountDialogVisible = true })
                }
                
                Spacer(Modifier.height(16.dp))
        }

        if (immediateDeleteDialogVisible) {
            AlertDialog(
                onDismissRequest = { immediateDeleteDialogVisible = false },
                title = { Text("Delete Account") },
                text = { Text("Are you sure you want to delete your account? This action will wipe all local data and initiate the server-side deletion process. This cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAccount(activeAccount.id) {
                                immediateDeleteDialogVisible = false
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { immediateDeleteDialogVisible = false }) { Text("Cancel") }
                }
            )
        }
        
        if (deleteAccountDialogVisible) {
            AlertDialog(
                onDismissRequest = { deleteAccountDialogVisible = false },
                title = { Text("Self-Destruct if inactive for...") },
                text = {
                    Column {
                        listOf("1 month", "3 months", "6 months", "1 year").forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { 
                                    deleteAccountValue = option
                                    deleteAccountDialogVisible = false
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = deleteAccountValue == option, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(option)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { deleteAccountDialogVisible = false }) { Text("Cancel") }
                }
            )
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGeneralScreen(viewModel: AppViewModel, navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Уведомления и звуки") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        val highlightId by viewModel.highlightEvent.collectAsState()
        val scrollState = rememberScrollState()
        androidx.compose.runtime.CompositionLocalProvider(
            LocalHighlightId provides highlightId,
            LocalSettingsScrollState provides scrollState
        ) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(scrollState)) {
                Text("Общие уведомления", modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                
                var notificationsEnabled by remember { mutableStateOf(true) }
                var soundsEnabled by remember { mutableStateOf(true) }
                var mentionsHighPriority by remember { mutableStateOf(true) }
                var bubblesEnabled by remember { mutableStateOf(true) }
                
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Всплывающие уведомления (FCM)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Получать Push-сообщения в фоне и на заблокированном экране", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Упоминания и ответы (@username)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Приоритетные уведомления при упоминании в групповых чатах", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = mentionsHighPriority, onCheckedChange = { mentionsHighPriority = it })
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Всплывающая FCP система (В приложении)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Интерактивный плавающий баннер с подсветкой @username, быстрым ответом и отметкой о прочтении", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = bubblesEnabled, onCheckedChange = { bubblesEnabled = it })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("1. Тест FCP всплывающих уведомлений (В приложении)", modifier = Modifier.padding(start = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Срабатывает, пока вы находитесь в приложении KuoteX (вне активного диалога). Показывает стильный плавающий баннер с подсветкой @username:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // FCP Test 1: Group Mention
                        FilledTonalButton(
                            onClick = {
                                viewModel.triggerSimulatedPushNotification(
                                    chatId = "c1",
                                    senderName = "Billy Herrington",
                                    text = "@neo_hacker Посмотри новый патч безопасности в morpheus empire! 🚀",
                                    isMention = true,
                                    context = context,
                                    forceSystemNotification = false,
                                    isGroup = true,
                                    isChannel = false,
                                    customChatTitle = "morpheus empire 👑"
                                )
                                android.widget.Toast.makeText(context, "FCP баннер отправлен в приложение!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFFB300))
                            Spacer(Modifier.width(8.dp))
                            Text("FCP: Упоминание в группе (morpheus empire 👑)")
                        }

                        // FCP Test 2: Channel Mention
                        FilledTonalButton(
                            onClick = {
                                viewModel.triggerSimulatedPushNotification(
                                    chatId = "c2",
                                    senderName = "KuoteX Official",
                                    text = "@neo_hacker Внимание всем подписчикам канала: доступно обновление FCP!",
                                    isMention = true,
                                    context = context,
                                    forceSystemNotification = false,
                                    isGroup = false,
                                    isChannel = true,
                                    customChatTitle = "KuoteX News 📢"
                                )
                                android.widget.Toast.makeText(context, "FCP баннер канала отправлен!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF00E5FF))
                            Spacer(Modifier.width(8.dp))
                            Text("FCP: Упоминание в канале (KuoteX News 📢)")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("2. Тест уведомлений на шторке экрана (Вне приложения)", modifier = Modifier.padding(start = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Срабатывает в шторке экрана Android со стилизацией группы/канала и кнопками «ОТВЕТИТЬ» (с вводом) и «ОТМЕТИТЬ ПРОЧИТАННЫМ»:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Delayed Background Test (allows minimizing app)
                        Button(
                            onClick = {
                                android.widget.Toast.makeText(
                                    context,
                                    "Сверните приложение! Через 3 сек придёт уведомление на шторку...",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()

                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(3000)
                                    viewModel.triggerSimulatedPushNotification(
                                        chatId = "c1",
                                        senderName = "Billy Herrington",
                                        text = "@neo_hacker Имба\nНе имба\nТолько в рот",
                                        isMention = true,
                                        context = context,
                                        forceSystemNotification = true,
                                        isGroup = true,
                                        isChannel = false,
                                        customChatTitle = "morpheus empire 👑"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Свернуть приложение и получить в шторку (через 3 сек)")
                        }

                        // Immediate Shade Test
                        OutlinedButton(
                            onClick = {
                                viewModel.triggerSimulatedPushNotification(
                                    chatId = "c1",
                                    senderName = "Billy Herrington",
                                    text = "@neo_hacker Имба\nНе имба\nТолько в рот",
                                    isMention = true,
                                    context = context,
                                    forceSystemNotification = true,
                                    isGroup = true,
                                    isChannel = false,
                                    customChatTitle = "morpheus empire 👑"
                                )
                                android.widget.Toast.makeText(context, "Уведомление отправлено на шторку экрана!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Отправить на шторку сейчас (проверить кнопки)")
                        }
                    }
                }
                
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsThemesScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    val currentTheme by viewModel.theme.collectAsState()
    val isAutoThemeEnabled by viewModel.isAutoThemeEnabled.collectAsState()
    val isDarkThemeEnabled by viewModel.isDarkThemeEnabled.collectAsState()
    val isQrSnowflakesEnabled by viewModel.isQrSnowflakesEnabled.collectAsState()
    val currentPrimary by viewModel.customPrimaryColor.collectAsState()
    val batterySaverEnabled by viewModel.batterySaverEnabled.collectAsState()
    val themeOpacity by viewModel.themeOpacity.collectAsState()

    val colors = listOf(
        Pair(Color(0xFFBB86FC), "Purple"),
        Pair(Color(0xFF6200EE), "Deep Purple"),
        Pair(Color(0xFF03DAC5), "Teal"),
        Pair(Color(0xFF00C853), "Green"),
        Pair(Color(0xFFFFD600), "Yellow"),
        Pair(Color(0xFFFF3D00), "Orange"),
        Pair(Color(0xFFD50000), "Red"),
        Pair(Color(0xFF2962FF), "Blue")
    )
    
    val themes = AppTheme.values().toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки чатов") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { viewModel.resetTheme() }) {
                        Text("Reset")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val highlightId by viewModel.highlightEvent.collectAsState()
        val scrollState = rememberScrollState()
        androidx.compose.runtime.CompositionLocalProvider(
            LocalHighlightId provides highlightId,
            LocalSettingsScrollState provides scrollState
        ) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(scrollState)) {
            
            Text("Неоновые темы", modifier = Modifier.settingTarget("theme_color").padding(16.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(themes) { theme ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(100.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp, 150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    3.dp,
                                    if (currentTheme == theme) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.switchTheme(theme) }
                        ) {
                            when (theme) {
                                AppTheme.NEON_SNOWFLAKES -> NeonSnowflakesBackground(isBatterySaver = batterySaverEnabled, opacity = 1f)
                                AppTheme.NEON_CHERRY_BLOSSOM -> NeonCherryBlossomBackground(isBatterySaver = batterySaverEnabled, opacity = 1f)
                                AppTheme.NEON_CONFETTI -> NeonConfettiBackground(isBatterySaver = batterySaverEnabled, opacity = 1f)
                                AppTheme.NEON_MOON -> NeonMoonBackground(opacity = 1f)
                                AppTheme.NEON_ROOM_FOG -> NeonRoomFogBackground(opacity = 1f)
                                AppTheme.DEFAULT -> ElegantDarkBackground(opacity = 1f)
                            }
                            if (currentTheme == theme) {
                                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = theme.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            
            Text("Цвет акцента", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(colors) { colorPair ->
                    val colorValue = colorPair.first.value.toLong()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colorPair.first)
                            .border(
                                3.dp,
                                if (currentPrimary == colorValue) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                CircleShape
                            )
                            .clickable { viewModel.setCustomPrimaryColor(colorValue) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentPrimary == colorValue) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = if (colorPair.first.red * 0.299 + colorPair.first.green * 0.587 + colorPair.first.blue * 0.114 > 0.5f) Color.Black else Color.White)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Интенсивность цвета", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = themeOpacity,
                onValueChange = { viewModel.setThemeOpacity(it) },
                valueRange = 0.1f..1f,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            
            Text("Настройки темы", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setAutoThemeEnabled(!isAutoThemeEnabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Автоматическая тема", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = isAutoThemeEnabled,
                        onCheckedChange = { viewModel.setAutoThemeEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                if (!isAutoThemeEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDarkThemeEnabled(!isDarkThemeEnabled) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Тёмная тема", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = isDarkThemeEnabled,
                            onCheckedChange = { viewModel.setDarkThemeEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.2f), modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setQrSnowflakesEnabled(!isQrSnowflakesEnabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("QR Code Snowflakes", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = isQrSnowflakesEnabled,
                        onCheckedChange = { viewModel.setQrSnowflakesEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    }
}
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsAccountsScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    val accounts by viewModel.accounts.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Accounts") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> 
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            accounts.forEach { account ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.switchAccount(account.id) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Text(account.username.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(account.username, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (account.isActive) {
                        Icon(Icons.Filled.Check, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.addAccountAction() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, contentDescription = "Add Account", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Add Account", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PasscodeLockScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    val activeAccount = LocalActiveAccount.current
    var isEnabled by remember { mutableStateOf(activeAccount?.encryptedPasscode != null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Passcode Lock") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> 
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().clickable { isEnabled = !isEnabled }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Passcode Lock", style = MaterialTheme.typography.titleMedium)
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
            }
            if (isEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Text("Change Passcode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text("When a passcode is set, a lock icon appears on the chats page. Tap it to lock your app.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LoginEmailScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    val activeAccount = LocalActiveAccount.current
    val email = activeAccount?.username?.takeIf { it.contains("@") } ?: "None"
    Scaffold(topBar = { TopAppBar(title = { Text("Login Email") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> 
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text("Current Email", modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(email, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { navController.navigate("settings/verify_email") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(if (email == "None") "Set Email" else "Change Email")
            }
            Text("Your email is used to log in and to reset your Two-Step Verification password if you forget it.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VerifyEmailScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Verify Email") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> 
        Column(modifier = Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))
            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("We've sent a verification code to your new email.", modifier = Modifier.padding(horizontal = 32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(value = "", onValueChange = {}, label = { Text("Code") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                Text("Verify")
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Devices") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> 
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text("Active Sessions", modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            SettingsListItem(icon = { Icon(Icons.Filled.PhoneAndroid, null) }, title = "KuoteX Android", subtitle = "Online • Current device", onClick = {})
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Terminate All Other Sessions")
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(viewModel: AppViewModel, navController: androidx.navigation.NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Blocked Users") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> 
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No blocked users", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingScreen(navController: androidx.navigation.NavController, title: String) {
    var selectedOption by remember { mutableStateOf("Everybody") }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding -> 
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text("Who can see my $title", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            listOf("Everybody", "My Contacts", "Nobody").forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selectedOption = option }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedOption == option, onClick = { selectedOption = option })
                    Spacer(Modifier.width(16.dp))
                    Text(option, style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Changes to your privacy settings may take a few minutes to apply.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLanguageScreen(viewModel: AppViewModel, navController: NavController) {
    val languages = listOf(
        Pair("Русский", "🇷🇺"),
        Pair("English", "🇬🇧"),
        Pair("Қазақша", "🇰🇿"),
        Pair("Українська", "🇺🇦"),
        Pair("Беларуская", "🇧🇾"),
        Pair("Oʻzbekcha", "🇺🇿")
    )
    
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "flagWave")
    val waveRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "wave"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Язык") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        val highlightId by viewModel.highlightEvent.collectAsState()
        val scrollState = rememberScrollState()
        androidx.compose.runtime.CompositionLocalProvider(
            LocalHighlightId provides highlightId,
            LocalSettingsScrollState provides scrollState
        ) {
            Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(scrollState)) {
            languages.forEach { (lang, flag) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* Handle Language Selection */ }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = flag, 
                        fontSize = 24.sp, 
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .graphicsLayer {
                                rotationZ = waveRotation
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f)
                            }
                    )
                    Text(text = lang, style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }
        }
    }
}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBatterySaverScreen(viewModel: AppViewModel, navController: NavController) {
    val batterySaverEnabled by viewModel.batterySaverEnabled.collectAsState()
    val themeOpacity by viewModel.themeOpacity.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Энергосбережение") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Режим энергосбережения", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Отключает тяжёлые анимации, неоновые эффекты и фоновые процессы для экономии заряда батареи.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = batterySaverEnabled,
                    onCheckedChange = { viewModel.setBatterySaverEnabled(it) }
                )
            }
        }
    }
}




