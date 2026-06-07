package com.null0x.chat.security.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class IdentityCryptoTest {

    private val wordList = File("src/main/assets/mnemonic/bip39_english.txt")
        .readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private val mnemonicManager = MnemonicManager(wordList)

    @Test
    fun generateMnemonicProducesTwelveValidWords() {
        val mnemonic = mnemonicManager.generate12Words()
        assertTrue(mnemonicManager.validateMnemonic(mnemonic))
        assertEquals(12, mnemonic.trim().split(Regex("\\s+")).size)
    }

    @Test
    fun restoreIdentityKeepsSamePublicKey() {
        val store = InMemoryEncryptedKeyStore()
        val manager = CryptoIdentityManager(mnemonicManager, store)
        val created = manager.createIdentity("senha forte")
        manager.lock()

        val restored = CryptoIdentityManager(mnemonicManager, store)
        restored.restoreIdentity(created.mnemonic, "senha forte")

        assertEquals(created.signingPublicKeyB64, restored.getPublicKey())
        assertEquals(created.exchangePublicKeyB64, restored.getExchangePublicKey())
    }

    @Test
    fun wrongPasswordDoesNotUnlock() {
        val store = InMemoryEncryptedKeyStore()
        val manager = CryptoIdentityManager(mnemonicManager, store)
        manager.createIdentity("senha correta")
        manager.lock()

        assertFalse(manager.unlock("senha errada"))
        assertFalse(manager.isUnlocked())
    }

    @Test
    fun privateSeedIsNotStoredInPlainText() {
        val store = InMemoryEncryptedKeyStore()
        val manager = CryptoIdentityManager(mnemonicManager, store)
        manager.createIdentity("senha segura")
        val record = store.load()
        assertNotNull(record)
        val seed = manager.privateSeedForTesting()!!
        assertNotEquals(Base64.getEncoder().encodeToString(seed), record!!.encryptedPrivateSeedB64)
    }

    @Test
    fun signatureValidationPassesAndFailsAsExpected() {
        val store = InMemoryEncryptedKeyStore()
        val manager = CryptoIdentityManager(mnemonicManager, store)
        manager.createIdentity("senha segura")
        val middleware = PrivateAuthMiddleware(manager.getPublicKey())

        val request = buildSignedRequest(manager, "POST", "/inbox", """{"hello":"world"}""")
        assertTrue(middleware.validateSignedRequest(request))
        val tampered = request.copy(body = """{"hello":"tampered"}""", nonce = "tampered-${request.nonce}")
        assertFalse(middleware.validateSignedRequest(tampered))
    }

    @Test
    fun replayAndTimestampGuardsWork() {
        val store = InMemoryEncryptedKeyStore()
        val manager = CryptoIdentityManager(mnemonicManager, store)
        manager.createIdentity("senha segura")
        val middleware = PrivateAuthMiddleware(manager.getPublicKey(), allowedClockSkewMs = 60_000)

        val replayRequest = buildSignedRequest(manager, "POST", "/send", """{"text":"ok"}""")
        assertTrue(middleware.validateSignedRequest(replayRequest, nowMs = replayRequest.timestamp))
        assertFalse(middleware.validateSignedRequest(replayRequest, nowMs = replayRequest.timestamp))

        val oldRequest = buildSignedRequest(manager, "POST", "/send", """{"text":"ok"}""")
        val skewed = oldRequest.copy(timestamp = oldRequest.timestamp - 120_000, nonce = "old-nonce")
        val oldSigned = skewed.copy(
            timestamp = skewed.timestamp,
            nonce = skewed.nonce,
            signatureB64 = manager.signRequest(
                skewed.method,
                skewed.path,
                skewed.body,
                skewed.timestamp,
                skewed.nonce
            )
        )
        assertFalse(middleware.validateSignedRequest(oldSigned, nowMs = oldSigned.timestamp + 120_000))
    }

    @Test
    fun messageCryptoRoundTripWorks() {
        val senderStore = InMemoryEncryptedKeyStore()
        val sender = CryptoIdentityManager(mnemonicManager, senderStore)
        sender.createIdentity("senha segura")
        val recipientStore = InMemoryEncryptedKeyStore()
        val recipient = CryptoIdentityManager(mnemonicManager, recipientStore)
        val mnemonic = mnemonicManager.generate12Words()
        recipient.restoreIdentity(mnemonic, "senha 2")
        recipient.unlock("senha 2")

        val payload = MessageCrypto.encryptForRecipient("mensagem secreta", recipient.getExchangePublicKey())
        val decrypted = MessageCrypto.decryptMessage(payload, recipient.getExchangePrivateKeyB64())
        assertEquals("mensagem secreta", decrypted)
    }

    @Test
    fun sendTokenRotationInvalidatesPreviousToken() {
        val store = InMemorySendTokenStore()
        val manager = SendTokenManager(store)
        val first = manager.createToken()
        val second = manager.rotateToken()

        assertFalse(manager.validateToken(first))
        assertTrue(manager.validateToken(second))
    }

    private fun buildSignedRequest(
        manager: CryptoIdentityManager,
        method: String,
        path: String,
        body: String
    ): SignedRequest {
        val timestamp = System.currentTimeMillis()
        val nonce = "nonce-${timestamp}"
        return SignedRequest(
            method = method,
            path = path,
            body = body,
            publicKeyB64 = manager.getPublicKey(),
            timestamp = timestamp,
            nonce = nonce,
            signatureB64 = manager.signRequest(method, path, body, timestamp, nonce)
        )
    }
}
