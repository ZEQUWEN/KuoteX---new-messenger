package com.example.ui.media

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.Message
import com.example.ui.components.EmojiItem
import com.example.ui.components.TelegramEmojiData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.BreakIterator
import java.util.Locale

enum class MediaPanelTab(val title: String) {
    EMOJI("Эмодзи"),
    GIF("GIF"),
    STICKERS("Стикеры")
}

enum class EmojiHintMode(val label: String) {
    ALL("Все"),
    CUSTOM_PACKS("Свои наборы"),
    NONE("Нет")
}

data class StickerItem(
    val id: String,
    val emoji: String,
    val label: String,
    val tags: List<String>,
    val bgColorHex: Long,
    val accentColorHex: Long,
    val caption: String = "",
    val isAnimated: Boolean = true
)

data class StickerPack(
    val id: String,
    val title: String,
    val coverEmoji: String,
    val stickers: List<StickerItem>,
    val isInstalled: Boolean = true,
    val isArchived: Boolean = false,
    val isTrending: Boolean = false,
    val isLocked: Boolean = false,
    val orderIndex: Int = 0,
    val shareUrl: String = "https://t.me/addstickers/$id"
)

data class CustomEmojiPack(
    val id: String,
    val title: String,
    val emojiCount: Int,
    val previewEmojis: List<String>,
    val isAdded: Boolean = false,
    val isLocked: Boolean = true
)

data class ReactionOption(
    val emoji: String,
    val title: String
)

data class StickerPayload(
    val stickerId: String,
    val packId: String,
    val emoji: String,
    val label: String,
    val caption: String,
    val bgColorHex: Long,
    val accentColorHex: Long
)

object MediaExpressionManager {

    private const val PREFS_NAME = "kuotex_media_expressions"
    private const val KEY_RECENT_EMOJIS = "recent_emojis"
    private const val KEY_RECENT_STICKERS = "recent_stickers"
    private const val KEY_PACK_STATES = "pack_states"
    private const val KEY_CUSTOM_STICKERS = "custom_stickers"
    private const val KEY_EMOJI_PACKS_ADDED = "emoji_packs_added"
    private const val KEY_EMOJI_HINT_MODE = "emoji_hint_mode"
    private const val KEY_LARGE_EMOJI = "large_emoji"
    private const val KEY_RECENT_FIRST = "recent_first"
    private const val KEY_SORT_DISABLED_NOTICE = "sort_disabled_notice"
    private const val KEY_SUGGEST_EMOJI_INSTEAD = "suggest_emoji_instead"
    private const val KEY_QUICK_REACTION = "quick_reaction"
    private const val KEY_TRENDING_DISMISSED = "trending_dismissed"

    private var prefs: SharedPreferences? = null

    private val _recentEmojis = MutableStateFlow<List<EmojiItem>>(TelegramEmojiData.recentEmojis.toList())
    val recentEmojis: StateFlow<List<EmojiItem>> = _recentEmojis.asStateFlow()

    private val _recentStickerIds = MutableStateFlow<List<String>>(emptyList())
    val recentStickerIds: StateFlow<List<String>> = _recentStickerIds.asStateFlow()

    private val _stickerPacks = MutableStateFlow(buildDefaultPacks())
    val stickerPacks: StateFlow<List<StickerPack>> = _stickerPacks.asStateFlow()

    private val _emojiPacks = MutableStateFlow(buildDefaultEmojiPacks())
    val emojiPacks: StateFlow<List<CustomEmojiPack>> = _emojiPacks.asStateFlow()

    private val _emojiHintMode = MutableStateFlow(EmojiHintMode.ALL)
    val emojiHintMode: StateFlow<EmojiHintMode> = _emojiHintMode.asStateFlow()

    private val _largeEmojiEnabled = MutableStateFlow(true)
    val largeEmojiEnabled: StateFlow<Boolean> = _largeEmojiEnabled.asStateFlow()

    private val _recentFirstEnabled = MutableStateFlow(true)
    val recentFirstEnabled: StateFlow<Boolean> = _recentFirstEnabled.asStateFlow()

    private val _recentSortDisabledNotice = MutableStateFlow(false)
    val recentSortDisabledNotice: StateFlow<Boolean> = _recentSortDisabledNotice.asStateFlow()

    private val _suggestEmojiInsteadOfStickers = MutableStateFlow(true)
    val suggestEmojiInsteadOfStickers: StateFlow<Boolean> = _suggestEmojiInsteadOfStickers.asStateFlow()

    private val _quickReaction = MutableStateFlow("❤️")
    val quickReaction: StateFlow<String> = _quickReaction.asStateFlow()

    private val _trendingBannerDismissed = MutableStateFlow(false)
    val trendingBannerDismissed: StateFlow<Boolean> = _trendingBannerDismissed.asStateFlow()

