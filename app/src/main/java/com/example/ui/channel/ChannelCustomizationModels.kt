package com.example.ui.channel

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Legacy-compatible + extended color palettes for channel/group accents & quotes.
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
            quoteBackground = Color(0xFF0072FF).copy(alpha = 0.14f),
            quoteBorder = Color(0xFF00C6FF),
            emojiAccent = "⚡"
        ),
        ProfileColorPalette(
            id = 1,
            name = "Яркий Оранж",
            primaryColor = Color(0xFFFF8800),
            secondaryColor = Color(0xFFFF5722),
            gradientColors = listOf(Color(0xFFFF9900), Color(0xFFFF5E36)),
            quoteBackground = Color(0xFFFF5E36).copy(alpha = 0.14f),
            quoteBorder = Color(0xFFFF9900),
            emojiAccent = "🔥"
        ),
        ProfileColorPalette(
            id = 2,
            name = "Фиолетовый Кибер",
            primaryColor = Color(0xFFA855F7),
            secondaryColor = Color(0xFF7C3AED),
            gradientColors = listOf(Color(0xFFC084FC), Color(0xFF7E22CE)),
            quoteBackground = Color(0xFF7E22CE).copy(alpha = 0.14f),
            quoteBorder = Color(0xFFC084FC),
            emojiAccent = "👑"
        ),
        ProfileColorPalette(
            id = 3,
            name = "Изумрудный Неон",
            primaryColor = Color(0xFF10B981),
            secondaryColor = Color(0xFF059669),
            gradientColors = listOf(Color(0xFF34D399), Color(0xFF047857)),
            quoteBackground = Color(0xFF047857).copy(alpha = 0.14f),
            quoteBorder = Color(0xFF34D399),
            emojiAccent = "💎"
        ),
        ProfileColorPalette(
            id = 4,
            name = "Розовый Сапфир",
            primaryColor = Color(0xFFEC4899),
            secondaryColor = Color(0xFFBE185D),
            gradientColors = listOf(Color(0xFFF472B6), Color(0xFFDB2777)),
            quoteBackground = Color(0xFFDB2777).copy(alpha = 0.14f),
            quoteBorder = Color(0xFFF472B6),
            emojiAccent = "🌸"
        ),
        ProfileColorPalette(
            id = 5,
            name = "Рубиновый Закат",
            primaryColor = Color(0xFFEF4444),
            secondaryColor = Color(0xFFB91C1C),
            gradientColors = listOf(Color(0xFFF87171), Color(0xFFDC2626)),
            quoteBackground = Color(0xFFDC2626).copy(alpha = 0.14f),
            quoteBorder = Color(0xFFF87171),
            emojiAccent = "🚀"
        ),
        ProfileColorPalette(
            id = 6,
            name = "Золотой Звездный",
            primaryColor = Color(0xFFF59E0B),
            secondaryColor = Color(0xFFD97706),
            gradientColors = listOf(Color(0xFFFBBF24), Color(0xFFB45309)),
            quoteBackground = Color(0xFFB45309).copy(alpha = 0.14f),
            quoteBorder = Color(0xFFFBBF24),
            emojiAccent = "🌟"
        )
    )

    fun getPalette(id: Int): ProfileColorPalette = palettes.getOrElse(id) { palettes[0] }
}

/**
 * Telegram-style Message / Quote / Link Preview color schemes:
 * Includes single solid tones, 2-tone diagonal splits, and 3-tone multi-stripe variants.
 */
data class MessageColorScheme(
    val id: Int,
    val name: String,
    val accentColor: Color,
    val stripeColors: List<Color>,
    val isThreeTone: Boolean = false
) {
    val isTwoTone: Boolean get() = stripeColors.size == 2 && !isThreeTone
    val quoteBackgroundDark: Color get() = accentColor.copy(alpha = 0.16f)
    val quoteBackgroundLight: Color get() = accentColor.copy(alpha = 0.12f)
}

