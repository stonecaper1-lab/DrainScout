package com.drain.scout

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.time.Instant
import java.util.concurrent.TimeUnit

internal data class DiagnosticSnapshot(
    val battery: String,
    val power: String,
    val alarms: String,
    val deviceIdle: String,
    val capturedAt: Instant
) {
    fun toReport(): String = buildString {
        appendLine("DrainScout report")
        appendLine("Captured: $capturedAt")
        appendLine("Access: Shizuku-authorized shell")
        appendLine("Interpretation: use this as a triage snapshot, not a final root cause verdict.")
        appendLine("\n== Battery ==\n${battery.ifBlank { "No output." }}")
        appendLine("\n== Power / wakelocks ==\n${power.ifBlank { "No output." }}")
        appendLine("\n== Alarms ==\n${alarms.ifBlank { "No output." }}")
        appendLine("\n== Device idle ==\n${deviceIdle.ifBlank { "No output." }}")
    }
}

internal data class Finding(val title: String, val detail: String, val severity: Severity)
internal enum class Severity { INFO, WATCH, LIKELY }

internal object DiagnosticEngine {
    fun hasShellPermission(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun collectSnapshot(): DiagnosticSnapshot {
        val battery = run("dumpsys", "batterystats", "--charged")
        val power = run("dumpsys", "power")
        val alarms = run("dumpsys", "alarm")
        val idle = run("dumpsys", "deviceidle")

        return DiagnosticSnapshot(
            battery = summarize(battery, listOf("Estimated power", "Uid", "wake", "screen", "Statistics since")),
            power = summarize(power, listOf("wake lock", "wakelock", "suspend blocker", "mWakefulness", "PARTIAL_WAKE_LOCK", "held")),
            alarms = summarize(alarms, listOf("Alarm Stats", "PendingIntent", "RTC_WAKEUP", "ELAPSED_REALTIME_WAKEUP", "whenElapsed")),
            deviceIdle = summarize(idle, listOf("mState=", "mLightState=", "IDLE", "mScreenOn", "mCharging")),
            capturedAt = Instant.now()
        )
    }

    fun summarizeFindings(snapshot: DiagnosticSnapshot): List<Finding> = buildList {
        val powerText = snapshot.power
        val alarmText = snapshot.alarms
        val idleText = snapshot.deviceIdle
        if (powerText.contains("wake", true) || powerText.contains("wakelock", true)) {
            add(Finding("Potential wake-lock activity", "Power output contains wake-related lines. Treat this as a signal to inspect the exact wakelock owner and its duration, not as proof of drain.", Severity.WATCH))
        } else {
            add(Finding("No obvious wake-lock signal", "The selected lines did not indicate matching wake-lock activity. Device-specific output may vary.", Severity.INFO))
        }
        if (alarmText.contains("Alarm Stats", true) || alarmText.contains("PendingIntent", true) || alarmText.contains("WAKEUP", true)) {
            add(Finding("Alarm traffic detected", "Alarm-related lines appear in the snapshot. Repeating alarms are a common idle drain pattern, especially when the screen is off.", Severity.WATCH))
        } else {
            add(Finding("No alarm signal matched", "No obvious alarm summary lines showed up in the filtered output. This may be a device-specific formatting issue.", Severity.INFO))
        }
        if (idleText.contains("IDLE", true) || idleText.contains("mState=", true)) {
            add(Finding("Idle state visible", "The device-idle dump returned idle markers, which helps confirm the capture happened during screen-off or low-activity conditions.", Severity.INFO))
        } else {
            add(Finding("Idle-state visibility weak", "No common idle markers showed up in the filtered output. This is often due to vendor formatting differences.", Severity.WATCH))
        }
        add(Finding("Best comparison method", "Capture two or more snapshots at the same battery level and duration with the screen off. Then compare the wake-lock and alarm output across intervals.", Severity.INFO))
    }

    private fun run(vararg args: String): String = try {
        val process = Shizuku.newProcess(args, null, null)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(25, TimeUnit.SECONDS)
        if (!finished) process.destroy()
        output
    } catch (e: Exception) {
        "Command unavailable: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun summarize(raw: String, patterns: List<String>): String {
        if (raw.isBlank()) return "No output returned."
        val selected = raw.lineSequence()
            .filter { line -> patterns.any { pattern -> line.contains(pattern, ignoreCase = true) } }
            .take(80)
            .toList()
        return if (selected.isEmpty()) {
            "No matching summary lines were selected. Device output may use different wording."
        } else {
            selected.joinToString("\n").take(12000)
        }
    }
}
