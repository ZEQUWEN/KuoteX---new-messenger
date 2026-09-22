package com.example.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsTracker {
    private const val TAG = "AnalyticsTracker"
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
            isInitialized = true
            FirebaseAnalyticsHelper.init(context)
            Log.d(TAG, "Firebase Analytics initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Analytics initialization warning: ${e.message}")
        }
    }

    /**
     * Log screen transition / view event
     */
    fun logScreenView(screenName: String, screenClass: String? = null, params: Map<String, Any> = emptyMap()) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass ?: screenName)
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "ScreenView: $screenName (class=$screenClass, params=$params)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log screen view: $screenName", e)
        }
    }

    /**
     * Log button or interactive component click
     */
    fun logButtonClick(
        buttonName: String,
        module: String,
        screenName: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val params = metadata.toMutableMap().apply {
            put("button_name", buttonName)
            put("module", module)
            if (screenName != null) put("screen_name", screenName)
        }
        logEvent("button_click", params)
    }

    /**
     * Log Chat module specific actions
     */
    fun logChatAction(
        action: String,
        chatId: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val params = metadata.toMutableMap().apply {
            put("action", action)
            put("chat_id", chatId)
            put("module", "chat")
        }
        logEvent("chat_action", params)
    }

    /**
     * Log Stream / Broadcast module specific actions
     */
    fun logStreamAction(
        action: String,
        streamId: String? = null,
        hostId: String? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val params = metadata.toMutableMap().apply {
            put("action", action)
            put("module", "stream")
            if (streamId != null) put("stream_id", streamId)
            if (hostId != null) put("host_id", hostId)
        }
        logEvent("stream_action", params)
    }

    /**
     * Log Stream Join event with latency and context using unified schema
     */
    fun logStreamJoin(
        streamId: String,
        hostId: String?,
        isHost: Boolean,
        joinDurationMs: Long,
        initialViewerCount: Int,
        streamTitle: String? = null,
        source: String = "direct"
    ) {
        FirebaseAnalyticsHelper.logStreamJoin(
            streamId = streamId,
            hostId = hostId,
            isHost = isHost,
            joinDurationMs = joinDurationMs,
            initialViewerCount = initialViewerCount,
            streamTitle = streamTitle,
            source = source
        )
    }

    /**
     * Log Stream Viewer Drop-off / Leave event with session performance metrics using unified schema
     */
    fun logStreamDropOff(
        streamId: String,
        hostId: String?,
        isHost: Boolean,
        watchDurationSeconds: Long,
        commentsSent: Int = 0,
        reactionsSent: Int = 0,
        starsDonated: Int = 0,
        peakViewersSeen: Int = 0,
        dropOffReason: String = FirebaseAnalyticsHelper.Schema.Values.REASON_USER_EXIT
    ) {
        FirebaseAnalyticsHelper.logViewerDropOff(
            streamId = streamId,
            hostId = hostId,
            isHost = isHost,
            watchDurationSeconds = watchDurationSeconds,
            commentsSent = commentsSent,
            reactionsSent = reactionsSent,
            starsDonated = starsDonated,
            peakViewersSeen = peakViewersSeen,
            dropOffReason = dropOffReason
        )
    }

    /**
     * Log Message Latency using unified schema
     */
    fun logMessageLatency(
        messageId: String,
        chatId: String,
        latencyMs: Long,
        status: String = FirebaseAnalyticsHelper.Schema.Values.STATUS_SUCCESS,
        transportType: String = FirebaseAnalyticsHelper.Schema.Values.TRANSPORT_WEBSOCKET,
        retryCount: Int = 0,
        wasCachedOffline: Boolean = false,
        errorCode: String? = null
    ) {
        FirebaseAnalyticsHelper.logMessageLatency(
            messageId = messageId,
            chatId = chatId,
            latencyMs = latencyMs,
            status = status,
            transportType = transportType,
            retryCount = retryCount,
            wasCachedOffline = wasCachedOffline,
            errorCode = errorCode
        )
    }

    /**
     * Log user authentication / login event
     */
    fun logUserLogin(userId: String, method: String = "firebase") {
        try {
            firebaseAnalytics?.setUserId(userId)
            logEvent(FirebaseAnalytics.Event.LOGIN, mapOf("user_id" to userId, "method" to method))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log user login", e)
        }
    }

    /**
     * Log user logout event
     */
    fun logUserLogout() {
        try {
            firebaseAnalytics?.setUserId(null)
            logEvent("user_logout", emptyMap())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log user logout", e)
        }
    }

    /**
     * Generic event logger
     */
    fun logEvent(eventName: String, params: Map<String, Any> = emptyMap()) {
        try {
            val bundle = Bundle().apply {
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics?.logEvent(eventName, bundle)
            Log.d(TAG, "Event: $eventName, params=$params")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event: $eventName", e)
        }
    }
}

/**
 * Composable side-effect helper to track screen transitions
 */
@Composable
fun TrackScreen(screenName: String, screenClass: String? = null, params: Map<String, Any> = emptyMap()) {
    LaunchedEffect(screenName) {
        AnalyticsTracker.logScreenView(screenName, screenClass, params)
    }
}
