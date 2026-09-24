package com.null0x.chat.market

import android.content.Context
import com.null0x.chat.network.TorHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class MarketQuote(
    val symbol: String,
    val lastMinor: String,
    val scale: Int
) {
    fun formattedLast(): String {
        val divisor = BigDecimal.TEN.pow(scale.coerceIn(0, 18))
        return BigDecimal(BigInteger(lastMinor)).divide(divisor)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
    }
}

data class MarketSnapshot(
    val mode: String,
    val quotes: Map<String, MarketQuote>
)

object MarketApi {
    fun isConfigured(): Boolean = MarketServerConfig.ONION_HOST.isNotBlank()

    suspend fun snapshot(context: Context): MarketSnapshot = withContext(Dispatchers.IO) {
        check(isConfigured()) { "Configure o endereço onion do Mercado no build" }
        val connection = TorHttp.openConnection(
            context = context.applicationContext,
            rawUrl = "http://${MarketServerConfig.ONION_HOST}/v1/market/snapshot",
            connectTimeoutMs = 25_000,
            readTimeoutMs = 25_000
        )
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status != 200) throw IllegalStateException("Servidor do Mercado respondeu HTTP $status")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseSnapshot(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseSnapshot(body: String): MarketSnapshot {
        val root = JSONObject(body)
        val mode = root.optString("mode").trim().ifBlank { "unknown" }
        val pairs = root.optJSONArray("pairs") ?: error("Snapshot sem pares")
        val quotes = buildMap {
            for (index in 0 until pairs.length()) {
                val item = pairs.optJSONObject(index) ?: continue
                val symbol = item.optString("symbol").trim()
                val minor = item.optString("lastMinor").trim()
                val scale = item.optInt("scale", -1)
                if (symbol !in setOf("BTC/USDT", "XMR/USDT") ||
                    minor.toBigIntegerOrNull() == null || scale !in 0..18) continue
                put(symbol, MarketQuote(symbol, minor, scale))
            }
        }
        check(quotes.isNotEmpty()) { "Snapshot sem cotação válida" }
        return MarketSnapshot(mode, quotes)
    }
}
