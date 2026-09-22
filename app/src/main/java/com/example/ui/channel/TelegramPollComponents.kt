package com.example.ui.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PollMessageView
 * Telegram-style interactive poll component with animated percentages,
 * single/multi choice selection, quiz validation, and retract vote support.
 */
@Composable
fun PollMessageView(
    poll: PollData,
    chatId: String,
    modifier: Modifier = Modifier,
    onVote: (optionIndex: Int) -> Unit = { idx ->
        ChannelCustomizationManager.votePoll(chatId, poll.id, idx, poll.isMultipleChoice)
    },
    onRetractVote: () -> Unit = {
        ChannelCustomizationManager.retractPollVote(chatId, poll.id)
    }
) {
    val hasVoted = poll.userSelectedOptionIds.isNotEmpty()
    var showExplanation by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF161C26).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.25f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Poll Header & Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (poll.isQuiz) Color(0xFFFFB300).copy(alpha = 0.2f)
                                else Color(0xFF00E5FF).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (poll.isQuiz) Icons.Filled.EmojiEvents else Icons.Filled.Poll,
                            contentDescription = null,
                            tint = if (poll.isQuiz) Color(0xFFFFB300) else Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (poll.isQuiz) "Викторина" else if (poll.isAnonymous) "Анонимный опрос" else "Публичный опрос",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (poll.isQuiz) Color(0xFFFFD54F) else Color(0xFF00E5FF)
                    )
                }

                if (poll.isQuiz && poll.explanation != null && hasVoted) {
                    IconButton(
                        onClick = { showExplanation = !showExplanation },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Filled.Lightbulb,
                            contentDescription = "Объяснение",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Question text
            Text(
                text = poll.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 22.sp
            )

            if (poll.isMultipleChoice) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Выберите один или несколько вариантов",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            // Options List
            poll.options.forEachIndexed { index, option ->
                val isSelected = poll.userSelectedOptionIds.contains(index)
                val isCorrect = poll.isQuiz && poll.correctOptionIndex == index
                val isWrong = poll.isQuiz && isSelected && poll.correctOptionIndex != null && poll.correctOptionIndex != index

                PollOptionRow(
                    option = option,
                    index = index,
                    isSelected = isSelected,
                    hasVoted = hasVoted,
                    isQuiz = poll.isQuiz,
                    isCorrect = isCorrect,
                    isWrong = isWrong,
                    isClosed = poll.isClosed,
                    onClick = {
                        if (!poll.isClosed) {
                            onVote(index)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Quiz Explanation Banner
            AnimatedVisibility(
                visible = showExplanation && poll.explanation != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                poll.explanation?.let { exp ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFB300).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = exp, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFF9C4))
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Footer: Vote count & Retract Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${poll.totalVoters} проголосовало${if (poll.isClosed) " • Опрос закрыт" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )

                if (hasVoted && !poll.isClosed && !poll.isQuiz) {
                    TextButton(
                        onClick = onRetractVote,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Отозвать голос",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PollOptionRow(
    option: PollOption,
    index: Int,
    isSelected: Boolean,
    hasVoted: Boolean,
    isQuiz: Boolean,
    isCorrect: Boolean,
    isWrong: Boolean,
    isClosed: Boolean,
    onClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (hasVoted) option.percentage else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "poll_bar_progress"
    )

    val barColor by animateColorAsState(
        targetValue = when {
            isQuiz && hasVoted && isCorrect -> Color(0xFF00E676)
            isQuiz && hasVoted && isWrong -> Color(0xFFFF5252)
            isSelected -> Color(0xFF00E5FF)
            else -> Color(0xFF2A394A)
        },
        label = "bar_color"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10141E))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) barColor else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !isClosed, onClick = onClick)
    ) {
        // Progress Fill Bar
        if (hasVoted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(barColor.copy(alpha = 0.22f))
            )
        }

        // Content Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio/Check circle icon
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        if (isSelected || (hasVoted && isCorrect)) barColor else Color.White.copy(alpha = 0.4f),
                        CircleShape
                    )
                    .background(
                        if (isSelected || (hasVoted && isCorrect)) barColor else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasVoted && isCorrect) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                } else if (hasVoted && isWrong) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                } else if (isSelected) {
                    Box(modifier = Modifier.size(8.dp).background(Color.Black, CircleShape))
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = option.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            // Percentage
            if (hasVoted) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(option.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
            }
        }
    }
}

