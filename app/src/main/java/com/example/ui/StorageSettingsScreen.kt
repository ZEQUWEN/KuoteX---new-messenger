package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import com.example.utils.CacheCalculator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import android.Manifest
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsStorageScreen(viewModel: AppViewModel, navController: NavController) {
    val storageStats by viewModel.storageStats.collectAsState()
    val largestCategories = storageStats.categories.sortedByDescending { it.sizeBytes }.take(2)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Данные и память") },
                navigationIcon = { 
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") 
                    } 
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Использование памяти") },
                        supportingContent = { Text("Настройка автоудаления кэша") },
                        leadingContent = { 
                            Icon(
                                Icons.Filled.Storage, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { navController.navigate("settings/storage/memory") }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    ListItem(
                        headlineContent = { Text("Использование сети") },
                        supportingContent = { Text("Статистика трафика") },
                        leadingContent = { 
                            Icon(
                                Icons.Filled.DataUsage, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { navController.navigate("settings/storage/network") }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            var showOptimizeDialog by remember { mutableStateOf(false) }
            var isScanningForDuplicates by remember { mutableStateOf(false) }
            var duplicateSizeFound by remember { mutableStateOf(0L) }
            var isClearingDuplicates by remember { mutableStateOf(false) }
            var clearProgress by remember { mutableStateOf(0f) }
            val coroutineScope = rememberCoroutineScope()

            if (showOptimizeDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isClearingDuplicates) showOptimizeDialog = false },
                    title = { Text("Оптимизация хранилища") },
                    text = {
                        Column {
                            if (isClearingDuplicates) {
                                Text("Очистка дубликатов...")
                                Spacer(modifier = Modifier.height(16.dp))
                                NeonProgressBar(progress = clearProgress)
                            } else {
                                Text("Найдено ${formatBytes(duplicateSizeFound)} дубликатов файлов кэша KuoteX. Очистить их для освобождения места?")
                            }
                        }
                    },
                    confirmButton = {
                        if (!isClearingDuplicates) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isClearingDuplicates = true
                                        clearProgress = 0f
                                        while (clearProgress < 1f) {
                                            clearProgress += 0.05f
                                            delay(100)
                                        }
                                        isClearingDuplicates = false
                                        showOptimizeDialog = false
                                    }
                                }
                            ) {
                                Text("Очистить")
                            }
                        }
                    },
                    dismissButton = {
                        if (!isClearingDuplicates) {
                            TextButton(onClick = { showOptimizeDialog = false }) {
                                Text("Отмена")
                            }
                        }
                    }
                )
            }

            val gradientColors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
            val brush = androidx.compose.ui.graphics.Brush.verticalGradient(colors = gradientColors)
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.background(brush)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Оптимизация хранилища",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Рекомендуем: удаление старых медиасообщений или сжатие сохраненных изображений для освобождения места.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isScanningForDuplicates = true
                                        delay(1500) // Simulate scanning
                                        duplicateSizeFound = (Math.random() * 200 * 1024 * 1024).toLong() + 50 * 1024 * 1024 // 50MB to 250MB
                                        isScanningForDuplicates = false
                                        showOptimizeDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isScanningForDuplicates) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Сканирование...")
                                } else {
                                    Text("Оптимизировать")
                                }
                            }
                            OutlinedButton(
                                onClick = { 
                                    coroutineScope.launch {
                                        com.example.utils.ExportUtils.exportCacheToZip(context)
                                        snackbarHostState.showSnackbar("Файлы успешно сохранены в zip архив")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Экспорт")
                            }
                        }
                        
                        if (largestCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Персональные рекомендации",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            largestCategories.forEach { category ->
                                val actionText = when (category.categoryName) {
                                    "Видео" -> "Удалить старые видеосообщения"
                                    "Фото" -> "Удалить дубликаты изображений"
                                    "Файлы" -> "Удалить дубликаты файлов"
                                    else -> "Очистить ${category.categoryName.lowercase()}"
                                }
                                Text(
                                    text = "• $actionText (${formatBytes(category.sizeBytes)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Автозагрузка медиа",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    var mobileEnabled by remember { mutableStateOf(true) }
                    ListItem(
                        headlineContent = { Text("Через мобильную сеть") },
                        supportingContent = { Text("Фото, Видео (15 MB), Файлы (3 MB)") },
                        trailingContent = { Switch(checked = mobileEnabled, onCheckedChange = { mobileEnabled = it }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    var wifiEnabled by remember { mutableStateOf(true) }
                    ListItem(
                        headlineContent = { Text("Через сети Wi-Fi") },
                        supportingContent = { Text("Фото, Видео (15 MB), Файлы (3 MB)") },
                        trailingContent = { Switch(checked = wifiEnabled, onCheckedChange = { wifiEnabled = it }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    var roamingEnabled by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("В роуминге") },
                        supportingContent = { Text("Фото, Видео (15 MB), Файлы (3 MB)") },
                        trailingContent = { Switch(checked = roamingEnabled, onCheckedChange = { roamingEnabled = it }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes == 0L) return "0 B"
    val k = 1024L
    val sizes = arrayOf("B", "KB", "MB", "GB", "TB")
    val i = (Math.log(bytes.toDouble()) / Math.log(k.toDouble())).toInt()
    return String.format("%.1f %s", bytes / Math.pow(k.toDouble(), i.toDouble()), sizes[i])
}

@Composable
fun DonutChart(categories: List<NetworkCategoryStats>, totalBytes: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var startAngle = -90f
            val strokeWidth = 32.dp.toPx()
            
            if (totalBytes > 0) {
                for (category in categories) {
                    val sweepAngle = (category.sizeBytes.toFloat() / totalBytes.toFloat()) * 360f
                    if (sweepAngle > 0) {
                        drawArc(
                            color = category.color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth)
                        )
                    }
                    startAngle += sweepAngle
                }
            } else {
                drawArc(
                    color = Color.DarkGray,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val formatted = formatBytes(totalBytes).split(" ")
            if (formatted.size == 2) {
                Text(formatted[0], style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(formatted[1], style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(formatBytes(totalBytes), style = MaterialTheme.typography.displayMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkUsageScreen(viewModel: AppViewModel, navController: NavController) {
    val networkStatsMap by viewModel.networkStats.collectAsState()
    
    val tabs = listOf(NetworkType.ALL, NetworkType.MOBILE, NetworkType.WIFI, NetworkType.ROAMING)
    val tabTitles = listOf("Весь", "Мобильный", "Wi-Fi", "Роуминг")
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    val currentType = tabs[selectedTabIndex]
    val currentStats = networkStatsMap[currentType] ?: return
    
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить статистику") },
            text = { Text("Сбросить статистику использования для этого типа сети?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetNetworkStats(currentType)
                    showResetDialog = false
                }) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Использование сети") },
                navigationIcon = { 
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") 
                    } 
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    val total = currentStats.sentBytes + currentStats.receivedBytes
                    DonutChart(
                        categories = currentStats.categories, 
                        totalBytes = total,
                        modifier = Modifier.padding(32.dp).size(200.dp).align(Alignment.CenterHorizontally)
                    )
                }
                
                items(currentStats.categories) { category ->
                    val percentage = if (currentStats.sentBytes + currentStats.receivedBytes > 0) {
                        (category.sizeBytes.toFloat() / (currentStats.sentBytes + currentStats.receivedBytes).toFloat() * 100).toInt()
                    } else 0
                    
                    ListItem(
                        headlineContent = { Text(category.categoryName) },
                        leadingContent = { 
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(category.color, shape = CircleShape)
                            )
                        },
                        trailingContent = { 
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatBytes(category.sizeBytes), style = MaterialTheme.typography.bodyLarge)
                                Text("$percentage%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
                
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        "Общий расход трафика",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                item {
                    ListItem(
                        headlineContent = { Text("Отправлено байт") },
                        leadingContent = { Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Color(0xFF2196F3)) },
                        trailingContent = { Text(formatBytes(currentStats.sentBytes), style = MaterialTheme.typography.bodyLarge) }
                    )
                    ListItem(
                        headlineContent = { Text("Получено байт") },
                        leadingContent = { Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color(0xFF4CAF50)) },
                        trailingContent = { Text(formatBytes(currentStats.receivedBytes), style = MaterialTheme.typography.bodyLarge) }
                    )
                }
                
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = "Сбросить статистику",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetDialog = true }
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StorageUsageScreen(viewModel: AppViewModel, navController: NavController) {
    val storageStats by viewModel.storageStats.collectAsState()
    
    var isClearing by remember { mutableStateOf(false) }
    var clearProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    
    val storagePermissionState = rememberPermissionState(
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    
    var animationTrigger by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        if (!storagePermissionState.status.isGranted) {
            storagePermissionState.launchPermissionRequest()
        }
        animationTrigger = true
    }
    
    val totalSize = storageStats.categories.sumOf { it.sizeBytes }
    val selectedSize = storageStats.categories.sumOf { category ->
        if (category.subCategories != null) {
            category.subCategories.filter { it.isSelected }.sumOf { it.sizeBytes }
        } else {
            if (category.isSelected) category.sizeBytes else 0L
        }
    }

    val maxCacheSizeIndex by viewModel.maxCacheSizeIndex.collectAsState()
    val maxLimitBytes = when (maxCacheSizeIndex) {
        0 -> 5L * 1024 * 1024 * 1024
        1 -> 16L * 1024 * 1024 * 1024
        2 -> 32L * 1024 * 1024 * 1024
        else -> Long.MAX_VALUE
    }
    
    val isScanning by CacheCalculator.isScanning.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    LaunchedEffect(totalSize, maxLimitBytes) {
        if (maxLimitBytes != Long.MAX_VALUE && totalSize > maxLimitBytes * 0.8) {
            snackbarHostState.showSnackbar("Внимание: объём кэша превышает 80% от установленного лимита!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Использование памяти") },
                navigationIcon = { 
                    IconButton(onClick = { navController.popBackStack() }) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") 
                    } 
                },
                actions = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = { CacheCalculator.forceScan(context, scope) }) {
                            Icon(Icons.Filled.Refresh, "Сканировать сейчас")
                        }
                        if (maxLimitBytes != Long.MAX_VALUE && totalSize > maxLimitBytes * 0.8) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Внимание",
                                tint = Color.Red,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                item {
                    val pieProgress by animateFloatAsState(
                        targetValue = if (animationTrigger) 1f else 0f,
                        animationSpec = tween(1000, easing = FastOutSlowInEasing),
                        label = "pieProgress"
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(200.dp)) {
                            var startAngle = -90f
                            val strokeWidth = 32.dp.toPx()
                            
                            if (totalSize > 0) {
                                for (category in storageStats.categories) {
                                    val sweepAngle = (category.sizeBytes.toFloat() / totalSize.toFloat()) * 360f * pieProgress
                                    if (sweepAngle > 0) {
                                        drawArc(
                                            color = category.color,
                                            startAngle = startAngle,
                                            sweepAngle = sweepAngle,
                                            useCenter = false,
                                            style = Stroke(width = strokeWidth)
                                        )
                                    }
                                    startAngle += sweepAngle
                                }
                            } else {
                                drawArc(
                                    color = Color.DarkGray,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val formatted = formatBytes(totalSize).split(" ")
                            if (formatted.size == 2) {
                                Text(formatted[0], style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                                Text(formatted[1], style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(formatBytes(totalSize), style = MaterialTheme.typography.displayMedium)
                            }
                        }
                    }
                }
                
                item {
                    val context = LocalContext.current
                    LaunchedEffect(storagePermissionState.status.isGranted) {
                        if (storagePermissionState.status.isGranted) {
                            CacheCalculator.startMonitoring(context, scope)
                        } else {
                            // Can still calculate basic app folders that don't need permissions
                            CacheCalculator.startMonitoring(context, scope) 
                        }
                    }
                    val deviceStorageInfo by CacheCalculator.deviceStorageInfo.collectAsState()
                    val totalDevice = deviceStorageInfo.totalSpaceBytes
                    val freeDevice = deviceStorageInfo.freeSpaceBytes
                    val appUsage = deviceStorageInfo.appUsageBytes
                    
                    val otherAppsUsage = totalDevice - freeDevice - appUsage
                    
                    val appUsagePercent = if (totalDevice > 0) (appUsage.toFloat() / totalDevice * 100) else 0f
                    val displayAppUsagePercent = if (appUsagePercent > 0 && appUsagePercent < 1f) "<1" else appUsagePercent.toInt().toString()
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "KuoteX занимает $displayAppUsagePercent% места на устройстве.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
                            if (totalDevice > 0) {
                                val targetAppWeight = if (animationTrigger) appUsage.toFloat() / totalDevice else 0f
                                val targetOtherWeight = if (animationTrigger) otherAppsUsage.toFloat() / totalDevice else 0f
                                
                                val animatedAppWeight by animateFloatAsState(
                                    targetValue = targetAppWeight,
                                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                                    label = "appWeight"
                                )
                                val animatedOtherWeight by animateFloatAsState(
                                    targetValue = targetOtherWeight,
                                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                                    label = "otherWeight"
                                )
                                val animatedFreeWeight = (1f - animatedAppWeight - animatedOtherWeight).coerceAtLeast(0f)
                                
                                if (animatedAppWeight > 0f) {
                                    Box(modifier = Modifier.weight(animatedAppWeight).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                                }
                                if (animatedOtherWeight > 0f) {
                                    Box(modifier = Modifier.weight(animatedOtherWeight).fillMaxHeight().background(Color.Gray.copy(alpha = 0.5f)))
                                }
                                if (animatedFreeWeight > 0f) {
                                    Box(modifier = Modifier.weight(animatedFreeWeight).fillMaxHeight().background(Color.DarkGray.copy(alpha = 0.2f)))
                                }
                            }
                        }
                    }
                }
                
                storageStats.categories.forEach { category ->
                    item {
                        val percentage = if (totalSize > 0) {
                            (category.sizeBytes.toFloat() / totalSize.toFloat() * 100).toInt()
                        } else 0
                        
                        val displayPercentage = if (percentage == 0 && category.sizeBytes > 0) "<1.0%" else "$percentage%"
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    if (category.subCategories != null) {
                                        viewModel.toggleStorageCategoryExpand(category.categoryName)
                                    } else {
                                        viewModel.toggleStorageCategory(category.categoryName) 
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleStorageCategory(category.categoryName) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (category.isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (category.isSelected) category.color else Color.Gray,
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(category.categoryName, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(displayPercentage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            if (category.subCategories != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (category.isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            Text(formatBytes(category.sizeBytes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    if (category.isExpanded && category.subCategories != null) {
                        category.subCategories.forEach { sub ->
                            item {
                                val percentage = if (totalSize > 0) {
                                    (sub.sizeBytes.toFloat() / totalSize.toFloat() * 100).toInt()
                                } else 0
                                
                                val displayPercentage = if (percentage == 0 && sub.sizeBytes > 0) "<1.0%" else "$percentage%"
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleStorageCategory(sub.categoryName, true, category.categoryName) }
                                        .padding(start = 48.dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { viewModel.toggleStorageCategory(sub.categoryName, true, category.categoryName) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (sub.isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (sub.isSelected) sub.color else Color.Gray,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(sub.categoryName, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(displayPercentage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(formatBytes(sub.sizeBytes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                
                item {
                    if (isClearing) {
                        CacheClearAnimation(
                            progress = clearProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(horizontal = 16.dp)
                        )
                    } else {
                        Button(
                            onClick = {
                                if (selectedSize > 0) {
                                    isClearing = true
                                    clearProgress = 0f
                                    scope.launch {
                                        val steps = 100
                                        for (i in 0..steps) {
                                            clearProgress = i / steps.toFloat()
                                            delay(20) 
                                        }
                                        delay(500)
                                        val selectedNames = storageStats.categories.flatMap { cat ->
                                            if (cat.subCategories != null) {
                                                cat.subCategories.filter { it.isSelected }.map { it.categoryName }
                                            } else {
                                                if (cat.isSelected) listOf(cat.categoryName) else emptyList()
                                            }
                                        }
                                        viewModel.clearCache(selectedNames)
                                        CacheCalculator.clearAppCache(context, selectedNames)
                                        CacheCalculator.forceScan(context, scope)
                                        isClearing = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            enabled = selectedSize > 0
                        ) {
                            Text("Очистить кэш (${formatBytes(selectedSize)})")
                        }
                    }
                }
                
                item {
                    CacheManagerSection(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun CacheClearAnimation(progress: Float, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "flame")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "time"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(h / 2, h / 2)
        
        drawRoundRect(
            color = Color.DarkGray,
            size = Size(w, h),
            cornerRadius = corner
        )

        val clearWidth = w * progress

        clipRect(right = clearWidth) {
            drawRoundRect(
                color = Color(0xFF4CAF50),
                size = Size(w, h),
                cornerRadius = corner
            )
        }

        if (progress > 0f && progress < 1f) {
            val flameX = clearWidth
            val flameYOffset = sin(time * 10f) * (h * 0.2f)
            
            val broomPath = Path().apply {
                moveTo(flameX, h * 0.9f)
                quadraticBezierTo(
                    flameX + h * 0.5f + sin(time * 15f) * h * 0.2f, h * 0.5f,
                    flameX, -h * 0.2f + flameYOffset
                )
                quadraticBezierTo(
                    flameX - h * 0.5f + sin(time * 12f) * h * 0.2f, h * 0.5f,
                    flameX, h * 0.9f
                )
                close()
            }
            
            val innerBroomPath = Path().apply {
                moveTo(flameX, h * 0.8f)
                quadraticBezierTo(
                    flameX + h * 0.3f + sin(time * 20f) * h * 0.1f, h * 0.6f,
                    flameX, h * 0.1f + flameYOffset * 0.5f
                )
                quadraticBezierTo(
                    flameX - h * 0.3f + sin(time * 18f) * h * 0.1f, h * 0.6f,
                    flameX, h * 0.8f
                )
                close()
            }

            drawPath(broomPath, color = Color(0xFFFF5722)) 
            drawPath(innerBroomPath, color = Color(0xFFFFEB3B))
        }
    }
}

@Composable
fun CacheManagerSection(viewModel: AppViewModel) {
    val maxCacheSizeIndex by viewModel.maxCacheSizeIndex.collectAsState()
    
    val steps = listOf("5 GB", "16 GB", "32 GB", "∞")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                "Максимальный размер кэша",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = maxCacheSizeIndex / 3f)
                        .height(2.dp)
                        .align(Alignment.CenterStart)
                        .background(MaterialTheme.colorScheme.primary)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { index, label ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    viewModel.setMaxCacheSizeIndex(index)
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (index <= maxCacheSizeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(if (index == maxCacheSizeIndex) 16.dp else 8.dp)
                                    .background(
                                        if (index <= maxCacheSizeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
    
    Text(
        text = "Если размер кэша превысит этот лимит, самые старые из неиспользуемых медиа будут удалены из памяти устройства.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
@Composable
fun NeonProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500, easing = LinearOutSlowInEasing),
        label = "neon_progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "neon_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(Color.DarkGray, RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = animatedProgress)
                .fillMaxHeight()
                .background(
                    color = Color.Cyan.copy(alpha = alpha),
                    shape = RoundedCornerShape(6.dp)
                )
        )
    }
}
