package com.eliteonetube.momentum.logic.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.eliteonetube.momentum.data.WeightDatabase
import com.eliteonetube.momentum.logic.utils.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** What checking a picked backup file found. */
sealed interface ImportCheck {
    /** Verified and unpacked; [DataBackup.applyImport] can put it in place. */
    data class Ready(val backup: ExtractedBackup) : ImportCheck
    data object NewerVersion : ImportCheck
    data object Invalid : ImportCheck
}

/** Export and import of everything the app stores: the database and the progress photos. */
object DataBackup {
    private const val TAG = "DataBackup"
    private const val PREFS = "data_backup"
    private const val KEY_LAST_EXPORT = "last_export"
    /** Inside filesDir so moving files in and out is a rename; excluded from Android's own backups. */
    private const val WORK_DIR = "backup_work"
    private val PHOTO_COLUMNS = listOf("frontPhotoPath", "backPhotoPath", "sidePhotoPath")
    private val SEEDER_PREFS = listOf("exercise_seeder_prefs", "food_seeder_prefs")

    fun fileName(today: LocalDate = LocalDate.now()) = "momentum-backup-$today.zip"

    fun lastExportDate(context: Context): LocalDate? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_EXPORT, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Removes leftovers of an export or import that didn't finish. */
    fun clearWorkFiles(context: Context) {
        workDir(context).deleteRecursively()
    }

    /** Writes a backup zip to [destination], a document the user just created. */
    suspend fun export(context: Context, destination: Uri, includePhotos: Boolean): Boolean = withContext(Dispatchers.IO) {
        val snapshotDir = File(workDir(context), "export").apply { deleteRecursively(); mkdirs() }
        try {
            val snapshot = File(snapshotDir, "momentum.db")
            val walSnapshot = File(snapshotDir, "momentum.db-wal")
            val live = context.getDatabasePath(WeightDatabase.NAME)
            WeightDatabase.getInstance(context).useWriterConnection { connection ->
                // Folds pending changes into the main file where it can, then holds the write lock
                // so the copy can't catch a half-written change
                connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
                connection.immediateTransaction {
                    live.copyTo(snapshot, overwrite = true)
                    File(live.path + "-wal").takeIf { it.exists() && it.length() > 0 }?.copyTo(walSnapshot, overwrite = true)
                }
            }

            val photos = if (includePhotos) {
                MediaUtils.progressPhotosDir(context).listFiles { file -> BackupArchive.photoFileName(BackupArchive.PHOTOS_PREFIX + file.name) != null }
                    ?.toList().orEmpty()
            } else emptyList()
            val today = LocalDate.now()
            val manifest = BackupManifest(
                format = BackupArchive.FORMAT,
                schemaVersion = WeightDatabase.SCHEMA_VERSION,
                appVersion = appVersionName(context),
                createdOn = today.toString(),
                includesPhotos = includePhotos
            )
            val out = context.contentResolver.openOutputStream(destination, "wt") ?: error("Couldn't open $destination")
            out.use { BackupArchive.write(it, manifest, snapshot, walSnapshot.takeIf { wal -> wal.exists() }, photos) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_LAST_EXPORT, today.toString()) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            // Don't leave a broken file behind looking like a backup
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            false
        } finally {
            snapshotDir.deleteRecursively()
        }
    }

