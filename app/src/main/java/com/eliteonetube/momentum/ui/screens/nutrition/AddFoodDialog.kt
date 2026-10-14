package com.eliteonetube.momentum.ui.screens.nutrition

import com.eliteonetube.momentum.ui.components.filledFieldColors
import com.eliteonetube.momentum.ui.components.FieldShape
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.eliteonetube.momentum.logic.api.ExternalFoodApi
import kotlinx.coroutines.launch
import com.eliteonetube.momentum.ui.components.PrimaryButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eliteonetube.momentum.data.*
import com.eliteonetube.momentum.ui.components.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodDialog(
    title: String = "Add Food",
    foodItems: List<FoodItem>,
    recentFoodIds: List<Long> = emptyList(),
    allMeals: List<Meal>,
    onDismiss: () -> Unit,
    onFoodSelected: (Long, Double) -> Unit,
    onMealSelected: (Long) -> Unit,
    onMealCreated: (String, List<Pair<FoodItem, Double>>) -> Unit,
    onMealUpdated: (Long, String, List<Pair<FoodItem, Double>>) -> Unit = { _, _, _ -> },
    onMealDeleted: (Long) -> Unit = {},
    getMealItems: suspend (Long) -> List<Pair<FoodItem, Double>> = { emptyList() },
    onFoodCreated: (FoodItem) -> Unit,
    onQuickLog: (FoodItem) -> Unit,
    onStartScan: () -> Unit,
    onlineSearchEnabled: Boolean = false,
    onEnableOnlineSearch: () -> Unit = {},
    /** Saves an online result as one of your foods (or returns the one you already have) so it can be logged. */
    onSaveOnlineFood: suspend (FoodItem) -> FoodItem = { it }
) {
    var searchQuery by remember { mutableStateOf("") }
    var showQuickMacro by remember { mutableStateOf(false) }
    var showCreateMeal by remember { mutableStateOf(false) }
    var showCreateFood by remember { mutableStateOf(false) }
    var selectedFoodForEdit by remember { mutableStateOf<FoodItem?>(null) }
    var selectedFoodForQuantity by remember { mutableStateOf<FoodItem?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Quick Log entries are one-offs: kept for the diary, never offered as foods to pick
    val pickableFoods = remember(foodItems) { foodItems.filterNot { it.isQuickLog } }
    // With no search: what you logged recently first, then everything A–Z
    val recentFoods = remember(recentFoodIds, pickableFoods) {
        val byId = pickableFoods.associateBy { it.id }
        recentFoodIds.mapNotNull { byId[it] }
    }
    val filteredItems = remember(searchQuery, pickableFoods) {
        if (searchQuery.isBlank()) pickableFoods
        else pickableFoods.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    var mealToEdit by remember { mutableStateOf<Pair<Meal, List<Pair<FoodItem, Double>>>?>(null) }
    var mealPendingDelete by remember { mutableStateOf<Meal?>(null) }
    val scope = rememberCoroutineScope()

    // Open Food Facts results for the current search; null = nothing searched (or still typing)
    var onlineResults by remember { mutableStateOf<List<FoodItem>?>(null) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineFailed by remember { mutableStateOf(false) }
    val onlineQuery = searchQuery.trim()
    val savedBarcodes = remember(foodItems) { foodItems.mapNotNull { it.barcode }.toSet() }
    LaunchedEffect(onlineQuery, onlineSearchEnabled) {
        onlineResults = null
        onlineFailed = false
        if (!onlineSearchEnabled || onlineQuery.length < MIN_ONLINE_QUERY) { onlineLoading = false; return@LaunchedEffect }
        delay(400) // wait for typing to pause; a new keystroke cancels this effect
        onlineLoading = true
        val results = ExternalFoodApi.search(onlineQuery)
        onlineLoading = false
        if (results == null) onlineFailed = true else onlineResults = results
    }
    
    val filteredMeals = remember(searchQuery, allMeals) {
        if (searchQuery.isBlank()) {
            allMeals
        } else {
            allMeals.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    if (showQuickMacro) {
        QuickMacroDialog(
            onDismiss = { showQuickMacro = false },
            onConfirm = {
                onQuickLog(it)
                showQuickMacro = false
                onDismiss()
            }
        )
    }

    if (showCreateMeal) {
        CreateMealDialog(
            allFoodItems = pickableFoods,
            onDismiss = { showCreateMeal = false },
            onStartScan = onStartScan,
            onSave = { name, items ->
                onMealCreated(name, items)
                showCreateMeal = false
            }
        )
    }

    mealToEdit?.let { (meal, items) ->
        CreateMealDialog(
            allFoodItems = pickableFoods,
            onDismiss = { mealToEdit = null },
            onStartScan = onStartScan,
            onSave = { name, newItems ->
                onMealUpdated(meal.id, name, newItems)
                mealToEdit = null
            },
            title = "Edit Meal",
            initialName = meal.name,
            initialItems = items
        )
    }

    mealPendingDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { mealPendingDelete = null },
            title = { Text("Delete meal?", fontWeight = FontWeight.Bold) },
            text = { Text("\"${meal.name}\" will be removed from your saved meals. Days you already logged it stay as they are.") },
            confirmButton = {
                Button(
                    onClick = { onMealDeleted(meal.id); mealPendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { mealPendingDelete = null }) { Text("Cancel") } }
        )
    }

    if (showCreateFood || selectedFoodForEdit != null) {
        CreateFoodDialog(
            initialFood = selectedFoodForEdit,
            onDismiss = { 
                showCreateFood = false
                selectedFoodForEdit = null
            },
            onSave = {
                onFoodCreated(it)
                showCreateFood = false
                selectedFoodForEdit = null
            }
        )
    }

    if (selectedFoodForQuantity != null) {
        LogQuantityDialog(
            foodItem = selectedFoodForQuantity!!,
            onDismiss = { selectedFoodForQuantity = null },
            onConfirm = { qty ->
                onFoodSelected(selectedFoodForQuantity!!.id, qty)
                selectedFoodForQuantity = null
                onDismiss()
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
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.width(48.dp)) // To center title
                }

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    // Actions Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FoodActionCard(
                            icon = Icons.Default.CameraAlt,
                            label = "Scan",
                            onClick = { 
                                onStartScan()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FoodActionCard(
                            icon = Icons.Default.Add,
                            label = "Quick Log",
                            onClick = { showQuickMacro = true },
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search your foods...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = { 
                            if(searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                            }
                        },
                        shape = FieldShape,
                        colors = filledFieldColors()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    SecondaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(selectedTab, matchContentSize = false),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Items", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Meals", fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            PrimaryButton(
                                onClick = { showCreateFood = true },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Custom Food")
                            }
                        }

                        if (searchQuery.isBlank() && recentFoods.isNotEmpty()) {
                            item { FoodListHeader("RECENT") }
                            items(recentFoods, key = { "recent_${it.id}" }) { item ->
                                FoodRow(item, onClick = { selectedFoodForQuantity = item }, onEdit = { selectedFoodForEdit = item })
                            }
                            item { FoodListHeader("ALL FOODS") }
                        }
                        items(filteredItems, key = { "food_${it.id}" }) { item ->
                            FoodRow(item, onClick = { selectedFoodForQuantity = item }, onEdit = { selectedFoodForEdit = item })
                        }

                        if (onlineQuery.length >= MIN_ONLINE_QUERY) {
                            item { FoodListHeader("FROM OPEN FOOD FACTS") }
                            when {
                                !onlineSearchEnabled -> item {
                                    OnlineSearchMessage("Online lookup is off. Turn it on to search millions of products by name.") {
                                        TextButton(onClick = onEnableOnlineSearch) { Text("Turn on") }
                                    }
                                }
                                onlineLoading -> item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    }
                                }
                                onlineFailed -> item { OnlineSearchMessage("Couldn't reach Open Food Facts. Check your connection and try again.") }
                                onlineResults?.isEmpty() == true -> item { OnlineSearchMessage("No matches online for \"$onlineQuery\".") }
                                else -> items(
                                    // Products already saved as your foods are listed above; don't show them twice
                                    onlineResults.orEmpty().filter { it.barcode == null || it.barcode !in savedBarcodes },
                                    key = { "online_${it.barcode ?: it.name}" }
                                ) { result ->
                                    OnlineFoodRow(result) {
                                        scope.launch { selectedFoodForQuantity = onSaveOnlineFood(result) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            PrimaryButton(
                                onClick = { showCreateMeal = true },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create New Meal")
                            }
                        }

                        if (filteredMeals.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "No meals saved yet.\nSave a group of items to see them here!",
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(filteredMeals) { meal ->
                                ListItem(
                                    headlineContent = { Text(meal.name, fontWeight = FontWeight.Bold) },
                                    supportingContent = { meal.notes?.let { Text(it) } },
                                    leadingContent = {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.RestaurantMenu, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        MealMenu(
                                            onEdit = {
                                                scope.launch { mealToEdit = meal to getMealItems(meal.id) }
                                            },
                                            onDelete = { mealPendingDelete = meal }
                                        )
                                    },
                                    modifier = Modifier
                                        .clickable { onMealSelected(meal.id) }
                                        .padding(horizontal = 8.dp)
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .bounceClick(onClick),
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = contentColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

@Composable
private fun FoodListHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun FoodRow(item: FoodItem, onClick: () -> Unit, onEdit: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
        supportingContent = {
            val amount = if (item.servingAmount % 1.0 == 0.0) item.servingAmount.toInt().toString() else item.servingAmount.toString()
            Text("${item.calories.toInt()} kcal per $amount ${item.servingUnit}")
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isCustom) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit ${item.name}", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
            }
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    )
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun MealMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "Meal options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit meal") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { expanded = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("Delete meal", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

private const val MIN_ONLINE_QUERY = 3

@Composable
private fun OnlineSearchMessage(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

@Composable
private fun OnlineFoodRow(item: FoodItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name, fontWeight = FontWeight.Bold, maxLines = 2) },
        supportingContent = { Text("${item.calories.roundToInt()} kcal per 100 g") },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    )
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}
