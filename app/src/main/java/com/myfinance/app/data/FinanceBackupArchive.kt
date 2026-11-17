package com.myfinance.app.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Small versioned envelope around a checkpointed SQLite database file. */
object FinanceBackupArchive {
    const val FORMAT_VERSION = 1
    private const val DB_ENTRY = "myfinance.db"
    private const val MANIFEST_ENTRY = "manifest.properties"
    private const val MAX_DATABASE_BYTES = 512L * 1024 * 1024

    data class Manifest(val formatVersion: Int, val schemaVersion: Int, val createdAtMillis: Long, val sha256: String)

    fun write(database: File, output: OutputStream, schemaVersion: Int, createdAtMillis: Long = System.currentTimeMillis()) {
        require(database.isFile && database.length() in 1..MAX_DATABASE_BYTES) { "Database snapshot is missing or too large" }
        val digest = sha256(database)
        val manifest = "formatVersion=$FORMAT_VERSION\nschemaVersion=$schemaVersion\ncreatedAtMillis=$createdAtMillis\nsha256=$digest\n"
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY)); zip.write(manifest.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB_ENTRY)); database.inputStream().buffered().use { it.copyTo(zip) }; zip.closeEntry()
        }
    }

    fun read(input: InputStream, databaseTarget: File): Manifest {
        val entries = mutableSetOf<String>()
        var manifest: String? = null
        var databaseBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf(MANIFEST_ENTRY, DB_ENTRY) && entries.add(entry.name)) { "Backup has an unexpected or duplicate entry" }
                when (entry.name) {
                    MANIFEST_ENTRY -> {
                        val bytes = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            require(bytes.size() + read <= 4096) { "Backup manifest is too large" }
                            bytes.write(buffer, 0, read)
                        }
                        manifest = bytes.toByteArray().toString(Charsets.UTF_8)
                    }
                    DB_ENTRY -> {
                        databaseTarget.outputStream().buffered().use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                databaseBytes += read
                                require(databaseBytes <= MAX_DATABASE_BYTES) { "Backup database is too large" }
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        require(entries == setOf(MANIFEST_ENTRY, DB_ENTRY) && databaseBytes > 0) { "Backup is incomplete" }
        val values = requireNotNull(manifest).lineSequence().mapNotNull { line ->
            val split = line.indexOf('=')
            if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
        }.toMap()
        val parsed = runCatching {
            Manifest(values.getValue("formatVersion").toInt(), values.getValue("schemaVersion").toInt(), values.getValue("createdAtMillis").toLong(), values.getValue("sha256"))
        }.getOrElse { throw IllegalArgumentException("Backup manifest is invalid", it) }
        require(parsed.formatVersion == FORMAT_VERSION) { "Unsupported backup format version ${parsed.formatVersion}" }
        require(parsed.schemaVersion > 0) { "Invalid database schema version" }
        require(parsed.sha256.matches(Regex("[0-9a-f]{64}")) && sha256(databaseTarget) == parsed.sha256) { "Backup checksum verification failed" }
        return parsed
    }

    private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
