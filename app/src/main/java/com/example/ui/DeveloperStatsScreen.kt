package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.analytics.FirebaseAnalyticsHelper
import com.example.analytics.HourlyFailureStat
import com.example.data.RegisteredUserRole
import com.example.data.ContentReport
import com.example.data.ReportStatus
import com.example.data.AdminActionLog
import com.example.data.ActionType
import com.example.data.DailyMetric
import com.example.utils.BatteryEfficiencyMonitor
import com.example.utils.NetworkBandwidthMonitor
import com.example.utils.NetworkStatusCategory
import com.example.utils.PowerEfficiencyStats
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperStatsScreen(viewModel: AppViewModel, navController: NavController) {
    val context = LocalContext.current
    val activeAccount = LocalActiveAccount.current

    // Access control verification
    val isDev = viewModel.isDeveloperAccount(activeAccount)
    if (!isDev) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Access Denied",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Developer Access Restricted",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This section is restricted to developer account (+79226692682).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    var selectedAdminTab by remember { mutableIntStateOf(0) }
    val contentReports by viewModel.contentReports.collectAsState()
    val pendingReportsCount = remember(contentReports) { contentReports.count { it.status == ReportStatus.PENDING } }

    val undeliveredCount by viewModel.undeliveredMessagesCount.collectAsState()
    val undeliveredMessages by viewModel.undeliveredMessages.collectAsState()
    val totalEventsLogged by FirebaseAnalyticsHelper.totalEventsLogged.collectAsState()
    val criticalEventsCount by FirebaseAnalyticsHelper.criticalEventsCount.collectAsState()
    val queuedAnalyticsCount by FirebaseAnalyticsHelper.queuedEvents.collectAsState()
    val batchesFlushed by FirebaseAnalyticsHelper.totalBatchesFlushed.collectAsState()
    val isPoorConnection by FirebaseAnalyticsHelper.isPoorConnectionMode.collectAsState()

    val powerStats by BatteryEfficiencyMonitor.powerStats.collectAsState()
    val bandwidthStats by NetworkBandwidthMonitor.throughputStats.collectAsState()

    var currentUptime by remember { mutableStateOf(FirebaseAnalyticsHelper.getUptimeFormatted()) }
    var messageSuccessRate by remember { mutableFloatStateOf(FirebaseAnalyticsHelper.getMessageSuccessRate()) }
    var avgMessageLatency by remember { mutableLongStateOf(FirebaseAnalyticsHelper.getAverageMessageLatency()) }
    var streamStats by remember { mutableStateOf(FirebaseAnalyticsHelper.getStreamPerformanceSummary()) }
    var failureRateTimeline by remember { mutableStateOf(FirebaseAnalyticsHelper.get24HourDeliveryFailureStats()) }

    var syncSuccessNotice by remember { mutableStateOf<String?>(null) }

    // Live ticker for uptime and stats
    LaunchedEffect(Unit) {
        while (true) {
            currentUptime = FirebaseAnalyticsHelper.getUptimeFormatted()
            messageSuccessRate = FirebaseAnalyticsHelper.getMessageSuccessRate()
            avgMessageLatency = FirebaseAnalyticsHelper.getAverageMessageLatency()
            streamStats = FirebaseAnalyticsHelper.getStreamPerformanceSummary()
            failureRateTimeline = FirebaseAnalyticsHelper.get24HourDeliveryFailureStats()
            delay(1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Панель администратора",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            // Status indicator dot
                            val dotColor = when (bandwidthStats.statusCategory) {
                                NetworkStatusCategory.ONLINE_HIGH_SPEED -> Color(0xFF00FF66)
                                NetworkStatusCategory.ONLINE_METERED -> Color(0xFF00E5FF)
                                NetworkStatusCategory.POOR_CONNECTION -> Color(0xFFFF9800)
                                NetworkStatusCategory.OFFLINE -> Color(0xFFFF3B30)
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        Text(
                            "Модерация Telegram • +79226692682",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings/developer_debug") }) {
                        Icon(
                            Icons.Filled.BugReport,
                            contentDescription = "Debug Event Stream",
                            tint = Color(0xFF00FFFF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Developer Identification & Network Mode Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, Color(0xFF00E5FF))
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00FFFF).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint = Color(0xFF00FFFF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Центр управления и модерации",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Admin Dev: +79226692682",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { navController.navigate("settings/developer_debug") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Logs", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Sub-Section Navigation Tabs
            item {
                // Scrollable tab bar with badges
                ScrollableTabRow(
                    selectedTabIndex = selectedAdminTab,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedAdminTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedAdminTab]),
                                color = Color(0xFFFFB300),
                                height = 3.dp
                            )
                        }
                    }
                ) {
                    val tabs = listOf(
                        Triple("Пользователи", Icons.Filled.PeopleAlt, 0),
                        Triple("Жалобы", Icons.Filled.ReportProblem, pendingReportsCount),
                        Triple("Графики (30д)", Icons.Filled.ShowChart, 0),
                        Triple("Журнал", Icons.Filled.History, 0),
                        Triple("Диагностика", Icons.Filled.SettingsSuggest, 0)
                    )

                    tabs.forEachIndexed { index, (label, icon, badgeCount) ->
                        Tab(
                            selected = selectedAdminTab == index,
                            onClick = { selectedAdminTab = index },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selectedAdminTab == index) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedAdminTab == index) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedAdminTab == index) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (badgeCount > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Badge(
                                            containerColor = Color(0xFFFF1744),
                                            contentColor = Color.White
                                        ) {
                                            Text("$badgeCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Tab 0: Registered Users Sub-Section
            if (selectedAdminTab == 0) {
                item {
                    RegisteredUsersFirestoreSubSection(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Tab 1: Content Reports & Moderation Queue
            if (selectedAdminTab == 1) {
                item {
                    AdminReportsQueueSection(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Tab 2: 30-Day Data Visualization
            if (selectedAdminTab == 2) {
                item {
                    AdminAnalytics30DayChartSection(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Tab 3: Action Audit Log / Journal
            if (selectedAdminTab == 3) {
                item {
                    AdminActionLogSection(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Tab 4: Diagnostics
            if (selectedAdminTab == 4) {
                // 1. 24-Hour Message Delivery Failure Rate Timeline Line Graph
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = Color(0xFFFF007F),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Delivery Failure Rate (24h)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Message transmission error rate tracking",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            val avgFailure24h = remember(failureRateTimeline) {
                                if (failureRateTimeline.isNotEmpty()) {
                                    failureRateTimeline.map { it.failureRatePercent }.average().toFloat()
                                } else 0f
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (avgFailure24h > 2.5f) Color(0xFFFF007F).copy(alpha = 0.18f) else Color(0xFF00E676).copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "Avg: ${String.format(Locale.US, "%.2f", avgFailure24h)}%",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (avgFailure24h > 2.5f) Color(0xFFFF007F) else Color(0xFF00E676)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Interactive Line Chart
                        DeliveryFailureLineGraph(
                            stats = failureRateTimeline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 24h Summary stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val peakPoint = failureRateTimeline.maxByOrNull { it.failureRatePercent }
                            val totalSent24h = failureRateTimeline.sumOf { it.totalSent }
                            val totalFailed24h = failureRateTimeline.sumOf { it.failedCount }

                            QueueStatBox(
                                title = "Peak Failure Slot",
                                value = peakPoint?.let { "${String.format(Locale.US, "%.1f", it.failureRatePercent)}%" } ?: "0%",
                                subtitle = peakPoint?.hourLabel ?: "--:--",
                                modifier = Modifier.weight(1f)
                            )
                            QueueStatBox(
                                title = "Total Sent (24h)",
                                value = "$totalSent24h",
                                subtitle = "$totalFailed24h errors",
                                modifier = Modifier.weight(1f)
                            )
                            QueueStatBox(
                                title = "Reliability Score",
                                value = "${String.format(Locale.US, "%.1f", 100f - (if (totalSent24h > 0) (totalFailed24h.toFloat() / totalSent24h) * 100f else 0f))}%",
                                subtitle = "SLA Target: 99.0%",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Power Efficiency Dashboard (Battery Consumption Monitor)
            item {
                PowerEfficiencyDashboardCard(
                    stats = powerStats,
                    onResetSession = { BatteryEfficiencyMonitor.resetSession() }
                )
            }

            // 3. Cached Message Queue Depth Overview
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Storage,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Cached Message Queue Depth",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // Queue Depth Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (undeliveredCount > 0) Color(0xFFFF9800).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f),
                                border = null
                            ) {
                                Text(
                                    text = if (undeliveredCount > 0) "$undeliveredCount PENDING" else "CLEAR (0)",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (undeliveredCount > 0) Color(0xFFFF9800) else Color(0xFF4CAF50)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QueueStatBox(
                                title = "Local SQLite Queue",
                                value = "$undeliveredCount msgs",
                                subtitle = "Room pending sync",
                                modifier = Modifier.weight(1f)
                            )
                            QueueStatBox(
                                title = "Sync Worker Status",
                                value = if (undeliveredCount > 0) "Queued" else "Idle",
                                subtitle = "WorkManager periodic",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.triggerBackgroundMessageSync(context)
                                    syncSuccessNotice = "Triggered background sync worker pass."
                                }
                            ) {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Force Queue Sync")
                            }
                        }

                        syncSuccessNotice?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }

            // Pending Messages List (if any in queue)
            if (undeliveredMessages.isNotEmpty()) {
                item {
                    Text(
                        "Queue Items (${undeliveredMessages.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(undeliveredMessages.take(5)) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PendingActions,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = msg.text.ifBlank { "[Media Message]" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Chat: ${msg.chatId} • ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 4. Key Uptime & System Health Metrics
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                tint = Color(0xFF00FFFF),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Uptime & Reliability Metrics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                label = "Service Uptime",
                                value = currentUptime,
                                icon = Icons.Filled.AccessTime,
                                accentColor = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                label = "Delivery Success",
                                value = "${String.format(Locale.US, "%.1f", messageSuccessRate)}%",
                                icon = Icons.Filled.CheckCircle,
                                accentColor = Color(0xFF00E676),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                label = "Avg Msg Latency",
                                value = if (avgMessageLatency > 0) "${avgMessageLatency}ms" else "<50ms",
                                icon = Icons.Filled.Speed,
                                accentColor = Color(0xFFFFB300),
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                label = "Critical Events",
                                value = "$criticalEventsCount",
                                icon = Icons.Filled.Bolt,
                                accentColor = Color(0xFFFF007F),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. Batch Processing & Network Adaptation
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.DynamicFeed,
                                    contentDescription = null,
                                    tint = Color(0xFFB500FF),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Analytics Batch Processing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Switch(
                                checked = isPoorConnection,
                                onCheckedChange = { FirebaseAnalyticsHelper.setPoorConnectionMode(it) }
                            )
                        }

                        Text(
                            text = if (isPoorConnection) "⚠️ Poor Connection Mode ACTIVE: Lower priority events are buffered in queue" else "✅ Normal Network: Real-time & batch dispatch enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPoorConnection) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QueueStatBox(
                                title = "Batched Pending",
                                value = "${queuedAnalyticsCount.size}",
                                subtitle = "Low-priority buffer",
                                modifier = Modifier.weight(1f)
                            )
                            QueueStatBox(
                                title = "Batches Flushed",
                                value = "$batchesFlushed",
                                subtitle = "Saved network calls",
                                modifier = Modifier.weight(1f)
                            )
                            QueueStatBox(
                                title = "Total Logged",
                                value = "$totalEventsLogged",
                                subtitle = "All schemas",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { FirebaseAnalyticsHelper.flushBatch() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB500FF)
                                )
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Flush Analytics Batch")
                            }
                        }
                    }
                }
            }

            // 6. Live Stream & Engagement Analytics Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.LiveTv,
                                contentDescription = null,
                                tint = Color(0xFFFF007F),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Live Stream Telemetry Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QueueStatBox(
                                title = "Streams Joined",
                                value = "${streamStats.totalStreamsJoined}",
                                subtitle = "Avg join: ${streamStats.avgJoinLatencyMs}ms",
                                modifier = Modifier.weight(1f)
                            )
                            QueueStatBox(
                                title = "Avg Retention",
                                value = "${streamStats.avgRetentionSeconds}s",
                                subtitle = "${streamStats.totalViewersTracked} viewers tracked",
                                modifier = Modifier.weight(1f)
                            )
                            QueueStatBox(
                                title = "Drop-offs",
                                value = "${streamStats.totalDropOffs}",
                                subtitle = "${streamStats.userExitCount} exit / ${streamStats.networkDropCount} net",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Power Efficiency Dashboard Card visualizing live background battery consumption
 */
@Composable
private fun PowerEfficiencyDashboardCard(
    stats: PowerEfficiencyStats,
    onResetSession: () -> Unit
) {
    val gradeColor = when (stats.efficiencyGrade) {
        "A+" -> Color(0xFF00E676)
        "A" -> Color(0xFF00FF66)
        "B" -> Color(0xFF00E5FF)
        "C" -> Color(0xFFFF9800)
        else -> Color(0xFFFF3B30)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BatteryChargingFull,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Power Efficiency Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Background messaging battery telemetry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // Efficiency Grade Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = gradeColor.copy(alpha = 0.2f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(gradeColor))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grade ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = stats.efficiencyGrade,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = gradeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Battery Metric Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Drain Rate",
                    value = "${String.format(Locale.US, "%.1f", stats.drainRatePerHour)}%/h",
                    icon = Icons.Filled.ElectricBolt,
                    accentColor = Color(0xFFFFB300),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Battery Level",
                    value = "${stats.currentBatteryLevel}%",
                    icon = if (stats.isCharging) Icons.Filled.Power else Icons.Filled.BatteryStd,
                    accentColor = if (stats.isCharging) Color(0xFF00E676) else Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Temperature",
                    value = "${String.format(Locale.US, "%.1f", stats.temperatureCelsius)}°C",
                    icon = Icons.Filled.DeviceThermostat,
                    accentColor = if (stats.temperatureCelsius > 40f) Color(0xFFFF3B30) else Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Est. Messaging Life",
                    value = "${String.format(Locale.US, "%.1f", stats.estimatedBatteryRemainingHours)} hrs",
                    icon = Icons.Filled.HourglassTop,
                    accentColor = Color(0xFF00FF66),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Info & Reset Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Session Messages: ${stats.messagesSentThisSession} • ${String.format(Locale.US, "%.2f", stats.avgDrainPer100Messages)}% / 100 msgs",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        "Voltage: ${stats.voltageMillivolts}mV • Status: ${if (stats.isCharging) "Charging" else "Discharging"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }

                TextButton(
                    onClick = onResetSession,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset Session", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * 24-Hour Line Graph tracking message delivery failure rate
 */
@Composable
private fun DeliveryFailureLineGraph(
    stats: List<HourlyFailureStat>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val lineColor = Color(0xFFFF007F)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fillGradient = Brush.verticalGradient(
        listOf(
            lineColor.copy(alpha = 0.35f),
            lineColor.copy(alpha = 0.0f)
        )
    )

    Column(modifier = modifier) {
        // Active Hover Tooltip Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val point = selectedIndex?.let { stats.getOrNull(it) } ?: stats.lastOrNull()
            if (point != null) {
                Text(
                    text = "Time: ${point.hourLabel} • Failure Rate: ${String.format(Locale.US, "%.1f", point.failureRatePercent)}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (point.failureRatePercent > 2f) Color(0xFFFF007F) else Color(0xFF00E676)
                )
                Text(
                    text = "Sent: ${point.totalSent} | Failed: ${point.failedCount}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        // Canvas Line Graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0E1A))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(stats) {
                        detectTapGestures { offset ->
                            val stepX = size.width / (stats.size - 1).coerceAtLeast(1)
                            val idx = (offset.x / stepX).toInt().coerceIn(0, stats.size - 1)
                            selectedIndex = idx
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                val width = size.width
                val height = size.height

                // Draw Horizontal Grid Lines (0%, 2%, 4%, 6%)
                val maxFailure = maxOf(stats.maxOfOrNull { it.failureRatePercent } ?: 5f, 5f)
                for (i in 0..4) {
                    val y = height - (i * (height / 4f))
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (stats.size < 2) return@Canvas

                val stepX = width / (stats.size - 1).coerceAtLeast(1)
                val linePath = Path()
                val fillPath = Path()

                stats.forEachIndexed { index, item ->
                    val x = index * stepX
                    val normalizedY = height - ((item.failureRatePercent / maxFailure) * (height - 16f)).coerceIn(8f, height - 8f)

                    if (index == 0) {
                        linePath.moveTo(x, normalizedY)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, normalizedY)
                    } else {
                        linePath.lineTo(x, normalizedY)
                        fillPath.lineTo(x, normalizedY)
                    }

                    if (index == stats.size - 1) {
                        fillPath.lineTo(x, height)
                        fillPath.close()
                    }
                }

                // Draw gradient under line
                drawPath(path = fillPath, brush = fillGradient)

                // Draw primary line
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw Points
                stats.forEachIndexed { index, item ->
                    val x = index * stepX
                    val normalizedY = height - ((item.failureRatePercent / maxFailure) * (height - 16f)).coerceIn(8f, height - 8f)
                    val isSelected = selectedIndex == index

                    drawCircle(
                        color = if (isSelected) Color.White else lineColor,
                        radius = if (isSelected) 5.dp.toPx() else 2.5.dp.toPx(),
                        center = Offset(x, normalizedY)
                    )
                }
            }
        }

        // X-Axis Hour Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelIndices = listOf(0, 6, 12, 18, 23)
            labelIndices.forEach { idx ->
                val label = stats.getOrNull(idx)?.hourLabel ?: ""
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun QueueStatBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Sub-Section within the Admin Panel to list registered users and provide toggles
 * to assign them 'Admin' or 'Moderator' roles using the existing Firestore integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisteredUsersFirestoreSubSection(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val users by viewModel.registeredUsers.collectAsState()
    val isSyncing by viewModel.isFirestoreUserSyncing.collectAsState()
    val syncMessage by viewModel.firestoreUserSyncMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Admins, 2: Mods, 3: Members
    var showAddUserDialog by remember { mutableStateOf(false) }

    // Rotating sync icon animation
    val infiniteTransition = rememberInfiniteTransition(label = "firestore_sync_spin")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_spin"
    )

    val adminCount = remember(users) { users.count { it.isAdmin } }
    val modCount = remember(users) { users.count { it.isModerator } }
    val regularCount = remember(users) { users.count { !it.isAdmin && !it.isModerator && !it.isBlocked && !it.isSpamRestricted } }
    val blockedCount = remember(users) { users.count { it.isBlocked || it.isSpamRestricted } }

    var userToBlock by remember { mutableStateOf<RegisteredUserRole?>(null) }

    val filteredUsers = remember(users, searchQuery, selectedFilterIndex) {
        users.filter { user ->
            val matchesSearch = searchQuery.isBlank() ||
                    user.displayName.contains(searchQuery, ignoreCase = true) ||
                    user.username.contains(searchQuery, ignoreCase = true) ||
                    user.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                    (!user.email.isNullOrBlank() && user.email.contains(searchQuery, ignoreCase = true)) ||
                    user.id.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilterIndex) {
                1 -> user.isAdmin
                2 -> user.isModerator
                3 -> !user.isAdmin && !user.isModerator && !user.isBlocked && !user.isSpamRestricted
                4 -> user.isBlocked || user.isSpamRestricted
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFFFFB300).copy(alpha = 0.6f),
                    Color(0xFF00E5FF).copy(alpha = 0.6f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Firestore Cloud Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFFFFB300).copy(alpha = 0.35f),
                                        Color(0xFF00E5FF).copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AdminPanelSettings,
                            contentDescription = "Admin Firestore Roles",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Пользователи и роли (Firestore)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isSyncing) Color(0xFFFF9800) else Color(0xFF00E676))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isSyncing) "Синхронизация Firestore..." else "Firestore Cloud Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSyncing) Color(0xFFFF9800) else Color(0xFF00E676),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Cloud Batch Sync Button
                IconButton(
                    onClick = { viewModel.syncAllUsersWithFirestore() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = "Sync with Firestore",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier
                            .size(20.dp)
                            .then(
                                if (isSyncing) Modifier.graphicsLayer { rotationZ = syncRotation }
                                else Modifier
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stat Summary Boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UserRoleCountBadge(
                    label = "Всего",
                    count = users.size,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                UserRoleCountBadge(
                    label = "★ Админы",
                    count = adminCount,
                    color = Color(0xFFFFB300),
                    modifier = Modifier.weight(1.1f)
                )
                UserRoleCountBadge(
                    label = "🛡 Модераторы",
                    count = modCount,
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1.2f)
                )
                UserRoleCountBadge(
                    label = "⛔ Блок/Спам",
                    count = blockedCount,
                    color = Color(0xFFFF1744),
                    modifier = Modifier.weight(1.1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar & Add User Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск пользователя...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFB300),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalIconButton(
                    onClick = { showAddUserDialog = true },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFFFFB300).copy(alpha = 0.2f),
                        contentColor = Color(0xFFFFB300)
                    )
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add User", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Role Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filterLabels = listOf(
                    "Все (${users.size})",
                    "★ Админы ($adminCount)",
                    "🛡 Модераторы ($modCount)",
                    "Обычные ($regularCount)",
                    "⛔ Заблокированные ($blockedCount)"
                )
                items(filterLabels.size) { idx ->
                    FilterChip(
                        selected = selectedFilterIndex == idx,
                        onClick = { selectedFilterIndex = idx },
                        label = { Text(filterLabels[idx], fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (idx) {
                                1 -> Color(0xFFFFB300).copy(alpha = 0.25f)
                                2 -> Color(0xFF00E5FF).copy(alpha = 0.25f)
                                4 -> Color(0xFFFF1744).copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = when (idx) {
                                1 -> Color(0xFFFFD54F)
                                2 -> Color(0xFF00E5FF)
                                4 -> Color(0xFFFF5252)
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    )
                }
            }

            syncMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFB300).copy(alpha = 0.9f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List of Registered Users with Direct Role Toggles and Block Action
            if (filteredUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PersonSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Пользователи не найдены",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filteredUsers.forEach { user ->
                        UserRoleItemCard(
                            user = user,
                            onToggleAdmin = { viewModel.toggleUserAdminRole(user.id) },
                            onToggleModerator = { viewModel.toggleUserModeratorRole(user.id) },
                            onBlockClick = { userToBlock = user },
                            onUnblockClick = { viewModel.unblockUserFromAdminPanel(user.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddUserDialog) {
        AddRegisteredUserDialog(
            onDismiss = { showAddUserDialog = false },
            onAddUser = { username, displayName, phone, email, isAdmin, isModerator ->
                viewModel.addNewRegisteredUser(
                    username = username,
                    displayName = displayName,
                    phoneNumber = phone,
                    email = email,
                    isAdmin = isAdmin,
                    isModerator = isModerator
                )
                showAddUserDialog = false
            }
        )
    }

    userToBlock?.let { targetUser ->
        BlockUserModerationDialog(
            user = targetUser,
            onDismiss = { userToBlock = null },
            onConfirmBlock = { reason, isSpamRestrictedOnly, durationMillis ->
                viewModel.blockUserFromAdminPanel(
                    userId = targetUser.id,
                    reason = reason,
                    isSpamRestrictedOnly = isSpamRestrictedOnly,
                    durationMillis = durationMillis
                )
                userToBlock = null
            }
        )
    }
}

@Composable
private fun UserRoleCountBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = 14.sp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.9f),
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Individual Card displaying registered user details, role switches, and moderation block actions.
 */
@Composable
fun UserRoleItemCard(
    user: RegisteredUserRole,
    onToggleAdmin: () -> Unit,
    onToggleModerator: () -> Unit,
    onBlockClick: () -> Unit,
    onUnblockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val starsGold = Color(0xFFFFB300)
    val modCyan = Color(0xFF00E5FF)
    val blockRed = Color(0xFFFF1744)
    val isRestricted = user.isBlocked || user.isSpamRestricted

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (user.isBlocked) MaterialTheme.colorScheme.surface.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(
            1.dp,
            if (user.isBlocked) blockRed.copy(alpha = 0.7f)
            else if (user.isSpamRestricted) Color(0xFFFF9100).copy(alpha = 0.7f)
            else if (user.isAdmin) starsGold.copy(alpha = 0.45f)
            else if (user.isModerator) modCyan.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // User Header Row: Avatar, Name, Username, Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with online status or block icon
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    AsyncImage(
                        model = user.profilePicUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (user.isBlocked) blockRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentScale = ContentScale.Crop
                    )
                    if (user.isBlocked) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(blockRed)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
                        }
                    } else if (user.isOnline) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & Username
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (user.isBlocked) Color(0xFFFF8A80) else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Status Badges
                        if (user.isBlocked) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = blockRed.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, blockRed)
                            ) {
                                Text(
                                    text = "⛔ BLOCKED",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFF5252),
                                    fontSize = 9.sp
                                )
                            }
                        } else if (user.isSpamRestricted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFF9100).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFFFF9100))
                            ) {
                                Text(
                                    text = "⚠️ SPAMBOT",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFAB40),
                                    fontSize = 9.sp
                                )
                            }
                        } else if (user.isAdmin) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = starsGold.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, starsGold)
                            ) {
                                Text(
                                    text = "★ ADMIN",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFD54F),
                                    fontSize = 9.sp
                                )
                            }
                        } else if (user.isModerator) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = modCyan.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, modCyan)
                            ) {
                                Text(
                                    text = "🛡 MOD",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = modCyan,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                        if (user.phoneNumber.isNotBlank()) {
                            Text(
                                text = " • ${user.phoneNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (!user.email.isNullOrBlank()) {
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Moderation info banner if blocked or restricted
            if (isRestricted) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (user.isBlocked) blockRed.copy(alpha = 0.12f) else Color(0xFFFF9100).copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, if (user.isBlocked) blockRed.copy(alpha = 0.4f) else Color(0xFFFF9100).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (user.isBlocked) Icons.Filled.Block else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (user.isBlocked) Color(0xFFFF5252) else Color(0xFFFFAB40),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = user.blockReason ?: if (user.isBlocked) "Блокировка аккаунта и входа" else "Ограничение Spambot (только взаимные контакты)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Display remaining ban duration if temporary ban is set
                        if (user.blockedUntilTimestamp != null && user.blockedUntilTimestamp > System.currentTimeMillis()) {
                            val remainingHours = ((user.blockedUntilTimestamp - System.currentTimeMillis()) / (1000 * 3600)).coerceAtLeast(1)
                            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(user.blockedUntilTimestamp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "⏱ До $dateStr (осталось ~$remainingHours ч.)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (user.isBlocked) Color(0xFFFF8A80) else Color(0xFFFFD180),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(8.dp))

            // Role Toggles Row: Admin Switch & Moderator Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Admin Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleAdmin() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Stars,
                        contentDescription = "Admin Role",
                        tint = if (user.isAdmin) starsGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Admin",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (user.isAdmin) FontWeight.Bold else FontWeight.Normal,
                        color = if (user.isAdmin) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = user.isAdmin,
                        onCheckedChange = { onToggleAdmin() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFB300),
                            checkedTrackColor = Color(0xFFFFB300).copy(alpha = 0.4f),
                            checkedBorderColor = Color(0xFFFFB300)
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }

                // Moderator Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleModerator() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = "Moderator Role",
                        tint = if (user.isModerator) modCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Moderator",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (user.isModerator) FontWeight.Bold else FontWeight.Normal,
                        color = if (user.isModerator) modCyan else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = user.isModerator,
                        onCheckedChange = { onToggleModerator() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = modCyan,
                            checkedTrackColor = modCyan.copy(alpha = 0.4f),
                            checkedBorderColor = modCyan
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Row: Block / Unblock Button & Firestore Cloud Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Block or Unblock button
                if (isRestricted) {
                    OutlinedButton(
                        onClick = onUnblockClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF00E676)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.LockOpen,
                            contentDescription = "Unblock User",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Разблокировать",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onBlockClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = "Block User",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Заблокировать",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Firestore doc & sync indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (user.isSyncedToFirestore) Icons.Filled.CloudDone else Icons.Filled.CloudSync,
                        contentDescription = null,
                        tint = if (user.isSyncedToFirestore) Color(0xFF00E676) else Color(0xFFFF9800),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (user.isSyncedToFirestore) "Firestore OK" else "Синхронизация...",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

/**
 * Telegram Moderation Dialog for blocking a user with detailed options (Full Ban or Spambot Restriction, duration options).
 */
@Composable
fun BlockUserModerationDialog(
    user: RegisteredUserRole,
    onDismiss: () -> Unit,
    onConfirmBlock: (reason: String, isSpamRestrictedOnly: Boolean, durationMillis: Long?) -> Unit
) {
    var isSpamRestrictedOnly by remember { mutableStateOf(false) }
    var selectedDurationIndex by remember { mutableIntStateOf(0) } // 0: 24 hours, 1: 7 days, 2: Forever (indefinite)
    var selectedReasonPreset by remember { mutableStateOf("Спам и массовая рассылка (Telegram SpamBot)") }
    var customReason by remember { mutableStateOf("") }

    val durationOptions = listOf(
        "24 часа" to (24L * 3600 * 1000),
        "7 дней" to (7L * 24 * 3600 * 1000),
        "Бессрочно" to null
    )

    val reasonPresets = listOf(
        "Спам и массовая рассылка (Telegram SpamBot)",
        "Нарушение правил сообщества KuoteX",
        "Подозрительная активность / Мультиаккаунт",
        "Жалобы других пользователей на спам",
        "Оскорбительное поведение / Неприемлемый контент"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFFFF1744), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = null,
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Блокировка пользователя",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252)
                    )
                    Text(
                        text = "${user.displayName} (${user.username})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Тип ограничения (модерация Telegram):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Option 1: Full Ban
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (!isSpamRestrictedOnly) Color(0xFFFF1744).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (!isSpamRestrictedOnly) Color(0xFFFF1744) else Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSpamRestrictedOnly = false }
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isSpamRestrictedOnly,
                            onClick = { isSpamRestrictedOnly = false },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF1744))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Полная блокировка (Full Ban)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (!isSpamRestrictedOnly) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Блокирует авторизацию, вход в аккаунт и любую отправку сообщений.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Option 2: Spambot Restriction
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSpamRestrictedOnly) Color(0xFFFF9100).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (isSpamRestrictedOnly) Color(0xFFFF9100) else Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSpamRestrictedOnly = true }
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSpamRestrictedOnly,
                            onClick = { isSpamRestrictedOnly = true },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF9100))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Ограничение Telegram SpamBot",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSpamRestrictedOnly) Color(0xFFFFAB40) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Запрещает отправку сообщений незнакомым пользователям без истории диалога.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Duration Selector
                Text(
                    text = "Длительность блокировки:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    durationOptions.forEachIndexed { index, pair ->
                        FilterChip(
                            selected = selectedDurationIndex == index,
                            onClick = { selectedDurationIndex = index },
                            label = { Text(pair.first, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Причина блокировки:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Presets
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    reasonPresets.forEach { preset ->
                        FilterChip(
                            selected = selectedReasonPreset == preset,
                            onClick = { selectedReasonPreset = preset },
                            label = { Text(preset, fontSize = 10.sp, maxLines = 1) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = customReason,
                    onValueChange = { customReason = it },
                    placeholder = { Text("Или введите свою причину...", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = customReason.ifBlank { selectedReasonPreset }
                    val selectedDurationMillis = durationOptions[selectedDurationIndex].second
                    onConfirmBlock(finalReason, isSpamRestrictedOnly, selectedDurationMillis)
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isSpamRestrictedOnly) Color(0xFFFF9100) else Color(0xFFFF1744))
            ) {
                Text(
                    text = if (isSpamRestrictedOnly) "Ограничить (SpamBot)" else "Заблокировать",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/**
 * Dialog to register a new user with Admin or Moderator permissions and push to Firestore.
 */
@Composable
fun AddRegisteredUserDialog(
    onDismiss: () -> Unit,
    onAddUser: (username: String, displayName: String, phone: String, email: String, isAdmin: Boolean, isModerator: Boolean) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var isModerator by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Новый пользователь Firestore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Имя пользователя (@username)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Отображаемое имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Номер телефона") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))
                Text("Назначить роли:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Роль Администратора (★ Admin)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isAdmin,
                        onCheckedChange = { isAdmin = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFB300))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Роль Модератора (🛡 Moderator)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isModerator,
                        onCheckedChange = { isModerator = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isNotBlank() || displayName.isNotBlank()) {
                        onAddUser(username, displayName, phoneNumber, email, isAdmin, isModerator)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
            ) {
                Text("Добавить в Firestore", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/**
 * Moderation Queue Sub-Section in the Admin Panel for incoming content & message reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportsQueueSection(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val reports by viewModel.contentReports.collectAsState()
    var selectedFilterIndex by remember { mutableIntStateOf(1) } // 0: All, 1: Pending, 2: Resolved, 3: Dismissed
    var reportToBlockAuthor by remember { mutableStateOf<ContentReport?>(null) }

    val pendingCount = remember(reports) { reports.count { it.status == ReportStatus.PENDING } }
    val resolvedCount = remember(reports) {
        reports.count { it.status == ReportStatus.RESOLVED_BANNED || it.status == ReportStatus.RESOLVED_WARNING }
    }
    val dismissedCount = remember(reports) { reports.count { it.status == ReportStatus.DISMISSED } }

    val filteredReports = remember(reports, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> reports.filter { it.status == ReportStatus.PENDING }
            2 -> reports.filter { it.status == ReportStatus.RESOLVED_BANNED || it.status == ReportStatus.RESOLVED_WARNING }
            3 -> reports.filter { it.status == ReportStatus.DISMISSED }
            else -> reports
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFFFF1744).copy(alpha = 0.6f),
                    Color(0xFFFF9100).copy(alpha = 0.6f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFFFF1744).copy(alpha = 0.35f),
                                        Color(0xFFFF9100).copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFFFF1744).copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ReportProblem,
                            contentDescription = "Reports Queue",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Очередь жалоб модерации",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$pendingCount ожидает рассмотрения",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pendingCount > 0) Color(0xFFFF5252) else Color(0xFF00E676),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val chips = listOf(
                    "Все (${reports.size})",
                    "⚠️ Ожидают ($pendingCount)",
                    "✅ Рассмотрено ($resolvedCount)",
                    "❌ Отклонено ($dismissedCount)"
                )
                items(chips.size) { idx ->
                    FilterChip(
                        selected = selectedFilterIndex == idx,
                        onClick = { selectedFilterIndex = idx },
                        label = { Text(chips[idx], fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (idx) {
                                1 -> Color(0xFFFF1744).copy(alpha = 0.25f)
                                2 -> Color(0xFF00E676).copy(alpha = 0.25f)
                                3 -> Color(0xFF757575).copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = when (idx) {
                                1 -> Color(0xFFFF5252)
                                2 -> Color(0xFF00E676)
                                3 -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredReports.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CheckCircleOutline,
                            contentDescription = null,
                            tint = Color(0xFF00E676).copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedFilterIndex == 1) "Нет ожидающих жалоб! Все сообщения проверены." else "Список жалоб пуст",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filteredReports.forEach { report ->
                        ReportItemCard(
                            report = report,
                            onResolve = {
                                viewModel.resolveContentReport(
                                    reportId = report.id,
                                    newStatus = ReportStatus.RESOLVED_WARNING,
                                    note = "Жалоба одобрена модератором"
                                )
                            },
                            onDismiss = {
                                viewModel.resolveContentReport(
                                    reportId = report.id,
                                    newStatus = ReportStatus.DISMISSED,
                                    note = "Нарушений не выявлено"
                                )
                            },
                            onBlockAuthor = { reportToBlockAuthor = report },
                            onSpamRestrictAuthor = {
                                viewModel.blockUserFromAdminPanel(
                                    userId = report.senderId,
                                    reason = "Жалоба на спам: ${report.reasonCategory}",
                                    isSpamRestrictedOnly = true,
                                    durationMillis = 24L * 3600 * 1000,
                                    durationLabel = "24 часа"
                                )
                                viewModel.resolveContentReport(
                                    reportId = report.id,
                                    newStatus = ReportStatus.RESOLVED_WARNING,
                                    note = "Ограничен Telegram SpamBot на 24ч"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    reportToBlockAuthor?.let { report ->
        val dummyUser = RegisteredUserRole(
            id = report.senderId,
            username = report.senderUsername,
            displayName = report.senderDisplayName,
            phoneNumber = "",
            profilePicUrl = ""
        )
        BlockUserModerationDialog(
            user = dummyUser,
            onDismiss = { reportToBlockAuthor = null },
            onConfirmBlock = { reason, isSpamOnly, durationMillis ->
                viewModel.blockUserFromAdminPanel(
                    userId = report.senderId,
                    reason = reason,
                    isSpamRestrictedOnly = isSpamOnly,
                    durationMillis = durationMillis
                )
                viewModel.resolveContentReport(
                    reportId = report.id,
                    newStatus = ReportStatus.RESOLVED_BANNED,
                    note = "Пользователь заблокирован ($reason)"
                )
                reportToBlockAuthor = null
            }
        )
    }
}

/**
 * Card representing a single content report from a user.
 */
@Composable
fun ReportItemCard(
    report: ContentReport,
    onResolve: () -> Unit,
    onDismiss: () -> Unit,
    onBlockAuthor: () -> Unit,
    onSpamRestrictAuthor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(report.timestamp) {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(report.timestamp))
    }

    val statusColor = when (report.status) {
        ReportStatus.PENDING -> Color(0xFFFF5252)
        ReportStatus.RESOLVED_BANNED, ReportStatus.RESOLVED_WARNING -> Color(0xFF00E676)
        ReportStatus.DISMISSED -> Color(0xFF9E9E9E)
    }

    val statusText = when (report.status) {
        ReportStatus.PENDING -> "ОЖИДАЕТ"
        ReportStatus.RESOLVED_BANNED -> "БАН"
        ReportStatus.RESOLVED_WARNING -> "ПРЕДУПРЕЖДЕНИЕ"
        ReportStatus.DISMISSED -> "ОТКЛОНЕНА"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(
            1.dp,
            if (report.status == ReportStatus.PENDING) Color(0xFFFF1744).copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Category, Status, Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFF1744).copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, Color(0xFFFF1744).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = report.reasonCategory,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor,
                            fontSize = 9.sp
                        )
                    }
                }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Offender info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Автор сообщения: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = "${report.senderDisplayName} (${report.senderUsername})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Reporter info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Flag,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Заявитель: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = "${report.reporterName} (ID: ${report.reporterId.takeLast(6)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reported Message snippet quote box
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = report.messageText.ifBlank { "[Медиа-сообщение / Вложение]" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                        if (report.userComment.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Комментарий заявителя: \"${report.userComment}\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFB300).copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Action Buttons for Pending Reports
            if (report.status == ReportStatus.PENDING) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Block button
                    OutlinedButton(
                        onClick = onBlockAuthor,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Бан", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // SpamBot restrict button
                    OutlinedButton(
                        onClick = onSpamRestrictAuthor,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB40)),
                        border = BorderStroke(1.dp, Color(0xFFFF9100).copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1.3f).height(32.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SpamBot 24ч", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Resolve Button
                    Button(
                        onClick = onResolve,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1.1f).height(32.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Принять", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    // Dismiss Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Отклонить жалобу", tint = Color(0xFF9E9E9E), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 30-Day Analytics & Metric Charts Sub-Section in the Admin Panel.
 * Visualizes Active Users (DAU), New Registrations, and Content Reports with interactive touch scrubber.
 */
@Composable
fun AdminAnalytics30DayChartSection(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val allMetrics by viewModel.adminDailyMetrics.collectAsState()
    var selectedRangeDays by remember { mutableIntStateOf(30) } // 7, 14, 30
    var showActiveUsers by remember { mutableStateOf(true) }
    var showRegistrations by remember { mutableStateOf(true) }
    var showReports by remember { mutableStateOf(true) }
    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }

    val filteredMetrics = remember(allMetrics, selectedRangeDays) {
        allMetrics.takeLast(selectedRangeDays)
    }

    val totalActiveUsersSum: Int = remember(filteredMetrics) { filteredMetrics.sumOf { it.activeUsers } }
    val avgActiveUsers: Int = remember(filteredMetrics, totalActiveUsersSum) {
        if (filteredMetrics.isNotEmpty()) totalActiveUsersSum / filteredMetrics.size else 0
    }
    val totalNewRegistrations: Int = remember(filteredMetrics) { filteredMetrics.sumOf { it.newRegistrations } }
    val totalReports: Int = remember(filteredMetrics) { filteredMetrics.sumOf { it.reportsCount } }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF9C27B0).copy(alpha = 0.6f),
                    Color(0xFF00E5FF).copy(alpha = 0.6f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Range Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF9C27B0).copy(alpha = 0.35f),
                                        Color(0xFF00E5FF).copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFF9C27B0).copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ShowChart,
                            contentDescription = "30-Day Analytics",
                            tint = Color(0xFFCE93D8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Визуализация данных (30 дней)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Активность, регистрации и модерация",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // Range Selector (7d, 14d, 30d)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(2.dp)
                ) {
                    listOf(7 to "7д", 14 to "14д", 30 to "30д").forEach { (days, label) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedRangeDays == days) Color(0xFF9C27B0) else Color.Transparent,
                            modifier = Modifier.clickable { selectedRangeDays = days }
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedRangeDays == days) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedRangeDays == days) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // KPI Overview Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsKpiTile(
                    title = "Ср. DAU",
                    value = "$avgActiveUsers",
                    subtitle = "активных в день",
                    color = Color(0xFFCE93D8),
                    modifier = Modifier.weight(1f)
                )
                AnalyticsKpiTile(
                    title = "Регистрации",
                    value = "+$totalNewRegistrations",
                    subtitle = "за $selectedRangeDays дней",
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                AnalyticsKpiTile(
                    title = "Жалобы",
                    value = "$totalReports",
                    subtitle = "за $selectedRangeDays дней",
                    color = Color(0xFFFF5252),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metric Toggle Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = showActiveUsers,
                    onClick = { showActiveUsers = !showActiveUsers },
                    label = { Text("● DAU", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF9C27B0).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFCE93D8)
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = showRegistrations,
                    onClick = { showRegistrations = !showRegistrations },
                    label = { Text("● Регистрации", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFF00E5FF)
                    ),
                    modifier = Modifier.weight(1.3f)
                )
                FilterChip(
                    selected = showReports,
                    onClick = { showReports = !showReports },
                    label = { Text("● Жалобы", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF1744).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFFF5252)
                    ),
                    modifier = Modifier.weight(1.1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Multi-Line Area Canvas Chart
            if (filteredMetrics.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Floating Scrubber Tooltip
                        val currentScrubMetric = scrubbedIndex?.let { filteredMetrics.getOrNull(it) }
                        if (currentScrubMetric != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, Color(0xFFCE93D8).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentScrubMetric.dateLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("DAU: ${currentScrubMetric.activeUsers}", color = Color(0xFFCE93D8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Рег: ${currentScrubMetric.newRegistrations}", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Жалобы: ${currentScrubMetric.reportsCount}", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "👆 Коснитесь графика для просмотра деталей за конкретный день",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        // Canvas Chart Drawing Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .pointerInput(filteredMetrics) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val stepX = size.width / (filteredMetrics.size - 1).coerceAtLeast(1)
                                            val idx = (offset.x / stepX).toInt().coerceIn(0, filteredMetrics.lastIndex)
                                            scrubbedIndex = idx
                                        },
                                        onDrag = { change, _ ->
                                            val stepX = size.width / (filteredMetrics.size - 1).coerceAtLeast(1)
                                            val idx = (change.position.x / stepX).toInt().coerceIn(0, filteredMetrics.lastIndex)
                                            scrubbedIndex = idx
                                        },
                                        onDragEnd = { scrubbedIndex = null },
                                        onDragCancel = { scrubbedIndex = null }
                                    )
                                }
                                .pointerInput(filteredMetrics) {
                                    detectTapGestures(
                                        onPress = { offset ->
                                            val stepX = size.width / (filteredMetrics.size - 1).coerceAtLeast(1)
                                            val idx = (offset.x / stepX).toInt().coerceIn(0, filteredMetrics.lastIndex)
                                            scrubbedIndex = idx
                                            tryAwaitRelease()
                                            scrubbedIndex = null
                                        }
                                    )
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val paddingBottom = 16.dp.toPx()
                                val chartHeight = height - paddingBottom
                                val n = filteredMetrics.size
                                val stepX = width / (n - 1).coerceAtLeast(1)

                                val maxDau = filteredMetrics.maxOfOrNull { it.activeUsers }?.coerceAtLeast(10) ?: 10
                                val maxReg = filteredMetrics.maxOfOrNull { it.newRegistrations }?.coerceAtLeast(5) ?: 5
                                val maxRep = filteredMetrics.maxOfOrNull { it.reportsCount }?.coerceAtLeast(5) ?: 5

                                // Horizontal grid lines
                                for (i in 1..3) {
                                    val y = chartHeight * (i / 4f)
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.08f),
                                        start = Offset(0f, y),
                                        end = Offset(width, y),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                // Helper function to build smooth cubic path
                                fun buildPath(values: List<Float>, scaleMax: Float): Path {
                                    val path = Path()
                                    values.forEachIndexed { index, v ->
                                        val x = index * stepX
                                        val y = chartHeight - (v / scaleMax * chartHeight * 0.85f)
                                        if (index == 0) {
                                            path.moveTo(x, y)
                                        } else {
                                            val prevX = (index - 1) * stepX
                                            val prevY = chartHeight - (values[index - 1] / scaleMax * chartHeight * 0.85f)
                                            val cx = (prevX + x) / 2f
                                            path.cubicTo(cx, prevY, cx, y, x, y)
                                        }
                                    }
                                    return path
                                }

                                // Draw Active Users line & fill
                                if (showActiveUsers) {
                                    val dauValues = filteredMetrics.map { it.activeUsers.toFloat() }
                                    val linePath = buildPath(dauValues, maxDau.toFloat())
                                    val fillPath = Path().apply {
                                        addPath(linePath)
                                        lineTo(width, chartHeight)
                                        lineTo(0f, chartHeight)
                                        close()
                                    }
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            listOf(Color(0xFF9C27B0).copy(alpha = 0.25f), Color.Transparent),
                                            startY = 0f,
                                            endY = chartHeight
                                        )
                                    )
                                    drawPath(
                                        path = linePath,
                                        color = Color(0xFFCE93D8),
                                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }

                                // Draw Registrations line & fill
                                if (showRegistrations) {
                                    val regValues = filteredMetrics.map { it.newRegistrations.toFloat() }
                                    val linePath = buildPath(regValues, (maxReg * 1.3f))
                                    val fillPath = Path().apply {
                                        addPath(linePath)
                                        lineTo(width, chartHeight)
                                        lineTo(0f, chartHeight)
                                        close()
                                    }
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            listOf(Color(0xFF00E5FF).copy(alpha = 0.2f), Color.Transparent),
                                            startY = 0f,
                                            endY = chartHeight
                                        )
                                    )
                                    drawPath(
                                        path = linePath,
                                        color = Color(0xFF00E5FF),
                                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }

                                // Draw Reports line
                                if (showReports) {
                                    val repValues = filteredMetrics.map { it.reportsCount.toFloat() }
                                    val linePath = buildPath(repValues, (maxRep * 1.5f))
                                    drawPath(
                                        path = linePath,
                                        color = Color(0xFFFF5252),
                                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }

                                // Vertical scrubber indicator line
                                scrubbedIndex?.let { idx ->
                                    val scrubX = idx * stepX
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.7f),
                                        start = Offset(scrubX, 0f),
                                        end = Offset(scrubX, chartHeight),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 4.dp.toPx(),
                                        center = Offset(scrubX, chartHeight - (filteredMetrics[idx].activeUsers.toFloat() / maxDau * chartHeight * 0.85f))
                                    )
                                }
                            }
                        }

                        // X-Axis Date Labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val step = (filteredMetrics.size / 4).coerceAtLeast(1)
                            for (i in filteredMetrics.indices step step) {
                                Text(
                                    text = filteredMetrics[i].dateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    fontSize = 9.sp
                                )
                            }
                            if (filteredMetrics.isNotEmpty()) {
                                Text(
                                    text = filteredMetrics.last().dateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsKpiTile(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 9.sp
            )
        }
    }
}

/**
 * Admin Action Audit Log Sub-Section displaying chronological moderator activities.
 */
@Composable
fun AdminActionLogSection(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val auditLogs by viewModel.adminAuditLogs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Bans, 2: Unbans, 3: Roles, 4: Reports

    val filteredLogs = remember(auditLogs, searchQuery, selectedFilterIndex) {
        auditLogs.filter { log ->
            val matchesSearch = searchQuery.isBlank() ||
                    log.performedBy.contains(searchQuery, ignoreCase = true) ||
                    log.targetUserName.contains(searchQuery, ignoreCase = true) ||
                    (log.reason != null && log.reason.contains(searchQuery, ignoreCase = true))

            val matchesFilter = when (selectedFilterIndex) {
                1 -> log.actionType == ActionType.USER_BANNED_TEMP || log.actionType == ActionType.USER_BANNED_PERM || log.actionType == ActionType.USER_SPAM_RESTRICTED
                2 -> log.actionType == ActionType.USER_UNBANNED || log.actionType == ActionType.USER_SPAM_UNRESTRICTED
                3 -> log.actionType == ActionType.ROLE_PROMOTED_ADMIN || log.actionType == ActionType.ROLE_REVOKED_ADMIN ||
                        log.actionType == ActionType.ROLE_PROMOTED_MOD || log.actionType == ActionType.ROLE_REVOKED_MOD
                4 -> log.actionType == ActionType.REPORT_RESOLVED_BAN || log.actionType == ActionType.REPORT_RESOLVED_RESTRICT ||
                        log.actionType == ActionType.REPORT_DISMISSED || log.actionType == ActionType.REPORT_REOPENED
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.6f),
                    Color(0xFFFFB300).copy(alpha = 0.6f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF00E5FF).copy(alpha = 0.35f),
                                        Color(0xFFFFB300).copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Action Log",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Журнал действий администраторов",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${auditLogs.size} записей аудита",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск в журнале...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filterLabels = listOf(
                    "Все (${auditLogs.size})",
                    "⛔ Блокировки",
                    "🔓 Разблокировки",
                    "★ Роли",
                    "🛡 Жалобы"
                )
                items(filterLabels.size) { idx ->
                    FilterChip(
                        selected = selectedFilterIndex == idx,
                        onClick = { selectedFilterIndex = idx },
                        label = { Text(filterLabels[idx], fontSize = 12.sp) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log Items
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Записей не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filteredLogs.forEach { log ->
                        AdminActionLogCard(log = log)
                    }
                }
            }
        }
    }
}

/**
 * Individual Card representing an audit trail log entry.
 */
@Composable
fun AdminActionLogCard(
    log: AdminActionLog,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(log.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    val (actionColor, actionIcon, actionTitle) = when (log.actionType) {
        ActionType.ROLE_PROMOTED_ADMIN -> Triple(Color(0xFFFFB300), Icons.Filled.Stars, "Назначение Администратором")
        ActionType.ROLE_REVOKED_ADMIN -> Triple(Color(0xFFFFAB40), Icons.Filled.RemoveCircleOutline, "Снятие роли Администратора")
        ActionType.ROLE_PROMOTED_MOD -> Triple(Color(0xFF00E5FF), Icons.Filled.Security, "Назначение Модератором")
        ActionType.ROLE_REVOKED_MOD -> Triple(Color(0xFFFFAB40), Icons.Filled.RemoveCircleOutline, "Снятие роли Модератора")
        ActionType.USER_BANNED_TEMP -> Triple(Color(0xFFFF5252), Icons.Filled.Block, "Временный бан")
        ActionType.USER_BANNED_PERM -> Triple(Color(0xFFFF1744), Icons.Filled.Block, "Бессрочный бан")
        ActionType.USER_UNBANNED -> Triple(Color(0xFF00E676), Icons.Filled.LockOpen, "Разблокировка пользователя")
        ActionType.USER_SPAM_RESTRICTED -> Triple(Color(0xFFFF9100), Icons.Filled.Warning, "Ограничение SpamBot")
        ActionType.USER_SPAM_UNRESTRICTED -> Triple(Color(0xFF00E676), Icons.Filled.CheckCircle, "Снятие ограничения SpamBot")
        ActionType.REPORT_RESOLVED_BAN -> Triple(Color(0xFFFF1744), Icons.Filled.Gavel, "Жалоба: Пользователь забанен")
        ActionType.REPORT_RESOLVED_RESTRICT -> Triple(Color(0xFFFF9100), Icons.Filled.Warning, "Жалоба: Выдано ограничение")
        ActionType.REPORT_DISMISSED -> Triple(Color(0xFF9E9E9E), Icons.Filled.Cancel, "Жалоба отклонена")
        ActionType.REPORT_REOPENED -> Triple(Color(0xFF29B6F6), Icons.Filled.Refresh, "Жалоба возвращена в очередь")
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, actionColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(actionColor.copy(alpha = 0.15f))
                    .border(1.dp, actionColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    actionIcon,
                    contentDescription = null,
                    tint = actionColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = actionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = actionColor,
                        fontSize = 12.sp
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${log.performedBy} → ${log.targetUserName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp
                )

                if (!log.durationLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Срок: ${log.durationLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD180),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!log.reason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Причина: ${log.reason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF8A80),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}
