package com.example.ui.channel

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class SuggestedPostStatus(val raw: String, val labelRu: String) {
    PENDING("PENDING", "На рассмотрении"),
    ACCEPTED("ACCEPTED", "Опубликовано"),
    SCHEDULED("SCHEDULED", "Запланировано"),
    DECLINED("DECLINED", "Отклонено");

    companion object {
        fun fromRaw(raw: String?): SuggestedPostStatus {
            return entries.find { it.raw.equals(raw, ignoreCase = true) } ?: PENDING
        }
    }
}

data class SuggestedPostPayload(
    val proposalId: String = "sp_${UUID.randomUUID().toString().replace("-", "").take(12)}",
    val channelId: String,
    val subscriberId: String,
    val subscriberName: String,
    val text: String,
    val mediaPath: String? = null,
    val mediaType: String? = null,
    val priceStars: Long = 0L,
    val approxUsdText: String = formatApproxUsd(priceStars),
    val scheduledTimestamp: Long? = null,
    val scheduledLabel: String = "Любое",
    val status: SuggestedPostStatus = SuggestedPostStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("type", TYPE_SUGGESTED_POST)
            put("proposalId", proposalId)
            put("channelId", channelId)
            put("subscriberId", subscriberId)
            put("subscriberName", subscriberName)
            put("text", text)
            if (mediaPath != null) put("mediaPath", mediaPath)
            if (mediaType != null) put("mediaType", mediaType)
            put("priceStars", priceStars)
            put("approxUsdText", approxUsdText)
            if (scheduledTimestamp != null) put("scheduledTimestamp", scheduledTimestamp)
            put("scheduledLabel", scheduledLabel)
            put("status", status.raw)
            put("createdAt", createdAt)
        }.toString()
    }

    companion object {
        const val TYPE_SUGGESTED_POST = "suggested_post"
        private const val USD_PER_STAR = 0.015

        fun formatApproxUsd(stars: Long): String {
            val safeStars = stars.coerceAtLeast(0L)
            val usd = safeStars * USD_PER_STAR
            val formatted = String.format(Locale.US, "%.2f", usd).replace('.', ',')
            return "~$formatted $"
        }

        fun isSuggestedPostJson(documentData: String?): Boolean {
            if (documentData.isNullOrBlank()) return false
            return documentData.contains("\"type\":\"$TYPE_SUGGESTED_POST\"")
        }

        fun fromJson(raw: String?): SuggestedPostPayload? {
            if (raw.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(raw)
                if (obj.optString("type") != TYPE_SUGGESTED_POST) return null
                val price = obj.optLong("priceStars", 0L)
                SuggestedPostPayload(
                    proposalId = obj.optString("proposalId", "sp_unknown"),
                    channelId = obj.optString("channelId", ""),
                    subscriberId = obj.optString("subscriberId", ""),
                    subscriberName = obj.optString("subscriberName", "Подписчик"),
                    text = obj.optString("text", ""),
                    mediaPath = obj.optString("mediaPath", "").ifBlank { null },
                    mediaType = obj.optString("mediaType", "").ifBlank { null },
                    priceStars = price,
                    approxUsdText = obj.optString("approxUsdText", formatApproxUsd(price)),
                    scheduledTimestamp = if (obj.has("scheduledTimestamp") && !obj.isNull("scheduledTimestamp")) {
                        obj.optLong("scheduledTimestamp")
                    } else null,
                    scheduledLabel = obj.optString("scheduledLabel", "Любое"),
                    status = SuggestedPostStatus.fromRaw(obj.optString("status")),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class ChannelDmThreadSummary(
    val threadId: String,
    val channelId: String,
    val subscriberId: String,
    val subscriberName: String,
    val subscriberAvatar: String? = null,
    val lastMessagePreview: String = "",
    val pendingSuggestionsCount: Int = 0,
    val unreadCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class StarsBundlePackage(
    val id: String,
    val stars: Long,
    val priceUsd: String,
    val bonusLabel: String? = null,
    val isPopular: Boolean = false
)

data class SuggestedPostDateOption(
    val dayOffset: Int,
    val wheelLabel: String,
    val buttonDateLabel: String,
    val year: Int,
    val month: Int,
    val dayOfMonth: Int
)

object ChannelDirectMessageManager {

    private const val PREFS_NAME = "kuotex_channel_dm_prefs"
    private const val KEY_THREADS_JSON = "dm_threads_json"
    private const val KEY_MUTED_THREADS = "dm_muted_threads"

    private var prefs: SharedPreferences? = null

    private val _threadsByChannel = MutableStateFlow<Map<String, List<ChannelDmThreadSummary>>>(emptyMap())
    val threadsByChannel: StateFlow<Map<String, List<ChannelDmThreadSummary>>> = _threadsByChannel.asStateFlow()

    private val _mutedThreadIds = MutableStateFlow<Set<String>>(emptySet())
    val mutedThreadIds: StateFlow<Set<String>> = _mutedThreadIds.asStateFlow()

    private val russianWeekdaysShort = mapOf(
        Calendar.MONDAY to "пн",
        Calendar.TUESDAY to "вт",
        Calendar.WEDNESDAY to "ср",
        Calendar.THURSDAY to "чт",
        Calendar.FRIDAY to "пт",
        Calendar.SATURDAY to "сб",
        Calendar.SUNDAY to "вс"
    )

    private val russianMonthsShort = listOf(
        "янв.", "февр.", "мар.", "апр.", "мая", "июн.",
        "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."
    )

    val defaultStarsPackages = listOf(
        StarsBundlePackage(id = "stars_50", stars = 50L, priceUsd = "$0.99"),
        StarsBundlePackage(id = "stars_100", stars = 100L, priceUsd = "$1.89"),
        StarsBundlePackage(id = "stars_250", stars = 250L, priceUsd = "$4.49", bonusLabel = "Популярно", isPopular = true),
        StarsBundlePackage(id = "stars_500", stars = 500L, priceUsd = "$8.49", bonusLabel = "+5% выгода"),
        StarsBundlePackage(id = "stars_1000", stars = 1000L, priceUsd = "$15.99", bonusLabel = "+10% выгода"),
        StarsBundlePackage(id = "stars_2500", stars = 2500L, priceUsd = "$37.99", bonusLabel = "Максимум")
    )

    fun buildThreadId(channelId: String, subscriberId: String): String {
        val cleanSub = subscriberId.ifBlank { "me" }
        return "channel_dm_${channelId}_$cleanSub"
    }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPersistedState()
    }

    fun recordThreadActivity(
        channelId: String,
        subscriberId: String,
        subscriberName: String,
        subscriberAvatar: String?,
        lastPreview: String,
        pendingDelta: Int = 0
    ) {
        val threadId = buildThreadId(channelId, subscriberId)
        val now = System.currentTimeMillis()
        _threadsByChannel.update { map ->
            val currentList = map[channelId].orEmpty().toMutableList()
            val idx = currentList.indexOfFirst { it.threadId == threadId }
            val updatedItem = if (idx >= 0) {
                val existing = currentList[idx]
                existing.copy(
                    subscriberName = subscriberName.ifBlank { existing.subscriberName },
                    subscriberAvatar = subscriberAvatar ?: existing.subscriberAvatar,
                    lastMessagePreview = lastPreview.ifBlank { existing.lastMessagePreview },
                    pendingSuggestionsCount = (existing.pendingSuggestionsCount + pendingDelta).coerceAtLeast(0),
                    updatedAt = now
                )
            } else {
                ChannelDmThreadSummary(
                    threadId = threadId,
                    channelId = channelId,
                    subscriberId = subscriberId,
                    subscriberName = subscriberName.ifBlank { "Подписчик" },
                    subscriberAvatar = subscriberAvatar,
                    lastMessagePreview = lastPreview,
                    pendingSuggestionsCount = pendingDelta.coerceAtLeast(0),
                    updatedAt = now
                )
            }
            if (idx >= 0) {
                currentList.removeAt(idx)
            }
            currentList.add(0, updatedItem)
            map + (channelId to currentList)
        }
        persistThreads()
    }

    fun adjustPendingCount(channelId: String, threadId: String, delta: Int) {
        _threadsByChannel.update { map ->
            val list = map[channelId] ?: return@update map
            val updated = list.map { item ->
                if (item.threadId == threadId) {
                    item.copy(pendingSuggestionsCount = (item.pendingSuggestionsCount + delta).coerceAtLeast(0))
                } else item
            }
            map + (channelId to updated)
        }
        persistThreads()
    }

    fun clearThreadSummary(channelId: String, threadId: String) {
        _threadsByChannel.update { map ->
            val list = map[channelId].orEmpty().map { item ->
                if (item.threadId == threadId) {
                    item.copy(lastMessagePreview = "История очищена", pendingSuggestionsCount = 0)
                } else item
            }
            map + (channelId to list)
        }
        persistThreads()
    }

    fun isThreadMuted(threadId: String): Boolean = _mutedThreadIds.value.contains(threadId)

    fun toggleMuteThread(threadId: String): Boolean {
        var nowMuted = false
        _mutedThreadIds.update { set ->
            if (set.contains(threadId)) {
                nowMuted = false
                set - threadId
            } else {
                nowMuted = true
                set + threadId
            }
        }
        prefs?.edit()?.putStringSet(KEY_MUTED_THREADS, _mutedThreadIds.value)?.apply()
        return nowMuted
    }

    fun generateUpcomingDateOptions(daysCount: Int = 30): List<SuggestedPostDateOption> {
        val list = mutableListOf<SuggestedPostDateOption>()
        val cal = Calendar.getInstance()
        for (offset in 0 until daysCount) {
            val itemCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dayOfWeek = russianWeekdaysShort[itemCal.get(Calendar.DAY_OF_WEEK)] ?: ""
            val dayNum = itemCal.get(Calendar.DAY_OF_MONTH)
            val monthShort = russianMonthsShort.getOrElse(itemCal.get(Calendar.MONTH)) { "нояб." }
            val wheelLabel = if (offset == 0) {
                "Сегодня"
            } else {
                "$dayOfWeek, $dayNum $monthShort"
            }
            val buttonLabel = if (offset == 0) {
                "Сегодня"
            } else {
                "$dayNum $monthShort"
            }
            list.add(
                SuggestedPostDateOption(
                    dayOffset = offset,
                    wheelLabel = wheelLabel,
                    buttonDateLabel = buttonLabel,
                    year = itemCal.get(Calendar.YEAR),
                    month = itemCal.get(Calendar.MONTH),
                    dayOfMonth = dayNum
                )
            )
        }
        return list
    }

    fun formatScheduledButtonLabel(dateOption: SuggestedPostDateOption, hour: Int, minute: Int): String {
        val hhmm = String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        return if (dateOption.dayOffset == 0) {
            "Сегодня в $hhmm"
        } else {
            "${dateOption.buttonDateLabel} $hhmm"
        }
    }

    fun computeTimestamp(dateOption: SuggestedPostDateOption, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, dateOption.year)
            set(Calendar.MONTH, dateOption.month)
            set(Calendar.DAY_OF_MONTH, dateOption.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun loadPersistedState() {
        val activePrefs = prefs ?: return
        _mutedThreadIds.value = activePrefs.getStringSet(KEY_MUTED_THREADS, emptySet()).orEmpty()
        val raw = activePrefs.getString(KEY_THREADS_JSON, null) ?: return
        try {
            val root = JSONObject(raw)
            val loaded = mutableMapOf<String, List<ChannelDmThreadSummary>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val channelId = keys.next()
                val arr = root.optJSONArray(channelId) ?: continue
                val items = mutableListOf<ChannelDmThreadSummary>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    items.add(
                        ChannelDmThreadSummary(
                            threadId = o.optString("threadId"),
                            channelId = channelId,
                            subscriberId = o.optString("subscriberId"),
                            subscriberName = o.optString("subscriberName", "Подписчик"),
                            subscriberAvatar = o.optString("subscriberAvatar", "").ifBlank { null },
                            lastMessagePreview = o.optString("lastMessagePreview", ""),
                            pendingSuggestionsCount = o.optInt("pendingSuggestionsCount", 0),
                            unreadCount = o.optInt("unreadCount", 0),
                            updatedAt = o.optLong("updatedAt", 0L)
                        )
                    )
                }
                loaded[channelId] = items.sortedByDescending { it.updatedAt }
            }
            _threadsByChannel.value = loaded
        } catch (_: Exception) {
        }
    }

    private fun persistThreads() {
        val activePrefs = prefs ?: return
        try {
            val root = JSONObject()
            _threadsByChannel.value.forEach { (channelId, list) ->
                val arr = JSONArray()
                list.forEach { item ->
                    arr.put(
                        JSONObject().apply {
                            put("threadId", item.threadId)
                            put("subscriberId", item.subscriberId)
                            put("subscriberName", item.subscriberName)
                            put("subscriberAvatar", item.subscriberAvatar ?: "")
                            put("lastMessagePreview", item.lastMessagePreview)
                            put("pendingSuggestionsCount", item.pendingSuggestionsCount)
                            put("unreadCount", item.unreadCount)
                            put("updatedAt", item.updatedAt)
                        }
                    )
                }
                root.put(channelId, arr)
            }
            activePrefs.edit().putString(KEY_THREADS_JSON, root.toString()).apply()
        } catch (_: Exception) {
        }
    }
}
