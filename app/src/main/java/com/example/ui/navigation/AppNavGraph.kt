package com.example.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.ui.*
import com.example.ui.channel.*
import com.example.ui.auth.AuthViewModel
import com.example.ui.botapi.BotFatherViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * Type-safe central route constants for KuoteX Messenger navigation.
 */
object AppDestinations {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val REGISTER = "register"
    const val TWO_FACTOR = "2fa"
    const val CHAT_LIST = "chat_list"
    const val ARCHIVED_CHATS = "archived_chats"
    const val ARCHIVE_SETTINGS = "archive_settings"
    const val DISCOVER_BOTS = "discover_bots"
    const val CONTACTS = "contacts"
    const val CALLS = "calls"
    const val SETTINGS = "settings"
    const val DATABASE_DIAGNOSTICS = "database_diagnostics"
    const val SETTINGS_DEVELOPER_STATS = "settings/developer_stats"
    const val SETTINGS_DEVELOPER_DEBUG = "settings/developer_debug"
    const val SETTINGS_REMOTE_CONFIG = "settings/remote_config"
    const val SETTINGS_ACCOUNTS = "settings/accounts"
    const val SETTINGS_PROFILE = "settings/profile?highlightId={highlightId}"
    const val SETTINGS_GENERAL = "settings/general?highlightId={highlightId}"
    const val SETTINGS_STORAGE = "settings/storage?highlightId={highlightId}"
    const val SETTINGS_STORAGE_MEMORY = "settings/storage/memory"
    const val SETTINGS_STORAGE_NETWORK = "settings/storage/network"
    const val SETTINGS_THEMES = "settings/themes?highlightId={highlightId}"
    const val SETTINGS_SECURITY = "settings/security?highlightId={highlightId}"
    const val SETTINGS_TWO_STEP = "settings/two_step"
    const val SETTINGS_PASSCODE = "settings/passcode"
    const val SETTINGS_EMAIL = "settings/email"
    const val SETTINGS_VERIFY_EMAIL = "settings/verify_email"
    const val SETTINGS_DEVICES = "settings/devices?highlightId={highlightId}"
    const val SETTINGS_BLOCKED_USERS = "settings/blocked_users"
    const val SETTINGS_LANGUAGE = "settings/language?highlightId={highlightId}"
    const val SETTINGS_BATTERY = "settings/battery?highlightId={highlightId}"
    const val SETTINGS_FOLDERS = "settings/folders"
    const val SETTINGS_FOLDER_EDIT = "settings/folders/edit?folderId={folderId}"
    fun folderEditRoute(folderId: String? = null) = if (folderId != null) "settings/folders/edit?folderId=$folderId" else "settings/folders/edit"
    const val SETTINGS_PRIVACY = "settings/privacy/{title}"
    const val CHAT = "chat/{chatId}"
    const val CALL = "call/{chatId}?isVideo={isVideo}"
    const val SANDBOX = "sandbox/{botId}"
    const val BROADCAST = "broadcast"
    const val BROADCAST_STREAM = "broadcast/{streamId}"
    const val BOT_DASHBOARD = "dashboard/{botId}"
    const val MY_PROFILE = "my_profile"
    const val PROFILE = "profile/{chatId}"
    const val GIFTS_MARKETPLACE = "gifts_marketplace?userId={userId}&userName={userName}"
    const val GROUP_ADMIN = "group_admin/{chatId}"
    const val CHANNEL_ADMIN = "channel_admin/{chatId}"
    const val CHANNEL_APPEARANCE = "channel_appearance/{chatId}"
    const val CHANNEL_BOOST = "channel_boost/{chatId}"