object TelegramMessageColors {
    val schemes = listOf(
        // Single solid colors (0..6)
        MessageColorScheme(0, "Синий", Color(0xFF3E95EA), listOf(Color(0xFF3E95EA))),
        MessageColorScheme(1, "Зелёный", Color(0xFF47B14A), listOf(Color(0xFF47B14A))),
        MessageColorScheme(2, "Оранжевый", Color(0xFFE6842A), listOf(Color(0xFFE6842A))),
        MessageColorScheme(3, "Красный", Color(0xFFDF524C), listOf(Color(0xFFDF524C))),
        MessageColorScheme(4, "Фиолетовый", Color(0xFF9E6BFF), listOf(Color(0xFF9E6BFF))),
        MessageColorScheme(5, "Бирюзовый", Color(0xFF28B5BA), listOf(Color(0xFF28B5BA))),
        MessageColorScheme(6, "Розовый", Color(0xFFD95B96), listOf(Color(0xFFD95B96))),
        // Two-tone diagonal split swatches (7..12)
        MessageColorScheme(7, "Голубой и Синий", Color(0xFF3E95EA), listOf(Color(0xFF2F7DE1), Color(0xFF72D6FF))),
        MessageColorScheme(8, "Изумруд и Лайм", Color(0xFF3FB85B), listOf(Color(0xFF2D9C48), Color(0xFFA2DF5E))),
        MessageColorScheme(9, "Закат и Золото", Color(0xFFEB862D), listOf(Color(0xFFD9691B), Color(0xFFF6C244))),
        MessageColorScheme(10, "Алый и Коралл", Color(0xFFE15650), listOf(Color(0xFFC83E3A), Color(0xFFFF8F73))),
        MessageColorScheme(11, "Сирень и Роза", Color(0xFF9C66FF), listOf(Color(0xFF7F4FE0), Color(0xFFF08AE8))),
        MessageColorScheme(12, "Мята и Бирюза", Color(0xFF2EBAC0), listOf(Color(0xFF1F9CA3), Color(0xFF76E7CD))),
        // Three-tone diamond center swatches matching Telegram (13..18)
        MessageColorScheme(13, "Триколор Синий", Color(0xFF3E95EA), listOf(Color(0xFF2E8AE6), Color(0xFFE45550), Color(0xFFF5F7FA)), isThreeTone = true),
        MessageColorScheme(14, "Оранж и Зелёный", Color(0xFFEB872E), listOf(Color(0xFFE67E22), Color(0xFF47B14A), Color(0xFFF5F7FA)), isThreeTone = true),
        MessageColorScheme(15, "Роза и Красный", Color(0xFFE05252), listOf(Color(0xFFDB4437), Color(0xFF47B14A), Color(0xFFF5F7FA)), isThreeTone = true),
        MessageColorScheme(16, "Синий и Лайм", Color(0xFF3898E8), listOf(Color(0xFF3898E8), Color(0xFF4FB954), Color(0xFFF5F7FA)), isThreeTone = true),
        MessageColorScheme(17, "Бирюза и Персик", Color(0xFF2EB3B8), listOf(Color(0xFF28B5BA), Color(0xFFF08D6E), Color(0xFFF5F7FA)), isThreeTone = true),
        MessageColorScheme(18, "Фиолет и Янтарь", Color(0xFF9C66FF), listOf(Color(0xFF945DF4), Color(0xFFF39C2A), Color(0xFFF5F7FA)), isThreeTone = true)
    )

    fun getScheme(id: Int): MessageColorScheme = schemes.getOrElse(id) { schemes[0] }
}

/**
 * Telegram-style 16-swatch Profile Header Color Schemes (2 rows x 8 columns):
 * Row 1 (0..7): Solid rich header colors
 * Row 2 (8..15): Matching 2-tone diagonal gradient splits
 */
data class ProfileHeaderColorScheme(
    val id: Int,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val headerGradient: List<Color>,
    val ringColors: List<Color>
) {
    val isTwoTone: Boolean get() = primaryColor != secondaryColor
}

