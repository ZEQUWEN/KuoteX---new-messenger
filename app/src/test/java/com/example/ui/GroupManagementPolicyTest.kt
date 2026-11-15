package com.example.ui

import com.example.ui.channel.ChannelCustomization
import com.example.ui.group.GroupInviteLinkEntity
import com.example.ui.group.GroupInvitePrivacy
import com.example.ui.group.canAddUserDirectlyToGroup
import com.example.ui.group.canSeePrivateGroupBadge
import com.example.ui.group.formatGroupSubtitle
import com.example.ui.group.formatParticipantCount
import com.example.ui.group.formatSubscriberCount
import com.example.ui.group.resolveAvailableSendAsIdentities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupManagementPolicyTest {

    private val activeUser = UserAccount(
        id = "u_me",
        username = "@neo",
        displayName = "Neo",
        profilePicUrl = "",
        isActive = true,
        customStatus = "⚡"
    )

    @Test
    fun privateGroupBadgeVisibleOnlyToCreatorAndCoOwnerAdmin() {
        val creator = GroupMember(
            chatId = "g1",
            userId = "u_me",
            userName = "Neo",
            isAdmin = true,
            role = "creator"
        )
        assertTrue(canSeePrivateGroupBadge(creator))

        val coOwnerAdmin = GroupMember(
            chatId = "g1",
            userId = "u_co",
            userName = "CoOwner",
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
        assertTrue(canSeePrivateGroupBadge(coOwnerAdmin))

        val partialAdmin = coOwnerAdmin.copy(canAddAdmins = false)
        assertFalse(canSeePrivateGroupBadge(partialAdmin))

        val regularMember = GroupMember(
            chatId = "g1",
            userId = "u_sub",
            userName = "Member",
            isAdmin = false,
            role = "subscriber"
        )
        assertFalse(canSeePrivateGroupBadge(regularMember))
        assertFalse(canSeePrivateGroupBadge(null))
    }

    @Test
    fun groupSubtitleShowsOnlyGroupTypeForOwnersAndOnlyCountForRegularAdminsAndMembers() {
        val creator = GroupMember(
            chatId = "g1",
            userId = "u_me",
            userName = "Neo",
            isAdmin = true,
            role = "creator"
        )
        val coOwnerAdmin = GroupMember(
            chatId = "g1",
            userId = "u_co",
            userName = "CoOwner",
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
        val partialAdmin = coOwnerAdmin.copy(canAddAdmins = false)
        val regularMember = GroupMember(
            chatId = "g1",
            userId = "u_sub",
            userName = "Member",
            isAdmin = false,
            role = "subscriber"
        )

        // Creator & co-owner with all rights see only group type without member count
        assertEquals("частная группа", formatGroupSubtitle(creator, isPublic = false, memberCount = 12))
        assertEquals("публичная группа", formatGroupSubtitle(creator, isPublic = true, memberCount = 12))
        assertEquals("частная группа", formatGroupSubtitle(coOwnerAdmin, isPublic = false, memberCount = 5))
        assertEquals("публичная группа", formatGroupSubtitle(coOwnerAdmin, isPublic = true, memberCount = 5))

        // Partial admin & regular member see only member count without group type
        assertEquals("12 участников", formatGroupSubtitle(partialAdmin, isPublic = false, memberCount = 12))
        assertEquals("12 участников", formatGroupSubtitle(partialAdmin, isPublic = true, memberCount = 12))
        assertEquals("1 участник", formatGroupSubtitle(regularMember, isPublic = false, memberCount = 1))
        assertEquals("2 участника", formatGroupSubtitle(regularMember, isPublic = true, memberCount = 2))
        assertEquals("5 участников", formatGroupSubtitle(null, isPublic = false, memberCount = 5))
    }

    @Test
    fun addUserDirectlyRespectsPrivacyRules() {
        assertTrue(
            canAddUserDirectlyToGroup(
                targetUserId = "u2",
                targetPrivacy = GroupInvitePrivacy.EVERYBODY,
                inviterUserId = "u1",
                inviterIsInTargetContacts = false
            )
        )
        assertTrue(
            canAddUserDirectlyToGroup(
                targetUserId = "u2",
                targetPrivacy = GroupInvitePrivacy.MY_CONTACTS,
                inviterUserId = "u1",
                inviterIsInTargetContacts = true
            )
        )
        assertFalse(
            canAddUserDirectlyToGroup(
                targetUserId = "u2",
                targetPrivacy = GroupInvitePrivacy.MY_CONTACTS,
                inviterUserId = "u1",
                inviterIsInTargetContacts = false
            )
        )
        assertFalse(
            canAddUserDirectlyToGroup(
                targetUserId = "u2",
                targetPrivacy = GroupInvitePrivacy.NOBODY,
                inviterUserId = "u1",
                inviterIsInTargetContacts = true
            )
        )
        assertTrue(
            canAddUserDirectlyToGroup(
                targetUserId = "u1",
                targetPrivacy = GroupInvitePrivacy.NOBODY,
                inviterUserId = "u1",
                inviterIsInTargetContacts = false
            )
        )
    }

    @Test
    fun inviteLinkTracksRevokedExpiredAndExhaustedStatesWithoutDeletion() {
        val baseTime = 1_700_000_000_000L
        val activeLink = GroupInviteLinkEntity(
            id = "l1",
            chatId = "g1",
            url = "t.me/+abc123",
            createdByUserId = "u_me",
            createdAt = baseTime,
            expiresAt = baseTime + 3_600_000L,
            maxUses = 10,
            usageCount = 3
        )
        assertTrue(activeLink.isActive(baseTime + 1_000L))
        assertFalse(activeLink.isExpired(baseTime + 1_000L))
        assertFalse(activeLink.isExhausted())
        assertNull(activeLink.inactiveReason(baseTime + 1_000L))

        val expiredLink = activeLink.copy(id = "l2")
        assertTrue(expiredLink.isExpired(baseTime + 3_600_000L))
        assertFalse(expiredLink.isActive(baseTime + 3_600_000L))
        assertEquals("Срок действия истёк", expiredLink.inactiveReason(baseTime + 3_600_000L))

        val exhaustedLink = activeLink.copy(id = "l3", usageCount = 10)
        assertTrue(exhaustedLink.isExhausted())
        assertFalse(exhaustedLink.isActive(baseTime + 1_000L))
        assertEquals("Лимит использований исчерпан", exhaustedLink.inactiveReason(baseTime + 1_000L))

        val revokedLink = activeLink.copy(id = "l4", isRevoked = true)
        assertFalse(revokedLink.isActive(baseTime + 1_000L))
        assertEquals("Ссылка отозвана", revokedLink.inactiveReason(baseTime + 1_000L))
    }

    @Test
    fun sendAsIdentitiesAvailableInAllGroupsWhenUserOwnsChannelsOrIsAdmin() {
        val groupChat = Chat(id = "g_any", title = "Public Devs", isGroup = true, lastMessage = "")
        val ownedChannel = Chat(id = "c_own", title = "KuoteX Officiall", isChannel = true, lastMessage = "")
        val foreignChannel = Chat(id = "c_other", title = "Other Channel", isChannel = true, lastMessage = "")

        val membersOrdinaryWithChannel = mapOf(
            "g_any" to listOf(
                GroupMember(chatId = "g_any", userId = "u_me", userName = "Neo", isAdmin = false, role = "subscriber")
            ),
            "c_own" to listOf(
                GroupMember(chatId = "c_own", userId = "u_me", userName = "Neo", isAdmin = true, role = "creator")
            ),
            "c_other" to listOf(
                GroupMember(chatId = "c_other", userId = "u_other", userName = "Other", isAdmin = true, role = "creator"),
                GroupMember(chatId = "c_other", userId = "u_me", userName = "Neo", isAdmin = false, role = "subscriber")
            )
        )
        val customizations = mapOf(
            "c_own" to ChannelCustomization(chatId = "c_own", emojiStatus = "🔋", subscriberCount = 240, isPublic = true)
        )

        val identitiesWithChannel = resolveAvailableSendAsIdentities(
            groupChat = groupChat,
            activeAccount = activeUser,
            allChats = listOf(groupChat, ownedChannel, foreignChannel),
            allMembersByChat = membersOrdinaryWithChannel,
            customizations = customizations
        )
        assertEquals(listOf("PERSONAL", "CHANNEL"), identitiesWithChannel.map { it.type })
        assertEquals("Neo", identitiesWithChannel[0].title)
        assertEquals("KuoteX Officiall", identitiesWithChannel[1].title)
        assertEquals("🔋", identitiesWithChannel[1].emojiStatus)
        assertEquals("240 подписчиков", identitiesWithChannel[1].subtitle)

        val ordinaryNoChannels = resolveAvailableSendAsIdentities(
            groupChat = groupChat,
            activeAccount = activeUser,
            allChats = listOf(groupChat, foreignChannel),
            allMembersByChat = membersOrdinaryWithChannel,
            customizations = emptyMap()
        )
        assertTrue(ordinaryNoChannels.isEmpty())

        val adminInGroupMembers = mapOf(
            "g_any" to listOf(
                GroupMember(chatId = "g_any", userId = "u_me", userName = "Neo", isAdmin = true, role = "creator")
            )
        )
        val adminIdentities = resolveAvailableSendAsIdentities(
            groupChat = groupChat,
            activeAccount = activeUser,
            allChats = listOf(groupChat),
            allMembersByChat = adminInGroupMembers,
            customizations = emptyMap()
        )
        assertEquals(listOf("PERSONAL", "GROUP"), adminIdentities.map { it.type })
        assertEquals("Владелец", adminIdentities[0].customTag)
    }

    @Test
    fun russianParticipantAndSubscriberPluralizationIsAccurate() {
        assertEquals("1 участник", formatParticipantCount(1))
        assertEquals("2 участника", formatParticipantCount(2))
        assertEquals("5 участников", formatParticipantCount(5))
        assertEquals("11 участников", formatParticipantCount(11))
        assertEquals("21 участник", formatParticipantCount(21))
        assertEquals("24 участника", formatParticipantCount(24))

        assertEquals("1 подписчик", formatSubscriberCount(1))
        assertEquals("2 подписчика", formatSubscriberCount(2))
        assertEquals("5 подписчиков", formatSubscriberCount(5))
        assertEquals("12 подписчиков", formatSubscriberCount(12))
    }
}
