# Installing on the head unit

1. Get `app-debug.apk` (build: `./gradlew assembleDebug`, or download from the CI artifact).
2. Easiest: copy the APK to a USB stick, open it with the head unit's file manager, allow "install unknown apps".
   Or over Wi-Fi/USB: `adb connect <unit-ip>:5555` then `adb install -r app-debug.apk`.
3. Open **Truck Dash**, grant Location (**Allow all the time** if offered) and Notifications.
4. In the head unit's settings: add Truck Dash to **autostart** and disable battery optimisation for it.
5. Drive. A trip starts after ~5 s above 5 mph and ends after 5 min stopped.

First-run checks: speed matches the truck speedo within ~1–2 mph; accelerating moves the G-dot **up**, right turn moves it **right**.
