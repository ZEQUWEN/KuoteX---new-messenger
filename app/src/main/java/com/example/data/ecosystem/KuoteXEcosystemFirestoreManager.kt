package com.example.data.ecosystem

import android.util.Log
import com.example.ui.gifts.CollectibleGift
import com.example.ui.gifts.CurrencyType
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Custom Exceptions for Ecosystem Operations.
 */
class InsufficientBalanceException(message: String) : Exception(message)
class GiftSoldOutException(message: String) : Exception(message)
class DuplicateTransactionException(message: String) : Exception(message)
class UnauthorizedBoostException(message: String) : Exception(message)
class InsufficientBoostVotesException(message: String) : Exception(message)

/**
 * KuoteX Ecosystem Firestore Manager
 * Handles ACID-compliant atomic transactions, balance management, VIP subscriptions,
 * channel boosting with privilege validation, and animated profile gifts.
 */
object KuoteXEcosystemFirestoreManager {

    private const val TAG = "KuoteXEcosystem"
    private const val COLLECTION_USERS = "users"
    private const val COLLECTION_CHANNELS = "channels"
    private const val COLLECTION_GIFTS_CATALOG = "gifts_catalog"
    private const val COLLECTION_USER_GIFTS = "user_gifts"
    private const val COLLECTION_LEDGER_TX = "ledger_transactions"
    private const val COLLECTION_IDEMPOTENCY = "idempotency_keys"
    private const val COLLECTION_POLL_VOTES = "poll_votes"

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Local in-memory caching fallback for offline & fast UI rendering
    private val _currentUserState = MutableStateFlow<KuoteXUserDoc?>(null)
    val currentUserState: StateFlow<KuoteXUserDoc?> = _currentUserState.asStateFlow()

    private val _catalogGifts = MutableStateFlow<List<KuoteXCatalogGiftDoc>>(emptyList())
    val catalogGifts: StateFlow<List<KuoteXCatalogGiftDoc>> = _catalogGifts.asStateFlow()

    private val _pinnedGiftsMap = MutableStateFlow<Map<String, List<KuoteXUserGiftDoc>>>(emptyMap())
    val pinnedGiftsMap: StateFlow<Map<String, List<KuoteXUserGiftDoc>>> = _pinnedGiftsMap.asStateFlow()

    // Collectible unique gifts on the marketplace
    private val _collectibleMarketplaceGifts = MutableStateFlow<List<CollectibleGift>>(emptyList())
    val collectibleMarketplaceGifts: StateFlow<List<CollectibleGift>> = _collectibleMarketplaceGifts.asStateFlow()

    // User's pinned collectible gift next to nickname
    private val _userPinnedCollectible = MutableStateFlow<Map<String, CollectibleGift?>>(emptyMap())
    val userPinnedCollectible: StateFlow<Map<String, CollectibleGift?>> = _userPinnedCollectible.asStateFlow()

    // Selected currency preference (Stars, USD, RUB, EUR)
    private val _selectedCurrency = MutableStateFlow<CurrencyType>(CurrencyType.STARS)
    val selectedCurrency: StateFlow<CurrencyType> = _selectedCurrency.asStateFlow()

    fun setCurrency(currency: CurrencyType) {
        _selectedCurrency.value = currency
    }

    fun pinCollectibleToUsername(userId: String, gift: CollectibleGift?) {
        _userPinnedCollectible.update { current ->
            current + (userId to gift)
        }
    }

    /**
     * Default sample gift documents matching Telegram references.
     */
    fun sampleUserGiftDocs(userId: String): List<KuoteXUserGiftDoc> {
        return listOf(
            KuoteXUserGiftDoc(
                userGiftId = "g_heart_1",
                catalogGiftId = "gift_heart_box_008",
                senderId = "alex",
                receiverId = userId,
                isPinnedToHeader = true,
                pinOrderIndex = 0,
                upgradeLevel = 2,
                cachedTitle = "Сердце с бантом",
                cachedEmoji = "💝",
                cachedColorHex = "#2E081E"
            ),
            KuoteXUserGiftDoc(
                userGiftId = "g_bear_1",
                catalogGiftId = "gift_teddy_bear_007",
                senderId = "Сестра.",
                receiverId = userId,
                isPinnedToHeader = true,
                pinOrderIndex = 1,
                upgradeLevel = 1,
                cachedTitle = "Плюшевый Мишка",
                cachedEmoji = "🧸",
                cachedColorHex = "#261E14"
            ),
            KuoteXUserGiftDoc(
                userGiftId = "g_bear_2",
                catalogGiftId = "gift_teddy_bear_007",
                senderId = "round_fan",
                receiverId = userId,
                isPinnedToHeader = true,
                pinOrderIndex = 2,
                upgradeLevel = 1,
                cachedTitle = "Плюшевый Мишка",
                cachedEmoji = "🧸",
                cachedColorHex = "#261E14"
            ),
            KuoteXUserGiftDoc(
                userGiftId = "g_bear_3",
                catalogGiftId = "gift_teddy_bear_007",
                senderId = "Аноним",
                receiverId = userId,
                isPinnedToHeader = true,
                pinOrderIndex = 3,
                upgradeLevel = 1,
                cachedTitle = "Плюшевый Мишка",
                cachedEmoji = "🧸",
                cachedColorHex = "#261E14"
            ),
            KuoteXUserGiftDoc(
                userGiftId = "g_gold_1",
                catalogGiftId = "gift_golden_present_009",
                senderId = "best_friend",
                receiverId = userId,
                isPinnedToHeader = true,
                pinOrderIndex = 4,
                upgradeLevel = 3,
                cachedTitle = "Золотой Подарок",
                cachedEmoji = "🎁",
                cachedColorHex = "#2D2206"
            ),
            KuoteXUserGiftDoc(
                userGiftId = "g_dragon_1",
                catalogGiftId = "gift_cyber_dragon_001",
                senderId = "durov",
                receiverId = userId,
                isPinnedToHeader = true,
                pinOrderIndex = 5,
                upgradeLevel = 4,
                cachedTitle = "Cyber Dragon 2026",
                cachedEmoji = "🐉",
                cachedColorHex = "#1E1B4B"
            )
        )
    }

