package com.example.ui

import com.example.ui.components.EmojiItem
import com.example.ui.components.TelegramEmojiData
import com.example.ui.media.EmojiHintMode
import com.example.ui.media.GifSearchRepository
import com.example.ui.media.MediaExpressionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaExpressionManagerTest {

    @Before
    fun setUp() {
        MediaExpressionManager.resetForTests()
    }

    @Test
    fun `picking emoji updates shared recent emojis across inline panel and TelegramEmojiData`() {
        val picked = EmojiItem("🦄", "Единорог", listOf("единорог", "unicorn"))
        MediaExpressionManager.recordUsedEmoji(picked)

        val panelRecents = MediaExpressionManager.recentEmojis.value
        assertEquals("🦄", panelRecents.first().emoji)
        assertEquals("🦄", TelegramEmojiData.recentEmojis.first().emoji)

        // Repeated use deduplicates rather than duplicating
        MediaExpressionManager.recordUsedEmoji(EmojiItem("🔥", "Огонь"))
        MediaExpressionManager.recordUsedEmoji(picked)
        assertEquals(1, MediaExpressionManager.recentEmojis.value.count { it.emoji == "🦄" })
        assertEquals("🦄", MediaExpressionManager.recentEmojis.value.first().emoji)
    }

    @Test
    fun `manual drag reorder moves sticker pack and disables recent first sorting`() {
        val before = MediaExpressionManager.getActiveStickerPacks().map { it.id }
        assertTrue(before.size >= 3)
        assertTrue(MediaExpressionManager.recentFirstEnabled.value)

        val secondId = before[1]
        MediaExpressionManager.moveActivePack(1, 0)

        val after = MediaExpressionManager.getActiveStickerPacks().map { it.id }
        assertEquals(secondId, after.first())
        assertFalse(MediaExpressionManager.recentFirstEnabled.value)
        assertTrue(MediaExpressionManager.recentSortDisabledNotice.value)
    }

    @Test
    fun `archive unarchive delete and trending pack install update active list`() {
        val initialActive = MediaExpressionManager.getActiveStickerPacks().map { it.id }
        assertTrue("joker" in initialActive)

        MediaExpressionManager.archiveStickerPacks(setOf("joker"))
        assertFalse("joker" in MediaExpressionManager.getActiveStickerPacks().map { it.id })
        assertTrue("joker" in MediaExpressionManager.getArchivedStickerPacks().map { it.id })

        MediaExpressionManager.unarchiveStickerPack("joker")
        assertTrue("joker" in MediaExpressionManager.getActiveStickerPacks().map { it.id })

        MediaExpressionManager.toggleInstallTrendingPack("duck")
        assertTrue("duck" in MediaExpressionManager.getActiveStickerPacks().map { it.id })

        MediaExpressionManager.deleteStickerPacks(setOf("duck"))
        assertFalse("duck" in MediaExpressionManager.getActiveStickerPacks().map { it.id })
    }

    @Test
    fun `createCustomSticker adds sticker to Joker pack and builds parseable message payload`() {
        val created = MediaExpressionManager.createCustomSticker(
            emoji = "⚡",
            label = "Молния",
            caption = "Бум!"
        )
        val joker = MediaExpressionManager.stickerPacks.value.first { it.id == "joker" }
        assertEquals(created.id, joker.stickers.first().id)

        val doc = MediaExpressionManager.buildStickerDocumentData("joker", created)
        val msg = Message(
            id = "m1",
            chatId = "g1",
            senderId = "me",
            text = created.emoji,
            timestamp = 1000L,
            mediaPath = created.id,
            mediaType = "sticker",
            documentData = doc
        )
        val parsed = MediaExpressionManager.parseStickerFromMessage(msg)
        assertNotNull(parsed)
        assertEquals("⚡", parsed?.emoji)
        assertEquals("Бум!", parsed?.caption)
    }

    @Test
    fun `emoji hint suggestions respect hint mode`() {
        MediaExpressionManager.setEmojiHintMode(EmojiHintMode.ALL)
        val matchesAll = MediaExpressionManager.getSuggestedStickersForInput("🔥")
        assertTrue(matchesAll.isNotEmpty())

        MediaExpressionManager.setEmojiHintMode(EmojiHintMode.NONE)
        val matchesNone = MediaExpressionManager.getSuggestedStickersForInput("🔥")
        assertTrue(matchesNone.isEmpty())
    }

    @Test
    fun `countPureEmojis handles 1 to 3 pure emojis and ignores mixed text`() {
        assertEquals(1, MediaExpressionManager.countPureEmojis("🔥"))
        assertEquals(2, MediaExpressionManager.countPureEmojis("🔥❤️"))
        assertEquals(3, MediaExpressionManager.countPureEmojis("🎉😎👍"))
        assertEquals(0, MediaExpressionManager.countPureEmojis("🎉😎👍🚀"))
        assertEquals(0, MediaExpressionManager.countPureEmojis("Привет 🔥"))
    }

    @Test
    fun `gif catalog filtering matches mood emojis and russian search tags`() {
        val partyGifs = GifSearchRepository.filterLocalCatalog("праздник")
        assertTrue(partyGifs.isNotEmpty())

        val heartMoodGifs = GifSearchRepository.filterLocalCatalog("", moodEmoji = "❤️")
        assertTrue(heartMoodGifs.isNotEmpty())
        assertTrue(heartMoodGifs.all { it.moodEmoji == "❤️" })
    }
}