    // Route Helper Builders
    fun chat(chatId: String) = "chat/$chatId"
    fun call(chatId: String, isVideo: Boolean = false) = "call/$chatId?isVideo=$isVideo"
    fun sandbox(botId: String) = "sandbox/$botId"
    fun dashboard(botId: String) = "dashboard/$botId"
    fun broadcast(streamId: String? = null) = if (streamId != null) "broadcast/$streamId" else "broadcast"
    fun profile(chatId: String) = "profile/$chatId"
    fun giftsMarketplace(userId: String? = null, userName: String? = null): String {
        return if (userId != null) "gifts_marketplace?userId=$userId&userName=${userName ?: ""}" else "gifts_marketplace"
    }
    fun groupAdmin(chatId: String) = "group_admin/$chatId"
    fun channelAdmin(chatId: String) = "channel_admin/$chatId"
    fun channelAppearance(chatId: String) = "channel_appearance/$chatId"
    fun channelBoost(chatId: String) = "channel_boost/$chatId"
    fun privacy(title: String) = "settings/privacy/$title"
    fun settingsProfile(highlightId: String? = null) = if (highlightId != null) "settings/profile?highlightId=$highlightId" else "settings/profile"
    fun settingsGeneral(highlightId: String? = null) = if (highlightId != null) "settings/general?highlightId=$highlightId" else "settings/general"
    fun settingsStorage(highlightId: String? = null) = if (highlightId != null) "settings/storage?highlightId=$highlightId" else "settings/storage"
    fun settingsThemes(highlightId: String? = null) = if (highlightId != null) "settings/themes?highlightId=$highlightId" else "settings/themes"
    fun settingsSecurity(highlightId: String? = null) = if (highlightId != null) "settings/security?highlightId=$highlightId" else "settings/security"
    fun settingsDevices(highlightId: String? = null) = if (highlightId != null) "settings/devices?highlightId=$highlightId" else "settings/devices"
    fun settingsLanguage(highlightId: String? = null) = if (highlightId != null) "settings/language?highlightId=$highlightId" else "settings/language"
    fun settingsBattery(highlightId: String? = null) = if (highlightId != null) "settings/battery?highlightId=$highlightId" else "settings/battery"
    fun settingsFolders() = "settings/folders"
    fun settingsFolderEdit(folderId: String? = null) = if (folderId != null) "settings/folders/edit?folderId=$folderId" else "settings/folders/edit"
}

/**
 * Dedicated Authentication Navigation Graph with isolated Koin AuthViewModel scope.
 */
@Composable
fun AuthNavGraph(
    rootNavController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = koinViewModel(),
    appViewModel: AppViewModel = koinViewModel(),
    accounts: List<UserAccount>,
    requires2FA: String?,
    isAddingAccount: Boolean,
    onAuthSuccess: (String) -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    DisposableEffect(rootNavController) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            focusManager.clearFocus()
        }
        rootNavController.addOnDestinationChangedListener(listener)
        onDispose {
            rootNavController.removeOnDestinationChangedListener(listener)
        }
    }

    NavHost(
        navController = rootNavController,
        startDestination = if (requires2FA != null) AppDestinations.TWO_FACTOR else AppDestinations.AUTH,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        composable(AppDestinations.AUTH) {
            LoginScreen(
                accounts = accounts,
                viewModel = appViewModel,
                onNavigateToRegister = { rootNavController.navigate(AppDestinations.REGISTER) },
                forceManualLogin = isAddingAccount,
                onLoginSuccess = { userIdentifier ->
                    appViewModel.clearAddingAccount()
                    val accountId = accounts.find {
                        it.id == userIdentifier ||
                        it.username.equals(userIdentifier, ignoreCase = true) ||
                        it.username.equals("@$userIdentifier", ignoreCase = true) ||
                        it.displayName.equals(userIdentifier, ignoreCase = true) ||
                        it.phoneNumber == userIdentifier
                    }?.id ?: userIdentifier
                    appViewModel.switchAccount(accountId)
                    onAuthSuccess(accountId)
                }
            )
        }
        composable(AppDestinations.REGISTER) {
            RegistrationScreen(
                accounts = accounts,
                viewModel = appViewModel,
                onNavigateToLogin = { rootNavController.navigate(AppDestinations.AUTH) },
                onRegisterSuccess = { userIdentifier ->
                    appViewModel.clearAddingAccount()
                    val accountId = accounts.find {
                        it.id == userIdentifier ||
                        it.username.equals(userIdentifier, ignoreCase = true) ||
                        it.username.equals("@$userIdentifier", ignoreCase = true) ||
                        it.displayName.equals(userIdentifier, ignoreCase = true) ||
                        it.phoneNumber == userIdentifier
                    }?.id ?: userIdentifier
                    appViewModel.switchAccount(accountId)
                    onAuthSuccess(accountId)
                },
                checkPhoneExists = { phone -> authViewModel.checkPhoneExists(phone) }
            )
        }
        composable(AppDestinations.TWO_FACTOR) {
            TwoFactorAuthScreen(
                onVerify = { appViewModel.verify2FA(it) },
                onCancel = { appViewModel.cancel2FA() }
            )
        }
    }
}

