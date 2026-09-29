package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AvatarViewerDialog(
    avatars: List<String>,
    initialPage: Int = 0,
    onDismiss: () -> Unit
) {
    if (avatars.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialPage.coerceIn(0, (avatars.size - 1).coerceAtLeast(0)),
            pageCount = { avatars.size }
        )

        val coroutineScope = rememberCoroutineScope()
        val offsetY = remember { Animatable(0f) }
        var isDragging by remember { mutableStateOf(false) }

        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val dismissThresholdPx = with(density) { 120.dp.toPx() }

        val backgroundAlpha = (1f - (offsetY.value / (screenHeightPx * 0.45f))).coerceIn(0f, 1f)
        val cardScale = (1f - (offsetY.value / (screenHeightPx * 2.2f))).coerceIn(0.80f, 1f)
        val cornerRadius = ((offsetY.value / dismissThresholdPx) * 32f).coerceIn(0f, 32f).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            if (offsetY.value > dismissThresholdPx) {
                                coroutineScope.launch {
                                    offsetY.animateTo(
                                        targetValue = screenHeightPx,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    onDismiss()
                                }
                            } else {
                                coroutineScope.launch {
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            coroutineScope.launch {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (offsetY.value + dragAmount).coerceAtLeast(0f)
                            coroutineScope.launch {
                                offsetY.snapTo(next)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        clip = true
                        shape = RoundedCornerShape(cornerRadius)
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .background(Color.Black)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    AsyncImage(
                        model = avatars[page],
                        contentDescription = "Profile Avatar $page",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Drag indicator pull pill at the top
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .width(42.dp)
                        .height(4.5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.55f))
                )

                // Top bar with segmented dashes indicator for multiple avatars
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 22.dp, start = 16.dp, end = 16.dp)
                ) {
                    if (avatars.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(avatars.size) { index ->
                                val isCurrent = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f))
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
