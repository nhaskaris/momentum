package com.eliteonetube.momentum.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.Units
import com.eliteonetube.momentum.ui.theme.dashboard.MascotMood
import com.eliteonetube.momentum.ui.theme.dashboard.MomentumMascot
import com.eliteonetube.momentum.ui.theme.bounceClick
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CheckInScreen(
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    onComplete: (Double, List<Uri?>) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) } // 0: Intro, 1: Weight, 2: Photos, 3: Summary
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
                title = { Text("Weekly Review", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { if (step > 0) step-- else onCancel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mascot at the top
                MomentumMascot(
                    mood = if (step == 3) MascotMood.HAPPY else MascotMood.IDLE,
                    modifier = Modifier.size(80.dp).padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step Progress
                LinearProgressIndicator(
                    progress = { (step + 1) / 4f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }.using(SizeTransform(clip = false))
                    },
                    label = "stepTransition"
                ) { currentStep ->
                    when (currentStep) {
                        0 -> IntroStep(onStart = { step = 1 })
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
    }
}

@Composable
private fun IntroStep(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Time to level up.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Consistency is how we win. Let's record your data from the past week so I can keep your metabolic plan perfectly dialled in.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp).bounceClick(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Let's Start", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun WeightStep(
    weightInput: String,
    onWeightChange: (String) -> Unit,
    unitLabel: String,
    onNext: () -> Unit,
    isValid: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Weight Check", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Precision matters for the algorithm. Enter your current scale weight.", 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = weightInput,
            onValueChange = onWeightChange,
            label = { Text("Weight ($unitLabel)") },
            placeholder = { Text("0.0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Black),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        )

        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp).bounceClick(),
            enabled = isValid,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Confirm Weight", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PhotoStep(
    frontUri: Uri?,
    backUri: Uri?,
    sideUri: Uri?,
    onFrontChange: (Uri?) -> Unit,
    onBackChange: (Uri?) -> Unit,
    onSideChange: (Uri?) -> Unit,
    onNext: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Visual Progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Optional, but powerful. Photos tell the story the scale can't.", 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PhotoPicker("Front", frontUri, onFrontChange, Modifier.weight(1f))
            PhotoPicker("Side", sideUri, onSideChange, Modifier.weight(1f))
            PhotoPicker("Back", backUri, onBackChange, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext, 
            modifier = Modifier.fillMaxWidth().height(56.dp).bounceClick(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (frontUri != null || backUri != null || sideUri != null) "Next" else "Skip Photos", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PhotoPicker(label: String, uri: Uri?, onUriChange: (Uri?) -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        onUriChange(it)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryStep(
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    newWeight: Double,
    onFinish: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Evolution", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Analysis complete. Here's your plan for the upcoming week.", 
            style = MaterialTheme.typography.bodyMedium, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        WeightTrendChart(
            entries = listOf(WeightEntry(LocalDate.now().toString(), newWeight)) + recentWeights,
            modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("The Verdict", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    profile.pendingAdjustmentReason ?: "Everything looks great! We're staying the course to maintain your current momentum.", 
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                if ((profile.pendingCalorieTarget != null) && (profile.pendingCalorieTarget != profile.currentCalorieTarget)) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("WAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                            Text("${profile.currentCalorieTarget}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(24.dp).rotate(180f), tint = MaterialTheme.colorScheme.primary)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("NEW TARGET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("${profile.pendingCalorieTarget} kcal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onFinish, 
            modifier = Modifier.fillMaxWidth().height(64.dp).bounceClick(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("Lock it in", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
