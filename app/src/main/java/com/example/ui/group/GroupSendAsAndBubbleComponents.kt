package com.example.ui.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONObject

@Composable
fun SendAsIdentityMenu(
    expanded: Boolean,
    identities: List<SendAsIdentity>,
    selectedIdentity: SendAsIdentity?,
    onSelect: (SendAsIdentity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1F2A),
        modifier = Modifier.widthIn(min = 270.dp, max = 330.dp)
    ) {
        Text(
            text = "Написать от имени...",
            color = Color(0xFFB072FF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        identities.forEach { identity ->
            val isSelected = selectedIdentity?.id == identity.id && selectedIdentity.type == identity.type
            DropdownMenuItem(
                modifier = Modifier.testTag("send_as_option_${identity.type}_${identity.id}"),
                onClick = { onSelect(identity) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.size(38.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            when (identity.type) {
                                                "CHANNEL" -> listOf(Color(0xFF9C5CFF), Color(0xFF536DFE))
                                                "GROUP" -> listOf(Color(0xFF2AABEE), Color(0xFF1E88E5))
                                                else -> listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                                            }
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!identity.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(identity.avatarUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = identity.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = identity.title.trim().take(1).ifBlank { "K" }.uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            if (identity.isPrivate) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1A1F2A))
                                        .padding(1.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2AABEE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Частный",
                                        tint = Color.White,
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = identity.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!identity.emojiStatus.isNullOrBlank()) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(text = identity.emojiStatus, fontSize = 13.sp)
                                }
                            }
                            Text(
                                text = identity.subtitle,
                                color = Color(0xFF8B93A3),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Выбрано",
                            tint = Color(0xFFB072FF)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
fun GroupSystemEventPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1A202B).copy(alpha = 0.85f)
        ) {
            Text(
                text = text,
                color = Color(0xFFE6EBF3),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun GroupInviteMessageCard(
    documentData: String,
    isMe: Boolean,
    onOpenInviteUrl: (String) -> Unit
) {
    val parsed = remember(documentData) {
        try {
            val json = JSONObject(documentData)
            if (json.optString("type") == "group_invite_card") {
                Triple(
                    json.optString("groupTitle", "Группа KuoteX"),
                    json.optString("inviteUrl", ""),
                    json.optBoolean("requiresApproval", false)
                )
            } else null
        } catch (_: Exception) {
            null
        }
    } ?: return

    val (groupTitle, inviteUrl, requiresApproval) = parsed
    val fullUrl = if (inviteUrl.startsWith("http")) inviteUrl else "https://$inviteUrl"

    Column(modifier = Modifier.widthIn(min = 210.dp, max = 280.dp)) {
        Text(
            text = fullUrl,
            color = if (isMe) Color(0xFFE8DAFF) else Color(0xFF64B5F6),
            fontSize = 13.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { onOpenInviteUrl(inviteUrl) }
                .padding(bottom = 8.dp)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMe) Color.Black.copy(alpha = 0.22f) else Color(0xFF1D2432),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF9C5CFF), Color(0xFF2AABEE)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = groupTitle.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = groupTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (requiresApproval) "Приглашение в группу (по заявкам)" else "Приглашение в группу",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onOpenInviteUrl(inviteUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMe) Color.White.copy(alpha = 0.2f) else Color(0xFF9C5CFF)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("open_group_invite_card_button")
                ) {
                    Text(
                        text = "ПЕРЕЙТИ В ГРУППУ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GroupSenderAvatar(
    avatarUrl: String?,
    displayName: String,
    seed: String,
    size: Dp = 36.dp,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF9C5CFF), Color(0xFF2AABEE))))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = displayName.trim().take(1).ifBlank { seed.take(1) }.uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}
