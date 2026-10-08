package com.eliteonetube.momentum.logic.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaUtils {
    // Matches ${applicationId}.fileprovider in the manifest, so debug and release builds don't clash
    private fun fileProviderAuthority(context: Context) = "${context.packageName}.fileprovider"

    /** Temporary target for a camera capture; lives in cache until [persistProgressPhoto] copies it. */
    fun createImageUri(context: Context): Uri? {
        val directory = File(context.cacheDir, "images")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, "temp_image_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
    }

    /**
     * Copies a check-in photo into the app's own storage and returns a `file://` URI string to save.
     *
     * Neither source is safe to keep: camera shots sit in the cache (cleared by the system when space
     * runs low), and gallery picks are content URIs the app may only read until it's closed.
     * Returns null if the photo couldn't be read.
     */
    suspend fun persistProgressPhoto(context: Context, source: Uri, label: String): String? = withContext(Dispatchers.IO) {
        try {
            val directory = File(context.filesDir, "progress_photos").apply { mkdirs() }
            val target = File(directory, "checkin_${System.currentTimeMillis()}_$label.jpg")
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (copied == null) return@withContext null

            // The camera's temporary file is no longer needed once copied
            if (source.authority == fileProviderAuthority(context)) {
                runCatching { context.contentResolver.delete(source, null, null) }
            }
            Uri.fromFile(target).toString()
        } catch (e: Exception) {
            Log.e("MediaUtils", "Couldn't save progress photo", e)
            null
        }
    }
}
