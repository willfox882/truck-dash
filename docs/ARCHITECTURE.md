# Architecture / file map

Single module `:app`, package `com.truckdash`. Data flow:

```
GPS (LocationManager) ─┐
IMU (SensorManager) ───┼─> service/DriveService ─┬─> service/TelemetryHub (StateFlow<LiveState>) ─> ui/live/LiveScreen
                       │                         └─> Room (trips, points, events) ─> ui/trips, ui/health (odometer)
user input ────────────────────────────────────────> Room (fuel_logs, maintenance_items) / SettingsStore
```

## core/  (pure Kotlin, unit-tested — `app/src/test/java/com/truckdash/core/CoreTests.kt`)
| File | Purpose |
|---|---|
| `Geo.kt` | unit factors (m/s→mph, m→mi/ft), haversine, compass label |
| `GForce.kt` | `Vec3`, `toVehicle(gravity, linear, yaw)` → long/lat/vert g in vehicle frame |
| `HarshEventDetector.kt` | hard accel/brake/corner detection with per-type cooldown |
| `TripSegmenter.kt` | speed stream → START/END trip events (hold + idle timers) |
| `ElevationGrade.kt` | `ElevationAccumulator` (hysteresis climb), `GradeEstimator` (% grade) |
| `MaintenanceCalc.kt` | maintenance item → status (miles/days left, OK/SOON/OVERDUE) |
| `MpgCalc.kt` | fill-to-fill MPG, avg MPG, cost/mile |
| `Analytics.kt` | miles per month / since, avg moving speed |

## data/
| File | Purpose |
|---|---|
| `Entities.kt` | Room entities: Trip, Point, Event, FuelLog, MaintenanceItem |
| `Daos.kt` | `TripDao`, `FuelDao`, `MaintDao` |
| `AppDatabase.kt` | Room DB (v1, schema exported to `app/schemas`) |
| `SettingsStore.kt` | SharedPreferences-backed `Settings` StateFlow (odometer base, passenger mode, mount yaw) |
| `AppGraph.kt` | service locator (`db`, `settings`) + first-run seeding of default maintenance items |

## service/
| File | Purpose |
|---|---|
| `DriveService.kt` | foreground location service: GPS 1 Hz, IMU @GAME rate, trip lifecycle, harsh events, DB writes |
| `TelemetryHub.kt` | `LiveState` + singleton StateFlow bridge to UI |
| `BootReceiver.kt` | best-effort autostart on boot |

## ui/
| File | Purpose |
|---|---|
| `TruckDashRoot.kt` | tab shell (LIVE/TRIPS/HEALTH) + speed lock ("PARKED ONLY") |
| `Theme.kt` | `Dash` palette, `TruckDashTheme`, `DashCard`, `StatTile` |
| `live/LiveScreen.kt` | portrait live layout: gauge, compass, g-ball, stat tiles |
| `live/Gauges.kt` | `SpeedGauge`, `CompassRose`, `GBall`, `GReadout` (Canvas) |
| `trips/TripsViewModel.kt` | trips list, summaries, selected trip detail |
| `trips/TripsScreen.kt` | list + monthly bar chart + detail (route map, speed/elevation charts, events) |
| `health/HealthViewModel.kt` | odometer, maintenance, fuel, settings actions |
| `health/HealthScreen.kt` | odometer, maintenance cards, fuel log/MPG, passenger-mode switch, dialogs |
| `charts/Charts.kt` | `LineChart`, `BarChart`, `RouteMap` (Canvas) |

Root: `MainActivity.kt` (permissions, keep-screen-on, immersive), `TruckDashApplication.kt` (AppGraph.init).
