package com.eliteonetube.momentum.logic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

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
            val directory = progressPhotosDir(context).apply { mkdirs() }
            val target = File(directory, "checkin_${System.currentTimeMillis()}_$label.jpg")
            // Saved small: full camera photos would outgrow backups within a few check-ins
            val saved = writeShrunkJpeg(target) { context.contentResolver.openInputStream(source) }
            if (!saved) {
                // Not decodable as an image here; keep the original bytes rather than lose the photo
                val copied = context.contentResolver.openInputStream(source)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                if (copied == null) return@withContext null
            }

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

    fun progressPhotosDir(context: Context) = File(context.filesDir, PROGRESS_PHOTOS_DIR)

    /** True when a saved photo path still has its file, e.g. not after a restore that left photos out. */
    fun photoExists(path: String?): Boolean =
        path != null && runCatching { Uri.parse(path).path?.let { File(it).exists() } == true }.getOrDefault(false)

    /**
     * Shrinks photos saved at full camera size by earlier versions. Runs once; file names stay the
     * same, so the paths stored with each check-in keep working.
     */
    suspend fun shrinkExistingPhotos(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PHOTOS_SHRUNK, false)) return@withContext
        progressPhotosDir(context).listFiles { file -> file.extension.equals("jpg", ignoreCase = true) }?.forEach { photo ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(photo.path, bounds)
                if (bounds.outWidth > 0 && PhotoSizing.needsShrinking(bounds.outWidth, bounds.outHeight)) {
                    val temp = File(photo.parentFile, photo.name + ".tmp")
                    // Replaced only once the smaller copy is fully written
                    if (writeShrunkJpeg(temp) { photo.inputStream() }) temp.renameTo(photo) else temp.delete()
                }
            }.onFailure { Log.e("MediaUtils", "Couldn't shrink ${photo.name}", it) }
        }
        prefs.edit { putBoolean(KEY_PHOTOS_SHRUNK, true) }
    }

    /** Makes the next launch re-check photo sizes, e.g. after importing a backup with large photos. */
    fun requestPhotoShrink(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_PHOTOS_SHRUNK, false) }
    }

    /** Decodes [open]'s image, scales it to [PhotoSizing] limits upright, and writes it to [target]. */
    private fun writeShrunkJpeg(target: File, open: () -> InputStream?): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode returns no bitmap, so only a missing stream counts as failure here
        (open() ?: return false).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val options = BitmapFactory.Options().apply { inSampleSize = PhotoSizing.sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return false
        val (width, height) = PhotoSizing.scaledSize(decoded.width, decoded.height)
        // Re-encoding drops the EXIF data, so the rotation it describes is applied to the pixels
        val orientation = open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        val matrix = Matrix().apply {
            postScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        return target.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, PhotoSizing.JPEG_QUALITY, it) }
    }

    private const val PROGRESS_PHOTOS_DIR = "progress_photos"
    private const val PREFS = "media"
    private const val KEY_PHOTOS_SHRUNK = "photos_shrunk"
}
