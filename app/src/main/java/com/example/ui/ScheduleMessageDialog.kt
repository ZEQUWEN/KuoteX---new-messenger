package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMessageDialog(
    onDismissRequest: () -> Unit,
    onSchedule: (Long) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(true) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    
    val timePickerState = rememberTimePickerState(
        initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().get(Calendar.MINUTE)
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(if (showDatePicker) "Select Date" else "Select Time") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showDatePicker) {
                    DatePicker(state = datePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (showDatePicker) {
                        showDatePicker = false
                    } else {
                        val selectedDate = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                        }
                        onSchedule(calendar.timeInMillis)
                    }
                }
            ) {
                Text(if (showDatePicker) "Next" else "Schedule")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (!showDatePicker) {
                        showDatePicker = true
                    } else {
                        onDismissRequest()
                    }
                }
            ) {
                Text(if (!showDatePicker) "Back" else "Cancel")
            }
        }
    )
}
