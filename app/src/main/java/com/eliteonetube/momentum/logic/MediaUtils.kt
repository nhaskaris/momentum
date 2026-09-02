package com.eliteonetube.momentum.logic

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object MediaUtils {
    fun createImageUri(context: Context): Uri? {
        val directory = File(context.cacheDir, "images")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, "temp_image_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "com.eliteonetube.momentum.fileprovider",
            file
        )
    }
}
