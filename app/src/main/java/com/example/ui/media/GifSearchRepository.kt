package com.example.ui.media

import com.example.BuildConfig
import com.example.ui.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GifItem(
    val id: String,
    val title: String,
    val previewEmoji: String,
    val gifUrl: String,
    val thumbUrl: String = gifUrl,
    val tags: List<String>,
    val moodEmoji: String,
    val aspectRatio: Float = 1.35f,
    val accentColorHex: Long = 0xFF2AABEE,
    val isFromNetwork: Boolean = false
)

object GifSearchRepository {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val _recentGifs = MutableStateFlow<List<GifItem>>(emptyList())
    val recentGifs: StateFlow<List<GifItem>> = _recentGifs.asStateFlow()

    val curatedCatalog: List<GifItem> = listOf(
        GifItem(
            id = "gif_dance_party",
            title = "Танцы и вечеринка",
            previewEmoji = "🎉",
            gifUrl = "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif",
            tags = listOf("праздник", "танцы", "вечеринка", "ура", "party", "dance", "туса"),
            moodEmoji = "🎉",
            aspectRatio = 1.33f,
            accentColorHex = 0xFF9C68FC
        ),
        GifItem(
            id = "gif_love_heart",
            title = "Сердечки и любовь",
            previewEmoji = "❤️",
            gifUrl = "https://media.giphy.com/media/26BRv0ThflsHCqDrG/giphy.gif",
            tags = listOf("любовь", "сердце", "обожаю", "love", "heart", "мило"),
            moodEmoji = "❤️",
            aspectRatio = 1.2f,
            accentColorHex = 0xFFF43F5E
        ),
        GifItem(
            id = "gif_thumbs_up",
            title = "Одобрение / Супер",
            previewEmoji = "👍",
            gifUrl = "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif",
            tags = listOf("класс", "супер", "ок", "лайк", "good", "thumbs up", "молодец"),
            moodEmoji = "👍",
            aspectRatio = 1.35f,
            accentColorHex = 0xFF10B981
        ),
        GifItem(
            id = "gif_thumbs_down",
            title = "Не одобряю",
            previewEmoji = "👎",
            gifUrl = "https://media.giphy.com/media/WrxoaVPiq0cG4/giphy.gif",
            tags = listOf("плохо", "нет", "дизлайк", "no", "boo", "фу"),
            moodEmoji = "👎",
            aspectRatio = 1.35f,
            accentColorHex = 0xFFEF4444
        ),
        GifItem(
            id = "gif_laugh_lol",
            title = "Смех до слёз",
            previewEmoji = "😆",
            gifUrl = "https://media.giphy.com/media/ZqlvCTNHpqrio/giphy.gif",
            tags = listOf("смех", "хаха", "ржака", "лол", "funny", "lol", "laugh"),
            moodEmoji = "😆",
            aspectRatio = 1.45f,
            accentColorHex = 0xFFF59E0B
        ),
        GifItem(
            id = "gif_happy_cat",
            title = "Счастливый котик",
            previewEmoji = "😊",
            gifUrl = "https://media.giphy.com/media/JIX9t2j0ZTN9S/giphy.gif",
            tags = listOf("кот", "радость", "работа", "печат", "cat", "happy", "мило"),
            moodEmoji = "😊",
            aspectRatio = 1.0f,
            accentColorHex = 0xFF38BDF8
        ),
        GifItem(
            id = "gif_shocked",
            title = "Шок и удивление",
            previewEmoji = "😨",
            gifUrl = "https://media.giphy.com/media/l3q2K5jinAlChoCLS/giphy.gif",
            tags = listOf("шок", "удивление", "что", "вау", "shock", "omg", "blink"),
            moodEmoji = "😨",
            aspectRatio = 1.4f,
            accentColorHex = 0xFFA855F7
        ),
        GifItem(
            id = "gif_angry_flame",
            title = "Горящая злость",
            previewEmoji = "😠",
            gifUrl = "https://media.giphy.com/media/11tTNkNy1SdXGg/giphy.gif",
            tags = listOf("злость", "бесит", "огонь", "гнев", "angry", "mad"),
            moodEmoji = "😠",
            aspectRatio = 1.33f,
            accentColorHex = 0xFFF97316
        ),
        GifItem(
            id = "gif_poker_face",
            title = "Невозмутимость",
            previewEmoji = "😐",
            gifUrl = "https://media.giphy.com/media/H5C8CevNMbpBqNqFjl/giphy.gif",
            tags = listOf("покер", "неловко", "взгляд", "странно", "awkward", "neutral"),
            moodEmoji = "😐",
            aspectRatio = 1.25f,
            accentColorHex = 0xFF64748B
        ),
        GifItem(
            id = "gif_thinking_math",
            title = "Глубокие раздумья",
            previewEmoji = "🤔",
            gifUrl = "https://media.giphy.com/media/3o7btPCcdNniyf0ArS/giphy.gif",
            tags = listOf("думаю", "логика", "математика", "хмм", "thinking", "hmm"),
            moodEmoji = "🤔",
            aspectRatio = 1.5f,
            accentColorHex = 0xFF06B6D4
        ),
        GifItem(
            id = "gif_fireworks",
            title = "Салют / Победа",
            previewEmoji = "🎆",
            gifUrl = "https://media.giphy.com/media/26tOZ42Mg6pbTUPHW/giphy.gif",
            tags = listOf("салют", "праздник", "новый год", "победа", "fireworks", "celebrate"),
            moodEmoji = "🎉",
            aspectRatio = 1.4f,
            accentColorHex = 0xFF8B5CF6
        ),
        GifItem(
            id = "gif_high_five",
            title = "Дай пять!",
            previewEmoji = "🙌",
            gifUrl = "https://media.giphy.com/media/3oEjHV0z8S7WM4MwnK/giphy.gif",
            tags = listOf("пять", "круто", "договорились", "high five", "yes"),
            moodEmoji = "👍",
            aspectRatio = 1.33f,
            accentColorHex = 0xFF22C55E
        ),
        GifItem(
            id = "gif_kiss_hug",
            title = "Обнимашки",
            previewEmoji = "🤗",
            gifUrl = "https://media.giphy.com/media/l4pTdcifPZLpDjL1e/giphy.gif",
            tags = listOf("объятия", "любовь", "привет", "мило", "hug", "love"),
            moodEmoji = "❤️",
            aspectRatio = 1.25f,
            accentColorHex = 0xFFEC4899
        ),
        GifItem(
            id = "gif_popcorn_watch",
            title = "Смотрю с попкорном",
            previewEmoji = "🍿",
            gifUrl = "https://media.giphy.com/media/tyqcJoNjNv0Fq/giphy.gif",
            tags = listOf("попкорн", "кино", "интересно", "смотрим", "popcorn", "drama"),
            moodEmoji = "😊",
            aspectRatio = 1.33f,
            accentColorHex = 0xFFEAB308
        ),
        GifItem(
            id = "gif_mind_blown",
            title = "Космос взрыв мозга",
            previewEmoji = "🤯",
            gifUrl = "https://media.giphy.com/media/xT0xeJpnrWC4XWblEk/giphy.gif",
            tags = listOf("космос", "шок", "гениально", "вау", "mind blown", "wow"),
            moodEmoji = "😨",
            aspectRatio = 1.33f,
            accentColorHex = 0xFF6366F1
        ),
        GifItem(
            id = "gif_facepalm",
            title = "Рукалицо",
            previewEmoji = "🤦",
            gifUrl = "https://media.giphy.com/media/6yRVg0HWzgS88/giphy.gif",
            tags = listOf("рукалицо", "фейспалм", "ужас", "facepalm", "sigh"),
            moodEmoji = "😐",
            aspectRatio = 1.33f,
            accentColorHex = 0xFF0EA5E9
        ),
        GifItem(
            id = "gif_applause",
            title = "Овации",
            previewEmoji = "👏",
            gifUrl = "https://media.giphy.com/media/7yOwB85TiTDhHBtu0m/giphy.gif",
            tags = listOf("аплодисменты", "браво", "класс", "clap", "applause"),
            moodEmoji = "👍",
            aspectRatio = 1.33f,
            accentColorHex = 0xFF14B8A6
        ),
        GifItem(
            id = "gif_hacker_code",
            title = "Быстрый кодинг",
            previewEmoji = "💻",
            gifUrl = "https://media.giphy.com/media/13HgwGsXF0aiGY/giphy.gif",
            tags = listOf("код", "программист", "хакер", "ноут", "coding", "dev"),
            moodEmoji = "🤔",
            aspectRatio = 1.5f,
            accentColorHex = 0xFF06B6D4
        ),
        GifItem(
            id = "gif_crying_laugh",
            title = "Падаю со смеху",
            previewEmoji = "😂",
            gifUrl = "https://media.giphy.com/media/G3w5bFfY85rag/giphy.gif",
            tags = listOf("ржака", "смешно", "смех", "хаха", "lmao"),
            moodEmoji = "😆",
            aspectRatio = 1.3f,
            accentColorHex = 0xFFFB923C
        ),
        GifItem(
            id = "gif_rage_desk",
            title = "Стол полетел",
            previewEmoji = "🤬",
            gifUrl = "https://media.giphy.com/media/13699jZW4PZcs6/giphy.gif",
            tags = listOf("злость", "ярость", "работа", "angry", "rage"),
            moodEmoji = "😠",
            aspectRatio = 1.3f,
            accentColorHex = 0xFFDC2626
        )
    )

