package com.eliteonetube.momentum.ui.theme.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eliteonetube.momentum.data.FoodItem

@Composable
fun QuickMacroDialog(
    onDismiss: () -> Unit,
    onConfirm: (FoodItem) -> Unit
) {
    var proteinInput by remember { mutableStateOf("") }
    var carbsInput by remember { mutableStateOf("") }
    var fatInput by remember { mutableStateOf("") }

    val protein = proteinInput.toDoubleOrNull() ?: 0.0
    val carbs = carbsInput.toDoubleOrNull() ?: 0.0
    val fat = fatInput.toDoubleOrNull() ?: 0.0

    val totalCalories = (protein * 4) + (carbs * 4) + (fat * 9)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Quick Macro Log",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Log custom grams for each macronutrient. Calories are calculated automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = proteinInput,
                    onValueChange = { proteinInput = it },
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = carbsInput,
                    onValueChange = { carbsInput = it },
                    label = { Text("Carbs (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = fatInput,
                    onValueChange = { fatInput = it },
                    label = { Text("Fats (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (totalCalories > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Estimated Calories", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${totalCalories.toInt()} kcal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nameParts = mutableListOf<String>()
                    if (protein > 0) nameParts.add("${protein.toInt()}g P")
                    if (carbs > 0) nameParts.add("${carbs.toInt()}g C")
                    if (fat > 0) nameParts.add("${fat.toInt()}g F")
                    
                    val name = if (nameParts.isEmpty()) "Quick Log" else "Log: " + nameParts.joinToString(", ")
                    
                    onConfirm(
                        FoodItem(
                            name = name,
                            calories = totalCalories,
                            protein = protein,
                            fat = fat,
                            carbs = carbs,
                            isCustom = true
                        )
                    )
                },
                enabled = totalCalories > 0,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Entry", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
