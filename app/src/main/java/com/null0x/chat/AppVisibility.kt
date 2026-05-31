package com.null0x.chat

object AppVisibility {
    @Volatile
    var isVisible: Boolean = false
        private set

    fun markVisible() {
        isVisible = true
    }

    fun markHidden() {
        isVisible = false
    }
}
