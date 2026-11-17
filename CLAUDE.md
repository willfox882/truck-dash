# Truck Dash - agent guide (READ THIS FIRST)

Android app (Kotlin + Compose + Room) for a 14.4" **portrait** head unit in a 2015 GMC Sierra 1500.
No OBD, no external hardware: only the unit's GPS, IMU and (later) low-speed body-bus broadcasts.

**Do not re-read the whole repo.** Workflow for every session:
0. Sandbox + screenshots exist: verify UI/logic changes there before touching a device (`docs/SANDBOX.md`).
1. Read `docs/PROGRESS.md` (state, next steps, known issues, decisions).
2. Read `docs/ARCHITECTURE.md` (file map - one line per file) and open only the files you need.
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
- **Design language (premium, Apple / Wealthsimple feel). Keep it:**
  - Matte near-black (`Dash.bg` #0A0A0B), surfaces #131315, hairlines (#28282C) instead of boxes. No navy, no gradients on surfaces, no glow.
  - **Typeface: Geist** (static Light/Regular/Medium/SemiBold in `shared/res/font`, OFL). Big numerals use Light; labels are 11sp Medium UPPERCASE with letter spacing (`Label`). Never rely on the platform default font. Platform code sets `DashFonts.family`.
  - **Colour is meaning, not decoration.** White is the primary accent. Green = ok/live GPS, amber = caution, red = critical/recording. Everything else is greyscale (`text`, `dim`, `faint`). Do not add new hues.
  - Reuse `Components.kt` (`DashCard`, `StatGrid`, `Numeral`, `PillButton`, `TextAction`, `DashDialog`, `Hairline`, `Label`); lists are rows separated by hairlines, not stacked coloured cards.
  - **No em-dashes** anywhere (UI text, docs, commit messages, chat). Use commas, colons or the middle dot. Short units go beside the value, long ones in `StatItem.sub`.
  - Portrait only. Big touch targets (head unit). After any UI change run `:sandbox:screenshots` and look at every screen.
- **Split-screen panes (PaneSpec):** the app may run in a top/bottom half (about 440 x 391 dp) or a short strip. Every new screen must read `LocalPaneSpec` / `pane(full, compact, tiny)` (compact < 620 dp, tiny < 330 dp) and be checked in the sandbox at all three shapes (`pane-full/half/strip-*.png`, plus `PaneRenderTests`). Full-size layout must stay unchanged.
- Safety: the speed lock is OPTIONAL and OFF by default (owner decision v0.5.1): the "Driving lock" setting (Health tab, `Settings.drivingLock`) makes Trips/Health show "PARKED ONLY" above 5 km/h unless Passenger mode (`core/SpeedLock.kt`, used in `ui/TruckDashRoot.kt`). With it off everything is tappable at any speed. If it is on, keep that rule for any dense UI.
- Room: schema exported to `app/schemas/`. Any entity change → bump `version` and add a migration (never destructive). v1→v2 (imperial→metric) lives in `data/Migrations.kt` and was verified against real SQLite; do the same for future ones.
- Version catalog: `gradle/libs.versions.toml`. Don't hard-code versions in `build.gradle.kts`.
- **Units (owner is in Canada - METRIC ONLY, never imperial):** speed km/h, distance km, altitude m, fuel litres and **L/100 km** (price $/L, cost per 100 km), oil in litres, **pressures in kPa** (never psi), temperature °C. Raw trip storage stays SI (metres, m/s); fuel/odometer/maintenance are stored in km / litres. `Geo` has the factors (`MPS_TO_KMH`, `M_TO_KM`). Any new feature (tire pressure, oil pressure, coolant temp…) must follow this.

## Key design facts
- `DriveEngine` (shared) is the brain: feed it GPS fixes + vehicle-frame g; it keeps `LiveState` and records trips through a `TripSink`. `DriveService` (Android) and `SimController` (sandbox) are just two feeders.
- UI talks only to `DashRepository` (Room impl in `app/data`, in-memory impl in `sandbox`). Screens are stateless; `*Presenter` classes hold UI state.
- Engine/recorder are single-threaded (main thread on Android); Room writes are serialised on a 1-thread dispatcher in `RoomTripSink`.
- Trailing idle (the 5 min before a trip auto-ends) is NOT part of the trip; mid-trip stops are.
- Odometer = user-entered base + (lifetime GPS distance counter − counter value when base was set). The counter is fed by `DriveEngine.onDistance`, NOT derived from trip rows. There is no OBD odometer.
- Wall-clock timestamps come from GPS UTC (head units can boot with a wrong clock); durations/gaps use the monotonic clock.
- Start the service only via `DriveService.start()` (checks location permission; a location FGS without it crashes on Android 14+).
- G-forces: device -Z (into screen) is "forward"; mount tilt handled via gravity projection (`core/GForce.kt`); `mountYawDeg` setting exists for rotated mounts.
