package com.eliteonetube.momentum.ui.theme.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.sin

enum class MascotMood { IDLE, HAPPY, ALERT }

@Composable
fun MomentumMascot(
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.IDLE
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascotIdle")
    
    // Base breathing/pulsing
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mood == MascotMood.HAPPY) 500 else 2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Vertical float/jump
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = if (mood == MascotMood.HAPPY) -12f else -4f,
        targetValue = if (mood == MascotMood.HAPPY) 4f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mood == MascotMood.HAPPY) 400 else 2500, easing = if (mood == MascotMood.HAPPY) EaseOutBack else EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // Eye blinking
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = if (mood == MascotMood.ALERT) 1000 else 4000
                1f at 0
                if (mood == MascotMood.ALERT) {
                    0.1f at 500
                } else {
                    1f at 3800
                    0.1f at 3900
                }
                1f at durationMillis
            }
        ),
        label = "blink"
    )

    // Color shifting based on mood
    val primaryColor by animateColorAsState(
        targetValue = when (mood) {
            MascotMood.IDLE -> MaterialTheme.colorScheme.primary
            MascotMood.HAPPY -> Color(0xFF00E676) // Bright Green
            MascotMood.ALERT -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(500),
        label = "color"
    )
    
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val center = Offset(size.width / 2, size.height / 2 + floatOffset)
            val mainSize = size.width * 0.6f * pulseScale

            // Draw Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                    center = center,
                    radius = mainSize * 2f
                ),
                center = center,
                radius = mainSize * 2f
            )

            // Draw Core Body (Rounded Diamond)
            withTransform({
                rotate(45f, center)
            }) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(primaryColor, secondaryColor),
                        start = Offset(center.x - mainSize / 2, center.y - mainSize / 2),
                        end = Offset(center.x + mainSize / 2, center.y + mainSize / 2)
                    ),
                    topLeft = Offset(center.x - mainSize / 2, center.y - mainSize / 2),
                    size = Size(mainSize, mainSize),
                    cornerRadius = CornerRadius(mainSize * 0.35f)
                )
            }

            // Draw Eyes
            val eyeWidth = 5.dp.toPx()
            val eyeHeight = (8.dp.toPx() * (if (mood == MascotMood.HAPPY) 0.6f else blinkProgress)).coerceAtLeast(1.dp.toPx())
            val eyeOffset = 9.dp.toPx()

            // Expression: squint if happy
            val yEyeShift = if (mood == MascotMood.HAPPY) -2.dp.toPx() else 0f

            // Left Eye
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(center.x - eyeOffset - eyeWidth / 2, center.y - eyeHeight / 2 + yEyeShift),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(eyeWidth / 2)
            )

            // Right Eye
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(center.x + eyeOffset - eyeWidth / 2, center.y - eyeHeight / 2 + yEyeShift),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(eyeWidth / 2)
            )
        }
    }
}