object TelegramProfileHeaderSchemes {
    val schemes = listOf(
        // Row 1: Solid 8 swatches
        ProfileHeaderColorScheme(0, "Голубой", Color(0xFF2F84D9), Color(0xFF2F84D9), listOf(Color(0xFF2772C0), Color(0xFF194B82)), listOf(Color(0xFF4BB4FF), Color(0xFF2F84D9))),
        ProfileHeaderColorScheme(1, "Зелёный", Color(0xFF3E9C2C), Color(0xFF3E9C2C), listOf(Color(0xFF348724), Color(0xFF1F5317)), listOf(Color(0xFF6AE053), Color(0xFF3E9C2C))),
        ProfileHeaderColorScheme(2, "Янтарный", Color(0xFFCC781D), Color(0xFFCC781D), listOf(Color(0xFFB56614), Color(0xFF6B3B0A)), listOf(Color(0xFFFFB247), Color(0xFFCC781D))),
        ProfileHeaderColorScheme(3, "Коралловый", Color(0xFFB84C42), Color(0xFFB84C42), listOf(Color(0xFFA03D34), Color(0xFF5D211C)), listOf(Color(0xFFFF7A6E), Color(0xFFB84C42))),
        ProfileHeaderColorScheme(4, "Фиолетовый", Color(0xFF8C5AD9), Color(0xFF8C5AD9), listOf(Color(0xFF7749C0), Color(0xFF452778)), listOf(Color(0xFFC29BFF), Color(0xFF8C5AD9))),
        ProfileHeaderColorScheme(5, "Бирюзовый", Color(0xFF2A9FB0), Color(0xFF2A9FB0), listOf(Color(0xFF218898), Color(0xFF12505A)), listOf(Color(0xFF5EE8F7), Color(0xFF2A9FB0))),
        ProfileHeaderColorScheme(6, "Малиновый", Color(0xFFB5497F), Color(0xFFB5497F), listOf(Color(0xFF9C3A6B), Color(0xFF581D3B)), listOf(Color(0xFFFF7DBA), Color(0xFFB5497F))),
        ProfileHeaderColorScheme(7, "Графитовый", Color(0xFF7E8C9E), Color(0xFF7E8C9E), listOf(Color(0xFF5E6B7B), Color(0xFF313944)), listOf(Color(0xFFB6C4D6), Color(0xFF7E8C9E))),
        // Row 2: Matching 8 two-tone diagonal gradient splits
        ProfileHeaderColorScheme(8, "Синий + Циан", Color(0xFF2E86DB), Color(0xFF66D5F7), listOf(Color(0xFF256FBA), Color(0xFF1B5770)), listOf(Color(0xFF66D5F7), Color(0xFF2E86DB))),
        ProfileHeaderColorScheme(9, "Изумруд + Лайм", Color(0xFF3D9C2E), Color(0xFF9BDB56), listOf(Color(0xFF2F8426), Color(0xFF436D1E)), listOf(Color(0xFF9BDB56), Color(0xFF3D9C2E))),
        ProfileHeaderColorScheme(10, "Оранж + Золото", Color(0xFFD17A1E), Color(0xFFF2B63D), listOf(Color(0xFFB86314), Color(0xFF7A5615)), listOf(Color(0xFFF2B63D), Color(0xFFD17A1E))),
        ProfileHeaderColorScheme(11, "Терракота + Персик", Color(0xFFBE5046), Color(0xFFFF8F6B), listOf(Color(0xFFA03E35), Color(0xFF733C2C)), listOf(Color(0xFFFF8F6B), Color(0xFFBE5046))),
        ProfileHeaderColorScheme(12, "Аметист + Розовый", Color(0xFF8C5AD9), Color(0xFFE979EB), listOf(Color(0xFF7445BC), Color(0xFF6B2D6D)), listOf(Color(0xFFE979EB), Color(0xFF8C5AD9))),
        ProfileHeaderColorScheme(13, "Аква + Мята", Color(0xFF29A2B2), Color(0xFF74E5BF), listOf(Color(0xFF1E8492), Color(0xFF216A5A)), listOf(Color(0xFF74E5BF), Color(0xFF29A2B2))),
        ProfileHeaderColorScheme(14, "Рубин + Роза", Color(0xFFBE4D82), Color(0xFFF78D90), listOf(Color(0xFF9F3967), Color(0xFF70343B)), listOf(Color(0xFFF78D90), Color(0xFFBE4D82))),
        ProfileHeaderColorScheme(15, "Сталь + Серебро", Color(0xFF677487), Color(0xFFADB8C7), listOf(Color(0xFF4D5868), Color(0xFF2E3641)), listOf(Color(0xFFADB8C7), Color(0xFF677487)))
    )

    fun getOrNull(id: Int?): ProfileHeaderColorScheme? =
        if (id == null || id < 0) null else schemes.getOrElse(id) { schemes[0] }
}

/**
 * Wallpaper presets for channels and chats (dark + light preview themes).
 */
