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
import androidx.compose.ui.draw.clip
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
import com.eliteonetube.momentum.ui.theme.bounceClick
import com.eliteonetube.momentum.ui.theme.nutrition.QuickMacroDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodDialog(
    foodItems: List<FoodItem>,
    allMeals: List<Meal>,
    onDismiss: () -> Unit,
    onFoodSelected: (Long, Double) -> Unit,
    onMealSelected: (Long) -> Unit,
    onMealCreated: (String, List<Pair<FoodItem, Double>>) -> Unit,
    onFoodCreated: (FoodItem) -> Unit,
    onQuickLog: (FoodItem) -> Unit,
    onStartScan: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showQuickMacro by remember { mutableStateOf(false) }
    var showCreateMeal by remember { mutableStateOf(false) }
    var showCreateFood by remember { mutableStateOf(false) }
    var selectedFoodForEdit by remember { mutableStateOf<FoodItem?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val filteredItems = remember(searchQuery, foodItems) {
        if (searchQuery.isBlank()) {
            foodItems.take(20)
        } else {
            foodItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
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
            allFoodItems = foodItems,
            onDismiss = { showCreateMeal = false },
            onStartScan = onStartScan,
            onSave = { name, items ->
                onMealCreated(name, items)
                showCreateMeal = false
            }
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
                    Text("Add Food", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = { 
                            if(searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null) }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            if (tabPositions.isNotEmpty()) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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
                            Button(
                                onClick = { showCreateFood = true },
                                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp).bounceClick(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Custom Food", fontWeight = FontWeight.Bold)
                            }
                        }

                        items(filteredItems) { item ->
                            ListItem(
                                headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("${item.calories.toInt()} kcal per ${item.servingSize}") },
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
                                            IconButton(onClick = { selectedFoodForEdit = item }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                modifier = Modifier
                                    .clickable { onFoodSelected(item.id, 1.0) }
                                    .padding(horizontal = 8.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Button(
                                onClick = { showCreateMeal = true },
                                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp).bounceClick(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create New Meal", fontWeight = FontWeight.Bold)
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
                                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
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
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .bounceClick(onClick),
        color = containerColor,
        shape = RoundedCornerShape(20.dp)
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
