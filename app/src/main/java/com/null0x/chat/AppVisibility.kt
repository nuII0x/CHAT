package com.null0x.chat

object AppVisibility {
    @Volatile
    var isVisible: Boolean = false
        private set

    @Volatile
    private var activeChatRoute: String = ""

    fun markVisible() {
        isVisible = true
    }

    fun markHidden() {
        isVisible = false
    }

    fun markChatOpen(route: String) {
        activeChatRoute = route.trim()
    }

    fun markChatClosed(route: String = "") {
        val cleanRoute = route.trim()
        if (cleanRoute.isBlank() || activeChatRoute == cleanRoute) {
            activeChatRoute = ""
        }
    }

    fun isChatOpen(route: String): Boolean {
        return isVisible && activeChatRoute == route.trim()
    }
}