    val reactionOptions: List<ReactionOption> = listOf(
        ReactionOption("❤️", "Красное сердце"),
        ReactionOption("👍", "Палец вверх"),
        ReactionOption("👎", "Палец вниз"),
        ReactionOption("🔥", "Огонь"),
        ReactionOption("😂", "Смех до слёз"),
        ReactionOption("🥰", "Улыбка и сердечки"),
        ReactionOption("👏", "Аплодисменты"),
        ReactionOption("🎉", "Праздник"),
        ReactionOption("🤩", "Звёздный восторг"),
        ReactionOption("🦄", "Единорог"),
        ReactionOption("😘", "Воздушный поцелуй"),
        ReactionOption("💊", "Таблетка"),
        ReactionOption("🙊", "«Ничего не скажу»"),
        ReactionOption("🙉", "«Ничего не слышу»"),
        ReactionOption("😎", "Лицо в тёмных очках"),
        ReactionOption("👾", "Космический монстр"),
        ReactionOption("🤷‍♂️", "Мужчина разводит руками"),
        ReactionOption("🤷", "Человек разводит руками"),
        ReactionOption("🤷‍♀️", "Женщина разводит руками"),
        ReactionOption("😡", "Злость"),
        ReactionOption("😇", "Улыбка и нимб"),
        ReactionOption("😨", "Страх"),
        ReactionOption("🤝", "Рукопожатие"),
        ReactionOption("✍️", "Пишущая рука"),
        ReactionOption("🤗", "Объятия"),
        ReactionOption("🫡", "Под козырёк"),
        ReactionOption("🎅", "Дед Мороз"),
        ReactionOption("🎄", "Ёлочка"),
        ReactionOption("☃️", "Снеговик"),
        ReactionOption("💅", "Маникюр"),
        ReactionOption("🤪", "Глупое лицо"),
        ReactionOption("🗿", "Каменное лицо"),
        ReactionOption("🆒", "Значок «круто»"),
        ReactionOption("💔", "Разбитое сердце"),
        ReactionOption("⚡", "Молния"),
        ReactionOption("💎", "Бриллиант")
    )

    val quickMoodFilters: List<Pair<String, String>> = listOf(
        "❤️" to "любовь",
        "👍" to "класс",
        "👎" to "плохо",
        "🎉" to "праздник",
        "😊" to "радость",
        "😨" to "шок",
        "😠" to "злость",
        "😐" to "покер",
        "🤔" to "думаю",
        "😆" to "смех"
    )

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    fun resetForTests() {
        prefs?.edit()?.clear()?.apply()
        prefs = null
        _stickerPacks.value = buildDefaultPacks()
        _emojiPacks.value = buildDefaultEmojiPacks()
        _emojiHintMode.value = EmojiHintMode.ALL
        _largeEmojiEnabled.value = true
        _recentFirstEnabled.value = true
        _recentSortDisabledNotice.value = false
        _suggestEmojiInsteadOfStickers.value = true
        _quickReaction.value = "❤️"
        _trendingBannerDismissed.value = false
        _recentStickerIds.value = emptyList()
        _recentEmojis.value = TelegramEmojiData.recentEmojis.toList()
    }

    fun recordUsedEmoji(item: EmojiItem) {
        val current = _recentEmojis.value.toMutableList()
        current.removeAll { it.emoji == item.emoji }
        current.add(0, item)
        val trimmed = current.take(32)
        _recentEmojis.value = trimmed
        syncTelegramEmojiDataRecents(trimmed)
        saveRecentEmojis(trimmed)
    }

    fun syncFromTelegramEmojiPicker(list: List<EmojiItem>) {
        if (list.isEmpty()) return
        val trimmed = list.distinctBy { it.emoji }.take(32)
        if (trimmed != _recentEmojis.value) {
            _recentEmojis.value = trimmed
            saveRecentEmojis(trimmed)
        }
    }

    private fun syncTelegramEmojiDataRecents(list: List<EmojiItem>) {
        TelegramEmojiData.recentEmojis.clear()
        TelegramEmojiData.recentEmojis.addAll(list.take(28))
    }

    fun recordUsedSticker(packId: String, sticker: StickerItem) {
        val recents = _recentStickerIds.value.toMutableList()
        recents.removeAll { it == sticker.id }
        recents.add(0, sticker.id)
        _recentStickerIds.value = recents.take(24)
        prefs?.let { p ->
            p.edit().putString(KEY_RECENT_STICKERS, JSONArray(_recentStickerIds.value).toString()).apply()
        }

        if (_recentFirstEnabled.value) {
            val current = _stickerPacks.value.toMutableList()
            val targetIdx = current.indexOfFirst { it.id == packId }
            if (targetIdx > 0 && current[targetIdx].isInstalled && !current[targetIdx].isArchived) {
                val target = current.removeAt(targetIdx)
                current.add(0, target)
                _stickerPacks.value = normalizeOrderIndices(current)
                savePackStates()
            }
        }
    }

    fun getActiveStickerPacks(): List<StickerPack> {
        return _stickerPacks.value
            .filter { it.isInstalled && !it.isArchived }
            .sortedBy { it.orderIndex }
    }

    fun getArchivedStickerPacks(): List<StickerPack> {
        return _stickerPacks.value
            .filter { it.isInstalled && it.isArchived }
            .sortedBy { it.orderIndex }
    }

    fun getTrendingStickerPacks(query: String = ""): List<StickerPack> {
        val q = query.trim().lowercase()
        val trending = _stickerPacks.value.filter { it.isTrending || !it.isInstalled }
        if (q.isBlank()) return trending
        return trending.filter { pack ->
            pack.title.lowercase().contains(q) ||
                pack.stickers.any { st ->
                    st.label.lowercase().contains(q) || st.tags.any { it.lowercase().contains(q) }
                }
        }
    }

    fun totalTrendingStickersCount(): Int {
        return _stickerPacks.value
            .filter { it.isTrending || !it.isInstalled }
            .sumOf { it.stickers.size }
            .coerceAtLeast(231)
    }

