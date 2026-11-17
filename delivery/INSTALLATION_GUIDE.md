# MYFINANCE Installation Guide

## Current delivery status

The delivery includes `MYFINANCE.apk`, a debug-signed APK for package `com.myfinance.app` (version 1.0.0, min SDK 26, target SDK 36). It is suitable for development/device review, not production release distribution.

To install with a connected Android device, enable developer options and USB debugging, then run `adb install -r delivery\MYFINANCE.apk` from a trusted development machine. No device installation was performed during this build.

## First-run working example

1. Open **Accounts** and set opening balances: HDFC ₹50,000, SBI ₹20,000, and Kotak ₹30,000.
2. On Kotak, add wallet entries to allocate ₹20,000 to Emergency Fund and ₹10,000 to Shift Allowance. These allocations reduce unallocated Kotak cash but do not change its physical bank balance.
3. Add an HDFC expense titled `Groceries`, category `Groceries`, for ₹1,250.50. The HDFC balance should become ₹48,749.50.
4. Open **More → Budgets**, add a current-month Groceries budget for all accounts of ₹5,000. It should show ₹1,250.50 spent and ₹3,749.50 remaining.
5. Open **Activity**, choose **Export transactions as CSV**, and save the file. The selected CSV contains the entered transaction and linked transfer rows, if any.
6. Open **More → Settings → Import MYFINANCE CSV…** and select a MYFINANCE-exported CSV. Review the preview; re-importing the file just exported should report its rows as already present and avoid adding them again.

The importer accepts MYFINANCE's own transaction CSV format. It does not ingest raw HDFC, Kotak, or SBI statement CSVs. Review and convert bank-statement rows to this format first; paired transfers need both matching sides. Make a backup before importing real financial history.

This is a usage example based on the implemented ledger paths. Build and unit tests passed, but the example was not exercised in an emulator because no AVD or device was available.

## Build from source

1. Use JDK 17 as the Gradle runtime. This project’s Gradle 8.13 wrapper cannot run on Java 25; `compileOptions`/Kotlin `jvmTarget=17` alone do not select Gradle’s runtime JDK.
2. Open the project in Android Studio with Android SDK Platform 36 and allow Gradle to resolve its configured plugins and dependencies.
3. Run `gradlew.bat testDebugUnitTest` and `gradlew.bat assembleDebug`.
4. The generated APK is at `app/build/outputs/apk/debug/app-debug.apk`.
5. Install with Android Studio or `adb install -r` from a trusted development machine.

The debug APK uses the standard Android debug signing key. No production signing key or release APK is included.
