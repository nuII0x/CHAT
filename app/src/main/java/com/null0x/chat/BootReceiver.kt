package com.null0x.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Mantido propositalmente sem auto-start em background.
        // A inicializacao de rede/Tor ocorre apenas quando o usuario abre o app.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> Unit
        }
    }
}
