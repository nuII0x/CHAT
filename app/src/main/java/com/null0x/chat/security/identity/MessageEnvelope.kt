package com.null0x.chat.security.identity

import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class EnvelopeDeliveryStatus {
    Pending,
    Replicated,
    Delivered,
    Acked,
    Expired
}

data class MessageEnvelope(
    val messageId: String = UUID.randomUUID().toString(),
    val recipientPublicKeyHash: String,
    val recipientPublicKey: String? = null,
    val recipientExchangePublicKey: String? = null,
    val senderRoute: String? = null,
    val senderPublicKey: String? = null,
    val senderExchangePublicKey: String? = null,
    val ciphertext: String,
    val nonce: String,
    val timestamp: Long,
    val ttl: Long,
    val proof: String,
    val deliveryStatus: EnvelopeDeliveryStatus = EnvelopeDeliveryStatus.Pending
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs > timestamp + ttl
    }

    fun canonicalString(): String {
        return buildString {
            append(messageId)
            append('|')
            append(recipientPublicKeyHash)
            append('|')
            append(recipientPublicKey.orEmpty())
            append('|')
            append(recipientExchangePublicKey.orEmpty())
            append('|')
            append(senderRoute.orEmpty())
            append('|')
            append(senderPublicKey.orEmpty())
            append('|')
            append(senderExchangePublicKey.orEmpty())
            append('|')
            append(ciphertextDigest())
            append('|')
            append(nonce)
            append('|')
            append(timestamp)
            append('|')
            append(ttl)
        }
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put("messageId", messageId)
            .put("recipientPublicKeyHash", recipientPublicKeyHash)
            .put("recipientPublicKey", recipientPublicKey)
            .put("recipientExchangePublicKey", recipientExchangePublicKey)
            .put("senderRoute", senderRoute)
            .put("senderPublicKey", senderPublicKey)
            .put("senderExchangePublicKey", senderExchangePublicKey)
            .put("ciphertext", ciphertext)
            .put("nonce", nonce)
            .put("timestamp", timestamp)
            .put("ttl", ttl)
            .put("proof", proof)
            .put("deliveryStatus", deliveryStatus.name)
    }

    fun copyWithStatus(status: EnvelopeDeliveryStatus): MessageEnvelope {
        return copy(deliveryStatus = status)
    }

    private fun ciphertextDigest(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(ciphertext.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    companion object {
        fun fromJson(json: JSONObject): MessageEnvelope? {
            val messageId = json.optString("messageId").trim()
            val recipientHash = json.optString("recipientPublicKeyHash").trim()
            val ciphertext = json.optString("ciphertext").trim()
            val nonce = json.optString("nonce").trim()
            val proof = json.optString("proof").trim()
            if (messageId.isBlank() || recipientHash.isBlank() || ciphertext.isBlank() || nonce.isBlank()) return null
            return MessageEnvelope(
                messageId = messageId,
                recipientPublicKeyHash = recipientHash,
                recipientPublicKey = json.optString("recipientPublicKey").trim().takeIf { it.isNotBlank() },
                recipientExchangePublicKey = json.optString("recipientExchangePublicKey").trim().takeIf { it.isNotBlank() },
                senderRoute = json.optString("senderRoute").trim().takeIf { it.isNotBlank() },
                senderPublicKey = json.optString("senderPublicKey").trim().takeIf { it.isNotBlank() },
                senderExchangePublicKey = json.optString("senderExchangePublicKey").trim().takeIf { it.isNotBlank() },
                ciphertext = ciphertext,
                nonce = nonce,
                timestamp = json.optLong("timestamp", 0L),
                ttl = json.optLong("ttl", 0L),
                proof = proof,
                deliveryStatus = runCatching {
                    EnvelopeDeliveryStatus.valueOf(json.optString("deliveryStatus", EnvelopeDeliveryStatus.Pending.name))
                }.getOrDefault(EnvelopeDeliveryStatus.Pending)
            )
        }
    }
}

data class AckPacket(
    val messageId: String,
    val recipientPublicKeyHash: String,
    val timestamp: Long,
    val signature: String
) {
    fun canonicalString(): String = "$messageId|$recipientPublicKeyHash|$timestamp"

    fun toJson(): JSONObject {
        return JSONObject()
            .put("messageId", messageId)
            .put("recipientPublicKeyHash", recipientPublicKeyHash)
            .put("timestamp", timestamp)
            .put("signature", signature)
    }

    companion object {
        fun fromJson(json: JSONObject): AckPacket? {
            val messageId = json.optString("messageId").trim()
            val recipientHash = json.optString("recipientPublicKeyHash").trim()
            val signature = json.optString("signature").trim()
            if (messageId.isBlank() || recipientHash.isBlank() || signature.isBlank()) return null
            return AckPacket(
                messageId = messageId,
                recipientPublicKeyHash = recipientHash,
                timestamp = json.optLong("timestamp", 0L),
                signature = signature
            )
        }
    }
}
