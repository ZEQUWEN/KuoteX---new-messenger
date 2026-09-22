package com.example.data.ecosystem

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * KuoteX User Roles in the ecosystem.
 */
enum class KuoteXUserRole(val value: String) {
    USER("user"),
    MODERATOR("moderator"),
    ADMIN("admin"),
    DEVELOPER("developer");

    companion object {
        fun fromString(role: String?): KuoteXUserRole {
            return entries.find { it.value.equals(role, ignoreCase = true) } ?: USER
        }
    }
}

/**
 * Record of a boost vote allocated by a user to a specific channel.
 */
@IgnoreExtraProperties
data class AllocatedBoost(
    @get:PropertyName("channel_id") @set:PropertyName("channel_id")
    var channelId: String = "",
    @get:PropertyName("votes_count") @set:PropertyName("votes_count")
    var votesCount: Int = 1,
    @get:PropertyName("boosted_at") @set:PropertyName("boosted_at")
    var boostedAt: Long = System.currentTimeMillis()
)

/**
 * Firestore Schema for `users/{user_id}`
 * Represents user account with internal currency balance, VIP subscription, and boost allocation.
 */
@IgnoreExtraProperties
data class KuoteXUserDoc(
    @DocumentId
    @get:PropertyName("user_id") @set:PropertyName("user_id")
    var userId: String = "",
    var username: String = "",
    @get:PropertyName("display_name") @set:PropertyName("display_name")
    var displayName: String = "",
    var balance: Long = 1000L, // Internal currency (KuoteX Stars / Coins)
    var role: String = "user",
    @get:PropertyName("vip_status") @set:PropertyName("vip_status")
    var vipStatus: Boolean = false,
    @get:PropertyName("vip_expiration") @set:PropertyName("vip_expiration")
    var vipExpiration: Long = 0L, // Epoch timestamp in ms
    @get:PropertyName("available_boost_votes") @set:PropertyName("available_boost_votes")
    var availableBoostVotes: Int = 0,
    @get:PropertyName("allocated_boosts") @set:PropertyName("allocated_boosts")
    var allocatedBoosts: List<AllocatedBoost> = emptyList(),
    @get:PropertyName("pinned_gifts_count") @set:PropertyName("pinned_gifts_count")
    var pinnedGiftsCount: Int = 0,
    @get:PropertyName("created_at") @set:PropertyName("created_at")
    var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("updated_at") @set:PropertyName("updated_at")
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun isVipActive(): Boolean {
        if (!vipStatus) return false
        if (vipExpiration == 0L) return true // Lifetime
        return System.currentTimeMillis() < vipExpiration
    }

    fun hasBoostPrivilege(): Boolean {
        return isVipActive() || role.equals("admin", ignoreCase = true) || role.equals("developer", ignoreCase = true)
    }

    val parsedRole: KuoteXUserRole get() = KuoteXUserRole.fromString(role)
}

/**
 * Firestore Schema for `channels/{channel_id}`
 * Represents a channel/group document with its level progression, vote counts, and unlocked perks.
 */
@IgnoreExtraProperties
data class KuoteXChannelDoc(
    @DocumentId
    @get:PropertyName("channel_id") @set:PropertyName("channel_id")
    var channelId: String = "",
    var title: String = "",
    @get:PropertyName("current_votes") @set:PropertyName("current_votes")
    var currentVotes: Int = 0,
    var level: Int = 0,
    @get:PropertyName("next_level_required_votes") @set:PropertyName("next_level_required_votes")
    var nextLevelRequiredVotes: Int = 5,
    @get:PropertyName("custom_color_unlocked") @set:PropertyName("custom_color_unlocked")
    var customColorUnlocked: Boolean = false,
    @get:PropertyName("status_emoji_unlocked") @set:PropertyName("status_emoji_unlocked")
    var statusEmojiUnlocked: Boolean = false,
    @get:PropertyName("wallpaper_unlocked") @set:PropertyName("wallpaper_unlocked")
    var wallpaperUnlocked: Boolean = false,
    @get:PropertyName("stories_per_day_limit") @set:PropertyName("stories_per_day_limit")
    var storiesPerDayLimit: Int = 0,
    @get:PropertyName("updated_at") @set:PropertyName("updated_at")
    var updatedAt: Long = System.currentTimeMillis()
)