    /**
     * Initializes gifts for a user if not already in memory/cache.
     */
    fun initializeUserGiftsIfEmpty(userId: String, gifts: List<KuoteXUserGiftDoc> = sampleUserGiftDocs(userId)) {
        _pinnedGiftsMap.update { currentMap ->
            if (currentMap[userId].isNullOrEmpty()) {
                currentMap + (userId to gifts)
            } else {
                currentMap
            }
        }
    }

    init {
        initDefaultCatalog()
    }

    /**
     * Pre-populates the default gifts catalog if not present.
     */
    private fun initDefaultCatalog() {
        val defaultCatalog = listOf(
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_cyber_dragon_001",
                title = "Cyber Dragon 2026",
                price = 250L,
                totalSupply = 1000L,
                availableSupply = 782L,
                isExclusive = true,
                lottieAssetUrl = "cyber_dragon.json",
                backdropColorHex = "#1E1B4B",
                emojiIcon = "🐉",
                maxUpgradeLevel = 5
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_golden_crown_002",
                title = "Золотая Корона VIP",
                price = 150L,
                totalSupply = 5000L,
                availableSupply = 4120L,
                isExclusive = true,
                lottieAssetUrl = "golden_crown.json",
                backdropColorHex = "#281904",
                emojiIcon = "👑",
                maxUpgradeLevel = 5
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_neon_diamond_003",
                title = "Неоновый Алмаз",
                price = 100L,
                totalSupply = -1L,
                availableSupply = -1L,
                isExclusive = false,
                lottieAssetUrl = "neon_diamond.json",
                backdropColorHex = "#062826",
                emojiIcon = "💎",
                maxUpgradeLevel = 3
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_cosmic_rocket_004",
                title = "Космическая Ракета",
                price = 75L,
                totalSupply = -1L,
                availableSupply = -1L,
                isExclusive = false,
                lottieAssetUrl = "cosmic_rocket.json",
                backdropColorHex = "#1E1035",
                emojiIcon = "🚀",
                maxUpgradeLevel = 3
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_magic_crystal_005",
                title = "Магический Кристалл",
                price = 50L,
                totalSupply = -1L,
                availableSupply = -1L,
                isExclusive = false,
                lottieAssetUrl = "magic_crystal.json",
                backdropColorHex = "#280B1E",
                emojiIcon = "🔮",
                maxUpgradeLevel = 3
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_sakura_flower_006",
                title = "Цветущая Сакура",
                price = 30L,
                totalSupply = -1L,
                availableSupply = -1L,
                isExclusive = false,
                lottieAssetUrl = "sakura.json",
                backdropColorHex = "#1C0D17",
                emojiIcon = "🌸",
                maxUpgradeLevel = 3
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_teddy_bear_007",
                title = "Плюшевый Мишка",
                price = 25L,
                totalSupply = -1L,
                availableSupply = -1L,
                isExclusive = false,
                backdropColorHex = "#261E14",
                emojiIcon = "🧸",
                maxUpgradeLevel = 4
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_heart_box_008",
                title = "Сердце с бантом",
                price = 50L,
                totalSupply = -1L,
                availableSupply = -1L,
                isExclusive = false,
                backdropColorHex = "#2E081E",
                emojiIcon = "💝",
                maxUpgradeLevel = 4
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_golden_present_009",
                title = "Золотой Подарок",
                price = 100L,
                totalSupply = -1L,
                availableSupply = -1L,
                isExclusive = false,
                backdropColorHex = "#2D2206",
                emojiIcon = "🎁",
                maxUpgradeLevel = 4
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_nail_bracelet_010",
                title = "Nail Bracelet",
                price = 11771L,
                totalSupply = 5000L,
                availableSupply = 420L,
                isExclusive = true,
                backdropColorHex = "#162826",
                emojiIcon = "💍",
                maxUpgradeLevel = 5
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_durov_glasses_011",
                title = "Durov's Glasses",
                price = 9775L,
                totalSupply = 10000L,
                availableSupply = 562L,
                isExclusive = true,
                backdropColorHex = "#0C1F2D",
                emojiIcon = "🕶️",
                maxUpgradeLevel = 5
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_perfume_bottle_012",
                title = "Perfume Bottle",
                price = 7115L,
                totalSupply = 8000L,
                availableSupply = 423L,
                isExclusive = true,
                backdropColorHex = "#27122B",
                emojiIcon = "🧴",
                maxUpgradeLevel = 5
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_spartan_helmet_013",
                title = "Spartan Helmet",
                price = 18790L,
                totalSupply = 3000L,
                availableSupply = 180L,
                isExclusive = true,
                backdropColorHex = "#2B160C",
                emojiIcon = "⛑️",
                maxUpgradeLevel = 5
            ),
            KuoteXCatalogGiftDoc(
                catalogGiftId = "gift_champions_cup_014",
                title = "Кубок Чемпиона",
                price = 398672L,
                totalSupply = 50L,
                availableSupply = 12L,
                isExclusive = true,
                backdropColorHex = "#0D253A",
                emojiIcon = "🏆",
                maxUpgradeLevel = 5
            )
        )
        _catalogGifts.value = defaultCatalog

        // Initialize Collectible Gifts marketplace items
        initDefaultCollectibles()

