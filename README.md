# CO Drive Log App

Android app for logging teen supervised driving hours per Colorado DMV requirements (Form DR 2324).

Sideloaded via developer mode — not published to the Play Store. Open-source under the MIT license.

## Features

- Start/stop drive timer with a foreground notification
- Auto day/night detection via GPS + sunrise/sunset calculation
- Manual retroactive drive entry
- Dashboard showing progress toward 50-hour total / 10-hour night goals
- Export driving log as PDF matching DR 2324 format
- CSV export for backup
- Multiple supervisor support (name + initials)
- Fully local — no cloud, no account required

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | Single-module MVVM + Repository |
| Database | Room (SQLite) |
| DI | Hilt |
| Build | Gradle with Kotlin DSL + Version Catalog |
| Min SDK | 34 (Android 14) |

## Building

```bash
# Debug APK
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (connected device/emulator required)
./gradlew connectedDebugAndroidTest

# Coverage report
./gradlew koverHtmlReportDebug
```

## License

MIT
