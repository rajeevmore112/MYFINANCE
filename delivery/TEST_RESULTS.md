# MYFINANCE Test Results

Date: 2026-11-15

## Automated tests

Executed with Gradle 8.13 and Temurin JDK 17.0.21 from an isolated temporary checkout outside OneDrive.

- `testDebugUnitTest`: **passed** (15 tests, 0 failures: 7 ledger tests, 5 CSV formatting/import parser/deduplication tests, 2 backup archive/checksum tests, 1 transaction filter test).
- `assembleDebug`: **passed** for the updated CSV import workflow.
- `lintDebug`: last passed on 2026-11-14, with 22 warnings and no lint errors; it was not rerun for this update.

## Build and device validation

- Compiled APK package: `com.myfinance.app`; min SDK 26; target SDK 36.
- Updated APK size: 20,392,326 bytes (19.45 MiB).
- APK signature: verified with Android debug certificate using APK Signature Scheme v2.
- Compiled permission list: `android.permission.INTERNET` absent.
- Room schema JSON generated for database version 1.
- Instrumentation/device install: not run on 2026-11-14; no connected device or emulator was listed by ADB. User screenshots show the preceding app build running on a phone, but are not a test of this updated APK.

CSV tests verify paise conversion, quoting, valid and invalid transfer pairs, strict INR precision, and deduplication signatures. Backup unit tests cover archive round-trip and checksum rejection. Filter tests cover combined text/account/type/month criteria. Ledger tests cover investment withdrawals separately from salary income and consumption. Import repository transactions, duplicate preview behavior, repository edits, budget calculations, Room restore, migrations, and UI flows require additional integration/device tests.

## Static package checks

- Android manifest parsed as XML and was checked for the absence of `android.permission.INTERNET`.
- Source ZIP was opened and its entries checked for the app source, ledger test source, and delivery documents.
- These checks do not validate Kotlin compilation or application behavior.
