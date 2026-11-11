package com.drain.scout

import android.content.pm.PackageManager
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

internal data class DiagnosticsUiState(
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val snapshot: DiagnosticSnapshot? = null,
    val error: String? = null
)

internal class DiagnosticsViewModel : ViewModel() {
    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    fun refreshPermissionState() {
        _state.value = _state.value.copy(hasPermission = DiagnosticEngine.hasShellPermission())
    }

    fun capture() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { DiagnosticEngine.collectSnapshot() }
                _state.value = _state.value.copy(loading = false, snapshot = snapshot, hasPermission = true)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message ?: "Could not capture diagnostics.")
            }
        }
    }
}

private enum class TabSpec(val title: String) { OVERVIEW("Overview"), WAKELOCKS("Wakelocks"), ALARMS("Alarms"), IDLE("Idle"), REPORT("Report") }

@Composable
internal fun DrainScoutTabs(vm: DiagnosticsViewModel) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var selected by remember { mutableIntStateOf(0) }
    val report = state.snapshot?.toReport().orEmpty()
    val findings = remember(state.snapshot) { state.snapshot?.let(DiagnosticEngine::summarizeFindings).orEmpty() }

    LaunchedEffect(Unit) { vm.refreshPermissionState() }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(background = Color(0xFF07131B), onBackground = Color(0xFFE6F3FF))
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF07131B)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DrainScout", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE6F3FF))
                    Text("Idle drain investigation", color = Color(0xFF9CC8FF))
                    Text(
                        if (state.hasPermission) "Shizuku shell ready" else "Shizuku permission required",
                        color = if (state.hasPermission) Color(0xFF8AE6C9) else Color(0xFFFFCF78)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.hasPermission) Button(onClick = { try { if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(4107) } catch (_: Exception) { } }) { Text("Connect Shizuku") }
                        Button(onClick = vm::capture, enabled = !state.loading) { Text(if (state.loading) "Capturing…" else "Capture") }
                        if (state.snapshot != null) Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy") }
                    }
                    state.error?.let { Text(it, color = Color(0xFFFFB4B4)) }
                    state.snapshot?.let { Text("Last capture: ${it.capturedAt}", color = Color(0xFF9BB0BF)) }
                }

                ScrollableTabRow(selectedTabIndex = selected, containerColor = Color(0xFF07131B), contentColor = Color(0xFF8AE6C9)) {
                    TabSpec.entries.forEachIndexed { index, tab ->
                        Tab(selected = selected == index, onClick = { selected = index }, text = { Text(tab.title) })
                    }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (TabSpec.entries[selected]) {
                        TabSpec.OVERVIEW -> OverviewTab(findings, report)
                        TabSpec.WAKELOCKS -> SectionCard("Wakelocks", state.snapshot?.power ?: "Capture a baseline to inspect wake-lock output.", "Wakelock lines are signals to investigate, not proof of drain.")
                        TabSpec.ALARMS -> SectionCard("Alarms", state.snapshot?.alarms ?: "Capture a baseline to inspect alarm output.", "Repeated alarms commonly correlate with battery drain, especially during screen-off periods.")
                        TabSpec.IDLE -> SectionCard("Device idle", state.snapshot?.deviceIdle ?: "Capture a baseline to inspect idle state.", "Device idle state is a point-in-time indicator. Use multiple captures over time for confidence.")
                        TabSpec.REPORT -> SectionCard("Full report", report.ifBlank { "Capture a report to inspect the full output." }, "Use multiple captures and compare battery level and duration before drawing conclusions.")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(findings: List<Finding>, report: String) {
    if (findings.isEmpty()) {
        SectionCard("No findings yet", "Capture a baseline first.", "Findings are generated from battery and power output.")
        return
    }
    findings.forEach { finding ->
        val color = when (finding.severity) {
            Severity.INFO -> Color(0xFF8AE6C9)
            Severity.WATCH -> Color(0xFFFFCF78)
            Severity.LIKELY -> Color(0xFFFF9A9A)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1E2A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(finding.title, color = color)
                Text(finding.detail, color = Color(0xFFE6F3FF))
            }
        }
    }
    Button(onClick = { /* share handled elsewhere */ }) { Text("Share full report") }
}

@Composable
private fun SectionCard(title: String, body: String, note: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1E2A)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color(0xFFE6F3FF))
            Text(body, color = Color(0xFFE6F3FF))
            Text(note, color = Color(0xFF9BB0BF), style = MaterialTheme.typography.bodySmall)
        }
    }
}
