package com.eliteonetube.momentum.logic.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The small info file inside every backup. */
data class BackupManifest(
    val format: Int,
    val schemaVersion: Int,
    val appVersion: String,
    /** ISO yyyy-MM-dd. */
    val createdOn: String,
    val includesPhotos: Boolean
)

enum class BackupCheck { OK, NEWER_VERSION, INVALID }

/** A backup unpacked into a folder, ready to be checked and put in place. */
data class ExtractedBackup(val manifest: BackupManifest, val database: File, val photosDir: File)

/**
 * The backup file format: a zip holding [MANIFEST_ENTRY], the database (plus its write-ahead log
 * when there is one) and the progress photos. Plain java.io and java.util.zip, so it can be unit-tested.
 */
object BackupArchive {
    const val FORMAT = 1
    const val MANIFEST_ENTRY = "backup.properties"
    const val DATABASE_ENTRY = "database/momentum.db"
    const val WAL_ENTRY = "$DATABASE_ENTRY-wal"
    const val PHOTOS_PREFIX = "photos/"

    /** Whether a backup with this [manifest] can be imported by an app at [currentSchemaVersion]. */
    fun check(manifest: BackupManifest?, currentSchemaVersion: Int, oldestSchemaVersion: Int): BackupCheck = when {
        manifest == null -> BackupCheck.INVALID
        manifest.format > FORMAT || manifest.schemaVersion > currentSchemaVersion -> BackupCheck.NEWER_VERSION
        manifest.format < 1 || manifest.schemaVersion < oldestSchemaVersion -> BackupCheck.INVALID
        else -> BackupCheck.OK
    }

    fun write(out: OutputStream, manifest: BackupManifest, database: File, wal: File?, photos: List<File>) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            manifest.toProperties().store(zip, "Momentum backup")
            zip.closeEntry()
            zip.addFile(DATABASE_ENTRY, database)
            if (wal != null && wal.length() > 0) zip.addFile(WAL_ENTRY, wal)
            photos.forEach { zip.addFile(PHOTOS_PREFIX + it.name, it) }
        }
    }

    /**
     * Unpacks a backup into [targetDir]. Returns null when the file isn't a Momentum backup
     * (no manifest or no database). Unknown entries are skipped, and nothing can be written
     * outside [targetDir] whatever the entry names say.
     */
    fun extract(input: InputStream, targetDir: File): ExtractedBackup? {
        val database = File(targetDir, "momentum.db")
        val photosDir = File(targetDir, "photos").apply { mkdirs() }
        var manifest: BackupManifest? = null
        var hasDatabase = false

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.isDirectory -> Unit
                    entry.name == MANIFEST_ENTRY -> manifest = manifestFrom(Properties().apply { load(zip) })
                    entry.name == DATABASE_ENTRY -> { database.outputStream().use { zip.copyTo(it) }; hasDatabase = true }
                    entry.name == WAL_ENTRY -> File(targetDir, database.name + "-wal").outputStream().use { zip.copyTo(it) }
                    else -> photoFileName(entry.name)?.let { name -> File(photosDir, name).outputStream().use { zip.copyTo(it) } }
                }
            }
        }
        return manifest?.takeIf { hasDatabase }?.let { ExtractedBackup(it, database, photosDir) }
    }

    /** The file name for a photo entry, or null for anything that isn't a plain .jpg directly under photos/. */
    fun photoFileName(entryName: String): String? {
        if (!entryName.startsWith(PHOTOS_PREFIX)) return null
        val name = entryName.removePrefix(PHOTOS_PREFIX)
        val plainName = name.isNotEmpty() && name.none { it == '/' || it == '\\' } && !name.startsWith(".")
        return name.takeIf { plainName && it.endsWith(".jpg", ignoreCase = true) }
    }

    private fun ZipOutputStream.addFile(entryName: String, file: File) {
        putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    private fun BackupManifest.toProperties() = Properties().apply {
        setProperty("format", format.toString())
        setProperty("schemaVersion", schemaVersion.toString())
        setProperty("appVersion", appVersion)
        setProperty("createdOn", createdOn)
        setProperty("includesPhotos", includesPhotos.toString())
    }

    private fun manifestFrom(properties: Properties): BackupManifest? {
        val format = properties.getProperty("format")?.toIntOrNull() ?: return null
        val schemaVersion = properties.getProperty("schemaVersion")?.toIntOrNull() ?: return null
        return BackupManifest(
            format = format,
            schemaVersion = schemaVersion,
            appVersion = properties.getProperty("appVersion").orEmpty(),
            createdOn = properties.getProperty("createdOn").orEmpty(),
            includesPhotos = properties.getProperty("includesPhotos").toBoolean()
        )
    }
}
