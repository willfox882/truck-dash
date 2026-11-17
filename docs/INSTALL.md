# Installing on the head unit

1. Get `app-debug.apk` (build: `./gradlew assembleDebug`, or download from the CI artifact).
2. Easiest: copy the APK to a USB stick, open it with the head unit's file manager, allow "install unknown apps".
   Or over Wi-Fi/USB: `adb connect <unit-ip>:5555` then `adb install -r app-debug.apk`.
3. Open **Truck Dash**, grant Location (**Allow all the time** if offered) and Notifications.
4. In the head unit's settings: add Truck Dash to **autostart** and disable battery optimisation for it.
5. Drive. A trip starts after ~5 s above 8 km/h and ends after 5 min stopped.

First-run checks: speed matches the truck speedo within ~1–3 km/h; accelerating moves the G-dot **up**, right turn moves it **right**.

## Signing key
`app/debug.keystore` (the standard Android debug key, alias `androiddebugkey`, password `android`) is committed and used for debug builds, so every build installs over the previous one without uninstalling (earlier APKs were signed with this same key). It is not a release secret.

## Cluster tab (optional, v0.6.0)
1. Pair the OBD dongle in the head unit's Bluetooth settings (PIN usually 1234 or 0000).
2. In Truck Dash: Health, switch on "Cluster tab". Nothing uses Bluetooth until you open the tab.
3. Open Cluster, allow the Bluetooth permission (Android 12+). If more than one device is paired, use Change next to "Dongle".
