package com.example.config

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Data class representing the active Remote Config parameters and dynamic feature flags.
 */
data class AppConfig(
    val isStoriesEnabled: Boolean = true,
    val isCallsEnabled: Boolean = true,
    val isBotStoreEnabled: Boolean = true,
    val isScheduledMessagesEnabled: Boolean = true,
    val isNeonParticlesEnabled: Boolean = true,
    val isWebAppsEnabled: Boolean = true,
    val isMaintenanceMode: Boolean = false,
    val maintenanceMessage: String = "Приложение находится на плановом обслуживании. Скоро вернемся!",
    val minRequiredVersion: Long = 1,
    val announcementBannerText: String = "",
    val announcementBannerUrl: String = "",
    val maxAttachmentSizeMb: Long = 25,
    val chatSyncIntervalSeconds: Long = 15,
    val supportContactUsername: String = "@support",
    val welcomeMessage: String = "Добро пожаловать в KuoteX Messenger!",
    val lastFetchTimeMillis: Long = 0L,
    val lastFetchStatus: String = "INITIAL"
)

/**
 * Central manager for Firebase Remote Config in KuoteX Messenger.
 * Allows updating app parameters and feature flags dynamically without requiring a new app store deployment.
 */
object FirebaseRemoteConfigManager {
    private const val TAG = "RemoteConfigManager"

    // Feature Flag Keys
    const val KEY_FEATURE_STORIES = "feature_stories_enabled"
    const val KEY_FEATURE_CALLS = "feature_voice_video_calls"
    const val KEY_FEATURE_BOT_STORE = "feature_bot_store"
    const val KEY_FEATURE_SCHEDULED_MESSAGES = "feature_scheduled_messages"
    const val KEY_FEATURE_NEON_PARTICLES = "feature_neon_particles"
    const val KEY_FEATURE_WEB_APPS = "feature_web_apps"

    // Operational Parameters Keys
    const val KEY_MAINTENANCE_MODE = "maintenance_mode"
    const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"
    const val KEY_MIN_REQUIRED_VERSION = "min_required_version"
    const val KEY_ANNOUNCEMENT_TEXT = "announcement_banner_text"
    const val KEY_ANNOUNCEMENT_URL = "announcement_banner_url"
    const val KEY_MAX_ATTACHMENT_SIZE_MB = "max_attachment_size_mb"
    const val KEY_CHAT_SYNC_INTERVAL_SEC = "chat_sync_interval_seconds"
    const val KEY_SUPPORT_CONTACT = "support_contact_username"
    const val KEY_WELCOME_MESSAGE = "welcome_message"

    private val _configState = MutableStateFlow(AppConfig())
    val configState: StateFlow<AppConfig> = _configState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false

    /**
     * Initializes Firebase Remote Config with default values, settings, and real-time updates.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()

            // Configure fetch intervals: 0 seconds in development for instant updates
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0) // Instant updates for dev / sandbox
                .setFetchTimeoutInSeconds(10)
                .build()

            remoteConfig.setConfigSettingsAsync(configSettings)

            // Set default in-app configuration values from XML
            try {
                remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load defaults from XML, applying in-code map fallback: ${e.message}")
                setInCodeDefaults(remoteConfig)
            }

            // Immediately update local state from active defaults
            updateStateFromRemoteConfig(remoteConfig, "DEFAULTS_LOADED")

            // Fetch and activate latest values from server
            fetchAndActivate(remoteConfig)

            // Register real-time config listener for instantaneous updates without app restart
            remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    Log.i(TAG, "Remote Config updated keys: ${configUpdate.updatedKeys}")
                    remoteConfig.activate().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "Real-time config update activated successfully")
                            updateStateFromRemoteConfig(remoteConfig, "REALTIME_UPDATED")
                        } else {
                            Log.w(TAG, "Failed to activate real-time config update")
                        }
                    }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    Log.w(TAG, "Remote Config real-time listener error: ${error.message}")
                }
            })

            Log.i(TAG, "Firebase Remote Config initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Remote Config initialization failed: ${e.message}")
            _configState.value = _configState.value.copy(
                lastFetchStatus = "ERROR: ${e.message}"
            )
        }
    }

    private fun setInCodeDefaults(remoteConfig: FirebaseRemoteConfig) {
        val defaults: Map<String, Any> = mapOf(
            KEY_FEATURE_STORIES to true,
            KEY_FEATURE_CALLS to true,
            KEY_FEATURE_BOT_STORE to true,
            KEY_FEATURE_SCHEDULED_MESSAGES to true,
            KEY_FEATURE_NEON_PARTICLES to true,
            KEY_FEATURE_WEB_APPS to true,
            KEY_MAINTENANCE_MODE to false,
            KEY_MAINTENANCE_MESSAGE to "Приложение находится на плановом обслуживании. Скоро вернемся!",
            KEY_MIN_REQUIRED_VERSION to 1L,
            KEY_ANNOUNCEMENT_TEXT to "",
            KEY_ANNOUNCEMENT_URL to "",
            KEY_MAX_ATTACHMENT_SIZE_MB to 25L,
            KEY_CHAT_SYNC_INTERVAL_SEC to 15L,
            KEY_SUPPORT_CONTACT to "@support",
            KEY_WELCOME_MESSAGE to "Добро пожаловать в KuoteX Messenger!"
        )
        remoteConfig.setDefaultsAsync(defaults)
    }

    /**
     * Fetches the latest parameters from Firebase Remote Config and activates them.
     */
    fun fetchAndActivate(remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()) {
        scope.launch {
            try {
                val fetchSuccessful = remoteConfig.fetchAndActivate().await()
                val status = if (fetchSuccessful) "FETCH_AND_ACTIVATED" else "FETCHED_UNCHANGED"
                Log.i(TAG, "Remote Config fetch completed: $status")
                updateStateFromRemoteConfig(remoteConfig, status)
            } catch (e: Exception) {
                Log.w(TAG, "Remote Config fetch failed: ${e.message}")
                updateStateFromRemoteConfig(remoteConfig, "FETCH_FAILED: ${e.message}")
            }
        }
    }

