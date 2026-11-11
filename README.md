# DrainScout

Android battery-drain triage app with Shizuku-authorized shell collection. The app emphasizes evidence over guesses: capture a baseline, repeat after a controlled screen-off interval, and compare.

## Features
- Tabbed Overview, Wakelocks, Alarms, Idle, and Report views
- ViewModel + StateFlow UI state and background diagnostic collection
- Shizuku permission gate; no privileged dumpsys calls before explicit approval
- Battery stats, power/wakelock, alarm, and device-idle snapshots
- Heuristic findings with cautious language, plus copy/share report actions
- Bounded command duration/output to keep large device dumps manageable

## Build
JDK 17, Android SDK 34, Gradle 8.7+:

```sh
gradle :app:assembleDebug
gradle :app:assembleRelease
```

## Use
1. Start Shizuku using its setup for your device.
2. Open DrainScout and grant access.
3. Capture a baseline; note time and battery percentage.
4. Leave the screen off for a repeatable interval and capture again.
5. Compare the Wakelocks and Alarms tabs, and share the report if desired.

## Limitations
`dumpsys` formats and access vary by Android release and manufacturer. A single snapshot cannot prove root cause; alarm and wakelock matches are investigative clues, not battery attribution. Compare intervals and corroborate with the device's battery usage view or host ADB. DrainScout does not kill apps or change system settings. The UI and analysis are original and this project is not affiliated with BetterBatteryStats.
