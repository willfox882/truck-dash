# Truck Dash — agent guide (READ THIS FIRST)

Android app (Kotlin + Compose + Room) for a 14.4" **portrait** head unit in a 2015 GMC Sierra 1500.
No OBD, no external hardware: only the unit's GPS, IMU and (later) low-speed body-bus broadcasts.

**Do not re-read the whole repo.** Workflow for every session:
0. Sandbox + screenshots exist: verify UI/logic changes there before touching a device (`docs/SANDBOX.md`).
1. Read `docs/PROGRESS.md` (state, next steps, known issues, decisions).
2. Read `docs/ARCHITECTURE.md` (file map — one line per file) and open only the files you need.
3. After changing anything: update `docs/PROGRESS.md` (and `ARCHITECTURE.md` if files were added/moved/renamed). Commit.

## Commands
```bash
export ANDROID_HOME=/home/user/android-sdk   # SDK location in the cloud sandbox; local.properties has sdk.dir
./gradlew testDebugUnitTest :sandbox:test   # logic tests (shared core/engine + simulator scenarios)
./gradlew assembleDebug                    # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug
./gradlew :sandbox:screenshots             # headless PNGs of the real UI -> sandbox/build/screenshots/ (LOOK at them after UI changes)
./gradlew :sandbox:sandboxJar              # Windows-ready desktop simulator jar (see docs/SANDBOX.md)
```
Sandbox network must allow `dl.google.com` + `maven.google.com` for Gradle/AGP/SDK downloads.

## Conventions
- Package `com.truckdash`. minSdk 26, targetSdk 34, Kotlin 2.1, Compose BOM 2025.01.
- **Code in `shared/src/main/kotlin` is compiled by BOTH `:app` (Android) and `:sandbox` (desktop JVM)**: no `android.*`, Room, `androidx.lifecycle`, or `androidx.activity` imports there. Android-only things go in `app/` and are injected (e.g. `LocalBackHook`, `DashRepository`).
- **Put logic in `shared/.../core` or `engine` as pure Kotlin** (no Android imports) and unit-test it in `app/src/test/.../core`. Services/UI stay thin.
- Dark-only UI (`ui/Theme.kt` → `Dash` palette). Portrait only. Big touch targets (head unit).
- Safety: Trips/Health tabs lock above 3 mph unless Passenger mode (`ui/TruckDashRoot.kt`). Keep that rule for any dense UI.
- Room: schema exported to `app/schemas/`. Any entity change → bump `version` and add a migration (never destructive). v1→v2 (imperial→metric) lives in `data/Migrations.kt` and was verified against real SQLite; do the same for future ones.
- Version catalog: `gradle/libs.versions.toml`. Don't hard-code versions in `build.gradle.kts`.
- **Units (owner is in Canada — METRIC ONLY, never imperial):** speed km/h, distance km, altitude m, fuel litres and **L/100 km** (price $/L, cost per 100 km), oil in litres, **pressures in kPa** (never psi), temperature °C. Raw trip storage stays SI (metres, m/s); fuel/odometer/maintenance are stored in km / litres. `Geo` has the factors (`MPS_TO_KMH`, `M_TO_KM`). Any new feature (tire pressure, oil pressure, coolant temp…) must follow this.

## Key design facts
- `DriveEngine` (shared) is the brain: feed it GPS fixes + vehicle-frame g; it keeps `LiveState` and records trips through a `TripSink`. `DriveService` (Android) and `SimController` (sandbox) are just two feeders.
- UI talks only to `DashRepository` (Room impl in `app/data`, in-memory impl in `sandbox`). Screens are stateless; `*Presenter` classes hold UI state.
- Engine/recorder are single-threaded (main thread on Android); Room writes are serialised on a 1-thread dispatcher in `RoomTripSink`.
- Trailing idle (the 5 min before a trip auto-ends) is NOT part of the trip; mid-trip stops are.
- Odometer = user-entered base + sum(trip distance since base was set). There is no OBD odometer.
- G-forces: device -Z (into screen) is "forward"; mount tilt handled via gravity projection (`core/GForce.kt`); `mountYawDeg` setting exists for rotated mounts.
