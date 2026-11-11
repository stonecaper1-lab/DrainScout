package com.drain.scout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrainScoutApp()
        }
    }
}

@Composable
private fun DrainScoutApp() {
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Ready") }
    var reportText by remember { mutableStateOf("Tap the button to collect battery and wakelock information.") }
    val clipboard = LocalClipboardManager.current

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = Color(0xFF07131B),
            onBackground = Color(0xFFE6F3FF)
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07131B)),
            color = Color(0xFF07131B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "DrainScout",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFE6F3FF)
                )
                Text(
                    text = "Idle drain and wakelock triage for ADB + Shizuku sessions.",
                    color = Color(0xFF9CC8FF)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                statusText = "Collecting idle stats…"
                                val result = withContext(Dispatchers.IO) { DiagnosticsCollector.collect() }
                                reportText = result
                                statusText = if (result.contains("Shizuku") || result.contains("ADB")) "Diagnostics captured" else "Best-effort report"
                            }
                        }
                    ) {
                        Text("Capture idle stats")
                    }

                    Button(
                        onClick = { clipboard.setText(AnnotatedString(reportText)) },
                        enabled = reportText.isNotBlank()
                    ) {
                        Text("Copy")
                    }
                }

                Text(
                    text = statusText,
                    color = Color(0xFF9ED8C7)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1E2A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = reportText,
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFFE6F3FF)
                    )
                }
            }
        }
    }
}

private object DiagnosticsCollector {
    fun collect(): String {
        val shizukuStatus = detectShizuku()
        val adbStatus = detectAdb()
        val batteryLine = safeCommand(listOf("dumpsys", "batterystats", "--charged"))
        val alarmLine = safeCommand(listOf("dumpsys", "alarm"))
        val wakeLine = safeCommand(listOf("dumpsys", "wakefulness"))
        val windowLine = safeCommand(listOf("dumpsys", "window"))

        val suspects = mutableListOf<String>()
        if (batteryLine.contains("UID", ignoreCase = true) || batteryLine.contains("wake", ignoreCase = true)) {
            suspects += "Battery stats show activity; inspect per-app wakeups and background work."
        }
        if (alarmLine.contains("Alarm", ignoreCase = true) || alarmLine.contains("PendingIntent", ignoreCase = true)) {
            suspects += "Alarm traffic is present; inspect scheduled jobs and repeated alarms."
        }
        if (wakeLine.contains("mWakefulness", ignoreCase = true) || wakeLine.contains("Awake", ignoreCase = true)) {
            suspects += "Wakefulness remains active; watch for partial wakelocks and wakeups."
        }
        if (windowLine.contains("mWindowState", ignoreCase = true) || windowLine.contains("Window", ignoreCase = true)) {
            suspects += "Window state activity is visible; check for keep-awake UI or system services."
        }

        val report = buildString {
            appendLine("DrainScout report")
            appendLine("------------------")
            appendLine("Shizuku: $shizukuStatus")
            appendLine("ADB access: $adbStatus")
            appendLine()
            appendLine("Likely drain suspects:")
            if (suspects.isEmpty()) {
                appendLine("- No obvious idle-drain signal was detected in the best-effort output.")
            } else {
                suspects.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Battery stats:\n${trimOutput(batteryLine, 1000)}")
            appendLine("\nAlarm stats:\n${trimOutput(alarmLine, 800)}")
            appendLine("\nWakefulness:\n${trimOutput(wakeLine, 800)}")
            appendLine("\nWindow state:\n${trimOutput(windowLine, 600)}")
        }

        return report
    }

    private fun detectShizuku(): String {
        val isPresent = runCatching {
            val input = safeCommand(listOf("pm", "list", "packages"), verbose = false)
            input.contains("moe.shizuku.privileged.api", ignoreCase = true) ||
                input.contains("rikka.shizuku", ignoreCase = true)
        }.getOrDefault(false)

        return if (isPresent) {
            "Shizuku is available; shell-level diagnostics are likely to have better visibility."
        } else {
            "Shizuku not detected. Best-effort checks still run, but full wakelock inspection is stronger with Shizuku."
        }
    }

    private fun detectAdb(): String {
        val adbPresent = safeCommand(listOf("which", "adb"), verbose = false)
        return if (adbPresent.isBlank()) {
            "ADB binary not visible inside the current shell. adb host access may still be available from a connected PC."
        } else {
            "ADB is visible on-device; shell data can be cross-checked with host-side `adb shell dumpsys ...`."
        }
    }

    private fun safeCommand(args: List<String>, verbose: Boolean = true): String {
        return try {
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            if (verbose && exitCode != 0) {
                "Command failed (${exitCode})\n${output.take(250)}"
            } else {
                output
            }
        } catch (e: Exception) {
            "Shell access unavailable: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
        }
    }

    private fun trimOutput(text: String, limit: Int): String {
        val normalized = text.trim().ifEmpty { "No output returned." }
        return if (normalized.length > limit) normalized.take(limit) + "…" else normalized
    }
}
