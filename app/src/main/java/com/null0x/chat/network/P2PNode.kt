package com.null0x.chat.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class P2PNode(
    private val tcpPort: Int = 5000
) {
    private companion object {
        private const val INCOMING_SOCKET_TIMEOUT_MS = 30_000
        private const val MAX_INCOMING_CIPHER_CHARS = 192 * 1024
        private const val MAX_INCOMING_PLAIN_CHARS = 64 * 1024
        private val ONION_HOST_REGEX = Regex("^[a-z2-7]{56}\\.onion$")
    }

    private var tcpJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var localUsername: String = ""
    @Volatile
    private var publicRoute: String = ""
    @Volatile
    private var serverReady: Boolean = false
    private var socksHost: String = "127.0.0.1"
    @Volatile
    private var socksPort: Int = 9050
    @Volatile
    private var socksEnabled: Boolean = false

    fun setTransportViaSocks(enabled: Boolean, host: String = "127.0.0.1", port: Int = 9050) {
        socksEnabled = enabled
        socksHost = host
        socksPort = port
    }

    fun setPublicRoute(route: String) {
        publicRoute = route.trim()
        if (publicRoute.isNotBlank()) {
            localUsername = publicRoute
        }
    }

    fun isServerReady(): Boolean = serverReady

    fun start(
        scope: CoroutineScope,
        onUsernameReady: (String) -> Unit,
        onMessage: (fromUsername: String, text: String) -> Unit,
        onPeersChanged: (List<String>, Map<String, String>) -> Unit
    ) {
        if (tcpJob?.isActive == true) return

        localUsername = publicRoute.ifBlank { "Aguardando rede..." }
        onUsernameReady(localUsername)
        onPeersChanged(emptyList(), emptyMap())

        tcpJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    ServerSocket(tcpPort, 50, InetAddress.getByName("127.0.0.1")).use { server ->
                        serverSocket = server
                        serverReady = true
                        while (isActive) {
                            val socket = server.accept()
                            launch {
                                runCatching {
                                    socket.use { handleIncomingSocket(it, onMessage) }
                                }
                            }
                        }
                    }
                } catch (_: SocketException) {
                    if (!isActive) break
                    serverReady = false
                    serverSocket = null
                    delay(750)
                } catch (_: Exception) {
                    serverReady = false
                    serverSocket = null
                    delay(750)
                } finally {
                    serverReady = false
                    serverSocket = null
                }
            }
        }
    }

    suspend fun sendMessage(toUsername: String, text: String): Result<Unit> {
        val endpoint = RouteEndpoint.parse(toUsername) ?: return Result.failure(
            IllegalArgumentException("Rota onion inválida")
        )
        return runCatching {
            withContextIo {
                if (!socksEnabled || socksPort !in 1..65535) {
                    throw IllegalStateException("Rede ainda nao esta pronta")
                }
                openSocks5Socket(endpoint).use { socket ->
                    val fromRoute = publicRoute.ifBlank {
                        throw IllegalStateException("Rota onion local ainda nao esta pronta")
                    }
                    val encrypted = SimpleCipher.encrypt("$fromRoute|$text")
                    socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(encrypted)
                        writer.newLine()
                        writer.flush()
                    }
                }
            }
        }
    }

    private fun openSocks5Socket(endpoint: RouteEndpoint): Socket {
        val socket = Socket()
        try {
            socket.soTimeout = 30_000
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(socksHost, socksPort), 30_000)
            socket.socks5Connect(endpoint)
            return socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    fun stop() {
        tcpJob?.cancel()
        tcpJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverReady = false
    }

    private fun handleIncomingSocket(
        socket: Socket,
        onMessage: (fromUsername: String, text: String) -> Unit
    ) {
        socket.soTimeout = INCOMING_SOCKET_TIMEOUT_MS
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        while (true) {
            val line = readBoundedLine(reader, MAX_INCOMING_CIPHER_CHARS) ?: break
            val plain = SimpleCipher.decrypt(line) ?: continue
            if (plain.length > MAX_INCOMING_PLAIN_CHARS) continue
            val separator = plain.indexOf('|')
            if (separator <= 0) continue
            val fromUser = plain.substring(0, separator)
            val message = plain.substring(separator + 1)
            if (RouteEndpoint.parse(fromUser) == null) continue
            onMessage(fromUser, message)
        }
    }

    private data class RouteEndpoint(val host: String, val port: Int) {
        companion object {
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

    private fun readBoundedLine(reader: BufferedReader, maxChars: Int): String? {
        val line = StringBuilder()
        while (true) {
            val value = reader.read()
            if (value < 0) {
                return if (line.isEmpty()) null else line.toString()
            }
            if (value == '\n'.code) {
                return line.toString().trimEnd('\r')
            }
            if (line.length >= maxChars) {
                throw IOException("Mensagem recebida excede o limite")
            }
            line.append(value.toChar())
        }
    }

    private fun Socket.socks5Connect(endpoint: RouteEndpoint) {
        val input = getInputStream()
        val output = getOutputStream()

        output.write(byteArrayOf(0x05, 0x01, 0x00))
        output.flush()
        val auth = input.readExactly(2)
        if (auth[0].toInt() != 0x05 || auth[1].toInt() != 0x00) {
            throw IOException("SOCKS5 sem autenticação não aceito")
        }

        val hostBytes = endpoint.host.toByteArray(Charsets.US_ASCII)
        if (hostBytes.size !in 1..255) {
            throw IOException("Endereço onion inválido para SOCKS5")
        }
        output.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
        output.write(hostBytes)
        output.write(byteArrayOf((endpoint.port shr 8).toByte(), endpoint.port.toByte()))
        output.flush()

        val header = input.readExactly(4)
        if (header[0].toInt() != 0x05) {
            throw IOException("Resposta SOCKS5 inválida")
        }
        if (header[1].toInt() != 0x00) {
            throw IOException("Falha SOCKS5: ${header[1].toInt() and 0xFF}")
        }
        when (header[3].toInt() and 0xFF) {
            0x01 -> input.readExactly(4)
            0x03 -> input.readExactly(input.readExactly(1)[0].toInt() and 0xFF)
            0x04 -> input.readExactly(16)
            else -> throw IOException("Tipo de endereço SOCKS5 inválido")
        }
        input.readExactly(2)
    }

    private fun InputStream.readExactly(size: Int): ByteArray {
        val output = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(output, offset, size - offset)
            if (read < 0) throw IOException("Resposta SOCKS5 incompleta")
            offset += read
        }
        return output
    }

    private suspend fun <T> withContextIo(block: () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }
}
