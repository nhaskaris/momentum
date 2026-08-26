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
import androidx.compose.ui.window.DialogProperties
import com.eliteonetube.momentum.data.FoodItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFoodDialog(
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var servingAmount by remember { mutableStateOf("100") }
    var servingUnit by remember { mutableStateOf("g") }

    val isValid = name.isNotBlank() && 
                 calories.toDoubleOrNull() != null &&
                 protein.toDoubleOrNull() != null &&
                 carbs.toDoubleOrNull() != null &&
                 fat.toDoubleOrNull() != null &&
                 servingAmount.toDoubleOrNull() != null &&
                 servingUnit.isNotBlank()

    val units = listOf("g", "ml", "serving", "piece", "unit", "egg", "banana", "scoop")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                    Text("Create Custom Food", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    TextButton(
                        onClick = {
                            onSave(
                                FoodItem(
                                    name = name.trim(),
                                    calories = calories.toDoubleOrNull() ?: 0.0,
                                    protein = protein.toDoubleOrNull() ?: 0.0,
                                    fat = fat.toDoubleOrNull() ?: 0.0,
                                    carbs = carbs.toDoubleOrNull() ?: 0.0,
                                    servingSize = "$servingAmount $servingUnit",
                                    servingAmount = servingAmount.toDoubleOrNull() ?: 100.0,
                                    servingUnit = servingUnit.trim(),
                                    isCustom = true
                                )
                            )
                        },
                        enabled = isValid
                    ) {
                        Text("Save", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }

                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Food Name") },
                        placeholder = { Text("e.g. Grandma's Apple Pie") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = servingAmount,
                            onValueChange = { servingAmount = it },
                            label = { Text("Serving Amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = servingUnit,
                            onValueChange = { servingUnit = it },
                            label = { Text("Unit (e.g. g, ml, piece)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = calories,
                            onValueChange = { calories = it },
                            label = { Text("Calories") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = protein,
                            onValueChange = { protein = it },
                            label = { Text("Protein (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = carbs,
                            onValueChange = { carbs = it },
                            label = { Text("Carbs (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = fat,
                            onValueChange = { fat = it },
                            label = { Text("Fat (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }
}
