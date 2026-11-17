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
| `PaneSpec.kt` | `PaneSpec(heightDp, compact, tiny)`, `PaneRules` (440 dp design width, compact < 620, tiny < 330, +/- 24 hysteresis), `PaneTracker`; tests in `PaneSpecTests.kt` |
| `SpeedLock.kt` | `LOCK_KMH` (5) + `SpeedLock.isLocked(speed, lockEnabled, passenger)`: pure rule for the optional Driving lock (default off); tests in `SpeedLockTests.kt` |
| `GForce.kt` | `Vec3`, `toVehicle(gravity, linear, yaw)` → `VehicleG` (long/lat/vert g in vehicle frame) |
| `HarshEventDetector.kt` | hard accel/brake/corner: must be sustained 300 ms, then re-arm below 70 % of threshold |
| `GravityTracker.kt` | accelerometer-only fallback: slow, steady-only gravity filter (sample-rate independent) |
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
| `engine/DriveEngine.kt` | fixes + g samples in → `StateFlow<LiveState>` + trips out; fix-quality gating; g smoothing (120 ms τ); 1 Hz `tick` (GPS-loss flag, ends trips on fix gaps); `onDistance` feeds the odometer counter |

## shared/.../obd  (pure Kotlin, Cluster tab logic; tests in `app/src/test/.../obd/`)
| File | Purpose |
|---|---|
| `ObdLink.kt` | `ObdLink` interface (open / write / readUntilPrompt with timeout / close), `ObdFailure`, `ObdLinkException`, `ObdTimeoutException`, `PairedDevice`, `exchange()` |
| `ObdProtocol.kt` | init sequence, PID table (0104, 0111, 010F, 221940, 0105), `parseByteA`, degC / percent conversion |
| `DeviceSelector.kt` | picks the dongle from bonded devices (saved address, else name hints) |
| `ClusterModels.kt` | `ClusterState`, `ClusterPhase`, `TransStage`, `GaugeSpec` + `ClusterGauges` (degC ranges and warn / critical points) |
| `ObdSession.kt` | connect, init, poll loop, 5 s reconnect, staged trans failsafe (`StateFlow<ClusterState>`) |
| `ClusterPresenter.kt` | runs the session (Mutex, one at a time) only while the tab is visible and the app active; permission ask / settings hand-off, dongle choice |
| `ClusterPlatform.kt` | what the platform supplies: `appActive`, permission (`hasPermission`, `canRequestPermission`, `requestPermission`, `openPermissionSettings`), `newLink`, `pairedDevices` |
| `ClusterText.kt` | failure titles / reasons and the status line strings |

## shared/.../ui
| File | Purpose |
|---|---|
| `Pane.kt` | `LocalPaneSpec` + `pane(full, compact, tiny)` helper (provided by `DesignScale`) |
| `TruckDashRoot.kt` | tab shell, optional speed lock ("PARKED ONLY", only when the Driving lock setting is on), `DesignScale` (layout as 440 dp wide at any density), `LocalBackHook` |
| `Theme.kt`, `Components.kt`, `Format.kt` | design tokens (`Dash`, `DashFonts`), theme; shared components (`DashCard`, `StatGrid`, `Numeral`, `PillButton`, `TextAction`, `DashDialog`, `Hairline`, `Label`); duration formatting |
| `live/LiveScreen.kt`, `live/Gauges.kt` | portrait live layout; `SpeedGauge`, `CompassRose`, `GBall` (Canvas, hairline style) |
| `trips/TripsScreen.kt`, `trips/TripsPresenter.kt` | list + monthly chart + detail (route, speed/elevation, events) |
| `health/HealthScreen.kt`, `health/HealthPresenter.kt` | odometer, maintenance, fuel (L/100 km), Driving lock + passenger-mode switches, dialogs |
| `cluster/ClusterScreen.kt`, `cluster/ClusterDial.kt` | Cluster tab: `ClusterScreen` (stateful) / `ClusterView` (stateless), failure screen, dongle picker; hairline `ClusterDial`, strip `ClusterReadout` |
| `charts/Charts.kt` | `LineChart`, `BarChart`, `RouteMap` |

## app/ (Android only)
| File | Purpose |
|---|---|
| `MainActivity.kt`, `TruckDashApplication.kt` | permissions, keep-awake, immersive, back hook; `AppGraph.init` |
| `service/DriveService.kt` | thin feeder: Location + Sensor callbacks → `DriveEngine`; notification; 1 s `tick` |
| `service/BootReceiver.kt` | best-effort autostart |
| `obd/BluetoothObdLink.kt` | RFCOMM `ObdLink` (SPP UUID, reflection channel-1 fallback, connect aborted by closing the socket on cancel, `available()` polling reads) |
| `obd/AndroidClusterPlatform.kt` | process-wide `ClusterPlatform` for Android: BLUETOOTH_CONNECT check / request / app-settings hand-off (launcher + rationale via the `Host` that `MainActivity` attaches), visible flag (onStart/onStop), bonded devices |
| `data/Entities.kt`, `Daos.kt`, `AppDatabase.kt` | Room (v2, schema in `app/schemas`) |
| `data/Mappers.kt`, `data/Migrations.kt` | entity ↔ model; Room v1→v2 (imperial→metric) migration |
| `data/RoomTripSink.kt`, `RoomDashRepository.kt` | engine output → Room; repository over Room + `SettingsStore` |
| `data/SettingsStore.kt`, `AppGraph.kt` | prefs-backed settings; service locator + first-run maintenance seeding + the process-wide `ClusterPresenter` / `AndroidClusterPlatform` |

## sandbox/ (desktop) - see `docs/SANDBOX.md`
`VehicleSim.kt` (physics + scenarios), `FakeDongle.kt` (simulated ELM327 + `SandboxClusterPlatform`), `ClusterPreview.kt` (Cluster states for shots / tests), `SimController.kt`, `MemoryStore.kt`, `Session.kt`, `SandboxApp.kt` (incl. pane-shape selector), `Main.kt`, `Screenshots.kt` (full + pane-* shots). Tests: `SimTests.kt`, `PaneRenderTests.kt` (every screen at full/half/strip), `ClusterSandboxTests.kt` (dongle scenarios through the real session).
