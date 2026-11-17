package com.example.ui.channel

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChannelDirectMessageModelsTest {

    @Test
    fun `formatApproxUsd formats zero and positive stars with Russian comma`() {
        assertEquals("~0,00 $", SuggestedPostPayload.formatApproxUsd(0L))
        assertEquals("~0,75 $", SuggestedPostPayload.formatApproxUsd(50L))
        assertEquals("~1,50 $", SuggestedPostPayload.formatApproxUsd(100L))
    }

    @Test
    fun `suggested post payload roundtrips to json and preserves fields`() {
        val original = SuggestedPostPayload(
            proposalId = "sp_test123",
            channelId = "c1",
            subscriberId = "user_42",
            subscriberName = "Иван",
            text = "Предлагаю новость про релиз KuoteX",
            priceStars = 25L,
            scheduledTimestamp = 1794860000000L,
            scheduledLabel = "Сегодня в 22:17",
            status = SuggestedPostStatus.PENDING
        )

        val rawJson = original.toJsonString()
        assertTrue(SuggestedPostPayload.isSuggestedPostJson(rawJson))

        val parsed = SuggestedPostPayload.fromJson(rawJson)
        assertNotNull(parsed)
        assertEquals("sp_test123", parsed!!.proposalId)
        assertEquals("c1", parsed.channelId)
        assertEquals("user_42", parsed.subscriberId)
        assertEquals(25L, parsed.priceStars)
        assertEquals("~0,38 $", parsed.approxUsdText)
        assertEquals("Сегодня в 22:17", parsed.scheduledLabel)
        assertEquals(SuggestedPostStatus.PENDING, parsed.status)
    }

    @Test
    fun `date wheel options start with Сегодня and format scheduled labels correctly`() {
        val options = ChannelDirectMessageManager.generateUpcomingDateOptions(daysCount = 5)
        assertEquals(5, options.size)
        assertEquals("Сегодня", options[0].wheelLabel)
        assertEquals("Сегодня в 22:17", ChannelDirectMessageManager.formatScheduledButtonLabel(options[0], 22, 17))

        val tomorrowLabel = ChannelDirectMessageManager.formatScheduledButtonLabel(options[1], 9, 5)
        assertTrue(tomorrowLabel.endsWith("09:05"))
    }

    @Test
    fun `thread id builder isolates channel and subscriber threads`() {
        assertEquals("channel_dm_c1_u9", ChannelDirectMessageManager.buildThreadId("c1", "u9"))
        assertEquals("channel_dm_c1_me", ChannelDirectMessageManager.buildThreadId("c1", ""))
    }
}