    /**
     * Unpacks and fully checks a picked file without touching current data. A [ImportCheck.Ready]
     * result is staged in the work folder for [applyImport]; anything else leaves nothing behind.
     */
    suspend fun stageImport(context: Context, source: Uri): ImportCheck = withContext(Dispatchers.IO) {
        val stagingDir = File(workDir(context), "import").apply { deleteRecursively(); mkdirs() }
        val result = try {
            val backup = context.contentResolver.openInputStream(source)?.use { BackupArchive.extract(it, stagingDir) }
            when (BackupArchive.check(backup?.manifest, WeightDatabase.SCHEMA_VERSION, WeightDatabase.OLDEST_MIGRATABLE_VERSION)) {
                BackupCheck.NEWER_VERSION -> ImportCheck.NewerVersion
                BackupCheck.INVALID -> ImportCheck.Invalid
                BackupCheck.OK -> prepareDatabase(context, backup!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't read backup", e)
            ImportCheck.Invalid
        }
        if (result !is ImportCheck.Ready) stagingDir.deleteRecursively()
        result
    }

    /**
     * Replaces the current data with a staged backup. On failure the previous data is put back and
     * false is returned. On success the caller must [restartApp]: the running app still has the old
     * database open, which keeps working on the moved-aside files until then.
     */
    suspend fun applyImport(context: Context, backup: ExtractedBackup): Boolean = withContext(Dispatchers.IO) {
        val liveDb = context.getDatabasePath(WeightDatabase.NAME)
        val livePhotos = MediaUtils.progressPhotosDir(context)
        val asideDir = File(workDir(context), "previous").apply { deleteRecursively(); mkdirs() }
        val asideDbFiles = listOf("", "-wal", "-shm").map { suffix -> File(liveDb.path + suffix) to File(asideDir, "db$suffix") }
        val asidePhotos = File(asideDir, "photos")
        try {
            asideDbFiles.forEach { (live, aside) -> if (live.exists() && !live.renameTo(aside)) error("Couldn't move ${live.name}") }
            if (livePhotos.exists() && !livePhotos.renameTo(asidePhotos)) error("Couldn't move photos")

            liveDb.parentFile?.mkdirs()
            backup.database.copyTo(liveDb, overwrite = true)
            if (!backup.photosDir.renameTo(livePhotos)) error("Couldn't place photos")

            // Bundled foods and exercises added since the backup was made get seeded again on restart
            SEEDER_PREFS.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit { clear() } }
            MediaUtils.requestPhotoShrink(context)
            workDir(context).deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Import failed, restoring previous data", e)
            listOf("", "-wal", "-shm").forEach { File(liveDb.path + it).delete() }
            livePhotos.deleteRecursively()
            asideDbFiles.forEach { (live, aside) -> if (aside.exists()) aside.renameTo(live) }
            if (asidePhotos.exists()) asidePhotos.renameTo(livePhotos)
            workDir(context).deleteRecursively()
            false
        }
    }

    /** Starts the app fresh, so every screen and background job reads the imported data. */
    fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        context.startActivity(Intent.makeRestartActivityTask(launch?.component))
        Runtime.getRuntime().exit(0)
    }

    /** Checks the unpacked database really is a usable Momentum database and points its photos at this install. */
    private fun prepareDatabase(context: Context, backup: ExtractedBackup): ImportCheck {
        val db = SQLiteDatabase.openDatabase(backup.database.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            // The manifest is only a claim; the database's own version decides
            val version = db.version
            if (version > WeightDatabase.SCHEMA_VERSION) return ImportCheck.NewerVersion
            if (version < WeightDatabase.OLDEST_MIGRATABLE_VERSION) return ImportCheck.Invalid

            val intact = db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            val hasProfileTable = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'user_profile_table'", null
            ).use { it.moveToFirst() }
            if (!intact || !hasProfileTable) return ImportCheck.Invalid

            relinkPhotos(context, db)
            // Leaves a single self-contained file to move into place
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        } finally {
            db.close()
        }
        listOf("-wal", "-shm").forEach { File(backup.database.path + it).delete() }
        return ImportCheck.Ready(backup)
    }

    /**
     * Photo paths are stored in full, including the folder of the install that saved them, so
     * each is pointed at this install's photo folder. Paths whose photo isn't in the backup are
     * kept too: the gallery leaves out photos whose file is missing.
     */
    private fun relinkPhotos(context: Context, db: SQLiteDatabase) {
        val photosDir = MediaUtils.progressPhotosDir(context)
        val columns = db.rawQuery("PRAGMA table_info(check_in_table)", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.toList()
        }
        PHOTO_COLUMNS.filter { it in columns }.forEach { column ->
            val paths = db.rawQuery("SELECT id, $column FROM check_in_table WHERE $column IS NOT NULL", null).use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getLong(0) to cursor.getString(1) else null }.toList()
            }
            paths.forEach { (id, path) ->
                val fileName = path.substringAfterLast('/')
                db.execSQL(
                    "UPDATE check_in_table SET $column = ? WHERE id = ?",
                    arrayOf<Any>(Uri.fromFile(File(photosDir, fileName)).toString(), id)
                )
            }
        }
    }

    private fun workDir(context: Context) = File(context.filesDir, WORK_DIR)

    private fun appVersionName(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }
}
