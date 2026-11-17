@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.stars

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.ecosystem.KuoteXEcosystemFirestoreManager
import com.example.ui.AppViewModel
import com.example.ui.channel.ChannelDirectMessageManager
import com.example.ui.channel.StarsBundlePackage
import com.example.ui.popBackStackSafe

/**
 * Dedicated Telegram-style Buy Stars screen ("Звёзды") launched from Suggested Post
 * or balance pills to top up user Stars with instant atomic updates.
 */
@Composable
fun BuyStarsScreen(
    viewModel: AppViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val userState by KuoteXEcosystemFirestoreManager.currentUserState.collectAsStateWithLifecycle()
    var purchasingPackId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeAccount?.id) {
        KuoteXEcosystemFirestoreManager.initLocalBalanceCache(context)
        val uid = activeAccount?.id?.ifBlank { "me" } ?: "me"
        KuoteXEcosystemFirestoreManager.ensureUserStateLoaded(
            userId = uid,
            username = activeAccount?.username ?: "user",
            displayName = activeAccount?.displayName ?: "Пользователь"
        )
    }

    val currentBalance = userState?.balance ?: 0L
    val packages = remember { ChannelDirectMessageManager.defaultStarsPackages }

    Scaffold(
        containerColor = Color(0xFF0A0D12),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0D12)),
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStackSafe() },
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(48.dp)
                            .testTag("buy_stars_back_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1F242F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Color.White
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = "Звёзды KuoteX",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2B2142), Color(0xFF171A24))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFB800).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Ваш баланс",
                            color = Color(0xFF9EA4B3),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "⭐ $currentBalance",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("buy_stars_balance_text")
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Используйте звёзды для предложенных постов, сообщений каналам и цифровых подарков.",
                            color = Color(0xFFB5BAC9),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item {
                Text(
                    text = "ПОПОЛНИТЬ БАЛАНС",
                    color = Color(0xFFB072FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            items(packages, key = { it.id }) { pack ->
                StarBundleCard(
                    pack = pack,
                    isPurchasing = purchasingPackId == pack.id,
                    onBuyClick = {
                        if (purchasingPackId != null) return@StarBundleCard
                        purchasingPackId = pack.id
                        viewModel.purchaseStarsBundle(
                            amount = pack.stars,
                            packageId = pack.id
                        ) { result ->
                            purchasingPackId = null
                            if (result.isSuccess) {
                                Toast.makeText(
                                    context,
                                    "Зачислено ⭐ ${pack.stars}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    result.exceptionOrNull()?.message ?: "Не удалось пополнить звёзды",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StarBundleCard(
    pack: StarsBundlePackage,
    isPurchasing: Boolean,
    onBuyClick: () -> Unit
) {
    Surface(
        onClick = onBuyClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161B24),
        border = BorderStroke(
            width = if (pack.isPopular) 1.5.dp else 1.dp,
            color = if (pack.isPopular) Color(0xFF9F6BFF) else Color.White.copy(alpha = 0.06f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("buy_stars_pack_${pack.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFB800).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${pack.stars} звёзд",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!pack.bonusLabel.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF9F6BFF).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = pack.bonusLabel,
                                color = Color(0xFFB794F4),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "Мгновенное зачисление на баланс",
                    color = Color(0xFF858C99),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF8E5AE2)
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 72.dp, minHeight = 36.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = pack.priceUsd,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
