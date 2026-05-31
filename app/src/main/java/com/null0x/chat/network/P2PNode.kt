package com.null0x.chat.network

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap

class P2PNode(
    private val tcpPort: Int = 5000,
    private val udpPort: Int = 5001
) {
    private val peersLastSeen = ConcurrentHashMap<String, Long>()
    private val peerNames = ConcurrentHashMap<String, String>()
    private var tcpJob: Job? = null
    private var udpListenJob: Job? = null
    private var udpAnnounceJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var localUsername: String = ""
    @Volatile
    private var publicRoute: String = ""
    @Volatile
    private var displayName: String = "PrimoChat"
    private var socksHost: String = "127.0.0.1"
    @Volatile
    private var socksPort: Int = 9050
    @Volatile
    private var socksEnabled: Boolean = false

    fun setDisplayName(name: String) {
        displayName = name.trim().ifBlank { "PrimoChat" }
    }

    fun setTransportViaSocks(enabled: Boolean, host: String = "127.0.0.1", port: Int = 9050) {
        socksEnabled = enabled
        socksHost = host
        socksPort = port
    }

    fun setPublicRoute(route: String) {
        publicRoute = route.trim()
    }

    fun start(
        scope: CoroutineScope,
        onUsernameReady: (String) -> Unit,
        onMessage: (fromUsername: String, text: String) -> Unit,
        onPeersChanged: (List<String>, Map<String, String>) -> Unit
    ) {
        if (tcpJob?.isActive == true) return

        val localIp = getLocalIpv4() ?: "127.0.0.1"
        localUsername = UsernameCodec.encode(localIp, tcpPort) ?: return
        onUsernameReady(localUsername)

        tcpJob = scope.launch(Dispatchers.IO) {
            try {
                ServerSocket(tcpPort).use { server ->
                    serverSocket = server
                    while (isActive) {
                        val socket = server.accept()
                        launch {
                            socket.use { handleIncomingSocket(it, onMessage) }
                        }
                    }
                }
            } catch (_: SocketException) {
                // Expected on stop.
            } finally {
                serverSocket = null
            }
        }

        udpListenJob = scope.launch(Dispatchers.IO) {
            DatagramSocket(udpPort).use { socket ->
                socket.broadcast = true
                val buf = ByteArray(256)
                while (isActive) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val peer = parseHello(text) ?: continue
                    if (peer.username == localUsername) continue
                    peersLastSeen[peer.username] = System.currentTimeMillis()
                    if (peer.displayName.isNotBlank()) {
                        peerNames[peer.username] = peer.displayName
                    }
                    onPeersChanged(activePeers(), activePeerNames())
                }
            }
        }

        udpAnnounceJob = scope.launch(Dispatchers.IO) {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                while (isActive) {
                    val encodedName = Base64.encodeToString(
                        displayName.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                    val encodedRoute = Base64.encodeToString(
                        publicRoute.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                    val payload = "HELLO|$localUsername|$encodedName|$encodedRoute".toByteArray(Charsets.UTF_8)
                    sendBroadcast(socket, payload)
                    evictStalePeers(onPeersChanged)
                    delay(2500)
                }
            }
        }
    }

    suspend fun sendMessage(toUsername: String, text: String): Result<Unit> {
        val endpoint = RouteEndpoint.parse(toUsername) ?: return Result.failure(
            IllegalArgumentException("Usuario invalido")
        )
        return runCatching {
            withContextIo {
                if (endpoint.isOnion && (!socksEnabled || socksPort !in 1..65535)) {
                    throw IllegalStateException("Tor ainda nao esta pronto")
                }
                val socket = if (endpoint.isOnion) {
                    Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort)))
                } else {
                    Socket()
                }
                socket.soTimeout = 30_000
                val address = if (endpoint.isOnion) {
                    InetSocketAddress.createUnresolved(endpoint.host, endpoint.port)
                } else {
                    InetSocketAddress(endpoint.host, endpoint.port)
                }
                socket.connect(address, 30_000)
                socket.use {
                    val fromRoute = publicRoute.ifBlank { localUsername }
                    val encrypted = SimpleCipher.encrypt("$fromRoute|$text")
                    socket.getOutputStream().bufferedWriter().use { writer ->
                        writer.write(encrypted)
                        writer.newLine()
                        writer.flush()
                    }
                }
            }
        }
    }

    fun stop() {
        tcpJob?.cancel()
        udpListenJob?.cancel()
        udpAnnounceJob?.cancel()
        tcpJob = null
        udpListenJob = null
        udpAnnounceJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        peersLastSeen.clear()
        peerNames.clear()
    }

    private fun handleIncomingSocket(
        socket: Socket,
        onMessage: (fromUsername: String, text: String) -> Unit
    ) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        while (true) {
            val line = reader.readLine() ?: break
            val plain = SimpleCipher.decrypt(line) ?: continue
            if (plain.length > 64 * 1024) continue
            val separator = plain.indexOf('|')
            if (separator <= 0) continue
            val fromUser = plain.substring(0, separator)
            val message = plain.substring(separator + 1)
            if (RouteEndpoint.parse(fromUser) == null) continue
            onMessage(fromUser, message)
        }
    }

    private data class HelloPeer(val username: String, val displayName: String)

    private fun parseHello(text: String): HelloPeer? {
        val parts = text.split('|')
        if (parts.size !in 2..4 || parts[0] != "HELLO") return null
        val user = parts[1]
        if (UsernameCodec.decode(user) == null) return null
        val name = if (parts.size >= 3) {
            runCatching {
                String(Base64.decode(parts[2], Base64.DEFAULT), Charsets.UTF_8)
            }.getOrDefault("")
        } else {
            ""
        }
        return HelloPeer(user, name)
    }

    private data class RouteEndpoint(val host: String, val port: Int, val isOnion: Boolean) {
        companion object {
            fun parse(route: String): RouteEndpoint? {
                val clean = route.trim()
                if (clean.startsWith("onion:", ignoreCase = true)) {
                    val value = clean.substringAfter(':')
                    val separator = value.lastIndexOf(':')
                    if (separator <= 0 || separator == value.lastIndex) return null
                    val host = value.substring(0, separator).lowercase()
                    val port = value.substring(separator + 1).toIntOrNull() ?: return null
                    if (!host.endsWith(".onion") || port !in 1..65535) return null
                    return RouteEndpoint(host, port, isOnion = true)
                }
                val endpoint = UsernameCodec.decode(clean) ?: return null
                return RouteEndpoint(endpoint.ip, endpoint.port, isOnion = false)
            }
        }
    }

    private fun sendBroadcast(socket: DatagramSocket, payload: ByteArray) {
        val targets = mutableSetOf<InetAddress>()
        targets.add(InetAddress.getByName("255.255.255.255"))
        for (iface in networkInterfaces()) {
            if (!iface.isUp || iface.isLoopback) continue
            for (address in iface.interfaceAddresses) {
                val broadcast = address.broadcast ?: continue
                targets.add(broadcast)
            }
        }
        for (target in targets) {
            runCatching {
                val packet = DatagramPacket(payload, payload.size, target, udpPort)
                socket.send(packet)
            }
        }
    }

    private fun evictStalePeers(onPeersChanged: (List<String>, Map<String, String>) -> Unit) {
        val now = System.currentTimeMillis()
        val removed = mutableListOf<String>()
        val changed = peersLastSeen.entries.removeIf {
            val stale = now - it.value > 10_000
            if (stale) removed.add(it.key)
            stale
        }
        removed.forEach { peerNames.remove(it) }
        if (changed) onPeersChanged(activePeers(), activePeerNames())
    }

    private fun activePeers(): List<String> = peersLastSeen.keys.sorted()

    private fun activePeerNames(): Map<String, String> = peerNames.toMap()

    private fun getLocalIpv4(): String? {
        for (iface in networkInterfaces()) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in inetAddressesOf(iface)) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }

    private suspend fun <T> withContextIo(block: () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }

    private fun networkInterfaces(): List<NetworkInterface> {
        val out = mutableListOf<NetworkInterface>()
        val enumeration = NetworkInterface.getNetworkInterfaces() ?: return out
        while (enumeration.hasMoreElements()) {
            out.add(enumeration.nextElement())
        }
        return out
    }

    private fun inetAddressesOf(iface: NetworkInterface): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        val enumeration = iface.inetAddresses
        while (enumeration.hasMoreElements()) {
            out.add(enumeration.nextElement())
        }
        return out
    }
}
