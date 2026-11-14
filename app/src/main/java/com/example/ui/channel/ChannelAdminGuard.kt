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

/**
 * Route-level guard that verifies creator or admin permissions before rendering
 * any channel administration screen. Non-admin subscribers are immediately redirected back.
 */
@Composable
fun ChannelAdminGuard(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController,
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
        isChannelAdminOrCreator(members, activeAccount)
    } else {
        false
    }

    LaunchedEffect(isReady, isAuthorized, chatId) {
        if (isReady && !isAuthorized && !hasRedirected) {
            hasRedirected = true
            Toast.makeText(
                context,
                "Доступ запрещён: требуются права администратора",
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
