package com.tapconvert.app.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tapconvert.app.BuildConfig
import com.tapconvert.core.common.diagnostics.AppObservabilityRegistry

/**
 * Diagnostics & observability broadcast receiver for automated testing and local debugging.
 * Security guarantee: STRICTLY inactive in production/release builds (guarded by BuildConfig.DEBUG).
 * Returns sanitized telemetry with zero PII, masked filesystem paths, and bounded ring-buffer traces.
 */
class DiagnosticBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (!BuildConfig.DEBUG) {
            return
        }

        when (intent?.action) {
            ACTION_SNAPSHOT -> {
                val report = AppObservabilityRegistry.instance.exportSanitizedReportJson()
                Log.i(TAG, "Observability Snapshot:\n$report")
                resultData = report
            }
            ACTION_RESET -> {
                AppObservabilityRegistry.instance.resetForTesting()
                Log.i(TAG, "Observability Registry metrics reset.")
                resultData = "RESET_OK"
            }
        }
    }

    companion object {
        const val TAG = "TapConvertObservability"
        const val ACTION_SNAPSHOT = "com.tapconvert.app.DIAGNOSTICS_SNAPSHOT"
        const val ACTION_RESET = "com.tapconvert.app.DIAGNOSTICS_RESET"
    }
}
