# SpendLens

A local-first Android expense tracker that records your spending at the moment you pay with UPI.

Instead of reconstructing expenses from bank SMS later, SpendLens sits in front of the payment: scan a UPI QR code (or
enter a UPI ID), pick a category (usually suggested for you), pay through your installed UPI app, and the transaction
is saved on the device. History, budgets and analytics are built from those records. There is no account and no
server.

## Features

- **Scan and pay.** Camera QR scanner for UPI codes, or manual entry of a UPI ID and amount.
- **Launches your UPI app.** Hands the payment to PhonePe, Google Pay, Paytm, CRED or Amazon Pay through an Android
  intent, then reads the result the app returns (completed, failed, cancelled or unknown).
- **Automatic categorization.** Four strategies run in priority order: rules learned from your past corrections,
  known merchants, a seed list of common Indian merchants, and a weak time-of-day/amount heuristic as a
  tiebreaker. A confident match (0.7 or higher) is applied automatically; otherwise the best guess is pre-selected
  for you to confirm.
- **Learns from you.** Picking a category saves a rule, so the same merchant is categorized correctly next time.
- **Budgets.** Monthly limits per category with progress bars.
- **Analytics.** Spending by category, daily totals, top merchants, and this week against last week.
- **History** of every transaction with its status.
- **Light and dark themes** that follow the system setting.

## Tech stack

Expo SDK 55 · React Native 0.83 · React 19 · TypeScript · Expo Router · Expo Camera · Expo SQLite · Expo Intent
Launcher · Zustand · React Native Reanimated · EAS Build

## How it works

```
Scan QR / enter UPI ID ──► parse UPI URI ──► suggest category ──► launch UPI app (Android intent)
                                                                          │
                     history · budgets · analytics ◄── save to SQLite ◄── parse returned status
```

A few details worth calling out:

- **Signed QR codes survive the hand-off.** Merchant QR codes carry a base64 `sign` parameter. Android's intent parser
  turns `+` into a space, which breaks the signature, so `src/services/upi/intent.ts` encodes `+` as `%2B` in query
  values only before launching the payment app.
- **Money is stored in paise** (integers) to avoid floating-point rounding.
- **UPI results are treated as hints.** Different apps return status in different fields and formats;
  `src/services/upi/result.ts` normalizes them, and a missing or unclear status is stored as `unknown` rather than
  assumed successful.

```
src/
  app/                    Expo Router screens: tabs (home, history, analytics, settings), scan, pay, budget,
                          category prompt
  services/upi/           URI parser/builder, intent launcher, result parser
  services/categorization engine + strategies (user-learned, merchant lookup, seed match, time heuristic)
  db/                     SQLite client, schema, migrations, seed, repositories
  stores/                 Zustand state for the app and the payment flow
  components/             UI primitives, charts, transaction list item
  theme/                  Colour tokens and theme provider
```

## Run locally

Requires Node.js 20+ and an Android phone or emulator. Real payments need an Android device with a UPI app
installed; the web target only renders parts of the UI.

```bash
npm install
npm start           # Expo dev server
npm run android     # open on Android
```

No environment variables are needed. To build an installable APK with EAS (`eas.json` has a `preview` profile):

```bash
npx eas build --platform android --profile preview
```

## Status and privacy

SpendLens is a working prototype. All data (amounts, recipients, UPI IDs, categories) stays in a SQLite database on
the device. It never sees UPI PINs or bank credentials; those stay inside the UPI app. A status returned by a UPI app
is not proof that money settled, so totals are a personal record, not a bank statement.
