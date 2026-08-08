package com.eliteonetube.momentum.ui.theme.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eliteonetube.momentum.data.FoodItem
import com.eliteonetube.momentum.logic.ScannedNutrition

@Composable
fun FoodReviewDialog(
    scanned: ScannedNutrition,
    barcode: String? = null,
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit
) {
    var name by remember { mutableStateOf(scanned.name ?: "") }
    var calories by remember { mutableStateOf(scanned.calories?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "") }
    var protein by remember { mutableStateOf(scanned.protein?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "") }
    var fat by remember { mutableStateOf(scanned.fat?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "") }
    var carbs by remember { mutableStateOf(scanned.carbs?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "") }
    var servingSize by remember { mutableStateOf("100g") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Confirm Item", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }

                Text(
                    "Review detected facts and add a name.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Name Card
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Food Name") },
                    placeholder = { Text("e.g. Greek Yogurt") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    supportingText = { if (barcode != null) Text("Barcode: $barcode") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Nutrition Values Grid
                Text("Nutrition Facts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NutritionInput(
                                label = "Calories",
                                value = calories,
                                onValueChange = { calories = it },
                                modifier = Modifier.weight(1.5f),
                                icon = Icons.Default.LocalFireDepartment,
                                color = MaterialTheme.colorScheme.primary
                            )
                            NutritionInput(
                                label = "Serving",
                                value = servingSize,
                                onValueChange = { servingSize = it },
                                modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Text
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NutritionInput("Protein", protein, { protein = it }, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            NutritionInput("Fat", fat, { fat = it }, Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary)
                            NutritionInput("Carbs", carbs, { carbs = it }, Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        onSave(
                            FoodItem(
                                name = name.ifBlank { "Scanned Item" },
                                calories = calories.toDoubleOrNull() ?: 0.0,
                                protein = protein.toDoubleOrNull() ?: 0.0,
                                fat = fat.toDoubleOrNull() ?: 0.0,
                                carbs = carbs.toDoubleOrNull() ?: 0.0,
                                servingSize = servingSize,
                                isCustom = true,
                                barcode = barcode
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = name.isNotBlank() && calories.isNotBlank()
                ) {
                    Text("Save to Database", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun NutritionInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = color
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}
