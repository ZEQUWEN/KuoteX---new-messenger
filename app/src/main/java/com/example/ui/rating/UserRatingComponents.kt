package com.example.ui.rating

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val RatingSheetBg = Color(0xFF1C1C22)
private val RatingTrackBg = Color(0xFF2B2B33)
private val RatingAccentPurple = Color(0xFFB366FF)
private val RatingAccentPurpleDeep = Color(0xFF9C51EB)
private val RatingPlusBadgeBg = Color(0xFF9F5CEB)
private val RatingMinusBadgeBg = Color(0xFF4B4D56)
private val RatingSubtitleColor = Color(0xFF91949E)

/**
 * Compact shield badge containing the user's current rating level, paired cleanly
 * next to the profile's online status line ("в сети" / "был(а) недавно").
 */
@Composable
fun UserRatingLevelStatusRow(
    level: Int,
    statusText: String,
    isSolidShield: Boolean,
    statusColor: Color = Color.White.copy(alpha = 0.78f),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "profile_rating_badge"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Уровень рейтинга $level, $statusText. Нажмите, чтобы открыть детали рейтинга"
            }
            .testTag(testTag)
    ) {
        RatingShieldGlyph(
            level = level,
            isSolid = isSolidShield
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Telegram-style shield silhouette with the level number centered inside.
 */
@Composable
fun RatingShieldGlyph(
    level: Int,
    isSolid: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(width = 18.dp, height = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val shieldPath = Path().apply {
                moveTo(w * 0.50f, h * 0.05f)
                lineTo(w * 0.92f, h * 0.21f)
                quadraticTo(w * 0.94f, h * 0.57f, w * 0.50f, h * 0.95f)
                quadraticTo(w * 0.06f, h * 0.57f, w * 0.08f, h * 0.21f)
                close()
            }

            if (isSolid) {
                drawPath(
                    path = shieldPath,
                    color = Color.White.copy(alpha = 0.90f)
                )
            } else {
                drawPath(
                    path = shieldPath,
                    color = Color.White.copy(alpha = 0.08f)
                )
                drawPath(
                    path = shieldPath,
                    color = Color.White.copy(alpha = 0.88f),
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        Text(
            text = level.toString(),
            color = if (isSolid) Color(0xFF1A1B20) else Color.White,
            fontSize = if (level >= 10) 9.sp else 10.5.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(y = (-0.5).dp)
        )
    }
}

/**
 * Full Telegram-style Rating & Level Progression ModalBottomSheet.
 * Works identically for both the active user's own profile and other users' profiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRatingBottomSheet(
    userName: String,
    isOwnProfile: Boolean,
    ratingState: UserRatingState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RatingSheetBg,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RatingLevelProgressBar(ratingState = ratingState)

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Рейтинг",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("rating_sheet_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isOwnProfile) {
                    "Рейтинг отражает Вашу активность\nв KuoteX. На него влияют:"
                } else {
                    "Рейтинг отражает активность $userName\nв KuoteX. На него влияют:"
                },
                color = Color(0xFFE5E7EB),
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .testTag("rating_sheet_subtitle")
            )

            Spacer(modifier = Modifier.height(22.dp))

            RatingCriterionRow(
                icon = Icons.Outlined.CardGiftcard,
                iconContentDescription = "Подарки от KuoteX",
                title = "Подарки от KuoteX",
                badgeLabel = "ПЛЮС",
                badgeColor = RatingPlusBadgeBg,
                description = "100% звёзд, потраченных на приобретение подарков от KuoteX."
            )

            Spacer(modifier = Modifier.height(18.dp))

            RatingCriterionRow(
                icon = Icons.Outlined.Groups,
                iconContentDescription = "Маркет и посты",
                title = "Маркет и посты",
                badgeLabel = "ПЛЮС",
                badgeColor = RatingPlusBadgeBg,
                description = "20% звёзд, потраченных на покупку подарков у пользователей, платные сообщения и посты в каналах."
            )

            Spacer(modifier = Modifier.height(18.dp))

            RatingCriterionRow(
                icon = Icons.Outlined.CurrencyExchange,
                iconContentDescription = "Возвраты и обмены",
                title = "Возвраты и обмены",
                badgeLabel = "МИНУС",
                badgeColor = RatingMinusBadgeBg,
                description = "Возвращённые звёзды, умноженные на 10, и 85% от стоимости подарков после обмена на звёзды."
            )

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("rating_sheet_ok_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RatingAccentPurple,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "👌  OK",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun RatingLevelProgressBar(
    ratingState: UserRatingState
) {
    var targetFraction by remember { mutableStateOf(0f) }
    LaunchedEffect(ratingState.progressFraction) {
        targetFraction = ratingState.progressFraction
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "rating_bar_progress"
    )

    val density = LocalDensity.current
    var bubbleWidthPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rating_progress_section")
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val minFillPx = with(density) { 94.dp.toPx() }.coerceAtMost(totalWidthPx * 0.58f)
        val rightReservedPx = with(density) { 88.dp.toPx() }.coerceAtMost(totalWidthPx * 0.36f)
        val maxFillPx = (totalWidthPx - rightReservedPx).coerceAtLeast(minFillPx)
        val fillWidthPx = minFillPx + ((maxFillPx - minFillPx) * animatedProgress)
        val pointerTipX = fillWidthPx.coerceIn(with(density) { 24.dp.toPx() }, totalWidthPx - with(density) { 24.dp.toPx() })
        val bubbleStartX = if (bubbleWidthPx > 0) {
            (pointerTipX - (bubbleWidthPx / 2f))
                .coerceIn(0f, (totalWidthPx - bubbleWidthPx).coerceAtLeast(0f))
        } else {
            with(density) { 20.dp.toPx() }
        }
        val fillWidthDp = with(density) { fillWidthPx.toDp() }
        val triangleWidthPx = with(density) { 12.dp.toPx() }
        val triangleStartX = (pointerTipX - (triangleWidthPx / 2f))
            .coerceIn(bubbleStartX + with(density) { 16.dp.toPx() }, (bubbleStartX + bubbleWidthPx - with(density) { 28.dp.toPx() }).coerceAtLeast(bubbleStartX + with(density) { 16.dp.toPx() }))

        Column(modifier = Modifier.fillMaxWidth()) {
            // Floating purple speech-bubble pill + downward pointer triangle
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(bubbleStartX.roundToInt(), 0) }
                            .onSizeChanged { bubbleWidthPx = it.width }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = RatingAccentPurple,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CrownIcon(modifier = Modifier.size(width = 18.dp, height = 14.dp))
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(
                                            SpanStyle(
                                                color = Color.White,
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append(ratingState.formattedCurrentPoints)
                                        }
                                        withStyle(
                                            SpanStyle(
                                                color = Color.White.copy(alpha = 0.78f),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        ) {
                                            append("/${ratingState.formattedNextTarget}")
                                        }
                                    },
                                    modifier = Modifier.testTag("rating_bubble_score")
                                )
                            }
                        }
                    }

                    // Downward pointer arrow directly under bubble aligned to progress edge
                    Canvas(
                        modifier = Modifier
                            .offset { IntOffset(triangleStartX.roundToInt(), 0) }
                            .size(width = 12.dp, height = 6.dp)
                    ) {
                        val arrowPath = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }
                        drawPath(arrowPath, color = RatingAccentPurple)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Horizontal Level Scale Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(RatingTrackBg)
            ) {
                // Left Filled Segment: "Уровень N"
                Box(
                    modifier = Modifier
                        .width(fillWidthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(RatingAccentPurpleDeep, RatingAccentPurple)
                            )
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Уровень ${ratingState.level}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }

                // Right Target Label: "Уровень N+1"
                Text(
                    text = "Уровень ${ratingState.nextLevel}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun CrownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.06f, h * 0.25f)
            lineTo(w * 0.28f, h * 0.56f)
            lineTo(w * 0.50f, h * 0.10f)
            lineTo(w * 0.72f, h * 0.56f)
            lineTo(w * 0.94f, h * 0.25f)
            lineTo(w * 0.85f, h * 0.88f)
            lineTo(w * 0.15f, h * 0.88f)
            close()
        }
        drawPath(path = path, color = Color.White)
    }
}

@Composable
private fun RatingCriterionRow(
    icon: ImageVector,
    iconContentDescription: String,
    title: String,
    badgeLabel: String,
    badgeColor: Color,
    description: String
) {
    val badgeId = "badge_pill"
    val inlineContent = remember(badgeLabel, badgeColor) {
        mapOf(
            badgeId to InlineTextContent(
                Placeholder(
                    width = 46.sp,
                    height = 17.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            tint = Color.White,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(26.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = buildAnnotatedString {
                    appendInlineContent(badgeId, "[$badgeLabel]")
                    append(" ")
                    append(description)
                },
                inlineContent = inlineContent,
                color = RatingSubtitleColor,
                fontSize = 13.5.sp,
                lineHeight = 18.sp
            )
        }
    }
}