data class WallpaperPreset(
    val id: String,
    val name: String,
    val previewGradient: List<Color>,
    val lightGradient: List<Color>,
    val patternType: String,
    val centerIcon: String = "",
    val isDark: Boolean = true
)

object TelegramWallpapers {
    val presets = listOf(
        WallpaperPreset(
            id = "none",
            name = "Без обоев",
            previewGradient = listOf(Color(0xFF0E131B), Color(0xFF0A0E15)),
            lightGradient = listOf(Color(0xFFD7ECD5), Color(0xFFC8E3C6)),
            patternType = "none",
            centerIcon = ""
        ),
        WallpaperPreset(
            id = "easter_chick",
            name = "Весенние узоры",
            previewGradient = listOf(Color(0xFF101812), Color(0xFF0A110C)),
            lightGradient = listOf(Color(0xFFDCF2D4), Color(0xFFCBE8C3)),
            patternType = "doodles",
            centerIcon = "🐥"
        ),
        WallpaperPreset(
            id = "winter_snowman",
            name = "Зимняя сказка",
            previewGradient = listOf(Color(0xFF0E171E), Color(0xFF091118)),
            lightGradient = listOf(Color(0xFFD5ECFA), Color(0xFFC1E0F4)),
            patternType = "stars",
            centerIcon = "☃️"
        ),
        WallpaperPreset(
            id = "diamond_doodles",
            name = "Классика Telegram",
            previewGradient = listOf(Color(0xFF0D1420), Color(0xFF090F1A)),
            lightGradient = listOf(Color(0xFFD7EDF4), Color(0xFFC5E1EC)),
            patternType = "doodles",
            centerIcon = "💎"
        ),
        WallpaperPreset(
            id = "tulips",
            name = "Цветочный сад",
            previewGradient = listOf(Color(0xFF181412), Color(0xFF120D0C)),
            lightGradient = listOf(Color(0xFFF6E7D8), Color(0xFFECD5BE)),
            patternType = "petals",
            centerIcon = "🌷"
        ),
        WallpaperPreset(
            id = "purple_hearts",
            name = "Неон Любовь",
            previewGradient = listOf(Color(0xFF1B1026), Color(0xFF120A1B)),
            lightGradient = listOf(Color(0xFFEDE0F9), Color(0xFFDFC8F4)),
            patternType = "hearts",
            centerIcon = "💜"
        ),
        WallpaperPreset(
            id = "holiday_tree",
            name = "Праздник",
            previewGradient = listOf(Color(0xFF0B1914), Color(0xFF07120E)),
            lightGradient = listOf(Color(0xFFD4EFE3), Color(0xFFC1E5D5)),
            patternType = "stars",
            centerIcon = "🎄"
        ),
        WallpaperPreset(
            id = "gaming",
            name = "Гейминг",
            previewGradient = listOf(Color(0xFF141126), Color(0xFF0D0B1B)),
            lightGradient = listOf(Color(0xFFE0E5FA), Color(0xFFCED6F4)),
            patternType = "cyber_grid",
            centerIcon = "🎮"
        ),
        WallpaperPreset(
            id = "telegram_doodles",
            name = "Telegram Doodles",
            previewGradient = listOf(Color(0xFF17212B), Color(0xFF0E1621)),
            lightGradient = listOf(Color(0xFFD5EBD6), Color(0xFFC4E1C5)),
            patternType = "doodles",
            centerIcon = "✨"
        ),
        WallpaperPreset(
            id = "cyber_grid",
            name = "Киберсетка 2077",
            previewGradient = listOf(Color(0xFF0A0E17), Color(0xFF161F30)),
            lightGradient = listOf(Color(0xFFD6E6F2), Color(0xFFC2D9EB)),
            patternType = "cyber_grid",
            centerIcon = "⚡"
        ),
        WallpaperPreset(
            id = "deep_space",
            name = "Космическая Небула",
            previewGradient = listOf(Color(0xFF1E1035), Color(0xFF0D0221)),
            lightGradient = listOf(Color(0xFFE5DCF7), Color(0xFFD5C8EE)),
            patternType = "stars",
            centerIcon = "🪐"
        ),
        WallpaperPreset(
            id = "dark_aurora",
            name = "Северное Сияние",
            previewGradient = listOf(Color(0xFF062826), Color(0xFF0B1B2B)),
            lightGradient = listOf(Color(0xFFD0EEE8), Color(0xFFBCE4DC)),
            patternType = "aurora",
            centerIcon = "🌌"
        ),
        WallpaperPreset(
            id = "sakura_night",
            name = "Ночная Сакура",
            previewGradient = listOf(Color(0xFF280B1E), Color(0xFF160613)),
            lightGradient = listOf(Color(0xFFF9E1EC), Color(0xFFF2CEDF)),
            patternType = "petals",
            centerIcon = "🌸"
        ),
        WallpaperPreset(
            id = "golden_matrix",
            name = "Золотая Матрица",
            previewGradient = listOf(Color(0xFF261904), Color(0xFF120C02)),
            lightGradient = listOf(Color(0xFFF6ECD4), Color(0xFFECE0BE)),
            patternType = "matrix",
            centerIcon = "🏆"
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
    val animationType: String = "pulse"
)

object ChannelEmojiCatalog {
    val emojis = listOf(
        CustomAnimatedEmoji("battery", "🪫", "Заряд", "Премиум", Color(0xFFEF5350), "pulse"),
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
        CustomAnimatedEmoji("unicorn", "🦄", "Единорог", "VIP & Премиум", Color(0xFFFF4081), "bounce"),
        CustomAnimatedEmoji("duck", "🦆", "Утка", "Duck Emoji", Color(0xFFFFCA28), "bounce"),
        CustomAnimatedEmoji("heart_fire", "❤️‍🔥", "Пылающее сердце", "Эмоции", Color(0xFFFF5252), "pulse"),
        CustomAnimatedEmoji("cool", "😎", "Крутой", "Эмоции", Color(0xFF42A5F5), "float"),
        CustomAnimatedEmoji("bot", "🤖", "Кибер-бот", "Гейминг", Color(0xFF26C6DA), "pulse"),
        CustomAnimatedEmoji("target", "🎯", "В яблочко", "Награды", Color(0xFFEF5350), "sparkle")
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
    val messageColorId: Int = 0,
    val messageEmojiPattern: String? = null,
    val profileColorId: Int = 0,
    val profileHeaderColorId: Int? = 0,
    val profileEmojiPattern: String? = "⚡",
    val chatWallpaperId: String = "none",
    val wallpaperCustomUri: String? = null,
    val wallpaperBlur: Float = 0f,
    val wallpaperDim: Float = 0.25f,
    val wallpaperPatternOpacity: Float = 0.35f,
    val boostLevel: Int = 3,
    val boostCount: Int = 15,
    val boostsRequiredForNextLevel: Int = 48,
    val hasVotedBoost: Boolean = false,
    val isSignaturesEnabled: Boolean = false,
    val showAuthorProfiles: Boolean = false,
    val discussionChatId: String? = null,
    val discussionChatTitle: String = "",
    val discussionOnlyMembersCanSend: Boolean = false,
    val discussionJoinRequestsRequired: Boolean = false,
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
    val autoGreetingText: String = "",
    val greetingMessages: List<ChannelGreetingMessage> = emptyList(),
    val greetingDeliveredUserIds: Set<String> = emptySet(),
    val restrictSavingContent: Boolean = false,
    val isPublic: Boolean = true,
    val autoDeletePeriod: String? = null,
    val blacklistedUsers: List<BlacklistedUser> = emptyList(),
    val additionalInviteLinks: List<ChannelInviteLink> = emptyList()
) {
    val wallpaperId: String get() = chatWallpaperId
    val activeMessageScheme: MessageColorScheme get() = TelegramMessageColors.getScheme(messageColorId)
    val activeProfileHeaderScheme: ProfileHeaderColorScheme? get() = TelegramProfileHeaderSchemes.getOrNull(profileHeaderColorId)
}

data class ChannelGreetingMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val mediaPath: String? = null,
    val mediaType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class BlacklistedUser(
    val userId: String,
    val userName: String,
    val reason: String = "Нарушение правил канала",
    val bannedBy: String = "Администратор",
    val timestamp: Long = System.currentTimeMillis()
)

data class ChannelInviteLink(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String,
    val label: String = "",
    val createdBy: String = "Владелец",
    val usageCount: Int = 0,
    val maxUses: Int? = null,
    val expiresAt: Long? = null,
    val isPrimary: Boolean = false
)

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
