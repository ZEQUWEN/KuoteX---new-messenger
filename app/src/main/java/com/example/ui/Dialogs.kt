package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
        val actualCount = avatars.size
        val pagerState = rememberPagerState(
            initialPage = initialPage.coerceIn(0, actualCount - 1),
            pageCount = { actualCount }
        )

        val currentActualIndex = pagerState.currentPage.coerceIn(0, actualCount - 1)

        val coroutineScope = rememberCoroutineScope()
        val offsetY = remember { Animatable(0f) }
        var isDragging by remember { mutableStateOf(false) }

        // Track zoom scale of currently active page to disable pager horizontal scroll while zoomed
        var activeZoomScale by remember { mutableFloatStateOf(1f) }

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
                .pointerInput(activeZoomScale) {
                    // Only enable pull-down dismiss gesture when photo is at normal scale (not zoomed in)
                    if (activeZoomScale <= 1.05f) {
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
                    }
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
                // Smooth Horizontal Pager for avatar browsing
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = actualCount > 1 && activeZoomScale <= 1.05f
                ) { page ->
                    val itemIndex = page.coerceIn(0, avatars.lastIndex)
                    ZoomableAvatarPage(
                        imageUrl = avatars[itemIndex],
                        pageIndex = itemIndex,
                        onZoomScaleChanged = { scale ->
                            if (page == pagerState.currentPage) {
                                activeZoomScale = scale
                            }
                        }
                    )
                }

                // Reset zoom scale when page changes
                LaunchedEffect(pagerState.currentPage) {
                    activeZoomScale = 1f
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
                    if (actualCount > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(actualCount) { index ->
                                val isCurrent = currentActualIndex == index
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

                    // Top action bar: clean close button, no obstructing photo numbering
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-performance zoomable avatar page with STRICT zoom limits (1.0x to 2.5x max)
 * to permanently eliminate any possibility of infinite zooming.
 */
@Composable
private fun ZoomableAvatarPage(
    imageUrl: String,
    pageIndex: Int,
    onZoomScaleChanged: (Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Strict zoom limits
    val minZoomScale = 1.0f
    val maxZoomScale = 2.5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // Apply strict clamp to prevent infinite zoom bug
                    val newScale = (scale * zoom).coerceIn(minZoomScale, maxZoomScale)
                    scale = newScale
                    onZoomScaleChanged(newScale)

                    if (newScale > 1.0f) {
                        // Limit pan offset based on zoomed excess
                        val maxPanX = (size.width * (newScale - 1f)) / 2f
                        val maxPanY = (size.height * (newScale - 1f)) / 2f
                        panOffset = Offset(
                            x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    } else {
                        panOffset = Offset.Zero
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = panOffset.x
                translationY = panOffset.y
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Profile Avatar $pageIndex",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
