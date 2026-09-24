package com.null0x.chat.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketApiTest {
    @Test
    fun parsesIntegerAmountsWithoutFloatingPointLoss() {
        val snapshot = MarketApi.parseSnapshot(
            """{"mode":"simulation","pairs":[{"symbol":"BTC/USDT","lastMinor":"6428140000000","scale":8}]}"""
        )

        assertEquals("simulation", snapshot.mode)
        assertEquals("64281.40", snapshot.quotes.getValue("BTC/USDT").formattedLast())
    }

    @Test
    fun ignoresUnsupportedPairs() {
        val snapshot = MarketApi.parseSnapshot(
            """{"mode":"simulation","pairs":[{"symbol":"DOGE/USDT","lastMinor":"10","scale":8},{"symbol":"XMR/USDT","lastMinor":"17126000000","scale":8}]}"""
        )

        assertTrue("DOGE/USDT" !in snapshot.quotes)
        assertEquals("171.26", snapshot.quotes.getValue("XMR/USDT").formattedLast())
    }
}
