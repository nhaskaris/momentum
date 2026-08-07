package com.eliteonetube.momentum.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.Units
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightHistoryBottomSheet(
    entries: List<WeightEntry>,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onPastWeightSubmitted: (date: String, weight: Double) -> Unit,
    onWeightDeleted: (date: String) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var entryPendingDelete by remember { mutableStateOf<WeightEntry?>(null) }

    entryPendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("Remove the weight logged on ${entry.date}? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onWeightDeleted(entry.date)
                    entryPendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Weight History & Trends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAddForm = !showAddForm }) {
                    Text(if (showAddForm) "Cancel" else "+ Add past entry")
                }
            }

            if (showAddForm) {
                Spacer(modifier = Modifier.height(12.dp))
                AddPastEntryForm(
                    unitSystem = unitSystem,
                    onSubmit = { date, weightKg ->
                        onPastWeightSubmitted(date, weightKg)
                        showAddForm = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Visual Weight Trend Chart", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            WeightTrendChart(entries = entries)

            Spacer(modifier = Modifier.height(16.dp))
            Text("Logged Entries List", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(entries) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(entry.date, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Units.displayWeight(entry.weight, unitSystem),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = { entryPendingDelete = entry },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete entry",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPastEntryForm(
    unitSystem: UnitSystem,
    onSubmit: (date: String, weightKg: Double) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var weightInput by remember { mutableStateOf("") }

    val parsedInput = weightInput.replace(',', '.').toDoubleOrNull()
    val parsedWeightKg = parsedInput?.let {
        if (unitSystem == UnitSystem.IMPERIAL) Units.lbToKg(it) else it
    }
    val weightError = weightInput.isNotBlank() && parsedWeightKg == null
    val selectedDateLabel = selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().format(isoFormatter)
    } ?: "Pick a date"
    val unitLabel = if (unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"

    // End of today, UTC — matches how DatePickerDialog reports selectedDateMillis (UTC midnight)
    val todayEndMillis = remember {
        LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedDateLabel)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = weightInput,
            onValueChange = { weightInput = it },
            label = { Text("Weight ($unitLabel)") },
            isError = weightError,
            supportingText = { if (weightError) Text("Enter a valid number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val date = selectedDateLabel.takeIf { selectedDateMillis != null } ?: return@Button
                parsedWeightKg?.let { onSubmit(date, it) }
            },
            enabled = parsedWeightKg != null && selectedDateMillis != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Entry")
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= todayEndMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}