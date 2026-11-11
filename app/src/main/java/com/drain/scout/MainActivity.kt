package com.drain.scout

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var permissionRefresh: (() -> Unit)? = null
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { permissionRefresh?.invoke() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        setContent {
            val vm: DiagnosticsViewModel = viewModel()
            permissionRefresh = vm::refreshPermissionState
            DrainScoutTabs(vm)
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRefresh?.invoke()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(4107)
        } catch (_: Exception) { }
    }

    private fun shareReport(report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DrainScout diagnostic report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, "Share diagnostic report"))
    }
}
