package com.eliteonetube.momentum.ui.screens.checkin

import com.eliteonetube.momentum.ui.components.PrimaryButton
import com.eliteonetube.momentum.ui.components.SecondaryButton
import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.eliteonetube.momentum.data.UnitSystem
import com.eliteonetube.momentum.data.UserProfile
import com.eliteonetube.momentum.data.WeightEntry
import com.eliteonetube.momentum.logic.utils.MediaUtils
import com.eliteonetube.momentum.logic.utils.Units
import com.eliteonetube.momentum.ui.screens.dashboard.MascotMood
import com.eliteonetube.momentum.ui.screens.dashboard.MomentumMascot
import com.eliteonetube.momentum.ui.components.WeightTrendChart
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
import com.eliteonetube.momentum.logic.algorithm.WeeklyReviewResult
import com.eliteonetube.momentum.ui.components.BigStat
import com.eliteonetube.momentum.ui.components.SectionLabel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CheckInScreen(
    profile: UserProfile,
    recentWeights: List<WeightEntry>,
    /** Runs the weekly review including the weight (and body fat) entered here. */
    runReview: (weightKg: Double, bodyFatPercentage: Double?) -> WeeklyReviewResult,
    onComplete: (weightKg: Double, bodyFatPercentage: Double?, photos: List<Uri?>, review: WeeklyReviewResult) -> Unit,
    onCancel: () -> Unit
) {
    val today = remember { LocalDate.now().toString() }
    val todayEntry = remember(recentWeights) { recentWeights.firstOrNull { it.date == today } }
    
    var step by remember { mutableIntStateOf(0) } // 0: Intro, 1: Weight, 2: Photos, 3: Summary
    var weightInput by remember { 
        mutableStateOf(
            todayEntry?.let { 
                if (profile.unitSystem == UnitSystem.IMPERIAL) "%.1f".format(Units.kgToLb(it.weight)) 
                else it.weight.toString()
            } ?: ""
        ) 
    }
    // Optional: left blank unless the user measured it this week
    var bodyFatInput by remember { mutableStateOf("") }
    var frontPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var backPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var sidePhotoUri by remember { mutableStateOf<Uri?>(null) }

    val unitLabel = if (profile.unitSystem == UnitSystem.IMPERIAL) "lb" else "kg"
    val parsedWeight = weightInput.replace(',', '.').toDoubleOrNull()
    val weightKg = parsedWeight?.let { 
        if (profile.unitSystem == UnitSystem.IMPERIAL) Units.lbToKg(it) else it 
    }
    val bodyFat = bodyFatInput.replace(',', '.').toDoubleOrNull()?.takeIf { it in 2.0..70.0 }
    val bodyFatValid = bodyFatInput.isBlank() || bodyFat != null

    // System back steps through the check-in like the arrow does
    BackHandler { if (step > 0) step-- else onCancel() }
    val isDark = isSystemInDarkTheme()

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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.15f else 0.12f),
                            MaterialTheme.colorScheme.background
                        ),
                        endY = 900f
                    )
                )
                .padding(padding)
        ) {
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
                            bodyFatInput = bodyFatInput,
                            onBodyFatChange = { bodyFatInput = it },
                            bodyFatError = !bodyFatValid,
                            lastBodyFat = profile.bodyFatPercentage,
                            onNext = { step = 2 },
                            isValid = weightKg != null && bodyFatValid
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
                        3 -> {
                            val newWeight = weightKg ?: 0.0
                            val review = remember(newWeight, bodyFat) { runReview(newWeight, bodyFat) }
                            SummaryStep(
                                profile = profile,
                                recentWeights = recentWeights,
                                newWeight = newWeight,
                                newBodyFat = bodyFat,
                                review = review,
                                onFinish = {
                                    onComplete(newWeight, bodyFat, listOf(frontPhotoUri, backPhotoUri, sidePhotoUri), review)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroStep(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Time to level up.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Log today's weight and I'll compare this week with last week to keep your plan dialled in.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        PrimaryButton(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Let's Start")
        }
    }
}

@Composable
private fun WeightStep(
    weightInput: String,
    onWeightChange: (String) -> Unit,
    unitLabel: String,
    bodyFatInput: String,
    onBodyFatChange: (String) -> Unit,
    bodyFatError: Boolean,
    lastBodyFat: Double?,
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

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = bodyFatInput,
            onValueChange = onBodyFatChange,
            label = { Text("Body fat % (optional)") },
            placeholder = { Text(lastBodyFat?.let { "Last: $it" } ?: "e.g. 18.5") },
            supportingText = {
                Text(
                    if (bodyFatError) "Enter a value between 2 and 70"
                    else "Only if you measured it — improves your maintenance estimate"
                )
            },
            isError = bodyFatError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        )

        Spacer(modifier = Modifier.height(32.dp))
        PrimaryButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = isValid
        ) {
            Text("Confirm Weight")
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
        PrimaryButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (frontUri != null || backUri != null || sideUri != null) "Next" else "Skip Photos")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoPicker(label: String, uri: Uri?, onUriChange: (Uri?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tempUri by remember { mutableStateOf<Uri?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) onUriChange(it)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempUri != null) {
            onUriChange(tempUri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            MediaUtils.createImageUri(context)?.let { uri ->
                tempUri = uri
                cameraLauncher.launch(uri)
                showSheet = false
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
            ) {
                Text("Select Photo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(24.dp))
                
                PrimaryButton(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            MediaUtils.createImageUri(context)?.let { uri ->
                                tempUri = uri
                                cameraLauncher.launch(uri)
                                showSheet = false
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Take Photo")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SecondaryButton(
                    onClick = {
                        galleryLauncher.launch("image/*")
                        showSheet = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Choose from Gallery")
                }
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .clickable { showSheet = true },
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
    newBodyFat: Double?,
    review: WeeklyReviewResult,
    onFinish: () -> Unit
) {
    val today = LocalDate.now().toString()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your Evolution", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Here's your plan for the upcoming week.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        WeightTrendChart(
            // Today's check-in weight replaces anything logged earlier today
            entries = listOf(WeightEntry(today, newWeight)) + recentWeights.filter { it.date != today },
            unitSystem = profile.unitSystem,
            modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 8.dp)
        )

        if (newBodyFat != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val previous = profile.bodyFatPercentage
            Text(
                buildString {
                    append("Body fat: $newBodyFat%")
                    if (previous != null && previous != newBodyFat) {
                        append("  (%+.1f from %s%%)".format(newBodyFat - previous, previous))
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(28.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("THE VERDICT")
            Text(review.reason, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))

            if (review.newTarget != profile.currentCalorieTarget) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BigStat("%,d".format(profile.currentCalorieTarget), "kcal")
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(24.dp).rotate(180f), tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("NEW TARGET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        BigStat("%,d".format(review.newTarget), "kcal", valueColor = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Text("Daily target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigStat("%,d".format(review.newTarget), "kcal")
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        PrimaryButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lock it in")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
