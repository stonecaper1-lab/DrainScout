package com.drain.scout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var refreshAccess: (() -> Unit)? = null
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshAccess?.invoke() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        setContent {
            val vm: DiagnosticsViewModel = viewModel()
            refreshAccess = vm::refreshAccess
            DrainScoutApp(stateFlow = vm.state, onRefreshAccess = vm::refreshAccess, onCapture = vm::capture, onRequestShizuku = ::requestShizuku, onShare = ::share)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccess?.invoke()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        refreshAccess = null
        super.onDestroy()
    }

    private fun requestShizuku() {
        try { if (Shizuku.pingBinder()) Shizuku.requestPermission(4107) } catch (_: Exception) { }
    }

    private fun share(report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DrainScout diagnostic report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, "Share diagnostic report"))
    }
}
