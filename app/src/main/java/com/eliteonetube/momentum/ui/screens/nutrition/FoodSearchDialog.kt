package com.eliteonetube.momentum.ui.screens.nutrition

import com.eliteonetube.momentum.ui.components.filledFieldColors
import com.eliteonetube.momentum.ui.components.FieldShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eliteonetube.momentum.data.FoodItem

@Composable
fun FoodSearchDialog(
    foodItems: List<FoodItem>,
    onDismiss: () -> Unit,
    onFoodSelected: (Long, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFoodForQuantity by remember { mutableStateOf<FoodItem?>(null) }
    val filteredItems = foodItems.filter { !it.isQuickLog && it.name.contains(searchQuery, ignoreCase = true) }

    if (selectedFoodForQuantity != null) {
        LogQuantityDialog(
            foodItem = selectedFoodForQuantity!!,
            onDismiss = { selectedFoodForQuantity = null },
            onConfirm = { qty ->
                onFoodSelected(selectedFoodForQuantity!!.id, qty)
                selectedFoodForQuantity = null
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add to meal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search your foods") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if(searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                    singleLine = true,
                    shape = FieldShape,
                    colors = filledFieldColors()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredItems) { item ->
                        ListItem(
                            headlineContent = { Text(item.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("${item.calories.toInt()} kcal per ${item.servingSize}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(item.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { 
                                selectedFoodForQuantity = item
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
