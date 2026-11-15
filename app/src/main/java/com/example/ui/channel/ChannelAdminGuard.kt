package com.example.ui.channel

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.AppViewModel
import com.example.ui.Chat
import com.example.ui.GroupMember
import com.example.ui.UserAccount
import com.example.ui.popBackStackSafe

/**
 * Finds the GroupMember record matching the currently signed-in account.
 */
fun findCurrentChannelMember(
    members: List<GroupMember>,
    activeAccount: UserAccount?
): GroupMember? {
    if (activeAccount == null) return null
    return members.find {
        it.userId == activeAccount.id ||
            (activeAccount.displayName.isNotBlank() && it.userName == activeAccount.displayName)
    }
}

/**
 * Returns true if the GroupMember has every channel administration right enabled
 * (creator-equivalent powers).
 */
fun GroupMember.hasAllChannelPermissions(): Boolean {
    return canChangeChannelInfo &&
        canPostMessages &&
        canEditMessages &&
        canDeleteMessages &&
        canPostStories &&
        canEditStories &&
        canDeleteStories &&
        canAccessDirectMessages &&
        canManageGreetings &&
        canManageInvites &&
        canManageStreams &&
        canAddAdmins &&
        canBanUsers
}

/**
 * Returns true for the channel creator OR an administrator whose granted permissions
 * are equivalent to those of the creator.
 */
fun hasFullChannelAdminRights(member: GroupMember): Boolean {
    if (member.role == "creator") return true
    if (!member.isAdmin && member.role != "admin") return false
    return member.hasAllChannelPermissions()
}

/**
 * Strict check determining whether the active user can open and configure
 * administrator rights for other admins in the channel.
 */
fun canCurrentUserManageAdminRights(
    members: List<GroupMember>,
    activeAccount: UserAccount?
): Boolean {
    val member = findCurrentChannelMember(members, activeAccount) ?: return false
    return hasFullChannelAdminRights(member)
}

/**
 * Strict role verification: only a member whose role is "creator" or "admin"
 * (or has isAdmin == true) is allowed to access channel/group administration.
 */
fun isChannelAdminOrCreator(
    members: List<GroupMember>,
    activeAccount: UserAccount?
): Boolean {
    val member = findCurrentChannelMember(members, activeAccount) ?: return false
    return member.isAdmin || member.role == "creator" || member.role == "admin"
}

data class DiscussionLinkValidation(
    val allowed: Boolean,
    val errorMessage: String? = null
)

/**
 * Verifies both channel management permissions AND strict creator ownership
 * (`role == "creator"`) on the target group so a channel admin/creator cannot
 * accidentally or illicitly link someone else's group to channel discussions.
 */
fun validateDiscussionGroupLink(
    channelChat: Chat?,
    channelMembers: List<GroupMember>,
    targetGroupChat: Chat?,
    targetGroupMembers: List<GroupMember>,
    activeAccount: UserAccount?
): DiscussionLinkValidation {
    if (activeAccount == null) {
        return DiscussionLinkValidation(
            allowed = false,
            errorMessage = "Необходимо авторизоваться для привязки группы"
        )
    }
    if (channelChat == null || !channelChat.isChannel) {
        return DiscussionLinkValidation(
            allowed = false,
            errorMessage = "Канал для привязки не найден"
        )
    }
    val channelMember = findCurrentChannelMember(channelMembers, activeAccount)
    val canManageChannelDiscussion = channelMember != null && (
        channelMember.role == "creator" ||
            hasFullChannelAdminRights(channelMember) ||
            channelMember.canChangeChannelInfo
        )
    if (!canManageChannelDiscussion) {
        return DiscussionLinkValidation(
            allowed = false,
            errorMessage = "Недостаточно прав администратора канала для привязки обсуждения"
        )
    }
    if (targetGroupChat == null || !targetGroupChat.isGroup || targetGroupChat.isChannel || targetGroupChat.id == channelChat.id) {
        return DiscussionLinkValidation(
            allowed = false,
            errorMessage = "Можно привязать только группу"
        )
    }
    val isCreatorOfTargetGroup = targetGroupMembers.any {
        it.userId == activeAccount.id && it.role == "creator"
    }
    if (!isCreatorOfTargetGroup) {
        return DiscussionLinkValidation(
            allowed = false,
            errorMessage = "Нельзя привязать чужую группу: привязать можно только группу, созданную Вами"
        )
    }
    return DiscussionLinkValidation(allowed = true)
}

/**
 * Route-level guard that verifies creator or admin permissions before rendering
 * any channel administration screen. Non-admin subscribers (or partial admins
 * when [requireFullAdminRights] is true) are immediately redirected back.
 */
@Composable
fun ChannelAdminGuard(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController,
    requireFullAdminRights: Boolean = false,
    content: @Composable (Chat?, List<GroupMember>, UserAccount?) -> Unit
) {
    val context = LocalContext.current
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()

    val membersState by produceState<List<GroupMember>?>(initialValue = null, chatId) {
        viewModel.getGroupMembers(chatId).collect { emitted ->
            value = emitted
        }
    }

    val chat = chats.find { it.id == chatId }
    val members = membersState
    var hasRedirected by remember(chatId) { mutableStateOf(false) }

    val isReady = members != null && activeAccount != null
    val isAuthorized = if (members != null) {
        if (requireFullAdminRights) {
            canCurrentUserManageAdminRights(members, activeAccount)
        } else {
            isChannelAdminOrCreator(members, activeAccount)
        }
    } else {
        false
    }

    LaunchedEffect(isReady, isAuthorized, chatId) {
        if (isReady && !isAuthorized && !hasRedirected) {
            hasRedirected = true
            Toast.makeText(
                context,
                if (requireFullAdminRights) {
                    "Настройка прав администраторов доступна только создателю и администраторам с полными правами"
                } else {
                    "Доступ запрещён: требуются права администратора"
                },
                Toast.LENGTH_SHORT
            ).show()
            navController.popBackStackSafe()
        }
    }

    if (!isReady || !isAuthorized) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1117)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFB072FF))
        }
    } else {
        content(chat, members ?: emptyList(), activeAccount)
    }
}
