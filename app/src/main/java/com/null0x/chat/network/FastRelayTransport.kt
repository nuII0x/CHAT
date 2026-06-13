package com.null0x.chat.network

import android.content.Context
import android.util.Log
import com.null0x.chat.security.identity.RouteIdentityRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URI
import java.net.SocketTimeoutException
import java.util.UUID

object FastRelayTransport {
    private const val PREFS_NAME = "fast_relay_transport"
    private const val ENABLED_KEY = "enabled"
    private const val DISCOVERED_URL_KEY = "discovered_url"
    private const val DISCOVERY_PORT = 37020
    private const val DISCOVERY_MAGIC = "NULLCHAT_FAST_RELAY_V1"
    private const val CONNECT_TIMEOUT_MS = 2_500
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_PACKET_CHARS = 192 * 1024

    data class Config(
        val enabled: Boolean,
        val discoveredUrl: String
    ) {
        val active: Boolean
            get() {
                val host = runCatching { URI(discoveredUrl).host.orEmpty() }.getOrDefault("")
                return enabled && host.endsWith(".onion", ignoreCase = true)
            }
    }

    private val _config = MutableStateFlow(Config(enabled = false, discoveredUrl = ""))
    val config: StateFlow<Config> = _config.asStateFlow()
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var discoveryJob: Job? = null

    fun initialize(context: Context) {
        _config.value = readConfig(context)
        if (_config.value.enabled) {
            ensureDiscoveryLoop(context)
        }
    }

