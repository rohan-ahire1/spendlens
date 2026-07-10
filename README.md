# SpendLens

A local-first mobile expense tracker built around the moment a UPI payment is made. SpendLens can scan UPI QR codes, launch payment intents, record transaction details, categorize spending and show budget/analytics views without requiring a cloud account.

> SpendLens records and organizes payment activity; it is not a bank, payment processor or authoritative settlement source.

## Features

- UPI QR-code scanning
- manual recipient and amount entry
- Android UPI intent launching
- local transaction history
- automatic merchant/category suggestions
- editable transaction categories
- monthly budgets
- analytics by category, day and merchant
- current-week versus previous-week comparisons
- local SQLite persistence
- light/dark theme support
- Android build configuration through EAS

## How it works

```text
Scan QR or enter payment
          │
          ▼
Parse recipient and amount
          │
          ▼
Suggest spending category
          │
          ▼
Launch installed UPI application
          │
          ▼
Return to SpendLens
          │
          ▼
Record and review local transaction
          │
          ├── history
          ├── analytics
          └── budgets
```

A return from a UPI app does not always prove that money settled successfully. Treat app responses carefully and allow users to correct transaction status.

## Technology

- Expo 55
- React Native 0.83 and React 19
- TypeScript
- Expo Router
- Expo Camera
- Expo SQLite
- Expo Intent Launcher and Linking
- Expo Contacts and File System
- Zustand
- React Native Reanimated

## Requirements

- Node.js 20 or a version supported by Expo 55
- npm
- Expo Go or an Android development build
- Android device recommended for real UPI intent testing

The web target can render parts of the UI, but camera, installed-payment-app and Android intent behavior must be validated on Android.

## Installation

```bash
git clone https://github.com/rohan-ahire1/spendlens.git
cd spendlens
npm install
npm start
```

Then choose a target from the Expo terminal.

```bash
npm run android
npm run ios
npm run web
```

## Available commands

| Command | Purpose |
| --- | --- |
| `npm start` | Start the Expo development server |
| `npm run android` | Open the project on Android |
| `npm run ios` | Open the project on iOS |
| `npm run web` | Start the web version |

## Platform support

### Android

Android is the primary target because the app integrates with installed UPI applications through intents. Test with multiple payment apps because supported parameters and returned status values can differ.

### iOS

The UI and local expense-management features may run, but UPI app availability and deep-link behavior differ. Treat iOS payment launching as unverified until tested with supported applications.

### Web

Useful for basic UI development. Native camera, contacts, SQLite behavior and UPI intents require platform-specific validation.

## Data and privacy

SpendLens stores financial records locally in SQLite. No cloud account is required by the current architecture.

Potentially sensitive data includes:

- recipient names and UPI IDs
- transaction amounts and timestamps
- merchant/category history
- budgets
- contact information selected by the user

Recommended safeguards:

- request camera and contacts permissions only when needed
- explain why every permission is required
- avoid logging UPI identifiers or payment URLs
- add export/delete controls before public release
- encrypt or protect backups if records leave the device
- never store UPI PINs, bank credentials or OTPs

## Transaction status caveat

UPI intents can return inconsistent, incomplete or spoofable client-side results. A production financial tracker should not mark a payment as definitively successful solely because an external app returned control.

Recommended behavior:

- store `pending`, `completed`, `failed` and `cancelled` states explicitly
- show the user what status was inferred
- allow manual correction
- avoid presenting SpendLens totals as bank-verified balances
- reconcile against bank/SMS/account data only with clear permission and validation

## Local database

The app uses Expo SQLite for categories, transactions, budgets and supporting records. Database initialization and migrations should remain idempotent so upgrades do not duplicate seed data or lose user history.

Before changing the schema:

1. add a migration rather than editing existing production tables in place
2. test upgrading from the previous app version
3. verify seed categories are not recreated repeatedly
4. back up a fixture database and confirm data survives

## Categorization

Automatic categorization is a convenience layer based on merchant/recipient signals and seeded rules. It can be wrong.

- always allow category editing
- retain user corrections where possible
- avoid implying that inferred categories are bank-provided
- test ambiguous merchants and person-to-person payments

## Building with EAS

The repository contains EAS Android build configuration.

```bash
npx eas login
npx eas build --platform android
```

Review signing, package name (`com.spendlens.app`), permissions and release channel before distributing an APK or AAB.

## Suggested test matrix

- QR codes with and without amount/name fields
- malformed and non-UPI QR codes
- no installed UPI app
- user cancels payment
- payment app returns missing or unknown status
- duplicate payment attempts
- app killed while external payment app is open
- database upgrade with existing history
- category correction and repeated merchant learning
- monthly budget boundary and timezone changes
- denied camera/contacts permission
- Android light/dark mode

## Current status

SpendLens is an active prototype with payment, history, categorization, analytics and budget flows. It needs deeper automated testing, settlement-status handling, data export/deletion and multi-device backup decisions before production use.

## License

No license has been selected. Add a `LICENSE` file before distributing the application.