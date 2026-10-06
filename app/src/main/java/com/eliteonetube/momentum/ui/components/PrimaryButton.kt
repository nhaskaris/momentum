package com.eliteonetube.momentum.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The app's two button sizes: these large ones for a screen's main full-width action, and
// Material's standard 40dp buttons for everything else. Tune the look here, not at call sites.

private val PrimaryButtonHeight = 52.dp
private val PrimaryButtonShape = RoundedCornerShape(16.dp)

@Composable
private fun primaryLabelStyle() = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

/** Filled main action, e.g. "Start workout", "Add Food", "Lock it in". Slightly shrinks while pressed. */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    // One interaction source for both the button and the bounce, so the press actually drives the scale
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "primaryButtonScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(PrimaryButtonHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = PrimaryButtonShape,
        colors = colors,
        interactionSource = interactionSource
    ) {
        ProvideTextStyle(primaryLabelStyle()) { content() }
    }
}

/** Outlined companion that sits next to a [PrimaryButton] (e.g. "Cancel", "Back") at the same size. */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(PrimaryButtonHeight),
        enabled = enabled,
        shape = PrimaryButtonShape,
        colors = colors,
        border = border
    ) {
        ProvideTextStyle(primaryLabelStyle()) { content() }
    }
}
