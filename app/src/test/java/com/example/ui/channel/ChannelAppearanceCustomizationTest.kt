package com.example.ui.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelAppearanceCustomizationTest {

    @Test
    fun messageColorSchemesContainSingleTwoAndThreeToneVariants() {
        val singles = TelegramMessageColors.schemes.filter { !it.isTwoTone && !it.isThreeTone }
        val twoTones = TelegramMessageColors.schemes.filter { it.isTwoTone }
        val threeTones = TelegramMessageColors.schemes.filter { it.isThreeTone }

        assertTrue("Expected single-tone schemes", singles.size >= 7)
        assertTrue("Expected two-tone diagonal schemes", twoTones.size >= 6)
        assertTrue("Expected three-tone diamond schemes", threeTones.size >= 6)
        assertEquals(3, threeTones.first().stripeColors.size)
    }

    @Test
    fun profileHeaderColorSchemesProvide16SolidAndTwoToneOptions() {
        assertEquals(16, TelegramProfileHeaderSchemes.schemes.size)
        val solidRow = TelegramProfileHeaderSchemes.schemes.take(8)
        val twoToneRow = TelegramProfileHeaderSchemes.schemes.drop(8)

        assertTrue(solidRow.all { !it.isTwoTone })
        assertTrue(twoToneRow.all { it.isTwoTone })
        assertNull(TelegramProfileHeaderSchemes.getOrNull(null))
        assertNotNull(TelegramProfileHeaderSchemes.getOrNull(10))
    }

    @Test
    fun wallpapersIncludeNoneAndTelegramThemedPresets() {
        val none = TelegramWallpapers.getPreset("none")
        assertEquals("none", none.patternType)

        val chick = TelegramWallpapers.getPreset("easter_chick")
        assertEquals("🐥", chick.centerIcon)

        val diamond = TelegramWallpapers.getPreset("diamond_doodles")
        assertEquals("💎", diamond.centerIcon)
    }

    @Test
    fun updateChannelAppearanceAppliesAllFieldsAndClampsGalleryBlurDim() {
        val chatId = "test_channel_appearance_1"
        ChannelCustomizationManager.updateChannelAppearance(
            chatId = chatId,
            messageColorId = 14,
            messageEmojiPattern = "🔥",
            profileHeaderColorId = 11,
            profileEmojiPattern = "⚡",
            emojiStatus = "💎",
            wallpaperId = "custom_gallery",
            customWallpaperUri = "file:///data/user/0/com.example/files/channel_wallpapers/wp_test.jpg",
            wallpaperBlur = 32f,
            wallpaperDim = 0.95f
        )

        val updated = ChannelCustomizationManager.getCustomization(chatId)
        assertEquals(14, updated.messageColorId)
        assertEquals("🔥", updated.messageEmojiPattern)
        assertEquals(11, updated.profileHeaderColorId)
        assertEquals("⚡", updated.profileEmojiPattern)
        assertEquals("💎", updated.emojiStatus)
        assertEquals("custom_gallery", updated.chatWallpaperId)
        assertNotNull(updated.wallpaperCustomUri)
        assertEquals(24f, updated.wallpaperBlur, 0.01f)
        assertEquals(0.85f, updated.wallpaperDim, 0.01f)
        assertTrue(updated.activeMessageScheme.isThreeTone)
        assertNotNull(updated.activeProfileHeaderScheme)
    }

    @Test
    fun resetProfileColorClearsOnlyProfileHeaderWhileKeepingMessageColorsAndWallpaper() {
        val chatId = "test_channel_appearance_2"
        ChannelCustomizationManager.updateChannelAppearance(
            chatId = chatId,
            messageColorId = 13,
            messageEmojiPattern = "🌟",
            profileHeaderColorId = 9,
            profileEmojiPattern = "🦆",
            emojiStatus = "👑",
            wallpaperId = "winter_snowman",
            customWallpaperUri = null,
            wallpaperBlur = 0f,
            wallpaperDim = 0.2f
        )

        ChannelCustomizationManager.resetProfileColor(chatId)
        val afterReset = ChannelCustomizationManager.getCustomization(chatId)

        assertNull(afterReset.profileHeaderColorId)
        assertNull(afterReset.profileEmojiPattern)
        assertNull(afterReset.activeProfileHeaderScheme)
        assertEquals(13, afterReset.messageColorId)
        assertEquals("🌟", afterReset.messageEmojiPattern)
        assertEquals("winter_snowman", afterReset.chatWallpaperId)
        assertEquals("👑", afterReset.emojiStatus)
    }

    @Test
    fun switchingFromGalleryWallpaperToNoneClearsCustomUriAndAllowsClearingStatus() {
        val chatId = "test_channel_appearance_3"
        ChannelCustomizationManager.updateChannelAppearance(
            chatId = chatId,
            messageColorId = 2,
            messageEmojiPattern = null,
            profileHeaderColorId = 2,
            profileEmojiPattern = null,
            emojiStatus = null,
            wallpaperId = "none",
            customWallpaperUri = "file:///should_be_cleared.jpg",
            wallpaperBlur = 5f,
            wallpaperDim = 0.4f
        )

        val state = ChannelCustomizationManager.getCustomization(chatId)
        assertEquals("none", state.chatWallpaperId)
        assertNull(state.wallpaperCustomUri)
        assertNull(state.emojiStatus)
    }
}
