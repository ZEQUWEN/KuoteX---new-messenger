package com.example.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.crypto.CryptoSession
import com.example.crypto.SignalProtocolManager
import com.example.data.AppDatabase
import com.example.data.MessengerRepository
import com.example.data.SecureDatabaseHelper
import com.example.data.UserPreferencesRepository
import com.example.data.WebSocketManager
import com.example.data.dataStore
import com.example.ui.botapi.BotRegistry
import com.example.webrtc.WebRtcCallSession
import okhttp3.OkHttpClient

/**
 * Clean, lightweight Dependency Injection & Service Locator container.
 * Eliminates bash/python file patching and allows dynamic feature swaps,
 * sandbox implementations, themes, and mock/live modules on the fly.
 */
interface AppContainer {
    val context: Context
    val database: AppDatabase
    val sharedPreferences: SharedPreferences
    val userPreferencesRepository: UserPreferencesRepository
    val okHttpClient: OkHttpClient
    val webSocketManager: WebSocketManager
    val messengerRepository: MessengerRepository
    val firestoreChatRepository: com.example.data.repository.FirestoreChatRepository
    val chatDataRepository: com.example.data.repository.ChatDataRepository
    val messengerSandbox: MessengerSandbox
    val themeEngine: ThemeEngine

    // Factory methods for Ephemeral / Scoped instances (avoiding memory leaks)
    fun createSignalProtocolManager(): SignalProtocolManager
    fun createCryptoSession(sessionId: String = "sess_${System.currentTimeMillis()}"): CryptoSession
    fun createCallSession(chatId: String, isVideo: Boolean): WebRtcCallSession
}

/**
 * Standard Production implementation of AppContainer.
 */
class DefaultAppContainer(override val context: Context) : AppContainer {

    override val database: AppDatabase by lazy {
        SecureDatabaseHelper.getInstance(context).database
    }

    override val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("neon_messenger_prefs", Context.MODE_PRIVATE)
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context.dataStore)
    }

    override val okHttpClient: OkHttpClient by lazy {
        com.example.data.NetworkModule.provideOkHttpClient(context) { _, _, _, _ -> }
    }

    override val webSocketManager: WebSocketManager by lazy {
        WebSocketManager(okHttpClient)
    }

    override fun createSignalProtocolManager(): SignalProtocolManager = SignalProtocolManager()

    override fun createCryptoSession(sessionId: String): CryptoSession = CryptoSession(sessionId)

    override fun createCallSession(chatId: String, isVideo: Boolean): WebRtcCallSession =
        WebRtcCallSession(chatId, isVideo)

    override val messengerRepository: MessengerRepository by lazy {
        val db = database
        MessengerRepository(
            db.botDao(),
            db.userDao(),
            db.chatDao(),
            db.messageDao(),
            db.groupMemberDao(),
            db.draftDao(),
            db.contactDao(),
            db.paymentTransactionDao(),
            db.queuedMessageDao(),
            sharedPreferences,
            webSocketManager
        )
    }

    override val firestoreChatRepository: com.example.data.repository.FirestoreChatRepository by lazy {
        val db = database
        com.example.data.repository.FirestoreChatRepositoryImpl(
            chatDao = db.chatDao(),
            messageDao = db.messageDao(),
            queuedMessageDao = db.queuedMessageDao(),
            draftDao = db.draftDao()
        )
    }

    override val chatDataRepository: com.example.data.repository.ChatDataRepository by lazy {
        val db = database
        com.example.data.repository.ChatDataRepositoryImpl(
            chatDao = db.chatDao(),
            messageDao = db.messageDao(),
            draftDao = db.draftDao(),
            contactDao = db.contactDao(),
            groupMemberDao = db.groupMemberDao(),
            firestoreChatRepo = firestoreChatRepository
        )
    }

    override val messengerSandbox: MessengerSandbox by lazy {
        AdvancedMessengerSandboxImpl(context)
    }

    override val themeEngine: ThemeEngine by lazy {
        DefaultThemeEngine()
    }
}

/**
 * Global DI Accessor with dynamic container switching (for test/build flavors/sandboxes).
 */
object Injector {
    private var containerInstance: AppContainer? = null

    fun init(container: AppContainer) {
        containerInstance = container
    }

    fun get(): AppContainer {
        return containerInstance ?: error("Injector is not initialized. Call Injector.init(...) in Application or MainActivity.")
    }

    /**
     * Allows dynamic swapping of modules or entire container at runtime without file patching.
     */
    fun overrideContainer(customContainer: AppContainer) {
        containerInstance = customContainer
    }
}
