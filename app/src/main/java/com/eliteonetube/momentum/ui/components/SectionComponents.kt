package com.eliteonetube.momentum.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shared building blocks for the card-less screen style used by the Dashboard and Analytics.

/** Small spaced capitals in the accent colour that head a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(bottom = 12.dp)
    )
}

/** Hairline with breathing room that separates sections in place of cards. */
@Composable
fun SectionDivider() {
    Spacer(modifier = Modifier.height(28.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(modifier = Modifier.height(28.dp))
}

/** Big heavy number with a smaller muted unit beside it. */
@Composable
fun BigStat(value: String, unit: String, valueColor: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-1).sp),
            fontWeight = FontWeight.Black,
            color = valueColor,
            modifier = Modifier.alignByBaseline()
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            unit,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
    }
}
