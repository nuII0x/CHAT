package com.null0x.chat.security.identity

import com.null0x.chat.storage.ChatStore
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import java.io.File
import java.util.UUID

open class DistributedMessageStore(
    private val database: LocalMessageDatabase,
    private val identityProvider: () -> CryptoIdentityManager,
    private val localRouteProvider: () -> String
) {
    fun localPublicKeyHash(): String = identityProvider().getPublicKeyHash()

    fun createOutgoingEnvelope(
        recipientRoute: String,
        recipientPublicKeyHash: String,
        recipientSigningPublicKey: String,
        recipientExchangePublicKey: String,
        plaintext: String,
        ttlMs: Long,
        messageId: String = UUID.randomUUID().toString()
    ): MessageEnvelope {
        require(recipientRoute.isNotBlank()) {
            "Rota de destino invalida"
        }
        val identity = identityProvider()
        val encrypted = EnvelopeCrypto.encrypt(
            plaintext = plaintext,
            senderExchangePrivateKeyB64 = identity.getExchangePrivateKeyB64(),
            recipientExchangePublicKeyB64 = recipientExchangePublicKey
        )
        val unsigned = MessageEnvelope(
            messageId = messageId,
            recipientPublicKeyHash = recipientPublicKeyHash,
            recipientPublicKey = recipientSigningPublicKey,
            recipientExchangePublicKey = recipientExchangePublicKey,
            senderRoute = localRouteProvider().ifBlank { null },
            senderPublicKey = identity.getPublicKey(),
            senderExchangePublicKey = identity.getExchangePublicKey(),
            ciphertext = encrypted.ciphertextB64,
            nonce = encrypted.nonceB64,
            timestamp = System.currentTimeMillis(),
            ttl = ttlMs,
            proof = ""
        )
        val proof = identity.signText(unsigned.canonicalString())
        val envelope = unsigned.copy(proof = proof)
        database.upsert(StoredEnvelopeRecord(envelope = envelope))
        return envelope
    }

    fun ingestEnvelope(envelope: MessageEnvelope): Boolean {
        if (!validateEnvelope(envelope)) return false
        database.upsert(StoredEnvelopeRecord(envelope = envelope))
        return true
    }

    fun listForRecipient(recipientPublicKeyHash: String, limit: Int = 50): List<StoredEnvelopeRecord> {
        return database.listForRecipient(recipientPublicKeyHash, limit)
    }

    fun pendingReplication(replicaFactor: Int = 5, limit: Int = 50): List<StoredEnvelopeRecord> {
        return database.listPendingReplication(replicaFactor, limit = limit)
    }

    fun markReplica(messageId: String, peer: String) {
        database.markReplica(messageId, peer)
    }

    fun markDelivered(messageId: String) {
        database.markDelivered(messageId)
    }

    fun acknowledge(ack: AckPacket): Boolean {
        val existing = database.get(ack.messageId) ?: return false
        val recipientPublicKey = existing.envelope.recipientPublicKey ?: return false
        if (existing.envelope.recipientPublicKeyHash != ack.recipientPublicKeyHash) return false
        if (!AckManager(identityProvider).validateAck(ack, recipientPublicKey)) return false
        database.markAcked(ack.messageId)
        database.remove(ack.messageId)
        return true
    }

    fun purgeExpired(): Int {
        return database.purgeExpired()
    }

    fun pendingLocalDeliveries(limit: Int = 50): List<StoredEnvelopeRecord> {
        val localHash = localPublicKeyHash()
        if (localHash.isBlank()) return emptyList()
        return database.listForRecipient(localHash, limit)
    }

    fun decryptForLocal(record: StoredEnvelopeRecord): String? {
        val envelope = record.envelope
        val identity = identityProvider()
        if (!identity.isUnlocked()) return null
        val recipientPrivateKey = identity.getExchangePrivateKeyB64()
        val senderExchangePublicKey = envelope.senderExchangePublicKey ?: return null
        return EnvelopeCrypto.decrypt(
            ciphertextB64 = envelope.ciphertext,
            nonceB64 = envelope.nonce,
            senderExchangePublicKeyB64 = senderExchangePublicKey,
            recipientExchangePrivateKeyB64 = recipientPrivateKey
        )
    }

    fun saveToChat(chatStore: ChatStore, peer: String, envelope: MessageEnvelope, text: String) {
        if (peer.isBlank()) return
        if (chatStore.hasMessage(peer, envelope.messageId, isMine = false)) return
        chatStore.upsert(
            peer,
            Message(
                id = envelope.messageId,
                text = text,
                isMine = false,
                timestamp = envelope.timestamp,
                delivery = DeliveryState.Delivered
            )
        )
    }

    private fun validateEnvelope(envelope: MessageEnvelope): Boolean {
        if (envelope.messageId.isBlank()) return false
        if (envelope.recipientPublicKeyHash.isBlank()) return false
        if (envelope.ciphertext.isBlank() || envelope.nonce.isBlank()) return false
        if (envelope.isExpired()) return false
        val senderPublicKey = envelope.senderPublicKey ?: return false
        return identityProvider().verifyText(senderPublicKey, envelope.canonicalString(), envelope.proof)
    }
}

class FileDistributedMessageStore(
    rootDir: File,
    identityProvider: () -> CryptoIdentityManager,
    localRouteProvider: () -> String
) : DistributedMessageStore(
    FileLocalMessageDatabase(rootDir),
    identityProvider,
    localRouteProvider
)
