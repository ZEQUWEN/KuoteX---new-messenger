package com.example.ui.gifts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.ecosystem.KuoteXCatalogGiftDoc
import com.example.data.ecosystem.KuoteXEcosystemFirestoreManager
import com.example.ui.AppViewModel
import kotlinx.coroutines.launch

/**
 * GiftMarketplaceScreen
 *
 * Full-featured marketplace for buying regular and rare collectible gifts
 * with in-app Stars, currency toggle (⭐, $, ₽, €), search, category filters,
 * and direct gift sending flow to other users or own profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftMarketplaceScreen(
    viewModel: AppViewModel,
    navController: NavController,
    ecosystemManager: KuoteXEcosystemFirestoreManager = KuoteXEcosystemFirestoreManager,
    targetUserId: String? = null,
    targetUserName: String? = null
) {
    val catalogGifts by ecosystemManager.catalogGifts.collectAsState()
    val collectibleGifts by ecosystemManager.collectibleMarketplaceGifts.collectAsState()
    val currentUser by ecosystemManager.currentUserState.collectAsState()
    val activeCurrency by ecosystemManager.selectedCurrency.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Все / Маркетплейс, 1: Обычные, 2: Коллекционные
    var selectedCategoryFilter by remember { mutableStateOf("Все") }
    var showCurrencyMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // State for purchasing dialog / sheet
    var selectedCatalogGiftForBuy by remember { mutableStateOf<KuoteXCatalogGiftDoc?>(null) }
    var selectedCollectibleForBuy by remember { mutableStateOf<CollectibleGift?>(null) }

    val userBalance = currentUser?.balance ?: 1000L

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (!targetUserName.isNullOrBlank()) "Подарок для $targetUserName" else "Магазин подарков",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Баланс: ${activeCurrency.formatPrice(userBalance)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFD54F)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Currency switcher button
                    Box {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCurrencyMenu = true },
                            color = Color(0xFF282538),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = activeCurrency.symbol,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD54F)
                                )
                                Text(
                                    text = activeCurrency.title,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCurrencyMenu,
                            onDismissRequest = { showCurrencyMenu = false }
                        ) {
                            CurrencyType.entries.forEach { currency ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("${currency.symbol}  ${currency.title}")
                                            if (currency == activeCurrency) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF8B5CF6)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        ecosystemManager.setCurrency(currency)
                                        showCurrencyMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0E17))
            )
        },
        containerColor = Color(0xFF0F0E17)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs: Каталог, Маркетплейс (Коллекционные), Обычные
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF161424),
                contentColor = Color.White,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color(0xFF8B5CF6),
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Маркетплейс", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Коллекционные (Лимитированные)", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Обычные подарки", fontWeight = FontWeight.SemiBold) }
                )
            }

            // Category filter chips for collectible items
            if (selectedTabIndex == 0 || selectedTabIndex == 1) {
                val categories = listOf("Все", "Nail Bracelet", "Durov's Glasses", "Perfume Bottle", "Кубок Чемпиона", "Spartan Helmet")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.take(4).forEach { cat ->
                        val isSelected = selectedCategoryFilter == cat
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedCategoryFilter = cat },
                            color = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF221F33),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Main Content Grid
            when (selectedTabIndex) {
                0 -> {
                    // Marketplace combined grid: Rare limited edition items & Exclusive items with Telegram-style green "маркет" badge
                    val filteredCollectibles = if (selectedCategoryFilter == "Все") {
                        collectibleGifts
                    } else {
                        collectibleGifts.filter { it.category == selectedCategoryFilter }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredCollectibles, key = { it.id }) { item ->
                            CollectibleMarketplaceCard(
                                gift = item,
                                activeCurrency = activeCurrency,
                                onClick = { selectedCollectibleForBuy = item }
                            )
                        }
                    }
                }
                1 -> {
                    // Only Collectibles
                    val filteredCollectibles = if (selectedCategoryFilter == "Все") {
                        collectibleGifts
                    } else {
                        collectibleGifts.filter { it.category == selectedCategoryFilter }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredCollectibles, key = { it.id }) { item ->
                            CollectibleMarketplaceCard(
                                gift = item,
                                activeCurrency = activeCurrency,
                                onClick = { selectedCollectibleForBuy = item }
                            )
                        }
                    }
                }
                2 -> {
                    // Regular & Catalog Gifts
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(catalogGifts, key = { it.catalogGiftId }) { gift ->
                            CatalogGiftCard(
                                gift = gift,
                                activeCurrency = activeCurrency,
                                onClick = { selectedCatalogGiftForBuy = gift }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Purchasing / Sending Catalog Gift
    selectedCatalogGiftForBuy?.let { gift ->
        PurchaseGiftBottomSheet(
            gift = gift,
            activeCurrency = activeCurrency,
            targetUserId = targetUserId ?: "me",
            targetUserName = targetUserName ?: "Себе в профиль",
            onDismiss = { selectedCatalogGiftForBuy = null },
            onConfirmPurchase = { message, isAnonymous, pinToHeader ->
                selectedCatalogGiftForBuy = null
                scope.launch {
                    val activeUser = currentUser?.userId ?: "me"
                    val receiver = targetUserId ?: activeUser
                    val idempotencyKey = "buy_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
                    val result = ecosystemManager.processGiftPurchaseAtomic(
                        senderUserId = activeUser,
                        targetUserId = receiver,
                        catalogGiftId = gift.catalogGiftId,
                        idempotencyKey = idempotencyKey,
                        message = message,
                        isAnonymous = isAnonymous,
                        pinToHeader = pinToHeader
                    )
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Подарок \"${gift.title}\" успешно отправлен!")
                    } else {
                        snackbarHostState.showSnackbar("Ошибка: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }

    // Modal Sheet for Purchasing Rare Collectible Gift
    selectedCollectibleForBuy?.let { collectible ->
        PurchaseCollectibleBottomSheet(
            collectible = collectible,
            activeCurrency = activeCurrency,
            targetUserId = targetUserId ?: "me",
            onDismiss = { selectedCollectibleForBuy = null },
            onConfirmPurchase = {
                val toBuy = collectible
                selectedCollectibleForBuy = null
                scope.launch {
                    val activeUser = currentUser?.userId ?: "me"
                    val result = ecosystemManager.purchaseCollectibleGift(activeUser, toBuy.id)
                    if (result.isSuccess) {
                        // Pin to username option
                        ecosystemManager.pinCollectibleToUsername(activeUser, result.getOrNull())
                        snackbarHostState.showSnackbar("Коллекционный подарок #${toBuy.serialNumber} куплен и закреплен!")
                    } else {
                        snackbarHostState.showSnackbar("Ошибка покупки: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }
}

/**
 * Card for Collectible Limited-Edition Gifts with green "маркет" badge and serial number,
 * matching screenshots from Telegram Gifts Marketplace.
 */
