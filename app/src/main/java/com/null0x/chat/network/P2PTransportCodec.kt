package com.null0x.chat.network

internal data class DecodedTransportMessage(
    val fromRoute: String,
    val text: String
)

internal object P2PTransportCodec {
    const val MAX_CIPHER_CHARS = 192 * 1024
    const val MAX_PLAIN_CHARS = 64 * 1024

    fun encode(fromRoute: String, text: String): String {
        val route = fromRoute.trim()
        require(RouteEndpoint.parse(route) != null) { "Rota onion local invalida" }
        require(text.length <= MAX_PLAIN_CHARS) { "Mensagem excede o limite" }
        return SimpleCipher.encrypt("$route|$text")
    }

    fun decode(line: String): DecodedTransportMessage? {
        if (line.length > MAX_CIPHER_CHARS) return null
        val plain = SimpleCipher.decrypt(line) ?: return null
        if (plain.length > MAX_PLAIN_CHARS) return null
        val separator = plain.indexOf('|')
        if (separator <= 0) return null
        val fromRoute = plain.substring(0, separator).trim()
        if (RouteEndpoint.parse(fromRoute) == null) return null
        return DecodedTransportMessage(
            fromRoute = fromRoute,
            text = plain.substring(separator + 1)
        )
    }
}

internal data class RouteEndpoint(val host: String, val port: Int) {
    fun maskedHost(): String {
        return "${host.take(6)}...${host.takeLast(6)}:$port"
    }

    companion object {
        private val ONION_HOST_REGEX = Regex("^[a-z2-7]{56}\\.onion$")

        fun parse(route: String): RouteEndpoint? {
            val clean = route.trim()
            if (!clean.startsWith("onion:", ignoreCase = true)) return null
            val value = clean.substringAfter(':')
            val separator = value.lastIndexOf(':')
            if (separator <= 0 || separator == value.lastIndex) return null
            val host = value.substring(0, separator).lowercase()
            val port = value.substring(separator + 1).toIntOrNull() ?: return null
            if (!ONION_HOST_REGEX.matches(host) || port !in 1..65535) return null
            return RouteEndpoint(host, port)
        }
    }
}
