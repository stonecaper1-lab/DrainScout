# DrainScout

Tiny Android diagnostics app for idle drain, wakelocks, and battery anomalies.

## Purpose
DrainScout is a compact helper for Android debugging. It checks whether Shizuku or ADB-style shell access is available and then gathers basic `dumpsys` output to look for common idle-drain patterns such as:

- wakelocks
- repeated alarms
- pending broadcast activity
- background wakeups and battery stats anomalies

## Current scope
This is intentionally a small app focused on diagnosis and reporting, not a battery optimizer.

## How to use
1. Install the app on your device.
2. Open Shizuku and grant Shell access, if available.
3. Tap "Capture idle stats".
4. Review the report and copy it if needed.
5. If Shizuku is unavailable, use the app as a guide to collect the same commands via ADB or a rooted shell.

## Typical commands the app looks for
- `dumpsys batterystats --charged`
- `dumpsys alarm`
- `dumpsys wakefulness`
- `dumpsys activity broadcasts`
- `dumpsys window`

## Notes
- Real device-level diagnosis may still require root, ADB host access, or Shizuku shell permission.
- This app focuses on being a lightweight triage and reporting tool.