    fun setConfig(context: Context, enabled: Boolean, url: String = "") {
        val cleanUrl = normalizeBaseUrl(url)
        val currentUrl = readConfig(context).discoveredUrl
        val resolvedUrl = if (cleanUrl.isNotBlank()) cleanUrl else currentUrl
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, enabled)
            .putString(DISCOVERED_URL_KEY, resolvedUrl)
            .apply()
        _config.value = Config(enabled = enabled, discoveredUrl = resolvedUrl)
        if (enabled) {
            ensureDiscoveryLoop(context)
        } else {
            discoveryJob?.cancel()
            discoveryJob = null
        }
    }

    fun currentConfig(context: Context): Config {
        val current = _config.value
        if (current.active || current.discoveredUrl.isNotBlank()) return current
        return readConfig(context).also { _config.value = it }
    }

    suspend fun send(context: Context, toRoute: String, fromRoute: String, text: String): Result<Unit> {
        val config = currentConfig(context)
        if (!config.active) return Result.failure(IllegalStateException("Rota Tor desativada ou invalida"))
        if (toRoute.isBlank() || fromRoute.isBlank()) {
            return Result.failure(IllegalArgumentException("Rotas incompletas para rota Tor"))
        }
        val packet = SimpleCipher.encrypt("$fromRoute|$text")
        return runCatching {
            val body = JSONObject()
                .put("toRoute", toRoute)
                .put("packet", packet)
                .put("ttl", 10 * 60 * 1000L)
            postJson(
                config.discoveredUrl,
                "/v1/fast/send",
                body,
                authHeaders = signedHeaders("/v1/fast/send", body.toString())
            )
        }
    }

    suspend fun pull(context: Context, localRoute: String, limit: Int = 50): Result<List<String>> {
        val config = currentConfig(context)
        if (!config.active) return Result.success(emptyList())
        if (localRoute.isBlank()) return Result.success(emptyList())
        return runCatching {
            val body = JSONObject()
                .put("route", localRoute)
                .put("limit", limit.coerceIn(1, 100))
            val response = postJson(
                config.discoveredUrl,
                "/v1/fast/pull",
                body,
                authHeaders = signedHeaders("/v1/fast/pull", body.toString())
            )
            val packets = response.optJSONArray("packets") ?: JSONArray()
            buildList {
                for (index in 0 until packets.length()) {
                    val item = packets.optJSONObject(index) ?: continue
                    val packet = item.optString("packet").trim()
                    if (packet.isNotBlank() && packet.length <= MAX_PACKET_CHARS) {
                        add(packet)
                    }
                }
            }
        }
    }

    private fun readConfig(context: Context): Config {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Config(
            enabled = prefs.getBoolean(ENABLED_KEY, true),
            discoveredUrl = normalizeBaseUrl(prefs.getString(DISCOVERED_URL_KEY, null).orEmpty())
        )
    }

    private fun normalizeBaseUrl(raw: String): String {
        return raw.trim().trimEnd('/').takeIf { clean ->
            clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true)
        }.orEmpty()
    }

    fun noteDiscoveredRelay(context: Context, relayUrl: String) {
        val cleanUrl = normalizeBaseUrl(relayUrl)
        if (!isTorRelayUrl(cleanUrl)) return
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(DISCOVERED_URL_KEY, cleanUrl)
            .apply()
        _config.value = _config.value.copy(discoveredUrl = cleanUrl)
    }

    private fun ensureDiscoveryLoop(context: Context) {
        if (discoveryJob?.isActive == true) return
        val appContext = context.applicationContext
        discoveryJob = scope.launch {
            while (isActive) {
                try {
                    DatagramSocket(DISCOVERY_PORT, InetAddress.getByName("0.0.0.0")).use { socket ->
                        socket.reuseAddress = true
                        socket.broadcast = true
                        socket.soTimeout = 1_000
                        val buffer = ByteArray(2048)
                        while (isActive) {
                            val packet = DatagramPacket(buffer, buffer.size)
                            try {
                                socket.receive(packet)
                            } catch (_: SocketTimeoutException) {
                                continue
                            } catch (_: Exception) {
                                break
                            }
                            val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                            val discovered = parseDiscoveryPacket(text)
                            if (discovered != null) {
                                noteDiscoveredRelay(appContext, discovered)
                            }
                        }
                    }
                } catch (error: Throwable) {
            Log.w("FastRelayTransport", "descoberta da rota Tor falhou: ${error.message.orEmpty()}")
                }
                delay(1_500)
            }
        }
    }

    private fun parseDiscoveryPacket(packet: String): String? {
        val clean = packet.trim()
        if (!clean.startsWith(DISCOVERY_MAGIC + "|")) return null
        return clean.substringAfter('|').trim().takeIf { isTorRelayUrl(normalizeBaseUrl(it)) }
    }

    fun isTorRelayUrl(raw: String): Boolean {
        val host = runCatching { URI(raw).host.orEmpty() }.getOrDefault("")
        return host.endsWith(".onion", ignoreCase = true)
    }

    private fun signedHeaders(path: String, body: String): Map<String, String> {
        val identity = RouteIdentityRegistry.identityManager()
        if (!identity.isUnlocked()) {
            throw IllegalStateException("Identidade bloqueada")
        }
        return identity.buildSignedRequestHeaders(
            method = "POST",
            path = path,
            body = body,
            timestamp = System.currentTimeMillis(),
            nonce = UUID.randomUUID().toString()
        )
    }

    private fun torProxyIfNeeded(baseUrl: String): Proxy? {
        if (!isTorRelayUrl(baseUrl)) return null
        return Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress.createUnresolved(TorManager.socksHost(), TorManager.socksPort())
        )
    }

    private suspend fun postJson(baseUrl: String, path: String, body: JSONObject, authHeaders: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val proxy = torProxyIfNeeded(baseUrl)
            val url = URL(baseUrl + path)
            val connection = (if (proxy != null) url.openConnection(proxy) else url.openConnection()) as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                authHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            try {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val response = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
                if (status !in 200..299 || !response.optBoolean("ok", true)) {
                    throw IllegalStateException(response.optString("error").ifBlank { "Rota Tor retornou HTTP $status" })
                }
                response
            } finally {
                connection.disconnect()
            }
        }
}
