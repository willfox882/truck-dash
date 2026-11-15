# Truck Dash — agent guide (READ THIS FIRST)

Android app (Kotlin + Compose + Room) for a 14.4" **portrait** head unit in a 2015 GMC Sierra 1500.
No OBD, no external hardware: only the unit's GPS, IMU and (later) low-speed body-bus broadcasts.

**Do not re-read the whole repo.** Workflow for every session:
1. Read `docs/PROGRESS.md` (state, next steps, known issues, decisions).
2. Read `docs/ARCHITECTURE.md` (file map — one line per file) and open only the files you need.
3. After changing anything: update `docs/PROGRESS.md` (and `ARCHITECTURE.md` if files were added/moved/renamed). Commit.

## Commands
```bash
export ANDROID_HOME=/home/user/android-sdk   # SDK location in the cloud sandbox; local.properties has sdk.dir
./gradlew testDebugUnitTest            # fast pure-Kotlin logic tests (core/)
./gradlew assembleDebug                # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug
```
Sandbox network must allow `dl.google.com` + `maven.google.com` for Gradle/AGP/SDK downloads.

## Conventions
- Package `com.truckdash`. minSdk 26, targetSdk 34, Kotlin 2.1, Compose BOM 2025.01.
- **Put logic in `core/` as pure Kotlin** (no Android imports) and unit-test it in `app/src/test/.../core`. Services/UI stay thin.
- Dark-only UI (`ui/Theme.kt` → `Dash` palette). Portrait only. Big touch targets (head unit).
- Safety: Trips/Health tabs lock above 3 mph unless Passenger mode (`ui/TruckDashRoot.kt`). Keep that rule for any dense UI.
- Room: schema exported to `app/schemas/`. Any entity change → bump `version` and add a migration (never destructive).
- Version catalog: `gradle/libs.versions.toml`. Don't hard-code versions in `build.gradle.kts`.
- Units are mph / miles / feet / gallons internally in UI; raw storage in metres & m/s (`Geo` has the factors).

## Key design facts
- One foreground service (`service/DriveService`) owns GPS + IMU + trip recording; UI reads `TelemetryHub` (StateFlow) and Room.
- Trip state is mutated on the main thread only; DB writes are serialised on a 1-thread dispatcher.
- Odometer = user-entered base + sum(trip distance since base was set). There is no OBD odometer.
- G-forces: device -Z (into screen) is "forward"; mount tilt handled via gravity projection (`core/GForce.kt`); `mountYawDeg` setting exists for rotated mounts.
