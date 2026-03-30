# CoDriveLog — Agent Instructions

This file is the authoritative baseline for AI agents working on this codebase.
**Keep this file up to date whenever you make structural changes** (see [Keeping This File Current](#keeping-this-file-current)).

---

## Project Summary

**CoDriveLog** is a local-only Android app that logs teen supervised driving hours to satisfy Colorado DMV Form DR 2324 requirements: 50 total hours, 10 of which must be at night. It is sideloaded via developer mode (not on Play Store). MIT-licensed, open source on GitHub.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2024.12.01) |
| Architecture | Single-module MVVM + Repository |
| Database | Room 2.6.1 (SQLite, local only) |
| DI | Hilt 2.52 |
| Navigation | Compose Navigation 2.8.4 |
| Async | Kotlin Coroutines + Flow 1.9.0 |
| Build | Gradle 8.9, Kotlin DSL, Version Catalog (`gradle/libs.versions.toml`) |
| Min / Target SDK | 34 / 35 (Android 14 / 15) |
| JVM target | 17 |
| Unit testing | JUnit 5 + Turbine 1.2.0 + MockK 1.13.12 |
| Instrumented testing | JUnit 4 + Espresso + Compose UI Test |
| Coverage | Kover 0.8.3 (target >80% on business logic) |
| PDF export | Android `PdfDocument` API (planned, not yet implemented) |

---

## Repository Layout

```
ColoradoTeenDriverLog/
├── .github/
│   └── copilot-instructions.md   # ← this file
├── AGENT.md                      # High-level project context (keep in sync)
├── README.md
├── build.gradle.kts              # Root build — plugin declarations only
├── settings.gradle.kts           # Project name + :app module
├── gradle.properties             # JVM args, AndroidX flags, room.schemaLocation
├── gradle/
│   └── libs.versions.toml        # Version Catalog
└── app/
    ├── build.gradle.kts          # App module config, Kover exclusions, deps
    ├── proguard-rules.pro
    ├── schemas/
    │   └── com.codrivelog.app.data.db.CoDriveLogDatabase/
    │       └── 1.json            # Exported Room schema v1
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   └── java/com/codrivelog/app/
        │       ├── CoDriveLogApplication.kt    # @HiltAndroidApp entry point
        │       ├── data/
        │       │   ├── db/                     # Room DAOs + Database class
        │       │   ├── model/                  # Room @Entity data classes
        │       │   └── repository/             # Repository singletons
        │       ├── di/                         # Hilt @Module classes
        │       ├── location/                   # GPS abstraction
        │       ├── service/                    # Foreground timer service
        │       ├── ui/                         # Compose screens + ViewModels
        │       └── util/                       # Pure-function utilities
        ├── test/                               # JVM unit tests
        └── androidTest/                        # Instrumented (device) tests
```

---

## Key Source Files

### Entry Points
| File | Role |
|---|---|
| `CoDriveLogApplication.kt` | `@HiltAndroidApp` — bootstraps Hilt |
| `ui/MainActivity.kt` | Single `ComponentActivity`; hosts Compose nav graph |
| `ui/NavHost.kt` | `Screen` sealed class + `NavHost` wiring |

### Data Layer
| File | Role |
|---|---|
| `data/model/DriveSession.kt` | `@Entity` for `drive_sessions` table |
| `data/model/Supervisor.kt` | `@Entity` for `supervisors` table |
| `data/db/CoDriveLogDatabase.kt` | `@Database` class, version 1 |
| `data/db/DriveSessionDao.kt` | CRUD + aggregate queries, returns `Flow` |
| `data/db/SupervisorDao.kt` | CRUD queries for supervisors, returns `Flow` |
| `data/db/DateTimeConverters.kt` | `@TypeConverter` — `LocalDate`/`LocalDateTime` ↔ ISO-8601 text |
| `data/repository/DriveSessionRepository.kt` | Thin `@Singleton` wrapping `DriveSessionDao` |
| `data/repository/SupervisorRepository.kt` | Thin `@Singleton` wrapping `SupervisorDao` |

### DI Modules
| File | Role |
|---|---|
| `di/DatabaseModule.kt` | Provides Room DB, both DAOs; `SingletonComponent` |
| `di/LocationModule.kt` | Binds `FusedLocationProvider` as `LocationProvider` |

### Foreground Service / Timer
| File | Role |
|---|---|
| `service/TimerState.kt` | Sealed interface: `Idle` / `Running` / `Saving` |
| `service/DriveTimerRepository.kt` | Process-scoped `@Singleton`; holds `MutableStateFlow<TimerState>`; bridges service ↔ UI |
| `service/DriveTimerService.kt` | `@AndroidEntryPoint` foreground service; 1-second tick loop; location poll every 60 s; persists session on stop |

### UI
| File | Role |
|---|---|
| `ui/theme/Theme.kt` | Material 3 theme, dynamic color (Android 12+) |
| `ui/dashboard/DashboardViewModel.kt` | Maps `DriveSessionRepository.getAll()` Flow → `DashboardUiState` |
| `ui/dashboard/DashboardScreen.kt` | Dashboard: progress cards, FAB, timer widget |
| `ui/timer/DriveTimerViewModel.kt` | Maps `TimerState` → `DriveTimerUiState`; sends intents to service |
| `ui/timer/DriveTimerWidget.kt` | Start form (supervisor fields) or active timer card |
| `ui/PlaceholderScreen.kt` | Stub for Entry and Export routes |

### Utilities
| File | Role |
|---|---|
| `util/ElapsedTimeFormatter.kt` | `formatHms(seconds)` → `"H:MM:SS"`, `formatHm(seconds)` → `"H:MM"` |
| `util/SunCalculator.kt` | NOAA USNO algorithm; UTC sunrise/sunset from lat/lng/date; handles polar edge cases |
| `util/NightMinutesCalculator.kt` | Splits `[start, end)` drive interval into night minutes; handles multi-day sessions |
| `location/LocationProvider.kt` | Interface returning `LatLng?` |
| `location/FusedLocationProvider.kt` | Uses `LocationManager`; tries GPS → network → passive |

---

## Database Schema (Room v1)

**DB name:** `co_drive_log.db`

### `drive_sessions`
| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `date` | TEXT NOT NULL | ISO-8601 `LocalDate` |
| `startTime` | TEXT NOT NULL | ISO-8601 `LocalDateTime` |
| `endTime` | TEXT NOT NULL | ISO-8601 `LocalDateTime` |
| `totalMinutes` | INTEGER NOT NULL | |
| `nightMinutes` | INTEGER NOT NULL | |
| `supervisorName` | TEXT NOT NULL | Denormalized from `supervisors` |
| `supervisorInitials` | TEXT NOT NULL | Denormalized from `supervisors` |
| `comments` | TEXT NULL | |
| `isManualEntry` | INTEGER NOT NULL | Boolean (0/1) |

### `supervisors`
| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `name` | TEXT NOT NULL | |
| `initials` | TEXT NOT NULL | |

Supervisor data is **denormalized** into each `drive_sessions` row; no foreign key constraint.

---

## Navigation Routes

| Route | Screen | Status |
|---|---|---|
| `"dashboard"` | `DashboardScreen` | Implemented |
| `"entry"` | `PlaceholderScreen` | Stub only |
| `"export"` | `PlaceholderScreen` | Stub only |

---

## Service Intent Contract

The UI communicates with `DriveTimerService` via `startForegroundService` intents:

| Action constant | Extras |
|---|---|
| `com.codrivelog.app.ACTION_START_DRIVE` | `supervisor_name` (String), `supervisor_initials` (String), `comments` (String?) |
| `com.codrivelog.app.ACTION_STOP_DRIVE` | *(none)* |

State flows back through `DriveTimerRepository.timerState: StateFlow<TimerState>`.

---

## State Management

Unidirectional data flow with Kotlin `StateFlow`:

```
Room DB  →  Repository  →  ViewModel (.stateIn())  →  Composable (.collectAsStateWithLifecycle())
DriveTimerService  →  DriveTimerRepository (MutableStateFlow)  →  DriveTimerViewModel  →  DriveTimerWidget
```

- Both ViewModels use `SharingStarted.WhileSubscribed(5_000)` — upstream stops 5 s after last subscriber.
- Sealed interfaces for all UI state types; all immutable.
- No `LiveData`, no Redux/MVI store.

---

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease         # ProGuard-minified

# Test
./gradlew testDebugUnitTest       # JVM unit tests (JUnit 5)
./gradlew connectedDebugAndroidTest  # Instrumented (device/emulator required)

# Coverage
./gradlew koverHtmlReportDebug    # HTML report in build/reports/kover/
```

---

## Testing Conventions

- **Unit tests** (`app/src/test/`) — JVM only, JUnit 5, no Android framework.
  - Use `FakeDriveSessionDao` / `FakeSupervisorDao` (in-memory `MutableStateFlow`) instead of mocking DAOs.
  - Use MockK for ViewModels and Android `Context`.
  - Use `runTest` + `UnconfinedTestDispatcher` + Turbine for Flow assertions.
- **Instrumented tests** (`app/src/androidTest/`) — JUnit 4, real Room with in-memory DB.
- Coverage target: >80% line coverage on business logic.
- Kover excludes: `*_HiltComponents*`, `*Hilt_*`, `*_Factory*`, `*Preview*`.

---

## Code Standards

- All public functions have KDoc.
- Every screen has a `@Preview` composable.
- No hardcoded strings — use `res/values/strings.xml`.
- Follow Repository pattern: DAO → Repository → ViewModel.
- Use Coroutines + Flow for all async; avoid `LiveData` and `RxJava`.

---

## Features Status

| Feature | Status |
|---|---|
| Live drive timer (foreground service) | Implemented |
| Auto day/night via GPS + NOAA algorithm | Implemented |
| Dashboard with DR 2324 progress | Implemented |
| Multiple supervisor support | Implemented (denormalized) |
| Manual retroactive drive entry | Stub (navigation route wired, screen is placeholder) |
| PDF export (DR 2324 format) | Not started |
| CSV export | Not started |

---

## Keeping This File Current

**Whenever you make a change that affects project structure or conventions, update both this file and `AGENT.md` in the same commit/PR.** Specifically:

- **New source file or package** — add it to the "Key Source Files" or "Repository Layout" section.
- **New navigation route** — update the "Navigation Routes" table.
- **Database migration** — bump the schema version note and update the "Database Schema" tables.
- **New intent action** — update the "Service Intent Contract" table.
- **New dependency or version bump** — update the "Tech Stack" table.
- **Feature completed** — flip its row in the "Features Status" table from stub/not-started to implemented.
- **New test convention** — update the "Testing Conventions" section.
- **New build command** — add it to "Build & Test Commands".

A good prompt to include in your PR description or commit message when updating this file:
> "Updated `.github/copilot-instructions.md` to reflect [what changed]."
