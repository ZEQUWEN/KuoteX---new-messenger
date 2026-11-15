package com.example.ui.group

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ui.Chat
import com.example.ui.GroupMember
import com.example.ui.UserAccount
import com.example.ui.channel.ChannelCustomization
import com.example.ui.channel.hasAllChannelPermissions

enum class GroupInvitePrivacy(val code: String, val label: String) {
    EVERYBODY("EVERYBODY", "Все"),
    MY_CONTACTS("MY_CONTACTS", "Мои контакты"),
    NOBODY("NOBODY", "Никто");

    companion object {
        fun fromCode(code: String?): GroupInvitePrivacy {
            return entries.find { it.code == code } ?: EVERYBODY
        }
    }
}

@Entity(
    tableName = "group_invite_links",
    indices = [Index("chatId"), Index("url")]
)
data class GroupInviteLinkEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val url: String,
    val label: String = "",
    val createdByUserId: String,
    val createdByName: String = "Владелец",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val maxUses: Int? = null,
    val usageCount: Int = 0,
    val requiresApproval: Boolean = false,
    val isPrimary: Boolean = false,
    val isRevoked: Boolean = false
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return expiresAt != null && now >= expiresAt
    }

    fun isExhausted(): Boolean {
        return maxUses != null && usageCount >= maxUses
    }

    fun isActive(now: Long = System.currentTimeMillis()): Boolean {
        return !isRevoked && !isExpired(now) && !isExhausted()
    }

    fun inactiveReason(now: Long = System.currentTimeMillis()): String? {
        return when {
            isRevoked -> "Ссылка отозвана"
            isExpired(now) -> "Срок действия истёк"
            isExhausted() -> "Лимит использований исчерпан"
            else -> null
        }
    }
}

@Entity(
    tableName = "group_join_requests",
    indices = [Index("chatId"), Index("userId")]
)
data class GroupJoinRequestEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val userId: String,
    val userName: String,
    val userAvatarUrl: String = "",
    val inviteLinkId: String? = null,
    val inviteLinkLabel: String = "",
    val status: String = STATUS_PENDING, // "PENDING", "APPROVED", "DECLINED"
    val requestedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_DECLINED = "DECLINED"
    }
}

@Entity(tableName = "user_privacy_rules")
data class UserPrivacyRuleEntity(
    @PrimaryKey val userId: String,
    val groupInvitePrivacy: String = GroupInvitePrivacy.EVERYBODY.code,
    val updatedAt: Long = System.currentTimeMillis()
)

data class InvitableUser(
    val userId: String,
    val displayName: String,
    val username: String = "",
    val avatarUrl: String? = null,
    val statusText: String = "был(а) недавно",
    val isContact: Boolean = false
)

data class GroupAddMembersOutcome(
    val addedUsers: List<InvitableUser>,
    val privacyBlockedUsers: List<InvitableUser>
)

data class SendAsIdentity(
    val id: String,
    val type: String, // "PERSONAL", "CHANNEL", "GROUP"
    val title: String,
    val subtitle: String,
    val avatarUrl: String? = null,
    val emojiStatus: String? = null,
    val isPrivate: Boolean = false,
    val customTag: String? = null
)

data class InvitePreviewResolution(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val chat: Chat? = null,
    val link: GroupInviteLinkEntity? = null,
    val memberCount: Int = 0,
    val requiresApproval: Boolean = false,
    val isAlreadyMember: Boolean = false,
    val hasPendingRequest: Boolean = false,
    val canSeePrivateBadge: Boolean = false,
    val isPublic: Boolean = false,
    val avatarUrl: String? = null
)

/**
 * Only the group creator (`role == "creator"`) or a co-owner administrator who possesses
 * every administrative permission (`hasAllChannelPermissions()`) is allowed to see the
 * "частная группа" badge. All other participants and partial admins must see only the
 * live actual participant count.
 */
fun canSeePrivateGroupBadge(member: GroupMember?): Boolean {
    if (member == null) return false
    if (member.role == "creator") return true
    return (member.isAdmin || member.role == "admin") && member.hasAllChannelPermissions()
}

fun formatParticipantCount(count: Int): String {
    val safe = count.coerceAtLeast(0)
    val mod10 = safe % 10
    val mod100 = safe % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "$safe участник"
        mod10 in 2..4 && (mod100 < 10 || mod100 >= 20) -> "$safe участника"
        else -> "$safe участников"
    }
}

/**
 * Group subtitle displayed in chat header and group profile card:
 * - Creator or co-owner admin with full rights: only group type ("частная группа" / "публичная группа")
 *   without participant count.
 * - Regular participants and partial admins without full co-owner rights: only participant count
 *   without group type label.
 */
fun formatGroupSubtitle(
    member: GroupMember?,
    isPublic: Boolean,
    memberCount: Int
): String {
    return if (canSeePrivateGroupBadge(member)) {
        if (isPublic) "публичная группа" else "частная группа"
    } else {
        formatParticipantCount(memberCount)
    }
}