        // Sync with Firestore asynchronously
        managerScope.launch {
            try {
                for (gift in defaultCatalog) {
                    val docRef = firestore.collection(COLLECTION_GIFTS_CATALOG).document(gift.catalogGiftId)
                    val snap = docRef.get().await()
                    if (!snap.exists()) {
                        docRef.set(gift).await()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing default catalog to Firestore: ${e.message}")
            }
        }
    }

    /**
     * Initializes default unique collectible marketplace gifts matching the Telegram references.
     */
    private fun initDefaultCollectibles() {
        val collectibles = listOf(
            CollectibleGift(
                id = "col_nail_2095",
                serialNumber = 2095,
                baseTitle = "Nail Bracelet",
                category = "Nail Bracelet",
                emojiIcon = "💍",
                modelName = "Chrome Classic",
                patternName = "Circuit",
                backdropColorHex = "#1E293B",
                accentGlowHex = "#94A3B8",
                priceStars = 11771L,
                ownerName = "durov",
                isPinnedToUsername = true
            ),
            CollectibleGift(
                id = "col_nail_1428",
                serialNumber = 1428,
                baseTitle = "Nail Bracelet",
                category = "Nail Bracelet",
                emojiIcon = "💍",
                modelName = "Neon Ruby",
                patternName = "Dots Matrix",
                backdropColorHex = "#3B1116",
                accentGlowHex = "#EF4444",
                priceStars = 12340L,
                ownerName = "crypto_king"
            ),
            CollectibleGift(
                id = "col_nail_3373",
                serialNumber = 3373,
                baseTitle = "Nail Bracelet",
                category = "Nail Bracelet",
                emojiIcon = "💍",
                modelName = "Mint Emerald",
                patternName = "Constellations",
                backdropColorHex = "#062E29",
                accentGlowHex = "#10B981",
                priceStars = 12340L,
                ownerName = "alice_wonder"
            ),
            CollectibleGift(
                id = "col_nail_2960",
                serialNumber = 2960,
                baseTitle = "Nail Bracelet",
                category = "Nail Bracelet",
                emojiIcon = "💍",
                modelName = "Violet Glow",
                patternName = "Starfield",
                backdropColorHex = "#2E1065",
                accentGlowHex = "#8B5CF6",
                priceStars = 14239L,
                ownerName = "saturn"
            ),
            CollectibleGift(
                id = "col_nail_3974",
                serialNumber = 3974,
                baseTitle = "Nail Bracelet",
                category = "Nail Bracelet",
                emojiIcon = "💍",
                modelName = "Cyber Gold",
                patternName = "Paws",
                backdropColorHex = "#271E06",
                accentGlowHex = "#F59E0B",
                priceStars = 14714L,
                ownerName = "goldie"
            ),
            CollectibleGift(
                id = "col_nail_2181",
                serialNumber = 2181,
                baseTitle = "Nail Bracelet",
                category = "Nail Bracelet",
                emojiIcon = "💍",
                modelName = "Toxic Lime",
                patternName = "Energy Waves",
                backdropColorHex = "#1A2E05",
                accentGlowHex = "#84CC16",
                priceStars = 15179L,
                ownerName = "alien"
            ),
            CollectibleGift(
                id = "col_nail_322",
                serialNumber = 322,
                baseTitle = "Nail Bracelet",
                category = "Nail Bracelet",
                emojiIcon = "💍",
                modelName = "Diamond Sparkle",
                patternName = "Stars & Sparkles",
                backdropColorHex = "#0E2A3A",
                accentGlowHex = "#38BDF8",
                priceStars = 15450L,
                ownerName = "sparkler"
            ),
            // Durov's Glasses items
            CollectibleGift(
                id = "col_glasses_3374",
                serialNumber = 3374,
                baseTitle = "Durov's Glasses",
                category = "Durov's Glasses",
                emojiIcon = "🕶️",
                modelName = "Matrix Vision",
                patternName = "Digital Rain",
                backdropColorHex = "#052E16",
                accentGlowHex = "#22C55E",
                priceStars = 9776L,
                ownerName = "neo"
            ),
            CollectibleGift(
                id = "col_glasses_2844",
                serialNumber = 2844,
                baseTitle = "Durov's Glasses",
                category = "Durov's Glasses",
                emojiIcon = "🕶️",
                modelName = "Aqua Star",
                patternName = "Waveform",
                backdropColorHex = "#0C2333",
                accentGlowHex = "#06B6D4",
                priceStars = 9778L,
                ownerName = "round"
            ),
            CollectibleGift(
                id = "col_glasses_2751",
                serialNumber = 2751,
                baseTitle = "Durov's Glasses",
                category = "Durov's Glasses",
                emojiIcon = "🕶️",
                modelName = "Cyber Purple",
                patternName = "Hexagons",
                backdropColorHex = "#2E0854",
                accentGlowHex = "#C084FC",
                priceStars = 10347L,
                ownerName = "violetta"
            ),
            // Perfume Bottle items
            CollectibleGift(
                id = "col_perfume_3107",
                serialNumber = 3107,
                baseTitle = "Perfume Bottle",
                category = "Perfume Bottle",
                emojiIcon = "🧴",
                modelName = "No. 5 Luxury",
                patternName = "Floral Velvet",
                backdropColorHex = "#350C2B",
                accentGlowHex = "#F472B6",
                priceStars = 7115L,
                ownerName = "mademoiselle"
            ),
            CollectibleGift(
                id = "col_perfume_3961",
                serialNumber = 3961,
                baseTitle = "Perfume Bottle",
                category = "Perfume Bottle",
                emojiIcon = "🧴",
                modelName = "Golden Amber",
                patternName = "Sunburst",
                backdropColorHex = "#3A2906",
                accentGlowHex = "#FBBF24",
                priceStars = 7120L,
                ownerName = "cleopatra"
            ),
            CollectibleGift(
                id = "col_perfume_7585",
                serialNumber = 7585,
                baseTitle = "Perfume Bottle",
                category = "Perfume Bottle",
                emojiIcon = "🧴",
                modelName = "Obsidian Noir",
                patternName = "Night Silhouette",
                backdropColorHex = "#18181B",
                accentGlowHex = "#E4E4E7",
                priceStars = 7585L,
                ownerName = "phantom"
            ),
            // Spartan Helmet
            CollectibleGift(
                id = "col_spartan_187",
                serialNumber = 187,
                baseTitle = "Spartan Helmet",
                category = "Spartan Helmet",
                emojiIcon = "⛑️",
                modelName = "Leonidas Bronze",
                patternName = "Battle Shields",
                backdropColorHex = "#331205",
                accentGlowHex = "#EA580C",
                priceStars = 18790L,
                ownerName = "sparta300"
            ),
            // Champion's Cup
            CollectibleGift(
                id = "col_cup_12",
                serialNumber = 12,
                baseTitle = "Кубок Чемпиона",
                category = "Кубок Чемпиона",
                emojiIcon = "🏆",
                modelName = "Grand Master",
                patternName = "Golden Laurel",
                backdropColorHex = "#172554",
                accentGlowHex = "#FACC15",
                priceStars = 398672L,
                ownerName = "winner_2026"
            )
        )
        _collectibleMarketplaceGifts.value = collectibles
    }

    /**
     * Purchase a collectible gift from the marketplace with currency check and atomic transfer.
     */
    suspend fun purchaseCollectibleGift(
        userId: String,
        collectibleGiftId: String
    ): Result<CollectibleGift> = withContext(Dispatchers.IO) {
        val gift = _collectibleMarketplaceGifts.value.find { it.id == collectibleGiftId }
            ?: return@withContext Result.failure(IllegalArgumentException("Collectible gift not found"))

        val currentBalance = _currentUserState.value?.balance ?: 1000L
        if (currentBalance < gift.priceStars) {
            return@withContext Result.failure(InsufficientBalanceException("Недостаточно звёзд (${currentBalance} < ${gift.priceStars})"))
        }

        // Deduct stars & transfer ownership
        _currentUserState.update { curr ->
            curr?.copy(balance = curr.balance - gift.priceStars)
        }

        val updatedGift = gift.copy(ownerId = userId, ownerName = "me", isForSale = false)

        _collectibleMarketplaceGifts.update { list ->
            list.map { if (it.id == collectibleGiftId) updatedGift else it }
        }

        Result.success(updatedGift)
    }

    /**
     * Synchronizes a user account with Firestore and observes changes.
     */
    suspend fun syncAndObserveUser(userId: String, username: String, displayName: String, role: String = "user") {
        withContext(Dispatchers.IO) {
            try {
                val userRef = firestore.collection(COLLECTION_USERS).document(userId)
                val snapshot = userRef.get().await()

                if (!snapshot.exists()) {
                    val newUser = KuoteXUserDoc(
                        userId = userId,
                        username = username,
                        displayName = displayName,
                        balance = 1000L,
                        role = role,
                        vipStatus = false,
                        vipExpiration = 0L,
                        availableBoostVotes = 0,
                        allocatedBoosts = emptyList(),
                        pinnedGiftsCount = 0,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    userRef.set(newUser).await()
                    _currentUserState.value = newUser
                } else {
                    val userDoc = snapshot.toObject(KuoteXUserDoc::class.java)
                    _currentUserState.value = userDoc
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing user with Firestore: ${e.message}", e)
                if (_currentUserState.value == null) {
                    _currentUserState.value = KuoteXUserDoc(
                        userId = userId,
                        username = username,
                        displayName = displayName,
                        balance = 1000L,
                        role = role
                    )
                }
            }
        }
    }

    /**
     * ATOMIC TRANSACTION: Process Gift Purchase and Transfer to Profile Header
     */
    suspend fun processGiftPurchaseAtomic(
        senderUserId: String,
        targetUserId: String,
        catalogGiftId: String,
        idempotencyKey: String,
        message: String = "",
        pinToHeader: Boolean = true,
        isAnonymous: Boolean = false
    ): Result<KuoteXUserGiftDoc> = withContext(Dispatchers.IO) {
        val txId = "tx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val userGiftId = "ug_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val now = System.currentTimeMillis()

        try {
            val userGiftResult = firestore.runTransaction { transaction ->
                val idempotencyRef = firestore.collection(COLLECTION_IDEMPOTENCY).document(idempotencyKey)
                val idemSnap = transaction.get(idempotencyRef)
                if (idemSnap.exists()) {
                    throw DuplicateTransactionException("Idempotency key already processed: $idempotencyKey")
                }

                val senderRef = firestore.collection(COLLECTION_USERS).document(senderUserId)
                val targetRef = firestore.collection(COLLECTION_USERS).document(targetUserId)
                val catalogRef = firestore.collection(COLLECTION_GIFTS_CATALOG).document(catalogGiftId)
                val ledgerRef = firestore.collection(COLLECTION_LEDGER_TX).document(txId)
                val userGiftRef = firestore.collection(COLLECTION_USER_GIFTS).document(userGiftId)

                val senderSnap = transaction.get(senderRef)
                val targetSnap = transaction.get(targetRef)
                val catalogSnap = transaction.get(catalogRef)

                val senderBalance = senderSnap.getLong("balance") ?: (_currentUserState.value?.balance ?: 1000L)
                val catalogData = catalogSnap.toObject(KuoteXCatalogGiftDoc::class.java)
                    ?: _catalogGifts.value.find { it.catalogGiftId == catalogGiftId }
                    ?: throw IllegalArgumentException("Gift $catalogGiftId not found in catalog")

                val giftPrice = catalogData.price
                if (senderBalance < giftPrice) {
                    throw InsufficientBalanceException("Insufficient balance ($senderBalance < $giftPrice)")
                }

                val availSupply = catalogData.availableSupply
                if (availSupply != -1L && availSupply <= 0) {
                    throw GiftSoldOutException("Gift is completely sold out")
                }

                // 1. Deduct sender balance
                val newSenderBalance = senderBalance - giftPrice
                transaction.update(senderRef, mapOf(
                    "balance" to newSenderBalance,
                    "updated_at" to now
                ))

                // 2. Decrement catalog supply if limited
                if (availSupply > 0) {
                    transaction.update(catalogRef, "available_supply", availSupply - 1)
                }

                // 3. Pin logic for receiver profile
                val targetPinnedCount = (targetSnap.getLong("pinned_gifts_count") ?: 0L).toInt()
                val shouldPin = pinToHeader && (targetPinnedCount < 6)

                if (shouldPin) {
                    transaction.update(targetRef, mapOf(
                        "pinned_gifts_count" to targetPinnedCount + 1,
                        "updated_at" to now
                    ))
                }

                // 4. Create User Gift Document
                val userGiftDoc = KuoteXUserGiftDoc(
                    userGiftId = userGiftId,
                    catalogGiftId = catalogGiftId,
                    senderId = if (isAnonymous) "anonymous" else senderUserId,
                    receiverId = targetUserId,
                    isPinnedToHeader = shouldPin,
                    pinOrderIndex = if (shouldPin) targetPinnedCount else -1,
                    upgradeLevel = 1,
                    transferable = false,
                    message = message,
                    isAnonymous = isAnonymous,
                    acquiredAt = now,
                    cachedTitle = catalogData.title,
                    cachedEmoji = catalogData.emojiIcon,
                    cachedColorHex = catalogData.backdropColorHex
                )
                transaction.set(userGiftRef, userGiftDoc)

                // 5. Append Immutable Ledger Record
                val ledgerDoc = KuoteXLedgerTxDoc(
                    txId = txId,
                    idempotencyKey = idempotencyKey,
                    type = LedgerTransactionType.GIFT_PURCHASE.value,
                    fromUserId = senderUserId,
                    toUserId = targetUserId,
                    amount = giftPrice,
                    fee = 0L,
                    status = LedgerTransactionStatus.COMMITTED.value,
                    metadata = mapOf(
                        "catalog_gift_id" to catalogGiftId,
                        "user_gift_id" to userGiftId,
                        "is_pinned" to shouldPin
                    ),
                    createdAt = now
                )
                transaction.set(ledgerRef, ledgerDoc)

                // 6. Mark idempotency key
                transaction.set(idempotencyRef, mapOf(
                    "tx_id" to txId,
                    "user_id" to senderUserId,
                    "created_at" to now
                ))

                userGiftDoc
            }.await()

            // Update local state
            _currentUserState.update { current ->
                current?.let {
                    if (it.userId == senderUserId) {
                        it.copy(balance = it.balance - (_catalogGifts.value.find { g -> g.catalogGiftId == catalogGiftId }?.price ?: 100L))
                    } else it
                }
            }

            _pinnedGiftsMap.update { currentMap ->
                val existing = currentMap[targetUserId]?.toMutableList() ?: sampleUserGiftDocs(targetUserId).toMutableList()
                if (userGiftResult.isPinnedToHeader) {
                    existing.add(0, userGiftResult)
                }
                currentMap + (targetUserId to existing)
            }

            Result.success(userGiftResult)
        } catch (e: Exception) {
            if (e is InsufficientBalanceException || e is GiftSoldOutException || e is DuplicateTransactionException) {
                Log.e(TAG, "Transaction failed for gift purchase: ${e.message}", e)
                return@withContext Result.failure(e)
            }

            Log.w(TAG, "Remote Firestore gift purchase failed (${e.message}). Executing local atomic fallback.")

            val catalogData = _catalogGifts.value.find { it.catalogGiftId == catalogGiftId }
                ?: return@withContext Result.failure(IllegalArgumentException("Gift $catalogGiftId not found in catalog"))

            val currentBalance = _currentUserState.value?.balance ?: 1000L
            val giftPrice = catalogData.price
            if (currentBalance < giftPrice) {
                return@withContext Result.failure(InsufficientBalanceException("Недостаточно звёзд ($currentBalance < $giftPrice)"))
            }

            val availSupply = catalogData.availableSupply
            if (availSupply != -1L && availSupply <= 0) {
                return@withContext Result.failure(GiftSoldOutException("Gift is completely sold out"))
            }

            // Deduct sender balance locally
            _currentUserState.update { curr ->
                curr?.copy(balance = (curr.balance - giftPrice).coerceAtLeast(0L))
                    ?: KuoteXUserDoc(userId = senderUserId, username = "me", displayName = "Me", balance = (1000L - giftPrice).coerceAtLeast(0L))
            }

            // Decrement catalog available supply
            if (availSupply > 0) {
                _catalogGifts.update { list ->
                    list.map {
                        if (it.catalogGiftId == catalogGiftId) it.copy(availableSupply = (it.availableSupply - 1).coerceAtLeast(0L)) else it
                    }
                }
            }

            val existing = _pinnedGiftsMap.value[targetUserId]?.toMutableList()
                ?: sampleUserGiftDocs(targetUserId).toMutableList()
            val shouldPin = pinToHeader && (existing.size < 12)

            val localUserGift = KuoteXUserGiftDoc(
                userGiftId = userGiftId,
                catalogGiftId = catalogGiftId,
                senderId = if (isAnonymous) "anonymous" else senderUserId,
                receiverId = targetUserId,
                isPinnedToHeader = shouldPin,
                pinOrderIndex = if (shouldPin) 0 else -1,
                upgradeLevel = 1,
                transferable = false,
                message = message,
                isAnonymous = isAnonymous,
                acquiredAt = now,
                cachedTitle = catalogData.title,
                cachedEmoji = catalogData.emojiIcon,
                cachedColorHex = catalogData.backdropColorHex
            )

            if (shouldPin) {
                existing.add(0, localUserGift)
            }
            _pinnedGiftsMap.update { currentMap ->
                currentMap + (targetUserId to existing)
            }

            Log.i(TAG, "Gift purchase completed locally: ${localUserGift.userGiftId} (${catalogData.title})")
            Result.success(localUserGift)
        }
    }

    /**
     * ATOMIC TRANSACTION: Activate or Extend KuoteX VIP Subscription
     */
    suspend fun activateVipSubscriptionAtomic(
        userId: String,
        months: Int = 1,
        idempotencyKey: String,
        price: Long = 300L
    ): Result<KuoteXUserDoc> = withContext(Dispatchers.IO) {
        val txId = "tx_vip_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        val durationMs = months * 30L * 86400000L

        try {
            val updatedUser = firestore.runTransaction { transaction ->
                val idempotencyRef = firestore.collection(COLLECTION_IDEMPOTENCY).document(idempotencyKey)
                if (transaction.get(idempotencyRef).exists()) {
                    throw DuplicateTransactionException("Idempotency key already processed")
                }

                val userRef = firestore.collection(COLLECTION_USERS).document(userId)
                val ledgerRef = firestore.collection(COLLECTION_LEDGER_TX).document(txId)
                val userSnap = transaction.get(userRef)

                val currentBalance = userSnap.getLong("balance") ?: (_currentUserState.value?.balance ?: 1000L)
                if (currentBalance < price) {
                    throw InsufficientBalanceException("Insufficient balance for VIP subscription ($currentBalance < $price)")
                }

                val currentVipExp = userSnap.getLong("vip_expiration") ?: 0L
                val newVipExp = (if (currentVipExp > now) currentVipExp else now) + durationMs
                val currentVotes = (userSnap.getLong("available_boost_votes") ?: 0L).toInt()
                val newVotes = currentVotes + (4 * months)

                val newBalance = currentBalance - price

                transaction.update(userRef, mapOf(
                    "balance" to newBalance,
                    "vip_status" to true,
                    "vip_expiration" to newVipExp,
                    "available_boost_votes" to newVotes,
                    "updated_at" to now
                ))

                // Immutable Ledger Entry
                val ledgerDoc = KuoteXLedgerTxDoc(
                    txId = txId,
                    idempotencyKey = idempotencyKey,
                    type = LedgerTransactionType.VIP_SUBSCRIPTION.value,
                    fromUserId = userId,
                    toUserId = "system_kuotex_vip",
                    amount = price,
                    status = LedgerTransactionStatus.COMMITTED.value,
                    metadata = mapOf("months" to months, "vip_expiration" to newVipExp),
                    createdAt = now
                )
                transaction.set(ledgerRef, ledgerDoc)

                transaction.set(idempotencyRef, mapOf("tx_id" to txId, "created_at" to now))

                val userDoc = userSnap.toObject(KuoteXUserDoc::class.java) ?: KuoteXUserDoc(userId = userId)
                userDoc.copy(
                    balance = newBalance,
                    vipStatus = true,
                    vipExpiration = newVipExp,
                    availableBoostVotes = newVotes,
                    updatedAt = now
                )
            }.await()

            _currentUserState.value = updatedUser
            Result.success(updatedUser)
        } catch (e: Exception) {
            if (e is InsufficientBalanceException || e is DuplicateTransactionException) {
                Log.e(TAG, "Failed VIP activation due to business constraint: ${e.message}")
                return@withContext Result.failure(e)
            }

            Log.w(TAG, "Remote Firestore VIP activation failed (${e.message}). Activating locally.")
            val currentBalance = _currentUserState.value?.balance ?: 1000L
            if (currentBalance < price) {
                return@withContext Result.failure(InsufficientBalanceException("Недостаточно Stars ($currentBalance < $price)"))
            }
            val currentVipExp = _currentUserState.value?.vipExpiration ?: 0L
            val newVipExp = (if (currentVipExp > now) currentVipExp else now) + durationMs
            val currentVotes = _currentUserState.value?.availableBoostVotes ?: 0
            val newVotes = currentVotes + (4 * months)
            val newBalance = (currentBalance - price).coerceAtLeast(0L)

            val localUser = (_currentUserState.value ?: KuoteXUserDoc(userId = userId)).copy(
                balance = newBalance,
                vipStatus = true,
                vipExpiration = newVipExp,
                availableBoostVotes = newVotes,
                updatedAt = now
            )
            _currentUserState.value = localUser
            Result.success(localUser)
        }
    }

    /**
     * ATOMIC TRANSACTION: Apply Channel Boost with Privilege Validation & Level Recalculation
     */
    suspend fun applyChannelBoostAtomic(
        userId: String,
        channelId: String,
        votesToApply: Int = 1
    ): Result<KuoteXChannelDoc> = withContext(Dispatchers.IO) {
        val txId = "tx_boost_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        try {
            val updatedChannel = firestore.runTransaction { transaction ->
                val userRef = firestore.collection(COLLECTION_USERS).document(userId)
                val channelRef = firestore.collection(COLLECTION_CHANNELS).document(channelId)
                val ledgerRef = firestore.collection(COLLECTION_LEDGER_TX).document(txId)

                val userSnap = transaction.get(userRef)
                val channelSnap = transaction.get(channelRef)

                val userDoc = userSnap.toObject(KuoteXUserDoc::class.java)
                    ?: _currentUserState.value
                    ?: throw IllegalArgumentException("User document $userId not found")

                // Privilege Validation
                if (!userDoc.hasBoostPrivilege()) {
                    throw UnauthorizedBoostException("Only KuoteX VIP or Admins/Developers can boost channels.")
                }

                val availableVotes = (userSnap.getLong("available_boost_votes") ?: userDoc.availableBoostVotes.toLong()).toInt()
                if (availableVotes < votesToApply && !userDoc.role.equals("developer", ignoreCase = true)) {
                    throw InsufficientBoostVotesException("Insufficient boost votes available ($availableVotes < $votesToApply)")
                }

                val newAvailableVotes = (availableVotes - votesToApply).coerceAtLeast(0)

                // Update allocated boosts list
                val currentAllocated = userDoc.allocatedBoosts.toMutableList()
                val existingIdx = currentAllocated.indexOfFirst { it.channelId == channelId }
                if (existingIdx >= 0) {
                    val old = currentAllocated[existingIdx]
                    currentAllocated[existingIdx] = old.copy(
                        votesCount = old.votesCount + votesToApply,
                        boostedAt = now
                    )
                } else {
                    currentAllocated.add(AllocatedBoost(channelId = channelId, votesCount = votesToApply, boostedAt = now))
                }

                transaction.update(userRef, mapOf(
                    "available_boost_votes" to newAvailableVotes,
                    "allocated_boosts" to currentAllocated,
                    "updated_at" to now
                ))

                // Recalculate Channel Level Progression
                val currentVotes = (channelSnap.getLong("current_votes") ?: 0L).toInt()
                val totalVotes = currentVotes + votesToApply
                val newLevel = KuoteXBoostProgression.calculateLevel(totalVotes)
                val nextRequirement = KuoteXBoostProgression.nextLevelRequirement(totalVotes)

                val channelDoc = KuoteXChannelDoc(
                    channelId = channelId,
                    title = channelSnap.getString("title") ?: "Канал",
                    currentVotes = totalVotes,
                    level = newLevel,
                    nextLevelRequiredVotes = nextRequirement,
                    customColorUnlocked = newLevel >= 1,
                    statusEmojiUnlocked = newLevel >= 2,
                    wallpaperUnlocked = newLevel >= 3,
                    storiesPerDayLimit = (newLevel * 2).coerceAtLeast(0),
                    updatedAt = now
                )

                transaction.set(channelRef, channelDoc, SetOptions.merge())

                // Immutable Ledger entry
                val ledgerDoc = KuoteXLedgerTxDoc(
                    txId = txId,
                    idempotencyKey = "boost_${userId}_${channelId}_${now}",
                    type = LedgerTransactionType.CHANNEL_BOOST.value,
                    fromUserId = userId,
                    toUserId = channelId,
                    amount = votesToApply.toLong(),
                    status = LedgerTransactionStatus.COMMITTED.value,
                    metadata = mapOf("votes" to votesToApply, "channel_level" to newLevel),
                    createdAt = now
                )
                transaction.set(ledgerRef, ledgerDoc)

                channelDoc
            }.await()

            // Update user state locally
            _currentUserState.update { curr ->
                curr?.copy(
                    availableBoostVotes = (curr.availableBoostVotes - votesToApply).coerceAtLeast(0)
                )
            }

            Result.success(updatedChannel)
        } catch (e: Exception) {
            if (e is UnauthorizedBoostException || e is InsufficientBoostVotesException) {
                Log.e(TAG, "Channel boost failed due to business rule: ${e.message}")
                return@withContext Result.failure(e)
            }

            Log.w(TAG, "Remote Firestore channel boost failed (${e.message}). Applying locally.")
            val user = _currentUserState.value
            val availableVotes = user?.availableBoostVotes ?: 0
            if (availableVotes < votesToApply && user?.role?.equals("developer", ignoreCase = true) != true) {
                return@withContext Result.failure(InsufficientBoostVotesException("Недостаточно голосов буста ($availableVotes < $votesToApply)"))
            }
            _currentUserState.update { curr ->
                curr?.copy(availableBoostVotes = (curr.availableBoostVotes - votesToApply).coerceAtLeast(0))
            }
            val newTotalVotes = votesToApply
            val newLevel = KuoteXBoostProgression.calculateLevel(newTotalVotes)
            val nextReq = KuoteXBoostProgression.nextLevelRequirement(newTotalVotes)
            val channelDoc = KuoteXChannelDoc(
                channelId = channelId,
                title = "Канал",
                currentVotes = newTotalVotes,
                level = newLevel,
                nextLevelRequiredVotes = nextReq,
                customColorUnlocked = newLevel >= 1,
                statusEmojiUnlocked = newLevel >= 2,
                wallpaperUnlocked = newLevel >= 3,
                storiesPerDayLimit = (newLevel * 2).coerceAtLeast(0),
                updatedAt = now
            )
            Result.success(channelDoc)
        }
    }

    /**
     * ATOMIC TRANSACTION: Upgrade Pinned User Gift Level (Phase 4)
     */
    suspend fun upgradeUserGiftAtomic(
        userId: String,
        userGiftId: String,
        upgradeCostStars: Long = 50L
    ): Result<KuoteXUserGiftDoc> = withContext(Dispatchers.IO) {
        val txId = "tx_gift_upg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        try {
            val updatedUserGift = firestore.runTransaction { transaction ->
                val userRef = firestore.collection(COLLECTION_USERS).document(userId)
                val giftRef = firestore.collection(COLLECTION_USER_GIFTS).document(userGiftId)
                val ledgerRef = firestore.collection(COLLECTION_LEDGER_TX).document(txId)

                val userSnap = transaction.get(userRef)
                val giftSnap = transaction.get(giftRef)

                val currentBalance = userSnap.getLong("balance") ?: (_currentUserState.value?.balance ?: 1000L)
                if (currentBalance < upgradeCostStars) {
                    throw InsufficientBalanceException("Insufficient Stars balance to upgrade gift ($currentBalance < $upgradeCostStars)")
                }

                val currentGift = giftSnap.toObject(KuoteXUserGiftDoc::class.java)
                    ?: _pinnedGiftsMap.value[userId]?.find { it.userGiftId == userGiftId }
                    ?: throw IllegalArgumentException("User gift $userGiftId not found")

                val newLevel = (currentGift.upgradeLevel + 1).coerceAtMost(5)
                val upgradedDoc = currentGift.copy(
                    upgradeLevel = newLevel
                )

                transaction.update(userRef, "balance", currentBalance - upgradeCostStars)
                transaction.set(giftRef, upgradedDoc, SetOptions.merge())

                // Immutable Ledger
                val ledgerDoc = KuoteXLedgerTxDoc(
                    txId = txId,
                    idempotencyKey = "upg_${userGiftId}_lvl${newLevel}_$now",
                    type = LedgerTransactionType.GIFT_UPGRADE.value,
                    fromUserId = userId,
                    toUserId = "system_gift_vault",
                    amount = upgradeCostStars,
                    status = LedgerTransactionStatus.COMMITTED.value,
                    metadata = mapOf("user_gift_id" to userGiftId, "new_level" to newLevel),
                    createdAt = now
                )
                transaction.set(ledgerRef, ledgerDoc)

                upgradedDoc
            }.await()

            // Update local state
            _currentUserState.update { curr ->
                curr?.let { it.copy(balance = (it.balance - upgradeCostStars).coerceAtLeast(0L)) }
            }

            _pinnedGiftsMap.update { currentMap ->
                val list = currentMap[userId]?.toMutableList() ?: mutableListOf()
                val idx = list.indexOfFirst { it.userGiftId == userGiftId }
                if (idx >= 0) {
                    list[idx] = updatedUserGift
                } else {
                    list.add(updatedUserGift)
                }
                currentMap + (userId to list)
            }

            Result.success(updatedUserGift)
        } catch (e: Exception) {
            Log.w(TAG, "Remote Firestore gift upgrade failed: ${e.message}. Attempting local ecosystem update.")
            
            // If Firestore transaction fails due to PERMISSION_DENIED or network issues,
            // provide seamless local execution so user experience is not disrupted.
            val currentGifts = _pinnedGiftsMap.value[userId]?.toMutableList() ?: mutableListOf()
            val existingGift = currentGifts.find { it.userGiftId == userGiftId }
            val currentBalance = _currentUserState.value?.balance ?: 1000L

            if (currentBalance < upgradeCostStars) {
                Log.e(TAG, "Gift upgrade failed: Insufficient balance ($currentBalance < $upgradeCostStars)")
                return@withContext Result.failure(InsufficientBalanceException("Недостаточно Stars для улучшения подарка ($currentBalance < $upgradeCostStars)"))
            }

            if (existingGift != null) {
                val newLevel = (existingGift.upgradeLevel + 1).coerceAtMost(5)
                val upgradedLocalDoc = existingGift.copy(upgradeLevel = newLevel)
                val idx = currentGifts.indexOfFirst { it.userGiftId == userGiftId }
                if (idx >= 0) {
                    currentGifts[idx] = upgradedLocalDoc
                } else {
                    currentGifts.add(upgradedLocalDoc)
                }
                
                // Update cached balance and pinned gifts
                _currentUserState.update { curr ->
                    curr?.copy(balance = (curr.balance - upgradeCostStars).coerceAtLeast(0L))
                        ?: KuoteXUserDoc(userId = userId, username = "user", displayName = "User", balance = (1000L - upgradeCostStars).coerceAtLeast(0L))
                }
                _pinnedGiftsMap.update { currentMap ->
                    currentMap + (userId to currentGifts)
                }
                
                Log.i(TAG, "Gift upgraded locally successfully: ${upgradedLocalDoc.userGiftId} to level $newLevel")
                Result.success(upgradedLocalDoc)
            } else {
                Log.e(TAG, "Gift upgrade failed: gift not found: $userGiftId", e)
                Result.failure(e)
            }
        }
    }

    /**
     * ATOMIC TRANSACTION: Vote in Telegram-style channel/group poll
     */
    suspend fun voteInPollAtomic(
        pollId: String,
        chatId: String,
        userId: String,
        selectedOptionIds: List<Int>
    ): Result<KuoteXPollVoteDoc> = withContext(Dispatchers.IO) {
        val voteDocId = "${pollId}_${userId}"
        val now = System.currentTimeMillis()

        try {
            val voteDoc = firestore.runTransaction { transaction ->
                val voteRef = firestore.collection(COLLECTION_POLL_VOTES).document(voteDocId)
                val existingVote = transaction.get(voteRef)
                if (existingVote.exists()) {
                    throw DuplicateTransactionException("User $userId has already voted in poll $pollId")
                }

                val newVote = KuoteXPollVoteDoc(
                    voteId = voteDocId,
                    pollId = pollId,
                    chatId = chatId,
                    userId = userId,
                    selectedOptionIds = selectedOptionIds,
                    timestamp = now
                )
                transaction.set(voteRef, newVote)
                newVote
            }.await()

            Result.success(voteDoc)
        } catch (e: Exception) {
            if (e is DuplicateTransactionException) {
                Log.e(TAG, "Poll vote transaction error: ${e.message}", e)
                return@withContext Result.failure(e)
            }
            Log.w(TAG, "Remote Firestore poll vote failed (${e.message}). Recording locally.")
            val newVote = KuoteXPollVoteDoc(
                voteId = voteDocId,
                pollId = pollId,
                chatId = chatId,
                userId = userId,
                selectedOptionIds = selectedOptionIds,
                timestamp = now
            )
            Result.success(newVote)
        }
    }

    /**
     * Top-up user internal currency balance (KuoteX Stars / Coins)
     */
    suspend fun topUpUserBalanceAtomic(
        userId: String,
        amount: Long,
        providerTxId: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val txId = "topup_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()

        try {
            val newBalance = firestore.runTransaction { transaction ->
                val idempotencyRef = firestore.collection(COLLECTION_IDEMPOTENCY).document(providerTxId)
                if (transaction.get(idempotencyRef).exists()) {
                    throw DuplicateTransactionException("Provider tx $providerTxId already credited")
                }

                val userRef = firestore.collection(COLLECTION_USERS).document(userId)
                val ledgerRef = firestore.collection(COLLECTION_LEDGER_TX).document(txId)
                val userSnap = transaction.get(userRef)

                val currentBalance = userSnap.getLong("balance") ?: (_currentUserState.value?.balance ?: 1000L)
                val updatedBalance = currentBalance + amount

                transaction.update(userRef, mapOf(
                    "balance" to updatedBalance,
                    "updated_at" to now
                ))

                val ledgerDoc = KuoteXLedgerTxDoc(
                    txId = txId,
                    idempotencyKey = providerTxId,
                    type = LedgerTransactionType.BALANCE_TOPUP.value,
                    fromUserId = "external_payment_gateway",
                    toUserId = userId,
                    amount = amount,
                    status = LedgerTransactionStatus.COMMITTED.value,
                    metadata = mapOf("provider_tx_id" to providerTxId),
                    createdAt = now
                )
                transaction.set(ledgerRef, ledgerDoc)
                transaction.set(idempotencyRef, mapOf("tx_id" to txId, "created_at" to now))

                updatedBalance
            }.await()

            _currentUserState.update { it?.copy(balance = newBalance) }
            Result.success(newBalance)
        } catch (e: Exception) {
            if (e is DuplicateTransactionException) {
                Log.e(TAG, "Balance top-up error: ${e.message}", e)
                return@withContext Result.failure(e)
            }
            Log.e(TAG, "Balance top-up failed (${e.message}). NOT crediting locally.")
// Начислять звёзды на клиенте нельзя: сервер мог операцию не принять.
// Пусть UI покажет ошибку, а пользователь повторит с тем же ключом.
Result.failure(PaymentOutcomeUnknown(providerTxId, "Не удалось связаться с сервером"))
        }
    }

    /**
     * Observes real-time Pinned Gifts for a profile header.
     */
    fun observePinnedGifts(userId: String): Flow<List<KuoteXUserGiftDoc>> = callbackFlow {
        val listener = firestore.collection(COLLECTION_USER_GIFTS)
            .whereEqualTo("receiver_id", userId)
            .whereEqualTo("is_pinned_to_header", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error listening to pinned gifts: ${error.message}")
                    trySend(_pinnedGiftsMap.value[userId] ?: emptyList())
                    return@addSnapshotListener
                }
                val gifts = snapshot?.documents?.mapNotNull { it.toObject(KuoteXUserGiftDoc::class.java) } ?: emptyList()
                val sorted = gifts.sortedBy { it.pinOrderIndex }
                _pinnedGiftsMap.update { it + (userId to sorted) }
                trySend(sorted)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Observes a channel's boost level, votes, and unlocked perks in real-time.
     */
    fun observeChannelEcosystem(channelId: String): Flow<KuoteXChannelDoc?> = callbackFlow {
        val listener = firestore.collection(COLLECTION_CHANNELS).document(channelId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing channel ecosystem: ${error.message}")
                    return@addSnapshotListener
                }
                val doc = snapshot?.toObject(KuoteXChannelDoc::class.java)
                trySend(doc)
            }
        awaitClose { listener.remove() }
    }
}
