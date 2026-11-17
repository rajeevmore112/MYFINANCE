package com.myfinance.app.data

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class FinanceBackupManager(context: Context, private val db: FinanceDatabase) {
    private val appContext = context.applicationContext

    suspend fun writeBackup(output: OutputStream) = withContext(Dispatchers.IO) {
        val database = db.openHelper.writableDatabase
        val databasePath = requireNotNull(database.path) { "Database file path is unavailable" }
        database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Could not checkpoint database for backup" }
        }
        val snapshot = File.createTempFile("myfinance-backup-", ".db", appContext.cacheDir)
        try {
            File(databasePath).copyTo(snapshot, overwrite = true)
            FinanceBackupArchive.write(snapshot, output, db.openHelper.writableDatabase.version)
        } finally {
            snapshot.delete()
        }
    }

    suspend fun restore(input: InputStream): String = withContext(Dispatchers.IO) {
        val staged = File.createTempFile("myfinance-restore-", ".db", appContext.cacheDir)
        try {
            val manifest = FinanceBackupArchive.read(input, staged)
            require(manifest.schemaVersion == db.openHelper.writableDatabase.version) {
                "Backup database version ${manifest.schemaVersion} is not supported by this app"
            }
            validateDatabase(staged, manifest.schemaVersion)

            val database = db.openHelper.writableDatabase
            val databasePath = requireNotNull(database.path) { "Database file path is unavailable" }
            val destination = File(databasePath)
            database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Could not prepare current data for safe restore" }
            }
            val safetyDirectory = File(appContext.filesDir, "safety-backups").apply { mkdirs() }
            val safetyCopy = File(safetyDirectory, "before-restore-${System.currentTimeMillis()}.db")
            File(databasePath).copyTo(safetyCopy, overwrite = false)
            db.close()
            File(databasePath + "-wal").delete()
            File(databasePath + "-shm").delete()
            File(databasePath + "-journal").delete()
            staged.copyTo(destination, overwrite = true)
            safetyCopy.absolutePath
        } finally {
            staged.delete()
        }
    }

    private fun validateDatabase(file: File, expectedSchemaVersion: Int) {
        val candidate = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        candidate.use { database ->
            require(database.version == expectedSchemaVersion) { "Backup schema version does not match its manifest" }
            val integrity = DatabaseUtils.stringForQuery(database, "PRAGMA integrity_check", null)
            require(integrity == "ok") { "Backup database failed SQLite integrity check" }
            val requiredTables = setOf("accounts", "wallets", "transactions", "budgets", "recurring_rules")
            val tableNames = mutableSetOf<String>()
            database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                while (cursor.moveToNext()) tableNames += cursor.getString(0)
            }
            require(tableNames.containsAll(requiredTables)) { "Backup does not contain required MYFINANCE data" }
        }
    }
}
