package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Telegram Emoji Model & Full Unicode + Custom Pack Repository
 */
data class EmojiItem(
    val emoji: String,
    val name: String = "",
    val keywords: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val packName: String? = null
)

data class EmojiCategory(
    val id: String,
    val title: String,
    val icon: String,
    val emojis: List<EmojiItem>,
    val isCustomPack: Boolean = false,
    val packAuthor: String? = null,
    val packLevelRequired: Int = 0
)

object TelegramEmojiData {

    val recentEmojis = mutableStateListOf(
        EmojiItem("🔥", "Огонь", listOf("fire", "огонь", "пламя")),
        EmojiItem("⚡", "Молния", listOf("lightning", "молния", "ток")),
        EmojiItem("❤️", "Красное сердце", listOf("heart", "любовь", "сердце")),
        EmojiItem("😂", "Смех до слез", listOf("joy", "смех", "ржака")),
        EmojiItem("🥰", "Влюбленный", listOf("love", "мило", "сердца")),
        EmojiItem("👍", "Палец вверх", listOf("like", "лайк", "класс")),
        EmojiItem("💎", "Бриллиант", listOf("gem", "алмаз", "кристалл")),
        EmojiItem("👑", "Корона", listOf("crown", "король", "вип")),
        EmojiItem("✨", "Искры", listOf("sparkles", "блеск", "магия")),
        EmojiItem("🚀", "Ракета", listOf("rocket", "старт", "космос")),
        EmojiItem("🪫", "Разряженная батарея", listOf("battery", "энергия", "статус")),
        EmojiItem("🎉", "Праздник", listOf("party", "туса", "салют")),
        EmojiItem("😎", "Крутой в очках", listOf("cool", "крутой", "очки")),
        EmojiItem("🤝", "Рукопожатие", listOf("deal", "согласие", "дружба")),
        EmojiItem("💯", "Сто процентов", listOf("100", "топ", "класс")),
        EmojiItem("🤩", "В восторге", listOf("stars", "вау", "звезды"))
    )

    fun addRecent(emoji: EmojiItem) {
        val existingIndex = recentEmojis.indexOfFirst { it.emoji == emoji.emoji }
        if (existingIndex != -1) {
            recentEmojis.removeAt(existingIndex)
        }
        recentEmojis.add(0, emoji)
        if (recentEmojis.size > 28) {
            recentEmojis.removeAt(recentEmojis.size - 1)
        }
    }

