package com.null0x.chat

object AppBranding {
    const val APP_NAME = "NoChat"
    const val INTERNAL_HEX_CODE = "0x4E6F43686174"

    fun internalId(suffix: String): String {
        return "${INTERNAL_HEX_CODE.lowercase()}_$suffix"
    }
}
