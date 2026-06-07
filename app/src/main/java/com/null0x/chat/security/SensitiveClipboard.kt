package com.null0x.chat.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

object SensitiveClipboard {
    private const val CLEAR_DELAY_MS = 60_000L
    private const val LEGACY_SENSITIVE_CLIP_KEY = "android.content.extra.IS_SENSITIVE"
    private val handler = Handler(Looper.getMainLooper())

    fun copy(context: Context, label: String, text: String) {
        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text).apply {
            description.extras = (description.extras ?: android.os.PersistableBundle()).apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                putBoolean(LEGACY_SENSITIVE_CLIP_KEY, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        handler.postDelayed({
            clearIfUnchanged(appContext, label, text)
        }, CLEAR_DELAY_MS)
    }

    private fun clearIfUnchanged(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val currentClip = clipboard.primaryClip ?: return
        if (currentClip.itemCount <= 0) return
        val currentItem = currentClip.getItemAt(0)?.coerceToText(context)?.toString() ?: return
        val currentLabel = currentClip.description?.label?.toString().orEmpty()
        if (currentLabel == label && currentItem == text) {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
