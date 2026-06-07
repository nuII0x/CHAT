package com.null0x.chat.ui.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun ProtectedWindowCapture(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val window = context.findActivity()?.window
        val wasAlreadySecure = window?.let { currentWindow ->
            currentWindow.attributes.flags.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        } ?: true

        if (window != null && enabled && !wasAlreadySecure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        onDispose {
            if (window != null && enabled && !wasAlreadySecure) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
