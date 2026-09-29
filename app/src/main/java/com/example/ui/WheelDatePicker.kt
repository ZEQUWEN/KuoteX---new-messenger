package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.Calendar

val RussianMonthsList = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

val RussianShortMonths = listOf(
    "янв.", "февр.", "март", "апр.", "май", "июн.",
    "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."
)

/**
 * Resolves a month string in various formats (full, short, numeric) to the full month name in [RussianMonthsList].
 */
fun resolveMonthToFull(monthStr: String): String {
    val clean = monthStr.trim().lowercase().removeSuffix(".")
    // Try short prefix match
    when {
        clean.startsWith("янв") -> return "Январь"
        clean.startsWith("фев") -> return "Февраль"
        clean.startsWith("мар") -> return "Март"
        clean.startsWith("апр") -> return "Апрель"
        clean.startsWith("май") -> return "Май"
        clean.startsWith("июн") -> return "Июнь"
        clean.startsWith("июл") -> return "Июль"
        clean.startsWith("авг") -> return "Август"
        clean.startsWith("сен") -> return "Сентябрь"
        clean.startsWith("окт") -> return "Октябрь"
        clean.startsWith("ноя") -> return "Ноябрь"
        clean.startsWith("дек") -> return "Декабрь"
    }
    // Check if numeric (1..12)
    val num = clean.toIntOrNull()
    if (num != null && num in 1..12) {
        return RussianMonthsList[num - 1]
    }
    return RussianMonthsList.firstOrNull { it.lowercase().startsWith(clean) } ?: "Июнь"
}

fun resolveMonthToShort(fullMonth: String): String {
    val index = RussianMonthsList.indexOf(fullMonth)
    return if (index in RussianShortMonths.indices) RussianShortMonths[index] else "июн."
}

/**
 * Formats a birthday string with age, matching Screenshot: "21 июн. 2005 (21 год)".
 */
fun formatBirthDateWithAge(dateString: String?): String {
    if (dateString.isNullOrBlank()) return "Укажите дату рождения"
    val parsed = parseDateComponents(dateString) ?: return dateString
    val (day, monthFull, year) = parsed
    val shortMonth = resolveMonthToShort(monthFull)
    
    val currentYear = 2026 // Based on system metadata
    val age = (currentYear - year).coerceAtLeast(0)
    
    val ageWord = when {
        age % 100 in 11..14 -> "лет"
        age % 10 == 1 -> "год"
        age % 10 in 2..4 -> "года"
        else -> "лет"
    }
    
    return "$day $shortMonth $year ($age $ageWord)"
}

/**
 * Formats a short birthday string: "21 июн. 2005".
 */
fun formatBirthDateShort(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    val parsed = parseDateComponents(dateString) ?: return dateString
    val (day, monthFull, year) = parsed
    val shortMonth = resolveMonthToShort(monthFull)
    return "$day $shortMonth $year"
}

fun parseDateComponents(rawDate: String): Triple<Int, String, Int>? {
    if (rawDate.isBlank() || rawDate.contains("Укажите", ignoreCase = true)) return null
    val clean = rawDate.replace(Regex("\\(.*\\)"), "").trim()
    
    // Check dot-separated: "21.06.2005"
    val dotParts = clean.split(".")
    if (dotParts.size == 3) {
        val d = dotParts[0].trim().toIntOrNull()
        val m = dotParts[1].trim().toIntOrNull()
        val y = dotParts[2].trim().toIntOrNull()
        if (d != null && m != null && y != null && m in 1..12) {
            return Triple(d, RussianMonthsList[m - 1], y)
        }
    }
    
    // Check space or dash separated: "21 июн. 2005" or "2005-06-21"
    val parts = clean.split(Regex("[\\s.,-]+")).filter { it.isNotBlank() }
    if (parts.size >= 3) {
        if (parts[0].length == 4 && parts[0].toIntOrNull() != null) {
            val year = parts[0].toInt()
            val m = parts[1].toIntOrNull() ?: 6
            val month = if (m in 1..12) RussianMonthsList[m - 1] else resolveMonthToFull(parts[1])
            val day = parts[2].toIntOrNull() ?: 21
            return Triple(day, month, year)
        }
        val day = parts[0].toIntOrNull() ?: 21
        val month = resolveMonthToFull(parts[1])
        val year = parts[2].toIntOrNull() ?: 2005
        return Triple(day, month, year)
    } else {
        val digits = Regex("\\d+").findAll(clean).map { it.value.toInt() }.toList()
        if (digits.size >= 2) {
            val day = digits[0]
            val year = digits.lastOrNull() ?: 2005
            return Triple(day, "Июнь", year)
        }
    }
    return null
}

