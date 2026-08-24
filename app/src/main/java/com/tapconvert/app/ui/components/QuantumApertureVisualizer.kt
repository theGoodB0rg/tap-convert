package com.tapconvert.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.PrimaryTeal
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.app.ui.theme.SecondaryCyan

/**
 * High-performance, GPU-rasterized Quantum Aperture visualizer.
 * Supports both indeterminate intake mode and deterministic percentage processing mode.
 */
@Composable
fun QuantumApertureVisualizer(
    percentage: Int? = null,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 150.dp,
    primaryColor: Color = PrimaryTeal,
    accentColor: Color = SecondaryCyan,
    glowColor: Color = SavingsGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "quantumAperture")

    // Continuous 360-degree rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "apertureRotation"
    )

    // Breathing pulse for ambient glow and inner halo
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "apertureBreathing"
    )

    val animatedPercentageProgress by animateFloatAsState(
        targetValue = (percentage ?: 0) / 100f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "apertureProgress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(sizeDp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = (sizeDp.toPx() * 0.065f).coerceIn(6f, 18f)
            val halfStroke = strokeWidthPx / 2f
            val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
            val arcTopLeft = Offset(halfStroke, halfStroke)

            // 1. Outer Ambient Glow Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.18f * breathingPulse),
                        accentColor.copy(alpha = 0.08f * breathingPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension / 2f
                )
            )

            // 2. Base Track Ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.12f),
                radius = (size.minDimension - strokeWidthPx) / 2f,
                style = Stroke(width = strokeWidthPx * 0.6f)
            )

            // 3. Rotating Gradient Aperture Orbit
            rotate(rotationAngle, pivot = center) {
                val sweepBrush = Brush.sweepGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.1f),
                        accentColor.copy(alpha = 0.85f),
                        primaryColor,
                        AccentAmber.copy(alpha = 0.7f),
                        primaryColor.copy(alpha = 0.1f)
                    )
                )

                if (percentage == null) {
                    // Indeterminate Mode: 270-degree sweeping arc
                    drawArc(
                        brush = sweepBrush,
                        startAngle = 0f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                } else {
                    // Determinate Progress Mode: Arc scaled to progress percentage
                    val sweep = (animatedPercentageProgress * 360f).coerceIn(4f, 360f)
                    drawArc(
                        brush = sweepBrush,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Center Content
        if (percentage != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = (sizeDp.value * 0.22f).sp,
                        letterSpacing = (-0.5).sp
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!badgeText.isNullOrBlank()) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = (sizeDp.value * 0.075f).sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = glowColor
                    )
                }
            }
        } else {
            // Intake Centerpiece: Pulsing Electric Bolt
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(sizeDp * 0.36f)
            )
        }
    }
}
