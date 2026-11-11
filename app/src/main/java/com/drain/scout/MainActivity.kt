package com.drain.scout

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DrainScoutTheme {
                DrainScoutScreen(context = this)
            }
        }
    }
}

@Composable
private fun DrainScoutScreen(context: ComponentActivity) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Ready") }
    var report by remember { mutableStateOf("Tap “Capture idle stats” to collect a best-effort battery and wake-state report.") }
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF07131B)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "DrainScout",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFE6F3FF)
            )
            Text(
                text = "Idle drain triage using Shizuku + adb-style dump checks.",
                color = Color(0xFF9CC8FF)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    scope.launch {
                        status = "Collecting diagnostics…"
                        val output = withContext(Dispatchers.IO) { DiagnosticEngine.collect(context) }
                        report = output
                        status = "Diagnostics captured"
                    }
                }) {
                    Text("Capture idle stats")
                }

                Button(onClick = { clipboard.setText(AnnotatedString(report)) }, enabled = report.isNotBlank()) {
                    Text("Copy")
                }
            }

            Text(text = status, color = Color(0xFF8AE6C9))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1E2A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = report,
                        color = Color(0xFFE7F3FF)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrainScoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = Color(0xFF07131B),
            onBackground = Color(0xFFE6F3FF)
        ),
        content = content
    )
}

private object DiagnosticEngine {
    fun collect(context: ComponentActivity): String {
        val packageManager = context.packageManager
        val shizukuInstalled = hasPackage(packageManager, "moe.shizuku.privileged.api") ||
            hasPackage(packageManager, "rikka.shizuku") ||
            hasPackage(packageManager, "moe.shizuku.privileged")

        val adbVisible = try {
            exec(listOf("which", "adb"))
            true
        } catch (_: Exception) {
            false
        }

        val battery = exec(listOf("dumpsys", "batterystats", "--charged"))
        val alarms = exec(listOf("dumpsys", "alarm"))
        val wakefulness = exec(listOf("dumpsys", "wakefulness"))
        val window = exec(listOf("dumpsys", "window"))
        val broadcasts = exec(listOf("dumpsys", "activity", "broadcasts"))

        val suspects = buildList {
            if (battery.contains("wake", ignoreCase = true) || battery.contains("UID", ignoreCase = true)) {
                add("Battery stats show activity; check per-app wakes and background work.")
            }
            if (alarms.contains("Alarm", ignoreCase = true) || alarms.contains("PendingIntent", ignoreCase = true)) {
                add("Repeated alarms or pending intents are present; inspect scheduled jobs and sync loops.")
            }
            if (wakefulness.contains("mWakefulness", ignoreCase = true) || wakefulness.contains("Awake", ignoreCase = true)) {
                add("Wakefulness is active; inspect partial wakelocks and device wakeups.")
            }
            if (broadcasts.contains("Broadcast", ignoreCase = true) || broadcasts.contains("Receiver", ignoreCase = true)) {
                add("Broadcast traffic is active; inspect sticky or repeated receivers.")
            }
            if (window.contains("Window", ignoreCase = true) || window.contains("mWindowState", ignoreCase = true)) {
                add("The window manager shows state churn; investigate UI or service keep-awake paths.")
            }
        }

        val intro = buildString {
            appendLine("DrainScout report")
            appendLine("-----------------")
            appendLine("Shizuku app installed: ${if (shizukuInstalled) "yes" else "no"}")
            appendLine("adb binary visible: ${if (adbVisible) "yes" else "no"}")
            appendLine("Recommendation: open Shizuku and grant Shell access for the most reliable dumpsys data.")
            appendLine()
            appendLine("Likely drain suspects:")
            if (suspects.isEmpty()) {
                appendLine("- No obvious idle-drain signal was detected in the captured best-effort output.")
            } else {
                suspects.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Battery stats:")
            appendLine(trim(battery, 800))
            appendLine()
            appendLine("Alarm stats:")
            appendLine(trim(alarms, 700))
            appendLine()
            appendLine("Wakefulness:")
            appendLine(trim(wakefulness, 700))
            appendLine()
            appendLine("Broadcast stats:")
            appendLine(trim(broadcasts, 600))
            appendLine()
            appendLine("Window state:")
            appendLine(trim(window, 600))
        }

        return intro
    }

    private fun hasPackage(pm: PackageManager, packageName: String): Boolean = try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    private fun exec(command: List<String>): String = try {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.ifBlank { "No output returned." }
    } catch (_: Exception) {
        "Command unavailable in this shell context: ${command.joinToString(" ")}."
    }

    private fun trim(text: String, limit: Int): String {
        val value = text.trim().ifBlank { "No output returned." }
        return if (value.length > limit) value.take(limit) + "…" else value
    }
}
