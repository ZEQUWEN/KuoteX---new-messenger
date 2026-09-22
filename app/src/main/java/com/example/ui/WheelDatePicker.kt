package com.example.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelDatePicker(
    modifier: Modifier = Modifier,
    initialDate: String,
    onDateSelected: (String) -> Unit
) {
    val days = (1..31).map { it.toString() }
    val months = listOf("Янв.", "Февр.", "Март", "Апр.", "Май", "Июнь", "Июль", "Авг.", "Сент.", "Окт.", "Нояб.", "Дек.")
    val years = (1900..2024).map { it.toString() }.reversed()

    val parts = initialDate.split(" ")
    val initDay = if (parts.isNotEmpty()) parts[0] else "21"
    val initMonth = if (parts.size > 1) parts[1].replaceFirstChar { it.uppercase() } else "Июнь"
    val initYear = if (parts.size > 2) parts[2] else "2005"

    var selectedDay by remember { mutableStateOf(initDay) }
    var selectedMonth by remember { mutableStateOf(initMonth) }
    var selectedYear by remember { mutableStateOf(initYear) }

    LaunchedEffect(selectedDay, selectedMonth, selectedYear) {
        onDateSelected("$selectedDay ${selectedMonth.lowercase()} $selectedYear")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPicker(
            items = days,
            initialItem = selectedDay,
            onItemSelected = { selectedDay = it },
            modifier = Modifier.weight(1f)
        )
        WheelPicker(
            items = months,
            initialItem = selectedMonth,
            onItemSelected = { selectedMonth = it },
            modifier = Modifier.weight(1f)
        )
        WheelPicker(
            items = years,
            initialItem = selectedYear,
            onItemSelected = { selectedYear = it },
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> WheelPicker(
    items: List<T>,
    initialItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialIndex = items.indexOf(initialItem).takeIf { it >= 0 } ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    val visibleItems = 3
    val itemHeight = 50.dp
    
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            if (centerIndex in items.indices) {
                onItemSelected(items[centerIndex])
            }
        }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight)
        ) {
            items(items.size) { index ->
                val isSelected = index == (listState.firstVisibleItemIndex)
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index].toString(),
                        style = if (isSelected) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold) 
                                else MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
