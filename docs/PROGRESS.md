# Progress log

_Update this file at the end of every session. Newest entry on top of "Log"._

## Status: v0.2.0 — builds, tests pass (app + sandbox), lint clean. Desktop sandbox verified by screenshots. **Never run on a real truck/head unit yet.**

## Done
- **v0.2.0**: shared source set (`shared/`) compiled by app + desktop sandbox; `DriveEngine`/`TripRecorder` extracted from the service; `DashRepository`; stateless UI + presenters; Windows-runnable sandbox (`docs/SANDBOX.md`) with scenarios, manual drive, seeded history, headless screenshots
- Fixes found via sandbox screenshots: compass label overlap, readable durations, newest-first trips, trailing-idle excluded from trips, progress-bar stray dot, equal stat-tile heights, elevation chart noise (minSpan)
- Project scaffold (Gradle KTS, version catalog, wrapper 8.14.3, Compose, Room+KSP)
- `DriveService`: GPS + IMU → live state; automatic trip recording; orphan-trip recovery
- LIVE tab: speed gauge (0–120), compass rose, G-ball, trip/moving/avg/max/alt/grade tiles, clock, REC + GPS status
- TRIPS tab: list, 7d/month/all-time miles, 6-month bar chart, detail (route map w/ harsh-event dots, speed + elevation charts, stats, delete)
- HEALTH tab: GPS odometer (+ manual set), maintenance items (seeded defaults, Done/add/delete), fuel log, MPG/avg/cost-per-mile + trend, passenger-mode switch
- Speed lock on TRIPS/HEALTH above 3 mph

## Next (suggested order)
1. **Try the desktop sandbox first** (`docs/SANDBOX.md`), then **install on the head unit and field-test** (see `docs/INSTALL.md`). Check: GPS fix, speed vs. truck speedo, g-ball direction (accel = up, right turn = right), heading, REC start/stop.
2. Calibration UI for g-force mount (button: "level now" / yaw offset) — `Settings.mountYawDeg` exists but has no UI.
3. Body-bus intents: sniff reverse-gear / steering-wheel-button broadcasts (`adb logcat`, `dumpsys`), add a receiver; ideas: reverse → open Live; SWC buttons → cycle tabs / reset trip.
4. Trip UX: manual start/stop, "reset trip" button, CSV/GPX export, merge/split.
5. Maintenance: edit item, odometer-correction when GPS drifts, reminder notification.
6. Speed-limit overlay (OSM Overpass cache) — separate feature.
7. Room migration policy once schema changes (currently v1).

## Known issues / risks
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
- minSdk 26 so `java.time` works without desugaring.
- No icons library (keeps APK small); text tabs.
- Shared code via a plain `srcDir` (not KMP plugin) to keep Android build simple; shared UI uses `collectAsState` (not lifecycle-aware) and `LocalBackHook` for platform back handling.
- UI is laid out at a fixed 440 dp design width (`DesignScale`) because head-unit densities are unknown.

## Log
- **2026-11-15 (v0.2.0)** — Pushed v0.1.0 to GitHub (`willfox882/truck-dash`). Built the sandbox; Maven Central occasionally 429s (just retry). Skiko GL falls back to software under Xvfb (harmless).
- **2026-11-15** — v0.1.0 scaffold. Sandbox needed `dl.google.com` + `maven.google.com` allowed; Android SDK 35 installed in `/home/user/android-sdk`. GitHub token in the sandbox was invalid → repo not yet pushed.