    fun findStickerById(stickerId: String): Pair<StickerPack, StickerItem>? {
        _stickerPacks.value.forEach { pack ->
            val st = pack.stickers.find { it.id == stickerId }
            if (st != null) return pack to st
        }
        return null
    }

    fun getRecentStickers(): List<Pair<StickerPack, StickerItem>> {
        return _recentStickerIds.value.mapNotNull { findStickerById(it) }
    }

    fun moveActivePack(fromActiveIndex: Int, toActiveIndex: Int) {
        val active = getActiveStickerPacks().toMutableList()
        if (fromActiveIndex !in active.indices || toActiveIndex !in active.indices || fromActiveIndex == toActiveIndex) {
            return
        }
        val moved = active.removeAt(fromActiveIndex)
        active.add(toActiveIndex, moved)

        val inactive = _stickerPacks.value.filter { !it.isInstalled || it.isArchived }
        val updated = normalizeOrderIndices(active + inactive)
        _stickerPacks.value = updated

        if (_recentFirstEnabled.value) {
            _recentFirstEnabled.value = false
            _recentSortDisabledNotice.value = true
            prefs?.edit()
                ?.putBoolean(KEY_RECENT_FIRST, false)
                ?.putBoolean(KEY_SORT_DISABLED_NOTICE, true)
                ?.apply()
        }
        savePackStates()
    }

    fun reorderActivePacksByIds(orderedActiveIds: List<String>) {
        val allById = _stickerPacks.value.associateBy { it.id }
        val activeOrdered = orderedActiveIds.mapNotNull { allById[it] }
        val rest = _stickerPacks.value.filter { it.id !in orderedActiveIds.toSet() }
        _stickerPacks.value = normalizeOrderIndices(activeOrdered + rest)
        if (_recentFirstEnabled.value) {
            _recentFirstEnabled.value = false
            _recentSortDisabledNotice.value = true
            prefs?.edit()
                ?.putBoolean(KEY_RECENT_FIRST, false)
                ?.putBoolean(KEY_SORT_DISABLED_NOTICE, true)
                ?.apply()
        }
        savePackStates()
    }

    fun archiveStickerPacks(packIds: Set<String>) {
        if (packIds.isEmpty()) return
        _stickerPacks.value = _stickerPacks.value.map { pack ->
            if (pack.id in packIds) pack.copy(isArchived = true, isInstalled = true) else pack
        }
        savePackStates()
    }

    fun unarchiveStickerPack(packId: String) {
        _stickerPacks.value = normalizeOrderIndices(
            _stickerPacks.value.map { pack ->
                if (pack.id == packId) pack.copy(isArchived = false, isInstalled = true) else pack
            }
        )
        savePackStates()
    }

    fun deleteStickerPacks(packIds: Set<String>) {
        if (packIds.isEmpty()) return
        _stickerPacks.value = _stickerPacks.value.map { pack ->
            if (pack.id in packIds) pack.copy(isInstalled = false, isArchived = false) else pack
        }
        savePackStates()
    }

    fun toggleInstallTrendingPack(packId: String) {
        val updated = _stickerPacks.value.map { pack ->
            if (pack.id == packId) {
                val nowInstalled = !pack.isInstalled || pack.isArchived
                pack.copy(isInstalled = nowInstalled, isArchived = false, isLocked = false)
            } else pack
        }
        _stickerPacks.value = normalizeOrderIndices(updated)
        savePackStates()
    }

    fun toggleAddEmojiPack(packId: String) {
        val updated = _emojiPacks.value.map { pack ->
            if (pack.id == packId) pack.copy(isAdded = !pack.isAdded) else pack
        }
        _emojiPacks.value = updated
        val addedIds = updated.filter { it.isAdded }.map { it.id }
        prefs?.edit()?.putString(KEY_EMOJI_PACKS_ADDED, JSONArray(addedIds).toString())?.apply()
    }

    fun dismissTrendingBanner(dismissed: Boolean = true) {
        _trendingBannerDismissed.value = dismissed
        prefs?.edit()?.putBoolean(KEY_TRENDING_DISMISSED, dismissed)?.apply()
    }

    fun setEmojiHintMode(mode: EmojiHintMode) {
        _emojiHintMode.value = mode
        prefs?.edit()?.putString(KEY_EMOJI_HINT_MODE, mode.name)?.apply()
    }

