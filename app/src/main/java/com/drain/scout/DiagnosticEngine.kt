package com.drain.scout

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class DiagnosticSnapshot(
    val capturedAt: Instant,
    val battery: String,
    val power: String,
    val alarms: String,
    val deviceIdle: String,
    val commandNotes: List<String>
) {
    fun fullReport(): String = buildString {
        appendLine("DrainScout diagnostic report")
        appendLine("Captured: $capturedAt")
        appendLine("Access: Shizuku-authorized shell")
        appendLine("Interpretation is heuristic. Compare captures over a consistent screen-off interval.")
        appendLine("\n== Battery stats ==\n$battery")
        appendLine("\n== Power / wakelocks ==\n$power")
        appendLine("\n== Alarms ==\n$alarms")
        appendLine("\n== Device idle ==\n$deviceIdle")
        if (commandNotes.isNotEmpty()) appendLine("\n== Collection notes ==\n${commandNotes.joinToString("\n")}")
    }
}

internal enum class FindingLevel { INFO, WATCH, LIKELY }
internal data class Finding(val level: FindingLevel, val title: String, val detail: String)

internal object DiagnosticEngine {
    fun hasShellPermission(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun collect(): DiagnosticSnapshot {
        check(hasShellPermission()) { "Start Shizuku and grant DrainScout permission before collecting diagnostics." }
        val results = listOf(
            run("Battery stats", "dumpsys", "batterystats", "--charged"),
            run("Power / wakelocks", "dumpsys", "power"),
            run("Alarms", "dumpsys", "alarm"),
            run("Device idle", "dumpsys", "deviceidle")
        )
        return DiagnosticSnapshot(
            capturedAt = Instant.now(),
            battery = summarizeBattery(results[0].output),
            power = summarizePower(results[1].output),
            alarms = summarizeAlarms(results[2].output),
            deviceIdle = summarizeIdle(results[3].output),
            commandNotes = results.filter { it.exitCode != 0 }.map { "${it.label}: ${it.note ?: "command failed (exit ${it.exitCode})"}" }
        )
    }

    fun findings(snapshot: DiagnosticSnapshot): List<Finding> = buildList {
        val power = snapshot.power
        val alarms = snapshot.alarms
        val idle = snapshot.deviceIdle
        val wakeEntries = power.lineSequence().count { it.contains("wake", true) && it.contains(":") }
        val wakeDuration = Regex("(?i)([1-9][0-9]*(?:\\.[0-9]+)?\\s*(?:h|hr|hours|m|ms|s|sec))").findAll(power).count()
        if (power.contains("Permission not granted", true)) {
            add(Finding(FindingLevel.WATCH, "Shell access unavailable", "Start Shizuku and grant permission to collect system-level evidence."))
        } else if (wakeEntries > 0) {
            add(Finding(FindingLevel.WATCH, "Wakelock-related entries found", "$wakeEntries relevant power lines were selected. Verify owner and duration in the full device output; a listed lock alone does not prove drain."))
        } else {
            add(Finding(FindingLevel.INFO, "No wakelock summary matched", "Vendor output may use different labels. Inspect raw output or collect with host ADB."))
        }
        val alarmRows = alarms.lineSequence().count { looksLikeAlarmEntry(it) }
        if (alarmRows >= 8) add(Finding(FindingLevel.WATCH, "Many alarm lines in snapshot", "$alarmRows alarm-related lines matched. Compare two captures or inspect repeat intervals before attributing drain."))
        else add(Finding(FindingLevel.INFO, "Alarm snapshot captured", "$alarmRows alarm-related lines matched; one snapshot cannot establish alarm frequency."))
        if (idle.contains("IDLE", true)) add(Finding(FindingLevel.INFO, "Device idle state reported", "Idle markers were present. Check whether the phone spent the measurement interval in idle, not just at capture time."))
        else add(Finding(FindingLevel.WATCH, "Idle state unclear", "No common idle marker was selected. Device-specific output may differ."))
        if (wakeDuration > 0) add(Finding(FindingLevel.INFO, "Duration-like values present", "Found duration-formatted values in selected power output; validate each against its actual wakelock row."))
        add(Finding(FindingLevel.INFO, "Best next step", "Record time and battery level, leave the screen off for a repeatable interval, then capture again and compare deltas."))
    }

    private data class CommandResult(val label: String, val output: String, val exitCode: Int, val note: String? = null)

    private fun run(label: String, vararg args: String): CommandResult {
        val reader = Executors.newSingleThreadExecutor()
        return try {
            val process = Shizuku.newProcess(args, null, null)
            val outputFuture = reader.submit<String> {
                process.inputStream.bufferedReader().use { input ->
                    val out = StringBuilder()
                    var line: String?
                    while (input.readLine().also { line = it } != null && out.length < MAX_OUTPUT_CHARS) {
                        out.appendLine(line)
                    }
                    out.toString()
                }
            }
            val ended = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!ended) process.destroy()
            val output = runCatching { outputFuture.get(3, TimeUnit.SECONDS) }.getOrElse { "Output read failed or exceeded time limit." }
            if (ended) CommandResult(label, output, process.exitValue())
            else CommandResult(label, output, -1, "command timed out after ${COMMAND_TIMEOUT_SECONDS}s")
        } catch (e: Exception) {
            CommandResult(label, "Command unavailable: ${e.message ?: e.javaClass.simpleName}", -1, e.message)
        } finally {
            reader.shutdownNow()
        }
    }

    private fun summarizeBattery(raw: String): String = select(raw, listOf("Statistics since", "Estimated power", "Uid", "wake", "screen", "mobile radio", "wifi"), 60)
    private fun summarizePower(raw: String): String = select(raw, listOf("wake lock", "wakelock", "suspend blocker", "mWakefulness", "PARTIAL_WAKE_LOCK", "held"), 100)
    private fun summarizeAlarms(raw: String): String = select(raw, listOf("Alarm Stats", "PendingIntent", "whenElapsed", "RTC_WAKEUP", "ELAPSED_REALTIME_WAKEUP", "wakeup", "com."), 100)
    private fun summarizeIdle(raw: String): String = select(raw, listOf("mState=", "mLightState=", "IDLE", "mScreenOn", "mCharging", "deep idle", "light idle"), 60)

    private fun select(raw: String, patterns: List<String>, limit: Int): String {
        if (raw.startsWith("Command unavailable:") || raw.isBlank()) return raw.ifBlank { "No output returned." }
        val selected = raw.lineSequence().filter { line -> patterns.any { line.contains(it, true) } }.take(limit).toList()
        return if (selected.isEmpty()) "No common summary lines matched. Device output may use different wording; compare raw shell output." else selected.joinToString("\n").take(MAX_SECTION_CHARS)
    }

    private fun looksLikeAlarmEntry(line: String): Boolean =
        line.contains("PendingIntent", true) || line.contains("WAKEUP", true) || line.contains("whenElapsed", true)

    private const val COMMAND_TIMEOUT_SECONDS = 25L
    private const val MAX_OUTPUT_CHARS = 300_000
    private const val MAX_SECTION_CHARS = 12_000
}
