# Architecture / file map

Modules: `:app` (Android), `:sandbox` (desktop JVM simulator). **`shared/src/main/kotlin/com/truckdash/`** is a plain
source dir compiled into both (via `srcDir`), so the UI/engine tested in the sandbox is byte-for-byte what ships.

```
 Android:  GPS+IMU -> service/DriveService ─┐                                  ┌-> ui/* (shared Compose screens)
 Sandbox:  sandbox/SimController ───────────┴-> engine/DriveEngine ─> LiveState┤
                                                   │ TripSink                 └-> repo/DashRepository
                                       Room (RoomTripSink) | MemoryStore            (RoomDashRepository | SandboxRepo)
```

## shared/.../core  (pure Kotlin; tests in `app/src/test/.../core/CoreTests.kt`)
| File | Purpose |
|---|---|
| `Geo.kt` | unit factors (m/s→km/h, m→km), haversine, compass label |
| `GForce.kt` | `Vec3`, `toVehicle(gravity, linear, yaw)` → `VehicleG` (long/lat/vert g in vehicle frame) |
| `HarshEventDetector.kt` | hard accel/brake/corner detection with per-type cooldown |
| `TripSegmenter.kt` | speed stream → START/END trip events (hold + idle timers) |
| `ElevationGrade.kt` | `ElevationAccumulator` (hysteresis climb), `GradeEstimator` (% grade) |
| `MaintenanceCalc.kt` / `FuelEconomy.kt` / `Analytics.kt` | maintenance status, fill-to-fill L/100 km, km per month etc. |

## shared/.../model + repo + engine  (tests: `app/src/test/.../engine/EngineTests.kt`)
| File | Purpose |
|---|---|
| `model/Models.kt` | plain data: Trip, TrackPoint, DriveEvent, TripDetail, FuelLog, MaintItem, Settings, LiveState |
| `repo/DashRepository.kt` | interface the UI uses (live state, settings, trips, odometer, maintenance, fuel + mutations) |
| `engine/TripSink.kt` | output interface of the recorder (tripStarted/pointsAdded/eventAdded/tripUpdated) |
| `engine/TripRecorder.kt` | trip state machine; holds stationary points back so trailing idle is dropped |
| `engine/DriveEngine.kt` | fixes + g samples in → `StateFlow<LiveState>` + trips out; g smoothing (120 ms τ); GPS-loss tick |

## shared/.../ui
| File | Purpose |
|---|---|
| `TruckDashRoot.kt` | tab shell, speed lock ("PARKED ONLY"), `DesignScale` (layout as 440 dp wide at any density), `LocalBackHook` |
| `Theme.kt`, `Format.kt` | `Dash` palette, `DashCard`, `StatTile`; duration formatting |
| `live/LiveScreen.kt`, `live/Gauges.kt` | portrait live layout; `SpeedGauge`, `CompassRose`, `GBall` (Canvas) |
| `trips/TripsScreen.kt`, `trips/TripsPresenter.kt` | list + monthly chart + detail (route, speed/elevation, events) |
| `health/HealthScreen.kt`, `health/HealthPresenter.kt` | odometer, maintenance, fuel (L/100 km), passenger-mode switch, dialogs |
| `charts/Charts.kt` | `LineChart`, `BarChart`, `RouteMap` |

## app/ (Android only)
| File | Purpose |
|---|---|
| `MainActivity.kt`, `TruckDashApplication.kt` | permissions, keep-awake, immersive, back hook; `AppGraph.init` |
| `service/DriveService.kt` | thin feeder: Location + Sensor callbacks → `DriveEngine`; notification; 1 s `tick` |
| `service/BootReceiver.kt` | best-effort autostart |
| `data/Entities.kt`, `Daos.kt`, `AppDatabase.kt` | Room (v2, schema in `app/schemas`) |
| `data/Mappers.kt`, `data/Migrations.kt` | entity ↔ model; Room v1→v2 (imperial→metric) migration |
| `data/RoomTripSink.kt`, `RoomDashRepository.kt` | engine output → Room; repository over Room + `SettingsStore` |
| `data/SettingsStore.kt`, `AppGraph.kt` | prefs-backed settings; service locator + first-run maintenance seeding |

## sandbox/ (desktop) — see `docs/SANDBOX.md`
`VehicleSim.kt` (physics + scenarios), `SimController.kt`, `MemoryStore.kt`, `Session.kt`, `SandboxApp.kt`, `Main.kt`, `Screenshots.kt`.
