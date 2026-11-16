# Progress log

_Update this file at the end of every session. Newest entry on top of "Log"._

## Status: v0.3.1 (metric + audit fixes) — builds, tests pass (app + sandbox), lint clean. Desktop sandbox verified by screenshots. **Never run on a real truck/head unit yet.**

## Done
- **v0.3.1** (independent Fable code review, all claims verified against code): trips end when fixes stop (>120 s gap / ACC-off) and via 1 Hz tick; stationary-jitter threshold 0.8 m/s; odometer is now a persisted GPS-distance counter (independent of trip rows: mid-trip set, trip deletion and clock changes can't corrupt it); timestamps anchored to GPS UTC (wrong head-unit clock at boot); service start checks permission first + try/catch (Android 14+ crash), BootReceiver checks action, `allowBackup=false`; RoomTripSink can't crash the process, in-progress trip can't be deleted; time-based, steady-only gravity filter for the accelerometer fallback (`GravityTracker`); fix-quality gating (accuracy >40 m, impossible speed jumps); harsh events need 300 ms sustained + re-arm; first odometer entry rebases untouched maintenance items; speed lock survives GPS loss; points ordered by id.
- **v0.3.0**: everything metric for Canada — km/h, km, m, litres, L/100 km, $/L; Room v2 + verified data migration (miles→km, gallons→litres); odometer pref migrated; sandbox scenarios use 50 km/h streets / 110 km/h highway
- **v0.2.0**: shared source set (`shared/`) compiled by app + desktop sandbox; `DriveEngine`/`TripRecorder` extracted from the service; `DashRepository`; stateless UI + presenters; Windows-runnable sandbox (`docs/SANDBOX.md`) with scenarios, manual drive, seeded history, headless screenshots
- Fixes found via sandbox screenshots: compass label overlap, readable durations, newest-first trips, trailing-idle excluded from trips, progress-bar stray dot, equal stat-tile heights, elevation chart noise (minSpan)
- Project scaffold (Gradle KTS, version catalog, wrapper 8.14.3, Compose, Room+KSP)
- `DriveService`: GPS + IMU → live state; automatic trip recording; orphan-trip recovery
- LIVE tab: speed gauge (0–120), compass rose, G-ball, trip/moving/avg/max/alt/grade tiles, clock, REC + GPS status
- TRIPS tab: list, 7d/month/all-time km, 6-month bar chart, detail (route map w/ harsh-event dots, speed + elevation charts, stats, delete)
- HEALTH tab: GPS odometer (+ manual set), maintenance items (seeded defaults, Done/add/delete), fuel log, L/100 km avg/last/cost per 100 km + trend, passenger-mode switch
- Speed lock on TRIPS/HEALTH above 5 km/h

## Next (suggested order)
1. **Try the desktop sandbox first** (`docs/SANDBOX.md`), then **install on the head unit and field-test** (see `docs/INSTALL.md`). Check: GPS fix, speed vs. truck speedo, g-ball direction (accel = up, right turn = right), heading, REC start/stop.
2. Calibration UI for g-force mount (button: "level now" / yaw offset) — `Settings.mountYawDeg` exists but has no UI.
3. Body-bus intents: sniff reverse-gear / steering-wheel-button broadcasts (`adb logcat`, `dumpsys`), add a receiver; ideas: reverse → open Live; SWC buttons → cycle tabs / reset trip.
4. Trip UX: manual start/stop, "reset trip" button, CSV/GPX export, merge/split.
5. Maintenance: edit item, odometer-correction when GPS drifts, reminder notification.
6. Speed-limit overlay (OSM Overpass cache) — separate feature.
7. Room migration policy once schema changes (currently v1).

## Known issues / risks
- **Deferred from the review** (valid, not yet done): (a) auto-calibration of the IMU mounting — if the IMU is mounted flat/rotated vs the panel the G-ball reads 0 or wrong; `mountYawDeg` only fixes yaw; (b) `points` table grows ~1.3M rows/yr at 1 Hz — store every 2–3 s / on heading change, add (tripId,tMs) index, downsample route drawing; (c) distance across GPS gaps (tunnels) is under-counted (dt clamped to 5 s) — add haversine for 5–120 s gaps; (d) root composable recomposes at 10 Hz (split G into its own flow); (e) `max speed` could use a 3-sample median.
- Whether ACC-off suspends or hard-cuts power decides if the last ≤10 s of a trip survive; boot autostart can't start a location FGS on Android 14+ (use the ROM's autostart to launch the activity).
- Untested on hardware; sensor axis assumptions (screen faces rear, -Z = forward) may need `mountYawDeg`.
- GPS altitude is WGS84 ellipsoid (not MSL) on most devices → absolute altitude may be off by tens of metres; relative climb/grade fine.
- Head-unit ROMs may kill background services or block BOOT_COMPLETED → set app to autostart / "don't optimise battery" in the ROM.
- Odometer drifts vs. real odometer (GPS distance); re-set it occasionally.
- Trip start is delayed by `startHoldMs` (5 s); first ~40 m of a trip are not counted.
- Harsh-event thresholds (0.30 g accel / 0.35 g brake / 0.40 g corner) are guesses for a pickup.

## Decisions
- Native Kotlin + Compose, not cross-platform: need foreground service, sensors, canvas gauges at 60 fps.
- Distance by integrating Doppler speed × dt (more accurate than position haversine at 1 Hz).
- One merged app for Live/Trips/Health (shared GPS/IMU service + DB). Markets Board is a **separate** app (internet-dependent, must not affect the speedometer).
- Metric only (owner in Canada). Fuel economy is L/100 km (lower = better), not km/L.
- minSdk 26 so `java.time` works without desugaring.
- No icons library (keeps APK small); text tabs.
- Shared code via a plain `srcDir` (not KMP plugin) to keep Android build simple; shared UI uses `collectAsState` (not lifecycle-aware) and `LocalBackHook` for platform back handling.
- UI is laid out at a fixed 440 dp design width (`DesignScale`) because head-unit densities are unknown.

## Log
- **2026-11-16 (v0.3.0)** — Metric conversion. Fable code review done and top findings fixed (v0.3.1).
- **2026-11-15 (v0.2.0)** — Pushed v0.1.0 to GitHub (`willfox882/truck-dash`). Built the sandbox; Maven Central occasionally 429s (just retry). Skiko GL falls back to software under Xvfb (harmless).
- **2026-11-15** — v0.1.0 scaffold. Sandbox needed `dl.google.com` + `maven.google.com` allowed; Android SDK 35 installed in `/home/user/android-sdk`. GitHub token in the sandbox was invalid → repo not yet pushed.
