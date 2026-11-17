# Installing on the head unit

1. Get `app-debug.apk` (build: `./gradlew assembleDebug`, or download from the CI artifact).
2. Easiest: copy the APK to a USB stick, open it with the head unit's file manager, allow "install unknown apps".
   Or over Wi-Fi/USB: `adb connect <unit-ip>:5555` then `adb install -r app-debug.apk`.
3. Open **Truck Dash**, grant Location (**Allow all the time** if offered) and Notifications.
4. In the head unit's settings: add Truck Dash to **autostart** and disable battery optimisation for it.
5. Drive. A trip starts after ~5 s above 8 km/h and ends after 5 min stopped.

First-run checks: speed matches the truck speedo within ~1–3 km/h; accelerating moves the G-dot **up**, right turn moves it **right**.

## Signing key
`app/debug.keystore` (the standard Android debug key, alias `androiddebugkey`, password `android`) is committed and used for debug builds. It is the same key the dev container's builds were signed with, so builds from the container and from this repo install over each other without uninstalling. It is not a release secret.

**Caution: CI APKs from before v0.6.0 were signed with a different key**, so installing a current build over one of those fails ("App not installed" / signature mismatch). The only way through is to uninstall first, and **uninstalling wipes all data** (trips, odometer, maintenance, fuel log: the app has `allowBackup=false`, so nothing is restored). Write down the odometer value (Health tab) and anything else you want to keep before uninstalling, then set the odometer again afterwards.

## Cluster tab (optional, v0.6.0, fixes in v0.6.1)
1. Pair the OBD dongle in the head unit's Bluetooth settings (PIN usually 1234 or 0000).
2. In Truck Dash: Health, switch on "Cluster tab". Nothing uses Bluetooth until you open the tab.
3. Open Cluster, allow the Bluetooth permission (Android 12+). If more than one device is paired, use Change next to "Dongle".
4. If you tapped "Don't allow", Retry asks again; when Android will not show the question any more, Retry opens the app's settings page instead (allow "Nearby devices" there, come back and tap Retry).
5. The dongle stays connected while the app is on screen (a reverse camera, volume or climate popup does not drop it) and disconnects when the app is stopped or you leave the Cluster tab. With the truck off, "Could not connect" shows within about 10 seconds.