fun getDaysInMonth(monthName: String, year: Int): Int {
    val monthIndex = RussianMonthsList.indexOf(resolveMonthToFull(monthName))
    return when (monthIndex) {
        1 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
        3, 5, 8, 10 -> 30
        else -> 31
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelDatePicker(
    modifier: Modifier = Modifier,
    initialDate: String,
    onDateSelected: (String) -> Unit
) {
    val parsed = remember(initialDate) {
        parseDateComponents(initialDate) ?: Triple(21, "Июнь", 2005)
    }
    var selectedDay by remember(parsed) { mutableStateOf(parsed.first.toString()) }
    var selectedMonth by remember(parsed) { mutableStateOf(parsed.second) }
    var selectedYear by remember(parsed) { mutableStateOf(parsed.third.toString()) }

    val currentYearInt = selectedYear.toIntOrNull() ?: 2005
    val maxDays = remember(selectedMonth, currentYearInt) {
        getDaysInMonth(selectedMonth, currentYearInt)
    }

    LaunchedEffect(maxDays) {
        val currentDayInt = selectedDay.toIntOrNull() ?: 1
        if (currentDayInt > maxDays) {
            selectedDay = maxDays.toString()
        }
    }

    val days = remember(maxDays) { (1..maxDays).map { it.toString() } }
    val months = RussianMonthsList
    val years = remember { (1920..2026).map { it.toString() } }

    LaunchedEffect(selectedDay, selectedMonth, selectedYear) {
        val shortMonth = resolveMonthToShort(selectedMonth)
        onDateSelected("$selectedDay $shortMonth $selectedYear")
    }

    val itemHeight = 44.dp
    val totalHeight = itemHeight * 5

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight),
        contentAlignment = Alignment.Center
    ) {
        // Selection highlight frame in center row matching Screenshot: purple border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(itemHeight)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelPicker(
                items = days,
                initialItem = selectedDay,
                onItemSelected = { selectedDay = it },
                itemHeight = itemHeight,
                modifier = Modifier.weight(0.9f)
            )
            WheelPicker(
                items = months,
                initialItem = selectedMonth,
                onItemSelected = { selectedMonth = it },
                itemHeight = itemHeight,
                modifier = Modifier.weight(1.3f)
            )
            WheelPicker(
                items = years,
                initialItem = selectedYear,
                onItemSelected = { selectedYear = it },
                itemHeight = itemHeight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> WheelPicker(
    items: List<T>,
    initialItem: T,
    onItemSelected: (T) -> Unit,
    itemHeight: androidx.compose.ui.unit.Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val initialIndex = remember(initialItem, items) {
        val found = items.indexOf(initialItem)
        if (found >= 0) found else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(listState, items) {
        snapshotFlow {
            if (listState.isScrollInProgress) null else listState.firstVisibleItemIndex
        }
        .collect { index ->
            if (index != null && index in items.indices) {
                onItemSelected(items[index])
            }
        }
    }

    Box(
        modifier = modifier.height(itemHeight * 5),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * 2)
        ) {
            items(items.size) { index ->
                val distance = kotlin.math.abs(index - listState.firstVisibleItemIndex)
                val isSelected = distance == 0
                val alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.65f
                    2 -> 0.28f
                    else -> 0.08f
                }
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index].toString(),
                        style = if (isSelected) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                else MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = alpha),
                        fontSize = if (isSelected) 17.sp else 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Telegram-Style Birthday Picker Dialog matching Screenshot:
 * Title "День рождения", 3-column wheel date picker with selection box,
 * pill button "Сохранить", text button "Удалить".
 */
@Composable
fun BirthdayPickerDialog(
    initialDate: String,
    onDateSaved: (String) -> Unit,
    onDateDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentDateSelection by remember { mutableStateOf(initialDate.ifBlank { "21 июн. 2005" }) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.60f))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E1E22),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "День рождения",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        textAlign = TextAlign.Start
                    )

                    WheelDatePicker(
                        initialDate = currentDateSelection,
                        onDateSelected = { currentDateSelection = it }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            onDateSaved(currentDateSelection)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Сохранить",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            onDateDeleted()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Удалить",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
