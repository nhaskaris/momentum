package com.eliteonetube.momentum.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Optimized Physical feedback on touch-down.
 * Uses InteractionSource to avoid heavy pointerInput logic.
 */
fun Modifier.bounceClick(
    onClick: (() -> Unit)? = null
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .let {
            if (onClick != null) {
                it.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else it
        }
}
