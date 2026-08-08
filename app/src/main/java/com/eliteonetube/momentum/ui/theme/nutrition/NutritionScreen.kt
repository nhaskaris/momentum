package com.eliteonetube.momentum.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eliteonetube.momentum.data.FoodItem
import com.eliteonetube.momentum.data.FoodLogWithItem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import kotlin.math.roundToInt
import java.util.Locale

@Composable
fun NutritionScreen(
    calorieTarget: Int,
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    todayLogs: List<FoodLogWithItem> = emptyList(),
    allFoodItems: List<FoodItem> = emptyList(),
    onLogFood: (Long, Double) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onEditLog: (FoodLogWithItem) -> Unit,
    onStartScan: () -> Unit
) {
    val currentWeightKg = recentWeights.firstOrNull()?.weight ?: profile.height

    // Consumed stats
    val caloriesConsumed = todayLogs.sumOf { it.calories * it.quantity }
    val proteinConsumed = todayLogs.sumOf { it.protein * it.quantity }
    val fatConsumed = todayLogs.sumOf { it.fat * it.quantity }
    val carbsConsumed = todayLogs.sumOf { it.carbs * it.quantity }

    // Targets
    val proteinGramsTarget = (currentWeightKg * 2.0).roundToInt()
    val fatGramsTarget = (currentWeightKg * 0.8).roundToInt()
    val carbGramsTarget = ((calorieTarget - (proteinGramsTarget * 4) - (fatGramsTarget * 9)) / 4.0).roundToInt()

    var showFoodSearch by remember { mutableStateOf(false) }

    if (showFoodSearch) {
        FoodSearchDialog(
            foodItems = allFoodItems,
            onDismiss = { showFoodSearch = false },
            onFoodSelected = { foodId, qty ->
                onLogFood(foodId, qty)
                showFoodSearch = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(top = 48.dp, bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "${(calorieTarget - caloriesConsumed).roundToInt()}",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "kcal remaining",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Macro Bars
                MacroBar("Protein", proteinConsumed.roundToInt(), proteinGramsTarget, MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                MacroBar("Fats", fatConsumed.roundToInt(), fatGramsTarget, MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(12.dp))
                MacroBar("Carbs", carbsConsumed.roundToInt(), carbGramsTarget, MaterialTheme.colorScheme.secondary)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showFoodSearch = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onStartScan,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Food", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Today's Logs
            SectionHeader("Today's Log", Icons.Default.Restaurant)
            
            if (todayLogs.isEmpty()) {
                EmptyState("No food logged yet. Use the scanner to start tracking.")
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        todayLogs.forEachIndexed { index, log ->
                            FoodLogItem(
                                log = log, 
                                onDelete = { onDeleteLog(log.id) },
                                onClick = { onEditLog(log) }
                            )
                            if (index < todayLogs.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Info Section
            SectionHeader("Coaching", Icons.Default.Info)
            MacroExplanationCard(
                title = "Protein: The Builder",
                description = "Crucial for repairing and building muscle tissue. Target: 2.0g/kg.",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            MacroExplanationCard(
                title = "Fats: The Regulator",
                description = "Essential for hormones and brain health. Target: 0.8g/kg.",
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(12.dp))
            MacroExplanationCard(
                title = "Carbs: The Fuel",
                description = "Primary energy source for intense training.",
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun MacroBar(label: String, current: Int, target: Int, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "$current / ${target}g",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(8.dp),
            color = color,
            shape = CircleShape
        ) {}
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyState(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Inbox, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FoodLogItem(log: FoodLogWithItem, onDelete: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(log.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(log.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    "${(log.calories * log.quantity).roundToInt()} kcal • ${if (log.quantity >= 1.0) log.quantity.roundToInt() else log.quantity} qty",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun MacroExplanationCard(title: String, description: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f)),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.05f))))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FoodSearchDialog(
    foodItems: List<FoodItem>,
    onDismiss: () -> Unit,
    onFoodSelected: (Long, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = foodItems.filter { it.name.contains(searchQuery, ignoreCase = true) }

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
                    Text("Search Foods", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if(searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredItems) { item ->
                        ListItem(
                            headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("${item.calories.toInt()} kcal per ${item.servingSize}") },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(item.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { 
                                onFoodSelected(item.id, 1.0)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}
