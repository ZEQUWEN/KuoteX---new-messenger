package com.example.ui.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rich Telegram-style Suggested Post card inside a Channel Direct Messages thread
 * or in ChatScreen. Shows post text, Stars price (~USD), requested time, status badge,
 * and Admin moderation actions (Publish / Schedule / Decline with full refund).
 */
@Composable
fun SuggestedPostMessageCard(
    payload: SuggestedPostPayload,
    isMe: Boolean,
    isAdminView: Boolean,
    onAcceptPublishNow: () -> Unit,
    onScheduleForRequestedTime: () -> Unit,
    onDeclineRefund: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (payload.status) {
        SuggestedPostStatus.PENDING -> Color(0xFFB072FF)
        SuggestedPostStatus.ACCEPTED -> Color(0xFF4CAF50)
        SuggestedPostStatus.SCHEDULED -> Color(0xFF2AABEE)
        SuggestedPostStatus.DECLINED -> Color(0xFFFF5252)
    }

    val cardBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF282B3A),
            Color(0xFF1B1E28)
        )
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
            color = Color.Transparent,
            modifier = Modifier
                .widthIn(min = 240.dp, max = 310.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cardBrush)
                .testTag("suggested_post_card_${payload.proposalId}")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header: Badge + Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ПРЕДЛОЖЕННЫЙ ПОСТ",
                        color = Color(0xFFB072FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = payload.status.labelRu,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Suggested Post Content
                Text(
                    text = payload.text,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Reward / Fee + Scheduled time rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (payload.priceStars > 0L) {
                                "⭐ ${payload.priceStars} (${payload.approxUsdText})"
                            } else {
                                "Бесплатно"
                            },
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF9EA4B3),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = payload.scheduledLabel.ifBlank { "Любое" },
                            color = Color(0xFFB5BAC9),
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Admin Action Buttons when PENDING
                if (isAdminView && payload.status == SuggestedPostStatus.PENDING) {
                    val hasFutureSchedule = payload.scheduledTimestamp != null &&
                        payload.scheduledTimestamp > System.currentTimeMillis()

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onAcceptPublishNow,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF8E5AE2),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("suggested_post_accept_${payload.proposalId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Принять",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = onDeclineRefund,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFFF6B6B)
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("suggested_post_decline_${payload.proposalId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Отклонить",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (hasFutureSchedule) {
                            FilledTonalButton(
                                onClick = onScheduleForRequestedTime,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF2AABEE).copy(alpha = 0.18f),
                                    contentColor = Color(0xFF64B5F6)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("suggested_post_schedule_${payload.proposalId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Запланировать на ${payload.scheduledLabel}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (payload.status == SuggestedPostStatus.DECLINED && payload.priceStars > 0L) {
                    Text(
                        text = "Возвращено ⭐ ${payload.priceStars} на баланс",
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
