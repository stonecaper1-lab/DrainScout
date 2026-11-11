# DrainScout

Tiny Android diagnostics app to catch idle drain and wakelock candidates.

## What it does
- checks whether the device has ADB or Shizuku available
- tries to collect battery, wake-lock, and alarm data from the device shell
- surfaces likely idle-drain suspects in a minimal UI
- makes it easy to copy the report for debugging or sharing

## What you need
- Android phone or emulator
- Shizuku installed and running with `Shell` permission for the best results
- optional ADB access from a host machine to compare shell output

## How to use
1. Install and open the app.
2. Start Shizuku and grant Shell access.
3. Tap "Capture idle stats".
4. Review the findings and copy the report.

## Typical checks the app runs
- `dumpsys batterystats --charged`
- `dumpsys batterystats` (best effort)
- `dumpsys alarm`
- `dumpsys window`
- `dumpsys activity broadcasts`
- `dumpsys wakefulness`

## Notes
- This is intentionally a small, debugging-focused app.
- On some devices, shell access is limited without Shizuku or a rooted/adb-enabled environment.
- This app is for analysis and debugging, not for production use as a battery optimizer.
