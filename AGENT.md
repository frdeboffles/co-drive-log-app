# CO Drive Time Logger

> **For AI agents:** The canonical, detailed agent baseline lives in
> `.github/copilot-instructions.md`. OpenCode loads it automatically via
> `opencode.json`. It contains the full project structure, key file map,
> schema, navigation routes, service contract, state management patterns,
> and feature status. This file is a quick-reference summary only.
>
> **Keeping both files current:** Whenever you make a structural change
> (new file, new route, schema migration, completed feature, etc.) update
> **both** `.github/copilot-instructions.md` **and** this file in the same
> commit. See the "Keeping This File Current" section at the bottom of
> `copilot-instructions.md` for a checklist.

## Project Context
Android app for logging teen supervised driving hours per Colorado DMV
requirements (Form DR 2324). Sideloaded via dev mode, not published to
Play Store. Open-source on GitHub (MIT license).

## Stack
- Language: Kotlin
- UI: Jetpack Compose (Material 3)
- Min SDK: 34 (Android 14)
- Target SDK: 35
- Architecture: Single-module MVVM with Repository pattern
- Database: Room (SQLite) for local persistence
- DI: Hilt
- Build: Gradle with Kotlin DSL (build.gradle.kts)
- Testing: JUnit 5 + Turbine for Flow testing, Compose UI testing,
  MockK for mocks
- PDF export: Android built-in PdfDocument API (no 3rd party)

## Colorado DR 2324 Requirements
- Track: Date, Drive Time (hours:minutes), Night Driving Time
  (hours:minutes), Supervisor Initials, Comments (optional)
- Goal: 50 total hours, 10 of which at night
- Must produce a printable log showing grand totals
- Day/night classification: sunset/sunrise based on device location
  (use android.location + sunrise/sunset calculation)

## Key Features
1. Start/Stop drive timer with background service (foreground notification)
2. Auto-detect day vs night using GPS + sunrise/sunset calc
3. Manual drive entry (for retroactive logging)
4. Dashboard showing progress toward 50hr / 10hr-night goals
5. Export driving log as PDF matching DR 2324 format
6. Export as CSV for backup
7. Multiple supervisor support (name + initials)
8. Local-only data (no cloud, no account needed)

## Code Standards
- All public functions have KDoc
- Compose previews for every screen
- Repository pattern: DAO -> Repository -> ViewModel
- Coroutines + Flow for async
- No hardcoded strings (use strings.xml)

## Testing Requirements
- Unit tests for: ViewModel logic, Repository, day/night calculator,
  time formatting, PDF content generation
- Integration tests for: Room database (DAO queries)
- UI tests for: main dashboard, drive entry form, export flow
- Target: >80% line coverage on business logic
- Use fakes for Room in ViewModel tests, MockK where needed

## Commands
- `./gradlew assembleDebug` builds the APK
- `./gradlew testDebugUnitTest` runs unit tests
- `./gradlew connectedDebugAndroidTest` runs instrumented tests
- `./gradlew koverHtmlReportDebug` generates coverage report
