package com.null0x.chat.security.identity

class AckManager(
    private val identityManagerProvider: () -> CryptoIdentityManager
) {
    fun createAck(messageId: String, recipientPublicKeyHash: String, timestamp: Long = System.currentTimeMillis()): AckPacket {
        val payload = AckPacket(
            messageId = messageId,
            recipientPublicKeyHash = recipientPublicKeyHash,
            timestamp = timestamp,
            signature = ""
        )
        val signature = identityManagerProvider().signText(payload.canonicalString())
        return payload.copy(signature = signature)
    }

    fun validateAck(ack: AckPacket, recipientPublicKeyB64: String): Boolean {
        if (ack.messageId.isBlank() || ack.recipientPublicKeyHash.isBlank() || ack.signature.isBlank()) return false
        return identityManagerProvider().verifyText(recipientPublicKeyB64, ack.canonicalString(), ack.signature)
    }

    fun encodeAck(ack: AckPacket): String {
        return ack.toJson().toString()
    }
}
