package com.example.ui.rating

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.max

/**
 * Room entity storing each user's cumulative KuoteX activity rating and score breakdown.
 */
@Entity(tableName = "user_ratings")
data class UserRatingEntity(
    @PrimaryKey val userId: String,
    val totalPoints: Int,
    val directGiftStarsPoints: Int = 0,
    val marketAndPostsPoints: Int = 0,
    val activityPoints: Int = 0,
    val refundPenaltyPoints: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface UserRatingDao {
    @Query("SELECT * FROM user_ratings")
    fun getAllRatings(): Flow<List<UserRatingEntity>>

    @Query("SELECT * FROM user_ratings WHERE userId = :userId LIMIT 1")
    fun observeRating(userId: String): Flow<UserRatingEntity?>

    @Query("SELECT * FROM user_ratings WHERE userId = :userId LIMIT 1")
    suspend fun getRatingSync(userId: String): UserRatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRating(entity: UserRatingEntity)
}

/**
 * Computed UI state for rendering a user's rating level badge and progression sheet.
 */
data class UserRatingState(
    val userId: String,
    val totalPoints: Int,
    val level: Int,
    val nextLevel: Int,
    val currentLevelMinPoints: Int,
    val nextLevelTargetPoints: Int,
    val progressFraction: Float,
    val formattedCurrentPoints: String,
    val formattedNextTarget: String,
    val directGiftStarsPoints: Int = 0,
    val marketAndPostsPoints: Int = 0,
    val activityPoints: Int = 0,
    val refundPenaltyPoints: Int = 0
)

/**
 * Pure domain rules and progressive level scale for KuoteX User Rating.
 * Level 1 -> Level 2 at 5K points, then progressive thresholds: 15K, 35K, 75K, 150K, 300K, 500K, 1M...
 */
object UserRatingProgression {

    val LEVEL_THRESHOLDS = listOf(
        0,          // Level 1 start
        5_000,      // Level 2
        15_000,     // Level 3
        35_000,     // Level 4
        75_000,     // Level 5
        150_000,    // Level 6
        300_000,    // Level 7
        500_000,    // Level 8
        1_000_000   // Level 9
    )

    const val ACTIVITY_MESSAGE_POINTS = 2
    const val ACTIVITY_CHANNEL_BOOST_POINTS = 25

    fun thresholdForLevel(level: Int): Int {
        if (level <= 1) return 0
        val index = level - 1
        if (index < LEVEL_THRESHOLDS.size) {
            return LEVEL_THRESHOLDS[index]
        }
        val extraSteps = index - (LEVEL_THRESHOLDS.size - 1)
        return LEVEL_THRESHOLDS.last() + (extraSteps * 500_000)
    }

    fun calculateLevel(totalPoints: Int): Int {
        val safePoints = totalPoints.coerceAtLeast(0)
        var currentLevel = 1
        while (safePoints >= thresholdForLevel(currentLevel + 1)) {
            currentLevel++
            if (currentLevel >= 99) break
        }
        return currentLevel
    }

    fun computeState(
        userId: String,
        totalPoints: Int,
        entity: UserRatingEntity? = null
    ): UserRatingState {
        val safePoints = totalPoints.coerceAtLeast(0)
        val level = calculateLevel(safePoints)
        val nextLevel = level + 1
        val currentMin = thresholdForLevel(level)
        val nextTarget = thresholdForLevel(nextLevel)
        val span = (nextTarget - currentMin).coerceAtLeast(1)
        val progress = ((safePoints - currentMin).toFloat() / span.toFloat()).coerceIn(0f, 1f)

        return UserRatingState(
            userId = userId,
            totalPoints = safePoints,
            level = level,
            nextLevel = nextLevel,
            currentLevelMinPoints = currentMin,
            nextLevelTargetPoints = nextTarget,
            progressFraction = progress,
            formattedCurrentPoints = formatPointsValue(safePoints),
            formattedNextTarget = formatCompactThreshold(nextTarget),
            directGiftStarsPoints = entity?.directGiftStarsPoints ?: 0,
            marketAndPostsPoints = entity?.marketAndPostsPoints ?: 0,
            activityPoints = entity?.activityPoints ?: 0,
            refundPenaltyPoints = entity?.refundPenaltyPoints ?: 0
        )
    }

    /**
     * Exact points up to 9,999 so scores like "150", "547", "4870" match Telegram verbatim;
     * compact K/M format for larger values so the floating bubble never clips.
     */
    fun formatPointsValue(points: Int): String {
        val safe = points.coerceAtLeast(0)
        return when {
            safe < 10_000 -> safe.toString()
            safe < 1_000_000 -> formatCompactThousands(safe)
            else -> formatCompactMillions(safe)
        }
    }

    fun formatCompactThreshold(points: Int): String {
        val safe = points.coerceAtLeast(0)
        return when {
            safe < 1_000 -> safe.toString()
            safe < 1_000_000 -> formatCompactThousands(safe)
            else -> formatCompactMillions(safe)
        }
    }

    private fun formatCompactThousands(value: Int): String {
        return if (value % 1_000 == 0) {
            "${value / 1_000}K"
        } else {
            val decimal = (value / 100) / 10.0
            if (decimal % 1.0 == 0.0) {
                "${decimal.toInt()}K"
            } else {
                String.format(Locale.US, "%.1fK", decimal)
            }
        }
    }

    private fun formatCompactMillions(value: Int): String {
        return if (value % 1_000_000 == 0) {
            "${value / 1_000_000}M"
        } else {
            val decimal = (value / 100_000) / 10.0
            if (decimal % 1.0 == 0.0) {
                "${decimal.toInt()}M"
            } else {
                String.format(Locale.US, "%.1fM", decimal)
            }
        }
    }

    // --- Telegram Scoring Formula Rules ---

    /** +100% of Stars spent on direct KuoteX gifts */
    fun pointsForDirectGift(starsSpent: Int): Int = starsSpent.coerceAtLeast(0)

    /** +20% of Stars spent on marketplace gifts, paid channel messages, paid posts, or upgrades */
    fun pointsForMarketOrPaidPost(starsSpent: Int): Int {
        if (starsSpent <= 0) return 0
        return max(1, (starsSpent * 0.20).toInt())
    }

    /** -10x of refunded Stars */
    fun penaltyForStarsRefund(refundedStars: Int): Int = refundedStars.coerceAtLeast(0) * 10

    /** -85% of gift Stars value when exchanging a gift for Stars */
    fun penaltyForGiftExchange(giftStarsValue: Int): Int {
        if (giftStarsValue <= 0) return 0
        return (giftStarsValue * 0.85).toInt()
    }
}

/**
 * Centralized reactive rating coordinator synced with Room DAO (`user_ratings`)
 * and exposed to both UI and Ecosystem managers.
 */
object UserRatingManager {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _ratingsMap = MutableStateFlow<Map<String, UserRatingEntity>>(emptyMap())
    val ratingsMap: StateFlow<Map<String, UserRatingEntity>> = _ratingsMap.asStateFlow()

    @Volatile
    private var attachedDao: UserRatingDao? = null
    private var daoCollectionJob: Job? = null

    @Volatile
    private var activeOwnUserId: String? = null

    fun attachDao(dao: UserRatingDao, scope: CoroutineScope = managerScope) {
        if (attachedDao === dao && daoCollectionJob?.isActive == true) return
        attachedDao = dao
        daoCollectionJob?.cancel()
        daoCollectionJob = scope.launch(Dispatchers.IO) {
            dao.getAllRatings().collect { rows ->
                val fromDb = rows.associateBy { it.userId }
                _ratingsMap.update { current ->
                    // Preserve any in-memory additions not yet flushed while favoring DB rows
                    current + fromDb
                }
            }
        }
    }

    fun setActiveOwnUserId(userId: String?) {
        val normalized = userId?.takeIf { it.isNotBlank() }
        activeOwnUserId = normalized
        if (normalized != null) {
            ensureInitialized(normalized, isOwnProfile = true)
        }
    }

    fun resolveUserId(rawUserId: String): String {
        val activeId = activeOwnUserId
        return if ((rawUserId == "me" || rawUserId.isBlank()) && !activeId.isNullOrBlank()) {
            activeId
        } else {
            rawUserId.ifBlank { activeId ?: "me" }
        }
    }

    fun defaultInitialPoints(userId: String, isOwnProfile: Boolean): Int {
        return when {
            isOwnProfile || userId == activeOwnUserId || userId == "me" || userId == "1" -> 150
            userId == "round" -> 547
            else -> 120 + (userId.hashCode().absoluteValue % 760)
        }
    }

    fun ensureInitialized(userId: String, isOwnProfile: Boolean = false): UserRatingEntity {
        val resolvedId = resolveUserId(userId)
        val existing = _ratingsMap.value[resolvedId]
        if (existing != null) return existing

        val initialPoints = defaultInitialPoints(resolvedId, isOwnProfile)
        val created = UserRatingEntity(
            userId = resolvedId,
            totalPoints = initialPoints,
            activityPoints = initialPoints
        )
        _ratingsMap.update { current ->
            if (current.containsKey(resolvedId)) current else current + (resolvedId to created)
        }
        persistAsync(created)
        return created
    }

    fun getRatingState(userId: String, isOwnProfile: Boolean = false): UserRatingState {
        val resolvedId = resolveUserId(userId)
        val entity = _ratingsMap.value[resolvedId]
            ?: UserRatingEntity(
                userId = resolvedId,
                totalPoints = defaultInitialPoints(resolvedId, isOwnProfile),
                activityPoints = defaultInitialPoints(resolvedId, isOwnProfile)
            )
        return UserRatingProgression.computeState(resolvedId, entity.totalPoints, entity)
    }

    fun observeRatingState(userId: String, isOwnProfile: Boolean = false): Flow<UserRatingState> {
        val resolvedId = resolveUserId(userId)
        return _ratingsMap
            .map { map ->
                val entity = map[resolvedId]
                    ?: UserRatingEntity(
                        userId = resolvedId,
                        totalPoints = defaultInitialPoints(resolvedId, isOwnProfile),
                        activityPoints = defaultInitialPoints(resolvedId, isOwnProfile)
                    )
                UserRatingProgression.computeState(resolvedId, entity.totalPoints, entity)
            }
            .distinctUntilChanged()
    }

    fun recordDirectGiftPurchase(senderUserId: String, starsSpent: Int): UserRatingState {
        val delta = UserRatingProgression.pointsForDirectGift(starsSpent)
        return mutateUserRating(senderUserId) { current ->
            current.copy(
                totalPoints = (current.totalPoints + delta).coerceAtLeast(0),
                directGiftStarsPoints = current.directGiftStarsPoints + delta,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun recordMarketOrPaidAction(userId: String, starsSpent: Int): UserRatingState {
        val delta = UserRatingProgression.pointsForMarketOrPaidPost(starsSpent)
        return mutateUserRating(userId) { current ->
            current.copy(
                totalPoints = (current.totalPoints + delta).coerceAtLeast(0),
                marketAndPostsPoints = current.marketAndPostsPoints + delta,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun recordStarsRefund(userId: String, refundedStars: Int): UserRatingState {
        val penalty = UserRatingProgression.penaltyForStarsRefund(refundedStars)
        return mutateUserRating(userId) { current ->
            current.copy(
                totalPoints = (current.totalPoints - penalty).coerceAtLeast(0),
                refundPenaltyPoints = current.refundPenaltyPoints + penalty,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun recordGiftExchangeForStars(userId: String, giftPriceStars: Int): UserRatingState {
        val penalty = UserRatingProgression.penaltyForGiftExchange(giftPriceStars)
        return mutateUserRating(userId) { current ->
            current.copy(
                totalPoints = (current.totalPoints - penalty).coerceAtLeast(0),
                refundPenaltyPoints = current.refundPenaltyPoints + penalty,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun recordMessageActivity(
        userId: String,
        points: Int = UserRatingProgression.ACTIVITY_MESSAGE_POINTS
    ): UserRatingState {
        val safeDelta = points.coerceAtLeast(0)
        return mutateUserRating(userId) { current ->
            current.copy(
                totalPoints = (current.totalPoints + safeDelta).coerceAtLeast(0),
                activityPoints = current.activityPoints + safeDelta,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun recordBoostActivity(
        userId: String,
        points: Int = UserRatingProgression.ACTIVITY_CHANNEL_BOOST_POINTS
    ): UserRatingState {
        val safeDelta = points.coerceAtLeast(0)
        return mutateUserRating(userId) { current ->
            current.copy(
                totalPoints = (current.totalPoints + safeDelta).coerceAtLeast(0),
                activityPoints = current.activityPoints + safeDelta,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private inline fun mutateUserRating(
        rawUserId: String,
        transform: (UserRatingEntity) -> UserRatingEntity
    ): UserRatingState {
        val resolvedId = resolveUserId(rawUserId)
        val isOwn = resolvedId == activeOwnUserId || rawUserId == "me" || resolvedId == "1"
        lateinit var updatedEntity: UserRatingEntity

        _ratingsMap.update { currentMap ->
            val base = currentMap[resolvedId] ?: UserRatingEntity(
                userId = resolvedId,
                totalPoints = defaultInitialPoints(resolvedId, isOwn),
                activityPoints = defaultInitialPoints(resolvedId, isOwn)
            )
            updatedEntity = transform(base)
            currentMap + (resolvedId to updatedEntity)
        }
        persistAsync(updatedEntity)
        return UserRatingProgression.computeState(resolvedId, updatedEntity.totalPoints, updatedEntity)
    }

    private fun persistAsync(entity: UserRatingEntity) {
        val dao = attachedDao ?: return
        managerScope.launch {
            try {
                dao.upsertRating(entity)
            } catch (_: Throwable) {
                // Keep reactive in-memory state intact even if storage is unavailable
            }
        }
    }

    fun resetForTesting() {
        daoCollectionJob?.cancel()
        daoCollectionJob = null
        attachedDao = null
        activeOwnUserId = null
        _ratingsMap.value = emptyMap()
    }
}