/**
 * Firestore Schema for `gifts_catalog/{catalog_gift_id}`
 * Defines gifts in the store catalog, including exclusive pinned items and pricing.
 */
@IgnoreExtraProperties
data class KuoteXCatalogGiftDoc(
    @DocumentId
    @get:PropertyName("catalog_gift_id") @set:PropertyName("catalog_gift_id")
    var catalogGiftId: String = "",
    var title: String = "",
    var price: Long = 100L,
    @get:PropertyName("total_supply") @set:PropertyName("total_supply")
    var totalSupply: Long = -1L, // -1 means unlimited
    @get:PropertyName("available_supply") @set:PropertyName("available_supply")
    var availableSupply: Long = -1L,
    @get:PropertyName("is_exclusive") @set:PropertyName("is_exclusive")
    var isExclusive: Boolean = false,
    @get:PropertyName("lottie_asset_url") @set:PropertyName("lottie_asset_url")
    var lottieAssetUrl: String = "",
    @get:PropertyName("backdrop_color_hex") @set:PropertyName("backdrop_color_hex")
    var backdropColorHex: String = "#161E2E",
    @get:PropertyName("emoji_icon") @set:PropertyName("emoji_icon")
    var emojiIcon: String = "🎁",
    @get:PropertyName("max_upgrade_level") @set:PropertyName("max_upgrade_level")
    var maxUpgradeLevel: Int = 5,
    @get:PropertyName("created_at") @set:PropertyName("created_at")
    var createdAt: Long = System.currentTimeMillis()
)

/**
 * Firestore Schema for `user_gifts/{user_gift_id}`
 * Gift assigned to a user's profile with pinning state and upgrade levels.
 */
@IgnoreExtraProperties
data class KuoteXUserGiftDoc(
    @DocumentId
    @get:PropertyName("user_gift_id") @set:PropertyName("user_gift_id")
    var userGiftId: String = "",
    @get:PropertyName("catalog_gift_id") @set:PropertyName("catalog_gift_id")
    var catalogGiftId: String = "",
    @get:PropertyName("sender_id") @set:PropertyName("sender_id")
    var senderId: String = "",
    @get:PropertyName("receiver_id") @set:PropertyName("receiver_id")
    var receiverId: String = "",
    @get:PropertyName("is_pinned_to_header") @set:PropertyName("is_pinned_to_header")
    var isPinnedToHeader: Boolean = false,
    @get:PropertyName("pin_order_index") @set:PropertyName("pin_order_index")
    var pinOrderIndex: Int = -1,
    @get:PropertyName("upgrade_level") @set:PropertyName("upgrade_level")
    var upgradeLevel: Int = 1,
    var transferable: Boolean = false,
    var message: String = "",
    @get:PropertyName("is_anonymous") @set:PropertyName("is_anonymous")
    var isAnonymous: Boolean = false,
    @get:PropertyName("acquired_at") @set:PropertyName("acquired_at")
    var acquiredAt: Long = System.currentTimeMillis(),
    // Metadata cache for fast rendering
    @get:PropertyName("cached_title") @set:PropertyName("cached_title")
    var cachedTitle: String = "Подарок",
    @get:PropertyName("cached_emoji") @set:PropertyName("cached_emoji")
    var cachedEmoji: String = "🎁",
    @get:PropertyName("cached_color_hex") @set:PropertyName("cached_color_hex")
    var cachedColorHex: String = "#1E293B"
)

/**
 * Types of financial ledger transactions.
 */
