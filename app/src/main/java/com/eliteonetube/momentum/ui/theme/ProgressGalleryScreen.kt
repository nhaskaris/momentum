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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                title = { Text("Progress Gallery", fontWeight = FontWeight.Bold) },
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
                            label = { Text("Compare") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ComparisonColumn("START", photo1, checkIns, onSelect1, onPhotoClick, Modifier.weight(1f))
            ComparisonColumn("END", photo2, checkIns, onSelect2, onPhotoClick, Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            val weightDiff = if (photo1 != null && photo2 != null) photo2.weight - photo1.weight else 0.0
            val color = if (weightDiff <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Overall Change: ", style = MaterialTheme.typography.bodyLarge)
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
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
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
        Spacer(modifier = Modifier.height(12.dp))
        var expanded by remember { mutableStateOf(false) }
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selected?.date ?: "Select Date",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                checkIns.forEach { checkIn ->
                    DropdownMenuItem(
                        text = { Text(checkIn.date) },
                        onClick = {
                            onSelect(checkIn)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (selected != null) {
            Text("${selected.weight} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun GalleryListLayout(checkIns: List<CheckIn>, onPhotoClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(checkIns) { checkIn ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(checkIn.date, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${checkIn.weight} kg",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item { PhotoCard("Front", checkIn.frontPhotoPath, onPhotoClick) }
                    item { PhotoCard("Back", checkIn.backPhotoPath, onPhotoClick) }
                    item { PhotoCard("Side", checkIn.sidePhotoPath, onPhotoClick) }
                }
            }
        }
    }
}

@Composable
fun PhotoCard(label: String, path: String?, onClick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyGalleryState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("No progress photos yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
        Text("Complete a check-in to start your gallery", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun FullScreenPhotoViewer(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(path),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
