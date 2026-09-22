package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MessageStatus {
    PENDING, SENT, DELIVERED, READ
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    text: String,
    isMe: Boolean,
    time: String,
    modifier: Modifier = Modifier,
    senderName: String? = null,
    status: MessageStatus? = null,
    onMessageClick: (() -> Unit)? = null,
    onMessageLongClick: (() -> Unit)? = null,
    replyToText: String? = null,
    replyToSender: String? = null,
    customContent: (@Composable () -> Unit)? = null
) {
    val scaleAnim = remember { Animatable(0.88f) }
    val alphaAnim = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val backgroundColor = if (isMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val shape: Shape = if (isMe) {
        RoundedCornerShape(16.dp).copy(bottomEnd = CornerSize(4.dp))
    } else {
        RoundedCornerShape(16.dp).copy(bottomStart = CornerSize(4.dp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scaleAnim.value)
            .alpha(alphaAnim.value)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        if (!isMe && senderName != null) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        Surface(
            color = backgroundColor,
            contentColor = contentColor,
            shape = shape,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 60.dp, max = 320.dp)
        ) {
            Box(
                modifier = Modifier
                    .combinedClickable(
                        onClick = { onMessageClick?.invoke() },
                        onLongClick = { onMessageLongClick?.invoke() }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    if (replyToText != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(IntrinsicSize.Min)
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = replyToSender ?: "Unknown",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        text = replyToText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (customContent != null) {
                        customContent()
                    } else if (text.isNotEmpty()) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                        
                        if (isMe && status != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val icon = when (status) {
                                MessageStatus.READ -> Icons.Filled.DoneAll
                                MessageStatus.DELIVERED -> Icons.Filled.DoneAll
                                MessageStatus.SENT -> Icons.Filled.Check
                                MessageStatus.PENDING -> Icons.Filled.Schedule
                            }
                            
                            // Use a distinct tint for read status (e.g. blue or primary)
                            val iconTint = if (status == MessageStatus.READ) {
                                Color(0xFF4CAF50) // Material Green for read receipt
                            } else {
                                contentColor.copy(alpha = 0.7f)
                            }
                            
                            Icon(
                                imageVector = icon,
                                contentDescription = status.name,
                                modifier = Modifier.size(16.dp),
                                tint = iconTint
                            )
                        }
                    }
                }
            }
        }
    }
}
