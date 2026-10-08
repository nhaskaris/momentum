package com.eliteonetube.momentum.ui.screens.onboarding

import com.eliteonetube.momentum.ui.components.PrimaryButton
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.ui.screens.dashboard.MascotMood
import com.eliteonetube.momentum.ui.screens.dashboard.MomentumMascot

data class TutorialStep(
    val title: String,
    val description: String,
    val targetTag: String
)

@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    onStepCompleted: (Int) -> Unit,
    onFinished: () -> Unit,
    targetPositions: Map<String, Rect>
) {
    var currentStepIdx by remember { mutableStateOf(0) }
    val currentStep = steps.getOrNull(currentStepIdx) ?: return
    
    val targetRect = targetPositions[currentStep.targetTag] ?: Rect.Zero
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Animate the highlight hole position and size
    val animatedTopLeft by animateOffsetAsState(
        targetValue = with(density) { Offset(targetRect.left - 12.dp.toPx(), targetRect.top - 12.dp.toPx()) },
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "holeOffset"
    )
    val animatedSize by animateSizeAsState(
        targetValue = with(density) { Size(targetRect.width + 24.dp.toPx(), targetRect.height + 24.dp.toPx()) },
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "holeSize"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consume clicks */ }
    ) {
        // Dimmed background with hole
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.99f)) {
            drawRect(color = Color.Black.copy(alpha = 0.75f))
            
            if (targetRect != Rect.Zero) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = animatedTopLeft,
                    size = animatedSize,
                    cornerRadius = with(density) { CornerRadius(16.dp.toPx()) },
                    blendMode = BlendMode.Clear
                )
            }
        }
        
        // Tooltip box position logic
        val isTargetInBottomHalf = targetRect.top > (screenHeightPx / 2)
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = if (isTargetInBottomHalf) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            // Animate card content changes
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "tooltipContent"
            ) { step ->
                Column(
                    horizontalAlignment = if (isTargetInBottomHalf) Alignment.CenterHorizontally else Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isTargetInBottomHalf) {
                        MomentumMascot(mood = MascotMood.IDLE, modifier = Modifier.size(64.dp))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "TIP ${steps.indexOf(step) + 1} OF ${steps.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                // Seven tips before you can type anything is a lot; let people skip ahead
                                if (currentStepIdx < steps.lastIndex) {
                                    TextButton(onClick = onFinished) { Text("Skip tour") }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                PrimaryButton(
                                    onClick = {
                                        if (currentStepIdx < steps.lastIndex) {
                                            currentStepIdx++
                                            onStepCompleted(currentStepIdx)
                                        } else {
                                            onFinished()
                                        }
                                    }
                                ) {
                                    Text(if (currentStepIdx == steps.lastIndex) "Start Journey" else "Next")
                                }
                            }
                        }
                    }

                    if (!isTargetInBottomHalf) {
                        MomentumMascot(mood = MascotMood.IDLE, modifier = Modifier.size(64.dp))
                    }
                }
            }
        }
    }
}

fun Modifier.tutorialTarget(
    tag: String,
    onPositioned: (String, Rect) -> Unit
) = this.onGloballyPositioned { coordinates ->
    val position = coordinates.positionInRoot()
    val size = coordinates.size
    onPositioned(
        tag,
        Rect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height
        )
    )
}