    /**
     * Suspend version to refresh configuration manually on demand.
     */
    suspend fun refreshConfig(): Boolean {
        return try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val result = remoteConfig.fetchAndActivate().await()
            updateStateFromRemoteConfig(remoteConfig, if (result) "MANUAL_ACTIVATED" else "MANUAL_UNCHANGED")
            result
        } catch (e: Exception) {
            Log.w(TAG, "Manual refreshConfig error: ${e.message}")
            false
        }
    }

    private fun updateStateFromRemoteConfig(remoteConfig: FirebaseRemoteConfig, status: String) {
        try {
            val stories = remoteConfig.getBoolean(KEY_FEATURE_STORIES)
            val calls = remoteConfig.getBoolean(KEY_FEATURE_CALLS)
            val botStore = remoteConfig.getBoolean(KEY_FEATURE_BOT_STORE)
            val scheduled = remoteConfig.getBoolean(KEY_FEATURE_SCHEDULED_MESSAGES)
            val neon = remoteConfig.getBoolean(KEY_FEATURE_NEON_PARTICLES)
            val webApps = remoteConfig.getBoolean(KEY_FEATURE_WEB_APPS)
            val maintenance = remoteConfig.getBoolean(KEY_MAINTENANCE_MODE)
            val maintenanceMsg = remoteConfig.getString(KEY_MAINTENANCE_MESSAGE).ifBlank {
                "Приложение находится на плановом обслуживании. Скоро вернемся!"
            }
            val minVersion = remoteConfig.getLong(KEY_MIN_REQUIRED_VERSION)
            val announcementText = remoteConfig.getString(KEY_ANNOUNCEMENT_TEXT)
            val announcementUrl = remoteConfig.getString(KEY_ANNOUNCEMENT_URL)
            val maxAttachmentSize = remoteConfig.getLong(KEY_MAX_ATTACHMENT_SIZE_MB).let { if (it <= 0) 25L else it }
            val syncInterval = remoteConfig.getLong(KEY_CHAT_SYNC_INTERVAL_SEC).let { if (it <= 0) 15L else it }
            val supportContact = remoteConfig.getString(KEY_SUPPORT_CONTACT).ifBlank { "@support" }
            val welcomeMsg = remoteConfig.getString(KEY_WELCOME_MESSAGE).ifBlank { "Добро пожаловать в KuoteX Messenger!" }

            _configState.value = AppConfig(
                isStoriesEnabled = stories,
                isCallsEnabled = calls,
                isBotStoreEnabled = botStore,
                isScheduledMessagesEnabled = scheduled,
                isNeonParticlesEnabled = neon,
                isWebAppsEnabled = webApps,
                isMaintenanceMode = maintenance,
                maintenanceMessage = maintenanceMsg,
                minRequiredVersion = minVersion,
                announcementBannerText = announcementText,
                announcementBannerUrl = announcementUrl,
                maxAttachmentSizeMb = maxAttachmentSize,
                chatSyncIntervalSeconds = syncInterval,
                supportContactUsername = supportContact,
                welcomeMessage = welcomeMsg,
                lastFetchTimeMillis = System.currentTimeMillis(),
                lastFetchStatus = status
            )
            Log.d(TAG, "StateFlow updated with Remote Config state: ${_configState.value}")
        } catch (e: Exception) {
            Log.w(TAG, "Error updating state from Remote Config: ${e.message}")
        }
    }

    // Direct Synchronous Getters
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            FirebaseRemoteConfig.getInstance().getBoolean(key)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return try {
            val value = FirebaseRemoteConfig.getInstance().getString(key)
            if (value.isNotEmpty()) value else defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return try {
            FirebaseRemoteConfig.getInstance().getLong(key)
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return try {
            FirebaseRemoteConfig.getInstance().getDouble(key)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Returns all active configuration entries for diagnostics & developer views.
     */
    fun getAllConfigParameters(): Map<String, Any> {
        val current = _configState.value
        return mapOf(
            KEY_FEATURE_STORIES to current.isStoriesEnabled,
            KEY_FEATURE_CALLS to current.isCallsEnabled,
            KEY_FEATURE_BOT_STORE to current.isBotStoreEnabled,
            KEY_FEATURE_SCHEDULED_MESSAGES to current.isScheduledMessagesEnabled,
            KEY_FEATURE_NEON_PARTICLES to current.isNeonParticlesEnabled,
            KEY_FEATURE_WEB_APPS to current.isWebAppsEnabled,
            KEY_MAINTENANCE_MODE to current.isMaintenanceMode,
            KEY_MAINTENANCE_MESSAGE to current.maintenanceMessage,
            KEY_MIN_REQUIRED_VERSION to current.minRequiredVersion,
            KEY_ANNOUNCEMENT_TEXT to current.announcementBannerText,
            KEY_ANNOUNCEMENT_URL to current.announcementBannerUrl,
            KEY_MAX_ATTACHMENT_SIZE_MB to current.maxAttachmentSizeMb,
            KEY_CHAT_SYNC_INTERVAL_SEC to current.chatSyncIntervalSeconds,
            KEY_SUPPORT_CONTACT to current.supportContactUsername,
            KEY_WELCOME_MESSAGE to current.welcomeMessage,
            "last_fetch_status" to current.lastFetchStatus,
            "last_fetch_time_millis" to current.lastFetchTimeMillis
        )
    }
}
