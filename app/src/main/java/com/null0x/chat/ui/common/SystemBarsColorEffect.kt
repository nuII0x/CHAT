package com.null0x.chat.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.null0x.chat.MainActivity

@Composable
internal fun SystemBarsColorEffect(
    statusBarColor: Color,
    navigationBarColor: Color,
    statusBarDarkIcons: Boolean = statusBarColor.luminance() > 0.5f
) {
    val activity = LocalContext.current.findMainActivity()
    SideEffect {
        activity?.applyStatusBarColor(statusBarColor, statusBarDarkIcons)
        activity?.applyNavigationBarColor(navigationBarColor)
    }
}

private fun Context.findMainActivity(): MainActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is MainActivity) {
            return current
        }
        current = current.baseContext
    }
    return current as? MainActivity
}
