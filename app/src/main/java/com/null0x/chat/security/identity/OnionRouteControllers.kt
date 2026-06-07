package com.null0x.chat.security.identity

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class OnionHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
    val remoteKey: String
)

data class OnionHttpResponse(
    val statusCode: Int,
    val body: String,
    val contentType: String = "application/json; charset=utf-8"
)

class SendRouteController(
    private val inboxStore: OnionInboxStore,
    private val sendTokenManager: SendTokenManager,
    private val recipientPublicKey: () -> String,
    private val maxMessageBytes: Int = 128 * 1024,
    private val allowedClockSkewMs: Long = 5 * 60 * 1000L
) {
    private val seenNonces = ConcurrentHashMap<String, Long>()
    private val rateLimit = ConcurrentHashMap<String, MutableList<Long>>()

    fun sendMessage(request: OnionHttpRequest, nowMs: Long = System.currentTimeMillis()): OnionHttpResponse {
        val json = runCatching { JSONObject(request.body) }.getOrNull()
            ?: return error(400, "JSON invalido")
        val timestamp = json.optLong("timestamp", 0L)
        if (kotlin.math.abs(nowMs - timestamp) > allowedClockSkewMs) return error(400, "Timestamp invalido")
        if (!checkRateLimit(request.remoteKey, nowMs)) return error(429, "Muitas tentativas")

        val nonce = json.optString("nonce").trim()
        if (nonce.length !in 12..160) return error(400, "Nonce invalido")
        purgeExpiredNonces(nowMs)
        if (seenNonces.putIfAbsent(nonce, nowMs) != null) return error(409, "Nonce repetido")

        val expectedRecipient = recipientPublicKey().trim()
        if (expectedRecipient.isBlank()) return error(503, "Chave publica indisponivel")
        if (json.optString("recipientPublicKey").trim() != expectedRecipient) {
            return error(403, "Destinatario invalido")
        }

        val token = json.optString("antiSpamToken").trim()
        if (!sendTokenManager.validateToken(token)) return error(401, "Token publico invalido")

        val ciphertext = json.optString("ciphertext").trim()
        if (ciphertext.isBlank() || ciphertext.toByteArray(Charsets.UTF_8).size > maxMessageBytes) {
            return error(413, "Mensagem grande demais")
        }

        val message = inboxStore.append(
            recipientPublicKey = expectedRecipient,
            senderPublicKey = json.optString("senderPublicKey").trim(),
            nonce = nonce,
            timestamp = timestamp,
            ciphertext = ciphertext
        )
        return ok(JSONObject().put("ok", true).put("id", message.id))
    }

    private fun checkRateLimit(remoteKey: String, nowMs: Long): Boolean {
        val windowStart = nowMs - 60_000L
        val hits = rateLimit.getOrPut(remoteKey.ifBlank { "unknown" }) { mutableListOf() }
        synchronized(hits) {
            hits.removeAll { it < windowStart }
            if (hits.size >= 12) return false
            hits.add(nowMs)
            return true
        }
    }

    private fun purgeExpiredNonces(nowMs: Long) {
        seenNonces.entries.removeIf { nowMs - it.value > allowedClockSkewMs }
    }
}

class InboxController(
    private val inboxStore: OnionInboxStore,
    private val sendTokenManager: SendTokenManager,
    private val auth: () -> PrivateAuthMiddleware
) {
    fun handle(request: OnionHttpRequest): OnionHttpResponse {
        if (!isAuthorized(request)) return error(401, "Assinatura invalida")
        return when {
            request.method == "GET" && request.path == "/inbox" -> listMessages()
            request.method == "POST" && request.path == "/message/read" -> markAsRead(request)
            request.method == "DELETE" && request.path.startsWith("/message/") -> deleteMessage(request)
            request.method == "POST" && request.path == "/rotate-send-token" -> rotateToken()
            request.method == "POST" && request.path == "/settings" -> ok(JSONObject().put("ok", true))
            else -> error(404, "Rota nao encontrada")
        }
    }

    private fun isAuthorized(request: OnionHttpRequest): Boolean {
        val signed = SignedRequest(
            method = request.method,
            path = request.path,
            body = request.body,
            publicKeyB64 = request.headers["x-public-key"].orEmpty(),
            timestamp = request.headers["x-timestamp"]?.toLongOrNull() ?: 0L,
            nonce = request.headers["x-nonce"].orEmpty(),
            signatureB64 = request.headers["x-signature"].orEmpty()
        )
        return auth().validateSignedRequest(signed)
    }

    private fun listMessages(): OnionHttpResponse {
        val array = JSONArray()
        inboxStore.listMessages().forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("senderPublicKey", message.senderPublicKey)
                    .put("nonce", message.nonce)
                    .put("timestamp", message.timestamp)
                    .put("ciphertext", message.ciphertext)
                    .put("read", message.read)
                    .put("receivedAt", message.receivedAt)
            )
        }
        return ok(JSONObject().put("messages", array))
    }

    private fun markAsRead(request: OnionHttpRequest): OnionHttpResponse {
        val id = runCatching { JSONObject(request.body).optString("id").trim() }.getOrDefault("")
        if (id.isBlank()) return error(400, "ID invalido")
        return ok(JSONObject().put("ok", inboxStore.markRead(id)))
    }

    private fun deleteMessage(request: OnionHttpRequest): OnionHttpResponse {
        val id = request.path.removePrefix("/message/").trim()
        if (id.isBlank()) return error(400, "ID invalido")
        return ok(JSONObject().put("ok", inboxStore.delete(id)))
    }

    private fun rotateToken(): OnionHttpResponse {
        return ok(JSONObject().put("sendToken", sendTokenManager.rotateToken()))
    }
}

fun ok(json: JSONObject): OnionHttpResponse = OnionHttpResponse(200, json.toString())

fun error(status: Int, message: String): OnionHttpResponse {
    return OnionHttpResponse(status, JSONObject().put("ok", false).put("error", message).toString())
}
