package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.notifications.InAppNotificationManager
import com.example.notifications.TelegramBubbleNotification
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBubbleNotificationOverlay(
    onNavigateToChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleNotification by InAppNotificationManager.currentBubble.collectAsState()
    val context = LocalContext.current

    var isReplying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-dismiss timer (6 seconds), reset when bubble changes or cancelled if typing reply
    LaunchedEffect(bubbleNotification?.id, isReplying) {
        if (bubbleNotification != null && !isReplying) {
            offsetY = 0f
            delay(5500)
            if (!isReplying) {
                InAppNotificationManager.dismissBubble()
            }
        }
    }

    // Reset reply state when notification changes
    LaunchedEffect(bubbleNotification?.id) {
        isReplying = false
        replyText = ""
        offsetY = 0f
    }

    AnimatedVisibility(
        visible = bubbleNotification != null,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
        exit = fadeOut(animationSpec = tween(200)) +
                slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(250)
                ),
        modifier = modifier
            .fillMaxWidth()
            .zIndex(999f)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val bubble = bubbleNotification ?: return@AnimatedVisibility

        val borderColor = if (bubble.isMention) {
            Color(0xFFFFB300) // Amber glow for mentions
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) // Cyan / Primary glow
        }

        val containerGradient = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.98f)
            )
        )

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .pointerInput(bubble.id) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offsetY < -40f) {
                                InAppNotificationManager.dismissBubble()
                            } else {
                                offsetY = 0f
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount < 0 || offsetY < 0) {
                                offsetY = (offsetY + dragAmount).coerceAtMost(0f)
                            }
                        }
                    )
                }
                .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = borderColor)
                .border(
                    width = if (bubble.isMention) 1.5.dp else 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            borderColor,
                            if (bubble.isMention) Color(0xFFFF4081) else borderColor.copy(alpha = 0.4f),
                            borderColor
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .background(containerGradient, RoundedCornerShape(22.dp))
                .clickable {
                    if (!isReplying) {
                        InAppNotificationManager.dismissBubble()
                        onNavigateToChat(bubble.chatId)
                    }
                }
                .testTag("telegram_bubble_notification")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Header row: Avatar + Name / Mention + Close button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar with Mention badge
                    Box(modifier = Modifier.size(42.dp)) {
                        if (!bubble.senderAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = bubble.senderAvatarUrl,
                                contentDescription = bubble.senderName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(1.dp, borderColor.copy(alpha = 0.5f), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                borderColor.copy(alpha = 0.8f),
                                                MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = bubble.senderName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                        }

                        // Glowing badge for mentions
                        if (bubble.isMention) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFB300))
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "@",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = bubble.senderName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (bubble.isMention) {
                                Surface(
                                    color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFB300))
                                ) {
                                    val mentionLabel = when {
                                        bubble.isChannel -> "В канале"
                                        bubble.isGroup -> "В группе"
                                        else -> "Упоминание"
                                    }
                                    Text(
                                        text = "@ $mentionLabel",
                                        color = Color(0xFFFFB300),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (!bubble.chatTitle.isNullOrBlank() && bubble.chatTitle != bubble.senderName) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (bubble.isChannel) Icons.Filled.Campaign else Icons.Filled.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = bubble.chatTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Dismiss Button
                    IconButton(
                        onClick = { InAppNotificationManager.dismissBubble() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Message Text Preview with styled @mentions
                val annotatedText = remember(bubble.text) {
                    androidx.compose.ui.text.buildAnnotatedString {
                        val words = bubble.text.split(" ")
                        words.forEachIndexed { index, word ->
                            if (word.startsWith("@")) {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFB300), fontWeight = FontWeight.Bold))
                                append(word)
                                pop()
                            } else {
                                append(word)
                            }
                            if (index < words.size - 1) append(" ")
                        }
                    }
                }

                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                    maxLines = if (isReplying) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action area: Reply & Mark as Read OR Inline Input Bar
                AnimatedContent(
                    targetState = isReplying,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "bubble_reply_transition"
                ) { replying ->
                    if (!replying) {
                        // Action buttons row: "Отметить прочитанным" and "Ответить"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "Отметить прочитанным" Button
                            FilledTonalButton(
                                onClick = {
                                    InAppNotificationManager.markAsRead(bubble.chatId, context)
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(36.dp)
                                    .testTag("bubble_mark_as_read_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Отметить прочитанным",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // "Ответить" Button
                            Button(
                                onClick = {
                                    isReplying = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (bubble.isMention) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                                    contentColor = if (bubble.isMention) Color.Black else MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(36.dp)
                                    .testTag("bubble_reply_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ответить",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Inline Quick Reply input field + Send button
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { replyText = it },
                                placeholder = {
                                    Text(
                                        text = "Ваш ответ...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = borderColor,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (replyText.isNotBlank()) {
                                            InAppNotificationManager.sendReply(
                                                chatId = bubble.chatId,
                                                replyText = replyText,
                                                context = context
                                            )
                                            keyboardController?.hide()
                                            isReplying = false
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .focusRequester(focusRequester)
                                    .testTag("bubble_inline_reply_input")
                            )

                            // Cancel Button
                            IconButton(
                                onClick = {
                                    isReplying = false
                                    keyboardController?.hide()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Отмена",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Send Button
                            IconButton(
                                onClick = {
                                    if (replyText.isNotBlank()) {
                                        InAppNotificationManager.sendReply(
                                            chatId = bubble.chatId,
                                            replyText = replyText,
                                            context = context
                                        )
                                        keyboardController?.hide()
                                        isReplying = false
                                    }
                                },
                                enabled = replyText.isNotBlank(),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (replyText.isNotBlank()) {
                                            if (bubble.isMention) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .testTag("bubble_send_reply_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить ответ",
                                    tint = if (replyText.isNotBlank()) {
                                        if (bubble.isMention) Color.Black else MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
