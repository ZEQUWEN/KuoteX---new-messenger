package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.analytics.AnalyticsEventRecord
import com.example.analytics.AnalyticsPriority
import com.example.analytics.FirebaseAnalyticsHelper
import com.example.utils.BandwidthPoint
import com.example.utils.NetworkBandwidthMonitor
import com.example.utils.NetworkStatusCategory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperAnalyticsDebugScreen(viewModel: AppViewModel, navController: NavController) {
    val context = LocalContext.current
    val activeAccount = LocalActiveAccount.current
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
                    "Only account +79226692682 is authorized to inspect live Firebase Analytics logs.",
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

    val eventLogs by FirebaseAnalyticsHelper.eventLogs.collectAsState()
    val queuedEvents by FirebaseAnalyticsHelper.queuedEvents.collectAsState()
    val isPoorConnection by FirebaseAnalyticsHelper.isPoorConnectionMode.collectAsState()
    val totalLogged by FirebaseAnalyticsHelper.totalEventsLogged.collectAsState()
    val batchesFlushed by FirebaseAnalyticsHelper.totalBatchesFlushed.collectAsState()

    val bandwidthStats by NetworkBandwidthMonitor.throughputStats.collectAsState()
    val bandwidthHistory by NetworkBandwidthMonitor.bandwidthHistory.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var selectedPriorityFilter by remember { mutableStateOf<AnalyticsPriority?>(null) }
    var expandedEventId by remember { mutableStateOf<String?>(null) }
    var showTestToolsSheet by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showBandwidthCard by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Helper to copy text to system clipboard
    fun copyToClipboard(text: String, label: String, message: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Filter events
    val filteredLogs = remember(eventLogs, searchQuery, selectedCategoryFilter, selectedPriorityFilter) {
        eventLogs.filter { record ->
            val matchesSearch = searchQuery.isBlank() ||
                    record.eventName.contains(searchQuery, ignoreCase = true) ||
                    record.params.any { (k, v) ->
                        k.contains(searchQuery, ignoreCase = true) ||
                                v.toString().contains(searchQuery, ignoreCase = true)
                    }

            val matchesCategory = when (selectedCategoryFilter) {
                "STREAM_JOIN" -> record.eventName == FirebaseAnalyticsHelper.Schema.Events.STREAM_JOIN
                "MSG_LATENCY" -> record.eventName == FirebaseAnalyticsHelper.Schema.Events.MESSAGE_LATENCY
                "MSG_DELIVERY" -> record.eventName == FirebaseAnalyticsHelper.Schema.Events.MESSAGE_SEND_SUCCESS ||
                        record.eventName == FirebaseAnalyticsHelper.Schema.Events.MESSAGE_SEND_FAILURE
                "DROP_OFF" -> record.eventName == FirebaseAnalyticsHelper.Schema.Events.VIEWER_DROP_OFF
                "UI_LATENCY" -> record.eventName == FirebaseAnalyticsHelper.Schema.Events.UI_INTERACTION_LATENCY
                "BATCH_SYNC" -> record.eventName == FirebaseAnalyticsHelper.Schema.Events.MESSAGE_QUEUE_BATCH_SYNC
                else -> true
            }

            val matchesPriority = selectedPriorityFilter == null || record.priority == selectedPriorityFilter

            matchesSearch && matchesCategory && matchesPriority
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Analytics Real-Time Debug",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            // Live status indicator dot
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
                            "${filteredLogs.size} visible • ${queuedEvents.size} queued • $totalLogged total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Export / Copy to Clipboard Action
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy Logs to Clipboard",
                                tint = Color(0xFF00E5FF)
                            )
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy Current Logs as JSON") },
                                leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    val json = FirebaseAnalyticsHelper.exportLogsAsJson(filteredLogs)
                                    copyToClipboard(json, "Firebase_Analytics_JSON", "Copied ${filteredLogs.size} events as JSON to clipboard")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy as Diagnostic Text") },
                                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    val text = FirebaseAnalyticsHelper.exportLogsAsFormattedText(filteredLogs)
                                    copyToClipboard(text, "Firebase_Analytics_Logs", "Copied ${filteredLogs.size} formatted logs to clipboard")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy All (${eventLogs.size}) Session Events") },
                                leadingIcon = { Icon(Icons.Filled.AllInclusive, contentDescription = null) },
                                onClick = {
                                    showExportMenu = false
                                    val json = FirebaseAnalyticsHelper.exportLogsAsJson(eventLogs)
                                    copyToClipboard(json, "All_Session_Analytics", "Copied full session (${eventLogs.size} events) as JSON")
                                }
                            )
                        }
                    }

                    // Flush Batch Action
                    IconButton(onClick = { FirebaseAnalyticsHelper.flushBatch() }) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Flush Batch",
                            tint = Color(0xFF00FFFF)
                        )
                    }
                    // Clear Logs Action
                    IconButton(onClick = { FirebaseAnalyticsHelper.clearLogs() }) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = "Clear Logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Test Tools Action
                    IconButton(onClick = { showTestToolsSheet = true }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Trigger Test Events",
                            tint = Color(0xFFFF007F)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Real-Time Visual Network Status Indicator Banner
            NetworkStatusIndicatorBanner(
                statusCategory = bandwidthStats.statusCategory,
                isPoorConnectionSimulated = isPoorConnection,
                queuedEventsCount = queuedEvents.size,
                onToggleSimulation = { FirebaseAnalyticsHelper.setPoorConnectionMode(it) }
            )

            // Real-Time Bandwidth Spike Visualizer Card
            RealTimeBandwidthCard(
                stats = bandwidthStats,
                history = bandwidthHistory,
                isExpanded = showBandwidthCard,
                onToggleExpand = { showBandwidthCard = !showBandwidthCard },
                onSimulateSpike = {
                    NetworkBandwidthMonitor.recordBatchSpike(
                        label = "Simulated Batch Sync (48KB)",
                        sentBytes = 48000L,
                        receivedBytes = 12000L
                    )
                }
            )

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter by event name, stream ID, latency...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "ALL" to "All Events",
                    "STREAM_JOIN" to "Stream Join",
                    "MSG_LATENCY" to "Msg Latency",
                    "MSG_DELIVERY" to "Msg Delivery",
                    "DROP_OFF" to "Viewer Drop-off",
                    "UI_LATENCY" to "UI Latency",
                    "BATCH_SYNC" to "Batch Sync"
                )
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = selectedCategoryFilter == key,
                        onClick = { selectedCategoryFilter = key },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            // Priority Filter Chips & Copy Quick Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PriorityPill(
                        label = "ALL",
                        isSelected = selectedPriorityFilter == null,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) { selectedPriorityFilter = null }

                    PriorityPill(
                        label = "CRITICAL",
                        isSelected = selectedPriorityFilter == AnalyticsPriority.CRITICAL,
                        color = Color(0xFFFF007F)
                    ) { selectedPriorityFilter = AnalyticsPriority.CRITICAL }

                    PriorityPill(
                        label = "HIGH",
                        isSelected = selectedPriorityFilter == AnalyticsPriority.HIGH,
                        color = Color(0xFFFF9800)
                    ) { selectedPriorityFilter = AnalyticsPriority.HIGH }

                    PriorityPill(
                        label = "NORMAL",
                        isSelected = selectedPriorityFilter == AnalyticsPriority.NORMAL,
                        color = Color(0xFF00E5FF)
                    ) { selectedPriorityFilter = AnalyticsPriority.NORMAL }

                    PriorityPill(
                        label = "LOW",
                        isSelected = selectedPriorityFilter == AnalyticsPriority.LOW,
                        color = Color(0xFF9E9E9E)
                    ) { selectedPriorityFilter = AnalyticsPriority.LOW }
                }

                TextButton(
                    onClick = {
                        val json = FirebaseAnalyticsHelper.exportLogsAsJson(filteredLogs)
                        copyToClipboard(json, "Filtered_Logs", "Copied ${filteredLogs.size} logs to clipboard")
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy (${filteredLogs.size})", fontSize = 11.sp)
                }
            }

            // Event Logs Feed
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No analytics events recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showTestToolsSheet = true }) {
                            Text("Trigger Test Analytics Event")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { record ->
                        val isExpanded = expandedEventId == record.id
                        AnalyticsEventCard(
                            record = record,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedEventId = if (isExpanded) null else record.id
                            },
                            onCopySingle = {
                                val json = FirebaseAnalyticsHelper.getSingleEventJson(record)
                                copyToClipboard(json, record.eventName, "Copied '${record.eventName}' payload to clipboard")
                            }
                        )
                    }
                    item {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Test Event Triggers
    if (showTestToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTestToolsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "Trigger Custom Analytics Events",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Inspect live telemetry parsing, queue behavior & bandwidth spikes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        val testStreamId = "stream_${System.currentTimeMillis().toString().takeLast(4)}"
                        FirebaseAnalyticsHelper.logStreamJoin(
                            streamId = testStreamId,
                            hostId = "user_neo",
                            isHost = false,
                            joinDurationMs = (150L..2500L).random(),
                            initialViewerCount = (10..500).random(),
                            streamTitle = "KuoteX Live Broadcast #42"
                        )
                        showTestToolsSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Trigger Stream Join (stream_join)")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val testMsgId = "msg_${System.currentTimeMillis().toString().takeLast(4)}"
                        val isSuccess = listOf(true, true, true, false).random()
                        val latency = (45L..1800L).random()
                        if (isSuccess) {
                            FirebaseAnalyticsHelper.logMessageSendSuccess(
                                messageId = testMsgId,
                                chatId = "chat_neo",
                                durationMs = latency
                            )
                        } else {
                            FirebaseAnalyticsHelper.logMessageSendFailure(
                                messageId = testMsgId,
                                chatId = "chat_neo",
                                errorCode = "NETWORK_TIMEOUT_504",
                                errorMessage = "Socket stream closed unexpectedly",
                                durationMs = latency
                            )
                        }
                        showTestToolsSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF007F))
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Trigger Message Delivery (CRITICAL)")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val testStreamId = "stream_${System.currentTimeMillis().toString().takeLast(4)}"
                        FirebaseAnalyticsHelper.logViewerDropOff(
                            streamId = testStreamId,
                            hostId = "user_neo",
                            isHost = false,
                            watchDurationSeconds = (5L..900L).random(),
                            commentsSent = (0..12).random(),
                            reactionsSent = (0..30).random(),
                            starsDonated = (0..50).random(),
                            peakViewersSeen = 350,
                            dropOffReason = listOf("user_exit", "network_disconnect", "stream_ended").random()
                        )
                        showTestToolsSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Trigger Viewer Drop-off (viewer_drop_off)")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        NetworkBandwidthMonitor.recordBatchSpike(
                            label = "Large Batch Message Sync (64KB)",
                            sentBytes = 64000L,
                            receivedBytes = 18000L
                        )
                        showTestToolsSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB500FF))
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Simulate Large Batch Bandwidth Spike")
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Visual Status Indicator Banner showing real-time network connectivity and queue mode transitions
 */
