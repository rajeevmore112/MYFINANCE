package com.myfinance.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FinanceBackupArchiveTest {
    @Test fun roundTripsVersionedBackupAndChecksum() {
        val directory = Files.createTempDirectory("finance-backup-test").toFile()
        val database = directory.resolve("myfinance.db").apply { writeBytes(byteArrayOf(0, 1, 2, 3, 0xff.toByte())) }
        val restored = directory.resolve("restored.db")
        val archive = ByteArrayOutputStream()
        FinanceBackupArchive.write(database, archive, schemaVersion = 1, createdAtMillis = 123L)

        val manifest = FinanceBackupArchive.read(ByteArrayInputStream(archive.toByteArray()), restored)

        assertEquals(FinanceBackupArchive.FORMAT_VERSION, manifest.formatVersion)
        assertEquals(1, manifest.schemaVersion)
        assertEquals(123L, manifest.createdAtMillis)
        assertEquals(database.readBytes().toList(), restored.readBytes().toList())
        directory.deleteRecursively()
    }

    @Test fun rejectsDatabaseChangedAfterChecksumWasCreated() {
        val directory = Files.createTempDirectory("finance-backup-tamper-test").toFile()
        val database = directory.resolve("myfinance.db").apply { writeText("database") }
        val archive = ByteArrayOutputStream()
        FinanceBackupArchive.write(database, archive, schemaVersion = 1)
        val modified = ByteArrayOutputStream()
        ZipOutputStream(modified).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.properties")); zip.write("formatVersion=1\nschemaVersion=1\ncreatedAtMillis=1\nsha256=${"0".repeat(64)}\n".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("myfinance.db")); zip.write("database".toByteArray()); zip.closeEntry()
        }

        assertThrows(IllegalArgumentException::class.java) {
            FinanceBackupArchive.read(ByteArrayInputStream(modified.toByteArray()), directory.resolve("restored.db"))
        }
        directory.deleteRecursively()
    }
}
