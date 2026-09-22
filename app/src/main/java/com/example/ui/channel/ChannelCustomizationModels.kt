package com.example.ui.channel

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Color palettes for channel and group profile colors & quote backgrounds.
 * Telegram-style vivid 7-color gradient collection.
 */
data class ProfileColorPalette(
    val id: Int,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val gradientColors: List<Color>,
    val quoteBackground: Color,
    val quoteBorder: Color,
    val emojiAccent: String
) {
    val brush: Brush get() = Brush.linearGradient(gradientColors)
}

object TelegramProfilePalettes {
    val palettes = listOf(
        ProfileColorPalette(
            id = 0,
            name = "Синий Неон",
            primaryColor = Color(0xFF2AABEE),
            secondaryColor = Color(0xFF229ED9),
            gradientColors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
            quoteBackground = Color(0xFF0072FF).copy(alpha = 0.12f),
            quoteBorder = Color(0xFF00C6FF),
            emojiAccent = "⚡"
        ),
        ProfileColorPalette(
            id = 1,
            name = "Яркий Оранж",
            primaryColor = Color(0xFFFF8800),
            secondaryColor = Color(0xFFFF5722),
            gradientColors = listOf(Color(0xFFFF9900), Color(0xFFFF5E36)),
            quoteBackground = Color(0xFFFF5E36).copy(alpha = 0.12f),
            quoteBorder = Color(0xFFFF9900),
            emojiAccent = "🔥"
        ),
        ProfileColorPalette(
            id = 2,
            name = "Фиолетовый Кибер",
            primaryColor = Color(0xFFA855F7),
            secondaryColor = Color(0xFF7C3AED),
            gradientColors = listOf(Color(0xFFC084FC), Color(0xFF7E22CE)),
            quoteBackground = Color(0xFF7E22CE).copy(alpha = 0.12f),
            quoteBorder = Color(0xFFC084FC),
            emojiAccent = "👑"
        ),
        ProfileColorPalette(
            id = 3,
            name = "Изумрудный Неон",
            primaryColor = Color(0xFF10B981),
            secondaryColor = Color(0xFF059669),
            gradientColors = listOf(Color(0xFF34D399), Color(0xFF047857)),
            quoteBackground = Color(0xFF047857).copy(alpha = 0.12f),
            quoteBorder = Color(0xFF34D399),
            emojiAccent = "💎"
        ),
        ProfileColorPalette(
            id = 4,
            name = "Розовый Сапфир",
            primaryColor = Color(0xFFEC4899),
            secondaryColor = Color(0xFFBE185D),
            gradientColors = listOf(Color(0xFFF472B6), Color(0xFFDB2777)),
            quoteBackground = Color(0xFFDB2777).copy(alpha = 0.12f),
            quoteBorder = Color(0xFFF472B6),
            emojiAccent = "🌸"
        ),
        ProfileColorPalette(
            id = 5,
            name = "Рубиновый Закат",
            primaryColor = Color(0xFFEF4444),
            secondaryColor = Color(0xFFB91C1C),
            gradientColors = listOf(Color(0xFFF87171), Color(0xFFDC2626)),
            quoteBackground = Color(0xFFDC2626).copy(alpha = 0.12f),
            quoteBorder = Color(0xFFF87171),
            emojiAccent = "🚀"
        ),
        ProfileColorPalette(
            id = 6,
            name = "Золотой Звездный",
            primaryColor = Color(0xFFF59E0B),
            secondaryColor = Color(0xFFD97706),
            gradientColors = listOf(Color(0xFFFBBF24), Color(0xFFB45309)),
            quoteBackground = Color(0xFFB45309).copy(alpha = 0.12f),
            quoteBorder = Color(0xFFFBBF24),
            emojiAccent = "🌟"
        )
    )

    fun getPalette(id: Int): ProfileColorPalette = palettes.getOrElse(id) { palettes[0] }
}

/**
 * Wallpaper presets for channels and chats.
 */
data class WallpaperPreset(
    val id: String,
    val name: String,
    val previewGradient: List<Color>,
    val patternType: String,
    val isDark: Boolean = true
)

