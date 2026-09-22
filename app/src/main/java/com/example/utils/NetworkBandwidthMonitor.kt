package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

enum class NetworkStatusCategory(
    val title: String,
    val subtitle: String,
    val isOnline: Boolean,
    val isConstrained: Boolean
) {
    ONLINE_HIGH_SPEED("Online (Direct Flush)", "High-speed connection available", true, false),
    ONLINE_METERED("Online (Metered)", "Normal connection, standard dispatch", true, false),
    POOR_CONNECTION("Poor Connection (Offline Queue)", "Network constrained: Low priority events queued", true, true),
    OFFLINE("Offline (Buffer Mode)", "No internet connection: All operations buffered", false, true)
}

data class BandwidthPoint(
    val timestamp: Long,
    val txKilobytesPerSec: Float,
    val rxKilobytesPerSec: Float,
    val isSpike: Boolean = false,
    val spikeLabel: String? = null
)

data class NetworkThroughputStats(
    val currentTxKbps: Float = 0f,
    val currentRxKbps: Float = 0f,
    val peakTxKbps: Float = 0f,
    val peakRxKbps: Float = 0f,
    val totalTxBytesSession: Long = 0L,
    val totalRxBytesSession: Long = 0L,
    val statusCategory: NetworkStatusCategory = NetworkStatusCategory.ONLINE_HIGH_SPEED,
    val lastSpikeEvent: String? = null,
    val lastSpikeTimestamp: Long = 0L
)

object NetworkBandwidthMonitor {
    private const val TAG = "NetworkBandwidthMonitor"

    private val monitorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isInitialized = false

    private val _throughputStats = MutableStateFlow(NetworkThroughputStats())
    val throughputStats: StateFlow<NetworkThroughputStats> = _throughputStats.asStateFlow()

    private val _bandwidthHistory = MutableStateFlow<List<BandwidthPoint>>(emptyList())
    val bandwidthHistory: StateFlow<List<BandwidthPoint>> = _bandwidthHistory.asStateFlow()

    private var lastTotalTxBytes = 0L
    private var lastTotalRxBytes = 0L
    private var lastSampleTimeMs = 0L
    private var simulatedSpikeTxBytes = 0L
    private var simulatedSpikeRxBytes = 0L
    private var pendingSpikeLabel: String? = null

    private var appContext: Context? = null
    private var connectivityManager: ConnectivityManager? = null

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext

        lastTotalTxBytes = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        lastTotalRxBytes = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        lastSampleTimeMs = SystemClock.elapsedRealtime()

        // Setup connectivity callback
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager = cm
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    evaluateNetworkState()
                }

                override fun onLost(network: Network) {
                    evaluateNetworkState()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    evaluateNetworkState()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Could not register NetworkCallback: ${e.message}")
        }

        // Initialize historical baseline
        val initialHistory = mutableListOf<BandwidthPoint>()
        val now = System.currentTimeMillis()
        for (i in 29 downTo 0) {
            initialHistory.add(
                BandwidthPoint(
                    timestamp = now - (i * 1000L),
                    txKilobytesPerSec = 0.5f + (Math.random().toFloat() * 1.2f),
                    rxKilobytesPerSec = 1.0f + (Math.random().toFloat() * 2.5f)
                )
            )
        }
        _bandwidthHistory.value = initialHistory

        // Periodic sampling loop
        monitorScope.launch {
            while (isActive) {
                sampleBandwidth()
                delay(1000L)
            }
        }
    }

    /**
     * Record a large batch synchronization or analytics flush event to visualize instantaneous throughput spike
     */
    fun recordBatchSpike(label: String, sentBytes: Long, receivedBytes: Long) {
        synchronized(this) {
            simulatedSpikeTxBytes += sentBytes
            simulatedSpikeRxBytes += receivedBytes
            pendingSpikeLabel = label
        }
        sampleBandwidth()
    }

    fun evaluateNetworkState(overridePoorConnection: Boolean? = null) {
        val poorMode = overridePoorConnection ?: false
        val cm = connectivityManager
        val activeNet = cm?.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }

        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val newCategory = when {
            poorMode -> NetworkStatusCategory.POOR_CONNECTION
            !hasInternet && activeNet == null -> NetworkStatusCategory.OFFLINE
            isWifi -> NetworkStatusCategory.ONLINE_HIGH_SPEED
            else -> NetworkStatusCategory.ONLINE_METERED
        }

        val current = _throughputStats.value
        _throughputStats.value = current.copy(statusCategory = newCategory)
    }

    private fun sampleBandwidth() {
        val nowRealtime = SystemClock.elapsedRealtime()
        val deltaMs = (nowRealtime - lastSampleTimeMs).coerceAtLeast(100L)
        val nowTime = System.currentTimeMillis()

        val rawTx = TrafficStats.getTotalTxBytes()
        val rawRx = TrafficStats.getTotalRxBytes()

        val currentTx = if (rawTx != TrafficStats.UNSUPPORTED.toLong() && rawTx >= lastTotalTxBytes) {
            rawTx - lastTotalTxBytes
        } else {
            0L
        }

        val currentRx = if (rawRx != TrafficStats.UNSUPPORTED.toLong() && rawRx >= lastTotalRxBytes) {
            rawRx - lastTotalRxBytes
        } else {
            0L
        }

        var extraTx = 0L
        var extraRx = 0L
        var spikeTag: String? = null

        synchronized(this) {
            extraTx = simulatedSpikeTxBytes
            extraRx = simulatedSpikeRxBytes
            spikeTag = pendingSpikeLabel
            simulatedSpikeTxBytes = 0L
            simulatedSpikeRxBytes = 0L
            pendingSpikeLabel = null
        }

        val totalDeltaTx = currentTx + extraTx
        val totalDeltaRx = currentRx + extraRx

        val txKbps = (totalDeltaTx / 1024f) / (deltaMs / 1000f)
        val rxKbps = (totalDeltaRx / 1024f) / (deltaMs / 1000f)

        lastTotalTxBytes = if (rawTx != TrafficStats.UNSUPPORTED.toLong()) rawTx else lastTotalTxBytes
        lastTotalRxBytes = if (rawRx != TrafficStats.UNSUPPORTED.toLong()) rawRx else lastTotalRxBytes
        lastSampleTimeMs = nowRealtime

        val current = _throughputStats.value
        val isSpike = (txKbps > 15f || rxKbps > 30f || spikeTag != null)

        val updated = current.copy(
            currentTxKbps = txKbps,
            currentRxKbps = rxKbps,
            peakTxKbps = maxOf(current.peakTxKbps, txKbps),
            peakRxKbps = maxOf(current.peakRxKbps, rxKbps),
            totalTxBytesSession = current.totalTxBytesSession + totalDeltaTx,
            totalRxBytesSession = current.totalRxBytesSession + totalDeltaRx,
            lastSpikeEvent = if (isSpike) (spikeTag ?: "Throughput Surge (${String.format(Locale.US, "%.1f", txKbps + rxKbps)} KB/s)") else current.lastSpikeEvent,
            lastSpikeTimestamp = if (isSpike) nowTime else current.lastSpikeTimestamp
        )
        _throughputStats.value = updated

        // Record history point
        val point = BandwidthPoint(
            timestamp = nowTime,
            txKilobytesPerSec = txKbps,
            rxKilobytesPerSec = rxKbps,
            isSpike = isSpike,
            spikeLabel = spikeTag
        )

        val history = _bandwidthHistory.value.toMutableList()
        history.add(point)
        if (history.size > 30) {
            _bandwidthHistory.value = history.takeLast(30)
        } else {
            _bandwidthHistory.value = history
        }
    }
}
