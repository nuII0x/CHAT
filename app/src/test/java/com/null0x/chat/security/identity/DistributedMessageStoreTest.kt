package com.null0x.chat.security.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DistributedMessageStoreTest {

    private val wordList = File("src/main/assets/mnemonic/bip39_english.txt")
        .readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private val mnemonicManager = MnemonicManager(wordList)

    @Test
    fun envelopeEncryptsAndDecryptsWithoutPlaintextAtRest() {
        val sender = createIdentity("senha remetente")
        val recipient = createIdentity("senha destinatario")
        val store = DistributedMessageStore(
            database = InMemoryLocalMessageDatabase(),
            identityProvider = { sender },
            localRouteProvider = { "onion:sender.onion:5000" }
        )

        val envelope = store.createOutgoingEnvelope(
            recipientRoute = "onion:recipient.onion:5000",
            recipientPublicKeyHash = recipient.getPublicKeyHash(),
            recipientSigningPublicKey = recipient.getPublicKey(),
            recipientExchangePublicKey = recipient.getExchangePublicKey(),
            plaintext = "mensagem super secreta",
            ttlMs = 60_000L,
            messageId = "message-1"
        )

        assertFalse(envelope.ciphertext.contains("mensagem super secreta"))
        val plain = EnvelopeCrypto.decrypt(
            ciphertextB64 = envelope.ciphertext,
            nonceB64 = envelope.nonce,
            senderExchangePublicKeyB64 = sender.getExchangePublicKey(),
            recipientExchangePrivateKeyB64 = recipient.getExchangePrivateKeyB64()
        )
        assertEquals("mensagem super secreta", plain)
    }

    @Test
    fun validAckRemovesEnvelopeAndInvalidOneIsRejected() {
        val sender = createIdentity("senha remetente")
        val recipient = createIdentity("senha destinatario")
        val db = InMemoryLocalMessageDatabase()
        val store = DistributedMessageStore(
            database = db,
            identityProvider = { sender },
            localRouteProvider = { "onion:sender.onion:5000" }
        )

        val envelope = store.createOutgoingEnvelope(
            recipientRoute = "onion:recipient.onion:5000",
            recipientPublicKeyHash = recipient.getPublicKeyHash(),
            recipientSigningPublicKey = recipient.getPublicKey(),
            recipientExchangePublicKey = recipient.getExchangePublicKey(),
            plaintext = "ok",
            ttlMs = 60_000L,
            messageId = "message-ack"
        )

        val ackManager = AckManager { recipient }
        val ack = ackManager.createAck(envelope.messageId, recipient.getPublicKeyHash())
        assertTrue(store.acknowledge(ack))
        assertNull(db.get(envelope.messageId))

        val invalidAck = ack.copy(signature = ack.signature.reversed())
        assertFalse(store.acknowledge(invalidAck))
    }

    @Test
    fun ttlExpiryPurgesRecords() {
        val sender = createIdentity("senha remetente")
        val recipient = createIdentity("senha destinatario")
        val db = InMemoryLocalMessageDatabase()
        val store = DistributedMessageStore(
            database = db,
            identityProvider = { sender },
            localRouteProvider = { "onion:sender.onion:5000" }
        )

        val envelope = store.createOutgoingEnvelope(
            recipientRoute = "onion:recipient.onion:5000",
            recipientPublicKeyHash = recipient.getPublicKeyHash(),
            recipientSigningPublicKey = recipient.getPublicKey(),
            recipientExchangePublicKey = recipient.getExchangePublicKey(),
            plaintext = "expira",
            ttlMs = 1L,
            messageId = "message-ttl"
        )
        assertNotNull(envelope)
        assertEquals(1, db.listAll().size)
        assertEquals(1, db.purgeExpired(System.currentTimeMillis() + 10_000L))
        assertEquals(0, db.listAll().size)
    }

    @Test
    fun duplicateEnvelopeIsNotSavedTwice() {
        val sender = createIdentity("senha remetente")
        val recipient = createIdentity("senha destinatario")
        val db = InMemoryLocalMessageDatabase()
        val store = DistributedMessageStore(
            database = db,
            identityProvider = { sender },
            localRouteProvider = { "onion:sender.onion:5000" }
        )

        val envelope = store.createOutgoingEnvelope(
            recipientRoute = "onion:recipient.onion:5000",
            recipientPublicKeyHash = recipient.getPublicKeyHash(),
            recipientSigningPublicKey = recipient.getPublicKey(),
            recipientExchangePublicKey = recipient.getExchangePublicKey(),
            plaintext = "duplicada",
            ttlMs = 60_000L,
            messageId = "message-dupe"
        )

        assertTrue(store.ingestEnvelope(envelope))
        assertTrue(store.ingestEnvelope(envelope))
        assertEquals(1, db.listAll().size)
    }

    private fun createIdentity(password: String): CryptoIdentityManager {
        val store = InMemoryEncryptedKeyStore()
        val manager = CryptoIdentityManager(mnemonicManager, store)
        manager.createIdentity(password)
        return manager
    }
}
