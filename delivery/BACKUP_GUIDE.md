# MYFINANCE Backup and Restoration

## Create a backup

Open **More → Settings → Create local backup**, choose a destination in Android's file picker, and keep the ZIP somewhere private and separate from the phone. The backup is user initiated and contains a checkpointed copy of the local SQLite database plus a versioned manifest with the database schema version, creation time, and SHA-256 checksum. No backup data is uploaded by the app.

Backups are not encrypted. Protect the file using the storage location's access controls. The database itself is currently unencrypted.

## Restore a backup

Open **More → Settings → Restore from backup…**, select the ZIP, and confirm the replacement. MYFINANCE checks the archive format, checksum, SQLite integrity, required tables, and schema compatibility before replacing current data. It creates a private app-internal safety copy first, then closes the app. Reopen MYFINANCE to use the restored database.

Restore currently accepts only the database schema version supported by this app build. The safety copy is stored in app-private storage and there is not yet an in-app recovery browser for it. Keep a separate copy of important backup files.

## Verification limits

The archive format has automated round-trip and checksum-rejection unit tests. Gradle tests, lint, and APK packaging are run as part of this delivery. No Android device/emulator is available here, so the Storage Access Framework picker, SQLite snapshot, process restart, and actual restore have not yet been exercised on-device. The backup is intended for personal data portability, not as a production-grade encrypted or remotely replicated backup system.