object TelegramWallpapers {
    val presets = listOf(
        WallpaperPreset(
            id = "default",
            name = "Стандартные Neon",
            previewGradient = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B)),
            patternType = "neon_grid"
        ),
        WallpaperPreset(
            id = "telegram_doodles",
            name = "Telegram Doodles",
            previewGradient = listOf(Color(0xFF17212B), Color(0xFF0E1621)),
            patternType = "doodles"
        ),
        WallpaperPreset(
            id = "cyber_grid",
            name = "Киберсетка 2077",
            previewGradient = listOf(Color(0xFF0A0E17), Color(0xFF161F30)),
            patternType = "cyber_grid"
        ),
        WallpaperPreset(
            id = "deep_space",
            name = "Космическая Небула",
            previewGradient = listOf(Color(0xFF1E1035), Color(0xFF0D0221)),
            patternType = "stars"
        ),
        WallpaperPreset(
            id = "dark_aurora",
            name = "Северное Сияние",
            previewGradient = listOf(Color(0xFF062826), Color(0xFF0B1B2B)),
            patternType = "aurora"
        ),
        WallpaperPreset(
            id = "sakura_night",
            name = "Ночная Сакура",
            previewGradient = listOf(Color(0xFF280B1E), Color(0xFF160613)),
            patternType = "petals"
        ),
        WallpaperPreset(
            id = "golden_matrix",
            name = "Золотая Матрица",
            previewGradient = listOf(Color(0xFF261904), Color(0xFF120C02)),
            patternType = "matrix"
        )
    )

    fun getPreset(id: String): WallpaperPreset = presets.find { it.id == id } ?: presets[0]
    fun getWallpaper(id: String): WallpaperPreset = getPreset(id)
}

/**
 * Animated/custom emojis available for channel headers and statuses.
 */
data class CustomAnimatedEmoji(
    val id: String,
    val emoji: String,
    val name: String,
    val category: String,
    val glowColor: Color,
    val animationType: String = "pulse" // pulse, rotate, bounce, sparkle, float
)

object ChannelEmojiCatalog {
    val emojis = listOf(
        CustomAnimatedEmoji("fire", "🔥", "Огонь", "Популярное", Color(0xFFFF5722), "pulse"),
        CustomAnimatedEmoji("lightning", "⚡", "Молния", "Популярное", Color(0xFFFFEB3B), "sparkle"),
        CustomAnimatedEmoji("crown", "👑", "Корона", "VIP & Премиум", Color(0xFFFFD700), "float"),
        CustomAnimatedEmoji("gem", "💎", "Бриллиант", "VIP & Премиум", Color(0xFF00E5FF), "sparkle"),
        CustomAnimatedEmoji("rocket", "🚀", "Ракета", "Космос", Color(0xFFFF5252), "bounce"),
        CustomAnimatedEmoji("planet", "🪐", "Планета", "Космос", Color(0xFFE040FB), "rotate"),
        CustomAnimatedEmoji("star", "🌟", "Сияющая звезда", "Популярное", Color(0xFFFFD54F), "sparkle"),
        CustomAnimatedEmoji("alien", "👾", "Кибер Пиксель", "Гейминг", Color(0xFF00E676), "bounce"),
        CustomAnimatedEmoji("magic", "✨", "Магические искры", "VIP & Премиум", Color(0xFFFFF176), "sparkle"),
        CustomAnimatedEmoji("cherry", "🌸", "Сакура", "Природа", Color(0xFFFF80AB), "float"),
        CustomAnimatedEmoji("shield", "🛡️", "Щит безопасности", "Безопасность", Color(0xFF00B0FF), "pulse"),
        CustomAnimatedEmoji("trophy", "🏆", "Кубок", "Награды", Color(0xFFFFC107), "sparkle"),
        CustomAnimatedEmoji("medal", "🎖️", "Медаль", "Награды", Color(0xFFFF9100), "float"),
        CustomAnimatedEmoji("crystal", "🔮", "Магический шар", "Мистика", Color(0xFF7C4DFF), "pulse"),
        CustomAnimatedEmoji("moon", "🌙", "Полумесяц", "Космос", Color(0xFFB388FF), "float"),
        CustomAnimatedEmoji("unicorn", "🦄", "Единорог", "VIP & Премиум", Color(0xFFFF4081), "bounce")
    )
}

