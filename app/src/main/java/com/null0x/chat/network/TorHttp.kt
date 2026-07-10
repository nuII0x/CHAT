package com.null0x.chat.network

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object TorHttp {
    private const val READY_TIMEOUT_MS = 90_000L
    private const val READY_CHECK_INTERVAL_MS = 250L

    suspend fun openConnection(
        context: Context,
        rawUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        waitUntilReady(context)
        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress.createUnresolved(TorManager.socksHost(), TorManager.socksPort())
        )
        return (URL(rawUrl).openConnection(proxy) as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            useCaches = false
        }
    }

    fun openDirectConnection(
        rawUrl: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        return (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            useCaches = false
        }
    }

    private suspend fun waitUntilReady(context: Context) {
        val appContext = context.applicationContext
        TorManager.ensureStarted(appContext)
        val startedAt = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - startedAt < READY_TIMEOUT_MS) {
            if (TorManager.status.value is TorManager.Status.Ready) {
                return
            }
            delay(READY_CHECK_INTERVAL_MS)
        }
        throw IllegalStateException("Tor indisponivel")
    }
}
