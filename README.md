# CO Drive Log

Android app for logging teen supervised driving hours per Colorado DMV requirements (Form DR 2324).

Sideloaded via developer mode — not published to the Play Store. Open-source under the MIT license.

## Colorado Requirements

Colorado Revised Statute 42-2-106 requires teen drivers to complete **50 hours** of supervised driving
(at least **10 hours at night**) before obtaining a full license. This app tracks those hours and
exports a printable log matching the official **DR 2324** form.

| Requirement | Goal |
|---|---|
| Total supervised driving | 50 hours |
| Night driving (1 hour after sunset to 1 hour before sunrise) | 10 hours |
| Supervisor | Licensed adult 21+ |

## Features

- Start/stop drive timer with a foreground service notification
- Auto night-hours detection via GPS + NOAA sunrise/sunset calculation with Colorado DMV offsets (1 hour after sunset to 1 hour before sunrise)
- Manual night override toggle when GPS is unavailable
- Midnight-crossing drives handled correctly
- Manual retroactive drive entry with automatic night-minute calculation
- Dashboard showing progress toward 50-hour total / 10-hour night goals
- Export driving log as PDF matching DR 2324 column layout
- CSV export for spreadsheet backup
- Multiple supervisor support (name + initials)
- First-launch onboarding: student name + permit number + first supervisor
- Dashboard edit for student profile (name + permit)
- History edit/delete for each drive entry
- Route tracking for active timer drives (start, periodic, stop points)
- "View Route" action that opens recorded routes in Google Maps
- Fully local — no cloud, no account, no analytics

## Route Tracking, Privacy, and Battery

Route capture is enabled only for **active timer-based drives**.

- Manual entries do **not** capture route points
- Route points are sampled at drive start, during drive, and at drive stop
- Points are filtered by GPS accuracy and deduped to reduce noisy/redundant samples
- Route data is stored locally in the app database and is not uploaded to a server

Battery behavior:

- Sampling is intentionally low-frequency to reduce battery drain
- Existing location polling is reused rather than continuous high-rate tracking
- If GPS is unavailable, drive timing still works and route capture simply skips points

Viewing route data:

- In **Drive History**, sessions with route points show a **View Route** action
- View Route opens Google Maps directions using recorded start/end/waypoints

## Debug Mode Utilities

The app uses standard Android build variants:

- `debug` build: debug tools are enabled
- `release` build: debug tools are hidden

To run with debug tools:

```bash
./gradlew installDebug
```

In the **Drive History** screen, debug builds show:

- `Seed 100 entries`: creates 100 random sessions and randomly chooses one of the saved supervisors per entry
- `Clear all`: removes all stored drive sessions

## Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | Single-module MVVM + Repository |
| Database | Room (SQLite) |
| DI | Hilt |
| Prefs | Jetpack DataStore |
| Build | Gradle with Kotlin DSL + Version Catalog |
| Min SDK | 34 (Android 14) |
| Test | JUnit 5 + Turbine + MockK + Kover |

## Screenshots

| Dashboard | Active Drive | History | Export |
|---|---|---|---|
| _(placeholder)_ | _(placeholder)_ | _(placeholder)_ | _(placeholder)_ |

## Building

```bash
# Clone
git clone https://github.com/youruser/ColoradoTeenDriverLog.git
cd ColoradoTeenDriverLog

# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Build + install release APK via USB (adb)
./gradlew installRelease

# If multiple devices are connected, target one explicitly
./gradlew installRelease -Pandroid.injected.device.serial=<device-serial>

# Note: release build is debug-signed for local sideload convenience

# Unit tests + coverage
./gradlew testDebugUnitTest
./gradlew koverHtmlReportDebug
# Report: app/build/reports/kover/htmlDebug/index.html

# Instrumented tests (connected device or emulator required)
./gradlew connectedDebugAndroidTest
```

## GitHub Actions

This repository includes two workflows:

- `Android CI` (`.github/workflows/android-ci.yml`)
  - Runs on every PR and push to `main`
  - Builds debug APK, runs unit tests, and compiles release Kotlin sources

- `Release APK` (`.github/workflows/release-apk.yml`)
  - Runs when a GitHub Release is published
  - Builds a signed release APK and attaches it to the release

### Release signing secrets

Configure these repository secrets before publishing a release:

- `CDL_RELEASE_STORE_B64` — base64-encoded keystore file contents
- `CDL_RELEASE_STORE_PASSWORD` — keystore password
- `CDL_RELEASE_KEY_ALIAS` — key alias inside the keystore
- `CDL_RELEASE_KEY_PASSWORD` — key password

Note: local release builds still default to debug signing unless these env vars are provided.

## Sideloading to an Android Device

1. Enable **Developer Options** on the device (Settings → About Phone → tap Build Number 7 times).
2. Enable **USB Debugging** and/or **Install via USB** in Developer Options.
3. Connect the device via USB cable.
4. Build the APK you want to install:
   ```bash
   # Debug APK (includes debug/dev tools)
   ./gradlew assembleDebug

   # Release APK (no debug/dev tools)
   ./gradlew assembleRelease
   ```
5. Install it:
   ```bash
    # Debug install (single command build+install)
    ./gradlew installDebug

    # Release install (single command build+install)
    ./gradlew installRelease

    # If multiple devices/emulators are connected:
    ./gradlew installRelease -Pandroid.injected.device.serial=<device-serial>

    # or debug manual install:
    adb install app/build/outputs/apk/debug/app-debug.apk

    # or release manual install via adb:
    adb install -r app/build/outputs/apk/release/app-release.apk
    ```
6. Open **CO Drive Log** from the app drawer.

You can also transfer the APK file via USB/email and open it directly on the device
(you will be prompted to allow installs from unknown sources once).

## Project Structure

```
app/src/main/java/com/codrivelog/app/
├── data/           Room DB, DAOs, models, repositories
├── export/         CSV + PDF export logic
├── location/       GPS location provider (Hilt)
├── onboarding/     DataStore-backed onboarding repository + ViewModel
├── service/        Foreground drive timer service + shared state
├── ui/             Composable screens + ViewModels
│   ├── active/     Active drive screen
│   ├── dashboard/  Dashboard screen
│   ├── entry/      Manual entry screen
│   ├── export/     Export screen
│   ├── history/    Drive history screen
│   ├── supervisor/ Supervisor management screen
│   └── theme/      Material 3 theme + color scheme
└── util/           SunCalculator, NightMinutesCalculator, formatter
```

## License

This project is licensed under the MIT License — see the `LICENSE` file.
