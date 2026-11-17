# MYFINANCE Development Status

Updated: 2026-11-15

## Environment verified

- Windows 10 Pro, build 19045.
- Android Studio JBR: OpenJDK 25.0.4 at `C:\Program Files\Android\Android Studio\jbr`; Java was not on this PowerShell session's `PATH`, and the reported `JAVA_HOME`/`ANDROID_HOME` variables were not inherited by the shell.
- Android SDK Platform 36 and Build Tools 36.0.0 are installed. Platform 37 was reported but was not present at the supplied SDK path. ADB 37.0.1 is installed; `adb devices -l` showed no attached device or emulator.
- The project uses AGP 8.13.2, Kotlin 2.2.20, Room 2.8.2, `compileSdk=36`, and Java/Kotlin bytecode target 17.
- AGP 8.13 requires Gradle 8.13 and JDK 17. Gradle 8.13 cannot run on Java 25; Java 25 requires Gradle 9.1 or later, which is outside the project's AGP 8.13 requirement. I used a temporary Temurin JDK 17.0.21 without changing the machine-wide Java setup. For normal Android Studio builds, select an installed JDK 17 as the Gradle JDK.
- Kotlin 2.2.20's documented fully supported AGP range ends at 8.11.1, while the existing project uses AGP 8.13.2. I kept the configured versions as requested; this exact combination built successfully here, but remains outside Kotlin's published full-support matrix.

## Changes completed

- Added Gradle Wrapper scripts and JAR for Gradle 8.13, pinned to the official distribution with SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
- Corrected the invalid stable Biometric dependency coordinate from 1.4.0 to 1.1.0. AndroidX lists 1.4.0 as prerelease; this dependency is not yet used for an app-lock workflow.
- Added the Room Gradle plugin and exported schema `app/schemas/com.myfinance.app.data.FinanceDatabase/1.json`.
- Fixed compiler errors in INR paise conversion, the exhaustive ledger type mapping, and Compose Material experimental API annotations.
- Added a transaction update operation that updates linked transfer entries atomically and rejects edits that would overdraw a source wallet.
- Added a transaction edit dialog, linked delete confirmation, linked bank transfers, wallet allocation/spend and wallet-to-wallet transfer forms, and balance reconciliation with a required explanation and difference preview.
- Updated amount formatting to Indian grouping with two decimal places.
- Connected account, transaction, analytics, and settings screens to Room-backed data. Analytics currently covers current-month income and consumption by category only.
- Added monthly category budgets scoped to all accounts or one bank account, with actual consumption spend, remaining/over-budget display, configurable 75/90/100% threshold indicators, and unused budget carry-forward from the immediately previous month when that month's matching budget enabled it. Thresholds are displayed in-app; Android notifications are not implemented.
- Added user-initiated transaction CSV export through Android's Storage Access Framework. The export includes dates, account, transaction type, title, amount in INR, category, wallet, description, notes, and linked-transfer ID. CSV quoting and paise formatting have a unit test.
- Added user-initiated import for MYFINANCE transaction CSVs through Android's Storage Access Framework. Import preview shows sample rows, existing duplicates, and validation errors before confirmation; it accepts only the app's own CSV format, validates paired transfers and account/wallet references, and commits new rows atomically. Matching linked transfer pairs are preserved; repeated imports skip exact existing rows. Raw bank statements still require review and conversion first.
- Added explicit investment contribution and withdrawal types to manual transaction entry and current-month analytics. Both update bank cash appropriately, stay out of salary/income and consumption totals, and do not infer investment market value.
- Added user-initiated SAF database backup/restore. Backups use a versioned ZIP manifest and SHA-256; restore validates the archive, SQLite integrity, required tables, and exact schema compatibility, creates a private safety copy, then replaces the database and closes the app for reopen. Backups are unencrypted; there is no in-app browser for private safety copies.
- Added transaction list search across titles/categories/descriptions/notes, plus account, type, and month filters. The combined filter logic has a unit test.
- Updated phone navigation to five destinations with distinct icons; Budgets and Settings are grouped under More. The add-transaction floating button is hidden on More/Settings, and the add form scrolls within a bounded height.

## Verified build results

- `gradlew.bat --version`: Gradle 8.13 launched on Temurin JDK 17.0.21.
- Gradle 8.13 `testDebugUnitTest`: passed on 2026-11-15 from an isolated temporary project copy using cached Temurin JDK 17.0.21; 15 tests, 0 failures (7 ledger-engine tests, 5 CSV export/import parsing and deduplication tests, 2 backup archive/checksum tests, and 1 combined transaction filter test). The OneDrive-hosted wrapper invocation could not fetch its distribution in the restricted shell; the cached Gradle installation was used directly. Room restore, repository import transactions, migrations, and UI workflows do not yet have automated integration coverage.
- `gradlew.bat lintDebug`: passed on 2026-11-14 with 22 warnings and no lint errors. Warnings include target SDK 36 not being the latest installed level, dependency/plugin age, KAPT usage, missing app icon, and data-extraction rules. Versions were not upgraded automatically.
- Gradle 8.13 `assembleDebug`: succeeded on 2026-11-15 from the isolated temporary project copy.
- APK: `delivery/MYFINANCE.apk` (updated 2026-11-15), package `com.myfinance.app`, version 1.0.0, min SDK 26, target SDK 36. The import flow is compiled into this build; no device install or UI flow test was performed this turn.
- `apksigner verify --verbose --print-certs`: verified using APK Signature Scheme v2 with the Android debug certificate. This is a development APK, not a production release.
- `aapt dump permissions`: confirmed `android.permission.INTERNET` is absent from the compiled APK. The merged manifest does include `ACCESS_NETWORK_STATE` and platform/library permissions such as `RECEIVE_BOOT_COMPLETED`; no banking or cloud service is implemented.
- APK signing, package name, min/target SDK, and size were checked. No device installation or instrumentation tests were run because ADB reported no attached device/emulator.
- Gradle was run from an unsynced temporary checkout because OneDrive file locking prevented KAPT from deleting generated files in the synchronized workspace. All edited source and deliverables are in this project; build outputs are delivered separately.

## Still not implemented end-to-end

- Recurring transaction scheduling, confirmation UI, missed-schedule handling, and duplicate-safe WorkManager posting; the data model exists but these workflows do not.
- Investment principal reconciliation and historical investment reports; the app records entered contributions/withdrawals but does not value investments.
- Transaction date editing and sorting; custom categories and account/category management.
- Full monthly/quarterly/yearly/custom reports, net worth, manual assets/liabilities, and historical snapshots.
- XLSX and budget/report exports.
- PIN/biometric app lock, Keystore-backed encryption, encrypted database, and a recovery design. Biometric libraries/platform permissions are present but no app lock workflow exists.
- Hidden balances, light/dark theme settings, threshold notification delivery, comprehensive Room migration and repository integration tests.
- Release signing and production APK validation.

The source and debug APK are not a validated production finance system. User-provided phone screenshots show the current major screens, but no device was attached to the 2026-11-14 build for install/export/backup/restore testing. Keep another verified record of important finances until the remaining items are implemented and tested.
