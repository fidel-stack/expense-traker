# Expense Tracker (Android)

A privacy-focused, offline-first Android expense tracker for Kenyan users. Financial records are stored locally with Room; the app does not use accounts, cloud storage, analytics, or network APIs.

## Current scope

The current foundation includes:

- Kotlin, Jetpack Compose, Material 3, and five bottom-navigation destinations (Home, History, Add, Reports, Settings). Reports and Settings remain placeholders.
- Home dashboard with monthly income/expense summaries, category spending, recent transactions, and Uncategorized review indicators.
- Transaction history grouped by local calendar date, with debounced search (recipient, note, category, reference code) and combined date/category/type filters.
- Transaction details, editing (including source/reference preservation for imported records), and single-record deletion with confirmation.
- Manual expense and income entry in KES, including bank income recorded with source `manual` and no external reference. Amounts are entered with at most two decimals and stored as integer cents.
- Room persistence for transactions, categories, and recipient categorization rules.
- Integer KES minor-unit transaction amounts (`Long`), explicit expense/income/transfer types, and explicit M-Pesa/bank/manual sources.
- Thirteen seeded default categories and a normalized, unique recipient-rule key.
- Room schema export and an explicit version 1 → version 2 migration.

Category-management UI, reports, SMS access/parsing, and automatic categorization are not implemented. No SMS permissions or receivers are registered. The Room schema is unchanged from Stage 2 and remains at version 2.

## Requirements

- Android SDK Platform 34 (compile/target SDK)
- Android SDK Platform 26 or later to run the app
- JDK 17 or newer

## Build

From the repository root:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Tests

Run local JVM tests:

```bash
./gradlew testDebugUnitTest
```

Run Android instrumentation tests with an emulator or connected device:

```bash
./gradlew connectedDebugAndroidTest
```

The instrumentation suite covers transaction CRUD and integrity, category seeding, recipient rules, navigation, and migration. It requires an Android device/emulator.

## Database notes

The Room database is `ExpenseTrackerDatabase`, currently schema version 2. Schema snapshots are checked in under `app/schemas/`. The migration preserves Stage 1 transaction fields and stores the former text ID in `legacy_id`; old custom category names are retained. Legacy duplicate references are retained as rows, with duplicate codes cleared and flagged for review. The migration has no destructive fallback.

