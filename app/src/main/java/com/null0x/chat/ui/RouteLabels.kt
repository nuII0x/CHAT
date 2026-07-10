package com.null0x.chat.ui

fun maskedRouteLabel(value: String): String {
    val clean = value.trim()
    if (!clean.startsWith("onion:", ignoreCase = true)) return clean
    val route = clean.substringAfter(':')
    val separator = route.lastIndexOf(':')
    val host = if (separator <= 0 || separator == route.lastIndex) {
        route
    } else {
        route.substring(0, separator)
    }
        .removeSuffix(".onion")
        .lowercase()
    return host
}
