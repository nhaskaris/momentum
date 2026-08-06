package com.eliteonetube.momentum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun NutritionScreen(
    calorieTarget: Int,
    profile: UserProfile,
    recentWeights: List<WeightEntry>
) {
    val currentWeightKg = recentWeights.firstOrNull()?.weight ?: profile.height

    // Updated Macro Calculation:
    // Protein: 2.0g per kg
    // Fat: 0.8g per kg
    // Carbs: Remaining calories
    val proteinGrams = (currentWeightKg * 2.0).roundToInt()
    val fatGrams = (currentWeightKg * 0.8).roundToInt()
    
    val proteinCalories = proteinGrams * 4
    val fatCalories = fatGrams * 9
    val carbCalories = (calorieTarget - proteinCalories - fatCalories).coerceAtLeast(0)
    val carbGrams = (carbCalories / 4.0).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Nutrition Facts",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Daily Target",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$calorieTarget kcal",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Recommended Macros",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        MacroRow("Protein", "$proteinGrams g", "${((proteinCalories.toDouble() / calorieTarget) * 100).roundToInt()}%", MaterialTheme.colorScheme.primary)
        MacroRow("Fats", "$fatGrams g", "${((fatCalories.toDouble() / calorieTarget) * 100).roundToInt()}%", MaterialTheme.colorScheme.tertiary)
        MacroRow("Carbs", "$carbGrams g", "${((carbCalories.toDouble() / calorieTarget) * 100).roundToInt()}%", MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Why these macros?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        MacroExplanationCard(
            title = "Protein: The Builder",
            description = "Crucial for repairing and building muscle tissue. Keeping protein high (2.0g/kg) helps you preserve muscle while losing fat or build muscle while gaining.",
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        MacroExplanationCard(
            title = "Fats: The Regulator",
            description = "Essential for hormone production (like testosterone) and brain health. A minimum of 0.8g/kg ensures your body functions optimally.",
            color = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

        MacroExplanationCard(
            title = "Carbs: The Fuel",
            description = "Your body's primary energy source. Carbs fuel your high-intensity workouts and aid in recovery. They fill the rest of your calorie budget.",
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Note",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "These targets are calculated based on your current weight of ${String.format(Locale.US, "%.1f", currentWeightKg)}kg. As your weight changes, these recommendations will update automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MacroRow(label: String, amount: String, percentage: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(12.dp),
                color = color,
                shape = RoundedCornerShape(2.dp)
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(amount, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(8.dp))
            Text(percentage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MacroExplanationCard(title: String, description: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.3f)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
