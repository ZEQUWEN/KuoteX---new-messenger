package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class ActionType {
    ROLE_PROMOTED_ADMIN,
    ROLE_REVOKED_ADMIN,
    ROLE_PROMOTED_MOD,
    ROLE_REVOKED_MOD,
    USER_BANNED_TEMP,
    USER_BANNED_PERM,
    USER_UNBANNED,
    USER_SPAM_RESTRICTED,
    USER_SPAM_UNRESTRICTED,
    REPORT_RESOLVED_BAN,
    REPORT_RESOLVED_RESTRICT,
    REPORT_DISMISSED,
    REPORT_REOPENED
}

data class AdminActionLog(
    val id: String,
    val actionType: ActionType,
    val targetUserId: String,
    val targetUserName: String,
    val performedBy: String,
    val reason: String? = null,
    val durationLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AdminAuditLogManager
 *
 * Tracks all moderation and administration actions:
 * - Role promotions/demotions
 * - Temporary and permanent bans
 * - SpamBot restrictions
 * - Report resolutions
 *
 * Stores audit logs in local preferences with automatic timestamping.
 */
object AdminAuditLogManager {
    private const val TAG = "AdminAuditLogManager"
    private const val PREFS_NAME = "neon_admin_audit_prefs"
    private const val KEY_AUDIT_JSON = "saved_audit_logs_json"

    private var sharedPrefs: SharedPreferences? = null

    private val _logs = MutableStateFlow<List<AdminActionLog>>(emptyList())
    val logs: StateFlow<List<AdminActionLog>> = _logs.asStateFlow()

    fun init(context: Context) {
        if (sharedPrefs == null) {
            sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val cached = loadFromPrefs()
        if (cached.isNotEmpty()) {
            _logs.value = cached
        } else {
            // Seed initial historical audit logs for a rich look
            val sampleLogs = listOf(
                AdminActionLog(
                    id = "log_001",
                    actionType = ActionType.ROLE_PROMOTED_ADMIN,
                    targetUserId = "123456789",
                    targetUserName = "Neo (@neo_hacker)",
                    performedBy = "System Root",
                    reason = "Главный разработчик и супер-администратор системы",
                    timestamp = System.currentTimeMillis() - (86400000L * 25)
                ),
                AdminActionLog(
                    id = "log_002",
                    actionType = ActionType.ROLE_PROMOTED_MOD,
                    targetUserId = "987654321",
                    targetUserName = "Synth Wave (@synth_wave)",
                    performedBy = "Dev Admin (+79226692682)",
                    reason = "Назначение модератором сообщества разработчиков",
                    timestamp = System.currentTimeMillis() - (86400000L * 18)
                ),
                AdminActionLog(
                    id = "log_003",
                    actionType = ActionType.ROLE_PROMOTED_MOD,
                    targetUserId = "u1_sarah",
                    targetUserName = "Sarah Connor (@sarah_connor)",
                    performedBy = "Dev Admin (+79226692682)",
                    reason = "Назначение аудитором безопасности чатов",
                    timestamp = System.currentTimeMillis() - (86400000L * 12)
                ),
                AdminActionLog(
                    id = "log_004",
                    actionType = ActionType.USER_BANNED_TEMP,
                    targetUserId = "u3_crypto",
                    targetUserName = "Crypto Alpha (@crypto_alpha)",
                    performedBy = "Dev Admin (+79226692682)",
                    reason = "Рассылка рекламы и спам-ссылок",
                    durationLabel = "24 часа",
                    timestamp = System.currentTimeMillis() - (3600000L * 4)
                ),
                AdminActionLog(
                    id = "log_005",
                    actionType = ActionType.USER_SPAM_RESTRICTED,
                    targetUserId = "456789123",
                    targetUserName = "Cyber P. (@cyber_punk)",
                    performedBy = "Synth Wave (@synth_wave)",
                    reason = "Частые запросы в незнакомые личные чаты (Telegram SpamBot)",
                    durationLabel = "7 дней",
                    timestamp = System.currentTimeMillis() - (3600000L * 16)
                )
            )
            _logs.value = sampleLogs
            saveToPrefs(sampleLogs)
        }
    }

    fun logAction(
        actionType: ActionType,
        targetUserId: String,
        targetUserName: String,
        performedBy: String = "Dev Admin (+79226692682)",
        reason: String? = null,
        durationLabel: String? = null
    ) {
        val entry = AdminActionLog(
            id = "log_${System.currentTimeMillis() % 1000000}",
            actionType = actionType,
            targetUserId = targetUserId,
            targetUserName = targetUserName,
            performedBy = performedBy,
            reason = reason,
            durationLabel = durationLabel,
            timestamp = System.currentTimeMillis()
        )

        _logs.update { listOf(entry) + it }
        saveToPrefs(_logs.value)
        Log.i(TAG, "📜 [Audit Log] ${actionType.name} on '$targetUserName' by '$performedBy'")
    }

    private fun saveToPrefs(list: List<AdminActionLog>) {
        try {
            val arr = JSONArray()
            for (l in list) {
                val obj = JSONObject().apply {
                    put("id", l.id)
                    put("actionType", l.actionType.name)
                    put("targetUserId", l.targetUserId)
                    put("targetUserName", l.targetUserName)
                    put("performedBy", l.performedBy)
                    put("reason", l.reason ?: "")
                    put("durationLabel", l.durationLabel ?: "")
                    put("timestamp", l.timestamp)
                }
                arr.put(obj)
            }
            sharedPrefs?.edit()?.putString(KEY_AUDIT_JSON, arr.toString())?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audit logs", e)
        }
    }

    private fun loadFromPrefs(): List<AdminActionLog> {
        val raw = sharedPrefs?.getString(KEY_AUDIT_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<AdminActionLog>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AdminActionLog(
                        id = obj.getString("id"),
                        actionType = try {
                            ActionType.valueOf(obj.getString("actionType"))
                        } catch (e: Exception) {
                            ActionType.USER_BANNED_PERM
                        },
                        targetUserId = obj.getString("targetUserId"),
                        targetUserName = obj.optString("targetUserName", "User"),
                        performedBy = obj.optString("performedBy", "Admin"),
                        reason = obj.optString("reason", "").ifBlank { null },
                        durationLabel = obj.optString("durationLabel", "").ifBlank { null },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audit logs", e)
            emptyList()
        }
    }
}
