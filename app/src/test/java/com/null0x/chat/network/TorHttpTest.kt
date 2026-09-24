package com.null0x.chat.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorHttpTest {

    @Test
    fun acceptsOnlyOnionV3HttpUrls() {
        val onionHost = "a".repeat(56) + ".onion"

        assertTrue(TorHttp.isOnionUrl("http://$onionHost/update.json"))
        assertTrue(TorHttp.isOnionUrl("https://$onionHost/profile/image"))
        assertFalse(TorHttp.isOnionUrl("https://github.com/example/app.apk"))
        assertFalse(TorHttp.isOnionUrl("http://abcdefghijklmnop.onion/update.json"))
        assertFalse(TorHttp.isOnionUrl("ftp://$onionHost/update.json"))
        assertFalse(TorHttp.isOnionUrl("http://user:password@$onionHost/update.json"))
    }
}