@Composable
private fun CollectibleMarketplaceCard(
    gift: CollectibleGift,
    activeCurrency: CurrencyType,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = gift.parsedBackdropColor),
        border = BorderStroke(1.dp, gift.parsedAccentColor.copy(alpha = 0.35f))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Serial number label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = gift.formattedSerialNumber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Center Icon / Graphic
                Text(
                    text = gift.emojiIcon,
                    fontSize = 42.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Price with Star or active currency
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "⭐",
                            fontSize = 11.sp
                        )
                        Text(
                            text = activeCurrency.formatPrice(gift.priceStars).replace(" ⭐", "+"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Green "маркет" badge in top right corner (exact Telegram visual reference)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF22C55E) // Green badge
            ) {
                Text(
                    text = "маркет",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Card for Regular Catalog Gifts
 */
@Composable
private fun CatalogGiftCard(
    gift: KuoteXCatalogGiftDoc,
    activeCurrency: CurrencyType,
    onClick: () -> Unit
) {
    val bgColor = try {
        Color(android.graphics.Color.parseColor(gift.backdropColorHex))
    } catch (_: Exception) {
        Color(0xFF1E1B4B)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = gift.emojiIcon, fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = gift.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = Color(0xFFFFD54F).copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = activeCurrency.formatPrice(gift.price),
                    color = Color(0xFFFFD54F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * BottomSheet to buy or send a catalog gift to someone
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseGiftBottomSheet(
    gift: KuoteXCatalogGiftDoc,
    activeCurrency: CurrencyType,
    targetUserId: String,
    targetUserName: String,
    onDismiss: () -> Unit,
    onConfirmPurchase: (message: String, isAnonymous: Boolean, pinToHeader: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var customMessage by remember { mutableStateOf("") }
    var isAnonymous by remember { mutableStateOf(false) }
    var pinToHeader by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161424),
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = gift.emojiIcon, fontSize = 60.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = gift.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Получатель: $targetUserName",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = customMessage,
                onValueChange = { customMessage = it },
                label = { Text("Поздравительное сообщение") },
                placeholder = { Text("Напишите теплые слова...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = Color(0xFF8B5CF6),
                    unfocusedLabelColor = Color.LightGray
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Options: Anonymous & Pin to Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAnonymous = !isAnonymous }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Скрыть мое имя (Анонимно)", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Имя отправителя будет скрыто от других", fontSize = 11.sp, color = Color.Gray)
                }
                androidx.compose.material3.Switch(
                    checked = isAnonymous,
                    onCheckedChange = { isAnonymous = it }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pinToHeader = !pinToHeader }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Закрепить в профиле", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Подарок появится в шапке профиля", fontSize = 11.sp, color = Color.Gray)
                }
                androidx.compose.material3.Switch(
                    checked = pinToHeader,
                    onCheckedChange = { pinToHeader = it }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            sheetState.hide()
                        } catch (_: Exception) {}
                        onConfirmPurchase(customMessage, isAnonymous, pinToHeader)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Отправить за ${activeCurrency.formatPrice(gift.price)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * BottomSheet to buy a limited rare collectible gift
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseCollectibleBottomSheet(
    collectible: CollectibleGift,
    activeCurrency: CurrencyType,
    targetUserId: String,
    onDismiss: () -> Unit,
    onConfirmPurchase: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161424),
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(collectible.parsedBackdropColor)
                    .border(2.dp, collectible.parsedAccentColor, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = collectible.emojiIcon, fontSize = 48.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${collectible.baseTitle} ${collectible.formattedSerialNumber}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Модель: ${collectible.modelName} • Узор: ${collectible.patternName}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF221F33),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Стоимость в звёздах", color = Color.Gray, fontSize = 13.sp)
                        Text("${collectible.priceStars} ⭐", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Эквивалент в ${activeCurrency.title}", color = Color.Gray, fontSize = 13.sp)
                        Text(activeCurrency.formatPrice(collectible.priceStars), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Закрепление у никнейма", color = Color.Gray, fontSize = 13.sp)
                        Text("Доступно сразу", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            sheetState.hide()
                        } catch (_: Exception) {}
                        onConfirmPurchase()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Купить за ${activeCurrency.formatPrice(collectible.priceStars)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