/**
 * CreatePollDialog
 * Dialog for creating Telegram-style Single, Multiple, or Quiz polls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePollDialog(
    chatId: String,
    onDismiss: () -> Unit,
    onPollCreated: (PollData) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var isAnonymous by remember { mutableStateOf(true) }
    var isMultipleChoice by remember { mutableStateOf(false) }
    var isQuiz by remember { mutableStateOf(false) }
    var correctOptionIndex by remember { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Poll,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Новый опрос / Викторина", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Задайте вопрос") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }

                item {
                    Text(
                        text = "Варианты ответа:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }

                itemsIndexed(options) { index, optText ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isQuiz) {
                            RadioButton(
                                selected = correctOptionIndex == index,
                                onClick = { correctOptionIndex = index }
                            )
                        }
                        OutlinedTextField(
                            value = optText,
                            onValueChange = { newText ->
                                val list = options.toMutableList()
                                list[index] = newText
                                options = list
                            },
                            label = { Text("Вариант ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (options.size > 2) {
                            IconButton(
                                onClick = {
                                    val list = options.toMutableList()
                                    list.removeAt(index)
                                    options = list
                                    if (correctOptionIndex >= options.size) {
                                        correctOptionIndex = 0
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить", tint = Color(0xFFFF5252))
                            }
                        }
                    }
                }

                if (options.size < 8) {
                    item {
                        TextButton(
                            onClick = { options = options + "" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Добавить вариант")
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                }

                // Poll settings switches
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Анонимное голосование", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Выбор нескольких ответов", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = isMultipleChoice,
                            onCheckedChange = {
                                isMultipleChoice = it
                                if (it) isQuiz = false
                            }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Режим викторины", style = MaterialTheme.typography.bodyMedium)
                            Text("Один правильный ответ с объяснением", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Switch(
                            checked = isQuiz,
                            onCheckedChange = {
                                isQuiz = it
                                if (it) isMultipleChoice = false
                            }
                        )
                    }
                }

                if (isQuiz) {
                    item {
                        OutlinedTextField(
                            value = explanation,
                            onValueChange = { explanation = it },
                            label = { Text("Объяснение (необязательно)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val filteredOpts = options.filter { it.isNotBlank() }
                    if (question.isNotBlank() && filteredOpts.size >= 2) {
                        val pollOptions = filteredOpts.mapIndexed { idx, txt ->
                            PollOption(id = idx, text = txt)
                        }
                        val newPoll = PollData(
                            chatId = chatId,
                            creatorId = "me",
                            creatorName = "Вы",
                            question = question.trim(),
                            options = pollOptions,
                            isAnonymous = isAnonymous,
                            isMultipleChoice = isMultipleChoice,
                            isQuiz = isQuiz,
                            correctOptionIndex = if (isQuiz) correctOptionIndex else null,
                            explanation = if (isQuiz && explanation.isNotBlank()) explanation.trim() else null
                        )
                        ChannelCustomizationManager.createPoll(chatId, newPoll)
                        onPollCreated(newPoll)
                        onDismiss()
                    }
                },
                enabled = question.isNotBlank() && options.count { it.isNotBlank() } >= 2
            ) {
                Text("Создать опрос")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun CreateTelegramPollDialog(
    chatId: String,
    onDismiss: () -> Unit,
    onPollCreated: (PollData) -> Unit
) {
    CreatePollDialog(chatId = chatId, onDismiss = onDismiss, onPollCreated = onPollCreated)
}
