package com.example.api

/**
 * Менеджер для стикеров и анимированных эмодзи.
 * Поддерживает форматы Lottie (TGS) и векторные (WEBM).
 * Референс: https://core.telegram.org/stickers
 */
class StickerManager {

    /**
     * Представляет набор стикеров.
     */
    data class StickerSet(
        val id: Long,
        val title: String,
        val shortName: String,
        val isAnimated: Boolean,
        val isVideo: Boolean,
        val stickers: List<Sticker>
    )

    /**
     * Представляет отдельный стикер.
     */
    data class Sticker(
        val fileId: String,
        val width: Int,
        val height: Int,
        val emoji: String,
        val format: StickerFormat
    )

    enum class StickerFormat {
        WEBP, TGS, WEBM
    }

    /**
     * Симуляция получения стикерпака по его короткому имени.
     */
    fun getStickerSet(shortName: String): StickerSet {
        println("StickerManager: Fetching sticker set $shortName")
        return StickerSet(
            id = 123456L,
            title = "Test Stickers",
            shortName = shortName,
            isAnimated = true,
            isVideo = false,
            stickers = listOf(
                Sticker("file_1", 512, 512, "😀", StickerFormat.TGS)
            )
        )
    }
}
