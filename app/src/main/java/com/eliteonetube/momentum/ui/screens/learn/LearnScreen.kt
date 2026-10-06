package com.eliteonetube.momentum.ui.screens.learn

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.ui.components.SectionLabel

/**
 * The in-app knowledge base. [initialCategory] scrolls to that section when opened from
 * elsewhere (e.g. the Eat tab's macro link).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    initialCategory: LearnCategory? = null,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val isDark = isSystemInDarkTheme()
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    // Flatten into list rows so a category header can be scrolled to directly
    val rows = remember {
        LearnCategory.entries.flatMap { category ->
            listOf<Any>(category) + LearnContent.topics.filter { it.category == category }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(initialCategory) {
        val index = initialCategory?.let { category -> rows.indexOf<Any>(category) } ?: -1
        if (index >= 0) listState.scrollToItem(index + 1) // +1 for the hero item
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.12f))
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.12f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(top = 8.dp, bottom = 32.dp)
                ) {
                    Text(
                        "Learn",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "HOW MOMENTUM WORKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(rows, key = { row -> if (row is LearnTopic) row.id else (row as LearnCategory).name }) { row ->
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    when (row) {
                        is LearnCategory -> {
                            if (row != LearnCategory.entries.first()) Spacer(modifier = Modifier.height(32.dp))
                            SectionLabel(row.label)
                        }
                        is LearnTopic -> TopicRow(
                            topic = row,
                            expanded = expandedId == row.id,
                            onToggle = { expandedId = if (expandedId == row.id) null else row.id }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: LearnTopic, expanded: Boolean, onToggle: () -> Unit) {
    val accent = topic.accent ?: MaterialTheme.colorScheme.primary
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "topicArrow")

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(topic.icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(topic.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(topic.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(arrowRotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                topic.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 56.dp, bottom = 16.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}
