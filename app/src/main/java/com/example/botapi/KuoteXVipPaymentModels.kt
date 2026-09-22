package com.example.botapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Moshi-based Data Models for VIP Subscriptions & Invoice Verification.
 */
@JsonClass(generateAdapter = true)
data class VipSubscriptionPlan(
    val planId: String,
    val title: String,
    val durationDays: Int = 30,
    val priceStars: Long = 300L,
    val boostVotesGranted: Int = 4,
    val perks: List<String> = listOf(
        "4x Буст-голоса для каналов",
        "Эксклюзивный значок VIP Звезды",
        "До 6 закрепленных подарков в профиле",
        "Увеличенные лимиты историй и медиа",
        "Анимированные эмодзи-статусы"
    )
)

/**
 * Webhook payload received when an invoice payment is completed.
 */
@JsonClass(generateAdapter = true)
data class PaymentWebhookPayload(
    @Json(name = "event_type") val eventType: String = "invoice.payment_succeeded",
    @Json(name = "payment_id") val paymentId: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "plan_id") val planId: String = "kuotex_vip_monthly",
    @Json(name = "amount") val amount: Long,
    @Json(name = "currency") val currency: String = "XTR", // Telegram Stars / KuoteX Coins
    @Json(name = "provider_payment_charge_id") val providerPaymentChargeId: String,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @Json(name = "signature") val signature: String = ""
)

/**
 * MTProto Real-Time Event for client state synchronization without restarts.
 */
@JsonClass(generateAdapter = true)
data class MtprotoVipUpdateNotification(
    @Json(name = "_") val type: String = "updateUserVipStatus",
    @Json(name = "user_id") val userId: String,
    @Json(name = "vip_status") val vipStatus: Boolean = true,
    @Json(name = "vip_expiration") val vipExpiration: Long,
    @Json(name = "available_boost_votes") val availableBoostVotes: Int,
    @Json(name = "system_message") val systemMessage: String = "Ваша подписка KuoteX VIP успешно активирована!",
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)
