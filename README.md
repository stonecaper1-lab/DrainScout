# DrainScout

A small Android app for investigating idle drain, wakelocks, and repeating alarms using Shizuku-authorized shell access.

## Included in this release
- tabbed overview, wakelocks, alarms, idle, and report views
- battery, power, alarm, and device-idle collection from `dumpsys`
- Shizuku permission gate before privileged commands run
- heuristic findings with careful wording instead of claiming certainty
- copy and share report actions
- comparison-friendly flow for repeated captures over the same screen-off interval

## Build
Requirements: JDK 17, Android SDK 34, Gradle 8.7+

```bash
gradle :app:assembleDebug
gradle :app:assembleRelease
```

## Use
1. Open Shizuku and grant Shell access to DrainScout.
2. Tap Capture.
3. Record battery percentage and time.
4. Leave the device idle for a consistent interval and capture again.
5. Compare Wakelocks and Alarms across captures.

## Limitations
`dumpsys` output differs by Android version and manufacturer. One capture is not enough to prove a cause. This tool is for triage, not for final attribution. It does not alter system settings or kill apps. It is not affiliated with BetterBatteryStats.