/**
 * Channel & Group Customization state data model.
 */
data class ChannelCustomization(
    val chatId: String,
    val avatarUrl: String? = null,
    val emojiStatus: String? = "🪫",
    val emojiStatusAnimated: Boolean = true,
    val profileColorId: Int = 0,
    val profileEmojiPattern: String? = "⚡",
    val chatWallpaperId: String = "default",
    val wallpaperCustomUri: String? = null,
    val wallpaperBlur: Float = 0f,
    val wallpaperDim: Float = 0.2f,
    val wallpaperPatternOpacity: Float = 0.35f,
    val boostLevel: Int = 3,
    val boostCount: Int = 15,
    val boostsRequiredForNextLevel: Int = 48,
    val hasVotedBoost: Boolean = false,
    val isSignaturesEnabled: Boolean = false,
    val showAuthorProfiles: Boolean = false,
    val discussionChatId: String? = null,
    val discussionChatTitle: String = "KuoteX chat✨",
    val slowModeSeconds: Int = 0,
    val subscriberCount: Int = 1,
    val description: String = "«Контроль над обществом возможен только там, где люди соглашаются его принять. Но разум — он не подчиняется. Он ищет путь.»",
    val inviteLink: String = "t.me/KuoteXMessenger",
    val restrictedPermissions: Set<String> = emptySet(),
    val directMessagesEnabled: Boolean = true,
    val directMessageStarPrice: Int = 26,
    val autoTranslateEnabled: Boolean = false,
    val reactionsEnabled: Boolean = true,
    val availableReactions: List<String> = listOf(
        "❤️", "👍", "👎", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱",
        "🤬", "😢", "🎉", "🤩", "🙏", "👌", "🕊️", "🥱", "🥴", "😍", "🐳",
        "❤️‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🦄", "💊", "🍌", "🏆", "💔",
        "🤨", "😐", "🍓", "🍾", "💋", "🖕", "😈", "😴", "😭", "🤓", "👻",
        "👨‍💻", "👀", "🎃", "🙈", "😇", "🤝", "🚀", "💎", "⭐", "✨", "👑"
    ),
    val maxReactionsPerPost: Int = 11,
    val paidStarReactionsEnabled: Boolean = true,
    val autoGreetingEnabled: Boolean = false,
    val autoGreetingText: String = "Новое приветствие\nПриветствие будет автоматически отправляться новым участникам.",
    val restrictSavingContent: Boolean = false,
    val isPublic: Boolean = true,
    val autoDeletePeriod: String? = null
) {
    val wallpaperId: String get() = chatWallpaperId
}

/**
 * Boost Perk definition for channel levels.
 */
data class BoostPerk(
    val level: Int,
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean
)

/**
 * Booster user entity.
 */
data class BoosterUser(
    val userId: String,
    val userName: String,
    val avatarUrl: String,
    val boostsCount: Int,
    val levelGranted: Int,
    val timestamp: Long
)

/**
 * Poll Models for Telegram-style Voting in channels/groups.
 */
data class PollOption(
    val id: Int,
    val text: String,
    val voteCount: Int = 0,
    val percentage: Float = 0f,
    val voters: List<String> = emptyList()
)

data class PollData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val chatId: String,
    val creatorId: String,
    val creatorName: String,
    val question: String,
    val options: List<PollOption>,
    val isAnonymous: Boolean = true,
    val isMultipleChoice: Boolean = false,
    val isQuiz: Boolean = false,
    val correctOptionIndex: Int? = null,
    val explanation: String? = null,
    val isClosed: Boolean = false,
    val totalVoters: Int = 0,
    val userSelectedOptionIds: Set<Int> = emptySet(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Recent Admin Actions / Audit Log.
 */
data class AdminActionLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val adminName: String,
    val adminAvatar: String,
    val actionTitle: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
