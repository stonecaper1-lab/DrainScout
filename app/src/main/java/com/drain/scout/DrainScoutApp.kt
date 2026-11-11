package com.drain.scout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import kotlinx.coroutines.flow.StateFlow

private enum class ReportTab(val title: String) { OVERVIEW("Overview"), WAKELOCKS("Wakelocks"), ALARMS("Alarms"), IDLE("Idle"), RAW("Report") }

@Composable
internal fun DrainScoutApp(
    stateFlow: StateFlow<DiagnosticsUiState>,
    onRefreshAccess: () -> Unit,
    onCapture: () -> Unit,
    onRequestShizuku: () -> Unit,
    onShare: (String) -> Unit
) {
    val state by stateFlow.collectAsState()
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { onRefreshAccess() }
    val snapshot = state.snapshot
    val report = remember(snapshot) { snapshot?.fullReport().orEmpty() }
    val findings = remember(snapshot) { snapshot?.let(DiagnosticEngine::findings).orEmpty() }

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(background = Color(0xFF07131B), onBackground = Color(0xFFE6F3FF))) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF07131B)) {
            Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DrainScout", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFE6F3FF))
                    Text("Battery drain investigation", color = Color(0xFF9CC8FF))
                    Text(if (state.hasPermission) "● Shizuku shell ready" else "○ Shizuku permission required", color = if (state.hasPermission) Color(0xFF8AE6C9) else Color(0xFFFFCF78))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!state.hasPermission) Button(onClick = onRequestShizuku) { Text("Connect Shizuku") }
                        Button(onClick = onCapture, enabled = !state.loading) { Text(if (state.loading) "Capturing…" else "Capture now") }
                        if (snapshot != null) Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy") }
                    }
                    state.error?.let { Text(it, color = Color(0xFFFF9A9A)) }
                    snapshot?.let { Text("Last capture: ${it.capturedAt}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9BB0BF)) }
                }
                val tabs = ReportTab.entries
                ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF07131B), contentColor = Color(0xFF8AE6C9)) {
                    tabs.forEachIndexed { index, tab -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(tab.title) }) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tabs[selectedTab]) {
                        ReportTab.OVERVIEW -> OverviewTab(state, findings, onShare = { if (report.isNotBlank()) onShare(report) })
                        ReportTab.WAKELOCKS -> DetailCard("Wakelocks & power", snapshot?.power ?: "Capture a report to inspect wake-lock output.", "A listed lock is evidence to investigate, not proof it caused measurable battery loss.")
                        ReportTab.ALARMS -> DetailCard("Alarms", snapshot?.alarms ?: "Capture a report to inspect scheduled alarms.", "Look for repeated wakeup alarms across comparable captures. A pending alarm alone does not show how often it fires.")
                        ReportTab.IDLE -> DetailCard("Device idle", snapshot?.deviceIdle ?: "Capture a report to inspect idle state.", "Idle state is only a point-in-time marker. Compare screen-off intervals and battery stats before drawing conclusions.")
                        ReportTab.RAW -> DetailCard("Diagnostic report", report.ifBlank { "Capture a report to see the full summarized output." }, "Copy or share from Overview. Output is device and Android-version dependent.")
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(state: DiagnosticsUiState, findings: List<Finding>, onShare: () -> Unit) {
    if (state.snapshot == null) {
        DetailCard("Start with a baseline", "Connect Shizuku and capture a snapshot. For useful comparisons, note battery percentage and time, keep the screen off for a repeatable interval, then capture again.", "No data is collected until you request a capture.")
        return
    }
    findings.forEach { finding ->
        val color = when (finding.level) { FindingLevel.LIKELY -> Color(0xFFFF9A9A); FindingLevel.WATCH -> Color(0xFFFFCF78); FindingLevel.INFO -> Color(0xFF8AE6C9) }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1E2A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(finding.title, color = color, style = MaterialTheme.typography.titleMedium)
                Text(finding.detail, color = Color(0xFFE6F3FF), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Button(onClick = onShare) { Text("Share full report") }
}

@Composable
private fun DetailCard(title: String, body: String, footnote: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1E2A)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color(0xFFE6F3FF))
            Text(body, color = Color(0xFFE6F3FF), style = MaterialTheme.typography.bodyMedium)
            Text(footnote, color = Color(0xFF9BB0BF), style = MaterialTheme.typography.bodySmall)
        }
    }
}
