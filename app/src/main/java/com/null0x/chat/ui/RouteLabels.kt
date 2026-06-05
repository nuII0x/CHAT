package com.null0x.chat.ui

fun maskedRouteLabel(value: String): String {
    val clean = value.trim()
    if (!clean.startsWith("onion:", ignoreCase = true)) return clean
    val route = clean.substringAfter(':')
    val separator = route.lastIndexOf(':')
    if (separator <= 0 || separator == route.lastIndex) return clean
    val host = route.substring(0, separator)
        .removeSuffix(".onion")
        .lowercase()
    val port = route.substring(separator + 1)
        .takeIf { candidate -> candidate.all { it.isDigit() } }
        ?: "5000"
    return "$host#$port"
}