    val categories: List<EmojiCategory> by lazy {
        listOf(
            EmojiCategory(
                id = "channel_statuses",
                title = "Статусы канала",
                icon = "🪫",
                isCustomPack = true,
                packAuthor = "Telegram Premium Statuses",
                packLevelRequired = 0,
                emojis = listOf(
                    EmojiItem("🪫", "Низкий заряд", listOf("battery", "статус", "заряд"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🪐", "Сатурн", listOf("saturn", "планета", "космос"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("⚡", "Неоновая молния", listOf("lightning", "молния", "ток"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("👑", "Золотая корона", listOf("crown", "корона", "лидер"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("💎", "Алмаз", listOf("gem", "кристалл", "алмаз"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("⭐", "Звезда Telegram", listOf("star", "звезда", "стар"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("💠", "Верификация", listOf("verify", "галочка", "ромб"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🚀", "Ракета буста", listOf("rocket", "ракета", "буст"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🔥", "Пламя", listOf("fire", "огонь", "хайп"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("👻", "Кибер призрак", listOf("ghost", "призрак", "фантом"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🏆", "Золотой кубок", listOf("cup", "победа", "трофей"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🌹", "Неоновая роза", listOf("rose", "роза", "цветок"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("💖", "Сияющее сердце", listOf("heart", "сердце", "любовь"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🛡️", "Щит защиты", listOf("shield", "безопасность", "щит"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🛸", "Космолет", listOf("ufo", "нло", "тарелка"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🎧", "Аудио поток", listOf("audio", "наушники", "музыка"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🎯", "В яблочко", listOf("target", "цель", "мишень"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🔮", "Магический шар", listOf("orb", "магия", "сфера"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🦾", "Кибер-рука", listOf("arm", "кибер", "сила"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🕶️", "Темные очки", listOf("cool", "матрица", "стиль"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🌟", "Супернова", listOf("nova", "звезда", "сияние"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("💸", "Крылатые купюры", listOf("money", "деньги", "прибыль"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("☕", "Кофе брейк", listOf("coffee", "кофе", "отдых"), isCustom = true, packName = "Статусы канала"),
                    EmojiItem("🎮", "Гейминг", listOf("game", "игра", "джойстик"), isCustom = true, packName = "Статусы канала")
                )
            ),
            EmojiCategory(
                id = "smileys",
                title = "Смайлы и эмоции",
                icon = "😀",
                emojis = listOf(
                    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "🥹", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗",
                    "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕",
                    "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😮‍💨", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨",
                    "😰", "😥", "😓", "🫣", "🤗", "🫡", "🤔", "🫢", "🤫", "🤥", "😶", "😶‍🌫️", "😐", "😑", "😬", "🫨", "🫠", "🙄", "😯", "😦",
                    "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "😵‍💫", "🫥", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠",
                    "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            EmojiCategory(
                id = "people",
                title = "Люди и жесты",
                icon = "👋",
                emojis = listOf(
                    "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
                    "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
                    "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🫀", "🫁", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄", "🫦", "👶", "🧒",
                    "👦", "👧", "🧑", "👱", "👨", "🧔", "🧔‍♂️", "🧔‍♀️", "👨‍🦰", "👨‍🦱", "👨‍🦳", "👨‍🦲", "👩", "👩‍🦰", "🧑‍🦰", "👩‍🦱", "🧑‍🦱", "👩‍🦳", "🧑‍🦳", "👩‍🦲",
                    "🧑‍🦲", "👱‍♀️", "👱‍♂️", "🧓", "👴", "👵", "🙍", "🙍‍♂️", "🙍‍♀️", "🙎", "🙎‍♂️", "🙎‍♀️", "🙅", "🙅‍♂️", "🙅‍♀️", "🙆", "🙆‍♂️", "🙆‍♀️", "💁", "💁‍♂️"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            EmojiCategory(
                id = "animals",
                title = "Животные и природа",
                icon = "🐻",
                emojis = listOf(
                    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊",
                    "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌",
                    "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀",
                    "🪼", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🦭", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪",
                    "🐫", "🦒", "🦘", "🦬", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐", "🦌", "🐕", "🐩", "🦮", "🐕‍🦺", "🐈", "🐈‍⬛",
                    "🪶", "🐓", "🦃", "🦤", "🦚", "🦜", "🦢", "🦩", "🕊️", "🐇", "🦝", "🦨", "🦡", "🦫", "🦦", "🦥", "🐁", "🐀", "🐿️", "🦔",
                    "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃", "🍄", "🪨", "🪵", "🌺", "🌸", "🌼", "🌻", "🌞", "🌝",
                    "🌛", "🌜", "🌚", "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔", "🌙", "🌎", "🌍", "🌏", "🪐", "💫", "⭐", "🌟", "✨",
                    "⚡", "☄️", "💥", "🔥", "🌪️", "🌈", "☀️", "🌤️", "⛅", "🌥️", "☁️", "🌦️", "🌧️", "⛈️", "🌩️", "🌨️", "❄️", "☃️", "⛄", "🌬️"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            EmojiCategory(
                id = "food",
                title = "Еда и напитки",
                icon = "🍔",
                emojis = listOf(
                    "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
                    "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠", "🫘", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚",
                    "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴", "🌭", "🍔", "🍟", "🍕", "🫓", "🥪", "🥙", "🧆", "🌮", "🌯", "🫔",
                    "🥗", "🥘", "🫕", "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠", "🥮", "🍢",
                    "🍡", "🍧", "🍨", "🍦", "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "🍼",
                    "🫖", "☕", "🍵", "🧃", "🥤", "🧋", "🫗", "🍶", "🍺", "🍻", "🥂", "🍷", "🫗", "🥃", "🍸", "🍹", "🧉", "🍾", "🧊", "🥄"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            EmojiCategory(
                id = "activity",
                title = "Активности и спорт",
                icon = "⚽",
                emojis = listOf(
                    "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳",
                    "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "🤺",
                    "⛹️", "🤾", "🧗", "🏇", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🏵️", "🎗️", "🎫",
                    "🎟️", "🎪", "🤹", "🎭", "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🪘", "🎷", "🎺", "🪗", "🎸", "🪕", "🎻", "🎲",
                    "♟️", "🎯", "🎳", "🎮", "🎰", "🧩"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            EmojiCategory(
                id = "travel",
                title = "Путешествия и места",
                icon = "🚗",
                emojis = listOf(
                    "🚗", "🚙", "🚕", "🛺", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛵", "🏍️", "🛞", "🚲", "🛴",
                    "🛹", "🛼", "🚁", "🛸", "✈️", "🛫", "🛬", "🪂", "🚀", "🛰️", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "🛟", "🪝", "⛽",
                    "🚨", "🚥", "🚦", "🛑", "🚧", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡", "🎢", "🎠", "⛲", "⛱️", "🏖️", "🏝️", "🏜️", "🌋",
                    "⛰️", "🏔️", "🗻", "🏕️", "⛺", "🛖", "🏠", "🏡", "🏘️", "🏚️", "🏗️", "🏢", "🏬", "🏣", "🏤", "🏥", "🏦", "🏨", "🏪", "🏫"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            EmojiCategory(
                id = "objects",
                title = "Предметы и объекты",
                icon = "💡",
                emojis = listOf(
                    "💡", "🔦", "🏮", "🪔", "🕯️", "🪩", "🔋", "🪫", "🔌", "💻", "🖥️", "🖨️", "⌨️", "🖱️", "🖲️", "💽", "💾", "💿", "📀", "📼",
                    "📷", "📸", "📹", "🎥", "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️", "🎛️", "⏱️", "⏲️", "⏰", "🕰️", "⌛",
                    "⏳", "📡", "🧭", "💎", "🔮", "🪄", "📿", "🧿", "💈", "⚗️", "🔭", "🔬", "🕳️", "💊", "💉", "🩸", "🩹", "🩺", "🩻", "🚪",
                    "🛗", "🪞", "🪟", "🛏️", "🛋️", "🪑", "🚽", "🪠", "🚿", "🛁", "🪤", "🪒", "🧴", "🧷", "🧹", "🧺", "🧻", "🪣", "🧼", "🫧",
                    "🪥", "🧽", "🧯", "🛒", "🚬", "⚰️", "🪦", "⚱️", "🏺", "🎁", "🎈", "🎉", "🎊", "🎏", "🎀", "🧧", "✉️", "📦", "📫"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            EmojiCategory(
                id = "symbols",
                title = "Символы и знаки",
                icon = "🔣",
                emojis = listOf(
                    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
                    "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎",
                    "♏", "♐", "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳", "🈶", "🈚", "🈸", "🈺", "🈷️", "✴️", "VS", "🈴",
                    "💮", "🉐", "㊙️", "㊗️", "🈡", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "🟤", "🔺", "🔻", "💠", "🔘", "🔳", "🔲",
                    "🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️", "💯", "🔠", "🔡", "🔢", "🔣", "🔤", "🅰️", "🆎", "🅱️", "🆑", "🆒", "🆓"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            EmojiCategory(
                id = "flags",
                title = "Флаги",
                icon = "🚩",
                emojis = listOf(
                    "🏳️", "🏴", "🏁", "🚩", "🎌", "🏴‍☠️", "🇷🇺", "🇧🇾", "🇰🇿", "🇺🇿", "🇦🇲", "🇦🇿", "🇬🇪", "🇺🇸", "🇬🇧", "🇩🇪", "🇫🇷", "🇮🇹", "🇪🇸", "🇨🇳",
                    "🇯🇵", "🇰🇷", "🇧🇷", "🇮🇳", "🇨🇦", "🇦🇺", "🇹🇷", "🇦🇪", "🇸🇦", "🇪🇬", "🇮🇱", "🇲🇽", "🇦🇷", "🇿🇦", "🇨🇭", "🇳🇱", "🇸🇪", "🇳🇴", "🇫🇮", "🇵🇱"
                ).map { EmojiItem(it, isCustom = false) }
            ),
            // --- Custom Animated Telegram Packs ---
            EmojiCategory(
                id = "pack_duck",
                title = "Duck Emoji",
                icon = "🦆",
                isCustomPack = true,
                packAuthor = "Telegram Designers",
                packLevelRequired = 0,
                emojis = listOf(
                    EmojiItem("🦆", "Duck Cool", listOf("duck", "утка"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🐥", "Baby Duck", listOf("baby", "утенок"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🦆‍🔥", "Duck Fire", listOf("fire", "огонь"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🪿", "Goose Honk", listOf("goose", "гусь"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🍗", "Duck Snack", listOf("food", "ножка"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🌊", "Duck Lake", listOf("water", "озеро"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🎩", "Duck Gentleman", listOf("gentleman", "цилиндр"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🕶️", "Duck Matrix", listOf("matrix", "очки"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🐣", "Hatching Duck", listOf("egg", "вылупился"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🐤", "Little Chic", listOf("chic", "цыпленок"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🦢", "Swan Glow", listOf("swan", "лебедь"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🪺", "Nest Warm", listOf("nest", "гнездо"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🪽", "Wing Flight", listOf("wing", "крыло"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🤿", "Diving Duck", listOf("dive", "ныряет"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("🎣", "Fisher Duck", listOf("fish", "рыбак"), isCustom = true, packName = "Duck Emoji"),
                    EmojiItem("⭐", "Duck Star", listOf("star", "звезда"), isCustom = true, packName = "Duck Emoji")
                )
            ),
            EmojiCategory(
                id = "pack_crayons",
                title = "Crayons Emoji",
                icon = "🖍️",
                isCustomPack = true,
                packAuthor = "Color Studio",
                packLevelRequired = 0,
                emojis = listOf(
                    EmojiItem("🖍️", "Red Crayon", listOf("crayon", "мелок"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("🎨", "Artist Palette", listOf("art", "палитра"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("🖌️", "Paintbrush", listOf("brush", "кисть"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("✏️", "Pencil Sketch", listOf("pencil", "карандаш"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("✒️", "Fountain Pen", listOf("pen", "перо"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("📝", "Color Memo", listOf("memo", "заметка"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("🌈", "Rainbow Spectrum", listOf("rainbow", "радуга"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("✨", "Color Glitter", listOf("glitter", "блеск"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("🎭", "Drama Masks", listOf("theater", "маски"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("🪄", "Magic Marker", listOf("magic", "магия"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("💎", "Crystal Ink", listOf("gem", "кристалл"), isCustom = true, packName = "Crayons Emoji"),
                    EmojiItem("💫", "Star Draw", listOf("dizzy", "искра"), isCustom = true, packName = "Crayons Emoji")
                )
            ),
            EmojiCategory(
                id = "pack_birthday",
                title = "Birthday Collection",
                icon = "🎂",
                isCustomPack = true,
                packAuthor = "Celebration Art",
                packLevelRequired = 1,
                emojis = listOf(
                    EmojiItem("🎂", "Birthday Cake", listOf("cake", "торт"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🧁", "Cupcake", listOf("cupcake", "кекс"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🎈", "Party Balloon", listOf("balloon", "шарик"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🎁", "Gift Box", listOf("gift", "подарок"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🎉", "Confetti Blast", listOf("confetti", "салют"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🥳", "Party Face", listOf("party", "праздник"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🥂", "Cheers Glasses", listOf("cheers", "тост"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🍾", "Champagne Pop", listOf("champagne", "шампанское"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🕯️", "Candle Glow", listOf("candle", "свеча"), isCustom = true, packName = "Birthday Collection"),
                    EmojiItem("🎊", "Party Ball", listOf("celebrate", "хлопушка"), isCustom = true, packName = "Birthday Collection")
                )
            ),
            EmojiCategory(
                id = "pack_cyber",
                title = "Neon Cyber",
                icon = "👾",
                isCustomPack = true,
                packAuthor = "KuoteX Matrix",
                packLevelRequired = 2,
                emojis = listOf(
                    EmojiItem("👾", "Pixel Cyber", listOf("pixel", "пиксель"), isCustom = true, packName = "Neon Cyber"),
                    EmojiItem("⚡", "Electric Neon", listOf("electric", "молния"), isCustom = true, packName = "Neon Cyber"),
                    EmojiItem("🤖", "Android Bot", listOf("bot", "робот"), isCustom = true, packName = "Neon Cyber"),
                    EmojiItem("🧬", "DNA Neon", listOf("dna", "днк"), isCustom = true, packName = "Neon Cyber"),
                    EmojiItem("🔮", "Holo Orb", listOf("orb", "сфера"), isCustom = true, packName = "Neon Cyber"),
                    EmojiItem("🪫", "Cyber Core Empty", listOf("battery", "батарея"), isCustom = true, packName = "Neon Cyber"),
                    EmojiItem("🛸", "Cyber Ship", listOf("ufo", "корабль"), isCustom = true, packName = "Neon Cyber"),
                    EmojiItem("🌐", "Meta Matrix", listOf("web", "сеть"), isCustom = true, packName = "Neon Cyber")
                )
            )
        )
    }

    fun search(query: String): List<EmojiItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()
        val all = categories.flatMap { it.emojis }
        return all.filter { item ->
            item.emoji.contains(trimmed) ||
            item.name.lowercase().contains(trimmed) ||
            item.keywords.any { it.lowercase().contains(trimmed) } ||
            (item.packName?.lowercase()?.contains(trimmed) == true)
        }.distinctBy { it.emoji }
    }
}

/**
 * TelegramEmojiPicker
 * Full-fledged Telegram-style emoji & animated status picker:
 * - High-speed streaming virtualization (LazyColumn with chunked rows and keyed elements)
 * - Synchronized category tab navigation with smooth scrolling
 * - Real-time search with instant filtering
 * - Interactive physics spring animation for every emoji
 * - Custom animated pack headers with Telegram level/unlock indicators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramEmojiPicker(
    modifier: Modifier = Modifier,
    selectedEmoji: String? = null,
    onEmojiSelected: (EmojiItem) -> Unit,
    onDismiss: (() -> Unit)? = null,
    title: String = "Выбор эмодзи",
    showRecentTab: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember(searchQuery) {
        if (searchQuery.isNotEmpty()) TelegramEmojiData.search(searchQuery) else emptyList()
    }

    val categories = remember { TelegramEmojiData.categories }
    val listState = rememberLazyListState()

    // Active Category tracking
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    // Calculate chunks per category (8 items per row for smooth phone grid)
    val chunkSize = 8

    // Map each category to row chunks
    val categoryRowOffsets = remember(categories, showRecentTab) {
        val offsets = mutableListOf<Int>()
        var currentOffset = 0
        if (showRecentTab && TelegramEmojiData.recentEmojis.isNotEmpty()) {
            offsets.add(currentOffset)
            val recentRows = (TelegramEmojiData.recentEmojis.size + chunkSize - 1) / chunkSize
            currentOffset += 1 + recentRows // header + rows
        }
        categories.forEach { cat ->
            offsets.add(currentOffset)
            val rows = (cat.emojis.size + chunkSize - 1) / chunkSize
            val packHeaderCount = if (cat.isCustomPack) 2 else 1
            currentOffset += packHeaderCount + rows
        }
        offsets
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF161E2E))
    ) {
        // Top Header with Drag Handle & Close
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Search Input Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0E131D),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Поиск эмодзи и стикеров...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    singleLine = true
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Очистить",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Category Tab Bar (Sticky Icons with animated indicator)
        if (searchQuery.isEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121926))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showRecentTab && TelegramEmojiData.recentEmojis.isNotEmpty()) {
                    item {
                        val isSelected = selectedCategoryIndex == 0
                        CategoryTabButton(
                            icon = "🕒",
                            title = "Недавние",
                            isSelected = isSelected,
                            onClick = {
                                selectedCategoryIndex = 0
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        )
                    }
                }

                itemsIndexed(categories) { index, cat ->
                    val actualIndex = if (showRecentTab && TelegramEmojiData.recentEmojis.isNotEmpty()) index + 1 else index
                    val isSelected = selectedCategoryIndex == actualIndex
                    CategoryTabButton(
                        icon = cat.icon,
                        title = cat.title,
                        isSelected = isSelected,
                        isCustomPack = cat.isCustomPack,
                        onClick = {
                            selectedCategoryIndex = actualIndex
                            val targetItemIndex = categoryRowOffsets.getOrElse(actualIndex) { 0 }
                            coroutineScope.launch {
                                listState.animateScrollToItem(targetItemIndex)
                            }
                        }
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        }

        // Content Area: Virtualized Streaming LazyColumn
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
        ) {
            if (searchQuery.isNotEmpty()) {
                // Search Results Grid
                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Эмодзи не найдены",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val searchRows = searchResults.chunked(chunkSize)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        items(searchRows, key = { it.joinToString { item -> item.emoji } }) { rowItems ->
                            EmojiGridRow(
                                items = rowItems,
                                selectedEmoji = selectedEmoji,
                                onSelect = { item ->
                                    TelegramEmojiData.addRecent(item)
                                    onEmojiSelected(item)
                                }
                            )
                        }
                    }
                }
            } else {
                // Full Categorized Streaming List with progressive chunks
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    // 1. Recent Section
                    if (showRecentTab && TelegramEmojiData.recentEmojis.isNotEmpty()) {
                        item(key = "hdr_recent") {
                            CategoryHeader(title = "Недавние эмодзи")
                        }
                        val recentChunks = TelegramEmojiData.recentEmojis.chunked(chunkSize)
                        items(recentChunks, key = { "recent_" + it.joinToString { item -> item.emoji } }) { rowItems ->
                            EmojiGridRow(
                                items = rowItems,
                                selectedEmoji = selectedEmoji,
                                onSelect = { item ->
                                    TelegramEmojiData.addRecent(item)
                                    onEmojiSelected(item)
                                }
                            )
                        }
                    }

                    // 2. All Categories & Custom Packs
                    categories.forEach { category ->
                        item(key = "hdr_${category.id}") {
                            CategoryHeader(
                                title = category.title,
                                isPack = category.isCustomPack,
                                packAuthor = category.packAuthor
                            )
                        }

                        if (category.isCustomPack && category.packLevelRequired > 0) {
                            item(key = "pack_card_${category.id}") {
                                PackPromotionBanner(
                                    packName = category.title,
                                    levelRequired = category.packLevelRequired
                                )
                            }
                        }

                        val chunks = category.emojis.chunked(chunkSize)
                        items(chunks, key = { category.id + "_" + it.joinToString { item -> item.emoji } }) { rowItems ->
                            EmojiGridRow(
                                items = rowItems,
                                selectedEmoji = selectedEmoji,
                                onSelect = { item ->
                                    TelegramEmojiData.addRecent(item)
                                    onEmojiSelected(item)
                                }
                            )
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTabButton(
    icon: String,
    title: String,
    isSelected: Boolean,
    isCustomPack: Boolean = false,
    onClick: () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tab_scale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scaleAnim)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) Color(0xFF2AABEE).copy(alpha = 0.25f)
                else if (isCustomPack) Color.White.copy(alpha = 0.05f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(16.dp)
                    .height(2.dp)
                    .background(Color(0xFF2AABEE), RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun CategoryHeader(title: String, isPack: Boolean = false, packAuthor: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPack) Color(0xFF2AABEE) else Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp
        )
        if (isPack) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF2AABEE).copy(alpha = 0.18f)
            ) {
                Text(
                    text = "PACK",
                    color = Color(0xFF2AABEE),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            if (packAuthor != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "• $packAuthor",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PackPromotionBanner(packName: String, levelRequired: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0E1420),
        border = BorderStroke(1.dp, Color(0xFF9C68FC).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Color(0xFF9C68FC),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Получить $packName",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Требуется $levelRequired уровень буста",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF9C68FC).copy(alpha = 0.25f)
            ) {
                Text(
                    text = "Уровень $levelRequired",
                    color = Color(0xFF9C68FC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmojiGridRow(
    items: List<EmojiItem>,
    selectedEmoji: String?,
    onSelect: (EmojiItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { item ->
            val isSelected = selectedEmoji == item.emoji
            AnimatedEmojiCell(
                item = item,
                isSelected = isSelected,
                onSelect = { onSelect(item) }
            )
        }
        // Fill remaining spaces to keep alignment
        repeat(8 - items.size) {
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

/**
 * Animated individual emoji cell with spring physics scale & pulse animation
 */
@Composable
private fun AnimatedEmojiCell(
    item: EmojiItem,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth interactive spring bounce animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.35f else if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "emoji_bounce"
    )

    // Subtle breathing pulse for custom animated packs
    val infiniteTransition = rememberInfiniteTransition(label = "pack_pulse")
    val ambientPulse by if (item.isCustom) {
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambient_scale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale * ambientPulse)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) Color(0xFF2AABEE).copy(alpha = 0.35f)
                else if (isPressed) Color.White.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.emoji,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Modal Bottom Sheet variant for Telegram Emoji Picker
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramEmojiPickerBottomSheet(
    onDismissRequest: () -> Unit,
    onEmojiSelected: (EmojiItem) -> Unit,
    selectedEmoji: String? = null,
    title: String = "Выбор эмодзи"
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF161E2E),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        TelegramEmojiPicker(
            onEmojiSelected = { emoji ->
                onEmojiSelected(emoji)
                onDismissRequest()
            },
            selectedEmoji = selectedEmoji,
            onDismiss = onDismissRequest,
            title = title
        )
    }
}

