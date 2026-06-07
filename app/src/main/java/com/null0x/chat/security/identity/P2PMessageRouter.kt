package com.null0x.chat.security.identity

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class PullRequest(
    val recipientPublicKeyHash: String,
    val sinceTimestamp: Long,
    val limit: Int
)

object P2PMessageRouter {
    const val PROTOCOL_PREFIX = "P2P_V1"
    private const val TYPE_ENVELOPE = "ENV"
    private const val TYPE_PULL = "PULL"
    private const val TYPE_PULL_RESPONSE = "PULL_RESP"
    private const val TYPE_ACK = "ACK"

    fun isProtocolMessage(text: String): Boolean {
        return text.startsWith("$PROTOCOL_PREFIX|")
    }

    fun encodeEnvelope(envelope: MessageEnvelope): String {
        return wire(TYPE_ENVELOPE, envelope.toJson().toString())
    }

    fun decodeEnvelope(text: String): MessageEnvelope? {
        val payload = extractPayload(text, TYPE_ENVELOPE) ?: return null
        return runCatching { MessageEnvelope.fromJson(JSONObject(payload)) }.getOrNull()
    }

    fun encodePullRequest(request: PullRequest): String {
        return wire(TYPE_PULL, JSONObject()
            .put("recipientPublicKeyHash", request.recipientPublicKeyHash)
            .put("sinceTimestamp", request.sinceTimestamp)
            .put("limit", request.limit)
            .toString())
    }

    fun decodePullRequest(text: String): PullRequest? {
        val payload = extractPayload(text, TYPE_PULL) ?: return null
        return runCatching {
            val json = JSONObject(payload)
            val recipientHash = json.optString("recipientPublicKeyHash").trim()
            if (recipientHash.isBlank()) return null
            PullRequest(
                recipientPublicKeyHash = recipientHash,
                sinceTimestamp = json.optLong("sinceTimestamp", 0L),
                limit = json.optInt("limit", 50).coerceIn(1, 250)
            )
        }.getOrNull()
    }

    fun encodePullResponse(envelopes: List<MessageEnvelope>): String {
        val array = JSONArray()
        envelopes.forEach { envelope -> array.put(envelope.toJson()) }
        return wire(TYPE_PULL_RESPONSE, array.toString())
    }

    fun decodePullResponse(text: String): List<MessageEnvelope> {
        val payload = extractPayload(text, TYPE_PULL_RESPONSE) ?: return emptyList()
        return runCatching {
            val array = JSONArray(payload)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    MessageEnvelope.fromJson(item)?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeAck(ack: AckPacket): String {
        return wire(TYPE_ACK, ack.toJson().toString())
    }

    fun decodeAck(text: String): AckPacket? {
        val payload = extractPayload(text, TYPE_ACK) ?: return null
        return runCatching { AckPacket.fromJson(JSONObject(payload)) }.getOrNull()
    }

    private fun wire(type: String, payload: String): String {
        return "$PROTOCOL_PREFIX|$type|${Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))}"
    }

    private fun extractPayload(text: String, expectedType: String): String? {
        val parts = text.split('|', limit = 3)
        if (parts.size != 3 || parts[0] != PROTOCOL_PREFIX || parts[1] != expectedType) return null
        return runCatching {
            String(Base64.getDecoder().decode(parts[2]), Charsets.UTF_8)
        }.getOrNull()
    }
}
