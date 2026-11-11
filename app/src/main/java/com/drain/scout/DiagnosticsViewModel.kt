package com.drain.scout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class DiagnosticsUiState(
    val loading: Boolean = false,
    val hasPermission: Boolean = false,
    val snapshot: DiagnosticSnapshot? = null,
    val error: String? = null
)

internal class DiagnosticsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = mutableState.asStateFlow()

    fun refreshAccess() {
        mutableState.value = mutableState.value.copy(hasPermission = DiagnosticEngine.hasShellPermission())
    }

    fun capture() {
        if (mutableState.value.loading) return
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { DiagnosticEngine.collect() } }
                .onSuccess { mutableState.value = mutableState.value.copy(loading = false, snapshot = it, hasPermission = true) }
                .onFailure { mutableState.value = mutableState.value.copy(loading = false, hasPermission = DiagnosticEngine.hasShellPermission(), error = it.message ?: "Could not capture diagnostics.") }
        }
    }
}
