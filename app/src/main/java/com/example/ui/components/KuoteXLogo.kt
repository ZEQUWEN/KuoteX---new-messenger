package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * KuoteX Logo Composable supporting high-res metallic image rendering with
 * drop shadows and optional neon ambient glow.
 */
@Composable
fun KuoteXLogoImage(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    shape: Shape = RoundedCornerShape(28.dp),
    showGlow: Boolean = true,
    glowColor: Color = Color(0xFF00E5FF),
    elevation: Dp = 12.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (showGlow) {
            Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.05f)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.45f),
                            glowColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = this.size.minDimension / 1.5f
                    ),
                    radius = this.size.minDimension / 1.5f
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation = elevation, shape = shape, spotColor = Color.Black.copy(alpha = 0.8f))
                .clip(shape)
                .background(Color(0xFF1E2024))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color(0xFF8E95A5).copy(alpha = 0.3f),
                            Color(0xFF2A2D34)
                        )
                    ),
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.kuotex_logo),
                contentDescription = "KuoteX Logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Vector Canvas geometric renderer of the iconic KuoteX "K" logo.
 * Features the signature 45-degree diagonal slit separating the lower-left stem,
 * flush horizontal extremities, and 3D bevel lighting.
 */
@Composable
fun KuoteXGeometricK(
    modifier: Modifier = Modifier,
    fillColor: Color = Color(0xFF111317),
    accentHighlightColor: Color = Color(0xFFC0C7D5),
    shadowColor: Color = Color(0xFF000000).copy(alpha = 0.75f),
    strokeWidth: Float = 0f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Coords normalized to w and h
        // Left vertical stem: x from w * 0.16 to w * 0.38
        val leftX = w * 0.18f
        val stemRightX = w * 0.38f
        val topY = h * 0.12f
        val bottomY = h * 0.84f

        // Diagonal cut parameters (45-degree slash)
        val cutTopY = h * 0.52f
        val cutBottomY = h * 0.63f
        val slitGap = h * 0.055f

        // Top stem piece + upper/lower diagonal arms
        val topPiece = Path().apply {
            moveTo(leftX, topY)
            lineTo(stemRightX, topY)
            // junction with upper arm
            lineTo(stemRightX, h * 0.40f)
            // upper arm top diagonal to top-right
            lineTo(w * 0.64f, topY)
            lineTo(w * 0.84f, topY) // top-right end
            // upper arm lower edge
            lineTo(w * 0.54f, h * 0.48f)
            // lower arm upper edge
            lineTo(w * 0.84f, bottomY) // bottom-right end
            lineTo(w * 0.64f, bottomY)
            // lower arm inner junction
            lineTo(stemRightX, h * 0.58f)
            // top piece bottom diagonal cut: from stemRightX, cutTopY down-left to leftX, cutBottomY
            lineTo(stemRightX, cutTopY)
            lineTo(leftX, cutBottomY)
            close()
        }

        // Bottom detached wedge piece (lower stem)
        val bottomPiece = Path().apply {
            val wedgeTopLeftY = cutBottomY + slitGap
            val wedgeTopRightY = cutTopY + slitGap
            moveTo(leftX, wedgeTopLeftY)
            lineTo(stemRightX, wedgeTopRightY)
            lineTo(stemRightX, bottomY)
            lineTo(leftX, bottomY)
            close()
        }

        // Draw soft drop shadow for 3D depth
        drawPath(
            path = topPiece,
            color = shadowColor,
            style = Fill
        )
        drawPath(
            path = bottomPiece,
            color = shadowColor,
            style = Fill
        )

        // Draw main black satin fill
        drawPath(
            path = topPiece,
            brush = Brush.linearGradient(
                colors = listOf(fillColor, Color(0xFF1E2129), fillColor),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            style = Fill
        )
        drawPath(
            path = bottomPiece,
            brush = Brush.linearGradient(
                colors = listOf(fillColor, Color(0xFF1E2129)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            style = Fill
        )

        // Draw metallic bevel highlight along edges
        drawPath(
            path = topPiece,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentHighlightColor.copy(alpha = 0.7f),
                    Color.Transparent,
                    accentHighlightColor.copy(alpha = 0.3f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, 0f)
            ),
            style = Stroke(width = if (strokeWidth > 0) strokeWidth else w * 0.015f)
        )
        drawPath(
            path = bottomPiece,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentHighlightColor.copy(alpha = 0.6f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(w, 0f)
            ),
            style = Stroke(width = if (strokeWidth > 0) strokeWidth else w * 0.015f)
        )
    }
}

/**
 * Compact KuoteX header badge suitable for TopAppBar or Navigation Drawer.
 */
@Composable
fun KuoteXHeaderLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.5f),
                        Color(0xFF00E5FF).copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(8.dp)
            )
            .background(Color(0xFF1A1C23)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.kuotex_logo),
            contentDescription = "KuoteX Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
