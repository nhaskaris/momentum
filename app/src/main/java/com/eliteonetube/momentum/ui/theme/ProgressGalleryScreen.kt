package com.eliteonetube.momentum.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.eliteonetube.momentum.data.CheckIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressGalleryScreen(
    checkIns: List<CheckIn>,
    onBack: () -> Unit
) {
    var compareMode by remember { mutableStateOf(false) }
    var fullScreenPhotoPath by remember { mutableStateOf<String?>(null) }

    val photoCheckIns = checkIns.filter { (it.frontPhotoPath != null) || (it.backPhotoPath != null) || (it.sidePhotoPath != null) }
        .sortedByDescending { it.date }

    var selectedPhoto1 by remember { mutableStateOf<CheckIn?>(null) }
    var selectedPhoto2 by remember { mutableStateOf<CheckIn?>(null) }

    LaunchedEffect(photoCheckIns) {
        if (photoCheckIns.size >= 2 && selectedPhoto1 == null) {
            selectedPhoto1 = photoCheckIns.last()
            selectedPhoto2 = photoCheckIns.first()
        } else if (photoCheckIns.isNotEmpty() && selectedPhoto1 == null) {
            selectedPhoto1 = photoCheckIns.first()
        }
    }

    if (fullScreenPhotoPath != null) {
        FullScreenPhotoViewer(
            path = fullScreenPhotoPath!!,
            onDismiss = { fullScreenPhotoPath = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (compareMode) "Comparison" else "Gallery", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (photoCheckIns.size >= 2) {
                        FilterChip(
                            selected = compareMode,
                            onClick = { compareMode = !compareMode },
                            label = { Text("Side-by-Side") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.padding(end = 12.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Hero Gradient Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), Color.Transparent)))
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TRANSFORMATION", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Visual History", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                }
            }

            if (compareMode && photoCheckIns.size >= 2) {
                ComparisonLayout(
                    checkIns = photoCheckIns,
                    photo1 = selectedPhoto1,
                    photo2 = selectedPhoto2,
                    onSelect1 = { selectedPhoto1 = it },
                    onSelect2 = { selectedPhoto2 = it },
                    onPhotoClick = { fullScreenPhotoPath = it }
                )
            } else if (photoCheckIns.isEmpty()) {
                EmptyGalleryState()
            } else {
                GalleryListLayout(photoCheckIns, onPhotoClick = { fullScreenPhotoPath = it })
            }
        }
    }
}

@Composable
fun ComparisonLayout(
    checkIns: List<CheckIn>,
    photo1: CheckIn?,
    photo2: CheckIn?,
    onSelect1: (CheckIn) -> Unit,
    onSelect2: (CheckIn) -> Unit,
    onPhotoClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ComparisonColumn("EARLIER", photo1, checkIns, onSelect1, onPhotoClick, Modifier.weight(1f))
            ComparisonColumn("LATEST", photo2, checkIns, onSelect2, onPhotoClick, Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            val weightDiff = if (photo1 != null && photo2 != null) photo2.weight - photo1.weight else 0.0
            val color = if (weightDiff <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            
            Row(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Total Weight Change: ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${if (weightDiff > 0) "+" else ""}${"%.1f".format(weightDiff)} kg",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = color
                )
            }
        }
    }
}

@Composable
fun ComparisonColumn(
    label: String,
    selected: CheckIn?,
    checkIns: List<CheckIn>,
    onSelect: (CheckIn) -> Unit,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .let { if (selected?.frontPhotoPath != null) it.clickable { onPhotoClick(selected.frontPhotoPath) } else it }
        ) {
            if (selected?.frontPhotoPath != null) {
                Image(
                    painter = rememberAsyncImagePainter(selected.frontPhotoPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        var expanded by remember { mutableStateOf(false) }
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selected?.date ?: "Select Date", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                checkIns.forEach { ci ->
                    DropdownMenuItem(text = { Text(ci.date) }, onClick = { onSelect(ci); expanded = false })
                }
            }
        }
    }
}

@Composable
fun GalleryListLayout(checkIns: List<CheckIn>, onPhotoClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(checkIns) { ci ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ci.date, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("${ci.weight} kg", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item { PhotoCard("Front", ci.frontPhotoPath, onPhotoClick) }
                    item { PhotoCard("Back", ci.backPhotoPath, onPhotoClick) }
                    item { PhotoCard("Side", ci.sidePhotoPath, onPhotoClick) }
                }
            }
        }
    }
}

@Composable
fun PhotoCard(label: String, path: String?, onClick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(160.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .let { if (path != null) it.clickable { onClick(path) } else it }
        ) {
            if (path != null) {
                Image(
                    painter = rememberAsyncImagePainter(path),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyGalleryState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(24.dp))
        Text("No progress photos yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        Text("Log photos during check-in", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FullScreenPhotoViewer(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Image(
                painter = rememberAsyncImagePainter(path),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
}
