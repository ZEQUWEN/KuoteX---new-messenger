package com.example.ui

import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.botapi.holdToReorder
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineButtonGrid(
    buttons: List<String>,
    onButtonClick: (String) -> Unit,
    onReorderComplete: (List<String>) -> Unit,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start
) {
    var editMode by remember { mutableStateOf(false) }
    var currentButtons by remember(buttons) { mutableStateOf(buttons) }
    
    val context = LocalContext.current
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    
    if (editMode) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Edit Layout Mode (Drag to reorder)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = horizontalArrangement,
                modifier = Modifier.fillMaxWidth()
            ) {
                currentButtons.forEachIndexed { index, buttonText ->
                    val isDragging = index == draggingIndex
                    
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp, bottom = 4.dp)
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { if (isDragging) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) else IntOffset.Zero }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { 
                                        draggingIndex = index
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                        val newIndex = index + (dragOffset.x / 100.dp.toPx()).toInt()
                                        val targetIndex = newIndex.coerceIn(0, currentButtons.size - 1)
                                        if (targetIndex != index && draggingIndex == index) {
                                            val mutable = currentButtons.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(targetIndex, item)
                                            currentButtons = mutable
                                            draggingIndex = targetIndex
                                            dragOffset = Offset.Zero
                                        }
                                    },
                                    onDragEnd = {
                                        draggingIndex = null
                                        dragOffset = Offset.Zero
                                        editMode = false
                                        onReorderComplete(currentButtons)
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        dragOffset = Offset.Zero
                                    }
                                )
                            }
                    ) {
                        OutlinedButton(
                            onClick = { },
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            val display = if (buttonText.contains("::")) buttonText.substringBefore("::") else buttonText
                            Text(display, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            horizontalAlignment = if (horizontalArrangement == Arrangement.End) Alignment.End else Alignment.Start
        ) {
            val rows = currentButtons.chunked(2)
            rows.forEach { rowButtons ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = horizontalArrangement) {
                    rowButtons.forEach { buttonText ->
                val scope = rememberCoroutineScope()
                var isPressed by remember { mutableStateOf(false) }
                
                OutlinedButton(
                    onClick = { onButtonClick(buttonText) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp, bottom = 4.dp)
                        .holdToReorder(
                            durationMs = 3000L,
                            onReorderStart = {
                                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                editMode = true
                            },
                            onDrag = { _, _ -> },
                            onDragEnd = { }
                        ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    val display = if (buttonText.contains("::")) buttonText.substringBefore("::") else buttonText
                    Text(display, style = MaterialTheme.typography.labelMedium)
                }
                    }
                }
            }
        }
    }
}
