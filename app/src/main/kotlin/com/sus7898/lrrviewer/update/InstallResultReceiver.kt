package com.sus7898.lrrviewer.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.sus7898.lrrviewer.App

/** Receives PackageInstaller session status for self-updates started by [UpdateManager]. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val updater = (context.applicationContext as? App)?.graph?.updater

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { updater?.onInstallResult(false, "설치 확인 화면을 열 수 없습니다: ${it.message}") }
                } else {
                    updater?.onInstallResult(false, "설치 확인 인텐트가 없습니다.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> updater?.onInstallResult(true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater?.onInstallResult(false, "사용자가 설치를 취소했습니다.")
            else -> updater?.onInstallResult(false, message ?: "status=$status")
        }
    }

    companion object {
        const val ACTION = "com.sus7898.lrrviewer.INSTALL_RESULT"
    }
}
