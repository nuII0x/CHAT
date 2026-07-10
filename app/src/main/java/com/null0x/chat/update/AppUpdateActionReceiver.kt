package com.null0x.chat.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AppUpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppUpdateManager.ACTION_DOWNLOAD_UPDATE -> {
                AppUpdateManager.enqueueDownload(context.applicationContext)
            }
            AppUpdateManager.ACTION_INSTALL_UPDATE -> {
                AppUpdateManager.installDownloadedUpdate(context.applicationContext)
                    .onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: "Não foi possível instalar",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }
}