@Composable
private fun NetworkStatusIndicatorBanner(
    statusCategory: NetworkStatusCategory,
    isPoorConnectionSimulated: Boolean,
    queuedEventsCount: Int,
    onToggleSimulation: (Boolean) -> Unit
) {
    val statusColor = when (statusCategory) {
        NetworkStatusCategory.ONLINE_HIGH_SPEED -> Color(0xFF00E676)
        NetworkStatusCategory.ONLINE_METERED -> Color(0xFF00E5FF)
        NetworkStatusCategory.POOR_CONNECTION -> Color(0xFFFF9800)
        NetworkStatusCategory.OFFLINE -> Color(0xFFFF3B30)
    }

    val statusIcon = when (statusCategory) {
        NetworkStatusCategory.ONLINE_HIGH_SPEED -> Icons.Filled.Wifi
        NetworkStatusCategory.ONLINE_METERED -> Icons.Filled.SignalCellularAlt
        NetworkStatusCategory.POOR_CONNECTION -> Icons.Filled.SignalCellularConnectedNoInternet0Bar
        NetworkStatusCategory.OFFLINE -> Icons.Filled.CloudOff
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = statusColor.copy(alpha = 0.12f),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(statusColor.copy(alpha = 0.5f)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusCategory.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        if (statusCategory.isConstrained) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = statusColor.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "$queuedEventsCount QUEUED",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = statusColor
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isPoorConnectionSimulated) "Simulated Poor Connection Active" else statusCategory.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Simulate Offline Queue",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = isPoorConnectionSimulated,
                    onCheckedChange = onToggleSimulation,
                    modifier = Modifier.scale(0.85f)
                )
            }
        }
    }
}