    fun setLargeEmojiEnabled(enabled: Boolean) {
        _largeEmojiEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_LARGE_EMOJI, enabled)?.apply()
    }

    fun setRecentFirstEnabled(enabled: Boolean) {
        _recentFirstEnabled.value = enabled
        _recentSortDisabledNotice.value = !enabled
        prefs?.edit()
            ?.putBoolean(KEY_RECENT_FIRST, enabled)
            ?.putBoolean(KEY_SORT_DISABLED_NOTICE, !enabled)
            ?.apply()
    }

    fun setSuggestEmojiInsteadOfStickers(enabled: Boolean) {
        _suggestEmojiInsteadOfStickers.value = enabled
        prefs?.edit()?.putBoolean(KEY_SUGGEST_EMOJI_INSTEAD, enabled)?.apply()
    }

    fun setQuickReaction(emoji: String) {
        _quickReaction.value = emoji
        prefs?.edit()?.putString(KEY_QUICK_REACTION, emoji)?.apply()
    }

    fun createCustomSticker(
        emoji: String,
        label: String,
        caption: String = "",
        packId: String = "joker"
    ): StickerItem {
        val cleanEmoji = emoji.trim().ifBlank { "✨" }
        val cleanLabel = label.trim().ifBlank { "Мой стикер" }
        val newSticker = StickerItem(
            id = "custom_${System.currentTimeMillis()}",
            emoji = cleanEmoji,
            label = cleanLabel,
            tags = listOf(cleanLabel.lowercase(), cleanEmoji, "custom", "мой"),
            bgColorHex = 0xFF28204A,
            accentColorHex = 0xFF8B5CF6,
            caption = caption.trim(),
            isAnimated = true
        )
        val updated = _stickerPacks.value.map { pack ->
            if (pack.id == packId) {
                pack.copy(stickers = listOf(newSticker) + pack.stickers, isInstalled = true, isArchived = false)
            } else pack
        }
        _stickerPacks.value = updated
        saveCustomSticker(packId, newSticker)
        recordUsedSticker(packId, newSticker)
        return newSticker
    }

    fun searchStickers(
        query: String,
        moodEmoji: String? = null
    ): List<Pair<StickerPack, StickerItem>> {
        val q = query.trim().lowercase()
        val moodTag = quickMoodFilters.find { it.first == moodEmoji }?.second
        val packs = _stickerPacks.value.filter { !it.isArchived }
        val results = mutableListOf<Pair<StickerPack, StickerItem>>()
        for (pack in packs) {
            for (st in pack.stickers) {
                val matchesMood = moodEmoji == null ||
                    st.emoji == moodEmoji ||
                    (moodTag != null && st.tags.any { it.contains(moodTag, ignoreCase = true) })
                val matchesQuery = q.isBlank() ||
                    pack.title.lowercase().contains(q) ||
                    st.label.lowercase().contains(q) ||
                    st.emoji.contains(q) ||
                    st.caption.lowercase().contains(q) ||
                    st.tags.any { it.lowercase().contains(q) }
                if (matchesMood && matchesQuery) {
                    results.add(pack to st)
                }
            }
        }
        return results
    }

    fun getSuggestedStickersForInput(inputText: String): List<Pair<StickerPack, StickerItem>> {
        val mode = _emojiHintMode.value
        if (mode == EmojiHintMode.NONE) return emptyList()
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val lastToken = trimmed.substringAfterLast(' ').lowercase()
        if (lastToken.isBlank()) return emptyList()

        val sourcePacks = if (mode == EmojiHintMode.CUSTOM_PACKS) {
            getActiveStickerPacks()
        } else {
            _stickerPacks.value.filter { !it.isArchived }
        }

        val matches = mutableListOf<Pair<StickerPack, StickerItem>>()
        for (pack in sourcePacks) {
            for (st in pack.stickers) {
                if (lastToken.contains(st.emoji) ||
                    st.tags.any { it.lowercase() == lastToken || (lastToken.length >= 2 && it.lowercase().startsWith(lastToken)) } ||
                    (lastToken.length >= 3 && st.label.lowercase().contains(lastToken))
                ) {
                    matches.add(pack to st)
                }
            }
        }
        return matches.distinctBy { it.second.id }.take(12)
    }

    fun countPureEmojis(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 24) return 0
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(trimmed)
        var start = iterator.first()
        var end = iterator.next()
        var count = 0
        while (end != BreakIterator.DONE) {
            val grapheme = trimmed.substring(start, end)
            if (grapheme.isNotBlank()) {
                val hasLetterOrDigit = grapheme.any { Character.isLetterOrDigit(it.code) }
                if (hasLetterOrDigit || !isLikelyEmojiGrapheme(grapheme)) {
                    return 0
                }
                count++
                if (count > 3) return 0
            }
            start = end
            end = iterator.next()
        }
        return count
    }

    private fun isLikelyEmojiGrapheme(grapheme: String): Boolean {
        var i = 0
        while (i < grapheme.length) {
            val cp = grapheme.codePointAt(i)
            val charCount = Character.charCount(cp)
            val isEmojiBlock = cp in 0x1F300..0x1FAFF ||
                cp in 0x2600..0x27BF ||
                cp in 0x1F1E6..0x1F1FF ||
                cp == 0x2764 ||
                cp == 0x2B50 ||
                cp == 0x26A1 ||
                cp == 0xFE0F ||
                cp == 0x200D
            if (isEmojiBlock) return true
            i += charCount
        }
        return false
    }

    fun buildStickerDocumentData(packId: String, sticker: StickerItem): String {
        return JSONObject().apply {
            put("type", "sticker")
            put("stickerId", sticker.id)
            put("packId", packId)
            put("emoji", sticker.emoji)
            put("label", sticker.label)
            put("caption", sticker.caption)
            put("bgColorHex", sticker.bgColorHex)
            put("accentColorHex", sticker.accentColorHex)
        }.toString()
    }

    fun parseStickerFromMessage(message: Message): StickerPayload? {
        if (message.mediaType != "sticker" && message.documentData?.contains("\"type\":\"sticker\"") != true) {
            return null
        }
        val doc = message.documentData
        if (!doc.isNullOrBlank()) {
            try {
                val json = JSONObject(doc)
                if (json.optString("type") == "sticker" || message.mediaType == "sticker") {
                    return StickerPayload(
                        stickerId = json.optString("stickerId", message.mediaPath ?: message.id),
                        packId = json.optString("packId", "joker"),
                        emoji = json.optString("emoji", message.text.ifBlank { "🎭" }),
                        label = json.optString("label", "Стикер"),
                        caption = json.optString("caption", ""),
                        bgColorHex = json.optLong("bgColorHex", 0xFF28204A),
                        accentColorHex = json.optLong("accentColorHex", 0xFF8B5CF6)
                    )
                }
            } catch (_: Exception) {}
        }
        val found = message.mediaPath?.let { findStickerById(it) }
        return if (found != null) {
            StickerPayload(
                stickerId = found.second.id,
                packId = found.first.id,
                emoji = found.second.emoji,
                label = found.second.label,
                caption = found.second.caption,
                bgColorHex = found.second.bgColorHex,
                accentColorHex = found.second.accentColorHex
            )
        } else {
            StickerPayload(
                stickerId = message.id,
                packId = "joker",
                emoji = message.text.take(4).ifBlank { "🎭" },
                label = "Стикер",
                caption = "",
                bgColorHex = 0xFF28204A,
                accentColorHex = 0xFF8B5CF6
            )
        }
    }

    private fun normalizeOrderIndices(list: List<StickerPack>): List<StickerPack> {
        return list.mapIndexed { index, pack -> pack.copy(orderIndex = index) }
    }

    private fun saveRecentEmojis(list: List<EmojiItem>) {
        val p = prefs ?: return
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("emoji", item.emoji)
                    put("name", item.name)
                    put("isCustom", item.isCustom)
                    put("packName", item.packName ?: "")
                }
            )
        }
        p.edit().putString(KEY_RECENT_EMOJIS, arr.toString()).apply()
    }

    private fun savePackStates() {
        val p = prefs ?: return
        val arr = JSONArray()
        _stickerPacks.value.forEach { pack ->
            arr.put(
                JSONObject().apply {
                    put("id", pack.id)
                    put("installed", pack.isInstalled)
                    put("archived", pack.isArchived)
                    put("locked", pack.isLocked)
                    put("order", pack.orderIndex)
                }
            )
        }
        p.edit().putString(KEY_PACK_STATES, arr.toString()).apply()
    }

    private fun saveCustomSticker(packId: String, sticker: StickerItem) {
        val p = prefs ?: return
        val existingRaw = p.getString(KEY_CUSTOM_STICKERS, null)
        val arr = try {
            if (!existingRaw.isNullOrBlank()) JSONArray(existingRaw) else JSONArray()
        } catch (_: Exception) {
            JSONArray()
        }
        arr.put(
            JSONObject().apply {
                put("packId", packId)
                put("id", sticker.id)
                put("emoji", sticker.emoji)
                put("label", sticker.label)
                put("caption", sticker.caption)
                put("bgColorHex", sticker.bgColorHex)
                put("accentColorHex", sticker.accentColorHex)
            }
        )
        p.edit().putString(KEY_CUSTOM_STICKERS, arr.toString()).apply()
    }

    private fun loadFromPrefs() {
        val p = prefs ?: return

        _emojiHintMode.value = p.getString(KEY_EMOJI_HINT_MODE, EmojiHintMode.ALL.name)?.let {
            runCatching { EmojiHintMode.valueOf(it) }.getOrNull()
        } ?: EmojiHintMode.ALL
        _largeEmojiEnabled.value = p.getBoolean(KEY_LARGE_EMOJI, true)
        _recentFirstEnabled.value = p.getBoolean(KEY_RECENT_FIRST, true)
        _recentSortDisabledNotice.value = p.getBoolean(KEY_SORT_DISABLED_NOTICE, false)
        _suggestEmojiInsteadOfStickers.value = p.getBoolean(KEY_SUGGEST_EMOJI_INSTEAD, true)
        _quickReaction.value = p.getString(KEY_QUICK_REACTION, "❤️") ?: "❤️"
        _trendingBannerDismissed.value = p.getBoolean(KEY_TRENDING_DISMISSED, false)

        p.getString(KEY_RECENT_EMOJIS, null)?.let { raw ->
            try {
                val arr = JSONArray(raw)
                val loaded = mutableListOf<EmojiItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    loaded.add(
                        EmojiItem(
                            emoji = obj.getString("emoji"),
                            name = obj.optString("name", ""),
                            isCustom = obj.optBoolean("isCustom", false),
                            packName = obj.optString("packName", "").ifBlank { null }
                        )
                    )
                }
                if (loaded.isNotEmpty()) {
                    _recentEmojis.value = loaded
                    syncTelegramEmojiDataRecents(loaded)
                }
            } catch (_: Exception) {}
        }

        p.getString(KEY_RECENT_STICKERS, null)?.let { raw ->
            try {
                val arr = JSONArray(raw)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                _recentStickerIds.value = list
            } catch (_: Exception) {}
        }

        p.getString(KEY_EMOJI_PACKS_ADDED, null)?.let { raw ->
            try {
                val arr = JSONArray(raw)
                val addedSet = buildSet {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
                _emojiPacks.value = _emojiPacks.value.map {
                    it.copy(isAdded = it.id in addedSet)
                }
            } catch (_: Exception) {}
        }

        var packs = buildDefaultPacks()

        p.getString(KEY_CUSTOM_STICKERS, null)?.let { raw ->
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val targetPackId = obj.optString("packId", "joker")
                    val sticker = StickerItem(
                        id = obj.getString("id"),
                        emoji = obj.getString("emoji"),
                        label = obj.optString("label", "Мой стикер"),
                        tags = listOf(obj.optString("label", "").lowercase(), obj.getString("emoji"), "custom"),
                        bgColorHex = obj.optLong("bgColorHex", 0xFF28204A),
                        accentColorHex = obj.optLong("accentColorHex", 0xFF8B5CF6),
                        caption = obj.optString("caption", "")
                    )
                    packs = packs.map { pack ->
                        if (pack.id == targetPackId) pack.copy(stickers = listOf(sticker) + pack.stickers) else pack
                    }
                }
            } catch (_: Exception) {}
        }

        p.getString(KEY_PACK_STATES, null)?.let { raw ->
            try {
                val arr = JSONArray(raw)
                val stateById = mutableMapOf<String, Triple<Boolean, Boolean, Int>>()
                val lockedById = mutableMapOf<String, Boolean>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getString("id")
                    stateById[id] = Triple(
                        obj.optBoolean("installed", true),
                        obj.optBoolean("archived", false),
                        obj.optInt("order", i)
                    )
                    lockedById[id] = obj.optBoolean("locked", false)
                }
                packs = packs.map { pack ->
                    val saved = stateById[pack.id]
                    if (saved != null) {
                        pack.copy(
                            isInstalled = saved.first,
                            isArchived = saved.second,
                            orderIndex = saved.third,
                            isLocked = lockedById[pack.id] ?: pack.isLocked
                        )
                    } else pack
                }.sortedBy { it.orderIndex }
            } catch (_: Exception) {}
        }

        _stickerPacks.value = normalizeOrderIndices(packs)
    }

    private fun buildDefaultEmojiPacks(): List<CustomEmojiPack> = listOf(
        CustomEmojiPack(
            id = "duck_emoji",
            title = "Duck Emoji",
            emojiCount = 53,
            previewEmojis = listOf("🐥", "😡", "😎", "🥳", "😭"),
            isAdded = false,
            isLocked = true
        ),
        CustomEmojiPack(
            id = "crayons_emoji",
            title = "Crayons Emoji",
            emojiCount = 37,
            previewEmojis = listOf("✏️", "🖍️", "🎨", "🌈", "⭐"),
            isAdded = false,
            isLocked = true
        ),
        CustomEmojiPack(
            id = "birthday_emoji",
            title = "Birthday Collection",
            emojiCount = 29,
            previewEmojis = listOf("🎂", "🎈", "🎁", "🎉", "🥂"),
            isAdded = false,
            isLocked = true
        ),
        CustomEmojiPack(
            id = "cursed_emoji",
            title = "Cursed Emoji",
            emojiCount = 42,
            previewEmojis = listOf("😵‍💫", "🫠", "🤯", "👻", "🤡"),
            isAdded = false,
            isLocked = true
        ),
        CustomEmojiPack(
            id = "emoticon_emoji",
            title = "Emoticon Emoji",
            emojiCount = 36,
            previewEmojis = listOf("😀", "🥰", "🤩", "😇", "🤗"),
            isAdded = false,
            isLocked = true
        )
    )

    private fun buildDefaultPacks(): List<StickerPack> {
        val jokerStickers = listOf(
            StickerItem("jk_1", "🤡", "Джокер смеётся", listOf("🤡", "смех", "джокер", "хаха", "joker", "😆"), 0xFF1F3A2C, 0xFF34D399, "ХА-ХА!"),
            StickerItem("jk_2", "🃏", "Джокер карта", listOf("🃏", "карта", "игра", "joker", "хитро"), 0xFF331C4A, 0xFFA855F7, "Твой ход"),
            StickerItem("jk_3", "😈", "Хитрый план", listOf("😈", "зло", "план", "ухмылка", "😠"), 0xFF421C2B, 0xFFF43F5E, "Почему так серьёзно?"),
            StickerItem("jk_4", "🤫", "Секрет", listOf("🤫", "тихо", "секрет", "тсс"), 0xFF1C2C42, 0xFF38BDF8, "Тсс..."),
            StickerItem("jk_5", "😎", "Покерфейс", listOf("😎", "круто", "очки", "покер", "👍"), 0xFF28204A, 0xFF818CF8, "По красоте"),
            StickerItem("jk_6", "😏", "Сарказм", listOf("😏", "сарказм", "угу", "думаю", "🤔"), 0xFF2D2540, 0xFFC084FC, "Ну-ну"),
            StickerItem("jk_7", "👏", "Браво", listOf("👏", "аплодисменты", "браво", "праздник", "🎉"), 0xFF243B32, 0xFF10B981, "Браво!"),
            StickerItem("jk_8", "🤯", "Взрыв мозга", listOf("🤯", "шок", "вау", "😨"), 0xFF452319, 0xFFFB923C, "Что?!"),
            StickerItem("jk_9", "💣", "Бум", listOf("💣", "бомба", "огонь", "🔥", "взрыв"), 0xFF441B1E, 0xFFEF4444, "БАМ!"),
            StickerItem("jk_10", "🎭", "Маска", listOf("🎭", "театр", "роль", "маска"), 0xFF1E2B48, 0xFF60A5FA),
            StickerItem("jk_11", "💵", "Куш", listOf("💵", "деньги", "кеш", "кэш", "богатство"), 0xFF1A3A2A, 0xFF4ADE80),
            StickerItem("jk_12", "🥂", "За нас", listOf("🥂", "тост", "праздник", "🎉"), 0xFF3E3119, 0xFFFACC15),
            StickerItem("jk_13", "🔥", "Жарко", listOf("🔥", "огонь", "топ", "хайп"), 0xFF4B2118, 0xFFF97316),
            StickerItem("jk_14", "👀", "Слежу", listOf("👀", "глаза", "смотрю", "думаю"), 0xFF222E44, 0xFF38BDF8),
            StickerItem("jk_15", "👋", "Ухожу красиво", listOf("👋", "пока", "привет"), 0xFF2A2444, 0xFFA78BFA),
            StickerItem("jk_16", "💯", "Согласен на 100", listOf("💯", "топ", "да", "👍"), 0xFF3E1E38, 0xFFEC4899)
        )

        val deathNoteStickers = listOf(
            StickerItem("dn_1", "📓", "Тетрадь смерти", listOf("📓", "тетрадь", "death note", "запись"), 0xFF1A1F2C, 0xFF94A3B8, "Death Note"),
            StickerItem("dn_2", "🍎", "Яблоко Рюка", listOf("🍎", "яблоко", "рюк", "вкусно", "❤️"), 0xFF471B22, 0xFFEF4444, "Яблоки!"),
            StickerItem("dn_3", "✍️", "Записываю", listOf("✍️", "пишу", "ручка", "список"), 0xFF1E293B, 0xFF60A5FA, "Записал..."),
            StickerItem("dn_4", "🧐", "Детектив L", listOf("🧐", "l", "думаю", "детектив", "🤔"), 0xFF1B2436, 0xFF38BDF8, "Интересно"),
            StickerItem("dn_5", "🍰", "Тортик для L", listOf("🍰", "торт", "сладкое", "радость", "😊"), 0xFF3B243B, 0xFFF472B6),
            StickerItem("dn_6", "😨", "Попался", listOf("😨", "шок", "испуг", "ужас"), 0xFF2A2438, 0xFFA855F7),
            StickerItem("dn_7", "😈", "План сработал", listOf("😈", "смех", "план", "класс"), 0xFF3A1A28, 0xFFF43F5E),
            StickerItem("dn_8", "🕯️", "В темноте", listOf("🕯️", "ночь", "свеча", "тайна"), 0xFF2D251C, 0xFFFBBF24),
            StickerItem("dn_9", "⚖️", "Правосудие", listOf("⚖️", "весы", "закон", "правда"), 0xFF1E2D3A, 0xFF34D399),
            StickerItem("dn_10", "👻", "Синигами рядом", listOf("👻", "призрак", "рюк", "шок"), 0xFF262335, 0xFFC084FC),
            StickerItem("dn_11", "👍", "Верно", listOf("👍", "да", "верно", "класс"), 0xFF1C3532, 0xFF2DD4BF),
            StickerItem("dn_12", "👎", "Ошибаешься", listOf("👎", "нет", "ошибка", "плохо"), 0xFF3D1F28, 0xFFFB7185)
        )

        val speedyElfStickers = listOf(
            StickerItem("se_1", "🧝", "Скоростной эльф", listOf("🧝", "эльф", "привет", "👋"), 0xFF471E22, 0xFFFB7185),
            StickerItem("se_2", "🎄", "Праздник к нам", listOf("🎄", "ёлка", "праздник", "🎉"), 0xFF1B3A2C, 0xFF34D399),
            StickerItem("se_3", "🎁", "Подарочек", listOf("🎁", "подарок", "сюрприз", "❤️"), 0xFF3E1E3C, 0xFFF472B6),
            StickerItem("se_4", "⚡", "Уже лечу!", listOf("⚡", "быстро", "бегу", "скорость"), 0xFF3B3116, 0xFFFACC15),
            StickerItem("se_5", "🛷", "Доставка", listOf("🛷", "еду", "доставка", "скоро"), 0xFF1D3247, 0xFF38BDF8),
            StickerItem("se_6", "😂", "Ржака", listOf("😂", "смех", "лол", "😆"), 0xFF3D261C, 0xFFFB923C),
            StickerItem("se_7", "❤️", "С любовью", listOf("❤️", "любовь", "сердце", "мило"), 0xFF481C2B, 0xFFF43F5E),
            StickerItem("se_8", "🥶", "Холодно!", listOf("🥶", "мороз", "зима", "холод"), 0xFF183148, 0xFF60A5FA),
            StickerItem("se_9", "☕", "Горячий какао", listOf("☕", "кофе", "чай", "уют"), 0xFF36271F, 0xFFD97706),
            StickerItem("se_10", "🥳", "Ура!", listOf("🥳", "ура", "праздник", "веселье", "🎉"), 0xFF2B2246, 0xFFA78BFA)
        )

        val huskyStickers = listOf(
            StickerItem("sh_1", "🐺", "Вуф!", listOf("🐺", "хаски", "пёс", "привет", "👋"), 0xFF223046, 0xFF60A5FA),
            StickerItem("sh_2", "👅", "Вкусняшка", listOf("👅", "мило", "радость", "😊"), 0xFF3B2434, 0xFFF472B6),
            StickerItem("sh_3", "🥺", "Дай вкусняшку", listOf("🥺", "прошу", "глазки", "мило"), 0xFF292544, 0xFFA78BFA),
            StickerItem("sh_4", "😴", "Сплю на диване", listOf("😴", "сон", "спать", "спокойной ночи"), 0xFF1C273A, 0xFF818CF8),
            StickerItem("sh_5", "😡", "Р-р-р!", listOf("😡", "злюсь", "злость", "😠"), 0xFF471C22, 0xFFEF4444),
            StickerItem("sh_6", "🏃", "Гулять!", listOf("🏃", "бег", "прогулка", "радость"), 0xFF1D392E, 0xFF34D399),
            StickerItem("sh_7", "❄️", "Люблю снег", listOf("❄️", "снег", "зима", "хаски"), 0xFF1A3548, 0xFF38BDF8),
            StickerItem("sh_8", "👍", "Хороший мальчик", listOf("👍", "класс", "молодец", "супер"), 0xFF263B2C, 0xFF4ADE80)
        )

        val cherryStickers = listOf(
            StickerItem("hc_1", "🍒", "Вишенка", listOf("🍒", "вишня", "привет", "мило"), 0xFF481A29, 0xFFFB7185),
            StickerItem("hc_2", "😘", "Чмок!", listOf("😘", "поцелуй", "любовь", "❤️"), 0xFF4A1B33, 0xFFF472B6),
            StickerItem("hc_3", "🔥", "Горячо!", listOf("🔥", "огонь", "жарко", "вау"), 0xFF472017, 0xFFF97316),
            StickerItem("hc_4", "💃", "Танцы!", listOf("💃", "танцы", "вечеринка", "🎉"), 0xFF391D48, 0xFFC084FC),
            StickerItem("hc_5", "😎", "Стиль", listOf("😎", "очки", "круто", "стиль"), 0xFF1D2C44, 0xFF38BDF8),
            StickerItem("hc_6", "🥳", "Вечеринка", listOf("🥳", "туса", "праздник", "🎉"), 0xFF3A281A, 0xFFFACC15),
            StickerItem("hc_7", "🥂", "Чин-чин", listOf("🥂", "шампанское", "праздник"), 0xFF342E1B, 0xFFFBBF24),
            StickerItem("hc_8", "❤️", "Обожаю", listOf("❤️", "любовь", "обожаю"), 0xFF4B1927, 0xFFEF4444)
        )

        val duckStickers = listOf(
            StickerItem("dk_1", "🦆", "Кря!", listOf("🦆", "утка", "кря", "привет"), 0xFF2A3B23, 0xFFFACC15),
            StickerItem("dk_2", "🐥", "Цыпа", listOf("🐥", "мило", "радость", "😊"), 0xFF3B3219, 0xFFFDE047),
            StickerItem("dk_3", "☕", "Утренний кофе", listOf("☕", "кофе", "утро"), 0xFF33261E, 0xFFF59E0B),
            StickerItem("dk_4", "😎", "Деловая утка", listOf("😎", "бизнес", "круто", "👍"), 0xFF1D2E42, 0xFF38BDF8),
            StickerItem("dk_5", "💻", "Работаю", listOf("💻", "код", "работа", "ноут"), 0xFF22253E, 0xFF818CF8)
        )

        val bearStickers = listOf(
            StickerItem("br_1", "🧸", "Мишка обнимашка", listOf("🧸", "мишка", "объятия", "❤️"), 0xFF3D2A20, 0xFFF59E0B),
            StickerItem("br_2", "🐻", "Привет!", listOf("🐻", "медведь", "привет", "👋"), 0xFF36241B, 0xFFD97706),
            StickerItem("br_3", "🍯", "Сладкоежка", listOf("🍯", "мёд", "вкусно", "😊"), 0xFF423117, 0xFFFACC15),
            StickerItem("br_4", "🥰", "Люблю тебя", listOf("🥰", "любовь", "сердца", "❤️"), 0xFF471F2E, 0xFFF472B6),
            StickerItem("br_5", "🛌", "Спокойной ночи", listOf("🛌", "сон", "кровать"), 0xFF212842, 0xFF818CF8)
        )

        val crocoStickers = listOf(
            StickerItem("cr_1", "🐊", "Кроко", listOf("🐊", "крокодил", "привет", "👋"), 0xFF1B3A29, 0xFF34D399),
            StickerItem("cr_2", "🕶️", "На чилле", listOf("🕶️", "отдых", "круто", "😎"), 0xFF19323C, 0xFF2DD4BF),
            StickerItem("cr_3", "💪", "Мощь", listOf("💪", "сила", "спорт", "👍"), 0xFF203A24, 0xFF4ADE80),
            StickerItem("cr_4", "🤣", "До слёз", listOf("🤣", "смех", "хаха", "😆"), 0xFF2D391D, 0xFFA3E635),
            StickerItem("cr_5", "🍕", "Перекус", listOf("🍕", "пицца", "еда"), 0xFF3D2919, 0xFFFB923C)
        )

        return listOf(
            StickerPack("joker", "Joker", "🃏", jokerStickers, isInstalled = true, isTrending = false, orderIndex = 0),
            StickerPack("death_note", "Death Note", "📓", deathNoteStickers, isInstalled = true, isTrending = false, orderIndex = 1),
            StickerPack("speedy_elf", "Speedy Elf", "🧝", speedyElfStickers, isInstalled = true, isTrending = false, orderIndex = 2),
            StickerPack("siberian_husky", "Siberian Husky", "🐺", huskyStickers, isInstalled = true, isTrending = false, orderIndex = 3),
            StickerPack("hot_cherry", "Hot Cherry", "🍒", cherryStickers, isInstalled = true, isTrending = false, orderIndex = 4),
            StickerPack("duck", "Duck", "🦆", duckStickers, isInstalled = false, isTrending = true, isLocked = false, orderIndex = 5),
            StickerPack("buddy_bear", "Buddy Bear", "🧸", bearStickers, isInstalled = false, isTrending = true, isLocked = true, orderIndex = 6),
            StickerPack("croco", "Croco", "🐊", crocoStickers, isInstalled = false, isTrending = true, isLocked = false, orderIndex = 7)
        )
    }
}