fun formatSubscriberCount(count: Int): String {
    val safe = count.coerceAtLeast(0)
    val mod10 = safe % 10
    val mod100 = safe % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "$safe подписчик"
        mod10 in 2..4 && (mod100 < 10 || mod100 >= 20) -> "$safe подписчика"
        else -> "$safe подписчиков"
    }
}

fun canAddUserDirectlyToGroup(
    targetUserId: String,
    targetPrivacy: GroupInvitePrivacy,
    inviterUserId: String,
    inviterIsInTargetContacts: Boolean
): Boolean {
    if (targetUserId == inviterUserId) return true
    return when (targetPrivacy) {
        GroupInvitePrivacy.EVERYBODY -> true
        GroupInvitePrivacy.MY_CONTACTS -> inviterIsInTargetContacts
        GroupInvitePrivacy.NOBODY -> false
    }
}

fun normalizeInvitePath(rawUrl: String): String {
    return rawUrl.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
}

fun generateTelegramStyleInviteUrl(seed: String = java.util.UUID.randomUUID().toString()): String {
    val clean = seed.replace("-", "")
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789"
    val suffix = buildString {
        clean.forEachIndexed { index, ch ->
            val code = (ch.code + index * 7) % alphabet.length
            append(alphabet[code])
        }
    }.take(16).ifBlank { "coyWNavW0q1kOThi" }
    return "t.me/+$suffix"
}

fun resolveAvailableSendAsIdentities(
    groupChat: Chat,
    activeAccount: UserAccount?,
    allChats: List<Chat>,
    allMembersByChat: Map<String, List<GroupMember>>,
    customizations: Map<String, ChannelCustomization>
): List<SendAsIdentity> {
    if (activeAccount == null || !groupChat.isGroup) return emptyList()
    val groupMembers = allMembersByChat[groupChat.id].orEmpty()
    val currentGroupMember = groupMembers.find { it.userId == activeAccount.id }
    val isGroupAdmin = currentGroupMember != null &&
        (currentGroupMember.isAdmin || currentGroupMember.role == "creator" || currentGroupMember.role == "admin")

    val ownedOrManagedChannels = allChats.filter { ch ->
        if (!ch.isChannel) return@filter false
        val chMembers = allMembersByChat[ch.id].orEmpty()
        chMembers.any {
            it.userId == activeAccount.id &&
                (it.role == "creator" || it.role == "admin" || it.isAdmin)
        }
    }

    // Per requirement: "Написать от имени..." works in all groups when the user has own/managed channels
    // or is an admin in the group.
    if (ownedOrManagedChannels.isEmpty() && !isGroupAdmin) {
        return emptyList()
    }

    val result = mutableListOf<SendAsIdentity>()

    // 1. Personal account
    val customGroupTag = currentGroupMember?.customTitle?.takeIf { it.isNotBlank() }
        ?: if (currentGroupMember?.role == "creator") "Владелец" else null
    result.add(
        SendAsIdentity(
            id = activeAccount.id,
            type = "PERSONAL",
            title = activeAccount.displayName.ifBlank { activeAccount.username.ifBlank { "Вы" } },
            subtitle = "личный аккаунт",
            avatarUrl = activeAccount.profilePicUrl.takeIf { it.isNotBlank() },
            emojiStatus = activeAccount.customStatus.takeIf { it.isNotBlank() && it.length <= 4 },
            isPrivate = false,
            customTag = customGroupTag
        )
    )

    // 2. User's owned/managed channels
    ownedOrManagedChannels.forEach { channel ->
        val custom = customizations[channel.id]
        val chCount = maxOf(
            custom?.subscriberCount ?: 0,
            allMembersByChat[channel.id]?.size ?: 0
        ).coerceAtLeast(1)
        result.add(
            SendAsIdentity(
                id = channel.id,
                type = "CHANNEL",
                title = channel.title,
                subtitle = formatSubscriberCount(chCount),
                avatarUrl = custom?.avatarUrl,
                emojiStatus = custom?.emojiStatus?.takeIf { it.isNotBlank() },
                isPrivate = custom?.isPublic == false,
                customTag = "канал"
            )
        )
    }

    // 3. Group anonymous identity (for group creator/admins)
    if (isGroupAdmin) {
        val groupCustom = customizations[groupChat.id]
        result.add(
            SendAsIdentity(
                id = groupChat.id,
                type = "GROUP",
                title = groupChat.title,
                subtitle = "анонимно от имени группы",
                avatarUrl = groupCustom?.avatarUrl,
                emojiStatus = groupCustom?.emojiStatus?.takeIf { it.isNotBlank() },
                isPrivate = groupCustom?.isPublic == false,
                customTag = "админ"
            )
        )
    }

    return result
}