/**
 * Real-Time Network Bandwidth Visualization Card showing live throughput & batch sync data spikes
 */
@Composable
private fun RealTimeBandwidthCard(
    stats: com.example.utils.NetworkThroughputStats,
    history: List<BandwidthPoint>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSimulateSpike: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.ShowChart,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Real-Time Bandwidth & Data Spikes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tx: ${String.format(Locale.US, "%.1f", stats.currentTxKbps)} KB/s • Rx: ${String.format(Locale.US, "%.1f", stats.currentRxKbps)} KB/s",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Bandwidth Canvas Chart
                    BandwidthSpikeCanvasChart(
                        history = history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Metrics Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Peak Throughput: Tx ${String.format(Locale.US, "%.1f", stats.peakTxKbps)} KB/s | Rx ${String.format(Locale.US, "%.1f", stats.peakRxKbps)} KB/s",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            stats.lastSpikeEvent?.let { spike ->
                                Text(
                                    "⚡ Last Surge: $spike",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF9800),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = onSimulateSpike,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Simulate Spike", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Canvas rendering real-time network throughput and data usage spikes
 */
@Composable
private fun BandwidthSpikeCanvasChart(
    history: List<BandwidthPoint>,
    modifier: Modifier = Modifier
) {
    val txColor = Color(0xFFFF007F)
    val rxColor = Color(0xFF00E5FF)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0E1A))
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        val width = size.width
        val height = size.height

        // Draw horizontal grid lines
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.25f),
            end = Offset(width, height * 0.25f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.5f),
            end = Offset(width, height * 0.5f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 0.75f),
            end = Offset(width, height * 0.75f),
            strokeWidth = 1.dp.toPx()
        )

        if (history.size < 2) return@Canvas

        val maxVal = maxOf(
            history.maxOfOrNull { maxOf(it.txKilobytesPerSec, it.rxKilobytesPerSec) } ?: 10f,
            20f
        )

        val stepX = width / (history.size - 1).coerceAtLeast(1)

        val txPath = Path()
        val rxPath = Path()

        history.forEachIndexed { index, point ->
            val x = index * stepX
            val txY = height - ((point.txKilobytesPerSec / maxVal) * (height - 8f)).coerceIn(4f, height - 4f)
            val rxY = height - ((point.rxKilobytesPerSec / maxVal) * (height - 8f)).coerceIn(4f, height - 4f)

            if (index == 0) {
                txPath.moveTo(x, txY)
                rxPath.moveTo(x, rxY)
            } else {
                txPath.lineTo(x, txY)
                rxPath.lineTo(x, rxY)
            }

            // Draw Spike Indicator dot
            if (point.isSpike) {
                drawCircle(
                    color = Color(0xFFFF9800),
                    radius = 4.dp.toPx(),
                    center = Offset(x, minOf(txY, rxY))
                )
            }
        }

        // Draw Rx Path (Cyan)
        drawPath(
            path = rxPath,
            color = rxColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Tx Path (Magenta)
        drawPath(
            path = txPath,
            color = txColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun PriorityPill(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(color)) else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnalyticsEventCard(
    record: AnalyticsEventRecord,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopySingle: () -> Unit
) {
    val timeFormatted = remember(record.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(record.timestamp))
    }

    val priorityColor = when (record.priority) {
        AnalyticsPriority.CRITICAL -> Color(0xFFFF007F)
        AnalyticsPriority.HIGH -> Color(0xFFFF9800)
        AnalyticsPriority.NORMAL -> Color(0xFF00E5FF)
        AnalyticsPriority.LOW -> Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Priority tag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = priorityColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = record.priority.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = priorityColor,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = record.eventName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onCopySingle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copy Event JSON",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Quick Parameters Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val summary = buildString {
                    record.params["latency_ms"]?.let { append("latency: ${it}ms • ") }
                    record.params["duration_ms"]?.let { append("duration: ${it}ms • ") }
                    record.params["watch_duration_seconds"]?.let { append("watch: ${it}s • ") }
                    record.params["status"]?.let { append("status: $it • ") }
                    record.params["stream_id"]?.let { append("stream: $it • ") }
                    record.params["message_id"]?.let { append("msg: $it • ") }
                    record.params["action_name"]?.let { append("action: $it • ") }
                }.trimEnd(' ', '•')

                Text(
                    text = summary.ifBlank { "${record.params.size} parameters" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (record.dispatchStatus == "SENT_IMMEDIATELY") Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = record.dispatchStatus,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.dispatchStatus == "SENT_IMMEDIATELY") Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontSize = 9.sp
                    )
                }
            }

            // Expanded Key-Value Inspector
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Payload Parameters:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = onCopySingle,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy JSON", fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    record.params.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extension to scale modifiers
private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.size((36 * scale).dp, (20 * scale).dp)
)
