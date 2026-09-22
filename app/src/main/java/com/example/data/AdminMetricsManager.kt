package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

data class DailyMetric(
    val dayIndex: Int, // 0 to 29 (0 = 29 days ago, 29 = today)
    val dateLabel: String, // "01 авг", "15 авг"
    val timestamp: Long,
    val activeUsers: Int, // DAU: 1200 - 3800
    val newRegistrations: Int, // 40 - 220
    val reportsCount: Int // 2 - 28
)

data class MetricsSummary(
    val totalActiveUsers30d: Int,
    val avgDailyActiveUsers: Int,
    val peakActiveUsers: Int,
    val totalNewRegistrations30d: Int,
    val registrationGrowthPercent: Float,
    val totalReports30d: Int,
    val resolvedReportsPercent: Float
)

/**
 * AdminMetricsManager
 *
 * Provides historical 30-day time-series telemetry data for the Admin Data Visualization Chart:
 * - Active Daily Users (DAU)
 * - New User Registrations
 * - Reported Messages & Violations
 */
object AdminMetricsManager {

    private val _dailyMetrics = MutableStateFlow<List<DailyMetric>>(generateInitial30DayMetrics())
    val dailyMetrics: StateFlow<List<DailyMetric>> = _dailyMetrics.asStateFlow()

    private fun generateInitial30DayMetrics(): List<DailyMetric> {
        val list = mutableListOf<DailyMetric>()
        val cal = Calendar.getInstance(Locale("ru", "RU"))
        val dateFormat = SimpleDateFormat("d MMM", Locale("ru", "RU"))

        // Anchor baseline numbers
        val baseUsers = 1450
        val baseReg = 65
        val baseRep = 7

        for (i in 29 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val time = dayCal.timeInMillis
            val dateStr = dateFormat.format(Date(time))

            // Organic progressive upward curve with realistic weekend/weekday wave fluctuations
            val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)
            val weekendBoost = if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) 1.25f else 1.0f
            val growthFactor = 1.0f + ((29 - i) * 0.035f) // ~100% growth over month

            // Deterministic pseudo-random variation based on day
            val userVariance = ((Math.sin(i * 0.8) * 180) + (Math.cos(i * 1.5) * 90)).toInt()
            val regVariance = ((Math.cos(i * 1.1) * 22) + (Math.sin(i * 0.5) * 15)).toInt()
            val repVariance = ((Math.sin(i * 1.9) * 4) + (if (i % 6 == 0) 6 else 0)).toInt()

            val dau = ((baseUsers * growthFactor * weekendBoost).toInt() + userVariance).coerceAtLeast(800)
            val newReg = ((baseReg * growthFactor * weekendBoost * 0.9f).toInt() + regVariance).coerceAtLeast(15)
            val reports = ((baseRep + (dau / 350) + repVariance)).coerceIn(1, 45)

            list.add(
                DailyMetric(
                    dayIndex = 29 - i,
                    dateLabel = dateStr,
                    timestamp = time,
                    activeUsers = dau,
                    newRegistrations = newReg,
                    reportsCount = reports
                )
            )
        }
        return list
    }

    fun recordNewRegistration() {
        _dailyMetrics.update { current ->
            if (current.isEmpty()) return@update current
            val last = current.last()
            current.dropLast(1) + last.copy(
                activeUsers = last.activeUsers + 1,
                newRegistrations = last.newRegistrations + 1
            )
        }
    }

    fun recordNewReport() {
        _dailyMetrics.update { current ->
            if (current.isEmpty()) return@update current
            val last = current.last()
            current.dropLast(1) + last.copy(
                reportsCount = last.reportsCount + 1
            )
        }
    }

    fun getSummary(daysRange: Int = 30): MetricsSummary {
        val slice = _dailyMetrics.value.takeLast(daysRange.coerceIn(1, 30))
        if (slice.isEmpty()) {
            return MetricsSummary(0, 0, 0, 0, 0f, 0, 100f)
        }

        val totalDau = slice.sumOf { it.activeUsers }
        val avgDau = totalDau / slice.size
        val peakDau = slice.maxOf { it.activeUsers }
        val totalReg = slice.sumOf { it.newRegistrations }
        val totalRep = slice.sumOf { it.reportsCount }

        val firstHalfReg = slice.take(slice.size / 2).sumOf { it.newRegistrations }
        val secondHalfReg = slice.takeLast(slice.size / 2).sumOf { it.newRegistrations }
        val growth = if (firstHalfReg > 0) {
            ((secondHalfReg - firstHalfReg).toFloat() / firstHalfReg) * 100f
        } else 18.5f

        return MetricsSummary(
            totalActiveUsers30d = totalDau,
            avgDailyActiveUsers = avgDau,
            peakActiveUsers = peakDau,
            totalNewRegistrations30d = totalReg,
            registrationGrowthPercent = growth,
            totalReports30d = totalRep,
            resolvedReportsPercent = 97.8f
        )
    }
}
