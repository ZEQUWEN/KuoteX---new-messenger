package com.example.ui

import com.example.ui.rating.UserRatingManager
import com.example.ui.rating.UserRatingProgression
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserRatingTest {

    @Before
    fun setUp() {
        UserRatingManager.resetForTesting()
    }

    @After
    fun tearDown() {
        UserRatingManager.resetForTesting()
    }

    @Test
    fun progressiveLevelThresholds_followFiveKScale() {
        assertEquals(1, UserRatingProgression.calculateLevel(0))
        assertEquals(1, UserRatingProgression.calculateLevel(150))
        assertEquals(1, UserRatingProgression.calculateLevel(4_999))

        assertEquals(2, UserRatingProgression.calculateLevel(5_000))
        assertEquals(2, UserRatingProgression.calculateLevel(14_999))

        assertEquals(3, UserRatingProgression.calculateLevel(15_000))
        assertEquals(3, UserRatingProgression.calculateLevel(34_999))

        assertEquals(4, UserRatingProgression.calculateLevel(35_000))
        assertEquals(5, UserRatingProgression.calculateLevel(75_000))
        assertEquals(6, UserRatingProgression.calculateLevel(150_000))
    }

    @Test
    fun computeState_formatsScoresAndProgressAccurately() {
        val ownInitial = UserRatingProgression.computeState("user_1", 150)
        assertEquals(1, ownInitial.level)
        assertEquals(2, ownInitial.nextLevel)
        assertEquals("150", ownInitial.formattedCurrentPoints)
        assertEquals("5K", ownInitial.formattedNextTarget)
        assertEquals(150f / 5000f, ownInitial.progressFraction, 0.0001f)

        val levelTwoMid = UserRatingProgression.computeState("user_2", 10_000)
        assertEquals(2, levelTwoMid.level)
        assertEquals(3, levelTwoMid.nextLevel)
        assertEquals(5_000, levelTwoMid.currentLevelMinPoints)
        assertEquals(15_000, levelTwoMid.nextLevelTargetPoints)
        assertEquals("10K", levelTwoMid.formattedCurrentPoints)
        assertEquals("15K", levelTwoMid.formattedNextTarget)
        assertEquals(0.5f, levelTwoMid.progressFraction, 0.0001f)
    }

    @Test
    fun scoringRules_awardAndDeductPointsAndLevelUpCorrectly() {
        UserRatingManager.setActiveOwnUserId("acc_1")
        val initial = UserRatingManager.getRatingState("acc_1", isOwnProfile = true)
        assertEquals(150, initial.totalPoints)
        assertEquals(1, initial.level)

        // +100% direct gift Stars (250 Stars -> +250 pts => 400 pts)
        val afterDirectGift = UserRatingManager.recordDirectGiftPurchase("me", 250)
        assertEquals(400, afterDirectGift.totalPoints)

        // +20% collectible marketplace purchase (1000 Stars -> +200 pts => 600 pts)
        val afterMarket = UserRatingManager.recordMarketOrPaidAction("acc_1", 1000)
        assertEquals(600, afterMarket.totalPoints)

        // +2 message activity, +25 channel boost => 627 pts
        UserRatingManager.recordMessageActivity("acc_1")
        val afterBoost = UserRatingManager.recordBoostActivity("acc_1")
        assertEquals(627, afterBoost.totalPoints)

        // Level up across 5,000 threshold
        val leveledUp = UserRatingManager.recordDirectGiftPurchase("acc_1", 4_400)
        assertEquals(5_027, leveledUp.totalPoints)
        assertEquals(2, leveledUp.level)
        assertEquals("15K", leveledUp.formattedNextTarget)

        // -85% gift exchange for Stars on 100-star gift (-85 pts => 4,942 pts, drops back to Level 1)
        val afterExchange = UserRatingManager.recordGiftExchangeForStars("acc_1", 100)
        assertEquals(4_942, afterExchange.totalPoints)
        assertEquals(1, afterExchange.level)

        // -10x Stars refund penalty on 600 Stars (-6000 pts, clamped at 0)
        val afterRefund = UserRatingManager.recordStarsRefund("acc_1", 600)
        assertEquals(0, afterRefund.totalPoints)
        assertEquals(1, afterRefund.level)
        assertTrue(afterRefund.refundPenaltyPoints >= 6_085)
    }

    @Test
    fun otherUserProfiles_haveDeterministicInitialScoresAndIndependentState() {
        val roundState = UserRatingManager.getRatingState("round", isOwnProfile = false)
        assertEquals(547, roundState.totalPoints)
        assertEquals("547", roundState.formattedCurrentPoints)
        assertEquals("5K", roundState.formattedNextTarget)
        assertEquals(1, roundState.level)

        val contactState = UserRatingManager.getRatingState("alice_contact", isOwnProfile = false)
        assertTrue(contactState.totalPoints in 120..879)
        assertEquals(1, contactState.level)
    }
}
