# MYFINANCE — Android project source

MYFINANCE is intended to be a local-first, manual personal finance ledger. It does not integrate with banks, request internet access, or use a cloud backend. Monetary values are stored as integer paise.

## Project status

This is an incomplete development snapshot, not a production-ready finance application. The UI currently supports opening balances, manual income/expense entry, linked bank and wallet transfers, transaction editing/deletion, balance reconciliation, account balances, and a basic monthly category summary. Several requested features do not have complete user workflows, and the database is not encrypted.

Do not rely on this build as the only record of important finances. Monthly category budgets, transaction CSV export, explicit investment contribution/withdrawal entry, and versioned local backup/restore are implemented. Backups are unencrypted and the restore UI has not been exercised on device. XLSX export, app lock, recurring transaction posting, advanced analytics, and several transaction workflows remain unimplemented. See [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) and the [delivery documents](delivery/) for exact limits and environment results.

## Privacy

The manifest declares no `INTERNET` permission and disables Android backup. Transactions are manually entered. Room data is app-private but currently unencrypted.

## Build environment and instructions

The project includes a Gradle 8.13 wrapper. A debug APK was built and verified using a temporary JDK 17.0.21 because Gradle 8.13 cannot run on Java 25. Eleven unit tests passed; Android lint completed with 22 warnings. This APK is debug-signed, and the updated build was not installed on a device. See [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) and the delivery documents for details.

Select/install JDK 17 as the Gradle runtime, and use Android Studio with Android SDK Platform 36 and Build Tools. Run `gradlew.bat testDebugUnitTest` and `gradlew.bat assembleDebug`. See [INSTALLATION_GUIDE.md](delivery/INSTALLATION_GUIDE.md).

The configured project uses Android Gradle Plugin 8.13.2, Kotlin 2.2.20, Compose, and Room. Actual dependency and plugin compatibility still needs confirmation by a real Gradle build.
