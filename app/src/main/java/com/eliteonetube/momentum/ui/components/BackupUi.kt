package com.eliteonetube.momentum.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliteonetube.momentum.logic.backup.DataBackup
import com.eliteonetube.momentum.logic.backup.ImportCheck
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val backupDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

fun formatBackupDate(date: LocalDate): String = date.format(backupDateFormat)

/**
 * Owns the import flow: pick a file, check it, optionally warn that it replaces everything, put it
 * in place and restart. [content] receives the function that starts it and whether it's working.
 *
 * [confirmReplace] is off where there's nothing to lose yet, i.e. before onboarding.
 */
@Composable
fun BackupImporter(
    confirmReplace: Boolean,
    content: @Composable (startImport: () -> Unit, isWorking: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var awaitingConfirmation by remember { mutableStateOf<ImportCheck.Ready?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun replaceData(ready: ImportCheck.Ready) {
        coroutineScope.launch {
            isWorking = true
            if (DataBackup.applyImport(context, ready.backup)) {
                DataBackup.restartApp(context)
            } else {
                message = "Couldn't read this backup; your data wasn't changed."
            }
            isWorking = false
        }
    }

    // Any type: file managers often don't label zips as application/zip
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isWorking = true
            when (val check = DataBackup.stageImport(context, uri)) {
                is ImportCheck.Ready -> if (confirmReplace) awaitingConfirmation = check else replaceData(check)
                ImportCheck.NewerVersion ->
                    message = "This backup was made with a newer version of Momentum. Update the app, then try again."
                ImportCheck.Invalid -> message = "Couldn't read this backup; your data wasn't changed."
            }
            isWorking = false
        }
    }

    content({ picker.launch(arrayOf("*/*")) }, isWorking)

    awaitingConfirmation?.let { ready ->
        val madeOn = runCatching { formatBackupDate(LocalDate.parse(ready.backup.manifest.createdOn)) }.getOrNull()
        val dismiss = {
            awaitingConfirmation = null
            DataBackup.clearWorkFiles(context)
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Replace all current data?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "This replaces all current data on this phone with the backup" +
                        (madeOn?.let { " from $it" } ?: "") + ". This can't be undone. Momentum restarts afterwards." +
                        if (!ready.backup.manifest.includesPhotos) "\n\nThis backup has no progress photos." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { awaitingConfirmation = null; replaceData(ready) }) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}

/** Asks whether to include photos, then saves a backup where the user chooses. */
@Composable
fun ExportDataDialog(onDismiss: () -> Unit, onExported: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var includePhotos by remember { mutableStateOf(true) }
    var isWorking by remember { mutableStateOf(false) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isWorking = true
            val saved = DataBackup.export(context, uri, includePhotos)
            isWorking = false
            Toast.makeText(context, if (saved) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_LONG).show()
            if (saved) onExported()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Export my data", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Saves everything in one file you can import later, on this phone or a new one.")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !isWorking) { includePhotos = !includePhotos },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = includePhotos, onCheckedChange = { includePhotos = it }, enabled = !isWorking)
                    Text("Include progress photos")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The file contains personal data: your weight, diary and photos. Keep it somewhere private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { saver.launch(DataBackup.fileName()) }, enabled = !isWorking) {
                Text(if (isWorking) "Saving…" else "Export")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancel") } }
    )
}
