package com.eliteonetube.momentum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private val loadingTips = listOf(
    "Weighing yourself at the same time each day gives the most consistent trend.",
    "Day-to-day weight swings are mostly water — look at your 7-day trend, not one reading.",
    "Protein at each meal helps preserve muscle while in a calorie deficit.",
    "Steps count as exercise too — walking is one of the most sustainable ways to burn extra calories.",
    "Sleep affects hunger hormones — poor sleep can make a deficit feel much harder.",
    "A stall for a few days is normal. Trends matter more than any single day.",
    "Drinking water before a meal can help with portion awareness."
)

@Composable
fun LoadingScreen() {
    // remember{} picks once per composition (i.e. once per app launch), not on every recomposition
    val tip = remember { loadingTips[Random.nextInt(loadingTips.size)] }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Loading your profile…", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = tip,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}