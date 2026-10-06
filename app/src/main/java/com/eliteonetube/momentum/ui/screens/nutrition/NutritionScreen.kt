package com.eliteonetube.momentum.ui.screens.nutrition

import com.eliteonetube.momentum.ui.components.PrimaryButton
import com.eliteonetube.momentum.ui.components.SecondaryButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.ui.theme.*
import com.eliteonetube.momentum.ui.components.SectionDivider
import com.eliteonetube.momentum.ui.components.SectionLabel
import com.eliteonetube.momentum.ui.components.bounceClick
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NutritionScreen(
    calorieTarget: Int,
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    todayLogs: List<FoodLogWithItem> = emptyList(),
    allFoodItems: List<FoodItem> = emptyList(),
    allMeals: List<Meal> = emptyList(),
    onLogFood: (Long, Double) -> Unit,
    onLogMeal: (Long) -> Unit,
    onDeleteLog: (Long, Boolean) -> Unit,
    onEditLog: (FoodLogWithItem) -> Unit,
    onQuickLog: (FoodItem) -> Unit,
    onMealCreated: (String, List<Pair<FoodItem, Double>>) -> Unit,
    onFoodCreated: (FoodItem) -> Unit,
    onStartScan: () -> Unit,
    onOpenLearn: () -> Unit = {}
) {
    // Newest logged weight (recentWeights is the latest entries, not a date window)
    val currentWeightKg = recentWeights.firstOrNull()?.weight

    // Deletes wait for the Undo snackbar to close; until then the entry is just hidden
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingDeleteKeys by remember { mutableStateOf(emptySet<String>()) }
    val visibleLogs = remember(todayLogs, pendingDeleteKeys) { todayLogs.filterNot { it.listKey in pendingDeleteKeys } }

    val caloriesConsumed = remember(visibleLogs) { visibleLogs.sumOf { it.calories * it.quantity } }
    val proteinConsumed = remember(visibleLogs) { visibleLogs.sumOf { it.protein * it.quantity } }
    val fatConsumed = remember(visibleLogs) { visibleLogs.sumOf { it.fat * it.quantity } }
    val carbsConsumed = remember(visibleLogs) { visibleLogs.sumOf { it.carbs * it.quantity } }

    val macroTargets = remember(calorieTarget, currentWeightKg) { MacroTargets.from(calorieTarget, currentWeightKg) }
    val remaining = (calorieTarget - caloriesConsumed).roundToInt()
    val isOver = remaining < 0
    val isDark = isSystemInDarkTheme()

    var showAddFood by remember { mutableStateOf(false) }

    if (showAddFood) {
        AddFoodDialog(
            foodItems = allFoodItems,
            allMeals = allMeals,
            onDismiss = { showAddFood = false },
            onFoodSelected = { foodId, qty ->
                onLogFood(foodId, qty)
                showAddFood = false
            },
            onMealSelected = { mealId ->
                onLogMeal(mealId)
                showAddFood = false
            },
            onMealCreated = onMealCreated,
            onFoodCreated = onFoodCreated,
            onQuickLog = onQuickLog,
            onStartScan = onStartScan
        )
    }

    fun deleteWithUndo(log: FoodLogWithItem) {
        // Only one Undo at a time: closing the previous snackbar commits its delete
        snackbarHostState.currentSnackbarData?.dismiss()
        pendingDeleteKeys = pendingDeleteKeys + log.listKey
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Removed ${log.name}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeleteKeys = pendingDeleteKeys - log.listKey
            } else {
                // Key stays hidden: ids are never reused, and it avoids a flash before the row leaves the list
                onDeleteLog(log.id, log.isMeal)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Hero Section — same gradient as the Dashboard hero
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.12f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(top = 48.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "%,d".format(if (isOver) -remaining else remaining),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 72.sp,
                            letterSpacing = (-4).sp
                        ),
                        fontWeight = FontWeight.Black,
                        color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isOver) "kcal over" else "kcal remaining",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    if (macroTargets != null) {
                        MacroBar("Protein", proteinConsumed.roundToInt(), macroTargets.proteinGrams, ProteinBlue)
                        Spacer(modifier = Modifier.height(12.dp))
                        MacroBar("Fats", fatConsumed.roundToInt(), macroTargets.fatGrams, FatYellow)
                        Spacer(modifier = Modifier.height(12.dp))
                        MacroBar("Carbs", carbsConsumed.roundToInt(), macroTargets.carbGrams, CarbGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onOpenLearn) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("How are my macros set?", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        MacroTargetsUnavailable()
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                PrimaryButton(
                    onClick = { showAddFood = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Add Food")
                }

                SectionDivider()
                SectionLabel("TODAY'S LOG")
            }
        }

        if (visibleLogs.isEmpty()) {
            item {
                Text(
                    "No food logged yet. Tap Add Food or scan a barcode to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        } else {
            // Foods and meals live in separate tables with overlapping ids, so the key needs the type too
            itemsIndexed(visibleLogs, key = { _, log -> log.listKey }) { index, log ->
                Column(modifier = Modifier.padding(horizontal = 24.dp).animateItem()) {
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    FoodLogItem(
                        log = log,
                        onDelete = { deleteWithUndo(log) },
                        onClick = { onEditLog(log) }
                    )
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)
    )
    }
}

/** Unique across foods and meals, whose ids come from different tables. */
private val FoodLogWithItem.listKey: String get() = (if (isMeal) "meal_" else "food_") + id

/** Daily macro targets: protein 2.0 g/kg, fat 0.8 g/kg, carbs fill the remaining calories. */
data class MacroTargets(val proteinGrams: Int, val fatGrams: Int, val carbGrams: Int) {
    companion object {
        /** Null when no weight has been logged: targets are per kg, so there is nothing to base them on. */
        fun from(calorieTarget: Int, weightKg: Double?): MacroTargets? {
            if (weightKg == null || weightKg <= 0.0) return null
            val protein = (weightKg * 2.0).roundToInt()
            val fat = (weightKg * 0.8).roundToInt()
            val carbs = ((calorieTarget - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
            return MacroTargets(protein, fat, carbs)
        }
    }
}

@Composable
fun MacroTargetsUnavailable() {
    Text(
        "Log your weight to get protein, fat and carb targets.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
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
            progress = { if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f },
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
            .bounceClick { if (!log.isMeal) onClick() } // Meals might not be editable yet
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (log.isMeal) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) 
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (log.isMeal) {
                        Icon(Icons.Default.RestaurantMenu, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                    } else {
                        Text(log.name.take(1).uppercase(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(log.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (log.isMeal) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "MEAL", 
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                val logQty = log.quantity * log.servingAmount
                val displayQty = if (logQty % 1.0 == 0.0) logQty.toInt().toString() else "%.1f".format(logQty)
                Text(
                    text = if (log.isMeal) "${log.calories.roundToInt()} kcal"
                           else "${(log.calories * log.quantity).roundToInt()} kcal • $displayQty ${log.servingUnit}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!log.isMeal) {
                IconButton(onClick = onClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

