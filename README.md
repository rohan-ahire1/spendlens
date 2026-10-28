# Spend Lens

An Android app that reads your bank SMS and shows where your money goes. Everything stays on the phone. The app has no internet permission, no account and no server.

## Screens

- Home is a calendar of the month. Each day is shaded by how much you spent, and you can step back through older months. Below it, the month's categories are laid out as tiles.
- Day shows every payment on a date and the hour it happened.
- Category lists every payment in a category for this month, 3 months, a year or all time. You can filter by who you paid.
- Payment shows the account, reference number, balance after and how often you've paid that payee this month. You can change its category, add a note, hide it, or file that payee under a category for good.
- Activity has a 14-day chart and the full list, filterable by spent, received, UPI or card.
- Insights shows how much of your salary you keep, living cost by month, the biggest changes against your usual, a typical month by category, which days and times you spend most, small payments that add up, your top places and your subscriptions.
- Accounts splits the month's spend by account, and holds settings and CSV export.

Payments are sorted into groceries, food delivery, eating out, commute, fuel, shopping, bills, subscriptions and so on, using merchant names in the SMS. Money sent to family and credit card bill payments can be left out of the totals so nothing is counted twice.

## Download

Get the latest APK from [Releases](https://github.com/rohan-ahire1/spendlens/releases/latest). A new one is built every time `master` changes. You'll need to allow installs from unknown sources.

## Build

Needs JDK 17 and the Android SDK.

```bash
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. It is signed with the debug key because it's meant for sideloading. Android 8.0 (API 26) or newer.

Run the parser tests with:

```bash
./gradlew testDebugUnitTest
```

## Stack

Kotlin, Jetpack Compose and Material 3. There is no database: transactions are parsed from the SMS inbox on each scan, and your edits are kept in SharedPreferences.

## Fonts

IBM Plex Sans and Inter, both under the SIL Open Font License.
