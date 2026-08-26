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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMacroDialog(
    onDismiss: () -> Unit,
    onConfirm: (FoodItem) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Macros, 1: Calories
    
    var proteinInput by remember { mutableStateOf("") }
    var carbsInput by remember { mutableStateOf("") }
    var fatInput by remember { mutableStateOf("") }
    var calorieInput by remember { mutableStateOf("") }

    val protein = proteinInput.toDoubleOrNull() ?: 0.0
    val carbs = carbsInput.toDoubleOrNull() ?: 0.0
    val fat = fatInput.toDoubleOrNull() ?: 0.0
    val manualCalories = calorieInput.toDoubleOrNull() ?: 0.0

    val calculatedCalories = (protein * 4) + (carbs * 4) + (fat * 9)
    val totalCalories = if (selectedTab == 0) calculatedCalories else manualCalories

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Quick Log",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Macros")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Calories")
                    }
                }

                if (selectedTab == 0) {
                    Text(
                        "Log custom grams for each macronutrient.",
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
                } else {
                    Text(
                        "Log calories directly without macro breakdown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = calorieInput,
                        onValueChange = { calorieInput = it },
                        label = { Text("Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

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
                            Text("Total Logged", style = MaterialTheme.typography.labelMedium)
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
                    val name: String
                    if (selectedTab == 0) {
                        val nameParts = mutableListOf<String>()
                        if (protein > 0) nameParts.add("${protein.toInt()}g P")
                        if (carbs > 0) nameParts.add("${carbs.toInt()}g C")
                        if (fat > 0) nameParts.add("${fat.toInt()}g F")
                        name = if (nameParts.isEmpty()) "Quick Log" else "Log: " + nameParts.joinToString(", ")
                    } else {
                        name = "Quick Calories"
                    }
                    
                    onConfirm(
                        FoodItem(
                            name = name,
                            calories = totalCalories,
                            protein = if (selectedTab == 0) protein else 0.0,
                            fat = if (selectedTab == 0) fat else 0.0,
                            carbs = if (selectedTab == 0) carbs else 0.0,
                            servingSize = "1 serving",
                            servingAmount = 1.0,
                            servingUnit = "serving",
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
