package com.example.ui.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ui.AppViewModel
import kotlinx.coroutines.launch

/**
 * ChannelBoostScreen
 * Telegram-style Channel Boost & Level Progression screen with interactive voting,
 * level perks timeline, boost link sharing, and top booster rankings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelBoostScreen(
    viewModel: AppViewModel,
    chatId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val customizationsMap by ChannelCustomizationManager.getCustomizationFlow(chatId).collectAsState()
    val customization = customizationsMap[chatId] ?: ChannelCustomization(chatId = chatId)

    val boostersMap by ChannelCustomizationManager.getBoostersFlow(chatId).collectAsState()
    val boosters = boostersMap[chatId] ?: emptyList()

    val currentLevel = customization.boostLevel
    val currentBoosts = customization.boostCount
    val nextLevelTarget = customization.boostsRequiredForNextLevel
    val progress = (currentBoosts.toFloat() / nextLevelTarget.coerceAtLeast(1)).coerceIn(0f, 1f)

    val coroutineScope = rememberCoroutineScope()
    val ecosystemUser by com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.currentUserState.collectAsState()
    val hasPrivilege = ecosystemUser?.hasBoostPrivilege() ?: true
    val availableVotes = ecosystemUser?.availableBoostVotes ?: 4

    val infiniteTransition = rememberInfiniteTransition(label = "boost_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val starRotation by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_rotation"
    )

    val perks = remember(currentLevel) {
        listOf(
            BoostPerk(1, "1 История в день", "Публикуйте истории от лица канала", "photo_camera", currentLevel >= 1),
            BoostPerk(2, "Цвет названия и цитат", "Персонализация цвета канала в постах", "palette", currentLevel >= 2),
            BoostPerk(3, "Анимированный эмодзи в шапку", "Установите кастомный статус рядом с названием", "star", currentLevel >= 3),
            BoostPerk(4, "Обои для подписчиков", "Кастомный фон диалога для всех участников", "wallpaper", currentLevel >= 4),
            BoostPerk(5, "Ссылки в историях", "Прикрепляйте ссылки к историям канала", "link", currentLevel >= 5),
            BoostPerk(6, "16 кастомных реакций", "Уникальные реакции для публикаций", "add_reaction", currentLevel >= 6),
            BoostPerk(7, "Кастомная обложка профиля", "Уникальный фон в шапке профиля канала", "image", currentLevel >= 7),
            BoostPerk(8, "Набор эмодзи канала", "Собственный набор эмодзи для подписчиков", "emoji_emotions", currentLevel >= 8)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Голоса и Бусты канала", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F141C)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F141C), Color(0xFF181824), Color(0xFF0D0F14))
                    )
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // Giant Glowing Star & Level Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1F1B2C),
                    border = BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFFFFB300).copy(alpha = glowAlpha), Color(0xFFE040FB), Color(0xFF00E5FF))
                        )
                    ),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Glowing Star Badge
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFFFFD54F), Color(0xFFFF6D00).copy(alpha = 0.4f))
                                    )
                                )
                                .border(2.dp, Color(0xFFFFD54F).copy(alpha = glowAlpha), CircleShape)
                                .graphicsLayer {
                                    rotationZ = starRotation
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Stars,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Уровень $currentLevel",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD54F)
                        )

                        Text(
                            text = "$currentBoosts из $nextLevelTarget голосов до Уровня ${currentLevel + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        Spacer(Modifier.height(14.dp))

                        // Animated Level Progress Bar
                        val animProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(800, easing = FastOutSlowInEasing),
                            label = "boost_level_progress"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFFB300), Color(0xFFFF4081), Color(0xFF00E5FF))
                                        )
                                    )
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // Available Votes Status Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Stars,
                                contentDescription = null,
                                tint = if (hasPrivilege) Color(0xFFFFD54F) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (hasPrivilege) "Доступно буст-голосов: $availableVotes" else "Требуется подписка KuoteX VIP",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasPrivilege) Color(0xFFFFD54F) else Color.LightGray.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Boost Button
                        Button(
                            onClick = {
                                if (!hasPrivilege) {
                                    Toast.makeText(
                                        context,
                                        "Буст доступен только пользователям с KuoteX VIP или Администраторам",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                                val voted = ChannelCustomizationManager.toggleBoost(chatId)
                                if (voted) {
                                    coroutineScope.launch {
                                        val userId = ecosystemUser?.userId ?: "me"
                                        com.example.data.ecosystem.KuoteXEcosystemFirestoreManager.applyChannelBoostAtomic(
                                            userId = userId,
                                            channelId = chatId,
                                            votesToApply = 1
                                        )
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    if (voted) "Вы отдали голос за канал! ⭐" else "Голос отозван",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!hasPrivilege) Color(0xFF263238) else if (customization.hasVotedBoost) Color(0xFF37474F) else Color(0xFFFF6D00)
                            )
                        ) {
                            Icon(
                                if (customization.hasVotedBoost) Icons.Filled.Check else Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (!hasPrivilege) "Нужен KuoteX VIP для буста" else if (customization.hasVotedBoost) "Вы проголосовали (Отозвать)" else "Проголосовать за канал",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Share Boost Link Section
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF141923),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ссылка для голосования",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "t.me/boost/${customization.inviteLink.substringAfterLast("/")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF00E5FF)
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Boost Link", "https://t.me/boost/neon_channel")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Ссылка для буста скопирована!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF).copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Копировать", color = Color(0xFF00E5FF), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Unlocked Perks Timeline Header
            item {
                Text(
                    text = "Возможности уровней буста",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Perks List
            items(perks) { perk ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (perk.isUnlocked) Color(0xFF1A2230) else Color(0xFF11141B).copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.dp,
                        if (perk.isUnlocked) Color(0xFF00E5FF).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Level Badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (perk.isUnlocked) Color(0xFFFFB300).copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ур.${perk.level}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (perk.isUnlocked) Color(0xFFFFD54F) else Color.Gray,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = perk.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (perk.isUnlocked) Color.White else Color.Gray
                            )
                            Text(
                                text = perk.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (perk.isUnlocked) Color.White.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }

                        if (perk.isUnlocked) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Разблокировано",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Заблокировано",
                                tint = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Booster ranking list
            if (boosters.isNotEmpty()) {
                item {
                    Text(
                        text = "Проголосовавшие подписчики (${boosters.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                items(boosters) { booster ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF141923),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = booster.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray.copy(alpha = 0.3f))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = booster.userName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Отдал ${booster.boostsCount} голос(а)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                            Icon(
                                Icons.Filled.Stars,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
