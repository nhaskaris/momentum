package com.eliteonetube.momentum.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.Units
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    onComplete: (Double, List<Uri?>) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var weightInput by remember { mutableStateOf("") }
    var frontPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var backPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var sidePhotoUri by remember { mutableStateOf<Uri?>(null) }

    val unitLabel = if (profile.unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
    val parsedWeight = weightInput.replace(',', '.').toDoubleOrNull()
    val weightKg = parsedWeight?.let { 
        if (profile.unitSystem == UnitSystem.IMPERIAL) Units.lbToKg(it) else it 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Check-in") },
                navigationIcon = {
                    IconButton(onClick = { if (step > 1) step-- else onCancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { step / 3f },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(32.dp))

            when (step) {
                1 -> WeightStep(
                    weightInput = weightInput,
                    onWeightChange = { weightInput = it },
                    unitLabel = unitLabel,
                    onNext = { step = 2 },
                    isValid = weightKg != null
                )
                2 -> PhotoStep(
                    frontUri = frontPhotoUri,
                    backUri = backPhotoUri,
                    sideUri = sidePhotoUri,
                    onFrontChange = { frontPhotoUri = it },
                    onBackChange = { backPhotoUri = it },
                    onSideChange = { sidePhotoUri = it },
                    onNext = { step = 3 }
                )
                3 -> SummaryStep(
                    profile = profile,
                    recentWeights = recentWeights,
                    newWeight = weightKg ?: 0.0,
                    onFinish = {
                        onComplete(weightKg ?: 0.0, listOf(frontPhotoUri, backPhotoUri, sidePhotoUri))
                    }
                )
            }
        }
    }
}

@Composable
fun WeightStep(
    weightInput: String,
    onWeightChange: (String) -> Unit,
    unitLabel: String,
    onNext: () -> Unit,
    isValid: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Today's Weight", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Enter your current weight to see how you've progressed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = weightInput,
            onValueChange = onWeightChange,
            label = { Text(unitLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = isValid
        ) {
            Text("Continue")
        }
    }
}

@Composable
fun PhotoStep(
    frontUri: Uri?,
    backUri: Uri?,
    sideUri: Uri?,
    onFrontChange: (Uri?) -> Unit,
    onBackChange: (Uri?) -> Unit,
    onSideChange: (Uri?) -> Unit,
    onNext: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Progress Photos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Optional: Front, back, and side photos help track your visual change.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoPicker("Front", frontUri, onFrontChange, Modifier.weight(1f))
            PhotoPicker("Back", backUri, onBackChange, Modifier.weight(1f))
            PhotoPicker("Side", sideUri, onSideChange, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
        TextButton(onClick = onNext) {
            Text("Skip photos")
        }
    }
}

@Composable
fun PhotoPicker(label: String, uri: Uri?, onUriChange: (Uri?) -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        onUriChange(it)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { launcher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SummaryStep(
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    newWeight: Double,
    onFinish: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("What's New", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Based on your data from the last week, here is your recommendation.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Logged weight: ${Units.displayWeight(newWeight, profile.unitSystem)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        WeightTrendChart(
            entries = listOf(WeightEntry(LocalDate.now().toString(), newWeight)) + recentWeights,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Recommendation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(profile.pendingAdjustmentReason ?: "Keep going as you are!", style = MaterialTheme.typography.bodyLarge)
                
                if ((profile.pendingCalorieTarget != null) && (profile.pendingCalorieTarget != profile.currentCalorieTarget)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Current", style = MaterialTheme.typography.labelSmall)
                            Text("${profile.currentCalorieTarget} kcal", style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(horizontal = 16.dp))
                        Column {
                            Text("New Target", style = MaterialTheme.typography.labelSmall)
                            Text("${profile.pendingCalorieTarget} kcal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Finish Check-in")
        }
    }
}
