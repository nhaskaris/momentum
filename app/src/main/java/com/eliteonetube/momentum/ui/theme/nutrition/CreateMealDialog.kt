package com.eliteonetube.momentum.ui.theme.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eliteonetube.momentum.data.FoodItem
import com.eliteonetube.momentum.ui.theme.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMealDialog(
    allFoodItems: List<FoodItem>,
    onDismiss: () -> Unit,
    onStartScan: () -> Unit,
    onSave: (name: String, items: List<Pair<FoodItem, Double>>) -> Unit
) {
    var mealName by remember { mutableStateOf("") }
    var selectedItems by remember { mutableStateOf<List<Pair<FoodItem, Double>>>(emptyList()) }
    var showSearch by remember { mutableStateOf(false) }

    if (showSearch) {
        FoodSearchDialog(
            foodItems = allFoodItems,
            onDismiss = { showSearch = false },
            onFoodSelected = { foodId, _ ->
                val food = allFoodItems.find { it.id == foodId }
                if (food != null) {
                    selectedItems = selectedItems + (food to 1.0)
                }
                showSearch = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                    Text("Create Meal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    TextButton(
                        onClick = { onSave(mealName.trim(), selectedItems) },
                        enabled = mealName.isNotBlank() && selectedItems.isNotEmpty()
                    ) {
                        Text("Save", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    OutlinedTextField(
                        value = mealName,
                        onValueChange = { mealName = it },
                        label = { Text("Meal Name") },
                        placeholder = { Text("e.g. Protein Smoothie") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Ingredients (${selectedItems.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onStartScan) {
                                Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Button(
                                onClick = { showSearch = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Item")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
                    items(selectedItems) { (item, qty) ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold)
                                    Text("${item.calories.toInt()} kcal per ${item.servingSize}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { 
                                    selectedItems = selectedItems.filter { it.first.id != item.id }
                                }) {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
