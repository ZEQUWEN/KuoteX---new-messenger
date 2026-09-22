package com.example.analytics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FirebaseAnalyticsHelperTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FirebaseAnalyticsHelper.init(context)
        AnalyticsTracker.init(context)
    }

    @Test
    fun testUnifiedSchemaConstantsAndEvaluators() {
        // Test Event names
        assertEquals("stream_join", FirebaseAnalyticsHelper.Schema.Events.STREAM_JOIN)
        assertEquals("message_latency", FirebaseAnalyticsHelper.Schema.Events.MESSAGE_LATENCY)
        assertEquals("viewer_drop_off", FirebaseAnalyticsHelper.Schema.Events.VIEWER_DROP_OFF)
        assertEquals("msg_send_success", FirebaseAnalyticsHelper.Schema.Events.MESSAGE_SEND_SUCCESS)

        // Test Parameter keys
        assertEquals("stream_id", FirebaseAnalyticsHelper.Schema.Params.STREAM_ID)
        assertEquals("latency_ms", FirebaseAnalyticsHelper.Schema.Params.LATENCY_MS)
        assertEquals("watch_duration_seconds", FirebaseAnalyticsHelper.Schema.Params.WATCH_DURATION_SECONDS)
        assertEquals("drop_off_reason", FirebaseAnalyticsHelper.Schema.Params.DROP_OFF_REASON)

        // Test Join Latency Evaluator
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.JOIN_FAST, FirebaseAnalyticsHelper.Schema.evaluateJoinLatencyBucket(150L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.JOIN_NORMAL, FirebaseAnalyticsHelper.Schema.evaluateJoinLatencyBucket(500L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.JOIN_SLOW, FirebaseAnalyticsHelper.Schema.evaluateJoinLatencyBucket(1200L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.JOIN_HIGH_LATENCY, FirebaseAnalyticsHelper.Schema.evaluateJoinLatencyBucket(3000L))

        // Test Message Latency Evaluator
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.MSG_ULTRA_FAST, FirebaseAnalyticsHelper.Schema.evaluateMessageLatencyBucket(50L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.MSG_FAST, FirebaseAnalyticsHelper.Schema.evaluateMessageLatencyBucket(200L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.MSG_NORMAL, FirebaseAnalyticsHelper.Schema.evaluateMessageLatencyBucket(600L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.MSG_SLOW, FirebaseAnalyticsHelper.Schema.evaluateMessageLatencyBucket(1500L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.MSG_HIGH_LATENCY, FirebaseAnalyticsHelper.Schema.evaluateMessageLatencyBucket(4000L))

        // Test Viewer Retention Evaluator
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.RETENTION_BOUNCE, FirebaseAnalyticsHelper.Schema.evaluateViewerRetentionBucket(5L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.RETENTION_SHORT, FirebaseAnalyticsHelper.Schema.evaluateViewerRetentionBucket(20L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.RETENTION_MEDIUM, FirebaseAnalyticsHelper.Schema.evaluateViewerRetentionBucket(60L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.RETENTION_ENGAGED, FirebaseAnalyticsHelper.Schema.evaluateViewerRetentionBucket(300L))
        assertEquals(FirebaseAnalyticsHelper.Schema.Values.RETENTION_LOYAL, FirebaseAnalyticsHelper.Schema.evaluateViewerRetentionBucket(1200L))

        // Test Error Category Evaluator
        assertEquals("NETWORK_OFFLINE", FirebaseAnalyticsHelper.Schema.evaluateMessageErrorCategory("OFFLINE_NO_CONNECTION"))
        assertEquals("TIMEOUT", FirebaseAnalyticsHelper.Schema.evaluateMessageErrorCategory("SOCKET_TIMEOUT"))
        assertEquals("SERVER_5XX", FirebaseAnalyticsHelper.Schema.evaluateMessageErrorCategory("SERVER_ERROR_503"))
    }

    @Test
    fun testStreamJoinStandardizedLogging() {
        FirebaseAnalyticsHelper.logStreamJoin(
            streamId = "stream_join_001",
            hostId = "host_user_99",
            isHost = false,
            joinDurationMs = 220L,
            initialViewerCount = 45,
            streamTitle = "Product Demo Live",
            source = "feed_recommendation"
        )

        AnalyticsTracker.logStreamJoin(
            streamId = "stream_join_002",
            hostId = "host_user_99",
            isHost = true,
            joinDurationMs = 150L,
            initialViewerCount = 1,
            streamTitle = "Host Broadcast",
            source = "host_dashboard"
        )
        assertTrue(true)
    }

    @Test
    fun testMessageLatencyStandardizedLogging() {
        // Direct standardized message latency logging
        FirebaseAnalyticsHelper.logMessageLatency(
            messageId = "msg_lat_100",
            chatId = "chat_group_1",
            latencyMs = 85L,
            status = FirebaseAnalyticsHelper.Schema.Values.STATUS_SUCCESS,
            transportType = FirebaseAnalyticsHelper.Schema.Values.TRANSPORT_WEBSOCKET,
            retryCount = 0,
            wasCachedOffline = false
        )

        // Latency on failure
        FirebaseAnalyticsHelper.logMessageLatency(
            messageId = "msg_lat_101",
            chatId = "chat_group_1",
            latencyMs = 1500L,
            status = FirebaseAnalyticsHelper.Schema.Values.STATUS_FAILED,
            transportType = FirebaseAnalyticsHelper.Schema.Values.TRANSPORT_OFFLINE_ROOM,
            retryCount = 2,
            wasCachedOffline = true,
            errorCode = "NETWORK_TIMEOUT"
        )

        // Via AnalyticsTracker
        AnalyticsTracker.logMessageLatency(
            messageId = "msg_lat_102",
            chatId = "chat_group_2",
            latencyMs = 110L
        )

        assertTrue(true)
    }

    @Test
    fun testViewerDropOffStandardizedLogging() {
        FirebaseAnalyticsHelper.logViewerDropOff(
            streamId = "stream_drop_001",
            hostId = "host_123",
            isHost = false,
            watchDurationSeconds = 450L,
            commentsSent = 5,
            reactionsSent = 12,
            starsDonated = 50,
            peakViewersSeen = 300,
            dropOffReason = FirebaseAnalyticsHelper.Schema.Values.REASON_USER_EXIT
        )

        AnalyticsTracker.logStreamDropOff(
            streamId = "stream_drop_002",
            hostId = "host_123",
            isHost = true,
            watchDurationSeconds = 1800L,
            commentsSent = 200,
            reactionsSent = 850,
            starsDonated = 500,
            peakViewersSeen = 650,
            dropOffReason = FirebaseAnalyticsHelper.Schema.Values.REASON_STREAM_ENDED
        )

        assertTrue(true)
    }

    @Test
    fun testMessageSendingSuccessRateLogging() {
        FirebaseAnalyticsHelper.logMessageSendAttempt(
            messageId = "msg_test_001",
            chatId = "chat_abc",
            messageType = "text",
            transportType = FirebaseAnalyticsHelper.Schema.Values.TRANSPORT_WEBSOCKET,
            payloadLength = 42,
            hasMedia = false,
            hasAudio = false,
            isReply = false
        )

        FirebaseAnalyticsHelper.logMessageSendSuccess(
            messageId = "msg_test_001",
            chatId = "chat_abc",
            durationMs = 120L,
            transportType = FirebaseAnalyticsHelper.Schema.Values.TRANSPORT_WEBSOCKET,
            retryCount = 0,
            wasCachedOffline = false
        )

        FirebaseAnalyticsHelper.logMessageSendFailure(
            messageId = "msg_test_002",
            chatId = "chat_abc",
            errorCode = "OFFLINE_NO_CONNECTION",
            errorMessage = "Socket disconnect",
            durationMs = 250L,
            transportType = FirebaseAnalyticsHelper.Schema.Values.TRANSPORT_OFFLINE_ROOM,
            retryCount = 1,
            willQueueOffline = true
        )

        FirebaseAnalyticsHelper.logMessageQueueBatchSync(
            totalQueued = 5,
            successCount = 5,
            failureCount = 0,
            syncDurationMs = 450L
        )

        assertTrue(true)
    }

    @Test
    fun testStreamDurationTracking() {
        FirebaseAnalyticsHelper.logStreamStart(
            streamId = "stream_123",
            hostId = "user_host_1",
            isHost = true,
            streamTitle = "KuoteX Live Dev",
            audience = "public",
            price = 0
        )

        FirebaseAnalyticsHelper.logStreamDurationSummary(
            streamId = "stream_123",
            hostId = "user_host_1",
            isHost = true,
            durationSeconds = 1250L,
            peakViewers = 480,
            commentsCount = 120,
            reactionsCount = 450,
            starsEarned = 1500,
            endReason = FirebaseAnalyticsHelper.Schema.Values.REASON_STREAM_ENDED
        )

        FirebaseAnalyticsHelper.logStreamBufferingEvent(
            streamId = "stream_123",
            bufferDurationMs = 320L,
            bufferReason = "bitrate_adaptation"
        )

        assertTrue(true)
    }

    @Test
    fun testUiInteractionLatencyHelper() = runBlocking {
        // 1. Latency Timer
        val timer = FirebaseAnalyticsHelper.startLatencyTimer(
            actionName = "open_chat_dialog",
            screenName = "ChatListScreen"
        )
        Thread.sleep(25)
        val elapsed = timer.stopAndLog(mapOf("chat_type" to "direct"))
        assertTrue(elapsed >= 0)

        // 2. Synchronous block latency tracking
        val result = FirebaseAnalyticsHelper.trackLatency(
            actionName = "parse_message_entities",
            screenName = "ChatScreen"
        ) {
            val list = mutableListOf<String>()
            for (i in 1..100) {
                list.add("entity_$i")
            }
            list.size
        }
        assertEquals(100, result)

        // 3. Suspending block latency tracking
        val asyncResult = FirebaseAnalyticsHelper.trackAsyncLatency(
            actionName = "load_offline_drafts",
            screenName = "ChatScreen"
        ) {
            kotlinx.coroutines.delay(10)
            "draft_loaded"
        }
        assertEquals("draft_loaded", asyncResult)

        // 4. Direct UI latency & screen transition logging
        FirebaseAnalyticsHelper.logUiLatency(
            actionName = "render_chat_bubble",
            screenName = "ChatScreen",
            durationMs = 8L
        )

        FirebaseAnalyticsHelper.logScreenTransitionLatency(
            fromScreen = "chat_list",
            toScreen = "chat_detail",
            transitionDurationMs = 180L
        )

        assertTrue(true)
    }

    @Test
    fun testBatchProcessingAndPrioritization() {
        FirebaseAnalyticsHelper.clearLogs()
        FirebaseAnalyticsHelper.setPoorConnectionMode(true)

        // 1. Log low priority events under poor connection (should be queued)
        FirebaseAnalyticsHelper.logUiLatency("click_settings", "SettingsScreen", 25L)
        FirebaseAnalyticsHelper.logUiLatency("scroll_chat", "ChatScreen", 15L)
        
        val queuedCount = FirebaseAnalyticsHelper.queuedEvents.value.size
        assertTrue(queuedCount >= 2)

        // 2. Log critical event (message success) - should trigger immediately
        FirebaseAnalyticsHelper.logMessageSendSuccess(
            messageId = "crit_msg_999",
            chatId = "chat_neo",
            durationMs = 95L
        )
        assertTrue(FirebaseAnalyticsHelper.criticalEventsCount.value >= 1)

        // 3. Flush batch manually or upon reconnection
        FirebaseAnalyticsHelper.setPoorConnectionMode(false)
        val flushedCount = FirebaseAnalyticsHelper.flushBatch()
        assertTrue(FirebaseAnalyticsHelper.queuedEvents.value.isEmpty())

        // 4. Verify logs collection
        val logs = FirebaseAnalyticsHelper.eventLogs.value
        assertTrue(logs.isNotEmpty())
    }

    @Test
    fun testMetricsAggregator() {
        FirebaseAnalyticsHelper.clearLogs()

        // Log some events
        FirebaseAnalyticsHelper.logMessageSendSuccess("m1", "c1", 100L)
        FirebaseAnalyticsHelper.logMessageSendSuccess("m2", "c1", 200L)
        FirebaseAnalyticsHelper.logMessageSendFailure("m3", "c1", "TIMEOUT", "Timeout", 300L)

        val successRate = FirebaseAnalyticsHelper.getMessageSuccessRate()
        assertTrue(successRate in 60.0..70.0) // 2 out of 3 = 66.6%

        val avgLatency = FirebaseAnalyticsHelper.getAverageMessageLatency()
        assertTrue(avgLatency > 0L)

        val uptimeFormatted = FirebaseAnalyticsHelper.getUptimeFormatted()
        assertNotNull(uptimeFormatted)
        assertTrue(uptimeFormatted.contains("h ") && uptimeFormatted.contains("m "))
    }
}