/**
 * Centralized Authenticated Application Navigation Graph.
 * Ensures each screen retrieves its required ViewModel using the koinViewModel delegate.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = koinViewModel(),
    isStoryExpanded: Boolean = false,
    onStoryExpandedChange: (Boolean) -> Unit = {},
    isSearchCollapsed: Boolean = false,
    onSearchCollapsedChange: (Boolean) -> Unit = {},
    storiesProgress: Float = 0f,
    onStoriesProgressChange: (Float) -> Unit = {},
    requestSearchFocus: Boolean = false,
    onSearchFocusHandled: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.CHAT_LIST,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(180)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) },
        popEnterTransition = { fadeIn(animationSpec = tween(180)) },
        popExitTransition = { fadeOut(animationSpec = tween(180)) }
    ) {
        composable(AppDestinations.CHAT_LIST) {
            ChatListScreen(
                viewModel = viewModel,
                navController = navController,
                isStoryExpanded = isStoryExpanded,
                onStoryExpandedChange = onStoryExpandedChange,
                isSearchCollapsed = isSearchCollapsed,
                onSearchCollapsedChange = onSearchCollapsedChange,
                storiesProgress = storiesProgress,
                onStoriesProgressChange = onStoriesProgressChange,
                requestSearchFocus = requestSearchFocus,
                onSearchFocusHandled = onSearchFocusHandled
            )
        }
        composable(AppDestinations.ARCHIVED_CHATS) {
            ArchivedChatsScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.ARCHIVE_SETTINGS) {
            ArchiveSettingsScreen(navController = navController)
        }
        composable(AppDestinations.DISCOVER_BOTS) {
            DiscoverBotsScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.CONTACTS) {
            ContactsScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.CALLS) {
            CallsListScreen(navController = navController)
        }
        composable(AppDestinations.SETTINGS) {
            SettingsMenuScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.DATABASE_DIAGNOSTICS) {
            DatabaseDiagnosticScreen()
        }
        composable(AppDestinations.SETTINGS_DEVELOPER_STATS) {
            DeveloperStatsScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_DEVELOPER_DEBUG) {
            DeveloperAnalyticsDebugScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_REMOTE_CONFIG) {
            RemoteConfigScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_ACCOUNTS) {
            SettingsAccountsScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(
            route = AppDestinations.SETTINGS_PROFILE,
            arguments = listOf(navArgument("highlightId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            LaunchedEffect(highlightId) { viewModel.setHighlightEvent(highlightId) }
            val accountViewModel: AccountViewModel = koinViewModel()
            AccountScreen(
                onBack = { navController.popBackStack() },
                appViewModel = viewModel,
                viewModel = accountViewModel
            )
        }
        composable(
            route = AppDestinations.SETTINGS_GENERAL,
            arguments = listOf(navArgument("highlightId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            LaunchedEffect(highlightId) { viewModel.setHighlightEvent(highlightId) }
            SettingsGeneralScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(
            route = AppDestinations.SETTINGS_STORAGE,
            arguments = listOf(navArgument("highlightId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            LaunchedEffect(highlightId) { viewModel.setHighlightEvent(highlightId) }
            SettingsStorageScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_STORAGE_MEMORY) {
            StorageUsageScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_STORAGE_NETWORK) {
            NetworkUsageScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(
            route = AppDestinations.SETTINGS_THEMES,
            arguments = listOf(navArgument("highlightId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            LaunchedEffect(highlightId) { viewModel.setHighlightEvent(highlightId) }
            SettingsThemesScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(
            route = AppDestinations.SETTINGS_SECURITY,
            arguments = listOf(navArgument("highlightId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            LaunchedEffect(highlightId) { viewModel.setHighlightEvent(highlightId) }
            SettingsSecurityScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_TWO_STEP) {
            TwoStepVerificationScreen(navController = navController)
        }
        composable(AppDestinations.SETTINGS_PASSCODE) {
            PasscodeLockScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_EMAIL) {
            LoginEmailScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_VERIFY_EMAIL) {
            VerifyEmailScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(
            route = AppDestinations.SETTINGS_DEVICES,
            arguments = listOf(navArgument("highlightId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            LaunchedEffect(highlightId) { viewModel.setHighlightEvent(highlightId) }
            DevicesScreen(navController = navController)
        }
        composable(AppDestinations.SETTINGS_BLOCKED_USERS) {
            BlockedUsersScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(
            route = AppDestinations.SETTINGS_LANGUAGE,
            arguments = listOf(navArgument("highlightId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            LaunchedEffect(highlightId) { viewModel.setHighlightEvent(highlightId) }
            SettingsLanguageScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(
            route = AppDestinations.SETTINGS_BATTERY,
            arguments = listOf(navArgument("highlightId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val highlightId = backStackEntry.arguments?.getString("highlightId")
            LaunchedEffect(highlightId) { viewModel.setHighlightEvent(highlightId) }
            SettingsBatterySaverScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.SETTINGS_PRIVACY) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: ""
            PrivacySettingScreen(
                navController = navController,
                title = title
            )
        }
        composable(AppDestinations.SETTINGS_FOLDERS) {
            com.example.ui.folders.ChatFoldersScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(
            route = AppDestinations.SETTINGS_FOLDER_EDIT,
            arguments = listOf(navArgument("folderId") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId")
            com.example.ui.folders.ChatFolderEditScreen(
                viewModel = viewModel,
                navController = navController,
                folderId = folderId
            )
        }
        composable(AppDestinations.CHAT) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            if (chatId != null) {
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                    ChatScreen(
                        viewModel = viewModel,
                        chatId = chatId,
                        navController = navController
                    )
                }
            }
        }
        composable(
            route = AppDestinations.CALL,
            arguments = listOf(
                navArgument("isVideo") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            if (chatId != null) {
                CallScreen(
                    viewModel = viewModel,
                    chatId = chatId,
                    isVideo = isVideo,
                    navController = navController
                )
            }
        }
        composable(AppDestinations.SANDBOX) { backStackEntry ->
            val botId = backStackEntry.arguments?.getString("botId")
            if (botId != null) {
                SandboxScreen(
                    viewModel = viewModel,
                    botId = botId,
                    navController = navController
                )
            }
        }
        composable(AppDestinations.MY_PROFILE) {
            MyProfileScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.BROADCAST) {
            BroadcastScreen(
                viewModel = viewModel,
                navController = navController
            )
        }
        composable(AppDestinations.BROADCAST_STREAM) { backStackEntry ->
            val streamId = backStackEntry.arguments?.getString("streamId")
            BroadcastScreen(
                viewModel = viewModel,
                navController = navController,
                targetStreamId = streamId
            )
        }
        composable(AppDestinations.BOT_DASHBOARD) { backStackEntry ->
            val botId = backStackEntry.arguments?.getString("botId")
            if (botId != null) {
                BotDashboardScreen(
                    botId = botId,
                    navController = navController
                )
            }
        }
        composable(AppDestinations.PROFILE) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            if (chatId != null) {
                val chats = viewModel.chats.value
                val chat = chats.find { it.id == chatId }
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                    if (chat?.isBot == true) {
                        BotProfileScreen(
                            viewModel = viewModel,
                            chatId = chatId,
                            navController = navController
                        )
                    } else if (chat?.isChannel == true || chat?.isGroup == true) {
                        ChannelProfileScreen(
                            viewModel = viewModel,
                            chatId = chatId,
                            navController = navController
                        )
                    } else {
                        ProfileScreen(
                            viewModel = viewModel,
                            chatId = chatId,
                            navController = navController
                        )
                    }
                }
            }
        }
        composable(
            route = AppDestinations.GIFTS_MARKETPLACE,
            arguments = listOf(
                navArgument("userId") { nullable = true; defaultValue = null },
                navArgument("userName") { nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val targetUserId = backStackEntry.arguments?.getString("userId")
            val targetUserName = backStackEntry.arguments?.getString("userName")
            com.example.ui.gifts.GiftMarketplaceScreen(
                viewModel = viewModel,
                navController = navController,
                targetUserId = targetUserId,
                targetUserName = targetUserName
            )
        }
        composable(AppDestinations.GROUP_ADMIN) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            if (chatId != null) {
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                    ChannelGroupAdminScreen(
                        viewModel = viewModel,
                        chatId = chatId,
                        navController = navController
                    )
                }
            }
        }
        composable(AppDestinations.CHANNEL_ADMIN) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            if (chatId != null) {
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                    ChannelGroupAdminScreen(
                        viewModel = viewModel,
                        chatId = chatId,
                        navController = navController
                    )
                }
            }
        }
        composable(AppDestinations.CHANNEL_APPEARANCE) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            if (chatId != null) {
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                    ChannelAppearanceScreen(
                        viewModel = viewModel,
                        chatId = chatId,
                        navController = navController
                    )
                }
            }
        }
        composable(AppDestinations.CHANNEL_BOOST) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            if (chatId != null) {
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                    ChannelBoostScreen(
                        viewModel = viewModel,
                        chatId = chatId,
                        navController = navController
                    )
                }
            }
        }
    }
}
