package com.eliteonetube.momentum.ui.theme.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliteonetube.momentum.data.FoodItem
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogQuantityDialog(
    foodItem: FoodItem,
    initialQuantity: Double? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val initialText = remember(initialQuantity) {
        if (initialQuantity != null) {
            val value = initialQuantity * foodItem.servingAmount
            if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
        } else foodItem.servingAmount.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
    }
    
    var quantityInput by remember { 
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length))) 
    }
    
    val qty = quantityInput.text.replace(",", ".").toDoubleOrNull() ?: 0.0
    val multiplier = if (foodItem.servingAmount > 0) qty / foodItem.servingAmount else 1.0
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = foodItem.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "How much did you have?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Hero Input Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                FilledTonalIconButton(
                    onClick = { 
                        val current = quantityInput.text.toDoubleOrNull() ?: 0.0
                        val step = if (foodItem.servingUnit.lowercase() in listOf("g", "ml")) 10.0 else 1.0
                        val newVal = (current - step).coerceAtLeast(0.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
                        quantityInput = TextFieldValue(newVal, selection = TextRange(newVal.length))
                    },
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(28.dp))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        modifier = Modifier.width(160.dp).focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (qty > 0) onConfirm(multiplier)
                            }
                        ),
                        textStyle = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold, 
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        singleLine = true
                    )
                    Text(
                        text = "${foodItem.servingUnit} eaten",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = { 
                        val current = quantityInput.text.toDoubleOrNull() ?: 0.0
                        val step = if (foodItem.servingUnit.lowercase() in listOf("g", "ml")) 10.0 else 1.0
                        val newVal = (current + step).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
                        quantityInput = TextFieldValue(newVal, selection = TextRange(newVal.length))
                    },
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Highlighted Result Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${(foodItem.calories * multiplier).roundToInt()}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "total calories",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MacroInfoItem("Prot", "${(foodItem.protein * multiplier).format(1)}g", MaterialTheme.colorScheme.primary)
                        MacroInfoItem("Fat", "${(foodItem.fat * multiplier).format(1)}g", MaterialTheme.colorScheme.tertiary)
                        MacroInfoItem("Carb", "${(foodItem.carbs * multiplier).format(1)}g", MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onConfirm(multiplier) },
                enabled = qty > 0,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text("Log to Nutrition Diary", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun MacroInfoItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Black
        )
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)
