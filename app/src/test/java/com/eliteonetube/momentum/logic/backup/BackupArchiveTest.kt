package com.eliteonetube.momentum.logic.backup

import com.eliteonetube.momentum.logic.utils.PhotoSizing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val manifest = BackupManifest(format = 1, schemaVersion = 36, appVersion = "1.0.3 (39)", createdOn = "2026-11-12", includesPhotos = true)

    private fun check(manifest: BackupManifest?) = BackupArchive.check(manifest, currentSchemaVersion = 36, oldestSchemaVersion = 12)

    private fun file(name: String, content: String): File = temp.newFile(name).apply { writeText(content) }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    @Test
    fun check_acceptsCurrentAndOlderBackups() {
        assertEquals(BackupCheck.OK, check(manifest))
        assertEquals(BackupCheck.OK, check(manifest.copy(schemaVersion = 12)))
    }

    @Test
    fun check_refusesNewerSchemaOrFormat() {
        assertEquals(BackupCheck.NEWER_VERSION, check(manifest.copy(schemaVersion = 37)))
        assertEquals(BackupCheck.NEWER_VERSION, check(manifest.copy(format = 2)))
    }

    @Test
    fun check_rejectsMissingOrImpossibleManifest() {
        assertEquals(BackupCheck.INVALID, check(null))
        assertEquals(BackupCheck.INVALID, check(manifest.copy(schemaVersion = 11)))
        assertEquals(BackupCheck.INVALID, check(manifest.copy(format = 0)))
    }

    @Test
    fun writeThenExtract_roundTripsEverything() {
        val database = file("live.db", "database bytes")
        val wal = file("live.db-wal", "wal bytes")
        val photos = listOf(file("checkin_1_front.jpg", "front"), file("checkin_1_side.jpg", "side"))
        val zipped = ByteArrayOutputStream().also { BackupArchive.write(it, manifest, database, wal, photos) }.toByteArray()

        val extracted = BackupArchive.extract(ByteArrayInputStream(zipped), temp.newFolder("out"))!!

        assertEquals(manifest, extracted.manifest)
        assertEquals("database bytes", extracted.database.readText())
        assertEquals("wal bytes", File(extracted.database.path + "-wal").readText())
        assertArrayEquals(arrayOf("checkin_1_front.jpg", "checkin_1_side.jpg"), extracted.photosDir.list()!!.sortedArray())
        assertEquals("side", File(extracted.photosDir, "checkin_1_side.jpg").readText())
    }

    @Test
    fun write_leavesOutAnEmptyWal() {
        val zipped = ByteArrayOutputStream().also {
            BackupArchive.write(it, manifest, file("live.db", "db"), file("live.db-wal", ""), emptyList())
        }.toByteArray()
        val extracted = BackupArchive.extract(ByteArrayInputStream(zipped), temp.newFolder("out"))!!
        assertFalse(File(extracted.database.path + "-wal").exists())
    }

    @Test
    fun extract_returnsNullForFilesThatArentBackups() {
        assertNull(BackupArchive.extract(ByteArrayInputStream("not a zip".toByteArray()), temp.newFolder("a")))
        assertNull(BackupArchive.extract(ByteArrayInputStream(zipOf("holiday.jpg" to "x")), temp.newFolder("b")))
        // A manifest alone isn't enough
        assertNull(BackupArchive.extract(ByteArrayInputStream(zipOf(BackupArchive.MANIFEST_ENTRY to "format=1\nschemaVersion=36")), temp.newFolder("c")))
    }

    @Test
    fun extract_neverWritesOutsideTheTargetFolder() {
        val target = temp.newFolder("out")
        val zipped = zipOf(
            BackupArchive.MANIFEST_ENTRY to "format=1\nschemaVersion=36",
            BackupArchive.DATABASE_ENTRY to "db",
            "photos/../../escaped.jpg" to "bad",
            "../escaped.jpg" to "bad",
            "photos/nested/deep.jpg" to "bad",
            "photos/ok.jpg" to "good"
        )
        val extracted = BackupArchive.extract(ByteArrayInputStream(zipped), target)
        assertNotNull(extracted)
        assertArrayEquals(arrayOf("ok.jpg"), extracted!!.photosDir.list())
        assertFalse(File(temp.root, "escaped.jpg").exists())
        assertFalse(File(target, "escaped.jpg").exists())
    }

    @Test
    fun photoFileName_onlyAcceptsPlainJpgsUnderPhotos() {
        assertEquals("checkin_1_front.jpg", BackupArchive.photoFileName("photos/checkin_1_front.jpg"))
        assertNull(BackupArchive.photoFileName("photos/notes.txt"))
        assertNull(BackupArchive.photoFileName("photos/.hidden.jpg"))
        assertNull(BackupArchive.photoFileName("photos/a\\b.jpg"))
        assertNull(BackupArchive.photoFileName("other/photo.jpg"))
    }

    @Test
    fun photoSizing_shrinksOnlyLargePhotosAndKeepsShape() {
        assertEquals(1200 to 900, PhotoSizing.scaledSize(1200, 900))
        assertEquals(1600 to 1200, PhotoSizing.scaledSize(4000, 3000))
        assertEquals(1200 to 1600, PhotoSizing.scaledSize(3000, 4000))
        assertFalse(PhotoSizing.needsShrinking(1600, 1200))
        assertTrue(PhotoSizing.needsShrinking(1601, 1200))
    }

    @Test
    fun photoSizing_sampleSizeNeverDecodesBelowTheTarget() {
        assertEquals(1, PhotoSizing.sampleSize(1600, 1200))
        assertEquals(1, PhotoSizing.sampleSize(3000, 2000))
        assertEquals(2, PhotoSizing.sampleSize(4000, 3000))
        assertEquals(4, PhotoSizing.sampleSize(8000, 6000))
    }
}
