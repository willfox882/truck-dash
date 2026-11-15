# Truck Dash

Portrait dashboard for a 14.4" Android head unit in a 2015 GMC Sierra 1500. GPS + IMU only — no OBD, no extra hardware.

- **LIVE** — speed gauge, compass, G-force ball, trip stats, grade, altitude
- **TRIPS** — auto-recorded trips, route maps, speed/elevation charts, harsh-driving events, miles per month
- **HEALTH** — GPS odometer, maintenance reminders, fuel log with MPG and cost per mile

**Try it on a desktop:** the [sandbox](docs/SANDBOX.md) runs the real UI against a simulated truck (Windows: Java 17 + `run-sandbox.bat`).

Docs: [`CLAUDE.md`](CLAUDE.md) (start here) · [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · [`docs/PROGRESS.md`](docs/PROGRESS.md) · [`docs/INSTALL.md`](docs/INSTALL.md)

```bash
./gradlew testDebugUnitTest :sandbox:test assembleDebug :sandbox:sandboxJar
```
