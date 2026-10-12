package com.eliteonetube.momentum.ui.screens.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import com.eliteonetube.momentum.ui.components.rememberCurrentDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.eliteonetube.momentum.ui.components.bottomNavClearance
import com.eliteonetube.momentum.ui.components.PrimaryButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.ui.theme.*
import com.eliteonetube.momentum.ui.components.SectionDivider
import com.eliteonetube.momentum.ui.components.SectionLabel
import com.eliteonetube.momentum.ui.components.bounceClick
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    calorieTarget: Int,
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    todayLogs: List<FoodLogWithItem> = emptyList(),
    diaryForDate: (String) -> Flow<List<FoodLogWithItem>> = { flowOf(emptyList()) },
    /** Increments when the widget's "+ Food" is tapped: open Add Food for today's current meal. */
    addFoodRequest: Int = 0,
    allFoodItems: List<FoodItem> = emptyList(),
    recentFoodIds: List<Long> = emptyList(),
    allMeals: List<Meal> = emptyList(),
    onLogFood: (foodId: Long, quantity: Double, target: LogTarget) -> Unit,
    onLogMeal: (mealId: Long, target: LogTarget) -> Unit,
    onDeleteLog: (Long, Boolean) -> Unit,
    onEditLog: (FoodLogWithItem) -> Unit,
    onMoveLog: (FoodLogWithItem, MealSlot) -> Unit = { _, _ -> },
    copyDiarySection: suspend (fromDate: String, toDate: String, slot: MealSlot) -> Int = { _, _, _ -> 0 },
    onQuickLog: (FoodItem, LogTarget) -> Unit,
    onMealCreated: (String, List<Pair<FoodItem, Double>>) -> Unit,
    onMealUpdated: (Long, String, List<Pair<FoodItem, Double>>) -> Unit = { _, _, _ -> },
    onMealDeleted: (Long) -> Unit = {},
    getMealItems: suspend (Long) -> List<Pair<FoodItem, Double>> = { emptyList() },
    onFoodCreated: (FoodItem) -> Unit,
    onStartScan: (LogTarget) -> Unit,
    onOpenLearn: () -> Unit = {},
    onEnableOnlineSearch: () -> Unit = {},
    onSaveOnlineFood: suspend (FoodItem) -> FoodItem = { it }
) {
    // Newest logged weight (recentWeights is the latest entries, not a date window)
    val currentWeightKg = recentWeights.firstOrNull()?.weight
    val isDark = isSystemInDarkTheme()

    // Which day the diary shows; never in the future
    val today by rememberCurrentDate()
    var selectedDateText by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = remember(selectedDateText, today) {
        runCatching { LocalDate.parse(selectedDateText) }.getOrDefault(today).coerceAtMost(today)
    }
    val isToday = selectedDate == today
    // Today's entries are already collected app-wide; other days are queried while shown
    val pastDayLogs by remember(selectedDate) { diaryForDate(selectedDate.toString()) }.collectAsState(initial = emptyList())
    val dayLogs = if (isToday) todayLogs else pastDayLogs

    // Deletes wait for the Undo snackbar to close; until then the entry is just hidden
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingDeleteKeys by remember { mutableStateOf(emptySet<String>()) }
    val visibleLogs = remember(dayLogs, pendingDeleteKeys) { dayLogs.filterNot { it.listKey in pendingDeleteKeys } }
    val logsBySlot = remember(visibleLogs) { visibleLogs.groupBy { it.mealSlot } }

    val caloriesConsumed = remember(visibleLogs) { visibleLogs.sumOf { it.calories * it.quantity } }
    val proteinConsumed = remember(visibleLogs) { visibleLogs.sumOf { it.protein * it.quantity } }
    val fatConsumed = remember(visibleLogs) { visibleLogs.sumOf { it.fat * it.quantity } }
    val carbsConsumed = remember(visibleLogs) { visibleLogs.sumOf { it.carbs * it.quantity } }
    val macroTargets = remember(calorieTarget, currentWeightKg) { MacroTargets.from(calorieTarget, currentWeightKg) }

    // Where the Add Food dialog logs to; non-null while it's open
    var addTarget by remember { mutableStateOf<LogTarget?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val collapsedSlots = remember { mutableStateListOf<MealSlot>() }

    LaunchedEffect(addFoodRequest) {
        if (addFoodRequest > 0) {
            selectedDateText = today.toString()
            addTarget = LogTarget.now()
        }
    }

    addTarget?.let { target ->
        AddFoodDialog(
            title = "Add to ${target.slot.label}",
            foodItems = allFoodItems,
            recentFoodIds = recentFoodIds,
            allMeals = allMeals,
            onDismiss = { addTarget = null },
            onFoodSelected = { foodId, qty ->
                onLogFood(foodId, qty, target)
                addTarget = null
            },
            onMealSelected = { mealId ->
                onLogMeal(mealId, target)
                addTarget = null
            },
            onMealCreated = onMealCreated,
            onMealUpdated = onMealUpdated,
            onMealDeleted = onMealDeleted,
            getMealItems = getMealItems,
            onFoodCreated = onFoodCreated,
            onQuickLog = { item -> onQuickLog(item, target) },
            onStartScan = { onStartScan(target) },
            onlineSearchEnabled = profile.useExternalApi,
            onEnableOnlineSearch = onEnableOnlineSearch,
            onSaveOnlineFood = onSaveOnlineFood
        )
    }

    if (showDatePicker) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        selectedDateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
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

    fun copyFromPreviousDay(slot: MealSlot) {
        val from = selectedDate.minusDays(1)
        coroutineScope.launch {
            val copied = copyDiarySection(from.toString(), selectedDate.toString(), slot)
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                if (copied > 0) "Copied $copied ${if (copied == 1) "entry" else "entries"} to ${slot.label.lowercase()}"
                else "Nothing logged for ${slot.label.lowercase()} on ${dayLabel(from, today).lowercase()}"
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomNavClearance())
        ) {
            // Date bar + compact summary
            item {
                Column(
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
                        .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 8.dp)
                ) {
                    // Grouped in the middle so the arrows stay clear of the mascot in the top-right corner
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { selectedDateText = selectedDate.minusDays(1).toString() }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                        }
                        Text(
                            dayLabel(selectedDate, today),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(min = 120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClickLabel = "Pick a date") { showDatePicker = true }
                                .padding(vertical = 4.dp)
                        )
                        IconButton(
                            onClick = { selectedDateText = selectedDate.plusDays(1).toString() },
                            enabled = !isToday
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                        }
                    }
                    if (!isToday) {
                        TextButton(
                            onClick = { selectedDateText = today.toString() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("Back to today") }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    SummaryRow("Calories", caloriesConsumed, calorieTarget, "kcal", MaterialTheme.colorScheme.primary)
                    if (macroTargets != null) {
                        SummaryRow("Protein", proteinConsumed, macroTargets.proteinGrams, "g", ProteinBlue)
                        SummaryRow("Carbs", carbsConsumed, macroTargets.carbGrams, "g", CarbGreen)
                        SummaryRow("Fat", fatConsumed, macroTargets.fatGrams, "g", FatYellow)
                        TextButton(onClick = onOpenLearn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("How are my macros set?", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        MacroTargetsUnavailable()
                    }
                }
            }

            // Uncategorized first (older entries), only when it has anything; then the four sections
            val slots = buildList {
                if (!logsBySlot[MealSlot.UNCATEGORIZED].isNullOrEmpty()) add(MealSlot.UNCATEGORIZED)
                addAll(MealSlot.sections)
            }
            slots.forEach { slot ->
                val entries = logsBySlot[slot].orEmpty()
                val expanded = slot !in collapsedSlots
                item(key = "header_${slot.name}") {
                    MealSectionHeader(
                        slot = slot,
                        calories = entries.sumOf { it.calories * it.quantity },
                        count = entries.size,
                        expanded = expanded,
                        onToggle = { if (expanded) collapsedSlots.add(slot) else collapsedSlots.remove(slot) },
                        onAdd = if (slot == MealSlot.UNCATEGORIZED) null else ({ addTarget = LogTarget(selectedDate.toString(), slot) }),
                        onCopyPreviousDay = if (slot == MealSlot.UNCATEGORIZED) null else ({ copyFromPreviousDay(slot) }),
                        previousDayLabel = dayLabel(selectedDate.minusDays(1), today)
                    )
                }
                if (expanded) {
                    itemsIndexed(entries, key = { _, log -> log.listKey }) { _, log ->
                        Column(modifier = Modifier.padding(horizontal = 24.dp).animateItem()) {
                            FoodLogItem(
                                log = log,
                                onDelete = { deleteWithUndo(log) },
                                onClick = { onEditLog(log) },
                                onMove = { target -> onMoveLog(log, target) }
                            )
                        }
                    }
                }
                item(key = "divider_${slot.name}") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)
        )
    }
}

/** "Today", "Yesterday", or e.g. "Mon 6 Oct". */
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

/** One compact line of the daily summary: "Protein   82 / 160 g   51%" over a thin bar. */
@Composable
private fun SummaryRow(label: String, current: Double, target: Int, unit: String, color: Color) {
    val progress = if (target > 0) (current / target).toFloat() else 0f
    val isOver = progress > 1f
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "%,d / %,d %s".format(current.roundToInt(), target, unit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Drawn by hand: a progress indicator's round cap shows a dot even at 0%
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(color.copy(alpha = 0.15f), CircleShape)) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (isOver) MaterialTheme.colorScheme.error else color, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun MealSectionHeader(
    slot: MealSlot,
    calories: Double,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)?,
    onCopyPreviousDay: (() -> Unit)?,
    previousDayLabel: String
) {
    var menuOpen by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 0f else -90f, label = "sectionChevron")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Collapse ${slot.label}" else "Expand ${slot.label}", onClick = onToggle)
            .padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).rotate(chevronRotation)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(slot.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (count == 0) "Nothing logged" else "%,d kcal".format(calories.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onCopyPreviousDay != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "${slot.label} options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy from ${previousDayLabel.lowercase()}") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { menuOpen = false; onCopyPreviousDay() }
                    )
                }
            }
        }
        if (onAdd != null) {
            FilledTonalIconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add to ${slot.label}")
            }
        }
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
fun FoodLogItem(log: FoodLogWithItem, onDelete: () -> Unit, onClick: () -> Unit, onMove: (MealSlot) -> Unit = {}) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { if (!log.isMeal) onClick() } // Meals are logged as a whole, so there's no amount to edit
            .padding(start = 28.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                if (log.isMeal) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "MEAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (!log.isMeal) {
                val logQty = log.quantity * log.servingAmount
                val displayQty = if (logQty % 1.0 == 0.0) logQty.toInt().toString() else "%.1f".format(logQty)
                Text(
                    "$displayQty ${log.servingUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "%,d kcal".format((log.calories * log.quantity).roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "${log.name} options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!log.isMeal) {
                    DropdownMenuItem(
                        text = { Text("Edit amount") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuOpen = false; onClick() }
                    )
                }
                MealSlot.sections.filter { it != log.mealSlot }.forEach { slot ->
                    DropdownMenuItem(
                        text = { Text("Move to ${slot.label}") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                        onClick = { menuOpen = false; onMove(slot) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

