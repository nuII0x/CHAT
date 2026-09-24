package com.null0x.chat.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class TransportRecoveryPolicyTest {
    @Test
    fun recoversWhenLocalSocksEndpointRefusesConnection() {
        val error = IOException("Falha ao enviar", ConnectException("ECONNREFUSED"))

        assertTrue(shouldRecoverTorAfterSendFailure(error))
    }

    @Test
    fun doesNotRestartTorForPeerTimeoutOrNetworkNotReady() {
        assertFalse(shouldRecoverTorAfterSendFailure(SocketTimeoutException("Read timed out")))
        assertFalse(shouldRecoverTorAfterSendFailure(IllegalStateException("Rede ainda nao esta pronta")))
        assertFalse(shouldRecoverTorAfterSendFailure(IOException("Falha SOCKS5: 4")))
    }
}
