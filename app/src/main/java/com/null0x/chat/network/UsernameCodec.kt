package com.null0x.chat.network

data class Endpoint(val ip: String, val port: Int)

object UsernameCodec {
    private val key = byteArrayOf(
        0x2A.toByte(),
        0x57.toByte(),
        0x11.toByte(),
        0x6C.toByte(),
        0x3D.toByte(),
        0x4F.toByte()
    )

    fun encode(ip: String, port: Int): String? {
        val octets = ip.split('.')
        if (octets.size != 4 || port !in 1..65535) return null

        val raw = ByteArray(6)
        for (i in 0..3) {
            val value = octets[i].toIntOrNull() ?: return null
            if (value !in 0..255) return null
            raw[i] = value.toByte()
        }
        raw[4] = ((port shr 8) and 0xFF).toByte()
        raw[5] = (port and 0xFF).toByte()

        for (i in raw.indices) {
            raw[i] = (raw[i].toInt() xor key[i].toInt()).toByte()
        }

        val hex = raw.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return hex.chunked(4).joinToString(":")
    }

    fun decode(username: String): Endpoint? {
        val compact = username.lowercase().replace(":", "")
        if (compact.length != 12 || compact.any { it !in "0123456789abcdef" }) return null

        val data = ByteArray(6)
        for (i in data.indices) {
            val part = compact.substring(i * 2, i * 2 + 2)
            data[i] = part.toInt(16).toByte()
        }

        for (i in data.indices) {
            data[i] = (data[i].toInt() xor key[i].toInt()).toByte()
        }

        val ipParts = (0..3).map { data[it].toInt() and 0xFF }
        val ip = ipParts.joinToString(".")
        val port = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (port !in 1..65535) return null
        return Endpoint(ip, port)
    }
}