enum class LedgerTransactionType(val value: String) {
    GIFT_PURCHASE("GIFT_PURCHASE"),
    GIFT_UPGRADE("GIFT_UPGRADE"),
    VIP_SUBSCRIPTION("VIP_SUBSCRIPTION"),
    BALANCE_TOPUP("BALANCE_TOPUP"),
    CHANNEL_BOOST("CHANNEL_BOOST"),
    DIRECT_MESSAGE_STAR("DIRECT_MESSAGE_STAR"),
    REWARD("REWARD");

    companion object {
        fun fromString(type: String?): LedgerTransactionType {
            return entries.find { it.value.equals(type, ignoreCase = true) } ?: BALANCE_TOPUP
        }
    }
}

/**
 * Status of a financial ledger entry.
 */
enum class LedgerTransactionStatus(val value: String) {
    COMMITTED("COMMITTED"),
    PENDING("PENDING"),
    FAILED("FAILED")
}

/**
 * Firestore Schema for `ledger_transactions/{tx_id}`
 * Immutable ledger record tracking all internal currency movements and purchases.
 */
@IgnoreExtraProperties
data class KuoteXLedgerTxDoc(
    @DocumentId
    @get:PropertyName("tx_id") @set:PropertyName("tx_id")
    var txId: String = "",
    @get:PropertyName("idempotency_key") @set:PropertyName("idempotency_key")
    var idempotencyKey: String = "",
    var type: String = "BALANCE_TOPUP",
    @get:PropertyName("from_user_id") @set:PropertyName("from_user_id")
    var fromUserId: String = "",
    @get:PropertyName("to_user_id") @set:PropertyName("to_user_id")
    var toUserId: String = "",
    var amount: Long = 0L,
    var fee: Long = 0L,
    var status: String = "COMMITTED",
    var metadata: Map<String, Any> = emptyMap(),
    @get:PropertyName("created_at") @set:PropertyName("created_at")
    var createdAt: Long = System.currentTimeMillis()
)

/**
 * Firestore Schema for `poll_votes/{vote_id}`
 * Ensures single vote integrity for Telegram-style voting in channels/groups.
 */
@IgnoreExtraProperties
data class KuoteXPollVoteDoc(
    @DocumentId
    @get:PropertyName("vote_id") @set:PropertyName("vote_id")
    var voteId: String = "",
    @get:PropertyName("poll_id") @set:PropertyName("poll_id")
    var pollId: String = "",
    @get:PropertyName("chat_id") @set:PropertyName("chat_id")
    var chatId: String = "",
    @get:PropertyName("user_id") @set:PropertyName("user_id")
    var userId: String = "",
    @get:PropertyName("selected_option_ids") @set:PropertyName("selected_option_ids")
    var selectedOptionIds: List<Int> = emptyList(),
    var timestamp: Long = System.currentTimeMillis()
)

/**
 * Helper object for Channel Boost Level Mathematical Progression.
 * Level 1: 5 boosts
 * Level 2: 10 boosts
 * Level 3: 20 boosts
 * Level 4: 35 boosts
 * Level 5: 55 boosts
 * Scaling exponentially up to Level 10.
 */
object KuoteXBoostProgression {
    val LEVEL_THRESHOLDS = mapOf(
        1 to 5,
        2 to 10,
        3 to 20,
        4 to 35,
        5 to 55,
        6 to 85,
        7 to 130,
        8 to 190,
        9 to 270,
        10 to 400
    )

    fun calculateLevel(totalVotes: Int): Int {
        var level = 0
        for ((lvl, requiredVotes) in LEVEL_THRESHOLDS.toSortedMap()) {
            if (totalVotes >= requiredVotes) {
                level = lvl
            } else {
                break
            }
        }
        return level.coerceIn(0, 10)
    }

    fun nextLevelRequirement(totalVotes: Int): Int {
        for ((_, requiredVotes) in LEVEL_THRESHOLDS.toSortedMap()) {
            if (totalVotes < requiredVotes) {
                return requiredVotes
            }
        }
        return LEVEL_THRESHOLDS[10] ?: 400
    }
}
