package com.example.ui.botapi

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeout

fun Modifier.holdToReorder(
    durationMs: Long = 3000L,
    onReorderStart: (Offset) -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var longPress: PointerInputChange? = null
        try {
            withTimeout(durationMs) {
                var upOrCancel = false
                while (!upOrCancel) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.any { !it.pressed || it.isConsumed }) {
                        upOrCancel = true
                    }
                }
            }
        } catch (e: PointerEventTimeoutCancellationException) {
            longPress = down
        }

        if (longPress != null) {
            onReorderStart(longPress.position)
            var isDragging = true
            while (isDragging) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val dragEvent = event.changes.firstOrNull { it.id == longPress.id }
                if (dragEvent == null || !dragEvent.pressed) {
                    isDragging = false
                    onDragEnd()
                } else {
                    if (dragEvent.positionChange() != Offset.Zero) {
                        dragEvent.consume()
                        onDrag(dragEvent, dragEvent.positionChange())
                    }
                }
            }
        }
    }
}
