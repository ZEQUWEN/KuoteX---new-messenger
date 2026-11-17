package com.example.ui.channel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

private val SheetSurfaceBg = Color(0xFF1B1D24)
private val SheetCardBg = Color(0xFF232630)
private val SheetPurple = Color(0xFF9F6BFF)
private val SheetGold = Color(0xFFFFB800)
private val SheetMuted = Color(0xFF8C92A3)

/**
 * Telegram-style Suggested Post modal overlay ("Предложенный пост") with floating top
 * Stars balance pill ("У Вас ⭐ X. / Купить звёзды >"), dynamic USD estimator (~0,00 $),
 * optional post text editor, and inline 3-column Date & Time Wheel Picker ("Изменение времени").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SuggestedPostBottomSheet(
    visible: Boolean,
    userStarsBalance: Long,
    defaultStarPrice: Int,
    initialPostText: String,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onBuyStarsClick: () -> Unit,
    onSubmitSuggestedPost: (postText: String, priceStars: Long, scheduledTimestamp: Long?, scheduledLabel: String) -> Unit
) {
    if (!visible) return

    val dateOptions = remember { ChannelDirectMessageManager.generateUpcomingDateOptions(30) }
    val initialCal = remember { Calendar.getInstance() }
    val defaultHour = remember { initialCal.get(Calendar.HOUR_OF_DAY) }
    val defaultMinute = remember { initialCal.get(Calendar.MINUTE) }

    var priceInput by remember(visible, defaultStarPrice) {
        mutableStateOf(if (defaultStarPrice > 0) defaultStarPrice.toString() else "")
    }
    var postText by remember(visible, initialPostText) {
        mutableStateOf(initialPostText)
    }
    var isTimePickerMode by remember(visible) { mutableStateOf(false) }
    var confirmedScheduledTimestamp by remember(visible) { mutableStateOf<Long?>(null) }
    var confirmedScheduledLabel by remember(visible) { mutableStateOf("Любое") }

    var wheelDateIndex by remember(visible) { mutableIntStateOf(0) }
    var wheelHour by remember(visible) { mutableIntStateOf(defaultHour) }
    var wheelMinute by remember(visible) { mutableIntStateOf(defaultMinute) }

    var isPriceFocused by remember { mutableStateOf(false) }
    var showInsufficientDialog by remember { mutableStateOf(false) }

    val parsedPriceStars = priceInput.filter { it.isDigit() }.toLongOrNull()?.coerceAtMost(1_000_000L) ?: 0L
    val approxUsd = SuggestedPostPayload.formatApproxUsd(parsedPriceStars)

    if (showInsufficientDialog) {
        AlertDialog(
            onDismissRequest = { showInsufficientDialog = false },
            containerColor = SheetSurfaceBg,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    text = "Недостаточно звёзд",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Для предложения этого поста требуется ⭐ $parsedPriceStars, а на Вашем балансе ⭐ $userStarsBalance.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInsufficientDialog = false
                        onDismiss()
                        onBuyStarsClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SheetPurple),
                    modifier = Modifier.testTag("suggested_post_buy_stars_confirm")
                ) {
                    Text("Купить звёзды", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsufficientDialog = false }) {
                    Text("Отмена", color = SheetMuted)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            // Floating Top Balance Pill ("У Вас ⭐ 0. / Купить звёзды >")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF181A22).copy(alpha = 0.96f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume click */ }
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "У Вас ",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SheetGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "$userStarsBalance.",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onDismiss()
                                onBuyStarsClick()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("suggested_post_buy_stars_pill")
                    ) {
                        Text(
                            text = "Купить звёзды",
                            color = SheetPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Купить звёзды",
                            tint = SheetPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Bottom Sheet Panel
            Surface(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                color = SheetSurfaceBg,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent dismissing when tapping inside sheet */ }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Header row: "Предложенный пост" + (X)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Предложенный пост",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("suggested_post_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Закрыть",
                                tint = SheetMuted
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Outlined Stars Price Input Field + right-aligned USD estimate (~0,00 $)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isPriceFocused) 1.8.dp else 1.2.dp,
                                color = if (isPriceFocused) SheetPurple else Color(0xFF3E4250),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(Color(0xFF171920))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Звёзды",
                            tint = SheetGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (priceInput.isEmpty()) {
                                Text(
                                    text = "Укажите цену в звёздах",
                                    color = SheetMuted,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = priceInput,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(7)
                                    priceInput = digits
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(SheetPurple),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isPriceFocused = it.isFocused }
                                    .testTag("suggested_post_price_input")
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = approxUsd,
                            color = SheetMuted,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.End
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Укажите, сколько звёзд будет заплачено каналу за публикацию.",
                        color = SheetMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    // Post Text Editor (compact so 360dp stays uncluttered, prefilled with composer text)
                    if (!isTimePickerMode) {
                        OutlinedTextField(
                            value = postText,
                            onValueChange = { postText = it },
                            label = {
                                Text(
                                    text = "Текст предложенного поста",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            placeholder = {
                                Text("Напишите текст поста для публикации в канале...")
                            },
                            maxLines = 4,
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SheetPurple,
                                unfocusedBorderColor = Color(0xFF3E4250),
                                focusedLabelColor = SheetPurple,
                                unfocusedLabelColor = SheetMuted,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = SheetPurple,
                                focusedContainerColor = Color(0xFF171920),
                                unfocusedContainerColor = Color(0xFF171920)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("suggested_post_text_input")
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Dynamic Bottom Section: Default Summary vs Inline 3-Column Date/Time Wheel
                    AnimatedContent(
                        targetState = isTimePickerMode,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(150))
                        },
                        label = "SuggestedPostTimeMode"
                    ) { pickerOpen ->
                        if (!pickerOpen) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Outlined "Время публикации" Dropdown Trigger
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isTimePickerMode = true }
                                        .testTag("suggested_post_time_dropdown")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.2.dp, Color(0xFF3E4250), RoundedCornerShape(12.dp))
                                            .background(Color(0xFF171920))
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "Время публикации",
                                            color = SheetPurple,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = confirmedScheduledLabel,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowDown,
                                                contentDescription = "Выбрать время публикации",
                                                tint = SheetMuted
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Выберите желаемые дату и время публикации. Пост будет доступен не менее 24 часов с момента публикации.",
                                    color = SheetMuted,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )

                                Spacer(Modifier.height(16.dp))

                                val ctaLabel = if (parsedPriceStars <= 0L) {
                                    "Предложить бесплатно"
                                } else {
                                    "Предложить за ⭐ $parsedPriceStars"
                                }
                                Button(
                                    onClick = {
                                        if (parsedPriceStars > userStarsBalance) {
                                            showInsufficientDialog = true
                                        } else {
                                            val finalText = postText.trim().ifBlank {
                                                "Предложенный пост для канала"
                                            }
                                            onSubmitSuggestedPost(
                                                finalText,
                                                parsedPriceStars,
                                                confirmedScheduledTimestamp,
                                                confirmedScheduledLabel
                                            )
                                        }
                                    },
                                    enabled = !isSubmitting,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SheetPurple,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("suggested_post_submit_button")
                                ) {
                                    if (isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Text(
                                            text = ctaLabel,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } else {
                            // Inline 3-Column Date & Time Wheel Picker ("Изменение времени")
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SheetCardBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = "Изменение времени",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Выберите желаемые дату и время публикации.",
                                        color = SheetMuted,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )

                                    Spacer(Modifier.height(14.dp))

                                    ThreeColumnWheelPicker(
                                        dateOptions = dateOptions,
                                        selectedDateIndex = wheelDateIndex,
                                        onDateSelected = { wheelDateIndex = it },
                                        selectedHour = wheelHour,
                                        onHourSelected = { wheelHour = it },
                                        selectedMinute = wheelMinute,
                                        onMinuteSelected = { wheelMinute = it }
                                    )

                                    Spacer(Modifier.height(14.dp))

                                    val selectedDateObj = dateOptions.getOrElse(wheelDateIndex) { dateOptions.first() }
                                    val confirmTimeText = ChannelDirectMessageManager.formatScheduledButtonLabel(
                                        dateOption = selectedDateObj,
                                        hour = wheelHour,
                                        minute = wheelMinute
                                    )

                                    Button(
                                        onClick = {
                                            confirmedScheduledTimestamp = ChannelDirectMessageManager.computeTimestamp(
                                                dateOption = selectedDateObj,
                                                hour = wheelHour,
                                                minute = wheelMinute
                                            )
                                            confirmedScheduledLabel = confirmTimeText
                                            isTimePickerMode = false
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SheetPurple,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("suggested_post_confirm_time_button")
                                    ) {
                                        Text(
                                            text = confirmTimeText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Surface(
                                        onClick = {
                                            confirmedScheduledTimestamp = null
                                            confirmedScheduledLabel = "Любое"
                                            isTimePickerMode = false
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF2B2F3C),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .testTag("suggested_post_anytime_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Любое",
                                                color = SheetPurple,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreeColumnWheelPicker(
    dateOptions: List<SuggestedPostDateOption>,
    selectedDateIndex: Int,
    onDateSelected: (Int) -> Unit,
    selectedHour: Int,
    onHourSelected: (Int) -> Unit,
    selectedMinute: Int,
    onMinuteSelected: (Int) -> Unit
) {
    val hours = remember { (0..23).toList() }
    val minutes = remember { (0..59).toList() }
    val itemHeight = 46.dp
    val wheelHeight = itemHeight * 3

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(wheelHeight)
    ) {
        // Horizontal purple selection dividers framing the center row
        HorizontalDivider(
            thickness = 1.5.dp,
            color = SheetPurple,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -(itemHeight / 2))
                .fillMaxWidth()
        )
        HorizontalDivider(
            thickness = 1.5.dp,
            color = SheetPurple,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = itemHeight / 2)
                .fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelColumn(
                items = dateOptions.map { it.wheelLabel },
                selectedIndex = selectedDateIndex,
                onSelectedIndexChange = onDateSelected,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.7f)
            )
            WheelColumn(
                items = hours.map { String.format(Locale.US, "%02d", it) },
                selectedIndex = selectedHour,
                onSelectedIndexChange = onHourSelected,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.65f)
            )
            WheelColumn(
                items = minutes.map { String.format(Locale.US, "%02d", it) },
                selectedIndex = selectedMinute,
                onSelectedIndexChange = onMinuteSelected,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.65f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    val itemHeight = 46.dp
    val safeInitial = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitial)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf safeInitial
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            visibleItems.minByOrNull { item ->
                abs((item.offset + item.size / 2) - viewportCenter)
            }?.index ?: safeInitial
        }
    }

    LaunchedEffect(centerIndex) {
        if (centerIndex in items.indices && centerIndex != selectedIndex) {
            onSelectedIndexChange(centerIndex)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = snapBehavior,
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxHeight()
    ) {
        itemsIndexed(items) { index, label ->
            val distance = abs(index - centerIndex)
            val textColor = when (distance) {
                0 -> Color.White
                1 -> Color(0xFFB5BAC9)
                else -> Color(0xFF5D6270)
            }
            val fontSize = if (distance == 0) 17.sp else 15.sp
            val fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    textAlign = textAlign,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