    fun recordUsedGif(gif: GifItem) {
        val current = _recentGifs.value.toMutableList()
        current.removeAll { it.id == gif.id }
        current.add(0, gif)
        _recentGifs.value = current.take(16)
    }

    fun filterLocalCatalog(query: String, moodEmoji: String? = null): List<GifItem> {
        val q = query.trim().lowercase()
        return curatedCatalog.filter { gif ->
            val moodMatches = moodEmoji == null || gif.moodEmoji == moodEmoji
            val queryMatches = q.isBlank() ||
                gif.title.lowercase().contains(q) ||
                gif.previewEmoji.contains(q) ||
                gif.tags.any { it.lowercase().contains(q) }
            moodMatches && queryMatches
        }
    }

    suspend fun searchGifs(query: String, moodEmoji: String? = null): List<GifItem> {
        val local = filterLocalCatalog(query, moodEmoji)
        val combinedQuery = buildString {
            if (query.isNotBlank()) append(query.trim())
            if (moodEmoji != null) {
                val moodKeyword = MediaExpressionManager.quickMoodFilters.find { it.first == moodEmoji }?.second
                if (!moodKeyword.isNullOrBlank()) {
                    if (isNotEmpty()) append(' ')
                    append(moodKeyword)
                }
            }
        }
        if (combinedQuery.isBlank()) return local

        val networkResults = fetchOnlineGifsIfConfigured(combinedQuery)
        if (networkResults.isEmpty()) return local
        return (networkResults + local).distinctBy { it.gifUrl }
    }

