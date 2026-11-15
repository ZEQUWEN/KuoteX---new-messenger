package com.example.ui

import com.example.ui.channel.ChannelCustomizationManager
import com.example.ui.channel.canCurrentUserManageAdminRights
import com.example.ui.channel.hasFullChannelAdminRights
import com.example.ui.channel.isChannelAdminOrCreator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelAdminRoleTest {

    private val activeUser = UserAccount(
        id = "123456789",
        username = "@neo",
        displayName = "Neo",
        profilePicUrl = "",
        isActive = true
    )

    @Test
    fun creatorRoleGrantsAdminAccess() {
        val members = listOf(
            GroupMember(chatId = "c2", userId = "123456789", userName = "Neo", isAdmin = true, role = "creator"),
            GroupMember(chatId = "c2", userId = "u11", userName = "Alice", isAdmin = false, role = "subscriber")
        )
        assertTrue(isChannelAdminOrCreator(members, activeUser))
        assertTrue(canCurrentUserManageAdminRights(members, activeUser))
    }

    @Test
    fun adminRoleGrantsAdminAccess() {
        val members = listOf(
            GroupMember(chatId = "c5", userId = "u10", userName = "Pavel Durov", isAdmin = true, role = "creator"),
            GroupMember(chatId = "c5", userId = "123456789", userName = "Neo", isAdmin = true, role = "admin")
        )
        assertTrue(isChannelAdminOrCreator(members, activeUser))
    }

    @Test
    fun onlyCreatorOrFullRightsAdminCanManageOtherAdmins() {
        val fullRightsAdmin = GroupMember(
            chatId = "c5",
            userId = "123456789",
            userName = "Neo",
            isAdmin = true,
            role = "admin",
            canPostMessages = true,
            canEditMessages = true,
            canDeleteMessages = true,
            canManageStreams = true,
            canAddAdmins = true,
            canChangeChannelInfo = true,
            canPostStories = true,
            canEditStories = true,
            canDeleteStories = true,
            canAccessDirectMessages = true,
            canManageGreetings = true,
            canManageInvites = true,
            canBanUsers = true
        )
        assertTrue(hasFullChannelAdminRights(fullRightsAdmin))
        assertTrue(canCurrentUserManageAdminRights(listOf(fullRightsAdmin), activeUser))

        // Partial admin missing story deletion or ban permission cannot configure admin rights
        val partialAdmin = fullRightsAdmin.copy(canBanUsers = false)
        assertFalse(hasFullChannelAdminRights(partialAdmin))
        assertFalse(canCurrentUserManageAdminRights(listOf(partialAdmin), activeUser))

        // Partial admin with only canAddAdmins=true but missing other creator powers is denied
        val addOnlyAdmin = GroupMember(
            chatId = "c5",
            userId = "123456789",
            userName = "Neo",
            isAdmin = true,
            role = "admin",
            canAddAdmins = true
        )
        assertFalse(hasFullChannelAdminRights(addOnlyAdmin))
        assertFalse(canCurrentUserManageAdminRights(listOf(addOnlyAdmin), activeUser))
    }

    @Test
    fun subscriberAndBotAreStrictlyDeniedAdminAccess() {
        val subscriberMembers = listOf(
            GroupMember(chatId = "c5", userId = "u10", userName = "Pavel Durov", isAdmin = true, role = "creator"),
            GroupMember(chatId = "c5", userId = "123456789", userName = "Neo", isAdmin = false, role = "subscriber")
        )
        assertFalse(isChannelAdminOrCreator(subscriberMembers, activeUser))
        assertFalse(canCurrentUserManageAdminRights(subscriberMembers, activeUser))

        val botMembers = listOf(
            GroupMember(chatId = "c5", userId = "123456789", userName = "Neo", isAdmin = false, role = "bot")
        )
        assertFalse(isChannelAdminOrCreator(botMembers, activeUser))
        assertFalse(canCurrentUserManageAdminRights(botMembers, activeUser))
        assertFalse(isChannelAdminOrCreator(emptyList(), activeUser))
        assertFalse(isChannelAdminOrCreator(subscriberMembers, null))
        assertFalse(canCurrentUserManageAdminRights(subscriberMembers, null))
    }

    @Test
    fun customizationManagerSupportsDiscussionBlacklistInvitesAndReactions() {
        val testChatId = "test_channel_admin_flow"
        ChannelCustomizationManager.initializeCreatedChannel(
            chatId = testChatId,
            description = "Test Desc",
            isPublic = true,
            inviteLink = "t.me/testchannel",
            avatarUrl = null
        )

        ChannelCustomizationManager.linkDiscussionChat(testChatId, "g_disc", "Test Discussion")
        assertEquals("Test Discussion", ChannelCustomizationManager.getCustomization(testChatId).discussionChatTitle)
        assertEquals("g_disc", ChannelCustomizationManager.getCustomization(testChatId).discussionChatId)

        ChannelCustomizationManager.unlinkDiscussionChat(testChatId)
        assertEquals("", ChannelCustomizationManager.getCustomization(testChatId).discussionChatTitle)
        assertNull(ChannelCustomizationManager.getCustomization(testChatId).discussionChatId)

        ChannelCustomizationManager.banUser(testChatId, "u99", "Spammer", "Спам")
        assertEquals(1, ChannelCustomizationManager.getCustomization(testChatId).blacklistedUsers.size)
        ChannelCustomizationManager.unbanUser(testChatId, "u99")
        assertTrue(ChannelCustomizationManager.getCustomization(testChatId).blacklistedUsers.isEmpty())

        val link = ChannelCustomizationManager.createInviteLink(testChatId, "Promo")
        assertEquals(1, ChannelCustomizationManager.getCustomization(testChatId).additionalInviteLinks.size)
        ChannelCustomizationManager.revokeInviteLink(testChatId, link.id)
        assertTrue(ChannelCustomizationManager.getCustomization(testChatId).additionalInviteLinks.isEmpty())

        ChannelCustomizationManager.updateReactionsConfig(testChatId, true, listOf("🔥", "❤️"), 2, false)
        val reactionsUpdated = ChannelCustomizationManager.getCustomization(testChatId)
        assertEquals(2, reactionsUpdated.maxReactionsPerPost)
        assertFalse(reactionsUpdated.paidStarReactionsEnabled)
        assertEquals(listOf("🔥", "❤️"), reactionsUpdated.availableReactions)
    }
}
