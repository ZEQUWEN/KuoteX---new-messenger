package com.example.ui

import com.example.ui.channel.ChannelCustomizationManager
import com.example.ui.channel.validateDiscussionGroupLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelDiscussionLinkTest {

    private val currentUser = UserAccount(
        id = "user_creator_1",
        username = "@neo",
        displayName = "Neo",
        profilePicUrl = "",
        isActive = true
    )

    private val otherUser = UserAccount(
        id = "user_other_2",
        username = "@trinity",
        displayName = "Trinity",
        profilePicUrl = "",
        isActive = false
    )

    private val myChannel = Chat(
        id = "channel_my_1",
        title = "KuoteX Official",
        isChannel = true,
        isGroup = false,
        lastMessage = ""
    )

    private val myOwnGroup = Chat(
        id = "group_my_1",
        title = "KuoteX chat✨",
        isChannel = false,
        isGroup = true,
        lastMessage = ""
    )

    private val foreignGroup = Chat(
        id = "group_foreign_1",
        title = "OpenAI Developers",
        isChannel = false,
        isGroup = true,
        lastMessage = ""
    )

    @Test
    fun linkingOwnCreatedGroupSucceedsWhenUserIsChannelCreator() {
        val channelMembers = listOf(
            GroupMember(chatId = myChannel.id, userId = currentUser.id, userName = "Neo", isAdmin = true, role = "creator")
        )
        val groupMembers = listOf(
            GroupMember(chatId = myOwnGroup.id, userId = currentUser.id, userName = "Neo", isAdmin = true, role = "creator")
        )

        val result = validateDiscussionGroupLink(
            channelChat = myChannel,
            channelMembers = channelMembers,
            targetGroupChat = myOwnGroup,
            targetGroupMembers = groupMembers,
            activeAccount = currentUser
        )

        assertTrue(result.allowed)
        assertNull(result.errorMessage)
    }

    @Test
    fun linkingOwnCreatedGroupSucceedsForChannelAdminWithChangeInfoRight() {
        val channelMembers = listOf(
            GroupMember(chatId = myChannel.id, userId = otherUser.id, userName = "Trinity", isAdmin = true, role = "creator"),
            GroupMember(
                chatId = myChannel.id,
                userId = currentUser.id,
                userName = "Neo",
                isAdmin = true,
                role = "admin",
                canChangeChannelInfo = true
            )
        )
        val groupMembers = listOf(
            GroupMember(chatId = myOwnGroup.id, userId = currentUser.id, userName = "Neo", isAdmin = true, role = "creator")
        )

        val result = validateDiscussionGroupLink(
            channelChat = myChannel,
            channelMembers = channelMembers,
            targetGroupChat = myOwnGroup,
            targetGroupMembers = groupMembers,
            activeAccount = currentUser
        )

        assertTrue(result.allowed)
    }

    @Test
    fun linkingForeignGroupIsRejectedEvenIfUserIsAdminOrSubscriberInForeignGroup() {
        val channelMembers = listOf(
            GroupMember(chatId = myChannel.id, userId = currentUser.id, userName = "Neo", isAdmin = true, role = "creator")
        )
        val foreignGroupMembers = listOf(
            GroupMember(chatId = foreignGroup.id, userId = otherUser.id, userName = "Trinity", isAdmin = true, role = "creator"),
            // Current user is merely an admin (not creator!) in the foreign group
            GroupMember(
                chatId = foreignGroup.id,
                userId = currentUser.id,
                userName = "Neo",
                isAdmin = true,
                role = "admin",
                canChangeChannelInfo = true
            )
        )

        val result = validateDiscussionGroupLink(
            channelChat = myChannel,
            channelMembers = channelMembers,
            targetGroupChat = foreignGroup,
            targetGroupMembers = foreignGroupMembers,
            activeAccount = currentUser
        )

        assertFalse(result.allowed)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("чужую группу"))
    }

    @Test
    fun linkingEmptyOrOwnerlessGroupIsRejected() {
        val channelMembers = listOf(
            GroupMember(chatId = myChannel.id, userId = currentUser.id, userName = "Neo", isAdmin = true, role = "creator")
        )

        val result = validateDiscussionGroupLink(
            channelChat = myChannel,
            channelMembers = channelMembers,
            targetGroupChat = foreignGroup,
            targetGroupMembers = emptyList(),
            activeAccount = currentUser
        )

        assertFalse(result.allowed)
    }

    @Test
    fun subscriberOrUnauthorizedAdminCannotLinkEvenOwnGroup() {
        val channelMembers = listOf(
            GroupMember(chatId = myChannel.id, userId = otherUser.id, userName = "Trinity", isAdmin = true, role = "creator"),
            GroupMember(chatId = myChannel.id, userId = currentUser.id, userName = "Neo", isAdmin = false, role = "subscriber")
        )
        val groupMembers = listOf(
            GroupMember(chatId = myOwnGroup.id, userId = currentUser.id, userName = "Neo", isAdmin = true, role = "creator")
        )

        val result = validateDiscussionGroupLink(
            channelChat = myChannel,
            channelMembers = channelMembers,
            targetGroupChat = myOwnGroup,
            targetGroupMembers = groupMembers,
            activeAccount = currentUser
        )

        assertFalse(result.allowed)
    }

    @Test
    fun customizationManagerTracksLinkedChannelAndDiscussionSettings() {
        val channelId = "test_ch_disc_link_1"
        val groupId = "test_grp_disc_link_1"

        ChannelCustomizationManager.initializeCreatedChannel(
            chatId = channelId,
            description = "Official channel",
            isPublic = true,
            inviteLink = "t.me/kuotex_official",
            avatarUrl = null
        )

        assertEquals("", ChannelCustomizationManager.getCustomization(channelId).discussionChatTitle)
        assertNull(ChannelCustomizationManager.getCustomization(channelId).discussionChatId)

        ChannelCustomizationManager.linkDiscussionChat(channelId, groupId, "KuoteX chat✨")
        ChannelCustomizationManager.updateDiscussionSettings(
            chatId = channelId,
            onlyMembersCanSend = true,
            joinRequestsRequired = true
        )

        val custom = ChannelCustomizationManager.getCustomization(channelId)
        assertEquals(groupId, custom.discussionChatId)
        assertEquals("KuoteX chat✨", custom.discussionChatTitle)
        assertTrue(custom.discussionOnlyMembersCanSend)
        assertTrue(custom.discussionJoinRequestsRequired)

        val reverseLookup = ChannelCustomizationManager.getLinkedChannelForGroup(groupId)
        assertNotNull(reverseLookup)
        assertEquals(channelId, reverseLookup?.chatId)

        ChannelCustomizationManager.unlinkDiscussionChat(channelId)
        val afterUnlink = ChannelCustomizationManager.getCustomization(channelId)
        assertNull(afterUnlink.discussionChatId)
        assertEquals("", afterUnlink.discussionChatTitle)
        assertFalse(afterUnlink.discussionOnlyMembersCanSend)
        assertFalse(afterUnlink.discussionJoinRequestsRequired)
        assertNull(ChannelCustomizationManager.getLinkedChannelForGroup(groupId))
    }
}
