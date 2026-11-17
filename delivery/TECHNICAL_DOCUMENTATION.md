# MYFINANCE Technical Documentation

## Architecture in this snapshot

- Kotlin Android application with a Compose/Material 3 activity.
- A `FinanceViewModel` exposes Room-backed flows to the dashboard.
- The bottom navigation presents account balances, transaction history, a current-month category summary, category budgets, and a privacy/status panel. Quick-add supports manual income and ordinary expenses. Account screens provide linked bank transfers, Kotak wallet funding/spending, wallet-to-wallet transfers, and balance reconciliation.
- Navigation uses five bottom destinations: Home, Accounts, Activity, Analytics, and More. Budgets and Settings are reachable from More.
- Transactions can be edited (paired linked entries update atomically) and deleted (linked entries delete together).
- Transaction history supports searching title/category/description/notes and filtering by account, transaction type, and month.
- Budget cards compare posted consumption against monthly category limits, optionally scoped to an account. Thresholds are shown in the budget screen; eligible unused budget rolls into the next month.
- Transactions can be saved as CSV through Android's Storage Access Framework. The user chooses the destination; no data is transmitted.
- Versioned ZIP backups include a checkpointed SQLite snapshot and SHA-256 manifest. Restore validates integrity/schema and makes an app-private safety copy before replacing the database. Backups are unencrypted and restore needs device testing.
- Room entities and DAO provide a local SQLite schema; `FinanceRepository` wraps ledger writes in database transactions.
- `LedgerEngine` calculates physical bank and virtual wallet deltas using integer paise and checked `Long` addition.
- The manifest requests no `INTERNET` permission and disables Android backup.

## Accounting rules

- Bank transfers are represented as linked opposite entries and do not change consolidated bank cash.
- Wallet funding changes only wallet allocation; wallet spending decreases both wallet allocation and physical bank cash.
- Wallet-to-wallet transfers use linked opposite wallet entries and have zero physical bank impact.
- Investment contributions/withdrawals change bank cash but are not classified as consumption expenses or salary income.
- The transaction form supports explicit investment contribution and withdrawal entries; current-month analytics reports those two totals separately. No return or valuation is estimated.
- Wallet balances cannot go negative through repository funding/spending/transfer operations; wallet funding is limited by unallocated physical account cash.

## Current product limits

The UI exposes the dashboard, opening balances, income/expense/investment entry, linked bank and wallet transfers, account balances, transaction edit/delete, reconciliation, searchable/filterable transaction history, current-month category summary, monthly budgets, CSV export, and backup/restore controls. Recurring processing, full-period analytics/reports, historical snapshots, XLSX export, app lock, PIN settings, database encryption, comprehensive migration and repository integration tests, and production release signing are not complete. See `DEVELOPMENT_STATUS.md` for the current work record.

## Build configuration

Application ID: `com.myfinance.app`; minimum SDK: 26; compile SDK: 36. The app uses Android Gradle Plugin 8.13.2, Kotlin 2.2.20, Compose, and Room. The Gradle 8.13 build, unit tests, lint, APK packaging, package metadata, signature, and permission list were verified on 2026-11-10. No emulator/device was available for UI testing.
