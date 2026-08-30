package com.eliteonetube.momentum.ui.theme.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eliteonetube.momentum.logic.NutritionScanner
import com.eliteonetube.momentum.logic.ScannedNutrition
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

enum class ScannerMode { BARCODE, FRONT_PACKAGE, NUTRITION }

private const val NUTRITION_OVERLAY_ASPECT = 0.8f

@OptIn(ExperimentalMaterial3Api::class)
@AndroidxOptIn(markerClass = [ExperimentalGetImage::class])
@Composable
fun FoodScannerScreen(
    apiEnabled: Boolean = false,
    onResult: (ScannedNutrition, String?) -> Unit,
    onBarcodeScanned: suspend (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val coroutineScope = rememberCoroutineScope()
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    DisposableEffect(Unit) {
        onDispose { 
            cameraExecutor.shutdown()
            barcodeScanner.close()
            textRecognizer.close()
        }
    }

    var scannerMode by remember { mutableStateOf(ScannerMode.BARCODE) }
    var currentBarcode by remember { mutableStateOf<String?>(null) }
    var detectedName by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Align barcode in frame") }
    var isCapturing by remember { mutableStateOf(false) }
    var isSearchingBarcode by remember { mutableStateOf(false) }
    var capturedNutrition by remember { mutableStateOf<ScannedNutrition?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required to scan labels")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
                TextButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(scannerMode) {
        if (scannerMode != ScannerMode.BARCODE) {
            val cam = camera
            val previewView = previewViewRef
            if (cam != null && previewView != null && previewView.width > 0) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
                val action = FocusMeteringAction.Builder(point).build()
                cam.cameraControl.startFocusAndMetering(action)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val title = when(scannerMode) {
                        ScannerMode.BARCODE -> "Scan Barcode"
                        ScannerMode.FRONT_PACKAGE -> "Scan Package Name"
                        ScannerMode.NUTRITION -> "Scan Nutrition Label"
                    }
                    Text(title, fontWeight = FontWeight.Black) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, 
                    titleContentColor = Color.White, 
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize().pointerInput(scannerMode) {
                    detectTapGestures { offset ->
                        val previewView = previewViewRef ?: return@detectTapGestures
                        val cam = camera ?: return@detectTapGestures
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        cam.cameraControl.startFocusAndMetering(action)
                    }
                },
                update = { _ -> }
            )

            LaunchedEffect(hasCameraPermission, previewViewRef) {
                if (hasCameraPermission && previewViewRef != null) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = try {
                            cameraProviderFuture.get()
                        } catch (_: Exception) {
                            return@addListener
                        }

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewViewRef!!.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && scannerMode == ScannerMode.BARCODE) {
                                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                        barcodeScanner.process(image)
                                            .addOnSuccessListener { barcodes: List<Barcode> ->
                                                if (barcodes.isNotEmpty()) {
                                                    val barcode = barcodes[0].rawValue
                                                    if (barcode != null && barcode != currentBarcode) {
                                                        currentBarcode = barcode
                                                        coroutineScope.launch {
                                                            statusMessage = "Searching barcode..."
                                                            isSearchingBarcode = true
                                                            onBarcodeScanned(barcode)
                                                            isSearchingBarcode = false
                                                            statusMessage = "Align barcode in frame"
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener { imageProxy.close() }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, imageAnalysis, imageCapture
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("FoodScanner", "Binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            }



            // High-tech Overlay
            Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                val aspect = when(scannerMode) {
                    ScannerMode.BARCODE -> 1.5f
                    ScannerMode.FRONT_PACKAGE -> 1.0f
                    ScannerMode.NUTRITION -> NUTRITION_OVERLAY_ASPECT
                }
                
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
                    // Frame
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {}
                    
                    // Animated Laser
                    if (scannerMode != ScannerMode.BARCODE && capturedNutrition == null) {
                        val infiniteTransition = rememberInfiniteTransition(label = "laser")
                        val laserPos by infiniteTransition.animateFloat(
                            initialValue = 0.1f,
                            targetValue = 0.9f,
                            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "laser"
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopCenter)
                                .fillMaxHeight(laserPos)
                                .wrapContentHeight(Alignment.Bottom)
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.primary, Color.Transparent)))
                        )
                    }
                }

                Text(
                    statusMessage,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Results / Controls Card (Glassmorphism inspired)
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp)) {
                if (scannerMode != ScannerMode.BARCODE) {
                    if (capturedNutrition == null) {
                        Button(
                            enabled = !isCapturing,
                            onClick = {
                                isCapturing = true
                                statusMessage = "Reading..."
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            try {
                                                val fullBitmap = decodeJpegImageProxy(image)
                                                val rotation = image.imageInfo.rotationDegrees
                                                val rotated = if (rotation != 0) rotateBitmap(fullBitmap, rotation) else fullBitmap
                                                val aspect = if (scannerMode == ScannerMode.FRONT_PACKAGE) 1.0f else NUTRITION_OVERLAY_ASPECT
                                                val cropped = cropToOverlay(rotated, aspect)
                                                val processed = preprocessBitmap(cropped)

                                                val inputImage = InputImage.fromBitmap(processed, 0)
                                                textRecognizer.process(inputImage).addOnSuccessListener { visionText ->
                                                    if (scannerMode == ScannerMode.FRONT_PACKAGE) {
                                                        detectedName = NutritionScanner.parseName(visionText)
                                                        scannerMode = ScannerMode.NUTRITION
                                                        statusMessage = "Now scan nutrition label"
                                                        isCapturing = false
                                                    } else {
                                                        capturedNutrition = NutritionScanner.parseText(visionText).copy(name = detectedName)
                                                        isCapturing = false
                                                        statusMessage = "Verify values"
                                                    }
                                                }
                                            } catch (_: Exception) { isCapturing = false }
                                            finally { image.close() }
                                        }
                                        override fun onError(exc: ImageCaptureException) { isCapturing = false }
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (scannerMode == ScannerMode.FRONT_PACKAGE) "Capture Name" else "Capture Label", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Glassy review card
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(capturedNutrition?.name ?: "Captured Values", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    ScanBadge("Calories", capturedNutrition?.calories?.toInt()?.toString() ?: "--")
                                    ScanBadge("Protein", capturedNutrition?.protein?.let { "%.1fg".format(it) } ?: "--")
                                    ScanBadge("Fat", capturedNutrition?.fat?.let { "%.1fg".format(it) } ?: "--")
                                    ScanBadge("Carbs", capturedNutrition?.carbs?.let { "%.1fg".format(it) } ?: "--")
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(
                                        onClick = { capturedNutrition = null; detectedName = null; scannerMode = ScannerMode.FRONT_PACKAGE; statusMessage = "Scan package name" },
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                    ) { Text("Re-take") }
                                    Button(
                                        onClick = { onResult(capturedNutrition!!, currentBarcode) },
                                        modifier = Modifier.weight(1.2f).height(50.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text("Confirm", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                } else if (currentBarcode != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("New Barcode: $currentBarcode", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            
                            if (isSearchingBarcode) {
                                Spacer(modifier = Modifier.height(16.dp))
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (apiEnabled) "Checking online database..." else "Searching locally...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    text = if (apiEnabled) "Not found locally or online." else "Not found in database.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { scannerMode = ScannerMode.FRONT_PACKAGE; statusMessage = "Scan package name" }) {
                                    Text("Add New Food Manually")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

private fun decodeJpegImageProxy(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalStateException()
}

private fun cropToOverlay(bitmap: Bitmap, targetAspect: Float): Bitmap {
    val bw = bitmap.width
    val bh = bitmap.height
    val currentAspect = bw.toFloat() / bh.toFloat()
    return if (currentAspect > targetAspect) {
        val newWidth = (bh * targetAspect).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(bitmap, ((bw - newWidth) / 2).coerceAtLeast(0), 0, newWidth, bh)
    } else {
        val newHeight = (bw / targetAspect).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(bitmap, 0, ((bh - newHeight) / 2).coerceAtLeast(0), bw, newHeight)
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
    val result = createBitmap(bitmap.width, bitmap.height)
    val canvas = Canvas(result)
    val paint = Paint()
    val cm = ColorMatrix().apply {
        setSaturation(0f)
        val contrast = 1.3f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        postConcat(ColorMatrix(floatArrayOf(contrast,0f,0f,0f,translate,0f,contrast,0f,0f,translate,0f,0f,contrast,0f,translate,0f,0f,0f,1f,0f)))
    }
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return result
}
