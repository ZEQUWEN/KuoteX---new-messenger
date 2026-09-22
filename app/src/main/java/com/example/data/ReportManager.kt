package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.analytics.AnalyticsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class ReportStatus {
    PENDING,
    RESOLVED_BANNED,
    RESOLVED_WARNING,
    DISMISSED
}

data class ContentReport(
    val id: String,
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val senderDisplayName: String,
    val senderUsername: String,
    val reporterId: String,
    val reporterName: String,
    val messageText: String,
    val reasonCategory: String, // "Спам", "Оскорбления / Угрозы", "Неприемлемый контент (18+)", "Мошенничество / Фишинг", "Другое"
    val userComment: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ReportStatus = ReportStatus.PENDING,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null,
    val resolutionNote: String? = null
)

/**
 * ReportManager
 *
 * Manages user reports on objectionable content, providing:
 * - Direct intake from message context menus
 * - Real-time StateFlow for Admin Panel notifications & badges
 * - Local persistence in SharedPreferences & Firestore sync simulation
 * - Resolution flows (Dismiss, Ban Author, Issue Warning)
 */
object ReportManager {
    private const val TAG = "ReportManager"
    private const val PREFS_NAME = "neon_reports_prefs"
    private const val KEY_REPORTS_JSON = "saved_reports_json"

    private var sharedPrefs: SharedPreferences? = null

    private val _reports = MutableStateFlow<List<ContentReport>>(emptyList())
    val reports: StateFlow<List<ContentReport>> = _reports.asStateFlow()

    private val _newReportEvent = MutableStateFlow<ContentReport?>(null)
    val newReportEvent: StateFlow<ContentReport?> = _newReportEvent.asStateFlow()

