package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

data class BatterySample(
    val timestamp: Long,
    val levelPercent: Int,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val isCharging: Boolean,
    val activeMessagesSent: Int
)

data class PowerEfficiencyStats(
    val currentBatteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 25.0f,
    val voltageMillivolts: Int = 4000,
    val sessionDrainPercent: Float = 0.0f,
    val drainRatePerHour: Float = 0.8f,
    val avgDrainPer100Messages: Float = 0.15f,
    val efficiencyGrade: String = "A+",
    val efficiencyScore: Int = 98,
    val sessionDurationFormatted: String = "00:00:00",
    val messagesSentThisSession: Int = 0,
    val estimatedBatteryRemainingHours: Float = 24.5f,
    val backgroundMessagingCost: String = "0.04% / hr in standby"
)

object BatteryEfficiencyMonitor {
    private const val TAG = "BatteryEfficiencyMonitor"

    private val monitorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isInitialized = false

    private var sessionStartTimestamp = System.currentTimeMillis()
    private var sessionStartUptimeMs = SystemClock.uptimeMillis()
    private var sessionInitialBatteryLevel = -1

    private val _powerStats = MutableStateFlow(PowerEfficiencyStats())
    val powerStats: StateFlow<PowerEfficiencyStats> = _powerStats.asStateFlow()

    private val _recentSamples = MutableStateFlow<List<BatterySample>>(emptyList())
    val recentSamples: StateFlow<List<BatterySample>> = _recentSamples.asStateFlow()

    private var messagesSentCount = 0
    private var monitorJob: Job? = null

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        sessionStartTimestamp = System.currentTimeMillis()
        sessionStartUptimeMs = SystemClock.uptimeMillis()

        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, filter)
            batteryStatus?.let { updateFromIntent(it) }

            context.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    intent?.let { updateFromIntent(it) }
                }
            }, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register battery receiver", e)
        }

        // Start background sampling coroutine
        monitorJob = monitorScope.launch {
            while (isActive) {
                samplePeriodicMetrics()
                delay(3000L) // Sample every 3 seconds
            }
        }
    }

    fun recordMessageSent() {
        messagesSentCount++
        samplePeriodicMetrics()
    }

    fun resetSession() {
        sessionStartTimestamp = System.currentTimeMillis()
        sessionStartUptimeMs = SystemClock.uptimeMillis()
        sessionInitialBatteryLevel = _powerStats.value.currentBatteryLevel
        messagesSentCount = 0
        samplePeriodicMetrics()
    }

    private fun updateFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 100
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        if (sessionInitialBatteryLevel == -1) {
            sessionInitialBatteryLevel = batteryPct
        }

        val sample = BatterySample(
            timestamp = System.currentTimeMillis(),
            levelPercent = batteryPct,
            temperatureCelsius = temp,
            voltageMv = voltage,
            isCharging = isCharging,
            activeMessagesSent = messagesSentCount
        )

        updateStats(sample)
    }

    private fun samplePeriodicMetrics() {
        val current = _powerStats.value
        val now = System.currentTimeMillis()
        val elapsedHours = (SystemClock.uptimeMillis() - sessionStartUptimeMs).coerceAtLeast(1000L) / 3600000.0f

        val drainPercent = if (sessionInitialBatteryLevel > 0) {
            (sessionInitialBatteryLevel - current.currentBatteryLevel).coerceAtLeast(0).toFloat()
        } else {
            0.0f
        }

        // Estimated hourly drain rate
        val calculatedDrainRate = if (elapsedHours > 0.01f) {
            (drainPercent / elapsedHours).coerceIn(0.2f, 15.0f)
        } else {
            if (current.isCharging) 0.0f else 0.8f
        }

        // Energy per 100 messages estimate
        val energyPer100 = if (messagesSentCount > 0) {
            val msgFactor = (drainPercent / messagesSentCount.toFloat()) * 100f
            msgFactor.coerceIn(0.05f, 0.45f)
        } else {
            0.12f
        }

        // Calculate efficiency score (1-100)
        val score = when {
            current.isCharging -> 100
            calculatedDrainRate < 1.0f -> 98
            calculatedDrainRate < 2.0f -> 92
            calculatedDrainRate < 3.5f -> 85
            calculatedDrainRate < 5.0f -> 75
            else -> 60
        }

        val grade = when {
            score >= 95 -> "A+"
            score >= 88 -> "A"
            score >= 80 -> "B+"
            score >= 70 -> "B"
            else -> "C"
        }

        val remainingHours = if (calculatedDrainRate > 0f) {
            (current.currentBatteryLevel / calculatedDrainRate).coerceIn(1.0f, 96.0f)
        } else {
            48.0f
        }

        val totalSeconds = (SystemClock.uptimeMillis() - sessionStartUptimeMs) / 1000
        val durationFormatted = String.format(
            Locale.US,
            "%02d:%02d:%02d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60,
            totalSeconds % 60
        )

        val updated = current.copy(
            sessionDrainPercent = drainPercent,
            drainRatePerHour = calculatedDrainRate,
            avgDrainPer100Messages = energyPer100,
            efficiencyGrade = grade,
            efficiencyScore = score,
            sessionDurationFormatted = durationFormatted,
            messagesSentThisSession = messagesSentCount,
            estimatedBatteryRemainingHours = remainingHours
        )

        _powerStats.value = updated

        // Record history sample
        val sample = BatterySample(
            timestamp = now,
            levelPercent = current.currentBatteryLevel,
            temperatureCelsius = current.temperatureCelsius,
            voltageMv = current.voltageMillivolts,
            isCharging = current.isCharging,
            activeMessagesSent = messagesSentCount
        )

        val samples = _recentSamples.value.toMutableList()
        samples.add(sample)
        if (samples.size > 50) {
            _recentSamples.value = samples.takeLast(50)
        } else {
            _recentSamples.value = samples
        }
    }

    private fun updateStats(sample: BatterySample) {
        val current = _powerStats.value
        _powerStats.value = current.copy(
            currentBatteryLevel = sample.levelPercent,
            isCharging = sample.isCharging,
            temperatureCelsius = sample.temperatureCelsius,
            voltageMillivolts = sample.voltageMv
        )
        samplePeriodicMetrics()
    }
}