    private fun isValidApiKey(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isNotBlank() &&
            !trimmed.startsWith("your_", ignoreCase = true) &&
            !trimmed.endsWith("_DEFAULT_VALUE", ignoreCase = true) &&
            !trimmed.equals("null", ignoreCase = true)
    }

    private suspend fun fetchOnlineGifsIfConfigured(query: String): List<GifItem> = withContext(Dispatchers.IO) {
        val tenorKey = runCatching { BuildConfig.TENOR_API_KEY }.getOrDefault("")
        val giphyKey = runCatching { BuildConfig.GIPHY_API_KEY }.getOrDefault("")

        if (isValidApiKey(tenorKey)) {
            val res = fetchFromTenor(query, tenorKey)
            if (res.isNotEmpty()) return@withContext res
        }
        if (isValidApiKey(giphyKey)) {
            val res = fetchFromGiphy(query, giphyKey)
            if (res.isNotEmpty()) return@withContext res
        }
        emptyList()
    }

    private fun fetchFromTenor(query: String, apiKey: String): List<GifItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://tenor.googleapis.com/v2/search?q=$encoded&key=$apiKey&limit=16&media_filter=gif,tinygif"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val arr = root.optJSONArray("results") ?: return emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val formats = obj.optJSONObject("media_formats") ?: continue
                        val gifUrl = formats.optJSONObject("tinygif")?.optString("url")
                            ?.takeIf { it.isNotBlank() }
                            ?: formats.optJSONObject("gif")?.optString("url")
                            ?: continue
                        val title = obj.optString("content_description", query)
                        add(
                            GifItem(
                                id = "tenor_${obj.optString("id", i.toString())}",
                                title = title.ifBlank { query },
                                previewEmoji = "🎞️",
                                gifUrl = gifUrl,
                                tags = listOf(query.lowercase()),
                                moodEmoji = "",
                                aspectRatio = 1.3f,
                                accentColorHex = 0xFF2AABEE,
                                isFromNetwork = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchFromGiphy(query: String, apiKey: String): List<GifItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.giphy.com/v1/gifs/search?api_key=$apiKey&q=$encoded&limit=16&rating=pg-13"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val arr = root.optJSONArray("data") ?: return emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val images = obj.optJSONObject("images") ?: continue
                        val fixedWidth = images.optJSONObject("fixed_width") ?: images.optJSONObject("downsized")
                        val gifUrl = fixedWidth?.optString("url")?.takeIf { it.isNotBlank() } ?: continue
                        val w = fixedWidth.optString("width").toFloatOrNull() ?: 200f
                        val h = fixedWidth.optString("height").toFloatOrNull() ?: 150f
                        val ratio = (w / h.coerceAtLeast(1f)).coerceIn(0.8f, 1.8f)
                        add(
                            GifItem(
                                id = "giphy_${obj.optString("id", i.toString())}",
                                title = obj.optString("title", query).ifBlank { query },
                                previewEmoji = "🎞️",
                                gifUrl = gifUrl,
                                tags = listOf(query.lowercase()),
                                moodEmoji = "",
                                aspectRatio = ratio,
                                accentColorHex = 0xFF8B5CF6,
                                isFromNetwork = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun buildGifDocumentData(gif: GifItem): String {
        return JSONObject().apply {
            put("type", "gif")
            put("gifId", gif.id)
            put("title", gif.title)
            put("previewEmoji", gif.previewEmoji)
            put("uri", gif.gifUrl)
            put("mimeType", "image/gif")
            put("aspectRatio", gif.aspectRatio.toDouble())
            put("accentColorHex", gif.accentColorHex)
        }.toString()
    }

    fun parseGifFromMessage(message: Message): GifItem? {
        if (message.mediaType != "gif" && message.documentData?.contains("\"type\":\"gif\"") != true) {
            return null
        }
        val doc = message.documentData
        if (!doc.isNullOrBlank()) {
            try {
                val json = JSONObject(doc)
                if (json.optString("type") == "gif" || message.mediaType == "gif") {
                    val uri = json.optString("uri", message.mediaPath ?: "")
                    return GifItem(
                        id = json.optString("gifId", message.id),
                        title = json.optString("title", "GIF"),
                        previewEmoji = json.optString("previewEmoji", "🎞️"),
                        gifUrl = uri,
                        tags = emptyList(),
                        moodEmoji = "",
                        aspectRatio = json.optDouble("aspectRatio", 1.35).toFloat().coerceIn(0.8f, 1.8f),
                        accentColorHex = json.optLong("accentColorHex", 0xFF2AABEE)
                    )
                }
            } catch (_: Exception) {}
        }
        val url = message.mediaPath ?: return null
        return GifItem(
            id = message.id,
            title = message.text.ifBlank { "GIF" },
            previewEmoji = "🎞️",
            gifUrl = url,
            tags = emptyList(),
            moodEmoji = ""
        )
    }
}