    fun init(context: Context) {
        if (sharedPrefs == null) {
            sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val cached = loadFromPrefs()
        if (cached.isNotEmpty()) {
            _reports.value = cached
        } else {
            // Seed initial sample reports for testing and rich admin UI
            val sampleReports = listOf(
                ContentReport(
                    id = "rep_101",
                    messageId = "msg_spam_99",
                    chatId = "c1",
                    senderId = "u3_crypto",
                    senderDisplayName = "Crypto Alpha",
                    senderUsername = "@crypto_alpha",
                    reporterId = "123456789",
                    reporterName = "Neo",
                    messageText = "🔥 ЗАБИРАЙ 5000 USDT БЕСПЛАТНО! Переходи по ссылке bit.ly/free-crypto-drop прямо сейчас!",
                    reasonCategory = "Спам и реклама",
                    userComment = "Подозрительная фишинговая ссылка в общем чате группы",
                    timestamp = System.currentTimeMillis() - (3600000L * 3),
                    status = ReportStatus.PENDING
                ),
                ContentReport(
                    id = "rep_102",
                    messageId = "msg_toxic_12",
                    chatId = "c1",
                    senderId = "456789123",
                    senderDisplayName = "Cyber P.",
                    senderUsername = "@cyber_punk",
                    reporterId = "u1_sarah",
                    reporterName = "Sarah Connor",
                    messageText = "Ты вообще ничего не понимаешь в коде, удали аккаунт и не позорься!",
                    reasonCategory = "Оскорбления и угрозы",
                    userComment = "Токсичное поведение и переход на личности",
                    timestamp = System.currentTimeMillis() - (3600000L * 8),
                    status = ReportStatus.PENDING
                ),
                ContentReport(
                    id = "rep_103",
                    messageId = "msg_scam_04",
                    chatId = "c2",
                    senderId = "u5_spammer",
                    senderDisplayName = "Fast Money Bot",
                    senderUsername = "@money_fast",
                    reporterId = "987654321",
                    reporterName = "Synth Wave",
                    messageText = "Инвестируйте в наш фонд 100$ и получайте 1000$ каждый день! Гарантия 100%!",
                    reasonCategory = "Мошенничество / Фишинг",
                    userComment = "Очевидная финансовая пирамида",
                    timestamp = System.currentTimeMillis() - (86400000L * 2),
                    status = ReportStatus.RESOLVED_BANNED,
                    resolvedBy = "Dev Admin (+79226692682)",
                    resolvedAt = System.currentTimeMillis() - (86400000L * 1),
                    resolutionNote = "Пользователь заблокирован бессрочно"
                )
            )
            _reports.value = sampleReports
            saveToPrefs(sampleReports)
        }
    }

    fun submitReport(
        messageId: String,
        chatId: String,
        senderId: String,
        senderDisplayName: String,
        senderUsername: String,
        reporterId: String,
        reporterName: String,
        messageText: String,
        reasonCategory: String,
        userComment: String
    ): ContentReport {
        val report = ContentReport(
            id = "rep_${System.currentTimeMillis() % 1000000}",
            messageId = messageId,
            chatId = chatId,
            senderId = senderId,
            senderDisplayName = senderDisplayName.ifBlank { "User $senderId" },
            senderUsername = senderUsername.ifBlank { "@user_${senderId.takeLast(4)}" },
            reporterId = reporterId,
            reporterName = reporterName.ifBlank { "Reporter $reporterId" },
            messageText = messageText,
            reasonCategory = reasonCategory,
            userComment = userComment,
            timestamp = System.currentTimeMillis(),
            status = ReportStatus.PENDING
        )

        _reports.update { listOf(report) + it }
        _newReportEvent.value = report
        saveToPrefs(_reports.value)

        // Log analytics
        AnalyticsTracker.logChatAction(
            action = "message_reported",
            chatId = chatId,
            metadata = mapOf(
                "report_id" to report.id,
                "reason" to reasonCategory,
                "sender_id" to senderId,
                "reporter_id" to reporterId
            )
        )

        Log.i(TAG, "🚨 [Report Received] ID: ${report.id}, Reason: $reasonCategory on message: '$messageText'")
        return report
    }

    fun clearNewReportEvent() {
        _newReportEvent.value = null
    }

    fun resolveReport(
        reportId: String,
        newStatus: ReportStatus,
        moderator: String = "Dev Admin (+79226692682)",
        note: String? = null
    ) {
        _reports.update { list ->
            list.map { report ->
                if (report.id == reportId) {
                    report.copy(
                        status = newStatus,
                        resolvedBy = moderator,
                        resolvedAt = System.currentTimeMillis(),
                        resolutionNote = note ?: when (newStatus) {
                            ReportStatus.RESOLVED_BANNED -> "Автор заблокирован модератором"
                            ReportStatus.RESOLVED_WARNING -> "Автору выдано предупреждение SpamBot"
                            ReportStatus.DISMISSED -> "Жалоба отклонена: нарушение не обнаружено"
                            ReportStatus.PENDING -> null
                        }
                    )
                } else report
            }
        }
        saveToPrefs(_reports.value)

        // Add to audit log
        val targetReport = _reports.value.find { it.id == reportId }
        if (targetReport != null) {
            AdminAuditLogManager.logAction(
                actionType = when (newStatus) {
                    ReportStatus.RESOLVED_BANNED -> ActionType.REPORT_RESOLVED_BAN
                    ReportStatus.RESOLVED_WARNING -> ActionType.REPORT_RESOLVED_RESTRICT
                    ReportStatus.DISMISSED -> ActionType.REPORT_DISMISSED
                    ReportStatus.PENDING -> ActionType.REPORT_REOPENED
                },
                targetUserId = targetReport.senderId,
                targetUserName = targetReport.senderDisplayName,
                performedBy = moderator,
                reason = "Жалоба #${targetReport.id.takeLast(4)} [${targetReport.reasonCategory}]: ${targetReport.messageText.take(40)}",
                durationLabel = null
            )
        }
    }

    fun getPendingCount(): Int {
        return _reports.value.count { it.status == ReportStatus.PENDING }
    }

    private fun saveToPrefs(list: List<ContentReport>) {
        try {
            val arr = JSONArray()
            for (r in list) {
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("messageId", r.messageId)
                    put("chatId", r.chatId)
                    put("senderId", r.senderId)
                    put("senderDisplayName", r.senderDisplayName)
                    put("senderUsername", r.senderUsername)
                    put("reporterId", r.reporterId)
                    put("reporterName", r.reporterName)
                    put("messageText", r.messageText)
                    put("reasonCategory", r.reasonCategory)
                    put("userComment", r.userComment)
                    put("timestamp", r.timestamp)
                    put("status", r.status.name)
                    put("resolvedBy", r.resolvedBy ?: "")
                    put("resolvedAt", r.resolvedAt ?: 0L)
                    put("resolutionNote", r.resolutionNote ?: "")
                }
                arr.put(obj)
            }
            sharedPrefs?.edit()?.putString(KEY_REPORTS_JSON, arr.toString())?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving reports", e)
        }
    }

    private fun loadFromPrefs(): List<ContentReport> {
        val raw = sharedPrefs?.getString(KEY_REPORTS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ContentReport>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ContentReport(
                        id = obj.getString("id"),
                        messageId = obj.optString("messageId", ""),
                        chatId = obj.optString("chatId", ""),
                        senderId = obj.getString("senderId"),
                        senderDisplayName = obj.optString("senderDisplayName", "User"),
                        senderUsername = obj.optString("senderUsername", "@user"),
                        reporterId = obj.optString("reporterId", ""),
                        reporterName = obj.optString("reporterName", "Reporter"),
                        messageText = obj.getString("messageText"),
                        reasonCategory = obj.getString("reasonCategory"),
                        userComment = obj.optString("userComment", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = try {
                            ReportStatus.valueOf(obj.getString("status"))
                        } catch (e: Exception) {
                            ReportStatus.PENDING
                        },
                        resolvedBy = obj.optString("resolvedBy", "").ifBlank { null },
                        resolvedAt = obj.optLong("resolvedAt", 0L).let { if (it > 0) it else null },
                        resolutionNote = obj.optString("resolutionNote", "").ifBlank { null }
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading reports", e)
            emptyList()
        }
    }
}
